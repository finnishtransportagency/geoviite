package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.printCsv
import kotlin.math.tan

data class GeometryProfileCalculationContext(
    val geometryAlignment: GeometryAlignment,
    val planHeader: GeometryPlanHeader,
    val curvedProfileSegments: List<CurvedProfileSegment>,
    val linearProfileSegments: List<LinearProfileSegment>,
)

data class CurvedSectionEndpoint(
    val address: TrackMeter?,
    val height: Double,
    val angle: Double?,
    val station: Double,
)

data class IntersectionPoint(
    val address: TrackMeter?,
    val height: Double,
    val station: Double,
)

data class LinearSection(
    val stationValueDistance: Double?,
    val linearSegmentLength: Double?,
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
    planAlignments: List<GeometryAlignment>,
    getTransformation: (srid: Srid) -> Transformation,
    planHeader: GeometryPlanHeader,
    geocodingContext: GeocodingContext?
): List<VerticalGeometryListing> {
    return planAlignments.filter { it.profile != null }.map { alignment ->
        val (curvedSegments, linearSegments) =
            alignment.profile?.segments
                ?.let(::separateCurvedAndLinearProfileSegments)
                ?: (emptyList<CurvedProfileSegment>() to emptyList())
        curvedSegments.map { segment ->
            toVerticalGeometryListing(
                segment,
                alignment,
                null,
                planHeader.units.coordinateSystemSrid?.let(getTransformation),
                planHeader.id,
                planHeader.source,
                planHeader.fileName,
                geocodingContext,
                curvedSegments,
                linearSegments
            )
        }
    }.flatten()
}

fun toVerticalGeometryListing(
    track: LocationTrack,
    layoutAlignment: LayoutAlignment,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
    geocodingContext: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    getPlanHeaderAndAlignment: (id: IntId<GeometryAlignment>) -> Pair<GeometryPlanHeader, GeometryAlignment>,
): List<VerticalGeometryListing> {
    val linkedElementIds = collectLinkedElements(
        layoutAlignment.segments,
        geocodingContext,
        startAddress,
        endAddress
    ).mapNotNull { it.second }
    val headersAndAlignments = linkedElementIds
        .map(::getAlignmentId)
        .distinct()
        .associateWith(getPlanHeaderAndAlignment)

    val curvedSegmentsAndGeometryListingContexts = linkedElementIds
        .map { elementId ->
            getCurvedProfileSegmentsAndContextsOverlappingElement(headersAndAlignments, elementId)
        }
        .flatten()
        .distinctBy { it.first }

    return curvedSegmentsAndGeometryListingContexts.map { (segment, context) ->
        toVerticalGeometryListing(
            segment,
            context.geometryAlignment,
            track.name,
            context.planHeader.units.coordinateSystemSrid
                ?.let(getTransformation),
            context.planHeader.id,
            context.planHeader.source,
            context.planHeader.fileName,
            geocodingContext,
            context.curvedProfileSegments,
            context.linearProfileSegments
        )
    }
}

fun toVerticalGeometryListing(
    segment: CurvedProfileSegment,
    alignment: GeometryAlignment,
    locationTrackName: AlignmentName?,
    coordinateTransform: Transformation?,
    planId: DomainId<GeometryPlan>,
    planSource: PlanSource,
    planFileName: FileName,
    geocodingContext: GeocodingContext?,
    curvedSegments: List<CurvedProfileSegment>,
    linearSegments: List<LinearProfileSegment>,
    ): VerticalGeometryListing {
    val stationPoint = circCurveStationPoint(segment)
    val stationPointCoordinates = alignment.getCoordinateAt(alignment.stationValueNormalized(stationPoint.x))
        ?.let { coordinateTransform?.transform(it) }
    val startCoordinates = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.start.x))
        ?.let { coordinateTransform?.transform(it) }
    val endCoordinates = alignment.getCoordinateAt(alignment.stationValueNormalized(segment.end.x))
        ?.let { coordinateTransform?.transform(it) }

    return VerticalGeometryListing(
        id = StringId("${alignment.id}_${segment.start.x}"),
        planId = planId,
        planSource = planSource,
        fileName = planFileName,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        locationTrackName,
        start = CurvedSectionEndpoint(
            address = startCoordinates?.let { geocodingContext?.getAddress(startCoordinates)?.first },
            height = segment.start.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.start),
            station = segment.start.x
        ),
        end = CurvedSectionEndpoint(
            address = endCoordinates?.let { geocodingContext?.getAddress(endCoordinates)?.first },
            height = segment.end.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.end),
            station = segment.end.x
        ),
        point = IntersectionPoint(
            address = stationPointCoordinates?.let { geocodingContext?.getAddress(stationPointCoordinates)?.first },
            height = stationPoint.y,
            station = stationPoint.x
        ),
        radius = segment.radius,
        tangent = lineLength(segment.start, stationPoint),
        linearSectionBackward = previousLinearSection(segment, curvedSegments, linearSegments),
        linearSectionForward = nextLinearSection(segment, curvedSegments, linearSegments),
    )
}

fun locationTrackVerticalGeometryListingToCsv(listing: List<VerticalGeometryListing>) =
    printCsv(listOf(locationTrackCsvEntry) + commonVerticalGeometryListingCsvEntries, listing)

fun planVerticalGeometryListingToCsv(listing: List<VerticalGeometryListing>) =
    printCsv(commonVerticalGeometryListingCsvEntries.toList(), listing)

private val locationTrackCsvEntry =
    CsvEntry<VerticalGeometryListing>(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LOCATION_TRACK)) {
        it.locationTrackName
    }

private val commonVerticalGeometryListingCsvEntries = arrayOf(
    CsvEntry<VerticalGeometryListing>(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.PLAN_NAME)) { it.fileName },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.PLAN_TRACK)) { it.alignmentName },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.TRACK_ADDRESS_START)) {
        it.start.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.HEIGHT_START)) {
        it.start.height
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.ANGLE_START)) {
        it.start.angle
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.TRACK_ADDRESS_POINT)) {
        it.point.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.HEIGHT_POINT)) {
        it.point.height
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.TRACK_ADDRESS_END)) {
        it.end.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.HEIGHT_END)) {
        it.end.height
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.ANGLE_END)) {
        it.end.angle
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.RADIUS)) { it.radius },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.TANGENT)) { it.tangent },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LENGTH)) { it.linearSectionBackward.stationValueDistance },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LINEAR_SECTION)) { it.linearSectionBackward.linearSegmentLength },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LENGTH)) { it.linearSectionForward.stationValueDistance },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LINEAR_SECTION)) { it.linearSectionForward.linearSegmentLength },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_START)) { it.start.station },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_POINT)) { it.point.station },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_END)) { it.end.station },
)

fun previousLinearSection(
    currentSegment: CurvedProfileSegment,
    curvedSegments: List<CurvedProfileSegment>,
    linearSegments: List<LinearProfileSegment>,
): LinearSection {
    val previousCurvedSegment = curvedSegments.findLast { it.start.x < currentSegment.start.x }
    val previousLinearSegment =
        linearSegments.findLast { it.start.x < currentSegment.start.x && (previousCurvedSegment == null || it.start.x > previousCurvedSegment.start.x) }
    return LinearSection(
        linearSegmentLength = previousLinearSegment?.let { previousLinearSegment.end.x - previousLinearSegment.start.x },
        stationValueDistance = previousLinearSegment?.let {
            val currentStationPoint = circCurveStationPoint(currentSegment)
            val previousStationPoint = previousCurvedSegment?.let { prev -> circCurveStationPoint(prev) }
                ?: previousLinearSegment.start
            currentStationPoint.x - previousStationPoint.x
        }
    )
}

fun nextLinearSection(
    currentSegment: CurvedProfileSegment,
    curvedSegments: List<CurvedProfileSegment>,
    linearSegments: List<LinearProfileSegment>,
): LinearSection {
    val nextCurvedSegment = curvedSegments.find { it.start.x > currentSegment.start.x }
    val nextLinearSegment =
        linearSegments.find { it.start.x > currentSegment.start.x && (nextCurvedSegment == null || it.start.x < nextCurvedSegment.start.x) }
    return LinearSection(
        linearSegmentLength = nextLinearSegment?.let { nextLinearSegment.end.x - nextLinearSegment.start.x },
        stationValueDistance = nextLinearSegment?.let {
            val currentStationPoint = circCurveStationPoint(currentSegment)
            val nextStationPoint = nextCurvedSegment?.let { next -> circCurveStationPoint(next) }
                ?: nextLinearSegment.end
            nextStationPoint.x - currentStationPoint.x
        }
    )
}

fun circCurveStationPoint(curve: CurvedProfileSegment): IPoint {
    val line1 = circCurveTangentLine(curve.start, curve.startAngle)
    val line2 = circCurveTangentLine(curve.end, curve.endAngle)
    val intersection = lineIntersection(line1.start, line1.end, line2.start, line2.end)
    require (intersection != null) {"Circular curve must have an intersection point"}
    return intersection.point
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

fun getCurvedProfileSegmentsAndContextsOverlappingElement(
    headersAndAlignments: Map<IntId<GeometryAlignment>, Pair<GeometryPlanHeader, GeometryAlignment>>,
    elementId: IndexedId<GeometryElement>
): List<Pair<CurvedProfileSegment, GeometryProfileCalculationContext>> {
    val (planHeader, geometryAlignment) = headersAndAlignments.getValue(getAlignmentId(elementId))
    val (curvedSegments, linearSegments) =
        geometryAlignment.profile?.segments
            ?.let(::separateCurvedAndLinearProfileSegments)
            ?: (emptyList<CurvedProfileSegment>() to emptyList())
    val elementRange = geometryAlignment.getElementStationRangeWithinAlignment(elementId)
    val segmentsOverlappingElement = curvedSegments
        .filter { segment ->
            geometryAlignment.stationValueNormalized(segment.start.x) <= elementRange.endInclusive &&
                    geometryAlignment.stationValueNormalized(segment.end.x) >= elementRange.start
        }

    return segmentsOverlappingElement.map { curve ->
        curve to GeometryProfileCalculationContext(
            geometryAlignment,
            planHeader,
            curvedSegments,
            linearSegments
        )
    }
}

private fun separateCurvedAndLinearProfileSegments(segments: List<ProfileSegment>) =
    segments.partition { it is CurvedProfileSegment }
        .let { partitioned ->
            partitioned.first.map { it as CurvedProfileSegment } to partitioned.second.map { it as LinearProfileSegment }
        }
