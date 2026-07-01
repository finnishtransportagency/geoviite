package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geometry.ElementListing
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.geometry.unknownSwitchName
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.math.IPoint
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class ExtLocationTrackElementListingServiceV1
@Autowired
constructor(
    private val publicationService: PublicationService,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryService: GeometryService,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val switchDao: LayoutSwitchDao,
) {
    fun getExtLocationTrackElementListing(
        oid: ExtOidV1<LocationTrack>,
        layoutVersion: ExtLayoutVersionV1?,
        extCoordinateSystem: ExtSridV1?,
    ): ExtLocationTrackElementListingResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, layoutVersion?.value)
        val coordinateSystem = coordinateSystem(extCoordinateSystem)
        return createElementListingResponse(oid, publication, coordinateSystem)
    }

    private fun createElementListingResponse(
        oid: ExtOidV1<LocationTrack>,
        publication: Publication,
        coordinateSystem: Srid,
    ): ExtLocationTrackElementListingResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val id = idLookup(locationTrackDao, oid.value)
        return locationTrackDao
            .fetchOfficialVersionAtMoment(branch, id, moment)
            ?.let(locationTrackDao::fetch)
            ?.takeIf { it.exists }
            ?.let { track ->
                val geometry = alignmentDao.fetch(track.getVersionOrThrow())
                val geocodingContext =
                    geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                        ?: throwGeocodingContextNotFound(branch, moment, track.trackNumberId)
                val listings = geometryService.getElementListing(
                    track,
                    geometry,
                    geocodingContext.trackNumber,
                    geocodingContext,
                ) { switchId -> switchNameAtMoment(branch, switchId, moment) }
                ExtLocationTrackElementListingResponseV1(
                    layoutVersion = ExtLayoutVersionV1(publication),
                    locationTrackOid = oid,
                    coordinateSystem = ExtSridV1(coordinateSystem),
                    trackIntervals = toElementAddressIntervals(listings, coordinateSystem),
                )
            }
    }

    private fun switchNameAtMoment(branch: LayoutBranch, switchId: IntId<LayoutSwitch>, moment: Instant) =
        switchDao.fetchOfficialVersionAtMoment(branch, switchId, moment)
            ?.let { switchDao.fetch(it).name }
            ?: unknownSwitchName

    private fun toElementAddressIntervals(
        listings: List<ElementListing>,
        coordinateSystem: Srid,
    ): List<ExtElementAddressIntervalV1> {
        if (listings.isEmpty()) return emptyList()

        val intervals = mutableListOf<ExtElementAddressIntervalV1>()
        var currentRun = mutableListOf<ElementListing>()

        for (listing in listings) {
            if (listing.start.address != null && listing.end.address != null) {
                currentRun.add(listing)
            } else {
                if (currentRun.isNotEmpty()) {
                    intervals.add(buildInterval(currentRun, coordinateSystem))
                    currentRun = mutableListOf()
                }
            }
        }
        if (currentRun.isNotEmpty()) {
            intervals.add(buildInterval(currentRun, coordinateSystem))
        }

        if (intervals.isEmpty()) throwNonGeocodableElementInterval()
        return intervals
    }

    private fun buildInterval(run: List<ElementListing>, coordinateSystem: Srid): ExtElementAddressIntervalV1 {
        val start = run.first().start.address!!.formatFixedDecimals(3)
        val end = run.last().end.address!!.formatFixedDecimals(3)
        return ExtElementAddressIntervalV1(
            start = start,
            end = end,
            elements = run.map { listing -> toExtGeometryElement(listing, coordinateSystem) },
        )
    }

    private fun toExtGeometryElement(listing: ElementListing, coordinateSystem: Srid): ExtGeometryElementV1 {
        val (layoutStart, layoutEnd) = toLayoutCoordinates(listing)
        return ExtGeometryElementV1(
            type = ExtElementTypeV1.of(listing.elementType),
            locationStart = toExtAddressPoint(layoutStart, listing.start.address, coordinateSystem),
            locationEnd = toExtAddressPoint(layoutEnd, listing.end.address, coordinateSystem),
            length = listing.lengthMeters,
            plan = toExtPlanReference(listing),
            radius = if (listing.start.radiusMeters != null || listing.end.radiusMeters != null) {
                ExtElementRadiusV1(listing.start.radiusMeters, listing.end.radiusMeters)
            } else null,
            cant = if (listing.start.cant != null || listing.end.cant != null) {
                ExtElementCantV1(listing.start.cant, listing.end.cant)
            } else null,
            direction = ExtElementDirectionV1(listing.start.directionGrads, listing.end.directionGrads),
            notes = toExtNotes(listing),
        )
    }

    private fun toLayoutCoordinates(listing: ElementListing): Pair<IPoint, IPoint> {
        val planSrid = listing.coordinateSystemSrid
        return if (listing.planId == null || planSrid == null || planSrid == LAYOUT_SRID) {
            listing.start.coordinate to listing.end.coordinate
        } else {
            val transform = coordinateTransformationService.getLayoutTransformation(planSrid)
            transform.transform(listing.start.coordinate) to transform.transform(listing.end.coordinate)
        }
    }

    private fun toExtPlanReference(listing: ElementListing): ExtGeometryPlanReferenceV1? {
        if (listing.planId == null) return null
        return ExtGeometryPlanReferenceV1(
            coordinateSystem = listing.coordinateSystemSrid?.toString() ?: listing.coordinateSystemName?.toString(),
            locationStart = ExtPlanCoordinateV1(listing.start.coordinate.x, listing.start.coordinate.y),
            locationEnd = ExtPlanCoordinateV1(listing.end.coordinate.x, listing.end.coordinate.y),
        )
    }

    private fun toExtNotes(listing: ElementListing): List<ExtElementNoteV1> {
        val notes = mutableListOf<ExtElementNoteV1>()
        if (listing.isPartial) {
            notes.add(
                ExtElementNoteV1(
                    code = NOTE_PARTIAL_ELEMENT,
                    description = "Raide sisältää vain osan geometriaelementistä",
                )
            )
        }
        if (listing.connectedSwitchName != null) {
            notes.add(
                ExtElementNoteV1(
                    code = NOTE_SWITCH_ELEMENT,
                    description = "Elementti kuuluu vaihteeseen",
                )
            )
        }
        return notes
    }
}
