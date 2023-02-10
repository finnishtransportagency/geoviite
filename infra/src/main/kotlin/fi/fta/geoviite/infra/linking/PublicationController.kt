package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.DatabaseLock.PUBLICATION
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.Page
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.*


val publicationMaxDuration: Duration = Duration.ofMinutes(15)

@RestController
@RequestMapping("/publications")
class PublicationController @Autowired constructor(
    private val lockDao: LockDao,
    private val publicationService: PublicationService,
    private val calculatedChangesService: CalculatedChangesService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/candidates")
    fun getPublishCandidates(): PublishCandidates {
        logger.apiCall("getPublishCandidates")
        return publicationService.collectPublishCandidates()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate")
    fun validatePublishCandidates(@RequestBody request: PublishRequestIds): ValidatedPublishCandidates {
        logger.apiCall("validatePublishCandidates", "request" to request)
        return publicationService.validatePublishCandidates(publicationService.collectPublishCandidates(), request)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/calculated-changes")
    fun getCalculatedChanges(@RequestBody request: PublishRequestIds): CalculatedChanges {
        logger.apiCall("getCalculatedChanges", "request" to request)
        return calculatedChangesService.getCalculatedChangesInDraft(
            publicationService.getPublicationVersions(request)
        )
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

    @PreAuthorize(AUTH_ALL_READ)
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
            val versions = publicationService.getPublicationVersions(request.content)
            publicationService.validatePublishRequest(versions)
            val calculatedChanges = publicationService.getCalculatedChanges(versions)
            publicationService.publishChanges(versions, calculatedChanges, request.message)
        } ?: throw PublicationFailureException(
            message = "Could not reserve publication lock",
            localizedMessageKey = "lock-obtain-failed",
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping
    fun getPublications(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
    ): Page<PublicationDetails> {
        logger.apiCall("getPublications", "from" to from, "to" to to)
        val publications = publicationService.fetchPublicationDetails(from, to)

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(50) //Prevents frontend from going kaput, todo: replace with proper paging
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("csv")
    fun getPublicationsAsCsv(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableSortField?,
        @RequestParam("order", required = false) order: SortOrder?,
        @RequestParam("timeZone") timeZone: ZoneId?,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getPublicationsAsCsv",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
            "timeZone" to timeZone
        )

        val publicationsAsCsv =
            publicationService.fetchPublicationsAsCsv(from, to, sortBy, order, timeZone)

        val dateString = getDateStringForFileName(from, to, timeZone)

        val fileName = FileName("julkaisuloki${dateString?.let { " $it" } ?: ""}.csv")
        return getCsvResponseEntity(publicationsAsCsv, fileName)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/table-rows")
    fun getPublicationDetailsAsTableRows(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationTableSortField?,
        @RequestParam("order", required = false) order: SortOrder?,
    ): Page<PublicationTableRow> {
        logger.apiCall(
            "getPublicationDetailsAsTableRows",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
        )

        val publications = publicationService.fetchPublicationDetails(
            from = from,
            to = to,
            sortBy = sortBy,
            order = order,
        )

        return Page(
            totalCount = publications.size,
            start = 0,
            items = publications.take(500) //Prevents frontend from going kaput
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/table-rows")
    fun getPublicationDetailsAsTableRows(@PathVariable("id") id: IntId<Publication>): List<PublicationTableRow> {
        logger.apiCall("getPublicationDetailsAsTableRow", "id" to id)
        return publicationService.getPublicationDetailsAsTableRows(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}")
    fun getPublicationDetails(@PathVariable("id") id: IntId<Publication>): PublicationDetails {
        logger.apiCall("getPublicationDetails", "id" to id)
        return publicationService.getPublicationDetails(id)
    }
}
