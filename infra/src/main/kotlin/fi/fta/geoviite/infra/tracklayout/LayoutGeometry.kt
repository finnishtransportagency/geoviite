package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.math.IntersectType.*
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.util.FileName
import java.time.Instant
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

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

interface IAlignment : Loggable {
    val segments: List<ISegment>
    val id: DomainId<*>
    val boundingBox: BoundingBox?

    val length: Double get() = segments.lastOrNull()?.let(ISegment::endM) ?: 0.0
    val firstSegmentStart: SegmentPoint? get() = segments.firstOrNull()?.segmentStart
    val lastSegmentEnd: SegmentPoint? get() = segments.lastOrNull()?.segmentEnd
    val start: LayoutPoint? get() = segments.firstOrNull()?.alignmentStart
    val end: LayoutPoint? get() = segments.lastOrNull()?.alignmentEnd

    val allSegmentPoints: List<SegmentPoint> get() = segments.flatMapIndexed { index, segment ->
        if (index == segments.lastIndex) segment.segmentPoints
        else segment.segmentPoints.take(segment.segmentPoints.size - 1)
    }

    fun filterSegmentsByBbox(bbox: BoundingBox): List<ISegment> {
        return if (!bbox.intersects(boundingBox)) {
            listOf() // Shortcut: if it doesn't hit the alignment, it won't hit segments either
        } else {
            segments.filter { s -> s.boundingBox?.intersects(bbox) ?: false }
        }
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

    fun getPointAtM(m: Double, snapDistance: Double = 0.0): LayoutPoint? =
        if (m <= 0.0) start
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
            closestPointProportionOnLine(segment.segmentPoints[0], segment.segmentPoints[1], target) < 0.0
        }
    }

    private fun isBeforeClosestPoint(segmentIndex: Int, target: IPoint): Boolean {
        val segment = segments[segmentIndex]
        return if (segment.source == GENERATED) {
            closestPointProportionOnGeneratedSegment(segmentIndex, target) > 1.0
        } else {
            closestPointProportionOnLine(
                segment.segmentPoints[segment.segmentPoints.lastIndex - 1],
                segment.segmentPoints[segment.segmentPoints.lastIndex],
                target,
            ) > 1.0
        }
    }

    // For generated segments, we use projections based on next/prev segment directions
    // Similarly, when finding the closest point on an alignment, we assume the segment going with that angle
    private fun closestPointProportionOnGeneratedSegment(segmentIndex: Int, target: IPoint): Double {
        val segment = segments[segmentIndex]
        val start = segment.segmentStart
        val end = segment.segmentEnd
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
    private fun approximateClosestSegmentIndex(target: IPoint): Int? = segments
        .mapIndexed { idx, seg -> pointDistanceToLine(seg.segmentStart, seg.segmentEnd, target) to idx }
        .minByOrNull { (distance, _) -> distance }
        ?.let { (_, index) -> index }

    fun getMaxDirectionDeltaRads(): Double {
        var prevPoint: SegmentPoint? = null
        var prevDirection: Double? = null
        return if (segments.isEmpty()) {
            0.0
        } else {
            segments.maxOf { segment ->
                segment.segmentPoints.maxOf { point ->
                    val direction = prevPoint?.let { prev -> directionBetweenPoints(prev, point) }
                    angleDeltaRads(prevDirection, direction).also {
                        prevPoint = point
                        prevDirection = direction
                    }
                }
            }
        }
    }

    private fun angleDeltaRads(dir1: Double?, dir2: Double?): Double =
        if (dir1 != null && dir2 != null) angleDiffRads(dir1, dir2) else 0.0

    override fun toLog(): String = logFormat("id" to id, "segments" to segments.size, "length" to round(length, 3))
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
                require(previous.segmentEnd.isSame(segment.segmentStart, LAYOUT_COORDINATE_DELTA)) {
                    "Alignment segment doesn't start where the previous one ended: " +
                            "alignment=$id segment=$index length=${segment.length} prevLength=${previous.length} " +
                            "diff=${lineLength(previous.segmentEnd, segment.segmentStart)}"
                }
                require(isSame(previous.startM + previous.length, segment.startM, LAYOUT_M_DELTA)) {
                    "Alignment segment m-calculation should be continuous: " +
                            "alignment=$id segment=$index " +
                            "prevStart=${previous.startM} prevLength=${previous.length} nextStart=${segment.startM}"
                }
            }
        }
    }

    fun withSegments(newSegments: List<LayoutSegment>) = copy(segments = newSegments)

    fun takeFirst(count: Int): List<LayoutPoint> = segments.firstOrNull()?.takeFirst(count) ?: listOf()

    fun takeLast(count: Int): List<LayoutPoint> = segments.lastOrNull()?.takeLast(count) ?: listOf()
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

    @get:JsonIgnore
    val segmentPoints: List<SegmentPoint>

    @get:JsonIgnore
    val startDirection: Double

    @get:JsonIgnore
    val endDirection: Double

    @get:JsonIgnore
    val boundingBox: BoundingBox?

    val segmentStart: SegmentPoint get() = segmentPoints.first()
    val segmentEnd: SegmentPoint get() = segmentPoints.last()
    val length: Double get() = segmentPoints.last().m

    fun includes(searchPoint: IPoint): Boolean {
        return segmentPoints.any { point -> point.isSame(searchPoint, LAYOUT_COORDINATE_DELTA) }
    }

    /**
     * Finds a point on the line at given in-segment m-value
     * Snaps to actual segment points at snapDistance, if provided and greater than zero.
     */
    fun seekPointAtSegmentM(segmentM: Double, snapDistance: Double = 0.0): PointSeekResult<SegmentPoint> {
        return if (segmentM <= 0.0) {
            PointSeekResult(segmentStart, 0, true)
        } else if (segmentM >= length) {
            PointSeekResult(segmentEnd, segmentPoints.lastIndex, true)
        } else {
            val indexAfter = segmentPoints.indexOfFirst { p -> p.m >= segmentM }
            val pointAfter = segmentPoints[indexAfter]
            segmentPoints.getOrNull(indexAfter - 1)?.let { pointBefore ->
                val distanceToLast = abs(pointBefore.m - segmentM)
                val distanceToNext = abs(pointAfter.m - segmentM)
                if (distanceToLast <= min(snapDistance, distanceToNext)) {
                    PointSeekResult(pointBefore, indexAfter - 1, true)
                } else if (distanceToNext <= min(snapDistance, distanceToLast)) {
                    PointSeekResult(pointAfter, indexAfter, true)
                } else {
                    val portion = (segmentM - pointBefore.m) / (pointAfter.m - pointBefore.m)
                    PointSeekResult(interpolate(pointBefore, pointAfter, portion), indexAfter, false)
                }
            } ?: PointSeekResult(pointAfter, indexAfter, true)
        }
    }
}

data class SegmentPoint(
    override val x: Double,
    override val y: Double,
    val z: Double?,
    /**
     * Length (in meters) along the segment, from start to this point.
     */
    override val m: Double,
    val cant: Double?,
) : IPoint3DM {
    fun toLayoutPoint(segmentStartM: Double) = LayoutPoint(x = x, y = y, z = z, m = m + segmentStartM, cant = cant)
}

data class SegmentGeometry(
    override val resolution: Int,
    override val segmentPoints: List<SegmentPoint>,
    val id: DomainId<SegmentGeometry> = StringId(),
) : ISegmentGeometry, Loggable {

    override val boundingBox: BoundingBox? by lazy { boundingBoxAroundPointsOrNull(segmentPoints) }

    override val startDirection: Double by lazy {
        directionBetweenPoints(segmentPoints[0], segmentPoints[1])
    }
    override val endDirection: Double by lazy {
        directionBetweenPoints(segmentPoints[segmentPoints.lastIndex - 1], segmentPoints[segmentPoints.lastIndex])
    }

    init {
        require(resolution > 0) { "Invalid segment geometry resolution: $resolution" }
        require(segmentPoints.size >= 2) { "Segment geometry must have at least 2 points: points=${segmentPoints.size}" }
        require(length.isFinite() && length >= 0.0) { "Invalid length: $length" }
        require(segmentStart.m == 0.0) { "Segment geometry m-values should start at 0.0" }
        segmentPoints.forEachIndexed { index, point ->
            require(index == 0 || point.x != segmentPoints[index - 1].x || point.y != segmentPoints[index - 1].y) {
                "There should be no duplicate points in segment geometry:" +
                        " id=$id ${index - 1}=${segmentPoints[index - 1]} ${index}=${segmentPoints[index]}"
            }
            require(index == 0 || point.m > segmentPoints[index - 1].m) {
                "Segment geometry m-values should be increasing:" +
                        " id=$id ${index - 1}=${segmentPoints[index - 1].m} $index=${point.m}"
            }
        }
    }

    fun withPoints(segmentPoints: List<SegmentPoint>): SegmentGeometry =
        copy(segmentPoints = fixSegmentGeometryMValues(segmentPoints), id = StringId())

    fun splitAtSegmentM(segmentM: Double, tolerance: Double): Pair<SegmentGeometry, SegmentGeometry?> =
        if (segmentM !in 0.0..length) this to null
        else {
            val pointAtM = seekPointAtSegmentM(segmentM, tolerance)
            if (pointAtM.isSnapped && (pointAtM.index <= 0 || pointAtM.index >= segmentPoints.lastIndex)) {
                this to null
            } else {
                val firstPoints =
                    if (pointAtM.isSnapped) segmentPoints.slice(0..pointAtM.index)
                    else segmentPoints.slice(0 until pointAtM.index) + pointAtM.point
                val secondPoints =
                    if (pointAtM.isSnapped) segmentPoints.slice(pointAtM.index..segmentPoints.lastIndex)
                    else listOf(pointAtM.point) + segmentPoints.slice(pointAtM.index..segmentPoints.lastIndex)
                withPoints(firstPoints) to withPoints(secondPoints)
            }
        }

    override fun toLog(): String = logFormat("id" to id, "points" to segmentPoints.size)
}

private fun fixSegmentGeometryMValues(points: List<SegmentPoint>): List<SegmentPoint> =
    if (points.first().m == 0.0) points
    else points.first().m.let { mOffset -> points.map { p -> p.copy(m = max(0.0, p.m - mOffset)) } }

interface ISegmentFields {
    val sourceId: DomainId<GeometryElement>?
    val sourceStart: Double?
    val source: GeometrySource
    val id: DomainId<LayoutSegment>
}

interface ISegment : ISegmentGeometry, ISegmentFields {
    @get:JsonIgnore
    val geometry: SegmentGeometry
    val startM: Double
    val endM: Double get() = startM + segmentPoints.last().m
    val alignmentStart: LayoutPoint get() = segmentStart.let(::toLayoutPoint)
    val alignmentEnd: LayoutPoint get() = segmentEnd.let(::toLayoutPoint)
    val alignmentPoints: List<LayoutPoint> get() = segmentPoints.map(::toLayoutPoint)

    fun getClosestPointM(target: IPoint): Pair<Double, IntersectType> =
        findClosestSegmentPointM(0..segmentPoints.lastIndex, target).let { (segmentM, intersect) ->
            segmentM + startM to intersect
        }

    private fun findClosestSegmentPointM(range: ClosedRange<Int>, target: IPoint): Pair<Double, IntersectType> {
        if (range.start == range.endInclusive) {
            return segmentPoints[range.start].m to WITHIN
        } else {
            val firstIndex = (range.start + range.endInclusive) / 2
            val secondIndex = firstIndex + 1
            require(secondIndex <= range.endInclusive) { "Halving search over-indexed" }
            val first = segmentPoints[firstIndex]
            val second = segmentPoints[secondIndex]
            // Note: Basic geometry, not geographic calc, but the difference is small in TM35FIN.
            val proportionOnLine = closestPointProportionOnLine(first, second, target).also { prop ->
                require(prop.isFinite()) { "Invalid proportion: prop=$prop first=$first second=$second target=$target" }
            }

            val interpolatedM = interpolate(first.m, second.m, proportionOnLine)
            return if (firstIndex == 0 && interpolatedM < first.m - POINT_SEEK_TOLERANCE) {
                // If in beginning & target is over 1m farther in negative direction
                first.m to BEFORE
            } else if (secondIndex == segmentPoints.lastIndex && interpolatedM > second.m + POINT_SEEK_TOLERANCE) {
                // If in the end & target is over 1m farther in positive direction
                second.m to AFTER
            } else if (proportionOnLine < 0.0) {
                // Target in the negative direction (towards start)
                findClosestSegmentPointM(range.start..firstIndex, target)
            } else if (proportionOnLine > 1.0) {
                // Target in the positive direction (towards end)
                findClosestSegmentPointM(secondIndex..range.endInclusive, target)
            } else {
                // Found target between the points
                interpolatedM to WITHIN
            }.also { (length, _) ->
                require(length.isFinite()) { "Invalid length value: length=$length target=$target" }
            }
        }
    }

    /**
     * Finds a point on the line at given alignment m-value (segment start + in-segment m).
     * Snaps to actual segment points at snapDistance, if provided and greater than zero.
     */
    fun seekPointAtM(m: Double, snapDistance: Double = 0.0): PointSeekResult<LayoutPoint> =
        seekPointAtSegmentM(m - startM, snapDistance).let { r ->
            PointSeekResult(toLayoutPoint(r.point), r.index, r.isSnapped)
        }

    fun toLayoutPoint(segmentPoint: SegmentPoint) = segmentPoint.toLayoutPoint(startM)

    fun takeFirst(count: Int): List<LayoutPoint> {
        require(count >= 0 && count <= segmentPoints.size) {
            "Invalid point range requested: points=${segmentPoints.size} count=$count"
        }
        return segmentPoints.take(count).map(::toLayoutPoint)
    }

    fun takeLast(count: Int): List<LayoutPoint> {
        require(count >= 0 && count <= segmentPoints.size) {
            "Invalid point range requested: points=${segmentPoints.size} count=$count"
        }
        return segmentPoints.takeLast(count).map(::toLayoutPoint)
    }
}

data class PointSeekResult<T : IPoint3DM>(
    val point: T,
    val index: Int,
    val isSnapped: Boolean,
)

data class LayoutSegment(
    @JsonIgnore override val geometry: SegmentGeometry,
    override val sourceId: DomainId<GeometryElement>?,
    override val sourceStart: Double?,
    override val startM: Double,
    val switchId: DomainId<TrackLayoutSwitch>?,
    val startJointNumber: JointNumber?,
    val endJointNumber: JointNumber?,
    override val source: GeometrySource,
    override val id: DomainId<LayoutSegment> = deriveFromSourceId("AS", sourceId),
) : ISegmentGeometry by geometry, ISegment, Loggable {

    init {
        require(source != GENERATED || segmentPoints.size == 2) { "Generated segment can't have more than 2 points" }
        require(sourceStart?.isFinite() != false) { "Invalid source start length: $sourceStart" }
        require(startM.isFinite() && startM >= 0.0) { "Invalid start m: $startM" }
        require(endM.isFinite() && endM >= startM) { "Invalid end m: $endM" }
    }

    fun slice(fromIndex: Int, toIndex: Int, newStart: Double? = null): LayoutSegment? {
        return if (fromIndex >= toIndex) null
        else segmentPoints.slice(fromIndex..toIndex).let { newPoints ->
            val offset = newPoints.first().m
            withPoints(
                points = fixSegmentGeometryMValues(newPoints),
                newStart = newStart ?: (startM + offset),
                newSourceStart = sourceStart?.plus(offset),
            )
        }
    }

    fun slice(mRange: Range<Double>, snapDistance: Double = 0.0): LayoutSegment {
        require(mRange.min + snapDistance < mRange.max) {
            "Slice m-range must be at least as long as snap distance: range=$mRange snapDistance=$snapDistance"
        }
        require(mRange.min + snapDistance >= startM && mRange.max - startM <= endM) {
            "Slice m-range ends must be within segment (with snapDistance tolerance):" + " range=$mRange snapDistance=$snapDistance segment=${startM..endM}"
        }
        val start = seekPointAtSegmentM(mRange.min - startM, snapDistance)
        val end = seekPointAtSegmentM(mRange.max - startM, snapDistance)
        val actualPointsRange = start.index..(if (end.isSnapped) end.index else end.index - 1)
        val currentSegmentPoints = segmentPoints.slice(actualPointsRange)
        val interpolatedStart = listOfNotNull(if (start.isSnapped) null else start.point)
        val interpolatedEnd = listOfNotNull(if (end.isSnapped) null else end.point)
        val newPoints = interpolatedStart + currentSegmentPoints + interpolatedEnd
        val startCutLength = newPoints.first().m
        return withPoints(newPoints, startM + startCutLength, sourceStart?.plus(startCutLength))
    }

    fun withPoints(points: List<SegmentPoint>, newStart: Double, newSourceStart: Double?): LayoutSegment =
        withGeometry(geometry.withPoints(points), newStart, newSourceStart)

    private fun withGeometry(geometry: SegmentGeometry, newStart: Double, newSourceStart: Double?): LayoutSegment =
        copy(geometry = geometry, startM = newStart, sourceStart = newSourceStart)

    fun withStartM(newStartM: Double): LayoutSegment = if (newStartM == startM) this else copy(startM = newStartM)

    fun splitAtM(m: Double, tolerance: Double): Pair<LayoutSegment, LayoutSegment?> {
        val (startGeom, endGeom) = geometry.splitAtSegmentM(m - startM, tolerance)
        return if (endGeom == null) {
            this to null
        } else {
            val splitLength = startGeom.length
            val startSegment = withGeometry(startGeom, startM, sourceStart)
            val endSegment = withGeometry(endGeom, startM + splitLength, sourceStart?.plus(splitLength))
            startSegment to endSegment
        }
    }

    fun withoutSwitch(): LayoutSegment =
        if (switchId == null && startJointNumber == null && endJointNumber == null) this
        else copy(switchId = null, startJointNumber = null, endJointNumber = null)

    override fun toLog(): String = logFormat("id" to id, "source" to source, "geometry" to geometry.toLog())
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
