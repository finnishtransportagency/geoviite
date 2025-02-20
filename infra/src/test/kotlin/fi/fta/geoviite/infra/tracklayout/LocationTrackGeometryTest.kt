package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
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
}
