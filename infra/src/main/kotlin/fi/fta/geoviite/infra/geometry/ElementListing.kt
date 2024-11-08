package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IndexedId
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
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
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
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    track: LocationTrack,
    layoutAlignment: LayoutAlignment,
    trackNumber: TrackNumber?,
    elementTypes: List<TrackGeometryElementType>,
    startAddress: TrackMeter?,
    endAddress: TrackMeter?,
    getPlanHeaderAndAlignment: (id: IntId<GeometryAlignment>) -> Pair<GeometryPlanHeader, GeometryAlignment>,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
): List<ElementListing> {
    val linkedElementIds = collectLinkedElements(layoutAlignment.segments, context, startAddress, endAddress)
    val lengthOfSegmentsConnectedToSameElement =
        linkedElementIds.groupBy { it.second }.map { it.key to it.value.sumOf { (segment, _) -> segment.length } }
    val linkedAlignmentIds = linkedElementIds.mapNotNull { (_, id) -> id?.let(::getAlignmentId) }.distinct()
    val headersAndAlignments = linkedAlignmentIds.associateWith { id -> getPlanHeaderAndAlignment(id) }

    return linkedElementIds
        .mapNotNull { (segment, elementId) ->
            if (elementId == null) {
                if (elementTypes.contains(MISSING_SECTION))
                    toMissingElementListing(context, trackNumber, segment, track, getSwitchName)
                else null
            } else {
                val (planHeader, alignment) =
                    headersAndAlignments[getAlignmentId(elementId)]
                        ?: throw IllegalStateException(
                            "Failed to fetch geometry alignment for element: element=$elementId"
                        )
                val element =
                    alignment.elements.find { e -> e.id == elementId }
                        ?: throw IllegalStateException(
                            "Geometry element not found on its parent alignment: alignment=${alignment.id} element=$elementId"
                        )
                if (elementTypes.contains(TrackGeometryElementType.of(element.type))) {
                    toElementListing(
                        context,
                        getTransformation,
                        track,
                        planHeader,
                        alignment,
                        trackNumber,
                        element,
                        segment,
                        getSwitchName,
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
                    if (calculatedSegmentLength != null && listing.planId != null)
                        abs(calculatedSegmentLength - listing.lengthMeters.toDouble()) >
                            SEGMENT_AND_ELEMENT_LENGTH_MAX_DELTA
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
) =
    plan.alignments.flatMap { alignment ->
        alignment.elements
            .filter { element -> elementTypes.contains(element.type) }
            .map { element -> toElementListing(context, getTransformation, plan, alignment, element, getSwitchName) }
    }

private fun toMissingElementListing(
    context: GeocodingContext?,
    trackNumber: TrackNumber?,
    segment: LayoutSegment,
    locationTrack: LocationTrack,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
) =
    ElementListing(
        id = StringId("MEL_${segment.id}"),
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
        start = getLocation(context, segment.alignmentStart, segment.startDirection),
        end = getLocation(context, segment.alignmentEnd, segment.endDirection),
        locationTrackName = locationTrack.name,
        connectedSwitchName = segment.switchId?.let { id -> getSwitchName(id) },
        isPartial = false,
    )

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    locationTrack: LocationTrack,
    planHeader: GeometryPlanHeader,
    alignment: GeometryAlignment,
    trackNumber: TrackNumber?,
    element: GeometryElement,
    segment: LayoutSegment,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
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
        segment = segment,
        getSwitchName = getSwitchName,
        planTime = planHeader.planTime,
    )

private fun toElementListing(
    context: GeocodingContext?,
    getTransformation: (srid: Srid) -> Transformation,
    plan: GeometryPlan,
    alignment: GeometryAlignment,
    element: GeometryElement,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
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
        segment = null,
        getSwitchName = getSwitchName,
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
            "$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.element-type" to
                {
                    translateTrackGeometryElementType(it.elementType, translation)
                },
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
        .map { (key, fn) -> CsvEntry(translation.t(key), fn) }

fun translateTrackGeometryElementType(type: TrackGeometryElementType, translation: Translation) =
    when (type) {
        TrackGeometryElementType.LINE -> translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.line")
        TrackGeometryElementType.CURVE -> translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.curve")
        TrackGeometryElementType.CLOTHOID -> translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.clothoid")
        TrackGeometryElementType.BIQUADRATIC_PARABOLA ->
            translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.biquadratic-parabola")
        MISSING_SECTION -> translation.t("$ELEMENT_LIST_CSV_TRANSLATION_PREFIX.missing-section")
    }

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
    context: GeocodingContext?,
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
    segment: LayoutSegment?,
    getSwitchName: (IntId<TrackLayoutSwitch>) -> SwitchName,
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
            connectedSwitchName = segment?.switchId?.let { id -> getSwitchName(id) },
            isPartial = false,
            planTime = planTime,
        )
    }

private fun getLocation(context: GeocodingContext?, point: AlignmentPoint, directionRads: Double) =
    ElementLocation(
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
) =
    ElementLocation(
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
) =
    ElementLocation(
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
