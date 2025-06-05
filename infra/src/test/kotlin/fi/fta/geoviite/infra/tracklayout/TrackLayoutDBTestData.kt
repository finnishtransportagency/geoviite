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

fun moveKmPostLocation(kmPost: LayoutKmPost, layoutLocation: Point, kmPostService: LayoutKmPostService) {
    val gkPoint = transformFromLayoutToGKCoordinate(layoutLocation)
    kmPostService.saveDraft(
        kmPost.layoutContext.branch,
        kmPost.copy(gkLocation = kmPost.gkLocation?.copy(location = gkPoint)),
    )
}

fun moveLocationTrackGeometryPointsAndUpdate(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    moveFunc: (point: AlignmentPoint) -> Point,
    locationTrackService: LocationTrackService,
) = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, moveLocationTrackPoints(geometry, moveFunc))

fun addTopologyEndSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
    locationTrackService: LocationTrackService,
) =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack,
        geometry.withNodeReplacements(mapOf(replaceEndWithTopoSwitch(geometry, switchId, jointNumber, jointRole))),
    )

private fun replaceEndWithTopoSwitch(
    geometry: LocationTrackGeometry,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
): Pair<NodeHash, LayoutNode> =
    geometry.edges.last().endNode.node.contentHash to LayoutNode.of(SwitchLink(switchId, jointRole, jointNumber))

fun removeTopologySwitchesFromLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    locationTrackService: LocationTrackService,
) =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack,
        geometry.withNodeReplacements(
            listOfNotNull(
                    replaceTopologyStartWithTrackBoundary(geometry, locationTrack),
                    replaceTopologyEndWithTrackBoundary(geometry, locationTrack),
                )
                .associate { it }
        ),
    )

private fun replaceTopologyStartWithTrackBoundary(
    geometry: LocationTrackGeometry,
    locationTrack: LocationTrack,
): Pair<NodeHash, LayoutNode>? =
    geometry.edges.first().startNode.let { start ->
        if (start.switchOut == null) null
        else {
            start.node.contentHash to LayoutNode.of(TrackBoundary(locationTrack.id as IntId, TrackBoundaryType.START))
        }
    }

private fun replaceTopologyEndWithTrackBoundary(
    geometry: LocationTrackGeometry,
    locationTrack: LocationTrack,
): Pair<NodeHash, LayoutNode>? =
    geometry.edges.last().endNode.let { end ->
        if (end.switchOut == null) null
        else {
            end.node.contentHash to LayoutNode.of(TrackBoundary(locationTrack.id as IntId, TrackBoundaryType.END))
        }
    }

fun addTopologyStartSwitchIntoLocationTrackAndUpdate(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
    locationTrackService: LocationTrackService,
): LayoutRowVersion<LocationTrack> =
    locationTrackService.saveDraft(
        LayoutBranch.main,
        locationTrack,
        geometry.withNodeReplacements(mapOf(replaceStartWithTopoSwitch(geometry, switchId, jointNumber, jointRole))),
    )

private fun replaceStartWithTopoSwitch(
    geometry: LocationTrackGeometry,
    switchId: IntId<LayoutSwitch>,
    jointNumber: JointNumber,
    jointRole: SwitchJointRole,
): Pair<NodeHash, LayoutNode> =
    geometry.edges.first().startNode.node.contentHash to LayoutNode.of(SwitchLink(switchId, jointRole, jointNumber))

fun moveReferenceLineGeometryPointsAndUpdate(
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment,
    moveFunc: (point: IPoint3DM) -> Point?,
    referenceLineService: ReferenceLineService,
): LayoutRowVersion<ReferenceLine> =
    referenceLineService.saveDraft(LayoutBranch.main, referenceLine, moveAlignmentPoints(alignment, moveFunc))

fun moveLocationTrackPoints(
    geometry: LocationTrackGeometry,
    moveFunc: (point: AlignmentPoint) -> Point?,
): LocationTrackGeometry {
    return TmpLocationTrackGeometry.of(
        geometry.edgesWithM.map { (edge, edgeM) ->
            val newSegments =
                edge.segmentsWithM.map { (segment, segmentM) ->
                    val newPoints =
                        toSegmentPoints(
                            to3DMPoints(
                                segment.segmentPoints.mapNotNull { point ->
                                    moveFunc(point.toAlignmentPoint(edgeM.min + segmentM.min))
                                }
                            )
                        )
                    segment.withPoints(points = newPoints, newSourceStart = null)
                }
            edge.withSegments(newSegments)
        },
        geometry.trackId,
    )
}

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
            assertEquals(0.0, it.segmentMValues.first().min)
            assertEquals(it.segments.sumOf { s -> s.length }, it.segmentMValues.last().max)
            assertEquals(it.segments.sumOf { s -> s.length }, it.length)
        }
}

fun moveSwitchPoints(switch: LayoutSwitch, moveFunc: (point: IPoint) -> IPoint, switchService: LayoutSwitchService) =
    switchService.saveDraft(LayoutBranch.main, moveSwitchPoints(switch, moveFunc))

fun moveSwitchPoints(switch: LayoutSwitch, moveFunc: (point: IPoint) -> IPoint) =
    switch.copy(joints = switch.joints.map { joint -> joint.copy(location = Point(moveFunc(joint.location))) })
