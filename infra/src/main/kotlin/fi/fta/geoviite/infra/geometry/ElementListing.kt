package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.TrackGeometryElementType.MISSING_SECTION
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.RoundedPoint
import fi.fta.geoviite.infra.math.radsToGrads
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import java.math.BigDecimal

const val COORDINATE_DECIMALS = 3
const val ADDRESS_DECIMALS = 3
const val LENGTH_DECIMALS = 3
const val DIRECTION_DECIMALS = 6
const val CANT_DECIMALS = 6

data class ElementListing(
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?,

    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val trackNumberDescription: PlanElementName?,

    val alignmentId: DomainId<GeometryAlignment>?,
    val alignmentName: AlignmentName?,

    val elementId: DomainId<GeometryElement>?,
    val elementType: TrackGeometryElementType,
    val lengthMeters: BigDecimal,

    val start: ElementLocation,
    val end: ElementLocation,
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
): List<ElementListing> {
    val linkedElementIds = collectLinkedElements(layoutAlignment.segments, context, startAddress, endAddress)
    val linkedAlignmentIds = linkedElementIds.mapNotNull { (_, id) -> id?.let(::getAlignmentId) }.distinct()
    val headersAndAlignments = linkedAlignmentIds.associateWith { id -> getPlanHeaderAndAlignment(id) }
    return linkedElementIds.mapNotNull { (segment, elementId) ->
        if (elementId == null) {
            if (elementTypes.contains(MISSING_SECTION)) toMissingElementListing(context, track.trackNumberId, segment)
            else null
        }
        else {
            val (planHeader, alignment) = headersAndAlignments[getAlignmentId(elementId)]
                ?: throw IllegalStateException("Failed to fetch geometry alignment for element: element=$elementId")
            val element = alignment.elements.find { e -> e.id == elementId } ?: throw IllegalStateException(
                "Geometry element not found on its parent alignment: alignment=${alignment.id} element=$elementId"
            )
            if (elementTypes.contains(TrackGeometryElementType.of(element.type))) {
                toElementListing(context, getTransformation, track, planHeader, alignment, element)
            } else {
                null
            }
        }
    }
}

fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    elementTypes: List<GeometryElementType>,
) = plan.alignments.flatMap { alignment ->
    alignment.elements
        .filter { element -> elementTypes.contains(element.type) }
        .map { element -> toElementListing(context, getTransformation, plan, alignment, element) }
}

private fun toMissingElementListing(
    context: GeocodingContext?,
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    segment: LayoutSegment,
) = ElementListing(
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
    start = getLocation(context, segment.points.first(), segment.startDirection()),
    end = getLocation(context, segment.points.last(), segment.endDirection()),
)

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    locationTrack: LocationTrack,
    planHeader: GeometryPlanHeader,
    alignment: GeometryAlignment,
    element: GeometryElement,
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
)

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    alignment: GeometryAlignment,
    element: GeometryElement,
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
)

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
    element: GeometryElement,
) = units.coordinateSystemSrid?.let(getTransformation).let { transformation ->
    ElementListing(
        planId = planId,
        planSource = planSource,
        fileName = fileName,
        coordinateSystemSrid = units.coordinateSystemSrid,
        coordinateSystemName = units.coordinateSystemName,
        trackNumberId = trackNumberId,
        trackNumberDescription = trackNumberDescription,
        alignmentId = alignment.id,
        alignmentName = alignment.name,
        elementId = element.id,
        elementType = TrackGeometryElementType.of(element.type),
        lengthMeters = round(element.calculatedLength, LENGTH_DECIMALS),
        start = getStartLocation(context, transformation, alignment, element),
        end = getEndLocation(context, transformation, alignment, element),
    )
}

private fun getLocation(
    context: GeocodingContext?,
    point: LayoutPoint,
    directionRads: Double,
) = ElementLocation(
    coordinate = point.round(COORDINATE_DECIMALS),
    address = context?.getAddress(point)?.first,
    directionGrads = round(radsToGrads(directionRads), DIRECTION_DECIMALS),
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
    cant = getStartCant(alignment, element)
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
    cant = getEndCant(alignment, element)
)

private fun collectLinkedElements(
    segments: List<LayoutSegment>,
    context: GeocodingContext?,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
) = segments
    .filter { segment -> overlapsAddressInterval(segment, context, startAddress, endAddress) }
    .map { s -> if (s.sourceId is IndexedId) s to s.sourceId else s to null }

private fun getAddress(context: GeocodingContext?, transformation: Transformation?, coordinate: Point) =
    if (context == null || transformation == null) null
    else context.getAddress(transformation.transform(coordinate), ADDRESS_DECIMALS)?.first

private fun getDirectionGrads(rads: Double) = round(radsToGrads(rads), DIRECTION_DECIMALS)

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

private fun overlapsAddressInterval(
    segment: LayoutSegment,
    context: GeocodingContext?,
    start: TrackMeter?,
    end: TrackMeter?,
): Boolean =
    (end == null || context != null && getStartAddress(segment, context)?.let { it < end } == true) &&
    (start == null || context != null && getEndAddress(segment, context)?.let { it > start } == true)

private fun getStartAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.points.first())?.first

private fun getEndAddress(segment: LayoutSegment, context: GeocodingContext) =
    context.getAddress(segment.points.last())?.first

private fun getAlignmentId(elementId: IndexedId<GeometryElement>) = IntId<GeometryAlignment>(elementId.parentId)
