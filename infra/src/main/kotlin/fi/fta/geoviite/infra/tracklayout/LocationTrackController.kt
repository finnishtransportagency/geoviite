package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.*
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEndWithId
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout")
class LocationTrackController(
    private val locationTrackService: LocationTrackService,
    private val geocodingService: GeocodingService,
    private val publicationService: PublicationService,
    private val switchService: LayoutSwitchService,
    private val switchLinkingService: SwitchLinkingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}", params = ["bbox"])
    fun getLocationTracksNear(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracksNear", "$PUBLICATION_STATE" to publicationState, "bbox" to bbox)
        return locationTrackService.listNear(publicationState, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}", params = ["searchTerm", "limit"])
    fun searchLocationTracks(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limit", required = true) limit: Int,
    ): List<LocationTrack> {
        logger.apiCall("searchLocationTracks", "$PUBLICATION_STATE" to publicationState, "searchTerm" to searchTerm, "limit" to limit)
        return locationTrackService.list(publicationState, searchTerm, limit)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}")
    fun getLocationTrack(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrack> {
        logger.apiCall("getLocationTrack", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(locationTrackService.get(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}", params = ["ids"])
    fun getLocationTracks(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<LocationTrack>>,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracks", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return locationTrackService.getMany(publicationState, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/start-and-end")
    fun getLocationTrackStartAndEnd(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentStartAndEndWithId<*>> {
        logger.apiCall("getLocationTrackStartAndEnd", "$PUBLICATION_STATE" to publicationState, "id" to id)
        val locationTrackAndAlignment = locationTrackService.getWithAlignment(publicationState, id)
        return toResponse(locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getLocationTrackStartAndEnd(
                publicationState, locationTrack, alignment
            )?.let { AlignmentStartAndEndWithId(locationTrack.id as IntId, it.start, it.end) }
        })
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/start-and-end")
    fun getManyLocationTracksStartsAndEnds(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEndWithId<*>> {
        logger.apiCall("getLocationTrackStartAndEnd", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return ids.mapNotNull { id ->
            locationTrackService.getWithAlignment(publicationState, id)?.let { (locationTrack, alignment) ->
                geocodingService.getLocationTrackStartAndEnd(
                    publicationState, locationTrack, alignment
                )?.let { AlignmentStartAndEndWithId(locationTrack.id as IntId, it.start, it.end) }
            }
        }
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/infobox-extras")
    fun getLocationTrackInfoboxExtras(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrackInfoboxExtras> {
        logger.apiCall("getLocationTrackInfoboxExtras", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return toResponse(locationTrackService.getInfoboxExtras(publicationState, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/description")
    fun getDescription(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<LocationTrackDescription> {
        logger.apiCall("getDescription", "$PUBLICATION_STATE" to publicationState, "ids" to ids)
        return ids.mapNotNull { id ->
            id.let { locationTrackService.get(publicationState, it) }?.let { lt ->
                LocationTrackDescription(
                    id, locationTrackService.getFullDescription(
                        publicationState, lt
                    )
                )
            }
        }
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/end-points")
    fun getLocationTrackAlignmentEndpoints(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrackEndpoint> {
        logger.apiCall("getLocationTrackAlignmentEndpoints", "$PUBLICATION_STATE" to publicationState, "bbox" to bbox)
        return locationTrackService.getLocationTrackEndpoints(bbox, publicationState)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/validation")
    fun validateLocationTrack(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<ValidatedAsset<LocationTrack>> {
        logger.apiCall("validateLocationTrack", "$PUBLICATION_STATE" to publicationState, "id" to id)
        return publicationService.validateLocationTracks(listOf(id), publicationState).firstOrNull().let(::toResponse)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/validation/switches")
    fun validateLocationTrackSwitches(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<SwitchValidationWithSuggestedSwitch> {
        logger.apiCall("validateLocationTrackSwitches", "$PUBLICATION_STATE" to publicationState, "id" to id)
        val switchSuggestions = switchLinkingService.getTrackSwitchSuggestions(publicationState, id)
        val switchValidation = publicationService.validateSwitches(switchSuggestions.map { (id, _) -> id }, publicationState)
        return switchValidation.map { validatedAsset ->
            SwitchValidationWithSuggestedSwitch(
                validatedAsset.id, validatedAsset, switchSuggestions.find { it.first == validatedAsset.id }?.second
            )
        }
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/location-tracks/draft")
    fun insertLocationTrack(@RequestBody request: LocationTrackSaveRequest): IntId<LocationTrack> {
        logger.apiCall("insertLocationTrack", "request" to request)
        return locationTrackService.insert(request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/location-tracks/draft/{id}")
    fun updateLocationTrack(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        logger.apiCall("updateLocationTrack", "locationTrackId" to locationTrackId, "request" to request)
        return locationTrackService.update(locationTrackId, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/location-tracks/draft/{id}")
    fun deleteLocationTrack(@PathVariable("id") id: IntId<LocationTrack>): IntId<LocationTrack> {
        logger.apiCall("deleteLocationTrack", "id" to id)
        return locationTrackService.deleteDraft(id).id
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/location-tracks/draft/non-linked")
    fun getNonLinkedLocationTracks(): List<LocationTrack> {
        logger.apiCall("getNonLinkedLocationTracks")
        return locationTrackService.listNonLinked()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/change-times")
    fun getLocationTrackChangeInfo(
        @PathVariable("id") id: IntId<LocationTrack>,
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        logger.apiCall("getLocationTrackChangeInfo", "id" to id, "$PUBLICATION_STATE" to publicationState)
        return toResponse(locationTrackService.getLayoutAssetChangeInfo(id, publicationState))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        logger.apiCall(
            "getTrackSectionsByPlan", "$PUBLICATION_STATE" to publicationState, "id" to id, "bbox" to boundingBox
        )
        return locationTrackService.getMetadataSections(id, publicationState, boundingBox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/track-numbers/{$PUBLICATION_STATE}/{trackNumberId}/location-tracks")
    fun getTrackNumberTracksByName(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("locationTrackNames") names: List<AlignmentName>,
    ): List<LocationTrack> {
        logger.apiCall(
            "getTrackNumberTracksByName",
            "$PUBLICATION_STATE" to publicationState,
            "trackNumberId" to trackNumberId,
            "names" to names,
        )
        return locationTrackService.list(publicationState, trackNumberId, names)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/location-track-owners")
    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        logger.apiCall("getLocationTrackOwners")
        return locationTrackService.getLocationTrackOwners()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$PUBLICATION_STATE}/{id}/splitting-initialization-parameters")
    fun getSplittingInitializationParameters(
        @PathVariable("$PUBLICATION_STATE") publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<SplittingInitializationParameters> {
        logger.apiCall(
            "getSplittingInitializationParameters",
            "$PUBLICATION_STATE" to publicationState,
            "id" to id,
        )
        return toResponse(locationTrackService.getSplittingInitializationParameters(id, publicationState))
    }
}
