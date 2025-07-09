package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlanHeader
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.closestPointOnLine
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.dot
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchPositionTransformation
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.calculateSwitchLocationDelta
import fi.fta.geoviite.infra.switchLibrary.transformSwitchPoint
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TrackSwitchLinkType
import fi.fta.geoviite.infra.tracklayout.toSegmentM
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

private const val TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT = 0.5
const val TOLERANCE_JOINT_LOCATION_NEW_POINT = 0.01
const val CROP_SLICE_SNAPPING_TOLERANCE = 0.0001

/**
 * Tools for finding a fit for a switch: The positioning of the switch's joints, based on the geometry of the tracks
 * around it.
 */
@GeoviiteService
class SwitchFittingService
@Autowired
constructor(
    private val locationTrackService: LocationTrackService,
    private val switchLibraryService: SwitchLibraryService,
    private val geometryDao: GeometryDao,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    @Transactional(readOnly = true)
    fun fitGeometrySwitch(branch: LayoutBranch, switchId: IntId<GeometrySwitch>): GeometrySwitchFittingResult =
        try {
            val geometrySwitch = getGeometrySwitchForFitting(switchId)
            val planHeader = geometryDao.getPlanHeader(geometryDao.getSwitchPlanId(switchId))
            val switchStructure = getGeometrySwitchStructure(geometrySwitch)
            GeometrySwitchFittingSuccess(
                fitGeometrySwitch(
                    switchStructure,
                    getTracksLinkedThroughGeometry(branch, switchId),
                    branch,
                    calculateLayoutSwitchJoints(
                        geometrySwitch,
                        switchStructure,
                        getTransformationForGeometryPlan(planHeader),
                    ),
                    planHeader.measurementMethod?.let(::mapMeasurementMethodToLocationAccuracy)
                        ?: LocationAccuracy.DIGITIZED_AERIAL_IMAGE,
                )
            )
        } catch (e: GeometrySwitchFittingException) {
            GeometrySwitchFittingFailure(e.failure)
        }

    private fun fitGeometrySwitch(
        switchStructure: SwitchStructure,
        tracksLinkedThroughGeometry: List<LayoutRowVersion<LocationTrack>>,
        branch: LayoutBranch,
        jointsInLayoutSpace: List<SwitchStructureJoint>,
        locationAccuracy: LocationAccuracy?,
    ): FittedSwitch {
        val tracks =
            listOf(
                    tracksLinkedThroughGeometry.map(locationTrackService::getWithGeometry),
                    locationTrackService.listNearWithGeometries(
                        branch.draft,
                        boundingBoxAroundPoints(jointsInLayoutSpace.map { it.location }) * 1.5,
                    ),
                )
                .flatten()
                .distinctBy { it.first.id }
                .map { (lt, geometry) -> lt to cropNothing(geometry) }

        return fitSwitch(jointsInLayoutSpace, switchStructure, tracks, locationAccuracy)
    }

    private fun getGeometrySwitchForFitting(id: IntId<GeometrySwitch>): GeometrySwitch {
        val geometrySwitch = geometryDao.getSwitch(id)
        if (geometrySwitch.joints.count() < 2) {
            throw GeometrySwitchFittingException(GeometrySwitchSuggestionFailureReason.LESS_THAN_TWO_JOINTS)
        }
        return geometrySwitch
    }

    private fun getTracksLinkedThroughGeometry(
        branch: LayoutBranch,
        switchId: IntId<GeometrySwitch>,
    ): List<LayoutRowVersion<LocationTrack>> =
        geometryDao
            .getLocationTracksLinkedThroughGeometryElementToSwitch(branch, switchId)
            // we might be able to link a geometry switch to whatever tracks are there at its
            // location, but it's always been a requirement that at least some track geometry should
            // be linked as well
            .takeIf { tracks -> tracks.isNotEmpty() }
            ?: throw GeometrySwitchFittingException(GeometrySwitchSuggestionFailureReason.RELATED_TRACKS_NOT_LINKED)

    private fun getGeometrySwitchStructure(geometrySwitch: GeometrySwitch): SwitchStructure =
        geometrySwitch.switchStructureId?.let { switchStructureId ->
            switchLibraryService.getSwitchStructure(switchStructureId)
        }
            ?: throw GeometrySwitchFittingException(
                GeometrySwitchSuggestionFailureReason.NO_SWITCH_STRUCTURE_ID_ON_SWITCH
            )

    private fun getTransformationForGeometryPlan(planHeader: GeometryPlanHeader): Transformation =
        planHeader.units.coordinateSystemSrid?.let { srid ->
            coordinateTransformationService.getTransformation(srid, LAYOUT_SRID)
        } ?: throw GeometrySwitchFittingException(GeometrySwitchSuggestionFailureReason.NO_SRID_ON_PLAN)
}

private fun calculateLayoutSwitchJoints(
    geomSwitch: GeometrySwitch,
    switchStructure: SwitchStructure,
    toLayoutCoordinate: Transformation,
): List<SwitchStructureJoint> {
    val layoutJointPoints =
        geomSwitch.joints.map { geomJoint ->
            SwitchStructureJoint(number = geomJoint.number, location = toLayoutCoordinate.transform(geomJoint.location))
        }
    val switchLocationDelta = calculateSwitchLocationDelta(layoutJointPoints, switchStructure)
    return if (switchLocationDelta != null) {
        switchStructure.alignmentJoints.map { joint ->
            SwitchStructureJoint(
                number = joint.number,
                location = transformSwitchPoint(switchLocationDelta, joint.location),
            )
        }
    } else {
        throw GeometrySwitchFittingException(GeometrySwitchSuggestionFailureReason.INVALID_JOINTS)
    }
}

private data class PossibleSegment(
    val segment: ISegment,
    val segmentIndex: Int,
    val mRangeOnTrack: Range<LineM<LocationTrackM>>,
    val closestPointMOnTrack: LineM<LocationTrackM>,
    val closestSegmentPointIndex: Int,
    val jointDistanceToSegment: Double,
)

private fun findSuggestedSwitchJointMatches(
    joint: SwitchStructureJoint,
    locationTrack: LocationTrack,
    geometry: CroppedTrackGeometry,
    tolerance: Double,
    switchDirectionVector: IPoint,
): List<FittedSwitchJointMatch> {
    val jointLocation = joint.location
    val possibleSegments = findPossiblyMatchableSegments(geometry, jointLocation, tolerance)
    if (possibleSegments.isEmpty()) {
        return listOf()
    }
    val jointDistanceToAlignment = possibleSegments.minOf { segment -> segment.jointDistanceToSegment }

    return possibleSegments.flatMap { possible ->
        val segmentIndex = possible.segmentIndex
        val segment = possible.segment
        val segmentMRangeOnTrack = possible.mRangeOnTrack
        val closestSegmentPointIndex = possible.closestSegmentPointIndex
        val segmentDirectionVector = (segment.segmentEnd - segment.segmentStart).normalized()
        val relativeDirection =
            if (dot(switchDirectionVector, segmentDirectionVector) > 0) RelativeDirection.Along
            else RelativeDirection.Against

        val segmentLinesWithinTolerance =
            takeSegmentLineMatchesAroundPoint(jointLocation, tolerance, segment, closestSegmentPointIndex)

        val startMatches = segment.segmentStart.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)
        val endMatches = segment.segmentEnd.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)

        listOfNotNull(
            // Check if segment's start point is within tolerance
            if (startMatches)
                FittedSwitchJointMatch(
                    locationTrackId = locationTrack.id as IntId,
                    mOnTrack = segmentMRangeOnTrack.min,
                    matchType = SuggestedSwitchJointMatchType.START,
                    switchJoint = joint,
                    distance = lineLength(segment.segmentStart, jointLocation),
                    distanceToAlignment = jointDistanceToAlignment,
                    segmentIndex = segmentIndex,
                    direction = relativeDirection,
                    location = segment.segmentStart.toPoint(),
                )
            else null,

            // Check if segment's end point is within tolerance
            if (endMatches)
                FittedSwitchJointMatch(
                    locationTrackId = locationTrack.id as IntId,
                    mOnTrack = segmentMRangeOnTrack.max,
                    matchType = SuggestedSwitchJointMatchType.END,
                    switchJoint = joint,
                    distance = lineLength(segment.segmentEnd, jointLocation),
                    distanceToAlignment = jointDistanceToAlignment,
                    segmentIndex = segmentIndex,
                    direction = relativeDirection,
                    location = segment.segmentEnd.toPoint(),
                )
            else null,
        ) +
            segmentLinesWithinTolerance
                .map { (closestAlignmentPoint, jointDistanceToSegment) ->
                    val mOnTrack = segment.getClosestPointM(segmentMRangeOnTrack.min, closestAlignmentPoint).first
                    FittedSwitchJointMatch(
                        locationTrackId = locationTrack.id as IntId,
                        mOnTrack = mOnTrack,
                        matchType = SuggestedSwitchJointMatchType.LINE,
                        switchJoint = joint,
                        distance = jointDistanceToSegment,
                        distanceToAlignment = jointDistanceToAlignment,
                        segmentIndex = segmentIndex,
                        direction = relativeDirection,
                        location = segment.seekPointAtM(segmentMRangeOnTrack.min, mOnTrack).point.toPoint(),
                    )
                }
                .toList()
    }
}

private fun findPossiblyMatchableSegments(
    geometry: CroppedTrackGeometry,
    jointLocation: Point,
    tolerance: Double,
): List<PossibleSegment> {
    val maxMatchableDistance = max(TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT, tolerance)
    val closestSegmentIndex = geometry.findClosestSegmentIndex(jointLocation) ?: return listOf()
    val firstPossibleSegmentIndex = (closestSegmentIndex - 1).coerceAtLeast(0)

    return geometry.segmentsWithM
        .subList(firstPossibleSegmentIndex, (closestSegmentIndex + 2).coerceAtMost(geometry.segments.size))
        .mapIndexed { index, (segment, segmentMRange) ->
            val closestPointM = segment.getClosestPointM(segmentMRange.min, jointLocation).first
            val pointSeekResult = segment.seekPointAtM(segmentMRange.min, closestPointM)
            val closestSegmentPointIndex = pointSeekResult.index
            val jointDistanceToSegment = lineLength(pointSeekResult.point, jointLocation)
            PossibleSegment(
                segment,
                index + firstPossibleSegmentIndex + geometry.cropStartSegmentIndex,
                segmentMRange,
                closestPointM,
                closestSegmentPointIndex,
                jointDistanceToSegment,
            )
        }
        .filter { segment -> segment.jointDistanceToSegment < maxMatchableDistance }
}

private fun takeSegmentLineMatchesAroundPoint(
    jointLocation: Point,
    tolerance: Double,
    segment: ISegment,
    closestSegmentPointIndex: Int,
): Sequence<Pair<Point, Double>> {
    val segmentLinesForward =
        takeSegmentLineMatches(
            segment.segmentPoints.subList(closestSegmentPointIndex, segment.segmentPoints.size),
            jointLocation,
            tolerance,
        )
    val segmentLinesBackward =
        takeSegmentLineMatches(
            segment.segmentPoints.subList(0, closestSegmentPointIndex + 1).asReversed(),
            jointLocation,
            tolerance,
        )
    return sequenceOf(segmentLinesForward, segmentLinesBackward).flatten()
}

fun takeSegmentLineMatches(
    points: List<SegmentPoint>,
    jointLocation: Point,
    tolerance: Double,
): Sequence<Pair<Point, Double>> =
    points
        .asSequence()
        .zipWithNext { previousPoint, point ->
            val closestAlignmentPoint = closestPointOnLine(previousPoint, point, jointLocation)
            val jointDistanceToSegment = lineLength(closestAlignmentPoint, jointLocation)
            closestAlignmentPoint to jointDistanceToSegment
        }
        .takeWhile { (_, d) -> d < tolerance }

private fun findSuggestedSwitchJointMatches(
    joints: List<SwitchStructureJoint>,
    locationTrackAndGeometry: Pair<LocationTrack, CroppedTrackGeometry>,
    tolerance: Double,
    switchDirectionVector: IPoint,
): List<FittedSwitchJointMatch> {
    return joints.flatMap { joint ->
        findSuggestedSwitchJointMatches(
            joint = joint,
            locationTrack = locationTrackAndGeometry.first,
            geometry = locationTrackAndGeometry.second,
            tolerance = tolerance,
            switchDirectionVector = switchDirectionVector,
        )
    }
}

fun getSwitchDirectionVector(
    switchStructure: SwitchStructure,
    jointsInLayoutSpace: List<SwitchStructureJoint>,
): IPoint {
    val longestSwitchAlignment =
        switchStructure.alignments.sortedByDescending { switchAlignment -> switchAlignment.length() }.first()
    val start = jointsInLayoutSpace.first { joint -> joint.number == longestSwitchAlignment.jointNumbers.first() }
    val end = jointsInLayoutSpace.first { joint -> joint.number == longestSwitchAlignment.jointNumbers.last() }
    return (end.location - start.location).normalized()
}

fun fitSwitch(
    jointsInLayoutSpace: List<SwitchStructureJoint>,
    switchStructure: SwitchStructure,
    tracksAndGeometries: List<Pair<LocationTrack, CroppedTrackGeometry>>,
    locationAccuracy: LocationAccuracy?,
): FittedSwitch {
    val jointMatchTolerance = 0.2 // TODO: There could be tolerance per joint point in switch structure
    val switchDirectionVector = getSwitchDirectionVector(switchStructure, jointsInLayoutSpace)

    val matchesByLocationTrack =
        tracksAndGeometries
            .associate { trackAndGeometry ->
                trackAndGeometry.first to
                    findSuggestedSwitchJointMatches(
                        joints = jointsInLayoutSpace,
                        locationTrackAndGeometry = trackAndGeometry,
                        tolerance = jointMatchTolerance,
                        switchDirectionVector = switchDirectionVector,
                    )
            }
            .filter { it.value.isNotEmpty() }

    val endJoints = getEndJoints(matchesByLocationTrack)
    val matchesByJoint = getBestMatchesByJoint(matchesByLocationTrack, endJoints)
    val jointsWithoutMatches =
        jointsInLayoutSpace
            .filterNot { matchesByJoint.containsKey(it) }
            .associateWith { emptyList<FittedSwitchJointMatch>() }

    val suggestedJoints =
        (matchesByJoint + jointsWithoutMatches).map { (joint, matches) ->
            FittedSwitchJoint(
                number = joint.number,
                location = joint.location,
                matches = matches,
                locationAccuracy = locationAccuracy,
            )
        }

    return FittedSwitch(switchStructure = switchStructure, joints = suggestedJoints)
}

private fun getBestMatchesByJoint(
    matchesByLocationTrack: Map<LocationTrack, List<FittedSwitchJointMatch>>,
    endJoints: Map<LocationTrack, Pair<SwitchStructureJoint, SwitchStructureJoint>>,
): Map<SwitchStructureJoint, List<FittedSwitchJointMatch>> =
    matchesByLocationTrack
        .flatMap { (lt, matches) ->
            matches
                .groupBy { it.switchJoint }
                .map { (joint, jointMatches) ->
                    getBestMatchesForJoint(
                        jointMatches = jointMatches,
                        isFirstJoint = endJoints[lt]?.first == joint,
                        isLastJoint = endJoints[lt]?.second == joint,
                    )
                }
                .flatMap { filteredMatches ->
                    findSegmentBoundaryMatch(filteredMatches)
                        ?: listOfNotNull(filteredMatches.minByOrNull { it.distance })
                }
        }
        .groupBy { it.switchJoint }

private fun findSegmentBoundaryMatch(filteredMatches: List<FittedSwitchJointMatch>): List<FittedSwitchJointMatch>? {
    // extremely short segments might cause any number of weird matches, so disambiguate
    val nearestEndMatch =
        filteredMatches.filter { it.matchType == SuggestedSwitchJointMatchType.END }.minByOrNull { it.distance }

    val startMatchAfterNearestEndMatch =
        if (nearestEndMatch == null) null
        else
            filteredMatches.find {
                it.matchType == SuggestedSwitchJointMatchType.START &&
                    it.segmentIndex == nearestEndMatch.segmentIndex + 1
            }
    return if (startMatchAfterNearestEndMatch == null || nearestEndMatch == null) null
    else
        listOf(
            // force same m-value on both sides, to avoid misplaced segment ends causing the
            // matches' m-value orders and segment index orders to contradict
            nearestEndMatch.copy(mOnTrack = startMatchAfterNearestEndMatch.mOnTrack),
            startMatchAfterNearestEndMatch,
        )
}

private fun getBestMatchesForJoint(
    jointMatches: List<FittedSwitchJointMatch>,
    isFirstJoint: Boolean,
    isLastJoint: Boolean,
): List<FittedSwitchJointMatch> {
    return if (isFirstJoint) {
        // First joint should never match with the last point of a segment
        jointMatches
            .filter { it.matchType == SuggestedSwitchJointMatchType.START }
            .ifEmpty { jointMatches.filterNot { it.matchType == SuggestedSwitchJointMatchType.END } }
    } else if (isLastJoint) {
        // Last joint should never match with the first point of a segment
        jointMatches
            .filter { it.matchType == SuggestedSwitchJointMatchType.END }
            .ifEmpty { jointMatches.filterNot { it.matchType == SuggestedSwitchJointMatchType.START } }
    } else {
        // Prefer end points over "normal" ones
        jointMatches
            .filter {
                it.matchType == SuggestedSwitchJointMatchType.START || it.matchType == SuggestedSwitchJointMatchType.END
            }
            .ifEmpty { jointMatches }
    }
}

private fun getEndJoints(matchesByLocationTrack: Map<LocationTrack, List<FittedSwitchJointMatch>>) =
    matchesByLocationTrack.mapValues { (_, joints) ->
        val jointsSortedByMatchLength = joints.sortedWith(compareBy(FittedSwitchJointMatch::mOnTrack))
        val min = jointsSortedByMatchLength.first().switchJoint
        val max = jointsSortedByMatchLength.last().switchJoint
        min to max
    }

private fun mapMeasurementMethodToLocationAccuracy(mm: MeasurementMethod): LocationAccuracy =
    when (mm) {
        MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY -> LocationAccuracy.DESIGNED_GEOLOCATION
        MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY -> LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY
        MeasurementMethod.TRACK_INSPECTION -> LocationAccuracy.MEASURED_GEODETICALLY
        MeasurementMethod.DIGITIZED_AERIAL_IMAGE -> LocationAccuracy.DIGITIZED_AERIAL_IMAGE
        MeasurementMethod.UNVERIFIED_DESIGNED_GEOMETRY -> LocationAccuracy.MEASURED_GEODETICALLY
    }

const val MAX_LINE_INTERSECTION_DISTANCE = 0.5
const val MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES = 1

private fun findIntersections(
    alignment1: IAlignment<LocationTrackM>,
    alignment2: IAlignment<LocationTrackM>,
): List<TrackIntersection> {
    // Ignore parallel alignments. Points of alignments are filtered so
    // that alignments are about 0 - 200 meters long, and therefore we can compare
    // angles from start to end.
    val directionDiff = alignmentStartEndDirectionDiff(alignment1, alignment2)?.let(::radsToDegrees)
    if (directionDiff == null || directionDiff < MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES) return emptyList()
    val lines2 = toChunkedLinesWithBoxes(alignment2)
    return toChunkedLinesWithBoxes(alignment1)
        .flatMap { (l1, bb1) ->
            lines2.flatMap { (l2, bb2) ->
                if (bb1.minimumDistance(bb2) <= MAX_LINE_INTERSECTION_DISTANCE) {
                    findIntersectionsBetweenLineLists(l1, l2, alignment1, alignment2)
                } else listOf()
            }
        }
        .toList()
}

private fun toChunkedLinesWithBoxes(alignment: IAlignment<LocationTrackM>) =
    alignment.allSegmentPoints
        .zipWithNext { a, b -> Line(a, b) }
        .chunked(10)
        .map { lineChunk ->
            lineChunk to boundingBoxAroundPoints(listOf(lineChunk[0].start) + lineChunk.map { it.end })
        }

private fun findIntersectionsBetweenLineLists(
    lines1: List<Line>,
    lines2: List<Line>,
    alignment1: IAlignment<LocationTrackM>,
    alignment2: IAlignment<LocationTrackM>,
) =
    lines1.flatMap { line1 ->
        lines2.mapNotNull { line2 ->
            val intersection = lineIntersection(line1.start, line1.end, line2.start, line2.end)
            if (intersection != null && intersection.linesIntersect()) {
                TrackIntersection(
                    point = intersection.point,
                    distance = 0.0,
                    alignment1 = alignment1,
                    alignment2 = alignment2,
                )
            } else {
                tryFindingNearIntersection(line1, line2, alignment1, alignment2)
            }
        }
    }

private fun tryFindingNearIntersection(
    line1: Line,
    line2: Line,
    alignment1: IAlignment<LocationTrackM>,
    alignment2: IAlignment<LocationTrackM>,
): TrackIntersection? {
    val linePointDistanceCheckPairs =
        listOf(line1 to line2.start, line1 to line2.end, line2 to line1.start, line2 to line1.end)
    val minDistanceAndPoint =
        linePointDistanceCheckPairs
            .map { (line, point) -> pointDistanceToLine(line.start, line.end, point) to point }
            .minBy { (distance, _) -> distance }
    return if (minDistanceAndPoint.first <= MAX_LINE_INTERSECTION_DISTANCE) {
        TrackIntersection(
            point = minDistanceAndPoint.second,
            distance = minDistanceAndPoint.first,
            alignment1 = alignment1,
            alignment2 = alignment2,
        )
    } else null
}

private fun alignmentStartEndDirectionDiff(
    alignment1: IAlignment<LocationTrackM>,
    alignment2: IAlignment<LocationTrackM>,
): Double? {
    val track1Direction = alignmentStartEndDirection(alignment1)
    val track2Direction = alignmentStartEndDirection(alignment2)
    return if (track1Direction != null && track2Direction != null) {
        angleDiffRads(track1Direction, track2Direction)
    } else null
}

private fun alignmentStartEndDirection(alignment: IAlignment<LocationTrackM>): Double? {
    val start = alignment.firstSegmentStart
    val end = alignment.lastSegmentEnd
    return if (start != null && end != null) directionBetweenPoints(start, end) else null
}

private fun getClosestPointAsIntersection(
    track1: IAlignment<LocationTrackM>,
    track2: IAlignment<LocationTrackM>,
    desiredLocation: IPoint,
): TrackIntersection? {
    return listOf(track1, track2)
        .mapNotNull { track -> track.getClosestPoint(desiredLocation) }
        .minByOrNull { (point, _) -> lineLength(point, desiredLocation) }
        ?.let { (closestPoint, _) ->
            TrackIntersection(alignment1 = track1, alignment2 = track2, point = closestPoint, distance = 0.0)
        }
}

private fun <T> pairsOf(things: List<T>): List<Pair<T, T>> =
    things.flatMapIndexed { index, t1 -> things.drop(index + 1).map { t2 -> t1 to t2 } }

private fun findTrackIntersectionsForGridPoints(
    trackAlignments: List<IAlignment<LocationTrackM>>,
    grid: SamplingGridPoints,
): PointAssociation<TrackIntersection> {
    val trackPairs = pairsOf(trackAlignments)
    val allActualIntersections = trackPairs.flatMap { (track1, track2) -> findIntersections(track1, track2) }

    val closestActualIntersections =
        grid.mapMulti(parallel = true) { gridPoint ->
            allActualIntersections.sortedWith(orderIntersectionsWithDesiredPoint(gridPoint)).take(2).toSet()
        }
    val closestPointsAsIntersections =
        grid.mapMulti { gridPoint ->
            trackPairs
                .mapNotNull { (track1, track2) -> getClosestPointAsIntersection(track1, track2, gridPoint) }
                .toSet()
        }

    return closestPointsAsIntersections.zipWithByPoint(closestActualIntersections, Set<TrackIntersection>::plus)
}

private fun orderIntersectionsWithDesiredPoint(
    desiredPoint: Point
): (a: TrackIntersection, b: TrackIntersection) -> Int {
    return { a, b ->
        if (a.distance < b.distance) -1
        else if (a.distance > b.distance) 1
        else {
            val aDesiredDistance = lineLength(a.point, desiredPoint)
            val bDesiredDistance = lineLength(b.point, desiredPoint)
            if (aDesiredDistance < bDesiredDistance) -1 else if (aDesiredDistance > bDesiredDistance) 1 else 0
        }
    }
}

private fun findFarthestJoint(
    switchStructure: SwitchStructure,
    joint: SwitchStructureJoint,
    switchAlignment: SwitchStructureAlignment,
): SwitchStructureJoint {
    val jointNumber =
        requireNotNull(
            switchAlignment.jointNumbers.maxByOrNull { jointNumber ->
                lineLength(joint.location, switchStructure.getJointLocation(jointNumber))
            }
        ) {
            "Cannot find farthest joint: joints=${switchAlignment.jointNumbers}"
        }
    return switchStructure.getJoint(jointNumber)
}

private data class SwitchPointSeekResult(
    val fixPoint: AlignmentPoint<LocationTrackM>,
    val pointBackwards: AlignmentPoint<LocationTrackM>?,
    val pointForwards: AlignmentPoint<LocationTrackM>?,
)

private fun findPointsOnTrack(
    from: IPoint,
    distance: Double,
    alignment: IAlignment<LocationTrackM>,
): SwitchPointSeekResult? {
    val snapDistance = 0.1
    return alignment
        .getClosestPointM(from)
        ?.let { (mValue, state) ->
            if (state == IntersectType.WITHIN) alignment.getPointAtM(mValue, snapDistance)?.let { p -> mValue to p }
            else null
        }
        ?.let { (mValue, pointOnTrack) ->
            SwitchPointSeekResult(
                fixPoint = pointOnTrack,
                pointBackwards = alignment.getPointAtM(mValue - distance, snapDistance),
                pointForwards = alignment.getPointAtM(mValue + distance, snapDistance),
            )
        }
}

private fun findTransformations(
    point: IPoint,
    alignment: IAlignment<LocationTrackM>,
    switchAlignment: SwitchStructureAlignment,
    joint: SwitchStructureJoint,
    switchStructure: SwitchStructure,
): List<SwitchPositionTransformation> {
    val farthestJoint = findFarthestJoint(switchStructure, joint, switchAlignment)
    val jointDistance = lineLength(joint.location, farthestJoint.location)
    val pointsOnTrack = findPointsOnTrack(point, jointDistance, alignment)
    return listOfNotNull(
            pointsOnTrack?.pointBackwards?.let { back -> pointsOnTrack.fixPoint to back },
            pointsOnTrack?.pointForwards?.let { forward -> pointsOnTrack.fixPoint to forward },
        )
        .mapNotNull { (from, to) ->
            val testJoints = listOf(joint.copy(location = from.toPoint()), farthestJoint.copy(location = to.toPoint()))
            calculateSwitchLocationDelta(testJoints, switchStructure)
        }
}

private fun findTransformations(
    point: IPoint,
    alignment1: IAlignment<LocationTrackM>,
    alignment2: IAlignment<LocationTrackM>,
    switchAlignment1: SwitchStructureAlignment,
    switchAlignment2: SwitchStructureAlignment,
    joint: SwitchStructureJoint,
    switchStructure: SwitchStructure,
): List<SwitchPositionTransformation> {
    return findTransformations(point, alignment1, switchAlignment1, joint, switchStructure) +
        findTransformations(point, alignment1, switchAlignment2, joint, switchStructure) +
        findTransformations(point, alignment2, switchAlignment1, joint, switchStructure) +
        findTransformations(point, alignment2, switchAlignment2, joint, switchStructure)
}

fun fitSwitch(
    transformation: SwitchPositionTransformation,
    tracks: List<Pair<LocationTrack, CroppedTrackGeometry>>,
    switchStructure: SwitchStructure,
    locationAccuracy: LocationAccuracy?,
): FittedSwitch {
    val jointsInLayoutSpace =
        switchStructure.joints.map { joint ->
            joint.copy(location = transformSwitchPoint(transformation, joint.location))
        }

    return fitSwitch(
        jointsInLayoutSpace = jointsInLayoutSpace,
        switchStructure = switchStructure,
        tracksAndGeometries = tracks,
        locationAccuracy = locationAccuracy,
    )
}

fun getSharedSwitchJoint(switchStructure: SwitchStructure): Pair<SwitchStructureJoint, List<SwitchStructureAlignment>> {
    val sortedSwitchJoints =
        switchStructure.joints.sortedWith { jointA, jointB ->
            when {
                jointA.number == switchStructure.presentationJointNumber -> -1
                jointB.number == switchStructure.presentationJointNumber -> 1
                else -> 0
            }
        }

    val (sharedSwitchJoint, switchAlignmentsContainingCommonJoint) =
        requireNotNull(
            sortedSwitchJoints.firstNotNullOfOrNull { joint ->
                val alignmentsContainingJoint =
                    switchStructure.alignments.filter { alignment -> alignment.jointNumbers.contains(joint.number) }
                if (alignmentsContainingJoint.size >= 2) joint to alignmentsContainingJoint else null
            }
        ) {
            "Switch structure ${switchStructure.type} does not contain shared switch joint and that is weird!"
        }

    return sharedSwitchJoint to switchAlignmentsContainingCommonJoint
}

data class SuggestedSwitchJointScore(
    val jointNumber: JointNumber,
    val jointScore: Double,
    val farthestJointExtraScore: Double,
    val maintainingAlignmentLinkScore: Double,
) {
    fun sum() = jointScore + farthestJointExtraScore + maintainingAlignmentLinkScore
}

fun getSuggestedSwitchScore(
    switchSuggestion: FittedSwitch,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchStructureJoint,
    maxFarthestJointDistance: Double,
    desiredLocation: IPoint,
    originallyLinkedTracks: Set<Pair<IntId<LocationTrack>, JointNumber>>,
): List<SuggestedSwitchJointScore> {
    val alignmentJointNumbers = switchStructure.alignmentJoints.map { joint -> joint.number }
    val suggestedAlignmentJoints =
        switchSuggestion.joints.filter { joint -> alignmentJointNumbers.contains(joint.number) }
    return suggestedAlignmentJoints.map { joint ->
        // Select best of each joint
        val jointScore =
            (joint.matches.maxOfOrNull { match ->
                // Smaller the match distance, better the score
                max(1.0 - match.distanceToAlignment, 0.0)
            } ?: 0.0)
        val farthestJointExtraScore =
            if (joint.number == farthestJoint.number && maxFarthestJointDistance > 0) {
                val distanceToFarthestJoint = lineLength(desiredLocation, joint.location)
                val maxExtraScore = 0.5
                val extraScore = (distanceToFarthestJoint / maxFarthestJointDistance) * maxExtraScore
                extraScore
            } else 0.0
        val maintainingAlignmentLinkScore =
            if (
                joint.matches.any { jointMatch ->
                    originallyLinkedTracks.contains(jointMatch.locationTrackId to joint.number)
                }
            )
                0.1
            else 0.0
        SuggestedSwitchJointScore(joint.number, jointScore, farthestJointExtraScore, maintainingAlignmentLinkScore)
    }
}

private fun selectBestSuggestedSwitch(
    switchSuggestions: Set<FittedSwitch>,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchStructureJoint,
    desiredLocation: IPoint,
    originallyLinkedTracks: Set<Pair<IntId<LocationTrack>, JointNumber>>,
): FittedSwitch {
    assert(switchSuggestions.isNotEmpty()) { "switchSuggestions.isNotEmpty()" }

    val maxFarthestJointDistance =
        switchSuggestions.maxOf { suggestedSwitch ->
            suggestedSwitch.joints.maxOf { joint ->
                if (joint.number == farthestJoint.number) {
                    lineLength(desiredLocation, joint.location)
                } else 0.0
            }
        }

    return switchSuggestions.maxBy { fittedSwitch ->
        getSuggestedSwitchScore(
                fittedSwitch,
                switchStructure,
                farthestJoint,
                maxFarthestJointDistance,
                desiredLocation,
                originallyLinkedTracks,
            )
            .sumOf { it.sum() }
    }
}

fun createFittedSwitchByPoint(
    switchId: IntId<LayoutSwitch>,
    location: IPoint,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): FittedSwitch? =
    findBestSwitchFitForAllPointsInSamplingGrid(
            SwitchPlacingRequest(SamplingGridPoints(location.toPoint()), switchId),
            switchStructure,
            nearbyLocationTracks,
        )
        .keys()
        .firstOrNull()

fun findBestSwitchFitForAllPointsInSamplingGrid(
    request: SwitchPlacingRequest,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): PointAssociation<FittedSwitch> {
    val (grid, switchId) = request
    val bboxExpansion = max(switchStructure.bbox.width, switchStructure.bbox.height) * 1.125
    val gridBbox = grid.bounds + bboxExpansion
    val pointBboxSize = max(switchStructure.bbox.width, switchStructure.bbox.height) * 2.25
    val pointBboxes =
        grid.points.associateWith { point -> BoundingBox(0.0..pointBboxSize, 0.0..pointBboxSize).centerAt(point) }

    val croppedTracks =
        nearbyLocationTracks.map { (track, geometry) -> track to cropPoints(track.id as IntId, geometry, gridBbox) }

    val intersections = findTrackIntersectionsForGridPoints(croppedTracks.map { it.second }, grid)
    val (sharedSwitchJoint, switchAlignmentsContainingSharedJoint) = getSharedSwitchJoint(switchStructure)
    val farthestJoint = findFarthestJoint(switchStructure, sharedSwitchJoint, switchAlignmentsContainingSharedJoint[0])

    val transformations =
        intersections.mapMulti(parallel = true) { intersection, points ->
            if (points.none { point -> pointBboxes[point]?.contains(intersection.point) == true }) setOf()
            else {
                findTransformations(
                        intersection.point,
                        intersection.alignment1,
                        intersection.alignment2,
                        switchAlignmentsContainingSharedJoint[0],
                        switchAlignmentsContainingSharedJoint[1],
                        sharedSwitchJoint,
                        switchStructure,
                    )
                    .toSet()
            }
        }
    val fits =
        transformations.map(parallel = true) { transformation ->
            fitSwitch(transformation, croppedTracks, switchStructure, LocationAccuracy.GEOMETRY_CALCULATED)
        }
    val originallyLinkedTracks = getOriginallyLinkedTrackJoints(nearbyLocationTracks, switchId).toSet()
    return fits.aggregateByPoint(parallel = true) { point, pointFits ->
        selectBestSuggestedSwitch(pointFits, switchStructure, farthestJoint, point, originallyLinkedTracks)
    }
}

private fun getOriginallyLinkedTrackJoints(
    tracksAndGeometries: List<Pair<LocationTrack, LocationTrackGeometry>>,
    switchId: IntId<LayoutSwitch>,
): Set<Pair<IntId<LocationTrack>, JointNumber>> =
    tracksAndGeometries
        .flatMap { (track, geometry) ->
            geometry.trackSwitchLinks.mapNotNull { switchLink ->
                if (switchLink.switchId == switchId && switchLink.type == TrackSwitchLinkType.INNER)
                    (track.id as IntId) to switchLink.jointNumber
                else null
            }
        }
        .toSet()

private data class TrackIntersection(
    val point: IPoint,
    val distance: Double,
    val alignment1: IAlignment<LocationTrackM>,
    val alignment2: IAlignment<LocationTrackM>,
)

/** Returns a copy of the alignment filtering out points that do not locate in the given bounding box. */
fun cropPoints(geometry: DbLocationTrackGeometry, bbox: BoundingBox): CroppedTrackGeometry =
    cropPoints(geometry.trackRowVersion.id, geometry, bbox, 0)

fun cropPoints(
    trackId: IntId<LocationTrack>,
    geometry: LocationTrackGeometry,
    bbox: BoundingBox,
): CroppedTrackGeometry = cropPoints(trackId, geometry, bbox, 0)

fun cropPoints(
    trackId: IntId<LocationTrack>,
    geometry: LocationTrackGeometry,
    bbox: BoundingBox,
    underlyingAlignmentCropStartSegmentIndex: Int,
): CroppedTrackGeometry {
    val filteredSegments =
        geometry.segmentsWithM.mapIndexedNotNull { segmentIndex, (segment, m) ->
            if (bbox.intersects(segment.boundingBox)) {
                val firstMatchingPointIndex = segment.segmentPoints.indexOfFirst(bbox::contains)
                val lastMatchingPointIndex = segment.segmentPoints.indexOfLast(bbox::contains)
                if (firstMatchingPointIndex in 0 until lastMatchingPointIndex) {
                    segment.slice(m.min, firstMatchingPointIndex, lastMatchingPointIndex)?.let { slice ->
                        segmentIndex to slice
                    }
                } else null
            } else null
        }
    return CroppedTrackGeometry(
        underlyingAlignmentCropStartSegmentIndex + (filteredSegments.firstOrNull()?.first ?: 0),
        filteredSegments.map { it.second.first },
        filteredSegments.map { it.second.second },
        trackId,
    )
}

data class CroppedTrackGeometry(
    val cropStartSegmentIndex: Int,
    override val segments: List<LayoutSegment>,
    override val segmentMValues: List<Range<LineM<LocationTrackM>>>,
    val id: IntId<LocationTrack>,
) : IAlignment<LocationTrackM> {

    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox)) }

    override fun toLog(): String = logFormat("id" to id, "segments" to segmentMValues)
}

data class CroppedAlignment<M : AlignmentM<M>>(
    val cropStartSegmentIndex: Int,
    override val segments: List<LayoutSegment>,
    override val segmentMValues: List<Range<LineM<M>>>,
) : IAlignment<M> {
    companion object {
        val empty = CroppedAlignment(0, emptyList(), emptyList())
    }

    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox)) }

    override fun toLog(): String = logFormat("segments" to segmentMValues)
}

fun cropNothing(geometry: DbLocationTrackGeometry) = cropNothing(geometry.trackRowVersion.id, geometry)

fun cropNothing(trackId: IntId<LocationTrack>, geometry: LocationTrackGeometry) =
    CroppedTrackGeometry(0, geometry.segments, geometry.segmentMValues, trackId)

fun <M : AlignmentM<M>> cropAlignment(
    segmentsWithM: List<Pair<LayoutSegment, Range<LineM<M>>>>,
    cropRange: Range<LineM<M>>,
): CroppedAlignment<M> {
    if (segmentsWithM.isEmpty()) return CroppedAlignment.empty as CroppedAlignment<M>
    val origRange = Range(segmentsWithM.first().second.min, segmentsWithM.last().second.max)
    val newSegments =
        when {
            !origRange.overlaps(cropRange) -> listOf()
            cropRange.contains(origRange) -> segmentsWithM
            else -> {
                segmentsWithM.mapNotNull { (s, m) ->
                    when {
                        cropRange.contains(m) -> s to m
                        cropRange.overlaps(m) -> {
                            val newRange = Range(maxOf(m.min, cropRange.min), minOf(m.max, cropRange.max))
                            s.slice(newRange.map { d -> d.toSegmentM(m.min) }, CROP_SLICE_SNAPPING_TOLERANCE) to
                                newRange
                        }

                        else -> null
                    }
                }
            }
        }
    return CroppedAlignment(0, newSegments.map { (s, _) -> s }, newSegments.map { (_, m) -> m })
}
