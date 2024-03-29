package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geometry.GeometryPlanSortField.ID
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.KnownFileSuffix.CSV
import fi.fta.geoviite.infra.util.SortOrder.ASCENDING
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/geometry")
class GeometryController @Autowired constructor(
    private val geometryService: GeometryService,
    private val planLayoutService: PlanLayoutService,
) {
    private val log: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plan-headers")
    fun getPlanHeaders(
        @RequestParam("bbox") bbox: BoundingBox?,
        @RequestParam("sources") sources: List<PlanSource>,
        @RequestParam("freeText") freeText: FreeText?,
        @RequestParam("trackNumberIds") trackNumberIds: List<IntId<TrackLayoutTrackNumber>>?,
        @RequestParam("limit") limit: Int?,
        @RequestParam("offset") offset: Int?,
        @RequestParam("sortField") sortField: GeometryPlanSortField?,
        @RequestParam("sortOrder") sortOrder: SortOrder?,
        @RequestParam("lang") lang: String,
    ): GeometryPlanHeadersSearchResult {
        log.apiCall("getPlanHeaders", "sources" to sources)
        val filter = geometryService.getFilter(freeText, trackNumberIds ?: listOf())
        val headers = geometryService.getPlanHeaders(sources, bbox, filter)
        val comparator = geometryService.getComparator(sortField ?: ID, sortOrder ?: ASCENDING, lang)
        val results = pageAndRest(items = headers, offset = offset ?: 0, limit = limit ?: 50, comparator = comparator)
        return GeometryPlanHeadersSearchResult(
            planHeaders = results.page,
            remainingIds = results.rest.map { plan -> plan.id },
        )
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plan-headers/{planId}")
    fun getPlanHeader(@PathVariable("planId") planId: IntId<GeometryPlan>): GeometryPlanHeader {
        log.apiCall("getPlanHeader", "planId" to planId)
        return geometryService.getPlanHeader(planId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plan-headers", params = ["planIds"])
    fun getPlanHeaders(
        @RequestParam(
            "planIds", required = true
        ) planIds: List<IntId<GeometryPlan>>,
    ): List<GeometryPlanHeader> {
        log.apiCall("getPlanHeaders", "planIds" to planIds)
        return geometryService.getManyPlanHeaders(planIds)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/areas")
    fun getGeometryPlanAreas(@RequestParam("bbox") bbox: BoundingBox): List<GeometryPlanArea> {
        log.apiCall("getGeometryPlanAreas", "bbox" to bbox)
        return geometryService.getGeometryPlanAreas(bbox)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{geometryPlanId}/layout")
    fun getTrackLayoutPlan(
        @PathVariable("geometryPlanId") geometryPlanId: IntId<GeometryPlan>,
        @RequestParam("includeGeometryData") includeGeometryData: Boolean = true,
    ): GeometryPlanLayout? {
        log.apiCall(
            "getTrackLayoutPlan", "planId" to geometryPlanId, "includeGeometryData" to includeGeometryData
        )
        return planLayoutService.getLayoutPlan(geometryPlanId, includeGeometryData).first
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/layout")
    fun getTrackLayoutPlans(
        @RequestParam("planIds") planIds: List<IntId<GeometryPlan>>,
        @RequestParam("includeGeometryData") includeGeometryData: Boolean = true,
    ): ResponseEntity<List<GeometryPlanLayout>> {
        log.apiCall(
            "getTrackLayoutPlans", "planIds" to planIds, "includeGeometryData" to includeGeometryData
        )
        return toResponse(planLayoutService.getManyLayoutPlans(planIds, includeGeometryData).mapNotNull { it.first })
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/switches/{switchId}")
    fun getGeometrySwitch(@PathVariable("switchId") switchId: IntId<GeometrySwitch>): GeometrySwitch {
        log.apiCall("getGeometrySwitch", "switchId" to switchId)
        return geometryService.getSwitch(switchId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/switches/{switchId}/layout")
    fun getGeometrySwitchLayout(
        @PathVariable("switchId") switchId: IntId<GeometrySwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        log.apiCall("getGeometrySwitchLayout", "switchId" to switchId)
        return toResponse(geometryService.getSwitchLayout(switchId))
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{id}")
    fun getGeometryPlan(@PathVariable("id") planId: IntId<GeometryPlan>): GeometryPlan {
        log.apiCall("getGeometryPlan", "planId" to planId)
        return geometryService.getGeometryPlan(planId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/elements/{id}")
    fun getGeometryElement(@PathVariable("id") geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        log.apiCall("getGeometryElement", "geometryElementId" to geometryElementId)
        return geometryService.getGeometryElement(geometryElementId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projects")
    fun getProjects(): List<Project> {
        log.apiCall("getProjects")
        return geometryService.getProjects()
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projects/{id}")
    fun getProject(@PathVariable("id") projectId: IntId<Project>): Project {
        log.apiCall("getProject")
        return geometryService.getProject(projectId)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping("/projects")
    fun createProject(@RequestBody project: Project): IntId<Project> {
        log.apiCall("createProject", "project" to project)
        return geometryService.createProject(project)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/authors")
    fun getAuthors(): List<Author> {
        log.apiCall("getAuthors")
        return geometryService.getAuthors()
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping("/authors")
    fun createAuthor(@RequestBody author: Author): Author {
        log.apiCall("createAuthor", "author" to author)
        return geometryService.createAuthor(author)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/plans/linking-summaries")
    fun getLinkingSummaries(
        @RequestBody planIds: List<IntId<GeometryPlan>>,
    ): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        log.apiCall("getLinkingSummaries", "planIds" to planIds)
        return geometryService.getLinkingSummaries(planIds)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plans/{id}/element-listing")
    fun getPlanElementListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
    ): List<ElementListing> {
        log.apiCall("getPlanElementList", "id" to id, "elementTypes" to elementTypes)
        return geometryService.getElementListing(id, elementTypes)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/plans/{id}/element-listing/file")
    fun getPlanElementListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getPlanElementList", "id" to id, "elementTypes" to elementTypes)
        val (filename, content) = geometryService.getElementListingCsv(id, elementTypes)
        return toFileDownloadResponse(filename.withSuffix(CSV), content.toByteArray(Charsets.UTF_8))
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/layout/location-tracks/{id}/element-listing")
    fun getTrackElementListing(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<ElementListing> {
        log.apiCall(
            "getPlanElementList",
            "id" to id,
            "elementTypes" to elementTypes,
            "startAddress" to startAddress,
            "endAddress" to endAddress
        )
        return geometryService.getElementListing(id, elementTypes, startAddress, endAddress)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/layout/location-tracks/{id}/element-listing/file")
    fun getTrackElementListingCSV(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): ResponseEntity<ByteArray> {
        log.apiCall(
            "getPlanElementListCsv",
            "id" to id,
            "elementTypes" to elementTypes,
            "startAddress" to startAddress,
            "endAddress" to endAddress
        )
        val (filename, content) = geometryService.getElementListingCsv(id, elementTypes, startAddress, endAddress)
        return toFileDownloadResponse(filename.withSuffix(CSV), content.toByteArray(Charsets.UTF_8))
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/rail-network/element-listing/file")
    fun getEntireNetworkElementListingCSV(): ResponseEntity<ByteArray> {
        log.apiCall("getPlanElementListCsv")
        val elementListingFile = geometryService.getElementListingCsv()
        return elementListingFile?.let {
            toFileDownloadResponse(
                elementListingFile.name.withSuffix(CSV), elementListingFile.content.toByteArray(Charsets.UTF_8)
            )
        } ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plans/{id}/vertical-geometry")
    fun getPlanVerticalGeometryListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
    ): List<VerticalGeometryListing> {
        log.apiCall("getPlanVerticalGeometryListing", "id" to id)
        return geometryService.getVerticalGeometryListing(id)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/plans/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getPlanVerticalGeometryListingCsv", "id" to id)
        val (filename, content) = geometryService.getVerticalGeometryListingCsv(id)
        return toFileDownloadResponse(filename.withSuffix(CSV), content)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/layout/{publicationType}/location-tracks/{id}/vertical-geometry")
    fun getTrackVerticalGeometryListing(
        @PathVariable("publicationType") publicationType: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<VerticalGeometryListing> {
        log.apiCall(
            "getTrackVerticalGeometryListing",
            "publicationType" to publicationType,
            "id" to id,
            "startAddress" to startAddress,
            "endAddress" to endAddress
        )
        return geometryService.getVerticalGeometryListing(publicationType, id, startAddress, endAddress)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/layout/location-tracks/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): ResponseEntity<ByteArray> {
        log.apiCall(
            "getTrackVerticalGeometryListingCsv", "id" to id, "startAddress" to startAddress, "endAddress" to endAddress
        )
        val (filename, content) = geometryService.getVerticalGeometryListingCsv(id, startAddress, endAddress)
        return toFileDownloadResponse(filename.withSuffix(CSV), content)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/rail-network/vertical-geometry/file")
    fun getEntireNetworkVerticalGeometryListingCSV(): ResponseEntity<ByteArray> {
        log.apiCall("getEntireNetworkVerticalGeometryListingCSV")
        val verticalGeometryListingFile = geometryService.getEntireVerticalGeometryListingCsv()
        return verticalGeometryListingFile?.let {
            toFileDownloadResponse(
                verticalGeometryListingFile.name.withSuffix(CSV),
                verticalGeometryListingFile.content.toByteArray(Charsets.UTF_8)
            )
        } ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{planId}/start-and-end/{planAlignmentId}")
    fun getPlanAlignmentStartAndEnd(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @PathVariable("planAlignmentId") planAlignmentId: IntId<GeometryAlignment>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getPlanAlignmentStartAndEnd", "planId" to planId, "planAlignmentId" to planAlignmentId)
        return toResponse(geometryService.getPlanAlignmentStartAndEnd(planId, planAlignmentId))

    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{planId}/plan-alignment-heights/{planAlignmentId}")
    fun getPlanAlignmentHeights(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @PathVariable("planAlignmentId") planAlignmentId: IntId<GeometryAlignment>,
        @RequestParam("startDistance") startDistance: Double,
        @RequestParam("endDistance") endDistance: Double,
        @RequestParam("tickLength") tickLength: Int,
    ): List<KmHeights> {
        logger.apiCall(
            "getPlanAlignmentHeights",
            "planId" to planId,
            "planAlignmentId" to planAlignmentId,
            "startDistance" to startDistance,
            "endDistance" to endDistance,
            "tickLength" to tickLength
        )
        return geometryService.getPlanAlignmentHeights(planId, planAlignmentId, startDistance, endDistance, tickLength)
            ?: emptyList()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("{$PUBLICATION_STATE}/layout/location-tracks/{id}/linking-summary")
    fun getLocationTrackLinkingSummary(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<PlanLinkingSummaryItem>? {
        logger.apiCall("getLocationTrackLinkingSummary", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return geometryService.getLocationTrackGeometryLinkingSummary(id, publicationState)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/layout/location-tracks/{id}/alignment-heights")
    fun getLayoutAlignmentHeights(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startDistance") startDistance: Double,
        @RequestParam("endDistance") endDistance: Double,
        @RequestParam("tickLength") tickLength: Int,
    ): List<KmHeights>? {
        logger.apiCall(
            "getLayoutAlignmentHeights",
            "$PUBLICATION_STATE" to publicationState,
            "id" to id,
            "startDistance" to startDistance,
            "endDistance" to endDistance,
            "tickLength" to tickLength
        )
        return geometryService.getLocationTrackHeights(id, publicationState, startDistance, endDistance, tickLength)
    }
}
