package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.ALIGNMENT_LINKING_SNAP
import fi.fta.geoviite.infra.linking.TrackEnd
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResult
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResultType
import fi.fta.geoviite.infra.linking.slice
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.ContextCache
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.EdgeNode
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackSpatialCache
import fi.fta.geoviite.infra.tracklayout.NearbyTracks
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TRACK_SEARCH_AREA_SIZE
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.calculateLocationTrackTopology
import fi.fta.geoviite.infra.tracklayout.combineEdges
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

private val temporarySwitchId: IntId<LayoutSwitch> = IntId(-1)

private const val TOLERANCE_JOINT_LOCATION_SAME_POINT = 0.001
private const val MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS = 5.0

@GeoviiteService
class SwitchLinkingService
@Autowired
constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
    private val switchDao: LayoutSwitchDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchFittingService: SwitchFittingService,
    private val locationTrackSpatialCache: LocationTrackSpatialCache,
) {

    @Transactional(readOnly = true)
    fun getSuggestedSwitches(
        branch: LayoutBranch,
        requests: List<SwitchPlacingRequest>,
    ): List<PointAssociation<SuggestedSwitch>> =
        getSuggestedSwitchesWithOriginallyLinkedTracks(branch, requests).map { atPoint ->
            atPoint.map { suggestedSwitch -> suggestedSwitch.suggestedSwitch }
        }

    fun getSuggestedSwitchesWithOriginallyLinkedTracks(
        branch: LayoutBranch,
        requests: List<SwitchPlacingRequest>,
    ): List<PointAssociation<SuggestedSwitchWithOriginallyLinkedTracks>> {
        val locationTrackCache = locationTrackSpatialCache.get(branch.draft)
        val fitGrids = getFitGrids(requests, branch, locationTrackCache)

        val originallyLinkedBySwitch =
            collectOriginallyLinkedLocationTracksBySwitch(branch, requests.map { it.layoutSwitchId })
        // fitting can move switches far enough to change how it e.g. makes topological links, so we
        // need to re-lookup nearby alignments
        val alignmentsNearFits = collectLocationTracksNearFitGrids(fitGrids, locationTrackCache)

        return fitGrids
            .mapIndexed { i, r -> i to r }
            .parallelStream()
            .map { (index, fitGrid) ->
                val switchId = requests[index].layoutSwitchId
                val originallyLinked = originallyLinkedBySwitch[switchId] ?: mapOf()
                val alignmentsNearFit = alignmentsNearFits[index] ?: listOf()
                val relevantTracks = originallyLinked + alignmentsNearFit.associateBy { it.first.id as IntId }
                fitGrid.map(parallel = true) { fit ->
                    SuggestedSwitchWithOriginallyLinkedTracks(
                        matchFittedSwitchToTracks(fit, relevantTracks, switchId),
                        originallyLinked.keys,
                    )
                }
            }
            .toList()
    }

    private fun getFitGrids(
        requests: List<SwitchPlacingRequest>,
        branch: LayoutBranch,
        locationTrackCache: ContextCache,
    ): List<PointAssociation<FittedSwitch>> {
        val originalSwitches = switchService.getMany(branch.draft, requests.map { it.layoutSwitchId })
        check(requests.size == originalSwitches.size) {
            val notFound =
                requests
                    .map { it.layoutSwitchId }
                    .filter { id -> originalSwitches.find { original -> original.id == id } == null }
                    .joinToString(", ")
            "expected to find a switch for every ID in replacement request, but none were found for $notFound"
        }
        val switchStructures =
            originalSwitches.map { switch -> switchLibraryService.getSwitchStructure(switch.switchStructureId) }
        val alignmentsNearRequests =
            requests.map { request ->
                locationTrackCache.getWithinBoundingBox(request.points.bounds + TRACK_SEARCH_AREA_SIZE).sortedBy {
                    (it.first.id as IntId).intValue
                }
            }
        return requests
            .mapIndexed { i, r -> i to r }
            .parallelStream()
            .map { (index, request) ->
                findBestSwitchFitForAllPointsInSamplingGrid(
                    request,
                    switchStructures[index],
                    alignmentsNearRequests[index],
                )
            }
            .toList()
    }

    @Transactional(readOnly = true)
    fun getSuggestedSwitch(branch: LayoutBranch, point: Point, switchId: IntId<LayoutSwitch>): SuggestedSwitch? =
        getSuggestedSwitches(branch, listOf(SwitchPlacingRequest(SamplingGridPoints(point), switchId)))
            .first()
            .keys()
            .firstOrNull()

    @Transactional(readOnly = true)
    fun getSuggestedSwitch(
        branch: LayoutBranch,
        geometrySwitchId: IntId<GeometrySwitch>,
    ): GeometrySwitchSuggestionResult =
        when (val fit = switchFittingService.fitGeometrySwitch(branch, geometrySwitchId)) {
            is GeometrySwitchFittingFailure -> GeometrySwitchSuggestionFailure(fit.failure)
            is GeometrySwitchFittingSuccess ->
                GeometrySwitchSuggestionSuccess(findRelevantTracksAndMatchFittedSwitch(branch, fit.switch).first)
        }

    private fun findRelevantTracksAndMatchFittedSwitch(
        branch: LayoutBranch,
        fit: FittedSwitch,
        layoutSwitchId: IntId<LayoutSwitch>? = null,
        originallyLinkedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>? = null,
    ): Pair<SuggestedSwitch, Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>> {
        val tracksAroundFit = findLocationTracksForMatchingSwitchToTracks(branch, fit)
        val relevantTracks = tracksAroundFit + (originallyLinkedTracks ?: mapOf())
        val match = matchFittedSwitchToTracks(fit, relevantTracks, layoutSwitchId)
        return match to relevantTracks.filterKeys { track -> match.trackLinks.containsKey(track) }
    }

    @Transactional
    fun saveSwitchLinking(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<LayoutSwitch>,
    ): LayoutRowVersion<LayoutSwitch> {
        verifySwitchExists(branch, switchId)
        suggestedSwitch.geometrySwitchId?.let(::verifyPlanNotHidden)
        val originalTracks =
            suggestedSwitch.trackLinks.keys.associateWith { id ->
                locationTrackService.getWithGeometryOrThrow(branch.draft, id)
            }
        val changedTracks = withChangesFromLinkingSwitch(suggestedSwitch, switchId, originalTracks)
        saveLocationTrackChanges(branch, changedTracks, originalTracks)
        return updateLayoutSwitch(branch, suggestedSwitch, switchId)
    }

    private fun saveLocationTrackChanges(
        branch: LayoutBranch,
        maybeChanged: List<Pair<LocationTrack, LocationTrackGeometry>>,
        original: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    ) =
        maybeChanged.forEach { (locationTrack, geometry) ->
            val (originalLocationTrack, originalGeometry) = original[locationTrack.id as IntId] ?: (null to null)
            if (originalGeometry != geometry) {
                locationTrackService.saveDraft(branch, locationTrack, geometry)
            } else if (originalLocationTrack != locationTrack) {
                // TODO: GVT-2927 Switch linking in graph model: is this branch needed?
                // TODO: GVT-2927 Switch links are all in geometry now, so can the track itself even
                // change?
                locationTrackService.saveDraft(branch, locationTrack, originalGeometry)
            }
        }

    fun findLocationTracksForMatchingSwitchToTracks(
        branch: LayoutBranch,
        fittedSwitch: FittedSwitch,
        switchId: IntId<LayoutSwitch>? = null,
    ): Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>> {
        fun indexTracksInBounds(boundingBox: BoundingBox?) =
            boundingBox
                ?.let { bounds -> locationTrackDao.fetchVersionsNear(branch.draft, bounds) }
                ?.map(locationTrackService::getWithGeometry)
                ?.associate { trackAndAlignment -> trackAndAlignment.first.id as IntId to trackAndAlignment } ?: mapOf()

        val originalTracks =
            if (switchId == null) emptyMap()
            else {
                switchDao.findLocationTracksLinkedToSwitch(branch.draft, switchId).associate { ids ->
                    val trackAndAlignment = locationTrackService.getWithGeometry(ids.rowVersion)
                    (trackAndAlignment.first.id as IntId) to trackAndAlignment
                }
            }
        return listOfNotNull(
                originalTracks,
                if (switchId != null)
                    indexTracksInBounds(
                        getSwitchBoundsFromTracks(originalTracks.values.map { (_, geom) -> geom }, switchId)
                    )
                else null,
                indexTracksInBounds(getSwitchBoundsFromSwitchFit(fittedSwitch)),
            )
            .reduceRight { a, b -> a + b }
    }

    private fun collectOriginallyLinkedLocationTracksBySwitch(
        branch: LayoutBranch,
        switches: List<IntId<LayoutSwitch>>,
    ): Map<IntId<LayoutSwitch>, Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>> =
        switchDao.findLocationTracksLinkedToSwitches(branch.draft, switches).mapValues { ids ->
            ids.value
                .map { lt -> locationTrackService.getWithGeometry(lt.rowVersion) }
                .associateBy { it.first.id as IntId }
        }

    @Transactional
    fun relinkTrack(branch: LayoutBranch, trackId: IntId<LocationTrack>): List<TrackSwitchRelinkingResult> {
        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, trackId)

        val originalSwitches =
            collectAllSwitchesOnTrackAndNearby(branch, track, geometry).let { nearbySwitchIds ->
                nearbySwitchIds.zip(switchService.getMany(branch.draft, nearbySwitchIds))
            }

        val originallyLinkedLocationTracksBySwitch =
            collectOriginallyLinkedLocationTracksBySwitch(branch, originalSwitches.map { (switchId) -> switchId })

        val changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>> =
            mutableMapOf()

        val relinkingResults =
            originalSwitches.map { (switchId, originalSwitch) ->
                val switchStructure = switchLibraryService.getSwitchStructure(originalSwitch.switchStructureId)
                val presentationJointLocation =
                    originalSwitch.getJoint(switchStructure.presentationJointNumber)?.location
                checkNotNull(presentationJointLocation) { "no presentation joint on switch ${originalSwitch.id}" }
                val nearbyTracksForFit =
                    locationTrackService
                        .getLocationTracksNear(branch.draft, presentationJointLocation)
                        .let { nearby ->
                            val map = nearby.associateBy { it.first.id as IntId }
                            map + changedLocationTracks.filterKeys { id -> map.containsKey(id) }
                        }
                        .values
                        .toList()

                val fittedSwitch =
                    createFittedSwitchByPoint(switchId, presentationJointLocation, switchStructure, nearbyTracksForFit)
                if (fittedSwitch == null) {
                    TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.NOT_AUTOMATICALLY_LINKABLE)
                } else {
                    val nearbyTracksForMatch =
                        findLocationTracksForMatchingSwitchToTracks(branch, fittedSwitch).let { nearby ->
                            val original = originallyLinkedLocationTracksBySwitch[switchId] ?: mapOf()
                            nearby +
                                original +
                                changedLocationTracks.filterKeys { key ->
                                    nearby.containsKey(key) || original.containsKey(key)
                                }
                        }
                    val match = matchFittedSwitchToTracks(fittedSwitch, nearbyTracksForMatch, switchId)
                    withChangesFromLinkingSwitch(
                            match,
                            switchId,
                            nearbyTracksForMatch.filterKeys { track -> match.trackLinks.containsKey(track) },
                        )
                        .forEach { track -> changedLocationTracks[track.first.id as IntId] = track }
                    updateLayoutSwitch(branch, match, switchId)
                    TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.RELINKED)
                }
            }
        changedLocationTracks.values.forEach { (track, geometry) ->
            locationTrackService.saveDraft(branch, track, geometry)
        }
        return relinkingResults
    }

    @Transactional(readOnly = true)
    fun getTrackSwitchSuggestions(context: LayoutContext, trackId: IntId<LocationTrack>) =
        getTrackSwitchSuggestions(context, locationTrackDao.getOrThrow(context, trackId))

    @Transactional(readOnly = true)
    fun getTrackSwitchSuggestions(
        layoutContext: LayoutContext,
        track: LocationTrack,
    ): List<Pair<IntId<LayoutSwitch>, SuggestedSwitch?>> {
        val replacementSwitchLocations =
            track.switchIds.map { switchId ->
                val switch = switchService.getOrThrow(layoutContext, switchId)
                SwitchPlacingRequest(SamplingGridPoints(switch.presentationJointOrThrow.location), switchId)
            }
        val switchSuggestions = getSuggestedSwitches(layoutContext.branch, replacementSwitchLocations)
        return track.switchIds.mapIndexed { index, id -> id to switchSuggestions[index].keys().firstOrNull() }
    }

    fun collectAllSwitchesOnTrackAndNearby(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        geometry: LocationTrackGeometry,
    ): List<IntId<LayoutSwitch>> {
        val trackSwitches = geometry.switchIds
        val nearbySwitches = switchDao.findSwitchesNearTrack(branch, locationTrack.versionOrThrow)
        return (trackSwitches + nearbySwitches).distinct()
    }

    private fun createModifiedLayoutSwitchLinking(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<LayoutSwitch>,
    ): LayoutSwitch =
        createModifiedLayoutSwitchLinking(suggestedSwitch, switchService.getOrThrow(branch.draft, switchId))

    private fun updateLayoutSwitch(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<LayoutSwitch>,
    ): LayoutRowVersion<LayoutSwitch> {
        return createModifiedLayoutSwitchLinking(branch, suggestedSwitch, switchId).let { modifiedLayoutSwitch ->
            switchService.saveDraft(branch, modifiedLayoutSwitch)
        }
    }

    private fun verifyPlanNotHidden(id: IntId<GeometrySwitch>) {
        val header = geometryDao.getSwitchPlanId(id).let(geometryDao::fetchPlanVersion).let(geometryDao::getPlanHeader)
        if (header.isHidden)
            throw LinkingFailureException(
                message = "Cannot link a plan that is hidden",
                localizedMessageKey = "plan-hidden",
            )
    }

    private fun verifySwitchExists(branch: LayoutBranch, switchId: IntId<LayoutSwitch>) {
        if (!switchService.getOrThrow(branch.draft, switchId).exists) {
            throw LinkingFailureException(
                message = "Cannot link to a deleted layout switch",
                localizedMessageKey = "switch-deleted",
            )
        }
    }
}

fun matchFittedSwitchToTracks(
    fittedSwitch: FittedSwitch,
    relevantLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>?,
    name: SwitchName? = null,
): SuggestedSwitch {
    // TODO: GVT-2927 switch linking in topology model: all links are now in nodes, so this should
    // be simpler
    val segmentLinks = mapOf<IntId<LocationTrack>, List<SwitchLinkingJoint>>()
    //        calculateSwitchLinkingJoints(fittedSwitch, relevantLocationTracks,
    // fittedSwitch.switchStructure, switchId)
    val topologyLinks = mapOf<IntId<LocationTrack>, SwitchLinkingTopologicalTrackLink>()
    //        findTopologyLinks(relevantLocationTracks, fittedSwitch, segmentLinks, switchId ?:
    // temporarySwitchId)
    val trackLinks =
        relevantLocationTracks.entries
            .mapNotNull { (id, trackAndGeometry) ->
                val segmentLink = segmentLinks[id] ?: listOf()
                val topologyLink = topologyLinks[id]

                // TODO: GVT-1727 Should be able to just use track.switchIds here, unless something
                // funky about the args
                val hadOriginalLink = switchId?.let(trackAndGeometry.second::containsSwitch) ?: false

                // "relevant" location tracks can contain tracks that are just nearby but not
                // actually affected by linking at
                // all; filter those out
                if (segmentLink.isEmpty() && topologyLink == null && !hadOriginalLink) null
                else {
                    id to SwitchLinkingTrackLinks(segmentLink, topologyLinks[id])
                }
            }
            .associate { it }

    return SuggestedSwitch(
        joints =
            fittedSwitch.joints.map { sj ->
                LayoutSwitchJoint(
                    number = sj.number,
                    role = SwitchJointRole.of(fittedSwitch.switchStructure, sj.number),
                    location = sj.location,
                    locationAccuracy = sj.locationAccuracy,
                )
            },
        trackLinks = trackLinks,
        switchStructureId = fittedSwitch.switchStructure.id,
        name = name ?: SwitchName(fittedSwitch.switchStructure.baseType.name),
    )
}

private fun findTopologyLinks(
    nearbyLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    fittedSwitch: FittedSwitch,
    segmentLinks: Map<IntId<LocationTrack>, List<SwitchLinkingJoint>>,
    switchId: IntId<LayoutSwitch>,
): Map<IntId<LocationTrack>, SwitchLinkingTopologicalTrackLink> {
    return nearbyLocationTracks.entries
        .filter { (id) -> !segmentLinks.containsKey(id) }
        .mapNotNull { (locationTrackId, trackAndAlignment) ->
            val (locationTrack, alignment) = trackAndAlignment
            fun tracksNear(point: IPoint) =
                filterTracksNear(locationTrack, nearbyLocationTracks.values, point.toPoint())

            val nearbyTracks =
                NearbyTracks(
                    alignment.firstSegmentStart?.let(::tracksNear) ?: listOf(),
                    alignment.lastSegmentEnd?.let(::tracksNear) ?: listOf(),
                )

            val locationTrackWithUpdatedTopology =
                calculateLocationTrackTopology(
                    locationTrack,
                    alignment,
                    startChanged = true,
                    endChanged = true,
                    nearbyTracks = nearbyTracks,
                    newSwitch = TopologyLinkFindingSwitch(fittedSwitch.joints, switchId),
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
                } else null)
                ?.let { locationTrackId to it }
        }
        .associate { it }
}

fun getSwitchBoundsFromTracks(tracks: Collection<LocationTrackGeometry>, switchId: IntId<LayoutSwitch>): BoundingBox? =
    tracks
        .flatMap { geometry ->
            geometry.trackSwitchLinks.mapNotNull { link -> link.location.takeIf { link.switchId == switchId } }
        }
        .let(::boundingBoxAroundPointsOrNull)

private fun getSwitchBoundsFromSwitchFit(suggestedSwitch: FittedSwitch): BoundingBox? =
    boundingBoxAroundPointsOrNull(suggestedSwitch.joints.map { joint -> joint.location }, TRACK_SEARCH_AREA_SIZE)

private fun calculateSwitchLinkingJoints(
    fittedSwitch: FittedSwitch,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchStructure: SwitchStructure,
    switchId: IntId<LayoutSwitch>?,
): Map<IntId<LocationTrack>, List<SwitchLinkingJoint>> {
    val switchJointsByLocationTrack =
        fittedSwitch.joints
            .flatMap { joint -> joint.matches.map { match -> match.locationTrackId } }
            .distinct()
            .associateWith { locationTrackId ->
                filterMatchingJointsBySwitchAlignment(switchStructure, fittedSwitch.joints, locationTrackId)
            }
            .filter { it.value.isNotEmpty() }

    return switchJointsByLocationTrack.entries.associate { (locationTrackId, switchJoints) ->
        locationTrackId to
            switchJoints
                .flatMap { suggestedSwitchJoint ->
                    suggestedSwitchJoint.matches.map { match ->
                        val alignment = tracks.getValue(locationTrackId).second
                        val segment = alignment.segments[match.segmentIndex]
                        val snappedMatch =
                            if (segment.switchId != null && segment.switchId != switchId) {
                                tryToSnapOverlappingSwitchSegmentToNearbySegment(
                                    tracks.getValue(locationTrackId).second,
                                    match,
                                )
                            } else match
                        SwitchLinkingJoint(
                            suggestedSwitchJoint.number,
                            snappedMatch.segmentIndex,
                            snappedMatch.m,
                            alignment.segments[snappedMatch.segmentIndex]
                                .seekPointAtSegmentM(
                                    snappedMatch.m - alignment.segmentMValues[snappedMatch.segmentIndex].min
                                )
                                .point
                                .toPoint(),
                        )
                    }
                }
                .sortedBy { it.m }
    }
}

private fun findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
    existingSwitchId: IntId<LayoutSwitch>,
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
            val m = layoutAlignment.segmentMValues[indexedExistingSwitchStartSegment.index]
            val distanceToPreviousSwitchLineStart = match.m - m.min
            val hasAdjacentLayoutSegment = indexedExistingSwitchStartSegment.index > 0

            if (
                hasAdjacentLayoutSegment &&
                    distanceToPreviousSwitchLineStart <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS
            ) {
                return match.copy(
                    m = match.m - distanceToPreviousSwitchLineStart,
                    segmentIndex = indexedExistingSwitchStartSegment.index - 1,
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
            val m = layoutAlignment.segmentMValues[indexedExistingSwitchEndSegment.index]
            val distanceToPreviousSwitchLineEnd = m.max - match.m
            val hasAdjacentLayoutSegment = indexedExistingSwitchEndSegment.index < layoutAlignment.segments.lastIndex

            if (
                hasAdjacentLayoutSegment &&
                    distanceToPreviousSwitchLineEnd <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS
            ) {
                return match.copy(
                    m = match.m + distanceToPreviousSwitchLineEnd,
                    segmentIndex = indexedExistingSwitchEndSegment.index + 1,
                )
            }
        }

    // Couldn't snap, possibly due to too much overlap or adjacent switch segment(s) already
    // contained another switch.
    return match
}

private fun filterTracksNear(
    centerTrack: LocationTrack,
    tracksWithAlignments: Collection<Pair<LocationTrack, LayoutAlignment>>,
    point: Point,
): List<Pair<LocationTrack, LayoutAlignment>> {
    val boundingBox = boundingBoxAroundPoint(point, 1.0)
    return tracksWithAlignments.filter { (track, alignment) ->
        centerTrack.id != track.id &&
            alignment.segments.any { segment ->
                val bb = segment.boundingBox
                bb != null && bb.intersects(boundingBox)
            }
    }
}

fun withChangesFromLinkingSwitch(
    suggestedSwitch: SuggestedSwitch,
    switchId: IntId<LayoutSwitch>,
    originalLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>,
): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
    val existingLinksCleared = withExistingLinksToSwitchCleared(originalLocationTracks, switchId)
    // TODO: GVT-2927 Switch linking in graph model
    return emptyList()
    //    val segmentLinksMade = withSegmentLinks(suggestedSwitch, existingLinksCleared, switchId)
    //    val topologicalLinksMade = withTopologicalLinks(suggestedSwitch, existingLinksCleared,
    // switchId)
    //    val onlyDelinked =
    //        existingLinksCleared.entries
    //            .filter { (id, trackAndAlignment) ->
    //                !segmentLinksMade.containsKey(id) &&
    //                    !topologicalLinksMade.containsKey(id) &&
    //                    trackAndAlignment != originalLocationTracks[id]
    //            }
    //            .map { it.value }
    //
    //    return segmentLinksMade.values + topologicalLinksMade.values + onlyDelinked
}

private fun withTopologicalLinks(
    suggestedSwitch: SuggestedSwitch,
    existingLinksCleared: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<LayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> {
    val topologicalLinksMade =
        suggestedSwitch.trackLinks.entries
            .mapNotNull { (locationTrackId, trackLink) ->
                trackLink.topologyJoint?.let { topologyJoint ->
                    val (locationTrack, alignment) = existingLinksCleared.getValue(locationTrackId)
                    val updatedTrack = updateLocationTrackWithTopologyEndLinking(locationTrack, switchId, topologyJoint)
                    locationTrackId to (updatedTrack to alignment)
                }
            }
            .associate { it }
    return topologicalLinksMade
}

private fun withSegmentLinks(
    suggestedSwitch: SuggestedSwitch,
    existingLinksCleared: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> {
    val segmentLinksMade =
        suggestedSwitch.trackLinks.entries
            .mapNotNull { (locationTrackId, trackLink) ->
                if (trackLink.segmentJoints.isEmpty()) null
                else {
                    val (locationTrack, alignment) = existingLinksCleared.getValue(locationTrackId)
                    locationTrackId to
                        (locationTrack to
                            updateAlignmentSegmentsWithSwitchLinking(alignment, switchId, trackLink.segmentJoints))
                }
            }
            .associate { it }
    return segmentLinksMade
}

private fun withExistingLinksToSwitchCleared(
    originalLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
    originalLocationTracks.mapValues { (_, trackAndGeometry) ->
        val (track, geometry) = trackAndGeometry
        track to geometry.withoutSwitch(switchId)
    }

fun updateLocationTrackWithTopologyEndLinking(
    locationTrack: LocationTrack,
    switchId: IntId<LayoutSwitch>,
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
    geometry: LocationTrackGeometry,
    layoutSwitchId: IntId<LayoutSwitch>,
    matchingJoints: List<SwitchLinkingJoint>,
): LocationTrackGeometry {
    val segmentIndexRange = matchingJoints.map { it.segmentIndex }.let { ixes -> ixes.min()..ixes.max() }

    val overriddenSwitches =
        geometry.segments
            .mapIndexedNotNull { index, segment -> if (index in segmentIndexRange) segment.switchId else null }
            .distinct()

    val cleanedSegments =
        // Collections#contains mapped over a long alignment is surprisingly expensive, but usually
        // we're overriding nothing anyway
        if (overriddenSwitches.isEmpty()) {
            geometry.segments
        } else
            geometry.segments.map { segment ->
                if (overriddenSwitches.contains(segment.switchId)) segment.withoutSwitch() else segment
            }

    val segmentsWithNewSwitch =
        cleanedSegments
            .subList(segmentIndexRange.first, segmentIndexRange.last + 1)
            .mapIndexed { indexInRange, segment ->
                val index = indexInRange + segmentIndexRange.first
                val switchLinkingJoints = matchingJoints.filter { joint -> joint.segmentIndex == index }

                if (switchLinkingJoints.isEmpty()) {
                    // Segment that is between two other segments that are linked to the switch
                    // joints
                    listOf(segment.copy(switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null))
                } else {
                    getSegmentsByLinkingJoints(
                        switchLinkingJoints,
                        segment,
                        geometry.segmentMValues[index],
                        layoutSwitchId,
                        index == segmentIndexRange.first,
                        index == segmentIndexRange.last,
                    )
                }
            }
            .let { segments -> combineAdjacentSegmentJointNumbers(segments, layoutSwitchId) }

    // TODO: GVT-2927 Switch linking in graph model
    TODO()
    //    return geometry.withSegments(
    //        listOf(
    //                cleanedSegments.subList(0, segmentIndexRange.first),
    //                segmentsWithNewSwitch,
    //                cleanedSegments.subList(segmentIndexRange.last + 1, cleanedSegments.size),
    //            )
    //            .flatten()
    //    )
}

private fun filterMatchingJointsBySwitchAlignment(
    switchStructure: SwitchStructure,
    matchingJoints: List<FittedSwitchJoint>,
    locationTrackId: DomainId<LocationTrack>,
): List<FittedSwitchJoint> {
    val locationTrackSwitchJoints =
        matchingJoints
            .map { joint ->
                joint.copy(matches = joint.matches.filter { segment -> segment.locationTrackId == locationTrackId })
            }
            .filter { it.matches.isNotEmpty() }

    val switchStructureJointNumbers =
        switchStructure.alignments
            .firstOrNull { alignment ->
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

                // Alignment must contain at least two of these ("etujatkos", "takajatkos",
                // presentation joint)
                listOf(hasFrontJoint, hasBackJoint, hasSeparatePresentationJoint).count { it } >= 2
            }
            ?.jointNumbers

    return locationTrackSwitchJoints.filter { joint ->
        switchStructureJointNumbers?.any { structureJoint -> structureJoint == joint.number } ?: false
    }
}

private fun getSegmentsByLinkingJoints(
    linkingJoints: List<SwitchLinkingJoint>,
    segment: LayoutSegment,
    segmentM: Range<Double>,
    layoutSwitchId: IntId<LayoutSwitch>,
    isFirstSegment: Boolean,
    isLastSegment: Boolean,
) =
    linkingJoints
        .foldIndexed(mutableListOf<LayoutSegment>()) { index, acc, linkingJoint ->
            val jointNumber = linkingJoint.number
            val previousSegment = acc.lastOrNull()?.also { acc.removeLast() } ?: segment
            val suggestedPointM = linkingJoint.m

            if (isSame(segmentM.min, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
                // Check if suggested point is start point
                acc.add(setStartJointNumber(segment, layoutSwitchId, jointNumber))
            } else if (isSame(segmentM.max, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
                // Check if suggested point is end point
                if (linkingJoints.size == 1) {
                    acc.add(setEndJointNumber(previousSegment, layoutSwitchId, jointNumber))
                } else {
                    acc.add(previousSegment.copy(endJointNumber = jointNumber))
                }
            } else {
                // Otherwise split the segment
                // StartSplitSegment: before M-value
                // EndSplitSegment: after M-value
                val (startSplitSegment, endSplitSegment) =
                    previousSegment.splitAtM(suggestedPointM - segmentM.min, TOLERANCE_JOINT_LOCATION_NEW_POINT)

                // Handle cases differently when there are multiple joint matches in a single
                // segment
                if (linkingJoints.size == 1) {
                    acc.add(
                        if (isFirstSegment) startSplitSegment.withoutSwitch()
                        else if (isLastSegment) setEndJointNumber(startSplitSegment, layoutSwitchId, jointNumber)
                        else
                            startSplitSegment.copy(
                                switchId = layoutSwitchId,
                                startJointNumber = null,
                                endJointNumber = null,
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
                        // First joint match
                        0 -> {
                            acc.add(
                                if (isFirstSegment) startSplitSegment.withoutSwitch()
                                else
                                    startSplitSegment.copy(
                                        switchId = layoutSwitchId,
                                        startJointNumber = null,
                                        endJointNumber = null,
                                    )
                            )

                            endSplitSegment?.let {
                                acc.add(setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber))
                            }
                        }
                        // Last joint match
                        linkingJoints.lastIndex -> {
                            acc.add(startSplitSegment.copy(endJointNumber = jointNumber))

                            endSplitSegment?.let {
                                acc.add(
                                    if (isLastSegment) endSplitSegment.withoutSwitch()
                                    else
                                        endSplitSegment.copy(
                                            switchId = layoutSwitchId,
                                            startJointNumber = null,
                                            endJointNumber = null,
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
        }
        .toList()

private fun setStartJointNumber(segment: LayoutSegment, switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = jointNumber, endJointNumber = null)

private fun setEndJointNumber(segment: LayoutSegment, switchId: IntId<LayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = null, endJointNumber = jointNumber)

private fun combineAdjacentSegmentJointNumbers(
    layoutSegments: List<List<LayoutSegment>>,
    switchId: IntId<LayoutSwitch>,
) =
    layoutSegments.fold(mutableListOf<LayoutSegment>()) { acc, segments ->
        val currentSegment = segments.first()
        val previousSegment = acc.lastOrNull()

        /**
         * For instance in case of line 1-5-2 J1 J5 J2
         *
         * -----|-------|-------|------
         * S0 S1 S2 S3 where the first switch segment S1 has start joint number 1, and the last switch segment S2 has
         * start joint number 5 and end joint number 2 we want the S1 to have end joint number 5
         */
        if (currentSegment.switchId == switchId && previousSegment?.switchId == switchId) {
            if (
                previousSegment.startJointNumber != null &&
                    previousSegment.endJointNumber == null &&
                    currentSegment.startJointNumber != null
            ) {
                acc[acc.lastIndex] = previousSegment.copy(endJointNumber = currentSegment.startJointNumber)
                acc.addAll(segments)
                return@fold acc
            } else if (
                previousSegment.endJointNumber != null &&
                    currentSegment.startJointNumber == null &&
                    currentSegment.endJointNumber != null
            ) {
                acc.add(currentSegment.copy(startJointNumber = previousSegment.endJointNumber))
                acc.addAll(segments.drop(1))
                return@fold acc
            }
        }

        acc.addAll(segments)
        acc
    }

data class SamplingGridPoints(val points: List<Point>) {
    constructor(point: Point) : this(listOf(point))

    val bounds: BoundingBox by lazy { boundingBoxAroundPoints(points) }

    fun <R> map(parallel: Boolean = false, f: (point: Point) -> R) =
        PointAssociation(
            points
                .let { if (parallel) it.parallelStream() else it.stream() }
                .map { point -> f(point) to point }
                .toList()
        )

    fun <R> mapMulti(parallel: Boolean = false, f: (point: Point) -> Set<R>): PointAssociation<R> {
        val stream = if (parallel) points.parallelStream() else points.stream()
        val map = stream.map { point -> point to f(point) }.collect(Collectors.toMap({ it.first }, { it.second }))
        return PointAssociation(invertMapOfSets(map))
    }

    fun get(index: Int) = points[index]
}

/** Associate a set of items with a set of points for each item. May be empty. */
data class PointAssociation<T>(val items: Map<T, Set<Point>>) {

    constructor(pairs: List<Pair<T, Point>>) : this(pairs.associate { (i, ps) -> i to setOf(ps) })

    fun keys(): Set<T> = items.keys

    fun <R> map(parallel: Boolean = false, transform: (item: T) -> R): PointAssociation<R> =
        PointAssociation(
            itemStream(parallel)
                .flatMap { (i, ps) -> transform(i).let { result -> ps.stream().map { point -> result to point } } }
                .collect(Collectors.groupingBy({ it.first }, Collectors.mapping({ it.second }, Collectors.toSet())))
        )

    fun <R> mapMulti(
        parallel: Boolean = false,
        transform: (item: T, points: Set<Point>) -> Set<R>,
    ): PointAssociation<R> =
        PointAssociation(
            itemStream(parallel)
                .flatMap { (i, ps) ->
                    transform(i, ps).stream().flatMap { result -> ps.stream().map { point -> result to point } }
                }
                .collect(Collectors.groupingBy({ it.first }, Collectors.mapping({ it.second }, Collectors.toSet())))
        )

    /**
     * Collect items by point, and run the aggregation callback for each point's collection. The callback does not
     * receive any calls with empty collections.
     */
    fun <R> aggregateByPoint(
        parallel: Boolean = false,
        aggregate: (point: Point, item: Set<T>) -> R,
    ): PointAssociation<R> {
        val byPoint = invertItems().entries
        val stream = if (parallel) byPoint.parallelStream() else byPoint.stream()
        return PointAssociation(
            stream
                .map { (point, es) -> aggregate(point, es) to point }
                .collect(Collectors.groupingBy({ it.first }, Collectors.mapping({ it.second }, Collectors.toSet())))
        )
    }

    fun <O, R> zipWithByPoint(
        other: PointAssociation<O>,
        combine: (my: Set<T>, theirs: Set<O>) -> Set<R>,
    ): PointAssociation<R> {
        val meByPoint = invertItems()
        val themByPoint = other.invertItems()

        return PointAssociation(
            invertMapOfSets(
                (meByPoint.keys + themByPoint.keys).associateWith { point ->
                    val myItems = meByPoint.getOrDefault(point, setOf())
                    val theirItems = themByPoint.getOrDefault(point, setOf())
                    combine(myItems, theirItems)
                }
            )
        )
    }

    private fun itemStream(parallel: Boolean) = if (parallel) items.entries.parallelStream() else items.entries.stream()

    private fun invertItems(): Map<Point, Set<T>> = invertMapOfSets(items)
}

fun <K, V> invertMapOfSets(map: Map<K, Set<V>>): Map<V, Set<K>> =
    map.entries
        .stream()
        .flatMap { (i, ps) -> ps.stream().map { point -> point to i } }
        .collect(Collectors.groupingBy({ it.first }, Collectors.mapping({ it.second }, Collectors.toSet())))

data class SuggestedSwitchesAtGridPoints(
    val suggestedSwitches: List<SuggestedSwitch>,
    val gridSwitchIndices: List<Int?>,
)

fun matchSamplingGridToQueryPoints(
    grid: PointAssociation<SuggestedSwitch>,
    queryPoints: List<Point>,
): SuggestedSwitchesAtGridPoints {
    val switchesByPoint = invertMapOfSets(grid.items)
    assert(switchesByPoint.values.all { it.size == 1 }) {
        "suggested switches grid assigns unique suggestion per point"
    }
    val switchByPoint = switchesByPoint.mapValues { (_, v) -> v.firstOrNull() }
    val switches = grid.keys().toList()
    return SuggestedSwitchesAtGridPoints(
        switches,
        queryPoints.map { point ->
            switchByPoint[point]?.let { switch ->
                switches.indexOf(switch).let { index -> if (index == -1) null else index }
            }
        },
    )
}

class SuggestedSwitchWithOriginallyLinkedTracks(
    val suggestedSwitch: SuggestedSwitch,
    val originallyLinkedTracks: Set<IntId<LocationTrack>>,
) {

    // need to use these as map keys, but avoid very expensive hashCode()s on all those alignments
    // with their geometries
    override fun hashCode(): Int = suggestedSwitch.hashCode()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SuggestedSwitchWithOriginallyLinkedTracks

        return suggestedSwitch == other.suggestedSwitch
    }
}

private fun collectLocationTracksNearFitGrids(
    fitGrids: List<PointAssociation<FittedSwitch>>,
    locationTrackCache: ContextCache,
): List<List<Pair<LocationTrack, LocationTrackGeometry>>?> =
    fitGrids.map { fitGrid ->
        val switchBboxes = fitGrid.keys().mapNotNull { fit -> getSwitchBoundsFromSwitchFit(fit) }
        boundingBoxCombining(switchBboxes)?.let { boundingBox -> locationTrackCache.getWithinBoundingBox(boundingBox) }
    }

fun createModifiedLayoutSwitchLinking(suggestedSwitch: SuggestedSwitch, layoutSwitch: LayoutSwitch): LayoutSwitch {
    val newGeometrySwitchId = suggestedSwitch.geometrySwitchId ?: layoutSwitch.sourceId

    return layoutSwitch.copy(
        sourceId = newGeometrySwitchId,
        joints = suggestedSwitch.joints,
        source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
    )
}

/**
 * When the main/presentation joint is in the middle of the switch alignment, tracks can end at the middle and therefore
 * the beginning and the end part of the switch alignment are also valid switch alignments.
 *
 * E.g. in RR type switch the joint 5 is the main/presentation joint and one of the switch alignments is 4-5-3,
 * therefore alignments 4-5 and 5-3 are also valid alignments.
 */
fun getPartialSwitchAlignmentJointSequences(switchStructure: SwitchStructure): List<List<JointNumber>> {
    val partialJointSequences =
        switchStructure.alignments.flatMap { alignment ->
            val originalSequence = alignment.jointNumbers
            val presentationJoinIndex = alignment.jointNumbers.indexOf(switchStructure.presentationJointNumber)
            if (presentationJoinIndex > 0 && presentationJoinIndex < alignment.jointNumbers.lastIndex) {
                // split alignment into two
                val headJoints = originalSequence.subList(0, presentationJoinIndex + 1)
                val tailJoints = originalSequence.subList(presentationJoinIndex, alignment.jointNumbers.lastIndex + 1)
                listOf(headJoints, tailJoints)
            } else emptyList()
        }
    return partialJointSequences
}

fun getSwitchAlignmentJointSequences(switchStructure: SwitchStructure): List<List<JointNumber>> {
    return switchStructure.alignments.map { alignment -> alignment.jointNumbers }
}

fun getAllSwitchAlignmentJointSequences(switchStructure: SwitchStructure): List<List<JointNumber>> {
    return getSwitchAlignmentJointSequences(switchStructure) + getPartialSwitchAlignmentJointSequences(switchStructure)
}

fun validateJointSequence(switchStructure: SwitchStructure, jointSequence: List<JointNumber>): List<JointNumber>? {
    val validJointSequences = getAllSwitchAlignmentJointSequences(switchStructure)
    val matchingSequence =
        validJointSequences.find { validJointSequence -> validJointSequence.containsAll(jointSequence) }
    return matchingSequence
}

fun mapFittedSwitchToEdges(
    fittedSwitch: FittedSwitch,
    nearbyTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<JointOnEdge> {
    val jointsOnEdge =
        fittedSwitch.joints.flatMap { joint ->
            joint.matches.map { match ->
                val (locationTrack, geometry) =
                    nearbyTracks.first { (locationTrack, _) -> locationTrack.id == match.locationTrackId }
                val edge = geometry.getEdgeAtMOrThrow(match.m)
                JointOnEdge(
                    locationTrackId = locationTrack.id as IntId,
                    geometry = geometry,
                    jointNumber = match.switchJoint.number,
                    jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, joint.number),
                    edge = edge,
                    m = match.m,
                    direction = RelativeDirection.Along, // TODO: Suunta pitäisi saada fitted switchiltä
                )
            }
        }
    return jointsOnEdge
}

fun filterValidJointsOnEdge(switchStructure: SwitchStructure, jointsOnEdge: List<JointOnEdge>): List<JointOnEdge> {
    val jointsByLocationTrack = jointsOnEdge.groupBy { jointOnEdge -> jointOnEdge.locationTrackId }
    val validJointsOnEdge =
        jointsByLocationTrack.flatMap { (_, joints) ->
            val jointSequence = joints.map { joint -> joint.jointNumber }
            val validJoints = validateJointSequence(switchStructure, jointSequence)
            if (validJoints != null) {
                val validJointsOnEdge = joints.filter { jointOnEdge -> validJoints.contains(jointOnEdge.jointNumber) }
                validJointsOnEdge
            } else listOf()
        }
    return validJointsOnEdge
}

fun mergeEdges(edgesToMerge: List<LayoutEdge>): LayoutEdge {
    val newSegments = edgesToMerge.flatMap { edgeToMerge -> edgeToMerge.segments }
    val newEdge = TmpLayoutEdge(edgesToMerge.first().startNode, edgesToMerge.last().endNode, newSegments)
    return newEdge
}

fun replaceEdges(
    geometry: LocationTrackGeometry,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): LocationTrackGeometry {
    return TmpLocationTrackGeometry(replaceEdges(originalEdges = geometry.edges, edgesToReplace, newEdges))
}

fun replaceEdges(
    originalEdges: List<LayoutEdge>,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): List<LayoutEdge> {
    val replaceStartIndex =
        originalEdges.indexOfFirst { originalEdge ->
            originalEdge.startNode.node == edgesToReplace.first().startNode.node
        }
    val replaceEndIndex =
        originalEdges.indexOfLast { originalEdge -> originalEdge.endNode.node == edgesToReplace.last().endNode.node }
    require(replaceStartIndex != -1 && replaceEndIndex != -1) { "Cannot replace non existing edges" }
    val newAllEdges =
        originalEdges.subList(0, replaceStartIndex) +
            newEdges +
            originalEdges.subList(replaceEndIndex + 1, originalEdges.lastIndex + 1)
    return combineEdges(newAllEdges)
}

/**
 * Tämä yhdistää sellaiset edget, jotka ovat samalla raiteella. Tosin mikä olisi tällainen tilanne, missä olisi node
 * raiteilla, mille ei olisi käyttöä???
 */
fun mergeJointsOnEdgesIntoSingleEdge(jointsOnEdge: List<JointOnEdge>): List<JointOnEdge> {
    val jointsByGeometry = jointsOnEdge.groupBy { jointOnEdge -> jointOnEdge.geometry }
    val jointsOnSingleEdge =
        jointsByGeometry.flatMap { (geometry, joints) ->
            val edgesToMerge = joints.map { joint -> joint.edge }.distinct()
            val newEdge = mergeEdges(edgesToMerge)
            val newGeometry = replaceEdges(geometry, edgesToMerge, listOf(newEdge))
            val newJoints = joints.map { joint -> joint.copy(geometry = newGeometry, edge = newEdge) }
            newJoints
        }
    return jointsOnSingleEdge
}

fun linkJointsToEdge(
    switchId: IntId<LayoutSwitch>,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
): List<LayoutEdge> {
    val sortedJointsOnEdge = jointsOnEdge.sortedBy { jointOnEdge -> jointOnEdge.m }
    val linkedEdges =
        sortedJointsOnEdge.fold(listOf<LayoutEdge>()) { linkedEdges, jointOnEdge ->
            val edgeToLink = if (linkedEdges.isEmpty()) edge else linkedEdges.last()
            val mValueAtStartOfEdgeToLink = linkedEdges.minus(edgeToLink).sumOf { edge -> edge.end.m }
            val mValueRelativeToEdgeToLink = jointOnEdge.m - mValueAtStartOfEdgeToLink
            val newEdges =
                linkJointToEdge(
                    switchId,
                    edgeToLink,
                    jointOnEdge.jointNumber,
                    jointOnEdge.jointRole,
                    mValueRelativeToEdgeToLink,
                )
            val newLinkedEdges =
                if (linkedEdges.isEmpty()) newEdges
                else replaceEdges(originalEdges = linkedEdges, edgesToReplace = listOf(edgeToLink), newEdges)
            newLinkedEdges
        }
    return linkedEdges
}

// TODO: Mieti toleranssit
const val SWITCH_JOINT_NODE_SNAPPING_TOLERANCE = ALIGNMENT_LINKING_SNAP

// TODO: Tämän pitää varmaan toimia yhteen topologialinkityksen kanssa
const val SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE = 2.0

fun linkJointToEdge(
    switchId: IntId<LayoutSwitch>,
    edge: LayoutEdge,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
    mValue: Double,
): List<LayoutEdge> {
    val switchLink = SwitchLink(switchId, jointRole, jointNumber)
    val switchEdgeNode = EdgeNode.switch(inner = switchLink, outer = null)

    return if (isSame(edge.start.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewStartNode = edge.withStartNode(switchEdgeNode)
        listOf(withNewStartNode)
    } else if (isSame(edge.end.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewEndNode = edge.withEndNode(switchEdgeNode)
        listOf(withNewEndNode)
    } else {
        val firstEdge = slice(edge, Range(0.0, mValue)).withEndNode(switchEdgeNode)
        val secondEdge = slice(edge, Range(mValue, edge.end.m)).withStartNode(switchEdgeNode)
        listOf(firstEdge, secondEdge)
    }
}

fun filterValidJointSequences(
    jointSequences: List<List<JointNumber>>,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge> {
    val jointsByLocationTrack = jointsOnEdge.groupBy { jointOnEdge -> jointOnEdge.locationTrackId }.toList()
    val validJointsOnEdge =
        jointSequences
            .flatMap { structureJointSequence ->
                val validJoints =
                    jointsByLocationTrack.flatMap { (_, jointsOnLocationTrack) ->
                        val jointNumbersOnLocationTrack =
                            jointsOnLocationTrack.map { jointOnLocationTrack -> jointOnLocationTrack.jointNumber }
                        val hasSameJoints =
                            jointNumbersOnLocationTrack.containsAll(structureJointSequence) &&
                                structureJointSequence.containsAll(jointNumbersOnLocationTrack)
                        val containsExtra =
                            jointNumbersOnLocationTrack.containsAll(structureJointSequence) &&
                                !structureJointSequence.containsAll(jointNumbersOnLocationTrack)
                        require(!containsExtra) {
                            "There is an additional joint match on location track: $jointNumbersOnLocationTrack expected: $jointSequences"
                        }
                        if (hasSameJoints) jointsOnLocationTrack else listOf()
                    }
                validJoints
            }
            .distinct()
    return validJointsOnEdge
}

fun findMissingJoints(jointSequence: List<JointNumber>, jointsOnLocationTrack: List<JointOnEdge>): List<JointNumber> {
    val jointNumbersOnLocationTrack =
        jointsOnLocationTrack.map { jointOnLocationTrack -> jointOnLocationTrack.jointNumber }.distinct()
    val missingJoints = jointSequence.minus(jointNumbersOnLocationTrack)
    val missingSomeJoint = missingJoints.size > 0
    val missingAllJoints = missingJoints.size == jointSequence.size
    return if (missingSomeJoint && !missingAllJoints) missingJoints else emptyList()
}

/**
 * Tries to create missing joints.
 *
 * @return completed joint sequence if it is possible, otherwise empty list
 */
fun completeJointSequence(
    fittedSwitch: FittedSwitch,
    jointSequence: List<JointNumber>,
    locationTrackId: IntId<LocationTrack>,
    jointsOnLocationTrack: List<JointOnEdge>,
): List<JointOnEdge> {
    val middleJointNumbers = jointSequence.drop(1).dropLast(1)
    val missingJointNumbers = findMissingJoints(jointSequence, jointsOnLocationTrack)
    val middleJointIsMissing =
        missingJointNumbers.any { missingJointNumber -> middleJointNumbers.contains(missingJointNumber) }
    val middleJointOnEdge =
        jointsOnLocationTrack.firstOrNull { jointOnEdge -> middleJointNumbers.contains(jointOnEdge.jointNumber) }

    val completedJointsOnLocationTrack =
        if (middleJointIsMissing || middleJointOnEdge == null) {
            // Middle joint is missing and it cannot be created automatically
            listOf<JointOnEdge>()
        } else {
            // Try to create missing joints
            val newJoints =
                missingJointNumbers
                    .map { missingJointNumber ->
                        val useReverseOrder = middleJointOnEdge.direction == RelativeDirection.Against
                        val sortedJointSequence = if (useReverseOrder) jointSequence.reversed() else jointSequence
                        val missingJointIsBeforeMiddleJoint =
                            sortedJointSequence.indexOf(missingJointNumber) <
                                sortedJointSequence.indexOf(middleJointOnEdge.jointNumber)
                        val newJointLocationM =
                            if (missingJointIsBeforeMiddleJoint) middleJointOnEdge.edge.start.m
                            else middleJointOnEdge.edge.end.m
                        JointOnEdge(
                            locationTrackId = locationTrackId,
                            geometry = middleJointOnEdge.geometry,
                            jointNumber = missingJointNumber,
                            jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, missingJointNumber),
                            edge = middleJointOnEdge.edge,
                            m = newJointLocationM,
                            direction = middleJointOnEdge.direction,
                        )
                    }
                    .filter { jointCandidate ->
                        val candidateLocation = jointCandidate.geometry.getPointAtM(jointCandidate.m)
                        val expectedLocation =
                            fittedSwitch.joints
                                .firstOrNull { joint -> joint.number == jointCandidate.jointNumber }
                                ?.location
                        val isValidLocation =
                            expectedLocation != null &&
                                candidateLocation != null &&
                                lineLength(expectedLocation, candidateLocation) < SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE
                        isValidLocation
                    }

            val allJoints = jointsOnLocationTrack + newJoints
            val allJointNumbers = allJoints.map { jointOnEdge -> jointOnEdge.jointNumber }
            val hasAllRequiredJoints = allJointNumbers.containsAll(jointSequence)
            if (hasAllRequiredJoints) allJoints else listOf()
        }
    return completedJointsOnLocationTrack
}

/** Tries to create missing joints. */
fun completeJointSequences(
    fittedSwitch: FittedSwitch,
    jointSequences: List<List<JointNumber>>,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge> {
    val jointsByLocationTrack = jointsOnEdge.groupBy { jointOnEdge -> jointOnEdge.locationTrackId }.toList()
    val completedJointSequences =
        jointSequences.flatMap { jointSequence ->
            jointsByLocationTrack.flatMap { (locationTrackId, jointsOnLocationTrack) ->
                completeJointSequence(fittedSwitch, jointSequence, locationTrackId, jointsOnLocationTrack)
            }
        }
    return completedJointSequences
}

/** Filters out all joints of all handled location tracks */
fun filterOutHandledJoints(
    allJointsOnEdge: List<JointOnEdge>,
    handledJointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge> {
    val handledLocationTracks = handledJointsOnEdge.map { jointOnEdge -> jointOnEdge.locationTrackId }
    return allJointsOnEdge.filter { jointOnEdge ->
        val notHandledTrack = !handledLocationTracks.contains(jointOnEdge.locationTrackId)
        notHandledTrack
    }
}

fun adjustJointPositions(
    fittedSwitch: FittedSwitch,
    structureJointSequences: List<List<JointNumber>>,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge> {
    val validJointsOnEdge = filterValidJointSequences(structureJointSequences, jointsOnEdge)
    val unhandledJointsOnEdges = filterOutHandledJoints(jointsOnEdge, validJointsOnEdge)
    val completedJointsOnEdges = completeJointSequences(fittedSwitch, structureJointSequences, unhandledJointsOnEdges)
    return validJointsOnEdge + completedJointsOnEdges
}

fun adjustJointPositions(fittedSwitch: FittedSwitch, jointsOnEdge: List<JointOnEdge>): List<JointOnEdge> {
    // First try to adjust joints by full switch alignments
    val fullJointSequences = getSwitchAlignmentJointSequences(fittedSwitch.switchStructure)
    val adjustedJointsForFullJointSequences = adjustJointPositions(fittedSwitch, fullJointSequences, jointsOnEdge)
    val unhandledJointsOnEdge = filterOutHandledJoints(jointsOnEdge, adjustedJointsForFullJointSequences)

    // Then try to adjust unhandled joint by partial switch alignments
    val partialJointSequences = getPartialSwitchAlignmentJointSequences(fittedSwitch.switchStructure)
    val adjustedJointsForPartialJointSequences =
        adjustJointPositions(fittedSwitch, partialJointSequences, unhandledJointsOnEdge)
    return adjustedJointsForFullJointSequences + adjustedJointsForPartialJointSequences
}

fun clearSwitchFromTracks(
    switchId: IntId<LayoutSwitch>,
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    return tracks.map { (locationTrack, geometry) ->
        val edgesWithoutSwitch = geometry.edges.map { edge -> edge.withoutSwitch(switchId) }
        val newGeometry = TmpLocationTrackGeometry(edgesWithoutSwitch)
        locationTrack to newGeometry
    }
}

fun linkFittedSwitch(
    switchId: IntId<LayoutSwitch>,
    fittedSwitch: FittedSwitch,
    nearbyTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    // tällä hetkellä kytkeytyvät raiteet
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    val tracksWithoutSwitch = clearSwitchFromTracks(switchId, nearbyTracks)
    val locationTracks = tracksWithoutSwitch.map { (locationTrack, _) -> locationTrack }
    val locationTracksById = locationTracks.associateBy { locationTrack -> locationTrack.id }
    val jointsOnEdges = mapFittedSwitchToEdges(fittedSwitch, tracksWithoutSwitch)
    val adjustedJointsOnEdges = adjustJointPositions(fittedSwitch, jointsOnEdges)
    val validatedJoints = filterValidJointsOnEdge(fittedSwitch.switchStructure, adjustedJointsOnEdges)
    val jointsOnSingleEdge = mergeJointsOnEdgesIntoSingleEdge(validatedJoints) // Onko tarpeen
    val jointsByEdge = jointsOnSingleEdge.groupBy { joint -> joint.edge }
    val linkedGeometryByLocationTrack =
        jointsByEdge.map { (edge, joints) ->
            val locationTrack = requireNotNull(locationTracksById[joints.first().locationTrackId])
            val geometry = joints.first().geometry
            val linkedEdges = linkJointsToEdge(switchId, edge, joints)
            val newGeometry = replaceEdges(geometry, listOf(edge), linkedEdges)
            locationTrack to newGeometry
        }
    // topologialinkitys tähän
    return linkedGeometryByLocationTrack
}
