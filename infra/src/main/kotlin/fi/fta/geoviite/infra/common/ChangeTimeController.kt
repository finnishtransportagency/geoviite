package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.projektivelho.PVDocumentService
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.ratko.RatkoOperatingPointDao
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

data class CollectedChangeTimes(
    val layoutTrackNumber: Instant,
    val layoutLocationTrack: Instant,
    val layoutReferenceLine: Instant,
    val layoutSwitch: Instant,
    val layoutKmPost: Instant,
    val geometryPlan: Instant,
    val project: Instant,
    val author: Instant,
    val publication: Instant,
    val ratkoPush: Instant,
    val pvDocument: Instant,
    val split: Instant,
    val operatingPoints: Instant,
)

@RestController
@RequestMapping("/change-times")
class ChangeTimeController(
    private val geometryService: GeometryService,
    private val switchService: LayoutSwitchService,
    private val trackNumberService: LayoutTrackNumberService,
    private val kmPostService: LayoutKmPostService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val publicationService: PublicationService,
    private val ratkoPushDao: RatkoPushDao,
    private val pvDocumentService: PVDocumentService,
    private val splitService: SplitService,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/collected")
    @Transactional(readOnly = true)
    fun getCollectedChangeTimes(): CollectedChangeTimes {
        logger.apiCall("getCollectedChangeTimes")
        return CollectedChangeTimes(
            layoutTrackNumber = trackNumberService.getChangeTime(),
            layoutLocationTrack = locationTrackService.getChangeTime(),
            layoutReferenceLine = referenceLineService.getChangeTime(),
            layoutKmPost = kmPostService.getChangeTime(),
            layoutSwitch = switchService.getChangeTime(),
            geometryPlan = geometryService.getGeometryPlanChangeTime(),
            project = geometryService.getProjectChangeTime(),
            author = geometryService.getAuthorChangeTime(),
            publication = publicationService.getChangeTime(),
            ratkoPush = ratkoPushDao.getRatkoPushChangeTime(),
            pvDocument = pvDocumentService.getDocumentChangeTime(),
            split = splitService.getChangeTime(),
            operatingPoints = ratkoOperatingPointDao.getChangeTime(),
        )
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/track-numbers")
    fun getTrackLayoutTrackNumberChangeTime(): Instant {
        logger.apiCall("getTrackLayoutTrackNumberChangeTime")
        return trackNumberService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/location-tracks")
    fun getTrackLayoutLocationTrackChangeTime(): Instant {
        logger.apiCall("getTrackLayoutLocationTrackChangeTime")
        return locationTrackService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/reference-lines")
    fun getTrackLayoutReferenceLineChangeTime(): Instant {
        logger.apiCall("getTrackLayoutReferenceLineChangeTime")
        return referenceLineService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/switches")
    fun getTrackLayoutSwitchChangeTime(): Instant {
        logger.apiCall("getTrackLayoutSwitchChangeTime")
        return switchService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/km-posts")
    fun getTrackLayoutKmPostChangeTime(): Instant {
        logger.apiCall("getTrackLayoutSwitchChangeTime")
        return kmPostService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/plans")
    fun getGeometryPlanChangeTime(): Instant {
        logger.apiCall("getGeometryPlanChangeTime")
        return geometryService.getGeometryPlanChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/projects")
    fun getProjectChangeTime(): Instant {
        logger.apiCall("getProjectChangeTime")
        return geometryService.getProjectChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/authors")
    fun getAuthorChangeTime(): Instant {
        logger.apiCall("getAuthorChangeTime")
        return geometryService.getAuthorChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/publications")
    fun getPublicationChangeTime(): Instant {
        logger.apiCall("getPublicationChangeTime")
        return publicationService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/projektivelho-documents")
    fun getPVDocumentChangeTime(): Instant {
        logger.apiCall("getPVDocumentChangeTime")
        return pvDocumentService.getDocumentChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/splits")
    fun getSplitChangeTime(): Instant {
        logger.apiCall("getSplitChangeTime")
        return splitService.getChangeTime()
    }
}
