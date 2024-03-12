package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.Integration
import fi.fta.geoviite.infra.error.IntegrationNotConfiguredException
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ratko")
class RatkoController(
    private val ratkoServiceParam: RatkoService?,
    private val ratkoStatusService: RatkoStatusService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val ratkoService by lazy {
        ratkoServiceParam ?: throw IntegrationNotConfiguredException(Integration.RATKO)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/push")
    fun pushChangesToRatko(): HttpStatus {
        logger.apiCall("pushChangesToRatko")
        ratkoService.pushChangesToRatko()

        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/push-location-tracks")
    fun pushLocationTracksToRatko(@RequestBody changes: List<LocationTrackChange>): ResponseEntity<String> {
        logger.apiCall("pushLocationTracksToRatko", "changes" to changes)
        ratkoService.pushLocationTracksToRatko(changes)
        return ResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/errors/{publishId}")
    fun getRatkoPushErrors(
        @PathVariable("publishId") publishId: IntId<Publication>,
    ): ResponseEntity<RatkoPushErrorWithAsset> {
        logger.apiCall("getRatkoPushErrors", "publishId" to publishId)
        return toResponse(ratkoStatusService.getRatkoPushError(publishId))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/is-online")
    fun getRatkoOnlineStatus(): RatkoClient.RatkoStatus {
        logger.apiCall("ratkoIsOnline")
        return ratkoStatusService.getRatkoOnlineStatus()
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/fetch-operating-points-from-ratko")
    fun fetchOperatingPointsFromRatko(): HttpStatus {
        logger.apiCall("fetchOperatingPointsFromRatko")
        ratkoService.fetchOperatingPointsFromRatko()

        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/operating-points")
    fun getOperatingPoints(bbox: BoundingBox): List<RatkoOperatingPoint> {
        return ratkoService.getOperatingPoints(bbox);
    }

}
