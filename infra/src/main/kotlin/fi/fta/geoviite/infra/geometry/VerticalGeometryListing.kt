package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.util.FileName
import kotlin.math.tan

data class CurvedSectionEndpoint(
    val address: TrackMeter?,
    val height: Double,
    val angle: Double?,
    val station: Double,
)

data class IntersectionPoint(
    val address: TrackMeter?,
    val height: Double?,
    val station: Double?,
)

data class LinearSection(
    val length: Double?,
    val linearSection: Double?,
)

data class VerticalGeometryListing(
    val id: StringId<ElementListing>,
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,

    val alignmentId: DomainId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,

    val locationTrackName: AlignmentName?,
    val start: CurvedSectionEndpoint,
    val end: CurvedSectionEndpoint,
    val point: IntersectionPoint,
    val radius: Double,
    val tangent: Double?,
    val linearSectionForward: LinearSection,
    val linearSectionBackward: LinearSection,
)

fun toVerticalGeometryListing(
    segment: CurvedProfileSegment,
    alignment: GeometryAlignment,
    coordinateTransform: Transformation?,
    planHeader: GeometryPlanHeader,
    geocodingContext: GeocodingContext?,
    previousCurvedSegment: CurvedProfileSegment?,
    previousLinearSegment: LinearProfileSegment?,
    nextCurvedSegment: CurvedProfileSegment?,
    nextLinearSegment: LinearProfileSegment?
): VerticalGeometryListing {
    val stationPoint = circCurveStationPoint(segment)
    val stationPointCoordinates = stationPoint
        ?.let { alignment.getCoordinateAt(alignment.stationValueNormalized(stationPoint.x)) }
    val startCoordinates = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.start.x))
        ?.let { coordinateTransform?.transform(it) }
    val endCoordinates = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.end.x))
        ?.let { coordinateTransform?.transform(it) }

    val previousStationPoint = previousCurvedSegment?.let(::circCurveStationPoint)
        ?: previousLinearSegment?.start
    val nextStationPoint = nextCurvedSegment?.let(::circCurveStationPoint)
        ?: nextLinearSegment?.start

    return VerticalGeometryListing(
        id = StringId("${alignment.id}_${segment.start.x}"),
        planId = planHeader.id,
        planSource = planHeader.source,
        fileName = planHeader.fileName,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        null,
        start = CurvedSectionEndpoint(
            address = startCoordinates?.let { geocodingContext?.getAddress(startCoordinates)?.first },
            height = segment.start.y,
            angle = stationPoint?.let { angleFractionBetweenPoints(stationPoint, segment.start) },
            station = segment.start.x
        ),
        end = CurvedSectionEndpoint(
            address = endCoordinates?.let { geocodingContext?.getAddress(endCoordinates)?.first },
            height = segment.end.y,
            angle = stationPoint?.let { angleFractionBetweenPoints(stationPoint, segment.end) },
            station = segment.end.x
        ),
        point = IntersectionPoint(
            address = stationPointCoordinates?.let { geocodingContext?.getAddress(stationPointCoordinates)?.first },
            height = stationPoint?.y,
            station = stationPoint?.x
        ),
        radius = segment.radius,
        tangent = stationPoint?.let { lineLength(segment.start, stationPoint) },
        linearSectionBackward = LinearSection(
            length = if (previousStationPoint != null && stationPoint != null) previousStationPoint.x - stationPoint.x else null,
            linearSection = previousCurvedSegment?.let { segment.start.x - previousCurvedSegment.end.x }
        ),
        linearSectionForward = LinearSection(
            length = if (nextStationPoint != null && stationPoint != null) stationPoint.x - nextStationPoint.x else null,
            linearSection = nextCurvedSegment?.let { nextCurvedSegment.start.x - segment.end.x }
        )
    )
}

fun circCurveStationPoint(curve: CurvedProfileSegment): IPoint? {
    val line1 = circCurveTangentLine(curve.start, curve.startAngle)
    val line2 = circCurveTangentLine(curve.end, curve.endAngle)
    return lineIntersection(line1.start, line1.end, line2.start, line2.end)?.point
}

private fun circCurveTangentLine(point: IPoint, angle: Double): Line {
    // Line length is meaningless here as this is used as an intermediary step for station point calculation
    val newX = point.x + 1.0
    val newY = (tan(angle)) + point.y
    return Line(point, Point(newX, newY))
}

fun angleFractionBetweenPoints(point1: IPoint, point2: IPoint) =
    if (point1.x == point2.x) null
    else if (point1.x < point2.x) (point2.y - point1.y) / (point2.x - point1.x)
    else (point1.y - point2.y) / (point1.x - point2.x)
