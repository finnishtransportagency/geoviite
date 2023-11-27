package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.TrackGeometryElementType.MISSING_SECTION
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.printCsv
import java.math.BigDecimal
import kotlin.math.abs

const val COORDINATE_DECIMALS = 3
const val ADDRESS_DECIMALS = 3
const val LENGTH_DECIMALS = 3
const val DIRECTION_DECIMALS = 6
const val CANT_DECIMALS = 6

const val SEGMENT_AND_ELEMENT_LENGTH_MAX_DELTA = 1.0

data class ElementListing(
    val id: StringId<ElementListing>,
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?,

    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val trackNumberDescription: PlanElementName?,

    val alignmentId: DomainId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,

    val locationTrackName: AlignmentName?,

    val elementId: DomainId<GeometryElement>?,
    val elementType: TrackGeometryElementType,
    val lengthMeters: BigDecimal,

    val start: ElementLocation,
    val end: ElementLocation,
    val connectedSwitchName: SwitchName?,
    val isPartial: Boolean,
)

data class ElementLocation(
    val coordinate: RoundedPoint,
    val address: TrackMeter?,
    val directionGrads: BigDecimal,
    val radiusMeters: BigDecimal?,
    val cant: BigDecimal?,
)

enum class TrackGeometryElementType {
    LINE,
    CURVE,
    CLOTHOID,
    BIQUADRATIC_PARABOLA,
    MISSING_SECTION,
    ;

    companion object {
        fun of(elementType: GeometryElementType): TrackGeometryElementType = when (elementType) {
            GeometryElementType.LINE -> LINE
            GeometryElementType.CURVE -> CURVE
            GeometryElementType.CLOTHOID -> CLOTHOID
            GeometryElementType.BIQUADRATIC_PARABOLA -> BIQUADRATIC_PARABOLA
        }
    }
}

fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    track: LocationTrack,
    layoutAlignment: LayoutAlignment,
    elementTypes: List<TrackGeometryElementType>,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
    getPlanHeaderAndAlignment: (id: IntId<GeometryAlignment>) -> Pair<GeometryPlanHeader, GeometryAlignment>,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
): List<ElementListing> {
    val linkedElementIds = collectLinkedElements(layoutAlignment.segments, context, startAddress, endAddress)
    val lengthOfSegmentsConnectedToSameElement = linkedElementIds.groupBy { it.second }.map {
        it.key to it.value.sumOf { (segment, _) -> segment.length }
    }
    val linkedAlignmentIds = linkedElementIds.mapNotNull { (_, id) -> id?.let(::getAlignmentId) }.distinct()
    val headersAndAlignments = linkedAlignmentIds.associateWith { id -> getPlanHeaderAndAlignment(id) }

    return linkedElementIds.mapNotNull { (segment, elementId) ->
        if (elementId == null) {
            if (elementTypes.contains(MISSING_SECTION)) toMissingElementListing(
                context,
                track.trackNumberId,
                segment,
                track,
                getSwitchName
            )
            else null
        } else {
            val (planHeader, alignment) = headersAndAlignments[getAlignmentId(elementId)]
                ?: throw IllegalStateException("Failed to fetch geometry alignment for element: element=$elementId")
            val element = alignment.elements.find { e -> e.id == elementId } ?: throw IllegalStateException(
                "Geometry element not found on its parent alignment: alignment=${alignment.id} element=$elementId"
            )
            if (elementTypes.contains(TrackGeometryElementType.of(element.type))) {
                toElementListing(
                    context,
                    getTransformation,
                    track,
                    planHeader,
                    alignment,
                    element,
                    segment,
                    getSwitchName
                )
            } else {
                null
            }
        }
    }.distinctBy(ElementListing::id).map { listing ->
        val calculatedSegmentLength =
            lengthOfSegmentsConnectedToSameElement.find { (elementId, _) -> elementId == listing.elementId }?.second
        listing.copy(
            isPartial = if (calculatedSegmentLength != null && listing.planId != null)
                abs(calculatedSegmentLength - listing.lengthMeters.toDouble()) > SEGMENT_AND_ELEMENT_LENGTH_MAX_DELTA
            else false
        )
    }
}

fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    elementTypes: List<GeometryElementType>,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
) = plan.alignments.flatMap { alignment ->
    alignment.elements
        .filter { element -> elementTypes.contains(element.type) }
        .map { element -> toElementListing(context, getTransformation, plan, alignment, element, getSwitchName) }
}

private fun toMissingElementListing(
    context: GeocodingContext?,
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    segment: LayoutSegment,
    locationTrack: LocationTrack,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
) = ElementListing(
    id = StringId("MEL_${segment.id.stringFormat()}"),
    planId = null,
    planSource = null,
    fileName = null,
    coordinateSystemSrid = LAYOUT_SRID,
    coordinateSystemName = null,
    trackNumberId = trackNumberId,
    trackNumberDescription = null,
    alignmentId = null,
    alignmentName = null,
    elementId = null,
    elementType = MISSING_SECTION,
    lengthMeters = round(segment.length, LENGTH_DECIMALS),
    start = getLocation(context, segment.points.first(), segment.startDirection),
    end = getLocation(context, segment.points.last(), segment.endDirection),
    locationTrackName = locationTrack.name,
    connectedSwitchName = segment.switchId?.let { id -> if (id is IntId) getSwitchName(id) else null },
    isPartial = false,
)

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    locationTrack: LocationTrack,
    planHeader: GeometryPlanHeader,
    alignment: GeometryAlignment,
    element: GeometryElement,
    segment: LayoutSegment,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName
) = elementListing(
    context = context,
    getTransformation = getTransformation,
    planId = planHeader.id,
    planSource = planHeader.source,
    fileName = planHeader.fileName,
    units = planHeader.units,
    trackNumberId = locationTrack.trackNumberId,
    trackNumberDescription = null,
    alignment = alignment,
    element = element,
    locationTrack = locationTrack,
    segment = segment,
    getSwitchName = getSwitchName
)

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    alignment: GeometryAlignment,
    element: GeometryElement,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
) = elementListing(
    context = context,
    getTransformation = getTransformation,
    planId = plan.id,
    planSource = plan.source,
    fileName = plan.fileName,
    units = plan.units,
    trackNumberId = plan.trackNumberId,
    trackNumberDescription = plan.trackNumberDescription,
    alignment = alignment,
    element = element,
    locationTrack = null,
    segment = null,
    getSwitchName = getSwitchName,
)

fun planElementListingToCsv(
    trackNumbers: List<TrackLayoutTrackNumber>,
    elementListing: List<ElementListing>,
) = printCsv(planCsvEntries(trackNumbers), elementListing)

fun locationTrackElementListingToCsv(
    trackNumbers: List<TrackLayoutTrackNumber>,
    elementListing: List<ElementListing>,
) = printCsv(locationTrackCsvEntries(trackNumbers), elementListing)

private fun trackNumberCsvEntry(trackNumbers: List<TrackLayoutTrackNumber>) =
    CsvEntry<ElementListing>(translateElementListingHeader(ElementListingHeader.TRACK_NUMBER)) {
        it.trackNumberId.let { locationTrackTrackNumber ->
            trackNumbers.find { tn -> tn.id == locationTrackTrackNumber }?.number
        }
    }

private val commonElementListingCsvEntries = arrayOf(
    CsvEntry<ElementListing>(translateElementListingHeader(ElementListingHeader.PLAN_TRACK)) { it.alignmentName },
    CsvEntry(translateElementListingHeader(ElementListingHeader.ELEMENT_TYPE)) {
        translateTrackGeometryElementType(
            it.elementType
        )
    },
    CsvEntry(translateElementListingHeader(ElementListingHeader.TRACK_ADDRESS_START)) {
        it.start.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translateElementListingHeader(ElementListingHeader.TRACK_ADDRESS_END)) {
        it.end.address?.let { address ->
            formatTrackMeter(
                address.kmNumber,
                address.meters
            )
        }
    },
    CsvEntry(translateElementListingHeader(ElementListingHeader.CRS)) {
        it.coordinateSystemSrid ?: it.coordinateSystemName
    },
    CsvEntry(translateElementListingHeader(ElementListingHeader.LOCATION_START_E)) { it.start.coordinate.roundedX.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.LOCATION_START_N)) { it.start.coordinate.roundedY.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.LOCATION_END_E)) { it.end.coordinate.roundedX.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.LOCATION_END_N)) { it.end.coordinate.roundedY.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.LENGTH)) { it.lengthMeters },
    CsvEntry(translateElementListingHeader(ElementListingHeader.RADIUS_START)) { it.start.radiusMeters?.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.RADIUS_END)) { it.end.radiusMeters?.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.CANT_START)) { it.start.cant?.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.CANT_END)) { it.end.cant?.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.DIRECTION_START)) { it.start.directionGrads.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.DIRECTION_END)) { it.end.directionGrads.toPlainString() },
    CsvEntry(translateElementListingHeader(ElementListingHeader.PLAN_NAME)) { it.fileName },
    CsvEntry(translateElementListingHeader(ElementListingHeader.PLAN_SOURCE)) { it.planSource },
    CsvEntry(translateElementListingHeader(ElementListingHeader.REMARKS)) { remarks(it) }
)

fun locationTrackCsvEntries(trackNumbers: List<TrackLayoutTrackNumber>) = listOf(
    trackNumberCsvEntry(trackNumbers),
    CsvEntry(translateElementListingHeader(ElementListingHeader.LOCATION_TRACK)) { it.locationTrackName },
    *commonElementListingCsvEntries
)

fun planCsvEntries(trackNumbers: List<TrackLayoutTrackNumber>) = listOf(
    trackNumberCsvEntry(trackNumbers),
    *commonElementListingCsvEntries
)

private fun remarks(elementListing: ElementListing) =
    listOfNotNull(
        if (elementListing.isPartial) IS_PARTIAL else null,
        if (elementListing.connectedSwitchName != null) connectedToSwitch(elementListing.connectedSwitchName) else null
    ).joinToString(separator = ", ")

private fun elementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    planId: DomainId<GeometryPlan>,
    planSource: PlanSource?,
    fileName: FileName,
    units: GeometryUnits,
    trackNumberId: IntId<TrackLayoutTrackNumber>?,
    trackNumberDescription: PlanElementName?,
    alignment: GeometryAlignment,
    locationTrack: LocationTrack?,
    element: GeometryElement,
    segment: LayoutSegment?,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
) = units.coordinateSystemSrid?.let(getTransformation).let { transformation ->
    val start = getStartLocation(context, transformation, alignment, element)
    val end = getEndLocation(context, transformation, alignment, element)
    ElementListing(
        id = StringId("EL_${element.id.stringFormat()}"),
        planId = planId,
        planSource = planSource,
        fileName = fileName,
        coordinateSystemSrid = units.coordinateSystemSrid,
        coordinateSystemName = units.coordinateSystemName,
        trackNumberId = trackNumberId,
        trackNumberDescription = trackNumberDescription,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        locationTrackName = locationTrack?.name,
        elementId = element.id,
        elementType = TrackGeometryElementType.of(element.type),
        lengthMeters = round(element.calculatedLength, LENGTH_DECIMALS),
        start = start,
        end = end,
        connectedSwitchName = segment?.switchId?.let { id -> if (id is IntId) getSwitchName(id) else null },
        isPartial = false,
    )
}

private fun getLocation(
    context: GeocodingContext?,
    point: LayoutPoint,
    directionRads: Double,
) = ElementLocation(
    coordinate = point.round(COORDINATE_DECIMALS),
    address = context?.getAddress(point)?.first,
    directionGrads = getDirectionGrads(directionRads),
    radiusMeters = null,
    cant = point.cant?.let { c -> round(c, CANT_DECIMALS) },
)

private fun getStartLocation(
    context: GeocodingContext?,
    transformation: Transformation?,
    alignment: GeometryAlignment,
    element: GeometryElement,
) = ElementLocation(
    coordinate = element.start.round(COORDINATE_DECIMALS),
    address = getAddress(context, transformation, element.start),
    directionGrads = getDirectionGrads(element.startDirectionRads),
    radiusMeters = getStartRadius(element),
    cant = getStartCant(alignment, element),
)

private fun getEndLocation(
    context: GeocodingContext?,
    transformation: Transformation?,
    alignment: GeometryAlignment,
    element: GeometryElement,
) = ElementLocation(
    coordinate = element.end.round(COORDINATE_DECIMALS),
    address = getAddress(context, transformation, element.end),
    directionGrads = getDirectionGrads(element.endDirectionRads),
    radiusMeters = getEndRadius(element),
    cant = getEndCant(alignment, element),
)

fun getAlignmentId(elementId: IndexedId<GeometryElement>) = IntId<GeometryAlignment>(elementId.parentId)

private fun getAddress(context: GeocodingContext?, transformation: Transformation?, coordinate: Point) =
    if (context == null || transformation == null) null
    else context.getAddress(transformation.transform(coordinate), ADDRESS_DECIMALS)?.first

private fun getDirectionGrads(rads: Double) = round(radsToGrads(radsMathToGeo(rads)), DIRECTION_DECIMALS)

private fun getStartRadius(element: GeometryElement) = when (element) {
    is GeometryLine -> null
    is GeometryCurve -> element.radius
    is GeometrySpiral -> element.radiusStart
}

private fun getEndRadius(element: GeometryElement) = when (element) {
    is GeometryLine -> null
    is GeometryCurve -> element.radius
    is GeometrySpiral -> element.radiusEnd
}

private fun getStartCant(alignment: GeometryAlignment, element: GeometryElement) =
    getCantAt(alignment, getElementStartLength(alignment, element.id))

private fun getEndCant(alignment: GeometryAlignment, element: GeometryElement) =
    getCantAt(alignment, getElementStartLength(alignment, element.id) + element.calculatedLength)

private fun getElementStartLength(alignment: GeometryAlignment, elementId: DomainId<GeometryElement>) =
    alignment.elements.takeWhile { e -> e.id != elementId }.sumOf { e -> e.calculatedLength }

private fun getCantAt(alignment: GeometryAlignment, locationDistance: Double) =
    // Cant station values are alignment m-values, calculated from 0 (ignoring alignment station-start)
    alignment.cant?.getCantValue(locationDistance)?.let { v -> round(v, CANT_DECIMALS) }
