package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.util.FreeText
import java.math.BigDecimal

/**
 * A geometry alignment is a segment of railway, described as:
 * - center-line geometry in x-y plane (single 2D line on map)
 * - vertical profile in length-height plane
 * - cants (tilt) as point-values with linear transition in between
 */
data class GeometryAlignment(
    val name: AlignmentName,
    val description: FreeText?,
    val oidPart: FreeText?,
    val state: PlanState?,
    val featureTypeCode: FeatureTypeCode?,
    @JsonIgnore val staStart: BigDecimal,
    val elements: List<GeometryElement>,
    val profile: GeometryProfile? = null,
    val cant: GeometryCant? = null,
    val id: DomainId<GeometryAlignment> = StringId(),
) : Loggable {
    @get:JsonIgnore val bounds by lazy { boundingBoxAroundPointsOrNull(elements.flatMap { e -> e.bounds }) }

    fun getElementAt(distance: Double) =
        foldElementLengths(elements)
            .findLast { element -> element.first <= distance }
            ?.let { match ->
                val distanceLeft = distance - match.first
                if (distanceLeft <= match.second.calculatedLength) match else null
            }

    fun getCoordinateAt(distance: Double) =
        getElementAt(distance)?.let { match -> match.second.getCoordinateAt(distance - match.first) }

    fun stationValueNormalized(station: Double) = station - (elements.firstOrNull()?.staStart?.toDouble() ?: 0.0)

    fun getElementStationRangeWithinAlignment(elementId: DomainId<GeometryElement>) =
        foldElementLengths(elements)
            .find { elementAndLength -> elementAndLength.second.id == elementId }
            ?.let { match -> match.first..(match.first + match.second.calculatedLength) }
            ?: throw IllegalArgumentException("Element not found from alignment")

    override fun toLog(): String = logFormat("id" to id, "name" to name, "elements" to elements.size)
}

private fun foldElementLengths(elements: List<GeometryElement>) =
    elements.fold(mutableListOf<Pair<Double, GeometryElement>>()) { acc, element ->
        val prev = acc.lastOrNull()
        val lengthUntilElementStart = prev?.let { prev.first + prev.second.calculatedLength } ?: 0.0
        acc.add(lengthUntilElementStart to element)
        acc
    }
