package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.SwitchJointType
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchStructureRR54_4x1_9
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class SwitchTopologicalConnectivityTest {

    @Test
    fun `minimal OK switch topological connectivity is OK`() {
        val (switch, link) = switchAndLink()
        val tracks = listOf(track("through track", null, link(1, 5), link(5, 2)), track("branching track", link(1, 3)))
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(listOf(), issues)
    }

    @Test
    fun `track from front joint is allowed to be only topologically connected`() {
        val (switch, link) = switchAndLink()
        val tracks =
            listOf(
                track("through track", link(1, 5), link(5, 2)),
                track("branching track", link(1, 3)),
                track("topo track", topologyLinks = link(1, null)),
            )
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(listOf(), issues)
    }

    @Test
    fun `the only track from the front joint must not be duplicate`() {
        val (switch, link) = switchAndLink()
        val tracks =
            listOf(
                track("through track", link(1, 5), link(5, 2)),
                track("branching track", link(1, 3)),
                track("topo track", isDuplicate = true, topologyLinks = link(1, null)),
            )
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(
            listOf("validation.layout.switch.track-linkage.front-joint-only-duplicate-connected"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `head-to-head YV switches with branching tracks topologically linked to opposing sides are okay`() {
        val (switchA, linkA) = switchAndLink()
        val (switchB, linkB) = switchAndLink()
        val tracks =
            listOf(
                track("through track", linkA(2, 5), linkA(5, 1), linkB(1, 5), linkB(5, 2)),
                track("branching A", linkA(3, 1), topologyLinks = linkB(null, 1)),
                track("branching B", linkB(1, 3), topologyLinks = linkA(1, null)),
            )
        val issuesA = validateSwitchTopologicalConnectivity(switchA, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(emptyList(), issuesA)
        val issuesB = validateSwitchTopologicalConnectivity(switchB, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(emptyList(), issuesB)
    }

    @Test
    fun `one missing alignment causes a warning`() {
        val (switch, link) = switchAndLink()
        val tracks = listOf(track("through track", null, link(1, 5), link(5, 2)))
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(
            listOf("validation.layout.switch.track-linkage.switch-alignment-not-connected"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `one duplicate-only alignment causes a warning`() {
        val (switch, link) = switchAndLink()
        val tracks =
            listOf(
                track("through track", null, link(1, 5), link(5, 2)),
                track("branching track", link(1, 3), isDuplicate = true),
            )
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(
            listOf("validation.layout.switch.track-linkage.switch-alignment-only-connected-to-duplicate"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `one multiply linked alignment causes a warning`() {
        val (switch, link) = switchAndLink()
        val tracks =
            listOf(
                track("through track", null, link(1, 5), link(5, 2)),
                track("branching track", link(1, 3)),
                track("also branching track", link(1, 3)),
            )
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), tracks, null)
        assertEquals(
            listOf("validation.layout.switch.track-linkage.switch-alignment-multiply-connected"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `some connected tracks must be present`() {
        val (switch) = switchAndLink()
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), listOf(), null)
        assertContains(
            issues,
            LayoutValidationIssue(
                LayoutValidationIssueType.ERROR,
                "validation.layout.switch.track-linkage.switch-no-alignments-connected",
                mapOf("switch" to switch.name.toString()),
            ),
        )
    }

    @Test
    fun `through track is not responsible for missing branching track`() {
        val (switch, link) = switchAndLink()
        val throughTrack = track("through track", null, link(1, 5), link(5, 2))
        val issues =
            validateSwitchTopologicalConnectivity(
                switch,
                switchStructureYV60_300_1_9(),
                listOf(throughTrack),
                throughTrack.first,
            )
        assertEquals(listOf(), issues)
    }

    @Test
    fun `rail crossing is OK with layout alignments connected to full or split switch alignments`() {
        val (switch, link) = switchAndLink(switchStructureRR54_4x1_9())
        val tracks =
            listOf(
                track("track 152", link(1, 5), link(5, 2)),
                track("track 45", link(4, 5)),
                track("track 53", link(5, 3)),
            )

        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureRR54_4x1_9(), tracks, null)
        assertEquals(emptyList(), issues)
    }

    @Test
    fun `track through rail crossing is not responsible for missing links on other switch alignments`() {
        val (switch, link) = switchAndLink(switchStructureRR54_4x1_9())
        val track152 = track("track 152", link(1, 5), link(5, 2))

        val issues =
            validateSwitchTopologicalConnectivity(switch, switchStructureRR54_4x1_9(), listOf(track152), track152.first)
        assertEquals(emptyList(), issues)
    }

    @Test
    fun `track partially through rail crossing is responsible for non-full link on its switch alignment`() {
        val (switch, link) = switchAndLink(switchStructureRR54_4x1_9())
        val track53 = track("track 53", link(5, 3))
        val tracks = listOf(track("track 152", link(1, 5), link(5, 2)), track53)

        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureRR54_4x1_9(), tracks, track53.first)
        assertEquals(
            listOf("validation.layout.location-track.switch-linkage.switch-alignment-not-connected"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `degenerate single-joint links cause a warning`() {
        val (switch, link) = switchAndLink()
        val oneTrack = track("one track", null, link(1, null))
        val anotherTrack = track("another track", link(1, null))
        val issues =
            validateSwitchTopologicalConnectivity(
                switch,
                switchStructureYV60_300_1_9(),
                listOf(oneTrack, anotherTrack),
                null,
            )
        assertEquals(
            setOf(
                "validation.layout.switch.track-linkage.switch-alignment-partially-connected",
                "validation.layout.switch.track-linkage.switch-alignment-multiply-connected",
            ),
            issues.map { it.localizationKey.toString() }.toSet(),
        )
    }

    @Test
    fun `track linked to a split alignment is not responsible for missing links on a different switch alignment`() {
        val (switch, link) = switchAndLink(switchStructureRR54_4x1_9())
        val track15 = track("track 15", link(1, 5))
        val track52 = track("track 52", link(5, 2))
        val track45 = track("track 45", link(4, 5))
        // missing track link on 5-3
        val tracks = listOf(track15, track52, track45)
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureRR54_4x1_9(), tracks, track15.first)
        assertEquals(listOf(), issues)
    }

    @Test
    fun `rail crossing alignments can be linked end to end`() {
        val (switch, link) = switchAndLink(switchStructureRR54_4x1_9())
        val track12 = track("track 12", link(1, 2))
        val track43 = track("track 43", link(4, 3))
        val tracks = listOf(track12, track43)
        val issues = validateSwitchTopologicalConnectivity(switch, switchStructureRR54_4x1_9(), tracks, null)
        assertEquals(listOf(), issues)
    }

    @Test
    fun `deleted alignments do not exist`() {
        val (switch, link) = switchAndLink()
        val throughTrack = track("through track", null, link(1, 2))
        val branchingTrack = track("branching track", link(1, 3), isDeleted = true)
        val issues =
            validateSwitchTopologicalConnectivity(
                switch,
                switchStructureYV60_300_1_9(),
                listOf(throughTrack, branchingTrack),
                null,
            )
        assertEquals(
            listOf("validation.layout.switch.track-linkage.switch-alignment-not-connected"),
            issues.map { it.localizationKey.toString() },
        )
    }

    @Test
    fun `switches can be abandoned by deletion`() {
        val (switch, link) = switchAndLink()
        val throughTrack = track("through track", null, link(1, 2), isDeleted = true)
        val branchingTrack = track("branching track", link(1, 3), isDeleted = true)
        val issues =
            validateSwitchTopologicalConnectivity(
                switch,
                switchStructureYV60_300_1_9(),
                listOf(throughTrack, branchingTrack),
                null,
            )
        assertEquals(
            setOf(
                "validation.layout.switch.track-linkage.front-joint-not-connected",
                "validation.layout.switch.track-linkage.switch-no-alignments-connected",
            ),
            issues.map { it.localizationKey.toString() }.toSet(),
        )
    }

    @Test
    fun `a switch with topological connections is not abandoned`() {
        val (switch, link) = switchAndLink()
        val topoTrack = track("topo track", null, topologyLinks = link(1, null))
        val issues =
            validateSwitchTopologicalConnectivity(switch, switchStructureYV60_300_1_9(), listOf(topoTrack), null)
        assertEquals(
            setOf("validation.layout.switch.track-linkage.switch-alignment-not-connected"),
            issues.map { it.localizationKey.toString() }.toSet(),
        )
    }

    // might be a segment's start and/or end joint, might be a track's topology start or end link
    private data class SwitchLinkPair(
        val switchId: IntId<LayoutSwitch>,
        val startJointNumber: JointNumber?,
        val endJointNumber: JointNumber?,
    )

    private fun interface MakeSwitchLinkPair {

        operator fun invoke(start: Int?, end: Int?): SwitchLinkPair
    }

    private fun switchLink(switchId: IntId<LayoutSwitch>) = MakeSwitchLinkPair { startJoint, endJoint ->
        SwitchLinkPair(
            switchId,
            startJointNumber = startJoint?.let(::JointNumber),
            endJointNumber = endJoint?.let(::JointNumber),
        )
    }

    private var idCounter: Int = 0

    private fun track(
        name: String,
        vararg switchLinks: SwitchLinkPair?,
        isDuplicate: Boolean = false,
        isDeleted: Boolean = false,
        topologyLinks: SwitchLinkPair? = null,
    ): Pair<LocationTrack, LayoutAlignment> {
        val locationTrack =
            locationTrack(
                IntId(1),
                id = IntId(idCounter++),
                name = name,
                duplicateOf = if (isDuplicate) IntId(12345) else null,
                state = if (isDeleted) LocationTrackState.DELETED else LocationTrackState.IN_USE,
                topologyStartSwitch =
                    topologyLinks?.let { t ->
                        t.startJointNumber?.let { jointNumber -> TopologyLocationTrackSwitch(t.switchId, jointNumber) }
                    },
                topologyEndSwitch =
                    topologyLinks?.let { t ->
                        t.endJointNumber?.let { jointNumber -> TopologyLocationTrackSwitch(t.switchId, jointNumber) }
                    },
            )
        val alignment =
            alignment(
                switchLinks.mapIndexed { index, link ->
                    segment(
                        Point(0.0, index.toDouble()),
                        Point(0.0, index.toDouble() + 1.0),
                        switchId = link?.switchId,
                        startJointNumber = link?.startJointNumber,
                        endJointNumber = link?.endJointNumber,
                    )
                }
            )
        return locationTrack to alignment
    }

    private fun switchAndLink(
        switchStructure: SwitchStructure = switchStructureYV60_300_1_9()
    ): Pair<LayoutSwitch, MakeSwitchLinkPair> {
        val switchId: IntId<LayoutSwitch> = IntId(idCounter++)
        val switch =
            switch(
                id = switchId,
                structureId = switchStructure.id as IntId,
                joints =
                    switchStructure.joints.map { j ->
                        LayoutSwitchJoint(j.number, SwitchJointType.of(switchStructure, j.number), j.location, null)
                    },
            )
        val link = switchLink(switchId)
        return switch to link
    }
}
