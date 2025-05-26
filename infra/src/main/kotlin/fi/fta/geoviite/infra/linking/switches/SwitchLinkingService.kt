package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.ALIGNMENT_LINKING_SNAP
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResult
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResultType
import fi.fta.geoviite.infra.linking.slice
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.boundingBoxAroundPointsOrNull
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.ContextCache
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackSpatialCache
import fi.fta.geoviite.infra.tracklayout.NodeConnection
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TRACK_SEARCH_AREA_SIZE
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
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
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val geometryDao: GeometryDao,
    private val switchService: LayoutSwitchService,
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

    fun linkFittedSwitch(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
        fittedSwitch: FittedSwitch,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        val fittedSwitchLocationTrackIds =
            fittedSwitch.joints.flatMap { joint -> joint.matches.map { match -> match.locationTrackId } }.distinct()
        val fittedSwitchTracks =
            fittedSwitchLocationTrackIds.map { locationTrackId ->
                requireNotNull(locationTrackService.getWithGeometry(layoutContext, locationTrackId)) {
                    "Location track $locationTrackId for fitted switch not found"
                }
            }
        val switchContainingTracks = switchService.getLocationTracksLinkedToSwitch(layoutContext, switchId)
        val linkedTracks =
            linkFittedSwitch(switchId, fittedSwitch, fittedSwitchTracks, switchContainingTracks).let { modifiedTracks ->
                locationTrackService.recalculateTopology(layoutContext, modifiedTracks, switchId)
            }

        return linkedTracks
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

fun getSwitchBoundsFromTracks(tracks: Collection<LocationTrackGeometry>, switchId: IntId<LayoutSwitch>): BoundingBox? =
    tracks
        .flatMap { geometry ->
            geometry.trackSwitchLinks.mapNotNull { link -> link.location.takeIf { link.switchId == switchId } }
        }
        .let(::boundingBoxAroundPointsOrNull)

private fun getSwitchBoundsFromSwitchFit(suggestedSwitch: FittedSwitch): BoundingBox? =
    boundingBoxAroundPointsOrNull(suggestedSwitch.joints.map { joint -> joint.location }, TRACK_SEARCH_AREA_SIZE)

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

private fun withExistingLinksToSwitchCleared(
    originalLocationTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>,
): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
    originalLocationTracks.mapValues { (_, trackAndGeometry) ->
        val (track, geometry) = trackAndGeometry
        track to geometry.withoutSwitch(switchId)
    }

fun updateAlignmentSegmentsWithSwitchLinking(
    geometry: LocationTrackGeometry,
    layoutSwitchId: IntId<LayoutSwitch>,
    matchingJoints: List<SwitchLinkingJoint>,
): LocationTrackGeometry {
    TODO()
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

fun validateJointSequence(
    switchStructure: SwitchStructure,
    jointsOnLocationTrack: List<JointOnEdge>,
    geometry: LocationTrackGeometry,
): List<JointOnEdge> {
    val validFullJointSequences = getSwitchAlignmentJointSequences(switchStructure)
    val jointNumbersOnLocationTrack =
        jointsOnLocationTrack.sortedBy { jointOnEdge -> jointOnEdge.m }.map { jointOnEdge -> jointOnEdge.jointNumber }
    val isValidFullAlignment =
        validFullJointSequences.any { validJointSequence ->
            validJointSequence == jointNumbersOnLocationTrack ||
                validJointSequence.reversed() == jointNumbersOnLocationTrack
        }
    if (isValidFullAlignment) {
        return jointsOnLocationTrack
    }

    val validPartialJointSequences = getPartialSwitchAlignmentJointSequences(switchStructure)
    val isValidPartialAlignment =
        validPartialJointSequences.any { validPartialJointSequence ->
            val hasAllJoints = validPartialJointSequence.containsAll(jointNumbersOnLocationTrack)
            val innerJointOnEdge =
                jointsOnLocationTrack.find { jointOnEdge -> switchStructure.isInnerJoint(jointOnEdge.jointNumber) }
            val trackEndsToInnerJoint =
                // TODO: toleranssit mietittävä, vaikka fittauksen snappays varmaankin asettaa
                // jointin raiteen päähän
                innerJointOnEdge?.m?.let { m -> m == geometry.start?.m || m == geometry.end?.m } ?: false
            hasAllJoints && trackEndsToInnerJoint
        }
    if (isValidPartialAlignment) {
        return jointsOnLocationTrack
    }
    return emptyList()
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
                val (edge, mRange) = geometry.getEdgeAtMOrThrow(match.m)
                JointOnEdge(
                    locationTrackId = locationTrack.id as IntId,
                    geometry = geometry,
                    jointNumber = match.switchJoint.number,
                    jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, joint.number),
                    edge = edge,
                    m = match.m - mRange.min,
                    direction = RelativeDirection.Along, // TODO: Suunta pitäisi saada fitted switchiltä
                )
            }
        }
    return jointsOnEdge
}

fun filterValidJointsOnEdge(
    switchStructure: SwitchStructure,
    jointsOnEdge: List<JointOnEdge>,
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<JointOnEdge> {
    val jointsByLocationTrack = jointsOnEdge.groupBy { jointOnEdge -> jointOnEdge.locationTrackId }
    val validJointsOnEdge =
        jointsByLocationTrack.flatMap { (locationTrackId, joints) ->
            val (_, geometry) = tracks.first { (locationTrack, _) -> locationTrack.id == locationTrackId }
            val validJoints = validateJointSequence(switchStructure, joints, geometry)
            validJoints
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
): LocationTrackGeometry =
    TmpLocationTrackGeometry.of(
        replaceEdges(originalEdges = geometry.edges, edgesToReplace, newEdges),
        geometry.trackId,
    )

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
    val switchNodeConnection = NodeConnection.switch(inner = switchLink, outer = null)

    return if (isSame(edge.start.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewStartNode = edge.withStartNode(switchNodeConnection)
        listOf(withNewStartNode)
    } else if (isSame(edge.end.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewEndNode = edge.withEndNode(switchNodeConnection)
        listOf(withNewEndNode)
    } else {
        val firstEdge = slice(edge, Range(0.0, mValue)).withEndNode(switchNodeConnection)
        val secondEdge = slice(edge, Range(mValue, edge.end.m)).withStartNode(switchNodeConnection)
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
                                lineLength(expectedLocation, candidateLocation) <=
                                    SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE
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

    // Then try to adjust unhandled joints by partial switch alignments
    val partialJointSequences = getPartialSwitchAlignmentJointSequences(fittedSwitch.switchStructure)
    val adjustedJointsForPartialJointSequences =
        adjustJointPositions(fittedSwitch, partialJointSequences, unhandledJointsOnEdge)
    return adjustedJointsForFullJointSequences + adjustedJointsForPartialJointSequences
}

fun clearSwitchFromTracks(
    switchId: IntId<LayoutSwitch>,
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> =
    tracks.map { (locationTrack, geometry) -> locationTrack to geometry.withoutSwitch(switchId) }

fun linkFittedSwitch(
    switchId: IntId<LayoutSwitch>,
    fittedSwitch: FittedSwitch,
    fittedSwitchTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    switchContainingTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    val tracksWithoutSwitch = clearSwitchFromTracks(switchId, switchContainingTracks)
    val fittedSwitchTracksWithoutSwitch = clearSwitchFromTracks(switchId, fittedSwitchTracks)

    val jointsOnEdges = mapFittedSwitchToEdges(fittedSwitch, fittedSwitchTracksWithoutSwitch)
    val adjustedJointsOnEdges = adjustJointPositions(fittedSwitch, jointsOnEdges)
    val validatedJoints =
        filterValidJointsOnEdge(fittedSwitch.switchStructure, adjustedJointsOnEdges, fittedSwitchTracks)
    val jointsOnSingleEdge = mergeJointsOnEdgesIntoSingleEdge(validatedJoints) // Onko merge tarpeen
    val jointsByEdge = jointsOnSingleEdge.groupBy { joint -> joint.edge }
    val tracksByEdge =
        jointsByEdge
            .map { (edge, joints) ->
                val (locationTrack, geometry) =
                    fittedSwitchTracksWithoutSwitch.first { (locationTrack, _) ->
                        locationTrack.id == joints.first().locationTrackId
                    }
                edge to (locationTrack to geometry)
            }
            .toMap()

    val linkedTracks =
        jointsByEdge.map { (edge, joints) ->
            val (locationTrack, geometry) = tracksByEdge.getValue(edge)
            val linkedEdges = linkJointsToEdge(switchId, edge, joints)
            val newGeometry = replaceEdges(geometry, listOf(edge), linkedEdges)
            locationTrack to newGeometry
        }

    val linkedAndClearedTracks =
        (linkedTracks + tracksWithoutSwitch).distinctBy { (locationTrack, _) -> locationTrack.id }

    return linkedAndClearedTracks
}
