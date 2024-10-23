package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
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
import fi.fta.geoviite.infra.linking.FittedSwitch
import fi.fta.geoviite.infra.linking.FittedSwitchJoint
import fi.fta.geoviite.infra.linking.FittedSwitchJointMatch
import fi.fta.geoviite.infra.linking.GeometrySwitchFittingException
import fi.fta.geoviite.infra.linking.GeometrySwitchFittingFailure
import fi.fta.geoviite.infra.linking.GeometrySwitchFittingResult
import fi.fta.geoviite.infra.linking.GeometrySwitchFittingSuccess
import fi.fta.geoviite.infra.linking.GeometrySwitchSuggestionFailureReason
import fi.fta.geoviite.infra.linking.SuggestedSwitchJointMatchType
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IntersectType
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.angleDiffRads
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.closestPointOnLine
import fi.fta.geoviite.infra.math.directionBetweenPoints
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchPositionTransformation
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.calculateSwitchLocationDelta
import fi.fta.geoviite.infra.switchLibrary.transformSwitchPoint
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import kotlin.math.max
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

private const val TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT = 0.5
const val TOLERANCE_JOINT_LOCATION_NEW_POINT = 0.01

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
        jointsInLayoutSpace: List<SwitchJoint>,
        locationAccuracy: LocationAccuracy?,
    ): FittedSwitch {
        val tracks =
            listOf(
                    tracksLinkedThroughGeometry.map(locationTrackService::getWithAlignment),
                    locationTrackService.listNearWithAlignments(
                        branch.draft,
                        boundingBoxAroundPoints(jointsInLayoutSpace.map { it.location }) * 1.5,
                    ),
                )
                .flatten()
                .distinctBy { it.first.id }
                .map { (lt, alignment) -> lt to cropNothing(alignment) }

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
): List<SwitchJoint> {
    val layoutJointPoints =
        geomSwitch.joints.map { geomJoint ->
            SwitchJoint(number = geomJoint.number, location = toLayoutCoordinate.transform(geomJoint.location))
        }
    val switchLocationDelta = calculateSwitchLocationDelta(layoutJointPoints, switchStructure)
    return if (switchLocationDelta != null) {
        switchStructure.alignmentJoints.map { joint ->
            SwitchJoint(number = joint.number, location = transformSwitchPoint(switchLocationDelta, joint.location))
        }
    } else {
        throw GeometrySwitchFittingException(GeometrySwitchSuggestionFailureReason.INVALID_JOINTS)
    }
}

private data class PossibleSegment(
    val segment: ISegment,
    val segmentIndex: Int,
    val closestPointM: Double,
    val closestSegmentPointIndex: Int,
    val jointDistanceToSegment: Double,
)

private fun findSuggestedSwitchJointMatches(
    joint: SwitchJoint,
    locationTrack: LocationTrack,
    alignment: CroppedAlignment,
    tolerance: Double,
): List<FittedSwitchJointMatch> {
    val jointLocation = joint.location
    val possibleSegments = findPossiblyMatchableSegments(alignment, jointLocation, tolerance)
    if (possibleSegments.isEmpty()) {
        return listOf()
    }
    val jointDistanceToAlignment = possibleSegments.minOf { segment -> segment.jointDistanceToSegment }

    return possibleSegments.flatMap { possibleSegment ->
        val segmentIndex = possibleSegment.segmentIndex
        val segment = possibleSegment.segment
        val closestSegmentPointIndex = possibleSegment.closestSegmentPointIndex

        val segmentLinesWithinTolerance =
            takeSegmentLineMatchesAroundPoint(jointLocation, tolerance, segment, closestSegmentPointIndex)

        val startMatches = segment.segmentStart.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)
        val endMatches = segment.segmentEnd.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)

        listOfNotNull(
            // Check if segment's start point is within tolerance
            if (startMatches)
                FittedSwitchJointMatch(
                    locationTrackId = locationTrack.id as IntId,
                    m = segment.startM,
                    matchType = SuggestedSwitchJointMatchType.START,
                    switchJoint = joint,
                    distance = lineLength(segment.segmentStart, jointLocation),
                    distanceToAlignment = jointDistanceToAlignment,
                    alignmentId = locationTrack.alignmentVersion?.id,
                    segmentIndex = segmentIndex,
                )
            else null,

            // Check if segment's end point is within tolerance
            if (endMatches)
                FittedSwitchJointMatch(
                    locationTrackId = locationTrack.id as IntId,
                    m = segment.endM,
                    matchType = SuggestedSwitchJointMatchType.END,
                    switchJoint = joint,
                    distance = lineLength(segment.segmentEnd, jointLocation),
                    distanceToAlignment = jointDistanceToAlignment,
                    alignmentId = locationTrack.alignmentVersion?.id,
                    segmentIndex = segmentIndex,
                )
            else null,
        ) +
            segmentLinesWithinTolerance
                .map { (closestAlignmentPoint, jointDistanceToSegment) ->
                    FittedSwitchJointMatch(
                        locationTrackId = locationTrack.id as IntId,
                        m = segment.getClosestPointM(closestAlignmentPoint).first,
                        matchType = SuggestedSwitchJointMatchType.LINE,
                        switchJoint = joint,
                        distance = jointDistanceToSegment,
                        distanceToAlignment = jointDistanceToAlignment,
                        alignmentId = locationTrack.alignmentVersion?.id,
                        segmentIndex = segmentIndex,
                    )
                }
                .toList()
    }
}

private fun findPossiblyMatchableSegments(
    alignment: CroppedAlignment,
    jointLocation: Point,
    tolerance: Double,
): List<PossibleSegment> {
    val maxMatchableDistance = max(TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT, tolerance)
    val closestSegmentIndex = alignment.findClosestSegmentIndex(jointLocation) ?: return listOf()
    val firstPossibleSegmentIndex = (closestSegmentIndex - 1).coerceAtLeast(0)

    return alignment.segments
        .subList(firstPossibleSegmentIndex, (closestSegmentIndex + 2).coerceAtMost(alignment.segments.size))
        .mapIndexed { index, segment ->
            val closestPointM = segment.getClosestPointM(jointLocation).first
            val pointSeekResult = segment.seekPointAtM(closestPointM)
            val closestSegmentPointIndex = pointSeekResult.index
            val jointDistanceToSegment = lineLength(pointSeekResult.point, jointLocation)
            PossibleSegment(
                segment,
                index + firstPossibleSegmentIndex + alignment.cropStartSegmentIndex,
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
    joints: List<SwitchJoint>,
    locationTrackAlignment: Pair<LocationTrack, CroppedAlignment>,
    tolerance: Double,
): List<FittedSwitchJointMatch> {
    return joints.flatMap { joint ->
        findSuggestedSwitchJointMatches(
            joint = joint,
            locationTrack = locationTrackAlignment.first,
            alignment = locationTrackAlignment.second,
            tolerance = tolerance,
        )
    }
}

fun fitSwitch(
    jointsInLayoutSpace: List<SwitchJoint>,
    switchStructure: SwitchStructure,
    alignments: List<Pair<LocationTrack, CroppedAlignment>>,
    locationAccuracy: LocationAccuracy?,
): FittedSwitch {
    val jointMatchTolerance = 0.2 // TODO: There could be tolerance per joint point in switch structure

    val matchesByLocationTrack =
        alignments
            .associate { alignment ->
                alignment.first to
                    findSuggestedSwitchJointMatches(
                        joints = jointsInLayoutSpace,
                        locationTrackAlignment = alignment,
                        tolerance = jointMatchTolerance,
                    )
            }
            .filter { it.value.isNotEmpty() }

    val endJoints = getEndJoints(matchesByLocationTrack)
    val matchByJoint = getBestMatchByJoint(matchesByLocationTrack, endJoints)
    val jointsWithoutMatches =
        jointsInLayoutSpace
            .filterNot { matchByJoint.containsKey(it) }
            .associateWith { emptyList<FittedSwitchJointMatch>() }

    val suggestedJoints =
        (matchByJoint + jointsWithoutMatches).map { (joint, matches) ->
            FittedSwitchJoint(
                number = joint.number,
                location = joint.location,
                matches = matches,
                locationAccuracy = locationAccuracy,
            )
        }

    return FittedSwitch(switchStructure = switchStructure, joints = suggestedJoints)
}

private fun getBestMatchByJoint(
    matchesByLocationTrack: Map<LocationTrack, List<FittedSwitchJointMatch>>,
    endJoints: Map<LocationTrack, Pair<SwitchJoint, SwitchJoint>>,
) =
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
                .mapNotNull { filteredMatches -> filteredMatches.minByOrNull { it.distance } }
        }
        .groupBy { it.switchJoint }

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
        val jointsSortedByMatchLength = joints.sortedWith(compareBy(FittedSwitchJointMatch::m))
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

private fun lines(alignment: IAlignment): List<Line> {
    return alignment.segments.flatMap { segment ->
        segment.segmentPoints.dropLast(1).mapIndexed { index, point -> Line(point, segment.segmentPoints[index + 1]) }
    }
}

const val MAX_LINE_INTERSECTION_DISTANCE = 0.5
const val MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES = 1

private fun findIntersections(alignment1: IAlignment, alignment2: IAlignment): List<TrackIntersection> {
    // Ignore parallel alignments. Points of alignments are filtered so
    // that alignments are about 0 - 200 meters long, and therefore we can compare
    // angles from start to end.
    val directionDiff = alignmentStartEndDirectionDiff(alignment1, alignment2)?.let(::radsToDegrees)
    if (directionDiff == null || directionDiff < MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES) return emptyList()

    val lines1 = lines(alignment1)
    val lines2 = lines(alignment2)
    return lines1.flatMap { line1 ->
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
                val linePointDistanceCheckPairs =
                    listOf(line1 to line2.start, line1 to line2.end, line2 to line1.start, line2 to line1.end)
                val minDistanceAndPoint =
                    linePointDistanceCheckPairs
                        .map { (line, point) -> pointDistanceToLine(line.start, line.end, point) to point }
                        .minBy { (distance, _) -> distance }
                if (minDistanceAndPoint.first <= MAX_LINE_INTERSECTION_DISTANCE) {
                    TrackIntersection(
                        point = minDistanceAndPoint.second,
                        distance = minDistanceAndPoint.first,
                        alignment1 = alignment1,
                        alignment2 = alignment2,
                    )
                } else null
            }
        }
    }
}

private fun alignmentStartEndDirectionDiff(alignment1: IAlignment, alignment2: IAlignment): Double? {
    val track1Direction = alignmentStartEndDirection(alignment1)
    val track2Direction = alignmentStartEndDirection(alignment2)
    return if (track1Direction != null && track2Direction != null) {
        angleDiffRads(track1Direction, track2Direction)
    } else null
}

private fun alignmentStartEndDirection(alignment: IAlignment): Double? {
    val start = alignment.firstSegmentStart
    val end = alignment.lastSegmentEnd
    return if (start != null && end != null) directionBetweenPoints(start, end) else null
}

private fun getClosestPointAsIntersection(
    track1: IAlignment,
    track2: IAlignment,
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
    trackAlignments: List<IAlignment>,
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
    joint: SwitchJoint,
    switchAlignment: SwitchAlignment,
): SwitchJoint {
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
    val fixPoint: AlignmentPoint,
    val pointBackwards: AlignmentPoint?,
    val pointForwards: AlignmentPoint?,
)

private fun findPointsOnTrack(from: IPoint, distance: Double, alignment: IAlignment): SwitchPointSeekResult? {
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
    alignment: IAlignment,
    switchAlignment: SwitchAlignment,
    joint: SwitchJoint,
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
    alignment1: IAlignment,
    alignment2: IAlignment,
    switchAlignment1: SwitchAlignment,
    switchAlignment2: SwitchAlignment,
    joint: SwitchJoint,
    switchStructure: SwitchStructure,
): List<SwitchPositionTransformation> {
    return findTransformations(point, alignment1, switchAlignment1, joint, switchStructure) +
        findTransformations(point, alignment1, switchAlignment2, joint, switchStructure) +
        findTransformations(point, alignment2, switchAlignment1, joint, switchStructure) +
        findTransformations(point, alignment2, switchAlignment2, joint, switchStructure)
}

fun fitSwitch(
    transformation: SwitchPositionTransformation,
    tracks: List<Pair<LocationTrack, CroppedAlignment>>,
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
        alignments = tracks,
        locationAccuracy = locationAccuracy,
    )
}

fun getSharedSwitchJoint(switchStructure: SwitchStructure): Pair<SwitchJoint, List<SwitchAlignment>> {
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
) {
    fun sum() = jointScore + farthestJointExtraScore
}

fun getSuggestedSwitchScore(
    switchSuggestion: FittedSwitch,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchJoint,
    maxFarthestJointDistance: Double,
    desiredLocation: IPoint,
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
        SuggestedSwitchJointScore(joint.number, jointScore, farthestJointExtraScore)
    }
}

private fun selectBestSuggestedSwitch(
    switchSuggestions: Set<FittedSwitch>,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchJoint,
    desiredLocation: IPoint,
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
        getSuggestedSwitchScore(fittedSwitch, switchStructure, farthestJoint, maxFarthestJointDistance, desiredLocation)
            .sumOf { it.sum() }
    }
}

fun createFittedSwitchByPoint(
    location: IPoint,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): FittedSwitch? =
    findBestSwitchFitForAllPointsInSamplingGrid(
            SamplingGridPoints(location.toPoint()),
            switchStructure,
            nearbyLocationTracks,
        )
        .keys()
        .firstOrNull()

fun findBestSwitchFitForAllPointsInSamplingGrid(
    grid: SamplingGridPoints,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): PointAssociation<FittedSwitch> {
    val bboxExpansion = max(switchStructure.bbox.width, switchStructure.bbox.height) * 1.125
    val gridBbox = grid.bounds() + bboxExpansion
    val pointBboxSize = max(switchStructure.bbox.width, switchStructure.bbox.height) * 2.25
    val pointBboxes =
        grid.points.associateWith { point -> BoundingBox(0.0..pointBboxSize, 0.0..pointBboxSize).centerAt(point) }

    val croppedTracks = nearbyLocationTracks.map { (track, alignment) -> track to cropPoints(alignment, gridBbox) }

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
    return fits.aggregateByPoint(parallel = true) { point, pointFits ->
        selectBestSuggestedSwitch(pointFits, switchStructure, farthestJoint, point)
    }
}

private data class TrackIntersection(
    val point: IPoint,
    val distance: Double,
    val alignment1: IAlignment,
    val alignment2: IAlignment,
)

/** Returns a copy of the alignment filtering out points that do not locate in the given bounding box. */
fun cropPoints(alignment: LayoutAlignment, bbox: BoundingBox): CroppedAlignment =
    cropPoints(alignment.id, alignment.segments, bbox, 0)

fun cropPoints(alignment: CroppedAlignment, bbox: BoundingBox): CroppedAlignment =
    cropPoints(alignment.id, alignment.segments, bbox, alignment.cropStartSegmentIndex)

fun cropPoints(
    alignmentId: DomainId<LayoutAlignment>,
    segments: List<LayoutSegment>,
    bbox: BoundingBox,
    underlyingAlignmentCropStartSegmentIndex: Int,
): CroppedAlignment {
    val filteredSegments =
        segments.mapIndexedNotNull { segmentIndex, segment ->
            if (bbox.intersects(segment.boundingBox)) {
                val firstMatchingPointIndex = segment.segmentPoints.indexOfFirst(bbox::contains)
                val lastMatchingPointIndex = segment.segmentPoints.indexOfLast(bbox::contains)
                if (firstMatchingPointIndex in 0 until lastMatchingPointIndex) {
                    segment.slice(firstMatchingPointIndex, lastMatchingPointIndex)?.let { slice ->
                        segmentIndex to slice
                    }
                } else null
            } else null
        }
    return CroppedAlignment(
        underlyingAlignmentCropStartSegmentIndex + (filteredSegments.firstOrNull()?.first ?: 0),
        filteredSegments.map { it.second },
        alignmentId,
    )
}

data class CroppedAlignment(
    val cropStartSegmentIndex: Int,
    override val segments: List<LayoutSegment>,
    override val id: DomainId<LayoutAlignment>,
) : IAlignment {

    override val boundingBox: BoundingBox? by lazy { boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox)) }

    override fun toLog(): String = logFormat("id" to id, "segments" to segments.map(ISegment::id))
}

fun cropNothing(alignment: LayoutAlignment) = CroppedAlignment(0, alignment.segments, alignment.id)
