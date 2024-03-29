package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.Point
import org.geotools.api.referencing.crs.CoordinateReferenceSystem
import org.locationtech.jts.geom.Coordinate

val KKJ0 = Srid(3386)
val KKJ1 = Srid(2391)
val KKJ2 = Srid(2392)
val KKJ3_YKJ = Srid(2393)
val KKJ4 = Srid(2394)
val KKJ5 = Srid(3387)
val YKJ_CRS = crs(KKJ3_YKJ)

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
        toJtsPolygon(listOf(corner1, corner2, corner3, corner1), crs(KKJ3_YKJ)) // Last parameter just indicates which axis is which
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
