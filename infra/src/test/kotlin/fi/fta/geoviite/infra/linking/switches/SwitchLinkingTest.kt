package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.asSwitchStructure
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.interpolate
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.data.RR54_1_9
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_10_V
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_9_O
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.clearLinksToSwitch
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.locationTrackWithTwoSwitches
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.segmentPoint
import fi.fta.geoviite.infra.tracklayout.someSegment
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtEnd
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtHalf
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtStart
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SwitchLinkingTest {
    private var testLayoutSwitchId = IntId<LayoutSwitch>(0)
    private var otherLayoutSwitchId = IntId<LayoutSwitch>(99)

    private fun assertSwitchLinkingInfoEquals(
        segment: LayoutSegment,
        layoutSwitchId: IntId<LayoutSwitch>?,
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

    private fun switchLinkingJointAtStart(
        alignment: LocationTrackGeometry,
        segmentIndex: Int,
        jointNumber: Int,
    ): SwitchLinkingJoint = toSwitchLinkingJoint(switchLinkingAtStart(IntId(-1), alignment, segmentIndex, jointNumber))

    private fun switchLinkingJointAtHalf(
        alignment: LocationTrackGeometry,
        segmentIndex: Int,
        jointNumber: Int,
    ): SwitchLinkingJoint = toSwitchLinkingJoint(switchLinkingAtHalf(IntId(-1), alignment, segmentIndex, jointNumber))

    private fun switchLinkingJointAtEnd(
        alignment: LocationTrackGeometry,
        segmentIndex: Int,
        jointNumber: Int,
    ): SwitchLinkingJoint = toSwitchLinkingJoint(switchLinkingAtEnd(IntId(-1), alignment, segmentIndex, jointNumber))

    @Test
    fun adjacentSegmentsShouldHaveSameJointNumber() {
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) =
            locationTrackAndGeometry(
                trackNumberId = IntId(0),
                segments =
                    (1..5).map { num ->
                        val start = (num - 1).toDouble() * 10.0
                        val end = start + 10.0
                        segment(Point(start, start), Point(end, end))
                    },
                id = locationTrackId,
                draft = false,
            )

        val joint1 = switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1)
        val joint2 = switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 1, 5)
        val joint3 = switchLinkingJointAtHalf(origAlignmentNoSwitchInfo, 1, 2)

        val linkingJoints = listOf(joint1, joint2, joint3)

        val updatedAlignment =
            updateAlignmentSegmentsWithSwitchLinking(origAlignmentNoSwitchInfo, testLayoutSwitchId, linkingJoints)

        assertEquals(origAlignmentNoSwitchInfo.segments.size + 1, updatedAlignment.segments.size)
        assertEquals(joint1.m, updatedAlignment.segmentMValues[0].min)
        assertEquals(joint2.m, updatedAlignment.segmentMValues[0].max)
        assertEquals(joint2.m, updatedAlignment.segmentMValues[1].min)
        assertEquals(joint3.m, updatedAlignment.segmentMValues[1].max)
        assertEquals(joint3.m, updatedAlignment.segmentMValues[2].min)

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
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) =
            locationTrackAndGeometry(
                trackNumberId = IntId(0),
                segments =
                    (1..5).map { num ->
                        val start = (num - 1).toDouble() * 10.0
                        val end = start + 10.0
                        segment(Point(start, start), Point(end, end))
                    },
                id = locationTrackId,
                draft = false,
            )

        val linkingJoints =
            listOf(
                SwitchLinkingJoint(
                    JointNumber(1),
                    1,
                    origAlignmentNoSwitchInfo.segmentMValues[1].min - 0.0001,
                    Point(0.0, 0.0),
                ),
                SwitchLinkingJoint(
                    JointNumber(2),
                    1,
                    origAlignmentNoSwitchInfo.segmentMValues[1].max + 0.0001,
                    Point(0.0, 0.0),
                ),
            )

        val updatedAlignment =
            updateAlignmentSegmentsWithSwitchLinking(origAlignmentNoSwitchInfo, testLayoutSwitchId, linkingJoints)
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
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) =
            locationTrackAndGeometry(
                trackNumberId = IntId(0),
                segments =
                    (1..5).map { num ->
                        val start = (num - 1).toDouble() * 10.0
                        val end = start + 10.0
                        segment(Point(start, start), Point(end, end))
                    },
                id = locationTrackId,
                draft = false,
            )

        val splitSegmentIndex = 1
        val splitPointM =
            origAlignmentNoSwitchInfo.segmentMValues[splitSegmentIndex].let { m -> interpolate(m.min, m.max, 0.5) }
        val linkingJoints =
            listOf(
                switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1),
                SwitchLinkingJoint(JointNumber(2), splitSegmentIndex, splitPointM, Point(0.0, 0.0)),
            )

        val updatedAlignment =
            updateAlignmentSegmentsWithSwitchLinking(origAlignmentNoSwitchInfo, testLayoutSwitchId, linkingJoints)

        assertEquals(origAlignmentNoSwitchInfo.segments.size + 1, updatedAlignment.segments.size)

        assertSwitchLinkingInfoEquals(updatedAlignment.segments[0], testLayoutSwitchId, JointNumber(1), null, "first")

        assertSwitchLinkingInfoEquals(updatedAlignment.segments[1], testLayoutSwitchId, null, JointNumber(2), "last")
    }

    @Test
    fun shouldUpdateSwitchLinkingIntoAlignment() {
        val locationTrackId = IntId<LocationTrack>(0)
        val (_, origAlignmentNoSwitchInfo) =
            locationTrackAndGeometry(
                trackNumberId = IntId(0),
                segments =
                    (1..3).map { num ->
                        val start = (num - 1).toDouble() * 10.0
                        val end = start + 10.0
                        segment(Point(start, start), Point(end, end))
                    },
                id = locationTrackId,
                draft = false,
            )

        val linkingJoints =
            listOf(
                switchLinkingJointAtStart(origAlignmentNoSwitchInfo, 0, 1),
                switchLinkingJointAtEnd(origAlignmentNoSwitchInfo, 2, 2),
            )

        val updatedAlignment =
            updateAlignmentSegmentsWithSwitchLinking(origAlignmentNoSwitchInfo, testLayoutSwitchId, linkingJoints)

        assertSwitchLinkingInfoEquals(updatedAlignment.segments[0], testLayoutSwitchId, JointNumber(1), null, "first")
        assertSwitchLinkingInfoEquals(updatedAlignment.segments[1], testLayoutSwitchId, null, null, "middle")
        assertSwitchLinkingInfoEquals(updatedAlignment.segments[2], testLayoutSwitchId, null, JointNumber(2), "last")
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackStart() {
        // TODO: GVT-2915 This would be simple to fix by moving the switch links into geometry,
        // but...
        // TODO: we should rather remove the whole crealLinksToSwitch function and implement the
        // same as
        // TODO: LocationTrackGeometry.withoutSwitch
        val locationTrackWithStartLink =
            locationTrack(
                trackNumberId = IntId(1),
                topologyStartSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(2)),
                topologyEndSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(1)),
                draft = false,
            )
        val (locationTrackWithStartLinkCleared, _) =
            clearLinksToSwitch(locationTrackWithStartLink, trackGeometryOfSegments(someSegment()), testLayoutSwitchId)
        assertEquals(null, locationTrackWithStartLinkCleared.topologyStartSwitch)
        assertEquals(locationTrackWithStartLink.topologyEndSwitch, locationTrackWithStartLinkCleared.topologyEndSwitch)
    }

    @Test
    fun shouldClearSwitchTopologyLinkingFromLocationTrackEnd() {
        // TODO: GVT-2915 This would be simple to fix by moving the switch links into geometry,
        // but...
        // TODO: we should rather remove the whole crealLinksToSwitch function and implement the
        // same as
        // TODO: LocationTrackGeometry.withoutSwitch
        val locationTrackWithEndLink =
            locationTrack(
                trackNumberId = IntId(1),
                topologyStartSwitch = TopologyLocationTrackSwitch(otherLayoutSwitchId, JointNumber(2)),
                topologyEndSwitch = TopologyLocationTrackSwitch(testLayoutSwitchId, JointNumber(1)),
                draft = false,
            )
        val (locationTrackWithEndLinkCleared, _) =
            clearLinksToSwitch(locationTrackWithEndLink, trackGeometryOfSegments(someSegment()), testLayoutSwitchId)
        assertEquals(null, locationTrackWithEndLinkCleared.topologyEndSwitch)
        assertEquals(locationTrackWithEndLink.topologyStartSwitch, locationTrackWithEndLinkCleared.topologyStartSwitch)
    }

    @Test
    fun shouldClearSwitchLinkingInfoFromAlignment() {
        // TODO: GVT-2915 This would be simple to fix by moving the switch links into geometry,
        // but...
        // TODO: we should rather remove the whole crealLinksToSwitch function and implement the
        // same as
        // TODO: LocationTrackGeometry.withoutSwitch
        val (origLocationTrack, origAlignment) =
            locationTrackWithTwoSwitches(
                trackNumberId = IntId(0),
                layoutSwitchId = testLayoutSwitchId,
                otherLayoutSwitchId = otherLayoutSwitchId,
                locationTrackId = IntId(0),
                draft = false,
            )

        val (_, clearedAlignment) = clearLinksToSwitch(origLocationTrack, origAlignment, testLayoutSwitchId)

        (1..3).forEach { i ->
            assertSwitchLinkingInfoEquals(clearedAlignment.segments[i], null, null, null, "#$i, first switch")
        }

        (3..6).forEach { i ->
            assertEquals(
                origAlignment.segments[i],
                clearedAlignment.segments[i],
                "Segments related to another switch should be untouched",
            )
        }
    }

    @Test
    fun `should find joint matches for suggested switch`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())

        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(5), Point(5.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(10.0, 0.0)),
                SwitchStructureJoint(JointNumber(3), Point(5.0, 5.0)),
            )

        val locationTrack1 = locationTrack(IntId(1), id = IntId(1), draft = false)
        val geometry1 =
            trackGeometryOfSegments(segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 0.0), Point(10.0, 0.0))))

        val locationTrack2 = locationTrack(IntId(1), id = IntId(2), draft = false)
        val geometry2 = trackGeometryOfSegments(segments = listOf(segment(Point(0.0, 0.0), Point(5.0, 5.0))))

        val suggestedSwitch =
            fitSwitch(
                joints,
                switch,
                listOf(
                    locationTrack1 to cropNothing(locationTrack1.id as IntId, geometry1),
                    locationTrack2 to cropNothing(locationTrack2.id as IntId, geometry2),
                ),
                null,
            )

        listOf(1, 5, 2, 3).forEach { jointNumber ->
            assertTrue(suggestedSwitch.joints.any { it.number.intValue == jointNumber })
        }

        val joint1 = getJoint(suggestedSwitch, 1)
        val joint5 = getJoint(suggestedSwitch, 5)
        val joint2 = getJoint(suggestedSwitch, 2)
        val joint3 = getJoint(suggestedSwitch, 3)

        // Line 1-5-2
        assertTrue(joint1.matches.any { it.locationTrackId == locationTrack1.id })
        assertTrue(joint5.matches.any { it.locationTrackId == locationTrack1.id })
        assertTrue(joint2.matches.any { it.locationTrackId == locationTrack1.id })

        // Line 1-3
        assertTrue(joint1.matches.any { it.locationTrackId == locationTrack2.id })
        assertTrue(joint3.matches.any { it.locationTrackId == locationTrack2.id })
    }

    @Test
    fun `should match suggested switch with inner segment`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())

        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(5.25, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(14.75, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val geometry =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(2.5, 0.0), Point(5.0, 0.0)),
                        segment(Point(5.0, 0.0), Point(7.5, 0.0), Point(10.0, 0.0)),
                        segment(Point(10.0, 0.0), Point(12.5, 0.0), Point(15.0, 0.0)),
                        segment(Point(15.0, 0.0), Point(17.5, 0.0), Point(20.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to geometry)

        val suggestedSwitch =
            fitSwitch(joints, switch, listOf(locationTrack to cropNothing(locationTrack.id as IntId, geometry)), null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should match suggested switch with inner segment even if its further away`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())

        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(4.75, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(15.25, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val alignment =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(2.5, 0.0), Point(5.0, 0.0)),
                        segment(Point(5.0, 0.0), Point(7.5, 0.0), Point(10.0, 0.0)),
                        segment(Point(10.0, 0.0), Point(12.5, 0.0), Point(15.0, 0.0)),
                        segment(Point(15.0, 0.0), Point(17.5, 0.0), Point(20.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should match with closest segment end point when there are multiple matches`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.6, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(10.6, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val alignment =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(0.1, 0.0), Point(0.5, 0.0)),
                        segment(Point(0.5, 0.0), Point(0.6, 0.0), Point(10.0, 0.0)),
                        segment(Point(10.0, 0.0), Point(10.6, 0.0), Point(11.0, 0.0)),
                        segment(Point(11.0, 0.0), Point(20.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 1, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 2, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should prefer segment end points over normal ones`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.25, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(9.75, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val alignment =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(0.5, 0.0), Point(1.0, 0.0)),
                        segment(Point(1.0, 0.0), Point(9.5, 0.0), Point(10.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 1, locationTrack.id, 0, SuggestedSwitchJointMatchType.START)
        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1, SuggestedSwitchJointMatchType.END)
    }

    @Test
    fun `should never match with segment end point for the first joint`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(3.9995, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(10.0, 0.0)),
            )

        val track = locationTrack(IntId(1), id = IntId(1), draft = false)

        val geometry =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(1.0, 0.0), Point(4.9991, 0.0)),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)),
                    )
            )

        val suggestedSwitch = fitSwitch(joints, switch, listOf(track to cropNothing(track.id as IntId, geometry)), null)

        val joint1 = getJoint(suggestedSwitch, 1)
        assertEquals(1, joint1.matches.size)
        assertTrue(
            joint1.matches.none { it.locationTrackId == track.id && it.matchType == SuggestedSwitchJointMatchType.END }
        )
    }

    @Test
    fun `should never match with the first point for last joint`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(11.0005, 0.0)),
            )

        val track = locationTrack(IntId(1), id = IntId(1), draft = false)

        val geometry =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0)),
                        segment(Point(10.0009, 0.0), Point(15.0, 0.0), Point(20.0, 0.0)),
                    )
            )

        val suggestedSwitch = fitSwitch(joints, switch, listOf(track to cropNothing(track.id as IntId, geometry)), null)

        val joint2 = getJoint(suggestedSwitch, 2)
        assertEquals(1, joint2.matches.size)
        assertTrue(
            joint2.matches.none {
                it.locationTrackId == track.id && it.matchType == SuggestedSwitchJointMatchType.START
            }
        )
    }

    @Test
    fun `should match with alignment regardless of direction`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(10.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(15.0, 0.0)),
            )

        val locationTrack1 = locationTrack(IntId(1), id = IntId(1), draft = false)
        val alignment1 =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(4.0, 0.0)),
                        segment(Point(4.0, 0.0), Point(8.0, 0.0)),
                        segment(Point(8.0, 0.0), Point(10.0, 0.0)),
                        segment(Point(10.0, 0.0), Point(12.0, 0.0)),
                        segment(Point(12.0, 0.0), Point(15.0, 0.0)),
                        segment(Point(15.0, 0.0), Point(16.0, 0.0)),
                        segment(Point(16.0, 0.0), Point(20.0, 0.0)),
                    )
            )

        val locationTrack2 = locationTrack(IntId(1), id = IntId(2), draft = false)
        val alignment2 =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(
                            Point(20.0, 0.0),
                            Point(19.0, 0.0),
                            Point(18.0, 0.0),
                            Point(17.0, 0.0),
                            Point(16.0, 0.0),
                            Point(15.0, 0.0),
                        ),
                        segment(Point(15.0, 0.0), Point(14.0, 0.0), Point(13.0, 0.0)),
                        segment(Point(13.0, 0.0), Point(12.0, 0.0), Point(11.0, 0.0), Point(10.0, 0.0)),
                        segment(
                            Point(10.0, 0.0),
                            Point(9.0, 0.0),
                            Point(8.0, 0.0),
                            Point(7.0, 0.0),
                            Point(6.0, 0.0),
                            Point(5.0, 0.0),
                        ),
                        segment(
                            Point(5.0, 0.0),
                            Point(4.0, 0.0),
                            Point(3.0, 0.0),
                            Point(2.0, 0.0),
                            Point(1.0, 0.0),
                            Point(0.0, 0.0),
                        ),
                    )
            )

        val tracks = listOf(locationTrack1 to alignment1, locationTrack2 to alignment2)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

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
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(11.0, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val alignment =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)),
                        segment(Point(5.0, 0.0), Point(7.5, 0.0), Point(10.0, 0.0), Point(12.5, 0.0), Point(15.0, 0.0)),
                        segment(Point(15.0, 0.0), Point(20.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1)
    }

    @Test
    fun `should match with alignment even if there's no point close by`() {
        val switch = asSwitchStructure(YV60_300_1_10_V())
        val joints =
            listOf(
                SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchStructureJoint(JointNumber(2), Point(11.5, 0.0)),
            )

        val locationTrack = locationTrack(IntId(1), id = IntId(1), draft = false)

        val alignment =
            trackGeometryOfSegments(
                segments =
                    listOf(
                        segment(Point(0.0, 0.0), Point(5.0, 0.0)),
                        segment(Point(5.0, 0.0), Point(10.0, 0.0), Point(15.0, 0.0)),
                        segment(Point(15.0, 0.0), Point(20.0, 0.0)),
                    )
            )

        val tracks = listOf(locationTrack to alignment)

        val suggestedSwitch =
            fitSwitch(joints, switch, tracks.map { (lt, a) -> lt to cropNothing(lt.id as IntId, a) }, null)

        assertOnlyJointMatch(suggestedSwitch, tracks, 2, locationTrack.id, 1, SuggestedSwitchJointMatchType.LINE)
    }

    @Test
    fun `should remove layout switches from all segments that are overridden by switch linking`() {
        val (_, origAlignment) =
            locationTrackWithTwoSwitches(
                trackNumberId = IntId(0),
                layoutSwitchId = testLayoutSwitchId,
                otherLayoutSwitchId = otherLayoutSwitchId,
                locationTrackId = IntId(0),
                draft = false,
            )

        val linkingJoints =
            listOf(switchLinkingJointAtStart(origAlignment, 0, 1), switchLinkingJointAtEnd(origAlignment, 1, 2))

        val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(origAlignment, IntId(100), linkingJoints)

        assertTrue { updatedAlignment.segments.none { it.switchId == testLayoutSwitchId } }
    }

    @Test
    fun cropAlignmentPointsShouldFindPointsInArea() {
        val bbox = BoundingBox(-2.0..3.0, -10.0..10.0)
        val locationTrackInArea =
            locationTrackAndGeometry(
                segment(Point(-4.0, 0.0), Point(-3.0, 0.0), Point(-2.0, 0.0)),
                segment(Point(-2.0, 0.0), Point(-1.0, 0.0), Point(0.0, 0.0), Point(1.0, 0.0), Point(2.0, 0.0)),
                segment(Point(2.0, 0.0), Point(3.0, 0.0), Point(4.0, 0.0), Point(5.0, 0.0)),
                draft = false,
            )
        val croppedAlignment = cropPoints(IntId(1), locationTrackInArea.second, bbox)

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

        val bbox = BoundingBox(-5.0..-4.0, 4.0..5.0)
        val locationTrack =
            locationTrackAndGeometry(segment(points = arrayOf(Point(-5.0, 0.0), Point(5.0, 5.0))), draft = false)
        val croppedAlignment = cropPoints(IntId(1), locationTrack.second, bbox)

        assertTrue(bbox.intersects(locationTrack.second.segments.first().boundingBox))
        assertEquals(0, croppedAlignment.segments.size)
    }

    @Test
    fun shouldFindMatchForYVSwitch() {
        val yvTurnRatio = 1.967 / 34.321 // ~ 0.06
        val switchStructure = asSwitchStructure(YV60_300_1_9_O())
        val locationTrack152 =
            locationTrackAndGeometry(
                segment(from = segmentPoint(-200.0, 0.0), to = Point(-100.0, 0.0)),
                segment(from = Point(-100.0, 0.0), to = Point(100.0, 0.0)),
                id = IntId(1),
                draft = false,
            )
        val locationTrack13 =
            locationTrackAndGeometry(
                segment(from = Point(0.0, 0.0), to = Point(100.0, -100.0 * yvTurnRatio)),
                id = IntId(2),
                draft = false,
            )
        val nearbyPoint = Point(10.0, -1.0)

        val suggestedSwitch =
            createFittedSwitchByPoint(
                IntId(1234),
                nearbyPoint,
                switchStructure,
                listOf(locationTrack152, locationTrack13),
            )

        assertNotNull(suggestedSwitch)
    }

    @Test
    fun `getSwitchBoundsFromTracks handles topology and segment points`() {
        val topoLinkedTrack = locationTrack(trackNumberId = IntId(1), draft = false)
        // let's say the switch's main joint is at (5.0, 5.0), this geometry incidentally doesn't
        // *quite* come to the
        // front joint, but close enough to link anyway
        val topoGeometry =
            trackGeometry(
                edge(
                    endOuterSwitch = switchLinkYV(IntId(1), 1),
                    segments = listOf(segment(Point(4.0, 5.0), Point(4.9, 5.0))),
                )
            )

        val geometryOn152 =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(1), 5),
                    segments = listOf(segment(Point(5.0, 5.0), Point(6.0, 5.0))),
                ),
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 5),
                    endInnerSwitch = switchLinkYV(IntId(1), 2),
                    segments = listOf(segment(Point(6.0, 5.0), Point(7.0, 5.0))),
                ),
                edge(
                    startOuterSwitch = switchLinkYV(IntId(1), 2),
                    segments = listOf(segment(Point(7.0, 5.0), Point(8.0, 5.0))),
                ),
            )
        val geometryOn13 =
            trackGeometry(
                edge(
                    startInnerSwitch = switchLinkYV(IntId(1), 1),
                    endInnerSwitch = switchLinkYV(IntId(1), 3),
                    segments = listOf(segment(Point(5.0, 5.0), Point(6.0, 6.0))),
                )
            )
        val tracks =
            listOf(
                topoLinkedTrack to topoGeometry,
                locationTrack(IntId(1), draft = false) to geometryOn152,
                locationTrack(IntId(1), draft = false) to geometryOn13,
            )
        assertEquals(
            boundingBoxAroundPoints(listOf(Point(4.9, 5.0), Point(7.0, 6.0))),
            getSwitchBoundsFromTracks(tracks.map { (_, g) -> g }, IntId(1)),
        )
        assertEquals(null, getSwitchBoundsFromTracks(tracks.map { (_, g) -> g }, IntId(2)))
    }

    @Test
    fun noSwitchBoundsAreFoundWhenNotLinkedToTracks() {
        val switchId = IntId<LayoutSwitch>(1)
        assertEquals(null, getSwitchBoundsFromTracks(listOf(), switchId))
    }

    @Test
    fun switchBoundsAreFoundFromTracks() {
        val tnId = IntId<LayoutTrackNumber>(1)
        val switchId = IntId<LayoutSwitch>(1)

        val point1 = Point(10.0, 10.0)
        val point2 = Point(12.0, 10.0)
        val point3e1 = Point(10.0, 12.0)
        val point3e2 = Point(10.0, 13.0)

        // Linked from the start only -> second point shouldn't matter
        val track1 =
            locationTrack(trackNumberId = tnId) to
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(switchId, 1),
                        segments = listOf(segment(point1, point1 + Point(5.0, 5.0))),
                    )
                )

        // Linked from the end only -> first point shouldn't matter
        val track2 =
            locationTrack(trackNumberId = tnId) to
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(point2 - Point(5.0, 5.0), point2)),
                    )
                )

        // Linked by segment ends -> both points matter
        val track3 =
            locationTrack(tnId) to
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                        segments = listOf(segment(point3e1, point3e2)),
                    )
                )

        assertEquals(
            boundingBoxAroundPoints(point1, point2, point3e1, point3e2),
            getSwitchBoundsFromTracks(listOf(track1, track2, track3).map { (_, geom) -> geom }, switchId),
        )
    }

    /* Testicaset
    Vaihteen jointtien sijaintien etsiminen

    Vaihteen alignment kahdella edgellä (eli limittäiset vaihteet)
    - tästä on kaksi eri tapausta
        - edgeillä on sama raide, jolloin vaihdealueelle jää vaihdepiste
        - edgeillä on eri raide
     */
    //

    @Test
    fun `Should link fitted switch to edges in simple and clean case`() {
        // track A   track B
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //
        val switchStructure = YV60_300_1_9_O()
        val switchId = IntId<LayoutSwitch>(1)

        // in this test tracks don't need to match switch structure geometrically,
        // but it might be easier to follow the test this way
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5, 2))
        val trackB = createTrack(switchStructure, asJointNumbers(1, 3))
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, 16.0),
                fittedJointMatch(trackA, 2, 30.0),
                fittedJointMatch(trackB, 1, 0.0),
                fittedJointMatch(trackB, 3, 32.567),
            )

        val linkedTracks = linkFittedSwitch(switchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertSwitchNodeExists(linkedTracks, trackA.locationTrackId, switchId = switchId, joint = 1, mValue = 0.0)
        assertSwitchNodeExists(linkedTracks, trackA.locationTrackId, switchId = switchId, joint = 5, mValue = 16.0)
        assertSwitchNodeExists(linkedTracks, trackA.locationTrackId, switchId = switchId, joint = 2, mValue = 30.0)
        assertJointsOnSequentialEdges(
            linkedTracks,
            trackA.locationTrackId,
            switchId = switchId,
            joints = listOf(1, 5, 2),
        )

        assertSwitchNodeExists(linkedTracks, trackB.locationTrackId, switchId = switchId, joint = 1, mValue = 0.0)
        assertSwitchNodeExists(linkedTracks, trackB.locationTrackId, switchId = switchId, joint = 3, mValue = 32.567)
        assertJointsOnSequentialEdges(linkedTracks, trackB.locationTrackId, switchId = switchId, joints = listOf(1, 3))
    }

    @Test
    fun `Should clear switch from old location when linking into new location`() {
        // Diverging tracks are somewhat irrelevant in this test and are therefore ignored.
        //
        //     track A      track B      track C
        //  ├──────────┼───────────────┼─────────┤
        //                             1    5    2   current switch position
        //  1    5     2                             new switch position
        //
        // Joints 1, 5, 2 should be removed from track B and C.
        // Joints 1, 5, 2 should be added to track A.
        //
        val switchStructure = YV60_300_1_9_O()
        val newSwitchId = IntId<LayoutSwitch>(1)

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5, 2), "track A")
        val trackB =
            trackA
                .asNew("track B")
                .moveForward(trackA.length)
                .withTopologicalEndSwitch(newSwitchId, switchStructure, 1) //
        val trackC =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track C")
                .moveForward(trackA.length + trackB.length)
                .withSwitchJointAtStart(newSwitchId, switchStructure, 1)
                .withSwitchJointAtM(16.0, newSwitchId, switchStructure, 5)
                .withSwitchJointAtEnd(newSwitchId, switchStructure, 2)
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry)
        val farawayTracks = listOf(trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length / 2),
                fittedJointMatch(trackA, 2, trackA.length),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, farawayTracks)

        // validate

        // TODO: enabloi kun topologialinkitys on toiminnassa
        // assertTracksExists(linkedTracks, trackA.name, trackB.name, trackC.name)

        assertSwitchDoesNotExist(linkedTracks, trackC.locationTrackId, newSwitchId)
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            jointsWithM = listOf(1 to 0.0, 5 to trackA.length / 2, 2 to trackA.length),
        )

        // TODO: enabloi kun topologialinkitys on toiminnassa
        // assertTopologySwitchAtStart(linkedTracks, trackB.locationTrackId, newSwitchId, 2)
    }

    @Test
    fun `Should adjust joint position in switch linking to handle overlapping switches`() {
        // Diverging tracks are somewhat irrelevant in this test and are therefore ignored.
        //
        //     track A    track B
        //  ├──────────┼──────────┤
        //             1     5    2   existing switch
        //  1     5     2             new switch
        //
        //  Joint 2 of new switch overlaps area of the existing switch.
        //  Joint 2 should be moved to the end of track A.
        //
        val switchStructure = YV60_300_1_9_O()
        val existingSwitchId = IntId<LayoutSwitch>(1)
        val newSwitchId = IntId<LayoutSwitch>(2)
        val overlappingLength = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track A")
                .cutFromEnd(overlappingLength)
                .withTopologicalEndSwitch(existingSwitchId, switchStructure, 1)
        val trackB =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track B")
                .moveForward(trackA.length) // this is unnecessary for the test, but might help debugging
                .withSwitchJointAtStart(existingSwitchId, switchStructure, 1)
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length / 2),
                // NOTE! joint 2 exists on track B
                fittedJointMatch(trackB, 2, overlappingLength),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 1,
            mValue = 0.0, // is unchanged
        )
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 5,
            mValue = trackA.length / 2, // is unchanged
        )
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 2,
            mValue = trackA.length, // joint 2 should be moved to the end of track A
        )
        assertJointsOnSequentialEdges(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joints = listOf(1, 5, 2),
        )
    }

    @Test
    fun `Should not adjust joint position in switch linking if they are too far away from intended position`() {
        // Diverging tracks are somewhat irrelevant in this test and are therefore ignored.
        //
        //     track A    track B
        //  ├──────────┼──────────┤
        //             1     5    2   existing switch
        //  1      5      2           new switch
        //
        //  Joint 2 of new switch overlaps area of the existing switch but is too far away
        //  from intended position to be moved.
        //
        val switchStructure = YV60_300_1_9_O()
        val existingSwitchId = IntId<LayoutSwitch>(1)
        val newSwitchId = IntId<LayoutSwitch>(2)
        val overlappingLength = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE + 0.001 // is 1 millimeter too far away

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track A")
                .cutFromEnd(overlappingLength)
                .withTopologicalEndSwitch(existingSwitchId, switchStructure, 1)
        val trackB =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track B")
                .moveForward(trackA.length) // this is unnecessary for the test, but might help debugging
                .withSwitchJointAtStart(existingSwitchId, switchStructure, 1)
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length / 2),
                // NOTE! joint 2 exists on track B
                fittedJointMatch(trackB, 2, overlappingLength),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertTrue(
            linkedTracks.isEmpty(),
            "There should be no linked tracks as fitted joint is too far from intended position",
        )
    }

    @Test
    fun `Should adjust joint positions at both ends in switch linking to handle overlapping switches`() {
        // Diverging tracks are somewhat irrelevant in this test and are therefore ignored.
        //
        //     track C    track A   track B
        //  ├──────────┼──────────┼──────────┤
        //                        1     5    2   existing switch X
        //  1     5    2                         existing switch Y
        //           1      5       2            new switch
        //
        //  Joints 1 and 2 of new switch overlaps areas of the existing switches.
        //  Joint 1 should be moved to the start of track A.
        //  Joint 2 should be moved to the end of track A.
        //
        val switchStructure = YV60_300_1_9_O()
        val existingSwitchXId = IntId<LayoutSwitch>(10)
        val existingSwitchYId = IntId<LayoutSwitch>(11)
        val newSwitchId = IntId<LayoutSwitch>(1)
        val overlappingLength = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track A")
                .cutFromStart(overlappingLength)
                .cutFromEnd(overlappingLength)
                .withTopologicalStartSwitch(existingSwitchYId, switchStructure, 2)
                .withTopologicalEndSwitch(existingSwitchXId, switchStructure, 1)
        val trackB =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track B")
                .moveForward(trackA.length) // this is unnecessary for the test, but might help debugging
                .withSwitchJointAtStart(existingSwitchXId, switchStructure, 1)
        val trackC =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track C")
                .moveForward(-trackA.length) // this is unnecessary for the test, but might help debugging
                .withSwitchJointAtEnd(existingSwitchYId, switchStructure, 2)
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry, trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                // NOTE! joint 1 exists on track C
                fittedJointMatch(trackC, 1, trackC.length - overlappingLength),
                // Joint 5 exists on track A, as it should
                fittedJointMatch(trackA, 5, trackA.length / 2),
                // NOTE! joint 2 exists on track B
                fittedJointMatch(trackB, 2, overlappingLength),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 1,
            mValue = 0.0, // joint 1 should be moved to the start of track A
        )
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 5,
            mValue = trackA.length / 2,
        )
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joint = 2,
            mValue = trackA.length, // joint 2 should be moved to the end of track A
        )
        assertJointsOnSequentialEdges(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            joints = listOf(1, 5, 2),
        )
    }

    @Test
    fun `Should accept partial joint sequence in switch linking`() {
        // RR-type switches contain partial joint sequences.
        //
        //               4 /
        //                /
        //               /
        //    1        5/       2
        //  ───────────┼───────────
        //   track A  /  track B
        //           /
        //        3 /
        //       track C
        //
        //  Joints 1 and 5 should be linked to track A
        //  Joints 5 and 2 should be linked to track B
        //  Joints 3, 5 and 4 should be linked to track C
        //
        val switchStructure = RR54_1_9()
        val newSwitchId = IntId<LayoutSwitch>(2)

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5), "track A")
        val trackB = createTrack(switchStructure, asJointNumbers(5, 2), "track B")
        val trackC = createTrack(switchStructure, asJointNumbers(3, 5, 4), "track C")
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry, trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                // track A
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length),
                // track B
                fittedJointMatch(trackB, 5, 0.0),
                fittedJointMatch(trackB, 2, trackB.length),
                // track C
                fittedJointMatch(trackC, 3, 0.0),
                fittedJointMatch(trackC, 5, trackC.length / 2),
                fittedJointMatch(trackC, 4, trackC.length),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    1 to 0.0,
                    5 to trackA.length,
                ),
        )

        assertSwitchNodeExists(
            linkedTracks,
            trackB.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    5 to 0.0,
                    2 to trackB.length,
                ),
        )

        assertSwitchNodeExists(
            linkedTracks,
            trackC.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    3 to 0.0,
                    5 to trackC.length / 2,
                    4 to trackC.length,
                ),
        )
    }

    @Test
    fun `Should adjust and accept partial joint sequence in switch linking`() {
        // RR-type switches contain partial joint sequences.
        //
        //               4 /
        //                /
        //               /
        //    1        5/       2
        //  ─────────────┼───────────
        //   track A  /    track B
        //           /
        //        3 /
        //       track C
        //
        // End of the track A is slightly off but in adjustment tolerance.
        // Joints 1 and 5 should be linked to track A
        // Joints 5 and 2 should be linked to track B
        // Joints 3, 5 and 4 should be linked to track C
        //
        val switchStructure = RR54_1_9()
        val newSwitchId = IntId<LayoutSwitch>(2)

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5), "track A")
        val trackB = createTrack(switchStructure, asJointNumbers(5, 2), "track B")
        val trackC = createTrack(switchStructure, asJointNumbers(3, 5, 4), "track C")
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry, trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                // track A
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length),
                // track B
                fittedJointMatch(trackB, 5, 0.0),
                fittedJointMatch(trackB, 2, trackB.length),
                // track C
                fittedJointMatch(trackC, 3, 0.0),
                fittedJointMatch(trackC, 5, trackC.length / 2),
                fittedJointMatch(trackC, 4, trackC.length),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    1 to 0.0,
                    5 to trackA.length,
                ),
        )

        assertSwitchNodeExists(
            linkedTracks,
            trackB.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    5 to 0.0,
                    2 to trackB.length,
                ),
        )

        assertSwitchNodeExists(
            linkedTracks,
            trackC.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    3 to 0.0,
                    5 to trackC.length / 2,
                    4 to trackC.length,
                ),
        )
    }

    @Test
    fun `Should ignore partial joint sequences which do not end at inner joint`() {
        // RR-type switches contain partial joint sequences.
        //
        //               4 /
        //                /
        //               /
        //    1        5/       2
        //  ────────────────┼──────────
        //   track A  /         track B
        //           /
        //        3 /
        //       track C
        //
        // End of the track A is so far away from joint 2 that joint 2 cannot be moved to end of
        // track A, and therefore sequence 1-5-2 cannot be formed.
        //
        // End of the track A is so far away from joint 5 that joint 5 cannot be moved to end of
        // track A, and therefore sequence 5-2 cannot be formed.
        //
        // Joints 1 and 5 are on track A, but track A does not end at joint 5, and therefore
        // sequence 1-5 is not valid. This is because node between track A and B could be another
        // switch node and that other switch would then be connected directly to joint 5, without
        // joint 2, and therefore it would be impossible to find a route, because it would be
        // unknown how routing is entering the switch.
        //
        // Joints 3, 5 and 4 should be linked to track C.
        //
        val switchStructure = RR54_1_9()
        val newSwitchId = IntId<LayoutSwitch>(2)
        val extraLength = 5.0

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA = createTrack(switchStructure, asJointNumbers(1, 5), "track A").expandFromStart(extraLength)
        val trackB = createTrack(switchStructure, asJointNumbers(5, 2), "track B").moveForward(extraLength)
        val trackC = createTrack(switchStructure, asJointNumbers(3, 5, 4), "track C")
        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry, trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                // track A
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, trackA.length - extraLength), // NOTE! is not at the end
                // track B
                fittedJointMatch(trackB, 2, extraLength),
                // track C
                fittedJointMatch(trackC, 3, 0.0),
                fittedJointMatch(trackC, 5, trackC.length / 2),
                fittedJointMatch(trackC, 4, trackC.length),
            )

        val linkedTracks = linkFittedSwitch(newSwitchId, fittedSwitch, nearbyTracks, emptyList())

        // validate
        assertTrue(linkedTracks.size == 1) // linked joints for track C only
        assertSwitchNodeExists(
            linkedTracks,
            trackC.locationTrackId,
            switchId = newSwitchId,
            jointsWithM =
                listOf( //
                    3 to 0.0,
                    5 to trackC.length / 2,
                    4 to trackC.length,
                ),
        )
    }

    //    @Test
    //    fun `foo test`() {
    //        val yv = YV54_200_1_9_V()
    //        val joint3 = (yv.joints.find { j -> j.number == JointNumber(3) })!!
    //        val joint2 = (yv.joints.find { j -> j.number == JointNumber(2) })!!
    //        val joint5 = (yv.joints.find { j -> j.number == JointNumber(5) })!!
    //
    //        val locationTrackId = IntId<LocationTrack>(1)
    //        val startNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.START)
    //        val endNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.END)
    //        val segments = listOf(segment(Point(0.0, 0.0), joint3.location))
    //        val startEdgeNode = TmpEdgeNode(NodePortType.A, startNode)
    //        val endEdgeNode = TmpEdgeNode(NodePortType.A, endNode)
    //        val curveEdge = TmpLayoutEdge(startEdgeNode, endEdgeNode, segments)
    //
    //        val locationTrackId2 = IntId<LocationTrack>(2)
    //        val startNode2 = TmpTrackBoundaryNode(locationTrackId2, TrackBoundaryType.START)
    //        val endNode2 = TmpTrackBoundaryNode(locationTrackId2, TrackBoundaryType.END)
    //        val segments2 = listOf(segment(Point(0.0, 0.0), Point(50.0, 0.0)))
    //        val startEdgeNode2 = TmpEdgeNode(NodePortType.A, startNode2)
    //        val endEdgeNode2 = TmpEdgeNode(NodePortType.A, endNode2)
    //        val straightEdge = TmpLayoutEdge(startEdgeNode2, endEdgeNode2, segments2)
    //
    //        val switchId = IntId<LayoutSwitch>(10)
    //        val linkedCurveEdges =
    //            linkJointsToEdge(
    //                switchId,
    //                curveEdge,
    //                listOf(
    //                    JointOnEdge(locationTrackId, curveEdge, 0.0, switchId,
    // SwitchJointRole.MAIN, JointNumber(1)),
    //                    SwitchJointPositionOnEdge(
    //                        curveEdge,
    //                        lineLength(Point(0.0, 0.0), joint3.location),
    //                        switchId,
    //                        SwitchJointRole.CONNECTION,
    //                        JointNumber(3),
    //                    ),
    //                ),
    //            )
    //        val linkedStraightEdges =
    //            linkJointsToEdge(
    //                straightEdge,
    //                listOf(
    //                    SwitchJointPositionOnEdge(straightEdge, 0.0, switchId,
    // SwitchJointRole.MAIN, JointNumber(1)),
    //                    SwitchJointPositionOnEdge(
    //                        straightEdge,
    //                        lineLength(Point(0.0, 0.0), joint5.location),
    //                        switchId,
    //                        SwitchJointRole.MATH,
    //                        JointNumber(5),
    //                    ),
    //                    SwitchJointPositionOnEdge(
    //                        straightEdge,
    //                        lineLength(Point(0.0, 0.0), joint2.location),
    //                        switchId,
    //                        SwitchJointRole.CONNECTION,
    //                        JointNumber(2),
    //                    ),
    //                ),
    //            )
    //    }

}

private fun getJoint(switchSuggestion: FittedSwitch, jointNumber: Int) =
    switchSuggestion.joints.first { it.number.intValue == jointNumber }

private fun assertOnlyJointMatch(
    switchSuggestion: FittedSwitch,
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
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
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    jointNumber: Int,
    locationTrackId: DomainId<LocationTrack>,
    segmentIndex: Int?,
    matchType: SuggestedSwitchJointMatchType?,
) {
    assertEquals(tracks.size, tracks.map { it.first.id }.distinct().size)

    val joint = getJoint(switchSuggestion, jointNumber)
    val match = joint.matches.find { it.locationTrackId == locationTrackId }!!
    if (matchType != null)
        assertEquals(matchType, match.matchType, "match type for joint $jointNumber on track $locationTrackId")
    assertEquals(segmentIndex, match.segmentIndex, "segment index for joint $jointNumber on track $locationTrackId")
}

fun assertTrackAndGeometry(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
): Pair<LocationTrack, LocationTrackGeometry> {
    val trackAndGeometry = tracks.firstOrNull { (locationTrack, _) -> locationTrack.id == locationTrackId }
    assertNotNull(trackAndGeometry, "Tracks do not contain location track $locationTrackId")
    return trackAndGeometry
}

fun assertTrackAndGeometry(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    name: AlignmentName,
): Pair<LocationTrack, LocationTrackGeometry> {
    val trackAndGeometry = tracks.firstOrNull { (locationTrack, _) -> locationTrack.name == name }
    assertNotNull(trackAndGeometry, "Tracks do not contain location track '$name'")
    return trackAndGeometry
}

fun assertTracksExists(tracks: List<Pair<LocationTrack, LocationTrackGeometry>>, vararg trackNames: AlignmentName) {
    trackNames.forEach { name -> assertTrackAndGeometry(tracks, name) }
}

fun assertSwitchNodeExists(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackName: String,
    switchId: IntId<LayoutSwitch>,
    jointsWithM: List<Pair<Int, Double>>,
) {
    val (locationTrack, _) =
        requireNotNull(
            tracks.firstOrNull { (locationTrack, _) -> locationTrack.name == AlignmentName(locationTrackName) }
        ) {
            "Location track $locationTrackName not found"
        }
    jointsWithM.forEach { (jointNumber, mValue) ->
        assertSwitchNodeExists(tracks, locationTrack.id, switchId, jointNumber, mValue)
    }
}

fun assertSwitchDoesNotExist(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
) {
    val (locationTrack, geometry) = assertTrackAndGeometry(tracks, locationTrackId)
    assertTrue(!geometry.containsSwitch(switchId), "Track ${locationTrack.name} should not contain switch $switchId")
}

fun assertTopologySwitchAtStart(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
    jointNumber: Int,
) {
    val (locationTrack, geometry) = assertTrackAndGeometry(tracks, locationTrackId)
    assertTrue(
        geometry.startNode?.switchOut?.let { switchLink ->
            switchLink.id == switchId && switchLink.jointNumber == JointNumber(jointNumber)
        } ?: false,
        "Track ${locationTrack.name} should have switch: $switchId joint: $jointNumber as start topology connection",
    )
}

fun assertTopologySwitchAtEnd(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
    jointNumber: Int,
) {
    val (locationTrack, geometry) = assertTrackAndGeometry(tracks, locationTrackId)
    assertTrue(
        geometry.endNode?.switchOut?.let { switchLink ->
            switchLink.id == switchId && switchLink.jointNumber == JointNumber(jointNumber)
        } ?: false,
        "Track ${locationTrack.name} should have switch: $switchId joint: $jointNumber as end topology connection",
    )
}

fun assertSwitchNodeExists(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
    jointsWithM: List<Pair<Int, Double>>,
) {
    jointsWithM.forEach { (jointNumber, mValue) ->
        assertSwitchNodeExists(tracks, locationTrackId, switchId, jointNumber, mValue)
    }
}

fun assertSwitchNodeExists(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
    joint: Int,
    mValue: Double,
) {
    val (locationTrack, geometry) = assertTrackAndGeometry(tracks, locationTrackId)
    val switchNode = geometry.nodes.firstOrNull { node -> node.containsInnerSwitch(switchId) }
    assertNotNull(switchNode, "Location track ${locationTrack.name} nodes do not contain switch $switchId")
    val jointNodeAndLocation =
        geometry.nodesWithLocation.firstOrNull { (node, _) -> node.containsInnerJoint(switchId, JointNumber(joint)) }
    assertNotNull(
        jointNodeAndLocation,
        "Location track ${locationTrack.name} nodes do not contain switch $switchId with joint $joint",
    )
    val (_, location) = jointNodeAndLocation
    assertEquals(mValue, location.m, 0.001, "Node for joint $joint M-value is not matching")
}

fun assertJointsOnSequentialEdges(
    tracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
    locationTrackId: DomainId<LocationTrack>,
    switchId: IntId<LayoutSwitch>,
    joints: List<Int>,
) {
    val (locationTrack, geometry) = assertTrackAndGeometry(tracks, locationTrackId)
    val nodeIndexes =
        joints.map { joint ->
            val nodeIndex =
                geometry.nodes.indexOfFirst { node -> node.containsInnerJoint(switchId, JointNumber(joint)) }
            assertTrue(
                nodeIndex != -1,
                "Location track ${locationTrack.name} nodes do not contain switch $switchId with joint $joint",
            )
            nodeIndex
        }
    val isContinuous = nodeIndexes.zipWithNext { index1, index2 -> index1 + 1 == index2 }.all { it }
    assertTrue(isContinuous, "Joints $joints are not in continuous edges, node indexes: $nodeIndexes")
}
