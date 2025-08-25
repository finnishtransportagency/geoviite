package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.error.HasLocalizedMessage
import fi.fta.geoviite.infra.geometry.Author
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometryValidationIssue
import fi.fta.geoviite.infra.geometry.PlanApplicability
import fi.fta.geoviite.infra.geometry.PlanDecisionPhase
import fi.fta.geoviite.infra.geometry.PlanName
import fi.fta.geoviite.infra.geometry.PlanPhase
import fi.fta.geoviite.infra.geometry.PlanSource
import fi.fta.geoviite.infra.geometry.Project
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.XmlCharset
import java.time.Instant

data class ValidationResponse(
    val geometryValidationIssues: List<GeometryValidationIssue>,
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
    val name: PlanName,
    val planApplicability: PlanApplicability?,
)

data class OverrideParameters(
    val coordinateSystemSrid: Srid?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val projectId: IntId<Project>?,
    val authorId: IntId<Author>?,
    val trackNumber: TrackNumber?,
    val createdDate: Instant?,
    val encoding: XmlCharset?,
    val source: PlanSource?,
)

fun tryParsing(source: PlanSource?, op: () -> ValidationResponse): ValidationResponse =
    try {
        op()
    } catch (e: Exception) {
        logger.warn("Failed to parse InfraModel", e)
        ValidationResponse(
            geometryValidationIssues =
                listOf(
                    ParsingError(
                        if (e is HasLocalizedMessage) e.localizationKey
                        else LocalizationKey.of(INFRAMODEL_PARSING_KEY_GENERIC)
                    )
                ),
            geometryPlan = null,
            planLayout = null,
            source = source ?: PlanSource.GEOMETRIAPALVELU,
        )
    }
