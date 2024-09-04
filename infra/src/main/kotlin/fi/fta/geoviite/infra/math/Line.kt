package fi.fta.geoviite.infra.math

import kotlin.math.abs
import kotlin.math.hypot

fun polyLineLength(points: List<IPoint>): Double =
    points.foldRightIndexed(0.0) { index, point, acc ->
        if (index == 0) acc else acc + lineLength(points[index - 1], point)
    }

fun lineLength(p1: IPoint, p2: IPoint): Double {
    return hypot(p1.x - p2.x, p1.y - p2.y)
}

fun pointDistanceToLine(start: IPoint, end: IPoint, target: IPoint): Double {
    return lineLength(closestPointOnLine(start, end, target), target)
}

fun closestPointProportionOnLine(start: IPoint, end: IPoint, target: IPoint): Double {
    if (start.x == end.x && start.y == end.y) return 0.0
    else if (start.x == target.x && start.y == target.y) return 0.0
    else if (end.x == target.x && end.y == target.y) return 1.0

    val deltaX = end.x - start.x
    val deltaY = end.y - start.y

    return ((target.x - start.x) * deltaX + (target.y - start.y) * deltaY) / (deltaX * deltaX + deltaY * deltaY)
}

fun closestPointOnLine(start: IPoint, end: IPoint, target: IPoint): Point {
    val portion = closestPointProportionOnLine(start, end, target)
    return when {
        portion < 0 -> Point(start.x, start.y)
        portion > 1 -> Point(end.x, end.y)
        else -> interpolate(start, end, portion)
    }
}

/** Derived from: https://en.wikipedia.org/wiki/Line%E2%80%93line_intersection */
fun lineIntersection(start1: IPoint, end1: IPoint, start2: IPoint, end2: IPoint): Intersection? {
    val denominator = (start1.x - end1.x) * (start2.y - end2.y) - (start1.y - end1.y) * (start2.x - end2.x)

    // Parallel lines -> no intersection (unless coincident)
    if (denominator == 0.0) return null

    val numeratorT = (start1.x - start2.x) * (start2.y - end2.y) - (start1.y - start2.y) * (start2.x - end2.x)
    val numeratorU = (start1.x - start2.x) * (start1.y - end1.y) - (start1.y - start2.y) * (start1.x - end1.x)

    val t = numeratorT / denominator
    return Intersection(
        point = Point(x = start1.x + (end1.x - start1.x) * t, y = start1.y + (end1.y - start1.y) * t),
        segment1Portion = numeratorT / denominator,
        segment2Portion = numeratorU / denominator,
    )
}

enum class IntersectType {
    BEFORE,
    WITHIN,
    AFTER,
}

data class Intersection(val point: Point, val segment1Portion: Double, val segment2Portion: Double) {
    val inSegment1 = getIntersectType(segment1Portion)
    val inSegment2 = getIntersectType(segment2Portion)
    val relativeDistance2 = toRelativeDistance(segment2Portion)

    fun linesIntersect() = inSegment1 == IntersectType.WITHIN && inSegment2 == IntersectType.WITHIN
}

fun getIntersectType(portion: Double): IntersectType =
    when {
        portion < 0.0 -> IntersectType.BEFORE
        portion > 1.0 -> IntersectType.AFTER
        else -> IntersectType.WITHIN
    }

private fun toRelativeDistance(portion: Double): Double =
    when {
        portion < 0.0 -> abs(portion)
        portion > 1.0 -> portion - 1.0
        else -> 0.0
    }

fun linePointAtDistance(line: Line, distance: Double): Point = linePointAtDistance(line.start, line.end, distance)

fun linePointAtDistance(start: IPoint, end: IPoint, distance: Double): Point {
    val length = lineLength(start, end)
    if (length == 0.0) throw IllegalArgumentException("Cannot interpolate along 0-length line")
    val ratio = distance / length
    return start + (end - start) * ratio
}

fun lineYAtX(start: Point, end: Point, x: Double): Double {
    val slope = lineSlope(start, end)
    val constant = lineConstant(start, slope)
    return (slope * x) + constant
}

fun lineSlope(pointA: Point, pointB: Point): Double {
    val deltaY = pointB.y - pointA.y
    val deltaX = pointB.x - pointA.x
    if (deltaX == 0.0)
        throw IllegalArgumentException("Cannot calculate line slope without X delta: pointA=$pointA pointB=$pointB")
    return (deltaY / deltaX)
}

fun lineConstant(point: Point, slope: Double): Double {
    return (point.x * slope - point.y) * -1
}

data class Line(val start: IPoint, val end: IPoint) {
    val angle: Double by lazy { directionBetweenPoints(start, end) }
    val length by lazy { lineLength(start, end) }
}
