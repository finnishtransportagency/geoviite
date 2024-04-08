package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.map.AlignmentPolyLine
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.AlignmentFetchType.ALL
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/map")
class MapAlignmentController(private val mapAlignmentService: MapAlignmentService) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/alignment-polylines")
    fun getAlignmentPolyLines(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
        @RequestParam("type") type: AlignmentFetchType? = null,
    ): List<AlignmentPolyLine<*>> {
        logger.apiCall(
            "getAlignmentPolyLines",
            "$PUBLICATION_STATE" to publicationState,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
        )
        return mapAlignmentService.getAlignmentPolyLines(publicationState, bbox, resolution, type ?: ALL)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/location-track/{id}/alignment-polyline")
    fun getLocationTrackPolyline(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
    ): AlignmentPolyLine<LocationTrack>? {
        logger.apiCall(
            "getLocationTrackPolyline",
            "$PUBLICATION_STATE" to publicationState,
            "id" to locationTrackId,
            "bbox" to bbox,
            "resolution" to resolution
        )

        return mapAlignmentService.getAlignmentPolyline(locationTrackId, publicationState, bbox, resolution)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/location-track/alignment-headers")
    fun getLocationTrackHeaders(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack, LocationTrackLayoutState>> {
        logger.apiCall("getReferenceLineHeaders", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return mapAlignmentService.getLocationTrackHeaders(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/reference-line/alignment-headers")
    fun getReferenceLineHeaders(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine, LayoutState>> {
        logger.apiCall("getReferenceLineHeaders", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return mapAlignmentService.getReferenceLineHeaders(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/location-track/{id}/segment-m")
    fun getLocationTrackSegmentMValues(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<Double> {
        logger.apiCall("getLocationTrackSegmentMValues", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return mapAlignmentService.getLocationTrackSegmentMValues(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/alignment/without-linking")
    fun getSectionsWithoutLinking(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("type") type: AlignmentFetchType,
    ): List<MapAlignmentHighlight<*>> {
        logger.apiCall("getSectionsWithoutLinking",
            "$PUBLICATION_STATE" to publicationState, "bbox" to bbox, "type" to type)
        return mapAlignmentService.getSectionsWithoutLinking(publicationState, bbox, type)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/location-track/without-profile")
    fun getSectionsWithoutProfile(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        logger.apiCall(
            "getSectionsWithoutProfile",
            "$PUBLICATION_STATE" to publicationState,
            "bbox" to bbox,
        )
        return mapAlignmentService.getSectionsWithoutProfile(publicationState, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/reference-line/{id}/segment-m")
    fun getReferenceLineSegmentMValues(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): List<Double> {
        logger.apiCall("getReferenceLineSegmentMValues", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return mapAlignmentService.getReferenceLineSegmentMValues(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/location-track/{id}/ends")
    fun getLocationTrackEnds(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): MapAlignmentEndPoints {
        logger.apiCall("getLocationTrackEnds", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return mapAlignmentService.getLocationTrackEnds(publicationState, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/reference-line/{id}/ends")
    fun getReferenceLineEnds(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): MapAlignmentEndPoints {
        logger.apiCall("getReferenceLineEnds", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return mapAlignmentService.getReferenceLineEnds(publicationState, id)
    }
}
