package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
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

const val POINT_SEEK_TOLERANCE = 1.0

enum class GeometrySource {
    IMPORTED, PLAN, GENERATED,
}

fun emptyAlignment() = LayoutAlignment(segments = listOf())

data class SegmentGeometryAndMetadata(
    val planId: IntId<GeometryPlan>?,
    val fileName: FileName?,
    val alignmentId: IntId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,
    val startPoint: IPoint?,
    val endPoint: IPoint?,
    val isLinked: Boolean,
    val id: StringId<SegmentGeometryAndMetadata>,
)

data class PlanSectionPoint(
    val address: TrackMeter,
    val m: Double,
)

data class AlignmentPlanSection(
    val planId: IntId<GeometryPlan>?,
    val planName: FileName?,
    val alignmentId: IntId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,
    val isLinked: Boolean,
    val start: PlanSectionPoint,
    val end: PlanSectionPoint,
    val id: StringId<SegmentGeometryAndMetadata>,
)

interface IAlignment {
    val segments: List<ISegment>
    val id: DomainId<*>
    val boundingBox: BoundingBox?

    val length: Double
        get() = segments.lastOrNull()?.let(ISegmentGeometry::endM) ?: 0.0
    val start: LayoutPoint?
        get() = segments.firstOrNull()?.points?.first()
    val end: LayoutPoint?
        get() = segments.lastOrNull()?.points?.last()

    fun allPoints() = segments.flatMapIndexed { index, segment ->
        if (index == segments.lastIndex) segment.points
        else segment.points.take(segment.points.size - 1)
    }

    fun getClosestPointM(target: IPoint): Pair<Double, IntersectType>? =
        findClosestSegmentIndex(target)?.let { segmentIndex ->
            val segment = segments[segmentIndex]
            if (segment.source == GENERATED) {
                val proportion = closestPointProportionOnGeneratedSegment(segmentIndex, target)
                val interpolatedInternalM = proportion * segment.length
                if (interpolatedInternalM < -POINT_SEEK_TOLERANCE) segment.startM to BEFORE
                else if (interpolatedInternalM > segment.length + POINT_SEEK_TOLERANCE) segment.endM to AFTER
                else if (interpolatedInternalM < 0.0) segment.startM to WITHIN
                else if (interpolatedInternalM > segment.length) segment.endM to WITHIN
                else segment.startM + interpolatedInternalM to WITHIN
            } else {
                segment.getClosestPointM(target)
            }
        }

    fun getPointAtM(m: Double, snapDistance: Double = 0.0): LayoutPoint? = if (m <= 0.0) start
    else if (m >= length) end
    else getSegmentAtM(m)?.seekPointAtM(m, snapDistance)?.point

    fun getSegmentIndexAtM(m: Double) = segments.binarySearch { s ->
        if (m < s.startM) 1
        else if (m > s.endM) -1
        else 0
    }

    fun getSegmentAtM(m: Double) = segments.getOrNull(getSegmentIndexAtM(m))

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
                target,
            ) > 1.0
        }
    }

    // For generated segments, we use projections based on next/prev segment directions
    // Similarly, when finding the closest point on an alignment, we assume the segment going with that angle
    private fun closestPointProportionOnGeneratedSegment(segmentIndex: Int, target: IPoint): Double {
        val segment = segments[segmentIndex]
        val start = segment.points.first()
        val end = segment.points.last()
        val prevDir = segments.getOrNull(segmentIndex - 1)?.endDirection
        val nextDir = segments.getOrNull(segmentIndex + 1)?.startDirection
        val fakeDir = if (prevDir != null && nextDir != null) angleAvgRads(prevDir, nextDir)
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
    private fun approximateClosestSegmentIndex(target: IPoint): Int? = segments.mapIndexed { idx, seg ->
        pointDistanceToLine(seg.points.first(), seg.points.last(), target) to idx
    }.minByOrNull { (distance, _) -> distance }?.let { (_, index) -> index }
}

data class LayoutAlignment(
    override val segments: List<LayoutSegment>,
    override val id: DomainId<LayoutAlignment> = StringId(),
    val dataType: DataType = DataType.TEMP,
) : IAlignment {
    override val boundingBox: BoundingBox? = boundingBoxCombining(segments.mapNotNull { s -> s.boundingBox })

    init {
        segments.forEachIndexed { index, segment ->
            if (index == 0) {
                require(segment.startM == 0.0) {
                    "First segment should start at 0.0: alignment=$id firstStart=${segment.startM}"
                }
            } else {
                val previous = segments[index - 1]
                require(previous.points.last().isSame(segment.points.first(), LAYOUT_COORDINATE_DELTA)) {
                    "Alignment segment doesn't start where the previous one ended: " + "alignment=$id segment=$index " + "length=${segment.length} prevLength=${previous.length} " + "diff=${
                        lineLength(
                            previous.points.last(), segment.points.first()
                        )
                    }"
                }
                require(isSame(previous.startM + previous.length, segment.startM, LAYOUT_M_DELTA)) {
                    "Alignment segment m-calculation should be continuous: " + "alignment=$id segment=$index prevEnd=${previous.startM + previous.length} start=${segment.startM}"
                }
            }
        }
    }

    fun withSegments(newSegments: List<LayoutSegment>) = copy(segments = newSegments)

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
    fun isEmpty() =
        alignmentName == null && planTime == null && measurementMethod == null && fileName == null && originalSrid == null

    fun hasSameMetadata(other: LayoutSegmentMetadata): Boolean =
        alignmentName == other.alignmentName && planTime == other.planTime && measurementMethod == other.measurementMethod && fileName == other.fileName && originalSrid == other.originalSrid
}

interface ISegmentGeometry {
    val resolution: Int
    val points: List<LayoutPoint>

    @get:JsonIgnore
    val startDirection: Double

    @get:JsonIgnore
    val endDirection: Double

    @get:JsonIgnore
    val boundingBox: BoundingBox?

    val startM: Double get() = points.first().m
    val endM: Double get() = points.last().m
    val length: Double get() = endM - startM

    fun includes(searchPoint: IPoint): Boolean {
        return points.any { point -> point.isSame(searchPoint, LAYOUT_COORDINATE_DELTA) }
    }

    fun getClosestPointM(target: IPoint): Pair<Double, IntersectType> = findClosestPointM(0..points.lastIndex, target)

    private fun findClosestPointM(range: ClosedRange<Int>, target: IPoint): Pair<Double, IntersectType> {
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
            return if (firstIndex == 0 && interpolatedM < first.m - POINT_SEEK_TOLERANCE) {
                // If in beginning & target is over 1m farther in negative direction
                first.m to BEFORE
            } else if (secondIndex == points.lastIndex && interpolatedM > second.m + POINT_SEEK_TOLERANCE) {
                // If in the end & target is over 1m farther in positive direction
                second.m to AFTER
            } else if (proportionOnLine < 0.0) {
                // Target in the negative direction (towards start)
                findClosestPointM(range.start..firstIndex, target)
            } else if (proportionOnLine > 1.0) {
                // Target in the positive direction (towards end)
                findClosestPointM(secondIndex..range.endInclusive, target)
            } else {
                // Found target between the points
                interpolatedM to WITHIN
            }.also { (length, _) ->
                require(length.isFinite()) { "Invalid length value: length=$length target=$target" }
            }
        }
    }

    /**
     * Finds a point on the line at given m-value (length along alignment).
     * Snaps to actual segment points at snapDistance, if provided and greater than zero.
     */
    fun seekPointAtM(m: Double, snapDistance: Double = 0.0): PointSeekResult = if (m <= startM) {
        PointSeekResult(points.first(), 0, true)
    } else if (m >= endM) {
        PointSeekResult(points.last(), points.lastIndex, true)
    } else {
        val indexAfter = points.indexOfFirst { p -> p.m >= m }
        val pointAfter = points[indexAfter]
        points.getOrNull(indexAfter - 1)?.let { pointBefore ->
            if (abs(pointAfter.m - m) <= snapDistance) {
                PointSeekResult(pointAfter, indexAfter, true)
            } else if (abs(pointBefore.m - m) <= snapDistance) {
                PointSeekResult(pointBefore, indexAfter - 1, true)
            } else {
                val portion = (m - pointBefore.m) / (pointAfter.m - pointBefore.m)
                PointSeekResult(interpolate(pointBefore, pointAfter, portion), indexAfter, false)
            }
        } ?: PointSeekResult(pointAfter, indexAfter, true)
    }

    data class PointSeekResult(
        val point: LayoutPoint,
        val index: Int,
        val isSnapped: Boolean,
    )
}

data class SegmentGeometry(
    override val resolution: Int,
    override val points: List<LayoutPoint>,
    val id: DomainId<SegmentGeometry> = StringId(),
) : ISegmentGeometry {
    constructor(resolution: Int, points: List<LayoutPoint>, start: Double) : this(
        resolution, adjustMValuesToStart(points, start)
    )

    override val boundingBox: BoundingBox? by lazy { boundingBoxAroundPointsOrNull(points) }

    override val startDirection: Double by lazy {
        directionBetweenPoints(points[0], points[1])
    }
    override val endDirection: Double by lazy {
        directionBetweenPoints(points[points.lastIndex - 1], points[points.lastIndex])
    }

    init {
        require(resolution > 0) { "Invalid segment geometry resolution: $resolution" }
        require(points.size >= 2) { "Segment geometry must have at least 2 points: points=${points.size}" }
        require(startM.isFinite() && startM >= 0.0) { "Invalid start m: $startM" }
        require(endM.isFinite() && endM >= startM) { "Invalid end m: $endM" }
        require(length.isFinite() && length >= 0.0) { "Invalid length: $length" }
        points.forEachIndexed { index, point ->
            require(index == 0 || point.x != points[index - 1].x || point.y != points[index - 1].y) {
                "There should be no duplicate points in segment geometry:" + " id=$id ${index - 1}=${points[index - 1]} ${index}=${points[index]}"
            }
            require(index == 0 || point.m > points[index - 1].m) {
                "Segment geometry m-values should be increasing:" + " id=$id ${index - 1}=${points[index - 1].m} $index=${point.m}"
            }
        }
    }

    fun withPoints(points: List<LayoutPoint>, start: Double? = null): SegmentGeometry =
        copy(points = adjustMValuesToStart(points, start ?: points.first().m), id = StringId())

    fun withStartMAt(start: Double): SegmentGeometry = copy(points = adjustMValuesToStart(points, start))
}

private fun adjustMValuesToStart(points: List<LayoutPoint>, start: Double): List<LayoutPoint> =
    if (start == points.first().m) points
    else (start - points.first().m).let { mOffset ->
        points.map { p -> p.copy(m = max(0.0, p.m + mOffset)) }
    }

interface ISegmentFields {
    val sourceId: DomainId<GeometryElement>?
    val sourceStart: Double?
    val source: GeometrySource
    val id: DomainId<LayoutSegment>
}

interface ISegment : ISegmentGeometry, ISegmentFields {
    @get:JsonIgnore
    val geometry: SegmentGeometry
}

data class LayoutSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    override val sourceId: DomainId<GeometryElement>?,
    override val sourceStart: Double?,
    val switchId: DomainId<TrackLayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    override val source: GeometrySource,
    override val id: DomainId<LayoutSegment> = deriveFromSourceId("AS", sourceId),
) : ISegmentGeometry by geometry, ISegment {

    init {
        require(source != GENERATED || points.size == 2) { "Generated segment can't have more than 2 points" }
        require(sourceStart?.isFinite() != false) { "Invalid source start length: $sourceStart" }
    }

    fun slice(fromIndex: Int, toIndex: Int, newStart: Double? = null): LayoutSegment? = if (fromIndex >= toIndex) null
    else points.slice(fromIndex..toIndex).let { newPoints ->
        withPoints(newPoints, newStart, sourceStart?.plus(newPoints.first().m - points.first().m))
    }

    fun slice(mRange: Range<Double>, snapDistance: Double = 0.0): LayoutSegment {
        require(mRange.min + snapDistance < mRange.max) {
            "Slice m-range must be at least as long as snap distance: range=$mRange snapDistance=$snapDistance"
        }
        require(mRange.min + snapDistance >= startM && mRange.max - startM <= endM) {
            "Slice m-range ends must be within segment (with snapDistance tolerance):" + " range=$mRange snapDistance=$snapDistance segment=${startM..endM}"
        }
        val start = seekPointAtM(mRange.min, snapDistance)
        val end = seekPointAtM(mRange.max, snapDistance)
        val actualPointsRange = start.index..(if (end.isSnapped) end.index else end.index - 1)
        val currentPoints = points.slice(actualPointsRange)
        val interpolatedStart = listOfNotNull(if (start.isSnapped) null else start.point)
        val interpolatedEnd = listOfNotNull(if (end.isSnapped) null else end.point)
        val newPoints = interpolatedStart + currentPoints + interpolatedEnd
        return withPoints(newPoints, null, sourceStart?.plus(newPoints.first().m - points.first().m))
    }

    private fun withPoints(points: List<LayoutPoint>, newStart: Double?, newSourceStart: Double?): LayoutSegment = copy(
        geometry = geometry.withPoints(points, newStart),
        sourceStart = newSourceStart,
    )

    fun withStartM(newStartM: Double): LayoutSegment = if (newStartM == startM) this
    else copy(geometry = geometry.withStartMAt(newStartM))

    fun splitAtM(m: Double, tolerance: Double): Pair<LayoutSegment, LayoutSegment?> =
        if (m !in startM..endM) this to null
        else {
            val pointAtM = seekPointAtM(m, tolerance)
            if (pointAtM.isSnapped && (pointAtM.index <= 0 || pointAtM.index >= points.lastIndex)) {
                this to null
            } else {
                val firstPoints = if (pointAtM.isSnapped) points.slice(0..pointAtM.index)
                else points.slice(0 until pointAtM.index) + pointAtM.point
                val secondPoints = if (pointAtM.isSnapped) points.slice(pointAtM.index..points.lastIndex)
                else listOf(pointAtM.point) + points.slice(pointAtM.index..points.lastIndex)
                val first = withPoints(
                    points = firstPoints,
                    newStart = startM,
                    newSourceStart = sourceStart,
                )
                val second = withPoints(
                    points = secondPoints,
                    newStart = pointAtM.point.m,
                    newSourceStart = sourceStart?.plus(secondPoints.first().m - points.first().m),
                )
                first to second
            }
        }

    fun withoutSwitch(): LayoutSegment =
        if (switchId == null && startJointNumber == null && endJointNumber == null) this
        else copy(switchId = null, startJointNumber = null, endJointNumber = null)

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
     * Length (in meters) along the alignment, from start to this point.
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
        super.isSame(other, LAYOUT_COORDINATE_DELTA) && isSame(z, other.z, LAYOUT_HEIGHT_DELTA) && isSame(
            cant, other.cant, LAYOUT_CANT_DELTA
        )

    fun isSame(other: IPoint) = super.isSame(other, LAYOUT_COORDINATE_DELTA)
}
