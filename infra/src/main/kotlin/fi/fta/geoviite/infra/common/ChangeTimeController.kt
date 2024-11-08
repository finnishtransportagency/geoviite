package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.projektivelho.PVDocumentService
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.ratko.RatkoOperatingPointDao
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.*
import java.time.Instant
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.bind.annotation.GetMapping

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
    val layoutDesign: Instant,
)

@GeoviiteController("/change-times")
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
    private val layoutDesignDao: LayoutDesignDao,
) {

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/collected")
    @Transactional(readOnly = true)
    fun getCollectedChangeTimes(): CollectedChangeTimes {
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
            layoutDesign = layoutDesignDao.getChangeTime(),
        )
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/track-numbers")
    fun getTrackLayoutTrackNumberChangeTime(): Instant {
        return trackNumberService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/location-tracks")
    fun getTrackLayoutLocationTrackChangeTime(): Instant {
        return locationTrackService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/reference-lines")
    fun getTrackLayoutReferenceLineChangeTime(): Instant {
        return referenceLineService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/switches")
    fun getTrackLayoutSwitchChangeTime(): Instant {
        return switchService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/km-posts")
    fun getTrackLayoutKmPostChangeTime(): Instant {
        return kmPostService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/plans")
    fun getGeometryPlanChangeTime(): Instant {
        return geometryService.getGeometryPlanChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/projects")
    fun getProjectChangeTime(): Instant {
        return geometryService.getProjectChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/authors")
    fun getAuthorChangeTime(): Instant {
        return geometryService.getAuthorChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/publications")
    fun getPublicationChangeTime(): Instant {
        return publicationService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/projektivelho-documents")
    fun getPVDocumentChangeTime(): Instant {
        return pvDocumentService.getDocumentChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/splits")
    fun getSplitChangeTime(): Instant {
        return splitService.getChangeTime()
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/layout-designs")
    fun getLayoutDesignChangeTime(): Instant {
        return layoutDesignDao.getChangeTime()
    }
}
