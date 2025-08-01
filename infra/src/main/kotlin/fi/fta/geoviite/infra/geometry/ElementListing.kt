package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.formatTrackMeter
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.TrackGeometryElementType.MISSING_SECTION
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.RoundedPoint
import fi.fta.geoviite.infra.math.radsMathToGeo
import fi.fta.geoviite.infra.math.radsToGrads
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.printCsv
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

const val COORDINATE_DECIMALS = 3
const val ADDRESS_DECIMALS = 3
const val LENGTH_DECIMALS = 3
const val DIRECTION_DECIMALS = 6
const val CANT_DECIMALS = 6

const val SEGMENT_AND_ELEMENT_LENGTH_MAX_DELTA = 1.0

const val ELEMENT_LIST_CSV_TRANSLATION_PREFIX = "data-products.element-list.csv"

val elementListShortDateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

data class ElementListing(
    val id: StringId<ElementListing>,
    val planId: DomainId<GeometryPlan>?,
    val planSource: PlanSource?,
    val fileName: FileName?,
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?,
    val trackNumber: TrackNumber?,
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
    val planTime: Instant? = null,
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
    MISSING_SECTION;

    companion object {
        fun of(elementType: GeometryElementType): TrackGeometryElementType =
            when (elementType) {
                GeometryElementType.LINE -> LINE
                GeometryElementType.CURVE -> CURVE
                GeometryElementType.CLOTHOID -> CLOTHOID
                GeometryElementType.BIQUADRATIC_PARABOLA -> BIQUADRATIC_PARABOLA
            }
    }
}

fun toElementListing(
    context: GeocodingContext<ReferenceLineM>?,
    getTransformation: (srid: Srid) -> Transformation,
    track: LocationTrack,
    geometry: LocationTrackGeometry,
    trackNumber: TrackNumber?,
    elementTypes: List<TrackGeometryElementType>,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
    getPlanHeaderAndAlignment: (id: IntId<GeometryAlignment>) -> Pair<GeometryPlanHeader, GeometryAlignment>,
    getSwitchName: (IntId<LayoutSwitch>) -> SwitchName,
): List<ElementListing> {
    val linkedElements = collectLinkedElements(geometry, context, startAddress, endAddress)
    val lengthOfSegmentsConnectedToSameElement =
        linkedElements.groupBy { e -> e.elementId }.map { (key, value) -> key to value.sumOf { e -> e.segment.length } }
    val linkedAlignmentIds = linkedElements.mapNotNull { e -> e.alignmentId }.distinct()
    val headersAndAlignments = linkedAlignmentIds.associateWith { id -> getPlanHeaderAndAlignment(id) }

    return linkedElements
        .mapNotNull { linked ->
            if (linked.elementId == null || linked.alignmentId == null) {
                if (elementTypes.contains(MISSING_SECTION))
                    toMissingElementListing(
                        context,
                        trackNumber,
                        linked.idString,
                        linked.segment,
                        track,
                        getEdgeSwitchName(linked.edge, getSwitchName),
                    )
                else null
            } else {
                val (planHeader, alignment) =
                    requireNotNull(linked.alignmentId?.let(headersAndAlignments::get)) {
                        "Failed to fetch geometry alignment for element: linked=$linked"
                    }
                val element =
                    requireNotNull(alignment.elements.find { e -> e.id == linked.elementId }) {
                        "Geometry element not found on its parent alignment: alignment=${alignment.id} linked=$linked"
                    }
                if (elementTypes.contains(TrackGeometryElementType.of(element.type))) {
                    toElementListing(
                        context,
                        getTransformation,
                        track,
                        planHeader,
                        alignment,
                        trackNumber,
                        element,
                        getEdgeSwitchName(linked.edge, getSwitchName),
                    )
                } else {
                    null
                }
            }
        }
        .distinctBy(ElementListing::id)
        .map { listing ->
            val calculatedSegmentLength =
                lengthOfSegmentsConnectedToSameElement.find { (elementId, _) -> elementId == listing.elementId }?.second
            listing.copy(
                isPartial =
                    calculatedSegmentLength != null &&
                        listing.planId != null &&
                        abs(calculatedSegmentLength - listing.lengthMeters.toDouble()) >
                            SEGMENT_AND_ELEMENT_LENGTH_MAX_DELTA
            )
        }
}

fun getEdgeSwitchName(edge: LayoutEdge, getSwitchName: (IntId<LayoutSwitch>) -> SwitchName): SwitchName? =
    (edge.startNode.switchIn ?: edge.endNode.switchIn)?.let { link -> getSwitchName(link.id) }

fun toElementListing(
    context: GeocodingContext<*>?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    elementTypes: List<GeometryElementType>,
) =
    plan.alignments.flatMap { alignment ->
        alignment.elements
            .filter { element -> elementTypes.contains(element.type) }
            .map { element -> toElementListing(context, getTransformation, plan, alignment, element) }
    }

private fun toMissingElementListing(
    context: GeocodingContext<ReferenceLineM>?,
    trackNumber: TrackNumber?,
    identifier: String,
    segment: ISegment,
    locationTrack: LocationTrack,
    switchName: SwitchName?,
) =
    ElementListing(
        id = StringId("MEL_${identifier}"),
        planId = null,
        planSource = null,
        fileName = null,
        coordinateSystemSrid = LAYOUT_SRID,
        coordinateSystemName = null,
        trackNumber = trackNumber,
        trackNumberDescription = null,
        alignmentId = null,
        alignmentName = null,
        elementId = null,
        elementType = MISSING_SECTION,
        lengthMeters = round(segment.length, LENGTH_DECIMALS),
        start = getLocation(context, segment.segmentStart, segment.startDirection),
        end = getLocation(context, segment.segmentEnd, segment.endDirection),
        locationTrackName = locationTrack.name,
        connectedSwitchName = switchName,
        isPartial = false,
    )

private fun toElementListing(
    context: GeocodingContext<*>?,
    getTransformation: (srid: Srid) -> Transformation,
    locationTrack: LocationTrack,
    planHeader: GeometryPlanHeader,
    alignment: GeometryAlignment,
    trackNumber: TrackNumber?,
    element: GeometryElement,
    switchName: SwitchName?,
) =
    elementListing(
        context = context,
        getTransformation = getTransformation,
        planId = planHeader.id,
        planSource = planHeader.source,
        fileName = planHeader.fileName,
        units = planHeader.units,
        trackNumber = trackNumber,
        trackNumberDescription = null,
        alignment = alignment,
        element = element,
        locationTrack = locationTrack,
        linkedSwitch = switchName,
        planTime = planHeader.planTime,
    )

private fun toElementListing(
    context: GeocodingContext<*>?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    alignment: GeometryAlignment,
    element: GeometryElement,
) =
    elementListing(
        context = context,
        getTransformation = getTransformation,
        planId = plan.id,
        planSource = plan.source,
        fileName = plan.fileName,
        units = plan.units,
        trackNumber = plan.trackNumber,
        trackNumberDescription = plan.trackNumberDescription,
        alignment = alignment,
        element = element,
        locationTrack = null,
        linkedSwitch = element.switchId?.let { sId -> plan.switches.find { s -> s.id == sId } }?.name,
        planTime = plan.planTime,
    )

fun planElementListingToCsv(elementListing: List<ElementListing>, translation: Translation) =
    printCsv(planCsvEntries(translation), elementListing)

fun locationTrackElementListingToCsv(elementListing: List<ElementListing>, translation: Translation) =
    printCsv(locationTrackCsvEntries(translation), elementListing)

private fun trackNumberCsvEntry(translation: Translation) =
    CsvEntry<ElementListing>(translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.track-number")) { it.trackNumber }

private fun commonElementListingCsvEntries(translation: Translation): List<CsvEntry<ElementListing>> =
    mapOf<String, (item: ElementListing) -> Any?>(
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.plan-track" to { it.alignmentName },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.element-type" to { translation.enum(it.elementType) },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.track-address-start" to
                {
                    it.start.address?.let { address -> formatTrackMeter(address.kmNumber, address.meters) }
                },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.track-address-end" to
                {
                    it.end.address?.let { address -> formatTrackMeter(address.kmNumber, address.meters) }
                },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.crs" to { it.coordinateSystemSrid ?: it.coordinateSystemName },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.location-start-e" to { it.start.coordinate.roundedX.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.location-start-n" to { it.start.coordinate.roundedY.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.location-end-e" to { it.end.coordinate.roundedX.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.location-end-n" to { it.end.coordinate.roundedY.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.length" to { it.lengthMeters },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.radius-start" to { it.start.radiusMeters?.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.radius-end" to { it.end.radiusMeters?.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.cant-start" to { it.start.cant?.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.cant-end" to { it.end.cant?.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.direction-start" to { it.start.directionGrads.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.direction-end" to { it.end.directionGrads.toPlainString() },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.plan-name" to { it.fileName },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.plan-source" to { it.planSource },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.plan-time" to
                {
                    it.planTime?.let { planTime -> elementListShortDateFormatter.format(planTime) } ?: ""
                },
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.remarks" to { remarks(it, translation) },
        )
        .map { (key, fn) -> CsvEntry(translation.t(key), fn) }

fun locationTrackCsvEntries(translation: Translation) =
    listOf(
        trackNumberCsvEntry(translation),
        CsvEntry(translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.location-track")) { it.locationTrackName },
    ) + commonElementListingCsvEntries(translation)

fun planCsvEntries(translation: Translation) =
    listOf(trackNumberCsvEntry(translation)) + commonElementListingCsvEntries(translation)

private fun remarks(elementListing: ElementListing, translation: Translation) =
    listOfNotNull(
            if (elementListing.isPartial) translation.t("data-products.element-list.remarks.is-partial") else null,
            if (elementListing.connectedSwitchName != null)
                translation.t(
                    "data-products.element-list.remarks.connected-to-switch",
                    LocalizationParams(mapOf("switchName" to elementListing.connectedSwitchName.toString())),
                )
            else null,
        )
        .joinToString(separator = ", ")

private fun elementListing(
    context: GeocodingContext<*>?,
    getTransformation: (srid: Srid) -> Transformation,
    planId: DomainId<GeometryPlan>,
    planSource: PlanSource?,
    fileName: FileName,
    units: GeometryUnits,
    trackNumber: TrackNumber?,
    trackNumberDescription: PlanElementName?,
    alignment: GeometryAlignment,
    locationTrack: LocationTrack?,
    element: GeometryElement,
    linkedSwitch: SwitchName?,
    planTime: Instant?,
) =
    units.coordinateSystemSrid?.let(getTransformation).let { transformation ->
        val start = getStartLocation(context, transformation, alignment, element)
        val end = getEndLocation(context, transformation, alignment, element)
        ElementListing(
            id = StringId("EL_${element.id}"),
            planId = planId,
            planSource = planSource,
            fileName = fileName,
            coordinateSystemSrid = units.coordinateSystemSrid,
            coordinateSystemName = units.coordinateSystemName,
            trackNumber = trackNumber,
            trackNumberDescription = trackNumberDescription,
            alignmentId = alignment.id,
            alignmentName = alignment.name,
            locationTrackName = locationTrack?.name,
            elementId = element.id,
            elementType = TrackGeometryElementType.of(element.type),
            lengthMeters = round(element.calculatedLength, LENGTH_DECIMALS),
            start = start,
            end = end,
            connectedSwitchName = linkedSwitch,
            isPartial = false,
            planTime = planTime,
        )
    }

private fun getLocation(context: GeocodingContext<*>?, point: SegmentPoint, directionRads: Double) =
    ElementLocation(
        coordinate = point.round(COORDINATE_DECIMALS),
        address = context?.getAddress(point)?.first,
        directionGrads = getDirectionGrads(directionRads),
        radiusMeters = null,
        cant = point.cant?.let { c -> round(c, CANT_DECIMALS) },
    )

private fun getStartLocation(
    context: GeocodingContext<*>?,
    transformation: Transformation?,
    alignment: GeometryAlignment,
    element: GeometryElement,
) =
    ElementLocation(
        coordinate = element.start.round(COORDINATE_DECIMALS),
        address = getAddress(context, transformation, element.start),
        directionGrads = getDirectionGrads(element.startDirectionRads),
        radiusMeters = getStartRadius(element),
        cant = getStartCant(alignment, element),
    )

private fun getEndLocation(
    context: GeocodingContext<*>?,
    transformation: Transformation?,
    alignment: GeometryAlignment,
    element: GeometryElement,
) =
    ElementLocation(
        coordinate = element.end.round(COORDINATE_DECIMALS),
        address = getAddress(context, transformation, element.end),
        directionGrads = getDirectionGrads(element.endDirectionRads),
        radiusMeters = getEndRadius(element),
        cant = getEndCant(alignment, element),
    )

private fun getAddress(context: GeocodingContext<*>?, transformation: Transformation?, coordinate: Point) =
    if (context == null || transformation == null) null
    else context.getAddress(transformation.transform(coordinate), ADDRESS_DECIMALS)?.first

private fun getDirectionGrads(rads: Double) = round(radsToGrads(radsMathToGeo(rads)), DIRECTION_DECIMALS)

private fun getStartRadius(element: GeometryElement) =
    when (element) {
        is GeometryLine -> null
        is GeometryCurve -> element.radius
        is GeometrySpiral -> element.radiusStart
    }

private fun getEndRadius(element: GeometryElement) =
    when (element) {
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
    // Cant station values are alignment m-values, calculated from 0 (ignoring alignment
    // station-start)
    alignment.cant?.getCantValue(locationDistance)?.let { v -> round(v, CANT_DECIMALS) }
