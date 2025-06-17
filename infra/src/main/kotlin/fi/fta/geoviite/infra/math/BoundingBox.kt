package fi.fta.geoviite.infra.math

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.geography.toJtsBox
import fi.fta.geoviite.infra.geography.toJtsLineString
import fi.fta.geoviite.infra.geography.toJtsPolygon
import kotlin.math.hypot

private const val DEFAULT_BUFFER = 0.000001
private const val SEPARATOR = "_"

data class BoundingBox(val x: Range<Double>, val y: Range<Double>) {
    constructor(ranges: Pair<Range<Double>, Range<Double>>) : this(ranges.first, ranges.second)

    constructor(min: Point, max: Point) : this(Range(min.x, max.x), Range(min.y, max.y))

    constructor(value: String) : this(parseRanges(value))

    constructor(
        xRange: ClosedFloatingPointRange<Double>,
        yRange: ClosedFloatingPointRange<Double>,
    ) : this(Range(xRange), Range(yRange))

    override fun toString() = "${x.min}$SEPARATOR${x.max}$SEPARATOR${y.min}$SEPARATOR${y.max}"

    @get:JsonIgnore val width: Double by lazy { x.max - x.min }

    @get:JsonIgnore val height: Double by lazy { y.max - y.min }

    @get:JsonIgnore val min: Point by lazy { Point(x.min, y.min) }

    @get:JsonIgnore val max: Point by lazy { Point(x.max, y.max) }

    @get:JsonIgnore
    val corners: List<Point> by lazy {
        listOf(Point(x.min, y.min), Point(x.max, y.min), Point(x.max, y.max), Point(x.min, y.max))
    }

    @get:JsonIgnore
    val polygonFromCorners: Polygon by lazy {
        Polygon(Point(x.min, y.min), Point(x.max, y.min), Point(x.max, y.max), Point(x.min, y.max), Point(x.min, y.min))
    }

    @get:JsonIgnore val center: Point by lazy { Point((x.min + x.max) / 2, (y.min + y.max) / 2) }

    private val jtsBox by lazy { toJtsBox(x, y) }

    fun contains(point: IPoint): Boolean {
        return x.contains(point.x) && y.contains(point.y)
    }

    fun contains(other: BoundingBox): Boolean {
        return x.contains(other.x) && y.contains(other.y)
    }

    fun intersects(other: BoundingBox?): Boolean {
        return other != null && this.x.overlaps(other.x) && this.y.overlaps(other.y)
    }

    /** Least distance between any pair of points in the boxes, whether inside or in the perimeter */
    // https://gist.github.com/dGr8LookinSparky/bd64a9f5f9deecf61e2c3c1592169c00
    fun minimumDistance(other: BoundingBox): Double {
        return hypot(minimumDistance(x, other.x), minimumDistance(y, other.y))
    }

    fun intersects(polygonPoints: List<Point>): Boolean {
        return when (polygonPoints.size) {
            0 -> false
            1 -> contains(polygonPoints.first())
            2 -> jtsBox.intersects(toJtsLineString(polygonPoints))
            else -> jtsBox.intersects(toJtsPolygon(polygonPoints))
        }
    }

    fun centerAt(point: IPoint): BoundingBox {
        val translation = point - center
        return BoundingBox(min + translation, max + translation)
    }

    operator fun plus(increment: Double): BoundingBox {
        return BoundingBox(min - increment, max + increment)
    }

    operator fun times(ratio: Double): BoundingBox {
        val width = x.max - x.min
        val height = y.max - y.min
        val delta = Point(width * ratio - width, height * ratio - height)
        return BoundingBox(min - delta / 2.0, max + delta / 2.0)
    }
}

fun parseRanges(value: String): Pair<Range<Double>, Range<Double>> {
    val values = value.split(SEPARATOR).map(String::toDouble)
    if (values.size != 4) throw IllegalArgumentException("Invalid bounding box (expected 4 numbers): \"$value\"")
    return Range(values[0]..values[1]) to Range(values[2]..values[3])
}

fun boundingBoxAroundPoint(point: IPoint, delta: Double) =
    BoundingBox(point.x - delta..point.x + delta, point.y - delta..point.y + delta)

fun boundingBoxAroundPoints(point1: Point, vararg rest: Point): BoundingBox =
    boundingBoxAroundPointsOrNull(listOf(point1) + rest) ?: throw IllegalStateException("Failed to create bounding box")

fun boundingBoxAroundPoints(points: List<IPoint>, buffer: Double = DEFAULT_BUFFER) =
    boundingBoxAroundPointsOrNull(points, buffer) ?: throw IllegalStateException("Failed to create bounding box")

fun <T : IPoint> boundingBoxAroundPointsOrNull(points: List<T>, buffer: Double = DEFAULT_BUFFER) =
    if (points.isEmpty()) null
    else BoundingBox(minPoint(points) - Point(buffer, buffer), maxPoint(points) + Point(buffer, buffer))

fun boundingBoxCombining(boxes: List<BoundingBox>) =
    if (boxes.isEmpty()) null else BoundingBox(combine(boxes.map(BoundingBox::x)), combine(boxes.map(BoundingBox::y)))
