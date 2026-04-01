package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.DOWN
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.UP
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import fi.fta.geoviite.infra.tracklayout.VertexDirection.IN
import fi.fta.geoviite.infra.tracklayout.VertexDirection.OUT
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertNotNull

class RoutingTest {

    @Test
    fun `Switch RouteConnections can be reversed`() {
        val connection =
            RoutingConnection(
                from = SwitchJointVertex(IntId(123), JointNumber(1), OUT),
                to = SwitchJointVertex(IntId(456), JointNumber(3), IN),
                length = 123.456,
            )
        val reversed =
            RoutingConnection(
                from = SwitchJointVertex(IntId(456), JointNumber(3), OUT),
                to = SwitchJointVertex(IntId(123), JointNumber(1), IN),
                length = 123.456,
            )
        assertEquals(reversed, connection.reverse())
        assertEquals(connection, reversed.reverse())
        assertNotEquals(connection.hashCode(), reversed.hashCode())
        assertEquals(connection.hashCode(), reversed.reverse().hashCode())
        assertEquals(reversed.hashCode(), connection.reverse().hashCode())
    }

    @Test
    fun `Track RouteConnections can be reversed`() {
        val connection =
            RoutingConnection(
                from = TrackBoundaryVertex(IntId(123), END, OUT),
                to = TrackBoundaryVertex(IntId(456), START, IN),
                length = 0.0,
            )
        val reversed =
            RoutingConnection(
                from = TrackBoundaryVertex(IntId(456), START, OUT),
                to = TrackBoundaryVertex(IntId(123), END, IN),
                length = 0.0,
            )
        assertEquals(reversed, connection.reverse())
        assertEquals(connection, reversed.reverse())
        assertNotEquals(connection.hashCode(), reversed.hashCode())
        assertEquals(connection.hashCode(), reversed.reverse().hashCode())
        assertEquals(reversed.hashCode(), connection.reverse().hashCode())
    }

    @Test
    fun `SwitchInternalEdges can be reversed`() {
        val structure = switchStructureYV60_300_1_9()
        val edge = SwitchInternalEdge(RoutingSwitchAlignment(IntId(123), structure.alignments[0]), UP)
        val reversed = SwitchInternalEdge(RoutingSwitchAlignment(IntId(123), structure.alignments[0]), DOWN)
        assertEquals(reversed, edge.reverse())
        assertEquals(edge, reversed.reverse())
        assertNotEquals(edge.hashCode(), reversed.hashCode())
        assertEquals(edge.hashCode(), reversed.reverse().hashCode())
        assertEquals(reversed.hashCode(), edge.reverse().hashCode())
    }

    @Test
    fun `TrackEdges can be reversed`() {
        val edge = TrackEdge(IntId(123), UP)
        val reversed = TrackEdge(IntId(123), DOWN)
        assertEquals(reversed, edge.reverse())
        assertEquals(edge, reversed.reverse())
        assertNotEquals(edge.hashCode(), reversed.hashCode())
        assertEquals(edge.hashCode(), reversed.reverse().hashCode())
        assertEquals(reversed.hashCode(), edge.reverse().hashCode())
    }

    @Test
    fun `Switch routing vertices are created by structure correctly`() {
        val structureYv = switchStructureYV60_300_1_9()
        val structureRr = switchStructureRR54_4x1_9()
        val structuresMap = mapOf(structureYv.id to structureYv, structureRr.id to structureRr)

        // Switch joints etc don't matter: the connections are built by stucture

        val switchYv = switch(id = IntId(123), name = "TestSwitchYV", structureId = structureYv.id)
        assertEquals(
            listOf(
                jointVertex(123, 1, IN),
                jointVertex(123, 1, OUT),
                jointVertex(123, 2, IN),
                jointVertex(123, 2, OUT),
                jointVertex(123, 3, IN),
                jointVertex(123, 3, OUT),
            ),
            createSwitchVertices(switchYv, structuresMap),
        )

        val switchRr = switch(id = IntId(456), name = "TestSwitchRR", structureId = structureRr.id)
        assertEquals(
            listOf(
                jointVertex(456, 1, IN),
                jointVertex(456, 1, OUT),
                jointVertex(456, 2, IN),
                jointVertex(456, 2, OUT),
                jointVertex(456, 4, IN),
                jointVertex(456, 4, OUT),
                jointVertex(456, 3, IN),
                jointVertex(456, 3, OUT),
            ),
            createSwitchVertices(switchRr, structuresMap),
        )
    }

    @Test
    fun `Switch routing connections are created by structure correctly`() {
        val structureYv = switchStructureYV60_300_1_9()
        val structureRr = switchStructureRR54_4x1_9()
        val structuresMap = mapOf(structureYv.id to structureYv, structureRr.id to structureRr)

        // Switch joints etc don't matter: the connections are built by stucture

        val switchYv = switch(id = IntId(123), name = "TestSwitchYV", structureId = structureYv.id)
        val connectionsYv = createThroughSwitchConnections(switchYv, structuresMap)
        assertEquals(
            listOf(
                connectionThroughSwitch(123, 1, 2, structureYv.alignments[0].length()) to
                    edgeSwitch(123, UP, structureYv.alignments[0]),
                connectionThroughSwitch(123, 2, 1, structureYv.alignments[0].length()) to
                    edgeSwitch(123, DOWN, structureYv.alignments[0]),
                connectionThroughSwitch(123, 1, 3, structureYv.alignments[1].length()) to
                    edgeSwitch(123, UP, structureYv.alignments[1]),
                connectionThroughSwitch(123, 3, 1, structureYv.alignments[1].length()) to
                    edgeSwitch(123, DOWN, structureYv.alignments[1]),
            ),
            connectionsYv,
        )

        val switchRr = switch(id = IntId(456), name = "TestSwitchRR", structureId = structureRr.id)
        val connectionsRr = createThroughSwitchConnections(switchRr, structuresMap)
        assertEquals(
            listOf(
                connectionThroughSwitch(456, 1, 2, structureRr.alignments[0].length()) to
                    edgeSwitch(456, UP, structureRr.alignments[0]),
                connectionThroughSwitch(456, 2, 1, structureRr.alignments[0].length()) to
                    edgeSwitch(456, DOWN, structureRr.alignments[0]),
                connectionThroughSwitch(456, 4, 3, structureRr.alignments[1].length()) to
                    edgeSwitch(456, UP, structureRr.alignments[1]),
                connectionThroughSwitch(456, 3, 4, structureRr.alignments[1].length()) to
                    edgeSwitch(456, DOWN, structureRr.alignments[1]),
            ),
            connectionsRr,
        )
    }

    @Test
    fun `Direct connections are properly created for track end nodes`() {
        val trackStartNode = DbTrackBoundaryNode(IntId(123), TrackBoundary(IntId(456), START))
        createDirectConnections(trackStartNode).also { result ->
            assertEquals(1, result.size)
            assertEquals(directTrackConnection(456, START, 456, START), result[0].first)
            assertEquals(UP, result[0].second.direction)
        }

        val trackEndNode = DbTrackBoundaryNode(IntId(123), TrackBoundary(IntId(456), END))
        createDirectConnections(trackEndNode).also { result ->
            assertEquals(1, result.size)
            assertEquals(directTrackConnection(456, END, 456, END), result[0].first)
            assertEquals(UP, result[0].second.direction)
        }
    }

    @Test
    fun `Direct connections are properly created for double-track nodes`() {
        val trackLinkNode =
            DbTrackBoundaryNode(IntId(123), TrackBoundary(IntId(456), END), TrackBoundary(IntId(789), START))
        createDirectConnections(trackLinkNode).also { result ->
            assertEquals(2, result.size)
            assertEquals(directTrackConnection(456, END, 789, START), result[0].first)
            assertEquals(UP, result[0].second.direction)
            assertEquals(directTrackConnection(789, START, 456, END), result[1].first)
            assertEquals(DOWN, result[1].second.direction)
        }
    }

    @Test
    fun `Direct connections are properly created for double-switch nodes`() {
        val switchJointNode = DbSwitchNode(IntId(123), switchLinkYV(IntId(456), 1), switchLinkYV(IntId(789), 2))
        createDirectConnections(switchJointNode).also { result ->
            assertEquals(2, result.size)
            assertEquals(directSwitchConnection(456, 1, 789, 2), result[0].first)
            assertEquals(UP, result[0].second.direction)
            assertEquals(directSwitchConnection(789, 2, 456, 1), result[1].first)
            assertEquals(DOWN, result[1].second.direction)
        }
    }

    @Test
    fun `Direct connections are not created for single-switch nodes`() {
        val switchJointNode = DbSwitchNode(IntId(123), switchLinkYV(IntId(456), 1), null)
        createDirectConnections(switchJointNode).also { result ->
            assertEquals(emptyList<Pair<RoutingConnection, DirectConnectionEdge>>(), result)
        }
    }

    @Test
    fun `Track endpoint vertices are created for track endpoint nodes`() {
        val unconnectedGeom =
            DbLocationTrackGeometry(trackRowVersion = layoutRowVersion(456), edges = listOf(dbEdgeEndToEnd(123, 456)))
        assertEquals(
            listOf(
                trackBoundaryVertex(456, START, IN),
                trackBoundaryVertex(456, START, OUT),
                trackBoundaryVertex(456, END, IN),
                trackBoundaryVertex(456, END, OUT),
            ),
            createTrackEndVertices(unconnectedGeom),
        )
    }

    @Test
    fun `Track endpoint vertices are not created for switch connections (as they have separate processing)`() {
        val connectedGeom =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(456),
                edges = listOf(dbEdgeOuterSwitchConnector(789, 123, 1, 456, 1)),
            )
        assertEquals(emptyList<TrackBoundaryVertex>(), createTrackEndVertices(connectedGeom))
    }

    @Test
    fun `Track connections are created correctly for track sections between switches`() {
        val edge = dbEdgeOuterSwitchConnector(123, 111, 1, 222, 2)
        assertEquals(
            listOf(
                connectionBetweenSwitches(111, 1, 222, 2, edge.length.distance) to TrackEdge(IntId(123), UP),
                connectionBetweenSwitches(222, 2, 111, 1, edge.length.distance) to TrackEdge(IntId(123), DOWN),
            ),
            createTrackConnections(edge),
        )
    }

    @Test
    fun `Track connections are created correctly for track endpoints`() {
        val edge = dbEdgeEndToEnd(123, 456)
        assertEquals(
            listOf(
                throughTrackConnection(456, START, END, edge.length.distance) to TrackEdge(IntId(123), UP),
                throughTrackConnection(456, END, START, edge.length.distance) to TrackEdge(IntId(123), DOWN),
            ),
            createTrackConnections(edge),
        )
    }

    @Test
    fun `Track connections are not created for switch internal edges (they have a separate processing)`() {
        val edge = dbEdgeInnerSwitch(123, 456)
        assertEquals(emptyList<Pair<RoutingConnection, TrackEdge>>(), createTrackConnections(edge))
    }

    @Test
    fun `SwitchEdge toSwitchM converts edge M to switch alignment M in UP direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(10.0), LineM(30.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(10.0), edge.toSwitchM(LineM(0.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(20.0), edge.toSwitchM(LineM(10.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(30.0), edge.toSwitchM(LineM(20.0)))
    }

    @Test
    fun `SwitchEdge toSwitchM converts edge M to switch alignment M in DOWN direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(10.0), LineM(30.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(30.0), edge.toSwitchM(LineM(0.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(20.0), edge.toSwitchM(LineM(10.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(10.0), edge.toSwitchM(LineM(20.0)))
    }

    @Test
    fun `SwitchEdge toEdgeM converts switch alignment M to edge M in UP direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(10.0), LineM(30.0)))
        assertEquals(LineM<EdgeM>(0.0), edge.toEdgeM(LineM(10.0)))
        assertEquals(LineM<EdgeM>(10.0), edge.toEdgeM(LineM(20.0)))
        assertEquals(LineM<EdgeM>(20.0), edge.toEdgeM(LineM(30.0)))
    }

    @Test
    fun `SwitchEdge toEdgeM converts switch alignment M to edge M in DOWN direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(10.0), LineM(30.0)))
        assertEquals(LineM<EdgeM>(20.0), edge.toEdgeM(LineM(10.0)))
        assertEquals(LineM<EdgeM>(10.0), edge.toEdgeM(LineM(20.0)))
        assertEquals(LineM<EdgeM>(0.0), edge.toEdgeM(LineM(30.0)))
    }

    @Test
    fun `SwitchEdge toSwitchM and toEdgeM are inverse operations in UP direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(5.0), LineM(25.0)))
        listOf(0.0, 7.5, 10.0, 15.0, 20.0).forEach { distanceOnEdge ->
            val edgeM = LineM<EdgeM>(distanceOnEdge)
            assertEquals(edgeM, edge.toEdgeM(edge.toSwitchM(edgeM)))
        }
        listOf(5.0, 10.0, 15.0, 20.0, 25.0).forEach { distanceOnSwitch ->
            val switchM = LineM<SwitchStructureAlignmentM>(distanceOnSwitch)
            assertEquals(switchM, edge.toSwitchM(edge.toEdgeM(switchM)))
        }
    }

    @Test
    fun `SwitchEdge toSwitchM and toEdgeM are inverse operations in DOWN direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(5.0), LineM(25.0)))
        listOf(0.0, 7.5, 10.0, 15.0, 20.0).forEach { distanceOnEdge ->
            val edgeM = LineM<EdgeM>(distanceOnEdge)
            assertEquals(edgeM, edge.toEdgeM(edge.toSwitchM(edgeM)))
        }
        listOf(5.0, 10.0, 15.0, 20.0, 25.0).forEach { distanceOnSwitch ->
            val switchM = LineM<SwitchStructureAlignmentM>(distanceOnSwitch)
            assertEquals(switchM, edge.toSwitchM(edge.toEdgeM(switchM)))
        }
    }

    @Test
    fun `resolveSwitchAlignments returns empty for a non-switch edge`() {
        val (switches, structures) = switchAndStructureAsMaps(IntId(1))

        val edge = dbEdgeEndToEnd(100, 42)
        assertEquals(
            emptyMap<RoutingSwitchAlignment, SwitchEdge>(),
            resolveSwitchAlignments(edge, switches, structures),
        )
    }

    @Test
    fun `resolveSwitchAlignments returns empty for an outer switch connector edge`() {
        val (switches, structures) = switchAndStructureAsMaps(IntId(1))

        val edge = dbEdgeOuterSwitchConnector(100, 1, 1, 1, 2)
        assertEquals(
            emptyMap<RoutingSwitchAlignment, SwitchEdge>(),
            resolveSwitchAlignments(edge, switches, structures),
        )
    }

    @Test
    fun `resolveSwitchAlignments resolves correct switch alignment (UP) for edge that is a part of the alignment`() {
        val switchId: IntId<LayoutSwitch> = IntId(1)
        val (switches, structures) = switchAndStructureAsMaps(switchId)
        val structure = switchStructureYV60_300_1_9()

        // YV alignment[0] joints: [1, 5, 2] - test edge from joint 1 to joint 5
        // Partial switch connection from start to the middle
        val edge = dbEdgeSwitchInner(100, 1, 1, 5)
        val result = resolveSwitchAlignments(edge, switches, structures)

        val alignment = structure.alignments[0]
        val endM = structure.distance(JointNumber(1), JointNumber(5), alignment)
        val expectedMRange = Range(LineM<SwitchStructureAlignmentM>(0.0), LineM(endM))

        assertEquals(1, result.size)
        assertEquals(SwitchEdge(IntId(100), UP, expectedMRange), result[RoutingSwitchAlignment(switchId, alignment)])
    }

    @Test
    fun `resolveSwitchAlignments resolves correct switch alignment (DOWN) for edge that is a part of the alignment`() {
        val switchId: IntId<LayoutSwitch> = IntId(1)
        val (switches, structures) = switchAndStructureAsMaps(switchId)
        val structure = switchStructureYV60_300_1_9()

        // YV alignment[0] joints: [1, 5, 2] - test edge from joint 5 to joint 1
        // => partial switch connection, reversed, from the middle to the end
        val edge = dbEdgeSwitchInner(100, 1, 5, 1)
        val result = resolveSwitchAlignments(edge, switches, structures)

        val alignment = structure.alignments[0]
        val endM = structure.distance(JointNumber(1), JointNumber(5), alignment)
        val expectedMRange = Range(LineM<SwitchStructureAlignmentM>(0.0), LineM(endM))

        assertEquals(1, result.size)
        assertEquals(SwitchEdge(IntId(100), DOWN, expectedMRange), result[RoutingSwitchAlignment(switchId, alignment)])
    }

    @Test
    fun `resolveSwitchAlignments resolves the branching alignment for an inner edge using branch joints`() {
        val switchId: IntId<LayoutSwitch> = IntId(1)
        val (switches, structures) = switchAndStructureAsMaps(switchId)
        val structure = switchStructureYV60_300_1_9()

        // YV alignment[1] joints: [1, 3] — edge from joint 1 to joint 3
        val edge = dbEdgeSwitchInner(100, 1, 1, 3)
        val result = resolveSwitchAlignments(edge, switches, structures)

        val alignment = structure.alignments[1]
        val endM = structure.distance(JointNumber(1), JointNumber(3), alignment)
        val expectedMRange = Range(LineM<SwitchStructureAlignmentM>(0.0), LineM(endM))

        assertEquals(1, result.size)
        assertEquals(SwitchEdge(IntId(100), UP, expectedMRange), result[RoutingSwitchAlignment(switchId, alignment)])
    }

    @Test
    fun `A sane graph is created for a simple track layout`() {
        val structure1 = switchStructureYV60_300_1_9()
        val structure2 = switchStructureRR54_4x1_9()
        val structures = mapOf(structure1.id to structure1, structure2.id to structure2)

        val switch1 = switch(id = IntId(1), name = "Switch1", structureId = structure1.id)
        val switch2 = switch(id = IntId(2), name = "Switch2", structureId = structure2.id)

        // Build the following tracks
        //  Track1: Start at nowhere, connect to switch 1 joint 1, go through the switch and end at joint 2
        //  Track2 (Continuing): Start where track 1 ended (switch 1 joint 2) and continue to end at switch 2 joint 4
        //  Track3 (Branching): start at switch1 joint 1, go through the switch, continue a bit to end at nowhere

        val track1StartNode = dbTrackEndNode(10, 1, START)
        val switch1StartNode = dbSwitchNode(11, 1, 1)
        val switch1StraightEndNode = dbSwitchNode(12, 1, 2)
        val switch1BranchEndNode = dbSwitchNode(13, 1, 3)
        val switch2StartNode = dbSwitchNode(14, 2, 4)
        val track3EndNode = dbTrackEndNode(15, 3, END)

        val track1StartEdge =
            DbLayoutEdge(
                id = IntId(10000),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switch1StartNode),
                segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val switch1InnerMainEdge =
            DbLayoutEdge(
                id = IntId(10001),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1StraightEndNode),
                segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
            )
        val switch1InnerBranchEdge =
            DbLayoutEdge(
                id = IntId(10002),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1BranchEndNode),
                segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 50.0))),
            )
        val track2Edge =
            DbLayoutEdge(
                id = IntId(10003),
                startNode = DbNodeConnection(NodePortType.B, switch1StraightEndNode),
                endNode = DbNodeConnection(NodePortType.B, switch2StartNode),
                segments = listOf(segment(Point(200.0, 0.0), Point(300.0, 0.0))),
            )
        val track3EndEdge =
            DbLayoutEdge(
                id = IntId(10004),
                startNode = DbNodeConnection(NodePortType.B, switch1BranchEndNode),
                endNode = DbNodeConnection(NodePortType.A, track3EndNode),
                segments = listOf(segment(Point(200.0, 50.0), Point(300.0, 100.0))),
            )

        val trackGeom1 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(1),
                edges = listOf(track1StartEdge, switch1InnerMainEdge),
            )
        val trackGeom2 = DbLocationTrackGeometry(trackRowVersion = layoutRowVersion(2), edges = listOf(track2Edge))
        val trackGeom3 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(3),
                edges = listOf(switch1InnerBranchEdge, track3EndEdge),
            )

        val graph =
            buildGraph(
                trackGeoms = listOf(trackGeom1, trackGeom2, trackGeom3),
                switches = listOf(switch1, switch2),
                structures = structures,
            )

        assertEquals(
            listOf(
                    // The switch vertices should be all the outer point, according to structure (not geometry)
                    jointVertex(1, 1, IN),
                    jointVertex(1, 1, OUT),
                    jointVertex(1, 2, IN),
                    jointVertex(1, 2, OUT),
                    jointVertex(1, 3, IN),
                    jointVertex(1, 3, OUT),
                    jointVertex(2, 1, IN),
                    jointVertex(2, 1, OUT),
                    jointVertex(2, 2, IN),
                    jointVertex(2, 2, OUT),
                    jointVertex(2, 3, IN),
                    jointVertex(2, 3, OUT),
                    jointVertex(2, 4, IN),
                    jointVertex(2, 4, OUT),
                    // There should be vertices for starting/ending tracks
                    trackBoundaryVertex(1, START, IN),
                    trackBoundaryVertex(1, START, OUT),
                    trackBoundaryVertex(3, END, IN),
                    trackBoundaryVertex(3, END, OUT),
                )
                .sortedBy { it.toString() },
            graph.getVertices().sortedBy { it.toString() },
        )

        assertEquals(
            mapOf(
                    // Inner switch edges
                    edgeSwitch(1, UP, structure1.alignments[0]) to (jointVertex(1, 1, IN) to jointVertex(1, 2, OUT)),
                    edgeSwitch(1, DOWN, structure1.alignments[0]) to (jointVertex(1, 2, IN) to jointVertex(1, 1, OUT)),
                    edgeSwitch(1, UP, structure1.alignments[1]) to (jointVertex(1, 1, IN) to jointVertex(1, 3, OUT)),
                    edgeSwitch(1, DOWN, structure1.alignments[1]) to (jointVertex(1, 3, IN) to jointVertex(1, 1, OUT)),
                    edgeSwitch(2, UP, structure2.alignments[0]) to (jointVertex(2, 1, IN) to jointVertex(2, 2, OUT)),
                    edgeSwitch(2, DOWN, structure2.alignments[0]) to (jointVertex(2, 2, IN) to jointVertex(2, 1, OUT)),
                    edgeSwitch(2, UP, structure2.alignments[1]) to (jointVertex(2, 4, IN) to jointVertex(2, 3, OUT)),
                    edgeSwitch(2, DOWN, structure2.alignments[1]) to (jointVertex(2, 3, IN) to jointVertex(2, 4, OUT)),
                    // Direct return connection at track ends
                    DirectConnectionEdge(track1StartNode.id, UP) to
                        (trackBoundaryVertex(1, START, OUT) to trackBoundaryVertex(1, START, IN)),
                    DirectConnectionEdge(track3EndNode.id, UP) to
                        (trackBoundaryVertex(3, END, OUT) to trackBoundaryVertex(3, END, IN)),
                    // Track connections between nodes, excluding switch internal edges
                    TrackEdge(track1StartEdge.id, UP) to (trackBoundaryVertex(1, START, IN) to jointVertex(1, 1, IN)),
                    TrackEdge(track1StartEdge.id, DOWN) to
                        (jointVertex(1, 1, OUT) to trackBoundaryVertex(1, START, OUT)),
                    TrackEdge(track2Edge.id, UP) to (jointVertex(1, 2, OUT) to jointVertex(2, 4, IN)),
                    TrackEdge(track2Edge.id, DOWN) to (jointVertex(2, 4, OUT) to jointVertex(1, 2, IN)),
                    TrackEdge(track3EndEdge.id, UP) to (jointVertex(1, 3, OUT) to trackBoundaryVertex(3, END, OUT)),
                    TrackEdge(track3EndEdge.id, DOWN) to (trackBoundaryVertex(3, END, IN) to jointVertex(1, 3, IN)),
                )
                .entries
                .sortedBy { it.key.toString() },
            graph.getEdges().entries.sortedBy { it.key.toString() },
        )
    }

    @Test
    fun `Basic pathfinding works`() {
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switchId = IntId<LayoutSwitch>(1)
        val switch1 = switch(id = switchId, structureId = structure.id)

        // Build the following tracks
        //  Track1 (Straight): Start at nowhere, go through the switch and continue on to end at nowhere
        //  Track2 (Branching): start at switch1 joint 1, go through the switch and end at nowhere

        val track1StartNode = dbTrackEndNode(10, 111, START)
        val track1EndNode = dbTrackEndNode(11, 111, END)
        val switchStartNode = dbSwitchNode(12, 1, 1)
        val switchStraightMidNode = dbSwitchNode(13, 1, 5)
        val switchStraightEndNode = dbSwitchNode(14, 1, 2)
        val switchBranchEndNode = dbSwitchNode(15, 1, 3)
        val track2EndNode = dbTrackEndNode(16, 222, END)

        val track1Start = Point(0.0, 0.0)
        val joint1Location = Point(100.0, 0.0) + structure.getJointLocation(JointNumber(1))
        val joint5Location = Point(100.0, 0.0) + structure.getJointLocation(JointNumber(5))
        val joint2Location = Point(100.0, 0.0) + structure.getJointLocation(JointNumber(2))
        val joint3Location = Point(100.0, 0.0) + structure.getJointLocation(JointNumber(3))
        val track1End = joint2Location + Point(100.0, 0.0)
        val track2End = joint3Location + Point(100.0, 20.0)

        val track1StartEdge =
            DbLayoutEdge(
                id = IntId(10000),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switchStartNode),
                segments = listOf(segment(track1Start, joint1Location)),
            )
        val switchInnerMainEdge1 =
            DbLayoutEdge(
                id = IntId(10001),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchStraightMidNode),
                segments = listOf(segment(joint1Location, joint5Location)),
            )
        val switchInnerMainEdge2 =
            DbLayoutEdge(
                id = IntId(10002),
                startNode = DbNodeConnection(NodePortType.A, switchStraightMidNode),
                endNode = DbNodeConnection(NodePortType.A, switchStraightEndNode),
                segments = listOf(segment(joint5Location, joint2Location)),
            )
        val track1EndEdge =
            DbLayoutEdge(
                id = IntId(10003),
                startNode = DbNodeConnection(NodePortType.B, switchStraightEndNode),
                endNode = DbNodeConnection(NodePortType.A, track1EndNode),
                segments = listOf(segment(joint2Location, track1End)),
            )

        val switchInnerBranchEdge =
            DbLayoutEdge(
                id = IntId(10004),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchBranchEndNode),
                segments = listOf(segment(joint1Location, joint3Location)),
            )
        val track2EndEdge =
            DbLayoutEdge(
                id = IntId(10005),
                startNode = DbNodeConnection(NodePortType.B, switchBranchEndNode),
                endNode = DbNodeConnection(NodePortType.A, track2EndNode),
                segments = listOf(segment(joint3Location, track2End)),
            )

        val trackGeom1 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(111),
                edges = listOf(track1StartEdge, switchInnerMainEdge1, switchInnerMainEdge2, track1EndEdge),
            )
        val trackGeom2 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(222),
                edges = listOf(switchInnerBranchEdge, track2EndEdge),
            )

        val graph =
            buildGraph(trackGeoms = listOf(trackGeom1, trackGeom2), switches = listOf(switch1), structures = structures)
        val getRoute =
            {
                (from, fromTrack): Pair<Point, DbLocationTrackGeometry>,
                (to, toTrack): Pair<Point, DbLocationTrackGeometry> ->
                graph.findPath(trackCacheHit(fromTrack, from), trackCacheHit(toTrack, to))
            }

        // Verify various routing cases on the same graph

        // The closest points should be on the same spot of the track -> no actual route
        assertEquals(Route(emptyList()), getRoute(Point(10.0, 0.0) to trackGeom1, Point(10.0, 5.0) to trackGeom1))

        // On the same edge -> route should be found inside the edge (both ways)
        assertEquals(
            Route(listOf(routeSection(111, 10.0, 50.0, UP))),
            getRoute(Point(10.0, 0.0) to trackGeom1, Point(50.0, 5.0) to trackGeom1),
        )
        assertEquals(
            Route(listOf(routeSection(111, 10.0, 50.0, DOWN))),
            getRoute(Point(50.0, 0.0) to trackGeom1, Point(10.0, 5.0) to trackGeom1),
        )

        // Inside the same switch but on different edges -> route should be found within switch alignment
        ((joint1Location + Point(1.0, 0.0)) to (joint2Location - Point(1.0, 0.0))).let { (start, end) ->
            val startM = lineLength(track1Start, start)
            val endM = lineLength(track1Start, end)
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, UP))),
                getRoute(start to trackGeom1, end to trackGeom1),
            )
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, DOWN))),
                getRoute(end to trackGeom1, start to trackGeom1),
            )
        }

        // Inside the same switch but on different edges, while very short
        ((joint5Location - Point(1.0, 0.0)) to (joint5Location + Point(1.0, 0.0))).let { (start, end) ->
            val startM = lineLength(track1Start, start)
            val endM = lineLength(track1Start, end)
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, UP))),
                getRoute(start to trackGeom1, end to trackGeom1),
            )
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, DOWN))),
                getRoute(end to trackGeom1, start to trackGeom1),
            )
        }

        // Through track 1
        assertEquals(
            Route(listOf(routeSection(111, 0.0, trackGeom1.length.distance, UP))),
            getRoute(track1Start to trackGeom1, track1End to trackGeom1),
        )
        assertEquals(
            Route(listOf(routeSection(111, 0.0, trackGeom1.length.distance, DOWN))),
            getRoute(track1End to trackGeom1, track1Start to trackGeom1),
        )

        // From track 1 to track 2
        assertEquals(
            Route(listOf(routeSection(111, 0.0, 100.0, UP), routeSection(222, 0.0, trackGeom2.length.distance, UP))),
            getRoute(track1Start to trackGeom1, track2End to trackGeom2),
        )
        assertEquals(
            Route(
                listOf(routeSection(222, 0.0, trackGeom2.length.distance, DOWN), routeSection(111, 0.0, 100.0, DOWN))
            ),
            getRoute(track2End to trackGeom2, track1Start to trackGeom1),
        )

        // Start on track, end in switch
        (Point(50.0, 0.0) to (joint2Location - Point(1.0, 0.0))).let { (start, end) ->
            val startM = lineLength(track1Start, start)
            val endM = lineLength(track1Start, end)
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, UP))),
                getRoute(start to trackGeom1, end to trackGeom1),
            )
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, DOWN))),
                getRoute(end to trackGeom1, start to trackGeom1),
            )
        }

        // Start in switch, end on track
        ((joint1Location + Point(1.0, 0.0)) to track1End - Point(50.0, 0.0)).let { (start, end) ->
            val startM = lineLength(track1Start, start)
            val endM = lineLength(track1Start, end)
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, UP))),
                getRoute(start to trackGeom1, end to trackGeom1),
            )
            assertEquals(
                Route(listOf(routeSection(111, startM, endM, DOWN))),
                getRoute(end to trackGeom1, start to trackGeom1),
            )
        }
    }

    @Test
    fun `Routing does not throw when an edge has a broken switch linking`() {
        // While we no longer create these, some historical data has edges with broken switch linkings:
        //   Both ends connect to a switch as inner connection, but the switches are different. In effect,
        //   the edge claims to be part of the internal geometry of two different switches.
        // Such a switch is unroutable, but we don't want the graph building to fail as we know these exist
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switch1Id = IntId<LayoutSwitch>(1)
        val switch2Id = IntId<LayoutSwitch>(2)
        val switch1 = switch(id = switch1Id, structureId = structure.id)
        val switch2 = switch(id = switch2Id, structureId = structure.id)

        val trackStartNode = dbTrackEndNode(10, 111, START)
        val trackEndNode = dbTrackEndNode(11, 111, END)
        val switch1Node = dbSwitchNode(12, 1, 1)
        val switch2Node = dbSwitchNode(13, 2, 2)

        val trackStart = Point(0.0, 0.0)
        val switch1Location = Point(100.0, 0.0)
        val switch2Location = Point(200.0, 0.0)
        val trackEnd = Point(300.0, 0.0)

        val startEdge =
            DbLayoutEdge(
                id = IntId(10000),
                // Start at track start
                startNode = DbNodeConnection(NodePortType.A, trackStartNode),
                // Connect to switch1 as outer connection
                endNode = DbNodeConnection(NodePortType.B, switch1Node),
                segments = listOf(segment(trackStart, switch1Location)),
            )
        val brokenInnerSwitchEdge =
            DbLayoutEdge(
                id = IntId(10001),
                // Connect start to switch1 as inner connection
                startNode = DbNodeConnection(NodePortType.A, switch1Node),
                // Connect end to switch2 as inner connection
                endNode = DbNodeConnection(NodePortType.A, switch2Node),
                segments = listOf(segment(switch1Location, switch2Location)),
            )
        val endEdge =
            DbLayoutEdge(
                id = IntId(10002),
                // Start at switch2 as outer connection
                startNode = DbNodeConnection(NodePortType.B, switch2Node),
                // End at track end
                endNode = DbNodeConnection(NodePortType.A, trackEndNode),
                segments = listOf(segment(switch2Location, trackEnd)),
            )

        val trackGeom =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(111),
                edges = listOf(startEdge, brokenInnerSwitchEdge, endEdge),
            )

        val graph = assertDoesNotThrow { buildGraph(listOf(trackGeom), listOf(switch1, switch2), structures) }

        // The graph should still have vertices for the valid switches and track ends
        val vertices = graph.getVertices()
        assertEquals(4, vertices.filter { it is TrackBoundaryVertex }.size) {
            "Graph should contain in & out boundary vertices for track start and end (2 x 2)"
        }
        assertEquals(12, vertices.filter { it is SwitchJointVertex }.size) {
            "Graph should contain in & out joint vertices for both switches' connection points (2 x 2 x 3)"
        }

        // Routing over / from / to the switch won't work but shouldn't throw either
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(10.0, 0.0)), trackCacheHit(trackGeom, Point(290.0, 0.0)))
        )
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(150.0, 0.0)), trackCacheHit(trackGeom, Point(290.0, 0.0)))
        )
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(10.0, 0.0)), trackCacheHit(trackGeom, Point(150.0, 0.0)))
        )

        // Routing will work on the un-borked section
        assertNotNull(
            graph.findPath(trackCacheHit(trackGeom, Point(10.0, 0.0)), trackCacheHit(trackGeom, Point(90.0, 0.0)))
        )
    }

    @Test
    fun `Routing does not throw when a switch is deleted but tracks still reference it`() {
        // When a switch has stateCategory=NOT_EXISTING, RoutingService filters it out of the
        // switches list passed to buildGraph. But the track edges may still have nodes that
        // reference the deleted switch. The graph building/routing should handle this gracefully.
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switchId = IntId<LayoutSwitch>(1)
        val deletedSwitch =
            switch(id = switchId, structureId = structure.id, stateCategory = LayoutStateCategory.NOT_EXISTING)

        val trackStartNode = dbTrackEndNode(10, 111, START)
        val trackEndNode = dbTrackEndNode(11, 111, END)
        val switchStartNode = dbSwitchNode(12, 1, 1)
        val switchEndNode = dbSwitchNode(13, 1, 2)

        val trackStart = Point(0.0, 0.0)
        val switchLocation = Point(100.0, 0.0)
        val switchEndLocation = Point(200.0, 0.0)
        val trackEnd = Point(300.0, 0.0)

        val startEdge =
            DbLayoutEdge(
                id = IntId(10000),
                startNode = DbNodeConnection(NodePortType.A, trackStartNode),
                endNode = DbNodeConnection(NodePortType.B, switchStartNode),
                segments = listOf(segment(trackStart, switchLocation)),
            )
        val switchInnerEdge =
            DbLayoutEdge(
                id = IntId(10001),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchEndNode),
                segments = listOf(segment(switchLocation, switchEndLocation)),
            )
        val endEdge =
            DbLayoutEdge(
                id = IntId(10002),
                startNode = DbNodeConnection(NodePortType.B, switchEndNode),
                endNode = DbNodeConnection(NodePortType.A, trackEndNode),
                segments = listOf(segment(switchEndLocation, trackEnd)),
            )

        val trackGeom =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(111),
                edges = listOf(startEdge, switchInnerEdge, endEdge),
            )

        // Simulate RoutingService behavior: the deleted switch is filtered out
        val activeSwitches = listOf(deletedSwitch).filter { it.exists }

        val graph = assertDoesNotThrow { buildGraph(listOf(trackGeom), activeSwitches, structures) }

        // Routing over or from the deleted switch area won't work but should not throw
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(10.0, 0.0)), trackCacheHit(trackGeom, Point(290.0, 0.0)))
        )
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(150.0, 0.0)), trackCacheHit(trackGeom, Point(290.0, 0.0)))
        )
        assertNull(
            graph.findPath(trackCacheHit(trackGeom, Point(10.0, 0.0)), trackCacheHit(trackGeom, Point(150.0, 0.0)))
        )
    }

    @Test
    fun `Route length is correctly calculated in and over switches`() {
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switchId1 = IntId<LayoutSwitch>(1)
        val switch1 = switch(id = switchId1, structureId = structure.id)
        val switchId2 = IntId<LayoutSwitch>(2)
        val switch2 = switch(id = switchId2, structureId = structure.id)

        // Build the following tracks
        //  Track1: start -- switch1 (outer)
        //  Track2: switch1 (inner) -- switch2 (inner)
        //  Track3: switch2 (outer) -- end

        val track1StartNode = dbTrackEndNode(10, 111, START)
        val switch1StartNode = dbSwitchNode(11, 1, 1)
        val switch1EndNode = dbSwitchNode(12, 1, 2)
        val switch2StartNode = dbSwitchNode(13, 2, 1)
        val switch2EndNode = dbSwitchNode(14, 2, 2)
        val track3EndNode = dbTrackEndNode(15, 333, END)

        val track1Start = Point(0.0, 10.0)
        val switch1Joint1Location = Point(100.0, 10.0)
        val switch1Joint2Location = Point(100.0, 10.0) + structure.getJointLocation(JointNumber(2))
        val switch2Joint1Location = Point(200.0, 10.0) - structure.getJointLocation(JointNumber(2))
        val switch2Joint2Location = Point(200.0, 10.0)
        val track3End = Point(300.0, 10.0)

        val track1Edge =
            DbLayoutEdge(
                id = IntId(10000),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switch1StartNode),
                segments = listOf(segment(track1Start, switch1Joint1Location)),
            )
        val switch1InnerEdge =
            DbLayoutEdge(
                id = IntId(10001),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1EndNode),
                segments = listOf(segment(switch1Joint1Location, switch1Joint2Location)),
            )
        val track2MidEdge =
            DbLayoutEdge(
                id = IntId(10002),
                startNode = DbNodeConnection(NodePortType.B, switch1EndNode),
                endNode = DbNodeConnection(NodePortType.B, switch2StartNode),
                segments = listOf(segment(switch1Joint2Location, switch2Joint1Location)),
            )
        val switch2InnerEdge =
            DbLayoutEdge(
                id = IntId(10003),
                startNode = DbNodeConnection(NodePortType.A, switch2StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch2EndNode),
                segments = listOf(segment(switch2Joint1Location, switch2Joint2Location)),
            )
        val track3Edge =
            DbLayoutEdge(
                id = IntId(10004),
                startNode = DbNodeConnection(NodePortType.B, switch2EndNode),
                endNode = DbNodeConnection(NodePortType.A, track3EndNode),
                segments = listOf(segment(switch2Joint2Location, track3End)),
            )

        val trackGeom1 = DbLocationTrackGeometry(layoutRowVersion(111), listOf(track1Edge))
        val trackGeom2 =
            DbLocationTrackGeometry(layoutRowVersion(222), listOf(switch1InnerEdge, track2MidEdge, switch2InnerEdge))
        val trackGeom3 = DbLocationTrackGeometry(layoutRowVersion(333), listOf(track3Edge))

        val graph =
            buildGraph(
                trackGeoms = listOf(trackGeom1, trackGeom2, trackGeom3),
                switches = listOf(switch1, switch2),
                structures = structures,
            )
        val getLength =
            {
                (from, fromTrack): Pair<Point, DbLocationTrackGeometry>,
                (to, toTrack): Pair<Point, DbLocationTrackGeometry> ->
                graph.findPath(trackCacheHit(fromTrack, from), trackCacheHit(toTrack, to))?.totalLength
            }

        // Verify correct lengths on various routing cases on the same graph

        // Full length over all tracks
        assertEquals(300.0, getLength(track1Start to trackGeom1, track3End to trackGeom3))

        // From start to switch1 joint 1 should be 100, regardless of direction & track the cache-hits land on
        assertEquals(100.0, getLength(track1Start to trackGeom1, switch1Joint1Location to trackGeom1))
        assertEquals(100.0, getLength(track1Start to trackGeom1, switch1Joint1Location to trackGeom2))
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, track1Start to trackGeom1))
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, track1Start to trackGeom1))

        // From end to switch2 joint 2 should be 100, regardless of direction & track the cache-hits land on
        assertEquals(100.0, getLength(track3End to trackGeom3, switch2Joint2Location to trackGeom3))
        assertEquals(100.0, getLength(track3End to trackGeom3, switch2Joint2Location to trackGeom2))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, track3End to trackGeom3))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, track3End to trackGeom3))

        // From switch1 joint 1 to switch2 joint 2 should be 100, regardless of direction & track the cache-hits land on
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, switch2Joint2Location to trackGeom2))
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, switch2Joint2Location to trackGeom2))
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, switch2Joint2Location to trackGeom3))
        assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, switch2Joint2Location to trackGeom3))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, switch1Joint1Location to trackGeom2))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, switch1Joint1Location to trackGeom1))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, switch1Joint1Location to trackGeom2))
        assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, switch1Joint1Location to trackGeom1))

        // From switch1 joint 2 to switch2 joint 1 should be 100-2*[switch alignment length] ~ 31.14, regardless of
        // direction
        assertEquals(
            31.14,
            getLength(switch1Joint2Location to trackGeom2, switch2Joint1Location to trackGeom2)!!,
            LAYOUT_M_DELTA,
        )
        assertEquals(
            31.14,
            getLength(switch2Joint1Location to trackGeom2, switch1Joint2Location to trackGeom2)!!,
            LAYOUT_M_DELTA,
        )

        // From mid-track1 to mid-track2 should be 100, regardless of direction
        assertEquals(100.0, getLength(Point(50.0, 10.0) to trackGeom1, Point(150.0, 10.0) to trackGeom2))
        assertEquals(100.0, getLength(Point(150.0, 10.0) to trackGeom2, Point(50.0, 10.0) to trackGeom1))

        // From mid-track1 to mid-track3 should be 200, regardless of direction
        assertEquals(200.0, getLength(Point(50.0, 10.0) to trackGeom1, Point(250.0, 10.0) to trackGeom3))
        assertEquals(200.0, getLength(Point(250.0, 10.0) to trackGeom3, Point(50.0, 10.0) to trackGeom1))

        // From mid-track2 to mid-track3 should be 100, regardless of direction
        assertEquals(100.0, getLength(Point(150.0, 10.0) to trackGeom2, Point(250.0, 10.0) to trackGeom3))
        assertEquals(100.0, getLength(Point(250.0, 10.0) to trackGeom3, Point(150.0, 10.0) to trackGeom2))

        // From start to mid-switch1 (by 10m) should be 110, regardless of direction
        assertEquals(110.0, getLength(track1Start to trackGeom1, Point(110.0, 0.0) to trackGeom2))
        assertEquals(110.0, getLength(Point(110.0, 0.0) to trackGeom2, track1Start to trackGeom1))

        // From mid-switch1 (by 10m) to end should be 190, regardless of direction
        assertEquals(190.0, getLength(Point(110.0, 0.0) to trackGeom2, track3End to trackGeom3))
        assertEquals(190.0, getLength(track3End to trackGeom3, Point(110.0, 0.0) to trackGeom2))

        // From mid-switch2 (by 10m) to end should be 110, regardless of direction
        assertEquals(110.0, getLength(Point(190.0, 10.0) to trackGeom2, track3End to trackGeom3))
        assertEquals(110.0, getLength(track3End to trackGeom3, Point(190.0, 10.0) to trackGeom2))

        // From start to mid-switch2 (by 10m) should be 190, regardless of direction
        assertEquals(190.0, getLength(track1Start to trackGeom1, Point(190.0, 10.0) to trackGeom2))
        assertEquals(190.0, getLength(Point(190.0, 10.0) to trackGeom2, track1Start to trackGeom1))

        // From mid-switch1 (by 10m) to mid-switch2 (by 10m) should be 80, regardless of direction
        assertEquals(80.0, getLength(Point(110.0, 0.0) to trackGeom2, Point(190.0, 10.0) to trackGeom2))
        assertEquals(80.0, getLength(Point(190.0, 10.0) to trackGeom2, Point(110.0, 0.0) to trackGeom2))
    }
}

private fun routeSection(trackId: Int, startM: Double, endM: Double, direction: EdgeDirection) =
    RouteSection(IntId(trackId), Range(LineM(startM), LineM(endM)), direction)

private fun trackCacheHit(geometry: DbLocationTrackGeometry, target: Point): LocationTrackCacheHit =
    geometry.getClosestPoint(target)!!.first.let { closest ->
        LocationTrackCacheHit(
            track = locationTrack(IntId(1), id = geometry.trackId),
            geometry = geometry,
            closestPoint = closest,
            distance = lineLength(target, closest),
        )
    }

private fun edgeSwitch(switchId: Int, direction: EdgeDirection, alignment: SwitchStructureAlignment) =
    SwitchInternalEdge(RoutingSwitchAlignment(IntId(switchId), alignment), direction)

private fun directTrackConnection(
    fromTrackId: Int,
    fromBoundary: TrackBoundaryType,
    toTrackId: Int,
    toBoundary: TrackBoundaryType,
) =
    RoutingConnection(
        from = trackBoundaryVertex(fromTrackId, fromBoundary, OUT),
        to = trackBoundaryVertex(toTrackId, toBoundary, IN),
        length = 0.0,
    )

private fun throughTrackConnection(
    trackId: Int,
    fromBoundary: TrackBoundaryType,
    toBoundary: TrackBoundaryType,
    length: Double,
) =
    RoutingConnection(
        from = trackBoundaryVertex(trackId, fromBoundary, IN),
        to = trackBoundaryVertex(trackId, toBoundary, OUT),
        length = length,
    )

private fun directSwitchConnection(fromSwitchId: Int, fromJoint: Int, toSwitchId: Int, toJoint: Int) =
    RoutingConnection(
        from = jointVertex(fromSwitchId, fromJoint, OUT),
        to = jointVertex(toSwitchId, toJoint, IN),
        length = 0.0,
    )

private fun connectionThroughSwitch(switchId: Int, fromJoint: Int, toJoint: Int, length: Double) =
    RoutingConnection(
        from = jointVertex(switchId, fromJoint, IN),
        to = jointVertex(switchId, toJoint, OUT),
        length = length,
    )

private fun connectionBetweenSwitches(
    fromSwitchId: Int,
    fromJoint: Int,
    toSwitchId: Int,
    toJoint: Int,
    length: Double,
) =
    RoutingConnection(
        from = jointVertex(fromSwitchId, fromJoint, OUT),
        to = jointVertex(toSwitchId, toJoint, IN),
        length = length,
    )

private fun jointVertex(switchId: Int, joint: Int, direction: VertexDirection) =
    SwitchJointVertex(IntId(switchId), JointNumber(joint), direction)

private fun trackBoundaryVertex(trackId: Int, boundary: TrackBoundaryType, direction: VertexDirection) =
    TrackBoundaryVertex(IntId(trackId), boundary, direction)

private fun dbEdgeSwitchInner(edgeId: Int, switchId: Int, startJoint: Int, endJoint: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.A, dbSwitchNode(100, switchId, startJoint)),
        endNode = DbNodeConnection(NodePortType.A, dbSwitchNode(200, switchId, endJoint)),
        segments = listOf(someSegment()),
    )

private fun dbEdgeOuterSwitchConnector(
    edgeId: Int,
    fromSwitchId: Int,
    fromSwitchJoint: Int,
    toSwitchId: Int,
    toSwitchJoint: Int,
) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.B, dbSwitchNode(123456, fromSwitchId, fromSwitchJoint)),
        endNode = DbNodeConnection(NodePortType.B, dbSwitchNode(654321, toSwitchId, toSwitchJoint)),
        segments = listOf(someSegment()),
    )

private fun dbEdgeInnerSwitch(edgeId: Int, switchId: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.A, dbSwitchNode(123456, switchId, 1)),
        endNode = DbNodeConnection(NodePortType.A, dbSwitchNode(654321, switchId, 2)),
        segments = listOf(someSegment()),
    )

private fun dbEdgeEndToEnd(edgeId: Int, trackId: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.A, dbTrackEndNode(123456, trackId, START)),
        endNode = DbNodeConnection(NodePortType.A, dbTrackEndNode(654321, trackId, END)),
        segments = listOf(someSegment()),
    )

private fun dbSwitchNode(nodeId: Int, switchId: Int, joint: Int) =
    DbSwitchNode(IntId(nodeId), switchLinkYV(IntId(switchId), joint), null)

private fun dbTrackEndNode(nodeId: Int, trackId: Int, type: TrackBoundaryType) =
    DbTrackBoundaryNode(IntId(nodeId), TrackBoundary(IntId(trackId), type))

private fun <T : LayoutAsset<T>> layoutRowVersion(id: Int): LayoutRowVersion<T> =
    LayoutRowVersion(LayoutRowId(IntId(id), LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)), 1)

private fun switchAndStructureAsMaps(
    id: IntId<LayoutSwitch>
): Pair<Map<IntId<LayoutSwitch>, LayoutSwitch>, Map<IntId<SwitchStructure>, SwitchStructure>> {
    val structure = switchStructureYV60_300_1_9()
    return mapOf(id to switch(id = id, structureId = structure.id)) to mapOf(structure.id to structure)
}
