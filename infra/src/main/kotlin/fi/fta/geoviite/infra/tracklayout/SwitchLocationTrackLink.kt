package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber

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
    val matchRanges = buildDuplicateIndexRanges(matchIndices)

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
                duplicateOfId = duplicateOf,
                startSwitchId = duplicateTrackJoints[range.first].first,
                endSwitchId = duplicateTrackJoints[range.last].first,
            )
        }
    }
}

fun findMatchingIndices(
    mainTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    duplicateTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
): List<Pair<Int, Int>> {
    var duplicateIndexStart = 0
    return mainTrackJoints.mapIndexedNotNull { mainIndex, idAndJoint ->
        val found = duplicateTrackJoints.subList(duplicateIndexStart, duplicateTrackJoints.size).indexOf(idAndJoint)
        found.takeIf { it >= 0 }?.let { foundIndex ->
            mainIndex to (duplicateIndexStart + foundIndex).also { duplicateIndexStart = it }
        }
    }
}

fun buildDuplicateIndexRanges(matches: List<Pair<Int, Int>>): List<IntRange> {
    val found = mutableListOf<IntRange>()
    matches.forEachIndexed { index, (mainIndex, duplicateIndex) ->
        val range = found.lastOrNull()

        val indicesSkip = matches
            .getOrNull(index - 1)
            ?.let { (prevMain, prevDup) -> prevMain != mainIndex - 1 || prevDup != duplicateIndex - 1 }
            ?: false

        if (range == null || indicesSkip) {
            // If there's no previous or it isn't compatible, start a new range
            found.add(duplicateIndex..duplicateIndex)
        } else {
            // Otherwise extend the last one
            found[found.lastIndex] = range.first..duplicateIndex
        }
    }
    // Single-joint range isn't a range
    return found.filter { r -> r.first != r.last }
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
