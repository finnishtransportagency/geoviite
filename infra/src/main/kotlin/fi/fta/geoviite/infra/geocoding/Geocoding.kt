package fi.fta.geoviite.infra.geocoding

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DEFAULT_TRACK_METER_DECIMALS
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.GeocodingFailureException
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.math.IntersectType.WITHIN
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.PI

data class AddressPoint(val point: LayoutPoint, val address: TrackMeter, val distance: Double) {
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

data class ProjectionLine(val address: TrackMeter, val projection: Line)

data class GeocodingReferencePoint(
    val kmNumber: KmNumber,
    val meters: BigDecimal,
    val distance: Double,
    val kmPostOffset: Double,
    val intersectType: IntersectType,
)

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

data class GeocodingContext(
    val trackNumber: TrackLayoutTrackNumber,
    val referenceLine: ReferenceLine,
    val referenceLineGeometry: IAlignment,
    val referencePoints: List<GeocodingReferencePoint>,
    val rejectedKmPosts: List<TrackLayoutKmPost> = listOf(),
    val projectionLineDistanceDeviation: Double = PROJECTION_LINE_DISTANCE_DEVIATION,
    val projectionLineMaxAngleDelta: Double = PROJECTION_LINE_MAX_ANGLE_DELTA,
) {
    val projectionLines: List<ProjectionLine> by lazy {
        val edges = getPolyLineEdges(referenceLineGeometry)
        require(isSame(edges.last().endDistance, referenceLineGeometry.length, LAYOUT_M_DELTA)) {
            "Polyline edges should cover the whole reference line geometry: " +
                    "trackNumber=${trackNumber.number} " +
                    "referenceLine=${referenceLine.id} " +
                    "alignment=${referenceLineGeometry.id}"
        }
        createProjectionLines(referencePoints, edges).also { lines ->
            validateProjectionLines(lines, projectionLineDistanceDeviation, projectionLineMaxAngleDelta)
        }
    }
    val allKms: List<KmNumber> by lazy {
        referencePoints.map(GeocodingReferencePoint::kmNumber).distinct()
    }

    fun getProjectionLine(address: TrackMeter): ProjectionLine? =
        projectionLines.getOrNull(projectionLines.binarySearch { projectionLine ->
            compareValuesBy(
                projectionLine.address, address,
                { a: TrackMeter -> a.kmNumber },
                { a: TrackMeter -> a.meters.toInt() }) // Drop fractions, as we only have even meters cached
        })

    companion object {
        fun create(
            trackNumber: TrackLayoutTrackNumber,
            referenceLine: ReferenceLine,
            referenceLineGeometry: LayoutAlignment,
            kmPosts: List<TrackLayoutKmPost>,
        ): GeocodingContext {
            val referencePoints = createReferencePoints(referenceLine.startAddress, kmPosts, referenceLineGeometry)
            return GeocodingContext(
                trackNumber = trackNumber,
                referenceLine = referenceLine,
                referenceLineGeometry = referenceLineGeometry,
                referencePoints = referencePoints,
                rejectedKmPosts = kmPosts.filterNot { post ->
                    referencePoints.any { reference ->
                        reference.kmNumber == post.kmNumber && reference.meters == BigDecimal.ZERO
                    }
                },
            )
        }

        private fun createReferencePoints(
            startAddress: TrackMeter,
            kmPosts: List<TrackLayoutKmPost>,
            referenceLineGeometry: IAlignment,
        ) = listOf(GeocodingReferencePoint(startAddress.kmNumber, startAddress.meters, 0.0, 0.0, WITHIN)) +
                kmPosts.mapNotNull { post ->
                    if (post.location != null && TrackMeter(post.kmNumber,0) > startAddress) {
                        toReferencePoint(post.location, post.kmNumber, referenceLineGeometry)
                    } else null
                }

        private fun toReferencePoint(location: IPoint, kmNumber: KmNumber, referenceLineGeometry: IAlignment) =
            referenceLineGeometry.getLengthUntil(location)
                ?.let { (distance, intersectType) ->
                    if (distance > 0.0 && intersectType == WITHIN) {
                        val pointOnLine = requireNotNull(referenceLineGeometry.getPointAtLength(distance)) {
                            "Couldn't resolve distance to point on reference line: not continuous?"
                        }
                        GeocodingReferencePoint(
                            kmNumber = kmNumber,
                            meters = BigDecimal.ZERO,
                            distance = distance,
                            kmPostOffset = lineLength(location, pointOnLine),
                            intersectType = intersectType,
                        )
                    } else null
                }
    }

    fun getDistance(coordinate: IPoint): Pair<Double, IntersectType>? =
        referenceLineGeometry.getLengthUntil(coordinate)

    fun getAddress(coordinate: IPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): Pair<TrackMeter, IntersectType>? =
        getDistance(coordinate)?.let { (dist, type) -> getAddress(dist, decimals) to type }

    fun getAddress(targetDistance: Double, decimals: Int = DEFAULT_TRACK_METER_DECIMALS): TrackMeter {
        val addressPoint = findPreviousPoint(targetDistance)
        val meters = addressPoint.meters.toDouble() + targetDistance - addressPoint.distance
        return TrackMeter(addressPoint.kmNumber, round(meters, decimals))
    }

    val referenceLineAddresses by lazy {
        getAddressPoints(referenceLineGeometry)
            ?: throw IllegalStateException("Can't resolve reference line addresses")
    }

    fun toAddressPoint(point: LayoutPoint, decimals: Int = DEFAULT_TRACK_METER_DECIMALS) =
        getDistance(point)?.let { (distance, intersectType) ->
            AddressPoint(point, getAddress(distance, decimals), distance) to intersectType
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

    fun getTrackLocation(alignment: LayoutAlignment, address: TrackMeter): AddressPoint? {
        val startAddress = alignment.start?.let(::getAddress)?.first
        val endAddress = alignment.end?.let(::getAddress)?.first
        return if (startAddress == null || endAddress == null || address !in startAddress..endAddress) {
            null
        } else getProjectionLine(address)?.let { projectionLine ->
            getProjectedAddressPoint(projectionLine, alignment)
        }
    }

    fun getStartAndEnd(alignment: LayoutAlignment): AlignmentStartAndEnd {
        val startAddress = alignment.start?.let(::toAddressPoint)
        val endAddress = alignment.end?.let(::toAddressPoint)
        return AlignmentStartAndEnd(startAddress?.first, endAddress?.first)
    }

    private fun getMidPoints(alignment: IAlignment, range: ClosedRange<TrackMeter>): List<AddressPoint> {
        val projectionLines = getSublistForRangeInOrderedList(projectionLines, range) { p, e -> p.address.compareTo(e) }
        return getProjectedAddressPoints(projectionLines, alignment)
    }

    fun getSwitchPoints(alignment: LayoutAlignment): List<AddressPoint> {
        val locations = alignment.segments.flatMap { segment -> listOfNotNull(
            segment.startJointNumber?.let { segment.points.first() },
            segment.endJointNumber?.let { segment.points.last() },
        ) }
        return locations.mapNotNull { location: LayoutPoint ->
            getDistance(location)?.first?.let { distance -> AddressPoint(
                point = location,
                address = getAddress(distance, 3),
                distance = distance,
            ) }
        }.distinctBy { addressPoint -> addressPoint.address }
    }

    private fun findPreviousPoint(targetDistance: Double): GeocodingReferencePoint {
        val target = roundTo3Decimals(targetDistance) // Round to 1mm to work around small imprecision
        if (target < BigDecimal.ZERO) throw GeocodingFailureException("Cannot reverse geocode with negative distance")
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
        if (start < 0) { -start-1 } else { start },
        if (end < 0) { -end-1 } else { end + 1 }
    )
}

fun getProjectedAddressPoint(
    projection: ProjectionLine,
    alignment: LayoutAlignment,
): AddressPoint? {
    val segment = getCollisionSegment(projection.projection, alignment)
    val segmentEdges = segment?.let { s -> getPolyLineEdges(s, null, null) }
    val edgeAndPortion = segmentEdges?.let { edges -> getIntersection(projection.projection, edges) }
    return edgeAndPortion?.let { (edge, portion) ->
        AddressPoint(
            point = edge.getPointAtPortion(portion),
            address = projection.address,
            distance = edge.getDistanceToPortion(portion),
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
            IntersectType.BEFORE -> {
                projectionIndex += 1
            }
            WITHIN -> {
                addressPoints.add(
                    AddressPoint(
                        point = edge.getPointAtPortion(intersection.segment1Portion),
                        address = projection.address,
                        distance = edge.getDistanceToPortion(intersection.segment1Portion),
                    )
                )
                projectionIndex += 1
            }
            IntersectType.AFTER -> {
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
    val endDistance = edges.lastOrNull()?.let(PolyLineEdge::endDistance) ?: 0.0
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
                        "edges=${edges.filter { e -> e.startDistance in distance - 10.0..distance + 10.0 }}"
            )
            ProjectionLine(TrackMeter(point.kmNumber, meter), edge.crossSectionAt(distance))
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

private fun getCollisionSegment(projection: Line, alignment: LayoutAlignment): LayoutSegment? {
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
            IntersectType.BEFORE -> 1
            IntersectType.AFTER -> -1
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
    return alignment.segments.flatMapIndexed { index: Int, segment: ISegment -> getPolyLineEdges(
        segment,
        alignment.segments.getOrNull(index-1)?.endDirection,
        alignment.segments.getOrNull(index+1)?.startDirection,
    ) }
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
                else if (prevDir == null || nextDir == null) prevDir ?: nextDir ?: directionBetweenPoints(previous, point)
                else angleAvgRads(prevDir, nextDir)
            PolyLineEdge(previous, point, segment.start, direction)
        }
    }
}

/**
 * Since segment start distance and m-values are stored with a limited precision, allow finding
 * a segment with that much delta-value. Otherwise, it's possible to get a distance-value "between segments"
 */
private fun findEdge(distance: Double, all: List<PolyLineEdge>, delta: Double = 0.000001): PolyLineEdge? =
    all.getOrNull(all.binarySearch { edge ->
        if (edge.startDistance > distance + delta) 1
        else if (edge.endDistance < distance - delta) -1
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
    val startDistance: Double = segmentStart + start.m
    val endDistance: Double = segmentStart + end.m

    fun crossSectionAt(distance: Double) = pointAt(distance).let { point ->
        Line(point, pointInDirection(point, distance = PROJECTION_LINE_LENGTH, direction = projectionDirection))
    }

    private fun pointAt(distance: Double): IPoint =
        if (distance <= startDistance) start
        else if (distance >= endDistance) end
        else interpolate(start, end, (distance - startDistance) / length)

    fun getPointAtPortion(portion: Double) =
        if (portion <= 0.0) start
        else if (portion >= 1.0) end
        else interpolate(start, end, portion)

    fun getDistanceToPortion(portion: Double): Double = startDistance + length * portion
}
