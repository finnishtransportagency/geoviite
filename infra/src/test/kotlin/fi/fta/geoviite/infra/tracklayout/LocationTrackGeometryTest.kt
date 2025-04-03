package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.CONNECTION
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MAIN
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LocationTrackGeometryTest {

    @Test
    fun `Start & End node content hash works`() {
        val track123 = IntId<LocationTrack>(123)
        val track124 = IntId<LocationTrack>(124)

        assertEquals(
            TmpTrackBoundaryNode(track123, START).contentHash,
            TmpTrackBoundaryNode(track123, START).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123, START)).contentHash,
            TmpTrackBoundaryNode(track123, START).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123, START)).contentHash,
            DbTrackBoundaryNode(IntId(654), TrackBoundary(track123, START)).contentHash,
        )
        assertNotEquals(
            TmpTrackBoundaryNode(track123, START).contentHash,
            TmpTrackBoundaryNode(track124, START).contentHash,
        )

        assertEquals(TmpTrackBoundaryNode(track123, END).contentHash, TmpTrackBoundaryNode(track123, END).contentHash)
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123, END)).contentHash,
            TmpTrackBoundaryNode(track123, END).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123, END)).contentHash,
            DbTrackBoundaryNode(IntId(654), TrackBoundary(track123, END)).contentHash,
        )
        assertNotEquals(
            TmpTrackBoundaryNode(track123, END).contentHash,
            TmpTrackBoundaryNode(track124, END).contentHash,
        )

        assertNotEquals(
            TmpTrackBoundaryNode(track123, START).contentHash,
            TmpTrackBoundaryNode(track123, END).contentHash,
        )
    }

    @Test
    fun `Switch node content hash works`() {
        val switch123Main1 = SwitchLink(IntId(123), MAIN, JointNumber(1))
        val switch124Main1 = SwitchLink(IntId(124), MAIN, JointNumber(1))
        val switch123Connection1 = SwitchLink(IntId(123), CONNECTION, JointNumber(1))
        val switch123Main2 = SwitchLink(IntId(123), MAIN, JointNumber(2))
        assertEquals(TmpSwitchNode(switch123Main1, null).contentHash, TmpSwitchNode(switch123Main1, null).contentHash)
        assertEquals(
            DbSwitchNode(IntId(789), switch123Main1, null).contentHash,
            TmpSwitchNode(switch123Main1, null).contentHash,
        )
        assertEquals(
            DbSwitchNode(IntId(789), switch123Main1, null).contentHash,
            DbSwitchNode(IntId(987), switch123Main1, null).contentHash,
        )
        assertNotEquals(TmpSwitchNode(switch123Main1, switch124Main1), TmpSwitchNode(switch124Main1, switch123Main1))
        assertNotEquals(TmpSwitchNode(switch123Main1, null), TmpSwitchNode(switch124Main1, null))
        assertNotEquals(TmpSwitchNode(switch123Main1, switch124Main1), TmpSwitchNode(switch123Main1, null))
        assertNotEquals(
            TmpSwitchNode(switch123Main1, null).contentHash,
            TmpSwitchNode(switch123Connection1, null).contentHash,
        )
        assertNotEquals(
            TmpSwitchNode(switch123Main1, null).contentHash,
            TmpSwitchNode(switch123Main2, null).contentHash,
        )
    }

    @Test
    fun `Edge switch node content hash works`() {
        val switchNode1 = EdgeNode.switch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        val switchNode2 =
            EdgeNode.switch(
                inner = SwitchLink(IntId(2), MAIN, JointNumber(2)),
                outer = SwitchLink(IntId(3), MAIN, JointNumber(3)),
            )
        val switchNode2Reverse =
            EdgeNode.switch(
                inner = SwitchLink(IntId(3), MAIN, JointNumber(3)),
                outer = SwitchLink(IntId(2), MAIN, JointNumber(2)),
            )
        assertEquals(switchNode1.contentHash, switchNode1.contentHash)
        assertNotEquals(switchNode1.contentHash, switchNode2.contentHash)
        assertNotEquals(switchNode1.contentHash, switchNode2Reverse.contentHash)
        // Reverse ordering is still the same node but different from edge point of view due to direction
        assertNotEquals(switchNode2.contentHash, switchNode2Reverse.contentHash)
        assertEquals(switchNode2.node.contentHash, switchNode2Reverse.node.contentHash)
    }

    @Test
    fun `Edge track boundary node content hash works`() {
        val trackNode1 = EdgeNode.trackBoundary(IntId(1), START)
        val trackNode2 =
            EdgeNode.trackBoundary(inner = TrackBoundary(IntId(2), END), outer = TrackBoundary(IntId(3), START))
        val trackNode2Reverse =
            EdgeNode.trackBoundary(inner = TrackBoundary(IntId(3), START), outer = TrackBoundary(IntId(2), END))
        assertEquals(trackNode1.contentHash, trackNode1.contentHash)
        assertNotEquals(trackNode1.contentHash, trackNode2.contentHash)
        assertNotEquals(trackNode1.contentHash, trackNode2Reverse.contentHash)
        // Reverse ordering is still the same node but different from edge point of view due to direction
        assertNotEquals(trackNode2.contentHash, trackNode2Reverse.contentHash)
        assertEquals(trackNode2.node.contentHash, trackNode2Reverse.node.contentHash)
    }

    @Test
    fun `Edge content hash works`() {
        val trackId = IntId<LocationTrack>(1)
        val startNode1 = EdgeNode.trackBoundary(trackId, START)
        val endNode1 = EdgeNode.trackBoundary(trackId, END)
        val switchNode1 = EdgeNode.switch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        val segments1 = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0)), segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val segments2 = listOf(segment(Point(1.0, 0.0), Point(2.0, 1.0)), segment(Point(2.0, 1.0), Point(3.0, 2.0)))

        val edgeContent = TmpLayoutEdge(startNode1, endNode1, segments1)
        assertEquals(edgeContent.contentHash, edgeContent.contentHash)
        assertEquals(
            edgeContent.contentHash,
            DbLayoutEdge(
                    IntId(123),
                    DbEdgeNode(NodePortType.A, DbTrackBoundaryNode(IntId(456), TrackBoundary(trackId, START))),
                    DbEdgeNode(NodePortType.A, DbTrackBoundaryNode(IntId(457), TrackBoundary(trackId, END))),
                    segments1,
                )
                .contentHash,
        )
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(startNode1, endNode1, segments2).contentHash)
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(startNode1, switchNode1, segments1).contentHash)
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(switchNode1, endNode1, segments1).contentHash)
    }

    @Test
    fun `Node combining works`() {
        val switch1 = switchLinkYV(IntId(1), 1)
        val switch2 = switchLinkYV(IntId(2), 2)
        val switch3 = switchLinkYV(IntId(3), 3)
        val loneSwitch1Node = TmpSwitchNode(switch1, null)
        val loneSwitch1_2Node = TmpSwitchNode(switchLinkYV(IntId(1), 2), null)
        val loneSwitch2Node = TmpSwitchNode(switch2, null)
        val loneSwitch3Node = TmpSwitchNode(switch3, null)
        val switch12Node = TmpSwitchNode(switch1, switch2)
        val switch23Node = TmpSwitchNode(switch2, switch3)
        val trackBoundaryNode1 = TmpTrackBoundaryNode(TrackBoundary(IntId(1), START), null)
        val trackBoundaryNode2 = TmpTrackBoundaryNode(TrackBoundary(IntId(2), END), null)
        val trackBoundary12Node = TmpTrackBoundaryNode(TrackBoundary(IntId(1), START), TrackBoundary(IntId(2), END))

        // Single switch nodes get combined
        assertEquals(
            mapOf(loneSwitch1Node to switch12Node, loneSwitch2Node to switch12Node),
            combineEligibleNodes(listOf(loneSwitch1Node, loneSwitch2Node)),
        )
        // Switches are not combined within the same switch
        assertEquals(
            emptyMap<LayoutNode, LayoutNode>(),
            combineEligibleNodes(listOf(loneSwitch1Node, loneSwitch1_2Node)),
        )
        // Single switch nodes get combined and multi-switch ones are kept (including the one that is already combined)
        assertEquals(
            mapOf(loneSwitch1Node to switch12Node, loneSwitch2Node to switch12Node),
            combineEligibleNodes(listOf(loneSwitch1Node, loneSwitch2Node, switch12Node, switch23Node)),
        )
        // Single switch nodes are added to existing multi-switch nodes rather than each other
        assertEquals(
            mapOf(loneSwitch1Node to switch12Node, loneSwitch3Node to switch23Node),
            combineEligibleNodes(listOf(loneSwitch1Node, loneSwitch3Node, switch12Node, switch23Node)),
        )
        // Track boundaries are not combined with each other
        assertEquals(
            emptyMap<LayoutNode, LayoutNode>(),
            combineEligibleNodes(listOf(trackBoundaryNode1, trackBoundaryNode2)),
        )
        // Track boundaries are combined to switches
        assertEquals(
            mapOf(trackBoundaryNode1 to loneSwitch1Node, trackBoundaryNode2 to loneSwitch1Node),
            combineEligibleNodes(listOf(trackBoundaryNode1, trackBoundaryNode2, loneSwitch1Node)),
        )
        // Track boundaries are combined to combination-switches generated in a previous step
        assertEquals(
            mapOf(
                loneSwitch1Node to switch12Node,
                loneSwitch2Node to switch12Node,
                trackBoundaryNode1 to switch12Node,
                trackBoundaryNode2 to switch12Node,
            ),
            combineEligibleNodes(listOf(trackBoundaryNode1, trackBoundaryNode2, loneSwitch1Node, loneSwitch2Node)),
        )
        // Track boundaries are combined with existing combination-tracks
        assertEquals(
            mapOf(trackBoundaryNode1 to trackBoundary12Node, trackBoundaryNode2 to trackBoundary12Node),
            combineEligibleNodes(listOf(trackBoundaryNode1, trackBoundaryNode2, trackBoundary12Node)),
        )
        // Combination track boundaries are not combined with anything
        assertFalse(
            combineEligibleNodes(
                    listOf(trackBoundaryNode1, trackBoundaryNode2, trackBoundary12Node, loneSwitch1Node, switch12Node)
                )
                .containsKey(trackBoundary12Node)
        )
    }

    @Test
    fun `Switch removal works`() {
        val switch1 = IntId<LayoutSwitch>(1)
        val switch2 = IntId<LayoutSwitch>(2)
        val switch3 = IntId<LayoutSwitch>(3)
        val switch4 = IntId<LayoutSwitch>(4)
        val geometry =
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(switch1, 1),
                    endOuterSwitch = switchLinkYV(switch2, 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(switch2, 1),
                    endInnerSwitch = switchLinkYV(switch2, 2),
                    endOuterSwitch = switchLinkYV(switch3, 1),
                    segments = listOf(segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(switch2, 2),
                    startInnerSwitch = switchLinkYV(switch3, 1),
                    endInnerSwitch = switchLinkYV(switch3, 2),
                    endOuterSwitch = switchLinkYV(switch4, 1),
                    segments = listOf(segment(Point(2.0, 0.0), Point(3.0, 0.0))),
                ),
            )

        assertEquals(
            listOf(
                switchLinkYV(switch1, 1),
                switchLinkYV(switch2, 1),
                switchLinkYV(switch2, 2),
                switchLinkYV(switch3, 1),
                switchLinkYV(switch3, 2),
                switchLinkYV(switch4, 1),
            ),
            geometry.trackSwitchLinks.map { l -> l.link },
        )

        assertEquals(
            listOf(
                switchLinkYV(switch2, 1),
                switchLinkYV(switch2, 2),
                switchLinkYV(switch3, 1),
                switchLinkYV(switch3, 2),
                switchLinkYV(switch4, 1),
            ),
            geometry.withoutSwitch(switch1).trackSwitchLinks.map { l -> l.link },
        )

        assertEquals(
            listOf(
                switchLinkYV(switch1, 1),
                switchLinkYV(switch3, 1),
                switchLinkYV(switch3, 2),
                switchLinkYV(switch4, 1),
            ),
            geometry.withoutSwitch(switch2).trackSwitchLinks.map { l -> l.link },
        )

        assertEquals(
            listOf(
                switchLinkYV(switch1, 1),
                switchLinkYV(switch2, 1),
                switchLinkYV(switch2, 2),
                switchLinkYV(switch4, 1),
            ),
            geometry.withoutSwitch(switch3).trackSwitchLinks.map { l -> l.link },
        )

        assertEquals(
            listOf(
                switchLinkYV(switch1, 1),
                switchLinkYV(switch2, 1),
                switchLinkYV(switch2, 2),
                switchLinkYV(switch3, 1),
                switchLinkYV(switch3, 2),
            ),
            geometry.withoutSwitch(switch4).trackSwitchLinks.map { l -> l.link },
        )
    }

    @Test
    fun `Node port ordering works`() {
        val switch1 = switchLinkYV(IntId(1), 1)
        val switch2 = switchLinkYV(IntId(2), 1)

        assertEquals(TmpSwitchNode(switch1, switch2), LayoutNode.of(switch1, switch2))
        assertEquals(TmpSwitchNode(switch1, switch2), LayoutNode.of(switch2, switch1))

        val boundary1 = TrackBoundary(IntId(1), START)
        val boundary2 = TrackBoundary(IntId(2), START)

        assertEquals(TmpTrackBoundaryNode(boundary1, boundary2), LayoutNode.of(boundary1, boundary2))
        assertEquals(TmpTrackBoundaryNode(boundary1, boundary2), LayoutNode.of(boundary2, boundary1))
    }

    @Test
    fun `Node replacement works`() {
        val geometry =
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments = listOf(segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                ),
            )
        val middleNode = geometry.nodes[1]
        assertEquals(TmpSwitchNode(switchLinkYV(IntId(2), 1), null), middleNode)
        val replacement = TmpSwitchNode(switchLinkYV(IntId(3), 1), null)
        assertEquals(
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(3), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endOuterSwitch = switchLinkYV(IntId(3), 1),
                    segments = listOf(segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                ),
            ),
            geometry.replaceNodes(mapOf(middleNode to replacement)),
        )
        TODO("More cases")
    }

    @Test
    fun `Topology change calculation works`() {
        TODO()
    }

    @Test
    fun `Topology recalculate works`() {
        TODO()
    }

    @Test
    fun `combineEdges works`() {
        TODO()
    }

    @Test
    fun `Track switch links are resolved correctly`() {
        TODO()
    }

    @Test
    fun `Segment m calculation works`() {
        TODO()
    }

    @Test
    fun `Edge m calculation works`() {
        TODO()
    }

    @Test
    fun `Node location is resolved correctly`() {
        TODO()
    }
}
