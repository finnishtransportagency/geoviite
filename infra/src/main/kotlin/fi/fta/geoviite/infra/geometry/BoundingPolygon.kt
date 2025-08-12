package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.MIN_POLYGON_POINTS
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon

fun collectAnglePoints(alignments: List<GeometryAlignment>): List<Point> =
    alignments.flatMap { a: GeometryAlignment -> a.elements.flatMap { e -> e.bounds } }

fun getBoundingPolygonFromPlan(plan: GeometryPlan, transformation: Transformation): Polygon? =
    tryCreateBoundingPolygon(
        collectAnglePoints(plan.alignments) + plan.kmPosts.mapNotNull { it.location },
        transformation,
    )

fun getBoundingPolygonPointsFromAlignments(
    alignments: List<GeometryAlignment>,
    transformation: Transformation,
): Polygon? = tryCreateBoundingPolygon(collectAnglePoints(alignments), transformation)

private fun tryCreateBoundingPolygon(anglePoints: List<IPoint>, transformation: Transformation): Polygon? =
    try {
        boundingPolygonPointsByConvexHull(anglePoints, transformation.sourceSrid)
            .map(transformation::transform)
            .takeIf { p -> p.size >= MIN_POLYGON_POINTS }
            ?.let(::Polygon)
    } catch (e: Exception) {
        null
    }
