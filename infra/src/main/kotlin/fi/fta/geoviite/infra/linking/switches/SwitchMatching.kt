package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.LinkableSwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.switchConnectivity
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.toEdgeM
import fi.fta.geoviite.infra.util.mapNonNullValues
import kotlin.math.absoluteValue

fun matchFittedSwitchToTracks(
    fittedSwitch: FittedSwitch,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    layoutSwitchId: IntId<LayoutSwitch>?,
): Pair<SuggestedSwitch, Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>> {
    val initiallyClearedTracks = if (layoutSwitchId == null) tracks else clearSwitchFromTracks(tracks, layoutSwitchId)
    val enclosingSwitches = findSwitchesEnclosingMiddleJointsOfFit(fittedSwitch, initiallyClearedTracks)
    val fullyClearedTracks = clearSwitchesFromTracks(initiallyClearedTracks, enclosingSwitches)

    return suggest(
        fittedSwitch,
        fullyClearedTracks,
        matchToJointListsOnEdges(fittedSwitch, fullyClearedTracks),
        enclosingSwitches,
    ) to fullyClearedTracks
}

private fun suggest(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    trackLinks: Map<EdgeId, List<JointOnEdge>>,
    detachedSwitches: Set<IntId<LayoutSwitch>>,
) =
    SuggestedSwitch(
        fittedSwitch.joints.map {
            LayoutSwitchJoint(
                it.number,
                SwitchJointRole.of(fittedSwitch.switchStructure, it.number),
                it.location,
                it.locationAccuracy,
            )
        },
        suggestDelinking(clearedTracks) + suggestLinking(trackLinks, clearedTracks),
        topologicallyLinkedTracks = setOf(),
        detachSwitches = detachedSwitches,
    )

private fun matchToJointListsOnEdges(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> {
    val jointsOnEdges = mapFittedSwitchToEdges(fittedSwitch, clearedTracks)
    val adjustedJointsOnEdges = completeIncompleteJointSequences(fittedSwitch, jointsOnEdges, clearedTracks)
    return pickBestLinkingByTrack(adjustedJointsOnEdges, jointsOnEdges)
}

private fun findSwitchesEnclosingMiddleJointsOfFit(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Set<IntId<LayoutSwitch>> {
    val middleJointsOfStructure =
        fittedSwitch.switchStructure.alignments
            .flatMap { alignment -> alignment.jointNumbers.drop(1).dropLast(1) }
            .toSet()
    return fittedSwitch.joints
        .filter { joint -> middleJointsOfStructure.contains(joint.number) }
        .flatMap { joint ->
            joint.matches.flatMap { match ->
                switchesEnclosingEdge(
                    clearedTracks.getValue(match.locationTrackId).second.edges,
                    mapFittedSwitchJointMatchToEdge(clearedTracks, match, fittedSwitch, joint).first.edgeIndex,
                )
            }
        }
        .toSet()
}

private fun switchesEnclosingEdge(edges: List<LayoutEdge>, edgeIndex: Int): Set<IntId<LayoutSwitch>> {
    val switchesOnTrackBefore =
        edges
            .subList(0, edgeIndex + 1)
            .flatMap { listOf(it.startNode.switchIn?.id, it.endNode.switchIn?.id) }
            .dropLast(1)
            .filterNotNull()
            .toSet()
    val switchesOnTrackAfter =
        edges
            .subList(edgeIndex, edges.size)
            .flatMap { listOf(it.startNode.switchIn?.id, it.endNode.switchIn?.id) }
            .drop(1)
            .filterNotNull()
            .toSet()

    return switchesOnTrackBefore.intersect(switchesOnTrackAfter)
}

private fun pickBestLinkingByTrack(
    linking: Map<EdgeId, List<List<JointOnEdge>>>,
    originalByEdge: Map<EdgeId, List<JointOnEdge>>,
): Map<EdgeId, List<JointOnEdge>> {
    val originalJointHitsByTrack =
        originalByEdge.entries.groupBy({ (edgeId) -> edgeId.locationTrackId }, { (_, joints) -> joints }).mapValues {
            (_, joints) ->
            joints.flatten().map { it.jointNumber }.toSet()
        }
    return linking.entries
        .flatMap { (edgeId, jointSequences) -> jointSequences.map { jointSequence -> edgeId to jointSequence } }
        .sortedBy { (edgeId, joints) ->
            val onTrack = originalJointHitsByTrack[edgeId.locationTrackId] ?: setOf()
            -joints.count { onTrack.contains(it.jointNumber) }
        }
        .distinctBy { it.first.locationTrackId }
        .associate { it }
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
    val segment = geometry.segments[match.segmentIndex]
    val edgeIndex = geometry.edges.indexOfFirst { edge -> edge.segments.contains(segment) }
    val edgeMRange = geometry.edgeMs[edgeIndex]
    val edgeId = EdgeId(match.locationTrackId, edgeIndex)
    val value =
        JointOnEdge(
            jointNumber = match.switchJoint.number,
            jointRole = SwitchJointRole.of(fittedSwitch.switchStructure, joint.number),
            mOnEdge = match.mOnTrack.toEdgeM(edgeMRange.min),
            direction = match.direction,
            location = match.location,
        )
    return edgeId to value
}

private fun completeIncompleteJointSequences(
    fittedSwitch: FittedSwitch,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<List<JointOnEdge>>> {
    val structureAlignments = switchConnectivity(fittedSwitch.switchStructure).alignments

    return mapNonNullValues(jointsOnEdge) { (edgeId, jointsFromSwitchFit) ->
        fun isValidInnerJointLinkSequence(
            structureAlignment: LinkableSwitchStructureAlignment,
            joints: List<JointOnEdge>,
        ) = validateJointSequence(fittedSwitch.switchStructure, structureAlignment, joints, edgeId, clearedTracks)

        val validStructureAlignmentsForSequence =
            structureAlignments.filter { structureAlignment ->
                isValidInnerJointLinkSequence(structureAlignment, jointsFromSwitchFit)
            }
        listOfNotNull(
                validStructureAlignmentsForSequence.map { validStructureAlignment ->
                    tossExtraJoints(validStructureAlignment, jointsFromSwitchFit)
                },
                structureAlignments.mapNotNull { structureAlignment ->
                    completeJointSequenceFromSwitchStructure(
                            fittedSwitch,
                            structureAlignment,
                            getEdge(edgeId, clearedTracks),
                            jointsFromSwitchFit,
                        )
                        ?.takeIf { completedSequence ->
                            isValidInnerJointLinkSequence(structureAlignment, completedSequence)
                        }
                        ?.let { tossExtraJoints(structureAlignment, it) }
                },
            )
            .flatten()
    }
}

private fun validateJointSequence(
    switchStructure: SwitchStructure,
    structureAlignment: LinkableSwitchStructureAlignment,
    joints: List<JointOnEdge>,
    edgeId: EdgeId,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Boolean {
    val nonMathStructureJointSet =
        structureAlignment.joints.filter { SwitchJointRole.of(switchStructure, it) != SwitchJointRole.MATH }.toSet()
    val nonMathSequenceJointSet =
        joints.mapNotNull { joint -> joint.jointNumber.takeIf { joint.jointRole != SwitchJointRole.MATH } }.toSet()

    return nonMathSequenceJointSet == nonMathStructureJointSet &&
        jointSequenceFitsPossibleSplitAlignment(structureAlignment, joints, edgeId, tracks)
}

private fun tossExtraJoints(structureAlignment: LinkableSwitchStructureAlignment, joints: List<JointOnEdge>) =
    joints.filter { joint -> structureAlignment.joints.any { it == joint.jointNumber } }

private fun jointSequenceFitsPossibleSplitAlignment(
    alignmentToLink: LinkableSwitchStructureAlignment,
    joints: List<JointOnEdge>,
    edgeId: EdgeId,
    nearbyTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Boolean {
    val innerJoint = alignmentToLink.innerJointOfSplitAlignment
    return if (innerJoint == null) true
    else {
        val innerJointM = joints.find { it.jointNumber == innerJoint }?.mOnEdge
        innerJointM == null ||
            innerJointM.distance < MAX_INNER_JOINT_CHECK_SNAP_DISTANCE ||
            (innerJointM - getEdge(edgeId, nearbyTracks).end.m).distance.absoluteValue <
                MAX_INNER_JOINT_CHECK_SNAP_DISTANCE
    }
}

const val MAX_INNER_JOINT_CHECK_SNAP_DISTANCE = 1.0

private fun completeJointSequenceFromSwitchStructure(
    fittedSwitch: FittedSwitch,
    structureAlignment: LinkableSwitchStructureAlignment,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge>? {
    val middleJointNumbers =
        structureAlignment.joints.drop(1).dropLast(1).ifEmpty {
            listOfNotNull(structureAlignment.innerJointOfSplitAlignment)
        }
    return jointsOnEdge
        .firstOrNull { jointOnEdge -> middleJointNumbers.contains(jointOnEdge.jointNumber) }
        ?.let { middleJoint ->
            completeJointSequenceWithRootJoint(fittedSwitch, structureAlignment.joints, edge, jointsOnEdge, middleJoint)
        }
}

private fun completeJointSequenceWithRootJoint(
    fittedSwitch: FittedSwitch,
    structureJointSequence: List<JointNumber>,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
    rootJoint: JointOnEdge,
): List<JointOnEdge>? {
    val newJoints =
        structureJointSequence.minus(jointsOnEdge.map { it.jointNumber }).mapNotNull { missingJointNumber ->
            proposeCandidateJoint(
                    structureJointSequence,
                    edge,
                    fittedSwitch.switchStructure,
                    rootJoint,
                    missingJointNumber,
                )
                ?.takeIf { candidate -> candidateJointLocationIsValid(candidate, fittedSwitch) }
        }

    return (jointsOnEdge + newJoints)
        .sortedBy { it.mOnEdge }
        .takeIf { joints -> joints.map { it.jointNumber }.toSet() == structureJointSequence.toSet() }
}

private fun proposeCandidateJoint(
    jointSequence: List<JointNumber>,
    edge: LayoutEdge,
    switchStructure: SwitchStructure,
    rootJoint: JointOnEdge,
    missingJointNumber: JointNumber,
): JointOnEdge? {
    val useReverseOrder = rootJoint.direction == RelativeDirection.Against
    val sortedJointSequence = if (useReverseOrder) jointSequence.reversed() else jointSequence
    val missingJointIsBeforeRootJoint =
        sortedJointSequence.indexOf(missingJointNumber) < sortedJointSequence.indexOf(rootJoint.jointNumber)
    val newJointLocationM = if (missingJointIsBeforeRootJoint) edge.start.m else edge.end.m
    val newJointLocationPoint = edge.getPointAtM(newJointLocationM)?.toPoint()
    return if (newJointLocationPoint == null) null
    else
        JointOnEdge(
            jointNumber = missingJointNumber,
            jointRole = SwitchJointRole.of(switchStructure, missingJointNumber),
            mOnEdge = newJointLocationM,
            direction = rootJoint.direction,
            location = newJointLocationPoint,
        )
}

private fun candidateJointLocationIsValid(jointCandidate: JointOnEdge, fittedSwitch: FittedSwitch): Boolean {
    val expectedLocation =
        fittedSwitch.joints.firstOrNull { joint -> joint.number == jointCandidate.jointNumber }?.location
    return expectedLocation != null &&
        lineLength(expectedLocation, jointCandidate.location) <= SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE
}

private fun suggestDelinking(
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>
): Map<IntId<LocationTrack>, SwitchLinkingTrackLinks> =
    clearedTracks.mapValues { (_, track) -> SwitchLinkingTrackLinks(track.first.getVersionOrThrow().version, null) }

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
        locationTrack.getVersionOrThrow().version,
        SuggestedLinks(
            edgeIndex,
            joints.map { joint -> SwitchLinkingJoint(joint.mOnEdge, joint.jointNumber, joint.location) },
        ),
    )

private fun getEdge(
    edgeId: EdgeId,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): LayoutEdge = clearedTracks.getValue(edgeId.locationTrackId).second.edges[edgeId.edgeIndex]
