package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.PUBLISH_TYPE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/geocoding")
class GeocodingController(
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/address/{trackNumberId}")
    fun getTrackAddress(
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("coordinate") coordinate: Point,
    ): ResponseEntity<TrackMeter> {
        logger.apiCall("getTrackAddress", "trackNumberId" to trackNumberId, "coordinate" to coordinate)
        return toResponse(geocodingService.getAddressIfWithin(publishType, trackNumberId, coordinate))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/location/{locationTrackId}")
    fun getTrackPoint(
        @PathVariable("locationTrackId") locationTrackId: IntId<LocationTrack>,
        @RequestParam("address") address: TrackMeter,
    ): ResponseEntity<AddressPoint> {
        logger.apiCall("getTrackPoint", "locationTrackId" to locationTrackId, "address" to address)
        return toResponse(locationTrackService.getTrackPoint(OFFICIAL, locationTrackId, address))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLISH_TYPE)
    @GetMapping("/{$PUBLISH_TYPE}/address-pointlist/{alignmentId}")
    fun getAlignmentAddressPoints(
        @PathVariable("alignmentId") locationTrackId: IntId<LocationTrack>,
        @PathVariable("$PUBLISH_TYPE") publishType: PublishType,
    ): ResponseEntity<AlignmentAddresses> {
        logger.apiCall("getAlignmentAddressPoints", "locationTrackId" to locationTrackId)
        return toResponse(geocodingService.getAddressPoints(locationTrackId, publishType))
    }
}
