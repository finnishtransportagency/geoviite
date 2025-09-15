package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingKm
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.GeocodingAlignmentM
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.util.getIndexRangeForRangeInOrderedList
import fi.fta.geoviite.infra.util.processFlattened
import java.math.BigDecimal

fun <M : AlignmentM<M>> collectTrackMeterTicks(
    startDistance: LineM<M>,
    endDistance: LineM<M>,
    geocodingContext: GeocodingContext<*>,
    alignment: IAlignment<M>,
    tickLength: Int,
    geometryAlignmentBoundaryPoints: List<GeometryAlignmentBoundaryPoint<M>> = listOf(),
): List<KmTicks<M>>? {
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
        val kmNumber = geocodingContext.kms[referencePointIndex].kmNumber
        val kmTicks = ticksByKm[kmIndex]
        val kmTrackLocations = tickTrackLocationsByKm[kmIndex]
        fun getTrackEndEndM() = getLastEndM(geocodingContext, referencePointIndices, alignmentEnd, alignment)
        val endM =
            (if (referencePointIndex == referencePointIndices.last) getTrackEndEndM()
            else tickTrackLocationsByKm[kmIndex + 1].first()?.point?.m ?: getTrackEndEndM())

        val trackKmTicks =
            kmTicks
                .zip(kmTrackLocations) { tick, address -> address?.let { TrackMeterTick(address, tick.segmentIndex) } }
                .filterNotNull()
        KmTicks(kmNumber, trackKmTicks, endM)
    }
}

private fun <M : AlignmentM<M>> getReferencePointIndexRangeCoveringAlignmentMRange(
    geocodingContext: GeocodingContext<*>,
    alignment: IAlignment<M>,
    startDistance: LineM<M>,
    endDistance: LineM<M>,
): IntRange? =
    alignment.getPointAtM(startDistance)?.let(geocodingContext::getAddress)?.let { (startAddress) ->
        alignment.getPointAtM(endDistance)?.let(geocodingContext::getAddress)?.let { (endAddress) ->
            getIndexRangeForRangeInOrderedList(geocodingContext.kms, startAddress, endAddress) { referencePoint, address
                ->
                referencePoint.kmNumber.compareTo(address.kmNumber)
            }
        }
    }

private fun <M : AlignmentM<M>, GM : GeocodingAlignmentM<GM>> getTicksToSendByKm(
    geometryAlignmentBoundaryPoints: List<GeometryAlignmentBoundaryPoint<M>>,
    alignment: IAlignment<M>,
    geocodingContext: GeocodingContext<GM>,
    kmIndices: IntRange,
    tickLength: Int,
    alignmentStart: AddressPoint<M>,
    alignmentEnd: AddressPoint<M>,
): List<List<TrackMeterHeightTick>> {
    val geometryAlignmentBoundariesInKmRange =
        filterGeometryAlignmentBoundariesWithinKmRange(
            alignment,
            geocodingContext,
            kmIndices,
            geometryAlignmentBoundaryPoints,
        )
    val alignmentBoundaryTicksByKm =
        locateAlignmentBoundaryAddresses(geometryAlignmentBoundariesInKmRange, alignment, geocodingContext)

    return kmIndices.map { kmIndex ->
        val km = geocodingContext.kms[kmIndex]
        getTicksToSendForKm(
            tickLength,
            alignmentBoundaryTicksByKm[km.kmNumber] ?: listOf(),
            km,
            alignmentStart,
            alignmentEnd,
        )
    }
}

// pure optimization: Ignore geometry alignment boundaries not within the km range we're sending
private fun <M : AlignmentM<M>> filterGeometryAlignmentBoundariesWithinKmRange(
    alignment: IAlignment<M>,
    geocodingContext: GeocodingContext<*>,
    referencePointIndices: IntRange,
    geometryAlignmentBoundaries: List<GeometryAlignmentBoundaryPoint<M>>,
): List<GeometryAlignmentBoundaryPoint<M>> {
    val startAddress = TrackMeter(geocodingContext.kms[referencePointIndices.first].kmNumber, 0)
    val endAddress =
        if (referencePointIndices.last == geocodingContext.kms.lastIndex) null
        else TrackMeter(geocodingContext.kms[referencePointIndices.last + 1].kmNumber, 0)
    val rangePoints = geocodingContext.getTrackLocations(alignment, listOfNotNull(startAddress, endAddress))
    // either or both addresses could fail to geocode onto the alignment
    val rangeStart = rangePoints[0]?.point?.m
    val rangeEnd = rangePoints.getOrNull(1)?.point?.m

    return geometryAlignmentBoundaries.filter { boundary ->
        (rangeStart == null || boundary.distanceOnAlignment >= rangeStart) &&
            (rangeEnd == null || boundary.distanceOnAlignment <= rangeEnd)
    }
}

private fun <M : AlignmentM<M>> locateAlignmentBoundaryAddresses(
    alignmentBoundaryAddresses: List<GeometryAlignmentBoundaryPoint<M>>,
    alignment: IAlignment<M>,
    geocodingContext: GeocodingContext<*>,
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

private fun <M : AlignmentM<M>, GM : GeocodingAlignmentM<GM>> getTicksToSendForKm(
    tickLength: Int,
    alignmentBoundaryTicks: List<TrackMeterHeightTick>,
    km: GeocodingKm<GM>,
    alignmentStart: AddressPoint<M>,
    alignmentEnd: AddressPoint<M>,
): List<TrackMeterHeightTick> {
    val allTicksForKm = getAllTicksToSendForKm(tickLength, km, alignmentBoundaryTicks)
    return fitKmTicksWithinAlignmentBounds(km.kmNumber, alignmentStart, alignmentEnd, allTicksForKm)
}

private fun <GM : GeocodingAlignmentM<GM>> getAllTicksToSendForKm(
    tickLength: Int,
    km: GeocodingKm<GM>,
    alignmentBoundaryTicks: List<TrackMeterHeightTick>,
): List<TrackMeterHeightTick> {
    // The choice of a half-tick-length minimum is totally arbitrary
    val minTickSpace = BigDecimal(tickLength).setScale(1) / BigDecimal(2)
    val lastPoint = (km.startMeters.toDouble() + km.length - minTickSpace.toDouble()).toInt().coerceAtLeast(0)

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

private fun <M : AlignmentM<M>> fitKmTicksWithinAlignmentBounds(
    kmNumber: KmNumber,
    alignmentStart: AddressPoint<M>,
    alignmentEnd: AddressPoint<M>,
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

private fun <M : AlignmentM<M>> getTickTrackLocationsByKm(
    geocodingContext: GeocodingContext<*>,
    alignment: IAlignment<M>,
    ticksByKm: List<List<TrackMeterHeightTick>>,
    referencePointIndices: IntRange,
): List<List<AddressPoint<M>?>> {
    val trackAddresses =
        ticksByKm.zip(referencePointIndices) { kmTicks, referencePointIndex ->
            val km = geocodingContext.kms[referencePointIndex].kmNumber
            kmTicks.map { tick -> TrackMeter(km, tick.trackMeterInKm) }
        }
    return processFlattened(trackAddresses) { allTrackAddresses ->
        geocodingContext.getTrackLocations(alignment, allTrackAddresses)
    }
}

private fun <M : AlignmentM<M>> getLastEndM(
    geocodingContext: GeocodingContext<*>,
    kmIndices: IntRange,
    alignmentEnd: AddressPoint<M>,
    alignment: IAlignment<M>,
): LineM<M> =
    if (geocodingContext.kms[kmIndices.last].kmNumber == alignmentEnd.address.kmNumber) alignmentEnd.point.m
    else {
        // indexing and null safety: Here we know we're on a track km before the alignment's end
        // address, so the next track km's start address must both exist on the reference line and
        // be geocodable onto the alignment.
        val nextKmTrackAddress = TrackMeter(geocodingContext.kms[kmIndices.last + 1].kmNumber, 0)
        checkNotNull(geocodingContext.getTrackLocation(alignment, nextKmTrackAddress)).point.m
    }

data class GeometryAlignmentBoundaryPoint<M : AlignmentM<M>>(val distanceOnAlignment: LineM<M>, val segmentIndex: Int)

data class TrackMeterHeightTick(val trackMeterInKm: BigDecimal, val segmentIndex: Int?)
