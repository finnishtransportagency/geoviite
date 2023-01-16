package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.geometry.PlanSource.PAIKANNUSPALVELU
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.LocalizationKey
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant


const val INFRAMODEL_TRANSFORMATION_KEY_PARENT = "error.infra-model.transformation"

data class TransformationError(
    private val key: String,
    private val units: GeometryUnits,
): ValidationError {
    override val errorType = ErrorType.TRANSFORMATION_ERROR
    override val localizationKey = LocalizationKey("$INFRAMODEL_TRANSFORMATION_KEY_PARENT.$key")
    val srid = units.coordinateSystemSrid
    val coordinateSystemName = units.coordinateSystemName
}

@Service
class GeometryService @Autowired constructor(
    private val geometryDao: GeometryDao,
    private val heightTriangleDao: HeightTriangleDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val coordinateTransformationService: CoordinateTransformationService
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)


    fun getGeometryPlanAreas(boundingBox: BoundingBox): List<GeometryPlanArea> {
        logger.serviceCall("getGeometryPlanAreas", "bbox" to boundingBox)
        return geometryDao.fetchPlanAreas(boundingBox)
    }

    fun getPlanHeaders(
        sources: List<PlanSource> = listOf(),
        bbox: BoundingBox? = null,
        filter: ((GeometryPlanHeader) -> Boolean)? = null,
    ): List<GeometryPlanHeader> {
        logger.serviceCall(
            "getPlanHeaders",
            "sources" to sources, "bbox" to bbox, "filtered" to (filter != null)
        )
        return geometryDao.fetchPlanVersions(sources = sources, bbox = bbox)
            .map(geometryDao::fetchPlanHeader)
            .let { all -> filter?.let(all::filter) ?: all }
    }

    fun getPlanHeader(planId: IntId<GeometryPlan>): GeometryPlanHeader {
        logger.serviceCall("getPlanHeader", "planId" to planId)
        return geometryDao.fetchPlanVersion(planId).let(geometryDao::fetchPlanHeader)
    }

    fun getTrackLayoutPlan(
        geometryPlanId: IntId<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        return getTrackLayoutPlan(
            geometryPlan = getGeometryPlan(geometryPlanId),
            includeGeometryData = includeGeometryData,
            pointListStepLength = pointListStepLength,
        )
    }

    fun getTrackLayoutPlan(
        geometryPlan: GeometryPlan,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        logger.serviceCall(
            "getTrackLayoutPlan",
            "geometryPlanId" to geometryPlan.id,
            "includeGeometryData" to includeGeometryData,
            "pointListStepLength" to pointListStepLength,
        )
        val srid = geometryPlan.units.coordinateSystemSrid
        val planToLayoutTransformation = if (srid != null) coordinateTransformationService.getTransformation(srid, LAYOUT_SRID) else null
        if (planToLayoutTransformation == null) {
            logger.warn("Not converting plan to layout as there is no SRID: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            return null to TransformationError("srid-missing", geometryPlan.units)
        }

        val polygon = geometryPlan.getBoundingPolygonPoints(planToLayoutTransformation)

        return if (polygon.isEmpty()) {
            logger.warn("Not converting plan to layout as bounds could not be resolved: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            null to TransformationError("bounds-resolution-failed", geometryPlan.units)
        } else if (!polygon.all { point -> FINNISH_BORDERS.contains(point) }) {
            logger.warn("Not converting plan to layout as bounds are outside Finnish borders: id=${geometryPlan.id} file=${geometryPlan.fileName}")
            null to TransformationError("bounds-outside-finland", geometryPlan.units)
        } else try {
            toTrackLayout(
                geometryPlan = geometryPlan,
                heightTriangles = heightTriangleDao.fetchTriangles(polygon),
                planToLayout = planToLayoutTransformation,
                pointListStepLength = pointListStepLength,
                includeGeometryData = includeGeometryData,
            ) to null
        } catch (e: CoordinateTransformationException) {
            logger.warn("Could not convert plan coordinates: " +
                    "id=${geometryPlan.id} " +
                    "srid=${geometryPlan.units.coordinateSystemSrid} " +
                    "file=${geometryPlan.fileName}",
                e)
            null to TransformationError("coordinate-transformation-failed", geometryPlan.units)
        } catch (e: Exception) {
            logger.warn("Failed to convert plan to layout form: " +
                    "id=${geometryPlan.id} " +
                    "srid=${geometryPlan.units.coordinateSystemSrid} " +
                    "file=${geometryPlan.fileName}",
                e)
            null to TransformationError("plan-transformation-failed", geometryPlan.units)
        }
    }

    fun getGeometryElement(geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        logger.serviceCall("getGeometryElement", "geometryElementId" to geometryElementId)
        return geometryDao.fetchElement(geometryElementId)
    }

    fun getGeometryPlan(planId: IntId<GeometryPlan>): GeometryPlan {
        logger.serviceCall("getGeometryPlan", "planId" to planId)
        return geometryDao.fetchPlan(geometryDao.fetchPlanVersion(planId))
    }

    fun getGeometryPlanChangeTime(): Instant {
        logger.serviceCall("getGeometryPlanChangeTime")
        return geometryDao.fetchPlanChangeTime()
    }

    fun getProjects(): List<Project> {
        logger.serviceCall("getProjects")
        return geometryDao.fetchProjects()
    }

    fun createProject(project: Project): Project {
        logger.serviceCall("createProject", "project" to project)
        val projectId = geometryDao.insertProject(project)
        return geometryDao.getProject(projectId.id)
    }

    fun getAuthors(): List<Author> {
        logger.serviceCall("getAuthors")
        return geometryDao.fetchAuthors()
    }

    fun createAuthor(author: Author): Author {
        logger.serviceCall("createAuthor", "author" to author)
        val authorId = geometryDao.insertAuthor(author)
        return geometryDao.getAuthor(authorId.id)
    }

    fun getSwitch(switchId: IntId<GeometrySwitch>): GeometrySwitch {
        logger.serviceCall("getSwitch", "switchId" to switchId)
        return geometryDao.getSwitch(switchId)
    }

    fun getSwitchLayout(switchId: IntId<GeometrySwitch>): TrackLayoutSwitch? {
        logger.serviceCall("getSwitchLayout", "switchId" to switchId)
        val switch = getSwitch(switchId)
        val srid = geometryDao.getSwitchSrid(switchId)
            ?: throw IllegalStateException("Coordinate system not found for geometry switch $switchId!")
        val transformation = coordinateTransformationService.getTransformation(srid, LAYOUT_SRID)
        return toTrackLayoutSwitch(switch, transformation)
    }

    fun getKmPost(kmPostId: IntId<GeometryKmPost>): GeometryKmPost {
        logger.serviceCall("getKmPost", "kmPostId" to kmPostId)
        return geometryDao.getKmPost(kmPostId)
    }

    fun getKmPostSrid(id: IntId<GeometryKmPost>): Srid? {
        logger.serviceCall("getKmPostSrid", "id" to id)
        return geometryDao.getKmPostSrid(id)
    }

    fun getPlanFile(planId: IntId<GeometryPlan>): InfraModelFile {
        logger.serviceCall("getPlanFile", "planId" to planId)
        return geometryDao.getPlanFile(planId)
    }

    fun getLinkingSummaries(planIds: List<IntId<GeometryPlan>>): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        logger.serviceCall("getLinkingSummaries", "planIds" to planIds)
        return geometryDao.getLinkingSummaries(planIds)
    }

    fun getComparator(sortField: GeometryPlanSortField, sortOrder: SortOrder): Comparator<GeometryPlanHeader> =
        if (sortOrder == SortOrder.ASCENDING) getComparator(sortField)
        else getComparator(sortField).reversed()

    val plannedGeometryFirstComparator: Comparator<GeometryPlanHeader> =
        Comparator.comparing { h -> if (h.source == PAIKANNUSPALVELU) 1 else 0 }
    private fun getComparator(sortField: GeometryPlanSortField): Comparator<GeometryPlanHeader> {
        if (sortField == GeometryPlanSortField.LINKED_AT || sortField == GeometryPlanSortField.LINKED_BY) {
            val linkingSummaries = geometryDao.getLinkingSummaries(null)
            return plannedGeometryFirstComparator.then(if (sortField == GeometryPlanSortField.LINKED_BY)
                Comparator.comparing { h -> linkingSummaries[h.id]?.linkedByUsers ?: "" }
            else
                Comparator.comparing { h -> linkingSummaries[h.id]?.linkedAt ?: Instant.MIN }
            )
        }

        return plannedGeometryFirstComparator.then(when (sortField) {
            GeometryPlanSortField.ID -> Comparator.comparing { h -> h.id.intValue }
            GeometryPlanSortField.PROJECT_NAME -> Comparator.comparing { h -> h.project.name.toString().lowercase() }
            GeometryPlanSortField.TRACK_NUMBER -> {
                val trackNumbers = trackNumberService.mapById(PublishType.DRAFT)
                Comparator.comparing { h -> trackNumbers[h.trackNumberId]?.number?.toString()?.lowercase() ?: "" }
            }

            GeometryPlanSortField.KM_START -> Comparator.comparing { h -> h.kmNumberRange?.min ?: KmNumber.ZERO }
            GeometryPlanSortField.KM_END -> Comparator.comparing { h -> h.kmNumberRange?.max ?: KmNumber.ZERO }
            GeometryPlanSortField.PLAN_PHASE -> Comparator.comparing { h -> h.planPhase?.name ?: "" }
            GeometryPlanSortField.DECISION_PHASE -> Comparator.comparing { h -> h.decisionPhase?.name ?: "" }
            GeometryPlanSortField.CREATED_AT -> Comparator.comparing { h -> h.planTime ?: h.uploadTime }
            GeometryPlanSortField.UPLOADED_AT -> Comparator.comparing { h -> h.uploadTime }
            GeometryPlanSortField.FILE_NAME -> Comparator.comparing { h -> h.fileName.toString().lowercase() }
            GeometryPlanSortField.LINKED_AT -> throw IllegalArgumentException("should have handled LINKED_AT above")
            GeometryPlanSortField.LINKED_BY -> throw IllegalArgumentException("should have handled LINKED_BY above")
        })
    }

    fun getFilter(
        freeText: FreeText?,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): (header: GeometryPlanHeader) -> Boolean {
        val searchTerms = splitSearchTerms(freeText)
        return { header: GeometryPlanHeader ->
            trackNumbersMatch(header, trackNumberIds) && freeTextMatches(header, searchTerms)
        }
    }
}

private fun trackNumbersMatch(
    header: GeometryPlanHeader,
    trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
) = (trackNumberIds.isEmpty() || (header.trackNumberId?.let(trackNumberIds::contains) ?: false))

private fun freeTextMatches(
    header: GeometryPlanHeader,
    terms: List<String>,
) = terms.isEmpty() || terms.all { term -> header.searchParams.any { s -> s.contains(term) } }

private val whitespace = "\\s+".toRegex()
private fun splitSearchTerms(freeText: FreeText?): List<String> =
    freeText?.toString()
        ?.split(whitespace)
        ?.toList()
        ?.map { s -> s.lowercase().trim() }
        ?.filter(String::isNotBlank)
        ?: listOf()

enum class GeometryPlanSortField {
    ID,
    PROJECT_NAME,
    TRACK_NUMBER,
    KM_START,
    KM_END,
    PLAN_PHASE,
    DECISION_PHASE,
    CREATED_AT,
    UPLOADED_AT,
    FILE_NAME,
    LINKED_AT,
    LINKED_BY,
}

private val FINNISH_BORDERS = BoundingBox(
    x = Range(70265.0, 732722.0),
    y = Range(6610378.0, 7780971.0),
)
