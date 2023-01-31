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
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ContentDisposition
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
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
        return publicationService.getPublishCandidates()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate")
    fun validatePublishCandidates(@RequestBody publishRequestIds: PublishRequestIds): ValidatedPublishCandidates {
        logger.apiCall("validatePublishCandidates")
        return publicationService.validatePublishCandidates(publicationService.getPublicationVersions(publishRequestIds))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/calculated-changes")
    fun getCalculatedChanges(@RequestBody request: PublishRequestIds): CalculatedChanges {
        logger.apiCall("getCalculatedChanges")
        return calculatedChangesService.getCalculatedChangesInDraft(
            publicationService.getPublicationVersions(request)
        )
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/candidates")
    fun revertPublishCandidates(@RequestBody toDelete: PublishRequestIds): PublishResult {
        logger.apiCall("revertPublishCandidates")
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
            val withoutMessage = PublishRequestIds(request)
            publicationService.updateExternalId(withoutMessage)
            val versions = publicationService.getPublicationVersions(withoutMessage)
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
    ): List<PublicationDetails> {
        logger.apiCall("getPublications", "from" to from, "to" to to)
        return publicationService
            .fetchPublicationDetails(from, to)
            .take(50) //Just to prevent frontend from crashing
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("csv")
    fun getPublicationsAsCsv(
        @RequestParam("from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: Instant?,
        @RequestParam("to", required = true) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: Instant?,
        @RequestParam("sortBy", required = false) sortBy: PublicationCsvSortField?,
        @RequestParam("order", required = false) order: SortOrder?,
    ): ResponseEntity<ByteArray> {
        logger.apiCall(
            "getPublicationsAsCsv",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order
        )

        val publicationsAsCsv =
            publicationService.fetchPublicationsAsCsv(from, to, sortBy, order)

        val headers = HttpHeaders()
        headers.contentType = MediaType.APPLICATION_OCTET_STREAM
        headers.set(
            HttpHeaders.CONTENT_DISPOSITION,
            ContentDisposition
                .attachment()
                .filename("julkaisuloki.csv")
                .build()
                .toString()
        )

        return ResponseEntity
            .ok()
            .headers(headers)
            .body(publicationsAsCsv.toByteArray())
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}")
    fun getPublicationDetails(@PathVariable("id") id: IntId<Publication>): PublicationDetails {
        logger.apiCall("getPublicationDetails", "id" to id)
        return publicationService.getPublicationDetails(id)
    }
}
