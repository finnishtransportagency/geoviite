package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationComparison
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.tracklayout.LAYOUT_COORDINATE_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import org.springframework.beans.factory.annotation.Autowired
import java.time.Instant

@GeoviiteService
class ExtLocationTrackGeometryServiceV1
@Autowired
constructor(
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val publicationService: PublicationService,
    private val publicationDao: PublicationDao,
) {
    fun getExtLocationTrackGeometry(
        oid: Oid<LocationTrack>,
        trackLayoutVersion: Uuid<Publication>?,
        extResolution: ExtResolutionV1?,
        coordinateSystem: Srid?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtLocationTrackGeometryResponseV1? {
        val publication = publicationService.getPublicationByUuidOrLatest(LayoutBranchType.MAIN, trackLayoutVersion)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val coordinateSystem = coordinateSystem ?: LAYOUT_SRID
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return createGeometryResponse(oid, publication, resolution, coordinateSystem, addressFilter)
    }

    fun getExtLocationTrackGeometryModifications(
        oid: Oid<LocationTrack>,
        trackLayoutVersionFrom: Uuid<Publication>,
        trackLayoutVersionTo: Uuid<Publication>?,
        extResolution: ExtResolutionV1?,
        coordinateSystem: Srid?,
        addressFilterStart: ExtMaybeTrackKmOrTrackMeterV1?,
        addressFilterEnd: ExtMaybeTrackKmOrTrackMeterV1?,
    ): ExtLocationTrackModifiedGeometryResponseV1? {
        val publications = publicationService.getPublicationsToCompare(trackLayoutVersionFrom, trackLayoutVersionTo)
        // Lookup before change check to produce consistent error if oid is not found
        val id = idLookup(locationTrackDao, oid)
        val resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
        val coordinateSystem = coordinateSystem ?: LAYOUT_SRID
        val addressFilter = createAddressFilter(addressFilterStart, addressFilterEnd)
        return if (publications.areDifferent()) {
            createGeometryModificationResponse(oid, id, publications, resolution, coordinateSystem, addressFilter)
        } else {
            publicationsAreTheSame(trackLayoutVersionFrom)
        }
    }

    private fun createGeometryModificationResponse(
        oid: Oid<LocationTrack>,
        id: IntId<LocationTrack>,
        publications: PublicationComparison,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtLocationTrackModifiedGeometryResponseV1? {
        val branch = publications.to.layoutBranch.branch
        val startMoment = publications.from.publicationTime
        val endMoment = publications.to.publicationTime
        return publicationDao
            .fetchPublishedLocationTrackGeomsBetween(id, startMoment, endMoment)
            ?.let { (old, new) -> old?.let(locationTrackDao::fetch) to locationTrackDao.fetch(new) }
            ?.takeIf { (old, new) -> old?.exists == true || new.exists }
            ?.let { (oldTrack, newTrack) ->
                val oldPoints =
                    oldTrack
                        ?.takeIf { it.exists }
                        ?.let { track -> getAddressPoints(branch, startMoment, track, resolution, addressFilter) }
                val newPoints =
                    newTrack
                        .takeIf { it.exists }
                        ?.let { track -> getAddressPoints(branch, endMoment, track, resolution, addressFilter) }
                ExtLocationTrackModifiedGeometryResponseV1(
                    trackLayoutVersionFrom = publications.from.uuid,
                    trackLayoutVersionTo = publications.to.uuid,
                    locationTrackOid = oid,
                    coordinateSystem = coordinateSystem,
                    trackIntervals = createModifiedTrackIntervals(oldPoints, newPoints, coordinateSystem),
                )
            }
    }

    private fun createModifiedTrackIntervals(
        oldPoints: AlignmentAddresses<LocationTrackM>?,
        newPoints: AlignmentAddresses<LocationTrackM>?,
        coordinateSystem: Srid,
    ): List<ExtCenterLineTrackIntervalV1> =
        when {
            oldPoints == null && newPoints == null -> error("Must have some points to compare")
            oldPoints == null -> {
                // Full new interval was added
                listOf(toExtInterval(newPoints!!, coordinateSystem))
            }
            newPoints == null -> {
                // All points removed on the original interval
                listOf(
                    ExtCenterLineTrackIntervalV1(
                        startAddress = oldPoints.startPoint.address.formatFixedDecimals(3),
                        endAddress = oldPoints.endPoint.address.formatFixedDecimals(3),
                        addressPoints = emptyList(),
                    )
                )
            }
            else -> {
                val changedIntervals = mutableListOf<ExtCenterLineTrackIntervalV1>()
                val rangeBuilder = RangeBuilder(coordinateSystem)
                var oldIndex = 0
                var newIndex = 0
                while (oldIndex < oldPoints.allPoints.lastIndex || newIndex < newPoints.allPoints.lastIndex) {
                    val old = oldPoints.allPoints.getOrNull(oldIndex)
                    val new = newPoints.allPoints.getOrNull(newIndex)
                    when {
                        old == null && new == null -> {
                            error("Loop failed to end despite both lists being fully processed")
                        }
                        old == null -> { // Reached the end of the old points -> rest of new different
                            rangeBuilder.extendWithPoints(
                                newPoints.allPoints.subList(newIndex, newPoints.allPoints.size)
                            )
                            newIndex = newPoints.allPoints.size
                        }
                        new == null -> { // Reached the end of the new points -> rest of old different
                            rangeBuilder.extendAsEmpty(old.address)
                            rangeBuilder.extendAsEmpty(oldPoints.allPoints.last().address)
                            oldIndex = oldPoints.allPoints.size
                        }
                        else ->
                            when {
                                old.address < new.address -> {
                                    rangeBuilder.extendAsEmpty(old.address)
                                    oldIndex++
                                }
                                old.address > new.address -> {
                                    rangeBuilder.extendWithPoint(new)
                                    newIndex++
                                }
                                else -> {
                                    if (old.point.isSame(new.point, LAYOUT_COORDINATE_DELTA)) {
                                        rangeBuilder.buildAndReset()?.let(changedIntervals::add)
                                    } else {
                                        rangeBuilder.extendWithPoint(new)
                                    }
                                    oldIndex++
                                    newIndex++
                                }
                            }
                    }
                }
                // Finish range if one still exists
                rangeBuilder.buildAndReset()?.let(changedIntervals::add)
                changedIntervals
            }
        }

    private class RangeBuilder(val coordinateSystem: Srid) {
        private var start: TrackMeter? = null
        private var end: TrackMeter? = null
        private val points = mutableListOf<AddressPoint<LocationTrackM>>()

        fun extendAsEmpty(address: TrackMeter) {
            if (start == null) start = address
            end = address
        }

        fun extendWithPoint(addressPoint: AddressPoint<LocationTrackM>) {
            if (start == null) start = addressPoint.address
            end = addressPoint.address
            points.add(addressPoint)
        }

        fun extendWithPoints(addressPoints: List<AddressPoint<LocationTrackM>>) {
            if (addressPoints.isNotEmpty()) {
                if (start == null) start = addressPoints.first().address
                end = addressPoints.last().address
                points.addAll(addressPoints)
            }
        }

        fun buildAndReset(): ExtCenterLineTrackIntervalV1? {
            val startAddress = start?.formatFixedDecimals(3)
            val endAddress = end?.formatFixedDecimals(3)
            val addressPoints = points.map { addressPoint -> toExtAddressPoint(addressPoint, coordinateSystem) }
            reset()
            return if (startAddress != null && endAddress != null) {
                ExtCenterLineTrackIntervalV1(startAddress, endAddress, addressPoints)
            } else {
                null
            }
        }

        fun reset() {
            start = null
            end = null
            points.clear()
        }
    }

    private fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        publication: Publication,
        resolution: Resolution,
        coordinateSystem: Srid,
        addressFilter: AddressFilter,
    ): ExtLocationTrackGeometryResponseV1? {
        val branch = publication.layoutBranch.branch
        val moment = publication.publicationTime
        val id = idLookup(locationTrackDao, oid)
        return locationTrackDao
            .fetchOfficialVersionAtMoment(branch, id, moment)
            ?.let(locationTrackDao::fetch)
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            ?.takeIf { it.exists }
            ?.let { locationTrack ->
                val filteredAddressPoints = getAddressPoints(branch, moment, locationTrack, resolution, addressFilter)
                ExtLocationTrackGeometryResponseV1(
                    trackLayoutVersion = publication.uuid,
                    locationTrackOid = oid,
                    coordinateSystem = coordinateSystem,
                    trackInterval =
                        // Address points are null for example in case when the user provided
                        // address filter is outside the track boundaries.
                        filteredAddressPoints?.let { addressPoints -> toExtInterval(addressPoints, coordinateSystem) },
                )
            }
    }

    private fun toExtInterval(
        addressPoints: AlignmentAddresses<LocationTrackM>,
        coordinateSystem: Srid,
    ): ExtCenterLineTrackIntervalV1 =
        ExtCenterLineTrackIntervalV1(
            startAddress = addressPoints.startPoint.address.formatFixedDecimals(3),
            endAddress = addressPoints.endPoint.address.formatFixedDecimals(3),
            addressPoints =
                addressPoints.allPoints.map { addressPoint -> toExtAddressPoint(addressPoint, coordinateSystem) },
        )

    private fun getAddressPoints(
        branch: LayoutBranch,
        moment: Instant,
        track: LocationTrack,
        resolution: Resolution,
        addressFilter: AddressFilter,
    ): AlignmentAddresses<LocationTrackM>? =
        if (!track.exists) {
            // Deleted tracks have no geometry in API since there's no guarantee of geocodable addressing
            null
        } else if (addressFilter.start == null && addressFilter.end == null) {
            // Prefer using cached (full) address list when address filter is unassigned
            val geocodingContextCacheKey =
                geocodingDao.getLayoutGeocodingContextCacheKey(branch, track.trackNumberId, moment)
                    ?: throw ExtGeocodingFailedV1("could not get geocoding context cache key")

            geocodingService.getAddressPoints(geocodingContextCacheKey, track.getVersionOrThrow(), resolution)
        } else {
            // When filter is assigned, compute the desired interval on the fly
            val geometry = alignmentDao.fetch(requireNotNull(track.version))
            val context =
                geocodingService.getGeocodingContextAtMoment(branch, track.trackNumberId, moment)
                    ?: throw ExtGeocodingFailedV1("could not calculate address points")
            context.getAddressPoints(geometry, resolution, addressFilter)
        }
}
