package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.RotationDirection
import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.common.RotationDirection.CW
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geometry.GeometryElementType.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.min

/** Type enumeration for JSON & database representations */
enum class GeometryElementType {
    LINE,
    CURVE,
    CLOTHOID,
    BIQUADRATIC_PARABOLA,
}

interface ElementContent {
    val name: PlanElementName?
    val oidPart: PlanElementName?
    val start: Point
    val end: Point
    val staStart: BigDecimal
    val length: BigDecimal
}

data class ElementData(
    override val name: PlanElementName?,
    override val oidPart: PlanElementName?,
    override val start: Point,
    override val end: Point,
    override val staStart: BigDecimal,
    override val length: BigDecimal,
) : ElementContent {
    init {
        if (start == end)
            throw IllegalArgumentException(
                "Element start and end should differ: name=$name oid=$oidPart start=$start end=$end"
            )
    }
}

interface SwitchContent {
    val switchId: DomainId<GeometrySwitch>?
    val startJointNumber: JointNumber?
    val endJointNumber: JointNumber?
}

data class SwitchData(
    override val switchId: DomainId<GeometrySwitch>?,
    override val startJointNumber: JointNumber?,
    override val endJointNumber: JointNumber?,
) : SwitchContent {
    fun contentEquals(other: SwitchData) =
        startJointNumber == other.startJointNumber && endJointNumber == other.endJointNumber
}

interface CurveContent {
    val rotation: RotationDirection
    val radius: BigDecimal
    val chord: BigDecimal
    val center: Point
}

data class CurveData(
    override val rotation: RotationDirection,
    override val radius: BigDecimal,
    override val chord: BigDecimal,
    override val center: Point,
) : CurveContent

interface SpiralContent {
    /** Direction of spiral curvature, relative to rail forward direction * */
    val rotation: RotationDirection

    /** Forward direction of the rail at segment start * */
    val directionStart: Angle?

    /** Forward direction of the rail at segment end * */
    val directionEnd: Angle?

    /** Spiral steepness at segment start, expressed as the circle radius of the curve * */
    val radiusStart: BigDecimal?

    /** Spiral steepness at segment end, expressed as the circle radius of the curve * */
    val radiusEnd: BigDecimal?

    /** Segment start & end point tangents' intersection * */
    val pi: Point
}

data class SpiralData(
    override val rotation: RotationDirection,
    override val directionStart: Angle?,
    override val directionEnd: Angle?,
    override val radiusStart: BigDecimal?,
    override val radiusEnd: BigDecimal?,
    override val pi: Point,
) : SpiralContent

/** Geometry element is a single mathematically defined piece of the overall geometry building up an alignment */
sealed class GeometryElement : ElementContent, SwitchContent {
    abstract val type: GeometryElementType
    abstract val id: DomainId<GeometryElement>
    abstract val calculatedLength: Double

    @get:JsonIgnore abstract val bounds: List<Point>
    @get:JsonIgnore abstract val startDirectionRads: Double
    @get:JsonIgnore abstract val endDirectionRads: Double

    abstract fun getCoordinateAt(distance: Double): Point

    abstract fun contentEquals(other: GeometryElement): Boolean

    abstract fun getLengthUntil(target: Point): Double
}

data class GeometryLine(
    private val elementData: ElementData,
    private val switchData: SwitchData,
    override val id: DomainId<GeometryElement> = StringId(),
) : ElementContent by elementData, SwitchContent by switchData, GeometryElement() {

    override val type: GeometryElementType = LINE
    override val calculatedLength: Double by lazy { lineLength(start, end) }
    override val bounds: List<Point> by lazy { listOf(start, end) }
    override val startDirectionRads: Double = directionBetweenPoints(start, end)
    override val endDirectionRads: Double = directionBetweenPoints(start, end)

    override fun getCoordinateAt(distance: Double): Point = linePointAtDistance(start, end, distance)

    override fun contentEquals(other: GeometryElement): Boolean {
        return other is GeometryLine && other.elementData == elementData && switchData.contentEquals(other.switchData)
    }

    override fun getLengthUntil(target: Point): Double = lineLength(start, closestPointOnLine(start, end, target))
}

data class GeometryCurve(
    private val elementData: ElementData,
    private val curveData: CurveData,
    private val switchData: SwitchData,
    override val id: DomainId<GeometryElement> = StringId(),
) : ElementContent by elementData, CurveContent by curveData, SwitchContent by switchData, GeometryElement() {

    override val type: GeometryElementType = CURVE
    override val calculatedLength: Double by lazy { circleArcLength(radius.toDouble(), chord.toDouble()) }

    override val bounds: List<Point> by lazy {
        val directionStartToEnd = directionBetweenPoints(start, end)

        // Rotate the curve to an ideal position: start -> end line along positive X
        val rotatedCenter = rotateAroundPoint(start, -directionStartToEnd, center)
        val rotatedEndPoint = rotateAroundPoint(start, -directionStartToEnd, end)

        // Now the X ranges from start to end now and Y is flat. The farthest Y is at the center of
        // the curve.
        val radiusOffset = Point(0.0, radius.toDouble())
        val rotatedCurvePoint = if (rotation == CW) rotatedCenter + radiusOffset else rotatedCenter - radiusOffset

        // Define minimal bounding corners in this rotated coordinate system and then rotate them
        // back
        val rotatedCorners = boundingBoxAroundPoints(start, rotatedCurvePoint, rotatedEndPoint).corners
        rotatedCorners.map { point -> rotateAroundPoint(start, directionStartToEnd, point) }
    }
    override val startDirectionRads: Double by lazy {
        rotateAngle(directionBetweenPoints(center, start), rotationDirectionSign * PI / 2)
    }
    override val endDirectionRads: Double by lazy {
        rotateAngle(directionBetweenPoints(center, end), rotationDirectionSign * PI / 2)
    }

    override fun getCoordinateAt(distance: Double): Point {
        val targetAngle = chordDirection + (rotationDirectionSign * distance / radius.toDouble())
        return pointInDirection(center, radius.toDouble(), targetAngle)
    }

    private val rotationDirectionSign by lazy { if (rotation == CCW) 1 else -1 }
    private val chordDirection: Double by lazy { directionBetweenPoints(center, start) }

    override fun contentEquals(other: GeometryElement): Boolean {
        return other is GeometryCurve &&
            other.elementData == elementData &&
            other.curveData == curveData &&
            switchData.contentEquals(other.switchData)
    }

    override fun getLengthUntil(target: Point): Double {
        val toStart = directionBetweenPoints(center, start)
        val toEnd = directionBetweenPoints(center, end)
        val toTarget = directionBetweenPoints(center, target)
        return if (isInsideArc(toStart, toEnd, toTarget)) {
            val angleDiff = if (rotation == CCW) angleDiffRads(toTarget, toStart) else angleDiffRads(toStart, toTarget)
            circleSubArcLength(radius.toDouble(), angleDiff)
        } else if (angleDiffRads(toStart, toTarget) <= angleDiffRads(toEnd, toTarget)) {
            0.0
        } else {
            calculatedLength
        }
    }

    private fun isInsideArc(start: Double, end: Double, value: Double): Boolean =
        if (rotation == CCW) angleIsBetween(start, end, value) else angleIsBetween(end, start, value)
}

sealed class GeometrySpiral : SpiralContent, GeometryElement() {
    override val bounds: List<Point> by lazy {
        // Rotate the curved end's point so that the flat part starts with positive X and curves
        // up/down
        // Define minimal bounding corners in this rotated coordinate system and then rotate them
        // back
        if (isSteepening) {
            val rotatedEnd = rotateAroundPoint(start, -startDirectionRads, end)
            val rotatedCorners = boundingBoxAroundPoints(start, rotatedEnd).corners
            rotatedCorners.map { point -> rotateAroundPoint(start, startDirectionRads, point) }
        } else {
            val rotatedStart = rotateAroundPoint(end, -endDirectionRads, start)
            val rotatedCorners = boundingBoxAroundPoints(end, rotatedStart).corners
            rotatedCorners.map { point -> rotateAroundPoint(end, endDirectionRads, point) }
        }
    }
    override val startDirectionRads: Double by lazy { directionBetweenPoints(start, pi) }
    override val endDirectionRads: Double by lazy { directionBetweenPoints(pi, end) }

    /**
     * We calculate everything from the first segment point along the spiral. This tells us if that's the start-point
     * (steepening) or the end-point (flattening)
     */
    protected val isSteepening: Boolean by lazy {
        (radiusStart?.toDouble() ?: Double.POSITIVE_INFINITY) >= (radiusEnd?.toDouble() ?: Double.POSITIVE_INFINITY)
    }

    /**
     * Ideal spiral turns counter-clockwise from spiral origin (goes along positive X, turning towards positive Y) This
     * is the wrong direction if actual spiral is (steepening & clockwise) OR (flattening & counter-clockwise)
     */
    protected val turnsClockwise: Boolean by lazy { (rotation == CW) == isSteepening }

    // In math, we always assume to start from straight line and go "into" the spiral.
    // If we're going the other direction, we have to treat the end as the start-point and vice
    // versa.
    protected val segmentStart: Point by lazy { if (isSteepening) start else end }
    protected val segmentStartRadius: Double? by lazy {
        if (isSteepening) radiusStart?.toDouble() else radiusEnd?.toDouble()
    }
    protected val segmentStartAngle: Double by lazy { if (isSteepening) startDirectionRads else endDirectionRads - PI }
}

data class GeometryClothoid(
    private val elementData: ElementData,
    private val spiralData: SpiralData,
    private val switchData: SwitchData,
    /** Clothoid flatness constant A = sqrt(R*L) * */
    val constant: BigDecimal,
    override val id: DomainId<GeometryElement> = StringId(),
) : ElementContent by elementData, SpiralContent by spiralData, SwitchContent by switchData, GeometrySpiral() {

    init {
        if (radiusStart == null && radiusEnd == null) {
            throw IllegalArgumentException("Clothoid cannot be defined without a start- or end-radius.")
        }
    }

    override val type: GeometryElementType = CLOTHOID

    override val calculatedLength: Double by lazy {
        clothoidLength(constant.toDouble(), radiusStart?.toDouble(), radiusEnd?.toDouble())
    }

    override fun getCoordinateAt(distance: Double): Point {
        // Calculate the offset for the ideal spiral, then rotate and translate to actual values
        var offset = clothoidPointAtOffset(constant.toDouble(), segmentToClothoidDistance(distance))
        if (turnsClockwise) {
            offset = Point(offset.x, -1 * offset.y)
        }
        offset = rotateAroundOrigin(clothoidAngle, offset)
        return clothoidOrigin + offset
    }

    override fun contentEquals(other: GeometryElement): Boolean {
        return other is GeometryClothoid &&
            other.elementData == elementData &&
            other.spiralData == spiralData &&
            other.constant == constant &&
            switchData.contentEquals(other.switchData)
    }

    fun segmentToClothoidDistance(segmentDistance: Double): Double {
        // If our spiral is flattening, calculate the point going backwards from the end
        return clothoidSegmentOffset + if (isSteepening) segmentDistance else calculatedLength - segmentDistance
    }

    /**
     * Spiral origin is the point where the spiral becomes straight (R=INF)
     *
     * This might be the segment start (steepening) or end (flattening), but can also be "before" or "after" the segment
     * itself, as the segment can be taken from farther in the spiral (a spiral between 2 curves has some curvature on
     * both ends).
     */
    private val clothoidOrigin: Point by lazy {
        when {
            radiusStart == null -> start
            radiusEnd == null -> end
            else -> {
                val idealOffset = clothoidPointAtOffset(constant.toDouble(), clothoidSegmentOffset)
                val flipped = if (turnsClockwise) Point(idealOffset.x, -1 * idealOffset.y) else idealOffset
                val rotatedOffset = rotateAroundOrigin(clothoidAngle, flipped)
                segmentStart - rotatedOffset
            }
        }
    }

    /** The angle (radians) that the entire clothoid is turned (around the origin) from the X-axis */
    private val clothoidAngle: Double by lazy {
        val spiralTwist = clothoidTwistAtLength(segmentStartRadius, clothoidSegmentOffset)
        val sign = if (turnsClockwise) 1 else -1
        segmentStartAngle + sign * spiralTwist
    }

    /**
     * Distance between the clothoid origin and the closest point of segment (start or end, depending on direction).
     * Offset = 0, if the spiral begins or ends at a straight line. Offset > 0, if the segment is from farther in the
     * spiral, having curvature in both ends.
     */
    private val clothoidSegmentOffset: Double by lazy {
        clothoidLengthAtRadius(constant.toDouble(), segmentStartRadius)
    }

    data class EstimationSegment(
        val startLength: Double,
        val endLength: Double,
        val start: Point,
        val end: Point,
        val length: Double = endLength - startLength,
    )

    @get:JsonIgnore
    val estimationSegments by lazy {
        val lengthRanges = splitLengthToMaxTwist(0.0..calculatedLength, PI / 32)
        lengthRanges.map { range ->
            EstimationSegment(
                range.start,
                range.endInclusive,
                getCoordinateAt(range.start),
                getCoordinateAt(range.endInclusive),
            )
        }
    }

    fun directionAt(segmentDistance: Double): Double {
        val clothoidDistance = segmentToClothoidDistance(segmentDistance)
        val clothoidRadiusAtDistance = clothoidRadiusAtLength(constant.toDouble(), clothoidDistance)
        val spiralTwist = clothoidTwistAtLength(clothoidRadiusAtDistance, clothoidDistance)
        val sign = if (turnsClockwise) 1 else -1
        return segmentStartAngle + sign * spiralTwist
    }

    private fun splitLengthToMaxTwist(range: ClosedRange<Double>, maxTwistRads: Double): List<ClosedRange<Double>> {
        val angleDiff = angleDiffRads(directionAt(range.start), directionAt(range.endInclusive))
        return if (angleDiff < maxTwistRads) {
            listOf(range)
        } else {
            val halfPoint = (range.start + range.endInclusive) / 2
            splitLengthToMaxTwist(range.start..halfPoint, maxTwistRads) +
                splitLengthToMaxTwist(halfPoint..range.endInclusive, maxTwistRads)
        }
    }

    override fun getLengthUntil(target: Point): Double {
        for (segment in estimationSegments) {
            val closest = closestPointOnLine(segment.start, segment.end, target)
            // If it's off from the end-side, move on to check the next segment. Otherwise, this is
            // as close as it gets.
            if (closest != segment.end) {
                val distancePortion = lineLength(segment.start, closest) / lineLength(segment.start, segment.end)
                return min(calculatedLength, segment.startLength + segment.length * distancePortion)
            }
        }
        return calculatedLength
    }
}

data class BiquadraticParabola(
    private val elementData: ElementData,
    private val spiralData: SpiralData,
    private val switchData: SwitchData,
    override val id: DomainId<GeometryElement> = StringId(),
) : ElementContent by elementData, SpiralContent by spiralData, SwitchContent by switchData, GeometrySpiral() {

    init {
        if (radiusStart == null && radiusEnd == null) {
            throw IllegalArgumentException("Biquadratic parabola cannot be defined without a start- or end-radius.")
        }
        if (radiusStart != null && radiusEnd != null) {
            throw IllegalArgumentException(
                "Biquadratic parabola is not supported between curves (only on of start- and end-radius can be given)."
            )
        }
    }

    override val type: GeometryElementType = BIQUADRATIC_PARABOLA

    override val calculatedLength: Double by lazy {
        // In bi-quadratic parabola, length is what defines the shape -> cannot be calculated.
        length.toDouble()
    }

    override fun getCoordinateAt(distance: Double): Point {
        val spiralDistance = if (isSteepening) distance else length.toDouble() - distance
        var offset = biquadraticParabolaPointAtOffset(spiralDistance, spiralRadiusChange, length.toDouble())
        if (turnsClockwise) {
            offset = Point(offset.x, -1 * offset.y)
        }
        offset = rotateAroundOrigin(segmentStartAngle, offset)
        return segmentStart + offset
    }

    override fun contentEquals(other: GeometryElement): Boolean {
        return other is BiquadraticParabola &&
            other.elementData == elementData &&
            other.spiralData == spiralData &&
            switchData.contentEquals(other.switchData)
    }

    private val spiralRadiusChange: Double by lazy { radiusStart?.toDouble() ?: (radiusEnd?.toDouble() ?: 0.0) }

    // Calculate parabola spiral as if it were a line:
    // this is inaccurate, but only as much as the official length-to-point formula
    // For more, see biquadraticParabolaPointAtOffset() and X-axis calculation
    override fun getLengthUntil(target: Point): Double = lineLength(start, closestPointOnLine(start, end, target))
}
