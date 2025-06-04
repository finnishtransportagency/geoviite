package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.LayoutNode
import fi.fta.geoviite.infra.tracklayout.TmpSwitchNode
import fi.fta.geoviite.infra.tracklayout.TmpTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.END
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType.START
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class TopologyLinkingTest {

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
}
