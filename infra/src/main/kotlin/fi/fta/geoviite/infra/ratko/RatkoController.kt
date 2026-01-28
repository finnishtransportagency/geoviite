package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_PUBLICATION
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.Integration
import fi.fta.geoviite.infra.error.IntegrationNotConfiguredException
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody

@GeoviiteController("/ratko")
class RatkoController(private val ratkoServiceParam: RatkoService?, private val ratkoLocalService: RatkoLocalService) {
    private val ratkoService by lazy { ratkoServiceParam ?: throw IntegrationNotConfiguredException(Integration.RATKO) }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/push")
    fun pushChangesToRatko(): ResponseEntity<Unit> {
        ratkoService.retryLatestFailedPush()

        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/push-designs")
    fun pushDesignChangesToRatko() {
        ratkoService.pushDesignChangesToRatko()
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @PostMapping("/push-location-tracks")
    fun pushLocationTracksToRatko(@RequestBody changes: List<LocationTrackChange>): ResponseEntity<Unit> {
        ratkoService.pushLocationTracksToRatko(LayoutBranch.main, changes, extIdsProvided = null)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_PUBLICATION)
    @GetMapping("/errors/current")
    fun getLatestPublicationError(): ResponseEntity<RatkoPushErrorAndDetails> =
        toResponse(ratkoLocalService.fetchCurrentRatkoPushError())

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/is-online")
    fun getRatkoOnlineStatus(): RatkoClient.RatkoStatus {
        return ratkoLocalService.getRatkoOnlineStatus()
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/update-operational-points-from-ratko")
    fun updateOperationalPointsFromRatko(): HttpStatus {
        ratkoService.updateOperationalPointsFromRatko()

        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/signal-assets/{x}/{y}/{z}")
    fun getSignalAsset(
        @PathVariable("x") x: Int,
        @PathVariable("y") y: Int,
        @PathVariable("z") z: Int,
        @RequestParam("cluster") cluster: Boolean = false,
    ): ResponseEntity<ByteArray> {
        return toResponse(ratkoService.getSignalAsset(x, y, z, cluster))
    }
}
