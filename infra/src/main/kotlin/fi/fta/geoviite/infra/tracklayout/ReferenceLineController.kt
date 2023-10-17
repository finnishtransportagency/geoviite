package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}")
    fun getReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall("getReferenceLine", "publishType" to publishType, "id" to id)
        return toResponse(referenceLineService.get(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["ids"])
    fun getReferenceLines(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<ReferenceLine>>,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLines", "publishType" to publishType, "ids" to ids)
        return referenceLineService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/by-track-number/{trackNumberId}")
    fun getTrackNumberReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall(
            "getTrackNumberReferenceLine", "publishType" to publishType, "trackNumberId" to trackNumberId
        )
        return toResponse(referenceLineService.getByTrackNumber(publishType, trackNumberId))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["bbox"])
    fun getReferenceLinesNear(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<ReferenceLine> {
        logger.apiCall("getReferenceLinesNear", "publishType" to publishType, "bbox" to bbox)
        return referenceLineService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getReferenceLineStartAndEnd", "publishType" to publishType, "id" to id)
        return toResponse(referenceLineService.getWithAlignment(publishType, id)?.let { (referenceLine, alignment) ->
            geocodingService.getReferenceLineStartAndEnd(publishType, referenceLine, alignment)
        })
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/draft/non-linked")
    fun getNonLinkedReferenceLines(): List<ReferenceLine> {
        logger.apiCall("getNonLinkedReferenceLines")
        return referenceLineService.listNonLinked()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/change-times")
    fun getReferenceLineChangeInfo(@PathVariable("id") id: IntId<ReferenceLine>): DraftableChangeInfo {
        logger.apiCall("getReferenceLineChangeInfo", "id" to id)
        return referenceLineService.getChangeTimes(id)
    }
}
