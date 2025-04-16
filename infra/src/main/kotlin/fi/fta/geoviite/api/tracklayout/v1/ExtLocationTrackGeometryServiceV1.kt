package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranchType
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingCacheService
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.GeometryChangeRanges
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.getChangedGeometryRanges
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

@Schema(name = "Vastaus: Sijaintiraidegeometria")
data class ExtLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidegeometria")
data class ExtLocationTrackModifiedGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Osoitepiste")
data class ExtCenterLineGeometryPointV1( // TODO Rename
    val x: Double,
    val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: KmNumber,
    @JsonProperty("ratametri") val trackMeter: BigDecimal,
) {
    companion object {
        fun of(addressPoint: AddressPoint): ExtCenterLineGeometryPointV1 {
            return ExtCenterLineGeometryPointV1(
                addressPoint.point.x,
                addressPoint.point.y,
                addressPoint.address.kmNumber,
                addressPoint.address.meters,
            )
        }
    }
}

@GeoviiteService
class ExtLocationTrackGeometryServiceV1
@Autowired
constructor(
    private val extLocationTrackService: ExtLocationTrackServiceV1,
    private val geocodingService: GeocodingService,
    private val geocodingCacheService: GeocodingCacheService,
    private val geocodingDao: GeocodingDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val publicationDao: PublicationDao,
) {
    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun createGeometryResponse(
        oid: Oid<LocationTrack>,
        trackNetworkVersion: Uuid<Publication>?,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
    ): ExtLocationTrackGeometryResponseV1 {
        val layoutContext = MainLayoutContext.official

        val publication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrack =
            extLocationTrackService.getLocationTrackByOidAtMoment(oid, layoutContext, publication.publicationTime)
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

        val geocodingContextCacheKey =
            geocodingDao
                .getLayoutGeocodingContextCacheKey(
                    layoutContext.branch,
                    locationTrack.trackNumberId,
                    publication.publicationTime,
                )
                .let(::requireNotNull)

        return ExtLocationTrackGeometryResponseV1(
            trackNetworkVersion = publication.uuid,
            trackIntervals =
                getExtLocationTrackGeometry(
                    locationTrack.getAlignmentVersionOrThrow(),
                    geocodingContextCacheKey,
                    trackIntervalFilter,
                    resolution,
                    coordinateSystem,
                ),
        )
    }

    fun createGeometryModificationResponse(
        oid: Oid<LocationTrack>,
        modificationsFromVersion: Uuid<Publication>,
        trackNetworkVersion: Uuid<Publication>?,
        resolution: Resolution,
        coordinateSystem: Srid,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
    ): ExtLocationTrackModifiedGeometryResponseV1? {
        val layoutContext = MainLayoutContext.official

        val previousPublication = publicationDao.fetchPublicationByUuid(modificationsFromVersion).let(::requireNotNull)
        val nextPublication =
            trackNetworkVersion?.let { uuid -> publicationDao.fetchPublicationByUuid(uuid).let(::requireNotNull) }
                ?: publicationDao.fetchLatestPublications(LayoutBranchType.MAIN, count = 1).single()

        val locationTrack =
            extLocationTrackService.getLocationTrackByOidAtMoment(oid, layoutContext, nextPublication.publicationTime)
                ?: throw ExtOidNotFoundExceptionV1("location track lookup failed, oid=$oid")

        val newerGeocodingContextCacheKey =
            geocodingDao
                .getLayoutGeocodingContextCacheKey(
                    layoutContext.branch,
                    locationTrack.trackNumberId,
                    nextPublication.publicationTime,
                )
                .let(::requireNotNull)

        return ExtLocationTrackModifiedGeometryResponseV1(
            modificationsFromVersion = previousPublication.uuid,
            trackNetworkVersion = nextPublication.uuid,
            trackIntervals =
                getModifiedLocationTrackGeometry(
                    locationTrack.id as IntId, // TODO Only id needed
                    previousPublication.publicationTime,
                    nextPublication.publicationTime,
                    newerGeocodingContextCacheKey,
                    resolution,
                    // TODO coordinateSystem
                    // TODO trackIntervalFilter
                ),
        )
    }

    private fun getExtLocationTrackGeometry(
        locationTrackAlignmentVersion: RowVersion<LayoutAlignment>,
        geocodingContextCacheKey: GeocodingContextCacheKey,
        trackIntervalFilter: ExtTrackKilometerIntervalV1,
        resolution: Resolution,
        coordinateSystem: Srid,
    ): List<ExtCenterLineTrackIntervalV1> {
        val alignmentAddresses =
            geocodingService
                .getAddressPoints(geocodingContextCacheKey, locationTrackAlignmentVersion, resolution)
                .let(::requireNotNull)

        val filteredPoints =
            alignmentAddresses.allPoints.filter { trackIntervalFilter.containsKmEndInclusive(it.address.kmNumber) }

        // TODO no errors needed here
        val (convertedMidPoints, conversionErrors) =
            convertAddressPointsToRequestCoordinateSystem(coordinateSystem, filteredPoints)

        // TODO This doesn't create changed intervals just yet
        return listOf(
            ExtCenterLineTrackIntervalV1(
                startAddress = alignmentAddresses.startPoint.address.toString(),
                endAddress = alignmentAddresses.endPoint.address.toString(),
                addressPoints = convertedMidPoints?.map(ExtCenterLineGeometryPointV1::of) ?: emptyList(),
            )
        )
    }

    private fun getModifiedLocationTrackGeometry(
        locationTrackId: IntId<LocationTrack>,
        earlierMoment: Instant,
        newerMoment: Instant,
        newerGeocodingContextCacheKey: GeocodingContextCacheKey,
        resolution: Resolution,
    ): List<ExtCenterLineTrackIntervalV1> {
        val layoutContext = MainLayoutContext.official

        val (earlierTrack, earlierAlignment) =
            locationTrackDao
                .fetchOfficialVersionAtMomentOrThrow(layoutContext.branch, locationTrackId, earlierMoment)
                .let(locationTrackService::getWithAlignment)

        val (newerTrack, newerAlignment) =
            locationTrackDao
                .fetchOfficialVersionAtMomentOrThrow(layoutContext.branch, locationTrackId, newerMoment)
                .let(locationTrackService::getWithAlignment)

        val changedGeometryRanges = getChangedGeometryRanges(earlierAlignment.segments, newerAlignment.segments)

        val addressPoints =
            geocodingService
                .getAddressPoints(newerGeocodingContextCacheKey, newerTrack.getAlignmentVersionOrThrow(), resolution)
                .let(::requireNotNull) // TODO Better error

        val geocodingContext =
            geocodingCacheService
                .getGeocodingContext(newerGeocodingContextCacheKey)
                .let(::requireNotNull) // TODO Better error

        // TODO Coordinate system conversion missing
        return groupAddressPointsByTrackRange(mergeIntervals(changedGeometryRanges), addressPoints.allPoints).map {
            (interval, addressPoints) ->
            ExtCenterLineTrackIntervalV1(
                // TODO This seems a little weird, having to geocode the start and endpoints?
                startAddress = geocodingContext.getAddress(interval.trackRange.min).let(::requireNotNull).toString(),
                endAddress = geocodingContext.getAddress(interval.trackRange.max).let(::requireNotNull).toString(),
                addressPoints = addressPoints.map(ExtCenterLineGeometryPointV1::of),
            )
        }
    }
}

private fun mergeIntervals(geometryChangeRanges: GeometryChangeRanges): List<ExtTrackInterval> {
    // TODO Remove the "event" term?
    val intervalEvents =
        listOf(
                geometryChangeRanges.added.flatMap { addedRange ->
                    listOf(
                        IntervalEvent(addedRange.min, IntervalType.ADDITION, IntervalState.START),
                        IntervalEvent(addedRange.max, IntervalType.ADDITION, IntervalState.END),
                    )
                },
                geometryChangeRanges.removed.flatMap { removedRange ->
                    listOf(
                        IntervalEvent(removedRange.min, IntervalType.REMOVAL, IntervalState.START),
                        IntervalEvent(removedRange.max, IntervalType.REMOVAL, IntervalState.END),
                    )
                },
            )
            .flatten()
            .sortedBy { event -> event.trackM }

    var mergedIntervals = mutableListOf<ExtTrackInterval>()

    var tempIntervals = mutableListOf<IntervalEvent>()
    var tempIntervalStartM: Double? = null

    val additionTolerance = 0.001

    for (event in intervalEvents) {
        if (tempIntervals.isEmpty()) {
            tempIntervals.add(event)
            tempIntervalStartM = event.trackM
        } else if (tempIntervals[0].type == event.type && tempIntervals[0].state == event.state) {
            error("previous interval of the same type=${event.type} was unexpectedly already active")
        } else if (tempIntervals[0].type < event.type && event.state == IntervalState.START) {
            // The previous active interval should be split, but kept in the stack as it did not end,
            // as the previous interval can still continue after the overriding interval.
            mergedIntervals.add(
                ExtTrackInterval(Range(requireNotNull(tempIntervalStartM), event.trackM), tempIntervals[0].type)
            )

            tempIntervals.add(0, event)
            tempIntervalStartM = event.trackM
        } else if (tempIntervals[0].type > event.type && event.state == IntervalState.START) {
            // Unprioritized interval addition to the stack, it is active but not the most prioritized.
            tempIntervals
                .binarySearchBy(event.type) { tempInterval -> tempInterval.type }
                .takeIf { index -> index != -1 }
                ?.let { index -> tempIntervals.add(index, event) } ?: tempIntervals.add(event)
        } else if (event.state == IntervalState.END) {
            if (tempIntervals[0].type == event.type) {
                // Active most prioritized interval ended.
                tempIntervalStartM
                    ?.takeIf { startM -> event.trackM - startM >= additionTolerance }
                    ?.let { startM ->
                        mergedIntervals.add(ExtTrackInterval(Range(startM, event.trackM), tempIntervals[0].type))
                    }

                tempIntervals.removeFirst()
                tempIntervalStartM = event.trackM
            } else {
                // This interval type was not prioritized, but it ended while overlapped by another interval.
                tempIntervals.removeAll { previousEvent -> previousEvent.type == event.type }
            }
        } else {
            error("Unhandled path?")
        }
    }

    // Handle last interval if active.
    if (tempIntervals.isNotEmpty()) {
        val lastTrackM = intervalEvents.last().trackM

        tempIntervalStartM
            ?.takeIf { startM -> lastTrackM - startM >= additionTolerance }
            ?.let { startM -> ExtTrackInterval(Range(startM, lastTrackM), tempIntervals[0].type) }
    }

    return mergedIntervals
}

// Assumes that intervals and addressPoints are already sorted.
private fun groupAddressPointsByTrackRange(
    intervals: List<ExtTrackInterval>,
    addressPoints: List<AddressPoint>,
): Map<ExtTrackInterval, List<AddressPoint>> {
    //    val result = mutableMapOf<ExtTrackInterval, MutableList<AddressPoint>>()
    val result = intervals.associateWith { interval -> mutableListOf<AddressPoint>() }.toMutableMap()
    var intervalIndex = 0

    for (addressPoint in addressPoints) {
        if (intervals[intervalIndex].trackRange.max < addressPoint.point.m) {
            if (intervalIndex < intervals.size) {
                intervalIndex++
            } else {
                break // All intervals handled, no more addressPoints needed
            }
        }

        val intervalContainsTrackMeter = intervals[intervalIndex].trackRange.contains(addressPoint.point.m)
        val isAdditiveInterval =
            intervals[intervalIndex].type ==
                IntervalType.ADDITION // TODO Could be optimized to only be assigned once per interval

        if (intervalContainsTrackMeter && isAdditiveInterval) {
            result[intervals[intervalIndex]]?.add(addressPoint)
        }
    }

    return result
}

fun layoutAddressPointToCoordinateSystem(addressPoint: AddressPoint, targetCoordinateSystem: Srid): AddressPoint {
    val convertedPoint = transformNonKKJCoordinate(LAYOUT_SRID, targetCoordinateSystem, addressPoint.point)
    return addressPoint.copy(point = addressPoint.point.copy(x = convertedPoint.x, y = convertedPoint.y))
}
