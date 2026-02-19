package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.DOWN
import fi.fta.geoviite.infra.tracklayout.EdgeDirection.UP
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import fi.fta.geoviite.infra.tracklayout.VertexDirection.IN
import fi.fta.geoviite.infra.tracklayout.VertexDirection.OUT
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class RoutingTest {

    @Test
    fun `Switch RouteConnections can be reversed`() {
        val connection = RoutingConnection(
            from = SwitchJointVertex(IntId(123), JointNumber(1), OUT),
            to = SwitchJointVertex(IntId(456), JointNumber(3), IN),
            length = 123.456,
        )
        val reversed = RoutingConnection(
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
        val connection = RoutingConnection(
            from = TrackBoundaryVertex(IntId(123), END, OUT),
            to = TrackBoundaryVertex(IntId(456), START, IN),
            length = 0.0,
        )
        val reversed = RoutingConnection(
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
        val edge = SwitchInternalEdge(
            IntId(123),
            listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
            UP,
        )
        val reversed = SwitchInternalEdge(
            IntId(123),
            listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
            DOWN,
        )
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
        assertEquals(listOf(
            jointVertex(123, 1, IN),
            jointVertex(123, 1, OUT),
            jointVertex(123, 2, IN),
            jointVertex(123, 2, OUT),
            jointVertex(123, 3, IN),
            jointVertex(123, 3, OUT),
        ), createSwitchVertices(switchYv, structuresMap))

        val switchRr = switch(id = IntId(456), name = "TestSwitchRR", structureId = structureRr.id)
        assertEquals(listOf(
            jointVertex(456, 1, IN),
            jointVertex(456, 1, OUT),
            jointVertex(456, 2, IN),
            jointVertex(456, 2, OUT),
            jointVertex(456, 4, IN),
            jointVertex(456, 4, OUT),
            jointVertex(456, 3, IN),
            jointVertex(456, 3, OUT),
        ), createSwitchVertices(switchRr, structuresMap))
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
                    edgeSwitch(123, UP, 1, 5, 2),
                connectionThroughSwitch(123, 2, 1, structureYv.alignments[0].length()) to
                    edgeSwitch(123, DOWN, 1, 5, 2),
                connectionThroughSwitch(123, 1, 3, structureYv.alignments[1].length()) to
                    edgeSwitch(123, UP, 1, 3),
                connectionThroughSwitch(123, 3, 1, structureYv.alignments[1].length()) to
                    edgeSwitch(123, DOWN, 1, 3),
            ),
            connectionsYv)

        val switchRr = switch(id = IntId(456), name = "TestSwitchRR", structureId = structureRr.id)
        val connectionsRr = createThroughSwitchConnections(switchRr, structuresMap)
        assertEquals(listOf(
            connectionThroughSwitch(456, 1, 2, structureRr.alignments[0].length()) to
                edgeSwitch(456, UP, 1, 5, 2),
            connectionThroughSwitch(456, 2, 1, structureRr.alignments[0].length()) to
                edgeSwitch(456, DOWN, 1, 5, 2),
            connectionThroughSwitch(456, 4, 3, structureRr.alignments[1].length()) to
                edgeSwitch(456, UP, 4, 5, 3),
            connectionThroughSwitch(456, 3, 4, structureRr.alignments[1].length()) to
                edgeSwitch(456, DOWN, 4, 5, 3),
        ), connectionsRr)
    }

    @Test
    fun `Direct connections are properly created for track end nodes`() {
        val trackStartNode = DbTrackBoundaryNode(IntId(123), TrackBoundary(IntId(456), START))
        createDirectConnections(trackStartNode).also { result ->
            assertEquals(1, result.size)
            assertEquals(
                directTrackConnection(456, START, 456, START),
                result[0].first
            )
            assertEquals(UP, result[0].second.direction)
        }

        val trackEndNode = DbTrackBoundaryNode(IntId(123), TrackBoundary(IntId(456), END))
        createDirectConnections(trackEndNode).also { result ->
            assertEquals(1, result.size)
            assertEquals(
                directTrackConnection(456, END, 456, END),
                result[0].first
            )
            assertEquals(UP, result[0].second.direction)
        }
    }

    @Test
    fun `Direct connections are properly created for double-track nodes`() {
        val trackLinkNode = DbTrackBoundaryNode(
            IntId(123),
            TrackBoundary(IntId(456), END),
            TrackBoundary(IntId(789), START)
        )
        createDirectConnections(trackLinkNode).also { result ->
            assertEquals(2, result.size)
            assertEquals(
                directTrackConnection(456, END, 789, START),
                result[0].first
            )
            assertEquals(UP, result[0].second.direction)
            assertEquals(
                directTrackConnection(789, START, 456, END),
                result[1].first
            )
            assertEquals(DOWN, result[1].second.direction)
        }
    }

    @Test
    fun `Direct connections are properly created for double-switch nodes`() {
        val switchJointNode = DbSwitchNode(
            IntId(123),
            switchLinkYV(IntId(456),1),
            switchLinkYV(IntId(789),2),
        )
        createDirectConnections(switchJointNode).also { result ->
            assertEquals(2, result.size)
            assertEquals(
                directSwitchConnection(456, 1, 789, 2),
                result[0].first
            )
            assertEquals(UP, result[0].second.direction)
            assertEquals(
                directSwitchConnection(789, 2, 456, 1),
                result[1].first
            )
            assertEquals(DOWN, result[1].second.direction)
        }
    }

    @Test
    fun `Track endpoint vertices are created for track endpoint nodes`() {
        val unconnectedGeom = DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(456),
            edges = listOf(dbEdgeEndToEnd(123, 456)),
        )
        assertEquals(
            listOf(
                trackBoundaryVertex(456, START, IN),
                trackBoundaryVertex(456, START, OUT),
                trackBoundaryVertex(456, END, IN),
                trackBoundaryVertex(456, END, OUT),
            ),
            createTrackEndVertices(unconnectedGeom)
        )
    }

    @Test
    fun `Track endpoint vertices are not created for switch connections (as they have separate processing)`() {
        val connectedGeom = DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(456),
            edges = listOf(dbEdgeOuterSwitchConnector(789, 123, 1, 456, 1)),
        )
        assertEquals(emptyList(), createTrackEndVertices(connectedGeom))
    }

    @Test
    fun `Track connections are created correctly for track sections between switches`() {
        val edge = dbEdgeOuterSwitchConnector(123, 111, 1, 222, 2)
        assertEquals(
            listOf(
                connectionBetweenSwitches(111, 1, 222, 2, edge.length.distance) to TrackEdge(
                    IntId(123), UP
                ),
                connectionBetweenSwitches(222, 2, 111, 1, edge.length.distance) to TrackEdge(
                    IntId(123), DOWN
                ),
            ),
            createTrackConnections(edge)
        )
    }

    @Test
    fun `Track connections are created correctly for track endpoints`() {
        val edge = dbEdgeEndToEnd(123, 456)
        assertEquals(
            listOf(
                throughTrackConnection(456, START, END, edge.length.distance)
                    to TrackEdge(IntId(123), UP),
                throughTrackConnection(456, END, START, edge.length.distance)
                    to TrackEdge(IntId(123), DOWN),
            ),
            createTrackConnections(edge)
        )
    }

    @Test
    fun `Track connections are not created for switch internal edges (they have a separate processing)`() {
        val edge = dbEdgeInnerSwitch(123, 456)
        assertEquals(emptyList(), createTrackConnections(edge))
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

        val track1StartEdge = DbLayoutEdge(
            id = IntId(10000),
            startNode = DbNodeConnection(NodePortType.A, track1StartNode),
            endNode = DbNodeConnection(NodePortType.B, switch1StartNode),
            segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
        )
        val switch1InnerMainEdge = DbLayoutEdge(
            id = IntId(10001),
            startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
            endNode = DbNodeConnection(NodePortType.A,switch1StraightEndNode),
            segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
        )
        val switch1InnerBranchEdge = DbLayoutEdge(
            id = IntId(10002),
            startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
            endNode = DbNodeConnection(NodePortType.A,switch1BranchEndNode),
            segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 50.0))),
        )
        val track2Edge = DbLayoutEdge(
            id = IntId(10003),
            startNode = DbNodeConnection(NodePortType.B, switch1StraightEndNode),
            endNode = DbNodeConnection(NodePortType.B, switch2StartNode),
            segments = listOf(segment(Point(200.0, 0.0), Point(300.0, 0.0))),
        )
        val track3EndEdge = DbLayoutEdge(
            id = IntId(10004),
            startNode = DbNodeConnection(NodePortType.B, switch1BranchEndNode),
            endNode = DbNodeConnection(NodePortType.A, track3EndNode),
            segments = listOf(segment(Point(200.0, 50.0), Point(300.0, 100.0))),
        )

        val trackGeom1 = DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(1),
            edges = listOf(track1StartEdge, switch1InnerMainEdge)
        )
        val trackGeom2 = DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(2),
            edges = listOf(track2Edge)
        )
        val trackGeom3 = DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(3),
            edges = listOf(switch1InnerBranchEdge, track3EndEdge)
        )

        val graph = buildGraph(trackGeoms = listOf(trackGeom1, trackGeom2, trackGeom3), switches = listOf(switch1, switch2), structures = structures)

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
            ).sortedBy { it.toString() },
            graph.getVertices().sortedBy { it.toString() },
        )

        assertEquals(
            mapOf(
                // Inner switch edges
                edgeSwitch(1, UP, 1, 5, 2)
                    to (jointVertex(1, 1, IN) to jointVertex(1, 2, OUT)),
                edgeSwitch(1, DOWN, 1, 5, 2)
                    to (jointVertex(1, 2, IN) to jointVertex(1, 1, OUT)),
                edgeSwitch(1, UP, 1, 3)
                    to (jointVertex(1, 1, IN) to jointVertex(1, 3, OUT)),
                edgeSwitch(1, DOWN, 1, 3)
                    to (jointVertex(1, 3, IN) to jointVertex(1, 1, OUT)),
                edgeSwitch(2, UP, 1, 5, 2)
                    to (jointVertex(2, 1, IN) to jointVertex(2, 2, OUT)),
                edgeSwitch(2, DOWN, 1, 5, 2)
                    to (jointVertex(2, 2, IN) to jointVertex(2, 1, OUT)),
                edgeSwitch(2, UP, 4, 5, 3)
                    to (jointVertex(2, 4, IN) to jointVertex(2, 3, OUT)),
                edgeSwitch(2, DOWN, 4, 5, 3)
                    to (jointVertex(2, 3, IN) to jointVertex(2, 4, OUT)),
                // Direct return connection at track ends
                DirectConnectionEdge(track1StartNode.id, UP)
                    to (trackBoundaryVertex(1, START, OUT) to trackBoundaryVertex(1, START, IN)),
                DirectConnectionEdge(track3EndNode.id, UP)
                    to (trackBoundaryVertex(3, END, OUT) to trackBoundaryVertex(3, END, IN)),
                // Track connections between nodes, excluding switch internal edges
                TrackEdge(track1StartEdge.id, UP)
                    to (trackBoundaryVertex(1, START, IN) to jointVertex(1, 1, IN)),
                TrackEdge(track1StartEdge.id, DOWN)
                    to (jointVertex(1, 1, OUT) to trackBoundaryVertex(1, START, OUT)),
                TrackEdge(track2Edge.id, UP)
                    to (jointVertex(1, 2, OUT) to jointVertex(2, 4, IN)),
                TrackEdge(track2Edge.id, DOWN)
                    to (jointVertex(2, 4, OUT) to jointVertex(1, 2, IN)),
                TrackEdge(track3EndEdge.id, UP)
                    to (jointVertex(1, 3, OUT) to trackBoundaryVertex(3, END, OUT)),
                TrackEdge(track3EndEdge.id, DOWN)
                    to (trackBoundaryVertex(3, END, IN) to jointVertex(1, 3, IN)),
             ).entries.sortedBy { it.key.toString() },
            graph.getEdges().entries.sortedBy { it.key.toString() },
        )
    }
}

private fun edgeSwitch(switchId: Int, direction: EdgeDirection, vararg joints: Int) =
    SwitchInternalEdge(IntId(switchId), joints.map(::JointNumber), direction)

private fun directTrackConnection(fromTrackId: Int, fromBoundary: TrackBoundaryType, toTrackId: Int, toBoundary: TrackBoundaryType) =
    RoutingConnection(
        from = trackBoundaryVertex(fromTrackId, fromBoundary, OUT),
        to = trackBoundaryVertex(toTrackId, toBoundary, IN),
        length = 0.0,
    )

private fun throughTrackConnection(trackId: Int, fromBoundary: TrackBoundaryType, toBoundary: TrackBoundaryType, length: Double) =
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

private fun connectionBetweenSwitches(fromSwitchId: Int, fromJoint: Int, toSwitchId: Int, toJoint: Int, length: Double) =
    RoutingConnection(
        from = jointVertex(fromSwitchId, fromJoint, OUT),
        to = jointVertex(toSwitchId, toJoint, IN),
        length = length,
    )

private fun jointVertex(switchId: Int, joint: Int, direction: VertexDirection) =
    SwitchJointVertex(IntId(switchId), JointNumber(joint), direction)

private fun trackBoundaryVertex(trackId: Int, boundary: TrackBoundaryType, direction: VertexDirection) =
    TrackBoundaryVertex(IntId(trackId), boundary, direction)

private fun dbEdgeOuterSwitchConnector(edgeId: Int, fromSwitchId: Int, fromSwitchJoint: Int, toSwitchId: Int, toSwitchJoint: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.B, dbSwitchNode(123456, fromSwitchId, fromSwitchJoint)),
        endNode = DbNodeConnection(NodePortType.B,dbSwitchNode(654321, toSwitchId, toSwitchJoint)),
        segments = listOf(someSegment()),
    )

private fun dbEdgeInnerSwitch(edgeId: Int, switchId: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.A, dbSwitchNode(123456, switchId, 1)),
        endNode = DbNodeConnection(NodePortType.A,dbSwitchNode(654321, switchId, 2)),
        segments = listOf(someSegment()),
    )

private fun dbEdgeEndToEnd(edgeId: Int, trackId: Int) =
    DbLayoutEdge(
        id = IntId(edgeId),
        startNode = DbNodeConnection(NodePortType.A, dbTrackEndNode(123456, trackId, START)),
        endNode = DbNodeConnection(NodePortType.A,dbTrackEndNode(654321, trackId, END)),
        segments = listOf(someSegment()),
    )

private fun dbSwitchNode(nodeId: Int, switchId: Int, joint: Int) =
    DbSwitchNode(IntId(nodeId), switchLinkYV(IntId(switchId), joint), null)

private fun dbTrackEndNode(nodeId: Int, trackId: Int, type: TrackBoundaryType) =
    DbTrackBoundaryNode(IntId(nodeId), TrackBoundary(IntId(trackId), type))

private fun <T: LayoutAsset<T>> layoutRowVersion(id: Int): LayoutRowVersion<T> =
    LayoutRowVersion(LayoutRowId(IntId(id),  LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)), 1)
