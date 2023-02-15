package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.math.IntersectType.*
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.util.FileName
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

enum class GeometrySource {
    IMPORTED,
    PLAN,
    GENERATED,
}

fun emptyAlignment() = LayoutAlignment(segments = listOf(), sourceId = null)

data class SegmentGeometryAndPlan(
    val planId: IntId<GeometryPlan>?,
    val planFileName: FileName?,
    val points: List<LayoutPoint>,
    val source: GeometrySource,
    val metadataFileName: FileName?
)

data class LayoutAlignment(
    val segments: List<LayoutSegment>,
    val sourceId: DomainId<GeometryAlignment>?,
    val id: DomainId<LayoutAlignment> = deriveFromSourceId("A", sourceId),
    val dataType: DataType = DataType.TEMP,
) {
    val length: Double = segments.lastOrNull()?.let { s -> s.start + s.length } ?: 0.0
    val boundingBox: BoundingBox? = boundingBoxCombining(segments.mapNotNull { s -> s.boundingBox })

    init {
        segments.forEachIndexed { index, segment ->
            if (index == 0) {
                require(segment.start == 0.0) {
                    "First segment should start at 0.0: alignment=$id firstStart=${segment.start}"
                }
            } else {
                val previous = segments[index - 1]
                require(previous.points.last().isSame(segment.points.first(), LAYOUT_COORDINATE_DELTA)) {
                    "Alignment segment doesn't start where the previous one ended: " +
                            "alignment=$id segment=$index " +
                            "length=${segment.length} prevLength=${previous.length} " +
                            "diff=${lineLength(previous.points.last(), segment.points.first())}"
                }
                require(isSame(previous.start + previous.length, segment.start, LAYOUT_M_DELTA)) {
                    "Alignment segment m-calculation should be continuous: " +
                            "alignment=$id segment=$index prevEnd=${previous.start + previous.length} start=${segment.start}"
                }
            }
        }
    }

    val start by lazy {
        segments.firstOrNull()?.points?.first()
    }

    val end by lazy {
        segments.lastOrNull()?.points?.last()
    }

    fun allPoints() = segments.flatMapIndexed { index, segment ->
        if (index == segments.lastIndex) segment.points
        else segment.points.take(segment.points.size - 1)
    }

    fun withSegments(newSegments: List<LayoutSegment>) = copy(segments = newSegments)

    fun getSegmentIndex(segmentId: DomainId<LayoutSegment>): Int? {
        val index = segments.indexOfFirst { segment -> segment.id == segmentId }
        return if (index != -1) index else null
    }

    fun getLengthUntil(target: IPoint): Pair<Double, IntersectType>? =
        findClosestSegmentIndex(target)?.let { segmentIndex ->
            val segment = segments[segmentIndex]
            val segmentM = if (segment.source == GENERATED) {
                val proportion = closestPointProportionOnGeneratedSegment(segmentIndex, target)
                val interpolatedM = proportion * segment.length
                if (interpolatedM < -1.0) 0.0 to BEFORE
                else if (interpolatedM > segment.length + 1.0) segment.length to AFTER
                else if (interpolatedM < 0.0) 0.0 to WITHIN
                else if (interpolatedM > segment.length) segment.length to WITHIN
                else interpolatedM to WITHIN
            } else {
                segment.getLengthUntil(target)
            }
            segmentM.let { (m, type) -> segment.start + m to type }
        }

    fun getPointAtLength(distance: Double, snapDistance: Double = 0.0): LayoutPoint? =
        if (distance <= 0.0) start
        else if (distance >= length) end
        else segments
            .findLast { segment -> segment.start <= distance }
            ?.let { segment -> segment.getPointAtLength(distance - segment.start, snapDistance) }

    fun findClosestSegmentIndex(target: IPoint): Int? {
        return approximateClosestSegmentIndex(target)?.let { approximation ->
            var index = approximation
            while (isAfterClosestPoint(index, target) && index > 0) index--
            while (isBeforeClosestPoint(index, target) && index < segments.lastIndex) index++
            index
        }
    }

    private fun isAfterClosestPoint(segmentIndex: Int, target: IPoint): Boolean {
        val segment = segments[segmentIndex]
        return if (segment.source == GENERATED) {
            closestPointProportionOnGeneratedSegment(segmentIndex, target) < 0.0
        } else {
            closestPointProportionOnLine(segment.points[0], segment.points[1], target) < 0.0
        }
    }

    private fun isBeforeClosestPoint(segmentIndex: Int, target: IPoint): Boolean {
        val segment = segments[segmentIndex]
        return if (segment.source == GENERATED) {
            closestPointProportionOnGeneratedSegment(segmentIndex, target) > 1.0
        } else {
            closestPointProportionOnLine(
                segment.points[segment.points.lastIndex - 1],
                segment.points[segment.points.lastIndex],
                target
            ) > 1.0
        }
    }

    // For generated segments, we use projections based on next/prev segment directions
    // Similarly, when finding the closest point on an alignment, we assume the segment going with that angle
    private fun closestPointProportionOnGeneratedSegment(segmentIndex: Int, target: IPoint): Double {
        val segment = segments[segmentIndex]
        val start = segment.points[0]
        val end = segment.points[segment.points.lastIndex]
        val prevDir = segments.getOrNull(segmentIndex - 1)?.endDirection()
        val nextDir = segments.getOrNull(segmentIndex + 1)?.startDirection()
        val fakeDir =
            if (prevDir != null && nextDir != null) angleAvgRads(prevDir, nextDir)
            else prevDir ?: nextDir

        return if (fakeDir != null) {
            val segmentDir = directionBetweenPoints(start, end)
            val fakeLineLength = segment.length * cos(angleDiffRads(segmentDir, fakeDir))
            val fakeEnd = pointInDirection(start, fakeLineLength, fakeDir)
            closestPointProportionOnLine(start, fakeEnd, target)
        } else {
            closestPointProportionOnLine(start, end, target)
        }
    }

    /**
     * Note: segment comparison is an approximation:
     * - Basic geometry, not geographic calc but in TM35FIN the difference is small
     * - Finds the closest by start/end, ignoring curvature
     */
    private fun approximateClosestSegmentIndex(target: IPoint): Int? =
        segments.mapIndexed { idx, seg ->
            pointDistanceToLine(seg.points.first(), seg.points.last(), target) to idx
        }.minByOrNull { (distance, _) -> distance }?.let { (_, index) -> index }
}

data class LayoutSegmentMetadata(
    val startPoint: Point,
    val endPoint: Point,
    val alignmentName: AlignmentName?,
    val planTime: Instant?,
    val measurementMethod: MeasurementMethod?,
    val fileName: FileName?,
    val originalSrid: Srid?,
) {
    fun isEmpty() = alignmentName == null
            && planTime == null
            && measurementMethod == null
            && fileName == null
            && originalSrid == null

    fun hasSameMetadata(other: LayoutSegmentMetadata): Boolean = alignmentName == other.alignmentName
            && planTime == other.planTime
            && measurementMethod == other.measurementMethod
            && fileName == other.fileName
            && originalSrid == other.originalSrid
}

data class LayoutSegment(
    val points: List<LayoutPoint>,
    val sourceId: DomainId<GeometryElement>?,
    val sourceStart: Double?,
    val resolution: Int,
    val switchId: DomainId<TrackLayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    val start: Double,
    val source: GeometrySource,
    val id: DomainId<LayoutSegment> = deriveFromSourceId("AS", sourceId),
) {
    val boundingBox: BoundingBox? = boundingBoxAroundPointsOrNull(points.map { sp -> Point(sp.x, sp.y) })
    val length: Double = points.last().m

    fun startDirection() = directionBetweenPoints(points[0], points[1])
    fun endDirection() = directionBetweenPoints(points[points.lastIndex - 1], points[points.lastIndex])

    init {
        require(points.size >= 2) { "Segment must have at least 2 points (start & end): points=${points.size}" }
        require(source != GENERATED || points.size == 2) { "Generated segment can't have more than 2 points" }
        require(resolution > 0) { "Invalid segment resolution: $resolution" }
        require(sourceStart?.isFinite() != false) { "Invalid source start length: $sourceStart" }
        require(start.isFinite()) { "Invalid start length: $start" }
        require(length.isFinite() && length >= 0.0) { "Invalid length: $length" }
        require(points.first().m == 0.0) { "M-values should be from segment start" }
        points.forEachIndexed { index, point ->
            require(index == 0 || point.x != points[index - 1].x || point.y != points[index - 1].y) {
                "There should be no duplicate points: id=$id ${index - 1}=${points[index - 1]} ${index}=${points[index]}"
            }
            require(index == 0 || point.m > points[index - 1].m) {
                "Segment m-values should be increasing: id=$id ${index - 1}=${points[index - 1].m} $index=${point.m}"
            }
        }
    }

    fun slice(fromIndex: Int, toIndex: Int, newStart: Double): LayoutSegment? =
        if (fromIndex >= toIndex) null
        else withPoints(points.slice(fromIndex..toIndex), newStart)

    fun withPoints(points: List<LayoutPoint>, newStart: Double): LayoutSegment {
        val mOffset = points.first().m
        val newPoints =
            if (mOffset == 0.0) points
            else points.map { p -> p.copy(m = max(0.0, p.m - mOffset)) }
        return copy(
            points = newPoints,
            sourceStart = sourceStart?.plus(mOffset),
            start = newStart,
        )
    }

    fun splitAtM(m: Double, tolerance: Double): Pair<LayoutSegment, LayoutSegment?> =
        if (m <= points.first().m || m >= points.last().m) this to null
        else {
            val pointAtM = getPointAtLengthInternal(m, tolerance)
            if (pointAtM.isSnapped && (pointAtM.index <= 0 || pointAtM.index >= points.lastIndex)) {
                this to null
            } else {
                val firstPoints =
                    if (pointAtM.isSnapped) points.slice(0..pointAtM.index)
                    else points.slice(0 until pointAtM.index) + pointAtM.point
                val secondPoints =
                    if (pointAtM.isSnapped) points.slice(pointAtM.index..points.lastIndex)
                    else listOf(pointAtM.point) + points.slice(pointAtM.index..points.lastIndex)
                withPoints(firstPoints, start) to withPoints(secondPoints, start + pointAtM.point.m)
            }
        }

    fun getPointIndex(trackLayoutPoint: IPoint): Int? {
        val index = points.indexOfFirst { point -> point.x == trackLayoutPoint.x && point.y == trackLayoutPoint.y }
        return if (index != -1) index else null
    }

    fun getSourceLengthAt(pointIndex: Int): Double? = sourceStart?.plus(points[pointIndex].m)

    fun includes(searchPoint: IPoint): Boolean {
        return points.any { point -> point.isSame(searchPoint, LAYOUT_COORDINATE_DELTA) }
    }

    fun getLengthUntil(target: IPoint): Pair<Double, IntersectType> =
        findLengthOnSegment(0..points.lastIndex, target)

    private fun findLengthOnSegment(range: ClosedRange<Int>, target: IPoint): Pair<Double, IntersectType> {
        if (range.start == range.endInclusive) {
            return points[range.start].m to WITHIN
        } else {
            val firstIndex = (range.start + range.endInclusive) / 2
            val secondIndex = firstIndex + 1
            require(secondIndex <= range.endInclusive) { "Halving search over-indexed" }
            val first = points[firstIndex]
            val second = points[secondIndex]
            // Note: Basic geometry, not geographic calc, but the difference is small in TM35FIN.
            val proportionOnLine = closestPointProportionOnLine(first, second, target).also { prop ->
                require(prop.isFinite()) { "Invalid proportion: prop=$prop first=$first second=$second target=$target" }
            }

            val interpolatedM = interpolate(first.m, second.m, proportionOnLine)
            return if (firstIndex == 0 && interpolatedM < -1.0) {
                // If in beginning & target is over 1m farther in negative direction
                0.0 to BEFORE
            } else if (secondIndex == points.lastIndex && interpolatedM > second.m + 1.0) {
                // If in the end & target is over 1m farther in positive direction
                second.m to AFTER
            } else if (proportionOnLine < 0.0) {
                // Target in the negative direction (towards start)
                findLengthOnSegment(range.start..firstIndex, target)
            } else if (proportionOnLine > 1.0) {
                // Target in the positive direction (towards end)
                findLengthOnSegment(secondIndex..range.endInclusive, target)
            } else {
                // Found target between the points
                interpolatedM to WITHIN
            }.also { (length, _) ->
                require(length.isFinite()) {
                    "Invalid length value: length=$length target=$target"
                }
            }
        }
    }

    /**
     * Finds a point on the line at given m-value (length along segment).
     * Snaps to actual segment points at snapDistance, if provided and greater than zero.
     * @return
     */
    fun getPointAtLength(m: Double, snapDistance: Double = 0.0): LayoutPoint =
        getPointAtLengthInternal(m, snapDistance).point


    private data class PointSeekResult(
        val point: LayoutPoint,
        val index: Int,
        val isSnapped: Boolean,
    )

    private fun getPointAtLengthInternal(m: Double, snapDistance: Double = 0.0): PointSeekResult =
        if (m <= 0.0) {
            PointSeekResult(points.first(), 0, true)
        } else if (m >= length) {
            PointSeekResult(points.last(), points.lastIndex, true)
        } else {
            val indexAfter = points.indexOfFirst { p -> p.m >= m }
            val pointAfter = points[indexAfter]
            points.getOrNull(indexAfter - 1)
                ?.let { pointBefore ->
                    if (abs(pointAfter.m - m) < snapDistance) {
                        PointSeekResult(pointAfter, indexAfter, true)
                    } else if (abs(pointBefore.m - m) < snapDistance) {
                        PointSeekResult(pointBefore, indexAfter - 1, true)
                    } else {
                        val portion = (m - pointBefore.m) / (pointAfter.m - pointBefore.m)
                        PointSeekResult(interpolate(pointBefore, pointAfter, portion), indexAfter, false)
                    }
                }
                ?: PointSeekResult(pointAfter, indexAfter, true)
        }
}

const val LAYOUT_COORDINATE_DELTA = 0.001
const val LAYOUT_HEIGHT_DELTA = 0.001
const val LAYOUT_CANT_DELTA = 0.00001
const val LAYOUT_M_DELTA = 0.001

data class LayoutPoint(
    override val x: Double,
    override val y: Double,
    val z: Double?,
    /**
     * Length (in meters) along the segment, from segment start to this point.
     * Combine with segment.start to get length from alignment start.
     */
    override val m: Double,
    val cant: Double?,
) : IPoint3DM {
    init {
        require(x.isFinite() && y.isFinite() && m.isFinite()) { "Cannot create layout point of: x=$x y=$y m=$m" }
        require(z?.isFinite() != false) { "Invalid Z value: $z" }
        require(cant?.isFinite() != false) { "Invalid cant value: $cant" }
        require(m >= 0.0) { "Layout point m-value must be positive" }
    }

    fun isSame(other: LayoutPoint) =
        super.isSame(other, LAYOUT_COORDINATE_DELTA)
                && isSame(z, other.z, LAYOUT_HEIGHT_DELTA)
                && isSame(cant, other.cant, LAYOUT_CANT_DELTA)

    fun isSame(other: IPoint) = super.isSame(other, LAYOUT_COORDINATE_DELTA)
}
