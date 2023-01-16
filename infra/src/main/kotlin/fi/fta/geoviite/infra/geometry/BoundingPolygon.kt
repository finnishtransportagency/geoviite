package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.KKJtoETRSTriangle
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.geography.crs
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID

fun collectAnglePoints(alignments: List<GeometryAlignment>): List<Point> =
    alignments.flatMap { a: GeometryAlignment -> a.elements.flatMap { e -> e.bounds } }

fun tryCreateBoundingPolygonPoints(
    anglePoints: List<IPoint>,
    transformation: Transformation,
): List<Point> {
    return try {
        createBoundingPolygonPoints(anglePoints, transformation)
    } catch (e: Exception) {
        listOf()
    }
}

fun createBoundingPolygonPoints(
    points: List<IPoint>,
    transformation: Transformation
): List<Point> {
    val bounds = boundingPolygonPointsByConvexHull(points, transformation.sourceRef)
    return bounds.map { p -> transformation.transform(p) }
}
