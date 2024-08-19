package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.map.AlignmentPolyLine
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.AlignmentFetchType.ALL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/track-layout/map")
class MapAlignmentController(private val mapAlignmentService: MapAlignmentService) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/alignment-polylines")
    fun getAlignmentPolyLines(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
        @RequestParam("type") type: AlignmentFetchType? = null,
        @RequestParam("includeSegmentEndPoints") includeSegmentEndPoints: Boolean = false
    ): List<AlignmentPolyLine<*>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall(
            "getAlignmentPolyLines",
            "layoutContext" to layoutContext,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "includeSegmentEndPoints" to includeSegmentEndPoints
        )
        return mapAlignmentService.getAlignmentPolyLines(layoutContext, bbox, resolution, type ?: ALL, includeSegmentEndPoints)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/{id}/alignment-polyline")
    fun getLocationTrackPolyline(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
    ): AlignmentPolyLine<LocationTrack>? {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall(
            "getLocationTrackPolyline",
            "layoutContext" to layoutContext,
            "id" to locationTrackId,
            "bbox" to bbox,
            "resolution" to resolution
        )

        return mapAlignmentService.getAlignmentPolyline(layoutContext, locationTrackId, bbox, resolution)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/alignment-headers")
    fun getLocationTrackHeaders(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack, LocationTrackState>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getReferenceLineHeaders", "layoutContext" to layoutContext, "ids" to ids)
        return mapAlignmentService.getLocationTrackHeaders(layoutContext, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/reference-line/alignment-headers")
    fun getReferenceLineHeaders(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine, LayoutState>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getReferenceLineHeaders", "layoutContext" to layoutContext, "ids" to ids)
        return mapAlignmentService.getReferenceLineHeaders(layoutContext, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/{id}/segment-m")
    fun getLocationTrackSegmentMValues(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<Double> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getLocationTrackSegmentMValues", "layoutContext" to layoutContext, "id" to id)
        return mapAlignmentService.getLocationTrackSegmentMValues(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/alignment/without-linking")
    fun getSectionsWithoutLinking(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("type") type: AlignmentFetchType,
    ): List<MapAlignmentHighlight<*>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getSectionsWithoutLinking",
            "layoutContext" to layoutContext, "bbox" to bbox, "type" to type)
        return mapAlignmentService.getSectionsWithoutLinking(layoutContext, bbox, type)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/without-profile")
    fun getSectionsWithoutProfile(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall(
            "getSectionsWithoutProfile",
            "layoutContext" to layoutContext,
            "bbox" to bbox,
        )
        return mapAlignmentService.getSectionsWithoutProfile(layoutContext, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/reference-line/{id}/segment-m")
    fun getReferenceLineSegmentMValues(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): List<Double> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getReferenceLineSegmentMValues", "layoutContext" to layoutContext, "id" to id)
        return mapAlignmentService.getReferenceLineSegmentMValues(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/{id}/ends")
    fun getLocationTrackEnds(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): MapAlignmentEndPoints {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getLocationTrackEnds", "layoutContext" to layoutContext, "id" to id)
        return mapAlignmentService.getLocationTrackEnds(layoutContext, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/reference-line/{id}/ends")
    fun getReferenceLineEnds(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): MapAlignmentEndPoints {
        val layoutContext = LayoutContext.of(branch, publicationState)
        logger.apiCall("getReferenceLineEnds", "layoutContext" to layoutContext, "id" to id)
        return mapAlignmentService.getReferenceLineEnds(layoutContext, id)
    }
}
