package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_DOWNLOAD_GEOMETRY
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}")
    fun getTrackNumbers(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("includeDeleted", defaultValue = "false") includeDeleted: Boolean,
    ): List<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumbers", "$PUBLICATION_STATE" to publicationState)
        return trackNumberService.list(publicationState, includeDeleted)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}")
    fun getTrackNumber(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<TrackLayoutTrackNumber> {
        logger.apiCall("getTrackNumber", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(trackNumberService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("{$PUBLICATION_STATE}/{id}/validation")
    fun validateTrackNumberAndReferenceLine(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): ResponseEntity<ValidatedAsset<TrackLayoutTrackNumber>> {
        logger.apiCall("validateTrackNumberAndReferenceLine", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return publicationService
            .validateTrackNumbersAndReferenceLines(listOf(id), publicationState)
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

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        logger.apiCall(
            "getTrackSectionsByPlan", "$PUBLICATION_STATE" to publicationState, "id" to id, "bbox" to boundingBox
        )
        return trackNumberService.getMetadataSections(id, publicationState, boundingBox)
    }

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/km-lengths")
    fun getTrackNumberKmLengths(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
    ): List<TrackLayoutKmLengthDetails> {
        logger.apiCall(
            "getTrackNumberKmLengths", "$PUBLICATION_STATE" to publicationState, "id" to id
        )

        return trackNumberService.getKmLengths(publicationState, id) ?: emptyList()
    }

    @PreAuthorize(AUTH_DOWNLOAD_GEOMETRY)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/km-lengths/as-csv")
    fun getTrackNumberKmLengthsAsCsv(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @RequestParam("startKmNumber") startKmNumber: KmNumber? = null,
        @RequestParam("endKmNumber") endKmNumber: KmNumber? = null,
        @RequestParam("lang") lang: String,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getTrackNumberKmLengthsAsCsv",
            "$PUBLICATION_STATE" to publicationState,
            "id" to id,
            "startKmNumber" to startKmNumber,
            "endKmNumber" to endKmNumber,
            "lang" to lang
        )

        val csv = trackNumberService.getKmLengthsAsCsv(
            publicationState = publicationState, trackNumberId = id, startKmNumber = startKmNumber, endKmNumber = endKmNumber, lang = lang
        )

        val trackNumber = trackNumberService.getOrThrow(publicationState, id)

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
            publicationState = OFFICIAL,
            trackNumberIds = trackNumberService.list(OFFICIAL).map { tn -> tn.id as IntId },
            lang
        )

        val localization = localizationService.getLocalization(lang)
        val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.of("Europe/Helsinki"))

        val fileDescription =
            localization.t("data-products.km-lengths.entire-rail-network-km-lengths-file-name-without-date")
        val fileDate = dateFormatter.format(Instant.now())

        return getCsvResponseEntity(csv, FileName("$fileDescription $fileDate.csv"))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$PUBLICATION_STATE}/{id}/change-times")
    fun getTrackNumberChangeInfo(
        @PathVariable("id") id: IntId<TrackLayoutTrackNumber>,
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getTrackNumberChangeInfo", "id" to id, "$PUBLICATION_STATE" to publicationState)
        return toResponse(trackNumberService.getLayoutAssetChangeInfo(id, publicationState))
    }
}
