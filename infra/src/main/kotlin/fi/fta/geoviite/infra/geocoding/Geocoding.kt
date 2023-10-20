package fi.fta.geoviite.infra.geocoding

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DEFAULT_TRACK_METER_DECIMALS
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.GeocodingFailureException
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.math.IntersectType.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI
import kotlin.math.abs

data class AddressPoint(val point: LayoutPoint, val address: TrackMeter) {
    fun isSame(other: AddressPoint) = address.isSame(other.address) && point.isSame(other.point)
}

data class AlignmentAddresses(
    val startPoint: AddressPoint,
    val endPoint: AddressPoint,
    val startIntersect: IntersectType,
    val endIntersect: IntersectType,
    val midPoints: List<AddressPoint>,
) {
    @get:JsonIgnore
    val allPoints: List<AddressPoint> by lazy {
        emptyList<AddressPoint>() + startPoint + midPoints + endPoint
    }
}

data class AlignmentStartAndEnd(
    val start: AddressPoint?,
    val end: AddressPoint?,
)

data class ProjectionLine(val address: TrackMeter, val projection: Line, val distance: Double)

data class GeocodingReferencePoint(
    val kmNumber: KmNumber,
    val meters: BigDecimal,
    val distance: Double,
    val kmPostOffset: Double,
    val intersectType: IntersectType,
)

data class AddressAndM(val address: TrackMeter, val m: Double, val intersectType: IntersectType)

/**
 * Don't generate a meter that is shorter than this.
 * Prevents an extra projection being generated when the KM changes on an exact meter.
 */
private const val MIN_METER_LENGTH = 0.001

/**
 * Projection line validation parameter.
 * They should be 1m apart from each other along reference line by definition.
 * This is the maximum deviation allowed - must accommodate some difference for turns.
 */
private const val PROJECTION_LINE_DISTANCE_DEVIATION = 0.05

/**
 * Projection line validation parameter.
 * If the line makes rough turns, the projections will result in convoluted addresses.
 * This is the maximum angle-change between 2 projection points (1m on reference line).
 */
private const val PROJECTION_LINE_MAX_ANGLE_DELTA = PI / 16


private val logger: Logger = LoggerFactory.getLogger(GeocodingContext::class.java)

data class KmPostWithRejectedReason(val kmPost: TrackLayoutKmPost, val rejectedReason: KmPostRejectedReason)

data class GeocodingContextCreateResult(
    val geocodingContext: GeocodingContext,
    val rejectedKmPosts: List<KmPostWithRejectedReason>,
)

enum class KmPostRejectedReason {
    TOO_FAR_APART,
    NO_LOCATION,
    IS_BEFORE_START_ADDRESS,
    INTERSECTS_BEFORE_REFERENCE_LINE,
    INTERSECTS_AFTER_REFERENCE_LINE,
}

data class GeocodingContext(
    val trackNumber: TrackLayoutTrackNumber,
    val startAddress: TrackMeter,
    val referenceLineGeometry: IAlignment,
    val kmPosts: List<TrackLayoutKmPost>,
    val referencePoints: List<GeocodingReferencePoint>,
    val projectionLineDistanceDeviation: Double = PROJECTION_LINE_DISTANCE_DEVIATION,
    val projectionLineMaxAngleDelta: Double = PROJECTION_LINE_MAX_ANGLE_DELTA,
) {

    init {
        require(referenceLineGeometry.segments.isNotEmpty()) {
            "Cannot geocode with empty reference line geometry, trackNumber=${trackNumber.number}"
        }

        require(referencePoints.isNotEmpty()) {
            "Cannot geocode without reference points, trackNumber=${trackNumber.number}"
        }

        require(
            referencePoints
                .zipWithNext { a, b -> abs(b.distance - a.distance) }
                .all { TrackMeter.isMetersValid(it) }
        ) {
            "Reference points are too far apart from each other, trackNumber=${trackNumber.number}"
        }
    }

    private val polyLineEdges: List<PolyLineEdge> by lazy { getPolyLineEdges(referenceLineGeometry) }
    val projectionLines: List<ProjectionLine> by lazy {
        require(isSame(polyLineEdges.last().end.m, referenceLineGeometry.length, LAYOUT_M_DELTA)) {
            "Polyline edges should cover the whole reference line geometry: " +
                    "trackNumber=${trackNumber.number} " +
                    "alignment=${referenceLineGeometry.id} " +
                    "edgeMValues=${polyLineEdges.map { e -> e.start.m..e.end.m }}"
        }
        createProjectionLines(referencePoints, polyLineEdges).also { lines ->
            validateProjectionLines(lines, projectionLineDistanceDeviation, projectionLineMaxAngleDelta)
        }
    }
    val allKms: List<KmNumber> by lazy {
        referencePoints.map(GeocodingReferencePoint::kmNumber).distinct()
    }

    val startProjection: ProjectionLine by lazy {
        val address = TrackMeter(referencePoints.first().kmNumber, referencePoints.first().meters)
        val projectionLine = polyLineEdges.first().crossSectionAt(0.0)
        ProjectionLine(address, projectionLine, 0.0)
    }

    val endProjection: ProjectionLine by lazy {
        val meters = referenceLineGeometry.length - referencePoints.last().let { p -> p.distance - p.meters.toDouble() }
        val address = TrackMeter(referencePoints.last().kmNumber, meters, referencePoints.first().meters.scale())
        val projectionLine = polyLineEdges.last().crossSectionAt(referenceLineGeometry.length)
        ProjectionLine(address, projectionLine, referenceLineGeometry.length)
    }

    fun getProjectionLine(address: TrackMeter): ProjectionLine? =
        if (address.decimalCount() == 0 || address <= startProjection.address || address >= endProjection.address) {
            findCachedProjectionLine(address)
        } else findCachedProjectionLine(address.floor())?.let { previous ->
            val distance = previous.distance + (address.meters.toDouble() - previous.address.meters.toDouble())
            findEdge(distance, polyLineEdges)?.let { edge ->
                ProjectionLine(address, edge.crossSectionAt(distance), distance)
            }
        }

    private fun findCachedProjectionLine(address: TrackMeter) =
        if (address !in startProjection.address..endProjection.address) null
        else if (address == startProjection.address) startProjection
        else if (address == endProjection.address) endProjection
        else if (projectionLines.isEmpty()) null
        else projectionLines.binarySearch { line -> line.address.compareTo(address) }.let(projectionLines::getOrNull)

    companion object {
        fun create(
            trackNumber: TrackLayoutTrackNumber,
            startAddress: TrackMeter,
            referenceLineGeometry: IAlignment,
            kmPosts: List<TrackLayoutKmPost>,
        ): GeocodingContextCreateResult {
            val (validKmPosts, invalidKmPosts) = validateKmPosts(kmPosts, startAddress)

            val referencePoints = createReferencePoints(startAddress, validKmPosts, referenceLineGeometry)
            val (validReferencePoints, rejectedKmPosts) = validateReferencePoints(referencePoints, validKmPosts)

            return GeocodingContextCreateResult(
                geocodingContext = GeocodingContext(
                    trackNumber = trackNumber,
                    kmPosts = kmPosts,
                    referenceLineGeometry = referenceLineGeometry,
                    referencePoints = validReferencePoints,
                    startAddress = startAddress
                ),
                rejectedKmPosts = rejectedKmPosts + invalidKmPosts
            )
        }

        private fun createReferencePoints(
            startAddress: TrackMeter,
            kmPosts: List<TrackLayoutKmPost>,
            referenceLineGeometry: IAlignment,
        ): List<GeocodingReferencePoint> {
            val kpReferencePoints = kmPosts
                .mapNotNull { post ->
                    post.location?.let { location -> toReferencePoint(location, post.kmNumber, referenceLineGeometry) }
                }

            val firstPoint = GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN)

            return listOf(firstPoint) + kpReferencePoints
        }

        private fun validateKmPosts(
            kmPosts: List<TrackLayoutKmPost>,
            startAddress: TrackMeter,
        ): Pair<List<TrackLayoutKmPost>, List<KmPostWithRejectedReason>> {
            val (withoutLocations, withLocations) = kmPosts.partition { it.location == null }

            val (invalidStartAddresses, validKmPosts) = withLocations.partition {
                TrackMeter(it.kmNumber, BigDecimal.ZERO) <= startAddress
            }

            val rejectedKmPosts = withoutLocations.map { it to KmPostRejectedReason.NO_LOCATION } +
                    invalidStartAddresses.map { it to KmPostRejectedReason.IS_BEFORE_START_ADDRESS }

            return validKmPosts to rejectedKmPosts.map { (kp, reason) -> KmPostWithRejectedReason(kp, reason) }
        }

        private fun validateReferencePoints(
            referencePoints: List<GeocodingReferencePoint>,
            kmPosts: List<TrackLayoutKmPost>,
        ): Pair<List<GeocodingReferencePoint>, List<KmPostWithRejectedReason>> {
            val (withinPoints, beforePoints, afterPoints) = referencePoints
                .groupBy { it.intersectType }
                .let { byIntersect ->
                    Triple(
                        byIntersect[WITHIN] ?: emptyList(),
                        byIntersect[BEFORE]?.map { it to KmPostRejectedReason.INTERSECTS_BEFORE_REFERENCE_LINE }
                            ?: emptyList(),
                        byIntersect[AFTER]?.map { it to KmPostRejectedReason.INTERSECTS_AFTER_REFERENCE_LINE }
                            ?: emptyList()
                    )
                }

            val invalidIndex = withinPoints
                .zipWithNext { a, b -> abs(b.distance - a.distance) }
                .let { distances -> distances.indexOfFirst { !TrackMeter.isMetersValid(it) } }

            val (validPoints, invalidPoints) =
                if (invalidIndex == -1) withinPoints to emptyList()
                else {
                    val idx = invalidIndex + 1
                    withinPoints.take(idx) to listOf(withinPoints[idx] to KmPostRejectedReason.TOO_FAR_APART)
                }

            val rejectedKmPosts = (beforePoints + afterPoints + invalidPoints).map { (rp, reason) ->
                val kp = kmPosts.first { k -> k.kmNumber == rp.kmNumber }

                KmPostWithRejectedReason(kp, reason)
            }

            return validPoints.distinctBy { it.distance } to rejectedKmPosts
        }

        private fun toReferencePoint(location: IPoint, kmNumber: KmNumber, referenceLineGeometry: IAlignment) =
            referenceLineGeometry.getClosestPointM(location)?.let { (distance, intersectType) ->
                val pointOnLine = requireNotNull(referenceLineGeometry.getPointAtM(distance)) {
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
        referenceLineGeometry.getClosestPointM(coordinate)?.let { (dist, type) ->
            AddressAndM(getAddress(dist, addressDecimals), dist, type)
        }

    fun getAddress(coordinate: IPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): Pair<TrackMeter, IntersectType>? =
        getAddressAndM(coordinate, decimals)?.let { (address, _, type) -> address to type }

    fun getAddress(targetDistance: Double, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): TrackMeter {
        val addressPoint = findPreviousPoint(targetDistance)
        val meters = addressPoint.meters.toDouble() + targetDistance - addressPoint.distance
        return TrackMeter(addressPoint.kmNumber, round(meters, decimals))
    }

    val referenceLineAddresses by lazy {
        getAddressPoints(referenceLineGeometry)
            ?: throw IllegalStateException("Can't resolve reference line addresses")
    }

    private fun toAddressPoint(point: LayoutPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS) =
        getAddress(point, decimals)?.let { (address, intersectType) ->
            AddressPoint(point, address) to intersectType
        }

    fun getAddressPoints(alignment: IAlignment): AlignmentAddresses? {
        val startPoint = alignment.start?.let(::toAddressPoint)
        val endPoint = alignment.end?.let(::toAddressPoint)
        return if (startPoint != null && endPoint != null) {
            val midPoints = getMidPoints(
                alignment,
                (startPoint.first.address + MIN_METER_LENGTH)..(endPoint.first.address - MIN_METER_LENGTH)
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
        val alignmentStart = alignment.start
        val alignmentEnd = alignment.end
        val startAddress = alignmentStart?.let(::getAddress)?.first
        val endAddress = alignmentEnd?.let(::getAddress)?.first
        return if (startAddress == null || endAddress == null || address !in startAddress..endAddress) {
            null
        } else if (startAddress.isSame(address)) {
            AddressPoint(alignmentStart, startAddress)
        } else if (endAddress.isSame(address)) {
            AddressPoint(alignmentEnd, endAddress)
        } else getProjectionLine(address)?.let { projectionLine ->
            getProjectedAddressPoint(projectionLine, alignment)
        }
    }

    fun getStartAndEnd(alignment: IAlignment): AlignmentStartAndEnd {
        val startAddress = alignment.start?.let(::toAddressPoint)
        val endAddress = alignment.end?.let(::toAddressPoint)
        return AlignmentStartAndEnd(startAddress?.first, endAddress?.first)
    }

    private fun getMidPoints(alignment: IAlignment, range: ClosedRange<TrackMeter>): List<AddressPoint> {
        val projectionLines = getSublistForRangeInOrderedList(projectionLines, range) { p, e -> p.address.compareTo(e) }
        return getProjectedAddressPoints(projectionLines, alignment)
    }

    fun getSwitchPoints(alignment: LayoutAlignment): List<AddressPoint> {
        val locations = alignment.segments.flatMap { segment ->
            listOfNotNull(
                segment.startJointNumber?.let { segment.points.first() },
                segment.endJointNumber?.let { segment.points.last() },
            )
        }
        return locations.mapNotNull { location: LayoutPoint ->
            getAddress(location, 3)?.let { (address) ->
                AddressPoint(
                    point = location,
                    address = address,
                )
            }
        }.distinctBy { addressPoint -> addressPoint.address }
    }

    private fun findPreviousPoint(targetDistance: Double): GeocodingReferencePoint {
        val target = roundTo3Decimals(targetDistance) // Round to 1mm to work around small imprecision
        if (target < BigDecimal.ZERO) throw GeocodingFailureException("Cannot geocode with negative distance")
        return referencePoints.findLast { (_, _, distance: Double) -> roundTo3Decimals(distance) <= target }
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
            currentRange = if (kms.contains(kmNumber)) {
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
     * Returns the inclusive range of addresses in the given range of km-numbers.
     * Note: Since this is inclusive, it does not include decimal meters after the last even meter,
     * even though such addresses can be calculated
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

fun <T, R : Comparable<R>> getSublistForRangeInOrderedList(
    things: List<T>,
    range: ClosedRange<R>,
    compare: (thing: T, rangeEnd: R) -> Int
): List<T> {
    if (range.isEmpty()) {
        return listOf()
    }
    val start = things.binarySearch { t -> compare(t, range.start) }
    val end = things.binarySearch { t -> compare(t, range.endInclusive) }
    return things.subList(
        if (start < 0) -start - 1 else start,
        if (end < 0) -end - 1 else end + 1
    )
}

fun getProjectedAddressPoint(
    projection: ProjectionLine,
    alignment: IAlignment,
): AddressPoint? {
    val segment = getCollisionSegment(projection.projection, alignment)
    val segmentEdges = segment?.let { s -> getPolyLineEdges(s, null, null) }
    val edgeAndPortion = segmentEdges?.let { edges -> getIntersection(projection.projection, edges) }
    return edgeAndPortion?.let { (edge, portion) ->
        AddressPoint(
            point = edge.getPointAtPortion(portion),
            address = projection.address,
        )
    }
}

fun getProjectedAddressPoints(
    projectionLines: List<ProjectionLine>,
    alignment: IAlignment,
): List<AddressPoint> {

    val alignmentEdges = getPolyLineEdges(alignment)
    var edgeIndex = 0
    var projectionIndex = 0
    val addressPoints: MutableList<AddressPoint> = mutableListOf()

    while (edgeIndex <= alignmentEdges.lastIndex && projectionIndex <= projectionLines.lastIndex) {
        val edge = alignmentEdges[edgeIndex]
        val projection = projectionLines[projectionIndex]
        val intersection = intersection(edge, projection.projection)
        when (intersection.inSegment1) {
            BEFORE -> {
                projectionIndex += 1
            }

            WITHIN -> {
                addressPoints.add(
                    AddressPoint(edge.getPointAtPortion(intersection.segment1Portion), projection.address),
                )
                projectionIndex += 1
            }

            AFTER -> {
                edgeIndex += 1
            }
        }
    }
    return addressPoints
}

private fun createProjectionLines(
    addressPoints: List<GeocodingReferencePoint>,
    edges: List<PolyLineEdge>,
): List<ProjectionLine> {
    val endDistance = edges.lastOrNull()?.end?.m ?: 0.0
    return addressPoints.flatMapIndexed { index: Int, point: GeocodingReferencePoint ->
        val minMeter = point.meters.setScale(0, RoundingMode.CEILING).toInt()
        val maxDistance = (addressPoints.getOrNull(index + 1)?.distance?.minus(MIN_METER_LENGTH) ?: endDistance)
        val maxMeter = (point.meters.toDouble() + maxDistance - point.distance).toInt()

        (minMeter..maxMeter step 1).map { meter ->
            val distance = point.distance + (meter.toDouble() - point.meters.toDouble())
            val edge = findEdge(distance, edges) ?: throw GeocodingFailureException(
                "Could not produce projection: " +
                        "km=${point.kmNumber} m=$meter distance=$distance " +
                        "endDistance=$endDistance refPointDistance=${point.distance} " +
                        "minMeter=$minMeter maxMeter=$maxMeter maxDistance=$maxDistance" +
                        "edges=${edges.filter { e -> e.start.m in distance - 10.0..distance + 10.0 }}"
            )
            ProjectionLine(TrackMeter(point.kmNumber, meter), edge.crossSectionAt(distance), distance)
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
        val distanceApprox = previous?.let { prev ->
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

private fun getCollisionSegment(projection: Line, alignment: IAlignment): ISegment? {
    return alignment.segments
        .mapNotNull { s ->
            val intersection = lineIntersection(s.points.first(), s.points.last(), projection.start, projection.end)
            if (intersection?.inSegment1 == WITHIN) intersection.relativeDistance2 to s else null
        }
        .minByOrNull { (distance, _) -> distance }?.second
}

fun getIntersection(projection: Line, edges: List<PolyLineEdge>): Pair<PolyLineEdge, Double>? {
    var intersection: Intersection? = null
    val collisionEdge = edges.getOrNull(edges.binarySearch { edge ->
        val edgeIntersection = intersection(edge, projection)
        when (edgeIntersection.inSegment1) {
            BEFORE -> 1
            AFTER -> -1
            else -> {
                intersection = edgeIntersection
                0
            }
        }
    })
    return if (collisionEdge != null) {
        intersection?.let { i -> collisionEdge to i.segment1Portion }
    } else {
        null
    }
}

private fun getPolyLineEdges(alignment: IAlignment): List<PolyLineEdge> {
    return alignment.segments.flatMapIndexed { index, segment ->
        getPolyLineEdges(
            segment,
            alignment.segments.getOrNull(index - 1)?.endDirection,
            alignment.segments.getOrNull(index + 1)?.startDirection,
        )
    }
}

private fun getPolyLineEdges(segment: ISegment, prevDir: Double?, nextDir: Double?): List<PolyLineEdge> {
    return segment.points.mapIndexedNotNull { pointIndex: Int, point: LayoutPoint ->
        if (pointIndex == 0) null
        else {
            val previous = segment.points[pointIndex - 1]
            // Direction for projection lines from the edge: 90 degrees turned from own direction
            val direction = PI / 2 +
                    if (segment.source != GeometrySource.GENERATED) directionBetweenPoints(previous, point)
                    // Generated connection segments can have a sideways offset, but the real line doesn't change direction
                    // To compensate, we want to project with the direction of previous/next segments
                    else if (prevDir == null || nextDir == null) prevDir ?: nextDir ?: directionBetweenPoints(
                        previous,
                        point
                    )
                    else angleAvgRads(prevDir, nextDir)
            PolyLineEdge(previous, point, segment.startM, direction)
        }
    }
}

/**
 * Since segment start distance and m-values are stored with a limited precision, allow finding
 * a segment with that much delta-value. Otherwise, it's possible to get a distance-value "between segments"
 */
private fun findEdge(distance: Double, all: List<PolyLineEdge>, delta: Double = 0.000001): PolyLineEdge? =
    all.getOrNull(all.binarySearch { edge ->
        if (edge.start.m > distance + delta) 1
        else if (edge.end.m < distance - delta) -1
        else 0
    })

private fun intersection(edge: PolyLineEdge, projection: Line) =
    lineIntersection(edge.start, edge.end, projection.start, projection.end)
        ?: throw GeocodingFailureException(
            "Projection line parallel to segment: " +
                    "edge=${edge.start}-${edge.end} projection=${projection.start} ${projection.end}"
        )

const val PROJECTION_LINE_LENGTH = 100.0

data class PolyLineEdge(
    val start: LayoutPoint,
    val end: LayoutPoint,
    val segmentStart: Double,
    val projectionDirection: Double,
) {
    val length: Double = end.m - start.m

    fun crossSectionAt(distance: Double) = pointAt(distance).let { point ->
        Line(point, pointInDirection(point, distance = PROJECTION_LINE_LENGTH, direction = projectionDirection))
    }

    private fun pointAt(distance: Double): IPoint =
        if (distance <= start.m) start
        else if (distance >= end.m) end
        else interpolate(start, end, (distance - start.m) / length)

    fun getPointAtPortion(portion: Double) =
        if (portion <= 0.0) start
        else if (portion >= 1.0) end
        else interpolate(start, end, portion)

    fun getDistanceToPortion(portion: Double): Double = start.m + length * portion
}
