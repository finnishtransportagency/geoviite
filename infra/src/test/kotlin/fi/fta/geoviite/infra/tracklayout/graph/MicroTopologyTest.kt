package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.SWITCH
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType.TRACK_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection.ASCENDING
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDirection.DESCENDING
import fi.fta.geoviite.infra.tracklayout.locationTrack
import java.nio.charset.StandardCharsets.UTF_8
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory

/**
 * The micro level topology is a deterministic simplification of the nano level topology, so the whole simplification
 * can be tested without a database: each test describes a nano level graph, wraps it in a [CachedTopology] and asserts
 * what the lazily derived micro level looks like.
 *
 * The rules under test (GVT-3703):
 * 1. A nano node survives to the micro level if it is a track boundary node or if it has at least one MAIN role switch
 *    joint. Node degree is not a selection criterion.
 * 2. A node that would otherwise be dropped is still kept if it does not have exactly two incident edge ends, because
 *    the surrounding edges cannot be combined into a single edge there.
 * 3. The edges around a dropped node are combined into one micro edge whose length is the sum of the combined lengths
 *    and whose track list is the union of the combined track lists.
 * 4. The combined edge's canonical direction is the direction of the edges that are not switch internal, i.e. whose end
 *    nodes do not share a switch. If the chain has several such edges, or none at all, the chain is oriented to follow
 *    the own ascending direction of the candidate with the lexicographically smallest UUID.
 * 5. The combined edge's id is [microEdgeUuid] of the combined nano edge ids, in canonical traversal order.
 * 6. Transitions of the surviving nodes are copied as-is, with nano edge references replaced by the micro edges that
 *    contain them and the direction converted to the micro edge's canonical direction. Nothing is added or removed, and
 *    duplicates are collapsed.
 * 7. A micro node lists each of its switches exactly once, preferring the MAIN role joint link.
 */
class MicroTopologyTest {

    @Test
    fun `Micro level keeps a plain track between two track ends unchanged`() {
        val nano = buildTopology {
            val west = trackEndNode("west-end", Point(0.0, 0.0))
            val east = trackEndNode("east-end", Point(100.0, 0.0))
            val track = edge("track-edge", west, east, 100.0, MAIN_TRACK)

            uTurn(west, desc(track))
            uTurn(east, asc(track))
        }

        val micro = CachedTopology(nano).micro

        assertEquals(nano.summary(), micro.summary())
    }

    @Test
    fun `Micro level keeps a node that joins the ends of two different tracks`() {
        val nano = buildTopology {
            val west = trackEndNode("west-end", Point(0.0, 0.0))
            val joint = trackEndNode("track-joint", Point(100.0, 0.0))
            val east = trackEndNode("east-end", Point(200.0, 0.0))
            val westEdge = edge("west-edge", west, joint, 100.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", joint, east, 100.0, CONTINUATION_TRACK)

            uTurn(west, desc(westEdge))
            uTurn(east, asc(eastEdge))
            // The tracks continue into each other, but no U-turn back onto the arriving track is allowed
            connectThrough(joint, asc(westEdge), asc(eastEdge))
        }

        val micro = CachedTopology(nano).micro

        assertEquals(nano.summary(), micro.summary())
    }

    @Test
    fun `Micro level drops the connection joint nodes of a fully linked switch`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro

        assertEquals(
            setOf(id("west-end"), id("joint-1"), id("east-end"), id("branch-end")),
            micro.nodes.map { node -> node.id }.toSet(),
        )
    }

    @Test
    fun `Micro level combines the switch lines of a fully linked switch into the tracks behind them`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro

        assertEquals(
            setOf(
                EdgeSummary(
                    id = id("west-edge"),
                    startNode = id("west-end"),
                    endNode = id("joint-1"),
                    length = 100.0,
                    tracks = listOf(MAIN_TRACK.id),
                ),
                EdgeSummary(
                    id = microEdgeUuid(listOf(id("line-1-2"), id("east-edge"))),
                    startNode = id("joint-1"),
                    endNode = id("east-end"),
                    length = 34.0 + 66.0,
                    tracks = listOf(MAIN_TRACK.id),
                ),
                EdgeSummary(
                    id = microEdgeUuid(listOf(id("line-1-3"), id("branch-edge"))),
                    startNode = id("joint-1"),
                    endNode = id("branch-end"),
                    length = 33.0 + 67.0,
                    tracks = listOf(BRANCH_TRACK.id),
                ),
            ),
            micro.edges.map { edge -> edge.summary() }.toSet(),
        )
    }

    @Test
    fun `Micro level rewrites the switch transitions to refer to the combined edges`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro
        val straight = microEdgeUuid(listOf(id("line-1-2"), id("east-edge")))
        val branch = microEdgeUuid(listOf(id("line-1-3"), id("branch-edge")))

        assertEquals(
            setOf(
                asc(id("west-edge")) to asc(straight),
                desc(straight) to desc(id("west-edge")),
                asc(id("west-edge")) to asc(branch),
                desc(branch) to desc(id("west-edge")),
            ),
            micro.node(id("joint-1")).transitionSummary(),
        )
    }

    @Test
    fun `Micro level keeps the U-turns at the track ends behind a switch`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro
        val straight = microEdgeUuid(listOf(id("line-1-2"), id("east-edge")))
        val branch = microEdgeUuid(listOf(id("line-1-3"), id("branch-edge")))

        assertEquals(
            setOf(asc(straight) to desc(straight)),
            micro.node(id("east-end")).transitionSummary(),
        )
        assertEquals(
            setOf(asc(branch) to desc(branch)),
            micro.node(id("branch-end")).transitionSummary(),
        )
        assertEquals(
            setOf(desc(id("west-edge")) to asc(id("west-edge"))),
            micro.node(id("west-end")).transitionSummary(),
        )
    }

    @Test
    fun `Micro level keeps the node type, location and switches of the surviving nodes`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro

        assertEquals(
            NodeSummary(
                id = id("joint-1"),
                type = SWITCH,
                location = Point(100.0, 0.0),
                switches = listOf(mainJoint(SWITCH_A, 1)),
            ),
            micro.node(id("joint-1")).summary(),
        )
        assertEquals(
            NodeSummary(
                id = id("east-end"),
                type = TRACK_BOUNDARY,
                location = Point(200.0, 0.0),
                switches = emptyList(),
            ),
            micro.node(id("east-end")).summary(),
        )
    }

    @Test
    fun `Combined edge follows the direction of the track edge that arrives at the dropped node`() {
        // The track behind joint 2 runs towards the switch: joint 2 is the end node of the track edge
        val nano = buildTopology {
            val joint1 = switchNode("joint-1", Point(100.0, 0.0), mainJoint(SWITCH_A, 1))
            val joint2 = switchNode("joint-2", Point(134.0, 0.0), connectionJoint(SWITCH_A, 2))
            val west = trackEndNode("west-end", Point(0.0, 0.0))
            val east = trackEndNode("east-end", Point(200.0, 0.0))
            val westEdge = edge("west-edge", west, joint1, 100.0, MAIN_TRACK)
            val line12 = edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", east, joint2, 66.0, CONTINUATION_TRACK)

            uTurn(west, desc(westEdge))
            uTurn(east, desc(eastEdge))
            connectThrough(joint1, asc(westEdge), asc(line12))
            connectThrough(joint2, asc(line12), desc(eastEdge))
        }

        val combined = microEdgeUuid(listOf(id("east-edge"), id("line-1-2")))
        val micro = CachedTopology(nano).micro
        assertEquals(
            EdgeSummary(
                id = combined,
                startNode = id("east-end"),
                endNode = id("joint-1"),
                length = 66.0 + 34.0,
                tracks = listOf(CONTINUATION_TRACK.id, MAIN_TRACK.id),
            ),
            micro.edge(combined).summary(),
        )
        assertEquals(
            setOf(asc(id("west-edge")) to desc(combined), asc(combined) to desc(id("west-edge"))),
            micro.node(id("joint-1")).transitionSummary(),
        )
    }

    @Test
    fun `Combined edge spans several dropped nodes and follows the track edge between them`() {
        // Two switches linked front joint to front joint through a short track edge between their joint 2 nodes
        val nano = buildTopology {
            val aJoint1 = switchNode("a-joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val aJoint2 = switchNode("a-joint-2", Point(34.0, 0.0), connectionJoint(SWITCH_A, 2))
            val bJoint2 = switchNode("b-joint-2", Point(54.0, 0.0), connectionJoint(SWITCH_B, 2))
            val bJoint1 = switchNode("b-joint-1", Point(88.0, 0.0), mainJoint(SWITCH_B, 1))
            val aLine = edge("a-line-1-2", aJoint1, aJoint2, 34.0, MAIN_TRACK)
            val middle = edge("middle", aJoint2, bJoint2, 20.0, MAIN_TRACK, CONTINUATION_TRACK)
            val bLine = edge("b-line-1-2", bJoint1, bJoint2, 34.0, CONTINUATION_TRACK)

            connectThrough(aJoint2, asc(aLine), asc(middle))
            connectThrough(bJoint2, asc(middle), desc(bLine))
        }

        val micro = CachedTopology(nano).micro
        val combined = microEdgeUuid(listOf(id("a-line-1-2"), id("middle"), id("b-line-1-2")))
        assertEquals(
            EdgeSummary(
                id = combined,
                startNode = id("a-joint-1"),
                endNode = id("b-joint-1"),
                length = 34.0 + 20.0 + 34.0,
                tracks = listOf(MAIN_TRACK.id, CONTINUATION_TRACK.id),
            ),
            micro.edge(combined).summary(),
        )
        assertEquals(setOf(id("a-joint-1"), id("b-joint-1")), micro.nodes.map { node -> node.id }.toSet())
    }

    @Test
    fun `Combined edge of two switch internal edges is oriented by the smallest edge id`() {
        // Two switches linked directly joint 2 to joint 2: neither combined edge is outside a switch, so the chain is
        // oriented by the lexicographically smallest nano edge id, which here is b-line-1-2
        val nano = buildTopology {
            val aJoint1 = switchNode("a-joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val shared =
                switchNode(
                    "shared-joint-2",
                    Point(34.0, 0.0),
                    connectionJoint(SWITCH_A, 2),
                    connectionJoint(SWITCH_B, 2),
                )
            val bJoint1 = switchNode("b-joint-1", Point(68.0, 0.0), mainJoint(SWITCH_B, 1))
            val aLine = edge("a-line-1-2", aJoint1, shared, 34.0, MAIN_TRACK)
            val bLine = edge("b-line-1-2", bJoint1, shared, 34.0, MAIN_TRACK)

            connectThrough(shared, asc(aLine), desc(bLine))
        }

        assertEquals("04e94b61-ab3d-3aba-b39d-7c03d371c765", id("b-line-1-2").toString())
        assertEquals("9c86b9c5-e00c-3002-8224-7802cb5668c1", id("a-line-1-2").toString())

        val micro = CachedTopology(nano).micro
        val combined = microEdgeUuid(listOf(id("b-line-1-2"), id("a-line-1-2")))
        assertEquals(
            EdgeSummary(
                id = combined,
                startNode = id("b-joint-1"),
                endNode = id("a-joint-1"),
                length = 68.0,
                tracks = listOf(MAIN_TRACK.id),
            ),
            micro.edge(combined).summary(),
        )
    }

    @Test
    fun `Micro edge id is a stable UUIDv5 of the combined nano edge ids`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro

        assertEquals(
            UUID.fromString("27e1fcbc-ecf0-5761-b90c-d65e578a2c1b"),
            microEdgeUuid(listOf(id("line-1-2"), id("east-edge"))),
        )
        assertEquals(
            setOf(
                id("west-edge"),
                microEdgeUuid(listOf(id("line-1-2"), id("east-edge"))),
                microEdgeUuid(listOf(id("line-1-3"), id("branch-edge"))),
            ),
            micro.edges.map { edge -> edge.id }.toSet(),
        )
    }

    @Test
    fun `Micro edge track list is the union of the combined track lists, without duplicates`() {
        val nano = buildTopology {
            val joint1 = switchNode("joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val joint2 = switchNode("joint-2", Point(34.0, 0.0), connectionJoint(SWITCH_A, 2))
            val east = trackEndNode("east-end", Point(100.0, 0.0))
            edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK, DUPLICATE_TRACK)
            edge("east-edge", joint2, east, 66.0, MAIN_TRACK, CONTINUATION_TRACK)
        }

        val micro = CachedTopology(nano).micro
        val combined = microEdgeUuid(listOf(id("line-1-2"), id("east-edge")))
        assertEquals(
            listOf(MAIN_TRACK.id, DUPLICATE_TRACK.id, CONTINUATION_TRACK.id),
            micro.edge(combined).trackReferences.map { reference -> reference.track.id },
        )
    }

    @Test
    fun `Micro level keeps a switch node whose main joint has only one edge`() {
        // A partially linked switch: only the straight line is linked, nothing continues behind joint 1
        val nano = buildTopology {
            val joint1 = switchNode("joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val joint2 = switchNode("joint-2", Point(34.0, 0.0), connectionJoint(SWITCH_A, 2))
            val east = trackEndNode("east-end", Point(100.0, 0.0))
            val line12 = edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", joint2, east, 66.0, MAIN_TRACK)

            connectThrough(joint2, asc(line12), asc(eastEdge))
            uTurn(east, asc(eastEdge))
        }

        val micro = CachedTopology(nano).micro

        assertEquals(setOf(id("joint-1"), id("east-end")), micro.nodes.map { node -> node.id }.toSet())
    }

    @Test
    fun `Micro level keeps a droppable node that has only one edge`() {
        // Nothing is linked behind joint 2, so the switch line cannot be combined with anything
        val nano = buildTopology {
            val joint1 = switchNode("joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val joint2 = switchNode("joint-2", Point(34.0, 0.0), connectionJoint(SWITCH_A, 2))
            edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK)
        }

        val micro = CachedTopology(nano).micro

        assertEquals(nano.summary(), micro.summary())
    }

    @Test
    fun `Micro level keeps a droppable node that has three edges`() {
        // A duplicate track ends at the connection joint: the three edges cannot be combined into one
        val nano = buildTopology {
            val joint1 = switchNode("joint-1", Point(0.0, 0.0), mainJoint(SWITCH_A, 1))
            val joint2 = switchNode("joint-2", Point(34.0, 0.0), connectionJoint(SWITCH_A, 2))
            val east = trackEndNode("east-end", Point(100.0, 0.0))
            val duplicateEnd = trackEndNode("duplicate-end", Point(100.0, 1.0))
            val line12 = edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", joint2, east, 66.0, MAIN_TRACK)
            val duplicateEdge = edge("duplicate-edge", joint2, duplicateEnd, 66.0, DUPLICATE_TRACK)

            connectThrough(joint2, asc(line12), asc(eastEdge))
            connectThrough(joint2, asc(line12), asc(duplicateEdge))
            uTurn(east, asc(eastEdge))
            uTurn(duplicateEnd, asc(duplicateEdge))
        }

        val micro = CachedTopology(nano).micro

        assertEquals(nano.summary(), micro.summary())
    }

    @Test
    fun `Every switch library category is covered by micro simplification tests`() {
        assertEquals(
            SwitchCategory.entries.toSet(),
            switchStructures.map(SwitchStructureData::category).toSet(),
        )
    }

    @TestFactory
    fun `Every switch library structure is correctly simplified`(): List<DynamicTest> =
        switchStructures.map { structure ->
            DynamicTest.dynamicTest(structure.type.toString()) {
                val fixture = switchNanoFixture(structure)
                val nano = fixture.nano
                val micro = CachedTopology(nano).micro

                assertSwitchSimplification(structure, fixture, micro)
            }
        }

    @Test
    fun `Micro node lists each switch once, preferring the main joint link`() {
        val nano = buildTopology {
            val combination =
                switchNode(
                    "combination-node",
                    Point(0.0, 0.0),
                    mainJoint(SWITCH_A, 1),
                    connectionJoint(SWITCH_B, 2),
                )
            val overlapping =
                switchNode(
                    "overlapping-node",
                    Point(50.0, 0.0),
                    connectionJoint(SWITCH_A, 2),
                    mainJoint(SWITCH_A, 1),
                )
            edge("line", combination, overlapping, 50.0, MAIN_TRACK)
        }

        val micro = CachedTopology(nano).micro
        assertEquals(
            listOf(mainJoint(SWITCH_A, 1), connectionJoint(SWITCH_B, 2)),
            micro.node(id("combination-node")).switchReferences.map { reference -> reference.switch },
        )
        assertEquals(
            listOf(mainJoint(SWITCH_A, 1)),
            micro.node(id("overlapping-node")).switchReferences.map { reference -> reference.switch },
        )
    }

    @Test
    fun `MAIN-MAIN combination node keeps both switches and their cross-switch transitions`() {
        val nano = buildTopology {
            val combination =
                switchNode("combination", Point(50.0, 0.0), mainJoint(SWITCH_A, 1), mainJoint(SWITCH_B, 1))
            val aJoint2 = switchNode("a-joint-2", Point(20.0, 0.0), connectionJoint(SWITCH_A, 2))
            val bJoint2 = switchNode("b-joint-2", Point(80.0, 0.0), connectionJoint(SWITCH_B, 2))
            val westEnd = trackEndNode("west-end", Point(0.0, 0.0))
            val eastEnd = trackEndNode("east-end", Point(100.0, 0.0))
            val aLine = edge("a-line", combination, aJoint2, 30.0, MAIN_TRACK)
            val bLine = edge("b-line", combination, bJoint2, 30.0, MAIN_TRACK)
            val westEdge = edge("west-edge", westEnd, aJoint2, 20.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", bJoint2, eastEnd, 20.0, MAIN_TRACK)

            connectThrough(combination, desc(aLine), asc(bLine))
            connectThrough(aJoint2, asc(aLine), desc(westEdge))
            connectThrough(bJoint2, asc(bLine), asc(eastEdge))
            uTurn(westEnd, desc(westEdge))
            uTurn(eastEnd, asc(eastEdge))
        }

        val micro = CachedTopology(nano).micro
        val westMicro = microEdgeUuid(listOf(id("west-edge"), id("a-line")))
        val eastMicro = microEdgeUuid(listOf(id("b-line"), id("east-edge")))
        val combination = micro.node(id("combination"))

        assertEquals(
            listOf(mainJoint(SWITCH_A, 1), mainJoint(SWITCH_B, 1)),
            combination.switchReferences.map { reference -> reference.switch },
        )
        assertEquals(
            setOf(asc(westMicro) to asc(eastMicro), desc(eastMicro) to desc(westMicro)),
            combination.transitionSummary(),
        )
    }

    @Test
    fun `MAIN-NON_MAIN combination node keeps both switch references and only the MAIN joint makes it survive`() {
        val nano = buildTopology {
            val combination =
                switchNode(
                    "combination",
                    Point(50.0, 0.0),
                    mainJoint(SWITCH_A, 1),
                    connectionJoint(SWITCH_B, 2),
                )
            val aJoint2 = switchNode("a-joint-2", Point(20.0, 0.0), connectionJoint(SWITCH_A, 2))
            val bMain = switchNode("b-main", Point(80.0, 0.0), mainJoint(SWITCH_B, 1))
            val westEnd = trackEndNode("west-end", Point(0.0, 0.0))
            val eastEnd = trackEndNode("east-end", Point(100.0, 0.0))
            val aLine = edge("a-line", combination, aJoint2, 30.0, MAIN_TRACK)
            val bLine = edge("b-line", bMain, combination, 30.0, MAIN_TRACK)
            val westEdge = edge("west-edge", westEnd, aJoint2, 20.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", bMain, eastEnd, 20.0, MAIN_TRACK)

            connectThrough(combination, desc(aLine), desc(bLine))
            connectThrough(aJoint2, asc(aLine), desc(westEdge))
            connectThrough(bMain, desc(bLine), asc(eastEdge))
            uTurn(westEnd, desc(westEdge))
            uTurn(eastEnd, asc(eastEdge))
        }

        val micro = CachedTopology(nano).micro
        val westMicro = microEdgeUuid(listOf(id("west-edge"), id("a-line")))
        val combination = micro.node(id("combination"))

        assertEquals(
            listOf(mainJoint(SWITCH_A, 1), connectionJoint(SWITCH_B, 2)),
            combination.switchReferences.map { reference -> reference.switch },
        )
        assertEquals(
            listOf(mainJoint(SWITCH_B, 1)),
            micro.node(id("b-main")).switchReferences.map { reference -> reference.switch },
        )
        assertEquals(
            setOf(asc(westMicro) to desc(id("b-line")), asc(id("b-line")) to desc(westMicro)),
            combination.transitionSummary(),
        )
    }

    @Test
    fun `Micro level keeps the transitions between the two switches of a combination node`() {
        // Two switch front joints at the same place: the node allows moving from one switch to the other
        val nano = buildTopology {
            val combination =
                switchNode("combination-node", Point(50.0, 0.0), mainJoint(SWITCH_A, 1), mainJoint(SWITCH_B, 1))
            val aJoint2 = switchNode("a-joint-2", Point(16.0, 0.0), connectionJoint(SWITCH_A, 2))
            val bJoint2 = switchNode("b-joint-2", Point(84.0, 0.0), connectionJoint(SWITCH_B, 2))
            val westEnd = trackEndNode("west-end", Point(0.0, 0.0))
            val eastEnd = trackEndNode("east-end", Point(100.0, 0.0))
            val aLine = edge("a-line-1-2", combination, aJoint2, 34.0, MAIN_TRACK)
            val bLine = edge("b-line-1-2", combination, bJoint2, 34.0, MAIN_TRACK)
            val westEdge = edge("west-edge", westEnd, aJoint2, 16.0, MAIN_TRACK)
            val eastEdge = edge("east-edge", bJoint2, eastEnd, 16.0, MAIN_TRACK)

            connectThrough(combination, desc(aLine), asc(bLine))
            connectThrough(aJoint2, asc(aLine), desc(westEdge))
            connectThrough(bJoint2, asc(bLine), asc(eastEdge))
            uTurn(westEnd, desc(westEdge))
            uTurn(eastEnd, asc(eastEdge))
        }

        // west-edge is the only non switch internal edge in its chain, so the combined edge runs west-end -> node
        val micro = CachedTopology(nano).micro
        val westMicro = microEdgeUuid(listOf(id("west-edge"), id("a-line-1-2")))
        val eastMicro = microEdgeUuid(listOf(id("b-line-1-2"), id("east-edge")))
        assertEquals(
            setOf(asc(westMicro) to asc(eastMicro), desc(eastMicro) to desc(westMicro)),
            micro.node(id("combination-node")).transitionSummary(),
        )
    }

    @Test
    fun `Micro level does not produce duplicate nodes, edges or transitions`() {
        val nano = completeSwitchNano()
        val micro = CachedTopology(nano).micro

        assertEquals(micro.nodes.map { node -> node.id }.distinct().size, micro.nodes.size)
        assertEquals(micro.edges.map { edge -> edge.id }.distinct().size, micro.edges.size)
        micro.nodes.forEach { node ->
            assertEquals(node.transitions.distinct().size, node.transitions.size, "Node ${node.id}")
        }
    }

    @Test
    fun `Micro level is derived without changing the nano level`() {
        val nano = completeSwitchNano()
        val nanoBefore = nano.summary()

        val micro = CachedTopology(nano).micro

        assertEquals(nanoBefore, nano.summary())
        assertNotSame(nano, micro)
    }

    @Test
    fun `Micro level is derived deterministically`() {
        val firstNano = completeSwitchNano()
        val secondNano = completeSwitchNano()
        val firstMicro = CachedTopology(firstNano).micro
        val secondMicro = CachedTopology(secondNano).micro

        assertEquals(firstMicro.summary(), secondMicro.summary())
    }
}

/**
 * A fully linked YV switch: the straight line 1-5-2 continues east and the branch line 1-3 continues to a side track.
 * Only the switch's main joint node survives the simplification.
 */
private fun completeSwitchNano(): Topology = buildTopology {
    val westEnd = trackEndNode("west-end", Point(0.0, 0.0))
    val joint1 = switchNode("joint-1", Point(100.0, 0.0), mainJoint(SWITCH_A, 1))
    val joint2 = switchNode("joint-2", Point(134.0, 0.0), connectionJoint(SWITCH_A, 2))
    val joint3 = switchNode("joint-3", Point(133.0, 3.0), connectionJoint(SWITCH_A, 3))
    val eastEnd = trackEndNode("east-end", Point(200.0, 0.0))
    val branchEnd = trackEndNode("branch-end", Point(200.0, 10.0))

    val westEdge = edge("west-edge", westEnd, joint1, 100.0, MAIN_TRACK)
    val line12 = edge("line-1-2", joint1, joint2, 34.0, MAIN_TRACK)
    val line13 = edge("line-1-3", joint1, joint3, 33.0, BRANCH_TRACK)
    val eastEdge = edge("east-edge", joint2, eastEnd, 66.0, MAIN_TRACK)
    val branchEdge = edge("branch-edge", joint3, branchEnd, 67.0, BRANCH_TRACK)

    uTurn(westEnd, desc(westEdge))
    uTurn(eastEnd, asc(eastEdge))
    uTurn(branchEnd, asc(branchEdge))

    connectThrough(joint1, asc(westEdge), asc(line12))
    connectThrough(joint1, asc(westEdge), asc(line13))
    connectThrough(joint2, asc(line12), asc(eastEdge))
    connectThrough(joint3, asc(line13), asc(branchEdge))
}

private enum class SwitchCategory(val prefix: String) {
    KRV("KRV"),
    KV("KV"),
    RR("RR"),
    SKV("SKV"),
    SRR("SRR"),
    TYV("TYV"),
    UKV("UKV"),
    YRV("YRV"),
    YV("YV"),
    EV_SJ("EV-SJ"),
}

private fun SwitchStructureData.category(): SwitchCategory =
    SwitchCategory.entries.single { category -> type.toString().startsWith(category.prefix) }

private data class SwitchNanoFixture(
    val nano: Topology,
    val mainNode: UUID,
    val trackEnds: Map<JointNumber, UUID>,
)

/**
 * Builds one fully linked switch as a nano graph. Every external switch joint is a port with an outside track edge, and
 * every non-main port has a switch-internal edge to the presentation point. The transition pairs at the presentation
 * point are taken directly from the structure's alignments. This makes simple, crossing and multi-route switch
 * categories use the same graph shape while retaining their category-specific routing rules.
 */
private fun switchNanoFixture(structure: SwitchStructureData): SwitchNanoFixture {
    val endJoints = structure.endJointNumbers.sorted()
    val mainJoint = structure.presentationJointNumber
    val mainNode = id("switch-main")
    val switchNodes = endJoints.associateWith { joint ->
        if (joint == mainJoint) mainNode else id("switch-joint-${joint.intValue}")
    }
    val trackEnds = endJoints.associateWith { joint -> id("track-end-${joint.intValue}") }
    val outsideEdges = endJoints.associateWith { joint -> id("outside-${joint.intValue}") }
    val switchInternalEdges =
        endJoints
            .filter { joint -> joint != mainJoint }
            .associateWith { joint -> id("switch-internal-${joint.intValue}") }

    val nano = buildTopology {
        addNode(
            id = mainNode,
            type = SWITCH,
            location = Point(0.0, 0.0),
            switchReferences =
                listOf(
                    TopologySwitchReference(
                        switch = SwitchLink(SWITCH_A, SwitchJointRole.MAIN, mainJoint),
                        oid = null,
                    )
                ),
        )
        endJoints.forEachIndexed { index, joint ->
            if (joint != mainJoint) {
                addNode(
                    id = switchNodes.getValue(joint),
                    type = SWITCH,
                    location = Point(index + 1.0, 0.0),
                    switchReferences =
                        listOf(
                            TopologySwitchReference(
                                switch = SwitchLink(SWITCH_A, SwitchJointRole.CONNECTION, joint),
                                oid = null,
                            )
                        ),
                )
                addEdge(
                    id = switchInternalEdges.getValue(joint),
                    startNode = mainNode,
                    endNode = switchNodes.getValue(joint),
                    length = index + 1.0,
                    trackReferences = listOf(TopologyLocationTrackReference(MAIN_TRACK, null)),
                )
            }
            addNode(
                id = trackEnds.getValue(joint),
                type = TRACK_BOUNDARY,
                location = Point(index.toDouble(), 100.0),
                switchReferences = emptyList(),
            )
            addEdge(
                id = outsideEdges.getValue(joint),
                startNode = switchNodes.getValue(joint),
                endNode = trackEnds.getValue(joint),
                length = 100.0,
                trackReferences = listOf(TopologyLocationTrackReference(MAIN_TRACK, null)),
            )
            uTurn(trackEnds.getValue(joint), asc(outsideEdges.getValue(joint)))
        }
        endJoints
            .filter { joint -> joint != mainJoint }
            .forEach { joint ->
                connectThrough(
                    switchNodes.getValue(joint),
                    asc(switchInternalEdges.getValue(joint)),
                    asc(outsideEdges.getValue(joint)),
                )
            }
        structure.alignments.forEach { alignment ->
            val first = alignment.jointNumbers.first()
            val last = alignment.jointNumbers.last()
            val firstOut =
                if (first == mainJoint) asc(outsideEdges.getValue(first)) else asc(switchInternalEdges.getValue(first))
            val lastOut =
                if (last == mainJoint) asc(outsideEdges.getValue(last)) else asc(switchInternalEdges.getValue(last))
            connectThrough(mainNode, firstOut.reversed(), lastOut)
        }
    }
    return SwitchNanoFixture(nano, mainNode, trackEnds)
}

private fun assertSwitchSimplification(
    structure: SwitchStructureData,
    fixture: SwitchNanoFixture,
    micro: Topology,
) {
    val mainJoint = structure.presentationJointNumber
    val mainNode = fixture.mainNode
    val expectedNodeIds = fixture.trackEnds.values.toSet() + mainNode
    val expectedEdgeNodePairs =
        structure.endJointNumbers.map { joint -> nodePair(mainNode, fixture.trackEnds.getValue(joint)) }.toSet()

    assertEquals(expectedNodeIds, micro.nodes.map(TopologyNode::id).toSet(), structure.type.toString())
    assertEquals(
        expectedEdgeNodePairs,
        micro.edges.map { edge -> nodePair(edge.startNode.id, edge.endNode.id) }.toSet(),
        structure.type.toString(),
    )
    assertEquals(
        listOf(SwitchLink(SWITCH_A, SwitchJointRole.MAIN, mainJoint)),
        micro.node(mainNode).switchReferences.map { reference -> reference.switch },
        structure.type.toString(),
    )
    val edgesByTrackEnd =
        fixture.trackEnds.mapValues { (_, trackEnd) ->
            micro.edges.single { edge -> edge.startNode.id == trackEnd || edge.endNode.id == trackEnd }
        }
    val expectedTransitions =
        structure.alignments
            .flatMap { alignment ->
                val first = outwardFrom(mainNode, edgesByTrackEnd.getValue(alignment.jointNumbers.first()))
                val last = outwardFrom(mainNode, edgesByTrackEnd.getValue(alignment.jointNumbers.last()))
                listOf(
                    first.reversed() to last,
                    last.reversed() to first,
                )
            }
            .toSet()
    assertEquals(
        expectedTransitions,
        micro.node(mainNode).transitionSummary(),
        structure.type.toString(),
    )
    assertTrackEndUTurns(structure, fixture, micro)
}

private fun outwardFrom(node: UUID, edge: TopologyEdge): EdgeRef =
    if (edge.startNode.id == node) asc(edge.id) else desc(edge.id)

private fun assertTrackEndUTurns(
    structure: SwitchStructureData,
    fixture: SwitchNanoFixture,
    micro: Topology,
) =
    fixture.trackEnds.values.forEach { trackEnd ->
        val transition = micro.node(trackEnd).transitions.single()
        assertEquals(transition.incomingEdge.edge.id, transition.outgoingEdge.edge.id, structure.type.toString())
        assertEquals(
            transition.incomingEdge.direction.reverse(),
            transition.outgoingEdge.direction,
            structure.type.toString(),
        )
    }

private fun nodePair(first: UUID, second: UUID): Pair<UUID, UUID> =
    if (first.toString() < second.toString()) first to second else second to first

private val SWITCH_A = IntId<LayoutSwitch>(1)
private val SWITCH_B = IntId<LayoutSwitch>(2)

private val TRACK_NUMBER = IntId<LayoutTrackNumber>(1)
private val MAIN_TRACK = testTrack(1)
private val BRANCH_TRACK = testTrack(2)
private val CONTINUATION_TRACK = testTrack(3)
private val DUPLICATE_TRACK = testTrack(4)

private fun testTrack(id: Int): LocationTrack =
    locationTrack(trackNumberId = TRACK_NUMBER, id = IntId(id), name = "track-$id")

private fun mainJoint(switch: IntId<LayoutSwitch>, joint: Int) =
    SwitchLink(switch, SwitchJointRole.MAIN, JointNumber(joint))

private fun connectionJoint(switch: IntId<LayoutSwitch>, joint: Int) =
    SwitchLink(switch, SwitchJointRole.CONNECTION, JointNumber(joint))

/** Node and edge ids are derived from readable names so that assertion failures can be traced back to the fixture. */
private fun id(name: String): UUID = UUID.nameUUIDFromBytes(name.toByteArray(UTF_8))

private fun TopologyBuilder.trackEndNode(name: String, location: Point): UUID =
    id(name).also { nodeId -> addNode(nodeId, TRACK_BOUNDARY, location, emptyList()) }

private fun TopologyBuilder.switchNode(name: String, location: Point, vararg switches: SwitchLink): UUID =
    id(name).also { nodeId ->
        addNode(
            nodeId,
            SWITCH,
            location,
            switches.map { switch -> TopologySwitchReference(switch, null) },
        )
    }

private fun TopologyBuilder.edge(
    name: String,
    startNode: UUID,
    endNode: UUID,
    length: Double,
    vararg tracks: LocationTrack,
): UUID =
    id(name).also { edgeId ->
        addEdge(
            edgeId,
            startNode,
            endNode,
            length,
            tracks.map { track -> TopologyLocationTrackReference(track, null) },
        )
    }

/** Adds the given transition through the node, as well as the same transition travelled in the opposite direction. */
private fun TopologyBuilder.connectThrough(node: UUID, incoming: EdgeRef, outgoing: EdgeRef) {
    transition(node, incoming, outgoing)
    transition(node, outgoing.reversed(), incoming.reversed())
}

private fun TopologyBuilder.transition(node: UUID, incoming: EdgeRef, outgoing: EdgeRef) =
    addTransition(node, incoming.edge, incoming.direction, outgoing.edge, outgoing.direction)

/** At the end of a track, the only allowed transition is a U-turn back along the arriving edge. */
private fun TopologyBuilder.uTurn(node: UUID, incoming: EdgeRef) =
    addTransition(node, incoming.edge, incoming.direction, incoming.edge, incoming.direction.reverse())

private data class EdgeRef(val edge: UUID, val direction: TopologyDirection) {
    fun reversed() = copy(direction = direction.reverse())
}

private fun asc(edge: UUID) = EdgeRef(edge, ASCENDING)

private fun desc(edge: UUID) = EdgeRef(edge, DESCENDING)

/**
 * The topology objects reference each other both ways, so they cannot be compared directly. These flat summaries carry
 * everything the simplification is responsible for.
 */
private data class EdgeSummary(
    val id: UUID,
    val startNode: UUID,
    val endNode: UUID,
    val length: Double,
    val tracks: List<DomainId<LocationTrack>>,
)

private data class NodeSummary(
    val id: UUID,
    val type: LayoutNodeType,
    val location: Point,
    val switches: List<SwitchLink>,
)

private data class TopologySummary(
    val nodes: Set<NodeSummary>,
    val edges: Set<EdgeSummary>,
    val transitions: Map<UUID, Set<Pair<EdgeRef, EdgeRef>>>,
)

private fun Topology.summary() =
    TopologySummary(
        nodes = nodes.map { node -> node.summary() }.toSet(),
        edges = edges.map { edge -> edge.summary() }.toSet(),
        transitions = nodes.associate { node -> node.id to node.transitionSummary() },
    )

private fun TopologyNode.summary() =
    NodeSummary(id, type, location, switchReferences.map(TopologySwitchReference::switch))

private fun TopologyEdge.summary() =
    EdgeSummary(
        id,
        startNode.id,
        endNode.id,
        length,
        trackReferences.map { reference -> reference.track.id },
    )

private fun TopologyNode.transitionSummary(): Set<Pair<EdgeRef, EdgeRef>> =
    transitions.map { transition -> transition.summary() }.toSet()

private fun TopologyTransition.summary(): Pair<EdgeRef, EdgeRef> =
    EdgeRef(incomingEdge.edge.id, incomingEdge.direction) to EdgeRef(outgoingEdge.edge.id, outgoingEdge.direction)

/**
 * The topology objects reference each other both ways, so the generated equality of a transition would walk the graph
 * references around in circles. This shadows [kotlin.collections.distinct] with a comparison of the flat summaries,
 * which is what duplicate transitions mean here anyway.
 */
private fun List<TopologyTransition>.distinct(): List<TopologyTransition> = distinctBy { transition ->
    transition.summary()
}

private fun Topology.node(id: UUID): TopologyNode = nodes.single { node -> node.id == id }

private fun Topology.edge(id: UUID): TopologyEdge = edges.single { edge -> edge.id == id }
