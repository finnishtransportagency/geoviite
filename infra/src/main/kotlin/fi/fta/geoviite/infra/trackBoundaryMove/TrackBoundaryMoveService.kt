package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import org.springframework.transaction.annotation.Transactional

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
        switch: IntId<LayoutSwitch>,
        switchJoint: JointNumber,
        direction: LengtheningDirection,
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
                switch,
                switchJoint,
                direction,
            )
        locationTrackService.saveDraft(layoutBranch, shorteningTrack, geometries.shortenedGeometry)
        locationTrackService.saveDraft(layoutBranch, lengtheningTrack, geometries.lengthenedGeometry)
        return trackBoundaryMoveDao.save(shorteningTrackVersion, geometries.movedEdgeRange, lengtheningTrackVersion)
    }

    fun fetchPublicationVersions(
        branch: LayoutBranch,
        locationTracks: List<IntId<LocationTrack>>,
    ): List<RowVersion<TrackBoundaryMove>> =
        findUnPublishedBoundaryMoves(branch, locationTracks).map { boundaryMove -> boundaryMove.version }

    fun findUnPublishedBoundaryMoves(
        branch: LayoutBranch,
        locationTrackIds: List<IntId<LocationTrack>>? = null,
    ): List<TrackBoundaryMove> =
        trackBoundaryMoveDao.getUnPublished().filter { boundaryMove ->
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
    switch: IntId<LayoutSwitch>,
    switchJoint: JointNumber,
    direction: LengtheningDirection,
): TrackBoundaryMoveGeometry {
    val switchNodeIndex = shorteningTrackGeometry.nodes.indexOfFirst { n -> n.containsJoint(switch, switchJoint) }
    require(switchNodeIndex >= 0) {
        "track to shorten $shorteningTrackId must contain switch $switch joint $switchJoint"
    }
    val edgeRangeToMove = when (direction) {
        LengtheningDirection.ASCENDING -> IntRange(0, switchNodeIndex - 1)
        LengtheningDirection.DESCENDING -> IntRange(switchNodeIndex, shorteningTrackGeometry.edges.lastIndex)
    }
    val edgesToMove = shorteningTrackGeometry.edges.slice(edgeRangeToMove)
    val remainingEdgeRange = when (direction) {
        LengtheningDirection.ASCENDING -> IntRange(switchNodeIndex, shorteningTrackGeometry.edges.lastIndex)
        LengtheningDirection.DESCENDING -> IntRange(0, switchNodeIndex - 1)
    }
    val lengthenedGeometry =
        TmpLocationTrackGeometry.of(
            combineEdges(
                when (direction) {
                    LengtheningDirection.ASCENDING -> lengtheningTrackGeometry.edges + edgesToMove
                    LengtheningDirection.DESCENDING -> edgesToMove + lengtheningTrackGeometry.edges
                }
            ),
            lengtheningTrackId,
        )
    val shortenedGeometry =
        TmpLocationTrackGeometry.of(shorteningTrackGeometry.edges.slice(remainingEdgeRange), shorteningTrackId)
    return TrackBoundaryMoveGeometry(
        movedEdgeRange = edgeRangeToMove,
        lengthenedGeometry = lengthenedGeometry,
        shortenedGeometry = shortenedGeometry,
    )
}
