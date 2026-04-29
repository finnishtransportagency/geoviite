package fi.fta.geoviite.api.tracklayout.v1

import LazyMap
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.geometry.CurvedSectionEndpoint
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.geometry.IntersectionPoint
import fi.fta.geoviite.infra.geometry.LinearSection
import fi.fta.geoviite.infra.geometry.VerticalGeometryListing
import fi.fta.geoviite.infra.geometry.toVerticalGeometryListing
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

@GeoviiteService
class ExtLocationTrackProfileServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val geometryDao: GeometryDao,
    private val heightTriangleDao: HeightTriangleDao,
) {
    fun getExtLocationTrackProfile(
        oid: ExtOidV1<LocationTrack>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackProfileResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        return createProfileResponse(oid.value, publication, coordinateSystem)
    }

    fun getExtLocationTrackProfileModifications(
        oid: ExtOidV1<LocationTrack>,
        layoutVersionFrom: ExtLayoutVersionV1,
        layoutVersionTo: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackModifiedProfileResponseV1? {
        val publications = publicationService.getPublicationsToCompare(layoutVersionFrom.value, layoutVersionTo?.value)
        val id = idLookup(locationTrackDao, oid.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        return if (publications.areDifferent()) {
            createProfileModificationResponse(oid.value, id, publications, coordinateSystem)
        } else {
            publicationsAreTheSame(layoutVersionFrom.value)
        }
    }

    private fun createProfileResponse(
        oid: Oid<LocationTrack>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackProfileResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val id = idLookup(locationTrackDao, oid)
        return locationTrackDao
            .fetchOfficialVersionAtMoment(branch, id, moment)
            ?.let(locationTrackDao::fetch)
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { track ->
                val geometry = alignmentDao.fetch(track.getVersionOrThrow())
                val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                val listings = getVerticalGeometryListings(track, geometry, geocodingContext)
                val (startAddress, endAddress) =
                    geocodingContext?.let { getTrackAddresses(geometry, it) } ?: (null to null)

                ExtLocationTrackProfileResponseV1(
                    layoutVersion = ExtLayoutVersionV1(publication),
                    locationTrackOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackInterval = toProfileAddressRange(startAddress, endAddress, listings, coordinateSystem),
                )
            }
    }

    private fun createProfileModificationResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        coordinateSystem: Srid,
    ): ExtLocationTrackModifiedProfileResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedLocationTrackBetween(id, startMoment, endMoment)
            ?.let(locationTrackDao::fetch)
            ?.let { newTrack ->
                // Get the new version's profile data
                val newGeometry = if (newTrack.exists) alignmentDao.fetch(newTrack.getVersionOrThrow()) else null
                val geocodingContext =
                    if (newTrack.exists) {
                        geocodingService.getGeocodingContextAtMoment(branch, newTrack.trackNumberId, endMoment)
                    } else null
                val listings =
                    newGeometry?.let { g -> getVerticalGeometryListings(newTrack, g, geocodingContext) } ?: emptyList()
                val (startAddress, endAddress) =
                    if (newGeometry != null && geocodingContext != null) {
                        getTrackAddresses(newGeometry, geocodingContext)
                    } else {
                        null to null
                    }

                ExtLocationTrackModifiedProfileResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    locationTrackOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackIntervals = listOf(toProfileAddressRange(startAddress, endAddress, listings, coordinateSystem)),
                )
            }
    }

    private fun getVerticalGeometryListings(
        track: LocationTrack,
        geometry: LocationTrackGeometry,
        geocodingContext: GeocodingContext<ReferenceLineM>?,
    ): List<VerticalGeometryListing> =
        toVerticalGeometryListing(
            track,
            geometry,
            null,
            null,
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            LazyMap(::getHeaderAndAlignment)::get,
        )

    private fun getHeaderAndAlignment(id: IntId<GeometryAlignment>): Pair<GeometryPlanHeader, GeometryAlignment> {
        val header = geometryDao.fetchAlignmentPlanVersion(id).let(geometryDao::getPlanHeader)
        val geometryAlignment = geometryDao.fetchAlignments(header.units, geometryAlignmentId = id).first()
        return header to geometryAlignment
    }

    private fun toProfileAddressRange(
        startAddress: String?,
        endAddress: String?,
        listings: List<VerticalGeometryListing>,
        coordinateSystem: Srid,
    ): ExtProfileAddressRangeV1 {
        val heightTriangles = fetchHeightTrianglesIfNeeded(listings)
        return ExtProfileAddressRangeV1(
            start = startAddress,
            end = endAddress,
            intersectionPoints =
                listings.map { listing -> toIntersectionPoint(listing, coordinateSystem, heightTriangles) },
        )
    }

    private fun fetchHeightTrianglesIfNeeded(listings: List<VerticalGeometryListing>): List<HeightTriangle> {
        val needsN60Conversion = listings.any { it.verticalCoordinateSystem == VerticalCoordinateSystem.N60 }
        if (!needsN60Conversion) return emptyList()

        val allPoints = listings.flatMap { listing ->
            listOfNotNull(listing.start.location, listing.point.location, listing.end.location)
        }
        if (allPoints.isEmpty()) return emptyList()

        val boundingBox = boundingBoxAroundPoints(allPoints)
        return heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)
    }
}

private fun getTrackAddresses(
    geometry: LocationTrackGeometry,
    geocodingContext: GeocodingContext<ReferenceLineM>,
): Pair<String?, String?> {
    val startAddress = geometry.start?.let { toAddress(it, geocodingContext) }
    val endAddress = geometry.end?.let { toAddress(it, geocodingContext) }
    return if (startAddress != null && endAddress != null) startAddress to endAddress else null to null
}

private fun toAddress(point: IPoint, geocodingContext: GeocodingContext<ReferenceLineM>): String? =
    geocodingContext
        .getAddress(point)
        ?.takeIf { (_, intersect) -> intersect == IntersectType.WITHIN }
        ?.first
        ?.formatFixedDecimals(3)

private fun toIntersectionPoint(
    listing: VerticalGeometryListing,
    coordinateSystem: Srid,
    heightTriangles: List<HeightTriangle>,
): ExtProfilePviPointV1 {
    val remarks = mutableListOf<ExtProfileRemarkV1>()
    if (listing.overlapsAnother) {
        remarks.add(
            ExtProfileRemarkV1(
                code = "kaltevuusjakso_limittain",
                description = "Kaltevuusjakso on limittäin toisen jakson kanssa",
            )
        )
    }

    return ExtProfilePviPointV1(
        curvedSectionStart =
            toProfileCurvedSectionEndpoint(
                listing.start,
                listing.verticalCoordinateSystem,
                heightTriangles,
                coordinateSystem,
            ),
        intersectionPoint =
            toProfileIntersectionPoint(
                listing.point,
                listing.verticalCoordinateSystem,
                heightTriangles,
                coordinateSystem,
            ),
        curvedSectionEnd =
            toProfileCurvedSectionEndpoint(
                listing.end,
                listing.verticalCoordinateSystem,
                heightTriangles,
                coordinateSystem,
            ),
        roundingRadius = listing.radius,
        tangent = listing.tangent,
        linearSectionBackward = toProfileLinearSection(listing.linearSectionBackward),
        linearSectionForward = toProfileLinearSection(listing.linearSectionForward),
        stationValues =
            ExtProfileStationValuesV1(
                start = listing.alignmentStartStation?.let(::roundTo3Decimals),
                intersectionPoint = listing.alignmentPointStation?.let(::roundTo3Decimals),
                end = listing.alignmentEndStation?.let(::roundTo3Decimals),
            ),
        planVerticalCoordinateSystem = listing.verticalCoordinateSystem?.toExtString(),
        planElevationMeasurementMethod = listing.elevationMeasurementMethod?.toExtString(),
        remarks = remarks,
    )
}

private fun toProfileCurvedSectionEndpoint(
    endpoint: CurvedSectionEndpoint,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    heightTriangles: List<HeightTriangle>,
    coordinateSystem: Srid,
): ExtProfileCurvedSectionEndpointV1 =
    ExtProfileCurvedSectionEndpointV1(
        heightOriginal = endpoint.height,
        heightN2000 = computeN2000Height(endpoint.height, endpoint.location, verticalCoordinateSystem, heightTriangles),
        gradient = endpoint.angle ?: BigDecimal.ZERO,
        location = toProfileLocation(endpoint.address, endpoint.location, coordinateSystem),
    )

private fun toProfileIntersectionPoint(
    point: IntersectionPoint,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    heightTriangles: List<HeightTriangle>,
    coordinateSystem: Srid,
): ExtProfileIntersectionPointV1 =
    ExtProfileIntersectionPointV1(
        heightOriginal = point.height,
        heightN2000 = computeN2000Height(point.height, point.location, verticalCoordinateSystem, heightTriangles),
        location = toProfileLocation(point.address, point.location, coordinateSystem),
    )

private fun toProfileLinearSection(section: LinearSection): ExtProfileLinearSectionV1 =
    ExtProfileLinearSectionV1(length = section.stationValueDistance, linearPartLength = section.linearSegmentLength)

private fun computeN2000Height(
    height: BigDecimal,
    location: IPoint?,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    heightTriangles: List<HeightTriangle>,
): BigDecimal? =
    when (verticalCoordinateSystem) {
        VerticalCoordinateSystem.N2000 -> height
        VerticalCoordinateSystem.N60 ->
            location?.let {
                try {
                    transformHeightValue(height.toDouble(), location, heightTriangles, verticalCoordinateSystem)
                        .let(::roundTo3Decimals)
                } catch (_: IllegalArgumentException) {
                    null
                }
            }
        VerticalCoordinateSystem.N43 -> null
        null -> null
    }

private fun toProfileLocation(
    address: fi.fta.geoviite.infra.common.TrackMeter?,
    location: fi.fta.geoviite.infra.math.RoundedPoint?,
    coordinateSystem: Srid,
): ExtProfileLocationV1 {
    val transformedPoint = location?.let { point ->
        when (coordinateSystem) {
            LAYOUT_SRID -> point
            else -> transformNonKKJCoordinate(LAYOUT_SRID, coordinateSystem, point)
        }
    }
    return ExtProfileLocationV1(
        trackAddress = address?.formatFixedDecimals(3),
        x = transformedPoint?.let { BigDecimal(it.x.toString()) },
        y = transformedPoint?.let { BigDecimal(it.y.toString()) },
    )
}

private fun VerticalCoordinateSystem.toExtString(): String =
    when (this) {
        VerticalCoordinateSystem.N2000 -> "N2000"
        VerticalCoordinateSystem.N60 -> "N60"
        VerticalCoordinateSystem.N43 -> "N43"
    }

private fun ElevationMeasurementMethod.toExtString(): String =
    when (this) {
        ElevationMeasurementMethod.TOP_OF_SLEEPER -> "Korkeusviiva"
        ElevationMeasurementMethod.TOP_OF_RAIL -> "Kiskon selkä"
    }
