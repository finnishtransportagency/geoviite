package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.linking.SwitchRelinkingSuggestion
import fi.fta.geoviite.infra.linking.SwitchRelinkingValidationResult
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType
import fi.fta.geoviite.infra.publication.VALIDATION_SWITCH
import fi.fta.geoviite.infra.publication.validateSwitchLocationTrackLinkStructure
import fi.fta.geoviite.infra.publication.validateWithParams
import fi.fta.geoviite.infra.split.VALIDATION_SPLIT
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SwitchPlacingRequest
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.asDraft
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class SwitchTrackRelinkingValidationService
@Autowired
constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val switchLinkingService: SwitchLinkingService,
    private val switchLibraryService: SwitchLibraryService,
    private val geocodingService: GeocodingService,
) {

    @Transactional(readOnly = true)
    fun validateRelinkingTrack(
        branch: LayoutBranch,
        trackId: IntId<LocationTrack>,
    ): List<SwitchRelinkingValidationResult> {
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(branch.draft, trackId)
        val switchIds = switchLinkingService.collectAllSwitchesOnTrackAndNearby(branch, track, alignment)
        val originalSwitches = getOriginalSwitches(branch, switchIds)
        val switchStructures = originalSwitches.map { switchLibraryService.getSwitchStructure(it.switchStructureId) }
        val originalLocations = getOriginalLocations(originalSwitches, switchStructures)

        val switchPlacingRequests = currentSwitchLocationsAsSwitchPlacingRequests(switchIds, originalLocations)
        val switchSuggestions = collectSwitchSuggestionsAtPoints(branch, switchPlacingRequests)
        val changedLocationTracks =
            collectLocationTrackChangesFromSwitchSuggestions(branch, switchSuggestions, switchPlacingRequests)

        val geocodingContext =
            geocodingService.getGeocodingContext(branch.draft, track.trackNumberId)
                ?: return switchIds.zip(originalSwitches) { switchId, originalSwitch ->
                    failRelinkingValidationFor(switchId, originalSwitch)
                }

        return switchIds.mapIndexed { index, switchId ->
            validateChangeFromSwitchRelinking(
                track,
                geocodingContext,
                switchId,
                switchSuggestions[index],
                originalSwitches[index],
                switchLibraryService.getSwitchStructure(originalSwitches[index].switchStructureId),
                changedLocationTracks[index],
            )
        }
    }

    private fun getOriginalSwitches(branch: LayoutBranch, switchIds: List<IntId<TrackLayoutSwitch>>) =
        switchService.getMany(branch.draft, switchIds).also { foundSwitches ->
            require(switchIds.size == foundSwitches.size) {
                val notFoundSwitches = switchIds.filterNot { id -> foundSwitches.any { found -> found.id == id } }
                "did not find switches with IDs $notFoundSwitches"
            }
        }

    private fun collectSwitchSuggestionsAtPoints(
        branch: LayoutBranch,
        switchPlacingRequests: List<SwitchPlacingRequest>,
    ): List<SuggestedSwitchWithOriginallyLinkedTracks?> =
        switchLinkingService.getSuggestedSwitchesWithOriginallyLinkedTracks(branch, switchPlacingRequests).map {
            pointAssociation ->
            pointAssociation.keys().firstOrNull()
        }

    private fun collectLocationTrackChangesFromSwitchSuggestions(
        branch: LayoutBranch,
        switchSuggestions: List<SuggestedSwitchWithOriginallyLinkedTracks?>,
        switchPlacingRequests: List<SwitchPlacingRequest>,
    ): List<List<Pair<LocationTrack, LayoutAlignment>>> =
        lookupTracksForSuggestedSwitchValidation(branch, switchSuggestions.map { it?.suggestedSwitch })
            .mapIndexed { index, tracks -> index to tracks }
            .parallelStream()
            .map { (index, originalTracks) ->
                switchSuggestions[index]?.let { suggestion ->
                    withChangesFromLinkingSwitch(
                        suggestion.suggestedSwitch,
                        switchPlacingRequests[index].layoutSwitchId,
                        originalTracks,
                    )
                }
            }
            .toList()

    private fun lookupTracksForSuggestedSwitchValidation(
        branch: LayoutBranch,
        suggestedSwitches: List<SuggestedSwitch?>,
    ): List<Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>> {
        val changedTracksIds =
            suggestedSwitches.asSequence().mapNotNull { it?.trackLinks?.keys }.flatten().distinct().toList()
        val tracks =
            locationTrackService.getManyWithAlignments(branch.draft, changedTracksIds).associateBy { it.first.id }
        return suggestedSwitches.map { suggestedSwitch ->
            suggestedSwitch?.trackLinks?.keys?.associateWith { id -> tracks.getValue(id) } ?: mapOf()
        }
    }
}

// some validation logic depends on draftness state, so we need to pre-draft tracks for online
// validation
private fun draft(tracks: List<Pair<LocationTrack, LayoutAlignment>>) =
    tracks.map { (track, alignment) -> asDraft(track.branch, track) to alignment }

private fun currentSwitchLocationsAsSwitchPlacingRequests(
    switchIds: List<IntId<TrackLayoutSwitch>>,
    locations: List<Point>,
) = locations.zip(switchIds) { location, switchId -> SwitchPlacingRequest(SamplingGridPoints(location), switchId) }

private fun validateChangeFromSwitchRelinking(
    track: LocationTrack,
    geocodingContext: GeocodingContext,
    switchId: IntId<TrackLayoutSwitch>,
    suggestedSwitchWithOriginallyLinkedTracks: SuggestedSwitchWithOriginallyLinkedTracks?,
    originalSwitch: TrackLayoutSwitch,
    switchStructure: SwitchStructure,
    changedTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): SwitchRelinkingValidationResult {
    return if (suggestedSwitchWithOriginallyLinkedTracks == null) failRelinkingValidationFor(switchId, originalSwitch)
    else {
        val suggestedSwitch = suggestedSwitchWithOriginallyLinkedTracks.suggestedSwitch
        val validationResults =
            validateForSplit(
                suggestedSwitch,
                originalSwitch,
                switchStructure,
                track,
                changedTracks,
                suggestedSwitchWithOriginallyLinkedTracks.originallyLinkedTracks.keys.toList(),
            )
        val presentationJointLocation = getSuggestedLocation(switchId, suggestedSwitch, switchStructure)
        val address =
            requireNotNull(geocodingContext.getAddress(presentationJointLocation)) {
                "Could not geocode relinked location for switch $switchId on track $track"
            }
        SwitchRelinkingValidationResult(
            switchId,
            SwitchRelinkingSuggestion(presentationJointLocation, address.first),
            validationResults,
        )
    }
}

private fun validateForSplit(
    suggestedSwitch: SuggestedSwitch,
    originalSwitch: TrackLayoutSwitch,
    switchStructure: SwitchStructure,
    track: LocationTrack,
    changedTracksFromSwitchSuggestion: List<Pair<LocationTrack, LayoutAlignment>>,
    currentSwitchLocationTrackConnections: List<IntId<LocationTrack>>,
): List<LayoutValidationIssue> {
    val createdSwitch = createModifiedLayoutSwitchLinking(suggestedSwitch, originalSwitch)

    val originTrackLinkErrors =
        validateRelinkingRetainsLocationTrackConnections(
            suggestedSwitch,
            track,
            originalSwitch.name,
            currentSwitchLocationTrackConnections,
        )
    val publicationValidationErrorsMapped =
        validateSwitchLocationTrackLinkStructure(
                createdSwitch,
                switchStructure,
                draft(changedTracksFromSwitchSuggestion),
            )
            .map { error ->
                // Structure based issues aren't critical for splitting/relinking -> turn them
                // into warnings
                LayoutValidationIssue(LayoutValidationIssueType.WARNING, error.localizationKey, error.params)
            }

    return publicationValidationErrorsMapped + originTrackLinkErrors
}

private fun validateRelinkingRetainsLocationTrackConnections(
    suggestedSwitch: SuggestedSwitch,
    track: LocationTrack,
    switchName: SwitchName,
    currentConnections: List<IntId<LocationTrack>>,
): List<LayoutValidationIssue> {
    val suggestedConnections = suggestedSwitch.trackLinks.filter { link -> link.value.isLinked() }.keys

    return listOfNotNull(
        validateWithParams(suggestedConnections.containsAll(currentConnections), LayoutValidationIssueType.ERROR) {
            "$VALIDATION_SPLIT.track-links-missing-after-relinking" to
                localizationParams("switchName" to switchName, "sourceName" to track.name)
        }
    )
}

private fun failRelinkingValidationFor(switchId: IntId<TrackLayoutSwitch>, originalSwitch: TrackLayoutSwitch) =
    SwitchRelinkingValidationResult(
        switchId,
        null,
        listOf(
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                "$VALIDATION_SWITCH.track-linkage.relinking-failed",
                mapOf("switch" to originalSwitch.name),
            )
        ),
    )

private fun getOriginalLocations(originalSwitches: List<TrackLayoutSwitch>, switchStructures: List<SwitchStructure>) =
    originalSwitches.zip(switchStructures) { switch, structure ->
        checkNotNull(switch.getJoint(structure.presentationJointNumber)) {
                "expected switch ${switch.id} to have a location"
            }
            .location
    }

private fun getSuggestedLocation(
    switchId: IntId<TrackLayoutSwitch>,
    suggestedSwitch: SuggestedSwitch,
    switchStructure: SwitchStructure,
) =
    checkNotNull(suggestedSwitch.joints.find { joint -> joint.number == switchStructure.presentationJointNumber }) {
            "expected suggested switch for switch $switchId to have location"
        }
        .location