package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId

fun getDuplicateTrackParentStatus(
    parentTrack: LocationTrack,
    parentGeometry: LocationTrackGeometry,
    childTrack: LocationTrack,
    childGeometry: LocationTrackGeometry,
): LocationTrackDuplicate {
    val parentTrackSplitPoints = collectSplitPoints(parentGeometry)
    val childTrackSplitPoints = collectSplitPoints(childGeometry)
    val (_, status) =
        getDuplicateMatches(parentTrackSplitPoints, childTrackSplitPoints, parentTrack.id, childTrack.duplicateOf)
            .first() // There has to at least one found, since we know the duplicateOf is set
    return LocationTrackDuplicate(
        id = parentTrack.id as IntId,
        trackNumberId = parentTrack.trackNumberId,
        nameStructure = parentTrack.nameStructure,
        name = parentTrack.name,
        descriptionStructure = parentTrack.descriptionStructure,
        start = childGeometry.start,
        end = childGeometry.end,
        length = childGeometry.length,
        duplicateStatus = status,
    )
}

fun getLocationTrackDuplicatesBySplitPoints(
    mainTrack: LocationTrack,
    mainGeometry: LocationTrackGeometry,
    duplicateTracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<LocationTrackDuplicate> {
    val mainTrackSplitPoints = collectSplitPoints(mainGeometry)
    return duplicateTracksAndGeometries
        .asSequence()
        .filter { (duplicateTrack, _) -> duplicateTrack.state != LocationTrackState.DELETED }
        .flatMap { (duplicateTrack, duplicateAlignment) ->
            getLocationTrackDuplicatesBySplitPoints(
                mainTrack.id,
                mainTrackSplitPoints,
                duplicateTrack,
                duplicateAlignment,
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
    duplicateGeometry: LocationTrackGeometry,
): List<Pair<Int, LocationTrackDuplicate>> {
    val duplicateTrackSplitPoints = collectSplitPoints(duplicateGeometry)
    val statuses =
        getDuplicateMatches(mainTrackSplitPoints, duplicateTrackSplitPoints, mainTrackId, duplicateTrack.duplicateOf)
            .filter { status -> (status.second.overlappingLength ?: 0.0) > 0 }
    return statuses.map { (jointIndex, status) ->
        jointIndex to
            LocationTrackDuplicate(
                id = duplicateTrack.id as IntId,
                trackNumberId = duplicateTrack.trackNumberId,
                nameStructure = duplicateTrack.nameStructure,
                name = duplicateTrack.name,
                descriptionStructure = duplicateTrack.descriptionStructure,
                start = duplicateGeometry.start,
                end = duplicateGeometry.end,
                length = duplicateGeometry.length,
                duplicateStatus = status,
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
                    overlappingLength = end.location.m.distance - start.location.m.distance,
                )
        }
    }
}

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

fun collectSplitPoints(geometry: LocationTrackGeometry): List<SplitPoint> {
    val geometryStart = geometry.start
    val nonSwitchStartPoint =
        if (geometryStart != null && geometry.startSwitchLink == null)
            EndpointSplitPoint(geometryStart, null, DuplicateEndPointType.START)
        else null

    val geometryEnd = geometry.end
    val nonSwitchEndPoint =
        if (geometryEnd != null && geometry.endSwitchLink == null)
            EndpointSplitPoint(geometryEnd, null, DuplicateEndPointType.END)
        else null

    val switchSplitPoints =
        geometry.trackSwitchLinks.map { link -> SwitchSplitPoint(link.location, null, link.switchId, link.jointNumber) }

    val allSplitPoints = listOfNotNull(nonSwitchStartPoint) + switchSplitPoints + listOfNotNull(nonSwitchEndPoint)
    return allSplitPoints
}
