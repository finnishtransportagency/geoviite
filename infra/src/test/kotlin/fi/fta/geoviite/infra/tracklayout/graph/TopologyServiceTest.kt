package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.DbLayoutEdge
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.DbNodeConnection
import fi.fta.geoviite.infra.tracklayout.DbSwitchNode
import fi.fta.geoviite.infra.tracklayout.DbTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.NodePortType
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection.ASCENDING
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection.DESCENDING
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologyServiceTest {

    @Test
    fun `Switch transitions match every switch library alignment`() {
        switchStructures.forEach { structureData ->
            val structureId = IntId<SwitchStructure>(1)
            val structure = SwitchStructure(RowVersion(structureId, 1), structureData)
            val mainJoint = structure.presentationJointNumber
            val switchJoints = structure.joints.map { joint -> switchJoint(joint.number.intValue, joint.location) }
            val layoutSwitch = switch(id = SWITCH_ID, structureId = structureId, joints = switchJoints)
            val switchRef = IdSwitchRef(SWITCH_ID)

            val nano =
                createNanoTopology(
                    tracks = buildSwitchTracks(structure, splitAtMainJoint = true),
                    switches = mapOf(SWITCH_ID to layoutSwitch),
                    structures = mapOf(structureId to structure),
                    switchRefs = mapOf(SWITCH_ID to switchRef),
                )

            (structure.endJointNumbers + mainJoint).forEach { joint ->
                val node = nano.nodes.single { n -> n.switchReferences.any { ref -> ref.switch.jointNumber == joint } }
                val actual =
                    node.transitions.map { transition ->
                        GeneratedTransition(
                            incomingEdge = transition.incomingEdge.edge.id,
                            incomingDirection = transition.incomingEdge.direction,
                            outgoingEdge = transition.outgoingEdge.edge.id,
                            outgoingDirection = transition.outgoingEdge.direction,
                        )
                    }
                val expected = expectedTransitions(structure, switchRef, joint)

                assertEquals(
                    actual,
                    actual.distinct(),
                    "Duplicate topology transitions for switch type ${structure.type}, joint $joint",
                )
                assertEquals(
                    expected,
                    actual.toSet(),
                    "Unexpected topology transitions for switch type ${structure.type}, joint $joint",
                )
            }
        }
    }

    @Test
    fun `Partially linked crossing switch keeps only linked joints in nano and retains main node in micro`() {
        val rrData = switchStructures.first { structure -> structure.baseType.name == "RR" }
        val structureId = IntId<SwitchStructure>(1)
        val structure = SwitchStructure(RowVersion(structureId, 1), rrData)
        val mainJoint = structure.presentationJointNumber
        val switchJoints = structure.joints.map { joint -> switchJoint(joint.number.intValue, joint.location) }
        val layoutSwitch = switch(id = SWITCH_ID, structureId = structureId, joints = switchJoints)
        val switchRef = IdSwitchRef(SWITCH_ID)

        // Link only the 1-5-2 alignment (joints 3 and 4 are unlinked), and continue externally only behind joint 1
        // (joint 2 is a dead-end connection joint with no external track).
        val node1 =
            DbSwitchNode(
                id = IntId(101),
                portA = SwitchLink(SWITCH_ID, SwitchJointRole.CONNECTION, JointNumber(1)),
                portB = null,
                extId = uuid("switch-node-1"),
            )
        val node2 =
            DbSwitchNode(
                id = IntId(102),
                portA = SwitchLink(SWITCH_ID, SwitchJointRole.CONNECTION, JointNumber(2)),
                portB = null,
                extId = uuid("switch-node-2"),
            )
        val boundary1 =
            DbTrackBoundaryNode(
                id = IntId(201),
                portA = TrackBoundary(IntId<LocationTrack>(1), START),
                portB = null,
                extId = uuid("track-end-1"),
            )
        val loc1 = structure.getJointLocation(JointNumber(1))
        val loc2 = structure.getJointLocation(JointNumber(2))
        val endLoc1 = Point(loc1.x - 100.0, loc1.y)

        val track1Geom =
            DbLocationTrackGeometry(
                trackRowVersion =
                    LayoutRowVersion(
                        LayoutRowId(IntId(1), LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)),
                        1,
                    ),
                edges =
                    listOf(
                        DbLayoutEdge(
                            id = IntId(1001),
                            extId = uuid("external-1"),
                            startNode = DbNodeConnection(NodePortType.A, boundary1),
                            endNode = DbNodeConnection(NodePortType.B, node1),
                            segments = listOf(segment(endLoc1, loc1)),
                        ),
                        DbLayoutEdge(
                            id = IntId(1002),
                            extId = uuid("inner-1-2"),
                            startNode = DbNodeConnection(NodePortType.A, node1),
                            endNode = DbNodeConnection(NodePortType.A, node2),
                            segments = listOf(segment(loc1, loc2)),
                        ),
                    ),
            )
        val nano =
            createNanoTopology(
                tracks = listOf(locationTrack(IntId(1), id = IntId(1)) to track1Geom),
                switches = mapOf(SWITCH_ID to layoutSwitch),
                structures = mapOf(structureId to structure),
                switchRefs = mapOf(SWITCH_ID to switchRef),
            )
        val micro = CachedTopology(nano).micro

        // Nano includes only the linked joints (1, 2) and the synthesized main joint (5)
        assertEquals(
            setOf(JointNumber(1), JointNumber(2), mainJoint),
            nano.nodes.flatMap { node -> node.switchReferences.map { ref -> ref.switch.jointNumber } }.toSet(),
        )
        assertEquals(
            setOf(
                switchLineUuid(switchRef, listOf(mainJoint, JointNumber(1))),
                switchLineUuid(switchRef, listOf(mainJoint, JointNumber(2))),
            ),
            nano.edges
                .filter { edge -> edge.routingReference is SwitchAlignmentRoutingReference }
                .map(TopologyEdge::id)
                .toSet(),
        )

        // Micro collapses joint 1 (degree 2 between external-1 and leg 5-1), keeps main joint 5, and keeps joint 2
        // (degree 1 dead-end connection joint)
        assertEquals(
            setOf(mainJoint, JointNumber(2)),
            micro.nodes.flatMap { node -> node.switchReferences.map { ref -> ref.switch.jointNumber } }.toSet(),
        )
    }

    @Test
    fun `Ambiguous switch joint is omitted from nano and micro topology`() {
        val structureId = IntId<SwitchStructure>(1)
        val structure = SwitchStructure(RowVersion(structureId, 1), switchStructures.first { it.baseType.name == "YV" })
        val switchJoints = structure.joints.map { joint -> switchJoint(joint.number.intValue, joint.location) }
        val layoutSwitch = switch(id = SWITCH_ID, structureId = structureId, joints = switchJoints)
        val switchRef = IdSwitchRef(SWITCH_ID)
        val tracks = buildSwitchTracks(structure, splitAtMainJoint = true)
        val duplicateTrackId = IntId<LocationTrack>(999)
        val duplicateTrack = locationTrack(IntId(1), id = duplicateTrackId)
        val ambiguousJoint = structure.presentationJointNumber
        val duplicateNode =
            DbSwitchNode(
                id = IntId(999),
                portA =
                    SwitchLink(
                        SWITCH_ID,
                        SwitchJointRole.MAIN,
                        ambiguousJoint,
                    ),
                portB = null,
                extId = uuid("duplicate-switch-joint"),
            )
        val duplicateBoundary =
            DbTrackBoundaryNode(
                id = IntId(998),
                portA = TrackBoundary(duplicateTrackId, START),
                portB = null,
                extId = uuid("duplicate-track-end"),
            )
        val jointLocation = structure.getJointLocation(ambiguousJoint)
        val duplicateEdge =
            DbLayoutEdge(
                id = IntId(999),
                extId = uuid("duplicate-external-edge"),
                startNode = DbNodeConnection(NodePortType.A, duplicateBoundary),
                endNode = DbNodeConnection(NodePortType.B, duplicateNode),
                segments = listOf(segment(Point(jointLocation.x - 100.0, jointLocation.y), jointLocation)),
            )

        val nano =
            createNanoTopology(
                tracks =
                    tracks +
                        (duplicateTrack to
                            DbLocationTrackGeometry(
                                trackRowVersion =
                                    LayoutRowVersion(
                                        LayoutRowId(
                                            duplicateTrackId,
                                            LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL),
                                        ),
                                        1,
                                    ),
                                edges = listOf(duplicateEdge),
                            )),
                switches = mapOf(SWITCH_ID to layoutSwitch),
                structures = mapOf(structureId to structure),
                switchRefs = mapOf(SWITCH_ID to switchRef),
            )
        val micro = CachedTopology(nano).micro
        listOf(nano, micro).forEach { topology ->
            assertTrue(
                topology.nodes.none { node ->
                    node.switchReferences.any { reference -> reference.switch.jointNumber == ambiguousJoint }
                }
            )
        }
    }

    private fun buildSwitchTracks(
        structure: SwitchStructure,
        splitAtMainJoint: Boolean,
    ): List<Pair<LocationTrack, DbLocationTrackGeometry>> {
        val mainJoint = structure.presentationJointNumber
        val switchNodes =
            (structure.endJointNumbers + mainJoint).associateWith { joint ->
                val role = if (joint == mainJoint) SwitchJointRole.MAIN else SwitchJointRole.CONNECTION
                DbSwitchNode(
                    id = IntId(100 + joint.intValue),
                    portA = SwitchLink(SWITCH_ID, role, joint),
                    portB = null,
                    extId = uuid("switch-node-${joint.intValue}"),
                )
            }
        var nextEdgeId = 1000
        val externalTracks =
            structure.endJointNumbers.sorted().mapIndexed { index, joint ->
                val trackId = IntId<LocationTrack>(index + 1)
                val boundaryNode =
                    DbTrackBoundaryNode(
                        id = IntId(200 + joint.intValue),
                        portA = TrackBoundary(trackId, START),
                        portB = null,
                        extId = uuid("track-end-${joint.intValue}"),
                    )
                val jointLoc = structure.getJointLocation(joint)
                val endLoc = Point(jointLoc.x * 2.0, jointLoc.y * 2.0 + 100.0)
                val edge =
                    DbLayoutEdge(
                        id = IntId(nextEdgeId++),
                        extId = uuid("external-${joint.intValue}"),
                        startNode = DbNodeConnection(NodePortType.A, boundaryNode),
                        endNode = DbNodeConnection(NodePortType.B, switchNodes.getValue(joint)),
                        segments = listOf(segment(endLoc, jointLoc)),
                    )
                val geom =
                    DbLocationTrackGeometry(
                        trackRowVersion =
                            LayoutRowVersion(
                                LayoutRowId(trackId, LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)),
                                1,
                            ),
                        edges = listOf(edge),
                    )
                locationTrack(IntId(1), id = trackId) to geom
            }
        val alignmentTracks =
            structure.alignments.mapIndexed { index, alignment ->
                val trackId = IntId<LocationTrack>(10 + index)
                val firstJoint = alignment.jointNumbers.first()
                val lastJoint = alignment.jointNumbers.last()
                val edges =
                    if (
                        splitAtMainJoint &&
                            mainJoint in alignment.jointNumbers &&
                            mainJoint !in structure.endJointNumbers
                    ) {
                        listOf(
                            DbLayoutEdge(
                                id = IntId(nextEdgeId++),
                                extId = uuid("inner-$index-1"),
                                startNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(firstJoint)),
                                endNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(mainJoint)),
                                segments =
                                    listOf(
                                        segment(
                                            structure.getJointLocation(firstJoint),
                                            structure.getJointLocation(mainJoint),
                                        )
                                    ),
                            ),
                            DbLayoutEdge(
                                id = IntId(nextEdgeId++),
                                extId = uuid("inner-$index-2"),
                                startNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(mainJoint)),
                                endNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(lastJoint)),
                                segments =
                                    listOf(
                                        segment(
                                            structure.getJointLocation(mainJoint),
                                            structure.getJointLocation(lastJoint),
                                        )
                                    ),
                            ),
                        )
                    } else {
                        listOf(
                            DbLayoutEdge(
                                id = IntId(nextEdgeId++),
                                extId = uuid("inner-$index-full"),
                                startNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(firstJoint)),
                                endNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(lastJoint)),
                                segments =
                                    listOf(
                                        segment(
                                            structure.getJointLocation(firstJoint),
                                            structure.getJointLocation(lastJoint),
                                        )
                                    ),
                            )
                        )
                    }
                val geom =
                    DbLocationTrackGeometry(
                        trackRowVersion =
                            LayoutRowVersion(
                                LayoutRowId(trackId, LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)),
                                1,
                            ),
                        edges = edges,
                    )
                locationTrack(IntId(1), id = trackId) to geom
            }
        return externalTracks + alignmentTracks
    }

    private fun expectedTransitions(
        structure: SwitchStructure,
        switchRef: SwitchRef,
        joint: JointNumber,
    ): Set<GeneratedTransition> {
        val mainJoint = structure.presentationJointNumber
        return if (joint == mainJoint && mainJoint !in structure.endJointNumbers) {
            structure.alignments
                .flatMap { alignment ->
                    val firstLeg = switchLineUuid(switchRef, listOf(mainJoint, alignment.jointNumbers.first()))
                    val lastLeg = switchLineUuid(switchRef, listOf(mainJoint, alignment.jointNumbers.last()))
                    listOf(
                        GeneratedTransition(
                            incomingEdge = firstLeg,
                            incomingDirection = DESCENDING,
                            outgoingEdge = lastLeg,
                            outgoingDirection = ASCENDING,
                        ),
                        GeneratedTransition(
                            incomingEdge = lastLeg,
                            incomingDirection = DESCENDING,
                            outgoingEdge = firstLeg,
                            outgoingDirection = ASCENDING,
                        ),
                    )
                }
                .toSet()
        } else {
            val externalEdgeId = uuid("external-${joint.intValue}")
            val internalConnections =
                if (mainJoint !in structure.endJointNumbers) {
                    listOf(switchLineUuid(switchRef, listOf(mainJoint, joint)) to EdgeEndpoint.END)
                } else {
                    structure.alignments.mapNotNull { alignment ->
                        val lineId = switchLineUuid(switchRef, alignment.jointNumbers)
                        when (joint) {
                            alignment.jointNumbers.first() -> lineId to EdgeEndpoint.START
                            alignment.jointNumbers.last() -> lineId to EdgeEndpoint.END
                            else -> null
                        }
                    }
                }
            internalConnections
                .flatMap { (internalEdgeId, endpoint) ->
                    val internalIncoming = if (endpoint == EdgeEndpoint.START) DESCENDING else ASCENDING
                    val internalOutgoing = internalIncoming.reverse()
                    listOf(
                        GeneratedTransition(
                            incomingEdge = externalEdgeId,
                            incomingDirection = ASCENDING,
                            outgoingEdge = internalEdgeId,
                            outgoingDirection = internalOutgoing,
                        ),
                        GeneratedTransition(
                            incomingEdge = internalEdgeId,
                            incomingDirection = internalIncoming,
                            outgoingEdge = externalEdgeId,
                            outgoingDirection = DESCENDING,
                        ),
                    )
                }
                .toSet()
        }
    }

    private fun uuid(value: String): UUID = UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8))

    companion object {
        private val SWITCH_ID = IntId<LayoutSwitch>(1)
    }
}
