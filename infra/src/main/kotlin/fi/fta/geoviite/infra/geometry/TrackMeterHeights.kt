package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingReferencePoint
import fi.fta.geoviite.infra.geocoding.getIndexRangeForRangeInOrderedList
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.util.processFlattened
import java.math.BigDecimal

fun collectTrackMeterHeights(
    startDistance: Double,
    endDistance: Double,
    geocodingContext: GeocodingContext,
    alignment: IAlignment,
    tickLength: Int,
    geometryAlignmentBoundaryPoints: List<GeometryAlignmentBoundaryPoint> = listOf(),
    getHeightAt: (point: AlignmentPoint, segmentIndex: Int?) -> Double?,
): List<KmHeights>? {
    val (alignmentStart, alignmentEnd) = geocodingContext.getStartAndEnd(alignment)
    if (alignmentStart == null || alignmentEnd == null) return null

    val referencePointIndices =
        getReferencePointIndexRangeCoveringAlignmentMRange(geocodingContext, alignment, startDistance, endDistance)
            ?: return null

    val ticksByKm =
        getTicksToSendByKm(
            geometryAlignmentBoundaryPoints,
            alignment,
            geocodingContext,
            referencePointIndices,
            tickLength,
            alignmentStart,
            alignmentEnd,
        )
    val tickTrackLocationsByKm =
        getTickTrackLocationsByKm(geocodingContext, alignment, ticksByKm, referencePointIndices)

    return referencePointIndices.toList().mapIndexed { kmIndex, referencePointIndex ->
        val kmNumber = geocodingContext.referencePoints[referencePointIndex].kmNumber
        val kmTicks = ticksByKm[kmIndex]
        val kmTrackLocations = tickTrackLocationsByKm[kmIndex]
        val endM =
            (if (referencePointIndex == referencePointIndices.last)
                getLastEndM(geocodingContext, referencePointIndices, alignmentEnd, alignment)
            else tickTrackLocationsByKm[kmIndex + 1].first().point.m)

        val trackKmHeights =
            kmTicks
                .zip(kmTrackLocations) { tick, address ->
                    TrackMeterHeight(
                        address.point.m,
                        address.address.meters.toDouble(),
                        getHeightAt(address.point, tick.segmentIndex),
                        address.point.toPoint(),
                    )
                }
                // if different sides of a segment boundary have exactly the same height, the height
                // ticks end up equal here: Throw one out as unneeded
                .distinct()
        KmHeights(kmNumber, trackKmHeights, endM)
    }
}

private fun getReferencePointIndexRangeCoveringAlignmentMRange(
    geocodingContext: GeocodingContext,
    alignment: IAlignment,
    startDistance: Double,
    endDistance: Double,
): IntRange? =
    alignment.getPointAtM(startDistance)?.let(geocodingContext::getAddress)?.let { (startAddress) ->
        alignment.getPointAtM(endDistance)?.let(geocodingContext::getAddress)?.let { (endAddress) ->
            getIndexRangeForRangeInOrderedList(geocodingContext.referencePoints, startAddress, endAddress) {
                referencePoint,
                address ->
                referencePoint.kmNumber.compareTo(address.kmNumber)
            }
        }
    }

private fun getTicksToSendByKm(
    geometryAlignmentBoundaryPoints: List<GeometryAlignmentBoundaryPoint>,
    alignment: IAlignment,
    geocodingContext: GeocodingContext,
    referencePointIndices: IntRange,
    tickLength: Int,
    alignmentStart: AddressPoint,
    alignmentEnd: AddressPoint,
): List<List<TrackMeterHeightTick>> {
    val geometryAlignmentBoundariesInKmRange =
        filterGeometryAlignmentBoundariesWithinKmRange(
            alignment,
            geocodingContext,
            referencePointIndices,
            geometryAlignmentBoundaryPoints,
        )
    val alignmentBoundaryTicksByKm =
        locateAlignmentBoundaryAddresses(geometryAlignmentBoundariesInKmRange, alignment, geocodingContext)

    return referencePointIndices.map { referencePointIndex ->
        val referencePoint = geocodingContext.referencePoints[referencePointIndex]
        val kmNumber = referencePoint.kmNumber
        getTicksToSendForKm(
            tickLength,
            alignmentBoundaryTicksByKm[kmNumber] ?: listOf(),
            referencePoint,
            getKmLengthAtReferencePointIndex(referencePointIndex, geocodingContext),
            kmNumber,
            alignmentStart,
            alignmentEnd,
        )
    }
}

// pure optimization: Ignore geometry alignment boundaries not within the km range we're sending
private fun filterGeometryAlignmentBoundariesWithinKmRange(
    alignment: IAlignment,
    geocodingContext: GeocodingContext,
    referencePointIndices: IntRange,
    geometryAlignmentBoundaries: List<GeometryAlignmentBoundaryPoint>,
): List<GeometryAlignmentBoundaryPoint> {
    val startAddress = TrackMeter(geocodingContext.referencePoints[referencePointIndices.first].kmNumber, 0)
    val endAddress =
        if (referencePointIndices.last == geocodingContext.referencePoints.lastIndex) null
        else TrackMeter(geocodingContext.referencePoints[referencePointIndices.last + 1].kmNumber, 0)
    val rangePoints = geocodingContext.getTrackLocations(alignment, listOfNotNull(startAddress, endAddress))
    // either or both addresses could fail to geocode onto the alignment
    val rangeStart = rangePoints[0]?.point?.m
    val rangeEnd = rangePoints.getOrNull(1)?.point?.m

    return geometryAlignmentBoundaries.filter { boundary ->
        (rangeStart == null || boundary.distanceOnAlignment >= rangeStart) &&
            (rangeEnd == null || boundary.distanceOnAlignment <= rangeEnd)
    }
}

private fun locateAlignmentBoundaryAddresses(
    alignmentBoundaryAddresses: List<GeometryAlignmentBoundaryPoint>,
    alignment: IAlignment,
    geocodingContext: GeocodingContext,
): Map<KmNumber, List<TrackMeterHeightTick>> =
    alignmentBoundaryAddresses
        .mapNotNull { boundary ->
            alignment.getPointAtM(boundary.distanceOnAlignment)?.let { point ->
                geocodingContext.getAddress(point)?.let { address -> address.first to boundary.segmentIndex }
            }
        }
        .groupBy(
            { (trackMeter) -> trackMeter.kmNumber },
            { (trackMeter, segmentIndex) -> TrackMeterHeightTick(trackMeter.meters, segmentIndex) },
        )

private fun getTicksToSendForKm(
    tickLength: Int,
    alignmentBoundaryTicks: List<TrackMeterHeightTick>,
    referencePoint: GeocodingReferencePoint,
    kmLength: Double,
    kmNumber: KmNumber,
    alignmentStart: AddressPoint,
    alignmentEnd: AddressPoint,
): List<TrackMeterHeightTick> {
    val allTicksForKm = getAllTicksToSendForKm(tickLength, referencePoint, kmLength, alignmentBoundaryTicks)
    return fitKmTicksWithinAlignmentBounds(kmNumber, alignmentStart, alignmentEnd, allTicksForKm)
}

private fun getAllTicksToSendForKm(
    tickLength: Int,
    referencePoint: GeocodingReferencePoint,
    kmLength: Double,
    alignmentBoundaryTicks: List<TrackMeterHeightTick>,
): List<TrackMeterHeightTick> {
    // The choice of a half-tick-length minimum is totally arbitrary
    val minTickSpace = BigDecimal(tickLength).setScale(1) / BigDecimal(2)
    val lastPoint = (referencePoint.meters.toDouble() + kmLength - minTickSpace.toDouble()).toInt().coerceAtLeast(0)

    // Ordinary track meter height ticks don't need segment indices because they clearly hit a
    // specific segment; but points on different sides of a segment boundary are often the exact
    // same point, but potentially have different heights (or more often null/not-null heights).
    val ordinaryTicks =
        (0..lastPoint step tickLength).map { distance -> TrackMeterHeightTick(distance.toBigDecimal(), null) }
    val allTicks = (alignmentBoundaryTicks + ordinaryTicks).sortedBy { it.trackMeterInKm }
    return allTicks.filterIndexed { i, (trackMeterInKm, segmentIndex) ->
        tickIsGoodToSend(segmentIndex, i, trackMeterInKm, allTicks, minTickSpace)
    }
}

// Logic for visualization: prevent height ticks from getting too close to each other. Ordinary
// ticks always have room between each other, we just want to prevent them from getting too close to
// segment boundary ticks for clarity.
private fun tickIsGoodToSend(
    segmentIndex: Int?,
    tickIndex: Int,
    trackMeterInKm: BigDecimal,
    allTicks: List<TrackMeterHeightTick>,
    minTickSpace: BigDecimal,
): Boolean {
    val isSegmentBoundaryPoint = segmentIndex != null
    val isKmStartTick = tickIndex == 0
    val hasSpaceAfterPreviousTick =
        tickIndex > 0 && trackMeterInKm - allTicks[tickIndex - 1].trackMeterInKm >= minTickSpace
    val hasSpaceBeforeNextTick =
        (tickIndex == allTicks.lastIndex || allTicks[tickIndex + 1].trackMeterInKm - trackMeterInKm >= minTickSpace)

    return isSegmentBoundaryPoint || isKmStartTick || (hasSpaceAfterPreviousTick && hasSpaceBeforeNextTick)
}

private fun fitKmTicksWithinAlignmentBounds(
    kmNumber: KmNumber,
    alignmentStart: AddressPoint,
    alignmentEnd: AddressPoint,
    ticks: List<TrackMeterHeightTick>,
): List<TrackMeterHeightTick> {
    // Special-case first and last alignment points so we get as close as possible to the track ends
    val alignmentStartTick =
        if (kmNumber == alignmentStart.address.kmNumber) TrackMeterHeightTick(alignmentStart.address.meters, null)
        else null
    val alignmentEndTick =
        if (kmNumber == alignmentEnd.address.kmNumber) TrackMeterHeightTick(alignmentEnd.address.meters, null) else null

    val ticksStrictlyAfterStart =
        if (alignmentStartTick != null)
            ticks.dropWhile { tick -> tick.trackMeterInKm <= alignmentStartTick.trackMeterInKm }
        else ticks

    val ticksStrictlyWithinAlignment =
        if (alignmentEndTick != null)
            ticksStrictlyAfterStart.takeWhile { tick -> tick.trackMeterInKm < alignmentEndTick.trackMeterInKm }
        else ticksStrictlyAfterStart

    return listOfNotNull(alignmentStartTick) + ticksStrictlyWithinAlignment + listOfNotNull(alignmentEndTick)
}

private fun getTickTrackLocationsByKm(
    geocodingContext: GeocodingContext,
    alignment: IAlignment,
    ticksByKm: List<List<TrackMeterHeightTick>>,
    referencePointIndices: IntRange,
): List<List<AddressPoint>> {
    val trackAddresses =
        ticksByKm.zip(referencePointIndices) { kmTicks, referencePointIndex ->
            val km = geocodingContext.referencePoints[referencePointIndex].kmNumber
            kmTicks.map { tick -> TrackMeter(km, tick.trackMeterInKm) }
        }
    return processFlattened(trackAddresses) { allTrackAddresses ->
        // null-safety: ticksByKm came from fitKmTicksWithinAlignmentBounds, which
        // drops all ticks outside the alignment bounds, so everything is geocodable.
        geocodingContext.getTrackLocations(alignment, allTrackAddresses).map(::checkNotNull)
    }
}

private fun getKmLengthAtReferencePointIndex(referencePointIndex: Int, geocodingContext: GeocodingContext) =
    if (referencePointIndex == geocodingContext.referencePoints.size - 1) {
        geocodingContext.referenceLineGeometry.length - geocodingContext.referencePoints[referencePointIndex].distance
    } else {
        geocodingContext.referencePoints[referencePointIndex + 1].distance -
            geocodingContext.referencePoints[referencePointIndex].distance
    }

private fun getLastEndM(
    geocodingContext: GeocodingContext,
    referencePointIndices: IntRange,
    alignmentEnd: AddressPoint,
    alignment: IAlignment,
): Double =
    if (geocodingContext.referencePoints[referencePointIndices.last].kmNumber == alignmentEnd.address.kmNumber)
        alignmentEnd.point.m
    else {
        // indexing and null safety: Here we know we're on a track km before the alignment's end
        // address, so the next track km's start address must both exist on the reference line and
        // be geocodable onto the alignment.
        val nextKmTrackAddress =
            TrackMeter(geocodingContext.referencePoints[referencePointIndices.last + 1].kmNumber, 0)
        checkNotNull(geocodingContext.getTrackLocation(alignment, nextKmTrackAddress)).point.m
    }

data class GeometryAlignmentBoundaryPoint(val distanceOnAlignment: Double, val segmentIndex: Int)

data class TrackMeterHeightTick(val trackMeterInKm: BigDecimal, val segmentIndex: Int?)
