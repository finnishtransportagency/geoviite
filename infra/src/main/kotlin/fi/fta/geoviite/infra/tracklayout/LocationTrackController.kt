package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.authorization.AUTH_ALL_WRITE
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.logging.apiCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.toResponse
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/track-layout/location-tracks")
class LocationTrackController(
    private val locationTrackService: LocationTrackService,
    private val geocodingService: GeocodingService,
    private val publicationService: PublicationService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["bbox"])
    fun getLocationTracksNear(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracksNear", "publishType" to publishType, "bbox" to bbox)
        return locationTrackService.listNear(publishType, bbox)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["searchTerm", "limit"])
    fun searchLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limit", required = true) limit: Int,
    ): List<LocationTrack> {
        logger.apiCall("searchLocationTracks", "searchTerm" to searchTerm, "limit" to limit)
        return locationTrackService.list(publishType, searchTerm, limit)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}")
    fun getLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrack> {
        logger.apiCall("getLocationTrack", "publishType" to publishType, "id" to id)
        return toResponse(locationTrackService.get(publishType, id))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}", params = ["ids"])
    fun getLocationTracks(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("ids", required = true) ids: List<IntId<LocationTrack>>,
    ): List<LocationTrack> {
        logger.apiCall("getLocationTracks", "publishType" to publishType, "ids" to ids)
        return locationTrackService.getMany(publishType, ids)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/duplicate-of")
    fun getLocationTrackDuplicateOfList(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<LocationTrackDuplicate> {
        logger.apiCall("getLocationTrack", "id" to id, "publishType" to publishType, "id" to id)
        return locationTrackService.getDuplicates(id, publishType)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/start-and-end")
    fun getLocationTrackStartAndEnd(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentStartAndEnd> {
        logger.apiCall("getLocationTrackStartAndEnd", "publishType" to publishType, "id" to id)
        val locationTrackAndAlignment = locationTrackService.getWithAlignment(publishType, id)
        return toResponse(locationTrackAndAlignment?.let { (locationTrack, alignment) ->
            geocodingService.getLocationTrackStartAndEnd(
                publishType, locationTrack, alignment
            )
        })
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/switches-at-ends")
    fun getLocationTrackSwitchesAtEnds(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<SwitchesAtEnds> {
        logger.apiCall("getLocationTrackSwitchesAtEnds", "publishType" to publishType, "id" to id)
        return toResponse(locationTrackService.getSwitchesAtEnds(id, publishType))
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/description")
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/end-points")
    fun getLocationTrackAlignmentEndpoints(
        @PathVariable("publishType") publishType: PublishType,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrackEndpoint> {
        logger.apiCall("getLocationTrackAlignmentEndpoints", "bbox" to bbox)
        return locationTrackService.getLocationTrackEndpoints(bbox, publishType)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("{publishType}/{id}/validation")
    fun validateLocationTrack(
        @PathVariable("publishType") publishType: PublishType,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ValidatedAsset<LocationTrack> {
        logger.apiCall("validateLocationTrack", "publishType" to publishType, "id" to id)
        return publicationService.validateLocationTrack(id, publishType)
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PostMapping("/draft")
    fun insertLocationTrack(@RequestBody request: LocationTrackSaveRequest): IntId<LocationTrack> {
        logger.apiCall("insertLocationTrack", "request" to request)
        return locationTrackService.insert(request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @PutMapping("/draft/{id}")
    fun updateLocationTrack(
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        logger.apiCall("updateLocationTrack", "locationTrackId" to locationTrackId, "request" to request)
        return locationTrackService.update(locationTrackId, request).id
    }

    @PreAuthorize(AUTH_ALL_WRITE)
    @DeleteMapping("/draft/{id}")
    fun deleteLocationTrack(@PathVariable("id") id: IntId<LocationTrack>): IntId<LocationTrack> {
        logger.apiCall("deleteLocationTrack", "id" to id)
        return locationTrackService.deleteUnpublishedDraft(id).id
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/draft/non-linked")
    fun getNonLinkedLocationTracks(): List<LocationTrack> {
        logger.apiCall("getNonLinkedLocationTracks")
        return locationTrackService.listNonLinked()
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{id}/change-times")
    fun getLocationTrackChangeInfo(@PathVariable("id") id: IntId<LocationTrack>): ChangeTimes {
        logger.apiCall("getLocationTrackChangeInfo", "id" to id)
        return locationTrackService.getChangeTimes(id)
    }

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/{publishType}/{id}/plan-geometry")
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

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/location-track-owners")
    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        logger.apiCall("getLocationTrackOwners")
        return locationTrackService.getLocationTrackOwners()
    }
}
