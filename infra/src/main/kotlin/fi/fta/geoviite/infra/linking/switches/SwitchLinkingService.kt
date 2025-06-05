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
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.switchLibrary.ISwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.ContextCache
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
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
import fi.fta.geoviite.infra.tracklayout.replaceEdges
import java.util.stream.Collectors
import kotlin.collections.find
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

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

        val originallyLinkedBySwitch =
            collectOriginallyLinkedLocationTracksBySwitch(branch, requests.map { it.layoutSwitchId })
        // fitting can move switches far enough to change how it e.g. makes topological links, so we
        // need to re-lookup nearby alignments
        val alignmentsNearFits = collectLocationTracksNearFitGrids(fitGrids, locationTrackCache)
        val switches = switchService.getMany(branch.draft, requests.map { it.layoutSwitchId }).associateBy { it.id }

        return fitGrids
            .mapIndexed { i, r -> i to r }
            .parallelStream()
            .map { (index, fitGrid) ->
                val switchId = requests[index].layoutSwitchId
                val originallyLinked = originallyLinkedBySwitch[switchId] ?: mapOf()
                val alignmentsNearFit = alignmentsNearFits[index] ?: listOf()
                val relevantTracks =
                    alignmentsNearFit.associateBy { it.first.id as IntId } +
                        clearSwitchFromTracks(switchId, originallyLinked)
                fitGrid.map(parallel = true) { fit ->
                    SuggestedSwitchWithOriginallyLinkedTracks(
                        matchFittedSwitchToTracks(
                            fit,
                            relevantTracks,
                            layoutSwitchId = switchId,
                            geometrySwitchId = switches.getValue(switchId).sourceId as? IntId,
                        ),
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
        layoutSwitchId: IntId<LayoutSwitch>?,
    ): GeometrySwitchSuggestionResult =
        when (val fit = switchFittingService.fitGeometrySwitch(branch, geometrySwitchId)) {
            is GeometrySwitchFittingFailure -> GeometrySwitchSuggestionFailure(fit.failure)
            is GeometrySwitchFittingSuccess ->
                GeometrySwitchSuggestionSuccess(
                    findRelevantTracksAndMatchFittedSwitch(
                            branch,
                            fit.switch,
                            layoutSwitchId = layoutSwitchId,
                            geometrySwitchId = geometrySwitchId,
                        )
                        .first
                )
        }

    private fun findRelevantTracksAndMatchFittedSwitch(
        branch: LayoutBranch,
        fit: FittedSwitch,
        geometrySwitchId: IntId<GeometrySwitch>? = null,
        layoutSwitchId: IntId<LayoutSwitch>? = null,
    ): Pair<SuggestedSwitch, Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>> {
        val tracksAroundFit = findLocationTracksForMatchingSwitchToTracks(branch, fit)
        val originallyLinkedTracks =
            layoutSwitchId?.let { id ->
                clearSwitchFromTracks(
                    id,
                    switchService.getLocationTracksLinkedToSwitch(branch.draft, id).associateBy { it.first.id as IntId },
                )
            } ?: mapOf()
        val relevantTracks = tracksAroundFit + originallyLinkedTracks
        val match =
            matchFittedSwitchToTracks(
                fit,
                relevantTracks,
                layoutSwitchId = layoutSwitchId,
                geometrySwitchId = geometrySwitchId,
            )
        return match to relevantTracks.filterKeys { track -> match.trackLinks.containsKey(track) }
    }

    @Transactional
    fun saveSwitchLinking(
        branch: LayoutBranch,
        suggestedSwitch: SuggestedSwitch,
        switchId: IntId<LayoutSwitch>,
    ): LayoutRowVersion<LayoutSwitch> {
        verifySwitchExists(branch, switchId)
        (suggestedSwitch.id as? IntId)?.let(::verifyPlanNotHidden)

        val originalTracks =
            suggestedSwitch.trackLinks.keys.associateWith { id ->
                locationTrackService.getWithGeometryOrThrow(branch.draft, id)
            }
        val changedTracks =
            withChangesFromLinkingSwitch(
                suggestedSwitch,
                switchLibraryService.getSwitchStructure(suggestedSwitch.switchStructureId),
                switchId,
                clearSwitchFromTracks(switchId, originalTracks),
            )
        saveLocationTrackChanges(branch, changedTracks, originalTracks)
        return updateLayoutSwitch(branch, suggestedSwitch, switchId)
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

    fun findLocationTracksForMatchingSwitchToTracks(
        branch: LayoutBranch,
        fittedSwitch: FittedSwitch,
        switchId: IntId<LayoutSwitch>? = null,
    ): Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> {
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

        val changedLocationTracks: MutableMap<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>> =
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
                            nearby + clearSwitchFromTracks(switchId, original + changedLocationTracks)
                        }
                    val match =
                        matchFittedSwitchToTracks(
                            fittedSwitch,
                            nearbyTracksForMatch,
                            geometrySwitchId = originalSwitch.sourceId as? IntId,
                            layoutSwitchId = switchId,
                        )
                    withChangesFromLinkingSwitch(
                            match,
                            switchLibraryService.getSwitchStructure(match.switchStructureId),
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

fun getSwitchBoundsFromTracks(tracks: Collection<LocationTrackGeometry>, switchId: IntId<LayoutSwitch>): BoundingBox? =
    tracks
        .flatMap { geometry ->
            geometry.nodesWithLocation.mapNotNull { (node, point) ->
                if (node.containsSwitch(switchId)) point else null
            }
        }
        .let(::boundingBoxAroundPointsOrNull)

private fun getSwitchBoundsFromSwitchFit(suggestedSwitch: FittedSwitch): BoundingBox? =
    boundingBoxAroundPointsOrNull(suggestedSwitch.joints.map { joint -> joint.location }, TRACK_SEARCH_AREA_SIZE)

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

fun linkJointsToEdge(
    switchId: IntId<LayoutSwitch>,
    switchStructure: ISwitchStructure,
    edge: LayoutEdge,
    joints: List<SwitchLinkingJoint>,
): List<LayoutEdge> {
    val edges = mutableListOf(edge)
    joints.forEachIndexed { index, joint ->
        val lastEdge = edges.removeLast()
        val edgeStartM = edges.sumOf { it.end.m }
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
    mValue: Double,
    isFirstJointInSequence: Boolean,
    isLastJointInSequence: Boolean,
): List<LayoutEdge> {
    val switchLink = SwitchLink(switchId, jointRole, jointNumber)

    return if (isSame(edge.start.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        linkJointToEdgeStart(edge, isLastJointInSequence, switchLink)
    } else if (isSame(edge.end.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
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
    mValue: Double,
    switchLink: SwitchLink,
    isFirstJointInSequence: Boolean,
    isLastJointInSequence: Boolean,
): List<TmpLayoutEdge> {
    require(!(isFirstJointInSequence && isLastJointInSequence)) { "can't link joint topologically mid-track" }
    val middleOuterNodeConnection = NodeConnection.switch(inner = null, outer = switchLink)
    val middleInnerNodeConnection = NodeConnection.switch(inner = switchLink, outer = null)
    return listOf(
        slice(edge, Range(0.0, mValue))
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

fun createModifiedLayoutSwitchLinking(suggestedSwitch: SuggestedSwitch, layoutSwitch: LayoutSwitch): LayoutSwitch {
    val newGeometrySwitchId = suggestedSwitch.id as? IntId

    return layoutSwitch.copy(
        sourceId = newGeometrySwitchId,
        joints = suggestedSwitch.joints,
        source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
    )
}
