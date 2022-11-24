package fi.fta.geoviite.infra.math

import com.fasterxml.jackson.annotation.JsonIgnore
import org.geotools.geometry.jts.GeometryBuilder
import org.springframework.core.convert.converter.Converter

private const val DEFAULT_BUFFER = 0.000001
private const val SEPARATOR = "_"

private val jtsBuilder = GeometryBuilder()

data class BoundingBox(val x: Range<Double>, val y: Range<Double>) {
    constructor(min: Point, max: Point) : this(Range(min.x, max.x), Range(min.y, max.y))

    constructor(
        xRange: ClosedFloatingPointRange<Double>,
        yRange: ClosedFloatingPointRange<Double>,
    ) : this(Range(xRange), Range(yRange))

    @get:JsonIgnore
    val width: Double by lazy { x.max - x.min }

    @get:JsonIgnore
    val height: Double by lazy { y.max - y.min }

    @get:JsonIgnore
    val min: Point by lazy { Point(x.min, y.min) }

    @get:JsonIgnore
    val max: Point by lazy { Point(x.max, y.max) }

    @get:JsonIgnore
    val corners: List<Point> by lazy {
        listOf(Point(x.min, y.min), Point(x.max, y.min), Point(x.max, y.max), Point(x.min, y.max))
    }

    @get:JsonIgnore
    val polygonFromCorners: List<Point> by lazy {
        listOf(Point(x.min, y.min), Point(x.max, y.min), Point(x.max, y.max), Point(x.min, y.max), Point(x.min, y.min))
    }

    @get:JsonIgnore
    val center: Point by lazy {
        Point((x.min + x.max) / 2, (y.min + y.max) / 2)
    }

    private val jtsBox by lazy { jtsBuilder.box(x.min, y.min, x.max, y.max) }

    fun contains(point: IPoint): Boolean {
        return x.contains(point.x) && y.contains(point.y)
    }

    fun intersects(other: BoundingBox?): Boolean {
        return other != null && this.x.overlaps(other.x) && this.y.overlaps(other.y)
    }

    fun intersects(polygonPoints: List<Point>): Boolean {
        return when (polygonPoints.size) {
            0 -> false
            1 -> contains(polygonPoints.first())
            2 -> jtsBox.intersects(jtsBuilder.lineString(*pointArray(polygonPoints)))
            else -> jtsBox.intersects(jtsBuilder.polygon(*pointArray(polygonPoints)))
        }
    }

    fun centerAt(point: IPoint): BoundingBox {
        val translation = point - center
        return BoundingBox(min + translation, max + translation)
    }

    private fun pointArray(points: List<Point>) = points.flatMap { p -> listOf(p.x, p.y) }.toDoubleArray()

    operator fun plus(increment: Double): BoundingBox {
        return BoundingBox(
            min - increment,
            max + increment
        )
    }

    operator fun times(ratio: Double): BoundingBox {
        val width = x.max - x.min
        val height = y.max - y.min
        val delta = Point(width * ratio - width, height * ratio - height)
        return BoundingBox(
            min - delta / 2.0,
            max + delta / 2.0
        )
    }
}

fun boundingBoxToString(value: BoundingBox) =
    "${value.x.min}$SEPARATOR${value.x.max}$SEPARATOR${value.y.min}$SEPARATOR${value.y.max}"

fun stringToBoundingBox(value: String): BoundingBox {
    val values = value.split(SEPARATOR).map(String::toDouble)
    if (values.size != 4) throw IllegalArgumentException("Invalid bounding box (expected 4 numbers): \"$value\"")
    return BoundingBox(values[0]..values[1], values[2]..values[3])
}

fun boundingBoxAroundPoint(point: IPoint, delta: Double) =
    BoundingBox(point.x-delta..point.x+delta, point.y-delta..point.y+delta)

fun boundingBoxAroundPoints(point1: Point, vararg rest: Point): BoundingBox =
    boundingBoxAroundPointsOrNull(listOf(point1) + rest) ?: throw IllegalStateException("Failed to create bounding box")

fun boundingBoxAroundPoints(points: List<Point>, buffer: Double = DEFAULT_BUFFER) =
    boundingBoxAroundPointsOrNull(points, buffer) ?: throw IllegalStateException("Failed to create bounding box")

fun boundingBoxAroundPointsOrNull(points: List<Point>, buffer: Double = DEFAULT_BUFFER) =
    if (points.isEmpty()) null
    else BoundingBox(minPoint(points) - Point(buffer, buffer), maxPoint(points) + Point(buffer, buffer))


fun boundingBoxCombining(boxes: List<BoundingBox>) =
    if (boxes.isEmpty()) null
    else BoundingBox(combine(boxes.map(BoundingBox::x)), combine(boxes.map(BoundingBox::y)))

class StringToBoundingBoxConverter : Converter<String, BoundingBox> {
    override fun convert(source: String): BoundingBox = stringToBoundingBox(source)
}

class BoundingBoxToStringConverter : Converter<BoundingBox, String> {
    override fun convert(source: BoundingBox): String = boundingBoxToString(source)
}
