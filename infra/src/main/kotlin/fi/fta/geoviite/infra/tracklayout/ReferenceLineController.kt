package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}")
    fun getReferenceLine(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall("getReferenceLine", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(referenceLineService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["ids"])
    fun getReferenceLines(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<ReferenceLine>>,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLines", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return referenceLineService.getMany(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/by-track-number/{trackNumberId}")
    fun getTrackNumberReferenceLine(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall(
            "getTrackNumberReferenceLine", "$PUBLICATION_STATE" to publicationState, "trackNumberId" to trackNumberId
        )
        return toResponse(referenceLineService.getByTrackNumber(publicationState, trackNumberId))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}", params = ["bbox"])
    fun getReferenceLinesNear(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLinesNear", "$PUBLICATION_STATE" to publicationState, "bbox" to bbox)
        return referenceLineService.listNear(publicationState, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getReferenceLineStartAndEnd", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(referenceLineService.getWithAlignment(publicationState, id)?.let { (referenceLine, alignment) ->
            geocodingService.getReferenceLineStartAndEnd(publicationState, referenceLine, alignment)
        })
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/draft/non-linked")
    fun getNonLinkedReferenceLines(): List<ReferenceLine> {
        logger.apiCall("getNonLinkedReferenceLines")
        return referenceLineService.listNonLinked()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/change-times")
    fun getReferenceLineChangeInfo(
        @PathVariable("id") id: IntId<ReferenceLine>,
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getReferenceLineChangeInfo", "id" to id)
        return toResponse(referenceLineService.getLayoutAssetChangeInfo(id, publicationState))
    }
}
