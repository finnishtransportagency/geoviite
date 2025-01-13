package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LinearUnit
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geography.CoordinateSystemName
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.AngularUnit
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.projektivelho.PVDocument
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.Page
import java.time.Instant

enum class PlanSource {
    GEOMETRIAPALVELU,
    PAIKANNUSPALVELU,
}

/** GeometryPlanHeader is a lightweight object to be us ed as list items etc. */
data class GeometryPlanHeader(
    val id: IntId<GeometryPlan>,
    val version: RowVersion<GeometryPlan>,
    val project: Project,
    val fileName: FileName,
    val source: PlanSource,
    val trackNumber: TrackNumber?,
    val kmNumberRange: Range<KmNumber>?,
    val measurementMethod: MeasurementMethod?,
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val planTime: Instant?,
    val message: FreeTextWithNewLines?,
    val linkedAsPlanId: IntId<GeometryPlan>?,
    val uploadTime: Instant,
    val units: GeometryUnits,
    val author: String?,
    val hasProfile: Boolean,
    val hasCant: Boolean,
    val isHidden: Boolean,
    val name: PlanName,
) : Loggable {
    @get:JsonIgnore
    val searchParams: List<String> by lazy {
        listOfNotNull(fileName, project.name, message, name).map { o -> o.toString().lowercase() }
    }

    override fun toLog(): String = logFormat("version" to version, "name" to fileName, "source" to source)
}

/**
 * Plan is a design for a portion of railways/roads/etc. that can go through various states of completion.
 *
 * It is typically handled as a single file, and can consists of a number of parallel ways (alignments), each with their
 * own geometries.
 */
data class GeometryPlan(
    val source: PlanSource,
    val project: Project,
    val application: Application,
    val author: Author?,
    val planTime: Instant?,
    val uploadTime: Instant?,
    val units: GeometryUnits,
    val trackNumber: TrackNumber?,
    val trackNumberDescription: PlanElementName,
    val alignments: List<GeometryAlignment>,
    val switches: List<GeometrySwitch>,
    val kmPosts: List<GeometryKmPost>,
    val fileName: FileName,
    val pvDocumentId: IntId<PVDocument>?,
    val planPhase: PlanPhase?,
    val decisionPhase: PlanDecisionPhase?,
    val measurementMethod: MeasurementMethod?,
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
    val message: FreeTextWithNewLines?,
    val name: PlanName,
    val isHidden: Boolean = false,
    val id: DomainId<GeometryPlan> = StringId(),
    val dataType: DataType = DataType.TEMP,
) : Loggable {
    @get:JsonIgnore val bounds by lazy { boundingBoxCombining(alignments.mapNotNull { a -> a.bounds }) }

    override fun toLog(): String =
        logFormat(
            "id" to id,
            "name" to fileName,
            "source" to source,
            "alignments" to alignments.map(GeometryAlignment::toLog),
            "switches" to switches.map(GeometrySwitch::toLog),
            "kmPosts" to kmPosts.map(GeometryKmPost::toLog),
        )
}

data class GeometryPlanArea(val id: DomainId<GeometryPlan>, val name: PlanName, val polygon: List<Point>)

data class GeometryPlanUnits(val id: IntId<GeometryPlan>, val units: GeometryUnits)

data class GeometryUnits(
    val coordinateSystemSrid: Srid?,
    val coordinateSystemName: CoordinateSystemName?, // Redundant, if SRID is resolved, but it might not be
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val directionUnit: AngularUnit,
    val linearUnit: LinearUnit,
)

data class GeometryPlanLinkingSummary(
    val linkedAt: Instant?,
    val linkedByUsers: List<UserName>,
    val currentlyLinked: Boolean,
)

data class GeometryPlanLinkedItems(
    val locationTracks: List<IntId<LocationTrack>>,
    val switches: List<IntId<LayoutSwitch>>,
    val kmPosts: List<IntId<LayoutKmPost>>,
) {
    val isEmpty = locationTracks.isEmpty() && switches.isEmpty() && kmPosts.isEmpty()
}

data class GeometryPlanHeadersSearchResult(
    val planHeaders: Page<GeometryPlanHeader>,
    val remainingIds: List<IntId<GeometryPlan>>,
)
