package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geometry.GeometryPlanSortField.ID
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.*
import fi.fta.geoviite.infra.util.SortOrder.ASCENDING
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
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

    @PreAuthorize(AUTH_ALL_READ)
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
    ): Page<GeometryPlanHeader> {
        log.apiCall("getPlanHeaders", "sources" to sources)
        val filter = geometryService.getFilter(freeText, trackNumberIds ?: listOf())
        val headers = geometryService.getPlanHeaders(sources, bbox, filter)
        val comparator = geometryService.getComparator(sortField ?: ID, sortOrder ?: ASCENDING)
        return page(items = headers, offset = offset ?: 0, limit = limit ?: 50, comparator = comparator)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plan-headers/{planId}")
    fun getPlanHeader(@PathVariable("planId") planId: IntId<GeometryPlan>): GeometryPlanHeader {
        log.apiCall("getPlanHeader", "planId" to planId)
        return geometryService.getPlanHeader(planId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plan-headers", params = ["planIds"])
    fun getPlanHeaders(@RequestParam("planIds", required = true) planIds: List<IntId<GeometryPlan>>): List<GeometryPlanHeader> {
        log.apiCall("getPlanHeaders", "planIds" to planIds)
        return geometryService.getManyPlanHeaders(planIds)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/areas")
    fun getGeometryPlanAreas(@RequestParam("bbox") bbox: BoundingBox): List<GeometryPlanArea> {
        log.apiCall("getGeometryPlanAreas", "bbox" to bbox)
        return geometryService.getGeometryPlanAreas(bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{geometryPlanId}/layout")
    fun getTackLayoutPlan(
        @PathVariable("geometryPlanId") geometryPlanId: IntId<GeometryPlan>,
        @RequestParam("includeGeometryData") includeGeometryData: Boolean = true,
    ): GeometryPlanLayout? {
        log.apiCall(
            "getTackLayoutPlan",
            "planId" to geometryPlanId, "includeGeometryData" to includeGeometryData
        )
        return planLayoutService.getLayoutPlan(geometryPlanId, includeGeometryData).first
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches/{switchId}")
    fun getGeometrySwitch(@PathVariable("switchId") switchId: IntId<GeometrySwitch>): GeometrySwitch {
        log.apiCall("getGeometrySwitch", "switchId" to switchId)
        return geometryService.getSwitch(switchId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches/{switchId}/layout")
    fun getGeometrySwitchLayout(
        @PathVariable("switchId") switchId: IntId<GeometrySwitch>,
    ): ResponseEntity<TrackLayoutSwitch> {
        log.apiCall("getGeometrySwitchLayout", "switchId" to switchId)
        return toResponse(geometryService.getSwitchLayout(switchId))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{id}")
    fun getGeometryPlan(@PathVariable("id") planId: IntId<GeometryPlan>): GeometryPlan {
        log.apiCall("getGeometryPlan", "planId" to planId)
        return geometryService.getGeometryPlan(planId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/elements/{id}")
    fun getGeometryElement(@PathVariable("id") geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        log.apiCall("getGeometryElement", "geometryElementId" to geometryElementId)
        return geometryService.getGeometryElement(geometryElementId)
    }


    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projects")
    fun getProjects(): List<Project> {
        log.apiCall("getProjects")
        return geometryService.getProjects()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/projects/{id}")
    fun getProject(@PathVariable("id") projectId: IntId<Project>): Project {
        log.apiCall("getProject")
        return geometryService.getProject(projectId)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/projects")
    fun createProject(@RequestBody project: Project): Project {
        log.apiCall("createProject", "project" to project)
        return geometryService.createProject(project)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/authors")
    fun getAuthors(): List<Author> {
        log.apiCall("getAuthors")
        return geometryService.getAuthors()
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/authors")
    fun createAuthor(@RequestBody author: Author): Author {
        log.apiCall("createAuthor", "author" to author)
        return geometryService.createAuthor(author)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/plans/linking-summaries")
    fun getLinkingSummaries(
        @RequestBody planIds: List<IntId<GeometryPlan>>,
    ): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        log.apiCall("getLinkingSummaries", "planIds" to planIds)
        return geometryService.getLinkingSummaries(planIds)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{id}/element-listing")
    fun getPlanElementListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
    ): List<ElementListing> {
        log.apiCall("getPlanElementList", "id" to id, "elementTypes" to elementTypes)
        return geometryService.getElementListing(id, elementTypes)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{id}/element-listing/file")
    fun getPlanElementListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getPlanElementList", "id" to id, "elementTypes" to elementTypes)
        val (filename, content) = geometryService.getElementListingCsv(id, elementTypes)
        return toFileDownloadResponse("${filename}.csv", content)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/layout/location-tracks/{id}/element-listing")
    fun getTrackElementListing(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<ElementListing> {
        log.apiCall("getPlanElementList",
            "id" to id, "elementTypes" to elementTypes, "startAddress" to startAddress,
            "endAddress" to endAddress)
        return geometryService.getElementListing(id, elementTypes, startAddress, endAddress)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/layout/location-tracks/{id}/element-listing/file")
    fun getTrackElementListingCSV(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getPlanElementListCsv",
            "id" to id, "elementTypes" to elementTypes, "startAddress" to startAddress,
            "endAddress" to endAddress)
        val (filename, content) = geometryService
            .getElementListingCsv(id, elementTypes, startAddress, endAddress)
        return toFileDownloadResponse("${filename}.csv", content)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{id}/vertical-geometry")
    fun getPlanVerticalGeometryListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
    ): List<VerticalGeometryListing> {
        log.apiCall("getPlanVerticalGeometryListing", "id" to id)
        return geometryService.getVerticalGeometryListing(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getPlanVerticalGeometryListingCsv", "id" to id)
        val (filename, content) = geometryService.getVerticalGeometryListingCsv(id)
        return toFileDownloadResponse("${filename}.csv", content)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/layout/location-tracks/{id}/vertical-geometry")
    fun getTrackVerticalGeometryListing(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<VerticalGeometryListing> {
        log.apiCall("getTrackVerticalGeometryListing", "id" to id,
            "startAddress" to startAddress, "endAddress" to endAddress)
        return geometryService.getVerticalGeometryListing(id, startAddress, endAddress)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/layout/location-tracks/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): ResponseEntity<ByteArray> {
        log.apiCall("getTrackVerticalGeometryListingCsv",
            "id" to id, "startAddress" to startAddress,
            "endAddress" to endAddress)
        val (filename, content) = geometryService
            .getVerticalGeometryListingCsv(id, startAddress, endAddress)
        return toFileDownloadResponse("${filename}.csv", content)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans/{planId}/plan-alignment-heights/{planAlignmentId}")
    fun getPlanAlignmentHeights(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @PathVariable("planAlignmentId") planAlignmentId: IntId<GeometryAlignment>,
        @RequestParam("startDistance") startDistance: Double,
        @RequestParam("endDistance") endDistance: Double,
        @RequestParam("tickLength") tickLength: Int,
    ): AlignmentHeights? {
        return geometryService.getPlanAlignmentHeights(planId, planAlignmentId, startDistance, endDistance, tickLength)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/layout/location-tracks/{id}/alignment-heights")
    fun getLayoutAlignmentHeights(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("startDistance") startDistance: Double,
        @RequestParam("endDistance") endDistance: Double,
        @RequestParam("tickLength") tickLength: Int,
    ): AlignmentHeights? {
        return geometryService.getLocationTrackHeights(locationTrackId, startDistance, endDistance, tickLength)
    }}
