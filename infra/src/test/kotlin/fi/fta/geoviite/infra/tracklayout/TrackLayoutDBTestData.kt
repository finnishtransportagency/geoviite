package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IPoint3DM
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import org.junit.jupiter.api.Assertions.assertEquals

fun moveKmPostLocation(kmPost: TrackLayoutKmPost, layoutLocation: Point, kmPostService: LayoutKmPostService) {
    val gkPoint = transformFromLayoutToGKCoordinate(layoutLocation)
    kmPostService.saveDraft(LayoutBranch.main, kmPost.copy(gkLocation = kmPost.gkLocation?.copy(location = gkPoint)))
}

fun moveLocationTrackGeometryPointsAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    moveFunc: (point: AlignmentPoint) -> Point,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, moveAlignmentPoints(alignment, moveFunc))

fun addTopologyEndSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService,
) =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack.copy(
            topologyEndSwitch = TopologyLocationTrackSwitch(switchId = switchId, jointNumber = jointNumber)
        ),
        alignment,
    )

fun removeTopologySwitchesFromLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    locationTrackService: LocationTrackService,
) =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack.copy(topologyStartSwitch = null, topologyEndSwitch = null),
        alignment,
    )

fun addTopologyStartSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService,
): LayoutRowVersion<LocationTrack> =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack.copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(switchId = switchId, jointNumber = jointNumber)
        ),
        alignment,
    )

fun moveReferenceLineGeometryPointsAndUpdate(
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint3DM) -> Point?,
    referenceLineService: ReferenceLineService,
): LayoutRowVersion<ReferenceLine> =
    referenceLineService.saveDraft(LayoutBranch.main, referenceLine, moveAlignmentPoints(alignment, moveFunc))

fun moveAlignmentPoints(alignment: LayoutAlignment, moveFunc: (point: AlignmentPoint) -> Point?): LayoutAlignment {
    return alignment
        .copy(
            segments =
                alignment.segmentsWithM.map { (segment, m) ->
                    var prevPoint: IPoint3DM? = null
                    val newPoints =
                        segment.segmentPoints.mapNotNull { point ->
                            moveFunc(point.toAlignmentPoint(m.min))?.let { newPoint ->
                                val segmentM = prevPoint?.let { p -> p.m + lineLength(p, newPoint) } ?: 0.0
                                point.copy(x = newPoint.x, y = newPoint.y, m = segmentM).also { p -> prevPoint = p }
                            }
                        }
                    segment.withPoints(points = newPoints, newSourceStart = null).also {
                        assertEquals(0.0, it.segmentPoints.first().m)
                    }
                }
        )
        .also {
            assertEquals(0.0, it.segmentMs.first().min)
            assertEquals(it.segments.sumOf { s -> s.length }, it.segmentMs.last().max)
            assertEquals(it.segments.sumOf { s -> s.length }, it.length)
        }
}

fun moveSwitchPoints(
    switch: TrackLayoutSwitch,
    moveFunc: (point: IPoint) -> IPoint,
    switchService: LayoutSwitchService,
) = switchService.saveDraft(LayoutBranch.main, moveSwitchPoints(switch, moveFunc))

fun moveSwitchPoints(switch: TrackLayoutSwitch, moveFunc: (point: IPoint) -> IPoint) =
    switch.copy(joints = switch.joints.map { joint -> joint.copy(location = Point(moveFunc(joint.location))) })
