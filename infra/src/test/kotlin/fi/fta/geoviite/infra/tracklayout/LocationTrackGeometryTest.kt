package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.CONNECTION
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole.MAIN
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

class LocationTrackGeometryTest {

    @Test
    fun `Start & End node content hash works`() {
        val track123 = IntId<LocationTrack>(123)
        val track124 = IntId<LocationTrack>(124)

        assertEquals(TmpTrackStartNode(track123).contentHash, TmpTrackStartNode(track123).contentHash)
        assertEquals(DbTrackStartNode(IntId(456), track123).contentHash, TmpTrackStartNode(track123).contentHash)
        assertEquals(
            DbTrackStartNode(IntId(456), track123).contentHash,
            DbTrackStartNode(IntId(654), track123).contentHash,
        )
        assertNotEquals(TmpTrackStartNode(track123).contentHash, TmpTrackStartNode(track124).contentHash)

        assertEquals(TmpTrackEndNode(track123).contentHash, TmpTrackEndNode(track123).contentHash)
        assertEquals(DbTrackEndNode(IntId(456), track123).contentHash, TmpTrackEndNode(track123).contentHash)
        assertEquals(DbTrackEndNode(IntId(456), track123).contentHash, DbTrackEndNode(IntId(654), track123).contentHash)
        assertNotEquals(TmpTrackEndNode(track123).contentHash, TmpTrackEndNode(track124).contentHash)

        assertNotEquals(TmpTrackStartNode(track123).contentHash, TmpTrackEndNode(track123).contentHash)
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
    fun `Edge node content hash works`() {
        val switchNode1 = EdgeNode.switch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        val switchNode2 =
            EdgeNode.switch(SwitchLink(IntId(2), MAIN, JointNumber(2)), SwitchLink(IntId(3), MAIN, JointNumber(3)))
        val switchNode2Reverse =
            EdgeNode.switch(SwitchLink(IntId(3), MAIN, JointNumber(3)), SwitchLink(IntId(2), MAIN, JointNumber(2)))
        assertEquals(switchNode1.contentHash, switchNode1.contentHash)
        assertNotEquals(switchNode1.contentHash, switchNode2.contentHash)
        assertNotEquals(switchNode1.contentHash, switchNode2Reverse.contentHash)
        // Reverse ordering is still the same node but different from edge point of view due to direction
        assertNotEquals(switchNode2.contentHash, switchNode2Reverse.contentHash)
        assertEquals(switchNode2.node.contentHash, switchNode2Reverse.node.contentHash)
    }

    @Test
    fun `Edge content hash works`() {
        val trackId = IntId<LocationTrack>(1)
        val startNode1 = EdgeNode.trackStart(trackId)
        val endNode1 = EdgeNode.trackEnd(trackId)
        val switchNode1 = EdgeNode.switch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        val segments1 = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0)), segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val segments2 = listOf(segment(Point(1.0, 0.0), Point(2.0, 1.0)), segment(Point(2.0, 1.0), Point(3.0, 2.0)))

        val edgeContent = TmpLayoutEdge(startNode1, endNode1, segments1)
        assertEquals(edgeContent.contentHash, edgeContent.contentHash)
        assertEquals(
            edgeContent.contentHash,
            DbLayoutEdge(
                    IntId(123),
                    DbEdgeNode(EdgeNodeDirection.INCREASING, DbTrackStartNode(IntId(456), trackId)),
                    DbEdgeNode(EdgeNodeDirection.INCREASING, DbTrackEndNode(IntId(457), trackId)),
                    segments1,
                )
                .contentHash,
        )
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(startNode1, endNode1, segments2).contentHash)
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(startNode1, switchNode1, segments1).contentHash)
        assertNotEquals(edgeContent.contentHash, TmpLayoutEdge(switchNode1, endNode1, segments1).contentHash)
    }
}
