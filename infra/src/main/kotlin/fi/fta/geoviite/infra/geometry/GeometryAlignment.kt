package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
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
    val trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    val id: DomainId<GeometryAlignment> = StringId(),
) {
    @get:JsonIgnore
    val bounds by lazy { boundingBoxAroundPointsOrNull(elements.flatMap { e -> e.bounds }) }

    private fun foldElementLengths(elements: List<GeometryElement>) =
        elements.fold(mutableListOf<Pair<Double, GeometryElement>>()) { acc, element ->
            if (acc.isEmpty()) acc.add(0.0 to element)
            else acc.add(acc.last().first + acc.last().second.calculatedLength to element)
            acc
        }

    fun getCoordinateAt(distance: Double) =
        foldElementLengths(elements).findLast { element -> element.first < distance }
            .let { match -> match?.second?.getCoordinateAt(distance - match.first) }

    fun stationValueNormalized(station: Double) =
        station - (elements.firstOrNull()?.staStart?.toDouble() ?: 0.0)
}
