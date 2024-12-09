package fi.fta.geoviite.infra.geocoding

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DEFAULT_TRACK_METER_DECIMALS
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.GeocodingFailureException
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.IntersectType.AFTER
import fi.fta.geoviite.infra.math.IntersectType.BEFORE
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.math.Intersection
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.angleAvgRads
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.interpolate
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointInDirection
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs

data class AddressPoint(val point: AlignmentPoint, val address: TrackMeter) {
    fun isSame(other: AddressPoint) = address.isSame(other.address) && point.isSame(other.point)

    fun withIntegerPrecision(): AddressPoint? =
        if (address.hasIntegerPrecision()) {
            this
        } else if (address.matchesIntegerValue()) {
            AddressPoint(point = point, address = address.floor())
        } else {
            null
        }
}

data class AlignmentAddresses(
    val startPoint: AddressPoint,
    val endPoint: AddressPoint,
    val startIntersect: IntersectType,
    val endIntersect: IntersectType,
    val midPoints: List<AddressPoint>,
) {
    @get:JsonIgnore
    val allPoints: List<AddressPoint> by lazy { emptyList<AddressPoint>() + startPoint + midPoints + endPoint }

    @get:JsonIgnore
    val integerPrecisionPoints: List<AddressPoint> by lazy {
        // midPoints are even anyhow, so just drop zero decimals from start/end
        val start =
            startPoint.withIntegerPrecision()?.takeIf { s ->
                midPoints.isEmpty() || s.address < midPoints.first().address
            }
        val end =
            endPoint.withIntegerPrecision()?.takeIf { e ->
                (start == null || e.address > start.address) &&
                    (midPoints.isEmpty() || e.address > midPoints.last().address)
            }
        listOfNotNull(start) + midPoints + listOfNotNull(end)
    }
}

data class AlignmentStartAndEnd<T>(val id: IntId<T>, val start: AlignmentEndPoint?, val end: AlignmentEndPoint?) {
    companion object {
        fun <T> of(id: IntId<T>, alignment: IAlignment, geocodingContext: GeocodingContext?): AlignmentStartAndEnd<T> {
            val start = alignment.start?.let { p -> AlignmentEndPoint(p, geocodingContext?.getAddress(p)?.first) }
            val end = alignment.end?.let { p -> AlignmentEndPoint(p, geocodingContext?.getAddress(p)?.first) }
            return AlignmentStartAndEnd(id, start, end)
        }
    }
}

data class AlignmentEndPoint(val point: AlignmentPoint, val address: TrackMeter?)

data class ProjectionLine(
    val address: TrackMeter,
    val projection: Line,
    val distance: Double,
    val referenceDirection: Double,
)

data class GeocodingReferencePoint(
    val kmNumber: KmNumber,
    val meters: BigDecimal,
    val distance: Double,
    val kmPostOffset: Double,
    val intersectType: IntersectType,
) {
    val distanceRounded = roundTo3Decimals(distance)
}

data class AddressAndM(val address: TrackMeter, val m: Double, val intersectType: IntersectType)

/**
 * Don't generate a meter that is shorter than this. Prevents an extra projection being generated when the KM changes on
 * an exact meter.
 */
private const val MIN_METER_LENGTH = 0.001

/**
 * Projection line validation parameter. They should be 1m apart from each other along reference line by definition.
 * This is the maximum deviation allowed - must accommodate some difference for turns.
 */
private const val PROJECTION_LINE_DISTANCE_DEVIATION = 0.05

/**
 * Projection line validation parameter. If the line makes rough turns, the projections will result in convoluted
 * addresses. This is the maximum angle-change between 2 projection points (1m on reference line).
 */
private const val PROJECTION_LINE_MAX_ANGLE_DELTA = PI / 16

private val logger: Logger = LoggerFactory.getLogger(GeocodingContext::class.java)

data class KmPostWithRejectedReason(val kmPost: TrackLayoutKmPost, val rejectedReason: KmPostRejectedReason)

data class GeocodingContextCreateResult(
    val geocodingContext: GeocodingContext,
    val rejectedKmPosts: List<KmPostWithRejectedReason>,
    val validKmPosts: List<TrackLayoutKmPost>,
    val startPointRejectedReason: StartPointRejectedReason?,
)

enum class StartPointRejectedReason {
    TOO_LONG
}

enum class KmPostRejectedReason {
    TOO_FAR_APART,
    NO_LOCATION,
    IS_BEFORE_START_ADDRESS,
    INTERSECTS_BEFORE_REFERENCE_LINE,
    INTERSECTS_AFTER_REFERENCE_LINE,
    DUPLICATE,
}

data class GeocodingContext(
    val trackNumber: TrackNumber,
    val startAddress: TrackMeter,
    val referenceLineGeometry: IAlignment,
    val referencePoints: List<GeocodingReferencePoint>,
    val projectionLineDistanceDeviation: Double = PROJECTION_LINE_DISTANCE_DEVIATION,
    val projectionLineMaxAngleDelta: Double = PROJECTION_LINE_MAX_ANGLE_DELTA,
) {

    init {
        require(referenceLineGeometry.segments.isNotEmpty()) {
            "Cannot geocode with empty reference line geometry, trackNumber=${trackNumber}"
        }

        require(referencePoints.isNotEmpty()) { "Cannot geocode without reference points, trackNumber=${trackNumber}" }

        require(
            referencePoints.zipWithNext { a, b -> abs(b.distance - a.distance) }.all { TrackMeter.isMetersValid(it) }
        ) {
            "Reference points are too far apart from each other, trackNumber=${trackNumber}"
        }
    }

    private val polyLineEdges: List<PolyLineEdge> by lazy { getPolyLineEdges(referenceLineGeometry) }
    val projectionLines: List<ProjectionLine> by lazy {
        require(isSame(polyLineEdges.last().endM, referenceLineGeometry.length, LAYOUT_M_DELTA)) {
            "Polyline edges should cover the whole reference line geometry: " +
                "trackNumber=$trackNumber " +
                "alignment=$referenceLineGeometry " +
                "edgeMValues=${polyLineEdges.map { e -> e.startM..e.endM }}"
        }
        // TODO: GVT-1727 The validation claims to filter out bad projections, but we use the un-filtered here
        createProjectionLines(referencePoints, polyLineEdges).also { lines ->
            validateProjectionLines(lines, projectionLineDistanceDeviation, projectionLineMaxAngleDelta)
        }
    }
    val allKms: List<KmNumber> by lazy { referencePoints.map(GeocodingReferencePoint::kmNumber).distinct() }

    val startProjection: ProjectionLine? by lazy {
        val meters = referencePoints.first().meters
        if (!TrackMeter.isMetersValid(meters)) null
        else {
            val address = TrackMeter(referencePoints.first().kmNumber, referencePoints.first().meters)
            val projectionLine = polyLineEdges.first().crossSectionAt(0.0)
            ProjectionLine(address, projectionLine, 0.0, polyLineEdges.first().referenceDirection)
        }
    }

    val endProjection: ProjectionLine? by lazy {
        val meters = referenceLineGeometry.length - referencePoints.last().let { p -> p.distance - p.meters.toDouble() }
        if (!TrackMeter.isMetersValid(meters)) null
        else {
            val address = TrackMeter(referencePoints.last().kmNumber, meters, referencePoints.first().meters.scale())
            val projectionLine = polyLineEdges.last().crossSectionAt(referenceLineGeometry.length)
            ProjectionLine(
                address,
                projectionLine,
                referenceLineGeometry.length,
                polyLineEdges.last().referenceDirection,
            )
        }
    }

    fun preload() {
        // Preload the lazy properties
        polyLineEdges
        startProjection
        endProjection
        projectionLines
        allKms
    }

    fun getProjectionLine(address: TrackMeter): ProjectionLine? {
        val startProjection = startProjection
        val endProjection = endProjection
        return if (startProjection == null || endProjection == null) {
            null
        } else if (
            address.decimalCount() == 0 || address <= startProjection.address || address >= endProjection.address
        ) {
            findCachedProjectionLine(address)
        } else
            findCachedProjectionLine(address.floor())?.let { previous ->
                val distance = previous.distance + (address.meters.toDouble() - previous.address.meters.toDouble())
                findEdge(distance, polyLineEdges)?.let { edge ->
                    ProjectionLine(address, edge.crossSectionAt(distance), distance, edge.referenceDirection)
                }
            }
    }

    private fun findCachedProjectionLine(address: TrackMeter): ProjectionLine? {
        val startProjection = startProjection
        val endProjection = endProjection
        return if (
            startProjection == null ||
                endProjection == null ||
                address !in startProjection.address..endProjection.address
        )
            null
        else if (address == startProjection.address) startProjection
        else if (address == endProjection.address) endProjection
        else if (projectionLines.isEmpty()) null
        else projectionLines.binarySearch { line -> line.address.compareTo(address) }.let(projectionLines::getOrNull)
    }

    companion object {
        fun create(
            trackNumber: TrackNumber,
            startAddress: TrackMeter,
            referenceLineGeometry: IAlignment,
            kmPosts: List<TrackLayoutKmPost>,
        ): GeocodingContextCreateResult {
            val (validatedKmPosts, invalidKmPosts) = validateKmPosts(kmPosts, startAddress)

            val (validReferencePoints, kmPostsOutsideGeometry) =
                createReferencePoints(startAddress, validatedKmPosts, referenceLineGeometry)

            val validKmPosts =
                validatedKmPosts.filterNot { vkp -> kmPostsOutsideGeometry.any { kp -> kp.kmPost.id == vkp.id } }

            val startKmIsTooLong = startKmIsTooLong(startAddress, referenceLineGeometry, validReferencePoints)

            return GeocodingContextCreateResult(
                geocodingContext =
                    GeocodingContext(
                        trackNumber = trackNumber,
                        referenceLineGeometry = referenceLineGeometry,
                        referencePoints = validReferencePoints,
                        startAddress = startAddress,
                    ),
                rejectedKmPosts = invalidKmPosts + kmPostsOutsideGeometry,
                validKmPosts = validKmPosts,
                startPointRejectedReason = if (startKmIsTooLong) StartPointRejectedReason.TOO_LONG else null,
            )
        }

        private fun startKmIsTooLong(
            startAddress: TrackMeter,
            referenceLineGeometry: IAlignment,
            referencePoints: List<GeocodingReferencePoint>,
        ): Boolean =
            if (referencePoints.isEmpty()) false
            else {
                val startMeters =
                    referencePoints[0].distance + startAddress.meters.setScale(0, RoundingMode.CEILING).toDouble()
                val length = if (referencePoints.size > 1) referencePoints[1].distance else referenceLineGeometry.length
                !TrackMeter.isMetersValid(startMeters + length)
            }

        private fun createReferencePoints(
            startAddress: TrackMeter,
            kmPosts: List<TrackLayoutKmPost>,
            referenceLineGeometry: IAlignment,
        ): Pair<List<GeocodingReferencePoint>, List<KmPostWithRejectedReason>> {
            val kpReferencePoints =
                kmPosts.mapNotNull { post ->
                    post.layoutLocation?.let { location ->
                        toReferencePoint(location, post.kmNumber, referenceLineGeometry)
                    }
                }

            val firstPoint = GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN)
            val referencePoints = listOf(firstPoint) + kpReferencePoints

            return validateReferencePoints(referencePoints, kmPosts)
        }

        private fun validateKmPosts(
            kmPosts: List<TrackLayoutKmPost>,
            startAddress: TrackMeter,
        ): Pair<List<TrackLayoutKmPost>, List<KmPostWithRejectedReason>> {
            val (withoutLocations, withLocations) = kmPosts.partition { it.layoutLocation == null }

            val (invalidStartAddresses, validKmPosts) =
                withLocations.partition { TrackMeter(it.kmNumber, BigDecimal.ZERO) <= startAddress }

            val duplicateKmPosts = kmPosts.groupBy { it.kmNumber }.filter { it.value.size > 1 }.values.flatten()

            val rejectedKmPosts =
                withoutLocations.map { it to KmPostRejectedReason.NO_LOCATION } +
                    invalidStartAddresses.map { it to KmPostRejectedReason.IS_BEFORE_START_ADDRESS } +
                    duplicateKmPosts.map { it to KmPostRejectedReason.DUPLICATE }

            return validKmPosts to rejectedKmPosts.map { (kp, reason) -> KmPostWithRejectedReason(kp, reason) }
        }

        private fun validateReferencePoints(
            referencePoints: List<GeocodingReferencePoint>,
            kmPosts: List<TrackLayoutKmPost>,
        ): Pair<List<GeocodingReferencePoint>, List<KmPostWithRejectedReason>> {
            val (withinPoints, beforePoints, afterPoints) =
                referencePoints
                    .groupBy { it.intersectType }
                    .let { byIntersect ->
                        Triple(
                            byIntersect[WITHIN] ?: emptyList(),
                            byIntersect[BEFORE]?.map { it to KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE }
                                ?: emptyList(),
                            byIntersect[AFTER]?.map { it to KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE }
                                ?: emptyList(),
                        )
                    }

            val invalidIndex =
                withinPoints
                    .zipWithNext { a, b -> abs(b.distance - a.distance) }
                    .let { distances -> distances.indexOfFirst { !TrackMeter.isMetersValid(it) } }

            val (validPoints, invalidPoints) =
                if (invalidIndex == -1) withinPoints to emptyList()
                else {
                    val idx = invalidIndex + 1
                    withinPoints.take(idx) to listOf(withinPoints[idx] to KmPostRejectedReason.TOO_FAR_APART)
                }

            val rejectedKmPosts =
                (beforePoints + afterPoints + invalidPoints).map { (rp, reason) ->
                    val kp = kmPosts.first { k -> k.kmNumber == rp.kmNumber }

                    KmPostWithRejectedReason(kp, reason)
                }

            return validPoints.distinctBy { it.distance } to rejectedKmPosts
        }

        private fun toReferencePoint(location: IPoint, kmNumber: KmNumber, referenceLineGeometry: IAlignment) =
            referenceLineGeometry.getClosestPointM(location)?.let { (distance, intersectType) ->
                val pointOnLine =
                    requireNotNull(referenceLineGeometry.getPointAtM(distance)) {
                        "Couldn't resolve distance to point on reference line: not continuous?"
                    }

                GeocodingReferencePoint(
                    kmNumber = kmNumber,
                    meters = BigDecimal.ZERO,
                    distance = distance,
                    kmPostOffset = lineLength(location, pointOnLine),
                    intersectType = intersectType,
                )
            }
    }

    fun getM(coordinate: IPoint) = referenceLineGeometry.getClosestPointM(coordinate)

    fun getAddressAndM(coordinate: IPoint, addressDecimals: Int = DEFAULT_TRACK_METER_DECIMALS): AddressAndM? =
        referenceLineGeometry.getClosestPointM(coordinate)?.let { (mValue, type) ->
            getAddress(mValue, addressDecimals)?.let { address -> AddressAndM(address, mValue, type) }
        }

    fun getAddress(coordinate: IPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): Pair<TrackMeter, IntersectType>? =
        getAddressAndM(coordinate, decimals)?.let { (address, _, type) -> address to type }

    fun getAddress(targetDistance: Double, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): TrackMeter? {
        val addressPoint = findPreviousPoint(targetDistance)
        val meters = round(addressPoint.meters.toDouble() + targetDistance - addressPoint.distance, decimals)
        return if (TrackMeter.isMetersValid(meters)) TrackMeter(addressPoint.kmNumber, meters) else null
    }

    val referenceLineAddresses by lazy { getAddressPoints(referenceLineGeometry) }

    fun getPartialAddressRange(
        sourceAlignment: LayoutAlignment,
        segmentIndices: IntRange,
    ): Pair<AddressPoint, AddressPoint>? {
        assert(segmentIndices.first >= 0 && segmentIndices.last <= sourceAlignment.segments.lastIndex) {
            "Segment indices out of bounds: indices=$segmentIndices segments=${sourceAlignment.segments.size}"
        }
        val startSegment = sourceAlignment.segments[segmentIndices.first]
        val startSegmentM = sourceAlignment.segmentMs[segmentIndices.first]
        val endSegment = sourceAlignment.segments[segmentIndices.last]
        val endSegmentM = sourceAlignment.segmentMs[segmentIndices.last]
        val sourceStart = startSegment.segmentStart.toAlignmentPoint(0.0).let(this::toAddressPoint)?.first
        val endSegmentStart = endSegmentM.min - startSegmentM.max
        val sourceEnd = endSegment.segmentEnd.toAlignmentPoint(endSegmentStart).let(this::toAddressPoint)?.first
        return if (sourceStart != null && sourceEnd != null) {
            sourceStart to sourceEnd
        } else {
            null
        }
    }

    private fun toAddressPoint(point: AlignmentPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS) =
        getAddress(point, decimals)?.let { (address, intersectType) -> AddressPoint(point, address) to intersectType }

    fun getAddressPoints(alignment: IAlignment): AlignmentAddresses? {
        val startPoint = alignment.start?.let(::toAddressPoint)
        val endPoint = alignment.end?.let(::toAddressPoint)
        return if (startPoint != null && endPoint != null) {
            val midPoints =
                getMidPoints(
                    alignment,
                    (startPoint.first.address + MIN_METER_LENGTH)..(endPoint.first.address - MIN_METER_LENGTH),
                )

            AlignmentAddresses(
                startPoint = startPoint.first,
                endPoint = endPoint.first,
                startIntersect = startPoint.second,
                endIntersect = endPoint.second,
                midPoints = midPoints,
            )
        } else null
    }

    fun getTrackLocation(alignment: IAlignment, address: TrackMeter): AddressPoint? {
        return getTrackLocations(alignment, listOf(address))[0]
    }

    fun getTrackLocations(alignment: IAlignment, addresses: List<TrackMeter>): List<AddressPoint?> {
        val alignmentStart = alignment.start
        val alignmentEnd = alignment.end
        val startAddress = alignmentStart?.let(::getAddress)?.first
        val endAddress = alignmentEnd?.let(::getAddress)?.first
        return addresses.map { address ->
            if (startAddress == null || endAddress == null || address !in startAddress..endAddress) {
                null
            } else if (startAddress.isSame(address)) {
                AddressPoint(alignmentStart, startAddress)
            } else if (endAddress.isSame(address)) {
                AddressPoint(alignmentEnd, endAddress)
            } else
                getProjectionLine(address)?.let { projectionLine ->
                    getProjectedAddressPoint(projectionLine, alignment)
                }
        }
    }

    fun getStartAndEnd(alignment: IAlignment): Pair<AddressPoint?, AddressPoint?> {
        val start = alignment.start?.let(::toAddressPoint)?.first
        val end = alignment.end?.let(::toAddressPoint)?.first
        return start to end
    }

    private fun getMidPoints(alignment: IAlignment, range: ClosedRange<TrackMeter>): List<AddressPoint> {
        val projectionLines = getSublistForRangeInOrderedList(projectionLines, range) { p, e -> p.address.compareTo(e) }
        return getProjectedAddressPoints(projectionLines, alignment)
    }

    fun getSwitchPoints(alignment: LayoutAlignment): List<AddressPoint> {
        val locations =
            alignment.segments.flatMapIndexed { index, segment ->
                val startM = alignment.segmentMs[index].min
                listOfNotNull(
                    segment.startJointNumber?.let { segment.segmentStart.toAlignmentPoint(startM) },
                    segment.endJointNumber?.let { segment.segmentEnd.toAlignmentPoint(startM) },
                )
            }
        return locations
            .mapNotNull { location: AlignmentPoint ->
                getAddress(location, 3)?.let { (address) -> AddressPoint(location, address) }
            }
            .distinctBy { addressPoint -> addressPoint.address }
    }

    private fun findPreviousPoint(targetDistance: Double): GeocodingReferencePoint {
        val target = roundTo3Decimals(targetDistance) // Round to 1mm to work around small imprecision
        if (target < BigDecimal.ZERO) throw GeocodingFailureException("Cannot geocode with negative distance")
        return referencePoints.findLast { referencePoint -> referencePoint.distanceRounded <= target }
            ?: throw GeocodingFailureException("Target point is not withing the reference line length")
    }

    fun cutRangeByKms(range: ClosedRange<TrackMeter>, kms: Set<KmNumber>): List<ClosedRange<TrackMeter>> {
        if (projectionLines.isEmpty()) return listOf()
        val addressRanges = getKmRanges(kms).mapNotNull(::toAddressRange)
        return splitRange(range, addressRanges)
    }

    private fun getKmRanges(kms: Set<KmNumber>): List<ClosedRange<KmNumber>> {
        val ranges: MutableList<ClosedRange<KmNumber>> = mutableListOf()
        var currentRange: ClosedRange<KmNumber>? = null
        allKms.forEach { kmNumber ->
            currentRange =
                if (kms.contains(kmNumber)) {
                    currentRange?.let { c -> c.start..kmNumber } ?: kmNumber..kmNumber
                } else {
                    currentRange?.let(ranges::add)
                    null
                }
        }
        currentRange?.let(ranges::add)
        return ranges
    }

    /**
     * Returns the inclusive range of addresses in the given range of km-numbers. Note: Since this is inclusive, it does
     * not include decimal meters after the last even meter, even though such addresses can be calculated
     */
    private fun toAddressRange(kmRange: ClosedRange<KmNumber>): ClosedRange<TrackMeter>? {
        val startAddress = projectionLines.find { l -> l.address.kmNumber == kmRange.start }?.address
        val endAddress = projectionLines.findLast { l -> l.address.kmNumber == kmRange.endInclusive }?.address
        return if (startAddress != null && endAddress != null) startAddress..endAddress else null
    }
}

fun splitRange(range: ClosedRange<TrackMeter>, splits: List<ClosedRange<TrackMeter>>): List<ClosedRange<TrackMeter>> =
    splits.mapNotNull { allowedRange ->
        if (range.start >= allowedRange.endInclusive || range.endInclusive <= allowedRange.start) null
        else maxOf(range.start, allowedRange.start)..minOf(range.endInclusive, allowedRange.endInclusive)
    }

fun <T, R : Comparable<R>> getIndexRangeForRangeInOrderedList(
    things: List<T>,
    rangeStart: R,
    rangeEnd: R,
    compare: (thing: T, rangeEnd: R) -> Int,
): IntRange? {
    if (rangeEnd < rangeStart) {
        return null
    }
    val startInsertionPoint = things.binarySearch { t -> compare(t, rangeStart) }
    val endInsertionPoint = things.binarySearch { t -> compare(t, rangeEnd) }
    val start = if (startInsertionPoint < 0) -startInsertionPoint - 1 else startInsertionPoint
    val end = if (endInsertionPoint < 0) -endInsertionPoint - 2 else endInsertionPoint
    return start..end
}

fun <T, R : Comparable<R>> getSublistForRangeInOrderedList(
    things: List<T>,
    range: ClosedRange<R>,
    compare: (thing: T, rangeEnd: R) -> Int,
): List<T> =
    getIndexRangeForRangeInOrderedList(things, range.start, range.endInclusive, compare)?.let { indexRange ->
        things.subList(indexRange.first, indexRange.last + 1)
    } ?: listOf()

fun getProjectedAddressPoint(projection: ProjectionLine, alignment: IAlignment): AddressPoint? {
    return getCollisionSegmentIndex(projection.projection, alignment)?.let { index ->
        val segment = alignment.segments[index]
        val m = alignment.segmentMs[index].min
        val segmentEdges = getPolyLineEdges(segment, m, null, null)
        val edgeAndPortion = getIntersection(projection.projection, segmentEdges)
        return edgeAndPortion?.let { (edge, portion) ->
            AddressPoint(point = edge.interpolateAlignmentPointAtPortion(portion), address = projection.address)
        }
    }
}

fun getProjectedAddressPoints(projectionLines: List<ProjectionLine>, alignment: IAlignment): List<AddressPoint> {
    val alignmentEdges = getPolyLineEdges(alignment)
    var edgeIndex = 0
    var projectionIndex = 0
    val addressPoints: MutableList<AddressPoint> = mutableListOf()

    while (edgeIndex <= alignmentEdges.lastIndex && projectionIndex <= projectionLines.lastIndex) {
        val edge = alignmentEdges[edgeIndex]
        val projection = projectionLines[projectionIndex]
        // Check if the edge goes in the same direction as the reference line at projection point
        // This affects how we should hande BEFORE/AFTER cases (missed intersections)
        val isEdgeAligned = angleDiffRads(edge.referenceDirection, projection.referenceDirection) <= PI / 2
        val intersection = intersection(edge, projection.projection)
        when (intersection.inSegment1) {
            BEFORE -> {
                // If the we're going the correct way, a projection hitting behind the current edge is an invalid
                // address for the track -> move on to the next one
                if (isEdgeAligned) projectionIndex += 1
                // If the edge is reversed, a "BEFORE" actually means we need to move on to the next edge
                else edgeIndex += 1
            }

            WITHIN -> {
                addressPoints.add(
                    AddressPoint(
                        edge.interpolateAlignmentPointAtPortion(intersection.segment1Portion),
                        projection.address,
                    )
                )
                projectionIndex += 1
            }

            AFTER -> {
                // If going the correct way, the projection intersection is after the current edge -> move on
                if (isEdgeAligned) edgeIndex += 1
                // Otherwise, "AFTER" is actually before the current point, so the address is invalid for the track ->
                // move on to the next projection
                else projectionIndex += 1
            }
        }
    }
    return addressPoints
}

private fun createProjectionLines(
    addressPoints: List<GeocodingReferencePoint>,
    edges: List<PolyLineEdge>,
): List<ProjectionLine> {
    val endDistance = edges.lastOrNull()?.endM ?: 0.0
    return addressPoints.flatMapIndexed { index: Int, point: GeocodingReferencePoint ->
        val minMeter = point.meters.setScale(0, RoundingMode.CEILING).toInt()
        val maxDistance = (addressPoints.getOrNull(index + 1)?.distance?.minus(MIN_METER_LENGTH) ?: endDistance)
        val maxMeter = (point.meters.toDouble() + maxDistance - point.distance).toInt()

        // If the km posts or reference line are sufficiently broken to cause invalid track meters
        // somewhere, it's probably bad enough that looking up track addresses is not useful, so we
        // just bail out here.
        if (!TrackMeter.isMetersValid(minMeter.toBigDecimal()) || !TrackMeter.isMetersValid(maxMeter.toBigDecimal())) {
            return listOf()
        }

        (minMeter..maxMeter step 1).map { meter ->
            val distance = point.distance + (meter.toDouble() - point.meters.toDouble())
            val edge =
                findEdge(distance, edges)
                    ?: throw GeocodingFailureException(
                        "Could not produce projection: " +
                            "km=${point.kmNumber} m=$meter distance=$distance " +
                            "endDistance=$endDistance refPointDistance=${point.distance} " +
                            "minMeter=$minMeter maxMeter=$maxMeter maxDistance=$maxDistance" +
                            "edges=${edges.filter { e -> e.startM in distance - 10.0..distance + 10.0 }}"
                    )

            val address = TrackMeter(point.kmNumber, meter)
            ProjectionLine(address, edge.crossSectionAt(distance), distance, edge.referenceDirection)
        }
    }
}

private fun validateProjectionLines(
    lines: List<ProjectionLine>,
    distanceDelta: Double,
    angleDelta: Double,
): List<ProjectionLine> {
    val distanceRange = (1.0 - distanceDelta)..(1.0 + distanceDelta)
    return lines.filterIndexed { index, line ->
        val previous = lines.getOrNull(index - 1)
        val distanceApprox =
            previous?.let { prev ->
                if (prev.address.kmNumber == line.address.kmNumber) {
                    lineLength(prev.projection.start, line.projection.start)
                } else null
            }
        val angleDiff = previous?.let { prev -> angleDiffRads(prev.projection.angle, line.projection.angle) }
        if (distanceApprox != null && distanceApprox !in distanceRange) {
            logger.warn(
                "Projection lines at un-even intervals: " +
                    "index=$index distance=$distanceApprox angleDiff=$angleDiff previous=$previous next=$line"
            )
        }
        if (angleDiff != null && angleDiff > angleDelta) {
            logger.error(
                "Projection lines turn unexpectedly (filtering out projection): " +
                    "index=$index distance=$distanceApprox angleDiff=$angleDiff previous=$previous next=$line"
            )
        }
        (angleDiff == null || angleDiff <= angleDelta)
    }
}

private fun getCollisionSegmentIndex(projection: Line, alignment: IAlignment): Int? {
    return alignment.segments
        .mapIndexedNotNull { index, s ->
            val intersection = lineIntersection(s.segmentStart, s.segmentEnd, projection.start, projection.end)
            if (intersection?.inSegment1 == WITHIN) intersection.relativeDistance2 to index else null
        }
        .minByOrNull { (distance, _) -> distance }
        ?.second
}

fun getIntersection(projection: Line, edges: List<PolyLineEdge>): Pair<PolyLineEdge, Double>? {
    var intersection: Intersection? = null
    val collisionEdge =
        edges.getOrNull(
            edges.binarySearch { edge ->
                val edgeIntersection = intersection(edge, projection)
                when (edgeIntersection.inSegment1) {
                    BEFORE -> 1
                    AFTER -> -1
                    else -> {
                        intersection = edgeIntersection
                        0
                    }
                }
            }
        )
    return if (collisionEdge != null) {
        intersection?.let { i -> collisionEdge to i.segment1Portion }
    } else {
        null
    }
}

private fun getPolyLineEdges(alignment: IAlignment): List<PolyLineEdge> {
    return alignment.segmentsWithM
        .flatMapIndexed { index, (segment, m) ->
            getPolyLineEdges(
                segment,
                m.min,
                alignment.segments.getOrNull(index - 1)?.endDirection,
                alignment.segments.getOrNull(index + 1)?.startDirection,
            )
        }
        .also { edges ->
            edges.forEachIndexed { index, edge ->
                val prev = edges.getOrNull(index - 1)
                if (prev != null) require(prev.endM == edge.startM) { "Edges not continuous: edges=$edges" }
            }
        }
}

private fun getPolyLineEdges(
    segment: ISegment,
    startM: Double,
    prevDir: Double?,
    nextDir: Double?,
): List<PolyLineEdge> {
    return segment.segmentPoints.mapIndexedNotNull { pointIndex: Int, point: SegmentPoint ->
        if (pointIndex == 0) null
        else {
            val previous = segment.segmentPoints[pointIndex - 1]
            // Edge direction
            val pointDirection =
                if (segment.source != GeometrySource.GENERATED) {
                    directionBetweenPoints(previous, point)
                } else if (prevDir == null || nextDir == null) {
                    // Generated connection segments can have a sideways offset, but the real line
                    // doesn't change direction. To compensate, we want to project with the direction
                    // of previous/next segments
                    prevDir ?: nextDir ?: directionBetweenPoints(previous, point)
                } else {
                    angleAvgRads(prevDir, nextDir)
                }
            PolyLineEdge(start = previous, end = point, segmentStart = startM, referenceDirection = pointDirection)
        }
    }
}

/**
 * Since segment start distance and m-values are stored with a limited precision, allow finding a segment with that much
 * delta-value. Otherwise, it's possible to get a distance-value "between segments"
 */
private fun findEdge(distance: Double, all: List<PolyLineEdge>, delta: Double = 0.000001): PolyLineEdge? =
    all.getOrNull(
        all.binarySearch { edge ->
            if (edge.startM > distance + delta) 1 else if (edge.endM < distance - delta) -1 else 0
        }
    )

private fun intersection(edge: PolyLineEdge, projection: Line) =
    lineIntersection(edge.start, edge.end, projection.start, projection.end)
        ?: throw GeocodingFailureException(
            "Projection line parallel to segment: edge=${edge.start}-${edge.end} projection=${projection.start}-${projection.end}"
        )

const val PROJECTION_LINE_LENGTH = 100.0

data class PolyLineEdge(
    val start: SegmentPoint,
    val end: SegmentPoint,
    val segmentStart: Double,
    val referenceDirection: Double,
) {
    // Direction for projection lines from the edge: 90 degrees turned from edge direction
    val projectionDirection by lazy { PI / 2 + referenceDirection }

    val startM: Double
        get() = start.m + segmentStart

    val endM: Double
        get() = end.m + segmentStart

    val length: Double
        get() = end.m - start.m

    fun crossSectionAt(distance: Double) =
        interpolatePointAtM(distance).let { point ->
            Line(point, pointInDirection(point, distance = PROJECTION_LINE_LENGTH, direction = projectionDirection))
        }

    private fun interpolatePointAtM(m: Double): IPoint =
        if (m <= startM) start else if (m >= endM) end else interpolate(start, end, (m - startM) / length)

    fun interpolateAlignmentPointAtPortion(portion: Double): AlignmentPoint =
        interpolateSegmentPointAtPortion(portion).toAlignmentPoint(segmentStart)

    fun interpolateSegmentPointAtPortion(portion: Double): SegmentPoint =
        if (portion <= 0.0) start else if (portion >= 1.0) end else interpolate(start, end, portion)
}
