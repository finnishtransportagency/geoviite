package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import java.time.Instant

fun moveLocationTrackGeometryPointsAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint,
    locationTrackService: LocationTrackService
): Instant {
    val version = locationTrackService.saveDraft(
        locationTrack,
        moveAlignmentPoints(alignment, moveFunc)
    )
    locationTrackService.publish(PublicationVersion(locationTrack.id as IntId, version))
    return locationTrackService.getChangeTime()
}

fun addTopologyEndSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService
): Instant {
    val version = locationTrackService.saveDraft(
        locationTrack.copy(
            topologyEndSwitch = TopologyLocationTrackSwitch(
                switchId = switchId,
                jointNumber = jointNumber
            )
        ),
        alignment
    )
    locationTrackService.publish(PublicationVersion(locationTrack.id as IntId, version))
    return locationTrackService.getChangeTime()
}

fun removeTopologySwitchesFromLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    locationTrackService: LocationTrackService
): Instant {
    val version = locationTrackService.saveDraft(
        locationTrack.copy(
            topologyStartSwitch = null,
            topologyEndSwitch = null
        ),
        alignment
    )
    locationTrackService.publish(PublicationVersion(locationTrack.id as IntId, version))
    return locationTrackService.getChangeTime()
}


fun addTopologyStartSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    jointNumber: JointNumber,
    locationTrackService: LocationTrackService
): Instant {
    val version = locationTrackService.saveDraft(
        locationTrack.copy(
            topologyStartSwitch = TopologyLocationTrackSwitch(
                switchId = switchId,
                jointNumber = jointNumber
            )
        ),
        alignment
    )
    locationTrackService.publish(PublicationVersion(locationTrack.id as IntId, version))
    return locationTrackService.getChangeTime()
}


fun moveReferenceLineGeometryPointsAndUpdate(
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint,
    referenceLineService: ReferenceLineService
): Instant {
    val version = referenceLineService.saveDraft(
        referenceLine,
        moveAlignmentPoints(alignment, moveFunc)
    )
    referenceLineService.publish(PublicationVersion(referenceLine.id as IntId, version))
    return referenceLineService.getChangeTime()
}

fun moveAlignmentPoints(
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint, length: Double) -> IPoint
): LayoutAlignment {
    return alignment.copy(
        segments = alignment.segments.map { segment ->
            segment.copy(
                points = segment.points.map { point ->
                    val length = segment.start + point.m
                    val newPoint = moveFunc(point, length)
                    point.copy(
                        x = newPoint.x,
                        y = newPoint.y
                    )
                }
            )
        }
    )
}

fun moveSwitchPoints(
    switch: TrackLayoutSwitch,
    moveFunc: (point: IPoint) -> IPoint,
    switchService: LayoutSwitchService,
): Pair<TrackLayoutSwitch, Instant> {
    val draftVersion = switchService.saveDraft(moveSwitchPoints(switch, moveFunc))
    val publishedVersion = switchService.publish(PublicationVersion(switch.id as IntId, draftVersion))
    val updatedSwitch = switchService.getOrThrow(PublishType.OFFICIAL, publishedVersion.id)
    return updatedSwitch to switchService.getChangeTime()
}

fun moveSwitchPoints(
    switch: TrackLayoutSwitch,
    moveFunc: (point: IPoint) -> IPoint,
): TrackLayoutSwitch {
    return switch.copy(
        joints = switch.joints.map { joint ->
            joint.copy(
                location = Point(moveFunc(joint.location))
            )
        }
    )
}
