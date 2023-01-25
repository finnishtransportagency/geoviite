import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.radsToGrads
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import java.math.BigDecimal

const val ADDRESS_DECIMALS = 3
const val LENGTH_DECIMALS = 3
const val DIRECTION_DECIMALS = 6
const val CANT_DECIMALS = 6

data class ElementListing(
    val planId: DomainId<GeometryPlan>,
    val fileName: FileName,
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?,

    val trackNumberId: IntId<TrackLayoutTrackNumber>?,

    val alignmentId: DomainId<GeometryAlignment>,
    val alignmentName: AlignmentName,

    val elementType: GeometryElementType,
    val lengthMeters: BigDecimal,

    val start: ElementLocation,
    val end: ElementLocation,
)

data class ElementLocation(
    val coordinate: Point,
    val address: TrackMeter?,
    val directionGrads: BigDecimal,
    val radiusMeters: BigDecimal?,
    val cant: BigDecimal?,
)

fun toElementListing(
    geocodingContext: GeocodingContext?,
    locationTrack: LocationTrack,
    plansAndAlignments: List<Pair<GeometryPlanHeader, GeometryAlignment>>,
    linkedElementIds: List<DomainId<GeometryElement>>,
    elementTypes: List<GeometryElementType>,
) = plansAndAlignments.flatMap { (plan, alignment) ->
    alignment.elements
        .filter { e -> elementTypes.contains(e.type) && linkedElementIds.contains(e.id) }
        .map { element -> toElementListing(geocodingContext, locationTrack, plan, alignment, element) }
}

fun toElementListing(
    geocodingContext: GeocodingContext?,
    plan: GeometryPlan,
    elementTypes: List<GeometryElementType>,
) = plan.alignments.flatMap { alignment ->
    alignment.elements
        .filter { e -> elementTypes.contains(e.type) }
        .map { e -> toElementListing(geocodingContext, plan, alignment, e) }
}

fun toElementListing(
    geocodingContext: GeocodingContext?,
    locationTrack: LocationTrack,
    planHeader: GeometryPlanHeader,
    alignment: GeometryAlignment,
    element: GeometryElement,
) = elementListing(
    geocodingContext = geocodingContext,
    planId = planHeader.id,
    fileName = planHeader.fileName,
    coordinateSystemSrid = planHeader.units.coordinateSystemSrid,
    coordinateSystemName = planHeader.units.coordinateSystemName,
    trackNumberId = locationTrack.trackNumberId,
    alignment = alignment,
    element = element,
)

fun toElementListing(
    geocodingContext: GeocodingContext?,
    plan: GeometryPlan,
    alignment: GeometryAlignment,
    element: GeometryElement,
) = elementListing(
    geocodingContext = geocodingContext,
    planId = plan.id,
    fileName = plan.fileName,
    coordinateSystemSrid = plan.units.coordinateSystemSrid,
    coordinateSystemName = plan.units.coordinateSystemName,
    trackNumberId = plan.trackNumberId,
    alignment = alignment,
    element = element,
)

fun elementListing(
    geocodingContext: GeocodingContext?,
    planId: DomainId<GeometryPlan>,
    fileName: FileName,
    coordinateSystemSrid: Srid?,
    coordinateSystemName: CoordinateSystemName?,
    trackNumberId: IntId<TrackLayoutTrackNumber>?,
    alignment: GeometryAlignment,
    element: GeometryElement,
) = ElementListing(
    planId = planId,
    fileName = fileName,
    coordinateSystemSrid = coordinateSystemSrid,
    coordinateSystemName = coordinateSystemName,
    trackNumberId = trackNumberId,
    alignmentId = alignment.id,
    alignmentName = alignment.name,
    elementType = element.type,
    lengthMeters = round(element.calculatedLength, LENGTH_DECIMALS),
    start = getStartLocation(geocodingContext, alignment, element),
    end = getEndLocation(geocodingContext, alignment, element),
)

fun getStartLocation(geocodingContext: GeocodingContext?, alignment: GeometryAlignment, element: GeometryElement) =
    ElementLocation(
        coordinate = element.start,
        address = getAddress(geocodingContext, element.start),
        directionGrads = getDirectionGrads(element.startDirectionRads),
        radiusMeters = getStartRadius(element),
        cant = getStartCant(alignment, element)
    )

fun getEndLocation(geocodingContext: GeocodingContext?, alignment: GeometryAlignment, element: GeometryElement) =
    ElementLocation(
        coordinate = element.end,
        address = getAddress(geocodingContext, element.end),
        directionGrads = getDirectionGrads(element.endDirectionRads),
        radiusMeters = getEndRadius(element),
        cant = getEndCant(alignment, element)
    )

private fun getAddress(geocodingContext: GeocodingContext?, coordinate: Point) =
    geocodingContext?.getAddress(coordinate, ADDRESS_DECIMALS)?.first

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
