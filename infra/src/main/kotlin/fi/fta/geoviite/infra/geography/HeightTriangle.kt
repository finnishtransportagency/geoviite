package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_CRS

data class HeightTriangle(
    val corner1: Point,
    val corner2: Point,
    val corner3: Point,
    val corner1Diff: Double,
    val corner2Diff: Double,
    val corner3Diff: Double
) {
    private val polygon by lazy {
        toJtsPolygon(listOf(corner1, corner2, corner3, corner1), LAYOUT_CRS)
    }
    val corner1XZ: Point by lazy { Point(corner1.x, corner1Diff) }
    val corner2XZ: Point by lazy { Point(corner2.x, corner2Diff) }
    val corner3XZ: Point by lazy { Point(corner3.x, corner3Diff) }

    fun contains(point: IPoint) = polygon?.intersects(toJtsPoint(point, LAYOUT_CRS)) ?: false
}
