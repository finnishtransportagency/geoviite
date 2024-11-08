package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_DOWNLOAD_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_GEOMETRY_FILE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY_FILE
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geometry.GeometryPlanSortField.ID
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.GeometryPlanLayout
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.KnownFileSuffix.CSV
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.SortOrder.ASCENDING
import fi.fta.geoviite.infra.util.pageAndRest
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/geometry")
class GeometryController
@Autowired
constructor(private val geometryService: GeometryService, private val planLayoutService: PlanLayoutService) {

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plan-headers")
    fun getPlanHeaders(
        @RequestParam("bbox") bbox: BoundingBox?,
        @RequestParam("sources") sources: List<PlanSource>,
        @RequestParam("freeText") freeText: FreeText?,
        @RequestParam("trackNumbers") trackNumbers: List<TrackNumber>?,
        @RequestParam("limit") limit: Int?,
        @RequestParam("offset") offset: Int?,
        @RequestParam("sortField") sortField: GeometryPlanSortField?,
        @RequestParam("sortOrder") sortOrder: SortOrder?,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): GeometryPlanHeadersSearchResult {
        val filter = geometryService.getFilter(freeText, trackNumbers ?: listOf())
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
        return geometryService.getPlanHeader(planId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plan-headers", params = ["planIds"])
    fun getPlanHeaders(
        @RequestParam("planIds", required = true) planIds: List<IntId<GeometryPlan>>
    ): List<GeometryPlanHeader> {
        return geometryService.getManyPlanHeaders(planIds)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/areas")
    fun getGeometryPlanAreas(@RequestParam("bbox") bbox: BoundingBox): List<GeometryPlanArea> {
        return geometryService.getGeometryPlanAreas(bbox)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{geometryPlanId}/layout")
    fun getTrackLayoutPlan(
        @PathVariable("geometryPlanId") geometryPlanId: IntId<GeometryPlan>,
        @RequestParam("includeGeometryData") includeGeometryData: Boolean = true,
    ): ResponseEntity<GeometryPlanLayout> {
        return toResponse(planLayoutService.getLayoutPlan(geometryPlanId, includeGeometryData).first)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/switches/{switchId}")
    fun getGeometrySwitch(@PathVariable("switchId") switchId: IntId<GeometrySwitch>): GeometrySwitch {
        return geometryService.getSwitch(switchId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/switches/{switchId}/layout")
    fun getGeometrySwitchLayout(
        @PathVariable("switchId") switchId: IntId<GeometrySwitch>
    ): ResponseEntity<TrackLayoutSwitch> {
        return toResponse(geometryService.getSwitchLayout(switchId))
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{id}")
    fun getGeometryPlan(@PathVariable("id") planId: IntId<GeometryPlan>): GeometryPlan {
        return geometryService.getGeometryPlan(planId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/elements/{id}")
    fun getGeometryElement(@PathVariable("id") geometryElementId: IndexedId<GeometryElement>): GeometryElement {
        return geometryService.getGeometryElement(geometryElementId)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projects")
    fun getProjects(): List<Project> {
        return geometryService.getProjects()
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/projects/{id}")
    fun getProject(@PathVariable("id") projectId: IntId<Project>): Project {
        return geometryService.getProject(projectId)
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping("/projects")
    fun createProject(@RequestBody project: Project): IntId<Project> {
        return geometryService.createProject(project)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/authors")
    fun getAuthors(): List<Author> {
        return geometryService.getAuthors()
    }

    @PreAuthorize(AUTH_EDIT_GEOMETRY_FILE)
    @PostMapping("/authors")
    fun createAuthor(@RequestBody author: Author): Author {
        return geometryService.createAuthor(author)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @PostMapping("/plans/linking-summaries")
    fun getLinkingSummaries(
        @RequestBody planIds: List<IntId<GeometryPlan>>
    ): Map<IntId<GeometryPlan>, GeometryPlanLinkingSummary> {
        return geometryService.getLinkingSummaries(planIds)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plans/{id}/element-listing")
    fun getPlanElementListing(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
    ): List<ElementListing> {
        return geometryService.getElementListing(id, elementTypes)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/plans/{id}/element-listing/file")
    fun getPlanElementListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("elementTypes") elementTypes: List<GeometryElementType>,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val (filename, content) = geometryService.getElementListingCsv(id, elementTypes, lang)
        return toFileDownloadResponse(filename.withSuffix(CSV), content.toByteArray(Charsets.UTF_8))
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/layout/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-tracks/{id}/element-listing")
    fun getTrackElementListing(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<ElementListing> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return geometryService.getElementListing(layoutContext, id, elementTypes, startAddress, endAddress)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/layout/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-tracks/{id}/element-listing/file")
    fun getTrackElementListingCSV(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("elementTypes") elementTypes: List<TrackGeometryElementType>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        val (filename, content) =
            geometryService.getElementListingCsv(
                layoutContext = layoutContext,
                trackId = id,
                elementTypes = elementTypes,
                startAddress = startAddress,
                endAddress = endAddress,
                lang = lang,
            )
        return toFileDownloadResponse(filename.withSuffix(CSV), content.toByteArray(Charsets.UTF_8))
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/rail-network/element-listing/file")
    fun getEntireNetworkElementListingCSV(refresh:Boolean = false): ResponseEntity<ByteArray> {
        if (refresh) {
            geometryService.makeElementListingCsv(force = true)
        }
        val elementListingFile = geometryService.getElementListingCsv()
        return elementListingFile?.let {
            toFileDownloadResponse(
                elementListingFile.name.withSuffix(CSV),
                elementListingFile.content.toByteArray(Charsets.UTF_8),
            )
        } ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/plans/{id}/vertical-geometry")
    fun getPlanVerticalGeometryListing(@PathVariable("id") id: IntId<GeometryPlan>): List<VerticalGeometryListing> {
        return geometryService.getVerticalGeometryListing(id)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/plans/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<GeometryPlan>,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val (filename, content) = geometryService.getVerticalGeometryListingCsv(id, lang)
        return toFileDownloadResponse(filename.withSuffix(CSV), content)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY_FILE)
    @GetMapping("/layout/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-tracks/{id}/vertical-geometry")
    fun getTrackVerticalGeometryListing(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
    ): List<VerticalGeometryListing> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return geometryService.getVerticalGeometryListing(
            layoutContext = layoutContext,
            locationTrackId = id,
            startAddress = startAddress,
            endAddress = endAddress,
        )
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/layout/location-tracks/{id}/vertical-geometry/file")
    fun getTrackVerticalGeometryListingCsv(
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startAddress") startAddress: TrackMeter? = null,
        @RequestParam("endAddress") endAddress: TrackMeter? = null,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): ResponseEntity<ByteArray> {
        val (filename, content) = geometryService.getVerticalGeometryListingCsv(id, startAddress, endAddress, lang)
        return toFileDownloadResponse(filename.withSuffix(CSV), content)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/rail-network/vertical-geometry/file")
    fun getEntireNetworkVerticalGeometryListingCSV(): ResponseEntity<ByteArray> {
        val verticalGeometryListingFile = geometryService.getEntireVerticalGeometryListingCsv()
        return verticalGeometryListingFile?.let {
            toFileDownloadResponse(
                verticalGeometryListingFile.name.withSuffix(CSV),
                verticalGeometryListingFile.content.toByteArray(Charsets.UTF_8),
            )
        } ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/plans/{planId}/start-and-end/{planAlignmentId}")
    fun getPlanAlignmentStartAndEnd(
        @PathVariable("planId") planId: IntId<GeometryPlan>,
        @PathVariable("planAlignmentId") planAlignmentId: IntId<GeometryAlignment>,
    ): ResponseEntity<AlignmentStartAndEnd<GeometryAlignment>> {
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
        return geometryService.getPlanAlignmentHeights(planId, planAlignmentId, startDistance, endDistance, tickLength)
            ?: emptyList()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/layout/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-tracks/{id}/linking-summary")
    fun getLocationTrackLinkingSummary(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<PlanLinkingSummaryItem>? {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return geometryService.getLocationTrackGeometryLinkingSummary(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/layout/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-tracks/{id}/alignment-heights")
    fun getLayoutAlignmentHeights(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startDistance") startDistance: Double,
        @RequestParam("endDistance") endDistance: Double,
        @RequestParam("tickLength") tickLength: Int,
    ): List<KmHeights>? {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return geometryService.getLocationTrackHeights(
            layoutContext = layoutContext,
            locationTrackId = id,
            startDistance = startDistance,
            endDistance = endDistance,
            tickLength = tickLength,
        )
    }
}
