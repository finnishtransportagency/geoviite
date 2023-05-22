package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/ratko")
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoController(private val ratkoService: RatkoService) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_WRITE)
    @GetMapping("/push")
    fun pushChangesToRatko() {
        logger.apiCall("pushChangesToRatko")
        ratkoService.pushChangesToRatko()
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/push-location-tracks")
    fun pushLocationTracksToRatko(@RequestBody locationTrackChanges: List<LocationTrackChange>): ResponseEntity<String> {
        logger.apiCall("pushLocationTracksToRatko")
        ratkoService.pushLocationTracksToRatko(locationTrackChanges)
        return ResponseEntity(HttpStatus.OK)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/errors/{publishId}")
    fun getRatkoPushErrors(
        @PathVariable("publishId") publishId: IntId<Publication>,
    ): ResponseEntity<RatkoPushErrorWithAsset> {
        logger.apiCall("getRatkoPushErrors")
        return toResponse(ratkoService.getRatkoPushError(publishId))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/is-online")
    fun getRatkoOnlineStatus(): RatkoClient.RatkoStatus {
        logger.apiCall("ratkoIsOnline")
        return ratkoService.getRatkoOnlineStatus()
    }
}
