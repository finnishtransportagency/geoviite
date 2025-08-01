package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.asSwitchStructure
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.linking.slice
import fi.fta.geoviite.infra.linking.splitSegments
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.switchConnectivity
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.MainOfficialContextData
import fi.fta.geoviite.infra.tracklayout.NodePortType
import fi.fta.geoviite.infra.tracklayout.StoredAssetId
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TmpNodeConnection
import fi.fta.geoviite.infra.tracklayout.TmpTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.toEdgeM

fun asJointNumbers(vararg joints: Int): List<JointNumber> {
    return joints.map { joint -> JointNumber(joint) }
}

fun createTrack(
    switchStructure: SwitchStructureData,
    jointNumbers: List<JointNumber>,
    name: String? = null,
    skipValidation: Boolean = false,
): TrackForSwitchFitting {
    val jointSequences =
        asSwitchStructure(switchStructure).let { switchStructure ->
            switchConnectivity(switchStructure).alignments.map { it.joints }
        }
    if (!skipValidation) {
        require(
            jointSequences.any { jointSequence ->
                jointSequence == jointNumbers || jointSequence.reversed() == jointNumbers
            }
        ) {
            "Switch alignment does not exists by joints $jointNumbers"
        }
    }
    val segmentEndPoints =
        jointNumbers.map { jointNumber -> switchStructure.getJointLocation(jointNumber) }.zipWithNext()
    val trackName = name ?: jointNumbers.map { it.intValue }.joinToString("-")
    val (locationTrack, geometry) = createTrack(segmentEndPoints, trackName)
    return TrackForSwitchFitting(jointNumbers, locationTrack, geometry)
}

fun locationTrackIdByName(name: String): IntId<LocationTrack> {
    return IntId(Math.abs(name.hashCode()))
}

fun createTrack(
    segmentEndPoints: List<Pair<IPoint, IPoint>>,
    trackName: String,
): Pair<LocationTrack, LocationTrackGeometry> {
    val locationTrackId = locationTrackIdByName(trackName)
    val startNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.START)
    val endNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.END)
    val (_, segments) =
        segmentEndPoints.fold(0.0 to listOf<LayoutSegment>()) { (startM, segments), (start, end) ->
            val segment = segment(from = start, to = end, startM = startM)
            val newSegments = segments + segment
            val newStartM = startM + segment.length
            newStartM to newSegments
        }
    val startNodeConnection = TmpNodeConnection(NodePortType.A, startNode)
    val endNodeConnection = TmpNodeConnection(NodePortType.A, endNode)
    val edge = TmpLayoutEdge(startNodeConnection, endNodeConnection, segments)
    val geometry = TmpLocationTrackGeometry.of(listOf(edge), null)
    val trackNumberId = IntId<LayoutTrackNumber>(0)
    // must fake a stored asset ID because the switch linking code implements its own version-based locking
    val contextData =
        MainOfficialContextData(
            StoredAssetId(LayoutRowVersion(LayoutRowId(locationTrackId, LayoutBranch.main.official), 1))
        )

    val locationTrack =
        locationTrack(trackNumberId = trackNumberId, geometry = geometry, contextData = contextData, name = trackName)
    return locationTrack to geometry
}

fun createContinuingTrack(track: TrackForSwitchFitting, length: Double, trackName: String): TrackForSwitchFitting {
    val points = track.geometry.allSegmentPoints.toList()
    val directionVector = (points[points.lastIndex] - points[points.lastIndex - 1]).normalized()
    val start = points.last()
    val end = points.last() + directionVector * length
    val (locationTrack, geometry) = createTrack(listOf(start to end), trackName)
    return TrackForSwitchFitting(listOf(), locationTrack, geometry)
}

fun createPrependingTrack(track: TrackForSwitchFitting, length: Double, trackName: String): TrackForSwitchFitting {
    val points = track.geometry.allSegmentPoints.toList()
    val directionVector = (points[1] - points[0]).normalized()
    val start = points.first() - directionVector * length
    val end = points.first()
    val (locationTrack, geometry) = createTrack(listOf(start to end), trackName)
    return TrackForSwitchFitting(listOf(), locationTrack, geometry).setTrackNumber(track.locationTrack.trackNumberId)
}

fun expandTrackFromStart(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: LineM<LocationTrackM>,
): Pair<LocationTrack, LocationTrackGeometry> {
    val firstSegment = geometry.segments.first()
    val newStartPoint = firstSegment.segmentPoints.let { points -> points[0] - points[1] } * length.distance
    val newStartSegment = segment(newStartPoint, firstSegment.segmentPoints.first())
    val newSegments = listOf(newStartSegment) + geometry.segments
    val firstEdge = geometry.edges.first()
    val newFirstEdge = TmpLayoutEdge(firstEdge.startNode, firstEdge.endNode, newSegments)
    val newEdges = listOf(newFirstEdge) + geometry.edges.drop(1)
    val newGeometry = TmpLocationTrackGeometry.of(newEdges, locationTrack.id as? IntId)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            contextData = locationTrack.contextData,
            name = locationTrack.name.toString(),
        )
    return newLocationTrack to newGeometry
}

fun cutFromStart(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: LineM<LocationTrackM>,
): Pair<LocationTrack, LocationTrackGeometry> {
    val cutPosition = length
    val newEdges =
        geometry.edgesWithM.mapNotNull { (edge, range) ->
            if (cutPosition <= range.min)
            // is fully included
            edge
            else if (cutPosition >= range.max)
            // is fully excluded
            null
            else {
                // is partly included
                // TODO GVT-3172 This is an actual M-type confusion
                val newSegments = slice(edge.segmentsWithM, Range(cutPosition.toEdgeM(LineM(0.0)), edge.length))
                edge.withSegments(newSegments)
            }
        }
    var newGeometry = TmpLocationTrackGeometry.of(newEdges, locationTrack.id as? IntId)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            contextData = locationTrack.contextData,
            name = locationTrack.name.toString(),
        )

    return newLocationTrack to newGeometry
}

fun cutFromEnd(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: LineM<LocationTrackM>,
): Pair<LocationTrack, LocationTrackGeometry> {
    val cutPosition = geometry.length - length
    val newEdges =
        geometry.edgesWithM.mapNotNull { (edge, range) ->
            if (cutPosition <= range.min)
            // is fully included
            edge
            else if (cutPosition >= range.max)
            // is fully excluded
            null
            else {
                // is partly included
                val newSegments = splitSegments(edge.segmentsWithM, cutPosition.castToDifferentM()).first
                edge.withSegments(newSegments)
            }
        }
    var newGeometry = TmpLocationTrackGeometry.of(newEdges, locationTrack.id as? IntId)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            contextData = locationTrack.contextData,
            name = locationTrack.name.toString(),
        )

    return newLocationTrack to newGeometry
}

fun expandTrackFromEnd(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: LineM<LocationTrackM>,
): Pair<LocationTrack, LocationTrackGeometry> {
    val lastSegment = geometry.segments.last()
    val newEndPoint =
        lastSegment.segmentPoints.let { points ->
            val direction = (points[points.lastIndex] - points[points.lastIndex - 1]).normalized()
            points.last() + direction * length.distance
        }
    val newLastSegment = segment(lastSegment.segmentPoints.last(), newEndPoint)
    val newSegments = geometry.segments + newLastSegment
    val lastEdge = geometry.edges.last()
    val newLastEdge = TmpLayoutEdge(lastEdge.startNode, lastEdge.endNode, newSegments)
    val newEdges = geometry.edges.dropLast(1) + newLastEdge
    val newGeometry = TmpLocationTrackGeometry.of(newEdges, locationTrack.id as? IntId)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            contextData = locationTrack.contextData,
            name = locationTrack.name.toString(),
        )
    return newLocationTrack to newGeometry
}

fun moveTrackForward(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    distance: Double,
): Pair<LocationTrack, LocationTrackGeometry> {
    val lastSegment = geometry.segments.last()
    val translation =
        lastSegment.segmentPoints.let { points ->
            val direction = (points[points.lastIndex] - points[points.lastIndex - 1]).normalized()
            direction * distance
        }
    val newEdges =
        geometry.edges.map { edge ->
            val newSegments =
                geometry.segments.map { segment ->
                    val newPoints =
                        segment.segmentPoints.map { point ->
                            point.copy(x = point.x + translation.x, y = point.y + translation.y)
                        }
                    segment.withPoints(newPoints, segment.sourceStartM)
                }
            edge.withSegments(newSegments)
        }
    val newGeometry = TmpLocationTrackGeometry.of(newEdges, locationTrack.id as? IntId)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            contextData = locationTrack.contextData,
            name = locationTrack.name.toString(),
        )
    return newLocationTrack to newGeometry
}

fun fittedSwitch(switchStructure: SwitchStructureData, vararg matches: FittedSwitchJointMatch): FittedSwitch {
    return fittedSwitch(switchStructure, Point.zero(), *matches)
}

fun fittedSwitch(
    switchStructure: SwitchStructureData,
    jointTranslation: IPoint,
    vararg matches: FittedSwitchJointMatch,
): FittedSwitch {
    val matchesByJointNumber = matches.groupBy({ match -> match.switchJoint.number }, { match -> match })
    val fittedJoints =
        matchesByJointNumber.map { (jointNumber, matches) ->
            FittedSwitchJoint(
                number = jointNumber,
                location = switchStructure.getJointLocation(jointNumber) + jointTranslation,
                locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED,
                matches = matches,
            )
        }
    return FittedSwitch(asSwitchStructure(switchStructure), fittedJoints)
}

fun fittedJointMatch(track: TrackForSwitchFitting, joint: Int, m: LineM<LocationTrackM>): FittedSwitchJointMatch {
    val coordinates = Point(track.geometry.getPointAtM(m)!!)
    return FittedSwitchJointMatch(
        locationTrackId = track.locationTrackId,
        segmentIndex = 0,
        mOnTrack = m,
        switchJoint = SwitchStructureJoint(JointNumber(joint), coordinates),
        matchType = SuggestedSwitchJointMatchType.START,
        0.0,
        0.0,
        RelativeDirection.Along,
        location = coordinates,
    )
}
