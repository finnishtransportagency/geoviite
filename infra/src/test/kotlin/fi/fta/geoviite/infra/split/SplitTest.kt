package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.NONE
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_BUFFER
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_OWNERSHIP_BOUNDARY
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix.SWITCH_TO_SWITCH
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.assertMatches
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchLinkKV
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SplitTest {

    @Test
    fun `minimal location track split works`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val switchId = IntId<LayoutSwitch>(1)
        val geometry =
            trackGeometry(
                edge(listOf(linearSegment(0..1)), endOuterSwitch = switchLinkYV(switchId, 1)),
                edge(
                    listOf(linearSegment(1..2)),
                    startInnerSwitch = switchLinkYV(switchId, 1),
                    endInnerSwitch = switchLinkYV(switchId, 2),
                ),
            )
        val targets =
            listOf(
                targetParams(null, null, "split1", "split desc 1"),
                targetParams(IntId(1), JointNumber(1), "split2", "split desc 2"),
            )
        val resultTracks = splitLocationTrack(track, geometry, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertEdgesMatch(geometry.edges.subList(0, 1), resultTracks[0].geometry)
        assertEdgesMatch(geometry.edges.subList(1, 2), resultTracks[1].geometry)
    }

    @Test
    fun `location track split works when overriding existing duplicate`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val switchId = IntId<LayoutSwitch>(1)
        val geometry =
            trackGeometry(
                edge(listOf(linearSegment(0..1)), endOuterSwitch = switchLinkYV(switchId, 1)),
                edge(
                    listOf(linearSegment(1..2)),
                    startInnerSwitch = switchLinkYV(switchId, 1),
                    endInnerSwitch = switchLinkYV(switchId, 2),
                ),
            )
        val dupTrack = locationTrack(trackNumberId = IntId(123), draft = false)
        // over-large duplicate, but the geometry should be overridden anyhow, so just make it different
        val dupGeometry = trackGeometryOfSegments(linearSegment(-1..5))
        val targets =
            listOf(
                targetParams(null, null, name = "split1", duplicate = dupTrack to dupGeometry),
                targetParams(IntId(1), JointNumber(1), "split2"),
            )
        val resultTracks = splitLocationTrack(track, geometry, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertEquals(dupTrack.id, resultTracks[0].locationTrack.id)
        assertEdgesMatch(geometry.edges.subList(0, 1), resultTracks[0].geometry)
        assertEdgesMatch(geometry.edges.subList(1, 2), resultTracks[1].geometry)
    }

    @Test
    fun `complex location track split works`() {
        val track = locationTrack(trackNumberId = IntId(123), draft = false)
        val switch1 = IntId<LayoutSwitch>(1)
        val switch2 = IntId<LayoutSwitch>(2)
        val switch3 = IntId<LayoutSwitch>(3)
        val geometry =
            trackGeometry(
                edge(listOf(linearSegment(0..2)), endOuterSwitch = switchLinkYV(switch1, 5)),
                edge(
                    listOf(linearSegment(2..5)),
                    startInnerSwitch = switchLinkKV(switch1, 5),
                    endInnerSwitch = switchLinkKV(switch1, 4),
                    endOuterSwitch = switchLinkKV(switch1, 4),
                ),
                // Split point 1: id=1 & joint=4
                edge(
                    listOf(linearSegment(5..6)),
                    startOuterSwitch = switchLinkKV(switch1, 4),
                    startInnerSwitch = switchLinkKV(switch1, 4),
                    endInnerSwitch = switchLinkKV(switch1, 3),
                    endOuterSwitch = switchLinkKV(switch1, 3),
                ),
                edge(
                    listOf(linearSegment(6..8)),
                    startOuterSwitch = switchLinkKV(switch1, 3),
                    startInnerSwitch = switchLinkKV(switch1, 3),
                    endInnerSwitch = switchLinkKV(switch1, 2),
                    endOuterSwitch = switchLinkKV(switch1, 2),
                ),
                edge(
                    listOf(linearSegment(8..9)),
                    startOuterSwitch = switchLinkKV(switch1, 2),
                    startInnerSwitch = switchLinkKV(switch1, 2),
                    endInnerSwitch = switchLinkKV(switch1, 1),
                    endOuterSwitch = switchLinkYV(switch2, 3),
                ),
                edge(
                    // Split point 2: id=2 & joint = 3
                    listOf(linearSegment(9..11)),
                    startOuterSwitch = switchLinkKV(switch1, 1),
                    startInnerSwitch = switchLinkYV(switch2, 3),
                    endInnerSwitch = switchLinkYV(switch2, 2),
                    endOuterSwitch = switchLinkYV(switch3, 5),
                ),
                edge(
                    listOf(linearSegment(11..12)),
                    startOuterSwitch = switchLinkYV(switch2, 2),
                    startInnerSwitch = switchLinkYV(switch3, 5),
                    endInnerSwitch = switchLinkYV(switch3, 2),
                ),
                // Split point 3: id=3 & joint = 2 -- but it is not marked as continuing from the node
                edge(listOf(linearSegment(12..13)), startOuterSwitch = switchLinkYV(switch3, 2)),
            )
        val targets =
            listOf(
                targetParams(null, null, "split1", "split desc 1", NONE),
                targetParams(IntId(1), JointNumber(4), "split2", "split desc 2", SWITCH_TO_BUFFER),
                targetParams(IntId(2), JointNumber(3), "split3", "split desc 3", SWITCH_TO_OWNERSHIP_BOUNDARY),
                targetParams(IntId(3), JointNumber(2), "split4", "split desc 4", SWITCH_TO_SWITCH),
            )
        val resultTracks = splitLocationTrack(track, geometry, targets)
        assertEquals(targets.size, resultTracks.size)
        resultTracks.forEachIndexed { index, result -> assertSplitResultFields(track, targets[index].request, result) }
        assertEdgesMatch(geometry.edges.subList(0, 2), resultTracks[0].geometry)
        assertEdgesMatch(geometry.edges.subList(2, 5), resultTracks[1].geometry)
        assertEdgesMatch(geometry.edges.subList(5, 7), resultTracks[2].geometry)
        assertEdgesMatch(geometry.edges.subList(7, 8), resultTracks[3].geometry)
    }
}

fun linearSegment(points: IntRange): LayoutSegment =
    segment(points = points.map { value -> Point(value.toDouble(), 0.0) }.toTypedArray())

private fun assertEdgesMatch(expectedEdges: List<LayoutEdge>, result: LocationTrackGeometry) {
    assertEquals(expectedEdges.size, result.edges.size)
    expectedEdges.forEachIndexed { index, expected ->
        val actual = result.edges[index]
        assertMatches(expected, actual)
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
        boundingBoxCombining(result.geometry.edges.map { edge -> edge.boundingBox }),
        result.locationTrack.boundingBox,
    )
    assertEquals(result.geometry.segments.sumOf { s -> s.length }, result.locationTrack.length)
    assertEquals(result.geometry.segments.size, result.locationTrack.segmentCount)
}
