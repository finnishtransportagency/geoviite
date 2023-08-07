package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.configuration.USER_HEADER
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.HeightTriangleDao
import fi.fta.geoviite.infra.geography.transformHeightValue
import fi.fta.geoviite.infra.geometry.PlanSource.PAIKANNUSPALVELU
import fi.fta.geoviite.infra.inframodel.InfraModelFile
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Duration
import java.time.Instant
import java.util.stream.Collectors


const val ELEMENT_LISTING_GENERATION_USER = "ELEMENT_LIST_GEN"
const val VERTICAL_GEOMETRY_LISTING_GENERATION_USER = "VERT_GEOM_LIST_GEN"


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
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private fun runElementListGeneration(op: () -> Unit) {
        MDC.put(USER_HEADER, ELEMENT_LISTING_GENERATION_USER)
        try {
            lockDao.runWithLock(DatabaseLock.ELEMENT_LIST_GEN, Duration.ofHours(1L)) {
                val lastFileUpdate = elementListingFileDao.getLastFileListingTime()
                if (Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) { op() }
            }
        } finally {
            MDC.remove(USER_HEADER)
        }
    }

    private fun runVerticalGeometryListGeneration(op: () -> Unit) {
        MDC.put(USER_HEADER, VERTICAL_GEOMETRY_LISTING_GENERATION_USER)
        try {
            lockDao.runWithLock(DatabaseLock.VERTICAL_GEOMETRY_LIST_GEN, Duration.ofHours(1L)) {
                val lastFileUpdate = verticalGeometryListingFileDao.getLastFileListingTime()
                if (Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) { op() }
            }
        } finally {
            MDC.remove(USER_HEADER)
        }
    }

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

    fun getProjectChangeTime(): Instant {
        logger.serviceCall("getProjectChangeTime")
        return geometryDao.fetchProjectChangeTime()
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
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val context = plan.trackNumberId?.let { tnId ->
            geocodingService.getGeocodingContext(tnId, planVersion)
        }
        return toElementListing(
            context,
            coordinateTransformationService::getLayoutTransformation,
            plan,
            elementTypes
        ) { switchId -> switchService.getOrThrow(OFFICIAL, switchId).name }
    }

    fun getElementListingCsv(planId: IntId<GeometryPlan>, elementTypes: List<GeometryElementType>): ElementListingFile {
        logger.serviceCall("getElementListingCsv", "planId" to planId, "elementTypes" to elementTypes)
        val plan = getPlanHeader(planId)
        val elementListing = getElementListing(planId, elementTypes)

        val csvFileContent = planElementListingToCsv(trackNumberService.list(OFFICIAL), elementListing)
        return ElementListingFile(FileName("$ELEMENT_LISTING ${plan.fileName}"), csvFileContent)
    }

    fun getElementListing(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        geocodingContext: GeocodingContext?
    ): List<ElementListing> {
        logger.serviceCall("getElementListing", "locationTrack" to locationTrack, "alignment" to alignment)
        return toElementListing(
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            locationTrack,
            alignment,
            TrackGeometryElementType.values().toList(),
            null,
            null,
            ::getHeaderAndAlignment,
        ) { switchId -> switchService.getOrThrow(OFFICIAL, switchId).name }
    }

    fun getElementListing(
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): List<ElementListing> {
        logger.serviceCall("getElementListing",
            "trackId" to trackId, "elementTypes" to elementTypes,
            "startAddress" to startAddress, "endAddress" to endAddress,
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
        ) { switchId -> switchService.getOrThrow(OFFICIAL, switchId).name }
    }

    fun getElementListingCsv(
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): ElementListingFile {
        logger.serviceCall("getElementListing",
            "trackId" to trackId, "elementTypes" to elementTypes,
            "startAddress" to startAddress, "endAddress" to endAddress,
        )
        val track = locationTrackService.getOrThrow(OFFICIAL, trackId)
        val elementListing = getElementListing(trackId, elementTypes, startAddress, endAddress)
        val csvFileContent = locationTrackElementListingToCsv(trackNumberService.list(OFFICIAL), elementListing)
        return ElementListingFile(FileName("$ELEMENT_LISTING ${track.name}"), csvFileContent)
    }

    @Scheduled(
        cron = "\${geoviite.rail-network-export.schedule}"
    )
    fun makeElementListingCsv() = runElementListGeneration {
        logger.serviceCall("makeElementListingCsv")
        val trackNumberAndGeocodingContextCache = trackNumberService.listOfficial().associate { tn ->
            tn.id to (tn to geocodingService.getGeocodingContext(OFFICIAL, tn.id))
        }
        val elementListing = locationTrackService.list(OFFICIAL, includeDeleted = false)
            .sortedBy { locationTrack -> locationTrack.name }
            .sortedBy { locationTrack -> trackNumberAndGeocodingContextCache[locationTrack.trackNumberId]?.first?.number }
            .flatMap { locationTrack ->
                val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(OFFICIAL, locationTrack.id as IntId)
                val geocodingContext = trackNumberAndGeocodingContextCache[locationTrack.trackNumberId]?.second
                getElementListing(locationTrack, alignment, geocodingContext)
            }
        val csvFileContent = locationTrackElementListingToCsv(trackNumberService.list(OFFICIAL), elementListing)
        elementListingFileDao.upsertElementListingFile(
            ElementListingFile(
                name = FileName(ELEMENT_LISTING_ENTIRE_RAIL_NETWORK),
                content = csvFileContent
            )
        )
    }

    fun getElementListingCsv() = elementListingFileDao.getElementListingFile()


    fun getVerticalGeometryListing(
        planId: IntId<GeometryPlan>
    ): List<VerticalGeometryListing> {
        logger.serviceCall("getVerticalGeometryListing", "planId" to planId)
        val planHeader = getPlanHeader(planId)
        val alignments = geometryDao.fetchAlignments(planHeader.units, planId)
        val geocodingContext = geocodingService.getGeocodingContext(OFFICIAL, planHeader.trackNumberId)

        return toVerticalGeometryListing(alignments, coordinateTransformationService::getLayoutTransformation, planHeader, geocodingContext)
    }

    fun getVerticalGeometryListingCsv(
        planId: IntId<GeometryPlan>
    ): Pair<FileName, ByteArray> {
        logger.serviceCall("getVerticalGeometryListingCsv", "planId" to planId)
        val plan = getPlanHeader(planId)
        val verticalGeometryListing = getVerticalGeometryListing(planId)

        val csvFileContent = planVerticalGeometryListingToCsv(verticalGeometryListing)
        return FileName("$VERTICAL_GEOMETRY ${plan.fileName}") to csvFileContent.toByteArray()
    }

    fun getVerticalGeometryListing(
        publicationType: PublishType,
        locationTrackId: IntId<LocationTrack>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): List<VerticalGeometryListing> {
        logger.serviceCall("getVerticalGeometryListing", "locationTrackId" to locationTrackId,
            "startAddress" to startAddress, "endAddress" to endAddress)
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(publicationType, locationTrackId)
        val geocodingContext = geocodingService.getGeocodingContext(publicationType, track.trackNumberId)
        return toVerticalGeometryListing(track, alignment, startAddress, endAddress, geocodingContext, coordinateTransformationService::getLayoutTransformation, ::getHeaderAndAlignment)
    }

    fun getVerticalGeometryListingCsv(
        locationTrackId: IntId<LocationTrack>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): Pair<FileName, ByteArray> {
        logger.serviceCall("getVerticalGeometryListingCsv",
            "trackId" to locationTrackId, "startAddress" to startAddress, "endAddress" to endAddress)
        val locationTrack = locationTrackService.getOrThrow(OFFICIAL, locationTrackId)
        val verticalGeometryListing = getVerticalGeometryListing(OFFICIAL, locationTrackId, startAddress, endAddress)

        val csvFileContent = locationTrackVerticalGeometryListingToCsv(verticalGeometryListing)
        return FileName("$VERTICAL_GEOMETRY ${locationTrack.name}") to csvFileContent.toByteArray()
    }

    @Scheduled(
        cron = "\${geoviite.rail-network-export.schedule}"
    )
    fun makeEntireVerticalGeometryListingCsv() = runVerticalGeometryListGeneration {
        logger.serviceCall("makeVerticalGeometryListingCsv")
        val trackNumberAndGeocodingContextCache = trackNumberService.listOfficial().associate { tn ->
            tn.id to (tn to geocodingService.getGeocodingContext(OFFICIAL, tn.id))
        }
        val verticalGeometryListingWithTrackNumbers = locationTrackService.list(OFFICIAL, includeDeleted = false)
            .sortedBy { locationTrack -> locationTrack.name }
            .sortedBy { locationTrack -> trackNumberAndGeocodingContextCache[locationTrack.trackNumberId]?.first?.number }
            .flatMap { locationTrack ->
                val verticalGeometryListingWithoutTrackNumbers = getVerticalGeometryListing(OFFICIAL, locationTrack.id as IntId<LocationTrack>, null, null)

                verticalGeometryListingWithoutTrackNumbers.map { verticalGeometryListing ->
                    verticalGeometryListing.copy(trackNumber = trackNumberAndGeocodingContextCache[locationTrack.trackNumberId]?.first?.number)
                }
            }

        val csvFileContent = entireTrackNetworkVerticalGeometryListingToCsv(verticalGeometryListingWithTrackNumbers)
        verticalGeometryListingFileDao.upsertVerticalGeometryListingFile(
            VerticalGeometryListingFile(
                name = FileName(VERTICAL_GEOMETRY_ENTIRE_RAIL_NETWORK),
                content = csvFileContent
            )
        )
    }

    fun getEntireVerticalGeometryListingCsv() = verticalGeometryListingFileDao.getVerticalGeometryListingFile()

    private fun getHeaderAndAlignment(id: IntId<GeometryAlignment>): Pair<GeometryPlanHeader, GeometryAlignment> {
        val header = geometryDao.fetchAlignmentPlanVersion(id).let(geometryDao::getPlanHeader)
        val geometryAlignment = geometryDao.fetchAlignments(header.units, geometryAlignmentId = id).first()
        return header to geometryAlignment
    }

    fun getComparator(sortField: GeometryPlanSortField, sortOrder: SortOrder): Comparator<GeometryPlanHeader> =
        if (sortOrder == SortOrder.ASCENDING) getComparator(sortField)
        else getComparator(sortField).reversed()

    private fun getComparator(sortField: GeometryPlanSortField): Comparator<GeometryPlanHeader> {
        val trackNumbers by lazy { trackNumberService.mapById(PublishType.DRAFT) }
        val linkingSummaries by lazy { geometryDao.getLinkingSummaries() }
        return when (sortField) {
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
        }
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
                val planAlignment = plan.alignments.find { alignment -> alignment.id == planAlignmentId }
                    ?: return@map SegmentSource(null, null, null, plan)
                val planProfile = planAlignment.profile ?: return@map SegmentSource(null, null, null, plan)
                val planElement = planAlignment.elements[sourceId.index]
                SegmentSource(planProfile, planElement, planAlignment, plan)
            }
        }
    }

    fun getLocationTrackGeometryLinkingSummary(locationTrackId: IntId<LocationTrack>, publishType: PublishType): List<PlanLinkingSummaryItem>? {
        val locationTrack = locationTrackService.get(publishType, locationTrackId) ?: return null
        val alignment = layoutAlignmentDao.fetch(locationTrack.alignmentVersion ?: return null)
        val segmentSources = collectSegmentSources(alignment)
        val planLinkEndSegmentIndices = segmentSources
            .zipWithNext { a, b -> a.plan == b.plan }
            .mapIndexedNotNull { i, equals -> if (equals) null else i }
        return (listOf(-1) + planLinkEndSegmentIndices + listOf(alignment.segments.lastIndex))
            .windowed(2, 1)
            .map { (lastPlanEndIndex, thisPlanEndIndex) ->
                PlanLinkingSummaryItem(
                    alignment.segments[lastPlanEndIndex + 1].startM,
                    alignment.segments[thisPlanEndIndex].endM,
                    segmentSources[thisPlanEndIndex].plan?.fileName,
                    segmentSources[thisPlanEndIndex].alignment?.let(::toAlignmentHeader),
                    segmentSources[thisPlanEndIndex].plan?.id,
                    segmentSources[thisPlanEndIndex].plan?.units?.verticalCoordinateSystem,
                )
            }
    }

    fun getLocationTrackHeights(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
        startDistance: Double,
        endDistance: Double,
        tickLength: Int,
    ): List<KmHeights>? {
        val locationTrack = locationTrackService.get(publishType, locationTrackId) ?: return null
        val alignment = layoutAlignmentDao.fetch(locationTrack.alignmentVersion ?: return null)
        val boundingBox = alignment.boundingBox ?: return null
        val geocodingContext =
            geocodingService.getGeocodingContext(publishType, locationTrack.trackNumberId) ?: return null

        val segmentSources = collectSegmentSources(alignment)
        val alignmentLinkEndSegmentIndices = segmentSources
            .zipWithNext { a, b -> a.alignment == b.alignment }
            .mapIndexedNotNull { i, equals -> if (equals) null else i }

        val alignmentBoundaryAddresses =
            alignmentLinkEndSegmentIndices.flatMap { i ->
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

    fun getPlanAlignmentStartAndEnd(
        planId: IntId<GeometryPlan>,
        planAlignmentId: IntId<GeometryAlignment>,
    ): AlignmentStartAndEnd? {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext =
            geocodingService.getGeocodingContext(plan.trackNumberId ?: return null, planVersion) ?: return null
        val alignment =
            planLayoutCache.getPlanLayout(planVersion).first?.alignments?.find { alignment -> alignment.id == planAlignmentId }
                ?: return null
        return geocodingContext.getStartAndEnd(alignment)
    }

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
            geocodingService.getGeocodingContext(plan.trackNumberId ?: return null, planVersion) ?: return null
        val geometryAlignment = plan.alignments.find { alignment -> alignment.id == planAlignmentId } ?: return null
        val profile = geometryAlignment.profile
        val alignment =
            planLayoutCache.getPlanLayout(planVersion).first?.alignments?.find { alignment -> alignment.id == planAlignmentId }
                ?: return null
        val boundingBox = alignment.boundingBox ?: return null
        val heightTriangles = heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)
        val verticalCoordinateSystem = plan.units.verticalCoordinateSystem ?: return null

        return collectTrackMeterHeights(
            startDistance,
            endDistance,
            geocodingContext,
            alignment,
            tickLength
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
        getHeightAt: (point: LayoutPoint, segmentIndex: Int?) -> Double?,
    ): List<KmHeights>? {
        val addressOfStartDistance =
            geocodingContext.getAddress(alignment.getPointAtM(startDistance) ?: return null)?.first ?: return null
        val addressOfEndDistance =
            geocodingContext.getAddress(alignment.getPointAtM(endDistance) ?: return null)?.first ?: return null

        val referencePointIndices =
            geocodingContext.referencePoints.indexOfFirst { referencePoint -> referencePoint.kmNumber == addressOfStartDistance.kmNumber }..
                    geocodingContext.referencePoints.indexOfFirst { referencePoint -> referencePoint.kmNumber == addressOfEndDistance.kmNumber }

        val alignmentBoundaryAddressesByKm =
            alignmentBoundaryAddresses.mapNotNull { boundary ->
                alignment.getPointAtM(boundary.distanceOnAlignment)?.let { point ->
                    geocodingContext.getAddress(point)?.let { address -> address.first to boundary.segmentIndex }
                }
            }.groupBy(
                { (trackMeter) -> trackMeter.kmNumber },
                { (trackMeter, segmentIndex) -> trackMeter.meters to segmentIndex }
            )

        val (alignmentStart, alignmentEnd) = geocodingContext.getStartAndEnd(alignment).let { startAndEnd ->
            (startAndEnd.start ?: return null) to (startAndEnd.end ?: return null)
        }

        return referencePointIndices.toList().parallelStream().map { referencePointIndex ->
            val referencePoint = geocodingContext.referencePoints[referencePointIndex]
            val kmNumber = referencePoint.kmNumber
            // The choice of a half-tick-length minimum is totally arbitrary
            val minTickSpace = BigDecimal(tickLength).setScale(1) / BigDecimal(2)
            val lastPoint = (referencePoint.meters.toDouble() + getKmLengthAtReferencePointIndex(
                referencePointIndex,
                geocodingContext
            ) - minTickSpace.toDouble()).toInt().coerceAtLeast(0)

            // Pairs of (track meter, segment index). Ordinary ticks don't need segment indices because they clearly
            // hit a specific segment; but points on different sides of a segment boundary are often the exact same
            // point, but potentially have different heights (or more often null/not-null heights).
            val allTicks = ((alignmentBoundaryAddressesByKm[kmNumber] ?: listOf()) +
                    ((0..lastPoint step tickLength).map { distance -> distance.toBigDecimal() to null }))
                .sortedBy { (trackMeterInKm) -> trackMeterInKm }

            val ticksToSend = (allTicks.filterIndexed { i, (trackMeterInKm, segmentIndex) ->
                segmentIndex != null || i == 0 ||
                        ((trackMeterInKm - allTicks[i - 1].first >= minTickSpace) &&
                                (i == allTicks.lastIndex || allTicks[i + 1].first - trackMeterInKm >= minTickSpace))
            }
                    // Special-case first and last points so we get as close as possible to the track ends
                    + (if (kmNumber == alignmentStart.address.kmNumber) listOf(alignmentStart.address.meters to null) else listOf())
                    + (if (kmNumber == alignmentEnd.address.kmNumber) listOf(alignmentEnd.address.meters to null) else listOf()))
                .sortedBy { (trackMeterInKm) -> trackMeterInKm }

            val endM = if (kmNumber == alignmentEnd.address.kmNumber) {
                alignmentEnd.point.m
            } else {
                geocodingContext.getTrackLocation(
                    alignment,
                    TrackMeter(geocodingContext.referencePoints[referencePointIndex + 1].kmNumber, 0)
                )?.point?.m!!
            }

            KmHeights(
                referencePoint.kmNumber,
                ticksToSend
                    .mapNotNull { (trackMeterInKm, segmentIndex) ->
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
        }.collect(Collectors.toList())
            .filter { km -> km.trackMeterHeights.isNotEmpty() }
    }
}

private fun getKmLengthAtReferencePointIndex(
    referencePointIndex: Int,
    geocodingContext: GeocodingContext,
) =
    if (referencePointIndex == geocodingContext.referencePoints.size - 1)
        geocodingContext.referenceLineGeometry.length - geocodingContext.referencePoints[referencePointIndex].distance
    else
        geocodingContext.referencePoints[referencePointIndex + 1].distance - geocodingContext.referencePoints[referencePointIndex].distance


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
