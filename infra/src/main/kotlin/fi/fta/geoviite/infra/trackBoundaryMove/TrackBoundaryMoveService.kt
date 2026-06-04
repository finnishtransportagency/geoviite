package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.TrackBoundaryMoveFailureException
import fi.fta.geoviite.infra.linking.TrackEnd
import fi.fta.geoviite.infra.math.boundingBoxAroundPoint
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
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
        switch: IntId<LayoutSwitch>,
        switchJoint: JointNumber,
    ): IntId<TrackBoundaryMove> {
        val context = layoutBranch.draft
        val shorteningTrackVersion = locationTrackDao.fetchVersionOrThrow(context, shorteningTrackId)
        val lengtheningTrackVersion = locationTrackDao.fetchVersionOrThrow(context, lengtheningTrackId)

        val (shorteningTrack, shorteningTrackGeometry) = locationTrackService.getWithGeometry(shorteningTrackVersion)
        val (lengtheningTrack, lengtheningTrackGeometry) = locationTrackService.getWithGeometry(lengtheningTrackVersion)

        val geometries =
            getTrackBoundaryMoveGeometry(
                shorteningTrackGeometry = shorteningTrackGeometry,
                lengtheningTrackGeometry = lengtheningTrackGeometry,
                shorteningTrackId = shorteningTrackVersion.id,
                lengtheningTrackId = lengtheningTrackVersion.id,
                boundaryMoveDirection = boundaryMoveDirection,
                switch = switch,
                switchJoint = switchJoint,
            )
        locationTrackService.saveDraft(layoutBranch, shorteningTrack, geometries.shortenedGeometry)
        locationTrackService.saveDraft(layoutBranch, lengtheningTrack, geometries.lengthenedGeometry)
        return trackBoundaryMoveDao.save(shorteningTrackVersion, geometries.movedEdgeRange, lengtheningTrackVersion)
    }

    fun fetchPublicationVersions(
        branch: LayoutBranch,
        locationTracks: List<IntId<LocationTrack>>,
    ): List<RowVersion<TrackBoundaryMove>> =
        findUnpublishedBoundaryMoves(branch, locationTracks).map { boundaryMove -> boundaryMove.version }

    fun findUnpublishedBoundaryMoves(
        branch: LayoutBranch,
        locationTrackIds: List<IntId<LocationTrack>>? = null,
    ): List<TrackBoundaryMove> =
        trackBoundaryMoveDao.getUnpublished().filter { boundaryMove ->
            locationTrackIds == null || locationTrackIds.any(boundaryMove::containsLocationTrack)
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

        val counterpartFirstOptions =
            locationTrackService
                .listWithGeometries(
                    layoutContext = layoutContext,
                    trackNumberId = headTrack.trackNumberId,
                    boundingBox = boundingBoxAroundPoint(headStartPoint, ENDPOINT_MATCH_DISTANCE),
                )
                .let { candidates ->
                    getCounterpartFirstOptions(candidates, headTrack, headStartPoint, headGeometry, headSwitchIds)
                }

        val headFirstOptions =
            locationTrackService
                .listWithGeometries(
                    layoutContext = layoutContext,
                    trackNumberId = headTrack.trackNumberId,
                    boundingBox = boundingBoxAroundPoint(headEndPoint, ENDPOINT_MATCH_DISTANCE),
                )
                .let { candidates ->
                    getHeadFirstOptions(candidates, headTrack, headEndPoint, headGeometry, headSwitchIds)
                }

        return counterpartFirstOptions + headFirstOptions
    }
}

private fun getHeadFirstOptions(
    candidates: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    headTrack: LocationTrack,
    headEndPoint: AlignmentPoint<LocationTrackM>,
    headGeometry: DbLocationTrackGeometry,
    headSwitchIds: Set<IntId<LayoutSwitch>>,
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
                )
        }

private fun getCounterpartFirstOptions(
    candidates: List<Pair<LocationTrack, DbLocationTrackGeometry>>,
    headTrack: LocationTrack,
    headStartPoint: AlignmentPoint<LocationTrackM>,
    headGeometry: DbLocationTrackGeometry,
    headSwitchIds: Set<IntId<LayoutSwitch>>,
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
    switch: IntId<LayoutSwitch>,
    switchJoint: JointNumber,
): TrackBoundaryMoveGeometry {
    val switchNodeIndex = shorteningTrackGeometry.nodes.indexOfFirst { n -> n.containsJoint(switch, switchJoint) }
    if (switchNodeIndex < 0) {
        throw TrackBoundaryMoveFailureException(
            "switch $switch joint $switchJoint is not on the track to shorten $shorteningTrackId",
            localizedMessageKey = "does-not-move-boundary",
        )
    }
    val edgeRangeToMove =
        if (boundaryMoveDirection == BoundaryMoveDirection.DESCENDING) IntRange(0, switchNodeIndex - 1)
        else IntRange(switchNodeIndex, shorteningTrackGeometry.edges.lastIndex)
    if (edgeRangeToMove.isEmpty()) {
        throw TrackBoundaryMoveFailureException(
            "switch $switch joint $switchJoint is already the boundary between $shorteningTrackId and " +
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
                    IntRange(switchNodeIndex, shorteningTrackGeometry.edges.lastIndex)
                else IntRange(0, switchNodeIndex - 1)
            ),
            shorteningTrackId,
        )
    return TrackBoundaryMoveGeometry(
        movedEdgeRange = edgeRangeToMove,
        lengthenedGeometry = lengthenedGeometry,
        shortenedGeometry = shortenedGeometry,
    )
}
