package fi.fta.geoviite.infra.math

import java.math.BigDecimal
import kotlin.math.*


/**
 * Returns the point that lies in the given direction (radians) at given distance from the given start-point
 */
fun pointInDirection(fromPoint: IPoint, distance: Double, direction: Double): Point {
    return fromPoint + pointInDirection(distance, direction)
}

/**
 * Returns the point that lies in the given direction (radians) at given distance from origin
 */
fun pointInDirection(distance: Double, direction: Double): Point {
    return Point(distance * cos(direction), distance * sin(direction))
}

/**
 * Returns the direction angle (radians, 0=direction of positive x, increasing counter-clockwise) between the given points
 */
fun directionBetweenPoints(fromPoint: IPoint, toPoint: IPoint): Double {
    return directionTowardsPoint(toPoint - fromPoint)
}

/**
 * Returns the direction angle (radians, 0=direction of positive x, increasing counter-clockwise) from origin to the given point
 */
fun directionTowardsPoint(point: IPoint): Double {
    return atan2(point.y, point.x)
}

/**
 * Calculates the point received by rotating the given point around the reference point, with the given delta angle (radians)
 */
fun rotateAroundPoint(referencePoint: IPoint, deltaRad: Double, point: IPoint): Point {
    return rotateAroundOrigin(deltaRad, point - referencePoint) + referencePoint
}

/**
 * Calculates the point received by rotating the given point around origin, with the given delta angle (radians)
 */
fun rotateAroundOrigin(deltaRad: Double, point: IPoint): Point {
    val sin = sin(deltaRad)
    val cos = cos(deltaRad)
    return Point(cos * point.x - sin * point.y, sin * point.x + cos * point.y)
}

interface IPoint {
    val x: Double
    val y: Double

    fun magnitude() = hypot(x, y)

    fun normalized(): IPoint {
        return this / magnitude()
    }

    /**
     * Non-exact coordinate equality check, allowing for small deviations in the value and still considers the point same.
     */
    fun isSame(other: IPoint, delta: Double): Boolean {
        return isSame(x, other.x, delta) && isSame(y, other.y, delta)
    }

    operator fun minus(other: IPoint): Point {
        return Point(x - other.x, y - other.y)
    }

    operator fun minus(value: Double): Point {
        return Point(x - value, y - value)
    }

    operator fun plus(other: IPoint): Point {
        return Point(x + other.x, y + other.y)
    }

    operator fun plus(value: Double): Point {
        return Point(x + value, y + value)
    }

    operator fun times(value: Double): Point {
        return Point(x * value, y * value)
    }

    operator fun times(value: IPoint): Point {
        return Point(x * value.x, y * value.y)
    }

    operator fun div(value: Double): Point {
        return Point(x / value, y / value)
    }

    fun toPoint() = Point(this)

    fun round(scale: Int) = RoundedPoint(x, y, scale)
}

data class Point(override val x: Double, override val y: Double) : IPoint {
    constructor(values: Pair<Double, Double>) : this(values.first, values.second)
    constructor(value: String) : this(parsePointPair(value))

    init {
        require(x.isFinite() && y.isFinite()) { "Cannot create coordinate of: x=$x y=$y" }
    }

    constructor(
        otherPoint: IPoint,
    ) : this(otherPoint.x, otherPoint.y)

    companion object {
        fun zero(): Point = Point(0.0, 0.0)
    }
}

data class RoundedPoint(val roundedX: BigDecimal, val roundedY: BigDecimal): IPoint {
    override val x: Double get() = roundedX.toDouble()
    override val y: Double get() = roundedY.toDouble()

    constructor(x: Double, y: Double, scale: Int): this(round(x, scale), round(y, scale))
    init {
        require(roundedX.scale() == roundedY.scale()) {
            "Point X & Y must be rounded to the same scale: x=$roundedX y=$roundedY"
        }
    }
}

fun minPoint(points: List<Point>): Point =
    points.reduceRight { acc, point -> Point(min(acc.x, point.x), min(acc.y, point.y)) }

fun maxPoint(points: List<Point>): Point =
    points.reduceRight { acc, point -> Point(max(acc.x, point.x), max(acc.y, point.y)) }

fun parsePointPair(value: String): Pair<Double, Double> {
    val values = value.split("_").map(String::toDouble)
    if (values.size != 2) throw IllegalArgumentException("Invalid point (expected 2 numbers): \"$value\"")
    return values[0] to values[1]
}

interface IPoint3DM : IPoint {
    val m: Double
}

data class Point3DM(override val x: Double, override val y: Double, override val m: Double) : IPoint3DM {
    init {
        require(x.isFinite() && y.isFinite() && m.isFinite()) { "Cannot create point of: x=$x y=$y m=$m" }
    }
}

interface IPoint3DZ : IPoint {
    val z: Double
}

data class Point3DZ(override val x: Double, override val y: Double, override val z: Double) : IPoint3DZ {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite()) { "Cannot create point of: x=$x y=$y z=$z" }
    }
}

interface IPoint4DZM : IPoint, IPoint3DZ, IPoint3DM
data class Point4DZM(
    override val x: Double,
    override val y: Double,
    override val z: Double,
    override val m: Double,
) : IPoint4DZM {
    init {
        require(x.isFinite() && y.isFinite() && z.isFinite() && m.isFinite()) {
            "Cannot create point of: x=$x y=$y z=$z m=$m"
        }
    }
}
