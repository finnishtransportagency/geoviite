package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.geography.create2DPolygonString
import fi.fta.geoviite.infra.geography.toJtsLineString
import fi.fta.geoviite.infra.geography.toJtsPoint
import fi.fta.geoviite.infra.geography.toJtsPolygon

const val MIN_POLYGON_POINTS = 4

data class Polygon(val points: List<Point>) {

    constructor(vararg points: Point) : this(points.toList())

    constructor(vararg points: IPoint) : this(points.map { Point(it) })

    init {
        require(points.size >= MIN_POLYGON_POINTS) {
            "A ${Polygon::class.simpleName} must have at least $MIN_POLYGON_POINTS points: points.size=${points.size}"
        }
        require(points.first() == points.last()) {
            "A ${Polygon::class.simpleName} must be closed: first=${points.first()} last=${points.last()}"
        }
    }

    fun toWkt(): String = create2DPolygonString(points)

    val boundingBox: BoundingBox
        get() =
            requireNotNull(boundingBoxAroundPointsOrNull(points)) {
                // Cannot actually happen, since polygon has at least 4 points
                "Failed to create bounding box for polygon: $this"
            }

    private val jtsPolygon by lazy { toJtsPolygon(points) }

    fun intersects(points: List<IPoint>): Boolean =
        when (points.size) {
            0 -> false
            1 -> jtsPolygon.intersects(toJtsPoint(points.first()))
            else -> jtsPolygon.intersects(toJtsLineString(points))
        }

    fun moveBy(vector: Point) = Polygon(points.map { it + vector })
}
