package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.printCsv
import fi.fta.geoviite.infra.util.toCsvDate
import java.math.BigDecimal
import java.time.Instant
import java.util.stream.Collectors
import kotlin.math.tan

private const val VERTICAL_GEOMETRY_CSV_PREFIX = "data-products.vertical-geometry.csv"

data class CurvedSectionEndpoint(
    val address: TrackMeter?,
    val height: BigDecimal,
    val angle: BigDecimal?,
    val station: BigDecimal,
    val location: RoundedPoint?,
)

fun toCurvedSectionEndpoint(address: TrackMeter?, location: RoundedPoint?, height: Double, angle: Double?, station: Double) =
    CurvedSectionEndpoint(
        address = address,
        height = roundTo3Decimals(height),
        angle = angle?.let(::roundTo6Decimals),
        station = roundTo3Decimals(station),
        location = location,
    )

data class IntersectionPoint(
    val address: TrackMeter?,
    val height: BigDecimal,
    val station: BigDecimal,
    val location: RoundedPoint?,
)

fun toIntersectionPoint(address: TrackMeter?, location: RoundedPoint?, height: Double, station: Double) =
    IntersectionPoint(
        address = address,
        height = roundTo3Decimals(height),
        station = roundTo3Decimals(station),
        location = location,
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
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?,
    val creationTime: Instant?,

    val layoutStartStation: Double? = null,
    val layoutPointStation: Double? = null,
    val layoutEndStation: Double? = null,

    val trackNumber: TrackNumber? = null,
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
                planHeader,
                geocodingContext,
                curvedSegments,
                linearSegments,
                segmentStartAddress,
                segmentEndAddress,
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

    return listing.parallelStream().map { entry ->
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
    }.collect(Collectors.toList())
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

    return if (segmentStartAddress != null && endAddress != null && segmentStartAddress > endAddress) {
        null
    } else if (segmentEndAddress != null && startAddress != null && segmentEndAddress < startAddress) {
        null
    } else {
        toVerticalGeometryListing(
            segment,
            geometryAlignment,
            track.name,
            planHeader.units.coordinateSystemSrid?.let(getTransformation),
            planHeader,
            geocodingContext,
            curvedProfileSegments,
            linearProfileSegments,
            segmentStartAddress,
            segmentEndAddress,
        )
    }
}

fun toVerticalGeometryListing(
    segment: CurvedProfileSegment,
    alignment: GeometryAlignment,
    locationTrackName: AlignmentName?,
    coordinateTransform: Transformation?,
    planHeader: GeometryPlanHeader,
    geocodingContext: GeocodingContext?,
    curvedSegments: List<CurvedProfileSegment>,
    linearSegments: List<LinearProfileSegment>,
    segmentStartAddress: TrackMeter?,
    segmentEndAddress: TrackMeter?,
): VerticalGeometryListing {
    val stationPoint = circCurveStationPoint(segment)
    val stationPointLocation = alignment
        .getCoordinateAt(alignment.stationValueNormalized(stationPoint.x))
        ?.round(COORDINATE_DECIMALS)
    val stationPointAddress = if (geocodingContext != null && coordinateTransform != null) getTrackAddressAtStation(
        geocodingContext,
        coordinateTransform,
        alignment,
        stationPoint.x
    ) else null

    return VerticalGeometryListing(
        id = StringId("${alignment.id}_${segment.start.x}"),
        planId = planHeader.id,
        planSource = planHeader.source,
        fileName = planHeader.fileName,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        locationTrackName = locationTrackName,
        start = toCurvedSectionEndpoint(
            address = segmentStartAddress,
            height = segment.start.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.start),
            station = segment.start.x,
            location = roundedLocationM(alignment, segment.start.x),
        ),
        end = toCurvedSectionEndpoint(
            address = segmentEndAddress,
            height = segment.end.y,
            angle = angleFractionBetweenPoints(stationPoint, segment.end),
            station = segment.end.x,
            location = roundedLocationM(alignment, segment.end.x),
        ),
        point = toIntersectionPoint(
            address = stationPointAddress,
            height = stationPoint.y,
            station = stationPoint.x,
            location = stationPointLocation,
        ),
        radius = round(segment.radius, 0),
        tangent = lineLength(segment.start, stationPoint).let(::roundTo3Decimals),
        linearSectionBackward = previousLinearSection(segment, curvedSegments, linearSegments),
        linearSectionForward = nextLinearSection(segment, curvedSegments, linearSegments),
        elevationMeasurementMethod = planHeader.elevationMeasurementMethod,
        verticalCoordinateSystem = planHeader.units.verticalCoordinateSystem,
        coordinateSystemSrid = planHeader.units.coordinateSystemSrid,
        coordinateSystemName = planHeader.units.coordinateSystemName,
        creationTime = planHeader.planTime,
        overlapsAnother = false,
    )
}
fun roundedLocationM(alignment: GeometryAlignment, distance: Double): RoundedPoint? =
    alignment.getCoordinateAt(alignment.stationValueNormalized(distance))?.round(COORDINATE_DECIMALS)

fun locationTrackVerticalGeometryListingToCsv(listing: List<VerticalGeometryListing>, translation: Translation) =
    printCsv(listOf(locationTrackCsvEntry(translation)) + commonVerticalGeometryListingCsvEntries(translation), listing)

fun entireTrackNetworkVerticalGeometryListingToCsv(listing: List<VerticalGeometryListing>, translation: Translation) =
    printCsv(
        listOf(
            trackNumberCsvEntry(translation),
            locationTrackCsvEntry(translation),
            *commonVerticalGeometryListingCsvEntries(translation)
        ), listing
    )

fun planVerticalGeometryListingToCsv(listing: List<VerticalGeometryListing>, translation: Translation) =
    printCsv(commonVerticalGeometryListingCsvEntries(translation).toList(), listing)

private fun trackNumberCsvEntry(translation: Translation) =
    CsvEntry<VerticalGeometryListing>(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.track-number")) {
       verticalGeometryListing -> verticalGeometryListing.trackNumber ?: ""
    }

private fun locationTrackCsvEntry(translation: Translation) =
    CsvEntry<VerticalGeometryListing>(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-track")) {
        it.locationTrackName
    }

private fun commonVerticalGeometryListingCsvEntries(translation: Translation) = arrayOf(
    CsvEntry<VerticalGeometryListing>(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.plan-name")) { it.fileName },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.crs")) { it.coordinateSystemSrid ?: it.coordinateSystemName },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.creation-date")) { it.creationTime?.let(::toCsvDate) },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.plan-track")) { it.alignmentName },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.track-address-start")) {
        it.start.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.height-start")) { it.start.height },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.angle-start")) { it.start.angle },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-e-start")) { it.start.location?.roundedX },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-n-start")) { it.start.location?.roundedY },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.track-address-point")) {
        it.point.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.height-point")) { it.point.height },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-e-point")) { it.point.location?.roundedX },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-n-point")) { it.point.location?.roundedY },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.track-address-end")) {
        it.end.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.height-end")) { it.end.height },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.angle-end")) { it.end.angle },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-e-end")) { it.end.location?.roundedX },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.location-n-end")) { it.end.location?.roundedY },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.radius")) { it.radius },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.tangent")) { it.tangent },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.linear-section-backward-length")) { it.linearSectionBackward.stationValueDistance },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.linear-section-backward-linear-section")) { it.linearSectionBackward.linearSegmentLength },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.linear-section-forward-length")) { it.linearSectionForward.stationValueDistance },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.linear-section-forward-linear-section")) { it.linearSectionForward.linearSegmentLength },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.station-start")) { it.start.station },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.station-point")) { it.point.station },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.station-end")) { it.end.station },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.vertical-coordinate-system")) { it.verticalCoordinateSystem },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.elevation-measurement-method")) {
        translateElevationMeasurementMethod(it.elevationMeasurementMethod, translation)
    },
    CsvEntry(translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.remarks")) {
        if (it.overlapsAnother) translation.t("data-products.vertical-geometry.overlaps-another") else ""
    }
)

fun translateElevationMeasurementMethod(elevationMeasurementMethod: ElevationMeasurementMethod?, translation: Translation) =
    when (elevationMeasurementMethod) {
        ElevationMeasurementMethod.TOP_OF_SLEEPER -> translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.top-of-sleeper")
        ElevationMeasurementMethod.TOP_OF_RAIL -> translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.top-of-rail")
        null -> translation.t("$VERTICAL_GEOMETRY_CSV_PREFIX.unknown")
    }

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
