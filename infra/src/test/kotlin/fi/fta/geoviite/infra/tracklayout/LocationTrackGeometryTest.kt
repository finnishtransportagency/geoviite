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
        val start123 = LayoutNodeStartTrack(IntId(123))
        val start124 = LayoutNodeStartTrack(IntId(124))
        val end123 = LayoutNodeEndTrack(IntId(123))
        val end124 = LayoutNodeEndTrack(IntId(124))
        assertEquals(start123.contentHash, start123.contentHash)
        assertEquals(LayoutNode(IntId(456), start123).contentHash, start123.contentHash)
        assertNotEquals(start123.contentHash, start124.contentHash)
        assertEquals(end123.contentHash, end123.contentHash)
        assertEquals(LayoutNode(IntId(456), end123).contentHash, end123.contentHash)
        assertNotEquals(end123.contentHash, end124.contentHash)
        assertNotEquals(start123.contentHash, end123.contentHash)
    }

    @Test
    fun `Switch node content hash works`() {
        val switch123Main1 = SwitchLink(IntId(123), MAIN, JointNumber(1))
        val switch124Main1 = SwitchLink(IntId(124), MAIN, JointNumber(1))
        val switch123Connection1 = SwitchLink(IntId(123), CONNECTION, JointNumber(1))
        val switch123Main2 = SwitchLink(IntId(123), MAIN, JointNumber(2))
        assertEquals(
            LayoutNodeSwitch(switch123Main1, null).contentHash,
            LayoutNodeSwitch(switch123Main1, null).contentHash,
        )
        assertEquals(
            LayoutNode(IntId(789), LayoutNodeSwitch(switch123Main1, null)).contentHash,
            LayoutNodeSwitch(switch123Main1, null).contentHash,
        )
        assertNotEquals(LayoutNodeSwitch(switch123Main1, null), LayoutNodeSwitch(null, switch123Main1))
        assertNotEquals(LayoutNodeSwitch(switch123Main1, null), LayoutNodeSwitch(switch124Main1, null))
        assertNotEquals(LayoutNodeSwitch(switch123Main1, switch124Main1), LayoutNodeSwitch(switch123Main1, null))
        assertNotEquals(
            LayoutNodeSwitch(switch123Main1, null).contentHash,
            LayoutNodeSwitch(switch123Connection1, null).contentHash,
        )
        assertNotEquals(
            LayoutNodeSwitch(switch123Main1, null).contentHash,
            LayoutNodeSwitch(switch123Main2, null).contentHash,
        )
    }

    @Test
    fun `Edge content hash works`() {
        val startNode1 = LayoutNodeStartTrack(IntId(1))
        val endNode1 = LayoutNodeEndTrack(IntId(1))
        val switchNode1 = LayoutNodeSwitch(SwitchLink(IntId(1), MAIN, JointNumber(1)), null)
        val segments1 = listOf(segment(Point(0.0, 0.0), Point(1.0, 1.0)), segment(Point(1.0, 1.0), Point(2.0, 2.0)))
        val segments2 = listOf(segment(Point(1.0, 0.0), Point(2.0, 1.0)), segment(Point(2.0, 1.0), Point(3.0, 2.0)))

        val edgeContent = LayoutEdgeContent(startNode1, endNode1, segments1)
        assertEquals(edgeContent.contentHash, edgeContent.contentHash)
        assertEquals(
            edgeContent.contentHash,
            LayoutEdge(
                    IntId(123),
                    LayoutEdgeContent(LayoutNode(IntId(456), startNode1), LayoutNode(IntId(457), endNode1), segments1),
                )
                .contentHash,
        )
        assertNotEquals(edgeContent.contentHash, LayoutEdgeContent(startNode1, endNode1, segments2).contentHash)
        assertNotEquals(edgeContent.contentHash, LayoutEdgeContent(startNode1, switchNode1, segments1).contentHash)
        assertNotEquals(edgeContent.contentHash, LayoutEdgeContent(switchNode1, endNode1, segments1).contentHash)
    }
}
