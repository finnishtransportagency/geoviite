package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.geography.create2DPolygonString

val MIN_POLYGON_POINTS = 4

data class Polygon(val points: List<Point>) {
    constructor(vararg points: Point) : this(points.toList())

    constructor(vararg points: IPoint) : this(points.map { Point(it) })

    init {
        require(points.size >= MIN_POLYGON_POINTS) {
            "A ${Polygon::class.simpleName} must have at least $MIN_POLYGON_POINTS points: points.size=${points.size}"
        }
        require(points.first() == points.last()) {
            "A ${Polygon::class.simpleName} must be closed: first=${points.first()}, last=${points.last()}"
        }
    }

    fun toWkt(): String = create2DPolygonString(points)

    val boundingBox: BoundingBox
        get() =
            boundingBoxAroundPointsOrNull(points)
                ?: throw IllegalStateException("Failed to create bounding box for polygon: $this")
}
