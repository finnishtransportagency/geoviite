package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.TrackBoundaryMoveFailureException
import fi.fta.geoviite.infra.linking.TrackEnd
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResultType
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.NodeConnection
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import org.springframework.transaction.annotation.Transactional

private const val ENDPOINT_MATCH_DISTANCE = 1.0

@GeoviiteService
class TrackBoundaryMoveService(
    private val trackBoundaryMoveDao: TrackBoundaryMoveDao,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val splitDao: SplitDao,
    private val switchLinkingService: SwitchLinkingService,
) {
    fun get(id: IntId<TrackBoundaryMove>): TrackBoundaryMove? {
        return trackBoundaryMoveDao.get(id)
    }

    @Transactional
    fun saveTrackBoundaryMove(
        layoutBranch: LayoutBranch,
        shorteningTrackId: IntId<LocationTrack>,
        lengtheningTrackId: IntId<LocationTrack>,
        boundaryMoveDirection: BoundaryMoveDirection,
        upToSwitchJoint: SwitchJointId?,
    ): IntId<TrackBoundaryMove> {
        if (layoutBranch != LayoutBranch.main) {
            throw TrackBoundaryMoveFailureException(
                "track boundary moves are only allowed in the main branch: branch=$layoutBranch",
                localizedMessageKey = "branch-not-main",
            )
        }
        val context = layoutBranch.draft
        val shorteningTrackVersion = locationTrackDao.fetchVersionOrThrow(context, shorteningTrackId)
        val lengtheningTrackVersion = locationTrackDao.fetchVersionOrThrow(context, lengtheningTrackId)

        val (shorteningTrack, shorteningTrackGeometry) = locationTrackService.getWithGeometry(shorteningTrackVersion)
        val (lengtheningTrack, lengtheningTrackGeometry) = locationTrackService.getWithGeometry(lengtheningTrackVersion)

        val unfinishedSplits = splitDao.fetchUnfinishedSplits(layoutBranch)
        val unpublishedBoundaryMoves = findUnpublishedBoundaryMoves(layoutBranch)
        val expectedTrackNumberId = lengtheningTrack.trackNumberId
        validateTrackForBoundaryMove(
            shorteningTrack,
            shorteningTrackGeometry,
            unfinishedSplits,
            unpublishedBoundaryMoves,
            expectedTrackNumberId,
        )
        validateTrackForBoundaryMove(
            lengtheningTrack,
            lengtheningTrackGeometry,
            unfinishedSplits,
            unpublishedBoundaryMoves,
            expectedTrackNumberId,
        )

        val geometries =
            getTrackBoundaryMoveGeometry(
                shorteningTrackGeometry = shorteningTrackGeometry,
                lengtheningTrackGeometry = lengtheningTrackGeometry,
                shorteningTrackId = shorteningTrackVersion.id,
                lengtheningTrackId = lengtheningTrackVersion.id,
                boundaryMoveDirection = boundaryMoveDirection,
                upToSwitchJoint = upToSwitchJoint,
            )
        locationTrackService.saveDraft(layoutBranch, shorteningTrack, geometries.shortenedGeometry)
        locationTrackService.saveDraft(layoutBranch, lengtheningTrack, geometries.lengthenedGeometry)
        val relinkedSwitches =
            relinkMovedSwitches(
                layoutBranch,
                lengtheningTrackVersion.id,
                shorteningTrackGeometry,
                geometries.movedEdgeRange,
            )
        return trackBoundaryMoveDao.save(
            layoutBranch,
            shorteningTrackVersion,
            geometries.movedEdgeRange,
            lengtheningTrackVersion,
            relinkedSwitches,
        )
    }

    private fun relinkMovedSwitches(
        layoutBranch: LayoutBranch,
        lengtheningTrackId: IntId<LocationTrack>,
        shorteningTrackGeometry: LocationTrackGeometry,
        movedEdgeRange: IntRange,
    ): List<IntId<LayoutSwitch>> {
        val movedSwitches =
            shorteningTrackGeometry.edges
                .slice(movedEdgeRange)
                .flatMap { edge -> edge.startNode.switches + edge.endNode.switches }
                .map { it.id }
                .toSet()
        if (movedSwitches.isEmpty()) return emptyList()
        val relinkingResults = switchLinkingService.relinkTrack(layoutBranch, lengtheningTrackId, movedSwitches)
        val failedSwitches =
            relinkingResults
                .filter { result -> result.outcome != TrackSwitchRelinkingResultType.RELINKED }
                .map { result -> result.id }
        if (failedSwitches.isNotEmpty()) {
            throw TrackBoundaryMoveFailureException(
                "switches could not be relinked after moving the track boundary: switches=$failedSwitches",
                localizedMessageKey = "switch-linking-failed",
            )
        }
        return relinkingResults.map { result -> result.id }
    }

    fun fetchPublicationVersions(
        branch: LayoutBranch,
        locationTracks: List<IntId<LocationTrack>>,
        switches: List<IntId<LayoutSwitch>>,
    ): List<RowVersion<TrackBoundaryMove>> =
        findUnpublishedBoundaryMoves(branch, locationTracks, switches).map { boundaryMove -> boundaryMove.version }

    /**
     * Fetches all boundary moves that are not published. Can be filtered by location tracks or relinked switches. If
     * both filters are defined, the result is combined by OR (match by either).
     */
    fun findUnpublishedBoundaryMoves(
        branch: LayoutBranch,
        locationTrackIds: List<IntId<LocationTrack>>? = null,
        switchIds: List<IntId<LayoutSwitch>>? = null,
    ): List<TrackBoundaryMove> =
        trackBoundaryMoveDao.getUnpublished().filter { boundaryMove ->
            val containsTrack = locationTrackIds?.any(boundaryMove::containsLocationTrack)
            val containsSwitch = switchIds?.any(boundaryMove::containsSwitch)
            boundaryMove.branch == branch &&
                when {
                    containsTrack != null && containsSwitch != null -> containsTrack || containsSwitch
                    containsTrack != null -> containsTrack
                    containsSwitch != null -> containsSwitch
                    else -> true
                }
        }

    @Transactional
    fun publishTrackBoundaryMove(
        validatedVersions: List<RowVersion<TrackBoundaryMove>>,
        locationTracks: Collection<LayoutRowVersion<LocationTrack>>,
        publicationId: IntId<Publication>,
    ): List<RowVersion<TrackBoundaryMove>> {
        return validatedVersions.map { boundaryMoveVersion ->
            val move = trackBoundaryMoveDao.getOrThrow(boundaryMoveVersion.id)
            requireNotNull(locationTracks.find { t -> t.id == move.shortenedLocationTrack.id }) {
                "Shortened track must be part of the same publication as the track boundary move: $move"
            }
            requireNotNull(locationTracks.find { t -> t.id == move.lengthenedLocationTrack.id }) {
                "Lengthened track must be part of the same publication as the track boundary move: $move"
            }

            trackBoundaryMoveDao.update(id = move.id, publicationId = publicationId)
        }
    }

    @Transactional(readOnly = true)
    fun getBoundaryMoveCounterpartOptions(
        layoutContext: LayoutContext,
        locationTrackId: IntId<LocationTrack>,
    ): List<BoundaryMoveCounterpart> {
        val (headTrack, headGeometry) = locationTrackService.getWithGeometryOrThrow(layoutContext, locationTrackId)
        val headStartPoint = TrackEnd.START.of(headGeometry)
        val headEndPoint = TrackEnd.END.of(headGeometry)
        val headSwitchIds = headGeometry.switchIds.toSet()

        if (headStartPoint == null || headEndPoint == null) return emptyList()

        val unfinishedSplits = splitDao.fetchUnfinishedSplits(layoutContext.branch)
        val unpublishedBoundaryMoves = findUnpublishedBoundaryMoves(layoutContext.branch)
        val getDisabledReasons = { track: LocationTrack, geometry: DbLocationTrackGeometry ->
            boundaryMoveDisabledReasons(
                track,
                geometry,
                unfinishedSplits,
                unpublishedBoundaryMoves,
                headTrack.trackNumberId,
            )
        }

        val counterpartFirstOptions =
            locationTrackService
                .listWithGeometries(
                    layoutContext = layoutContext,
                    boundingBox = boundingBoxAroundPoint(headStartPoint, ENDPOINT_MATCH_DISTANCE),
                )
                .let { candidates ->
                    getCounterpartFirstOptions(
                        candidates,
                        headTrack,
                        headStartPoint,
                        headGeometry,
                        headSwitchIds,
                        getDisabledReasons,
                    )
                }

        val headFirstOptions =
            locationTrackService
                .listWithGeometries(
                    layoutContext = layoutContext,
                    boundingBox = boundingBoxAroundPoint(headEndPoint, ENDPOINT_MATCH_DISTANCE),
                )
                .let { candidates ->
                    getHeadFirstOptions(
                        candidates,
                        headTrack,
                        headEndPoint,
                        headGeometry,
                        headSwitchIds,
                        getDisabledReasons,
                    )
                }

        return counterpartFirstOptions + headFirstOptions
    }

    fun delete(id: IntId<TrackBoundaryMove>) = trackBoundaryMoveDao.delete(id)
}

private fun validateTrackForBoundaryMove(
    track: LocationTrack,
    geometry: LocationTrackGeometry,
    unfinishedSplits: List<Split>,
    unpublishedBoundaryMoves: List<TrackBoundaryMove>,
    expectedTrackNumberId: IntId<LayoutTrackNumber>,
) {
    val reason =
        boundaryMoveDisabledReasons(track, geometry, unfinishedSplits, unpublishedBoundaryMoves, expectedTrackNumberId)
            .firstOrNull() ?: return
    val messageKey =
        when (reason) {
            BoundaryMoveDisabledReason.PART_OF_SPLIT -> "track-part-of-split"
            BoundaryMoveDisabledReason.PART_OF_BOUNDARY_MOVE -> "track-part-of-boundary-move"
            BoundaryMoveDisabledReason.TRACK_DRAFT_EXISTS -> "track-draft-exists"
            BoundaryMoveDisabledReason.NO_GEOMETRY -> "no-geometry"
            BoundaryMoveDisabledReason.SWITCHES_PART_OF_SPLIT -> "switches-part-of-split"
            BoundaryMoveDisabledReason.ON_DIFFERENT_TRACK_NUMBER -> "on-different-track-number"
        }
    throw TrackBoundaryMoveFailureException(
        "track cannot take part in a boundary move: id=${track.id}, reason=$reason",
        localizedMessageKey = messageKey,
        localizationParams = localizationParams("name" to track.name),
    )
}

private fun getHeadFirstOptions(
    candidates: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    headTrack: LocationTrack,
    headEndPoint: AlignmentPoint<LocationTrackM>,
    headGeometry: DbLocationTrackGeometry,
    headSwitchIds: Set<IntId<LayoutSwitch>>,
    getDisabledReasons: (LocationTrack, DbLocationTrackGeometry) -> List<BoundaryMoveDisabledReason>,
): List<BoundaryMoveCounterpart> =
    candidates
        .filter { (track, _) -> track.id != headTrack.id && track.exists }
        .mapNotNull { (candidate, candidateGeometry) ->
            val candidateStartPoint = TrackEnd.START.of(candidateGeometry)
            val candidateStartNode = candidateGeometry.startNode
            if (candidateStartPoint == null || candidateStartNode == null) null
            else
                getCounterpartOption(
                    headEndPoint,
                    headGeometry.edges.last().endNode,
                    candidateStartPoint,
                    candidateStartNode,
                    headSwitchIds,
                    candidateGeometry.switchIds.toSet(),
                    candidate.id as IntId,
                    BoundaryOrientation.HEAD_FIRST,
                    getDisabledReasons(candidate, candidateGeometry),
                )
        }

private fun getCounterpartFirstOptions(
    candidates: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    headTrack: LocationTrack,
    headStartPoint: AlignmentPoint<LocationTrackM>,
    headGeometry: DbLocationTrackGeometry,
    headSwitchIds: Set<IntId<LayoutSwitch>>,
    getDisabledReasons: (LocationTrack, DbLocationTrackGeometry) -> List<BoundaryMoveDisabledReason>,
): List<BoundaryMoveCounterpart> =
    candidates
        .filter { (track, _) -> track.id != headTrack.id && track.exists }
        .mapNotNull { (candidate, candidateGeometry) ->
            val candidateEndPoint = TrackEnd.END.of(candidateGeometry)
            val candidateEndNode = candidateGeometry.endNode
            if (candidateEndPoint == null || candidateEndNode == null) null
            else
                getCounterpartOption(
                    headStartPoint,
                    headGeometry.edges.first().startNode,
                    candidateEndPoint,
                    candidateEndNode,
                    headSwitchIds,
                    candidateGeometry.switchIds.toSet(),
                    candidate.id as IntId,
                    BoundaryOrientation.COUNTERPART_FIRST,
                    getDisabledReasons(candidate, candidateGeometry),
                )
        }

private fun getCounterpartOption(
    headPoint: AlignmentPoint<LocationTrackM>,
    headNode: NodeConnection,
    candidatePoint: AlignmentPoint<LocationTrackM>,
    candidateNode: NodeConnection,
    headSwitchIds: Set<IntId<LayoutSwitch>>,
    candidateSwitchIds: Set<IntId<LayoutSwitch>>,
    candidateId: IntId<LocationTrack>,
    orientation: BoundaryOrientation,
    disabledReasons: List<BoundaryMoveDisabledReason>,
): BoundaryMoveCounterpart? {
    val outsideMatchDistance = lineLength(headPoint, candidatePoint) > ENDPOINT_MATCH_DISTANCE
    val linkingSwitches = (candidateNode.switches.map { it.id } + headNode.switches.map { it.id }).toSet()
    val linkingSwitchJoint =
        (headNode.switchIn ?: headNode.switchOut)?.let { link -> SwitchJointId(link.id, link.jointNumber) }
    val tracksOverlap = !headSwitchIds.intersect(candidateSwitchIds).minus(linkingSwitches).isEmpty()
    return if (outsideMatchDistance || tracksOverlap) null
    else
        BoundaryMoveCounterpart(
            trackId = candidateId,
            orientation = orientation,
            connectingSwitchJoint = linkingSwitchJoint,
            disabledReasons = disabledReasons,
        )
}

private data class TrackBoundaryMoveGeometry(
    val movedEdgeRange: IntRange,
    val shortenedGeometry: TmpLocationTrackGeometry,
    val lengthenedGeometry: TmpLocationTrackGeometry,
)

private fun getTrackBoundaryMoveGeometry(
    shorteningTrackGeometry: LocationTrackGeometry,
    lengtheningTrackGeometry: LocationTrackGeometry,
    shorteningTrackId: IntId<LocationTrack>,
    lengtheningTrackId: IntId<LocationTrack>,
    boundaryMoveDirection: BoundaryMoveDirection,
    upToSwitchJoint: SwitchJointId?,
): TrackBoundaryMoveGeometry {
    val nodeIndex =
        if (upToSwitchJoint == null) {
            if (boundaryMoveDirection == BoundaryMoveDirection.DESCENDING) shorteningTrackGeometry.edges.size else 0
        } else {
            val index =
                shorteningTrackGeometry.nodes.indexOfFirst { n ->
                    n.containsJoint(upToSwitchJoint.switchId, upToSwitchJoint.jointNumber)
                }
            if (index < 0) {
                throw TrackBoundaryMoveFailureException(
                    "switch ${upToSwitchJoint.switchId} joint ${upToSwitchJoint.jointNumber} is not on the track to shorten " +
                        "$shorteningTrackId",
                    localizedMessageKey = "does-not-move-boundary",
                )
            }
            index
        }
    val edgeRangeToMove =
        if (boundaryMoveDirection == BoundaryMoveDirection.DESCENDING) IntRange(0, nodeIndex - 1)
        else IntRange(nodeIndex, shorteningTrackGeometry.edges.lastIndex)
    if (edgeRangeToMove.isEmpty()) {
        throw TrackBoundaryMoveFailureException(
            "switch joint $upToSwitchJoint is already the boundary between $shorteningTrackId and " +
                "$lengtheningTrackId, so the boundary would not move",
            localizedMessageKey = "does-not-move-boundary",
        )
    }
    val edgesToMove = shorteningTrackGeometry.edges.slice(edgeRangeToMove)
    val lengthenedGeometry =
        TmpLocationTrackGeometry.of(
            combineEdges(
                if (boundaryMoveDirection == BoundaryMoveDirection.DESCENDING)
                    lengtheningTrackGeometry.edges + edgesToMove
                else edgesToMove + lengtheningTrackGeometry.edges
            ),
            lengtheningTrackId,
        )
    val shortenedGeometry =
        TmpLocationTrackGeometry.of(
            shorteningTrackGeometry.edges.slice(
                if (boundaryMoveDirection == BoundaryMoveDirection.DESCENDING)
                    IntRange(nodeIndex, shorteningTrackGeometry.edges.lastIndex)
                else IntRange(0, nodeIndex - 1)
            ),
            shorteningTrackId,
        )
    return TrackBoundaryMoveGeometry(
        movedEdgeRange = edgeRangeToMove,
        lengthenedGeometry = lengthenedGeometry,
        shortenedGeometry = shortenedGeometry,
    )
}
