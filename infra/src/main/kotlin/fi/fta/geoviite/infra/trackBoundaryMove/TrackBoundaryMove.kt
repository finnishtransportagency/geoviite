package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack

enum class BoundaryOrientation {
    HEAD_FIRST,
    COUNTERPART_FIRST,
}

enum class BoundaryMoveDirection {
    ASCENDING,
    DESCENDING,
}

data class TrackBoundaryMove(
    val version: RowVersion<TrackBoundaryMove>,
    val shortenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val edgeRange: IntRange,
    val lengthenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val publicationId: IntId<Publication>?,
) {
    val id = version.id

    fun containsLocationTrack(track: IntId<LocationTrack>) =
        shortenedLocationTrack.id == track || lengthenedLocationTrack.id == track

    val locationTracks
        get() = listOf(shortenedLocationTrack, lengthenedLocationTrack)
}

data class TrackBoundaryMoveRequest(
    val shorteningTrackId: IntId<LocationTrack>,
    val lengtheningTrackId: IntId<LocationTrack>,
    val switch: IntId<LayoutSwitch>,
    val switchJoint: JointNumber,
    val boundaryMoveDirection: BoundaryMoveDirection,
)

data class SwitchJointId(val switchId: IntId<LayoutSwitch>, val joint: JointNumber)

data class BoundaryMoveCounterpart(
    val trackId: IntId<LocationTrack>,
    val orientation: BoundaryOrientation,
    val connectingSwitchJoint: SwitchJointId?,
)
