package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.math.Point
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.locationtech.jts.geom.Coordinate

val YKJ_CRS = crs(KKJ3_YKJ_SRID)

data class KkjTm35finTriangle(
    val corner1: Point,
    val corner2: Point,
    val corner3: Point,
    val a1: Double,
    val a2: Double,
    val deltaE: Double,
    val b1: Double,
    val b2: Double,
    val deltaN: Double,
    private val crs: CoordinateReferenceSystem,
) {
    val polygon by lazy {
        toJtsPolygon(listOf(corner1, corner2, corner3, corner1), crs(KKJ3_YKJ_SRID)) // Last parameter just indicates which axis is which
            ?: throw IllegalStateException("Failed to create polygon")
    }

    fun intersects(point: org.locationtech.jts.geom.Point): Boolean =
        polygon.intersects(point)

    fun intersects(poly: org.locationtech.jts.geom.Polygon): Boolean =
        polygon.intersects(poly)
}

fun transformPointInTriangle(point: org.locationtech.jts.geom.Point, targetCrs: CoordinateReferenceSystem, triangle: KkjTm35finTriangle): Point {
    val x = (triangle.a2 * point.y) + (triangle.a1 * point.x) + triangle.deltaE
    val y = (triangle.b2 * point.y) + (triangle.b1 * point.x) + triangle.deltaN
    return toPoint(Coordinate(x, y), targetCrs)
}
