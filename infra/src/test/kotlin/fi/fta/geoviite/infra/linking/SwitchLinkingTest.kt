package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
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
        val fullSegmentName = "Segment" + if (segmentName.isNotEmpty())
            " ($segmentName)" else ""
        assertEquals(layoutSwitchId, segment.switchId, "$fullSegmentName switchId")
        assertEquals(startJointNumber, segment.startJointNumber, "$fullSegmentName startJointNumber")
        assertEquals(endJointNumber, segment.endJointNumber, "$fullSegmentName endJointNumber")
    }

    @Test
    fun adjacentSegmentsShouldHaveSameJointNumber() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point.zero(),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId,
                        segmentIndex = 0,
                        m = origAlignmentNoSwitchInfo.segments[0].start,
                    )
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(5),
                Point(10.0, 10.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId,
                        segmentIndex = 1,
                        m = origAlignmentNoSwitchInfo.segments[1].start,
                    )
                )
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(15.0, 15.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId,
                        segmentIndex = 1,
                        m = origAlignmentNoSwitchInfo.segments[1].end,
                    )
                )
            )
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
            JointNumber(5),
            "first"
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            JointNumber(5),
            JointNumber(2),
            "last"
        )
    }

    @Test
    fun shouldSortLinkingJointsInSegmentOrder() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(2),
                Point(15.0, 15.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(locationTrackId, origAlignmentNoSwitchInfo, 1),
                )
            ),
            SwitchLinkingJoint(
                JointNumber(5),
                Point(10.0, 10.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 1),
                )
            ),
            SwitchLinkingJoint(
                JointNumber(1),
                Point.zero(),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 0),
                ),
            ),
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
            JointNumber(5),
            "first"
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            JointNumber(5),
            JointNumber(2),
            "last"
        )
    }

    @Test
    fun shouldSnapToSegmentsFirstPoint() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(2),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(locationTrackId, origAlignmentNoSwitchInfo, 1),
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 0.5, y = 0.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 0),
                ),
            ),
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
            "first"
        )
    }

    @Test
    fun shouldSnapToSegmentsLastPoint() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 9.5, y = 9.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 0),
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(locationTrackId, origAlignmentNoSwitchInfo, 1),
                ),
            ),
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            JointNumber(1),
            JointNumber(2),
            "first"
        )
    }

    @Test
    fun shouldSplitSegmentsToSwitchGeometry() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point.zero(),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 0),
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(15.0, 15.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(locationTrackId, origAlignmentNoSwitchInfo, 1),
                )
            )
        )

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
            origAlignmentNoSwitchInfo,
            testLayoutSwitchId,
            linkingJoints,
        )

        assertEquals(6, updatedAlignment.segments.size)

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[0],
            testLayoutSwitchId,
            JointNumber(1),
            null,
            "first"
        )

        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            null,
            JointNumber(2),
            "last"
        )
    }

    @Test
    fun shouldUpdateSwitchLinkingIntoAlignment() {
        var startLength = 0.0
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) = locationTrackAndAlignment(IntId(0), (1..3).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), start = startLength)
                .also { s -> startLength += s.length }
        }, locationTrackId)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point.zero(),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(locationTrackId, origAlignmentNoSwitchInfo, 0),
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(30.0, 30.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(locationTrackId, origAlignmentNoSwitchInfo, 2),
                )
            )
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
            "first"
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[1],
            testLayoutSwitchId,
            null,
            null,
            "middle"
        )
        assertSwitchLinkingInfoEquals(
            updatedAlignment.segments[2],
            testLayoutSwitchId,
            null,
            JointNumber(2),
            "last"
        )
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackStart() {
        val locationTrackWithStartLink = locationTrack(IntId(1)).copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(2)),
            topologyEndSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(1)),
        )
        val (locationTrackWithStartLinkCleared, _) =
            clearSwitchInformationFromSegments(locationTrackWithStartLink, alignment(someSegment()), testLayoutSwitchId)
        assertEquals(null, locationTrackWithStartLinkCleared.topologyStartSwitch)
        assertEquals(locationTrackWithStartLink.topologyEndSwitch, locationTrackWithStartLinkCleared.topologyEndSwitch)
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackEnd() {
        val locationTrackWithEndLink = locationTrack(IntId(1)).copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(2)),
            topologyEndSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(1)),
        )
        val (locationTrackWithEndLinkCleared, _) =
            clearSwitchInformationFromSegments(locationTrackWithEndLink, alignment(someSegment()), testLayoutSwitchId)
        assertEquals(null, locationTrackWithEndLinkCleared.topologyEndSwitch)
        assertEquals(locationTrackWithEndLink.topologyStartSwitch, locationTrackWithEndLinkCleared.topologyStartSwitch)
    }

    @Test
    fun shouldClearSwitchLinkingInfoFromAlignment() {
        val (origLocationTrack, origAlignment) = locationTrackWithTwoSwitches(
            IntId(0), testLayoutSwitchId, otherLayoutSwitchId, locationTrackId = IntId(0)
        )

        val (_, clearedAlignment) = clearSwitchInformationFromSegments(
            origLocationTrack,
            origAlignment,
            testLayoutSwitchId
        )

        (1..3).forEach { i ->
            assertSwitchLinkingInfoEquals(
                clearedAlignment.segments[i],
                null,
                null,
                null,
                "#$i, first switch"
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
                Point(0.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(5),
                Point(5.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(3),
                Point(5.0, 5.0)
            )
        )

        val locationTrack1 = locationTrack(IntId(1))
        val alignment1 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                )
            ),
            sourceId = null
        )

        val locationTrack2 = locationTrack(IntId(1))
        val alignment2 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 5.0),
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack1 to alignment1, locationTrack2 to alignment2),
            null,
            null,
            null
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
                Point(5.25, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(14.75, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 5.0
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.5, 0.0),
                    Point(15.0, 0.0),
                    start = 10.0
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(17.5, 0.0),
                    Point(20.0, 0.0),
                    start = 15.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(joint1.matches.all { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.START })

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.all { it.segmentIndex == 2 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.END })
    }

    @Test
    fun `should match suggested switch with inner segment even if its further away`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(4.75, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(15.25, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 5.0
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.5, 0.0),
                    Point(15.0, 0.0),
                    start = 10.0
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(17.5, 0.0),
                    Point(20.0, 0.0),
                    start = 15.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(joint1.matches.all { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.START })

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.all { it.segmentIndex == 2 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.END })
    }

    @Test
    fun `should match with closest segment end point when there are multiple matches`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.6, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.6, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 0.5
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(10.6, 0.0),
                    Point(11.0, 0.0),
                    start = 10.0
                ),
                segment(
                    Point(11.0, 0.0),
                    Point(20.0, 0.0),
                    start = 11.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(joint1.matches.all { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.START })

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.all { it.segmentIndex == 2 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.END })
    }

    @Test
    fun `should prefer segment end points over normal ones`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.25, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(9.75, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 1.0
                ),
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(joint1.matches.all { it.segmentIndex == 0 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.START })

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.all { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.END })
    }

    @Test
    fun `should never match with segment end point for the first joint`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(3.9995, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(10.0, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 4.999
                ),
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
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
                Point(0.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.0005, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

        val alignment = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(5.0, 0.0),
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(10.0, 0.0),
                    start = 5.0
                ),
                segment(
                    Point(10.0009, 0.0),
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    start = 10.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
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
                Point(10.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(15.0, 0.0)
            ),
        )

        val locationTrack1 = locationTrack(IntId(1))
        val alignment1 = LayoutAlignment(
            segments = listOf(
                segment(
                    Point(0.0, 0.0),
                    Point(4.0, 0.0),
                ),
                segment(
                    Point(4.0, 0.0),
                    Point(8.0, 0.0),
                    start = 4.0
                ),
                segment(
                    Point(8.0, 0.0),
                    Point(10.0, 0.0),
                    start = 8.0
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(12.0, 0.0),
                    start = 10.0
                ),
                segment(
                    Point(12.0, 0.0),
                    Point(15.0, 0.0),
                    start = 12.0
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(16.0, 0.0),
                    start = 15.0
                ),
                segment(
                    Point(16.0, 0.0),
                    Point(20.0, 0.0),
                    start = 16.0
                )
            ),
            sourceId = null
        )

        val locationTrack2 = locationTrack(IntId(1))
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
                    start = 5.0
                ),
                segment(
                    Point(13.0, 0.0),
                    Point(12.0, 0.0),
                    Point(11.0, 0.0),
                    Point(10.0, 0.0),
                    start = 7.0
                ),
                segment(
                    Point(10.0, 0.0),
                    Point(9.0, 0.0),
                    Point(8.0, 0.0),
                    Point(7.0, 0.0),
                    Point(6.0, 0.0),
                    Point(5.0, 0.0),
                    start = 10.0
                ),
                segment(
                    Point(5.0, 0.0),
                    Point(4.0, 0.0),
                    Point(3.0, 0.0),
                    Point(2.0, 0.0),
                    Point(1.0, 0.0),
                    Point(0.0, 0.0),
                    start = 15.0
                ),
            ),
            sourceId = null
        )


        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack1 to alignment1, locationTrack2 to alignment2),
            null,
            null,
            null
        ) { null }

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(2, joint1.matches.size)
        assertTrue(joint1.matches.any { it.segmentIndex == 3 && it.locationTrackId == locationTrack1.id && it.matchType == SuggestedSwitchJointMatchType.START })
        assertTrue(joint1.matches.any { it.segmentIndex == 2 && it.locationTrackId == locationTrack2.id && it.matchType == SuggestedSwitchJointMatchType.END })


        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(2, joint2.matches.size)
        assertTrue(joint2.matches.any { it.segmentIndex == 4 && it.locationTrackId == locationTrack1.id && it.matchType == SuggestedSwitchJointMatchType.END })
        assertTrue(joint2.matches.any { it.segmentIndex == 1 && it.locationTrackId == locationTrack2.id && it.matchType == SuggestedSwitchJointMatchType.START })
    }

    @Test
    fun `should match with alignment if joint is on the line`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.0, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 5.0
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    start = 15.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(joint2.matches.all { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id })
    }

    @Test
    fun `should match with alignment even if there's no point close by`() {
        val switch = YV60_300_1_10_V()

        val joints = listOf(
            SwitchJoint(
                JointNumber(1),
                Point(0.0, 0.0)
            ),
            SwitchJoint(
                JointNumber(2),
                Point(11.5, 0.0)
            ),
        )

        val locationTrack = locationTrack(IntId(1))

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
                    start = 5.0
                ),
                segment(
                    Point(15.0, 0.0),
                    Point(20.0, 0.0),
                    start = 15.0
                )
            ),
            sourceId = null
        )

        val suggestedSwitch = createSuggestedSwitch(
            joints,
            switch,
            listOf(locationTrack to alignment),
            null,
            null,
            null
        ) { null }

        val joint2 = getJoint(suggestedSwitch, 2)
        assertNotNull(joint2.matches.find { it.segmentIndex == 1 && it.locationTrackId == locationTrack.id && it.matchType == SuggestedSwitchJointMatchType.LINE })
    }

    @Test
    fun `should remove layout switches from all segments that are overridden by switch linking`() {
        val (origLocationTrack, origAlignment) = locationTrackWithTwoSwitches(
            IntId(0), testLayoutSwitchId, otherLayoutSwitchId, locationTrackId = IntId(0)
        )

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(10.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(origLocationTrack.id as IntId, origAlignment, 0),
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(25.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(origLocationTrack.id as IntId, origAlignment, 1),
                )
            )
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
            -10.0..10.0
        )
        val locationTrackInArea = locationTrackAndAlignment(
            segment(
                Point(-4.0, 0.0),
                Point(-3.0, 0.0),
                Point(-2.0, 0.0)
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
        assertEquals(Point(-2.0, 0.0), Point(croppedAlignment.allPoints().first()))
        assertEquals(Point(3.0, 0.0), Point(croppedAlignment.allPoints().last()))
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
            4.0..5.0
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
        val switchStructure = YV60_300_1_9_O()
        val locationTrack152 = locationTrackAndAlignment(
            segment(from = point(-200.0, 0.0), to = Point(-100.0, 0.0)),
            segment(from = Point(-100.0, 0.0), to = Point(100.0, 0.0))
        )
        val locationTrack13 = locationTrackAndAlignment(
            segment(from = Point(0.0, 0.0), to = Point(100.0, -100.0 * yvTurnRatio))
        )
        val nearbyPoint = Point(10.0, -1.0)

        val suggestedSwitch = createSuggestedSwitchByPoint(
            nearbyPoint,
            switchStructure,
            listOf(locationTrack152, locationTrack13)
        )

        assertNotNull(suggestedSwitch)
    }
}

private fun getJoint(suggestedSwitch: SuggestedSwitch, jointNumber: Int) =
    suggestedSwitch.joints.first { it.number.intValue == jointNumber }
