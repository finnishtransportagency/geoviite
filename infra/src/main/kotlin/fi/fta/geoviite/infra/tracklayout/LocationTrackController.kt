package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_EDIT_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_LAYOUT_DRAFT
import fi.fta.geoviite.infra.authorization.LAYOUT_BRANCH
import fi.fta.geoviite.infra.authorization.PUBLICATION_STATE
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.linking.LocationTrackSaveRequest
import fi.fta.geoviite.infra.linking.switches.SuggestedSwitch
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.map.ALIGNMENT_POLYGON_BUFFER
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.publication.PublicationValidationService
import fi.fta.geoviite.infra.publication.ValidateTransition
import fi.fta.geoviite.infra.publication.ValidatedAsset
import fi.fta.geoviite.infra.publication.draftTransitionOrOfficialState
import fi.fta.geoviite.infra.publication.publicationInOrMergeFromBranch
import fi.fta.geoviite.infra.split.SplittingInitializationParameters
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.toResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam

data class SwitchValidationWithSuggestedSwitch(
    val switchId: IntId<LayoutSwitch>,
    val switchValidation: ValidatedAsset<LayoutSwitch>,
    val switchSuggestion: SuggestedSwitch?,
)

@GeoviiteController("/track-layout")
class LocationTrackController(
    private val locationTrackService: LocationTrackService,
    private val searchService: LayoutSearchService,
    private val publicationValidationService: PublicationValidationService,
    private val switchLinkingService: SwitchLinkingService,
) {

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["bbox"])
    fun getLocationTracksNear(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("bbox") bbox: BoundingBox,
    ): List<LocationTrack> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.listNear(context, bbox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["searchTerm", "limit"])
    fun searchLocationTracks(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("searchTerm", required = true) searchTerm: FreeText,
        @RequestParam("limit", required = true) limit: Int,
    ): List<LocationTrack> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return searchService.searchAllLocationTracks(context, searchTerm, limit)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}")
    fun getLocationTrack(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrack> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(locationTrackService.get(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}", params = ["ids"])
    fun getLocationTracks(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids", required = true) ids: List<IntId<LocationTrack>>,
    ): List<LocationTrack> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.getMany(context, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/start-and-end")
    fun getLocationTrackStartAndEnd(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<AlignmentStartAndEnd<LocationTrack>> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.getStartAndEnd(context, listOf(id)).singleOrNull().let(::toResponse)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/start-and-end")
    fun getManyLocationTracksStartsAndEnds(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
    ): List<AlignmentStartAndEnd<LocationTrack>> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.getStartAndEnd(context, ids)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/infobox-extras")
    fun getLocationTrackInfoboxExtras(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LocationTrackInfoboxExtras> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(locationTrackService.getInfoboxExtras(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/relinkable-switches-count")
    fun getRelinkableSwitchesCount(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<Int> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(locationTrackService.getRelinkableSwitchesCount(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/description")
    fun getDescription(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @RequestParam("ids") ids: List<IntId<LocationTrack>>,
        @RequestParam("lang") lang: LocalizationLanguage,
    ): List<LocationTrackDescription> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return ids.mapNotNull { id ->
            id.let { locationTrackService.get(context, it) }
                ?.let { lt -> LocationTrackDescription(id, locationTrackService.getFullDescription(context, lt, lang)) }
        }
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/validation")
    fun validateLocationTrack(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<ValidatedAsset<LocationTrack>> {
        return publicationValidationService
            .validateLocationTracks(draftTransitionOrOfficialState(publicationState, layoutBranch), listOf(id))
            .firstOrNull()
            .let(::toResponse)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/validation/switches")
    fun validateLocationTrackSwitches(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): List<SwitchValidationWithSuggestedSwitch> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        val switchSuggestions = switchLinkingService.getTrackSwitchSuggestions(context, id)
        val target = ValidateTransition(publicationInOrMergeFromBranch(layoutBranch, publicationState))
        val switchValidation =
            publicationValidationService.validateSwitches(target, switchSuggestions.map { (id, _) -> id })
        return switchValidation.map { validatedAsset ->
            SwitchValidationWithSuggestedSwitch(
                validatedAsset.id,
                validatedAsset,
                switchSuggestions.find { it.first == validatedAsset.id }?.second,
            )
        }
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/location-tracks/{$LAYOUT_BRANCH}/draft")
    fun insertLocationTrack(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        return locationTrackService.insert(layoutBranch, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PutMapping("/location-tracks/{$LAYOUT_BRANCH}/draft/{id}")
    fun updateLocationTrack(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") locationTrackId: IntId<LocationTrack>,
        @RequestBody request: LocationTrackSaveRequest,
    ): IntId<LocationTrack> {
        return locationTrackService.update(layoutBranch, locationTrackId, request).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @DeleteMapping("/location-tracks/{$LAYOUT_BRANCH}/draft/{id}")
    fun deleteLocationTrack(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): IntId<LocationTrack> {
        return locationTrackService.deleteDraft(layoutBranch, id).id
    }

    @PreAuthorize(AUTH_EDIT_LAYOUT)
    @PostMapping("/location-tracks/{$LAYOUT_BRANCH}/{id}/cancel")
    fun cancelLocationTrack(
        @PathVariable(LAYOUT_BRANCH) branch: DesignBranch,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<IntId<LocationTrack>> = toResponse(locationTrackService.cancel(branch, id)?.id)

    @PreAuthorize(AUTH_VIEW_LAYOUT_DRAFT)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/draft/non-linked")
    fun getNonLinkedLocationTracks(@PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch): List<LocationTrack> {
        return locationTrackService.listNonLinked(layoutBranch)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/change-info")
    fun getLocationTrackChangeInfo(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<LayoutAssetChangeInfo> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(locationTrackService.getLayoutAssetChangeInfo(context, id))
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/plan-geometry")
    fun getTrackSectionsByPlan(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("bbox") boundingBox: BoundingBox? = null,
    ): List<AlignmentPlanSection> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.getMetadataSections(context, id, boundingBox)
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/overlapping-plans")
    fun getOverlappingPlanHeaders(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
        @RequestParam("startKm") startKmNumber: KmNumber? = null,
        @RequestParam("endKm") endKmNumber: KmNumber? = null,
    ): List<GeometryPlanHeader> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.getOverlappingPlanHeaders(
            context,
            id,
            ALIGNMENT_POLYGON_BUFFER,
            startKmNumber,
            endKmNumber,
        )
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/track-numbers/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{trackNumberId}/location-tracks")
    fun getTrackNumberTracksByName(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("trackNumberId") trackNumberId: IntId<LayoutTrackNumber>,
        @RequestParam("locationTrackNames") names: List<AlignmentName>,
        @RequestParam("includeDeleted") includeDeleted: Boolean = true,
    ): List<LocationTrack> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return locationTrackService.list(context, includeDeleted, trackNumberId, names)
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/location-track-owners")
    fun getLocationTrackOwners(): List<LocationTrackOwner> {
        return locationTrackService.getLocationTrackOwners()
    }

    @PreAuthorize(AUTH_VIEW_DRAFT_OR_OFFICIAL_BY_PUBLICATION_STATE)
    @GetMapping("/location-tracks/{$LAYOUT_BRANCH}/{$PUBLICATION_STATE}/{id}/splitting-initialization-parameters")
    fun getSplittingInitializationParameters(
        @PathVariable(LAYOUT_BRANCH) layoutBranch: LayoutBranch,
        @PathVariable(PUBLICATION_STATE) publicationState: PublicationState,
        @PathVariable("id") id: IntId<LocationTrack>,
    ): ResponseEntity<SplittingInitializationParameters> {
        val context = LayoutContext.of(layoutBranch, publicationState)
        return toResponse(locationTrackService.getSplittingInitializationParameters(context, id))
    }

    @PreAuthorize(AUTH_VIEW_LAYOUT)
    @GetMapping("/location-tracks/{id}/oids")
    fun getLocationTrackOids(@PathVariable("id") id: IntId<LocationTrack>): Map<LayoutBranch, Oid<LocationTrack>> {
        return locationTrackService.getExternalIdsByBranch(id)
    }
}
