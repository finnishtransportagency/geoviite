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
import fi.fta.geoviite.infra.tracklayout.graph.IdSwitchRef
import fi.fta.geoviite.infra.tracklayout.graph.createNanoTopology
import java.util.UUID
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
    fun `SwitchEdge toSwitchM converts edge M to switch alignment M in UP direction`() {
        val edge =
            SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(10.0), LineM(30.0)), edgeLength = 20.0)
        assertEquals(LineM<SwitchStructureAlignmentM>(10.0), edge.toSwitchM(LineM(0.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(20.0), edge.toSwitchM(LineM(10.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(30.0), edge.toSwitchM(LineM(20.0)))
    }

    @Test
    fun `SwitchEdge toSwitchM converts edge M to switch alignment M in DOWN direction`() {
        val edge =
            SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(10.0), LineM(30.0)), edgeLength = 20.0)
        assertEquals(LineM<SwitchStructureAlignmentM>(30.0), edge.toSwitchM(LineM(0.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(20.0), edge.toSwitchM(LineM(10.0)))
        assertEquals(LineM<SwitchStructureAlignmentM>(10.0), edge.toSwitchM(LineM(20.0)))
    }

    @Test
    fun `SwitchEdge toEdgeM converts switch alignment M to edge M in UP direction`() {
        val edge =
            SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(10.0), LineM(30.0)), edgeLength = 20.0)
        assertEquals(LineM<EdgeM>(0.0), edge.toEdgeM(LineM(10.0)))
        assertEquals(LineM<EdgeM>(10.0), edge.toEdgeM(LineM(20.0)))
        assertEquals(LineM<EdgeM>(20.0), edge.toEdgeM(LineM(30.0)))
    }

    @Test
    fun `SwitchEdge toEdgeM converts switch alignment M to edge M in DOWN direction`() {
        val edge =
            SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(10.0), LineM(30.0)), edgeLength = 20.0)
        assertEquals(LineM<EdgeM>(20.0), edge.toEdgeM(LineM(10.0)))
        assertEquals(LineM<EdgeM>(10.0), edge.toEdgeM(LineM(20.0)))
        assertEquals(LineM<EdgeM>(0.0), edge.toEdgeM(LineM(30.0)))
    }

    @Test
    fun `SwitchEdge toSwitchM and toEdgeM are inverse operations in UP direction`() {
        val edge = SwitchEdge(id = IntId(1), direction = UP, mRange = Range(LineM(5.0), LineM(25.0)), edgeLength = 20.0)
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
        val edge =
            SwitchEdge(id = IntId(1), direction = DOWN, mRange = Range(LineM(5.0), LineM(25.0)), edgeLength = 20.0)
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
    fun `A sane topology graph is created for a simple track layout`() {
        val structure1 = switchStructureYV60_300_1_9()
        val structure2 = switchStructureRR54_4x1_9()
        val structures = mapOf(structure1.id to structure1, structure2.id to structure2)

        val switch1Id = IntId<LayoutSwitch>(1)
        val switch2Id = IntId<LayoutSwitch>(2)
        val switch1 = switch(id = switch1Id, name = "Switch1", structureId = structure1.id)
        val switch2 = switch(id = switch2Id, name = "Switch2", structureId = structure2.id)

        // Build the following tracks
        //  Track1: Start at nowhere, connect to switch 1 joint 1, go through the switch and end at joint 2
        //  Track2 (Continuing): Start where track 1 ended (switch 1 joint 2) and continue to end at switch 2 joint 4
        //  Track3 (Branching): start at switch1 joint 1, go through the switch, continue a bit to end at nowhere

        val track1StartNode = DbTrackBoundaryNode(IntId(10), TrackBoundary(IntId(1), START), null, UUID(0L, 10L))
        val switch1StartNode = DbSwitchNode(IntId(11), switchLinkYV(IntId(1), 1), null, UUID(0L, 11L))
        val switch1StraightEndNode = DbSwitchNode(IntId(12), switchLinkYV(IntId(1), 2), null, UUID(0L, 12L))
        val switch1BranchEndNode = DbSwitchNode(IntId(13), switchLinkYV(IntId(1), 3), null, UUID(0L, 13L))
        val switch2StartNode = DbSwitchNode(IntId(14), switchLinkYV(IntId(2), 4), null, UUID(0L, 14L))
        val track3EndNode = DbTrackBoundaryNode(IntId(15), TrackBoundary(IntId(3), END), null, UUID(0L, 15L))

        val track1StartEdge =
            DbLayoutEdge(
                id = IntId(10000),
                extId = UUID(1L, 10000L),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switch1StartNode),
                segments = listOf(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )
        val switch1InnerMainEdge =
            DbLayoutEdge(
                id = IntId(10001),
                extId = UUID(1L, 10001L),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1StraightEndNode),
                segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 0.0))),
            )
        val switch1InnerBranchEdge =
            DbLayoutEdge(
                id = IntId(10002),
                extId = UUID(1L, 10002L),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1BranchEndNode),
                segments = listOf(segment(Point(100.0, 0.0), Point(200.0, 50.0))),
            )
        val track2Edge =
            DbLayoutEdge(
                id = IntId(10003),
                extId = UUID(1L, 10003L),
                startNode = DbNodeConnection(NodePortType.B, switch1StraightEndNode),
                endNode = DbNodeConnection(NodePortType.B, switch2StartNode),
                segments = listOf(segment(Point(200.0, 0.0), Point(300.0, 0.0))),
            )
        val track3EndEdge =
            DbLayoutEdge(
                id = IntId(10004),
                extId = UUID(1L, 10004L),
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

        val topology =
            createNanoTopology(
                tracks =
                    listOf(
                        locationTrack(IntId(1), id = trackGeom1.trackId) to trackGeom1,
                        locationTrack(IntId(1), id = trackGeom2.trackId) to trackGeom2,
                        locationTrack(IntId(1), id = trackGeom3.trackId) to trackGeom3,
                    ),
                switches = mapOf(switch1Id to switch1, switch2Id to switch2),
                structures = structures,
                switchRefs = mapOf(switch1Id to IdSwitchRef(switch1Id), switch2Id to IdSwitchRef(switch2Id)),
            )
        val graph = buildGraph(topology)

        // The topology graph is built on edge endpoints: each topology edge gets in & out vertices for both ends
        assertEquals(
            topology.edges.size * 4,
            graph.getVertices().filterIsInstance<TopologyEndpointVertex>().size,
        )
        assertEquals(topology.edges.size * 4, graph.getVertices().size)

        // The database edges that are not switch internal geometry are routable in both directions
        assertEquals(
            setOf(
                TrackEdge(track1StartEdge.id, UP),
                TrackEdge(track1StartEdge.id, DOWN),
                TrackEdge(track2Edge.id, UP),
                TrackEdge(track2Edge.id, DOWN),
                TrackEdge(track3EndEdge.id, UP),
                TrackEdge(track3EndEdge.id, DOWN),
            ),
            graph.getEdges().keys.filterIsInstance<TrackEdge>().toSet(),
        )

        // Switch internal geometry is routable via switch alignments, for the linked alignments of switch 1.
        // Switch 2 is only connected from the outside (joint 4), so it has no routable alignments.
        assertEquals(
            setOf(
                edgeSwitch(1, UP, structure1.alignments[0]),
                edgeSwitch(1, DOWN, structure1.alignments[0]),
                edgeSwitch(1, UP, structure1.alignments[1]),
                edgeSwitch(1, DOWN, structure1.alignments[1]),
            ),
            graph.getEdges().keys.filterIsInstance<SwitchInternalEdge>().toSet(),
        )

        // Routing between the topology edges happens via node transitions: 4 at switch 1 joint 1 (two alignments,
        // both directions), 2 at joints 2 and 3 each, and a U-turn at both track boundaries. Switch 2 only has a
        // single connection at joint 4, so it has no transitions.
        assertEquals(10, graph.getEdges().keys.filterIsInstance<TopologyTransitionEdge>().size)
        assertEquals(20, graph.getEdges().size)
    }

    @Test
    fun `Basic pathfinding works on a topology graph`() {
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switchId = IntId<LayoutSwitch>(1)
        val switch1 = switch(id = switchId, structureId = structure.id)

        // Build the following tracks
        //  Track1 (Straight): Start at nowhere, go through the switch and continue on to end at nowhere
        //  Track2 (Branching): start at switch1 joint 1, go through the switch and end at nowhere

        val track1StartNode = DbTrackBoundaryNode(IntId(10), TrackBoundary(IntId(111), START), null, UUID(0L, 10L))
        val track1EndNode = DbTrackBoundaryNode(IntId(11), TrackBoundary(IntId(111), END), null, UUID(0L, 11L))
        val switchStartNode = DbSwitchNode(IntId(12), switchLinkYV(IntId(1), 1), null, UUID(0L, 12L))
        val switchStraightMidNode = DbSwitchNode(IntId(13), switchLinkYV(IntId(1), 5), null, UUID(0L, 13L))
        val switchStraightEndNode = DbSwitchNode(IntId(14), switchLinkYV(IntId(1), 2), null, UUID(0L, 14L))
        val switchBranchEndNode = DbSwitchNode(IntId(15), switchLinkYV(IntId(1), 3), null, UUID(0L, 15L))
        val track2EndNode = DbTrackBoundaryNode(IntId(16), TrackBoundary(IntId(222), END), null, UUID(0L, 16L))

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
                extId = UUID(1L, 10000L),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switchStartNode),
                segments = listOf(segment(track1Start, joint1Location)),
            )
        val switchInnerMainEdge1 =
            DbLayoutEdge(
                id = IntId(10001),
                extId = UUID(1L, 10001L),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchStraightMidNode),
                segments = listOf(segment(joint1Location, joint5Location)),
            )
        val switchInnerMainEdge2 =
            DbLayoutEdge(
                id = IntId(10002),
                extId = UUID(1L, 10002L),
                startNode = DbNodeConnection(NodePortType.A, switchStraightMidNode),
                endNode = DbNodeConnection(NodePortType.A, switchStraightEndNode),
                segments = listOf(segment(joint5Location, joint2Location)),
            )
        val track1EndEdge =
            DbLayoutEdge(
                id = IntId(10003),
                extId = UUID(1L, 10003L),
                startNode = DbNodeConnection(NodePortType.B, switchStraightEndNode),
                endNode = DbNodeConnection(NodePortType.A, track1EndNode),
                segments = listOf(segment(joint2Location, track1End)),
            )

        val switchInnerBranchEdge =
            DbLayoutEdge(
                id = IntId(10004),
                extId = UUID(1L, 10004L),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchBranchEndNode),
                segments = listOf(segment(joint1Location, joint3Location)),
            )
        val track2EndEdge =
            DbLayoutEdge(
                id = IntId(10005),
                extId = UUID(1L, 10005L),
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
            buildGraph(
                createNanoTopology(
                    tracks =
                        listOf(
                            locationTrack(IntId(1), id = trackGeom1.trackId) to trackGeom1,
                            locationTrack(IntId(1), id = trackGeom2.trackId) to trackGeom2,
                        ),
                    switches = mapOf(switchId to switch1),
                    structures = structures,
                    switchRefs = mapOf(switchId to IdSwitchRef(switchId)),
                )
            )
        val getRoute =
            {
                (from, fromTrack): Pair<Point, DbLocationTrackGeometry>,
                (to, toTrack): Pair<Point, DbLocationTrackGeometry> ->
                graph.findPath(trackCacheHit(fromTrack, from), trackCacheHit(toTrack, to))
            }

        assertBasicSwitchRoutes(getRoute, trackGeom1, track1Start, joint1Location, joint2Location, joint5Location)
        assertRoutesAcrossTopologyTracks(getRoute, trackGeom1, trackGeom2, track1Start, track1End, track2End)
        assertRoutesBetweenTrackAndSwitch(getRoute, trackGeom1, track1Start, track1End, joint1Location, joint2Location)
    }

    @Test
    fun `Topology routing does not throw when an edge has a broken switch linking`() {
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

        val trackStartNode = DbTrackBoundaryNode(IntId(10), TrackBoundary(IntId(111), START), null, UUID(0L, 10L))
        val trackEndNode = DbTrackBoundaryNode(IntId(11), TrackBoundary(IntId(111), END), null, UUID(0L, 11L))
        val switch1Node = DbSwitchNode(IntId(12), switchLinkYV(IntId(1), 1), null, UUID(0L, 12L))
        val switch2Node = DbSwitchNode(IntId(13), switchLinkYV(IntId(2), 2), null, UUID(0L, 13L))

        val trackStart = Point(0.0, 0.0)
        val switch1Location = Point(100.0, 0.0)
        val switch2Location = Point(200.0, 0.0)
        val trackEnd = Point(300.0, 0.0)

        val startEdge =
            DbLayoutEdge(
                id = IntId(10000),
                extId = UUID(1L, 10000L),
                // Start at track start
                startNode = DbNodeConnection(NodePortType.A, trackStartNode),
                // Connect to switch1 as outer connection
                endNode = DbNodeConnection(NodePortType.B, switch1Node),
                segments = listOf(segment(trackStart, switch1Location)),
            )
        val brokenInnerSwitchEdge =
            DbLayoutEdge(
                id = IntId(10001),
                extId = UUID(1L, 10001L),
                // Connect start to switch1 as inner connection
                startNode = DbNodeConnection(NodePortType.A, switch1Node),
                // Connect end to switch2 as inner connection
                endNode = DbNodeConnection(NodePortType.A, switch2Node),
                segments = listOf(segment(switch1Location, switch2Location)),
            )
        val endEdge =
            DbLayoutEdge(
                id = IntId(10002),
                extId = UUID(1L, 10002L),
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

        val graph = assertDoesNotThrow {
            buildGraph(
                createNanoTopology(
                    tracks = listOf(locationTrack(IntId(1), id = trackGeom.trackId) to trackGeom),
                    switches = mapOf(switch1Id to switch1, switch2Id to switch2),
                    structures = structures,
                    switchRefs = mapOf(switch1Id to IdSwitchRef(switch1Id), switch2Id to IdSwitchRef(switch2Id)),
                )
            )
        }

        // The graph should contain endpoint vertices for all three edges: the broken edge is not recognized as
        // switch internal geometry, so it remains a regular topology edge
        assertEquals(12, graph.getVertices().filterIsInstance<TopologyEndpointVertex>().size) {
            "Graph should contain in & out endpoint vertices for both ends of the three track edges (3 x 2 x 2)"
        }
        assertEquals(0, graph.getEdges().keys.filterIsInstance<SwitchInternalEdge>().size) {
            "Graph should not contain routable alignments for the broken switch linking"
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
    fun `Topology routing does not throw when a switch is deleted but tracks still reference it`() {
        // When a switch has stateCategory=NOT_EXISTING, RoutingService filters it out of the
        // switches passed to the topology. But the track edges may still have nodes that
        // reference the deleted switch. The graph building/routing should handle this gracefully.
        val structure = switchStructureYV60_300_1_9()
        val structures = mapOf(structure.id to structure)

        val switchId = IntId<LayoutSwitch>(1)
        val deletedSwitch =
            switch(id = switchId, structureId = structure.id, stateCategory = LayoutStateCategory.NOT_EXISTING)

        val trackStartNode = DbTrackBoundaryNode(IntId(10), TrackBoundary(IntId(111), START), null, UUID(0L, 10L))
        val trackEndNode = DbTrackBoundaryNode(IntId(11), TrackBoundary(IntId(111), END), null, UUID(0L, 11L))
        val switchStartNode = DbSwitchNode(IntId(12), switchLinkYV(IntId(1), 1), null, UUID(0L, 12L))
        val switchEndNode = DbSwitchNode(IntId(13), switchLinkYV(IntId(1), 2), null, UUID(0L, 13L))

        val trackStart = Point(0.0, 0.0)
        val switchLocation = Point(100.0, 0.0)
        val switchEndLocation = Point(200.0, 0.0)
        val trackEnd = Point(300.0, 0.0)

        val startEdge =
            DbLayoutEdge(
                id = IntId(10000),
                extId = UUID(1L, 10000L),
                startNode = DbNodeConnection(NodePortType.A, trackStartNode),
                endNode = DbNodeConnection(NodePortType.B, switchStartNode),
                segments = listOf(segment(trackStart, switchLocation)),
            )
        val switchInnerEdge =
            DbLayoutEdge(
                id = IntId(10001),
                extId = UUID(1L, 10001L),
                startNode = DbNodeConnection(NodePortType.A, switchStartNode),
                endNode = DbNodeConnection(NodePortType.A, switchEndNode),
                segments = listOf(segment(switchLocation, switchEndLocation)),
            )
        val endEdge =
            DbLayoutEdge(
                id = IntId(10002),
                extId = UUID(1L, 10002L),
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
        val activeSwitches = listOf(deletedSwitch).filter { it.exists }.associateBy { it.id as IntId }

        val graph = assertDoesNotThrow {
            buildGraph(
                createNanoTopology(
                    tracks = listOf(locationTrack(IntId(1), id = trackGeom.trackId) to trackGeom),
                    switches = activeSwitches,
                    structures = structures,
                    switchRefs = activeSwitches.keys.associateWith(::IdSwitchRef),
                )
            )
        }

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
    fun `Topology route length is correctly calculated in and over switches`() {
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

        val track1StartNode = DbTrackBoundaryNode(IntId(10), TrackBoundary(IntId(111), START), null, UUID(0L, 10L))
        val switch1StartNode = DbSwitchNode(IntId(11), switchLinkYV(IntId(1), 1), null, UUID(0L, 11L))
        val switch1EndNode = DbSwitchNode(IntId(12), switchLinkYV(IntId(1), 2), null, UUID(0L, 12L))
        val switch2StartNode = DbSwitchNode(IntId(13), switchLinkYV(IntId(2), 1), null, UUID(0L, 13L))
        val switch2EndNode = DbSwitchNode(IntId(14), switchLinkYV(IntId(2), 2), null, UUID(0L, 14L))
        val track3EndNode = DbTrackBoundaryNode(IntId(15), TrackBoundary(IntId(333), END), null, UUID(0L, 15L))

        val track1Start = Point(0.0, 10.0)
        val switch1Joint1Location = Point(100.0, 10.0)
        val switch1Joint2Location = Point(100.0, 10.0) + structure.getJointLocation(JointNumber(2))
        val switch2Joint1Location = Point(200.0, 10.0) - structure.getJointLocation(JointNumber(2))
        val switch2Joint2Location = Point(200.0, 10.0)
        val track3End = Point(300.0, 10.0)

        val track1Edge =
            DbLayoutEdge(
                id = IntId(10000),
                extId = UUID(1L, 10000L),
                startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                endNode = DbNodeConnection(NodePortType.B, switch1StartNode),
                segments = listOf(segment(track1Start, switch1Joint1Location)),
            )
        val switch1InnerEdge =
            DbLayoutEdge(
                id = IntId(10001),
                extId = UUID(1L, 10001L),
                startNode = DbNodeConnection(NodePortType.A, switch1StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch1EndNode),
                segments = listOf(segment(switch1Joint1Location, switch1Joint2Location)),
            )
        val track2MidEdge =
            DbLayoutEdge(
                id = IntId(10002),
                extId = UUID(1L, 10002L),
                startNode = DbNodeConnection(NodePortType.B, switch1EndNode),
                endNode = DbNodeConnection(NodePortType.B, switch2StartNode),
                segments = listOf(segment(switch1Joint2Location, switch2Joint1Location)),
            )
        val switch2InnerEdge =
            DbLayoutEdge(
                id = IntId(10003),
                extId = UUID(1L, 10003L),
                startNode = DbNodeConnection(NodePortType.A, switch2StartNode),
                endNode = DbNodeConnection(NodePortType.A, switch2EndNode),
                segments = listOf(segment(switch2Joint1Location, switch2Joint2Location)),
            )
        val track3Edge =
            DbLayoutEdge(
                id = IntId(10004),
                extId = UUID(1L, 10004L),
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
                createNanoTopology(
                    tracks =
                        listOf(
                            locationTrack(IntId(1), id = trackGeom1.trackId) to trackGeom1,
                            locationTrack(IntId(1), id = trackGeom2.trackId) to trackGeom2,
                            locationTrack(IntId(1), id = trackGeom3.trackId) to trackGeom3,
                        ),
                    switches = mapOf(switchId1 to switch1, switchId2 to switch2),
                    structures = structures,
                    switchRefs = mapOf(switchId1 to IdSwitchRef(switchId1), switchId2 to IdSwitchRef(switchId2)),
                )
            )
        val getLength =
            {
                (from, fromTrack): Pair<Point, DbLocationTrackGeometry>,
                (to, toTrack): Pair<Point, DbLocationTrackGeometry> ->
                graph.findPath(trackCacheHit(fromTrack, from), trackCacheHit(toTrack, to))?.totalLength
            }

        assertEquals(300.0, getLength(track1Start to trackGeom1, track3End to trackGeom3))
        assertStartToSwitchJointLength(getLength, trackGeom1, trackGeom2, track1Start, switch1Joint1Location)
        assertEndToSwitchJointLength(getLength, trackGeom2, trackGeom3, track3End, switch2Joint2Location)
        assertSwitchJointRouteLengths(
            getLength,
            trackGeom1,
            trackGeom2,
            trackGeom3,
            switch1Joint1Location,
            switch2Joint2Location,
        )

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

        assertMiddleTopologyRouteLengths(getLength, trackGeom1, trackGeom2, trackGeom3, track1Start, track3End)
    }

    @Test
    fun `Pathfinding over an RR crossing switch allows straight crossings and forbids turning onto the crossing track`() {
        val structure = switchStructureRR54_4x1_9()
        val structures = mapOf(structure.id to structure)
        val switchId = IntId<LayoutSwitch>(1)
        val switchJoints =
            structure.joints.map { joint -> switchJoint(joint.number.intValue, Point(100.0, 100.0) + joint.location) }
        val rrSwitch = switch(id = switchId, structureId = structure.id, joints = switchJoints)
        val jointLoc = { j: Int -> Point(100.0, 100.0) + structure.getJointLocation(JointNumber(j)) }

        val switchNodes = fiveJointSwitchNodes(switchId)
        val node1 = switchNodes.getValue(1)
        val node2 = switchNodes.getValue(2)
        val node3 = switchNodes.getValue(3)
        val node4 = switchNodes.getValue(4)
        val node5 = switchNodes.getValue(5)

        val track1StartNode = DbTrackBoundaryNode(IntId(21), TrackBoundary(IntId(111), START), null, UUID(0L, 21L))
        val track1EndNode = DbTrackBoundaryNode(IntId(22), TrackBoundary(IntId(111), END), null, UUID(0L, 22L))
        val track2StartNode = DbTrackBoundaryNode(IntId(23), TrackBoundary(IntId(222), START), null, UUID(0L, 23L))
        val track2EndNode = DbTrackBoundaryNode(IntId(24), TrackBoundary(IntId(222), END), null, UUID(0L, 24L))

        val track1Start = Point(0.0, 80.0)
        val track1End = Point(200.0, 120.0)
        val track2Start = Point(0.0, 120.0)
        val track2End = Point(200.0, 80.0)

        val trackGeom1 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(111),
                edges =
                    listOf(
                        DbLayoutEdge(
                            id = IntId(10001),
                            extId = UUID(1L, 10001L),
                            startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                            endNode = DbNodeConnection(NodePortType.B, node1),
                            segments = listOf(segment(track1Start, jointLoc(1))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10002),
                            extId = UUID(1L, 10002L),
                            startNode = DbNodeConnection(NodePortType.A, node1),
                            endNode = DbNodeConnection(NodePortType.A, node5),
                            segments = listOf(segment(jointLoc(1), jointLoc(5))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10003),
                            extId = UUID(1L, 10003L),
                            startNode = DbNodeConnection(NodePortType.A, node5),
                            endNode = DbNodeConnection(NodePortType.A, node2),
                            segments = listOf(segment(jointLoc(5), jointLoc(2))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10004),
                            extId = UUID(1L, 10004L),
                            startNode = DbNodeConnection(NodePortType.B, node2),
                            endNode = DbNodeConnection(NodePortType.A, track1EndNode),
                            segments = listOf(segment(jointLoc(2), track1End)),
                        ),
                    ),
            )
        val trackGeom2 =
            DbLocationTrackGeometry(
                trackRowVersion = layoutRowVersion(222),
                edges =
                    listOf(
                        DbLayoutEdge(
                            id = IntId(10005),
                            extId = UUID(1L, 10005L),
                            startNode = DbNodeConnection(NodePortType.A, track2StartNode),
                            endNode = DbNodeConnection(NodePortType.B, node4),
                            segments = listOf(segment(track2Start, jointLoc(4))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10006),
                            extId = UUID(1L, 10006L),
                            startNode = DbNodeConnection(NodePortType.A, node4),
                            endNode = DbNodeConnection(NodePortType.A, node5),
                            segments = listOf(segment(jointLoc(4), jointLoc(5))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10007),
                            extId = UUID(1L, 10007L),
                            startNode = DbNodeConnection(NodePortType.A, node5),
                            endNode = DbNodeConnection(NodePortType.A, node3),
                            segments = listOf(segment(jointLoc(5), jointLoc(3))),
                        ),
                        DbLayoutEdge(
                            id = IntId(10008),
                            extId = UUID(1L, 10008L),
                            startNode = DbNodeConnection(NodePortType.B, node3),
                            endNode = DbNodeConnection(NodePortType.A, track2EndNode),
                            segments = listOf(segment(jointLoc(3), track2End)),
                        ),
                    ),
            )

        val graph =
            buildGraph(
                createNanoTopology(
                    tracks =
                        listOf(
                            locationTrack(IntId(1), id = trackGeom1.trackId) to trackGeom1,
                            locationTrack(IntId(1), id = trackGeom2.trackId) to trackGeom2,
                        ),
                    switches = mapOf(switchId to rrSwitch),
                    structures = structures,
                    switchRefs = mapOf(switchId to IdSwitchRef(switchId)),
                )
            )

        // Straight through track 1 (1 -> 5 -> 2) and track 2 (4 -> 5 -> 3) is allowed
        assertEquals(
            Route(listOf(routeSection(111, 0.0, trackGeom1.length.distance, UP))),
            graph.findPath(trackCacheHit(trackGeom1, track1Start), trackCacheHit(trackGeom1, track1End)),
        )
        assertEquals(
            Route(listOf(routeSection(222, 0.0, trackGeom2.length.distance, UP))),
            graph.findPath(trackCacheHit(trackGeom2, track2Start), trackCacheHit(trackGeom2, track2End)),
        )

        // Turning from track 1 onto track 2 at joint 5 is forbidden
        assertNull(graph.findPath(trackCacheHit(trackGeom1, track1Start), trackCacheHit(trackGeom2, track2End)))
        assertNull(graph.findPath(trackCacheHit(trackGeom1, track1Start), trackCacheHit(trackGeom2, track2Start)))
    }

    @Test
    fun `Pathfinding over a KRV slip switch routes through both straight and curved alignments`() {
        val structure =
            SwitchStructure(
                version = fi.fta.geoviite.infra.common.RowVersion(IntId(134), 1),
                data = fi.fta.geoviite.infra.switchLibrary.data.KRV54_200_1_9(),
            )
        val structures = mapOf(structure.id to structure)
        val switchId = IntId<LayoutSwitch>(1)
        val switchJoints =
            structure.joints.map { joint -> switchJoint(joint.number.intValue, Point(100.0, 100.0) + joint.location) }
        val krvSwitch = switch(id = switchId, structureId = structure.id, joints = switchJoints)
        val jointLoc = { j: Int -> Point(100.0, 100.0) + structure.getJointLocation(JointNumber(j)) }

        val switchNodes = fiveJointSwitchNodes(switchId)

        val track1Start = Point(0.0, 100.0)
        val track1End = Point(200.0, 100.0)
        val track2Start = Point(0.0, 110.0)
        val track2End = Point(200.0, 90.0)

        val (trackGeom1, trackGeom2) = krvCrossingTracks(switchNodes, jointLoc)
        val (trackGeom3, trackGeom4) = krvCurvedSlipTracks(switchNodes, jointLoc)

        val topology =
            createNanoTopology(
                tracks =
                    listOf(
                        locationTrack(IntId(1), id = trackGeom1.trackId) to trackGeom1,
                        locationTrack(IntId(1), id = trackGeom2.trackId) to trackGeom2,
                        locationTrack(IntId(1), id = trackGeom3.trackId) to trackGeom3,
                        locationTrack(IntId(1), id = trackGeom4.trackId) to trackGeom4,
                    ),
                switches = mapOf(switchId to krvSwitch),
                structures = structures,
                switchRefs = mapOf(switchId to IdSwitchRef(switchId)),
            )
        val graph = buildGraph(topology)

        assertKrvSlipSwitchPaths(
            graph,
            structure,
            listOf(trackGeom1, trackGeom2, trackGeom3, trackGeom4),
            listOf(track1Start, track2Start),
            listOf(track1End, track2End),
        )
    }
}

private typealias TopologyRouteFinder =
    (Pair<Point, DbLocationTrackGeometry>, Pair<Point, DbLocationTrackGeometry>) -> Route?

private typealias TopologyLengthFinder =
    (Pair<Point, DbLocationTrackGeometry>, Pair<Point, DbLocationTrackGeometry>) -> Double?

private fun assertBasicSwitchRoutes(
    getRoute: TopologyRouteFinder,
    trackGeometry: DbLocationTrackGeometry,
    trackStart: Point,
    joint1Location: Point,
    joint2Location: Point,
    joint5Location: Point,
) {
    // The closest points should be on the same spot of the track -> no actual route
    assertEquals(Route(emptyList()), getRoute(Point(10.0, 0.0) to trackGeometry, Point(10.0, 5.0) to trackGeometry))

    // On the same edge -> route should be found inside the edge (both ways)
    assertEquals(
        Route(listOf(routeSection(111, 10.0, 50.0, UP))),
        getRoute(Point(10.0, 0.0) to trackGeometry, Point(50.0, 5.0) to trackGeometry),
    )
    assertEquals(
        Route(listOf(routeSection(111, 10.0, 50.0, DOWN))),
        getRoute(Point(50.0, 0.0) to trackGeometry, Point(10.0, 5.0) to trackGeometry),
    )

    // Inside the same switch but on different edges -> route should be found within switch alignment
    ((joint1Location + Point(1.0, 0.0)) to (joint2Location - Point(1.0, 0.0))).let { (start, end) ->
        val startM = lineLength(trackStart, start)
        val endM = lineLength(trackStart, end)
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, UP))),
            getRoute(start to trackGeometry, end to trackGeometry),
        )
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, DOWN))),
            getRoute(end to trackGeometry, start to trackGeometry),
        )
    }

    // Inside the same switch but on different edges, while very short
    ((joint5Location - Point(1.0, 0.0)) to (joint5Location + Point(1.0, 0.0))).let { (start, end) ->
        val startM = lineLength(trackStart, start)
        val endM = lineLength(trackStart, end)
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, UP))),
            getRoute(start to trackGeometry, end to trackGeometry),
        )
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, DOWN))),
            getRoute(end to trackGeometry, start to trackGeometry),
        )
    }
}

private fun assertRoutesAcrossTopologyTracks(
    getRoute: TopologyRouteFinder,
    trackGeom1: DbLocationTrackGeometry,
    trackGeom2: DbLocationTrackGeometry,
    track1Start: Point,
    track1End: Point,
    track2End: Point,
) {
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
        Route(listOf(routeSection(222, 0.0, trackGeom2.length.distance, DOWN), routeSection(111, 0.0, 100.0, DOWN))),
        getRoute(track2End to trackGeom2, track1Start to trackGeom1),
    )
}

private fun assertRoutesBetweenTrackAndSwitch(
    getRoute: TopologyRouteFinder,
    trackGeometry: DbLocationTrackGeometry,
    trackStart: Point,
    trackEnd: Point,
    joint1Location: Point,
    joint2Location: Point,
) {
    // Start on track, end in switch
    (Point(50.0, 0.0) to (joint2Location - Point(1.0, 0.0))).let { (start, end) ->
        val startM = lineLength(trackStart, start)
        val endM = lineLength(trackStart, end)
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, UP))),
            getRoute(start to trackGeometry, end to trackGeometry),
        )
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, DOWN))),
            getRoute(end to trackGeometry, start to trackGeometry),
        )
    }

    // Start in switch, end on track
    ((joint1Location + Point(1.0, 0.0)) to (trackEnd - Point(50.0, 0.0))).let { (start, end) ->
        val startM = lineLength(trackStart, start)
        val endM = lineLength(trackStart, end)
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, UP))),
            getRoute(start to trackGeometry, end to trackGeometry),
        )
        assertEquals(
            Route(listOf(routeSection(111, startM, endM, DOWN))),
            getRoute(end to trackGeometry, start to trackGeometry),
        )
    }
}

private fun assertStartToSwitchJointLength(
    getLength: TopologyLengthFinder,
    trackGeom1: DbLocationTrackGeometry,
    trackGeom2: DbLocationTrackGeometry,
    track1Start: Point,
    switch1Joint1Location: Point,
) {
    assertEquals(100.0, getLength(track1Start to trackGeom1, switch1Joint1Location to trackGeom1))
    assertEquals(100.0, getLength(track1Start to trackGeom1, switch1Joint1Location to trackGeom2))
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, track1Start to trackGeom1))
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, track1Start to trackGeom1))
}

private fun assertEndToSwitchJointLength(
    getLength: TopologyLengthFinder,
    trackGeom2: DbLocationTrackGeometry,
    trackGeom3: DbLocationTrackGeometry,
    track3End: Point,
    switch2Joint2Location: Point,
) {
    assertEquals(100.0, getLength(track3End to trackGeom3, switch2Joint2Location to trackGeom3))
    assertEquals(100.0, getLength(track3End to trackGeom3, switch2Joint2Location to trackGeom2))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, track3End to trackGeom3))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, track3End to trackGeom3))
}

private fun assertSwitchJointRouteLengths(
    getLength: TopologyLengthFinder,
    trackGeom1: DbLocationTrackGeometry,
    trackGeom2: DbLocationTrackGeometry,
    trackGeom3: DbLocationTrackGeometry,
    switch1Joint1Location: Point,
    switch2Joint2Location: Point,
) {
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, switch2Joint2Location to trackGeom2))
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, switch2Joint2Location to trackGeom2))
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom2, switch2Joint2Location to trackGeom3))
    assertEquals(100.0, getLength(switch1Joint1Location to trackGeom1, switch2Joint2Location to trackGeom3))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, switch1Joint1Location to trackGeom2))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom2, switch1Joint1Location to trackGeom1))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, switch1Joint1Location to trackGeom2))
    assertEquals(100.0, getLength(switch2Joint2Location to trackGeom3, switch1Joint1Location to trackGeom1))
}

private fun assertMiddleTopologyRouteLengths(
    getLength: TopologyLengthFinder,
    trackGeom1: DbLocationTrackGeometry,
    trackGeom2: DbLocationTrackGeometry,
    trackGeom3: DbLocationTrackGeometry,
    track1Start: Point,
    track3End: Point,
) {
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

private fun routeSection(trackId: Int, startM: Double, endM: Double, direction: EdgeDirection) =
    RouteSection(IntId(trackId), Range(LineM(startM), LineM(endM)), direction)

private fun fiveJointSwitchNodes(switchId: IntId<LayoutSwitch>): Map<Int, DbSwitchNode> =
    (1..5).associateWith { joint ->
        val role = if (joint == 5) SwitchJointRole.MAIN else SwitchJointRole.CONNECTION
        DbSwitchNode(
            IntId(10 + joint),
            SwitchLink(switchId, role, JointNumber(joint)),
            null,
            UUID(0L, (10 + joint).toLong()),
        )
    }

private fun krvCrossingTracks(
    switchNodes: Map<Int, DbSwitchNode>,
    jointLoc: (Int) -> Point,
): Pair<DbLocationTrackGeometry, DbLocationTrackGeometry> {
    val node1 = switchNodes.getValue(1)
    val node2 = switchNodes.getValue(2)
    val node3 = switchNodes.getValue(3)
    val node4 = switchNodes.getValue(4)
    val node5 = switchNodes.getValue(5)
    val track1StartNode = DbTrackBoundaryNode(IntId(21), TrackBoundary(IntId(111), START), null, UUID(0L, 21L))
    val track1EndNode = DbTrackBoundaryNode(IntId(22), TrackBoundary(IntId(111), END), null, UUID(0L, 22L))
    val track2StartNode = DbTrackBoundaryNode(IntId(23), TrackBoundary(IntId(222), START), null, UUID(0L, 23L))
    val track2EndNode = DbTrackBoundaryNode(IntId(24), TrackBoundary(IntId(222), END), null, UUID(0L, 24L))

    val track1Start = Point(0.0, 100.0)
    val track1End = Point(200.0, 100.0)
    val track2Start = Point(0.0, 110.0)
    val track2End = Point(200.0, 90.0)

    // Track 1: 1 -> 5 -> 2
    val trackGeom1 =
        DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(111),
            edges =
                listOf(
                    DbLayoutEdge(
                        id = IntId(10001),
                        extId = UUID(1L, 10001L),
                        startNode = DbNodeConnection(NodePortType.A, track1StartNode),
                        endNode = DbNodeConnection(NodePortType.B, node1),
                        segments = listOf(segment(track1Start, jointLoc(1))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10002),
                        extId = UUID(1L, 10002L),
                        startNode = DbNodeConnection(NodePortType.A, node1),
                        endNode = DbNodeConnection(NodePortType.A, node5),
                        segments = listOf(segment(jointLoc(1), jointLoc(5))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10003),
                        extId = UUID(1L, 10003L),
                        startNode = DbNodeConnection(NodePortType.A, node5),
                        endNode = DbNodeConnection(NodePortType.A, node2),
                        segments = listOf(segment(jointLoc(5), jointLoc(2))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10004),
                        extId = UUID(1L, 10004L),
                        startNode = DbNodeConnection(NodePortType.B, node2),
                        endNode = DbNodeConnection(NodePortType.A, track1EndNode),
                        segments = listOf(segment(jointLoc(2), track1End)),
                    ),
                ),
        )
    // Track 2: 4 -> 5 -> 3
    val trackGeom2 =
        DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(222),
            edges =
                listOf(
                    DbLayoutEdge(
                        id = IntId(10005),
                        extId = UUID(1L, 10005L),
                        startNode = DbNodeConnection(NodePortType.A, track2StartNode),
                        endNode = DbNodeConnection(NodePortType.B, node4),
                        segments = listOf(segment(track2Start, jointLoc(4))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10006),
                        extId = UUID(1L, 10006L),
                        startNode = DbNodeConnection(NodePortType.A, node4),
                        endNode = DbNodeConnection(NodePortType.A, node5),
                        segments = listOf(segment(jointLoc(4), jointLoc(5))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10007),
                        extId = UUID(1L, 10007L),
                        startNode = DbNodeConnection(NodePortType.A, node5),
                        endNode = DbNodeConnection(NodePortType.A, node3),
                        segments = listOf(segment(jointLoc(5), jointLoc(3))),
                    ),
                    DbLayoutEdge(
                        id = IntId(10008),
                        extId = UUID(1L, 10008L),
                        startNode = DbNodeConnection(NodePortType.B, node3),
                        endNode = DbNodeConnection(NodePortType.A, track2EndNode),
                        segments = listOf(segment(jointLoc(3), track2End)),
                    ),
                ),
        )
    return trackGeom1 to trackGeom2
}

private fun krvCurvedSlipTracks(
    switchNodes: Map<Int, DbSwitchNode>,
    jointLoc: (Int) -> Point,
): Pair<DbLocationTrackGeometry, DbLocationTrackGeometry> {
    val trackGeom3 =
        DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(333),
            edges =
                listOf(
                    DbLayoutEdge(
                        id = IntId(10009),
                        extId = UUID(1L, 10009L),
                        startNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(1)),
                        endNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(3)),
                        segments = listOf(segment(jointLoc(1), jointLoc(3))),
                    )
                ),
        )
    val trackGeom4 =
        DbLocationTrackGeometry(
            trackRowVersion = layoutRowVersion(444),
            edges =
                listOf(
                    DbLayoutEdge(
                        id = IntId(10010),
                        extId = UUID(1L, 10010L),
                        startNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(4)),
                        endNode = DbNodeConnection(NodePortType.A, switchNodes.getValue(2)),
                        segments = listOf(segment(jointLoc(4), jointLoc(2))),
                    )
                ),
        )
    return trackGeom3 to trackGeom4
}

private fun assertKrvSlipSwitchPaths(
    graph: RoutingGraph,
    structure: SwitchStructure,
    geometries: List<DbLocationTrackGeometry>,
    starts: List<Point>,
    ends: List<Point>,
) {
    val (trackGeom1, trackGeom2, trackGeom3, trackGeom4) = geometries
    val (track1Start, track2Start) = starts
    val (track1End, track2End) = ends

    assertNotNull(graph.findPath(trackCacheHit(trackGeom1, track1Start), trackCacheHit(trackGeom1, track1End)))
    assertNotNull(graph.findPath(trackCacheHit(trackGeom2, track2Start), trackCacheHit(trackGeom2, track2End)))

    val slipStart13 = trackCacheHit(trackGeom1, track1Start)
    val slipEnd13 = trackCacheHit(trackGeom2, track2End)
    val slipRoute13 = graph.findPath(slipStart13, slipEnd13)
    assertNotNull(slipRoute13)
    val alignment13 =
        structure.alignments.single { alignment ->
            alignment.jointNumbers == listOf(JointNumber(1), JointNumber(5), JointNumber(3))
        }
    assertEquals(
        listOf(alignment13),
        graph
            .findPathEdges(slipStart13, slipEnd13)!!
            .filterIsInstance<PartialSwitchInternalEdge>()
            .map { edge -> edge.alignment.alignment }
            .distinct(),
    )
    assertEquals(
        listOf(
            RouteSection(trackGeom1.trackId, trackGeom1.edgesWithM.first().second, UP),
            RouteSection(trackGeom3.trackId, trackGeom3.edgesWithM.single().second, UP),
            RouteSection(trackGeom2.trackId, trackGeom2.edgesWithM.last().second, UP),
        ),
        slipRoute13.sections,
    )

    val slipStart42 = trackCacheHit(trackGeom2, track2Start)
    val slipEnd42 = trackCacheHit(trackGeom1, track1End)
    val slipRoute42 = graph.findPath(slipStart42, slipEnd42)
    assertNotNull(slipRoute42)
    val alignment42 =
        structure.alignments.single { alignment ->
            alignment.jointNumbers == listOf(JointNumber(4), JointNumber(5), JointNumber(2))
        }
    assertEquals(
        listOf(alignment42),
        graph
            .findPathEdges(slipStart42, slipEnd42)!!
            .filterIsInstance<PartialSwitchInternalEdge>()
            .map { edge -> edge.alignment.alignment }
            .distinct(),
    )
    assertEquals(
        listOf(
            RouteSection(trackGeom2.trackId, trackGeom2.edgesWithM.first().second, UP),
            RouteSection(trackGeom4.trackId, trackGeom4.edgesWithM.single().second, UP),
            RouteSection(trackGeom1.trackId, trackGeom1.edgesWithM.last().second, UP),
        ),
        slipRoute42.sections,
    )

    val route14 = graph.findPath(trackCacheHit(trackGeom1, track1Start), trackCacheHit(trackGeom2, track2Start))
    assertNotNull(route14)
    assertEquals(4, route14.sections.size)
}

private fun trackCacheHit(geometry: DbLocationTrackGeometry, target: Point): PointNearTrack =
    geometry.getClosestPoint(target)!!.first.let { closest ->
        PointNearTrack(
            track = locationTrack(IntId(1), id = geometry.trackId),
            geometry = geometry,
            closestPoint = closest,
            distance = lineLength(target, closest),
        )
    }

private fun edgeSwitch(switchId: Int, direction: EdgeDirection, alignment: SwitchStructureAlignment) =
    SwitchInternalEdge(RoutingSwitchAlignment(IntId(switchId), alignment), direction)

private fun <T : LayoutAsset<T>> layoutRowVersion(id: Int): LayoutRowVersion<T> =
    LayoutRowVersion(LayoutRowId(IntId(id), LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)), 1)
