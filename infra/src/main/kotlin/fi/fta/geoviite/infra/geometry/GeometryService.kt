package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geometry.PlanSource.PAIKANNUSPALVELU
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant


@Service
class GeometryService @Autowired constructor(
    private val geometryDao: GeometryDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val planLayoutCache: PlanLayoutCache,
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
            .map(geometryDao::getPlanHeader)
            .let { all -> filter?.let(all::filter) ?: all }
    }

    fun getPlanHeader(planId: IntId<GeometryPlan>): GeometryPlanHeader {
        logger.serviceCall("getPlanHeader", "planId" to planId)
        return geometryDao.fetchPlanVersion(planId).let(geometryDao::getPlanHeader)
    }

    fun getManyPlanHeaders(planIds: List<IntId<GeometryPlan>>): List<GeometryPlanHeader> {
        logger.serviceCall("getManyPlanHeaders", "planIds" to planIds)
        return geometryDao.fetchManyPlanVersions(planIds).map(geometryDao::getPlanHeader)
    }

    fun getLayoutPlan(
        geometryPlanId: IntId<GeometryPlan>,
        includeGeometryData: Boolean = true,
        pointListStepLength: Int = 1,
    ): Pair<GeometryPlanLayout?, TransformationError?> {
        val planVersion = geometryDao.fetchPlanVersion(geometryPlanId)
        val (layout, error) = planLayoutCache.getPlanLayout(planVersion, includeGeometryData)
        return if (layout != null && includeGeometryData && pointListStepLength > 1) {
            simplifyPlanLayout(layout, pointListStepLength) to error
        } else layout to error
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

    fun getProject(id: IntId<Project>): Project {
        logger.serviceCall("getProject", "id" to id)
        return geometryDao.getProject(id)
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

        val fileAndSource = geometryDao.getPlanFile(planId)
        return if (fileAndSource.source == PAIKANNUSPALVELU) {
            InfraModelFile(
                fileNameWithSourcePrefixIfPaikannuspalvelu(fileAndSource.file.name, fileAndSource.source),
                fileAndSource.file.content
            )
        } else fileAndSource.file
    }

    private fun fileNameWithSourcePrefixIfPaikannuspalvelu(originalFileName: FileName, source: PlanSource): FileName =
        if (source == PAIKANNUSPALVELU) FileName("PAIKANNUSPALVELU_EPÄLUOTETTAVA_$originalFileName")
        else originalFileName

    fun getLinkingSummaries(planIds: List<IntId<GeometryPlan>>): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        logger.serviceCall("getLinkingSummaries", "planIds" to planIds)
        return geometryDao.getLinkingSummaries(planIds)
    }

    fun fetchDuplicateGeometryPlanHeader(newFile: InfraModelFile, source: PlanSource): GeometryPlanHeader? {
        logger.serviceCall("fetchDuplicateGeometryPlanHeader", "newFile" to newFile, "source" to source)
        return geometryDao.fetchDuplicateGeometryPlanVersion(newFile, source)?.let(geometryDao::getPlanHeader)
    }

    fun getElementListing(planId: IntId<GeometryPlan>, elementTypes: List<GeometryElementType>): List<ElementListing> {
        logger.serviceCall("getElementListing", "planId" to planId, "elementTypes" to elementTypes)
        val plan = geometryDao.fetchPlan(geometryDao.fetchPlanVersion(planId))
        val context = plan.trackNumberId?.let { tnId ->
            geocodingService.getGeocodingContext(OFFICIAL, tnId)
        }
        return toElementListing(context, coordinateTransformationService::getLayoutTransformation, plan, elementTypes)
    }

    fun getElementListingCsv(planId: IntId<GeometryPlan>, elementTypes: List<GeometryElementType>): Pair<FileName, ByteArray> {
        logger.serviceCall("getElementListingCsv", "planId" to planId, "elementTypes" to elementTypes)
        val plan = getPlanHeader(planId)
        val elementListing = getElementListing(planId, elementTypes)

        val csvFileContent = planElementListingToCsv(trackNumberService.list(OFFICIAL), elementListing)
        return FileName("$ELEMENT_LISTING ${plan.fileName}") to csvFileContent.toByteArray()
    }

    fun getElementListing(
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): List<ElementListing> {
        logger.serviceCall("getElementListing",
            "trackId" to trackId, "elementTypes" to elementTypes,
            "startAddress" to startAddress, "endAdress" to endAddress,
        )
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(OFFICIAL, trackId)
        return toElementListing(
            geocodingService.getGeocodingContext(OFFICIAL, track.trackNumberId),
            coordinateTransformationService::getLayoutTransformation,
            track,
            alignment,
            elementTypes,
            startAddress,
            endAddress,
            ::getHeaderAndAlignment,
        )
    }

    fun getElementListingCsv(
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): Pair<FileName, ByteArray> {
        logger.serviceCall("getElementListing",
            "trackId" to trackId, "elementTypes" to elementTypes,
            "startAddress" to startAddress, "endAdress" to endAddress,
        )
        val track = locationTrackService.getOrThrow(OFFICIAL, trackId)
        val elementListing = getElementListing(trackId, elementTypes, startAddress, endAddress)
        val csvFileContent = locationTrackElementListingToCsv(trackNumberService.list(OFFICIAL), elementListing)
        return FileName("$ELEMENT_LISTING ${track.name}") to csvFileContent.toByteArray()
    }

    private fun getHeaderAndAlignment(id: IntId<GeometryAlignment>): Pair<GeometryPlanHeader, GeometryAlignment> {
        val header = geometryDao.fetchAlignmentPlanVersion(id).let(geometryDao::getPlanHeader)
        val geometryAlignment = geometryDao.fetchAlignments(header.units, geometryAlignmentId = id).first()
        return header to geometryAlignment
    }

    fun getComparator(sortField: GeometryPlanSortField, sortOrder: SortOrder): Comparator<GeometryPlanHeader> =
        if (sortOrder == SortOrder.ASCENDING) getComparator(sortField)
        else getComparator(sortField).reversed()

    val plannedGeometryFirstComparator: Comparator<GeometryPlanHeader> =
        Comparator.comparing { h -> if (h.source == PAIKANNUSPALVELU) 1 else 0 }
    private fun getComparator(sortField: GeometryPlanSortField): Comparator<GeometryPlanHeader> {
        val trackNumbers by lazy { trackNumberService.mapById(PublishType.DRAFT) }
        val linkingSummaries by lazy { geometryDao.getLinkingSummaries(null) }
        return plannedGeometryFirstComparator.then(when (sortField) {
            GeometryPlanSortField.ID -> Comparator.comparing { h -> h.id.intValue }
            GeometryPlanSortField.PROJECT_NAME -> stringComparator { h -> h.project.name }
            GeometryPlanSortField.TRACK_NUMBER -> stringComparator { h -> trackNumbers[h.trackNumberId]?.number }
            GeometryPlanSortField.KM_START -> Comparator.comparing { h -> h.kmNumberRange?.min ?: KmNumber.ZERO }
            GeometryPlanSortField.KM_END -> Comparator.comparing { h -> h.kmNumberRange?.max ?: KmNumber.ZERO }
            GeometryPlanSortField.PLAN_PHASE -> stringComparator { h -> h.planPhase?.name }
            GeometryPlanSortField.DECISION_PHASE -> stringComparator { h -> h.decisionPhase?.name }
            GeometryPlanSortField.CREATED_AT -> Comparator.comparing { h -> h.planTime ?: h.uploadTime }
            GeometryPlanSortField.UPLOADED_AT -> Comparator.comparing { h -> h.uploadTime }
            GeometryPlanSortField.FILE_NAME -> stringComparator { h -> h.fileName }
            GeometryPlanSortField.LINKED_AT -> Comparator.comparing { h -> linkingSummaries[h.id]?.linkedAt ?: Instant.MIN }
            GeometryPlanSortField.LINKED_BY -> stringComparator { h -> linkingSummaries[h.id]?.linkedByUsers }
        })
    }

    private inline fun <reified T> stringComparator(crossinline getValue: (T) -> CharSequence?) =
        Comparator.comparing { h: T -> getValue(h)?.toString()?.lowercase() ?: "" }

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
