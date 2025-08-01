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
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LocationTrackGeometryTest {

    @Test
    fun `Start & End node content hash works`() {
        fun track123() = IntId<LocationTrack>(123)
        fun track124() = IntId<LocationTrack>(124)

        assertEquals(
            TmpTrackBoundaryNode(track123(), START).contentHash,
            TmpTrackBoundaryNode(track123(), START).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123(), START)).contentHash,
            TmpTrackBoundaryNode(track123(), START).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123(), START)).contentHash,
            DbTrackBoundaryNode(IntId(654), TrackBoundary(track123(), START)).contentHash,
        )
        assertNotEquals(
            TmpTrackBoundaryNode(track123(), START).contentHash,
            TmpTrackBoundaryNode(track124(), START).contentHash,
        )

        assertEquals(
            TmpTrackBoundaryNode(track123(), END).contentHash,
            TmpTrackBoundaryNode(track123(), END).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123(), END)).contentHash,
            TmpTrackBoundaryNode(track123(), END).contentHash,
        )
        assertEquals(
            DbTrackBoundaryNode(IntId(456), TrackBoundary(track123(), END)).contentHash,
            DbTrackBoundaryNode(IntId(654), TrackBoundary(track123(), END)).contentHash,
        )
        assertNotEquals(
            TmpTrackBoundaryNode(track123(), END).contentHash,
            TmpTrackBoundaryNode(track124(), END).contentHash,
        )

        assertNotEquals(
            TmpTrackBoundaryNode(track123(), START).contentHash,
            TmpTrackBoundaryNode(track123(), END).contentHash,
        )
    }

    @Test
    fun `Switch node content hash works`() {
        fun switch123Main1() = SwitchLink(IntId(123), MAIN, JointNumber(1))
        fun switch124Main1() = SwitchLink(IntId(124), MAIN, JointNumber(1))
        fun switch123Connection1() = SwitchLink(IntId(123), CONNECTION, JointNumber(1))
        fun switch123Main2() = SwitchLink(IntId(123), MAIN, JointNumber(2))
        assertEquals(
            TmpSwitchNode(switch123Main1(), null).contentHash,
            TmpSwitchNode(switch123Main1(), null).contentHash,
        )
        assertEquals(
            DbSwitchNode(IntId(789), switch123Main1(), null).contentHash,
            TmpSwitchNode(switch123Main1(), null).contentHash,
        )
        assertEquals(
            DbSwitchNode(IntId(789), switch123Main1(), null).contentHash,
            DbSwitchNode(IntId(987), switch123Main1(), null).contentHash,
        )
        assertNotEquals(
            TmpSwitchNode(switch123Main1(), switch124Main1()),
            TmpSwitchNode(switch124Main1(), switch123Main1()),
        )
        assertNotEquals(TmpSwitchNode(switch123Main1(), null), TmpSwitchNode(switch124Main1(), null))
        assertNotEquals(TmpSwitchNode(switch123Main1(), switch124Main1()), TmpSwitchNode(switch123Main1(), null))
        assertNotEquals(
            TmpSwitchNode(switch123Main1(), null).contentHash,
            TmpSwitchNode(switch123Connection1(), null).contentHash,
        )
        assertNotEquals(
            TmpSwitchNode(switch123Main1(), null).contentHash,
            TmpSwitchNode(switch123Main2(), null).contentHash,
        )
    }

    @Test
    fun `Edge content hash works`() {
        fun switchNode1() = NodeConnection.switch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        fun switchNode2() =
            NodeConnection.switch(
                inner = SwitchLink(IntId(2), MAIN, JointNumber(2)),
                outer = SwitchLink(IntId(3), MAIN, JointNumber(3)),
            )
        fun switchNode2Reverse() =
            NodeConnection.switch(
                inner = SwitchLink(IntId(3), MAIN, JointNumber(3)),
                outer = SwitchLink(IntId(2), MAIN, JointNumber(2)),
            )
        fun trackNode1() = NodeConnection.trackBoundary(IntId(1), START)
        fun trackNode2() =
            NodeConnection.trackBoundary(inner = TrackBoundary(IntId(2), END), outer = TrackBoundary(IntId(3), START))

        // Segments generate internal ids which are not stable on recreation, but only after storing the geometry
        // -> don't recreate the segments themselves here
        val segment1 = segment(Point(0.0, 0.0), Point(1.0, 1.0))
        val segment2 = segment(Point(1.0, 1.0), Point(2.0, 2.0))
        fun segments1() = listOf(segment1)
        fun segments2() = listOf(segment1, segment2)

        // Sanity check: we should get the same hashes despite recreating the edge
        assertEquals(
            TmpLayoutEdge(switchNode1(), switchNode2(), segments1()).contentHash,
            TmpLayoutEdge(switchNode1(), switchNode2(), segments1()).contentHash,
        )
        assertEquals(
            TmpLayoutEdge(trackNode1(), trackNode2(), segments1()).contentHash,
            TmpLayoutEdge(trackNode1(), trackNode2(), segments1()).contentHash,
        )

        // The actual verifications: different edges need different hashes
        assertNotEquals(
            TmpLayoutEdge(switchNode1(), trackNode2(), segments1()).contentHash,
            TmpLayoutEdge(switchNode2(), trackNode2(), segments1()).contentHash,
        )
        assertNotEquals(
            TmpLayoutEdge(switchNode1(), trackNode2(), segments1()).contentHash,
            TmpLayoutEdge(trackNode1(), switchNode1(), segments1()).contentHash,
        )
        assertNotEquals(
            TmpLayoutEdge(switchNode1(), trackNode2(), segments1()).contentHash,
            TmpLayoutEdge(switchNode1(), trackNode2(), segments2()).contentHash,
        )
        assertNotEquals(
            TmpLayoutEdge(switchNode1(), switchNode2(), segments1()).contentHash,
            TmpLayoutEdge(switchNode2(), switchNode1(), segments1()).contentHash,
        )
        assertNotEquals(
            TmpLayoutEdge(switchNode1(), switchNode2(), segments1()).contentHash,
            TmpLayoutEdge(switchNode1(), switchNode2Reverse(), segments1()).contentHash,
        )
        assertNotEquals(
            TmpLayoutEdge(switchNode2(), switchNode1(), segments1()).contentHash,
            TmpLayoutEdge(switchNode2Reverse(), switchNode1(), segments1()).contentHash,
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

        val result =
            geometry.withNodeReplacements(
                mapOf(startNode.contentHash to startCombined, endNode.contentHash to endCombined)
            )
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

        val result =
            geometry.withNodeReplacements(
                mapOf(startNode.contentHash to startCombined, endNode.contentHash to endCombined)
            )
        assertEquals(listOf(startCombined, endCombined), result.nodes)
    }

    @Test
    fun `Node replacement works when edge needs to get merged with previous`() {
        val geometry =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 2),
                    endInnerSwitch = switchLinkYV(IntId(1), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                ),
            )
        val node1 = TmpSwitchNode(switchLinkYV(IntId(1), 2), null)
        val node2 = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val node3 = TmpSwitchNode(switchLinkYV(IntId(2), 1), null)
        assertEquals(listOf(node1, node2, node3), geometry.nodes)

        val combined23 = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(2), 1))

        val result =
            geometry.withNodeReplacements(mapOf(node2.contentHash to combined23, node3.contentHash to combined23))
        assertEquals(listOf(node1, combined23), result.nodes)
        assertMatches(
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 2),
                    endInnerSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments =
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0)), segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                )
            ),
            result,
        )
    }

    @Test
    fun `Node replacement works when edge needs to get merged with next`() {
        val geometry =
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                ),
            )
        val node1 = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val node2 = TmpSwitchNode(switchLinkYV(IntId(2), 1), null)
        val node3 = TmpSwitchNode(switchLinkYV(IntId(2), 2), null)
        assertEquals(listOf(node1, node2, node3), geometry.nodes)

        val combined12 = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(2), 1))

        val result =
            geometry.withNodeReplacements(mapOf(node1.contentHash to combined12, node2.contentHash to combined12))
        assertEquals(listOf(combined12, node3), result.nodes)
        assertMatches(
            trackGeometry(
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments =
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0)), segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                )
            ),
            result,
        )
    }

    @Test
    fun `Node replacement works when edge needs to get split and merged both ways`() {
        val geometry =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 2),
                    endInnerSwitch = switchLinkYV(IntId(1), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments = listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                ),
            )
        val node1 = TmpSwitchNode(switchLinkYV(IntId(1), 2), null)
        val node2 = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val node3 = TmpSwitchNode(switchLinkYV(IntId(2), 1), null)
        val node4 = TmpSwitchNode(switchLinkYV(IntId(2), 2), null)
        assertEquals(listOf(node1, node2, node3, node4), geometry.nodes)

        val combined23 = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(2), 1))

        val result =
            geometry.withNodeReplacements(mapOf(node2.contentHash to combined23, node3.contentHash to combined23))
        assertEquals(listOf(node1, combined23, node4), result.nodes)
        assertMatches(
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 2),
                    endInnerSwitch = switchLinkYV(IntId(1), 1),
                    endOuterSwitch = switchLinkYV(IntId(2), 1),
                    segments =
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0)), segment(Point(10.0, 0.0), Point(15.0, 0.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 1),
                    startInnerSwitch = switchLinkYV(IntId(2), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 2),
                    segments =
                        listOf(segment(Point(15.0, 0.0), Point(20.0, 0.0)), segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                ),
            ),
            result,
        )
    }

    @Test
    fun `Node replacement does nothing if node merging would connect track ends to each other`() {
        val geometry =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(2), 1),
                    segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                )
            )
        val startNode = TmpSwitchNode(switchLinkYV(IntId(1), 1), null)
        val endNode = TmpSwitchNode(switchLinkYV(IntId(2), 1), null)
        assertEquals(listOf(startNode, endNode), geometry.nodes)

        val combined = TmpSwitchNode(switchLinkYV(IntId(1), 1), switchLinkYV(IntId(2), 1))

        val result =
            geometry.withNodeReplacements(mapOf(startNode.contentHash to combined, endNode.contentHash to combined))
        assertEquals(geometry, result)
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
                TmpSwitchNode(switchLinkYV(IntId(1), 1), null) to locationTrackPoint(2.0, 0.0, m = 2.0),
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
            listOf(Range(0.0, 2.0), Range(2.0, 5.0), Range(5.0, 9.0)).map { it.map(::LineM) },
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
            listOf(Range(0.0, 2.0), Range(2.0, 5.0), Range(5.0, 9.0)).map { it.map(::LineM) },
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
