package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
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
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.switchLibrary.ISwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.ContextCache
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.EdgeM
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LineM
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
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType
import fi.fta.geoviite.infra.tracklayout.replaceEdges
import fi.fta.geoviite.infra.util.processFlattened
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors

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
        val originallyLinkedByRequestIndex =
            collectOriginallyLinkedLocationTracks(branch, requests.map { it.layoutSwitchId })
        val newlyMatchedByRequestIndex =
            collectNewlyMatchedLocationTracks(fitGrids, originallyLinkedByRequestIndex, branch)
        val originalSwitches =
            requests.map { request -> switchDao.fetch(switchDao.fetchVersion(branch.draft, request.layoutSwitchId)!!) }
        val switchStructures = originalSwitches.map { switchLibraryService.getSwitchStructure(it.switchStructureId) }

        return fitGrids
            .mapIndexed { i, r -> i to r }
            .map { (index, fitGrid) ->
                val switchId = requests[index].layoutSwitchId
                val switchStructure = switchStructures[index]
                val originallyLinked = originallyLinkedByRequestIndex[index]
                val relevantTracks =
                    newlyMatchedByRequestIndex[index] + clearSwitchFromTracks(switchId, originallyLinked)
                fitGrid
                    .map(parallel = true) { fit ->
                        SuggestedSwitchWithOriginallyLinkedTracks(
                            matchFittedSwitchToTracks(fit, relevantTracks, layoutSwitchId = switchId),
                            originallyLinked.keys,
                        )
                    }
                    .map { suggestion ->
                        val changedTracks =
                            withChangesFromLinkingSwitch(
                                suggestion.suggestedSwitch,
                                switchStructure,
                                switchId,
                                relevantTracks,
                            )
                        val withTopoChanges =
                            locationTrackService.recalculateTopology(branch.draft, changedTracks, switchId)
                        val topoLinkTrackIds = gatherOuterSwitchLinks(withTopoChanges, switchId)

                        SuggestedSwitchWithOriginallyLinkedTracks(
                            suggestion.suggestedSwitch.copy(topologicallyLinkedTracks = topoLinkTrackIds),
                            suggestion.originallyLinkedTracks,
                        )
                    }
            }
            .toList()
    }

    private fun collectNewlyMatchedLocationTracks(
        fitGrids: List<PointAssociation<FittedSwitch>>,
        originallyLinkedByRequestIndex: List<Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>>,
        branch: LayoutBranch,
    ): List<Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>> {
        val matchedLocationTracksByRequestIndex =
            fitGrids.map { grid ->
                grid
                    .keys()
                    .flatMap { fit ->
                        fit.joints.flatMap { joint -> joint.matches.map { match -> match.locationTrackId } }
                    }
                    .toSet()
            }
        return processFlattened(
                matchedLocationTracksByRequestIndex.zip(originallyLinkedByRequestIndex) { matched, original ->
                    matched.minus(original.keys).toList()
                }
            ) { locationTrackIds ->
                locationTrackService.getManyWithGeometries(branch.draft, locationTrackIds)
            }
            .map { tracks -> tracks.associateBy { it.first.id as IntId } }
    }

    private fun gatherOuterSwitchLinks(
        tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
        switchId: IntId<LayoutSwitch>,
    ): Set<IntId<LocationTrack>> =
        tracks
            .filter {
                it.second.trackSwitchLinks.any { sl -> sl.switchId == switchId && sl.type == TrackSwitchLinkType.OUTER }
            }
            .map { it.first.id as IntId }
            .toSet()

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
        layoutSwitchId: IntId<LayoutSwitch>?,
    ): GeometrySwitchSuggestionResult =
        when (val fit = switchFittingService.fitGeometrySwitch(branch, geometrySwitchId)) {
            is GeometrySwitchFittingFailure -> GeometrySwitchSuggestionFailure(fit.failure)
            is GeometrySwitchFittingSuccess ->
                GeometrySwitchSuggestionSuccess(createSwitchLinkingMatch(branch, fit.switch, layoutSwitchId))
        }

    private fun createSwitchLinkingMatch(
        branch: LayoutBranch,
        fit: FittedSwitch,
        layoutSwitchId: IntId<LayoutSwitch>? = null,
    ): SuggestedSwitch {
        val tracksAroundFit = findLocationTracksNearFittedSwitch(branch, fit)
        val unlinkedOriginalTracks =
            layoutSwitchId?.let { clearSwitchFromTracks(it, findOriginallyLinkedTracks(branch, it)) } ?: emptyMap()
        return matchFittedSwitchToTracks(fit, tracksAroundFit + unlinkedOriginalTracks, layoutSwitchId)
    }

    @Transactional
    fun saveSwitchLinking(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        layoutSwitchId: IntId<LayoutSwitch>,
        geometrySwitchId: IntId<GeometrySwitch>?,
    ): LayoutRowVersion<LayoutSwitch> {
        verifySwitchExists(branch, layoutSwitchId)
        val originalSwitch = switchService.getOrThrow(branch.draft, layoutSwitchId)
        geometrySwitchId?.let(::verifyPlanNotHidden)

        val originalTracks =
            suggestedSwitch.trackLinks.keys.associateWith { id ->
                locationTrackService.getWithGeometryOrThrow(branch.draft, id)
            }
        val changedTracks =
            withChangesFromLinkingSwitch(
                suggestedSwitch,
                switchLibraryService.getSwitchStructure(originalSwitch.switchStructureId),
                layoutSwitchId,
                clearSwitchFromTracks(layoutSwitchId, originalTracks),
            )
        val recalc = locationTrackService.recalculateTopology(branch.draft, changedTracks, layoutSwitchId)
        saveLocationTrackChanges(branch, recalc, originalTracks)
        return updateLayoutSwitch(branch, suggestedSwitch, layoutSwitchId)
    }

    private fun saveLocationTrackChanges(
        branch: LayoutBranch,
        maybeChanged: List<Pair<LocationTrack, LocationTrackGeometry>>,
        original: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    ) =
        maybeChanged.forEach { (locationTrack, geometry) ->
            val (_, originalGeometry) = original[locationTrack.id as IntId] ?: (null to null)
            if (originalGeometry != geometry) {
                locationTrackService.saveDraft(branch, locationTrack, geometry)
            }
        }

    fun findLocationTracksNearFittedSwitch(
        branch: LayoutBranch,
        fittedSwitch: FittedSwitch,
    ): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
        getSwitchBoundsFromSwitchFit(fittedSwitch)
            ?.let { bbox -> locationTrackService.listNearWithGeometries(branch.draft, bbox) }
            ?.associateBy { (t, _) -> t.id as IntId } ?: mapOf()

    private fun findOriginallyLinkedTracks(
        branch: LayoutBranch,
        switchId: IntId<LayoutSwitch>,
    ): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
        switchService.getLocationTracksLinkedToSwitch(branch.draft, switchId).associateBy { (t, _) -> t.id as IntId }

    private fun collectOriginallyLinkedLocationTracks(
        branch: LayoutBranch,
        switches: List<IntId<LayoutSwitch>>,
    ): List<Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>> {
        val bySwitchId =
            switchDao.findLocationTracksLinkedToSwitches(branch.draft, switches).mapValues { ids ->
                ids.value
                    .map { lt -> lt.rowVersion }
                    .let(locationTrackService::getManyWithGeometries)
                    .associateBy { it.first.id as IntId }
            }
        return switches.map { switchId -> bySwitchId[switchId] ?: mapOf() }
    }

    @Transactional
    fun relinkTrack(branch: LayoutBranch, trackId: IntId<LocationTrack>): List<TrackSwitchRelinkingResult> {
        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, trackId)

        val originalSwitches =
            collectAllSwitchesOnTrackAndNearby(branch, track, geometry).let { nearbySwitchIds ->
                nearbySwitchIds.zip(switchService.getMany(branch.draft, nearbySwitchIds))
            }

        val originallyLinkedLocationTracksByIndex =
            collectOriginallyLinkedLocationTracks(branch, originalSwitches.map { (switchId) -> switchId })

        val changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
            mutableMapOf()

        val relinkingResults =
            originalSwitches.mapIndexedNotNull { index, (switchId, originalSwitch) ->
                val originallyLinked = originallyLinkedLocationTracksByIndex[index]
                relinkOneSwitchOnTrack(
                    branch,
                    trackId,
                    originalSwitch,
                    switchId,
                    originallyLinked,
                    changedLocationTracks,
                )
            }
        changedLocationTracks.values.forEach { (track, geometry) ->
            locationTrackService.saveDraft(branch, track, geometry)
        }
        return relinkingResults
    }

    private fun relinkOneSwitchOnTrack(
        branch: LayoutBranch,
        relinkingTrackId: IntId<LocationTrack>,
        originalSwitch: LayoutSwitch,
        switchId: IntId<LayoutSwitch>,
        originallyLinked: Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>,
        changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    ): TrackSwitchRelinkingResult? {
        val switchStructure = switchLibraryService.getSwitchStructure(originalSwitch.switchStructureId)
        val presentationJointLocation = originalSwitch.getJoint(switchStructure.presentationJointNumber)?.location
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

        return applyFittedSwitchInTrackRelinking(
            relinkingTrackId,
            createFittedSwitchByPoint(switchId, presentationJointLocation, switchStructure, nearbyTracksForFit),
            switchId,
            branch,
            originallyLinked,
            changedLocationTracks,
            switchStructure,
        )
    }

    private fun applyFittedSwitchInTrackRelinking(
        relinkingTrackId: IntId<LocationTrack>,
        fittedSwitch: FittedSwitch?,
        switchId: IntId<LayoutSwitch>,
        branch: LayoutBranch,
        originallyLinked: Map<IntId<LocationTrack>, Pair<LocationTrack, DbLocationTrackGeometry>>,
        changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
        switchStructure: SwitchStructure,
    ): TrackSwitchRelinkingResult? =
        if (fittedSwitch == null) {
            TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.NOT_AUTOMATICALLY_LINKABLE)
        } else if (!fittedSwitch.isFittedOn(relinkingTrackId)) {
            null
        } else {
            val nearbyTracksForMatch =
                findLocationTracksNearFittedSwitch(branch, fittedSwitch) +
                    clearSwitchFromTracks(switchId, originallyLinked + changedLocationTracks)
            val match = matchFittedSwitchToTracks(fittedSwitch, nearbyTracksForMatch, layoutSwitchId = switchId)
            locationTrackService
                .recalculateTopology(
                    branch.draft,
                    withChangesFromLinkingSwitch(
                        match,
                        switchStructure,
                        switchId,
                        nearbyTracksForMatch.filterKeys { track -> match.trackLinks.containsKey(track) },
                    ),
                    switchId,
                )
                .forEach { track ->
                    val id = track.first.id as IntId
                    val orig = nearbyTracksForMatch[id]
                    if (orig == null || orig != track) changedLocationTracks[id] = track
                }
            updateLayoutSwitch(branch, match, switchId)
            TrackSwitchRelinkingResult(switchId, TrackSwitchRelinkingResultType.RELINKED)
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
        val nearbySwitches = switchDao.findSwitchesNearTrack(branch, locationTrack.getVersionOrThrow())
        return (trackSwitches + nearbySwitches).distinct()
    }

    private fun createModifiedLayoutSwitchLinking(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<LayoutSwitch>,
    ): LayoutSwitch {
        val switch = switchService.getOrThrow(branch.draft, switchId)
        return createModifiedLayoutSwitchLinking(suggestedSwitch, switch, switch.sourceId as? IntId)
    }

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

fun getSwitchBoundsFromTracks(tracks: Collection<LocationTrackGeometry>, switchId: IntId<LayoutSwitch>): BoundingBox? =
    tracks
        .flatMap { geometry ->
            geometry.nodesWithLocation.mapNotNull { (node, point) ->
                if (node.containsSwitch(switchId)) point else null
            }
        }
        .let(::boundingBoxAroundPointsOrNull)

private fun getSwitchBoundsFromSwitchFit(fittedSwitch: FittedSwitch): BoundingBox? =
    boundingBoxAroundPointsOrNull(fittedSwitch.joints.map { joint -> joint.location }, TRACK_SEARCH_AREA_SIZE)

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

fun linkJointsToEdge(
    switchId: IntId<LayoutSwitch>,
    switchStructure: ISwitchStructure,
    edge: LayoutEdge,
    joints: List<SwitchLinkingJoint>,
): List<LayoutEdge> {
    val edges = mutableListOf(edge)
    joints.forEachIndexed { index, joint ->
        val lastEdge = edges.removeLast()
        val edgeStartM = edges.sumOf { it.end.m.distance }
        val role = SwitchJointRole.of(switchStructure, joint.jointNumber)
        val first = index == 0
        val last = index == joints.lastIndex
        edges.addAll(
            linkJointToEdge(switchId, lastEdge, joint.jointNumber, role, joint.mvalueOnEdge - edgeStartM, first, last)
        )
    }
    return edges
}

// TODO: Mieti toleranssit
const val SWITCH_JOINT_NODE_SNAPPING_TOLERANCE = ALIGNMENT_LINKING_SNAP

// TODO: Mostly 1.0 is good, ROI V0600 wants more but then also complains in validation if it does get it
const val SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE = 2.0

private fun linkJointToEdge(
    switchId: IntId<LayoutSwitch>,
    edge: LayoutEdge,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
    mValue: LineM<EdgeM>,
    isFirstJointInSequence: Boolean,
    isLastJointInSequence: Boolean,
): List<LayoutEdge> {
    val switchLink = SwitchLink(switchId, jointRole, jointNumber)

    return if (isSame(edge.start.m.distance, mValue.distance, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        linkJointToEdgeStart(edge, isLastJointInSequence, switchLink)
    } else if (isSame(edge.end.m.distance, mValue.distance, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        linkJointToEdgeEnd(edge, isFirstJointInSequence, switchLink)
    } else {
        linkJointToEdgeMiddle(
            edge,
            mValue,
            switchLink,
            isFirstJointInSequence = isFirstJointInSequence,
            isLastJointInSequence = isLastJointInSequence,
        )
    }
}

private fun linkJointToEdgeStart(
    edge: LayoutEdge,
    isLastJointInSequence: Boolean,
    switchLink: SwitchLink,
): List<TmpLayoutEdge> =
    listOf(
        edge.withStartNode(
            if (isLastJointInSequence) NodeConnection.switch(inner = edge.startNode.switchIn, outer = switchLink)
            else NodeConnection.switch(inner = switchLink, outer = edge.startNode.switchOut)
        )
    )

private fun linkJointToEdgeEnd(
    edge: LayoutEdge,
    isFirstJointInSequence: Boolean,
    switchLink: SwitchLink,
): List<TmpLayoutEdge> =
    listOf(
        edge.withEndNode(
            if (isFirstJointInSequence) NodeConnection.switch(inner = edge.endNode.switchIn, outer = switchLink)
            else NodeConnection.switch(inner = switchLink, outer = edge.endNode.switchOut)
        )
    )

private fun linkJointToEdgeMiddle(
    edge: LayoutEdge,
    mValue: LineM<EdgeM>,
    switchLink: SwitchLink,
    isFirstJointInSequence: Boolean,
    isLastJointInSequence: Boolean,
): List<TmpLayoutEdge> {
    require(!(isFirstJointInSequence && isLastJointInSequence)) { "can't link joint topologically mid-track" }
    val middleOuterNodeConnection = NodeConnection.switch(inner = null, outer = switchLink)
    val middleInnerNodeConnection = NodeConnection.switch(inner = switchLink, outer = null)
    return listOf(
        slice(edge, Range(LineM(0.0), mValue))
            .withEndNode(if (isFirstJointInSequence) middleOuterNodeConnection else middleInnerNodeConnection),
        slice(edge, Range(mValue, edge.end.m))
            .withStartNode(if (isLastJointInSequence) middleOuterNodeConnection else middleInnerNodeConnection),
    )
}

fun withChangesFromLinkingSwitch(
    suggestedSwitch: SuggestedSwitch,
    switchStructure: SwitchStructure,
    switchId: IntId<LayoutSwitch>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    require(clearedTracks.values.none { it.second.containsSwitch(switchId) }) {
        "Must clear switch from tracks before calling withChangesFromLinkingSwitch on it"
    }
    return suggestedSwitch.trackLinks.map { (locationTrackId, links) ->
        val (locationTrack, geometry) = clearedTracks.getValue(locationTrackId)
        locationTrack to
            (if (links.suggestedLinks != null) {
                val suggested = links.suggestedLinks
                val edge = geometry.edges[suggested.edgeIndex]
                replaceEdges(
                    geometry,
                    listOf(edge),
                    linkJointsToEdge(switchId, switchStructure, edge, suggested.joints),
                )
            } else geometry)
    }
}

fun clearSwitchFromTracks(
    switchId: IntId<LayoutSwitch>,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
) = tracks.mapValues { (_, track) -> track.first to track.second.withoutSwitch(switchId) }

fun createModifiedLayoutSwitchLinking(
    suggestedSwitch: SuggestedSwitch,
    layoutSwitch: LayoutSwitch,
    geometrySwitchId: IntId<GeometrySwitch>?,
): LayoutSwitch {
    return layoutSwitch.copy(
        sourceId = geometrySwitchId,
        joints = suggestedSwitch.joints,
        source = if (geometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
    )
}
