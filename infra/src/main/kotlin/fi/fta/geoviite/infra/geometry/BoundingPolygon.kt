package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangle
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID

fun collectAnglePoints(alignments: List<GeometryAlignment>): List<Point> =
    alignments.flatMap { a: GeometryAlignment -> a.elements.flatMap { e -> e.bounds } }

fun tryCreateBoundingPolygonPoints(
    srid: Srid,
    anglePoints: List<IPoint>,
    triangulationTriangles: List<KKJtoETRSTriangle>,
): List<Point> {
    return try {
        createBoundingPolygonPoints(srid, anglePoints, triangulationTriangles)
    } catch (e: Exception) {
        listOf()
    }
}

fun createBoundingPolygonPoints(
    srid: Srid,
    points: List<IPoint>,
    triangulationTriangles: List<KKJtoETRSTriangle>,
): List<Point> {
    val bounds = boundingPolygonPointsByConvexHull(points, srid)
    val transformation = Transformation(srid, LAYOUT_SRID, triangulationTriangles)
    return bounds.map { p -> transformation.transform(p) }
}
