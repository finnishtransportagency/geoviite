package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/track-numbers")
class LayoutTrackNumberController(
    private val trackNumberService: LayoutTrackNumberService,
    private val publicationService: PublicationService,
    private val referenceLineService: ReferenceLineService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}")
    fun getTrackNumbers(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("includeDeleted", defaultValue = "false") includeDeleted: Boolean
    ): List<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumbers", "publishType" to publishType)
        return trackNumberService.list(publishType, includeDeleted)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}")
    fun getTrackNumber(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumber", "publishType" to publishType, "id" to id)
        return toResponse(trackNumberService.get(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/{id}/validation")
    fun validateTrackNumberAndReferenceLine(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ValidatedAsset<TrackLayoutTrackNumber> {
        logger.apiCall("validateTrackNumberAndReferenceLine", "publishType" to publishType, "id" to id)
        val referenceLine = referenceLineService.getByTrackNumber(publishType, id)
        return publicationService.validateTrackNumberAndReferenceLine(
            id,
            referenceLine?.id as IntId<ReferenceLine>,
            publishType
        )
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/draft")
    fun insertTrackNumber(@RequestBody saveRequest: TrackNumberSaveRequest): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("insertTrackNumber", "trackNumber" to saveRequest)
        return trackNumberService.insert(saveRequest)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/draft/{id}")
    fun updateTrackNumber(
        @PathVariable id: IntId<TrackLayoutTrackNumber>,
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("updateTrackNumber", "id" to id, "trackNumber" to saveRequest)
        trackNumberService.update(id, saveRequest)
        return id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftTrackNumber(@PathVariable("id") id: IntId<TrackLayoutTrackNumber>): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("deleteDraftTrackNumber", "id" to id)
        return trackNumberService.deleteDraftOnlyTrackNumberAndReferenceLine(id)
    }
}
