package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.lineFromPointAndAngle
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.util.FileName

data class VIStartOrEnd(
    val address: TrackMeter?,
    val height: Double,
    val angle: Double?,
    val station: Double,
)

data class VIListingPoint(
    val address: TrackMeter?,
    val height: Double?,
    val station: Double?,
)

data class LinearSection(
    val length: Double?,
    val linearSection: Double?,
)

data class ProfileListing(
    val id: StringId<ElementListing>,
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,

    val alignmentId: DomainId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,

    val locationTrackName: AlignmentName?,
    val start: VIStartOrEnd,
    val end: VIStartOrEnd,
    val point: VIListingPoint,
    val radius: Double,
    val tangent: Double?,
    val linearSectionForward: LinearSection,
    val linearSectionBackward: LinearSection,
)

fun toProfileListing(
    segment: CurvedProfileSegment,
    alignment: GeometryAlignment,
    coordinateTransform: Transformation?,
    planHeader: GeometryPlanHeader,
    geocodingContext: GeocodingContext?,
    previousCurvedSegment: CurvedProfileSegment?,
    previousLinearSegment: LinearProfileSegment?,
    nextCurvedSegment: CurvedProfileSegment?,
    nextLinearSegment: LinearProfileSegment?
): ProfileListing {
    val centerpoint = calculateCenterpoint(segment.start, segment.startAngle, segment.end, segment.endAngle)
    val pointCoord = centerpoint?.let { alignment.getCoordinateAt(alignment.stationValueNormalized(centerpoint.x)) }
    val startCoord = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.start.x))
        ?.let { coordinateTransform?.transform(it) }
    val endCoord = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.end.x))
        ?.let { coordinateTransform?.transform(it) }

    return ProfileListing(
        id = StringId("${alignment.id}_${segment.start.x}"),
        planId = planHeader.id,
        planSource = planHeader.source,
        fileName = planHeader.fileName,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        null,
        start = VIStartOrEnd(
            address = startCoord?.let { geocodingContext?.getAddress(startCoord)?.first },
            height = segment.start.y,
            angle = centerpoint?.let { calculateAnglePercentage(centerpoint, segment.start) },
            station = segment.start.x
        ),
        end = VIStartOrEnd(
            address = endCoord?.let { geocodingContext?.getAddress(endCoord)?.first },
            height = segment.end.y,
            angle = centerpoint?.let { calculateAnglePercentage(centerpoint, segment.end) },
            station = segment.end.x
        ),
        point = VIListingPoint(
            address = pointCoord?.let { geocodingContext?.getAddress(pointCoord)?.first },
            height = centerpoint?.y,
            station = centerpoint?.x
        ),
        radius = segment.radius,
        tangent = centerpoint?.let { centerpoint.x - segment.start.x },
        linearSectionBackward = LinearSection(
            length = previousLinearSegment?.let { previousLinearSegment.end.x - previousLinearSegment.start.x },
            linearSection = previousCurvedSegment?.let { segment.start.x - previousCurvedSegment.end.x }
        ),
        linearSectionForward = LinearSection(
            length = nextLinearSegment?.let { nextLinearSegment.end.x - nextLinearSegment.start.x },
            linearSection = nextCurvedSegment?.let { nextCurvedSegment.start.x - segment.end.x }
        )
    )
}

private fun calculateCenterpoint(point1: IPoint, angle1: Double, point2: IPoint, angle2: Double): IPoint? {
    val line1 = lineFromPointAndAngle(point1, angle1, 1.0)
    val line2 = lineFromPointAndAngle(point2, angle2, 1.0)
    return lineIntersection(line1.start, line1.end, line2.start, line2.end).let { it?.point }
}

private fun calculateAnglePercentage(point1: IPoint, point2: IPoint) =
    if (point1.x == point2.x) null
    else if (point1.x < point2.x) (point2.y - point1.y) / (point2.x - point1.x)
    else (point1.y - point2.y) / (point1.x - point2.x)
