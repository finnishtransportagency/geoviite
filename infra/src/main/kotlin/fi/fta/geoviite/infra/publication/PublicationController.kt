package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.authorization.AUTH_PUBLICATION_DOWNLOAD
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.DatabaseLock.PUBLICATION
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Duration
import java.time.Instant
import java.time.ZoneId


val publicationMaxDuration: Duration = Duration.ofMinutes(15)

@RestController
@RequestMapping("/publications")
class PublicationController @Autowired constructor(
    private val lockDao: LockDao,
    private val publicationService: PublicationService,
    private val calculatedChangesService: CalculatedChangesService,
    private val localizationService: LocalizationService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/candidates")
    fun getPublishCandidates(): PublishCandidates {
        logger.apiCall("getPublishCandidates")
        return publicationService.collectPublishCandidates()
    }

    @PreAuthorize(AUTH_UI_READ)
    @PostMapping("/validate")
    fun validatePublishCandidates(@RequestBody request: PublishRequestIds): ValidatedPublishCandidates {
        logger.apiCall("validatePublishCandidates", "request" to request)
        return publicationService.validatePublishCandidates(publicationService.collectPublishCandidates(), request)
    }

    @PreAuthorize(AUTH_UI_READ)
    @PostMapping("/calculated-changes")
    fun getCalculatedChanges(@RequestBody request: PublishRequestIds): CalculatedChanges {
        logger.apiCall("getCalculatedChanges", "request" to request)
        return calculatedChangesService.getCalculatedChanges(publicationService.getValidationVersions(request))
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/candidates")
    fun revertPublishCandidates(@RequestBody toDelete: PublishRequestIds): PublishResult {
        logger.apiCall("revertPublishCandidates", "toDelete" to toDelete)
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publicationService.revertPublishCandidates(toDelete)
        } ?: throw PublicationFailureException(
            message = "Could not reserve publication lock",
            localizedMessageKey = "lock-obtain-failed",
        )
    }

    @PreAuthorize(AUTH_UI_READ)
    @PostMapping("/candidates/revert-request-dependencies")
    fun getRevertRequestDependencies(@RequestBody toDelete: PublishRequestIds): PublishRequestIds {
        logger.apiCall("getRevertRequestDependencies")
        return publicationService.getRevertRequestDependencies(toDelete)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping
    fun publishChanges(@RequestBody request: PublishRequest): PublishResult {
        logger.apiCall("publishChanges", "request" to request)
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publicationService.updateExternalId(request.content)
            val versions = publicationService.getValidationVersions(request.content)
            publicationService.validatePublishRequest(versions)
            val calculatedChanges = publicationService.getCalculatedChanges(versions)
            publicationService.publishChanges(versions, calculatedChanges, request.message)
        } ?: throw PublicationFailureException(
            message = "Could not reserve publication lock",
            localizedMessageKey = "lock-obtain-failed",
        )
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping
    fun getPublicationsBetween(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): Page<PublicationDetails> {
        logger.apiCall("getPublicationsBetween", "from" to from, "to" to to)
        val publications = publicationService.fetchPublicationDetailsBetweenInstants(from, to)

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(50) //Prevents frontend from going kaput
        )
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("latest")
    fun getLatestPublications(
        @RequestParam("count") count: Int,
    ): Page<PublicationDetails> {
        logger.apiCall("getLatestPublications", "count" to count)
        val publications = publicationService.fetchLatestPublicationDetails(count)

        return Page(
            totalCount = publications.size, start = 0, items = publications
        )
    }

    @PreAuthorize(AUTH_PUBLICATION_DOWNLOAD)
    @GetMapping("csv")
    fun getPublicationsAsCsv(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableColumn?,
        @RequestParam("order", required = false) order: SortOrder?,
        @RequestParam("timeZone") timeZone: ZoneId?,
        @RequestParam("lang") lang: String,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getPublicationsAsCsv",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
            "timeZone" to timeZone,
            "lang" to lang,
        )

        val publicationsAsCsv = publicationService.fetchPublicationsAsCsv(
            from, to, sortBy, order, timeZone, localizationService.getLocalization(lang)
        )

        val dateString = getDateStringForFileName(from, to, timeZone ?: ZoneId.of("UTC"))

        val fileName = FileName("julkaisuloki${dateString?.let { " $it" } ?: ""}.csv")
        return getCsvResponseEntity(publicationsAsCsv, fileName)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/table-rows")
    fun getPublicationDetailsAsTableRows(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableColumn?,
        @RequestParam("order", required = false) order: SortOrder?,
        @RequestParam("lang") lang: String,
    ): Page<PublicationTableItem> {
        logger.apiCall(
            "getPublicationDetailsAsTableRows",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
            "lang" to lang
        )

        val publications = publicationService.fetchPublicationDetails(
            from = from,
            to = to,
            sortBy = sortBy,
            order = order,
            translation = localizationService.getLocalization(lang)
        )

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(500) //Prevents frontend from going kaput
        )
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{id}/table-rows")
    fun getPublicationDetailsAsTableRows(
        @PathVariable("id") id: IntId<Publication>,
        @RequestParam("lang") lang: String,
    ): List<PublicationTableItem> {
        logger.apiCall("getPublicationDetailsAsTableRow", "id" to id)
        return publicationService.getPublicationDetailsAsTableItems(id, localizationService.getLocalization(lang))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{id}")
    fun getPublicationDetails(@PathVariable("id") id: IntId<Publication>): PublicationDetails {
        logger.apiCall("getPublicationDetails", "id" to id)
        return publicationService.getPublicationDetails(id)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{id}/split-details")
    fun getSplitDetails(@PathVariable("id") id: IntId<Publication>): ResponseEntity<SplitInPublication> {
        logger.apiCall("getLocationTrackDetails", "id" to id)
        return publicationService.getSplitInPublication(id).let(::toResponse)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/{id}/split-details/csv")
    fun getSplitDetailsAsCsv(@PathVariable("id") id: IntId<Publication>): ResponseEntity<ByteArray> {
        logger.apiCall("getSplitDetailsAsCsv", "id" to id)
        return publicationService
            .getSplitInPublicationCsv(id)
            .let { (csv, ltName) ->
                getCsvResponseEntity(csv, FileName("Raiteen jakaminen $ltName.csv"))
            }
    }
}
