package fi.fta.geoviite.infra.tracklayout.graph

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutNodeType
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class TopologyTest {

    @Test
    fun `built topology copies source lists and exposes unmodifiable lists`() {
        val firstNodeId = UUID.randomUUID()
        val secondNodeId = UUID.randomUUID()
        val switchReferences = mutableListOf<TopologySwitchReference>()
        val trackReferences = mutableListOf<TopologyLocationTrackReference>()
        val topology = buildTopology {
            addNode(firstNodeId, LayoutNodeType.TRACK_BOUNDARY, Point(0.0, 0.0), switchReferences)
            addNode(secondNodeId, LayoutNodeType.TRACK_BOUNDARY, Point(1.0, 0.0), emptyList())
            addEdge(UUID.randomUUID(), firstNodeId, secondNodeId, 1.0, trackReferences)
        }

        switchReferences.add(
            TopologySwitchReference(
                SwitchLink(IntId<LayoutSwitch>(1), SwitchJointRole.MAIN, JointNumber(1)),
                null,
            )
        )

        assertEquals(0, topology.nodes.first().switchReferences.size)
        assertThrows<UnsupportedOperationException> { (topology.nodes as MutableList<TopologyNode>).clear() }
        assertThrows<UnsupportedOperationException> {
            (topology.nodes.first().switchReferences as MutableList<TopologySwitchReference>).clear()
        }
        assertThrows<UnsupportedOperationException> {
            (topology.nodes.first().transitions as MutableList<TopologyTransition>).clear()
        }
        assertThrows<UnsupportedOperationException> { (topology.edges as MutableList<TopologyEdge>).clear() }
        assertThrows<UnsupportedOperationException> {
            (topology.edges.first().trackReferences as MutableList<TopologyLocationTrackReference>).clear()
        }
    }
}
