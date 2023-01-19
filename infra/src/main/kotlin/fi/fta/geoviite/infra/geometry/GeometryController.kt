package fi.fta.geoviite.infra.geometry

import ElementListing
import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geometry.GeometryPlanSortField.ID
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
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
class GeometryController @Autowired constructor(private val geometryService: GeometryService) {
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
        return geometryService.getTrackLayoutPlan(geometryPlanId, includeGeometryData).first
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches/{switchId}")
    fun getGeometrySwitch(@PathVariable("switchId") switchId: IntId<GeometrySwitch>): GeometrySwitch {
        log.apiCall("getGeometrySwitch", "switchId" to switchId)
        return geometryService.getSwitch(switchId);
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
        log.apiCall("getLinkingSummaries")
        return geometryService.getLinkingSummaries(planIds)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/plans/{id}/element-listing")
    fun getPlanElementListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("element-types") elementTypes: List<GeometryElementType>,
    ): List<ElementListing> {
        log.apiCall("getPlanElementList")
        return geometryService.getElementListing(id, elementTypes)
    }
}
