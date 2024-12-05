package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber

data class EndPointSwitchInfo(val switchId: IntId<TrackLayoutSwitch>, val jointNumber: JointNumber)

data class EndPointSwitchInfos(val start: EndPointSwitchInfo?, val end: EndPointSwitchInfo?)

fun getDuplicateTrackParentStatus(
    parentTrack: LocationTrack,
    parentAlignment: LayoutAlignment,
    childTrack: LocationTrack,
    childAlignment: LayoutAlignment,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): LocationTrackDuplicate {
    val parentTrackSplitPoints = collectSplitPoints(parentTrack, parentAlignment, isPresentationJointNumber)
    val childTrackSplitPoints = collectSplitPoints(childTrack, childAlignment, isPresentationJointNumber)
    val (_, status) =
        getDuplicateMatches(parentTrackSplitPoints, childTrackSplitPoints, parentTrack.id, childTrack.duplicateOf)
            .first() // There has to at least one found, since we know the duplicateOf is set
    return LocationTrackDuplicate(
        parentTrack.id as IntId,
        parentTrack.trackNumberId,
        parentTrack.name,
        start = childAlignment.start,
        end = childAlignment.end,
        length = childAlignment.length,
        status,
    )
}

fun getLocationTrackDuplicatesBySplitPoints(
    mainTrack: LocationTrack,
    mainAlignment: LayoutAlignment,
    duplicateTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): List<LocationTrackDuplicate> {
    val mainTrackSplitPoints = collectSplitPoints(mainTrack, mainAlignment, isPresentationJointNumber)
    return duplicateTracksAndAlignments
        .asSequence()
        .flatMap { (duplicateTrack, duplicateAlignment) ->
            getLocationTrackDuplicatesBySplitPoints(
                mainTrack.id,
                mainTrackSplitPoints,
                duplicateTrack,
                duplicateAlignment,
                isPresentationJointNumber,
            )
        }
        .sortedWith(compareBy({ it.first }, { it.second.name }))
        .map { (_, duplicate) -> duplicate }
        .toList()
}

private fun getLocationTrackDuplicatesBySplitPoints(
    mainTrackId: DomainId<LocationTrack>,
    mainTrackSplitPoints: List<SplitPoint>,
    duplicateTrack: LocationTrack,
    duplicateAlignment: LayoutAlignment,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): List<Pair<Int, LocationTrackDuplicate>> {
    val duplicateTrackSplitPoints = collectSplitPoints(duplicateTrack, duplicateAlignment, isPresentationJointNumber)
    val statuses =
        getDuplicateMatches(mainTrackSplitPoints, duplicateTrackSplitPoints, mainTrackId, duplicateTrack.duplicateOf)
    return statuses.map { (jointIndex, status) ->
        jointIndex to
            LocationTrackDuplicate(
                duplicateTrack.id as IntId,
                duplicateTrack.trackNumberId,
                duplicateTrack.name,
                start = duplicateAlignment.start,
                end = duplicateAlignment.end,
                length = duplicateAlignment.length,
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
    mainTrackSplitPoints: List<SplitPoint>,
    duplicateTrackSplitPoints: List<SplitPoint>,
    mainTrackId: DomainId<LocationTrack>,
    duplicateOf: IntId<LocationTrack>?,
): List<Pair<Int, DuplicateStatus>> {
    val matchIndices =
        findOrderedMatches(mainTrackSplitPoints, duplicateTrackSplitPoints) { splitPoint1, splitPoint2 ->
            splitPoint1.isSame(splitPoint2)
        }
    val matchRanges = buildDuplicateIndexRanges(matchIndices)

    return if (matchRanges.isEmpty() && duplicateOf != mainTrackId) {
        // No matches, but also not marked as duplicate
        emptyList()
    } else if (matchRanges.isEmpty() && duplicateOf == mainTrackId) {
        // Marked as duplicate, but no matches -> something is likely wrong
        listOf(-1 to DuplicateStatus(DuplicateMatch.NONE, duplicateOf, null, null, null))
    } else {
        matchRanges.map { range ->
            val match =
                if (range == 0..duplicateTrackSplitPoints.lastIndex) DuplicateMatch.FULL else DuplicateMatch.PARTIAL
            val start = duplicateTrackSplitPoints[range.first]
            val end = duplicateTrackSplitPoints[range.last]
            range.first to
                DuplicateStatus(
                    match = match,
                    duplicateOfId = duplicateOf,
                    startSplitPoint = start,
                    endSplitPoint = end,
                    overlappingLength = end.location.m - start.location.m,
                )
        }
    }
}

private fun <T> findOrderedMatches(list1: List<T>, list2: List<T>): List<Pair<Int, Int>> =
    findOrderedMatches(list1, list2) { item1, item2 -> item1 == item2 }

/**
 * Finds matching items between two lists, continuing the search from the previous match to never seek backwards. This
 * ordering ensures that the result indices are monotonically increasing for both lists.
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

        val indicesSkip =
            matches.getOrNull(index - 1)?.let { (prevMain, prevDup) ->
                prevMain != mainIndex - 1 || prevDup != duplicateIndex - 1
            } ?: false

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

fun getEndPointSwitchInfo(
    segmentSwitchId: IntId<TrackLayoutSwitch>?,
    segmentJointNumber: JointNumber?,
    topologySwitchId: IntId<TrackLayoutSwitch>?,
    topologyJointNumber: JointNumber?,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): EndPointSwitchInfo? {
    return if (
        segmentSwitchId != null &&
            segmentJointNumber != null &&
            isPresentationJointNumber(segmentSwitchId, segmentJointNumber)
    ) {
        EndPointSwitchInfo(segmentSwitchId, segmentJointNumber)
    } else if (topologySwitchId != null && topologyJointNumber != null) {
        EndPointSwitchInfo(topologySwitchId, topologyJointNumber)
    } else if (segmentSwitchId != null && segmentJointNumber != null) {
        EndPointSwitchInfo(segmentSwitchId, segmentJointNumber)
    } else {
        null
    }
}

fun getEndPointSwitchInfos(
    track: LocationTrack,
    alignment: LayoutAlignment,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): EndPointSwitchInfos {
    val firstSegment = alignment.segments.firstOrNull()
    val lastSegment = alignment.segments.lastOrNull()
    return EndPointSwitchInfos(
        start =
            getEndPointSwitchInfo(
                firstSegment?.switchId,
                firstSegment?.startJointNumber,
                track.topologyStartSwitch?.switchId,
                track.topologyStartSwitch?.jointNumber,
                isPresentationJointNumber,
            ),
        end =
            getEndPointSwitchInfo(
                lastSegment?.switchId,
                lastSegment?.endJointNumber,
                track.topologyEndSwitch?.switchId,
                track.topologyEndSwitch?.jointNumber,
                isPresentationJointNumber,
            ),
    )
}

fun collectSplitPoints(
    track: LocationTrack,
    alignment: LayoutAlignment,
    isPresentationJointNumber: (IntId<TrackLayoutSwitch>, JointNumber) -> Boolean,
): List<SplitPoint> {
    val endPointSwitchInfos = getEndPointSwitchInfos(track, alignment, isPresentationJointNumber)

    val startSplitPoint =
        alignment.start?.let { start ->
            endPointSwitchInfos.start?.let { endPointSwitchInfo ->
                SwitchSplitPoint(start, null, endPointSwitchInfo.switchId, endPointSwitchInfo.jointNumber)
            } ?: EndpointSplitPoint(start, null, DuplicateEndPointType.START)
        }

    val endSplitPoint =
        alignment.end?.let { end ->
            endPointSwitchInfos.end?.let { endPointSwitchInfo ->
                SwitchSplitPoint(end, null, endPointSwitchInfo.switchId, endPointSwitchInfo.jointNumber)
            } ?: EndpointSplitPoint(end, null, DuplicateEndPointType.END)
        }

    val switchSplitPoints = alignment.segmentsWithM.flatMap { (s, m) -> getSwitchSplitPoints(m.min, s) }

    val allSplitPoints =
        listOf(listOfNotNull(startSplitPoint), switchSplitPoints, listOfNotNull(endSplitPoint)).flatten()

    val uniqueSplitPoints =
        allSplitPoints.filterIndexed { index, splitPoint ->
            val firstIndex = allSplitPoints.indexOfFirst { otherSplitPoint -> splitPoint.isSame(otherSplitPoint) }
            firstIndex == index
        }
    return uniqueSplitPoints
}

fun getSwitchSplitPoints(segmentStartM: Double, segment: LayoutSegment): List<SwitchSplitPoint> {
    val joints =
        segment.switchId?.let { switchId ->
            val startPoint =
                segment.startJointNumber?.let { jointNumber ->
                    SwitchSplitPoint(segment.segmentStart.toAlignmentPoint(segmentStartM), null, switchId, jointNumber)
                }
            val endPoint =
                segment.endJointNumber?.let { jointNumber ->
                    SwitchSplitPoint(segment.segmentEnd.toAlignmentPoint(segmentStartM), null, switchId, jointNumber)
                }
            listOfNotNull(startPoint, endPoint)
        } ?: emptyList()
    return joints
}
