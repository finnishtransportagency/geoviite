package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.aspects.GeoviiteService
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
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.ElementListingFile
import fi.fta.geoviite.infra.tracklayout.ElementListingFileDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutEdgeSegment
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
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import withUser

val unknownSwitchName = SwitchName("-")

val elementListingGenerationUser = UserName.of("ELEMENT_LIST_GEN")
val verticalGeometryListingGenerationUser = UserName.of("VERT_GEOM_LIST_GEN")

@GeoviiteService
class GeometryService
@Autowired
constructor(
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

    private fun runElementListGeneration(force: Boolean, op: () -> Unit) =
        runWithLock(elementListingGenerationUser, ELEMENT_LIST_GEN, Duration.ofHours(1L)) {
            val lastFileUpdate = elementListingFileDao.getLastFileListingTime()
            if (force || Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) {
                op()
            }
        }

    private fun runVerticalGeometryListGeneration(op: () -> Unit) =
        runWithLock(verticalGeometryListingGenerationUser, VERTICAL_GEOMETRY_LIST_GEN, Duration.ofHours(1L)) {
            val lastFileUpdate = verticalGeometryListingFileDao.getLastFileListingTime()
            if (Duration.between(lastFileUpdate, Instant.now()) > Duration.ofHours(12L)) {
                op()
            }
        }

    private fun runWithLock(userName: UserName, lock: DatabaseLock, timeout: Duration, op: () -> Unit) =
        withUser(userName) { lockDao.runWithLock(lock, timeout) { op() } }

    fun getGeometryPlanAreas(boundingBox: BoundingBox): List<GeometryPlanArea> {
        return geometryDao.fetchPlanAreas(boundingBox)
    }

    fun getPlanHeaders(
        sources: List<PlanSource> = listOf(),
        bbox: BoundingBox? = null,
        filter: ((GeometryPlanHeader) -> Boolean)? = null,
    ): List<GeometryPlanHeader> {
        return geometryDao.fetchPlanHeaders(sources = sources, bbox = bbox).let { all ->
            filter?.let(all::filter) ?: all
        }
    }

    fun getPlanHeader(planId: IntId<GeometryPlan>): GeometryPlanHeader {
        return geometryDao.getPlanHeader(planId)
    }

    fun getManyPlanHeaders(planIds: List<IntId<GeometryPlan>>): List<GeometryPlanHeader> {
        return geometryDao.getPlanHeaders(planIds)
    }

    fun getGeometryElement(geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        return geometryDao.fetchElement(geometryElementId)
    }

    @Transactional(readOnly = true)
    fun getGeometryPlan(planId: IntId<GeometryPlan>): GeometryPlan {
        return geometryDao.fetchPlan(geometryDao.fetchPlanVersion(planId))
    }

    fun getGeometryPlanChangeTime(): Instant {
        return geometryDao.fetchPlanChangeTime()
    }

    fun getProjects(): List<Project> {
        return geometryDao.fetchProjects()
    }

    fun getProjectChangeTime(): Instant {
        return geometryDao.fetchProjectChangeTime()
    }

    fun getAuthorChangeTime(): Instant {
        return geometryDao.fetchAuthorChangeTime()
    }

    fun getProject(id: IntId<Project>): Project {
        return geometryDao.getProject(id)
    }

    fun createProject(project: Project): IntId<Project> {
        return geometryDao.insertProject(project).id
    }

    fun getAuthors(): List<Author> {
        return geometryDao.fetchAuthors()
    }

    fun createAuthor(author: Author): Author {
        val authorId = geometryDao.insertAuthor(author)
        return geometryDao.getAuthor(authorId.id)
    }

    fun getSwitch(switchId: IntId<GeometrySwitch>): GeometrySwitch {
        return geometryDao.getSwitch(switchId)
    }

    fun getSwitchLayout(switchId: IntId<GeometrySwitch>): TrackLayoutSwitch? {
        val switch = getSwitch(switchId)
        val srid =
            geometryDao.getSwitchSrid(switchId)
                ?: throw IllegalStateException("Coordinate system not found for geometry switch $switchId!")
        val transformation = coordinateTransformationService.getTransformation(srid, LAYOUT_SRID)
        return toTrackLayoutSwitch(switch, transformation)
    }

    fun getKmPost(kmPostId: IntId<GeometryKmPost>): GeometryKmPost {
        return geometryDao.getKmPost(kmPostId)
    }

    fun getKmPostSrid(id: IntId<GeometryKmPost>): Srid? {
        return geometryDao.getKmPostSrid(id)
    }

    fun getPlanFile(planId: IntId<GeometryPlan>): InfraModelFile {
        val fileAndSource = geometryDao.getPlanFile(planId)
        return if (fileAndSource.source == PAIKANNUSPALVELU) {
            InfraModelFile(
                fileNameWithSourcePrefixIfPaikannuspalvelu(fileAndSource.file.name, fileAndSource.source),
                fileAndSource.file.content,
            )
        } else fileAndSource.file
    }

    private fun fileNameWithSourcePrefixIfPaikannuspalvelu(originalFileName: FileName, source: PlanSource): FileName =
        if (source == PAIKANNUSPALVELU) FileName("PAIKANNUSPALVELU_EPÄLUOTETTAVA_$originalFileName")
        else originalFileName

    fun getLinkingSummaries(planIds: List<IntId<GeometryPlan>>): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        return geometryDao.getLinkingSummaries(planIds)
    }

    @Transactional(readOnly = true)
    fun fetchDuplicateGeometryPlanHeader(newFile: InfraModelFile, source: PlanSource): GeometryPlanHeader? {
        return geometryDao.fetchDuplicateGeometryPlanVersion(newFile, source)?.let(geometryDao::getPlanHeader)
    }

    private fun getSwitchName(context: LayoutContext, switchId: IntId<TrackLayoutSwitch>): SwitchName =
        switchService.get(context, switchId)?.name ?: unknownSwitchName

    @Transactional(readOnly = true)
    fun getElementListing(planId: IntId<GeometryPlan>, elementTypes: List<GeometryElementType>): List<ElementListing> {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext =
            plan.trackNumber?.let { geocodingService.getGeocodingContext(plan.trackNumber, planVersion) }

        return toElementListing(
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            plan,
            elementTypes,
        ) { id ->
            getSwitchName(MainLayoutContext.official, id)
        }
    }

    @Transactional(readOnly = true)
    fun getElementListingCsv(
        planId: IntId<GeometryPlan>,
        elementTypes: List<GeometryElementType>,
        lang: LocalizationLanguage,
    ): ElementListingFile {
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
        geometry: DbLocationTrackGeometry,
        trackNumber: TrackNumber?,
        geocodingContext: GeocodingContext?,
    ): List<ElementListing> {
        return toElementListing(
            geocodingContext,
            coordinateTransformationService::getLayoutTransformation,
            locationTrack,
            geometry,
            trackNumber,
            TrackGeometryElementType.entries,
            null,
            null,
            ::getHeaderAndAlignment,
        ) { id ->
            getSwitchName(MainLayoutContext.official, id)
        }
    }

    @Transactional(readOnly = true)
    fun getElementListing(
        layoutContext: LayoutContext,
        trackId: IntId<LocationTrack>,
        elementTypes: List<TrackGeometryElementType>,
        startAddress: TrackMeter?,
        endAddress: TrackMeter?,
    ): List<ElementListing> {
        val (track, alignment) = locationTrackService.getWithGeometryOrThrow(layoutContext, trackId)
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
        ) { id ->
            getSwitchName(layoutContext, id)
        }
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
        val track = locationTrackService.getOrThrow(layoutContext, trackId)
        val elementListing = getElementListing(layoutContext, trackId, elementTypes, startAddress, endAddress)
        val translation = localizationService.getLocalization(lang)
        val csvFileContent = locationTrackElementListingToCsv(elementListing, translation)
        return ElementListingFile(
            FileName("${translation.t("data-products.element-list.element-list-title")} ${track.name}"),
            csvFileContent,
        )
    }

    fun makeElementListingCsv() = makeElementListingCsv(false)

    fun makeElementListingCsv(force: Boolean) =
        runElementListGeneration(force) {
            val translation = localizationService.getLocalization(LocalizationLanguage.FI)
            val geocodingContexts = geocodingService.getGeocodingContexts(MainLayoutContext.official)
            val trackNumbers = trackNumberService.mapById(MainLayoutContext.official)
            val elementListing =
                locationTrackService
                    .listWithGeometries(MainLayoutContext.official, includeDeleted = false)
                    .map { (locationTrack, alignment) ->
                        Triple(locationTrack, alignment, trackNumbers[locationTrack.trackNumberId]?.number)
                    }
                    .sortedWith(compareBy({ (_, _, tn) -> tn }, { (track, _, _) -> track.name }))
                    .flatMap { (track, alignment, trackNumber) ->
                        getElementListing(track, alignment, trackNumber, geocodingContexts[track.trackNumberId])
                    }
            val csvFileContent = locationTrackElementListingToCsv(elementListing, translation)
            val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

            elementListingFileDao.upsertElementListingFile(
                ElementListingFile(
                    name =
                        FileName(
                            "${translation.t("data-products.element-list.element-list-whole-network-title")} ${dateFormatter.format(Instant.now())}"
                        ),
                    content = csvFileContent,
                )
            )
        }

    fun getElementListingCsv() = elementListingFileDao.getElementListingFile()

    fun getVerticalGeometryListing(planId: IntId<GeometryPlan>): List<VerticalGeometryListing> {
        val planHeader = getPlanHeader(planId)
        val alignments = geometryDao.fetchAlignments(planHeader.units, planId)
        val geocodingContext = getLayoutGeocodingContextForPlanTrackNumber(planHeader.trackNumber)

        return toVerticalGeometryListing(
            alignments,
            coordinateTransformationService::getLayoutTransformation,
            planHeader,
            geocodingContext,
        )
    }

    fun getVerticalGeometryListingCsv(
        planId: IntId<GeometryPlan>,
        lang: LocalizationLanguage,
    ): Pair<FileName, ByteArray> {
        val plan = getPlanHeader(planId)
        val verticalGeometryListing = getVerticalGeometryListing(planId)
        val translation = localizationService.getLocalization(lang)

        val csvFileContent = planVerticalGeometryListingToCsv(verticalGeometryListing, translation)
        return FileName(
            "${translation.t("data-products.vertical-geometry.vertical-geometry-title")} ${plan.fileName}"
        ) to csvFileContent.toByteArray()
    }

    @Transactional(readOnly = true)
    fun getVerticalGeometryListing(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
        startAddress: TrackMeter? = null,
        endAddress: TrackMeter? = null,
    ): List<VerticalGeometryListing> {
        val (track, alignment) = locationTrackService.getWithGeometryOrThrow(layoutContext, locationTrackId)
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
        val locationTrack = locationTrackService.getOrThrow(MainLayoutContext.official, locationTrackId)
        val verticalGeometryListing =
            getVerticalGeometryListing(MainLayoutContext.official, locationTrackId, startAddress, endAddress)
        val translation = localizationService.getLocalization(lang)

        val csvFileContent = locationTrackVerticalGeometryListingToCsv(verticalGeometryListing, translation)
        val name =
            FileName(
                "${translation.t("data-products.vertical-geometry.vertical-geometry-title")} ${locationTrack.name}"
            )
        return name to csvFileContent.toByteArray()
    }

    fun makeEntireVerticalGeometryListingCsv() = runVerticalGeometryListGeneration {
        val geocodingContexts = geocodingService.getGeocodingContexts(MainLayoutContext.official)
        val verticalGeometryListingWithTrackNumbers =
            locationTrackService
                .list(MainLayoutContext.official, includeDeleted = false)
                .sortedWith(
                    compareBy(
                        { locationTrack -> geocodingContexts[locationTrack.trackNumberId]?.trackNumber },
                        { locationTrack -> locationTrack.name },
                    )
                )
                .flatMap { locationTrack ->
                    val verticalGeometryListingWithoutTrackNumbers =
                        getVerticalGeometryListing(MainLayoutContext.official, locationTrack.id as IntId)

                    verticalGeometryListingWithoutTrackNumbers.map { verticalGeometryListing ->
                        verticalGeometryListing.copy(
                            trackNumber = geocodingContexts[locationTrack.trackNumberId]?.trackNumber
                        )
                    }
                }

        val translation = localizationService.getLocalization(LocalizationLanguage.FI)
        val csvFileContent =
            entireTrackNetworkVerticalGeometryListingToCsv(verticalGeometryListingWithTrackNumbers, translation)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        verticalGeometryListingFileDao.upsertVerticalGeometryListingFile(
            VerticalGeometryListingFile(
                name =
                    FileName(
                        "${translation.t("data-products.vertical-geometry.vertical-geometry-whole-network-title")} ${dateFormatter.format(Instant.now())}"
                    ),
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

    fun getComparator(
        sortField: GeometryPlanSortField,
        sortOrder: SortOrder,
        lang: LocalizationLanguage,
    ): Comparator<GeometryPlanHeader> =
        if (sortOrder == SortOrder.ASCENDING) getComparator(sortField, lang)
        else getComparator(sortField, lang).reversed()

    private fun getComparator(
        sortField: GeometryPlanSortField,
        lang: LocalizationLanguage,
    ): Comparator<GeometryPlanHeader> {
        val linkingSummaries by lazy { geometryDao.getLinkingSummaries() }
        val translation = localizationService.getLocalization(lang)
        return when (sortField) {
            GeometryPlanSortField.ID -> Comparator.comparing { h -> h.id.intValue }
            GeometryPlanSortField.PROJECT_NAME -> stringComparator { h -> h.project.name }
            GeometryPlanSortField.TRACK_NUMBER -> stringComparator { h -> h.trackNumber }
            GeometryPlanSortField.KM_START ->
                Comparator { a, b -> nullsLastComparator(a.kmNumberRange?.min, b.kmNumberRange?.min) }

            GeometryPlanSortField.KM_END ->
                Comparator { a, b -> nullsLastComparator(a.kmNumberRange?.max, b.kmNumberRange?.max) }
            GeometryPlanSortField.PLAN_PHASE -> stringComparator { h -> h.planPhase?.let(translation::enum) }
            GeometryPlanSortField.DECISION_PHASE -> stringComparator { h -> h.decisionPhase?.let(translation::enum) }
            GeometryPlanSortField.CREATED_AT -> Comparator { a, b -> nullsLastComparator(a.planTime, b.planTime) }
            GeometryPlanSortField.UPLOADED_AT -> Comparator.comparing { h -> h.uploadTime }
            GeometryPlanSortField.FILE_NAME -> stringComparator { h -> h.fileName }
            GeometryPlanSortField.LINKED_AT ->
                Comparator { a, b ->
                    nullsLastComparator(linkingSummaries[a.id]?.linkedAt, linkingSummaries[b.id]?.linkedAt)
                }

            GeometryPlanSortField.LINKED_BY ->
                stringComparator { h -> linkingSummaries[h.id]?.linkedByUsers?.joinToString(",") }
        }
    }

    private inline fun <reified T> stringComparator(crossinline getValue: (T) -> CharSequence?) =
        Comparator.comparing({ h: T -> getValue(h)?.toString()?.lowercase() }, ::nullsLastComparator)

    fun getFilter(freeText: FreeText?, trackNumbers: List<TrackNumber>): (header: GeometryPlanHeader) -> Boolean {
        val searchTerms = splitSearchTerms(freeText)
        return { header: GeometryPlanHeader ->
            trackNumbersMatch(header, trackNumbers) && freeTextMatches(header, searchTerms)
        }
    }

    private fun collectSegmentSources(segments: List<LayoutEdgeSegment>): List<SegmentSource> {
        val planAlignmentIdToPlans: MutableMap<IntId<GeometryAlignment>, RowVersion<GeometryPlan>> = mutableMapOf()

        return segments.map { segment ->
            val sourceId = segment.sourceId
            if (sourceId == null || segment.sourceStart == null) {
                SegmentSource(null, null, null, null)
            } else {
                val planAlignmentId = IntId<GeometryAlignment>(sourceId.parentId)
                val planVersion =
                    planAlignmentIdToPlans.computeIfAbsent(planAlignmentId, geometryDao::fetchAlignmentPlanVersion)
                val plan = geometryDao.fetchPlan(planVersion)
                val planAlignment =
                    plan.alignments.find { alignment -> alignment.id == planAlignmentId }
                        ?: return@map SegmentSource(null, null, null, plan)
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
        val alignment = layoutAlignmentDao.get(locationTrack.versionOrThrow)
        val segmentSources = collectSegmentSources(alignment.segments)
        val planLinkEndSegmentIndices =
            segmentSources
                .zipWithNext { a, b -> a.plan == b.plan }
                .mapIndexedNotNull { i, equals -> if (equals) null else i }
        return (listOf(-1) + planLinkEndSegmentIndices + listOf(alignment.segments.lastIndex))
            .windowed(2, 1)
            .mapNotNull { (lastPlanEndIndex, thisPlanEndIndex) ->
                if (alignment.segments.isNotEmpty())
                    PlanLinkingSummaryItem(
                        alignment.segmentMs[lastPlanEndIndex + 1].min,
                        alignment.segmentMs[thisPlanEndIndex].max,
                        segmentSources[thisPlanEndIndex].plan?.fileName,
                        segmentSources[thisPlanEndIndex].alignment?.let { toAlignmentHeader(null, it) },
                        segmentSources[thisPlanEndIndex].plan?.id,
                        segmentSources[thisPlanEndIndex].plan?.units?.verticalCoordinateSystem,
                        segmentSources[thisPlanEndIndex].plan?.elevationMeasurementMethod,
                    )
                else null
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
        val alignment = layoutAlignmentDao.get(locationTrack.versionOrThrow)
        val boundingBox = alignment.boundingBox ?: return null
        val geocodingContext =
            geocodingService.getGeocodingContext(layoutContext, locationTrack.trackNumberId) ?: return null

        val segmentSources = collectSegmentSources(alignment.segments)
        val alignmentLinkEndSegmentIndices =
            segmentSources
                .zipWithNext { a, b -> a.alignment == b.alignment }
                .mapIndexedNotNull { i, equals -> if (equals) null else i }

        val alignmentBoundaryAddresses =
            alignmentLinkEndSegmentIndices.flatMap { i ->
                listOf(
                    GeometryAlignmentBoundaryPoint(alignment.segmentMs[i].max, i),
                    GeometryAlignmentBoundaryPoint(alignment.segmentMs[i + 1].min, i + 1),
                )
            }

        val heightTriangles = heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)

        return collectTrackMeterHeights(
            startDistance,
            endDistance,
            geocodingContext,
            alignment,
            tickLength,
            geometryAlignmentBoundaryPoints = alignmentBoundaryAddresses,
        ) { point, givenSegmentIndex ->
            val segmentIndex = givenSegmentIndex ?: alignment.getSegmentIndexAtM(point.m)
            val segment = alignment.segments[segmentIndex]
            val segmentM = alignment.segmentMs[segmentIndex]
            val source = segmentSources[segmentIndex]
            val distanceInSegment = point.m - segmentM.min
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
    ): AlignmentStartAndEnd<GeometryAlignment>? {
        val planVersion = geometryDao.fetchPlanVersion(planId)
        val plan = geometryDao.fetchPlan(planVersion)
        val geocodingContext =
            plan.trackNumber?.let { geocodingService.getGeocodingContext(plan.trackNumber, planVersion) }
        return planLayoutCache
            .getPlanLayout(planVersion)
            .first
            ?.alignments
            ?.find { alignment -> alignment.id == planAlignmentId }
            ?.let { alignment -> AlignmentStartAndEnd.of(planAlignmentId, alignment, geocodingContext) }
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
            planLayoutCache.getPlanLayout(planVersion).first?.alignments?.find { alignment ->
                alignment.id == planAlignmentId
            } ?: return null
        val boundingBox = alignment.boundingBox ?: return null
        val heightTriangles = heightTriangleDao.fetchTriangles(boundingBox.polygonFromCorners)
        val verticalCoordinateSystem = plan.units.verticalCoordinateSystem ?: return null

        return collectTrackMeterHeights(startDistance, endDistance, geocodingContext, alignment, tickLength) { point, _
            ->
            profile?.getHeightAt(point.m)?.let { height ->
                transformHeightValue(height, point, heightTriangles, verticalCoordinateSystem)
            }
        }
    }

    @Transactional
    fun setPlanHidden(planId: IntId<GeometryPlan>, hidden: Boolean): RowVersion<GeometryPlan> {
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
        return geometryDao.getPlanLinking(planId)
    }

    private fun getLayoutGeocodingContextForPlanTrackNumber(trackNumber: TrackNumber?): GeocodingContext? =
        trackNumber
            ?.let { number -> trackNumberService.find(MainLayoutContext.official, number).firstOrNull()?.id }
            ?.let { trackNumberId -> geocodingService.getGeocodingContext(MainLayoutContext.official, trackNumberId) }
}

private fun trackNumbersMatch(header: GeometryPlanHeader, trackNumbers: List<TrackNumber>) =
    (trackNumbers.isEmpty() || (header.trackNumber?.let(trackNumbers::contains) ?: false))

private fun freeTextMatches(header: GeometryPlanHeader, terms: List<String>) =
    terms.isEmpty() || terms.all { term -> header.searchParams.any { s -> s.contains(term) } }

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
