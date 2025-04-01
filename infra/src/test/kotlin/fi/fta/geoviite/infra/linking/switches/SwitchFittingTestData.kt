package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.asSwitchStructure
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.NodePortType
import fi.fta.geoviite.infra.tracklayout.TmpEdgeNode
import fi.fta.geoviite.infra.tracklayout.TmpLayoutEdge
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TmpTrackBoundaryNode
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.splitSegments

fun asJointNumbers(vararg joints: Int): List<JointNumber> {
    return joints.map { joint -> JointNumber(joint) }
}

fun createTrack(switchStructure: SwitchStructureData, jointNumbers: List<JointNumber>): TrackForSwitchFitting {
    val switchAlignment =
        requireNotNull(
            switchStructure.alignments.firstOrNull { switchAlignment ->
                switchAlignment.jointNumbers.containsAll(jointNumbers) &&
                    jointNumbers.containsAll(switchAlignment.jointNumbers)
            }
        ) {
            "Switch alignment does not exists by joints $jointNumbers"
        }
    val segmentEndPoints =
        switchAlignment.elements.map { element -> element.start to element.end }.map { (p1, p2) -> (p1) to (p2) }
    val trackName = jointNumbers.map { it.intValue }.joinToString("-")
    val (locationTrack, geometry) = createTrack(segmentEndPoints, trackName)
    return TrackForSwitchFitting(jointNumbers, locationTrack, geometry)
}

fun createTrack(
    segmentEndPoints: List<Pair<IPoint, IPoint>>,
    trackName: String,
): Pair<LocationTrack, LocationTrackGeometry> {
    val locationTrackId = IntId<LocationTrack>(trackName.hashCode())
    val startNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.START)
    val endNode = TmpTrackBoundaryNode(locationTrackId, TrackBoundaryType.END)
    val (_, segments) =
        segmentEndPoints.fold(0.0 to listOf<LayoutSegment>()) { (startM, segments), (start, end) ->
            val segment = segment(from = start, to = end, startM = startM)
            val newSegments = segments + segment
            val newStartM = startM + segment.length
            newStartM to newSegments
        }
    val startEdgeNode = TmpEdgeNode(NodePortType.A, startNode)
    val endEdgeNode = TmpEdgeNode(NodePortType.A, endNode)
    val edge = TmpLayoutEdge(startEdgeNode, endEdgeNode, segments)
    val geometry = TmpLocationTrackGeometry(listOf(edge))
    val trackNumberId = IntId<LayoutTrackNumber>(0)
    val locationTrack =
        locationTrack(trackNumberId = trackNumberId, geometry = geometry, id = locationTrackId, name = trackName)
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
    return TrackForSwitchFitting(listOf(), locationTrack, geometry)
}

fun expandTrackFromStart(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: Double,
): Pair<LocationTrack, LocationTrackGeometry> {
    val firstSegment = geometry.segments.first()
    val newStartPoint = firstSegment.segmentPoints.let { points -> points[0] - points[1] } * length
    val newStartSegment = segment(newStartPoint, firstSegment.segmentPoints.first())
    val newSegments = listOf(newStartSegment) + geometry.segments
    val firstEdge = geometry.edges.first()
    val newFirstEdge = TmpLayoutEdge(firstEdge.startNode, firstEdge.endNode, newSegments)
    val newEdges = listOf(newFirstEdge) + geometry.edges.drop(1)
    val newGeometry = TmpLocationTrackGeometry(newEdges)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            id = locationTrack.id as IntId,
            name = locationTrack.name.toString(),
        )
    return newLocationTrack to newGeometry
}

fun cutFromStart(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: Double,
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
                val newSegments = splitSegments(cutPosition, edge.segmentsWithM).second
                edge.withSegments(newSegments)
            }
        }
    var newGeometry = TmpLocationTrackGeometry(newEdges)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            id = locationTrack.id as IntId,
            name = locationTrack.name.toString(),
        )

    return newLocationTrack to newGeometry
}

fun expandTrackFromEnd(
    locationTrack: LocationTrack,
    geometry: LocationTrackGeometry,
    length: Double,
): Pair<LocationTrack, LocationTrackGeometry> {
    val lastSegment = geometry.segments.last()
    val newEndPoint =
        lastSegment.segmentPoints.let { points -> points[points.lastIndex] - points[points.lastIndex - 1] } * length
    val newLastSegment = segment(lastSegment.segmentPoints.last(), newEndPoint)
    val newSegments = geometry.segments + newLastSegment
    val lastEdge = geometry.edges.last()
    val newLastEdge = TmpLayoutEdge(lastEdge.startNode, lastEdge.endNode, newSegments)
    val newEdges = geometry.edges.dropLast(1) + newLastEdge
    val newGeometry = TmpLocationTrackGeometry(newEdges)
    val newLocationTrack =
        locationTrack(
            trackNumberId = locationTrack.trackNumberId,
            geometry = newGeometry,
            id = locationTrack.id as IntId,
            name = locationTrack.name.toString(),
        )
    return newLocationTrack to newGeometry
}

fun fittedSwitch(switchStructure: SwitchStructureData, vararg matches: FittedSwitchJointMatch): FittedSwitch {
    val matchesByJointNumber = matches.groupBy({ match -> match.switchJoint.number }, { match -> match })
    val fittedJoints =
        matchesByJointNumber.map { (jointNumber, matches) ->
            FittedSwitchJoint(
                number = jointNumber,
                location = Point(0.0, 0.0),
                locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED,
                matches = matches,
            )
        }
    return FittedSwitch(asSwitchStructure(switchStructure), fittedJoints)
}

fun fittedJointMatch(locationTrackId: IntId<LocationTrack>, joint: Int, m: Double): FittedSwitchJointMatch {
    return FittedSwitchJointMatch(
        locationTrackId = locationTrackId,
        segmentIndex = 0,
        m = m,
        switchJoint = SwitchStructureJoint(JointNumber(joint), Point(0.0, 0.0)),
        matchType = SuggestedSwitchJointMatchType.START,
        0.0,
        0.0,
    )
}
