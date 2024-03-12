package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.PUBLISH_TYPE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/reference-lines")
class ReferenceLineController(
    private val referenceLineService: ReferenceLineService,
    private val geocodingService: GeocodingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}")
    fun getReferenceLine(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall("getReferenceLine", "$PUBLISH_TYPE" to publishType, "id" to id)
        return toResponse(referenceLineService.get(publishType, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["ids"])
    fun getReferenceLines(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<ReferenceLine>>,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLines", "$PUBLISH_TYPE" to publishType, "ids" to ids)
        return referenceLineService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/by-track-number/{trackNumberId}")
    fun getTrackNumberReferenceLine(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall(
            "getTrackNumberReferenceLine", "$PUBLISH_TYPE" to publishType, "trackNumberId" to trackNumberId
        )
        return toResponse(referenceLineService.getByTrackNumber(publishType, trackNumberId))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}", params = ["bbox"])
    fun getReferenceLinesNear(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLinesNear", "$PUBLISH_TYPE" to publishType, "bbox" to bbox)
        return referenceLineService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getReferenceLineStartAndEnd", "$PUBLISH_TYPE" to publishType, "id" to id)
        return toResponse(referenceLineService.getWithAlignment(publishType, id)?.let { (referenceLine, alignment) ->
            geocodingService.getReferenceLineStartAndEnd(publishType, referenceLine, alignment)
        })
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/draft/non-linked")
    fun getNonLinkedReferenceLines(): List<ReferenceLine> {
        logger.apiCall("getNonLinkedReferenceLines")
        return referenceLineService.listNonLinked()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/change-times")
    fun getReferenceLineChangeInfo(
        @PathVariable("id") id: IntId<ReferenceLine>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getReferenceLineChangeInfo", "id" to id)
        return toResponse(referenceLineService.getDraftableChangeInfo(id, publishType))
    }
}
