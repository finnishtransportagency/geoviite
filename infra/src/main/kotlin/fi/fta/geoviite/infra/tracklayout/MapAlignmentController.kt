package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.AlignmentFetchType.ALL
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/map")
class MapAlignmentController(private val mapAlignmentService: MapAlignmentService) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/alignments")
    fun getMapAlignments(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
        @RequestParam("resolution") resolution: Int,
        @RequestParam("type") type: AlignmentFetchType? = null,
        @RequestParam("selectedId") selectedId: IntId<LocationTrack>? = null,
    ): List<MapAlignment<*>> {
        logger.apiCall(
            "getMapAlignments",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "selectedId" to selectedId,
        )
        return mapAlignmentService.getMapAlignments(publishType, bbox, resolution, type ?: ALL, selectedId)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/reference-lines/{id}")
    fun getMapReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<ReferenceLine>,
    ): ResponseEntity<MapAlignment<ReferenceLine>> {
        logger.apiCall("getMapReferenceLine", "publishType" to publishType, "id" to id)
        return toResponse(mapAlignmentService.getMapReferenceLine(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/location-tracks/{id}")
    fun getMapLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<MapAlignment<LocationTrack>> {
        logger.apiCall("getMapLocationTrack", "publishType" to publishType, "id" to id)
        return toResponse(mapAlignmentService.getMapLocationTrack(publishType, id))
    }
}
