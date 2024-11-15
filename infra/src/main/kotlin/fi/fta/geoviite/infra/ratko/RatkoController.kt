package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.Integration
import fi.fta.geoviite.infra.error.IntegrationNotConfiguredException
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.ratko.model.RatkoOperatingPoint
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/ratko")
class RatkoController(private val ratkoServiceParam: RatkoService?, private val ratkoLocalService: RatkoLocalService) {
    private val ratkoService by lazy { ratkoServiceParam ?: throw IntegrationNotConfiguredException(Integration.RATKO) }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/push")
    fun pushChangesToRatko(): HttpStatus {
        ratkoService.pushChangesToRatko(LayoutBranch.main)

        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/push-designs")
    fun pushDesignChangesToRatko(): HttpStatus {
        // TODO implement
        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @PostMapping("/push-location-tracks")
    fun pushLocationTracksToRatko(@RequestBody changes: List<LocationTrackChange>): ResponseEntity<Unit> {
        ratkoService.pushLocationTracksToRatko(LayoutBranch.main, changes)
        return ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/errors/{publicationId}")
    fun getRatkoPushErrors(
        @PathVariable("publicationId") publicationId: IntId<Publication>
    ): ResponseEntity<RatkoPushErrorWithAsset> {
        return toResponse(ratkoLocalService.getRatkoPushError(publicationId))
    }

    @PreAuthorize(AUTH_BASIC)
    @GetMapping("/is-online")
    fun getRatkoOnlineStatus(): RatkoClient.RatkoStatus {
        return ratkoLocalService.getRatkoOnlineStatus()
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/update-operating-points-from-ratko")
    fun updateOperatingPointsFromRatko(): HttpStatus {
        ratkoService.updateOperatingPointsFromRatko()

        return HttpStatus.NO_CONTENT
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/operating-points")
    fun getOperatingPoints(@RequestParam bbox: BoundingBox): List<RatkoOperatingPoint> {
        return ratkoLocalService.getOperatingPoints(bbox)
    }
}
