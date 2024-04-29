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
    val (_, status) = getDuplicateMatches(
        parentTrackJoints,
        childTrackJoints,
        parentTrack.id,
        childTrack.duplicateOf,
    ).first() // There has to at least one found, since we know the duplicateOf is set
    return LocationTrackDuplicate(
        parentTrack.id as IntId,
        parentTrack.trackNumberId,
        parentTrack.name,
        parentTrack.externalId,
        status,
    )
}

// TODO: Minne tämä kuuluisi?
data class SwitchJointOnTrack(
    val switchId: IntId<TrackLayoutSwitch>,
    val jointNumber: JointNumber,
    val pointOnTrack: AlignmentPoint,
)

fun getLocationTrackDuplicatesByJoint(
    mainTrack: LocationTrack,
    mainAlignment: LayoutAlignment,
    duplicateTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
): List<LocationTrackDuplicate> {
    val mainTrackJoints = collectSwitchJoints(mainTrack, mainAlignment)
    return duplicateTracksAndAlignments.asSequence().flatMap { (duplicateTrack, duplicateAlignment) ->
        getLocationTrackDuplicatesByJoint(mainTrack.id, mainTrackJoints, duplicateTrack, duplicateAlignment)
    }.sortedWith(compareBy({ it.first }, { it.second.name })).map { (_, duplicate) -> duplicate }.toList()
}

private fun getLocationTrackDuplicatesByJoint(
    mainTrackId: DomainId<LocationTrack>,
    mainTrackJoints: List<SwitchJointOnTrack>,
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
    mainTrackJoints: List<SwitchJointOnTrack>,
    duplicateTrackJoints: List<SwitchJointOnTrack>,
    mainTrackId: DomainId<LocationTrack>,
    duplicateOf: IntId<LocationTrack>?,
): List<Pair<Int, DuplicateStatus>> {
    val matchIndices = findOrderedMatches(mainTrackJoints, duplicateTrackJoints) { joint1, joint2 ->
        joint1.switchId == joint2.switchId && joint1.jointNumber == joint2.jointNumber
    }
    val matchRanges = buildDuplicateIndexRanges(matchIndices)

    return if (matchRanges.isEmpty() && duplicateOf != mainTrackId) {
        // No matches, but also not marked as duplicate
        emptyList()
    } else if (matchRanges.isEmpty() && duplicateOf == mainTrackId) {
        // Marked as duplicate, but no matches -> something is likely wrong
        listOf(-1 to DuplicateStatus(DuplicateMatch.NONE, duplicateOf, null, null, null, null))
    } else {
        matchRanges.map { range ->
            val match = if (range == 0..duplicateTrackJoints.lastIndex) DuplicateMatch.FULL
            else DuplicateMatch.PARTIAL
            range.first to DuplicateStatus(
                match = match,
                duplicateOfId = duplicateOf,
                startSwitchId = duplicateTrackJoints[range.first].switchId,
                startPoint = duplicateTrackJoints[range.first].pointOnTrack,
                endSwitchId = duplicateTrackJoints[range.last].switchId,
                endPoint = duplicateTrackJoints[range.last].pointOnTrack,
            )
        }
    }
}

private fun <T> findOrderedMatches(list1: List<T>, list2: List<T>): List<Pair<Int, Int>> =
    findOrderedMatches(list1, list2) { item1, item2 -> item1 == item2 }

/**
 * Finds matching items between two lists, continuing the search from the previous match to never seek backwards.
 * This ordering ensures that the result indices are monotonically increasing for both lists.
 *
 * @return list of pairs where each pair represents a matching item with the index from each list
 */
private fun <T> findOrderedMatches(
    list1: List<T>,
    list2: List<T>,
    equalsFunc: (item1: T, item2: T) -> Boolean,
): List<Pair<Int, Int>> {
    var duplicateIndexStart = 0
    return list1.mapIndexedNotNull { mainIndex, item ->
        list2
            // Search starting from previous match (sublist is a view -> doesn't copy the list)
            .subList(duplicateIndexStart, list2.size)
            .indexOfFirst { otherItem -> equalsFunc(item, otherItem) }
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
            ?.let { (prevMain, prevDup) -> prevMain != mainIndex - 1 || prevDup != duplicateIndex - 1 } ?: false

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
): List<SwitchJointOnTrack> {
    val topologyStartJoint = alignment.start?.let { start ->
        track.topologyStartSwitch?.let { s -> SwitchJointOnTrack(s.switchId, s.jointNumber, start) }
    }
    val topologyEndJoint = alignment.end?.let { end ->
        track.topologyEndSwitch?.let { s -> SwitchJointOnTrack(s.switchId, s.jointNumber, end) }
    }

    val allJoints = listOf(
        listOfNotNull(topologyStartJoint),
        alignment.segments.flatMap(::getSwitchJoints),
        listOfNotNull(topologyEndJoint),
    ).flatten().distinct()

    // Skip all but first/last joint of each switch
    return allJoints.filterIndexed { index, (switchId, _) ->
        allJoints.getOrNull(index - 1)?.switchId != switchId || allJoints.getOrNull(index + 1)?.switchId != switchId
    }
}

fun getSwitchJoints(segment: LayoutSegment): List<SwitchJointOnTrack> {
    val switchId = segment.switchId as IntId?
    val joints = switchId?.let { switchId ->
        val startJoint = segment.startJointNumber?.let { jointNumber ->
            SwitchJointOnTrack(
                switchId, jointNumber, segment.alignmentStart
            )
        }
        val endJoint = segment.endJointNumber?.let { jointNumber ->
            SwitchJointOnTrack(
                switchId, jointNumber, segment.alignmentEnd
            )
        }
        listOfNotNull(startJoint, endJoint)
    } ?: emptyList()
    return joints
}
