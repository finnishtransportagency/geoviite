package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.KnownFileSuffix.CSV
import fi.fta.geoviite.infra.util.toFileDownloadResponse
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam

@GeoviiteController("/geocoding")
class GeocodingController(
    private val geocodingService: GeocodingService,
    private val locationTrackService: LocationTrackService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/address/{trackNumberId}")
    fun getTrackAddress(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("coordinate") coordinate: Point,
    ): ResponseEntity<TrackMeter> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(geocodingService.getAddressIfWithin(layoutContext, trackNumberId, coordinate))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location/{locationTrackId}")
    fun getTrackPoint(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("locationTrackId") locationTrackId: IntId<LocationTrack>,
        @RequestParam("address") address: TrackMeter,
    ): ResponseEntity<AddressPoint> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(locationTrackService.getTrackPoint(layoutContext, locationTrackId, address))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/address-pointlist/{locationTrackId}")
    fun getAlignmentAddressPoints(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("locationTrackId") locationTrackId: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentAddresses> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(geocodingService.getAddressPoints(layoutContext, locationTrackId))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location-track/address-pointlist-csv")
    fun getLocationTrackMValues(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ext-id") locationTrackOid: Oid<LocationTrack>,
    ): ResponseEntity<ByteArray> {
        val context = LayoutContext.of(branch, publicationState)
        return locationTrackService
            // TODO: create a fetch-method by OID
            .list(context)
            .find { lt -> lt.externalId == locationTrackOid }
            ?.let { lt -> geocodingService.getAddressPoints(context, lt.id as IntId) }
            ?.let { ap ->
                val points = ap.allPoints
                    .map { p -> listOf(p.address, p.point.x, p.point.y, p.point.m).joinToString(",") }
                    .joinToString("\r\n")
                val content = "rata_osoite,x,y,m\r\n$points"
                toFileDownloadResponse(
                    FileName("rata_$locationTrackOid").withSuffix(CSV),
                    content.toByteArray(Charsets.UTF_8),
                )
            } ?: ResponseEntity(NO_CONTENT)
    }
}
