package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.linking.switches.*
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.interpolate
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_10_V
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_9_O
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SwitchLinkingTest {
    private var testLayoutSwitchId = IntId<TrackLayoutSwitch>(0)
    private var otherLayoutSwitchId = IntId<TrackLayoutSwitch>(99)

    private fun assertSwitchLinkingInfoEquals(
        segment: LayoutSegment,
        layoutSwitchId: IntId<TrackLayoutSwitch>?,
        startJointNumber: JointNumber?,
        endJointNumber: JointNumber?,
        segmentName: String = "",
    ) {
        val fullSegmentName = "Segment" + if (segmentName.isNotEmpty()) " ($segmentName)" else ""
        assertEquals(layoutSwitchId, segment.switchId, "$fullSegmentName switchId")
        assertEquals(startJointNumber, segment.startJointNumber, "$fullSegmentName startJointNumber")
        assertEquals(endJointNumber, segment.endJointNumber, "$fullSegmentName endJointNumber")
    }

    private fun toSwitchLinkingJoint(ss: FittedSwitchJointMatch) =
        SwitchLinkingJoint(ss.switchJoint.number, ss.segmentIndex, ss.m, Point(0.0, 0.0))

    private fun switchLinkingJointAtStart(alignment: LayoutAlignment, segmentIndex: Int, jointNumber: Int): SwitchLinkingJoint =
        toSwitchLinkingJoint(switchLinkingAtStart(IntId(-1), alignment, segmentIndex, jointNumber))
    private fun switchLinkingJointAtHalf(alignment: LayoutAlignment, segmentIndex: Int, jointNumber: Int): SwitchLinkingJoint =
        toSwitchLinkingJoint(switchLinkingAtHalf(IntId(-1), alignment, segmentIndex, jointNumber))
    private fun switchLinkingJointAtEnd(alignment: LayoutAlignment, segmentIndex: Int, jointNumber: Int): SwitchLinkingJoint =
        toSwitchLinkingJoint(switchLinkingAtEnd(IntId(-1), alignment, segmentIndex, jointNumber))

    @Test
    fun adjacentSegmentsShouldHaveSameJointNumber() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength).also { s -> startLength += s.length }
        }, locationTrackId)

        val joint1 = switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1)
        val joint2 = switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 1, 5)
        val joint3 = switchLinkingJointAtHalf(origAlignmentNoSwitchInfo, 1, 2)

        val linkingJoints = listOf(joint1, joint2, joint3)

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )

        assertEquals(origAlignmentNoSwitchInfo.segments.size+1, updatedAlignment.segments.size)
        assertEquals(joint1.m, updatedAlignment.segments[0].startM)
        assertEquals(joint2.m, updatedAlignment.segments[0].endM)
        assertEquals(joint2.m, updatedAlignment.segments[1].startM)
        assertEquals(joint3.m, updatedAlignment.segments[1].endM)
        assertEquals(joint3.m, updatedAlignment.segments[2].startM)

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[0],
            testLayoutSwitchId,
            JointNumber(1),
            JointNumber(5),
            "first",
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            JointNumber(5),
            JointNumber(2),
            "last",
        )
    }

    @Test
    fun shouldSnapToSegmentsFirstAndLastPoint() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength).also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                1,
                origAlignmentNoSwitchInfo.segments[1].startM - 0.0001,
                Point(0.0, 0.0),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                1,
                origAlignmentNoSwitchInfo.segments[1].endM + 0.0001,
                Point(0.0, 0.0),
            ),
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )
        assertEquals(origAlignmentNoSwitchInfo.segments.size, updatedAlignment.segments.size)
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            JointNumber(1),
            JointNumber(2),
            "first",
        )
    }

    @Test
    fun shouldSplitSegmentsToSwitchGeometry() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength).also { s -> startLength += s.length }
        }, locationTrackId)

        val splitSegmentIndex = 1
        val splitPointM = origAlignmentNoSwitchInfo.segments[splitSegmentIndex]
            .let { s -> interpolate(s.startM, s.endM, 0.5) }
        val linkingJoints = listOf(
            switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1),
            SwitchLinkingJoint(JointNumber(2), splitSegmentIndex, splitPointM, Point(0.0, 0.0)),
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )

        assertEquals(origAlignmentNoSwitchInfo.segments.size+1, updatedAlignment.segments.size)

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[0],
            testLayoutSwitchId,
            JointNumber(1),
            null,
            "first",
        )

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            null,
            JointNumber(2),
            "last",
        )
    }

    @Test
    fun shouldUpdateSwitchLinkingIntoAlignment() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..3).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength).also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1),
            switchLinkingJointAtEnd(origAlignmentNoSwitchInfo, 2, 2),
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[0],
            testLayoutSwitchId,
            JointNumber(1),
            null,
            "first",
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            null,
            null,
            "middle",
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[2],
            testLayoutSwitchId,
            null,
            JointNumber(2),
            "last",
        )
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackStart() {
        val locationTrackWithStartLink = locationTrack(IntId(1)).copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(2)),
            topologyEndSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(1)),
        )
        val (locationTrackWithStartLinkCleared, _) = clearLinksToSwitch(
            locationTrackWithStartLink,
            alignment(someSegment()),
            testLayoutSwitchId,
        )
        assertEquals(null, locationTrackWithStartLinkCleared.topologyStartSwitch)
        assertEquals(locationTrackWithStartLink.topologyEndSwitch, locationTrackWithStartLinkCleared.topologyEndSwitch)
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackEnd() {
        val locationTrackWithEndLink = locationTrack(IntId(1)).copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(2)),
            topologyEndSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(1)),
        )
        val (locationTrackWithEndLinkCleared, _) = clearLinksToSwitch(
            locationTrackWithEndLink,
            alignment(someSegment()),
            testLayoutSwitchId,
        )
        assertEquals(null, locationTrackWithEndLinkCleared.topologyEndSwitch)
        assertEquals(locationTrackWithEndLink.topologyStartSwitch, locationTrackWithEndLinkCleared.topologyStartSwitch)
    }

    @Test
    fun shouldClearSwitchLinkingInfoFromAlignment() {
        val (origLocationTrack, origAlignment) = locationTrackWithTwoSwitches(
            IntId(0), testLayoutSwitchId, otherLayoutSwitchId, locationTrackId = IntId(0)
        )

        val (_, clearedAlignment) = clearLinksToSwitch(
            origLocationTrack,
            origAlignment,
            testLayoutSwitchId,
        )

        (1..3).forEach { i ->
            assertSwitchLinkingInfoEquals(
                clearedAlignment.segments[i],
                null,
                null,
                null,
                "#$i, first switch",
            )
        }

        (3..6).forEach { i ->
            assertEquals(
                origAlignment.segments[i],
                clearedAlignment.segments[i],
                "Segments related to another switch should be untouched"
            )
        }
    }

    @Test
    fun `should find joint matches for suggested switch`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(5),
                Point(5.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(3),
                Point(5.0, 5.0),
            ),
        )

        val locationTrack1 = locationTrack(IntId(1), id = IntId(1))
        val alignment1 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                )
            ),
        )

        val locationTrack2 = locationTrack(IntId(1), id = IntId(2))
        val alignment2 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 5.0),
                )
            ),
        )

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            listOf(locationTrack1 to alignment1, locationTrack2 to alignment2),
            null,
            null,
            null,
        ) { null }

        listOf(1, 5, 2, 3).forEach { jointNumber ->
            assertTrue(suggestedSwitch.joints.any { it.number.intValue == jointNumber })
        }

        val joint1 = getJoint(suggestedSwitch, 1)
        val joint5 = getJoint(suggestedSwitch, 5)
        val joint2 = getJoint(suggestedSwitch, 2)
        val joint3 = getJoint(suggestedSwitch, 3)

        //Line 1-5-2
        assertTrue(joint1.matches.any { it.locationTrackId == locationTrack1.id })
        assertTrue(joint5.matches.any { it.locationTrackId == locationTrack1.id })
        assertTrue(joint2.matches.any { it.locationTrackId == locationTrack1.id })

        //Line 1-3
        assertTrue(joint1.matches.any { it.locationTrackId == locationTrack2.id })
        assertTrue(joint3.matches.any { it.locationTrackId == locationTrack2.id })
    }

    @Test
    fun `should match suggested switch with inner segment`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(5.25, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(14.75, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(2.5, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(7.5, 0.0),
                    Point(10.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.5, 0.0),
                    Point(15.0, 0.0),
                    startM = 10.0,
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(17.5, 0.0),
                    Point(20.0, 0.0),
                    startM = 15.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should match suggested switch with inner segment even if its further away`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(4.75, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(15.25, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(2.5, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(7.5, 0.0),
                    Point(10.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.5, 0.0),
                    Point(15.0, 0.0),
                    startM = 10.0,
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(17.5, 0.0),
                    Point(20.0, 0.0),
                    startM = 15.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should match with closest segment end point when there are multiple matches`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.6, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.6, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(0.1, 0.0),
                    Point(0.5, 0.0),
                ),
                segment(
                    Point(0.5, 0.0),
                    Point(0.6, 0.0),
                    Point(10.0, 0.0),
                    startM = 0.5,
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(10.6, 0.0),
                    Point(11.0, 0.0),
                    startM = 10.0,
                ),
                segment(
                    Point(11.0, 0.0),
                    Point(20.0, 0.0),
                    startM = 11.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should prefer segment end points over normal ones`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.25, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(9.75, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(0.5, 0.0),
                    Point(1.0, 0.0),
                ),
                segment(
                    Point(1.0, 0.0),
                    Point(9.5, 0.0),
                    Point(10.0, 0.0),
                    startM = 1.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 0, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should never match with segment end point for the first joint`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(3.9995, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.0, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(1.0, 0.0),
                    Point(4.9991, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                    startM = 4.999,
                ),
            ),
        )

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null,
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(joint1.matches.none { it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.END })
    }

    @Test
    fun `should never match with the first point for last joint`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.0005, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(10.0009, 0.0),
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    startM = 10.0,
                ),
            ),
        )

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null,
        ) { null }

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.none { it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.START })
    }

    @Test
    fun `should match with alignment regardless of direction`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(10.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(15.0, 0.0),
            ),
        )

        val locationTrack1 = locationTrack(IntId(1), id = IntId(1))
        val alignment1 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(4.0, 0.0),
                ),
                segment(
                    Point(4.0, 0.0),
                    Point(8.0, 0.0),
                    startM = 4.0,
                ),
                segment(
                    Point(8.0, 0.0),
                    Point(10.0, 0.0),
                    startM = 8.0,
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.0, 0.0),
                    startM = 10.0,
                ),
                segment(
                    Point(12.0, 0.0),
                    Point(15.0, 0.0),
                    startM = 12.0,
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(16.0, 0.0),
                    startM = 15.0,
                ),
                segment(
                    Point(16.0, 0.0),
                    Point(20.0, 0.0),
                    startM = 16.0,
                ),
            ),
        )

        val locationTrack2 = locationTrack(IntId(1), id = IntId(2))
        val alignment2 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(20.0, 0.0),
                    Point(19.0, 0.0),
                    Point(18.0, 0.0),
                    Point(17.0, 0.0),
                    Point(16.0, 0.0),
                    Point(15.0, 0.0),
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(14.0, 0.0),
                    Point(13.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(13.0, 0.0),
                    Point(12.0, 0.0),
                    Point(11.0, 0.0),
                    Point(10.0, 0.0),
                    startM = 7.0,
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(9.0, 0.0),
                    Point(8.0, 0.0),
                    Point(7.0, 0.0),
                    Point(6.0, 0.0),
                    Point(5.0, 0.0),
                    startM = 10.0,
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(4.0, 0.0),
                    Point(3.0, 0.0),
                    Point(2.0, 0.0),
                    Point(1.0, 0.0),
                    Point(0.0, 0.0),
                    startM = 15.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack1 to alignment1, locationTrack2 to alignment2)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(2, joint1.matches.size)
        assertJointMatchExists(suggestedSwitch, tracks, 1, locationTrack1.id, 3, SuggestedSwitchJointMatchType.START)
        assertJointMatchExists(suggestedSwitch, tracks, 1, locationTrack2.id, 2, SuggestedSwitchJointMatchType.END)

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(2, joint2.matches.size)
        assertJointMatchExists(suggestedSwitch, tracks, 2, locationTrack1.id, 4, SuggestedSwitchJointMatchType.END)
        assertJointMatchExists(suggestedSwitch, tracks, 2, locationTrack2.id, 1, SuggestedSwitchJointMatchType.START)
    }

    @Test
    fun `should match with alignment if joint is on the line`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.0, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(7.5, 0.0),
                    Point(10.0, 0.0),
                    Point(12.5, 0.0),
                    Point(15.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    startM = 15.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1)
    }

    @Test
    fun `should match with alignment even if there's no point close by`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0),
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.5, 0.0),
            ),
        )

        val locationTrack = locationTrack(IntId(1), id = IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                    Point(15.0, 0.0),
                    startM = 5.0,
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    startM = 15.0,
                ),
            ),
        )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch = fitSwitch(
            joints,
            switch,
            tracks,
            null,
            null,
            null,
        ) { null }

        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1, SuggestedSwitchJointMatchType.LINE)
    }

    @Test
    fun `should remove layout switches from all segments that are overridden by switch linking`() {
        val (_, origAlignment) = locationTrackWithTwoSwitches(
            IntId(0), testLayoutSwitchId, otherLayoutSwitchId, locationTrackId = IntId(0)
        )

        val linkingJoints = listOf(
            switchLinkingJointAtStart(origAlignment, 0, 1),
            switchLinkingJointAtEnd(origAlignment, 1, 2),
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignment,
            IntId(100),
            linkingJoints,
        )

        assertTrue { updatedAlignment.segments.none { it.switchId == testLayoutSwitchId } }
    }

    @Test
    fun cropAlignmentPointsShouldFindPointsInArea() {
        val bbox = BoundingBox(
            -2.0..3.0,
            -10.0..10.0,
        )
        val locationTrackInArea = locationTrackAndAlignment(
            segment(
                Point(-4.0, 0.0),
                Point(-3.0, 0.0),
                Point(-2.0, 0.0),
            ),
            segment(
                Point(-2.0, 0.0),
                Point(-1.0, 0.0),
                Point(0.0, 0.0),
                Point(1.0, 0.0),
                Point(2.0, 0.0),
            ),
            segment(
                Point(2.0, 0.0),
                Point(3.0, 0.0),
                Point(4.0, 0.0),
                Point(5.0, 0.0),
            ),
        )
        val croppedAlignment = cropPoints(locationTrackInArea.second, bbox)

        assertEquals(2, croppedAlignment.segments.size)
        assertEquals(Point(-2.0, 0.0), croppedAlignment.firstSegmentStart?.toPoint())
        assertEquals(Point(3.0, 0.0), croppedAlignment.lastSegmentEnd?.toPoint())
    }


    @Test
    fun cropAlignmentPointsShouldIgnoreSegmentsThatDoesNotHavePointsInArea() {
        // Bounding box of a segment intersects with the bounding box, but the segment
        // does not contain points inside the bounding box. Crop should filter out
        // all segments/points.
        //
        //  \
        //   \
        //    \
        //  □  \
        //
        //  □ = bounding box
        //  \ = alignment

        val bbox = BoundingBox(
            -5.0..-4.0,
            4.0..5.0,
        )
        val locationTrack = locationTrackAndAlignment(
            segment(
                points = arrayOf(
                    Point(-5.0, 0.0),
                    Point(5.0, 5.0),
                )
            ),
        )
        val croppedAlignment = cropPoints(locationTrack.second, bbox)

        assertTrue(bbox.intersects(locationTrack.second.segments.first().boundingBox))
        assertEquals(0, croppedAlignment.segments.size)
    }

    @Test
    fun shouldFindMatchForYVSwitch() {
        val yvTurnRatio = 1.967 / 34.321 // ~ 0.06
        val switchStructure = YV60_300_1_9_O().copy(id = IntId(1))
        val locationTrack152 = locationTrackAndAlignment(
            segment(from = segmentPoint(-200.0, 0.0), to = Point(-100.0, 0.0)),
            segment(from = Point(-100.0, 0.0), to = Point(100.0, 0.0)),
            id = IntId(1),
        )
        val locationTrack13 = locationTrackAndAlignment(
            segment(from = Point(0.0, 0.0), to = Point(100.0, -100.0 * yvTurnRatio)),
            id = IntId(2),
        )
        val nearbyPoint = Point(10.0, -1.0)

        val suggestedSwitch = createSuggestedSwitchByPoint(
            nearbyPoint,
            switchStructure,
            listOf(locationTrack152, locationTrack13),
        )

        assertNotNull(suggestedSwitch)
    }

    @Test
    fun `getSwitchBoundsFromTracks handles topology and segment points`() {
        val topoLinkedTrack = locationTrack(
            IntId(1), topologyEndSwitch = TopologyLocationTrackSwitch(IntId(1), JointNumber(1))
        )
        // let's say the switch's main joint is at (5.0, 5.0), this geometry incidentally doesn't *quite* come to the
        // front joint, but close enough to link anyway
        val topoAlignment = alignment(segment(Point(4.0, 5.0), Point(4.9, 5.0)))

        val alignmentOn152 = alignment(
            segment(
                Point(5.0, 5.0),
                Point(6.0, 5.0),
                switchId = IntId(1),
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(5)
            ),
            segment(
                Point(6.0, 5.0),
                Point(7.0, 5.0),
                switchId = IntId(1),
                startJointNumber = JointNumber(5),
                endJointNumber = JointNumber(2)
            ),
            segment(Point(7.0, 5.0), Point(8.0, 5.0))
        )
        val alignmentOn13 = alignment(
            segment(
                Point(5.0, 5.0),
                Point(6.0, 6.0),
                switchId = IntId(1),
                startJointNumber = JointNumber(1),
                endJointNumber = JointNumber(3)
            )
        )
        val tracks = listOf(
            topoLinkedTrack to topoAlignment,
            locationTrack(IntId(1)) to alignmentOn152,
            locationTrack(IntId(1)) to alignmentOn13
        )
        assertEquals(
            boundingBoxAroundPoints(listOf(Point(4.9, 5.0), Point(7.0, 6.0))), getSwitchBoundsFromTracks(
                tracks, IntId(1)
            )
        )
        assertEquals(null, getSwitchBoundsFromTracks(tracks, IntId(2)))
    }
}

private fun getJoint(switchSuggestion: FittedSwitch, jointNumber: Int) =
    switchSuggestion.joints.first { it.number.intValue == jointNumber }

private fun assertOnlyJointMatch(
    switchSuggestion: FittedSwitch,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    jointNumber: Int,
    locationTrackId: DomainId<LocationTrack>,
    segmentIndex: Int?,
    matchType: SuggestedSwitchJointMatchType? = null,
) {
    assertEquals(1, getJoint(switchSuggestion, jointNumber).matches.size)
    assertJointMatchExists(switchSuggestion, tracks, jointNumber, locationTrackId, segmentIndex, matchType)
}

private fun assertJointMatchExists(
    switchSuggestion: FittedSwitch,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    jointNumber: Int,
    locationTrackId: DomainId<LocationTrack>,
    segmentIndex: Int?,
    matchType: SuggestedSwitchJointMatchType?,
) {
    assertEquals(tracks.size, tracks.map { it.first.id }.distinct().size)

    val joint = getJoint(switchSuggestion, jointNumber)
    val match = joint.matches.find { it.locationTrackId == locationTrackId }!!
    if (matchType != null) assertEquals(
        matchType,
        match.matchType,
        "match type for joint $jointNumber on track $locationTrackId"
    )
    assertEquals(segmentIndex, match.segmentIndex, "segment index for joint $jointNumber on track $locationTrackId")
}
