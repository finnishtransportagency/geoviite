package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.DeletingFailureException
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.geometry.PlanSource.PAIKANNUSPALVELU
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.DatabaseLock.ELEMENT_LIST_GEN
import fi.fta.geoviite.infra.integration.DatabaseLock.VERTICAL_GEOMETRY_LIST_GEN
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.ElementListingFile
import fi.fta.geoviite.infra.tracklayout.ElementListingFileDao
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.toAlignmentHeader
import fi.fta.geoviite.infra.tracklayout.toTrackLayoutSwitch
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsLastComparator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import withUser
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.stream.Collectors

val elementListingGenerationUser = UserName.of("ELEMENT_LIST_GEN")
val verticalGeometryListingGenerationUser = UserName.of("VERT_GEOM_LIST_GEN")

@Service
class GeometryService @Autowired constructor(
    private val geometryDao: GeometryDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
    private val planLayoutCache: PlanLayoutCache,
    private val layoutAlignmentDao: LayoutAlignmentDao,
    private val switchService: LayoutSwitchService,
    private val heightTriangleDao: HeightTriangleDao,
    private val elementListingFileDao: ElementListingFileDao,
    private val verticalGeometryListingFileDao: VerticalGeometryListingFileDao,
    private val lockDao: LockDao,
    private val localizationService: LocalizationService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private fun runElementListGeneration(op: () -> Unit) =
        runWithLock(elementListingGenerationUser, ELEMENT_LIST_GEN, Duration.ofHours(1L)) {
            val lastFileUpdate = elementListingFileDao.getLastFileListingTime()
            if (Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) { op() }
        }

    private fun runVerticalGeometryListGeneration(op: () -> Unit) =
        runWithLock(verticalGeometryListingGenerationUser, VERTICAL_GEOMETRY_LIST_GEN, Duration.ofHours(1L)) {
            val lastFileUpdate = verticalGeometryListingFileDao.getLastFileListingTime()
            if (Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) { op() }
        }

    private fun runWithLock(userName: UserName, lock: DatabaseLock, timeout: Duration, op: () -> Unit) =
        withUser(userName) { lockDao.runWithLock(lock, timeout) { op() } }

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
            "getPlanHeaders", "sources" to sources, "bbox" to bbox, "filtered" to (filter != null)
        )
        return geometryDao
            .fetchPlanHeaders(sources = sources, bbox = bbox)
            .let { all -> filter?.let(all::filter) ?: all }
    }

    fun getPlanHeader(planId: IntId<GeometryPlan>): GeometryPlanHeader {
        logger.serviceCall("getPlanHeader", "planId" to planId)
        return geometryDao.getPlanHeader(planId)
    }

    fun getManyPlanHeaders(planIds: List<IntId<GeometryPlan>>): List<GeometryPlanHeader> {
        logger.serviceCall("getManyPlanHeaders", "planIds" to planIds)
        return geometryDao.getPlanHeaders(planIds)
    }

    fun getGeometryElement(geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        logger.serviceCall("getGeometryElement", "geometryElementId" to geometryElementId)
        return geometryDao.fetchElement(geometryElementId)
    }

    @Transactional(readOnly = true)
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

    fun getProjectChangeTime(): Instant {
        logger.serviceCall("getProjectChangeTime")
        return geometryDao.fetchProjectChangeTime()
    }

    fun getAuthorChangeTime(): Instant {
        logger.serviceCall("getAuthorChangeTime")
        return geometryDao.fetchAuthorChangeTime()
    }

    fun getProject(id: IntId<Project>): Project {
        logger.serviceCall("getProject", "id" to id)
        return geometryDao.getProject(id)
    }

    fun createProject(project: Project): IntId<Project> {
        logger.serviceCall("createProject", "project" to project)
        return geometryDao.insertProject(project).id
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

    @Transactional(readOnly = true)
    fun fetchDuplicateGeometryPlanHeader(newFile: InfraModelFile, source: PlanSource): GeometryPlanHeader? {
        logger.serviceCall("fetchDuplicateGeometryPlanHeader", "newFile" to newFile, "source" to source)
        return geometryDao.fetchDuplicateGeometryPlanVersion(newFile, source)?.let(geometryDao::getPlanHeader)
    }

    private fun getSwitchName(context: LayoutContext, switchId: IntId<TrackLayoutSwitch>): SwitchName =
        switchService.get(context, switchId)?.name ?: SwitchName("-")

    @Transactional(readOnly = true)
    fun getElementListing(
        planId: IntId<GeometryPlan>,
        elementTypes: List<GeometryElementType>,
    ): List<ElementListing> {
        logger.serviceCall(
            "getElementListing",
            "planId" to planId,
            "elementTypes" to elementTypes,
        )
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext = plan.trackNumber
            ?.let { geocodingService.getGeocodingContext(plan.trackNumber, planVersion) }

        return toElementListing(
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            plan,
            elementTypes,
        ) { id -> getSwitchName(MainLayoutContext.official, id) }
    }

    @Transactional(readOnly = true)
    fun getElementListingCsv(
        planId: IntId<GeometryPlan>,
        elementTypes: List<GeometryElementType>,
        lang: LocalizationLanguage,
    ): ElementListingFile {
        logger.serviceCall("getElementListingCsv", "planId" to planId, "elementTypes" to elementTypes, "lang" to lang)
        val plan = getPlanHeader(planId)
        val elementListing = getElementListing(planId, elementTypes)
        val translation = localizationService.getLocalization(lang)

        val csvFileContent = planElementListingToCsv(elementListing, translation)
        return ElementListingFile(
            FileName("${translation.t("data-products.element-list.element-list-title")} ${plan.fileName}"),
            csvFileContent,
        )
    }

    @Transactional(readOnly = true)
    fun getElementListing(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        trackNumber: TrackNumber?,
        geocodingContext: GeocodingContext?,
    ): List<ElementListing> {
        logger.serviceCall("getElementListing", "locationTrack" to locationTrack, "alignment" to alignment)
        return toElementListing(
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            locationTrack,
            alignment,
            trackNumber,
            TrackGeometryElementType.entries,
            null,
            null,
            ::getHeaderAndAlignment,
        ) { id -> getSwitchName(MainLayoutContext.official, id) }
    }

    @Transactional(readOnly = true)
    fun getElementListing(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): List<ElementListing> {
        logger.serviceCall(
            "getElementListing",
            "layoutContext" to layoutContext,
            "trackId" to trackId,
            "elementTypes" to elementTypes,
            "startAddress" to startAddress,
            "endAddress" to endAddress,
        )
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(layoutContext, trackId)
        val trackNumber = trackNumberService.get(layoutContext, track.trackNumberId)?.number
        return toElementListing(
            geocodingService.getGeocodingContext(layoutContext, track.trackNumberId),
            coordinateTransformationService::getLayoutTransformation,
            track,
            alignment,
            trackNumber,
            elementTypes,
            startAddress,
            endAddress,
            ::getHeaderAndAlignment,
        ) { id -> getSwitchName(layoutContext, id) }
    }

    @Transactional(readOnly = true)
    fun getElementListingCsv(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
        lang: LocalizationLanguage,
    ): ElementListingFile {
        logger.serviceCall(
            "getElementListing",
            "layoutContext" to layoutContext,
            "trackId" to trackId,
            "elementTypes" to elementTypes,
            "startAddress" to startAddress,
            "endAddress" to endAddress,
            "lang" to lang
        )
        val track = locationTrackService.getOrThrow(layoutContext, trackId)
        val elementListing = getElementListing(layoutContext, trackId, elementTypes, startAddress, endAddress)
        val translation = localizationService.getLocalization(lang)
        val csvFileContent = locationTrackElementListingToCsv(elementListing, translation)
        return ElementListingFile(FileName("${translation.t("data-products.element-list.element-list-title")} ${track.name}"), csvFileContent)
    }

    @Scheduled(cron = "\${geoviite.rail-network-export.schedule}")
    @Scheduled(initialDelay = 1000 * 300, fixedDelay = Long.MAX_VALUE)
    fun makeElementListingCsv() = runElementListGeneration {
        logger.serviceCall("makeElementListingCsv")
        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
        val geocodingContexts = geocodingService.getGeocodingContexts(MainLayoutContext.official)
        val trackNumbers = trackNumberService.mapById(MainLayoutContext.official)
        val elementListing = locationTrackService
            .listWithAlignments(MainLayoutContext.official, includeDeleted = false)
            .sortedBy { (locationTrack, _) -> locationTrack.name }
            .map { (locationTrack, alignment) ->
                Triple(
                    locationTrack,
                    alignment,
                    trackNumbers[locationTrack.trackNumberId]?.number
                )
            }
            .sortedBy { (_, _, trackNumber) -> trackNumber }
            .flatMap { (track, alignment, trackNumber) ->
                getElementListing(track, alignment, trackNumber, geocodingContexts[track.trackNumberId])
            }
        val csvFileContent = locationTrackElementListingToCsv(elementListing, translation)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        elementListingFileDao.upsertElementListingFile(
            ElementListingFile(
                name = FileName("${translation.t("data-products.element-list.element-list-whole-network-title")} ${dateFormatter.format(Instant.now())}"),
                content = csvFileContent
            )
        )
    }

    fun getElementListingCsv() = elementListingFileDao.getElementListingFile()

    fun getVerticalGeometryListing(planId: IntId<GeometryPlan>): List<VerticalGeometryListing> {
        logger.serviceCall("getVerticalGeometryListing", "planId" to planId)
        val planHeader = getPlanHeader(planId)
        val alignments = geometryDao.fetchAlignments(planHeader.units, planId)
        val geocodingContext = getLayoutGeocodingContextForPlanTrackNumber(planHeader.trackNumber)

        return toVerticalGeometryListing(
            alignments, coordinateTransformationService::getLayoutTransformation, planHeader, geocodingContext
        )
    }

    fun getVerticalGeometryListingCsv(planId: IntId<GeometryPlan>, lang: LocalizationLanguage): Pair<FileName, ByteArray> {
        logger.serviceCall("getVerticalGeometryListingCsv", "planId" to planId)
        val plan = getPlanHeader(planId)
        val verticalGeometryListing = getVerticalGeometryListing(planId)
        val translation = localizationService.getLocalization(lang)

        val csvFileContent = planVerticalGeometryListingToCsv(verticalGeometryListing, translation)
        return FileName("${translation.t("data-products.vertical-geometry.vertical-geometry-title")} ${plan.fileName}") to csvFileContent.toByteArray()
    }

    @Transactional(readOnly = true)
    fun getVerticalGeometryListing(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        startAddress: TrackMeter? = null,
        endAddress: TrackMeter? = null,
    ): List<VerticalGeometryListing> {
        logger.serviceCall(
            "getVerticalGeometryListing",
            "locationTrackId" to locationTrackId,
            "startAddress" to startAddress,
            "endAddress" to endAddress
        )
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(layoutContext, locationTrackId)
        val geocodingContext = geocodingService.getGeocodingContext(layoutContext, track.trackNumberId)
        return toVerticalGeometryListing(
            track,
            alignment,
            startAddress,
            endAddress,
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            ::getHeaderAndAlignment,
        )
    }

    fun getVerticalGeometryListingCsv(
        locationTrackId: IntId<LocationTrack>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
        lang: LocalizationLanguage,
    ): Pair<FileName, ByteArray> {
        logger.serviceCall(
            "getVerticalGeometryListingCsv",
            "trackId" to locationTrackId,
            "startAddress" to startAddress,
            "endAddress" to endAddress,
        )
        val locationTrack = locationTrackService.getOrThrow(MainLayoutContext.official, locationTrackId)
        val verticalGeometryListing = getVerticalGeometryListing(MainLayoutContext.official, locationTrackId, startAddress, endAddress)
        val translation = localizationService.getLocalization(lang)

        val csvFileContent = locationTrackVerticalGeometryListingToCsv(verticalGeometryListing, translation)
        val name = FileName(
            "${translation.t("data-products.vertical-geometry.vertical-geometry-title")} ${locationTrack.name}"
        )
        return name to csvFileContent.toByteArray()
    }

    @Scheduled(cron = "\${geoviite.rail-network-export.vertical-geometry-schedule}")
    @Scheduled(initialDelay = 1000 * 300, fixedDelay = Long.MAX_VALUE)
    fun makeEntireVerticalGeometryListingCsv() = runVerticalGeometryListGeneration {
        logger.serviceCall("makeEntireVerticalGeometryListingCsv")
        val geocodingContexts = geocodingService.getGeocodingContexts(MainLayoutContext.official)
        val verticalGeometryListingWithTrackNumbers =
            locationTrackService.list(MainLayoutContext.official, includeDeleted = false).sortedWith(
                compareBy(
                    { locationTrack -> geocodingContexts[locationTrack.trackNumberId]?.trackNumber },
                    { locationTrack -> locationTrack.name },
                )
            ).flatMap { locationTrack ->
                val verticalGeometryListingWithoutTrackNumbers =
                    getVerticalGeometryListing(MainLayoutContext.official, locationTrack.id as IntId)

                verticalGeometryListingWithoutTrackNumbers.map { verticalGeometryListing ->
                    verticalGeometryListing.copy(
                        trackNumber = geocodingContexts[locationTrack.trackNumberId]?.trackNumber
                    )
                }
            }

        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
        val csvFileContent = entireTrackNetworkVerticalGeometryListingToCsv(verticalGeometryListingWithTrackNumbers, translation)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        verticalGeometryListingFileDao.upsertVerticalGeometryListingFile(
            VerticalGeometryListingFile(
                name = FileName("${translation.t("data-products.vertical-geometry.vertical-geometry-whole-network-title")} ${dateFormatter.format(Instant.now())}"),
                content = csvFileContent,
            )
        )
    }

    fun getEntireVerticalGeometryListingCsv() = verticalGeometryListingFileDao.getVerticalGeometryListingFile()

    private fun getHeaderAndAlignment(id: IntId<GeometryAlignment>): Pair<GeometryPlanHeader, GeometryAlignment> {
        val header = geometryDao.fetchAlignmentPlanVersion(id).let(geometryDao::getPlanHeader)
        val geometryAlignment = geometryDao.fetchAlignments(header.units, geometryAlignmentId = id).first()
        return header to geometryAlignment
    }

    fun getComparator(sortField: GeometryPlanSortField, sortOrder: SortOrder, lang: LocalizationLanguage): Comparator<GeometryPlanHeader> =
        if (sortOrder == SortOrder.ASCENDING) getComparator(sortField, lang)
        else getComparator(sortField, lang).reversed()

    private fun getComparator(sortField: GeometryPlanSortField, lang: LocalizationLanguage): Comparator<GeometryPlanHeader> {
        val linkingSummaries by lazy { geometryDao.getLinkingSummaries() }
        val translation = localizationService.getLocalization(lang)
        return when (sortField) {
            GeometryPlanSortField.ID -> Comparator.comparing { h -> h.id.intValue }
            GeometryPlanSortField.PROJECT_NAME -> stringComparator { h -> h.project.name }
            GeometryPlanSortField.TRACK_NUMBER -> stringComparator { h -> h.trackNumber }
            GeometryPlanSortField.KM_START -> Comparator { a, b ->
                nullsLastComparator(
                    a.kmNumberRange?.min, b.kmNumberRange?.min
                )
            }

            GeometryPlanSortField.KM_END -> Comparator { a, b ->
                nullsLastComparator(
                    a.kmNumberRange?.max, b.kmNumberRange?.max
                )
            }
            GeometryPlanSortField.PLAN_PHASE -> stringComparator { h ->
                h.planPhase?.let { planPhase -> translation.t("enum.plan-phase.${planPhase.name}") } }
            GeometryPlanSortField.DECISION_PHASE -> stringComparator { h ->
                h.decisionPhase?.let { decisionPhase -> translation.t("enum.plan-decision.${decisionPhase.name}") } }
            GeometryPlanSortField.CREATED_AT -> Comparator { a, b -> nullsLastComparator(a.planTime, b.planTime) }
            GeometryPlanSortField.UPLOADED_AT -> Comparator.comparing { h -> h.uploadTime }
            GeometryPlanSortField.FILE_NAME -> stringComparator { h -> h.fileName }
            GeometryPlanSortField.LINKED_AT -> Comparator { a, b ->
                nullsLastComparator(linkingSummaries[a.id]?.linkedAt, linkingSummaries[b.id]?.linkedAt)
            }

            GeometryPlanSortField.LINKED_BY -> stringComparator { h ->
                linkingSummaries[h.id]?.linkedByUsers?.joinToString(",")
            }
        }
    }

    private inline fun <reified T> stringComparator(crossinline getValue: (T) -> CharSequence?) =
        Comparator.comparing({ h: T -> getValue(h)?.toString()?.lowercase() }, ::nullsLastComparator)

    fun getFilter(
        freeText: FreeText?,
        trackNumbers: List<TrackNumber>,
    ): (header: GeometryPlanHeader) -> Boolean {
        val searchTerms = splitSearchTerms(freeText)
        return { header: GeometryPlanHeader ->
            trackNumbersMatch(header, trackNumbers) && freeTextMatches(header, searchTerms)
        }
    }

    private fun collectSegmentSources(alignment: LayoutAlignment): List<SegmentSource> {
        val planAlignmentIdToPlans: MutableMap<IntId<GeometryAlignment>, RowVersion<GeometryPlan>> = mutableMapOf()

        return alignment.segments.map { segment ->
            val sourceId = segment.sourceId
            if (sourceId == null || sourceId !is IndexedId || segment.sourceStart == null) {
                SegmentSource(null, null, null, null)
            } else {
                val planAlignmentId = IntId<GeometryAlignment>(sourceId.parentId)
                val planVersion =
                    planAlignmentIdToPlans.computeIfAbsent(planAlignmentId, geometryDao::fetchAlignmentPlanVersion)
                val plan = geometryDao.fetchPlan(planVersion)
                val planAlignment =
                    plan.alignments.find { alignment -> alignment.id == planAlignmentId } ?: return@map SegmentSource(
                        null, null, null, plan
                    )
                val planProfile = planAlignment.profile ?: return@map SegmentSource(null, null, null, plan)
                val planElement = planAlignment.elements[sourceId.index]
                SegmentSource(planProfile, planElement, planAlignment, plan)
            }
        }
    }

    @Transactional(readOnly = true)
    fun getLocationTrackGeometryLinkingSummary(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<PlanLinkingSummaryItem>? {
        val locationTrack = locationTrackService.get(layoutContext, locationTrackId) ?: return null
        val alignment = layoutAlignmentDao.fetch(locationTrack.alignmentVersion ?: return null)
        val segmentSources = collectSegmentSources(alignment)
        val planLinkEndSegmentIndices = segmentSources.zipWithNext { a, b -> a.plan == b.plan }
            .mapIndexedNotNull { i, equals -> if (equals) null else i }
        return (listOf(-1) + planLinkEndSegmentIndices + listOf(alignment.segments.lastIndex)).windowed(2, 1)
            .mapNotNull { (lastPlanEndIndex, thisPlanEndIndex) ->
                if (alignment.segments.isNotEmpty()) PlanLinkingSummaryItem(
                    alignment.segments[lastPlanEndIndex + 1].startM,
                    alignment.segments[thisPlanEndIndex].endM,
                    segmentSources[thisPlanEndIndex].plan?.fileName,
                    segmentSources[thisPlanEndIndex].alignment?.let { toAlignmentHeader(null, it) },
                    segmentSources[thisPlanEndIndex].plan?.id,
                    segmentSources[thisPlanEndIndex].plan?.units?.verticalCoordinateSystem,
                    segmentSources[thisPlanEndIndex].plan?.elevationMeasurementMethod,
                ) else null
            }
    }

    @Transactional(readOnly = true)
    fun getLocationTrackHeights(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        startDistance: Double,
        endDistance: Double,
        tickLength: Int,
    ): List<KmHeights>? {
        val locationTrack = locationTrackService.get(layoutContext, locationTrackId) ?: return null
        val alignment = layoutAlignmentDao.fetch(locationTrack.alignmentVersion ?: return null)
        val boundingBox = alignment.boundingBox ?: return null
        val geocodingContext =
            geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId) ?: return null

        val segmentSources = collectSegmentSources(alignment)
        val alignmentLinkEndSegmentIndices = segmentSources.zipWithNext { a, b -> a.alignment == b.alignment }
            .mapIndexedNotNull { i, equals -> if (equals) null else i }

        val alignmentBoundaryAddresses = alignmentLinkEndSegmentIndices.flatMap { i ->
            listOf(
                PlanBoundaryPoint(alignment.segments[i].endM, i),
                PlanBoundaryPoint(alignment.segments[i + 1].startM, i + 1),
            )
        }

        val heightTriangles = heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)

        return collectTrackMeterHeights(
            startDistance,
            endDistance,
            geocodingContext,
            alignment,
            tickLength,
            alignmentBoundaryAddresses = alignmentBoundaryAddresses,
        ) { point, givenSegmentIndex ->
            val segmentIndex = givenSegmentIndex ?: alignment.getSegmentIndexAtM(point.m)
            val segment = alignment.segments[segmentIndex]
            val source = segmentSources[segmentIndex]
            val distanceInSegment = point.m - segment.startM
            val distanceInElement = distanceInSegment + (segment.sourceStart ?: 0.0)
            val distanceInGeometryAlignment = distanceInElement + (source.element?.staStart?.toDouble() ?: 0.0)
            val profileHeight = source.profile?.getHeightAt(distanceInGeometryAlignment)
            profileHeight?.let { height ->
                source.plan?.units?.verticalCoordinateSystem?.let { verticalCoordinateSystem ->
                    transformHeightValue(height, point, heightTriangles, verticalCoordinateSystem)
                }
            }
        }
    }

    @Transactional(readOnly = true)
    fun getPlanAlignmentStartAndEnd(
        planId: IntId<GeometryPlan>,
        planAlignmentId: IntId<GeometryAlignment>,
    ): AlignmentStartAndEnd? {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext =
            plan.trackNumber?.let { geocodingService.getGeocodingContext(plan.trackNumber, planVersion) } ?: return null
        val alignment =
            planLayoutCache.getPlanLayout(planVersion).first?.alignments?.find { alignment -> alignment.id == planAlignmentId }
                ?: return null
        return geocodingContext.getStartAndEnd(alignment)
    }

    @Transactional(readOnly = true)
    fun getPlanAlignmentHeights(
        planId: IntId<GeometryPlan>,
        planAlignmentId: IntId<GeometryAlignment>,
        startDistance: Double,
        endDistance: Double,
        tickLength: Int,
    ): List<KmHeights>? {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext =
            plan.trackNumber?.let { geocodingService.getGeocodingContext(plan.trackNumber, planVersion) } ?: return null
        val geometryAlignment = plan.alignments.find { alignment -> alignment.id == planAlignmentId } ?: return null
        val profile = geometryAlignment.profile
        val alignment =
            planLayoutCache.getPlanLayout(planVersion).first?.alignments?.find { alignment -> alignment.id == planAlignmentId }
                ?: return null
        val boundingBox = alignment.boundingBox ?: return null
        val heightTriangles = heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)
        val verticalCoordinateSystem = plan.units.verticalCoordinateSystem ?: return null

        return collectTrackMeterHeights(
            startDistance, endDistance, geocodingContext, alignment, tickLength
        ) { point, _ ->
            profile?.getHeightAt(point.m)?.let { height ->
                transformHeightValue(height, point, heightTriangles, verticalCoordinateSystem)
            }
        }
    }

    private fun collectTrackMeterHeights(
        startDistance: Double,
        endDistance: Double,
        geocodingContext: GeocodingContext,
        alignment: IAlignment,
        tickLength: Int,
        alignmentBoundaryAddresses: List<PlanBoundaryPoint> = listOf(),
        getHeightAt: (point: AlignmentPoint, segmentIndex: Int?) -> Double?,
    ): List<KmHeights>? {
        val addressOfStartDistance =
            geocodingContext.getAddress(alignment.getPointAtM(startDistance) ?: return null)?.first ?: return null
        val addressOfEndDistance =
            geocodingContext.getAddress(alignment.getPointAtM(endDistance) ?: return null)?.first ?: return null

        val referencePointIndices =
            geocodingContext.referencePoints.indexOfFirst { referencePoint -> referencePoint.kmNumber == addressOfStartDistance.kmNumber }..geocodingContext.referencePoints.indexOfFirst { referencePoint -> referencePoint.kmNumber == addressOfEndDistance.kmNumber }

        val alignmentBoundaryAddressesByKm = alignmentBoundaryAddresses.mapNotNull { boundary ->
            alignment.getPointAtM(boundary.distanceOnAlignment)?.let { point ->
                geocodingContext.getAddress(point)?.let { address -> address.first to boundary.segmentIndex }
            }
        }.groupBy({ (trackMeter) -> trackMeter.kmNumber },
            { (trackMeter, segmentIndex) -> trackMeter.meters to segmentIndex })

        val (alignmentStart, alignmentEnd) = geocodingContext.getStartAndEnd(alignment).let { startAndEnd ->
            (startAndEnd.start ?: return null) to (startAndEnd.end ?: return null)
        }

        return referencePointIndices.toList().parallelStream().map { referencePointIndex ->
            val referencePoint = geocodingContext.referencePoints[referencePointIndex]
            val kmNumber = referencePoint.kmNumber
            // The choice of a half-tick-length minimum is totally arbitrary
            val minTickSpace = BigDecimal(tickLength).setScale(1) / BigDecimal(2)
            val lastPoint = (referencePoint.meters.toDouble() + getKmLengthAtReferencePointIndex(
                referencePointIndex, geocodingContext
            ) - minTickSpace.toDouble()).toInt().coerceAtLeast(0)

            // Pairs of (track meter, segment index). Ordinary ticks don't need segment indices because they clearly
            // hit a specific segment; but points on different sides of a segment boundary are often the exact same
            // point, but potentially have different heights (or more often null/not-null heights).
            val allTicks = ((alignmentBoundaryAddressesByKm[kmNumber]
                ?: listOf()) + ((0..lastPoint step tickLength).map { distance -> distance.toBigDecimal() to null })).sortedBy { (trackMeterInKm) -> trackMeterInKm }

            val ticksToSend = (allTicks.filterIndexed { i, (trackMeterInKm, segmentIndex) ->
                segmentIndex != null || i == 0 || ((trackMeterInKm - allTicks[i - 1].first >= minTickSpace) && (i == allTicks.lastIndex || allTicks[i + 1].first - trackMeterInKm >= minTickSpace))
            }
                    // Special-case first and last points so we get as close as possible to the track ends
                    + (if (kmNumber == alignmentStart.address.kmNumber) listOf(alignmentStart.address.meters to null) else listOf()) + (if (kmNumber == alignmentEnd.address.kmNumber) listOf(
                alignmentEnd.address.meters to null
            ) else listOf())).sortedBy { (trackMeterInKm) -> trackMeterInKm }

            val endM = if (kmNumber == alignmentEnd.address.kmNumber) {
                alignmentEnd.point.m
            } else {
                geocodingContext.getTrackLocation(
                    alignment, TrackMeter(geocodingContext.referencePoints[referencePointIndex + 1].kmNumber, 0)
                )?.point?.m!!
            }

            KmHeights(
                referencePoint.kmNumber,
                ticksToSend.mapNotNull { (trackMeterInKm, segmentIndex) ->
                    val trackMeter = TrackMeter(kmNumber, trackMeterInKm)
                    geocodingContext.getTrackLocation(alignment, trackMeter)?.let { address ->
                        TrackMeterHeight(
                            address.point.m,
                            address.address.meters.toDouble(),
                            getHeightAt(address.point, segmentIndex),
                            address.point.toPoint(),
                        )
                    }
                }.distinct(), // don't bother sending segment boundary sides with the same location and height
                endM,
            )
        }.collect(Collectors.toList()).filter { km -> km.trackMeterHeights.isNotEmpty() }
    }

    @Transactional
    fun setPlanHidden(planId: IntId<GeometryPlan>, hidden: Boolean): RowVersion<GeometryPlan> {
        logger.serviceCall("setPlanHidden", "planId" to planId, "hidden" to hidden)
        if (hidden && !geometryDao.getPlanLinking(planId).isEmpty) {
            throw DeletingFailureException(
                message = "Cannot hide geometry plan that is linked to layout",
                localizedMessageKey = "error.deleting.plan-linked",
            )
        } else {
            return geometryDao.setPlanHidden(planId, hidden)
        }
    }

    fun getPlanLinkedItems(planId: IntId<GeometryPlan>): GeometryPlanLinkedItems {
        logger.serviceCall("getPlanLinkedItems", "planId" to planId)
        return geometryDao.getPlanLinking(planId)
    }

    private fun getLayoutGeocodingContextForPlanTrackNumber(trackNumber: TrackNumber?): GeocodingContext? = trackNumber
        ?.let { number -> trackNumberService.find(MainLayoutContext.official, number).firstOrNull()?.id }
        ?.let { trackNumberId -> geocodingService.getGeocodingContext(MainLayoutContext.official, trackNumberId) }
}

private fun getKmLengthAtReferencePointIndex(
    referencePointIndex: Int,
    geocodingContext: GeocodingContext,
) =
    if (referencePointIndex == geocodingContext.referencePoints.size - 1) {
        geocodingContext.referenceLineGeometry.length - geocodingContext.referencePoints[referencePointIndex].distance
    } else {
        geocodingContext.referencePoints[referencePointIndex + 1].distance - geocodingContext.referencePoints[referencePointIndex].distance
    }

private fun trackNumbersMatch(
    header: GeometryPlanHeader,
    trackNumbers: List<TrackNumber>,
) = (trackNumbers.isEmpty() || (header.trackNumber?.let(trackNumbers::contains) ?: false))

private fun freeTextMatches(
    header: GeometryPlanHeader,
    terms: List<String>,
) = terms.isEmpty() || terms.all { term -> header.searchParams.any { s -> s.contains(term) } }

private val whitespace = "\\s+".toRegex()
private fun splitSearchTerms(freeText: FreeText?): List<String> =
    freeText?.toString()?.split(whitespace)?.toList()?.map { s -> s.lowercase().trim() }?.filter(String::isNotBlank)
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

private data class SegmentSource(
    val profile: GeometryProfile?,
    val element: GeometryElement?,
    val alignment: GeometryAlignment?,
    val plan: GeometryPlan?,
)

private data class PlanBoundaryPoint(
    val distanceOnAlignment: Double,
    val segmentIndex: Int,
)
