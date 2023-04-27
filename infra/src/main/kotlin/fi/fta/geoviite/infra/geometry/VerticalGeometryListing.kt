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
import java.math.BigDecimal
import kotlin.math.tan

data class CurvedSectionEndpoint(
    val address: TrackMeter?,
    val height: BigDecimal,
    val angle: BigDecimal?,
    val station: BigDecimal,
)

fun toCurvedSectionEndpoint(address: TrackMeter?, height: Double, angle: Double?, station: Double) =
    CurvedSectionEndpoint(
        address = address,
        height = roundTo3Decimals(height),
        angle = angle?.let(::roundTo6Decimals),
        station = roundTo3Decimals(station)
    )

data class IntersectionPoint(
    val address: TrackMeter?,
    val height: BigDecimal,
    val station: BigDecimal,
)

fun toIntersectionPoint(address: TrackMeter?, height: Double, station: Double) =
    IntersectionPoint(
        address = address,
        height = roundTo3Decimals(height),
        station = roundTo3Decimals(station)
    )

data class LinearSection(
    val stationValueDistance: BigDecimal?,
    val linearSegmentLength: BigDecimal?,
)

fun toLinearSection(stationValueDistance: Double?, linearSegmentLength: Double?) =
    LinearSection(
        stationValueDistance = stationValueDistance?.let(::roundTo3Decimals),
        linearSegmentLength = linearSegmentLength?.let(::roundTo3Decimals)
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
    val radius: BigDecimal,
    val tangent: BigDecimal?,
    val linearSectionForward: LinearSection,
    val linearSectionBackward: LinearSection,
    val overlapsAnother: Boolean,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,

    val layoutStartStation: Double? = null,
    val layoutPointStation: Double? = null,
    val layoutEndStation: Double? = null,
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
            val coordinateTransform = planHeader.units.coordinateSystemSrid?.let(getTransformation)
            val (segmentStartAddress, segmentEndAddress) =
                if (geocodingContext != null && coordinateTransform != null) {
                    getTrackAddressAtStation(
                        geocodingContext,
                        coordinateTransform,
                        alignment,
                        segment.start.x
                    ) to getTrackAddressAtStation(
                        geocodingContext,
                        coordinateTransform,
                        alignment,
                        segment.end.x
                    )
                } else (null to null)

            toVerticalGeometryListing(
                segment,
                alignment,
                null,
                coordinateTransform,
                planHeader.id,
                planHeader.source,
                planHeader.fileName,
                geocodingContext,
                curvedSegments,
                linearSegments,
                segmentStartAddress,
                segmentEndAddress,
                planHeader.units.verticalCoordinateSystem
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
    ).distinctBy { (segment, elementId) -> elementId ?: segment.id }.mapNotNull { it.second }
    val headersAndAlignments = linkedElementIds
        .map(::getAlignmentId)
        .distinct()
        .associateWith(getPlanHeaderAndAlignment)

    val listing = linkedElementIds.flatMap { elementId ->
        val (planHeader, geometryAlignment) = headersAndAlignments.getValue(getAlignmentId(elementId))
        val elementRange = Range(geometryAlignment.getElementStationRangeWithinAlignment(elementId))

        val (curvedProfileSegments, linearProfileSegments) = separateCurvedAndLinearProfileSegments(geometryAlignment.profile?.segments ?: emptyList())
        geometryAlignment.profile?.segments?.mapNotNull { segment ->
            val segmentRange = Range(geometryAlignment.stationValueNormalized(segment.start.x)..geometryAlignment.stationValueNormalized(segment.end.x))
            if (segment is CurvedProfileSegment && segmentRange.overlaps(elementRange)) {
                toVerticalGeometry(
                    planHeader,
                    getTransformation,
                    geocodingContext,
                    geometryAlignment,
                    segment,
                    endAddress,
                    startAddress,
                    track,
                    curvedProfileSegments,
                    linearProfileSegments,
                )?.let { segment to it }
            }
            else null
        } ?: emptyList()
    }.distinctBy { it.first }.map { it.second }

    fun getAlignmentStation(maybeAddress: TrackMeter?) =
        maybeAddress?.let { address -> geocodingContext?.getTrackLocation(layoutAlignment, address)?.point?.m }

    return listing.map { entry ->
        entry.copy(
            overlapsAnother = listing.filter { overlapCandidate ->
                entry.start.address != null &&
                        entry.end.address != null &&
                        overlapCandidate.start.address != null &&
                        overlapCandidate.end.address != null &&
                        entry.start.address <= overlapCandidate.end.address &&
                        entry.end.address >= overlapCandidate.start.address
            }.size > 1,
            layoutStartStation = getAlignmentStation(entry.start.address),
            layoutPointStation = getAlignmentStation(entry.point.address),
            layoutEndStation = getAlignmentStation(entry.end.address),
        )
    }
}

private fun toVerticalGeometry(
    planHeader: GeometryPlanHeader,
    getTransformation: (srid: Srid) -> Transformation,
    geocodingContext: GeocodingContext?,
    geometryAlignment: GeometryAlignment,
    segment: CurvedProfileSegment,
    endAddress: TrackMeter?,
    startAddress: TrackMeter?,
    track: LocationTrack,
    curvedProfileSegments: List<CurvedProfileSegment>,
    linearProfileSegments: List<LinearProfileSegment>,
): VerticalGeometryListing? {
    val coordinateTransform = planHeader.units.coordinateSystemSrid?.let(getTransformation)
    val (segmentStartAddress, segmentEndAddress) =
        if (geocodingContext != null && coordinateTransform != null) {
            getTrackAddressAtStation(
                geocodingContext,
                coordinateTransform,
                geometryAlignment,
                segment.start.x
            ) to getTrackAddressAtStation(
                geocodingContext,
                coordinateTransform,
                geometryAlignment,
                segment.end.x
            )
        } else (null to null)

    return if (segmentStartAddress != null && endAddress != null && segmentStartAddress > endAddress) null
    else if (segmentEndAddress != null && startAddress != null && segmentEndAddress < startAddress) null
    else toVerticalGeometryListing(
        segment,
        geometryAlignment,
        track.name,
        planHeader.units.coordinateSystemSrid?.let(getTransformation),
        planHeader.id,
        planHeader.source,
        planHeader.fileName,
        geocodingContext,
        curvedProfileSegments,
        linearProfileSegments,
        segmentStartAddress,
        segmentEndAddress,
        planHeader.units.verticalCoordinateSystem
    )
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
    segmentStartAddress: TrackMeter?,
    segmentEndAddress: TrackMeter?,
    verticalCoordinateSystem: VerticalCoordinateSystem?,
    ): VerticalGeometryListing {
    val stationPoint = circCurveStationPoint(segment)
    val stationPointAddress = if (geocodingContext != null && coordinateTransform != null) getTrackAddressAtStation(
        geocodingContext,
        coordinateTransform,
        alignment,
        stationPoint.x
    ) else null

    return VerticalGeometryListing(
        id = StringId("${alignment.id}_${segment.start.x}"),
        planId = planId,
        planSource = planSource,
        fileName = planFileName,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        locationTrackName,
        start = toCurvedSectionEndpoint(
            address = segmentStartAddress,
            height = segment.start.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.start),
            station = segment.start.x
        ),
        end = toCurvedSectionEndpoint(
            address = segmentEndAddress,
            height = segment.end.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.end),
            station = segment.end.x
        ),
        point = toIntersectionPoint(
            address = stationPointAddress,
            height = stationPoint.y,
            station = stationPoint.x
        ),
        radius = round(segment.radius, 0),
        tangent = lineLength(segment.start, stationPoint).let(::roundTo3Decimals),
        linearSectionBackward = previousLinearSection(segment, curvedSegments, linearSegments),
        linearSectionForward = nextLinearSection(segment, curvedSegments, linearSegments),
        verticalCoordinateSystem = verticalCoordinateSystem,
        overlapsAnother = false,
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
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.TANGENT)) {
        it.tangent
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LENGTH)) {
        it.linearSectionBackward.stationValueDistance
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_BACKWARD_LINEAR_SECTION)) {
        it.linearSectionBackward.linearSegmentLength
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LENGTH)) {
        it.linearSectionForward.stationValueDistance
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.LINEAR_SECTION_FORWARD_LINEAR_SECTION)) {
        it.linearSectionForward.linearSegmentLength
    },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_START)) { it.start.station },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_POINT)) { it.point.station },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.STATION_END)) { it.end.station },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.VERTICAL_COORDINATE_SYSTEM)) { it.verticalCoordinateSystem },
    CsvEntry(translateVerticalGeometryListingHeader(VerticalGeometryListingHeader.REMARKS)) { if (it.overlapsAnother) VERTICAL_SECTIONS_OVERLAP else "" }
)

fun previousLinearSection(
    currentSegment: CurvedProfileSegment,
    curvedSegments: List<CurvedProfileSegment>,
    linearSegments: List<LinearProfileSegment>,
): LinearSection {
    val previousCurvedSegment = curvedSegments.findLast { it.start.x < currentSegment.start.x }
    val previousLinearSegment =
        linearSegments.findLast { it.start.x < currentSegment.start.x && (previousCurvedSegment == null || it.start.x > previousCurvedSegment.start.x) }
    return toLinearSection(
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
    return toLinearSection(
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
    requireNotNull(intersection) {"Circular curve must have an intersection point"}
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

private fun separateCurvedAndLinearProfileSegments(segments: List<ProfileSegment>) =
    segments.partition { it is CurvedProfileSegment }
        .let { partitioned ->
            partitioned.first.map { it as CurvedProfileSegment } to partitioned.second.map { it as LinearProfileSegment }
        }

fun getTrackAddressAtStation(
    geocodingContext: GeocodingContext,
    coordinateTransform: Transformation,
    geometryAlignment: GeometryAlignment,
    station: Double
) = geometryAlignment
        .getCoordinateAt(geometryAlignment.stationValueNormalized(station))
        ?.let(coordinateTransform::transform)
        ?.let(geocodingContext::getAddress)?.first
