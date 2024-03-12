package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.publication.getCsvResponseEntity
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@RestController
@RequestMapping("/track-layout/track-numbers")
class LayoutTrackNumberController(
    private val trackNumberService: LayoutTrackNumberService,
    private val publicationService: PublicationService,
    private val localizationService: LocalizationService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}")
    fun getTrackNumbers(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @RequestParam("includeDeleted", defaultValue = "false") includeDeleted: Boolean,
    ): List<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumbers", "$PUBLISH_TYPE" to publishType)
        return trackNumberService.list(publishType, includeDeleted)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}")
    fun getTrackNumber(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumber", "$PUBLISH_TYPE" to publishType, "id" to id)
        return toResponse(trackNumberService.get(publishType, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("{$PUBLISH_TYPE}/{id}/validation")
    fun validateTrackNumberAndReferenceLine(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutTrackNumber>> {
        logger.apiCall("validateTrackNumberAndReferenceLine", "$PUBLISH_TYPE" to publishType, "id" to id)
        return publicationService
            .validateTrackNumbersAndReferenceLines(listOf(id), publishType)
            .firstOrNull()
            .let(::toResponse)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/draft")
    fun insertTrackNumber(@RequestBody saveRequest: TrackNumberSaveRequest): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("insertTrackNumber", "trackNumber" to saveRequest)
        return trackNumberService.insert(saveRequest)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/draft/{id}")
    fun updateTrackNumber(
        @PathVariable id: IntId<TrackLayoutTrackNumber>,
        @RequestBody saveRequest: TrackNumberSaveRequest,
    ): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("updateTrackNumber", "id" to id, "trackNumber" to saveRequest)
        trackNumberService.update(id, saveRequest)
        return id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/draft/{id}")
    fun deleteDraftTrackNumber(@PathVariable("id") id: IntId<TrackLayoutTrackNumber>): IntId<TrackLayoutTrackNumber> {
        logger.apiCall("deleteDraftTrackNumber", "id" to id)
        return trackNumberService.deleteDraftAndReferenceLine(id)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        logger.apiCall(
            "getTrackSectionsByPlan", "$PUBLISH_TYPE" to publishType, "id" to id, "bbox" to boundingBox
        )
        return trackNumberService.getMetadataSections(id, publishType, boundingBox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/km-lengths")
    fun getTrackNumberKmLengths(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails> {
        logger.apiCall(
            "getTrackNumberKmLengths", "$PUBLISH_TYPE" to publishType, "id" to id
        )

        return trackNumberService.getKmLengths(publishType, id) ?: emptyList()
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/km-lengths/as-csv")
    fun getTrackNumberKmLengthsAsCsv(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("startKmNumber") startKmNumber: KmNumber? = null,
        @RequestParam("endKmNumber") endKmNumber: KmNumber? = null,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getTrackNumberKmLengthsAsCsv",
            "$PUBLISH_TYPE" to publishType,
            "id" to id,
            "startKmNumber" to startKmNumber,
            "endKmNumber" to endKmNumber
        )

        val csv = trackNumberService.getKmLengthsAsCsv(
            publishType = publishType, trackNumberId = id, startKmNumber = startKmNumber, endKmNumber = endKmNumber
        )

        val trackNumber = trackNumberService.getOrThrow(publishType, id)

        val fileName = FileName("ratakilometrien-pituudet_${trackNumber.number}.csv")
        return getCsvResponseEntity(csv, fileName)
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/rail-network/km-lengths/file")
    fun getEntireRailNetworkKmLengthsAsCsv(
        @RequestParam(name = "lang", defaultValue = "fi") lang: String,
    ): ResponseEntity<ByteArray> {
        logger.apiCall("getEntireRailNetworkTrackNumberKmLengthsAsCsv", "lang" to lang)

        val csv = trackNumberService.getAllKmLengthsAsCsv(
            publishType = OFFICIAL,
            trackNumberIds = trackNumberService.list(OFFICIAL).map { tn -> tn.id as IntId },
        )

        val localization = localizationService.getLocalization(lang)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        val fileDescription =
            localization.t("data-products.km-lengths.entire-rail-network-km-lengths-file-name-without-date")
        val fileDate = dateFormatter.format(Instant.now())

        return getCsvResponseEntity(csv, FileName("$fileDescription $fileDate.csv"))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/{id}/change-times")
    fun getTrackNumberChangeInfo(
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getTrackNumberChangeInfo", "id" to id, "$PUBLISH_TYPE" to publishType)
        return toResponse(trackNumberService.getDraftableChangeInfo(id, publishType))
    }
}
