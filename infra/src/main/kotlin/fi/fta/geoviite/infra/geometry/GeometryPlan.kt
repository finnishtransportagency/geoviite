package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.projektivelho.PVDocument
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import java.time.Instant


enum class PlanSource {
    GEOMETRIAPALVELU,
    PAIKANNUSPALVELU,
}

/**
 * GeometryPlanHeader is a lightweight object to be us
 * ed as list items etc.
 */
data class GeometryPlanHeader(
    val id: IntId<GeometryPlan>,
    val project: Project,
    val fileName: FileName,
    val source: PlanSource,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val kmNumberRange: Range<KmNumber>?,
    val measurementMethod: MeasurementMethod?,
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val planTime: Instant?,
    val message: FreeText?,
    val linkedAsPlanId: IntId<GeometryPlan>?,
    val uploadTime: Instant,
    val units: GeometryUnits,
    val author: String?,
    val hasProfile: Boolean,
    val hasCant: Boolean,
) {
    @get:JsonIgnore
    val searchParams: List<String> by lazy {
        listOfNotNull(fileName, project.name, message).map { o -> o.toString().lowercase() }
    }
}

/**
 * Plan is a design for a portion of railways/roads/etc. that can go through various states of completion.
 *
 * It is typically handled as a single file, and can consists of a number of parallel ways (alignments), each with their own geometries.
 */
data class GeometryPlan(
    val source: PlanSource,
    val project: Project,
    val application: Application,
    val author: Author?,
    val planTime: Instant?,
    val uploadTime: Instant?,
    val units: GeometryUnits,
    val trackNumberId: IntId<TrackLayoutTrackNumber>?,
    val trackNumberDescription: PlanElementName,
    val alignments: List<GeometryAlignment>,
    val switches: List<GeometrySwitch>,
    val kmPosts: List<GeometryKmPost>,
    val fileName: FileName,
    val pvDocumentId: IntId<PVDocument>?,
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val measurementMethod: MeasurementMethod?,
    val message: FreeText?,
    val id: DomainId<GeometryPlan> = StringId(),
    val dataType: DataType = DataType.TEMP,
) {
    @get:JsonIgnore
    val bounds by lazy { boundingBoxCombining(alignments.mapNotNull { a -> a.bounds }) }
}

data class GeometryPlanArea(
    val id: DomainId<GeometryPlan>,
    val fileName: FileName,
    val polygon: List<Point>,
)

data class GeometryPlanUnits(
    val id: IntId<GeometryPlan>,
    val units: GeometryUnits,
)

data class GeometryUnits(
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?, // Redundant, if SRID is resolved, but it might not be
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val directionUnit: AngularUnit,
    val linearUnit: LinearUnit,
)

data class GeometryPlanLinkingSummary(
    val linkedAt: Instant,
    val linkedByUsers: String,
)
