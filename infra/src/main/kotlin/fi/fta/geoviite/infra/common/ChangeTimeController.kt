package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.geometry.GeometryService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
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
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/collected")
    fun getCollectedChangeTimes(): CollectedChangeTimes {
        logger.apiCall("getCollectedChangeTimes")
        return CollectedChangeTimes(
            layoutTrackNumber = trackNumberService.getChangeTime(),
            layoutLocationTrack = locationTrackService.getChangeTime(),
            layoutReferenceLine = referenceLineService.getChangeTime(),
            layoutKmPost = kmPostService.getChangeTime(),
            layoutSwitch = switchService.getChangeTime(),
            geometryPlan = geometryService.getGeometryPlanChangeTime(),
        )
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/track-numbers")
    fun getTrackLayoutTrackNumberChangeTime(): Instant {
        logger.apiCall("getTrackLayoutTrackNumberChangeTime")
        return trackNumberService.getChangeTime()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/location-tracks")
    fun getTrackLayoutLocationTrackChangeTime(): Instant {
        logger.apiCall("getTrackLayoutLocationTrackChangeTime")
        return locationTrackService.getChangeTime()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/reference-lines")
    fun getTrackLayoutReferenceLineChangeTime(): Instant {
        logger.apiCall("getTrackLayoutReferenceLineChangeTime")
        return referenceLineService.getChangeTime()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/switches")
    fun getTrackLayoutSwitchChangeTime(): Instant {
        logger.apiCall("getTrackLayoutSwitchChangeTime")
        return switchService.getChangeTime()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/km-posts")
    fun getTrackLayoutKmPostChangeTime(): Instant {
        logger.apiCall("getTrackLayoutSwitchChangeTime")
        return kmPostService.getChangeTime()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/plans")
    fun getGeometryPlanChangeTime(): Instant {
        logger.apiCall("getGeometryPlanChangeTime")
        return geometryService.getGeometryPlanChangeTime()
    }
}
