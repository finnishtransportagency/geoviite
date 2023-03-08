package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point

fun moveKmPostLocation(
    kmPost: TrackLayoutKmPost,
    location: Point,
    kmPostService: LayoutKmPostService,
) = kmPostService.saveDraft(
    kmPost.copy(
        location = location
    )
)

fun moveLocationTrackGeometryPointsAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(
    locationTrack,
    moveAlignmentPoints(alignment, moveFunc)
)

fun addTopologyEndSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(
    locationTrack.copy(
        topologyEndSwitch = TopologyLocationTrackSwitch(
            switchId = switchId,
            jointNumber = jointNumber
        )
    ),
    alignment
)

fun removeTopologySwitchesFromLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(
    locationTrack.copy(
        topologyStartSwitch = null,
        topologyEndSwitch = null
    ),
    alignment
)

fun addTopologyStartSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(
    locationTrack.copy(
        topologyStartSwitch = TopologyLocationTrackSwitch(
            switchId = switchId,
            jointNumber = jointNumber
        )
    ),
    alignment
)


fun moveReferenceLineGeometryPointsAndUpdate(
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint,
    referenceLineService: ReferenceLineService,
) = referenceLineService.saveDraft(
    referenceLine,
    moveAlignmentPoints(alignment, moveFunc)
)

fun moveAlignmentPoints(
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint,
) = alignment.copy(
    segments = alignment.segments.map { segment ->
        segment.withPoints(
            points = segment.points.map { point ->
                val length = segment.start + point.m
                val newPoint = moveFunc(point, length)
                point.copy(
                    x = newPoint.x,
                    y = newPoint.y
                )
            },
        )
    }
)

fun moveSwitchPoints(
    switch: TrackLayoutSwitch,
    moveFunc: (point: IPoint) -> IPoint,
    switchService: LayoutSwitchService,
) = switchService.saveDraft(moveSwitchPoints(switch, moveFunc))

fun moveSwitchPoints(
    switch: TrackLayoutSwitch,
    moveFunc: (point: IPoint) -> IPoint,
) = switch.copy(
    joints = switch.joints.map { joint ->
        joint.copy(
            location = Point(moveFunc(joint.location))
        )
    }
)
