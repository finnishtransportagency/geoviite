package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE
import fi.fta.geoviite.infra.authorization.PUBLISH_TYPE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/alignment-polylines")
    fun getAlignmentPolyLines(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
        @RequestParam("type") type: AlignmentFetchType? = null,
    ): List<AlignmentPolyLine<*>> {
        logger.apiCall(
            "getAlignmentPolyLines",
            "$PUBLISH_TYPE" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
        )
        return mapAlignmentService.getAlignmentPolyLines(publishType, bbox, resolution, type ?: ALL)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/location-track/{id}/alignment-polyline")
    fun getLocationTrackPolyline(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
    ): AlignmentPolyLine<LocationTrack>? {
        logger.apiCall(
            "getLocationTrackPolyline",
            "$PUBLISH_TYPE" to publishType,
            "id" to locationTrackId,
            "bbox" to bbox,
            "resolution" to resolution
        )

        return mapAlignmentService.getAlignmentPolyline(locationTrackId, publishType, bbox, resolution)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/location-track/alignment-headers")
    fun getLocationTrackHeaders(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack>> {
        logger.apiCall("getReferenceLineHeaders", "$PUBLISH_TYPE" to publishType, "ids" to ids)
        return mapAlignmentService.getLocationTrackHeaders(publishType, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/reference-line/alignment-headers")
    fun getReferenceLineHeaders(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine>> {
        logger.apiCall("getReferenceLineHeaders", "$PUBLISH_TYPE" to publishType, "ids" to ids)
        return mapAlignmentService.getReferenceLineHeaders(publishType, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/location-track/{id}/segment-m")
    fun getLocationTrackSegmentMValues(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<Double> {
        logger.apiCall("getLocationTrackSegmentMValues", "$PUBLISH_TYPE" to publishType, "id" to id)
        return mapAlignmentService.getLocationTrackSegmentMValues(publishType, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/alignment/without-linking")
    fun getSectionsWithoutLinking(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("type") type: AlignmentFetchType,
    ): List<MapAlignmentHighlight<*>> {
        logger.apiCall("getSectionsWithoutLinking",
            "$PUBLISH_TYPE" to publishType, "bbox" to bbox, "type" to type)
        return mapAlignmentService.getSectionsWithoutLinking(publishType, bbox, type)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/location-track/without-profile")
    fun getSectionsWithoutProfile(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        logger.apiCall(
            "getSectionsWithoutProfile",
            "$PUBLISH_TYPE" to publishType,
            "bbox" to bbox,
        )
        return mapAlignmentService.getSectionsWithoutProfile(publishType, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/reference-line/{id}/segment-m")
    fun getReferenceLineSegmentMValues(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): List<Double> {
        logger.apiCall("getReferenceLineSegmentMValues", "$PUBLISH_TYPE" to publishType, "id" to id)
        return mapAlignmentService.getReferenceLineSegmentMValues(publishType, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/location-track/{id}/ends")
    fun getLocationTrackEnds(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): MapAlignmentEndPoints {
        logger.apiCall("getLocationTrackEnds", "$PUBLISH_TYPE" to publishType, "id" to id)
        return mapAlignmentService.getLocationTrackEnds(publishType, id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/reference-line/{id}/ends")
    fun getReferenceLineEnds(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): MapAlignmentEndPoints {
        logger.apiCall("getReferenceLineEnds", "$PUBLISH_TYPE" to publishType, "id" to id)
        return mapAlignmentService.getReferenceLineEnds(publishType, id)
    }
}
