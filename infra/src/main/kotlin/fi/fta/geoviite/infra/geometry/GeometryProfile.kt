package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import java.math.BigDecimal
import kotlin.math.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val LOG: Logger = LoggerFactory.getLogger(GeometryProfile::class.java)

sealed class VerticalIntersection {
    abstract val description: PlanElementName
    abstract val point: Point
}

data class VIPoint(override val description: PlanElementName, override val point: Point) : VerticalIntersection()

data class VICircularCurve(
    override val description: PlanElementName,
    override val point: Point,
    val radius: BigDecimal?,
    val length: BigDecimal?,
) : VerticalIntersection() {
    init {
        require(radius?.compareTo(BigDecimal.ZERO) != 0) { "Circular curve cannot have zero radius" }
    }
}

data class GeometryProfile(val name: PlanElementName, val elements: List<VerticalIntersection>) {
    @get:JsonIgnore val segments: List<ProfileSegment> by lazy { createSegments(elements) }

    fun getHeightAt(distance: LineM<PlanLayoutAlignmentM>): Double? {
        return when {
            segments.isEmpty() -> null
            distance.distance <= elements.first().point.x -> elements.first().point.y
            distance.distance >= elements.last().point.x -> elements.last().point.y
            else -> {
                val segment =
                    segments.find { s -> s.contains(distance.distance) }
                        ?: throw IllegalArgumentException(
                            "Requested point outside profile segments: " +
                                "$distance <> " +
                                "[${elements.first().point.x} to ${elements.last().point.x}] => " +
                                "${segments.map { s -> "${s.start.x}-${s.end.x}" }}"
                        )
                segment.getYValueAt(distance.distance)
            }
        }
    }
}

private fun createSegments(elements: List<VerticalIntersection>): List<ProfileSegment> {
    return when {
        elements.size < 2 -> listOf()
        elements.size == 2 ->
            listOfNotNull(
                createLinearIfNeeded(elements.last().description, elements.first().point, elements.last().point, true)
            )
        else -> {
            val segments: MutableList<ProfileSegment> = mutableListOf()
            var segmentStart: Point = elements.first().point
            var startValid = true
            (1..elements.lastIndex).forEach { index ->
                val element = elements[index]
                val nextElement = elements.getOrNull(index + 1)
                val calculationResult = createProfileSegments(segmentStart, startValid, element, nextElement)
                segments.addAll(calculationResult.segments)
                segmentStart = calculationResult.end
                startValid = calculationResult.endValid
            }
            segments
        }
    }
}

private data class ProfileCalculationResult(val end: Point, val endValid: Boolean, val segments: List<ProfileSegment>)

private fun createProfileSegments(
    start: Point,
    startValid: Boolean,
    intersection: VerticalIntersection,
    nextIntersection: VerticalIntersection?,
): ProfileCalculationResult {
    // If elements are not growing, skip ahead (validation error, but don't fail handling).
    if (intersection.point.x <= start.x) {
        return ProfileCalculationResult(intersection.point, false, listOf())
    }
    when (intersection) {
        is VIPoint -> {
            val segment = createLinearIfNeeded(intersection.description, start, intersection.point, startValid)
            return ProfileCalculationResult(intersection.point, true, listOfNotNull(segment))
        }
        is VICircularCurve -> {
            if (
                intersection.radius != null &&
                    nextIntersection != null &&
                    intersection.length?.compareTo(BigDecimal.ZERO) != 0
            ) {
                try {
                    val radius =
                        abs(intersection.radius.toDouble()) * getRadiusSign(start, intersection, nextIntersection)
                    val tangents = tangentPointsOfPvi(start, intersection.point, nextIntersection.point, radius)
                    // Linear segment connecting previous point to first curve tangent
                    val connectSegment =
                        createLinearIfNeeded(intersection.description, start, tangents.first, startValid)
                    // The actual curve segment
                    val center = circularCurveCenterPoint(radius, tangents.first, intersection.point)
                    val curvedSegment =
                        if (tangents.first.x >= tangents.second.x) null
                        else
                            CurvedProfileSegment(
                                intersection.description,
                                tangents.first,
                                tangents.second,
                                center,
                                radius,
                            )
                    return ProfileCalculationResult(tangents.second, true, listOfNotNull(connectSegment, curvedSegment))
                } catch (e: IllegalArgumentException) {
                    LOG.warn("Profile curve calculation failed: element=$intersection cause=${e.message}")
                }
            }

            // Faulty curve params -> can't calculate the curve
            val segment = createLinearIfNeeded(intersection.description, start, intersection.point, false)
            return ProfileCalculationResult(intersection.point, false, listOfNotNull(segment))
        }
    }
}

private fun getRadiusSign(
    start: Point,
    intersection: VerticalIntersection,
    nextIntersection: VerticalIntersection,
): Double {
    val leftRun = intersection.point.x - start.x
    val rightRun = nextIntersection.point.x - intersection.point.x
    val leftRise = intersection.point.y - start.y
    val rightRise = nextIntersection.point.y - intersection.point.y

    // segment lengths certainly can't be negative, but maybe they might be very short; so we cross-multiply
    // (leftRise/leftRun) < (rightRise/rightRun) over the less-than sign to avoid division by zero
    return if (leftRise * rightRun < rightRise * leftRun) 1.0 else -1.0
}

private fun createLinearIfNeeded(viName: PlanElementName, start: Point, end: Point, valid: Boolean) =
    if (start.x >= end.x) null else LinearProfileSegment(viName, start, end, valid)

sealed class ProfileSegment {
    abstract val viName: PlanElementName

    abstract val start: Point
    abstract val end: Point

    abstract val startAngle: Double
    abstract val endAngle: Double

    fun contains(x: Double) = x in start.x..end.x

    abstract fun getYValueAt(x: Double): Double?
}

data class LinearProfileSegment(
    override val viName: PlanElementName,
    override val start: Point,
    override val end: Point,
    val valid: Boolean,
    override val startAngle: Double = directionBetweenPoints(start, end),
    override val endAngle: Double = startAngle,
) : ProfileSegment() {
    init {
        require(start.x <= end.x) { "Linear profile segment x must be growing: start=${start.x} end=${end.x}" }
    }

    override fun getYValueAt(x: Double) = if (valid) lineYAtX(start, end, x) else null
}

data class CurvedProfileSegment(
    override val viName: PlanElementName,
    override val start: Point,
    override val end: Point,
    val center: Point,
    val radius: Double,
    override val startAngle: Double = directionBetweenPoints(center, start) + (sign(center.y) * PI / 2),
    override val endAngle: Double = directionBetweenPoints(center, end) + (sign(center.y) * PI / 2),
) : ProfileSegment() {
    init {
        require(start.x <= end.x) { "Curved profile segment x must be growing: start=${start.x} end=${end.x}" }
    }

    override fun getYValueAt(x: Double) = circleArcYAtX(center, radius, x)
}

fun circularCurveCenterPoint(radius: Double, leftTangentPoint: Point, pviPoint: Point): Point {
    val angleFromTangentToPvi = directionBetweenPoints(pviPoint, leftTangentPoint)
    val angleFromTangentToCenter = angleFromTangentToPvi - (Math.PI / 2)
    return pointInDirection(leftTangentPoint, radius, angleFromTangentToCenter)
}

fun tangentPointsOfPvi(leftPvi: Point, middlePvi: Point, rightPvi: Point, radius: Double): Pair<Point, Point> {
    require(leftPvi.x < middlePvi.x) {
        "Profile curve preceding X must be before middle point X: leftPvi=$leftPvi middlePvi=$middlePvi"
    }
    require(middlePvi.x < rightPvi.x) {
        "Profile curve following X must be after middle point X: middlePvi=$middlePvi rightPvi=$rightPvi"
    }

    val lengthFromPviToTangent = lengthFromPviToTangent(leftPvi, middlePvi, rightPvi, radius)

    val leftTangentPoint = linePointAtDistance(middlePvi, leftPvi, lengthFromPviToTangent)
    val rightTangentPoint = linePointAtDistance(middlePvi, rightPvi, lengthFromPviToTangent)
    return Pair(leftTangentPoint, rightTangentPoint)
}

fun lengthFromPviToTangent(leftPvi: Point, middlePvi: Point, rightPvi: Point, radius: Double): Double {
    val leftHalfAngle =
        halfAngleOfPviPoint(
            deltaX = middlePvi.x - leftPvi.x,
            deltaY = if (radius < 0.0) middlePvi.y - leftPvi.y else leftPvi.y - middlePvi.y,
        )
    val rightHalfAngle =
        halfAngleOfPviPoint(
            deltaX = rightPvi.x - middlePvi.x,
            deltaY = if (radius < 0.0) middlePvi.y - rightPvi.y else rightPvi.y - middlePvi.y,
        )
    return lengthFromPviToTangent(leftHalfAngle, rightHalfAngle, radius).also { length ->
        require(length > 0.0) { "PVI - tangent length should be positive: length=$length" }
    }
}

fun halfAngleOfPviPoint(deltaX: Double, deltaY: Double): Double =
    checkHalfAngle(
        if (deltaY == 0.0) PI / 2.0
        else if (deltaY > 0.0) atan(deltaX / deltaY) else PI / 2.0 + abs(atan(deltaY / deltaX))
    )

fun checkHalfAngle(halfAngle: Double) =
    halfAngle.also { angle ->
        require(angle > 0.0) { "VI half-angle component should be positive: angle=$angle" }
        require(angle < PI) { "VI half-angle component should under PI: angle=$angle" }
    }

fun lengthFromPviToTangent(leftHalfAngle: Double, rightHalfAngle: Double, radius: Double): Double {
    val halfAngleOfAlfa = (leftHalfAngle + rightHalfAngle) / 2
    require(halfAngleOfAlfa > 0.0) { "VI angle should be positive: left=$leftHalfAngle right=$rightHalfAngle" }
    require(halfAngleOfAlfa < PI / 2) {
        "VI angle should not exceed PI (is the radius sign correct?): " +
            "left=$leftHalfAngle right=$rightHalfAngle radius=$radius"
    }
    return abs(radius) / tan(halfAngleOfAlfa)
}
