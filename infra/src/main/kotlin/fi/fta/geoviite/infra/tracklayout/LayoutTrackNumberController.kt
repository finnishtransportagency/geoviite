package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.publication.getCsvResponseEntity
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}")
    fun getTrackNumbers(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("includeDeleted", defaultValue = "false") includeDeleted: Boolean,
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
        return publicationService.validateTrackNumberAndReferenceLine(id, publishType)
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        logger.apiCall(
            "getTrackSectionsByPlan", "publishType" to publishType, "id" to id, "bbox" to boundingBox
        )
        return trackNumberService.getMetadataSections(id, publishType, boundingBox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/km-lengths")
    fun getTrackNumberKmLengths(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails> {
        logger.apiCall(
            "getTrackNumberKmLengths", "publishType" to publishType, "id" to id
        )

        return trackNumberService.getKmLengths(publishType, id) ?: emptyList()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/km-lengths/as-csv")
    fun getTrackNumberKmLengthsAsCsv(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("startKmNumber") startKmNumber: KmNumber? = null,
        @RequestParam("endKmNumber") endKmNumber: KmNumber? = null,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getTrackNumberKmLengthsAsCsv",
            "publishType" to publishType,
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/rail-network/km-lengths/file")
    fun getEntireRailNetworkKmLengthsAsCsv(
        @RequestParam(name = "lang", defaultValue = "fi") lang: String,
    ): ResponseEntity<ByteArray> {
        logger.apiCall("getEntireRailNetworkTrackNumberKmLengthsAsCsv", "lang" to lang)

        val csv = trackNumberService.getAllKmLengthsAsCsv(
            publishType = PublishType.OFFICIAL,
            trackNumberIds = trackNumberService.listOfficial().map { tn ->
                tn.id as IntId
            })

        val localization = localizationService.getLocalization(lang)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        val fileDescription =
            localization.t("data-products.km-lengths.entire-rail-network-km-lengths-file-name-without-date")
        val fileDate = dateFormatter.format(Instant.now())

        return getCsvResponseEntity(csv, FileName("$fileDescription $fileDate.csv"))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/change-times")
    fun getTrackNumberChangeInfo(@PathVariable("id") id: IntId<TrackLayoutTrackNumber>): DraftableChangeInfo {
        logger.apiCall("getTrackNumberChangeInfo", "id" to id)
        return trackNumberService.getDraftableChangeInfo(id)
    }
}
