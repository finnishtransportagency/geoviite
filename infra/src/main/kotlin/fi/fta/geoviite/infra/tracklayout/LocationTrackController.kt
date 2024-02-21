package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.authorization.AUTH_UI_READ
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEndWithId
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.SwitchLinkingService
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

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}", params = ["bbox"])
    fun getLocationTracksNear(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracksNear", "publishType" to publishType, "bbox" to bbox)
        return locationTrackService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}", params = ["searchTerm", "limit"])
    fun searchLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limit", required = true) limit: Int,
    ): List<LocationTrack> {
        logger.apiCall("searchLocationTracks", "searchTerm" to searchTerm, "limit" to limit)
        return locationTrackService.list(publishType, searchTerm, limit)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}")
    fun getLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrack> {
        logger.apiCall("getLocationTrack", "publishType" to publishType, "id" to id)
        return toResponse(locationTrackService.get(publishType, id))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}", params = ["ids"])
    fun getLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<LocationTrack>>,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracks", "publishType" to publishType, "ids" to ids)
        return locationTrackService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/start-and-end")
    fun getLocationTrackStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentStartAndEndWithId<*>> {
        logger.apiCall("getLocationTrackStartAndEnd", "publishType" to publishType, "id" to id)
        val locationTrackAndAlignment = locationTrackService.getWithAlignment(publishType, id)
        return toResponse(locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getLocationTrackStartAndEnd(
                publishType, locationTrack, alignment
            )?.let { AlignmentStartAndEndWithId(locationTrack.id as IntId, it.start, it.end) }
        })
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/start-and-end")
    fun getManyLocationTracksStartsAndEnds(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEndWithId<*>> {
        logger.apiCall("getLocationTrackStartAndEnd", "publishType" to publishType, "ids" to ids)
        return ids.mapNotNull { id ->
            locationTrackService.getWithAlignment(publishType, id)?.let { (locationTrack, alignment) ->
                geocodingService.getLocationTrackStartAndEnd(
                    publishType, locationTrack, alignment
                )?.let { AlignmentStartAndEndWithId(locationTrack.id as IntId, it.start, it.end) }
            }
        }
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/infobox-extras")
    fun getLocationTrackInfoboxExtras(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrackInfoboxExtras> {
        logger.apiCall("getLocationTrackInfoboxExtras", "publishType" to publishType, "id" to id)
        return toResponse(locationTrackService.getInfoboxExtras(publishType, id))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/description")
    fun getDescription(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<LocationTrackDescription> {
        logger.apiCall("getDescription", "publishType" to publishType, "ids" to ids)
        return ids.mapNotNull { id ->
            id.let { locationTrackService.get(publishType, it) }?.let { lt ->
                LocationTrackDescription(
                    id, locationTrackService.getFullDescription(
                        publishType, lt
                    )
                )
            }
        }
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/end-points")
    fun getLocationTrackAlignmentEndpoints(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrackEndpoint> {
        logger.apiCall("getLocationTrackAlignmentEndpoints", "bbox" to bbox)
        return locationTrackService.getLocationTrackEndpoints(bbox, publishType)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/validation")
    fun validateLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<ValidatedAsset<LocationTrack>> {
        logger.apiCall("validateLocationTrack", "publishType" to publishType, "id" to id)
        return publicationService.validateLocationTracks(listOf(id), publishType).firstOrNull().let(::toResponse)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/validation/switches")
    fun validateLocationTrackSwitches(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<SwitchValidationWithSuggestedSwitch> {
        logger.apiCall("validateLocationTrackSwitches", "publishType" to publishType, "id" to id)
        val switchSuggestions = switchLinkingService.getTrackSwitchSuggestions(publishType, id)
        val switchValidation = publicationService.validateSwitches(switchSuggestions.map { (id, _) -> id }, publishType)
        return switchValidation.map { validatedAsset ->
            SwitchValidationWithSuggestedSwitch(
                validatedAsset.id, validatedAsset, switchSuggestions.find { it.first == validatedAsset.id }?.second
            )
        }
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/location-tracks/draft")
    fun insertLocationTrack(@RequestBody request: LocationTrackSaveRequest): IntId<LocationTrack> {
        logger.apiCall("insertLocationTrack", "request" to request)
        return locationTrackService.insert(request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/location-tracks/draft/{id}")
    fun updateLocationTrack(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        logger.apiCall("updateLocationTrack", "locationTrackId" to locationTrackId, "request" to request)
        return locationTrackService.update(locationTrackId, request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/location-tracks/draft/{id}")
    fun deleteLocationTrack(@PathVariable("id") id: IntId<LocationTrack>): IntId<LocationTrack> {
        logger.apiCall("deleteLocationTrack", "id" to id)
        return locationTrackService.deleteDraft(id).id
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/draft/non-linked")
    fun getNonLinkedLocationTracks(): List<LocationTrack> {
        logger.apiCall("getNonLinkedLocationTracks")
        return locationTrackService.listNonLinked()
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/change-times")
    fun getLocationTrackChangeInfo(
        @PathVariable("id") id: IntId<LocationTrack>,
        @PathVariable("publishType") publishType: PublishType,
    ): ResponseEntity<DraftableChangeInfo> {
        logger.apiCall("getLocationTrackChangeInfo", "id" to id)
        return toResponse(locationTrackService.getDraftableChangeInfo(id, publishType))
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        logger.apiCall(
            "getTrackSectionsByPlan", "publishType" to publishType, "id" to id, "bbox" to boundingBox
        )
        return locationTrackService.getMetadataSections(id, publishType, boundingBox)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/track-numbers/{publicationState}/{trackNumberId}/location-tracks")
    fun getTrackNumberTracksByName(
        @PathVariable("publicationState") publicationState: PublishType,
        @PathVariable("trackNumberId") trackNumberId: IntId<TrackLayoutTrackNumber>,
        @RequestParam("locationTrackNames") names: List<AlignmentName>,
    ): List<LocationTrack> {
        logger.apiCall(
            "getTrackNumberTracksByName",
            "publicationState" to publicationState,
            "trackNumberId" to trackNumberId,
            "names" to names,
        )
        return locationTrackService.list(publicationState, trackNumberId, names)
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-track-owners")
    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        logger.apiCall("getLocationTrackOwners")
        return locationTrackService.getLocationTrackOwners()
    }

    @PreAuthorize(AUTH_UI_READ)
    @GetMapping("/location-tracks/{publishType}/{id}/splitting-initialization-parameters")
    fun getSplittingInitializationParameters(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<SplittingInitializationParameters> {
        logger.apiCall(
            "getSplittingInitializationParameters",
            "publishType" to publishType,
            "id" to id,
        )
        return toResponse(locationTrackService.getSplittingInitializationParameters(id, publishType))
    }
}
