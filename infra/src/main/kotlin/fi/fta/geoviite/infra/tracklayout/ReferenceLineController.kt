package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.ChangeTimes
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
    @GetMapping("/{publishType}/by-track-number/{trackNumberId}")
    fun getTrackNumberReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>,
    ): ResponseEntity<ReferenceLine> {
        logger.apiCall(
            "getTrackNumberReferenceLine",
            "publishType" to publishType, "trackNumberId" to trackNumberId
        )
        return toResponse(referenceLineService.getByTrackNumber(publishType, trackNumberId))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/draft/non-linked")
    fun getNonLinkedReferenceLines(): List<ReferenceLine> {
        logger.apiCall("getNonLinkedReferenceLines")
        return referenceLineService.listNonLinked()
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
    @GetMapping("/official/{id}/change-times")
    fun getReferenceLineChangeInfo(@PathVariable("id") id: IntId<ReferenceLine>): ChangeTimes {
        logger.apiCall("getReferenceLineChangeInfo", "id" to id)
        return referenceLineService.getChangeTimes(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/reference-lines/{id}/start-and-end")
    fun getReferenceLineStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getReferenceLineStartAndEnd", "publishType" to publishType, "id" to id)
        return toResponse(geocodingService.getReferenceLineStartAndEnd(publishType, id))
    }

}
