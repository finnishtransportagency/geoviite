package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber

fun getDuplicateTrackParentStatus(
    parentTrack: LocationTrack,
    parentAlignment: LayoutAlignment,
    childTrack: LocationTrack,
    childAlignment: LayoutAlignment,
): LocationTrackDuplicate {
    val parentTrackJoints = collectSwitchJoints(parentTrack, parentAlignment)
    val childTrackJoints = collectSwitchJoints(childTrack, childAlignment)
    val statuses = getDuplicateMatches(
        parentTrackJoints,
        childTrackJoints,
        parentTrack.id,
        childTrack.duplicateOf,
    )
    return statuses.map { (jointIndex, status) ->
        jointIndex to LocationTrackDuplicate(
            parentTrack.id as IntId,
            parentTrack.trackNumberId,
            parentTrack.name,
            parentTrack.externalId,
            status,
        )
    }.first().second // There has to at least one found, since we know the duplicateOf is set
}

fun getLocationTrackDuplicatesByJoint(
    mainTrack: LocationTrack,
    mainAlignment: LayoutAlignment,
    duplicateTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
): List<LocationTrackDuplicate> {
    val mainTrackJoints = collectSwitchJoints(mainTrack, mainAlignment)
    return duplicateTracksAndAlignments
        .asSequence()
        .flatMap { (duplicateTrack, duplicateAlignment) ->
            getLocationTrackDuplicatesByJoint(mainTrack.id, mainTrackJoints, duplicateTrack, duplicateAlignment)
        }
        .sortedWith(compareBy({ it.first }, { it.second.name }))
        .map { (_, duplicate) -> duplicate }
        .toList()
}

private fun getLocationTrackDuplicatesByJoint(
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

/**
 * This function finds duplicate-track segments, comparing the potential duplicate to the main track.
 *
 * @return a pair of match start-index (for sorting matches between duplicates) and the duplicate status
 */
fun getDuplicateMatches(
    mainTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    duplicateTrackJoints: List<Pair<IntId<TrackLayoutSwitch>, JointNumber>>,
    mainTrackId: DomainId<LocationTrack>,
    duplicateOf: IntId<LocationTrack>?,
): List<Pair<Int, DuplicateStatus>> {
    val matchIndices =
        findOrderedMatches(mainTrackJoints, duplicateTrackJoints)
    val matchRanges = buildDuplicateIndexRanges(matchIndices)

    return if (matchRanges.isEmpty() && duplicateOf != mainTrackId) {
        // No matches, but also not marked as duplicate
        emptyList()
    } else if (matchRanges.isEmpty() && duplicateOf == mainTrackId) {
        // Marked as duplicate, but no matches -> something is likely wrong
        listOf(-1 to DuplicateStatus(DuplicateMatch.NONE, duplicateOf, null, null))
    } else {
        matchRanges.map { range ->
            val match =
                if (range == 0..duplicateTrackJoints.lastIndex) DuplicateMatch.FULL
                else DuplicateMatch.PARTIAL
            range.first to DuplicateStatus(
                match = match,
                duplicateOfId = duplicateOf,
                startSwitchId = duplicateTrackJoints[range.first].first,
                endSwitchId = duplicateTrackJoints[range.last].first,
            )
        }
    }
}

/**
 * Finds matching items between two lists, continuing the search from the previous match to never seek backwards.
 * This ordering ensures that the result indices are monotonically increasing for both lists.
 *
 * @return list of pairs where each pair represents a matching item with the index from each list
 */
private fun <T> findOrderedMatches(list1: List<T>, list2: List<T>): List<Pair<Int, Int>> {
    var duplicateIndexStart = 0
    return list1.mapIndexedNotNull { mainIndex, item ->
        list2
            // Search starting from previous match (sublist is a view -> doesn't copy the list)
            .subList(duplicateIndexStart, list2.size)
            .indexOf(item)
            .takeIf { it >= 0 } // indexOf returns -1 if not found
            ?.let { foundIndex -> mainIndex to (duplicateIndexStart + foundIndex) }
            ?.also { (_, duplicateIndex) -> duplicateIndexStart = duplicateIndex }
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
