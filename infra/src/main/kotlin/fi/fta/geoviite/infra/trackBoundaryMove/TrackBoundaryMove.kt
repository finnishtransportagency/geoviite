package fi.fta.geoviite.infra.trackBoundaryMove

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.split.AdministrativeChange
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM

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
    val branch: LayoutBranch,
    val shortenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val edgeRange: IntRange,
    val lengthenedLocationTrack: LayoutRowVersion<LocationTrack>,
    val relinkedSwitches: List<IntId<LayoutSwitch>>,
    val publicationId: IntId<Publication>?,
) : AdministrativeChange {
    val id = version.id

    override fun containsLocationTrack(id: IntId<LocationTrack>) =
        shortenedLocationTrack.id == id || lengthenedLocationTrack.id == id

    override fun containsSwitch(id: IntId<LayoutSwitch>) = relinkedSwitches.contains(id)

    val locationTracks
        get() = listOf(shortenedLocationTrack, lengthenedLocationTrack)
}

data class TrackBoundaryMoveRequest(
    val shorteningTrackId: IntId<LocationTrack>,
    val lengtheningTrackId: IntId<LocationTrack>,
    val upToSwitchJoint: SwitchJointId?,
    val boundaryMoveDirection: BoundaryMoveDirection,
    val deleteShorteningTrack: Boolean,
)

data class SwitchJointId(val switchId: IntId<LayoutSwitch>, val jointNumber: JointNumber)

enum class BoundaryMoveDisabledReason {
    PART_OF_SPLIT,
    PART_OF_BOUNDARY_MOVE,
    TRACK_DRAFT_EXISTS,
    NO_GEOMETRY,
    SWITCHES_PART_OF_SPLIT,
    ON_DIFFERENT_TRACK_NUMBER,
    OVERLAPPING_ADDRESSES,
    GEOCODING_FAILED,
}

fun boundaryMoveDisabledReasons(
    track: LocationTrack,
    geometry: LocationTrackGeometry,
    trackAddresses: AlignmentAddresses<LocationTrackM>?,
    unfinishedSplits: List<Split>,
    unpublishedBoundaryMoves: List<TrackBoundaryMove>,
    expectedTrackNumberId: IntId<LayoutTrackNumber>,
): List<BoundaryMoveDisabledReason> {
    val trackId = track.id as IntId
    val unpublishedSplits = unfinishedSplits.filter { split -> split.publicationId == null }
    return listOfNotNull(
        BoundaryMoveDisabledReason.PART_OF_SPLIT.takeIf {
            unfinishedSplits.any { split -> split.containsLocationTrack(trackId) }
        },
        BoundaryMoveDisabledReason.PART_OF_BOUNDARY_MOVE.takeIf {
            unpublishedBoundaryMoves.any { move -> move.containsLocationTrack(trackId) }
        },
        BoundaryMoveDisabledReason.TRACK_DRAFT_EXISTS.takeIf { track.isDraft },
        BoundaryMoveDisabledReason.NO_GEOMETRY.takeIf { geometry.isEmpty },
        BoundaryMoveDisabledReason.SWITCHES_PART_OF_SPLIT.takeIf {
            geometry.switchIds.any { switchId -> unpublishedSplits.any { split -> split.containsSwitch(switchId) } }
        },
        BoundaryMoveDisabledReason.ON_DIFFERENT_TRACK_NUMBER.takeIf { track.trackNumberId != expectedTrackNumberId },
        BoundaryMoveDisabledReason.GEOCODING_FAILED.takeIf { trackAddresses == null && !geometry.isEmpty },
    )
}

data class BoundaryMoveCounterpart(
    val trackId: IntId<LocationTrack>,
    val orientation: BoundaryOrientation,
    val connectingSwitchJoint: SwitchJointId?,
    val disabledReasons: List<BoundaryMoveDisabledReason> = emptyList(),
)
