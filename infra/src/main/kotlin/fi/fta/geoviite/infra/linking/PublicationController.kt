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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.Duration

val publicationMaxDuration: Duration = Duration.ofMinutes(15)

@RestController
@RequestMapping("/publications")
class PublicationController @Autowired constructor(
    private val lockDao: LockDao,
    private val publishService: PublishService,
    private val calculatedChangesService: CalculatedChangesService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/candidates")
    fun getPublishCandidates(): PublishCandidates {
        logger.apiCall("getPublishCandidates")
        return publishService.getPublishCandidates()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/validate")
    fun validatePublishCandidates(@RequestBody publishRequest: PublishRequest): ValidatedPublishCandidates {
        logger.apiCall("validatePublishCandidates")
        return publishService.validatePublishCandidates(publishRequest)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @PostMapping("/calculated-changes")
    fun getCalculatedChanges(@RequestBody request: PublishRequest): CalculatedChanges {
        logger.apiCall("getCalculatedChanges")
        return calculatedChangesService.getCalculatedChangesInDraft(
            trackNumberIds = request.trackNumbers,
            referenceLineIds = request.referenceLines,
            kmPostIds = request.kmPosts,
            locationTrackIds = request.locationTracks,
            switchIds = request.switches,
        )
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/candidates")
    fun revertPublishCandidates(): PublishResult {
        logger.apiCall("revertPublishCandidates")
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publishService.revertPublishCandidates()
        } ?: throw PublicationFailureException(
            message = "Could not reserve publication lock",
            localizedMessageKey = "lock-obtain-failed",
        )
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping
    fun publishChanges(@RequestBody request: PublishRequest): PublishResult {
        logger.apiCall("publishChanges", "request" to request)
        return lockDao.runWithLock(PUBLICATION, publicationMaxDuration) {
            publishService.validatePublishRequest(request)
            publishService.updateExternalId(request)
            publishService.publishChanges(request)
        } ?: throw PublicationFailureException(
            message = "Could not reserve publication lock",
            localizedMessageKey = "lock-obtain-failed",
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping
    fun getRatkoPublicationListing(): List<PublicationListingItem> {
        logger.apiCall("getRatkoPublicationListing")
        return publishService.getPublicationListing()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}")
    fun getRatkoPublication(@PathVariable("id") id: IntId<Publication>): Publication {
        logger.apiCall("getRatkoPublication", "id" to id)
        return publishService.getPublication(id)
    }
}
