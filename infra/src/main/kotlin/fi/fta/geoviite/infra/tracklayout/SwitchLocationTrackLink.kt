package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.util.buildIntRanges

fun getLocationTrackDuplicatesByJoint(
    mainTrackId: DomainId<LocationTrack>,
    mainTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    duplicateTrack: LocationTrack,
    duplicateAlignment: LayoutAlignment,
): List<Pair<Int, LocationTrackDuplicate>> {
    val duplicateTrackJoints = collectSwitchJoints(duplicateTrack, duplicateAlignment)
    val statuses = getDuplicateMatches(mainTrackJoints, duplicateTrackJoints, mainTrackId, duplicateTrack.duplicateOf)
    return statuses.map { (jointIndex, status) ->
        jointIndex to LocationTrackDuplicate(
            duplicateTrack.id as IntId,
            duplicateTrack.trackNumberId,
            duplicateTrack.name,
            duplicateTrack.externalId,
            status,
        )
    }
}

fun getDuplicateMatches(
    mainTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    duplicateTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    mainTrackId: DomainId<LocationTrack>,
    duplicateOf: IntId<LocationTrack>?,
): List<Pair<Int, DuplicateStatus>> {
    val matchIndices = findMatchingIndices(mainTrackJoints, duplicateTrackJoints)
    val matchRanges = buildIntRanges(matchIndices).filter { r -> r.first != r.last }

    return if (matchRanges.isEmpty() && duplicateOf != mainTrackId) {
        // No matches, but also not marked as duplicate
        emptyList()
    } else if (matchRanges.isEmpty() && duplicateOf == mainTrackId) {
        // Marked as duplicate, but no matches -> something is likely wrong
        listOf(-1 to DuplicateStatus(SplitDuplicateMatch.NONE, duplicateOf, null, null))
    } else {
        matchRanges.map { range ->
            val match =
                if (range == 0..duplicateTrackJoints.lastIndex) SplitDuplicateMatch.FULL
                else SplitDuplicateMatch.PARTIAL
            range.first to DuplicateStatus(
                match = match,
                duplicateOf = duplicateOf,
                startSwitch = duplicateTrackJoints[range.first].first,
                endSwitch = duplicateTrackJoints[range.last].first,
            )
        }
    }
}

fun findMatchingIndices(
    mainTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    duplicateTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
): List<Int> {
    return if (duplicateTrackJoints.isNotEmpty()) {
        var duplicateIndexStart = 0
        mainTrackJoints.mapNotNull { idAndJoint ->
            val found = duplicateTrackJoints.subList(duplicateIndexStart, duplicateTrackJoints.size).indexOf(idAndJoint)
            if (found >= 0) (found + duplicateIndexStart).also { duplicateIndexStart = it } else null
        }
    } else {
        emptyList()
    }
}

fun collectSwitchJoints(
    track: LocationTrack,
    alignment: LayoutAlignment,
): List<Pair<IntId<TrackLayoutSwitch>, JointNumber>> {
    val allJoints = listOf(
        listOfNotNull(track.topologyStartSwitch?.let { s -> s.switchId to s.jointNumber }),
        alignment.segments.flatMap(::getSwitchJoints),
        listOfNotNull(track.topologyEndSwitch?.let { s -> s.switchId to s.jointNumber }),
    ).flatten().distinct()
    // Skip all but first/last joint of each switch
    return allJoints.filterIndexed { index, (id, _) ->
        allJoints.getOrNull(index - 1)?.first != id || allJoints.getOrNull(index + 1)?.first != id
    }
}

fun getSwitchJoints(segment: LayoutSegment) =
    segment.switchId
        ?.let { id -> listOfNotNull(segment.startJointNumber, segment.endJointNumber).map { id as IntId to it } }
        ?: emptyList()
