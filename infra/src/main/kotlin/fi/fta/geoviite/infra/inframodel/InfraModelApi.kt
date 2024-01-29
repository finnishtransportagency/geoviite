package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.error.HasLocalizeMessageKey
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.XmlCharset
import java.time.Instant

data class ValidationResponse(
    val validationErrors: List<ValidationError>,
    val geometryPlan: GeometryPlan?,
    val planLayout: GeometryPlanLayout?,
    val source: PlanSource,
)

data class ExtraInfoParameters(
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val measurementMethod: MeasurementMethod?,
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
    val message: FreeTextWithNewLines?,
)

data class OverrideParameters(
    val coordinateSystemSrid: Srid?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val projectId: IntId<Project>?,
    val authorId: IntId<Author>?,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val createdDate: Instant?,
    val encoding: XmlCharset?,
    val source: PlanSource?,
)

fun tryParsing(source: PlanSource?, op: () -> ValidationResponse): ValidationResponse = try {
    op()
} catch (e: Exception) {
    logger.warn("Failed to parse InfraModel", e)
    ValidationResponse(
        validationErrors = listOf(
            ParsingError(
                if (e is HasLocalizeMessageKey) e.localizedMessageKey
                else LocalizationKey(INFRAMODEL_PARSING_KEY_GENERIC)
            ),
        ),
        geometryPlan = null,
        planLayout = null,
        source = source ?: PlanSource.GEOMETRIAPALVELU,
    )
}
