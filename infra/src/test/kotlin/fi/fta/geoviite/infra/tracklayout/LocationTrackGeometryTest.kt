package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
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
        // However, boundaries cannot be connected to such nodes as we wouldn't know which port to use
        assertEquals(
            mapOf(loneSwitch1Node to switch12Node, loneSwitch2Node to switch12Node),
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
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(1), 2),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 2),
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments = listOf(segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                ),
            )
        val startNode = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val middleNode = TmpSwitchNode(switchLinkYV(IntId(1), 2), switchLinkYV(IntId(2), 1))
        val endNode = TmpSwitchNode(switchLinkYV(IntId(2), 2), null)
        assertEquals(listOf(startNode, middleNode, endNode), geometry.nodes)

        val startCombined = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(3), 1))
        val endCombined = TmpSwitchNode(switchLinkYV(IntId(2), 2), switchLinkYV(IntId(4), 1))

        val result = geometry.withCombinationNodes(mapOf(startNode to startCombined, endNode to endCombined))
        assertEquals(listOf(startCombined, middleNode, endCombined), result.nodes)
    }

    @Test
    fun `Node replacement works with single-edge node`() {
        val geometry =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(1), 2),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                )
            )
        val startNode = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val endNode = TmpSwitchNode(switchLinkYV(IntId(1), 2), null)
        assertEquals(listOf(startNode, endNode), geometry.nodes)

        val startCombined = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(2), 1))
        val endCombined = TmpSwitchNode(switchLinkYV(IntId(1), 2), switchLinkYV(IntId(3), 1))

        val result = geometry.withCombinationNodes(mapOf(startNode to startCombined, endNode to endCombined))
        assertEquals(listOf(startCombined, endCombined), result.nodes)
    }

    @Test
    fun `combineEdges works`() {
        val segments1 = listOf(segment(Point(0.0, 0.0), Point(2.0, 0.0)))
        val segments2 = listOf(segment(Point(2.0, 0.0), Point(4.0, 0.0)))
        val segments3 = listOf(segment(Point(4.0, 0.0), Point(6.0, 0.0)))
        assertEquals(
            listOf(
                edge(segments1, endOuterSwitch = switchLinkYV(IntId(1), 1)),
                edge(
                    segments2,
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 1),
                    endOuterSwitch = switchLinkYV(IntId(3), 1),
                ),
                edge(
                    segments3,
                    startOuterSwitch = switchLinkYV(IntId(2), 1),
                    startInnerSwitch = switchLinkYV(IntId(3), 1),
                    endInnerSwitch = switchLinkYV(IntId(4), 1),
                ),
            ),
            combineEdges(
                listOf(
                    edge(segments1),
                    edge(
                        segments2,
                        startInnerSwitch = switchLinkYV(IntId(1), 1),
                        endInnerSwitch = switchLinkYV(IntId(2), 1),
                    ),
                    edge(
                        segments3,
                        startInnerSwitch = switchLinkYV(IntId(3), 1),
                        endInnerSwitch = switchLinkYV(IntId(4), 1),
                    ),
                )
            ),
        )
    }

    @Test
    fun `Track switch links are resolved correctly`() {
        assertEquals(
            listOf(
                TrackSwitchLink(
                    switchLinkYV(IntId(1), 1),
                    alignmentPoint(0.0, 0.0, m = 0.0),
                    TrackSwitchLinkType.OUTER,
                ),
                TrackSwitchLink(
                    switchLinkYV(IntId(2), 1),
                    alignmentPoint(2.0, 0.0, m = 2.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(IntId(3), 1),
                    alignmentPoint(4.0, 0.0, m = 4.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(IntId(4), 1),
                    alignmentPoint(4.0, 0.0, m = 4.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(
                    switchLinkYV(IntId(5), 1),
                    alignmentPoint(6.0, 0.0, m = 6.0),
                    TrackSwitchLinkType.INNER,
                ),
                TrackSwitchLink(switchLinkYV(IntId(6), 1), alignmentPoint(6.0, 0.0, m = 6.0), TrackSwitchLinkType.OUTER),
            ),
            trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 0.0), Point(2.0, 0.0))),
                        startOuterSwitch = switchLinkYV(IntId(1), 1),
                        endOuterSwitch = switchLinkYV(IntId(2), 1),
                    ),
                    edge(
                        listOf(segment(Point(2.0, 0.0), Point(4.0, 0.0))),
                        startInnerSwitch = switchLinkYV(IntId(2), 1),
                        endInnerSwitch = switchLinkYV(IntId(3), 1),
                        endOuterSwitch = switchLinkYV(IntId(4), 1),
                    ),
                    edge(
                        listOf(segment(Point(4.0, 0.0), Point(6.0, 0.0))),
                        startOuterSwitch = switchLinkYV(IntId(3), 1),
                        startInnerSwitch = switchLinkYV(IntId(4), 1),
                        endInnerSwitch = switchLinkYV(IntId(5), 1),
                        endOuterSwitch = switchLinkYV(IntId(6), 1),
                    ),
                )
                .trackSwitchLinks,
        )
    }

    @Test
    fun `Node location is resolved correctly`() {
        assertEquals(
            listOf(
                PlaceholderNode to alignmentPoint(0.0, 0.0, m = 0.0),
                TmpSwitchNode(switchLinkYV(IntId(1), 1), null) to alignmentPoint(2.0, 0.0, m = 2.0),
                TmpSwitchNode(switchLinkYV(IntId(2), 1), switchLinkYV(IntId(3), 1)) to
                    alignmentPoint(4.0, 0.0, m = 4.0),
                TmpSwitchNode(switchLinkYV(IntId(4), 1), null) to alignmentPoint(6.0, 0.0, m = 6.0),
            ),
            trackGeometry(
                    edge(listOf(segment(Point(0.0, 0.0), Point(2.0, 0.0))), endOuterSwitch = switchLinkYV(IntId(1), 1)),
                    edge(
                        listOf(segment(Point(2.0, 0.0), Point(4.0, 0.0))),
                        startInnerSwitch = switchLinkYV(IntId(1), 1),
                        endInnerSwitch = switchLinkYV(IntId(2), 1),
                        endOuterSwitch = switchLinkYV(IntId(3), 1),
                    ),
                    edge(
                        listOf(segment(Point(4.0, 0.0), Point(6.0, 0.0))),
                        startOuterSwitch = switchLinkYV(IntId(2), 1),
                        startInnerSwitch = switchLinkYV(IntId(3), 1),
                        endInnerSwitch = switchLinkYV(IntId(4), 1),
                    ),
                )
                .nodesWithLocation,
        )
    }

    @Test
    fun `Segment m calculation works`() {
        assertEquals(
            listOf(Range(0.0, 2.0), Range(2.0, 5.0), Range(5.0, 9.0)),
            calculateSegmentMValues(
                listOf(
                    segment(Point(0.0, 0.0), Point(2.0, 0.0)),
                    segment(Point(2.0, 0.0), Point(5.0, 0.0)),
                    segment(Point(5.0, 0.0), Point(9.0, 0.0)),
                )
            ),
        )
    }

    @Test
    fun `Edge m calculation works`() {
        assertEquals(
            listOf(Range(0.0, 2.0), Range(2.0, 5.0), Range(5.0, 9.0)),
            calculateEdgeMValues(
                listOf(
                    edge(listOf(segment(Point(0.0, 0.0), Point(2.0, 0.0)))),
                    edge(
                        listOf(
                            segment(Point(2.0, 0.0), Point(3.0, 0.0)),
                            segment(Point(3.0, 0.0), Point(4.0, 0.0)),
                            segment(Point(4.0, 0.0), Point(5.0, 0.0)),
                        )
                    ),
                    edge(listOf(segment(Point(5.0, 0.0), Point(9.0, 0.0)))),
                )
            ),
        )
    }

    @Test
    fun `Track primary start switch is resolved correctly`() {
        val mainLink = switchLinkYV(IntId(1), 1)
        val mathLink = switchLinkYV(IntId(2), 5)
        val mathLink2 = switchLinkYV(IntId(3), 5)
        assertEquals(null, trackGeometry(edge(listOf(someSegment()))).startSwitchLink)
        assertEquals(mainLink, trackGeometry(edge(listOf(someSegment()), startInnerSwitch = mainLink)).startSwitchLink)
        assertEquals(mainLink, trackGeometry(edge(listOf(someSegment()), startOuterSwitch = mainLink)).startSwitchLink)
        assertEquals(
            mainLink,
            trackGeometry(edge(listOf(someSegment()), startInnerSwitch = mainLink, startOuterSwitch = mathLink))
                .startSwitchLink,
        )
        assertEquals(
            mainLink,
            trackGeometry(edge(listOf(someSegment()), startInnerSwitch = mathLink, startOuterSwitch = mainLink))
                .startSwitchLink,
        )
        assertEquals(
            mathLink,
            trackGeometry(edge(listOf(someSegment()), startInnerSwitch = mathLink, startOuterSwitch = mathLink2))
                .startSwitchLink,
        )
        assertEquals(
            mathLink2,
            trackGeometry(edge(listOf(someSegment()), startInnerSwitch = mathLink2, startOuterSwitch = mathLink))
                .startSwitchLink,
        )
    }

    @Test
    fun `Track primary end switch is resolved correctly`() {
        val mainLink = switchLinkYV(IntId(1), 1)
        val mathLink = switchLinkYV(IntId(2), 5)
        val mathLink2 = switchLinkYV(IntId(3), 5)
        assertEquals(null, trackGeometry(edge(listOf(someSegment()))).endSwitchLink)
        assertEquals(mainLink, trackGeometry(edge(listOf(someSegment()), endInnerSwitch = mainLink)).endSwitchLink)
        assertEquals(mainLink, trackGeometry(edge(listOf(someSegment()), endOuterSwitch = mainLink)).endSwitchLink)
        assertEquals(
            mainLink,
            trackGeometry(edge(listOf(someSegment()), endInnerSwitch = mainLink, endOuterSwitch = mathLink))
                .endSwitchLink,
        )
        assertEquals(
            mainLink,
            trackGeometry(edge(listOf(someSegment()), endInnerSwitch = mathLink, endOuterSwitch = mainLink))
                .endSwitchLink,
        )
        assertEquals(
            mathLink,
            trackGeometry(edge(listOf(someSegment()), endInnerSwitch = mathLink, endOuterSwitch = mathLink2))
                .endSwitchLink,
        )
        assertEquals(
            mathLink2,
            trackGeometry(edge(listOf(someSegment()), endInnerSwitch = mathLink2, endOuterSwitch = mathLink))
                .endSwitchLink,
        )
    }

    @Test
    fun `Track portion end nodes are resolved correctly`() {
        val switch1 = switchLinkYV(IntId(1), 1)
        val switch2 = switchLinkYV(IntId(2), 1)
        val switch3 = switchLinkYV(IntId(3), 1)
        val geometry =
            trackGeometry(
                edge(
                    endInnerSwitch = switch1,
                    segments =
                        listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0)), segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switch1,
                    endInnerSwitch = switch2,
                    segments =
                        listOf(segment(Point(2.0, 0.0), Point(3.0, 0.0)), segment(Point(3.0, 0.0), Point(4.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switch2,
                    endInnerSwitch = switch3,
                    segments =
                        listOf(segment(Point(4.0, 0.0), Point(5.0, 0.0)), segment(Point(5.0, 0.0), Point(6.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switch3,
                    segments =
                        listOf(segment(Point(6.0, 0.0), Point(7.0, 0.0)), segment(Point(7.0, 0.0), Point(8.0, 0.0))),
                ),
            )

        val start = alignmentPoint(0.0, 0.0, m = 0.0)
        val mid1 = alignmentPoint(2.0, 0.0, m = 2.0)
        val mid2 = alignmentPoint(4.0, 0.0, m = 4.0)
        val mid3 = alignmentPoint(6.0, 0.0, m = 6.0)
        val end = alignmentPoint(8.0, 0.0, m = 8.0)

        assertEquals(start to end, geometry.getEdgeStartAndEnd(0..3))

        assertEquals(start to mid1, geometry.getEdgeStartAndEnd(0..0))
        assertEquals(start to mid2, geometry.getEdgeStartAndEnd(0..1))
        assertEquals(start to mid3, geometry.getEdgeStartAndEnd(0..2))

        assertEquals(mid1 to end, geometry.getEdgeStartAndEnd(1..3))
        assertEquals(mid2 to end, geometry.getEdgeStartAndEnd(2..3))
        assertEquals(mid3 to end, geometry.getEdgeStartAndEnd(3..3))

        assertEquals(mid1 to mid3, geometry.getEdgeStartAndEnd(1..2))
    }
}
