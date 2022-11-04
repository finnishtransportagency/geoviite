package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Point
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/geocoding")
class GeocodingController(
    private val geocodingService: GeocodingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/address/{trackNumberId}")
    fun getTrackAddress(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("coordinate") coordinate: Point,
    ): ResponseEntity<TrackMeter> {
        logger.apiCall("getTrackAddress", "trackNumberId" to trackNumberId, "coordinate" to coordinate)
        return geocodingService.getTrackAddress(trackNumberId, coordinate, publishType)
            ?.let { (address, intersect) -> if (intersect != IntersectType.WITHIN) null else address }
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/location/{alignmentId}")
    fun getTrackPoint(
        @PathVariable("alignmentId") alignmentId: IntId<LocationTrack>,
        @RequestParam("address") address: TrackMeter,
    ): ResponseEntity<AddressPoint> {
        logger.apiCall("getTrackPoint", "alignmentId" to alignmentId, "address" to address)
        return geocodingService.getTrackLocation(alignmentId, address, OFFICIAL)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/address-pointlist/{alignmentId}")
    fun getAlignmentAddressPoints(
        @PathVariable("alignmentId") alignmentId: IntId<LocationTrack>,
        @PathVariable("publishType") publishType: PublishType,
    ): ResponseEntity<AlignmentAddresses> {
        logger.apiCall("getAlignmentAddressPoints", "alignmentId" to alignmentId)
        return geocodingService.getAddressPoints(alignmentId, publishType)
            ?.let { ResponseEntity(it, HttpStatus.OK) }
            ?: ResponseEntity(HttpStatus.NO_CONTENT)
    }
}
