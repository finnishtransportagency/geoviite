package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point

fun collectAnglePoints(alignments: List<GeometryAlignment>): List<Point> =
    alignments.flatMap { a: GeometryAlignment -> a.elements.flatMap { e -> e.bounds } }

fun getBoundingPolygonFromPlan(plan: GeometryPlan, transformation: Transformation): List<Point> =
    tryCreateBoundingPolygonPoints(
        collectAnglePoints(plan.alignments) + plan.kmPosts.mapNotNull { it.location },
        transformation,
    )

fun getBoundingPolygonPointsFromAlignments(
    alignments: List<GeometryAlignment>,
    transformation: Transformation,
): List<Point> = tryCreateBoundingPolygonPoints(collectAnglePoints(alignments), transformation)

fun tryCreateBoundingPolygonPoints(anglePoints: List<IPoint>, transformation: Transformation): List<Point> =
    try {
        createBoundingPolygonPoints(anglePoints, transformation)
    } catch (e: Exception) {
        listOf()
    }

fun createBoundingPolygonPoints(points: List<IPoint>, transformation: Transformation): List<Point> {
    val bounds = boundingPolygonPointsByConvexHull(points, transformation.sourceSrid)
    return bounds.map { p -> transformation.transform(p) }
}
