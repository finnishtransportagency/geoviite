package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.publication.validateSwitchLocationTrackLinkStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private val temporarySwitchId: IntId<TrackLayoutSwitch> = IntId(-1)

private const val TOLERANCE_JOINT_LOCATION_SAME_POINT = 0.001
private const val MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS = 5.0

@Service
class SwitchLinkingService @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
    private val switchDao: LayoutSwitchDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
    private val switchFittingService: SwitchFittingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun getSuggestedSwitches(bbox: BoundingBox): List<SuggestedSwitch> {
        return switchFittingService.getFitsInArea(bbox).map(::matchFittedSwitch)
    }

    @Transactional(readOnly = true)
    fun getSuggestedSwitches(points: List<Pair<IPoint, IntId<TrackLayoutSwitch>>>): List<SuggestedSwitch?> {
        logger.serviceCall("getSuggestedSwitches", "points" to points)
        return getSuggestedSwitchesWithRelevantTracks(points).map { it?.first }
    }

    // "relevant" = definitely includes tracks that the switches are linked to after the suggestion, but also tracks
    // that are just within the expanded bounding box of the fitted switch joints, or that the switch was originally
    // linked to
    private fun getSuggestedSwitchesWithRelevantTracks(
        points: List<Pair<IPoint, IntId<TrackLayoutSwitch>>>,
    ): List<Pair<SuggestedSwitch, Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>>?> {
        val originalSwitches = points.map { (_, switchId) -> switchService.getOrThrow(DRAFT, switchId) }
        val originallyLinkedLocationTracksBySwitch =
            collectOriginallyLinkedLocationTracksBySwitch(points.map { (_, switchId) -> switchId })

        val pointsWithStructures = points.mapIndexed { index, (point) ->
            point to originalSwitches[index].switchStructureId
        }

        return switchFittingService.getFitsAtPoints(pointsWithStructures).mapIndexed { index, fit ->
            fit?.let {
                val tracksAroundFit = findLocationTracksForMatchingSwitchToTracks(fit)
                val originallyLinkedTracks = originallyLinkedLocationTracksBySwitch[points[index].second] ?: mapOf()
                val relevantTracks = tracksAroundFit + originallyLinkedTracks
                val match = matchFittedSwitchToTracks(
                    fit,
                    switchLibraryService.getSwitchStructure(fit.switchStructureId),
                    relevantTracks,
                    points[index].second,
                    fit.geometrySwitchId?.let { id -> geometryDao.getSwitch(id).name },
                )
                match to relevantTracks.filterKeys { track -> match.trackLinks.containsKey(track)}
            }
        }
    }

    fun getSuggestedSwitch(location: IPoint, switchId: IntId<TrackLayoutSwitch>): SuggestedSwitch? =
        getSuggestedSwitches(listOf(location to switchId)).getOrNull(0)

    @Transactional(readOnly = true)
    fun getSuggestedSwitch(createParams: SuggestedSwitchCreateParams): SuggestedSwitch? {
        logger.serviceCall("getSuggestedSwitch", "createParams" to createParams)

        return switchFittingService.getFitAtEndpoint(createParams)?.let(::matchFittedSwitch)
    }

    @Transactional
    fun saveSwitchLinking(suggestedSwitch: SuggestedSwitch, switchId: IntId<TrackLayoutSwitch>): DaoResponse<TrackLayoutSwitch> {
        logger.serviceCall("saveSwitchLinking", "switchLinkingParameters" to suggestedSwitch)

        suggestedSwitch.geometrySwitchId?.let(::verifyPlanNotHidden)
        val originalTracks = suggestedSwitch.trackLinks.keys.associateWith { id ->
            locationTrackService.getWithAlignmentOrThrow(DRAFT, id)
        }
        saveLocationTrackChanges(
            withChangesFromLinkingSwitch(suggestedSwitch, switchId, originalTracks),
            originalTracks
        )
        return updateLayoutSwitch(suggestedSwitch, switchId)
    }

    private fun saveLocationTrackChanges(
        maybeChanged: List<Pair<LocationTrack, LayoutAlignment>>,
        original: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    ) = maybeChanged.forEach { (locationTrack, alignment) ->
        val (originalLocationTrack, originalAlignment) = original[locationTrack.id as IntId] ?: (null to null)
        if (originalAlignment != alignment) {
            locationTrackService.saveDraft(locationTrack, alignment)
        } else if (originalLocationTrack != locationTrack) {
            locationTrackService.saveDraft(locationTrack)
        }
    }

    @Transactional(readOnly = true)
    fun matchFittedSwitch(
        fittedSwitch: FittedSwitch,
        switchId: IntId<TrackLayoutSwitch>? = null,
    ): SuggestedSwitch = matchFittedSwitchToTracks(
        fittedSwitch,
        switchLibraryService.getSwitchStructure(fittedSwitch.switchStructureId),
        findLocationTracksForMatchingSwitchToTracks(fittedSwitch, switchId),
        switchId,
        fittedSwitch.geometrySwitchId?.let { id -> geometryDao.getSwitch(id).name },
    )

    private fun findLocationTracksForMatchingSwitchToTracks(
        fittedSwitch: FittedSwitch,
        switchId: IntId<TrackLayoutSwitch>? = null,
    ): Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> {
        fun indexTracksInBounds(boundingBox: BoundingBox?) = boundingBox
            ?.let { bounds -> locationTrackDao.fetchVersionsNear(DRAFT, bounds) }
            ?.map(locationTrackService::getWithAlignment)
            ?.associate { trackAndAlignment -> trackAndAlignment.first.id as IntId to trackAndAlignment } ?: mapOf()

        val originalTracks = if (switchId == null) emptyMap() else {
            switchDao.findLocationTracksLinkedToSwitch(DRAFT, switchId).associate { ids ->
                val trackAndAlignment = locationTrackService.getWithAlignment(ids.rowVersion)
                (trackAndAlignment.first.id as IntId) to trackAndAlignment
            }
        }
        return listOfNotNull(
            originalTracks,
            if (switchId != null) indexTracksInBounds(getSwitchBoundsFromTracks(originalTracks.values, switchId)) else null,
            indexTracksInBounds(getSwitchBoundsFromSwitchFit(fittedSwitch)
        )).reduceRight { a, b -> a + b}
    }

    private fun collectOriginallyLinkedLocationTracksBySwitch(
        switches: List<IntId<TrackLayoutSwitch>>,
    ): Map<IntId<TrackLayoutSwitch>, Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>> =
        switchDao
            .findLocationTracksLinkedToSwitches(DRAFT, switches)
            .map {
                locationTrackService.getWithAlignment(it.rowVersion)
            }
            // might have found both an official and a draft version of a track, we prefer the drafts
            .sortedBy { if (it.first.isDraft) 0 else 1 }.distinct()
            .flatMap { trackAndAlignment ->
                trackAndAlignment.first.switchIds.map { switchId -> switchId to trackAndAlignment }
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, tracksAndAlignments) -> tracksAndAlignments.associateBy { it.first.id as IntId } }

    @Transactional
    fun relinkTrack(trackId: IntId<LocationTrack>): List<TrackSwitchRelinkingResult> {
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, trackId)

        val originalSwitches = collectAllSwitchesOnTrackAndNearby(track, track.alignmentVersion!!, alignment).map { switchId ->
            switchId to switchService.getOrThrow(DRAFT, switchId)
        }
        val originallyLinkedLocationTracksBySwitch =
            collectOriginallyLinkedLocationTracksBySwitch(originalSwitches.map { (switchId) -> switchId })

        val changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> =
            mutableMapOf()

        val relinkingResults = originalSwitches.map { (switchId, originalSwitch) ->
            val switchStructure = switchLibraryService.getSwitchStructure(originalSwitch.switchStructureId)
            val presentationJointLocation = originalSwitch.getJoint(switchStructure.presentationJointNumber)?.location
            checkNotNull(presentationJointLocation) { "no presentation joint on switch ${originalSwitch.id}" }
            val nearbyTracksForFit =
                locationTrackService.getLocationTracksNear(presentationJointLocation, DRAFT).let { nearby ->
                    val map = nearby.associateBy { it.first.id as IntId }
                    map + changedLocationTracks.filterKeys { id -> map.containsKey(id) }
                }.values.toList()

            val fittedSwitch =
                createSuggestedSwitchByPoint(presentationJointLocation, switchStructure, nearbyTracksForFit)
            if (fittedSwitch == null) {
                TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.NOT_AUTOMATICALLY_LINKABLE)
            } else {
                val nearbyTracksForMatch = findLocationTracksForMatchingSwitchToTracks(
                    fittedSwitch,
                ).let { nearby ->
                    val original = originallyLinkedLocationTracksBySwitch[switchId] ?: mapOf()
                    nearby + original + changedLocationTracks.filterKeys { key ->
                        nearby.containsKey(key) || original.containsKey(key)
                    }
                }
                val match = matchFittedSwitchToTracks(fittedSwitch, switchStructure, nearbyTracksForMatch, switchId)
                withChangesFromLinkingSwitch(
                    match,
                    switchId,
                    nearbyTracksForMatch.filterKeys { track -> match.trackLinks.containsKey(track) },
                ).forEach { track -> changedLocationTracks[track.first.id as IntId] = track }
                updateLayoutSwitch(match, switchId)
                TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.RELINKED)
            }
        }
        changedLocationTracks.values.forEach { (track, alignment) -> locationTrackService.saveDraft(track, alignment) }
        return relinkingResults
    }

    @Transactional(readOnly = true)
    fun validateRelinkingTrack(trackId: IntId<LocationTrack>): List<SwitchRelinkingValidationResult> {
        val trackVersion = locationTrackDao.fetchDraftVersionOrThrow(trackId)
        val track = trackVersion.let(locationTrackDao::fetch)
        val alignment = requireNotNull(track.alignmentVersion) {
            "No alignment on track ${track.toLog()}"
        }.let(alignmentDao::fetch)

        val switchIds = collectAllSwitchesOnTrackAndNearby(track, track.alignmentVersion, alignment)

        val replacementSwitchLocations = switchIds.map { switchId ->
            val switch = switchService.getOrThrow(DRAFT, switchId)
            switchService.getPresentationJointOrThrow(switch).location to switchId
        }

        val switchSuggestions = getSuggestedSwitchesWithRelevantTracks(replacementSwitchLocations)
        val geocodingContext = requireNotNull(geocodingService.getGeocodingContext(DRAFT, track.trackNumberId)) {
            "Could not get geocoding context: trackNumber=${track.trackNumberId} track=$track"
        }
        return switchIds.mapIndexed { index, switchId ->
            val suggestionWithTracks = switchSuggestions[index]
            if (suggestionWithTracks == null) SwitchRelinkingValidationResult(switchId, null, listOf())
            else {
                val (suggestedSwitch, relevantTracks) = suggestionWithTracks
                val (validationResults, presentationJointLocation) = validateForSplit(
                    suggestedSwitch, switchId, relevantTracks
                )
                val address = requireNotNull(geocodingContext.getAddress(presentationJointLocation)) {
                    "Could not geocode relinked location for switch $switchId on track $track"
                }
                SwitchRelinkingValidationResult(
                    switchId,
                    SwitchRelinkingSuggestion(presentationJointLocation, address.first),
                    validationResults,
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getTrackSwitchSuggestions(publicationState: PublicationState, trackId: IntId<LocationTrack>) =
        getTrackSwitchSuggestions(publicationState, locationTrackDao.getOrThrow(publicationState, trackId))

    @Transactional(readOnly = true)
    fun getTrackSwitchSuggestions(
        publicationState: PublicationState,
        track: LocationTrack
    ): List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch?>> {
        logger.serviceCall("getTrackSwitchSuggestions", "track" to track)

        val alignment = requireNotNull(track.alignmentVersion) {
            "No alignment on track ${track.toLog()}"
        }.let(alignmentDao::fetch)

        val switchIds = collectAllSwitches(track, alignment)
        val replacementSwitchLocations = switchIds.map { switchId ->
            val switch = switchService.getOrThrow(publicationState, switchId)
            switchService.getPresentationJointOrThrow(switch).location to switchId
        }
        val switchSuggestions = getSuggestedSwitches(replacementSwitchLocations)
        return switchIds.mapIndexed { index, id -> id to switchSuggestions[index] }
    }

    private fun collectAllSwitchesOnTrackAndNearby(
        locationTrack: LocationTrack,
        alignmentVersion: RowVersion<LayoutAlignment>,
        alignment: LayoutAlignment,
    ): List<IntId<TrackLayoutSwitch>> {
        val topologySwitches = listOfNotNull(
            locationTrack.topologyStartSwitch?.switchId, locationTrack.topologyEndSwitch?.switchId
        )
        val segmentSwitches = alignment.segments.mapNotNull { segment -> segment.switchId as IntId? }
        val nearbySwitches = switchDao.findSwitchesNearAlignment(alignmentVersion)

        return (topologySwitches + segmentSwitches + nearbySwitches).distinct()
    }

    private fun validateForSplit(
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<TrackLayoutSwitch>,
        relevantLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    ): Pair<List<PublicationValidationError>, Point> {

        val changedTracks = withChangesFromLinkingSwitch(
            suggestedSwitch,
            switchId,
            relevantLocationTracks,
        )
        val createdSwitch = createModifiedLayoutSwitchLinking(suggestedSwitch, switchId)
        val presentationJointLocation = switchService.getPresentationJointOrThrow(createdSwitch).location
        val switchStructure = switchLibraryService.getSwitchStructure(suggestedSwitch.switchStructureId)

        return validateSwitchLocationTrackLinkStructure(
            createdSwitch,
            switchStructure,
            draft(changedTracks),
        ) to presentationJointLocation
    }

    private fun createModifiedLayoutSwitchLinking(
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<TrackLayoutSwitch>,
    ): TrackLayoutSwitch {
        val layoutSwitch = switchService.getOrThrow(DRAFT, switchId)
        val newGeometrySwitchId = suggestedSwitch.geometrySwitchId ?: layoutSwitch.sourceId

        return layoutSwitch.copy(
            sourceId = newGeometrySwitchId,
            joints = suggestedSwitch.joints,
            source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
        )
    }

    private fun updateLayoutSwitch(suggestedSwitch: SuggestedSwitch, switchId: IntId<TrackLayoutSwitch>): DaoResponse<TrackLayoutSwitch> {
        return createModifiedLayoutSwitchLinking(suggestedSwitch, switchId).let { modifiedLayoutSwitch ->
            switchService.saveDraft(modifiedLayoutSwitch)
        }
    }

    private fun verifyPlanNotHidden(id: IntId<GeometrySwitch>) {
        if (geometryDao.getSwitchPlanId(id)?.let(geometryDao::fetchPlanVersion)?.let(geometryDao::getPlanHeader)?.isHidden != false) throw LinkingFailureException(
            message = "Cannot link a plan that is hidden", localizedMessageKey = "plan-hidden"
        )
    }
}

fun matchFittedSwitchToTracks(
    fittedSwitch: FittedSwitch,
    switchStructure: SwitchStructure,
    relevantLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>?,
    name: SwitchName? = null,
): SuggestedSwitch {
    val segmentLinks = calculateSwitchLinkingJoints(fittedSwitch, relevantLocationTracks, switchStructure, switchId)
    val topologyLinks =
        findTopologyLinks(relevantLocationTracks, fittedSwitch, segmentLinks, switchId ?: temporarySwitchId)
    val trackLinks = relevantLocationTracks.entries.mapNotNull { (id, trackAndAlignment) ->
        val segmentLink = segmentLinks[id] ?: listOf()
        val topologyLink = topologyLinks[id]
        val hadOriginalLink = collectAllSwitches(trackAndAlignment.first, trackAndAlignment.second).contains(switchId)

        // "relevant" location tracks can contain tracks that are just nearby but not actually affected by linking at
        // all; filter those out
        if (segmentLink.isEmpty() && topologyLink == null && !hadOriginalLink) null else {
            id to SwitchLinkingTrackLinks(segmentLink, topologyLinks[id])
        }
    }.associate { it }

    return SuggestedSwitch(
        joints = fittedSwitch.joints.map(::suggestedSwitchJointToTrackLayoutSwitchJoint),
        trackLinks = trackLinks,
        geometrySwitchId = fittedSwitch.geometrySwitchId,
        switchStructureId = fittedSwitch.switchStructureId,
        alignmentEndPoint = fittedSwitch.alignmentEndPoint,
        name = name ?: SwitchName(switchStructure.baseType.name),
    )
}

private fun suggestedSwitchJointToTrackLayoutSwitchJoint(sj: FittedSwitchJoint) = TrackLayoutSwitchJoint(
    sj.number, sj.location, sj.locationAccuracy
)

private fun findTopologyLinks(
    nearbyLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    fittedSwitch: FittedSwitch,
    segmentLinks: Map<IntId<LocationTrack>, List<SwitchLinkingJoint>>,
    switchId: IntId<TrackLayoutSwitch>,
): Map<IntId<LocationTrack>, SwitchLinkingTopologicalTrackLink> {
    return nearbyLocationTracks.entries
        .filter { (id) -> !segmentLinks.containsKey(id) }
        .mapNotNull { (locationTrackId, trackAndAlignment) ->
            val (locationTrack, alignment) = trackAndAlignment
            fun tracksNear(point: IPoint) =
                filterTracksNear(locationTrack, nearbyLocationTracks.values, point.toPoint())

            val nearbyTracks = NearbyTracks(
                alignment.firstSegmentStart?.let(::tracksNear) ?: listOf(),
                alignment.lastSegmentEnd?.let(::tracksNear) ?: listOf()
            )

            val locationTrackWithUpdatedTopology = calculateLocationTrackTopology(
                locationTrack,
                alignment,
                startChanged = true,
                endChanged = true,
                nearbyTracks = nearbyTracks,
                newSwitch = TopologyLinkFindingSwitch(fittedSwitch.joints, switchId)
            )
            (if (locationTrackWithUpdatedTopology.topologyStartSwitch?.switchId == switchId) {
                SwitchLinkingTopologicalTrackLink(
                    locationTrackWithUpdatedTopology.topologyStartSwitch.jointNumber,
                    TrackEnd.START,
                )
            } else if (locationTrackWithUpdatedTopology.topologyEndSwitch?.switchId == switchId) {
                SwitchLinkingTopologicalTrackLink(
                    locationTrackWithUpdatedTopology.topologyEndSwitch.jointNumber,
                    TrackEnd.END,
                )
            } else null)?.let { locationTrackId to it }
        }
        .associate { it }
}

fun getSwitchBoundsFromTracks(
    tracks: Collection<Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>,
): BoundingBox? = tracks.flatMap { (track, alignment) ->
    listOfNotNull(
        track.topologyStartSwitch?.let { ts -> if (ts.switchId == switchId) alignment.firstSegmentStart else null },
        track.topologyEndSwitch?.let { ts -> if (ts.switchId == switchId) alignment.lastSegmentEnd else null }) + alignment.segments.flatMap { segment ->
        if (segment.switchId != switchId) listOf() else listOfNotNull(
            if (segment.startJointNumber != null) segment.segmentStart else null,
            if (segment.endJointNumber != null) segment.segmentEnd else null
        )
    }
}.let(::boundingBoxAroundPointsOrNull)

private fun getSwitchBoundsFromSwitchFit(
    suggestedSwitch: FittedSwitch,
): BoundingBox? = boundingBoxAroundPointsOrNull(suggestedSwitch.joints.map { joint -> joint.location }, TRACK_SEARCH_AREA_SIZE)

private fun calculateSwitchLinkingJoints(
    suggestedSwitch: FittedSwitch,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchStructure: SwitchStructure,
    switchId: IntId<TrackLayoutSwitch>?,
): Map<IntId<LocationTrack>, List<SwitchLinkingJoint>> {
    val switchJointsByLocationTrack = suggestedSwitch.joints
        .flatMap { joint -> joint.matches.map { match -> match.locationTrackId } }
        .distinct()
        .associateWith { locationTrackId ->
            filterMatchingJointsBySwitchAlignment(switchStructure, suggestedSwitch.joints, locationTrackId)
        }
        .filter { it.value.isNotEmpty() }

    return switchJointsByLocationTrack.entries.associate { (locationTrackId, switchJoints) ->
        locationTrackId to switchJoints.flatMap { suggestedSwitchJoint ->
            suggestedSwitchJoint.matches.map { match ->
                val alignment = tracks.getValue(locationTrackId).second
                val segment = alignment.segments[match.segmentIndex]
                val snappedMatch = if (segment.switchId != null && segment.switchId != switchId) {
                    tryToSnapOverlappingSwitchSegmentToNearbySegment(tracks.getValue(locationTrackId).second, match)
                } else match
                SwitchLinkingJoint(
                    suggestedSwitchJoint.number,
                    snappedMatch.segmentIndex,
                    snappedMatch.m,
                    alignment.segments[snappedMatch.segmentIndex].seekPointAtSegmentM(snappedMatch.m).point.toPoint(),
                )
            }
        }.sortedBy { it.m }
    }
}

private fun findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
    existingSwitchId: IntId<TrackLayoutSwitch>,
    layoutSegments: List<LayoutSegment>,
    searchIndexRange: IntProgression,
): IndexedValue<LayoutSegment>? {
    val layoutSegmentIndicesAreValid =
        searchIndexRange.first in layoutSegments.indices && searchIndexRange.last in layoutSegments.indices

    val step = searchIndexRange.step
    val firstAdjacentIndexIsValid = (searchIndexRange.first + step) in layoutSegments.indices
    val lastAdjacentIndexIsValid = (searchIndexRange.last + step) in layoutSegments.indices

    val adjacentSegmentIndicesAreValid = firstAdjacentIndexIsValid && lastAdjacentIndexIsValid

    require(layoutSegmentIndicesAreValid) {
        "Invalid searchIndexRange: $searchIndexRange contains indices outside of layoutSegments (${layoutSegments.indices})"
    }

    require(adjacentSegmentIndicesAreValid) {
        "Invalid searchIndexRange: $searchIndexRange contains adjacent indices outside of layoutSegments (${layoutSegments.indices})"
    }

    for (i in searchIndexRange) {
        val segment = layoutSegments[i]

        val existingSwitchIdMatchesSegment = existingSwitchId == segment.switchId
        if (!existingSwitchIdMatchesSegment) {
            return null
        }

        val adjacentSegmentHasNoSwitch = layoutSegments[i + searchIndexRange.step].switchId == null
        if (adjacentSegmentHasNoSwitch) {
            return IndexedValue(i, segment)
        }
    }

    return null
}

fun tryToSnapOverlappingSwitchSegmentToNearbySegment(
    layoutAlignment: LayoutAlignment,
    match: FittedSwitchJointMatch,
): FittedSwitchJointMatch {
    val referencedLayoutSegment = layoutAlignment.segments[match.segmentIndex]

    val segmentIsSwitchFree = referencedLayoutSegment.switchId == null
    if (segmentIsSwitchFree) {
        return match
    }

    // Snapping towards the start of the location track.
    match.segmentIndex
        .takeIf { segmentIndex -> segmentIndex > 1 }
        ?.let { segmentIndex -> IntProgression.fromClosedRange(segmentIndex, 1, -1) }
        ?.let { negativeSearchIndexDirection ->
            findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
                referencedLayoutSegment.switchId as IntId,
                layoutAlignment.segments,
                searchIndexRange = negativeSearchIndexDirection,
            )
        }
        ?.let { indexedExistingSwitchStartSegment ->
            val distanceToPreviousSwitchLineStart =
                match.m - indexedExistingSwitchStartSegment.value.startM
            val hasAdjacentLayoutSegment = indexedExistingSwitchStartSegment.index > 0

            if (hasAdjacentLayoutSegment && distanceToPreviousSwitchLineStart <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS) {
                return match.copy(
                    m = match.m - distanceToPreviousSwitchLineStart,
                    segmentIndex = indexedExistingSwitchStartSegment.index - 1
                )
            }
        }

    // Snapping towards the end of the location track.
    match.segmentIndex
        .takeIf { segmentIndex -> segmentIndex < layoutAlignment.segments.lastIndex - 1 }
        ?.let { segmentIndex ->
            IntProgression.fromClosedRange(segmentIndex, layoutAlignment.segments.lastIndex - 1, 1)
        }
        ?.let { positiveSearchIndexDirection ->
            findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
                referencedLayoutSegment.switchId as IntId,
                layoutAlignment.segments,
                searchIndexRange = positiveSearchIndexDirection,
            )
        }
        ?.let { indexedExistingSwitchEndSegment ->
            val distanceToPreviousSwitchLineEnd =
                indexedExistingSwitchEndSegment.value.endM - match.m
            val hasAdjacentLayoutSegment =
                indexedExistingSwitchEndSegment.index < layoutAlignment.segments.lastIndex

            if (hasAdjacentLayoutSegment && distanceToPreviousSwitchLineEnd <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS) {
                return match.copy(
                    m = match.m + distanceToPreviousSwitchLineEnd,
                    segmentIndex = indexedExistingSwitchEndSegment.index + 1
                )
            }
        }

    // Couldn't snap, possibly due to too much overlap or adjacent switch segment(s) already contained another switch.
    return match
}

private fun filterTracksNear(
    centerTrack: LocationTrack,
    tracksWithAlignments: Collection<Pair<LocationTrack, LayoutAlignment>>,
    point: Point,
): List<Pair<LocationTrack, LayoutAlignment>> {
    val boundingBox = boundingBoxAroundPoint(point, 1.0)
    return tracksWithAlignments.filter { (track, alignment) ->
        centerTrack.id != track.id && alignment.segments.any { segment ->
            val bb = segment.boundingBox
            bb != null && bb.intersects(boundingBox)
        }
    }
}

private fun withChangesFromLinkingSwitch(
    suggestedSwitch: SuggestedSwitch,
    switchId: IntId<TrackLayoutSwitch>,
    originalLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
): List<Pair<LocationTrack, LayoutAlignment>> {
    val existingLinksCleared = withExistingLinksToSwitchCleared(originalLocationTracks, switchId)
    val segmentLinksMade = withSegmentLinks(suggestedSwitch, existingLinksCleared, switchId)
    val topologicalLinksMade = withTopologicalLinks(suggestedSwitch, existingLinksCleared, switchId)
    val onlyDelinked = existingLinksCleared.entries.filter { (id, trackAndAlignment) ->
        !segmentLinksMade.containsKey(id) && !topologicalLinksMade.containsKey(id) && trackAndAlignment != originalLocationTracks[id]
    }.map { it.value }

    return segmentLinksMade.values + topologicalLinksMade.values + onlyDelinked
}

private fun withTopologicalLinks(
    suggestedSwitch: SuggestedSwitch,
    existingLinksCleared: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> {
    val topologicalLinksMade = suggestedSwitch.trackLinks.entries.mapNotNull { (locationTrackId, trackLink) ->
        if (trackLink.topologyJoint == null) null else {
            val (locationTrack, alignment) = existingLinksCleared.getValue(locationTrackId)
            locationTrackId to (updateLocationTrackWithTopologyEndLinking(
                locationTrack, switchId, trackLink.topologyJoint
            ) to alignment)
        }
    }.associate { it }
    return topologicalLinksMade
}

private fun withSegmentLinks(
    suggestedSwitch: SuggestedSwitch,
    existingLinksCleared: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> {
    val segmentLinksMade = suggestedSwitch.trackLinks.entries.mapNotNull { (locationTrackId, trackLink) ->
        if (trackLink.segmentJoints.isEmpty()) null else {
            val (locationTrack, alignment) = existingLinksCleared.getValue(locationTrackId)
            locationTrackId to (locationTrack to updateAlignmentSegmentsWithSwitchLinking(
                alignment, switchId, trackLink.segmentJoints
            ))
        }
    }.associate { it }
    return segmentLinksMade
}

private fun withExistingLinksToSwitchCleared(
    originalLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> =
    originalLocationTracks.mapValues { (_, trackAndAlignment) ->
        val (track, alignment) = trackAndAlignment
        clearLinksToSwitch(track, alignment, switchId)
    }

// some validation logic depends on draftness state, so we need to pre-draft tracks for online validation
private fun draft(tracks: List<Pair<LocationTrack, LayoutAlignment>>) =
    tracks.map { (track, alignment) -> asMainDraft(track) to alignment }


fun updateLocationTrackWithTopologyEndLinking(
    locationTrack: LocationTrack,
    switchId: IntId<TrackLayoutSwitch>,
    link: SwitchLinkingTopologicalTrackLink,
): LocationTrack {
    val topologySwitch = TopologyLocationTrackSwitch(switchId, link.number)
    return if (link.trackEnd == TrackEnd.START) {
        locationTrack.copy(topologyStartSwitch = topologySwitch)
    } else {
        locationTrack.copy(topologyEndSwitch = topologySwitch)
    }
}
fun updateAlignmentSegmentsWithSwitchLinking(
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    matchingJoints: List<SwitchLinkingJoint>,
): LayoutAlignment {
    val segmentIndexRange = matchingJoints.map { it.segmentIndex }.let { ixes -> ixes.min()..ixes.max() }

    val overriddenSwitches = alignment.segments.mapIndexedNotNull { index, segment ->
        if (index in segmentIndexRange) segment.switchId
        else null
    }.distinct()

    val segmentsWithNewSwitch = alignment.segments.map { segment ->
        if (overriddenSwitches.contains(segment.switchId)) segment.withoutSwitch()
        else segment
    }.mapIndexed { index, segment ->
        if (index in segmentIndexRange) {
            val switchLinkingJoints = matchingJoints
                .filter { joint -> joint.segmentIndex == index }

            if (switchLinkingJoints.isEmpty()) {
                //Segment that is between two other segments that are linked to the switch joints
                listOf(segment.copy(switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null))
            } else {
                getSegmentsByLinkingJoints(
                    switchLinkingJoints,
                    segment,
                    layoutSwitchId,
                    index == segmentIndexRange.first,
                    index == segmentIndexRange.last
                )
            }
        } else {
            listOf(segment)
        }
    }

    return alignment.withSegments(combineAdjacentSegmentJointNumbers(segmentsWithNewSwitch, layoutSwitchId))
}

private fun filterMatchingJointsBySwitchAlignment(
    switchStructure: SwitchStructure,
    matchingJoints: List<FittedSwitchJoint>,
    locationTrackId: DomainId<LocationTrack>,
): List<FittedSwitchJoint> {
    val locationTrackSwitchJoints = matchingJoints.map { joint ->
        joint.copy(matches = joint.matches.filter { segment -> segment.locationTrackId == locationTrackId })
    }.filter { it.matches.isNotEmpty() }

    val switchStructureJointNumbers = switchStructure.alignments.firstOrNull { alignment ->
        val frontJoint = alignment.jointNumbers.first()
        val backJoint = alignment.jointNumbers.last()
        val presentationJoint = switchStructure.presentationJointNumber
        val hasFrontJoint = locationTrackSwitchJoints.any { joint -> joint.number == frontJoint }
        val hasBackJoint = locationTrackSwitchJoints.any { joint -> joint.number == backJoint }
        val hasSeparatePresentationJoint =
            presentationJoint != frontJoint &&
                    presentationJoint != backJoint &&
                    alignment.jointNumbers.any { jointNumber -> jointNumber == presentationJoint } &&
                    locationTrackSwitchJoints.any { joint -> joint.number == presentationJoint }

        // Alignment must contain at least two of these ("etujatkos", "takajatkos", presentation joint)
        listOf(hasFrontJoint, hasBackJoint, hasSeparatePresentationJoint).count { it } >= 2
    }?.jointNumbers

    return locationTrackSwitchJoints.filter { joint ->
        switchStructureJointNumbers?.any { structureJoint -> structureJoint == joint.number } ?: false
    }
}

private fun getSegmentsByLinkingJoints(
    linkingJoints: List<SwitchLinkingJoint>,
    segment: LayoutSegment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    isFirstSegment: Boolean,
    isLastSegment: Boolean,
) = linkingJoints.foldIndexed(mutableListOf<LayoutSegment>()) { index, acc, linkingJoint ->
    val jointNumber = linkingJoint.number
    val previousSegment = acc.lastOrNull()?.also { acc.removeLast() } ?: segment
    val suggestedPointM = linkingJoint.m

    if (isSame(segment.startM, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
        //Check if suggested point is start point
        acc.add(setStartJointNumber(segment, layoutSwitchId, jointNumber))
    } else if (isSame(segment.endM, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
        //Check if suggested point is end point
        if (linkingJoints.size == 1) {
            acc.add(setEndJointNumber(previousSegment, layoutSwitchId, jointNumber))
        } else {
            acc.add(previousSegment.copy(endJointNumber = jointNumber))
        }
    } else {
        //Otherwise split the segment
        //StartSplitSegment: before M-value
        //EndSplitSegment: after M-value
        val (startSplitSegment, endSplitSegment) = previousSegment.splitAtM(
            suggestedPointM, TOLERANCE_JOINT_LOCATION_NEW_POINT
        )

        //Handle cases differently when there are multiple joint matches in a single segment
        if (linkingJoints.size == 1) {
            acc.add(
                if (isFirstSegment) startSplitSegment.withoutSwitch()
                else if (isLastSegment) setEndJointNumber(startSplitSegment, layoutSwitchId, jointNumber)
                else startSplitSegment.copy(
                    switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null
                )
            )
            endSplitSegment?.let {
                acc.add(
                    if (isFirstSegment) setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                    else if (isLastSegment) endSplitSegment.withoutSwitch()
                    else setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                )
            }
        } else {
            when (index) {
                //First joint match
                0 -> {
                    acc.add(
                        if (isFirstSegment) startSplitSegment.withoutSwitch()
                        else startSplitSegment.copy(
                            switchId = layoutSwitchId,
                            startJointNumber = null,
                            endJointNumber = null,
                        )
                    )

                    endSplitSegment?.let {
                        acc.add(setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber))
                    }
                }
                //Last joint match
                linkingJoints.lastIndex -> {
                    acc.add(startSplitSegment.copy(endJointNumber = jointNumber))

                    endSplitSegment?.let {
                        acc.add(
                            if (isLastSegment) endSplitSegment.withoutSwitch()
                            else endSplitSegment.copy(
                                switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null
                            )
                        )
                    }
                }

                else -> {
                    acc.add(startSplitSegment.copy(endJointNumber = jointNumber))
                    endSplitSegment?.let {
                        acc.add(setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber))
                    }
                }
            }
        }
    }

    acc
}.toList()

private fun setStartJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = jointNumber, endJointNumber = null)

private fun setEndJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = null, endJointNumber = jointNumber)

private fun combineAdjacentSegmentJointNumbers(
    layoutSegments: List<List<LayoutSegment>>,
    switchId: IntId<TrackLayoutSwitch>,
) = layoutSegments.fold(mutableListOf<LayoutSegment>()) { acc, segments ->
    val currentSegment = segments.first()
    val previousSegment = acc.lastOrNull()

    /**
     * For instance in case of line 1-5-2
     *      J1      J5      J2
     * -----|-------|-------|------
     * S0      S1      S2     S3
     * where the first switch segment S1 has start joint number 1,
     * and the last switch segment S2 has start joint number 5 and end joint number 2
     * we want the S1 to have end joint number 5
     */
    if (currentSegment.switchId == switchId && previousSegment?.switchId == switchId) {
        if (previousSegment.startJointNumber != null && previousSegment.endJointNumber == null && currentSegment.startJointNumber != null) {
            acc[acc.lastIndex] = previousSegment.copy(endJointNumber = currentSegment.startJointNumber)
            acc.addAll(segments)
            return@fold acc
        } else if (previousSegment.endJointNumber != null && currentSegment.startJointNumber == null && currentSegment.endJointNumber != null) {
            acc.add(currentSegment.copy(startJointNumber = previousSegment.endJointNumber))
            acc.addAll(segments.drop(1))
            return@fold acc
        }
    }

    acc.addAll(segments)
    acc
}
