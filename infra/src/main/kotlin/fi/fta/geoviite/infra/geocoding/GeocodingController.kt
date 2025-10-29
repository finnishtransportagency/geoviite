package fi.fta.geoviite.infra.geocoding

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.toResponse
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
        @PathVariable("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>,
        @RequestParam("coordinate") coordinate: Point,
    ): ResponseEntity<TrackMeter> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(geocodingService.getAddressIfWithin(layoutContext, trackNumberId, coordinate))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/projection-lines/{trackNumberId}")
    fun getMeterProjectionLines(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>,
    ): ResponseEntity<List<ProjectionLine<ReferenceLineM>>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(
            geocodingService.getGeocodingContext(layoutContext, trackNumberId)?.getProjectionLines(Resolution.ONE_METER)
        )
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/location/{locationTrackId}")
    fun getTrackPoint(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("locationTrackId") locationTrackId: IntId<LocationTrack>,
        @RequestParam("address") address: TrackMeter,
    ): ResponseEntity<AddressPoint<LocationTrackM>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(locationTrackService.getTrackPoint(layoutContext, locationTrackId, address))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/address-pointlist/{alignmentId}")
    fun getAlignmentAddressPoints(
        @PathVariable(LAYOUT_BRANCH) branch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("alignmentId") locationTrackId: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentAddresses<LocationTrackM>> {
        val layoutContext = LayoutContext.of(branch, publicationState)
        return toResponse(geocodingService.getAddressPoints(layoutContext, locationTrackId))
    }
}
