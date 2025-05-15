package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.SwitchName
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
import fi.fta.geoviite.infra.tracklayout.TOPOLOGY_CALC_DISTANCE
import fi.fta.geoviite.infra.util.mapNonNullValues
import kotlin.collections.component1
import kotlin.collections.component2

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
    val adjustedJointsOnEdges = completeIncompleteJointSequences(fittedSwitch, jointsOnEdges, clearedTracks)

    val linkedTracks =
        (if (switchId != null) suggestDelinking(switchId, clearedTracks) else mapOf()) +
            suggestLinking(adjustedJointsOnEdges, clearedTracks)

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

private fun completeIncompleteJointSequences(
    fittedSwitch: FittedSwitch,
    jointsOnEdge: Map<EdgeId, List<JointOnEdge>>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<JointOnEdge>> {
    val structureAlignments = switchConnectivity(fittedSwitch.switchStructure).alignments

    return mapNonNullValues(jointsOnEdge) { (edgeId, jointsFromSwitchFit) ->
        fun isValidInnerJointLinkSequence(
            structureAlignment: LinkableSwitchStructureAlignment,
            joints: List<JointOnEdge>,
        ) = validateJointSequence(structureAlignment, joints, edgeId, clearedTracks)

        val sequenceIsValidTopoLink =
            jointSequenceIsValidTopologyLink(fittedSwitch, edgeId, jointsFromSwitchFit, clearedTracks)
        val sequenceIsValidEdgeLink =
            structureAlignments.any { structureAlignment ->
                isValidInnerJointLinkSequence(structureAlignment, jointsFromSwitchFit)
            }
        if (sequenceIsValidTopoLink || sequenceIsValidEdgeLink) {
            jointsFromSwitchFit
        } else {
            structureAlignments.firstNotNullOfOrNull { structureAlignment ->
                completeJointSequenceFromSwitchStructure(
                        fittedSwitch,
                        structureAlignment.joints,
                        getEdge(edgeId, clearedTracks),
                        jointsFromSwitchFit,
                    )
                    ?.takeIf { completedSequence ->
                        isValidInnerJointLinkSequence(structureAlignment, completedSequence)
                    }
            }
        }
    }
}

private fun jointSequenceIsValidTopologyLink(
    fittedSwitch: FittedSwitch,
    edgeId: EdgeId,
    jointSequence: List<JointOnEdge>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
) =
    if (jointSequence.size != 1 || jointSequence[0].jointRole == SwitchJointRole.MATH) {
        false
    } else {
        val singleJoint = jointSequence[0]
        val edge = getEdge(edgeId, clearedTracks)
        val point = edge.getPointAtM(singleJoint.mOnEdge)
        val switchJoint = fittedSwitch.joints.find { it.number == singleJoint.jointNumber }
        point != null &&
            switchJoint != null &&
            (singleJoint.mOnEdge < TOPOLOGY_CALC_DISTANCE ||
                edge.end.m - singleJoint.mOnEdge < TOPOLOGY_CALC_DISTANCE) &&
            lineLength(point, switchJoint.location) < TOPOLOGY_CALC_DISTANCE
    }

private fun validateJointSequence(
    structureAlignment: LinkableSwitchStructureAlignment,
    joints: List<JointOnEdge>,
    edgeId: EdgeId,
    tracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
) =
    structureAlignment.joints.toSet() == joints.map { it.jointNumber }.toSet() &&
        jointSequenceFitsPossibleSplitAlignment(structureAlignment, joints, edgeId, tracks)

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
        innerJointM == null || innerJointM == 0.0 || innerJointM == getEdge(edgeId, nearbyTracks).end.m
    }
}

private fun completeJointSequenceFromSwitchStructure(
    fittedSwitch: FittedSwitch,
    structureJointSequence: List<JointNumber>,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
): List<JointOnEdge>? {
    val middleJointNumbers = structureJointSequence.drop(1).dropLast(1)
    return jointsOnEdge
        .firstOrNull { jointOnEdge -> middleJointNumbers.contains(jointOnEdge.jointNumber) }
        ?.let { middleJoint ->
            completeJointSequenceWithMiddleJoint(fittedSwitch, structureJointSequence, edge, jointsOnEdge, middleJoint)
        }
}

private fun completeJointSequenceWithMiddleJoint(
    fittedSwitch: FittedSwitch,
    structureJointSequence: List<JointNumber>,
    edge: LayoutEdge,
    jointsOnEdge: List<JointOnEdge>,
    middleJoint: JointOnEdge,
): List<JointOnEdge>? {
    val newJoints =
        structureJointSequence.minus(jointsOnEdge.map { it.jointNumber }).mapNotNull { missingJointNumber ->
            proposeCandidateJoint(
                    structureJointSequence,
                    edge,
                    fittedSwitch.switchStructure,
                    middleJoint,
                    missingJointNumber,
                )
                .takeIf { candidate -> candidateJointLocationIsValid(edge, candidate, fittedSwitch) }
        }

    return (jointsOnEdge + newJoints)
        .sortedBy { it.mOnEdge }
        .takeIf { joints -> joints.map { it.jointNumber }.toSet() == structureJointSequence.toSet() }
}

private fun proposeCandidateJoint(
    jointSequence: List<JointNumber>,
    edge: LayoutEdge,
    switchStructure: SwitchStructure,
    middleJointOnEdge: JointOnEdge,
    missingJointNumber: JointNumber,
): JointOnEdge {
    val useReverseOrder = middleJointOnEdge.direction == RelativeDirection.Against
    val sortedJointSequence = if (useReverseOrder) jointSequence.reversed() else jointSequence
    val missingJointIsBeforeMiddleJoint =
        sortedJointSequence.indexOf(missingJointNumber) < sortedJointSequence.indexOf(middleJointOnEdge.jointNumber)
    val newJointLocationM = if (missingJointIsBeforeMiddleJoint) edge.start.m else edge.end.m
    return JointOnEdge(
        jointNumber = missingJointNumber,
        jointRole = SwitchJointRole.of(switchStructure, missingJointNumber),
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
