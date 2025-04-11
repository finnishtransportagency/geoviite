package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineYAtX

fun interpolateHeightAtGivenPoint(heightTriangle: HeightTriangle, point: IPoint, originalPointHeight: Double): Double {
    val interpolationZ1 = lineYAtX(heightTriangle.corner1XZ, heightTriangle.corner2XZ, point.x)
    val interpolationPoint1 = Point(point.x, interpolationZ1)
    val interpolationZ = lineYAtX(interpolationPoint1, heightTriangle.corner3XZ, point.x)
    return originalPointHeight + interpolationZ
}

fun transformHeightValue(
    height: Double,
    point: IPoint,
    heightTriangles: List<HeightTriangle>,
    verticalCoordinateSystem: VerticalCoordinateSystem,
): Double {
    return when (verticalCoordinateSystem) {
        VerticalCoordinateSystem.N2000 -> height
        VerticalCoordinateSystem.N60 -> {
            val triangle = findHeightTriangleContainingPoint(heightTriangles, point)
            if (triangle != null) interpolateHeightAtGivenPoint(triangle, point, height)
            else {
                fi.fta.geoviite.infra.inframodel.logger.error("triangles=$heightTriangles point=$point")
                throw IllegalArgumentException("Could not transform height N60 -> N2000. Point not in any triangle.")
            }
        }
        VerticalCoordinateSystem.N43 -> throw IllegalArgumentException("N43 -> N2000 transformation not supported")
    }
}
