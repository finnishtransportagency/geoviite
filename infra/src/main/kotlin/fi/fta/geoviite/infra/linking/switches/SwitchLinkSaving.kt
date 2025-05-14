package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.linking.ALIGNMENT_LINKING_SNAP
import fi.fta.geoviite.infra.linking.slice
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.isSame
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.EdgeNode
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.util.mapNonNullValues
import kotlin.collections.component1
import kotlin.collections.component2

fun withChangesFromLinkingSwitch(
    suggestedSwitch: SuggestedSwitch,
    switchStructure: SwitchStructure,
    switchId: IntId<LayoutSwitch>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    require(clearedTracks.values.none { it.second.containsSwitch(switchId) }) {
        "Must clear switch from tracks before calling withChangesFromLinkingSwitch on it"
    }
    return suggestedSwitch.trackLinks.map { (locationTrackId, links) ->
        val (locationTrack, geometry) = clearedTracks.getValue(locationTrackId)
        locationTrack to
            (if (links.suggestedLinks != null) {
                val suggested = links.suggestedLinks
                val edge = geometry.edges[suggested.edgeIndex]
                replaceEdges(
                    geometry,
                    listOf(edge),
                    linkJointsToEdge(switchId, switchStructure, edge, suggested.joints),
                )
            } else geometry)
    }
}

fun clearSwitchFromTracks(
    switchId: IntId<LayoutSwitch>,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
) = tracks.mapValues { (_, track) -> track.first to track.second.withoutSwitch(switchId) }

fun matchFittedSwitchToTracks(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>?,
    name: SwitchName? = null,
): SuggestedSwitch {
    require(switchId == null || clearedTracks.values.none { it.second.containsSwitch(switchId) }) {
        "Must clear switch from tracks before calling matchFittedSwitchToTracks on it"
    }
    val jointsOnEdges = mapFittedSwitchToEdges(fittedSwitch, clearedTracks)
    val adjustedJointsOnEdges = adjustJointPositions(fittedSwitch, jointsOnEdges, clearedTracks)
    val validatedJoints = filterValidJointsOnEdge(fittedSwitch.switchStructure, adjustedJointsOnEdges, clearedTracks)

    val linkedTracks =
        (if (switchId != null) suggestDelinking(switchId, clearedTracks) else mapOf()) +
            suggestLinking(validatedJoints, clearedTracks)

    return SuggestedSwitch(
        fittedSwitch.switchStructure.id,
        fittedSwitch.joints.map {
            LayoutSwitchJoint(
                it.number,
                SwitchJointRole.of(fittedSwitch.switchStructure, it.number),
                it.location,
                it.locationAccuracy,
            )
        },
        linkedTracks,
        null,
        name ?: SwitchName(fittedSwitch.switchStructure.baseType.name),
    )
}

fun directlyApplyFittedSwitchChangesToTracks(
    switchId: IntId<LayoutSwitch>,
    fittedSwitch: FittedSwitch,
    relevantTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    val clearedTracks = clearSwitchFromTracks(switchId, relevantTracks.associateBy { it.first.id as IntId })
    val suggested = matchFittedSwitchToTracks(fittedSwitch, clearedTracks, switchId)
    return withChangesFromLinkingSwitch(suggested, fittedSwitch.switchStructure, switchId, clearedTracks)
}

fun createModifiedLayoutSwitchLinking(suggestedSwitch: SuggestedSwitch, layoutSwitch: LayoutSwitch): LayoutSwitch {
    val newGeometrySwitchId = suggestedSwitch.geometrySwitchId ?: layoutSwitch.sourceId

    return layoutSwitch.copy(
        sourceId = newGeometrySwitchId,
        joints = suggestedSwitch.joints,
        source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
    )
}

/**
 * When the main/presentation joint is in the middle of the switch alignment, tracks can end at the middle and therefore
 * the beginning and the end part of the switch alignment are also valid switch alignments.
 *
 * E.g. in RR type switch the joint 5 is the main/presentation joint and one of the switch alignments is 4-5-3,
 * therefore alignments 4-5 and 5-3 are also valid alignments.
 */
fun getPartialSwitchAlignmentJointSequences(switchStructure: SwitchStructure): List<List<JointNumber>> {
    val partialJointSequences =
        switchStructure.alignments.flatMap { alignment ->
            val originalSequence = alignment.jointNumbers
            val presentationJoinIndex = alignment.jointNumbers.indexOf(switchStructure.presentationJointNumber)
            if (presentationJoinIndex > 0 && presentationJoinIndex < alignment.jointNumbers.lastIndex) {
                // split alignment into two
                val headJoints = originalSequence.subList(0, presentationJoinIndex + 1)
                val tailJoints = originalSequence.subList(presentationJoinIndex, alignment.jointNumbers.lastIndex + 1)
                listOf(headJoints, tailJoints)
            } else emptyList()
        }
    return partialJointSequences
}

fun getSwitchAlignmentJointSequences(switchStructure: SwitchStructure): List<List<JointNumber>> {
    return switchStructure.alignments.map { alignment -> alignment.jointNumbers }
}

private fun validateJointSequence(
    switchStructure: SwitchStructure,
    edgeId: EdgeId,
    jointsOnEdge: List<JointOnEdge>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Boolean {
    val jointNumbersOnEdge = jointsOnEdge.map { jointOnEdge -> jointOnEdge.jointNumber }
    return isValidFullAlignment(switchStructure, jointNumbersOnEdge) ||
        isValidPartialAlignment(switchStructure, jointNumbersOnEdge, jointsOnEdge, clearedTracks, edgeId)
}

private fun isValidFullAlignment(
    switchStructure: SwitchStructure,
    jointNumbersOnLocationTrack: List<JointNumber>,
): Boolean {
    val validFullJointSequences = getSwitchAlignmentJointSequences(switchStructure)

    return validFullJointSequences.any { validJointSequence ->
        validJointSequence == jointNumbersOnLocationTrack ||
            validJointSequence.reversed() == jointNumbersOnLocationTrack
    }
}

private fun isValidPartialAlignment(
    switchStructure: SwitchStructure,
    jointNumbersOnLocationTrack: List<JointNumber>,
    jointsOnEdge: List<JointOnEdge>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    edgeId: EdgeId,
): Boolean {
    val validPartialJointSequences = getPartialSwitchAlignmentJointSequences(switchStructure)
    return validPartialJointSequences.any { validPartialJointSequence ->
        val hasAllJoints = validPartialJointSequence.containsAll(jointNumbersOnLocationTrack)
        val innerJointOnEdge =
            jointsOnEdge.find { jointOnEdge -> switchStructure.isInnerJoint(jointOnEdge.jointNumber) }
        val edge = clearedTracks.getValue(edgeId.locationTrackId).second.edges[edgeId.edgeIndex]
        val trackEndsToInnerJoint =
            // TODO: toleranssit mietittävä, vaikka fittauksen snappays varmaankin asettaa
            // jointin raiteen päähän
            innerJointOnEdge?.mOnEdge?.let { m -> m == 0.0 || m == edge.end.m } ?: false
        hasAllJoints && trackEndsToInnerJoint
    }
}

private fun mapFittedSwitchToEdges(
    fittedSwitch: FittedSwitch,
    nearbyTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> =
    fittedSwitch.joints
        .flatMap { joint ->
            joint.matches.map { match -> mapFittedSwitchJointMatchToEdge(nearbyTracks, match, fittedSwitch, joint) }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, joints) -> joints.distinct().sortedBy { it.mOnEdge } }

private fun mapFittedSwitchJointMatchToEdge(
    nearbyTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    match: FittedSwitchJointMatch,
    fittedSwitch: FittedSwitch,
    joint: FittedSwitchJoint,
): Pair<EdgeId, JointOnEdge> {
    val (_, geometry) = nearbyTracks.getValue(match.locationTrackId)
    val (edge, edgeMRange) = geometry.getEdgeAtMOrThrow(match.mOnTrack)
    val edgeId = EdgeId(match.locationTrackId, geometry.edges.indexOf(edge))
    val value =
        JointOnEdge(
            jointNumber = match.switchJoint.number,
            jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, joint.number),
            mOnEdge = match.mOnTrack - edgeMRange.min,
            direction = match.direction,
        )
    return edgeId to value
}

private fun filterValidJointsOnEdge(
    switchStructure: SwitchStructure,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> =
    jointsOnEdge.filter { (edgeId, joints) -> validateJointSequence(switchStructure, edgeId, joints, clearedTracks) }

fun replaceEdges(
    geometry: LocationTrackGeometry,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): LocationTrackGeometry {
    return TmpLocationTrackGeometry(replaceEdges(originalEdges = geometry.edges, edgesToReplace, newEdges))
}

private fun replaceEdges(
    originalEdges: List<LayoutEdge>,
    edgesToReplace: List<LayoutEdge>,
    newEdges: List<LayoutEdge>,
): List<LayoutEdge> {
    val replaceStartIndex =
        originalEdges.indexOfFirst { originalEdge ->
            originalEdge.startNode.node == edgesToReplace.first().startNode.node
        }
    val replaceEndIndex =
        originalEdges.indexOfLast { originalEdge -> originalEdge.endNode.node == edgesToReplace.last().endNode.node }
    require(replaceStartIndex != -1 && replaceEndIndex != -1) { "Cannot replace non existing edges" }
    val newAllEdges =
        originalEdges.subList(0, replaceStartIndex) +
            newEdges +
            originalEdges.subList(replaceEndIndex + 1, originalEdges.lastIndex + 1)
    return combineEdges(newAllEdges)
}

private fun linkJointsToEdge(
    switchId: IntId<LayoutSwitch>,
    switchStructure: SwitchStructure,
    edge: LayoutEdge,
    joints: List<SuggestedJoint>,
): List<LayoutEdge> {
    val edges = mutableListOf(edge)
    joints.forEachIndexed { index, joint ->
        val lastEdge = edges.removeLast()
        val edgeStartM = edges.sumOf { it.end.m }
        val role = SwitchJointRole.of(switchStructure, joint.jointNumber)
        val first = index == 0
        val last = index == joints.lastIndex
        edges.addAll(
            linkJointToEdge(switchId, lastEdge, joint.jointNumber, role, joint.mvalueOnEdge - edgeStartM, first, last)
        )
    }
    return edges
}

// TODO: Mieti toleranssit
const val SWITCH_JOINT_NODE_SNAPPING_TOLERANCE = ALIGNMENT_LINKING_SNAP

// TODO: Tämän pitää varmaan toimia yhteen topologialinkityksen kanssa
const val SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE = 2.0

private fun linkJointToEdge(
    switchId: IntId<LayoutSwitch>,
    edge: LayoutEdge,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
    mValue: Double,
    isFirstJointInSwitchAlignment: Boolean,
    isLastJointInSwitchAlignment: Boolean,
): List<LayoutEdge> {
    val switchLink = SwitchLink(switchId, jointRole, jointNumber)
    val switchEdgeNode = EdgeNode.switch(inner = switchLink, outer = null)

    return if (isSame(edge.start.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewStartNode = edge.withStartNode(switchEdgeNode)
        listOf(withNewStartNode)
    } else if (isSame(edge.end.m, mValue, SWITCH_JOINT_NODE_SNAPPING_TOLERANCE)) {
        val withNewEndNode = edge.withEndNode(switchEdgeNode)
        listOf(withNewEndNode)
    } else {
        val firstEdge =
            slice(edge, Range(0.0, mValue)).let {
                if (isFirstJointInSwitchAlignment) it else it.withEndNode(switchEdgeNode)
            }
        val secondEdge =
            slice(edge, Range(mValue, edge.end.m)).let {
                if (isLastJointInSwitchAlignment) it else it.withStartNode(switchEdgeNode)
            }
        listOf(firstEdge, secondEdge)
    }
}

private fun filterValidJointSequences(
    jointSequences: List<List<JointNumber>>,
    jointsByEdge: Map<EdgeId, List<JointOnEdge>>,
): Map<EdgeId, List<JointOnEdge>> =
    jointsByEdge.filterValues { joints ->
        val jointNumbersOnEdge = joints.map { jointOnLocationTrack -> jointOnLocationTrack.jointNumber }.toSet()
        jointSequences.any { structureJointSequence -> jointNumbersOnEdge == structureJointSequence.toSet() }
    }

private fun findMissingJoints(
    jointSequence: List<JointNumber>,
    jointsOnLocationTrack: List<JointOnEdge>,
): List<JointNumber> {
    val jointNumbersOnLocationTrack =
        jointsOnLocationTrack.map { jointOnLocationTrack -> jointOnLocationTrack.jointNumber }.distinct()
    val missingJoints = jointSequence.minus(jointNumbersOnLocationTrack)
    val missingSomeJoint = missingJoints.size > 0
    val missingAllJoints = missingJoints.size == jointSequence.size
    return if (missingSomeJoint && !missingAllJoints) missingJoints else emptyList()
}

/**
 * Tries to create missing joints.
 *
 * @return completed joint sequence if it is possible
 */
private fun completeJointSequence(
    fittedSwitch: FittedSwitch,
    jointSequence: List<JointNumber>,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge>? {
    val middleJointNumbers = jointSequence.drop(1).dropLast(1)
    val missingJointNumbers = findMissingJoints(jointSequence, jointsOnEdge)
    val middleJointIsMissing =
        missingJointNumbers.any { missingJointNumber -> middleJointNumbers.contains(missingJointNumber) }
    val middleJointOnEdge =
        jointsOnEdge.firstOrNull { jointOnEdge -> middleJointNumbers.contains(jointOnEdge.jointNumber) }

    return if (middleJointIsMissing || middleJointOnEdge == null) {
        // Middle joint is missing and it cannot be created automatically
        null
    } else {
        // Try to create missing joints
        val newJoints =
            missingJointNumbers.mapNotNull { missingJointNumber ->
                proposeCandidateJoint(middleJointOnEdge, jointSequence, missingJointNumber, edge, fittedSwitch)
                    .takeIf { candidate -> candidateJointLocationIsValid(edge, candidate, fittedSwitch) }
            }

        val allJoints = (jointsOnEdge + newJoints).sortedBy { it.mOnEdge }
        val allJointNumbers = allJoints.map { jointOnEdge -> jointOnEdge.jointNumber }
        val hasAllRequiredJoints = allJointNumbers.containsAll(jointSequence)
        if (hasAllRequiredJoints) allJoints else null
    }
}

private fun proposeCandidateJoint(
    middleJointOnEdge: JointOnEdge,
    jointSequence: List<JointNumber>,
    missingJointNumber: JointNumber,
    edge: LayoutEdge,
    fittedSwitch: FittedSwitch,
): JointOnEdge {
    val useReverseOrder = middleJointOnEdge.direction == RelativeDirection.Against
    val sortedJointSequence = if (useReverseOrder) jointSequence.reversed() else jointSequence
    val missingJointIsBeforeMiddleJoint =
        sortedJointSequence.indexOf(missingJointNumber) < sortedJointSequence.indexOf(middleJointOnEdge.jointNumber)
    val newJointLocationM = if (missingJointIsBeforeMiddleJoint) edge.start.m else edge.end.m
    return JointOnEdge(
        jointNumber = missingJointNumber,
        jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, missingJointNumber),
        mOnEdge = newJointLocationM,
        direction = middleJointOnEdge.direction,
    )
}

private fun candidateJointLocationIsValid(
    edge: LayoutEdge,
    jointCandidate: JointOnEdge,
    fittedSwitch: FittedSwitch,
): Boolean {
    val candidateLocation = edge.getPointAtM(jointCandidate.mOnEdge)
    val expectedLocation =
        fittedSwitch.joints.firstOrNull { joint -> joint.number == jointCandidate.jointNumber }?.location
    return expectedLocation != null &&
        candidateLocation != null &&
        lineLength(expectedLocation, candidateLocation) <= SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE
}

/** Tries to create missing joints. */
private fun completeJointSequences(
    fittedSwitch: FittedSwitch,
    structureJointSequences: List<List<JointNumber>>,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> =
    mapNonNullValues(jointsOnEdge) { (edgeId, joints) ->
        val edge = getEdge(edgeId, clearedTracks)
        structureJointSequences.firstNotNullOfOrNull { structureJointSequence ->
            completeJointSequence(fittedSwitch, structureJointSequence, edge, joints)
        }
    }

/** Filters out all joints of all handled location tracks */
private fun filterOutHandledJoints(
    allJointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    handledJointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
): Map<EdgeId, List<JointOnEdge>> {
    val handledLocationTracks = handledJointsOnEdge.keys.map { it.locationTrackId }.toSet()
    return allJointsOnEdge.filterKeys { edgeId -> !handledLocationTracks.contains(edgeId.locationTrackId) }
}

private fun adjustJointPositions(
    fittedSwitch: FittedSwitch,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> {
    // First try to adjust joints by full switch alignments
    val fullJointSequences = getSwitchAlignmentJointSequences(fittedSwitch.switchStructure)
    val adjustedJointsForFullJointSequences =
        adjustJointPositions(fittedSwitch, fullJointSequences, jointsOnEdge, clearedTracks)
    val unhandledJointsOnEdge = filterOutHandledJoints(jointsOnEdge, adjustedJointsForFullJointSequences)

    // Then try to adjust unhandled joints by partial switch alignments
    val partialJointSequences = getPartialSwitchAlignmentJointSequences(fittedSwitch.switchStructure)
    val adjustedJointsForPartialJointSequences =
        adjustJointPositions(fittedSwitch, partialJointSequences, unhandledJointsOnEdge, clearedTracks)
    return adjustedJointsForFullJointSequences + adjustedJointsForPartialJointSequences
}

private fun adjustJointPositions(
    fittedSwitch: FittedSwitch,
    structureJointSequences: List<List<JointNumber>>,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> {
    val validJointsOnEdge = filterValidJointSequences(structureJointSequences, jointsOnEdge)
    val unhandledJointsOnEdges = filterOutHandledJoints(jointsOnEdge, validJointsOnEdge)
    val completedJointsOnEdges =
        completeJointSequences(fittedSwitch, structureJointSequences, unhandledJointsOnEdges, clearedTracks)
    return validJointsOnEdge + completedJointsOnEdges
}

private fun suggestDelinking(
    switchId: IntId<LayoutSwitch>,
    relevantTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<IntId<LocationTrack>, SwitchLinkingTrackLinks> =
    relevantTracks
        .filterValues { it.first.switchIds.contains(switchId) }
        .mapValues { (_, track) -> SwitchLinkingTrackLinks(track.first.versionOrThrow.version, null) }

private fun suggestLinking(
    validatedJoints: Map<EdgeId, List<JointOnEdge>>,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<IntId<LocationTrack>, SwitchLinkingTrackLinks> =
    validatedJoints.entries.associate { (edgeId, joints) ->
        val (locationTrackId, edgeIndex) = edgeId
        val (locationTrack) = tracks.getValue(locationTrackId)
        locationTrackId to suggestTrackLink(locationTrack, edgeIndex, joints)
    }

private fun suggestTrackLink(locationTrack: LocationTrack, edgeIndex: Int, joints: List<JointOnEdge>) =
    SwitchLinkingTrackLinks(
        locationTrack.versionOrThrow.version,
        SuggestedLinks(edgeIndex, joints.map { joint -> SuggestedJoint(joint.mOnEdge, joint.jointNumber) }),
    )

private fun getEdge(
    edgeId: EdgeId,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): LayoutEdge = clearedTracks.getValue(edgeId.locationTrackId).second.edges[edgeId.edgeIndex]
