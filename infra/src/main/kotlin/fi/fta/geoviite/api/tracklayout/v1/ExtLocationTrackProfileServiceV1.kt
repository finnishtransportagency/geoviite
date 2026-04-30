package fi.fta.geoviite.api.tracklayout.v1

import LazyMap
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangle
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.geography.transformHeightValue
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
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.Instant

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
            .fetchPublishedLocationTrackVersionBetween(id, startMoment, endMoment)
            ?.map(locationTrackDao::fetch)
            ?.takeIf { (oldTrack, newTrack) -> oldTrack?.exists == true || newTrack.exists }
            ?.let { (oldTrack, newTrack) ->
                val oldListings = oldTrack?.let { getVerticalGeometryListings(it, branch, startMoment) } ?: emptyList()
                val newListings = getVerticalGeometryListings(newTrack, branch, endMoment)
                createProfileChangeIntervals(oldListings, newListings, coordinateSystem)
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let { trackIntervals ->
                ExtLocationTrackModifiedProfileResponseV1(
                    layoutVersionFrom = ExtLayoutVersionV1(publications.from),
                    layoutVersionTo = ExtLayoutVersionV1(publications.to),
                    locationTrackOid = ExtOidV1(oid),
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackIntervals = trackIntervals,
                )
            }
    }

    private fun getVerticalGeometryListings(
        track: LocationTrack,
        branch: LayoutBranch,
        moment: Instant,
    ): List<VerticalGeometryListing> =
        if (track.exists) {
            val geometry = alignmentDao.fetch(track.getVersionOrThrow())
            val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
            getVerticalGeometryListings(track, geometry, geocodingContext)
        } else emptyList()

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
        val heightTriangles = fetchN60HeightTriangles(listings)
        return ExtProfileAddressRangeV1(
            start = startAddress,
            end = endAddress,
            intersectionPoints =
                listings.map { listing -> toIntersectionPoint(listing, coordinateSystem, heightTriangles) },
        )
    }

    private fun createProfileChangeIntervals(
        oldListings: List<VerticalGeometryListing>,
        newListings: List<VerticalGeometryListing>,
        coordinateSystem: Srid,
    ): List<ExtProfileAddressRangeV1> {
        return when {
            oldListings.isEmpty() -> {
                // All new — single interval covering everything
                if (newListings.isEmpty()) emptyList()
                else {
                    val addresses = newListings.mapNotNull { it.point.address?.round(3) }
                    val heightTriangles = fetchN60HeightTriangles(newListings)
                    listOf(
                        ExtProfileAddressRangeV1(
                            start = addresses.minOrNull()?.format(),
                            end = addresses.maxOrNull()?.format(),
                            intersectionPoints =
                                newListings.map { toIntersectionPoint(it, coordinateSystem, heightTriangles) },
                        )
                    )
                }
            }
            newListings.isEmpty() -> {
                // All removed — single interval covering old addresses, empty points
                val addresses = oldListings.mapNotNull { it.point.address?.round(3) }
                if (addresses.isEmpty()) emptyList()
                else {
                    listOf(
                        ExtProfileAddressRangeV1(
                            start = addresses.minOrNull()?.format(),
                            end = addresses.maxOrNull()?.format(),
                            intersectionPoints = emptyList(),
                        )
                    )
                }
            }
            else -> {
                val heightTriangles = fetchN60HeightTriangles(newListings + oldListings)
                diffProfileListings(oldListings, newListings, coordinateSystem, heightTriangles)
            }
        }
    }

    private fun fetchN60HeightTriangles(listings: List<VerticalGeometryListing>): List<HeightTriangle> =
        listings
            .filter { it.verticalCoordinateSystem == VerticalCoordinateSystem.N60 }
            .flatMap { l -> listOfNotNull(l.start.location, l.point.location, l.end.location) }
            .let { n60Points -> boundingBoxAroundPointsOrNull(n60Points) }
            ?.let { bbox -> heightTriangleDao.fetchTriangles(bbox.polygonFromCorners) } ?: emptyList()
}

private fun diffProfileListings(
    oldListings: List<VerticalGeometryListing>,
    newListings: List<VerticalGeometryListing>,
    coordinateSystem: Srid,
    heightTriangles: List<HeightTriangle>,
): List<ExtProfileAddressRangeV1> {
    // Convert listings to final PVI points, rounding addresses up front so range checks use the same precision
    val oldPoints = oldListings.mapNotNull { listing ->
        listing.point.address?.round(3)?.let { addr ->
            addr to toIntersectionPoint(listing, coordinateSystem, heightTriangles)
        }
    }
    val newPoints = newListings.mapNotNull { listing ->
        listing.point.address?.round(3)?.let { addr ->
            addr to toIntersectionPoint(listing, coordinateSystem, heightTriangles)
        }
    }

    val changedRanges = findChangedRanges(oldPoints, newPoints)

    // For each range, collect ALL new-state points whose address falls within it.
    // The API semantic is "replace all points in this range with the returned list".
    return changedRanges.map { range ->
        val pointsInRange = newPoints.filter { (addr, _) -> range.contains(addr) }.map { (_, point) -> point }
        ExtProfileAddressRangeV1(
            start = range.min.format(),
            end = range.max.format(),
            intersectionPoints = pointsInRange,
        )
    }
}

private fun findChangedRanges(
    oldPoints: List<Pair<TrackMeter, ExtProfilePviPointV1>>,
    newPoints: List<Pair<TrackMeter, ExtProfilePviPointV1>>,
): List<Range<TrackMeter>> {
    val ranges = mutableListOf<Range<TrackMeter>>()
    var range: Range<TrackMeter>? = null

    var oldIdx = 0
    var newIdx = 0

    while (oldIdx <= oldPoints.lastIndex && newIdx <= newPoints.lastIndex) {
        val (oldAddr, oldPoint) = oldPoints[oldIdx]
        val (newAddr, newPoint) = newPoints[newIdx]

        when {
            // No change at this address, close any open range
            oldAddr == newAddr && oldPoint == newPoint -> {
                range?.let(ranges::add)
                range = null
                oldIdx++
                newIdx++
            }
            // Same address but different content: the iteration is in sync but content changed
            newAddr == oldAddr -> {
                range = range?.extend(newAddr) ?: Range(newAddr, newAddr)
                oldIdx++
                newIdx++
            }
            // If addresses differ, we have a change but we're also out-of-sync. Advance the lesser iteration only.
            newAddr > oldAddr -> {
                range = range?.extend(oldAddr) ?: Range(oldAddr, oldAddr)
                oldIdx++
            }
            else -> {
                range = range?.extend(newAddr) ?: Range(newAddr, newAddr)
                newIdx++
            }
        }
    }
    // Any remaining old/new points are obviously changed
    if (oldIdx <= oldPoints.lastIndex) {
        val last = oldPoints.last().first
        range = range?.extend(last) ?: Range(oldPoints[oldIdx].first, last)
    }
    if (newIdx <= newPoints.lastIndex) {
        val last = newPoints.last().first
        range = range?.extend(last) ?: Range(newPoints[newIdx].first, last)
    }
    // Add final range if still open
    range?.let(ranges::add)

    return ranges
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
        gradient = endpoint.angle,
        location = endpoint.location?.let { toExtAddressPoint(it, endpoint.address, coordinateSystem) },
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
        location = point.location?.let { toExtAddressPoint(it, point.address, coordinateSystem) },
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
