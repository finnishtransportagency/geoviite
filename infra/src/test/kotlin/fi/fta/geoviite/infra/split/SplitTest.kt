package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.NONE
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplitTest {

    @Test
    fun `minimal location track split works`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val alignment =
            alignment(
                linearSegment(0..1, switchId = null, startJoint = null, endJoint = null),
                linearSegment(1..2, switchId = IntId(1), startJoint = 1, endJoint = 2),
            )
        val targets =
            listOf(
                targetParams(null, null, "split1", "split desc 1"),
                targetParams(IntId(1), JointNumber(1), "split2", "split desc 2"),
            )
        val resultTracks = splitLocationTrack(track, alignment, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertSegmentsMatch(alignment.segments.subList(0, 1), resultTracks[0].alignment)
        assertSegmentsMatch(alignment.segments.subList(1, 2), resultTracks[1].alignment)
    }

    @Test
    fun `location track split works when overriding existing duplicate`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val alignment =
            alignment(
                linearSegment(0..1, switchId = null, startJoint = null, endJoint = null),
                linearSegment(1..2, switchId = IntId(1), startJoint = 1, endJoint = 2),
            )
        val dupTrack = locationTrack(trackNumberId = IntId(123), draft = false)
        // over-large duplicate, but the geometry should be overridden anyhow, so just make it
        // different
        val dupAlignment = alignment(linearSegment(-1..5))
        val targets =
            listOf(
                targetParams(null, null, name = "split1", duplicate = dupTrack to dupAlignment),
                targetParams(IntId(1), JointNumber(1), "split2"),
            )
        val resultTracks = splitLocationTrack(track, alignment, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertEquals(dupTrack.id, resultTracks[0].locationTrack.id)
        assertSegmentsMatch(alignment.segments.subList(0, 1), resultTracks[0].alignment)
        assertSegmentsMatch(alignment.segments.subList(1, 2), resultTracks[1].alignment)
    }

    @Test
    fun `complex location track split works`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val alignment =
            alignment(
                linearSegment(0..2, switchId = null, startJoint = null, endJoint = null),
                linearSegment(2..5, switchId = IntId(1), startJoint = 5, endJoint = 4),
                // Split point 1: id=1 & joint=4
                linearSegment(5..6, switchId = IntId(1), startJoint = 4, endJoint = 3),
                linearSegment(6..8, switchId = IntId(1), startJoint = 3, endJoint = 2),
                linearSegment(8..9, switchId = IntId(1), startJoint = 2, endJoint = 1),
                // Split point 2: id=2 & joint = 3
                linearSegment(9..11, switchId = IntId(2), startJoint = 3, endJoint = 2),
                linearSegment(11..12, switchId = IntId(3), startJoint = 5, endJoint = 2),
                // Split point 3: id=3 & joint = 2 -- but it was mentioned only at the end of the
                // previous
                linearSegment(12..13, switchId = null, startJoint = null, endJoint = null),
            )
        val targets =
            listOf(
                targetParams(null, null, "split1", "split desc 1", NONE),
                targetParams(IntId(1), JointNumber(4), "split2", "split desc 2", SWITCH_TO_BUFFER),
                targetParams(IntId(2), JointNumber(3), "split3", "split desc 3", SWITCH_TO_OWNERSHIP_BOUNDARY),
                targetParams(IntId(3), JointNumber(2), "split4", "split desc 4", SWITCH_TO_SWITCH),
            )
        val resultTracks = splitLocationTrack(track, alignment, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertSegmentsMatch(alignment.segments.subList(0, 2), resultTracks[0].alignment)
        assertSegmentsMatch(alignment.segments.subList(2, 5), resultTracks[1].alignment)
        assertSegmentsMatch(alignment.segments.subList(5, 7), resultTracks[2].alignment)
        assertSegmentsMatch(alignment.segments.subList(7, 8), resultTracks[3].alignment)
    }
}

fun linearSegment(
    points: IntRange,
    switchId: IntId<TrackLayoutSwitch>? = null,
    startJoint: Int? = null,
    endJoint: Int? = null,
): LayoutSegment =
    segment(
        points = points.map { value -> Point(value.toDouble(), 0.0) }.toTypedArray(),
        switchId = switchId,
        startJointNumber = startJoint?.let(::JointNumber),
        endJointNumber = endJoint?.let(::JointNumber),
    )

private fun assertSegmentsMatch(expectedSegments: List<LayoutSegment>, result: LayoutAlignment) {
    assertEquals(expectedSegments.size, result.segments.size)
    expectedSegments.forEachIndexed { index, expected ->
        val actual = result.segments[index]
        assertEquals(expected.segmentPoints, actual.segmentPoints)
        assertEquals(expected.switchId, actual.switchId)
        assertEquals(expected.startJointNumber, actual.startJointNumber)
        assertEquals(expected.endJointNumber, actual.endJointNumber)
    }
}

private fun assertSplitResultFields(track: LocationTrack, request: SplitRequestTarget, result: SplitTargetResult) {
    assertEquals(track.trackNumberId, result.locationTrack.trackNumberId)
    assertEquals(track.type, result.locationTrack.type)
    assertEquals(track.state, result.locationTrack.state)
    assertEquals(track.sourceId, result.locationTrack.sourceId)
    assertEquals(request.name, result.locationTrack.name)
    assertEquals(request.descriptionBase, result.locationTrack.descriptionBase)
    assertEquals(request.descriptionSuffix, result.locationTrack.descriptionSuffix)
    assertEquals(null, result.locationTrack.duplicateOf)
    assertEquals(
        boundingBoxCombining(result.alignment.segments.mapNotNull { s -> s.boundingBox }),
        result.locationTrack.boundingBox,
    )
    assertEquals(result.alignment.segments.sumOf { s -> s.length }, result.locationTrack.length)
    assertEquals(result.alignment.segments.size, result.locationTrack.segmentCount)
}
