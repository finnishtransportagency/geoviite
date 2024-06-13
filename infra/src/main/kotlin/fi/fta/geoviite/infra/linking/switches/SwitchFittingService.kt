package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.linking.FittedSwitch
import fi.fta.geoviite.infra.linking.FittedSwitchJoint
import fi.fta.geoviite.infra.linking.FittedSwitchJointMatch
import fi.fta.geoviite.infra.linking.LinkingDao
import fi.fta.geoviite.infra.linking.LocationTrackEndpoint
import fi.fta.geoviite.infra.linking.SuggestedSwitchCreateParams
import fi.fta.geoviite.infra.linking.SuggestedSwitchCreateParamsAlignmentMapping
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
import fi.fta.geoviite.infra.math.interpolate
import fi.fta.geoviite.infra.math.lineIntersection
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.math.pointDistanceToLine
import fi.fta.geoviite.infra.math.radsToDegrees
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchPositionTransformation
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.calculateSwitchLocationDeltaOrNull
import fi.fta.geoviite.infra.switchLibrary.transformSwitchPoint
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ISegment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors
import kotlin.math.max

private const val TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT = 0.5
const val TOLERANCE_JOINT_LOCATION_NEW_POINT = 0.01


/**
 * Tools for finding a fit for a switch: The positioning of the switch's joints, based on the geometry of the tracks
 * around it.
 */
@GeoviiteService
class SwitchFittingService @Autowired constructor(
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val linkingDao: LinkingDao,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
    private val coordinateTransformationService: CoordinateTransformationService,
) {

    @Transactional(readOnly = true)
    fun getFitsInArea(branch: LayoutBranch, bbox: BoundingBox): List<FittedSwitch> {
        val missing = linkingDao.getMissingLayoutSwitchLinkings(bbox)
        return missing.mapNotNull { missingLayoutSwitchLinking ->
            // Transform joints to layout space and calculate missing joints
            val geomSwitch = geometryDao.getSwitch(missingLayoutSwitchLinking.geometrySwitchId)
            val structure = geomSwitch.switchStructureId?.let(switchLibraryService::getSwitchStructure)
            val toLayoutCoordinate =
                coordinateTransformationService.getTransformation(missingLayoutSwitchLinking.planSrid, LAYOUT_SRID)
            // TODO: There is a missing switch here, but current logic doesn't support non-typed suggestions
            if (structure == null) null else calculateLayoutSwitchJoints(
                geomSwitch, structure, toLayoutCoordinate
            )?.let { calculatedJoints ->
                val switchBoundingBox = boundingBoxAroundPoints(calculatedJoints.map { it.location }) * 1.5
                val nearAlignmentIds = locationTrackDao.fetchVersionsNear(branch.draft, switchBoundingBox)

                val alignments = (nearAlignmentIds + missingLayoutSwitchLinking.locationTrackIds)
                    .distinct()
                    .map { id -> locationTrackService.getWithAlignment(id) }

                fitSwitch(
                    jointsInLayoutSpace = calculatedJoints,
                    switchStructure = structure,
                    alignments = alignments,
                    geometrySwitch = geomSwitch,
                    alignmentEndPoint = null,
                    geometryPlanId = missingLayoutSwitchLinking.planId,
                    getMeasurementMethod = this::getMeasurementMethod,
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getFitAtPoint(branch: LayoutBranch, point: IPoint, switchStructureId: IntId<SwitchStructure>): FittedSwitch? {
        return getFitsAtPoints(branch, listOf(point to switchStructureId))[0]
    }

    @Transactional(readOnly = true)
    fun getFitsAtPoints(branch: LayoutBranch, points: List<Pair<IPoint, IntId<SwitchStructure>>>): List<FittedSwitch?> {
        val nearbyLocationTracks = points.map { (location) ->
            locationTrackService.getLocationTracksNear(branch.draft, location)
        }
        val switchStructures = points.map { (_, id) -> switchLibraryService.getSwitchStructure(id) }
        return points.mapIndexed { index, point -> index to point }.parallelStream().map { (index, point) ->
            createSuggestedSwitchByPoint(point.first, switchStructures[index], nearbyLocationTracks[index])
        }.collect(Collectors.toList())
    }

    @Transactional(readOnly = true)
    fun getFitAtEndpoint(branch: LayoutBranch, createParams: SuggestedSwitchCreateParams): FittedSwitch? {
        val switchStructure =
            createParams.switchStructureId?.let(switchLibraryService::getSwitchStructure) ?: return null
        val locationTracks = createParams.alignmentMappings
            .map { mapping -> mapping.locationTrackId }
            .associateWith { id -> locationTrackService.getWithAlignmentOrThrow(branch.draft, id) }

        val areaSize = switchStructure.bbox.width.coerceAtLeast(switchStructure.bbox.height) * 2.0
        val switchAreaBbox = BoundingBox(
            Point(0.0, 0.0), Point(areaSize, areaSize)
        ).centerAt(createParams.locationTrackEndpoint.location)
        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(branch.draft, switchAreaBbox)

        return fitSwitch(
            createParams.locationTrackEndpoint,
            switchStructure,
            createParams.alignmentMappings,
            nearbyLocationTracks,
            locationTracks,
            getMeasurementMethod = this::getMeasurementMethod,
        )
    }

    private fun getMeasurementMethod(id: IntId<GeometrySwitch>): MeasurementMethod? =
        geometryDao.getMeasurementMethodForSwitch(id)
}

fun calculateLayoutSwitchJoints(
    geomSwitch: GeometrySwitch,
    switchStructure: SwitchStructure,
    toLayoutCoordinate: Transformation,
): List<SwitchJoint>? {
    val layoutJointPoints = geomSwitch.joints.map { geomJoint ->
        SwitchJoint(
            number = geomJoint.number,
            location = toLayoutCoordinate.transform(geomJoint.location),
        )
    }
    val switchLocationDelta = calculateSwitchLocationDeltaOrNull(layoutJointPoints, switchStructure)
    return if (switchLocationDelta != null) {
        switchStructure.alignmentJoints.map { joint ->
            SwitchJoint(
                number = joint.number, location = transformSwitchPoint(switchLocationDelta, joint.location)
            )
        }
    } else {
        null
    }
}

fun findSuggestedSwitchJointMatches(
    joint: SwitchJoint,
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    tolerance: Double,
): List<FittedSwitchJointMatch> {
    val jointLocation = joint.location

    val closestSegmentIndex = alignment.findClosestSegmentIndex(jointLocation) ?: return listOf()
    val possibleSegmentIndices = IntRange(
        (closestSegmentIndex - 1).coerceAtLeast(0), (closestSegmentIndex + 1).coerceAtMost(alignment.segments.lastIndex)
    )

    val possibleSegments = alignment.segments.slice(possibleSegmentIndices)

    val jointDistanceToAlignment = possibleSegments.flatMap { segment ->
        segment.segmentPoints.mapIndexedNotNull { pIdx, point ->
            segment.segmentPoints.getOrNull(pIdx - 1)?.let { previousPoint ->
                val closestSegmentPoint = closestPointOnLine(previousPoint, point, jointLocation)
                val jointDistanceToSegment = lineLength(closestSegmentPoint, jointLocation)
                jointDistanceToSegment
            }
        }
    }.min()

    return possibleSegments.flatMapIndexed { index, segment ->
        val segmentIndex = possibleSegmentIndices.first + index

        val startMatches = segment.segmentStart.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)
        val endMatches = segment.segmentEnd.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)

        listOfNotNull(
            // Check if segment's start point is within tolerance
            if (startMatches) FittedSwitchJointMatch(
                locationTrackId = locationTrack.id as IntId,
                m = segment.startM,
                matchType = SuggestedSwitchJointMatchType.START,
                switchJoint = joint,
                distance = lineLength(segment.segmentStart, jointLocation),
                distanceToAlignment = jointDistanceToAlignment,
                alignmentId = locationTrack.alignmentVersion?.id,
                segmentIndex = segmentIndex,
            ) else null,

            // Check if segment's end point is within tolerance
            if (endMatches) FittedSwitchJointMatch(
                locationTrackId = locationTrack.id as IntId,
                m = segment.endM,
                matchType = SuggestedSwitchJointMatchType.END,
                switchJoint = joint,
                distance = lineLength(segment.segmentEnd, jointLocation),
                distanceToAlignment = jointDistanceToAlignment,
                alignmentId = locationTrack.alignmentVersion?.id,
                segmentIndex = segmentIndex,
            ) else null
        ) + segment.segmentPoints.mapIndexedNotNull { pIdx, point ->
            segment.segmentPoints.getOrNull(pIdx - 1)?.let { previousPoint ->
                val closestAlignmentPoint = closestPointOnLine(previousPoint, point, jointLocation)
                val jointDistanceToSegment = lineLength(closestAlignmentPoint, jointLocation)

                if (jointDistanceToSegment < tolerance) {
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
                } else null
            }
        }
    }
}

fun findSuggestedSwitchJointMatches(
    joints: List<SwitchJoint>,
    locationTrackAlignment: Pair<LocationTrack, LayoutAlignment>,
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
    alignments: List<Pair<LocationTrack, LayoutAlignment>>,
    alignmentEndPoint: LocationTrackEndpoint?,
    geometrySwitch: GeometrySwitch? = null,
    geometryPlanId: IntId<GeometryPlan>? = null,
    getMeasurementMethod: (switchId: IntId<GeometrySwitch>) -> MeasurementMethod?,
): FittedSwitch {
    val jointMatchTolerance = 0.2 // TODO: There could be tolerance per joint point in switch structure

    val matchesByLocationTrack = alignments.associate { alignment ->
        alignment.first to findSuggestedSwitchJointMatches(
            joints = jointsInLayoutSpace,
            locationTrackAlignment = alignment,
            tolerance = jointMatchTolerance,
        )
    }.filter { it.value.isNotEmpty() }

    val endJoints = getEndJoints(matchesByLocationTrack)
    val matchByJoint = getBestMatchByJoint(matchesByLocationTrack, endJoints)
    val jointsWithoutMatches = jointsInLayoutSpace
        .filterNot { matchByJoint.containsKey(it) }
        .associateWith { emptyList<FittedSwitchJointMatch>() }

    val suggestedJoints = (matchByJoint + jointsWithoutMatches).map { (joint, matches) ->
        val locationAccuracy = switchJointLocationAccuracy(getMeasurementMethod, geometrySwitch)
        FittedSwitchJoint(
            number = joint.number,
            location = joint.location,
            matches = matches,
            locationAccuracy = locationAccuracy,
        )
    }

    return FittedSwitch(
        name = geometrySwitch?.name ?: SwitchName(switchStructure.baseType.name),
        switchStructureId = switchStructure.id as IntId,
        joints = suggestedJoints,
        geometrySwitchId = geometrySwitch?.id?.let { id -> id as? IntId },
        alignmentEndPoint = alignmentEndPoint,
        geometryPlanId = geometryPlanId,
    )
}

private fun getBestMatchByJoint(
    matchesByLocationTrack: Map<LocationTrack, List<FittedSwitchJointMatch>>,
    endJoints: Map<LocationTrack, Pair<SwitchJoint, SwitchJoint>>,
) = matchesByLocationTrack.flatMap { (lt, matches) ->
    matches.groupBy { it.switchJoint }.map { (joint, jointMatches) ->
        getBestMatchesForJoint(
            jointMatches = jointMatches,
            isFirstJoint = endJoints[lt]?.first == joint,
            isLastJoint = endJoints[lt]?.second == joint
        )
    }.mapNotNull { filteredMatches -> filteredMatches.minByOrNull { it.distance } }
}.groupBy { it.switchJoint }

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
            .filter { it.matchType == SuggestedSwitchJointMatchType.START || it.matchType == SuggestedSwitchJointMatchType.END }
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

private fun switchJointLocationAccuracy(
    getMeasurementMethod: (match: IntId<GeometrySwitch>) -> MeasurementMethod?,
    geometrySwitch: GeometrySwitch?,
): LocationAccuracy? {
    return if (geometrySwitch != null) {
        getMeasurementMethod(geometrySwitch.id as IntId)?.let(::mapMeasurementMethodToLocationAccuracy)
    } else {
        LocationAccuracy.GEOMETRY_CALCULATED
    }
}

private fun mapMeasurementMethodToLocationAccuracy(mm: MeasurementMethod): LocationAccuracy = when (mm) {
    MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY -> LocationAccuracy.DESIGNED_GEOLOCATION
    MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY -> LocationAccuracy.OFFICIALLY_MEASURED_GEODETICALLY
    MeasurementMethod.TRACK_INSPECTION -> LocationAccuracy.MEASURED_GEODETICALLY
    MeasurementMethod.DIGITIZED_AERIAL_IMAGE -> LocationAccuracy.DIGITIZED_AERIAL_IMAGE
    MeasurementMethod.UNVERIFIED_DESIGNED_GEOMETRY -> LocationAccuracy.MEASURED_GEODETICALLY
}

fun inferSwitchTransformationBothDirection(
    location: IPoint,
    switchStructure: SwitchStructure,
    switchAlignmentId: StringId<SwitchAlignment>,
    alignment: LayoutAlignment,
): SwitchPositionTransformation? {
    return inferSwitchTransformation(
        location, switchStructure, switchAlignmentId, alignment, true
    ) ?: inferSwitchTransformation(
        location, switchStructure, switchAlignmentId, alignment, false
    )
}

fun inferSwitchTransformation(
    location: IPoint,
    switchStructure: SwitchStructure,
    switchAlignmentId: StringId<SwitchAlignment>,
    alignment: LayoutAlignment,
    ascending: Boolean,
): SwitchPositionTransformation? {
    val switchAlignment = switchStructure.getAlignment(switchAlignmentId)

    val alignmentJointNumberPairs = switchAlignment.jointNumbers.flatMapIndexed { index1, jointNumber1 ->
        switchAlignment.jointNumbers.mapIndexed { index2, jointNumber2 ->
            if (index2 > index1) jointNumber1 to jointNumber2
            else null
        }.filterNotNull()
    }.sortedBy { (jointNumber1, jointNumber2) ->
        // Prefer full alignments, then alignments from the presentation point
        if (jointNumber1 == switchAlignment.jointNumbers.first() && jointNumber2 == switchAlignment.jointNumbers.last()) 0
        else if (jointNumber1 == switchStructure.presentationJointNumber) 1
        else 2
    }

    val allPoints = alignment.allSegmentPoints.toList()
    // Find the "start" point for switch joint
    // It's usually the first or the last point of alignment
    val startPointIndex = allPoints.indexOfFirst { point ->
        point.isSame(location, TOLERANCE_JOINT_LOCATION_NEW_POINT)
    }

    if (startPointIndex == -1) return null

    val switchEndPointSearchIndexes = if (ascending) (startPointIndex..allPoints.lastIndex)
    else (startPointIndex downTo 0)

    val startPoint = allPoints[startPointIndex]

    return alignmentJointNumberPairs.firstNotNullOfOrNull { (startJointNumber, endJointNumber) ->
        val startJointLocation = switchStructure.getJointLocation(startJointNumber)
        val endJointLocation = switchStructure.getJointLocation(endJointNumber)
        val switchJointLength = lineLength(startJointLocation, endJointLocation)

        switchEndPointSearchIndexes.firstOrNull { endPointIndex ->
            val pointsLength = lineLength(allPoints[endPointIndex], startPoint)
            pointsLength > switchJointLength
        }?.let { endPointIndex ->
            allPoints
                .getOrNull(if (ascending) endPointIndex - 1 else endPointIndex + 1)
                ?.let { previousEndPoint ->
                    val endPoint = allPoints[endPointIndex]
                    val lengthToPreviousEndPoint = lineLength(previousEndPoint, startPoint)
                    val lengthToEndPoint = lineLength(endPoint, startPoint)

                    val proportion =
                        (switchJointLength - lengthToPreviousEndPoint) / (lengthToEndPoint - lengthToPreviousEndPoint)
                    interpolate(previousEndPoint, endPoint, proportion)
                }
        }?.let { endPoint ->
            val testJoints = listOf(
                SwitchJoint(startJointNumber, startPoint.toPoint()),
                SwitchJoint(endJointNumber, endPoint.toPoint()),
            )

            calculateSwitchLocationDeltaOrNull(testJoints, switchStructure)
        }
    }
}

fun hasAllMappedAlignments(
    alignmentMappings: List<SuggestedSwitchCreateParamsAlignmentMapping>,
    switchSuggestion: FittedSwitch,
    switchStructure: SwitchStructure,
): Boolean {
    return alignmentMappings.all { mappingToCheck ->
        val switchAlignmentToCheck = switchStructure.getAlignment(mappingToCheck.switchAlignmentId)
        switchAlignmentToCheck.jointNumbers.all { jointNumberToCheck ->
            switchSuggestion.joints.any { suggestedSwitchJoint ->
                suggestedSwitchJoint.number == jointNumberToCheck && suggestedSwitchJoint.matches.any { match ->
                    match.locationTrackId == mappingToCheck.locationTrackId
                }
            }
        }
    }
}

fun fitSwitch(
    locationTrackEndpoint: LocationTrackEndpoint,
    switchStructure: SwitchStructure,
    alignmentMappings: List<SuggestedSwitchCreateParamsAlignmentMapping>,
    nearbyAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
    alignmentById: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    getMeasurementMethod: (switchId: IntId<GeometrySwitch>) -> MeasurementMethod?,
): FittedSwitch? {
    val mappedAlignments = alignmentMappings.map { mapping ->
        alignmentById[mapping.locationTrackId]
            ?: throw IllegalArgumentException("Alignment with id ${mapping.locationTrackId} is not provided")
    }

    alignmentMappings.forEach { alignmentMapping ->
        val alignment = alignmentById[alignmentMapping.locationTrackId]?.second
        require(alignment != null) { "Alignment mapping failed: id=${alignmentMapping.locationTrackId}" }
        val switchTransformation = if (alignmentMapping.ascending == null) inferSwitchTransformationBothDirection(
            locationTrackEndpoint.location,
            switchStructure,
            alignmentMapping.switchAlignmentId,
            alignment,
        )
        else inferSwitchTransformation(
            locationTrackEndpoint.location,
            switchStructure,
            alignmentMapping.switchAlignmentId,
            alignment,
            alignmentMapping.ascending,
        )

        if (switchTransformation != null) {
            val jointsInLayoutSpace = switchStructure.joints.map { joint ->
                joint.copy(location = transformSwitchPoint(switchTransformation, joint.location))
            }

            val alignments = (nearbyAlignments + mappedAlignments).distinctBy { (locationTrack, _) -> locationTrack.id }

            val suggestedSwitch = fitSwitch(
                jointsInLayoutSpace = jointsInLayoutSpace,
                switchStructure = switchStructure,
                alignments = alignments,
                geometrySwitch = null,
                geometryPlanId = null,
                alignmentEndPoint = locationTrackEndpoint,
                getMeasurementMethod = getMeasurementMethod
            )

            return if (hasAllMappedAlignments(alignmentMappings, suggestedSwitch, switchStructure)) suggestedSwitch
            else null
        }
    }

    return null
}

private fun lines(alignment: IAlignment): List<Line> {
    return alignment.segments.flatMap { segment ->
        segment.segmentPoints.dropLast(1).mapIndexed { index, point ->
            Line(point, segment.segmentPoints[index + 1])
        }
    }
}

const val MAX_LINE_INTERSECTION_DISTANCE = 0.5
const val MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES = 1

private fun findClosestIntersections(
    alignment1: IAlignment,
    alignment2: IAlignment,
    desiredLocation: IPoint,
    count: Int,
): List<TrackIntersection> {
    // Ignore parallel alignments. Points of alignments are filtered so
    // that alignments are about 0 - 200 meters long, and therefore we can compare
    // angles from start to end.
    val directionDiff = alignmentStartEndDirectionDiff(alignment1, alignment2)?.let(::radsToDegrees)
    if (directionDiff == null || directionDiff < MAX_PARALLEL_LINE_ANGLE_DIFF_IN_DEGREES) return emptyList()

    val lines1 = lines(alignment1)
    val lines2 = lines(alignment2)
    val intersections = lines1.flatMap { line1 ->
        lines2.mapNotNull { line2 ->
            val intersection = lineIntersection(line1.start, line1.end, line2.start, line2.end)
            if (intersection != null && intersection.linesIntersect()) {
                TrackIntersection(
                    point = intersection.point,
                    distance = 0.0,
                    alignment1 = alignment1,
                    alignment2 = alignment2,
                    desiredLocation = desiredLocation
                )
            } else {
                val linePointDistanceCheckPairs = listOf(
                    line1 to line2.start, line1 to line2.end, line2 to line1.start, line2 to line1.end
                )
                val minDistanceAndPoint = linePointDistanceCheckPairs.map { (line, point) ->
                    pointDistanceToLine(line.start, line.end, point) to point
                }.minBy { (distance, _) -> distance }
                if (minDistanceAndPoint.first <= MAX_LINE_INTERSECTION_DISTANCE) {
                    TrackIntersection(
                        point = minDistanceAndPoint.second,
                        distance = minDistanceAndPoint.first,
                        alignment1 = alignment1,
                        alignment2 = alignment2,
                        desiredLocation = desiredLocation
                    )
                } else null
            }
        }
    }
    return intersections.sorted().take(count)
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

private fun getClosestPointAsIntersection(track1: IAlignment, track2: IAlignment, desiredLocation: IPoint): TrackIntersection? {
    return listOf(track1, track2)
        .mapNotNull { track -> track.getClosestPoint(desiredLocation) }
        .minByOrNull { (point, _) -> lineLength(point,desiredLocation) }
        ?.let { (closestPoint,_) ->
            TrackIntersection(
                alignment1 = track1,
                alignment2 = track2,
                point = closestPoint,
                distance = 0.0,
                desiredLocation = desiredLocation
            )
        }
}

private fun findTrackIntersections(
    trackAlignments: List<IAlignment>,
    desiredLocation: IPoint,
): List<TrackIntersection> {
    val trackPairs = trackAlignments.flatMapIndexed { index, track1 ->
        trackAlignments.drop(index + 1).map { track2 -> track1 to track2 }
    }
    return trackPairs.flatMap { (track1, track2) ->
        val closestPointAsIntersection = getClosestPointAsIntersection(track1, track2, desiredLocation)

        // Take two closest intersections instead of one because there might
        // be two points very close to each other and it is cheap to
        // calculate additional suggested switch and then select the best one.
        val actualIntersections = findClosestIntersections(track1, track2, desiredLocation, 2)
        val allIntersections = actualIntersections + listOfNotNull(closestPointAsIntersection)
        allIntersections
    }
}

fun findFarthestJoint(
    switchStructure: SwitchStructure,
    joint: SwitchJoint,
    switchAlignment: SwitchAlignment,
): SwitchJoint {
    val jointNumber = requireNotNull(switchAlignment.jointNumbers.maxByOrNull { jointNumber ->
        lineLength(joint.location, switchStructure.getJointLocation(jointNumber))
    }) { "Cannot find farthest joint: joints=${switchAlignment.jointNumbers}" }
    return switchStructure.getJoint(jointNumber)
}

private data class SwitchPointSeekResult(
    val fixPoint: AlignmentPoint,
    val pointBackwards: AlignmentPoint?,
    val pointForwards: AlignmentPoint?,
)

private fun findPointsOnTrack(from: IPoint, distance: Double, alignment: IAlignment): SwitchPointSeekResult? {
    val snapDistance = 0.1
    return alignment.getClosestPointM(from)?.let { (mValue, state) ->
        if (state == IntersectType.WITHIN) alignment.getPointAtM(mValue, snapDistance)?.let { p -> mValue to p }
        else null
    }?.let { (mValue, pointOnTrack) ->
        SwitchPointSeekResult(
            fixPoint = pointOnTrack,
            pointBackwards = alignment.getPointAtM(mValue - distance, snapDistance),
            pointForwards = alignment.getPointAtM(mValue + distance, snapDistance),
        )
    }
}

fun findTransformations(
    point: IPoint, alignment: IAlignment, switchAlignment: SwitchAlignment, joint: SwitchJoint,
    switchStructure: SwitchStructure,
): List<SwitchPositionTransformation> {
    val farthestJoint = findFarthestJoint(switchStructure, joint, switchAlignment)
    val jointDistance = lineLength(joint.location, farthestJoint.location)
    val pointsOnTrack = findPointsOnTrack(point, jointDistance, alignment)
    return listOfNotNull(
        pointsOnTrack?.pointBackwards?.let { back -> pointsOnTrack.fixPoint to back },
        pointsOnTrack?.pointForwards?.let { forward -> pointsOnTrack.fixPoint to forward },
    ).mapNotNull { (from, to) ->
        val testJoints = listOf(
            joint.copy(location = from.toPoint()),
            farthestJoint.copy(location = to.toPoint()),
        )
        calculateSwitchLocationDeltaOrNull(testJoints, switchStructure)
    }
}

fun findTransformations(
    point: IPoint,
    alignment1: IAlignment,
    alignment2: IAlignment,
    switchAlignment1: SwitchAlignment,
    switchAlignment2: SwitchAlignment,
    joint: SwitchJoint,
    switchStructure: SwitchStructure,
): List<SwitchPositionTransformation> {
    return findTransformations(point, alignment1, switchAlignment1, joint, switchStructure) + findTransformations(
        point, alignment1, switchAlignment2, joint, switchStructure
    ) + findTransformations(point, alignment2, switchAlignment1, joint, switchStructure) + findTransformations(
        point, alignment2, switchAlignment2, joint, switchStructure
    )
}

fun fitSwitch(
    transformation: SwitchPositionTransformation,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switchStructure: SwitchStructure,
): FittedSwitch {
    val jointsInLayoutSpace = switchStructure.joints.map { joint ->
        joint.copy(
            location = transformSwitchPoint(transformation, joint.location)
        )
    }

    return fitSwitch(jointsInLayoutSpace = jointsInLayoutSpace,
        switchStructure = switchStructure,
        alignments = tracks,
        geometrySwitch = null,
        geometryPlanId = null,
        alignmentEndPoint = null,
        getMeasurementMethod = { MeasurementMethod.DIGITIZED_AERIAL_IMAGE })
}

fun getSharedSwitchJoint(switchStructure: SwitchStructure): Pair<SwitchJoint, List<SwitchAlignment>> {
    val sortedSwitchJoints = switchStructure.joints.sortedWith { jointA, jointB ->
        when {
            jointA.number == switchStructure.presentationJointNumber -> -1
            jointB.number == switchStructure.presentationJointNumber -> 1
            else -> 0
        }
    }

    val (sharedSwitchJoint, switchAlignmentsContainingCommonJoint) = requireNotNull(
        sortedSwitchJoints.firstNotNullOfOrNull { joint ->
            val alignmentsContainingJoint = switchStructure.alignments.filter { alignment ->
                alignment.jointNumbers.contains(joint.number)
            }
            if (alignmentsContainingJoint.size >= 2) joint to alignmentsContainingJoint
            else null
        }
    ) { "Switch structure ${switchStructure.type} does not contain shared switch joint and that is weird!" }

    return sharedSwitchJoint to switchAlignmentsContainingCommonJoint
}

fun getSuggestedSwitchScore(
    switchSuggestion: FittedSwitch,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchJoint,
    maxFarthestJointDistance: Double,
    desiredLocation: IPoint,
): Double {
    val alignmentJointNumbers = switchStructure.alignmentJoints.map { joint -> joint.number }
    val suggestedAlignmentJoints =
        switchSuggestion.joints.filter { joint -> alignmentJointNumbers.contains(joint.number) }
    return suggestedAlignmentJoints.sumOf { joint ->
        // Select best of each joint
        val jointScore = (joint.matches.maxOfOrNull { match ->
            // Smaller the match distance, better the score
            max(1.0 - match.distanceToAlignment, 0.0)
        } ?: 0.0)
        val farthestJointExtraScore = if (joint.number == farthestJoint.number && maxFarthestJointDistance > 0) {
            val distanceToFarthestJoint = lineLength(desiredLocation, joint.location)
            val maxExtraScore = 0.5
            val extraScore = (distanceToFarthestJoint / maxFarthestJointDistance) * maxExtraScore
            extraScore
        } else 0.0
        jointScore + farthestJointExtraScore
    }
}

fun selectBestSuggestedSwitch(
    switchSuggestions: List<FittedSwitch>,
    switchStructure: SwitchStructure,
    farthestJoint: SwitchJoint,
    desiredLocation: IPoint,
): FittedSwitch? {
    if (switchSuggestions.isEmpty()) {
        return null
    }

    val maxFarthestJointDistance = switchSuggestions.maxOf { suggestedSwitch ->
        suggestedSwitch.joints.maxOf { joint ->
            if (joint.number == farthestJoint.number) {
                lineLength(desiredLocation, joint.location)
            } else 0.0
        }
    }

    return switchSuggestions.maxBy { suggestedSwitch ->
        getSuggestedSwitchScore(
            suggestedSwitch, switchStructure, farthestJoint, maxFarthestJointDistance, desiredLocation
        )
    }
}

fun createSuggestedSwitchByPoint(
    point: IPoint,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): FittedSwitch? {
    val bboxSize = max(switchStructure.bbox.width, switchStructure.bbox.height) * 2.25
    val bbox = BoundingBox(0.0..bboxSize, 0.0..bboxSize).centerAt(point)
    val croppedTracks = nearbyLocationTracks.map { (_, alignment) -> cropPoints(alignment, bbox) }

    val intersections = findTrackIntersections(croppedTracks, point)
    val (sharedSwitchJoint, switchAlignmentsContainingSharedJoint) = getSharedSwitchJoint(switchStructure)

    val suggestedSwitches = intersections.flatMap { intersection ->
        val transformations = findTransformations(
            intersection.point,
            intersection.alignment1,
            intersection.alignment2,
            switchAlignmentsContainingSharedJoint[0],
            switchAlignmentsContainingSharedJoint[1],
            sharedSwitchJoint,
            switchStructure
        )
        val suggestedSwitches = transformations.parallelStream().map { transformation ->
            fitSwitch(transformation, nearbyLocationTracks, switchStructure)
        }.collect(Collectors.toList())
        suggestedSwitches
    }

    val farthestJoint = findFarthestJoint(switchStructure, sharedSwitchJoint, switchAlignmentsContainingSharedJoint[0])
    return selectBestSuggestedSwitch(suggestedSwitches, switchStructure, farthestJoint, point)
}

private data class TrackIntersection(
    val point: IPoint,
    val distance: Double,
    val alignment1: IAlignment,
    val alignment2: IAlignment,
    val desiredLocation: IPoint,
) : Comparable<TrackIntersection> {
    private val distanceToDesiredLocation by lazy { lineLength(point, desiredLocation) }

    override fun compareTo(other: TrackIntersection): Int {
        return when {
            distance < other.distance -> -1
            distance > other.distance -> 1
            else -> when {
                distanceToDesiredLocation < other.distanceToDesiredLocation -> -1
                distanceToDesiredLocation > other.distanceToDesiredLocation -> 1
                else -> 0
            }
        }
    }
}

/**
 * Returns a copy of the alignment filtering out points that do not locate
 * in the given bounding box.
 */
fun cropPoints(alignment: LayoutAlignment, bbox: BoundingBox): IAlignment {
    val filteredSegments = alignment.segments.mapNotNull { segment ->
        if (bbox.intersects(segment.boundingBox)) {
            val firstMatchingPointIndex = segment.segmentPoints.indexOfFirst(bbox::contains)
            val lastMatchingPointIndex = segment.segmentPoints.indexOfLast(bbox::contains)
            if (firstMatchingPointIndex in 0 until lastMatchingPointIndex) {
                segment.slice(firstMatchingPointIndex, lastMatchingPointIndex)
            } else null
        } else null
    }
    return CroppedAlignment(filteredSegments, alignment.id)
}

private data class CroppedAlignment(
    override val segments: List<ISegment>,
    override val id: DomainId<*>,
) : IAlignment {
    override val boundingBox: BoundingBox? by lazy {
        boundingBoxCombining(segments.mapNotNull(ISegment::boundingBox))
    }

    override fun toLog(): String = logFormat("id" to id, "segments" to segments.map(ISegment::id))
}
