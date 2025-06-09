package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.LinkableSwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.switchConnectivity
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.TOPOLOGY_CALC_DISTANCE
import fi.fta.geoviite.infra.util.mapNonNullValues
import kotlin.math.absoluteValue

fun matchFittedSwitchToTracks(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
    layoutSwitchId: IntId<LayoutSwitch>?,
): SuggestedSwitch {
    require(layoutSwitchId == null || clearedTracks.values.none { it.second.containsSwitch(layoutSwitchId) }) {
        "Must clear switch from tracks before calling matchFittedSwitchToTracks on it"
    }
    val jointsOnEdges = mapFittedSwitchToEdges(fittedSwitch, clearedTracks)
    val adjustedJointsOnEdges = completeIncompleteJointSequences(fittedSwitch, jointsOnEdges, clearedTracks)
    val extraTopologyLinks = findTopologyLinksToUnmatchedNearbyTracks(fittedSwitch, clearedTracks)
    val bestLinks = pickBestLinkingByTrack(adjustedJointsOnEdges + extraTopologyLinks, jointsOnEdges)

    val linkedTracks = suggestDelinking(clearedTracks) + suggestLinking(bestLinks, clearedTracks)

    return SuggestedSwitch(
        fittedSwitch.joints.map {
            LayoutSwitchJoint(
                it.number,
                SwitchJointRole.of(fittedSwitch.switchStructure, it.number),
                it.location,
                it.locationAccuracy,
            )
        },
        linkedTracks,
    )
}

private fun findTopologyLinksToUnmatchedNearbyTracks(
    fittedSwitch: FittedSwitch,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): Map<EdgeId, List<List<JointOnEdge>>> =
    clearedTracks
        .filterKeys { trackId ->
            fittedSwitch.joints.none { joint -> joint.matches.any { match -> match.locationTrackId == trackId } }
        }
        .flatMap { (trackId, track) ->
            val (_, geometry) = track
            listOfNotNull(geometry.start?.let { 0 to it }, geometry.end?.let { geometry.edges.lastIndex to it })
                .flatMap { (trackEndEdgeIndex, trackEndPoint) ->
                    findTopologyLinksToTrackEnd(
                        fittedSwitch,
                        trackEndPoint,
                        trackId,
                        trackEndEdgeIndex,
                        geometry.edgeMs[trackEndEdgeIndex].min,
                    )
                }
        }
        .associate { (trackId, joint) -> trackId to listOf(listOf(joint)) }

private fun findTopologyLinksToTrackEnd(
    fittedSwitch: FittedSwitch,
    trackEndPoint: AlignmentPoint,
    trackId: IntId<LocationTrack>,
    trackEndEdgeIndex: Int,
    trackEndEdgeStartM: Double,
): List<Pair<EdgeId, JointOnEdge>> =
    fittedSwitch.joints.mapNotNull { fittedSwitchJoint ->
        if (lineLength(fittedSwitchJoint.location, trackEndPoint) > TOPOLOGY_CALC_DISTANCE) null
        else {
            val edgeId = EdgeId(trackId, trackEndEdgeIndex)
            val joint = asTopologyJointOnEdge(fittedSwitchJoint, fittedSwitch, trackEndPoint, trackEndEdgeStartM)
            edgeId to joint
        }
    }

private fun asTopologyJointOnEdge(
    fittedSwitchJoint: FittedSwitchJoint,
    fittedSwitch: FittedSwitch,
    trackEndPoint: AlignmentPoint,
    trackEndEdgeStartM: Double,
): JointOnEdge =
    JointOnEdge(
        fittedSwitchJoint.number,
        SwitchJointRole.of(fittedSwitch.switchStructure, fittedSwitchJoint.number),
        trackEndPoint.m - trackEndEdgeStartM,
        RelativeDirection.Along,
        trackEndPoint.toPoint(),
    )

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
            mOnEdge = match.mOnTrack - edgeMRange.min,
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

        val sequenceAsValidTopologicalLink =
            jointSequenceAsValidTopologyLink(fittedSwitch, edgeId, jointsFromSwitchFit, clearedTracks)
        val validStructureAlignmentsForSequence =
            structureAlignments.filter { structureAlignment ->
                isValidInnerJointLinkSequence(structureAlignment, jointsFromSwitchFit)
            }
        listOfNotNull(
                sequenceAsValidTopologicalLink?.let(::listOf)?.let(::listOf),
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

private fun jointSequenceAsValidTopologyLink(
    fittedSwitch: FittedSwitch,
    edgeId: EdgeId,
    jointSequence: List<JointOnEdge>,
    clearedTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LocationTrackGeometry>>,
): JointOnEdge? =
    if (jointSequence.size != 1 || jointSequence[0].jointRole == SwitchJointRole.MATH) {
        null
    } else {
        val singleJoint = jointSequence[0]
        val edge = getEdge(edgeId, clearedTracks)
        val point = edge.getPointAtM(singleJoint.mOnEdge)
        val switchJoint = fittedSwitch.joints.find { it.number == singleJoint.jointNumber }
        if (
            point != null &&
                switchJoint != null &&
                (singleJoint.mOnEdge < TOPOLOGY_CALC_DISTANCE ||
                    edge.end.m - singleJoint.mOnEdge < TOPOLOGY_CALC_DISTANCE) &&
                lineLength(point, switchJoint.location) < TOPOLOGY_CALC_DISTANCE
        ) {
            val isStart = singleJoint.mOnEdge < TOPOLOGY_CALC_DISTANCE
            singleJoint.copy(
                mOnEdge = if (isStart) 0.0 else edge.end.m,
                location = if (isStart) edge.start.toPoint() else edge.end.toPoint(),
            )
        } else null
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
            innerJointM < MAX_INNER_JOINT_CHECK_SNAP_DISTANCE ||
            (innerJointM - getEdge(edgeId, nearbyTracks).end.m).absoluteValue < MAX_INNER_JOINT_CHECK_SNAP_DISTANCE
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
