package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.validateSwitchLocationTrackLinkStructure
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.stream.Collectors
import kotlin.math.max

private const val TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT = 0.5
private const val TOLERANCE_JOINT_LOCATION_NEW_POINT = 0.01
private const val TOLERANCE_JOINT_LOCATION_SAME_POINT = 0.001

private const val MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS = 5.0

private val temporarySwitchId: IntId<TrackLayoutSwitch> = IntId(-1)

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
): List<SuggestedSwitchJointMatch> {
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
            //Check if segment's start point is within tolerance
            if (startMatches) SuggestedSwitchJointMatch(
                locationTrackId = locationTrack.id,
                layoutSwitchId = segment.switchId,
                m = segment.startM,
                matchType = SuggestedSwitchJointMatchType.START,
                switchJoint = joint,
                distance = lineLength(segment.segmentStart, jointLocation),
                distanceToAlignment = jointDistanceToAlignment,
                alignmentId = locationTrack.alignmentVersion?.id,
                segmentIndex = segmentIndex,
            ) else null,

            //Check if segment's end point is within tolerance
            if (endMatches) SuggestedSwitchJointMatch(
                locationTrackId = locationTrack.id,
                layoutSwitchId = segment.switchId,
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
                    SuggestedSwitchJointMatch(
                        locationTrackId = locationTrack.id,
                        layoutSwitchId = segment.switchId,
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
): List<SuggestedSwitchJointMatch> {
    return joints.flatMap { joint ->
        findSuggestedSwitchJointMatches(
            joint = joint,
            locationTrack = locationTrackAlignment.first,
            alignment = locationTrackAlignment.second,
            tolerance = tolerance,
        )
    }
}

fun createSuggestedSwitch(
    jointsInLayoutSpace: List<SwitchJoint>,
    switchStructure: SwitchStructure,
    alignments: List<Pair<LocationTrack, LayoutAlignment>>,
    alignmentEndPoint: LocationTrackEndpoint?,
    geometrySwitch: GeometrySwitch? = null,
    geometryPlanId: IntId<GeometryPlan>? = null,
    getMeasurementMethod: (switchId: IntId<GeometrySwitch>) -> MeasurementMethod?,
): SuggestedSwitch {
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
        .associateWith { emptyList<SuggestedSwitchJointMatch>() }

    val suggestedJoints = (matchByJoint + jointsWithoutMatches).map { (joint, matches) ->
        val locationAccuracy = switchJointLocationAccuracy(getMeasurementMethod, geometrySwitch)
        SuggestedSwitchJoint(
            number = joint.number,
            location = joint.location,
            matches = matches,
            locationAccuracy = locationAccuracy,
        )
    }

    return SuggestedSwitch(
        name = geometrySwitch?.name ?: SwitchName(switchStructure.baseType.name),
        switchStructure = switchStructure,
        joints = suggestedJoints,
        geometrySwitchId = geometrySwitch?.id?.let { id -> if (id is IntId) id else null },
        alignmentEndPoint = alignmentEndPoint,
        geometryPlanId = geometryPlanId,
    )
}

private fun getBestMatchByJoint(
    matchesByLocationTrack: Map<LocationTrack, List<SuggestedSwitchJointMatch>>,
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
    jointMatches: List<SuggestedSwitchJointMatch>,
    isFirstJoint: Boolean,
    isLastJoint: Boolean,
): List<SuggestedSwitchJointMatch> {
    return if (isFirstJoint) {
        //First joint should never match with the last point of a segment
        jointMatches
            .filter { it.matchType == SuggestedSwitchJointMatchType.START }
            .ifEmpty { jointMatches.filterNot { it.matchType == SuggestedSwitchJointMatchType.END } }
    } else if (isLastJoint) {
        //Last joint should never match with the first point of a segment
        jointMatches
            .filter { it.matchType == SuggestedSwitchJointMatchType.END }
            .ifEmpty { jointMatches.filterNot { it.matchType == SuggestedSwitchJointMatchType.START } }
    } else {
        //Prefer end points over "normal" ones
        jointMatches
            .filter { it.matchType == SuggestedSwitchJointMatchType.START || it.matchType == SuggestedSwitchJointMatchType.END }
            .ifEmpty { jointMatches }
    }
}

private fun getEndJoints(matchesByLocationTrack: Map<LocationTrack, List<SuggestedSwitchJointMatch>>) =
    matchesByLocationTrack.mapValues { (_, joints) ->
        val jointsSortedByMatchLength = joints.sortedWith(compareBy(SuggestedSwitchJointMatch::m))
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
    //Find the "start" point for switch joint
    //It's usually the first or the last point of alignment
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
    suggestedSwitch: SuggestedSwitch,
): Boolean {
    return alignmentMappings.all { mappingToCheck ->
        val switchAlignmentToCheck = suggestedSwitch.switchStructure.getAlignment(mappingToCheck.switchAlignmentId)
        switchAlignmentToCheck.jointNumbers.all { jointNumberToCheck ->
            suggestedSwitch.joints.any { suggestedSwitchJoint ->
                suggestedSwitchJoint.number == jointNumberToCheck && suggestedSwitchJoint.matches.any { match ->
                    match.locationTrackId == mappingToCheck.locationTrackId
                }
            }
        }
    }
}

fun createSuggestedSwitch(
    locationTrackEndpoint: LocationTrackEndpoint,
    switchStructure: SwitchStructure,
    alignmentMappings: List<SuggestedSwitchCreateParamsAlignmentMapping>,
    nearbyAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
    alignmentById: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    getMeasurementMethod: (switchId: IntId<GeometrySwitch>) -> MeasurementMethod?,
): SuggestedSwitch? {
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

            val suggestedSwitch = createSuggestedSwitch(
                jointsInLayoutSpace = jointsInLayoutSpace,
                switchStructure = switchStructure,
                alignments = alignments,
                geometrySwitch = null,
                geometryPlanId = null,
                alignmentEndPoint = locationTrackEndpoint,
                getMeasurementMethod = getMeasurementMethod
            )

            return if (hasAllMappedAlignments(alignmentMappings, suggestedSwitch)) suggestedSwitch
            else null
        }
    }

    return null
}

fun updateAlignmentSegmentsWithSwitchLinking(
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    matchingJoints: List<SwitchLinkingJoint>,
): LayoutAlignment {
    val segmentIndexRange = matchingJoints.flatMap { joint -> joint.segments }.let { segments ->
        val min = segments.minOf { segment -> segment.segmentIndex }
        val max = segments.maxOf { segment -> segment.segmentIndex }
        min..max
    }

    val overriddenSwitches = alignment.segments.mapIndexedNotNull { index, segment ->
        if (index in segmentIndexRange) segment.switchId
        else null
    }.distinct()

    val segmentsWithNewSwitch = alignment.segments.map { segment ->
        if (overriddenSwitches.contains(segment.switchId)) segment.withoutSwitch()
        else segment
    }.mapIndexed { index, segment ->
        if (index in segmentIndexRange) {
            val switchLinkingJoints = matchingJoints
                .filter { joint -> joint.segments.any { segment -> segment.segmentIndex == index } }
                .onEach { joint ->
                    check(joint.segments.size == 1) {
                        "Switch joint has multiple segment matches. Only one is allowed: $joint"
                    }
                }
                // getSegmentsByLinkingJoints expects the linking joints to be in track address order, and we
                // couldn't sort them earlier due to the possibility of duplicate tracks going in the opposite
                // direction
                .sortedWith(compareBy { linkingJoint -> linkingJoint.segments.first().m })

            if (switchLinkingJoints.isEmpty()) {
                //Segment that is between two other segments that are linked to the switch joints
                listOf(segment.copy(switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null))
            } else {
                getSegmentsByLinkingJoints(
                    switchLinkingJoints,
                    segment,
                    layoutSwitchId,
                    index == segmentIndexRange.first,
                    index == segmentIndexRange.last
                )
            }
        } else {
            listOf(segment)
        }
    }

    return alignment.withSegments(combineAdjacentSegmentJointNumbers(segmentsWithNewSwitch, layoutSwitchId))
}

private fun filterMatchingJointsBySwitchAlignment(
    switchStructure: SwitchStructure,
    matchingJoints: List<SwitchLinkingJoint>,
    locationTrackId: DomainId<LocationTrack>,
): List<SwitchLinkingJoint> {
    val locationTrackSwitchJoints = matchingJoints.map { joint ->
        joint.copy(segments = joint.segments.filter { segment -> segment.locationTrackId == locationTrackId })
    }.filter { it.segments.isNotEmpty() }

    val switchStructureJointNumbers = switchStructure.alignments.firstOrNull { alignment ->
        val frontJoint = alignment.jointNumbers.first()
        val backJoint = alignment.jointNumbers.last()
        val presentationJoint = switchStructure.presentationJointNumber
        val hasFrontJoint = locationTrackSwitchJoints.any { joint -> joint.jointNumber == frontJoint }
        val hasBackJoint = locationTrackSwitchJoints.any { joint -> joint.jointNumber == backJoint }
        val hasSeparatePresentationJoint =
            presentationJoint != frontJoint && presentationJoint != backJoint && alignment.jointNumbers.any { jointNumber -> jointNumber == presentationJoint } && locationTrackSwitchJoints.any { joint -> joint.jointNumber == presentationJoint }

        // Alignment must contain at least two of these ("etujatkos", "takajatkos", presentation joint)
        listOf(hasFrontJoint, hasBackJoint, hasSeparatePresentationJoint).count { it } >= 2
    }?.jointNumbers

    return locationTrackSwitchJoints.filter { joint ->
        switchStructureJointNumbers?.any { structureJoint -> structureJoint == joint.jointNumber } ?: false
    }
}

private fun getSegmentsByLinkingJoints(
    linkingJoints: List<SwitchLinkingJoint>,
    segment: LayoutSegment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    isFirstSegment: Boolean,
    isLastSegment: Boolean,
) = linkingJoints.foldIndexed(mutableListOf<LayoutSegment>()) { index, acc, linkingJoint ->
    val jointNumber = linkingJoint.jointNumber
    val previousSegment = acc.lastOrNull()?.also { acc.removeLast() } ?: segment
    val suggestedPointM = linkingJoint.segments.first().m

    if (isSame(segment.startM, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
        //Check if suggested point is start point
        acc.add(setStartJointNumber(segment, layoutSwitchId, jointNumber))
    } else if (isSame(segment.endM, suggestedPointM, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
        //Check if suggested point is end point
        if (linkingJoints.size == 1) {
            acc.add(setEndJointNumber(previousSegment, layoutSwitchId, jointNumber))
        } else {
            acc.add(previousSegment.copy(endJointNumber = jointNumber))
        }
    } else {
        //Otherwise split the segment
        //StartSplitSegment: before M-value
        //EndSplitSegment: after M-value
        val (startSplitSegment, endSplitSegment) = previousSegment.splitAtM(
            suggestedPointM, TOLERANCE_JOINT_LOCATION_NEW_POINT
        )

        //Handle cases differently when there are multiple joint matches in a single segment
        if (linkingJoints.size == 1) {
            acc.add(
                if (isFirstSegment) startSplitSegment.withoutSwitch()
                else if (isLastSegment) setEndJointNumber(startSplitSegment, layoutSwitchId, jointNumber)
                else startSplitSegment.copy(
                    switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null
                )
            )
            endSplitSegment?.let {
                acc.add(
                    if (isFirstSegment) setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                    else if (isLastSegment) endSplitSegment.withoutSwitch()
                    else setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                )
            }
        } else {
            when (index) {
                //First joint match
                0 -> {
                    acc.add(
                        if (isFirstSegment) startSplitSegment.withoutSwitch()
                        else startSplitSegment.copy(
                            switchId = layoutSwitchId,
                            startJointNumber = null,
                            endJointNumber = null,
                        )
                    )

                    endSplitSegment?.let {
                        acc.add(setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber))
                    }
                }
                //Last joint match
                linkingJoints.lastIndex -> {
                    acc.add(startSplitSegment.copy(endJointNumber = jointNumber))

                    endSplitSegment?.let {
                        acc.add(
                            if (isLastSegment) endSplitSegment.withoutSwitch()
                            else endSplitSegment.copy(
                                switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null
                            )
                        )
                    }
                }

                else -> {
                    acc.add(startSplitSegment.copy(endJointNumber = jointNumber))
                    endSplitSegment?.let {
                        acc.add(setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber))
                    }
                }
            }
        }
    }

    acc
}.toList()

private fun setStartJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = jointNumber, endJointNumber = null)

private fun setEndJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = null, endJointNumber = jointNumber)

private fun combineAdjacentSegmentJointNumbers(
    layoutSegments: List<List<LayoutSegment>>,
    switchId: IntId<TrackLayoutSwitch>,
) = layoutSegments.fold(mutableListOf<LayoutSegment>()) { acc, segments ->
    val currentSegment = segments.first()
    val previousSegment = acc.lastOrNull()

    /**
     * For instance in case of line 1-5-2
     *      J1      J5      J2
     * -----|-------|-------|------
     * S0      S1      S2     S3
     * where the first switch segment S1 has start joint number 1,
     * and the last switch segment S2 has start joint number 5 and end joint number 2
     * we want the S1 to have end joint number 5
     */
    if (currentSegment.switchId == switchId && previousSegment?.switchId == switchId) {
        if (previousSegment.startJointNumber != null && previousSegment.endJointNumber == null && currentSegment.startJointNumber != null) {
            acc[acc.lastIndex] = previousSegment.copy(endJointNumber = currentSegment.startJointNumber)
            acc.addAll(segments)
            return@fold acc
        } else if (previousSegment.endJointNumber != null && currentSegment.startJointNumber == null && currentSegment.endJointNumber != null) {
            acc.add(currentSegment.copy(startJointNumber = previousSegment.endJointNumber))
            acc.addAll(segments.drop(1))
            return@fold acc
        }
    }

    acc.addAll(segments)
    acc
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

private fun getClosestPointAsIntersection(
    track1: IAlignment,
    track2:IAlignment,
    desiredLocation: IPoint,
): TrackIntersection? {
    return track1.getClosestPoint(desiredLocation)?.let { (closestPoint, _) ->
        TrackIntersection(
            alignment1 = track1,
            alignment2 = track2,
            point = closestPoint,
            distance = 0.0,
            desiredLocation = desiredLocation,
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

fun createSuggestedSwitch(
    transformation: SwitchPositionTransformation,
    tracks: List<Pair<LocationTrack, LayoutAlignment>>,
    switchStructure: SwitchStructure,
): SuggestedSwitch {
    val jointsInLayoutSpace = switchStructure.joints.map { joint ->
        joint.copy(
            location = transformSwitchPoint(transformation, joint.location)
        )
    }

    return createSuggestedSwitch(jointsInLayoutSpace = jointsInLayoutSpace,
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
    suggestedSwitch: SuggestedSwitch,
    farthestJoint: SwitchJoint,
    maxFarthestJointDistance: Double,
    desiredLocation: IPoint,
): Double {
    return suggestedSwitch.joints.sumOf { joint ->
        // Select best of each joint
        (joint.matches.maxOfOrNull { match ->
            // Smaller the match distance, better the score
            max(1.0 - match.distanceToAlignment, 0.0)
        } ?: 0.0) + if (joint.number == farthestJoint.number && maxFarthestJointDistance > 0) {
            val distanceToFarthestJoint = lineLength(desiredLocation, joint.location)
            val maxExtraScore = 0.5
            val extraScore = (distanceToFarthestJoint / maxFarthestJointDistance) * maxExtraScore
            extraScore
        } else 0.0
    }
}

fun selectBestSuggestedSwitch(
    suggestedSwitches: List<SuggestedSwitch>,
    farthestJoint: SwitchJoint,
    desiredLocation: IPoint,
): SuggestedSwitch? {
    if (suggestedSwitches.isEmpty()) {
        return null
    }

    val maxFarthestJointDistance = suggestedSwitches.maxOf { suggestedSwitch ->
        suggestedSwitch.joints.maxOf { joint ->
            if (joint.number == farthestJoint.number) {
                lineLength(desiredLocation, joint.location)
            } else 0.0
        }
    }

    return suggestedSwitches.maxBy { suggestedSwitch ->
        getSuggestedSwitchScore(
            suggestedSwitch, farthestJoint, maxFarthestJointDistance, desiredLocation
        )
    }
}

fun createSuggestedSwitchByPoint(
    point: IPoint,
    switchStructure: SwitchStructure,
    nearbyLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): SuggestedSwitch? {
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
        val suggestedSwitches = transformations.map { transformation ->
            createSuggestedSwitch(transformation, nearbyLocationTracks, switchStructure)
        }
        suggestedSwitches
    }

    val farthestJoint = findFarthestJoint(switchStructure, sharedSwitchJoint, switchAlignmentsContainingSharedJoint[0])
    return selectBestSuggestedSwitch(suggestedSwitches, farthestJoint, point)
}

@Service
class SwitchLinkingService @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val linkingDao: LinkingDao,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
    private val coordinateTransformationService: CoordinateTransformationService,
    private val switchDao: LayoutSwitchDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geocodingService: GeocodingService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun getSuggestedSwitches(bbox: BoundingBox): List<SuggestedSwitch> {
        logger.serviceCall("getSuggestedSwitches", "bbox" to bbox)
        val missing = linkingDao.getMissingLayoutSwitchLinkings(bbox)
        val result = missing.mapNotNull { missingLayoutSwitchLinking ->
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
                val nearAlignmentIds = locationTrackDao.fetchVersionsNear(DRAFT, switchBoundingBox)

                val alignments = (nearAlignmentIds + missingLayoutSwitchLinking.locationTrackIds)
                    .distinct()
                    .map { id -> locationTrackService.getWithAlignment(id) }

                createSuggestedSwitch(
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
        return result
    }

    @Transactional(readOnly = true)
    fun getSuggestedSwitches(points: List<Pair<IPoint, IntId<SwitchStructure>>>): List<SuggestedSwitch?> {
        logger.serviceCall("getSuggestedSwitches", "points" to points)
        return points.map { (location, switchStructureId) ->
            Triple(
                location,
                switchLibraryService.getSwitchStructure(switchStructureId),
                locationTrackService.getLocationTracksNear(location, DRAFT)
            )
        }.parallelStream().map { (location, switchStructure, nearbyLocationTracks) ->
            createSuggestedSwitchByPoint(
                location, switchStructure, nearbyLocationTracks
            )
        }.collect(Collectors.toList()).map(::adjustSuggestedSwitchForNearbyOverlaps)
    }

    private fun adjustSuggestedSwitchForNearbyOverlaps(suggestedSwitch: SuggestedSwitch?) = suggestedSwitch
        ?.let(::createSwitchLinkingParameters)
        ?.let(::calculateModifiedLocationTracksForSegmentLinks)
        ?.let(::assignNewSwitchLinkingToLocationTracksAndAlignments)
        ?.asSequence()
        ?.filter(::locationTrackHasTemporaryTopologicalSwitchConnection)
        ?.mapNotNull(::topologicalConnectionJointNumberToLocationTrackId)
        ?.groupBy(
            { (jointNumber, _) -> jointNumber },
            { (_, locationTrackId) -> locationTrackId },
        )
        ?.map { (jointNumber, locationTrackIds) ->
            TopologicalJointConnection(jointNumber, locationTrackIds)
        }
        ?.sortedBy { temporaryTopologicalJointConnection ->
            temporaryTopologicalJointConnection.jointNumber
        }
        ?.let { temporaryTopologicalJointConnections ->
            suggestedSwitch.copy(
                topologicalJointConnections = temporaryTopologicalJointConnections
            )
        } ?: suggestedSwitch

    fun getSuggestedSwitch(location: IPoint, switchStructureId: IntId<SwitchStructure>): SuggestedSwitch? =
        getSuggestedSwitches(listOf(location to switchStructureId))[0]

    private fun assignNewSwitchLinkingToLocationTracksAndAlignments(
        locationTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
        switchId: IntId<TrackLayoutSwitch> = temporarySwitchId
    ): List<LocationTrack> {
        // It is unnecessary to get the original switch bounds as well, as the new switch
        // is not linked to anywhere beforehand.
        val updatedArea = getSwitchBoundsFromTracks(
            locationTracksAndAlignments,
            switchId,
        )

        val nearbyTracks = (locationTracksAndAlignments + listDraftTracksNearArea(updatedArea))

        return nearbyTracks
            .distinctBy { t -> t.first.id }
            .map { (locationTrack, alignment) ->
                locationTrackService.calculateLocationTrackTopology(
                    track = locationTrack,
                    alignment = alignment,
                    nearbyTracks = NearbyTracks(
                        aroundStart = nearbyTracks,
                        aroundEnd = nearbyTracks,
                    ),
                )
            }
    }

    @Transactional(readOnly = true)
    fun getSuggestedSwitch(createParams: SuggestedSwitchCreateParams): SuggestedSwitch? {
        logger.serviceCall("getSuggestedSwitch", "createParams" to createParams)

        val switchStructure =
            createParams.switchStructureId?.let(switchLibraryService::getSwitchStructure) ?: return null
        val locationTracks = createParams.alignmentMappings
            .map { mapping -> mapping.locationTrackId }
            .associateWith { id -> locationTrackService.getWithAlignmentOrThrow(DRAFT, id) }

        val areaSize = switchStructure.bbox.width.coerceAtLeast(switchStructure.bbox.height) * 2.0
        val switchAreaBbox = BoundingBox(
            Point(0.0, 0.0), Point(areaSize, areaSize)
        ).centerAt(createParams.locationTrackEndpoint.location)
        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(DRAFT, switchAreaBbox)

        return createSuggestedSwitch(
            createParams.locationTrackEndpoint,
            switchStructure,
            createParams.alignmentMappings,
            nearbyLocationTracks,
            locationTracks,
            getMeasurementMethod = this::getMeasurementMethod,
        )
    }

    @Transactional(readOnly = true)
    fun getSuggestedSwitchesAtPresentationJointLocations(switches: List<TrackLayoutSwitch>): List<Pair<DomainId<TrackLayoutSwitch>, SuggestedSwitch?>> {
        logger.serviceCall("getSuggestedSwitchesAtPresentationJointLocations", "switches" to switches.map(TrackLayoutSwitch::toLog))
        val switchSuggestionCalculationData = switches.map { switch ->
            val location = switchService.getPresentationJointOrThrow(switch).location
            val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
            val locationTracks = locationTrackService.getLocationTracksNear(location, DRAFT)

            Pair(switch.id, Triple(location, structure, locationTracks))
        }

        return switchSuggestionCalculationData.parallelStream().map { (switchId, triple) ->
            val (location, structure, locationTracks) = triple
            createSuggestedSwitchByPoint(location, structure, locationTracks)?.let { switchSuggestion ->
                switchId to switchSuggestion
            }
        }.collect(Collectors.toList())
    }

    @Transactional
    fun saveSwitchLinking(linkingParameters: SwitchLinkingParameters): DaoResponse<TrackLayoutSwitch> {
        logger.serviceCall("saveSwitchLinking", "linkingParameters" to linkingParameters)
        linkingParameters.geometryPlanId?.let(::verifyPlanNotHidden)
        val changes = getLocationTrackChangesFromLinkingSwitch(linkingParameters)
        changes.onlyDelinked.forEach { (track, alignment) -> locationTrackService.saveDraft(track, alignment) }
        changes.onlyTopoLinkEdited.forEach { track -> locationTrackService.saveDraft(track) }
        changes.alignmentLinkEdited.forEach { (track, alignment) -> locationTrackService.saveDraft(track, alignment) }
        return updateLayoutSwitch(linkingParameters)
    }

    private fun getLocationTrackChangesFromLinkingSwitch(
        linkingParameters: SwitchLinkingParameters,
    ): LocationTrackChangesFromLinkingSwitch {
        val originalTracks = switchDao
            .findLocationTracksLinkedToSwitch(DRAFT, linkingParameters.layoutSwitchId)
            .associateBy({ ids -> ids.rowVersion.id }, { ids ->
                locationTrackService.getWithAlignment(ids.rowVersion)
            })

        val existingLinksCleared = originalTracks.mapValues { (_, trackAndAlignment) ->
            val (track, alignment) = trackAndAlignment
            clearLinksToSwitch(track, alignment, linkingParameters.layoutSwitchId)
        }

        val segmentLinksMade = calculateModifiedLocationTracksForSegmentLinks(linkingParameters, existingLinksCleared)
        val segmentLinksMadeOverlay = segmentLinksMade.associateBy { (track) -> track.id as IntId }

        val topologicalLinksMade = getPotentiallyChangedTracks(
            originalTracks,
            linkingParameters.layoutSwitchId,
            segmentLinksMadeOverlay,
        ).mapNotNull { (locationTrack, alignment) ->
            val updated = locationTrackService.fetchNearbyTracksAndCalculateLocationTrackTopology(
                locationTrack,
                alignment,
                overlaidTracks = segmentLinksMadeOverlay,
            )
            if (updated != locationTrack) updated else null
        }
        val topoLinksMadeIds = topologicalLinksMade.map { track -> track.id as IntId }.toSet()
        val onlyDelinked = existingLinksCleared.entries
            .filter { (id) -> !segmentLinksMadeOverlay.containsKey(id) && !topoLinksMadeIds.contains(id) }
            .map { (_, track) -> track }
        return LocationTrackChangesFromLinkingSwitch(
            onlyDelinked,
            topologicalLinksMade,
            segmentLinksMade.filter { (track) -> !topoLinksMadeIds.contains(track.id) },
        )
    }

    private fun getPotentiallyChangedTracks(
        originalTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
        switchId: IntId<TrackLayoutSwitch>,
        segmentLinksMadeOverlay: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        val originalArea = getSwitchBoundsFromTracks(originalTracks.values, switchId)
        val updatedArea = getSwitchBoundsFromTracks(segmentLinksMadeOverlay.values, switchId)
        return (listDraftTracksNearArea(originalArea) + listDraftTracksNearArea(updatedArea))
            .distinctBy { t -> t.first.id }
            .map { t -> segmentLinksMadeOverlay.getOrDefault(t.first.id, t) }
    }

    @Transactional(readOnly = true)
    fun validateRelinkingTrack(trackId: IntId<LocationTrack>): List<SwitchRelinkingResult> {
        val trackVersion = locationTrackDao.fetchDraftVersionOrThrow(trackId)
        val track = trackVersion.let(locationTrackDao::fetch)
        val switchSuggestions = getTrackSwitchSuggestions(track)
        val geocodingContext = requireNotNull(geocodingService.getGeocodingContext(OFFICIAL, track.trackNumberId)) {
            "Could not get geocoding context: trackNumber=${track.trackNumberId} track=$track"
        }
        return switchSuggestions.map { (switchId, suggestedSwitch) ->
            if (suggestedSwitch == null) SwitchRelinkingResult(switchId, null, listOf())
            else {
                val (validationResults, presentationJointLocation) = validateSwitchLinkingParametersForSplit(
                    createSwitchLinkingParameters(suggestedSwitch).copy(
                        layoutSwitchId = switchId
                    )
                )
                val address = requireNotNull(geocodingContext.getAddress(presentationJointLocation)) {
                    "Could not geocode relinked location for switch $switchId on track $track"
                }
                SwitchRelinkingResult(
                    switchId,
                    SwitchRelinkingSuggestion(presentationJointLocation, address.first),
                    validationResults,
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getTrackSwitchSuggestions(track: LocationTrack): List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch?>> {
        val alignment = requireNotNull(track.alignmentVersion) {
            "No alignment on track ${track.toLog()}"
        }.let(alignmentDao::fetch)

        val switchIds = alignment.segments.mapNotNull { it.switchId as? IntId }.distinct()
        val replacementSwitchLocations = switchIds.map { switchId ->
            val switch = switchService.getOrThrow(OFFICIAL, switchId)
            switchService.getPresentationJointOrThrow(switch).location to switch.switchStructureId
        }
        val switchSuggestions = getSuggestedSwitches(replacementSwitchLocations)
        return switchIds.mapIndexed { index, id -> id to switchSuggestions[index] }
    }

    fun validateSwitchLinkingParametersForSplit(
        linkingParameters: SwitchLinkingParameters,
    ): Pair<List<PublishValidationError>, Point> {
        val trackChanges = getLocationTrackChangesFromLinkingSwitch(linkingParameters)
        val createdSwitch = createModifiedLayoutSwitchLinking(linkingParameters)
        val presentationJointLocation = switchService.getPresentationJointOrThrow(createdSwitch).location
        val switchStructure = switchLibraryService.getSwitchStructure(linkingParameters.switchStructureId)

        return validateSwitchLocationTrackLinkStructure(
            createdSwitch,
            switchStructure,
            trackChanges.alignmentLinkEdited + trackChanges.onlyTopoLinkEdited.mapNotNull { track ->
                track.alignmentVersion?.let { track to alignmentDao.fetch(it) }
            },
        ) to presentationJointLocation
    }

    private fun listDraftTracksNearArea(area: BoundingBox?) = if (area == null) listOf()
    else locationTrackService.listNearWithAlignments(DRAFT, area.plus(1.0))

    private fun createModifiedLayoutSwitchLinking(linkingParameters: SwitchLinkingParameters): TrackLayoutSwitch {
        val layoutSwitch = switchService.getOrThrow(DRAFT, linkingParameters.layoutSwitchId)
        val newGeometrySwitchId = linkingParameters.geometrySwitchId ?: layoutSwitch.sourceId
        val newJoints = linkingParameters.joints.map { linkingJoint ->
            TrackLayoutSwitchJoint(
                number = linkingJoint.jointNumber,
                location = linkingJoint.location,
                locationAccuracy = linkingJoint.locationAccuracy
            )
        }

        return layoutSwitch.copy(
            sourceId = newGeometrySwitchId,
            joints = newJoints,
            source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
        )
    }

    private fun updateLayoutSwitch(linkingParameters: SwitchLinkingParameters): DaoResponse<TrackLayoutSwitch> {
        return createModifiedLayoutSwitchLinking(linkingParameters).let { modifiedLayoutSwitch ->
            switchService.saveDraft(modifiedLayoutSwitch)
        }
    }

    private fun calculateModifiedLocationTracksForSegmentLinks(
        linkingParameters: SwitchLinkingParameters,
        overlaidTracks: Map<IntId<LocationTrack>, Pair<LocationTrack, LayoutAlignment>> = mapOf(),
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        val switchStructure = switchLibraryService.getSwitchStructure(linkingParameters.switchStructureId)

        val switchJointsByLocationTrack = linkingParameters.joints
            .flatMap { joint -> joint.segments.map { segment -> segment.locationTrackId } }
            .distinct()
            .associateWith { locationTrackId ->
                filterMatchingJointsBySwitchAlignment(switchStructure, linkingParameters.joints, locationTrackId)
            }
            .filter { it.value.isNotEmpty() }

        return switchJointsByLocationTrack.map { (locationTrackId, switchJoints) ->
            val (locationTrack, alignment) = overlaidTracks.getOrElse(locationTrackId) {
                locationTrackService.getWithAlignmentOrThrow(DRAFT, locationTrackId)
            }

            val switchJointsWithSlightlyOverlappingSegmentsSnapped = switchJoints.map { switchLinkingJoint ->
                switchLinkingJoint.copy(
                    segments = switchLinkingJoint.segments.map { switchLinkingSegment ->
                        val referencedLayoutSegment = alignment.segments[switchLinkingSegment.segmentIndex]

                        val layoutSegmentHasExistingSwitch = referencedLayoutSegment.switchId != null
                        val layoutSegmentSwitchReferencesDifferentSwitch =
                            layoutSegmentHasExistingSwitch && referencedLayoutSegment.switchId != linkingParameters.layoutSwitchId

                        if (layoutSegmentSwitchReferencesDifferentSwitch) {
                            tryToSnapOverlappingSwitchSegmentToNearbySegment(alignment, switchLinkingSegment)
                        } else {
                            switchLinkingSegment
                        }
                    },
                )
            }

            val updatedAlignment = updateAlignmentSegmentsWithSwitchLinking(
                alignment = alignment,
                layoutSwitchId = linkingParameters.layoutSwitchId,
                matchingJoints = switchJointsWithSlightlyOverlappingSegmentsSnapped,
            )

            val locationTrackWithUpdatedTopology =
                locationTrackService.fetchNearbyTracksAndCalculateLocationTrackTopology(
                    locationTrack,
                    updatedAlignment,
                    overlaidTracks = overlaidTracks,
                )

            locationTrackWithUpdatedTopology to updatedAlignment
        }
    }

    private fun findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
        existingSwitchId: IntId<TrackLayoutSwitch>,
        layoutSegments: List<LayoutSegment>,
        searchIndexRange: IntProgression,
    ): IndexedValue<LayoutSegment>? {
        val layoutSegmentIndicesAreValid =
            searchIndexRange.first in layoutSegments.indices && searchIndexRange.last in layoutSegments.indices

        val step = searchIndexRange.step
        val firstAdjacentIndexIsValid = (searchIndexRange.first + step) in layoutSegments.indices
        val lastAdjacentIndexIsValid = (searchIndexRange.last + step) in layoutSegments.indices

        val adjacentSegmentIndicesAreValid = firstAdjacentIndexIsValid && lastAdjacentIndexIsValid

        require(layoutSegmentIndicesAreValid) {
            "Invalid searchIndexRange: $searchIndexRange contains indices outside of layoutSegments (${layoutSegments.indices})"
        }

        require(adjacentSegmentIndicesAreValid) {
            "Invalid searchIndexRange: $searchIndexRange contains adjacent indices outside of layoutSegments (${layoutSegments.indices})"
        }

        for (i in searchIndexRange) {
            val segment = layoutSegments[i]

            val existingSwitchIdMatchesSegment = existingSwitchId == segment.switchId
            if (!existingSwitchIdMatchesSegment) {
                logger.info("Expected to find switch $existingSwitchId from segment, but found ${segment.switchId} (at least one switch should be overridden from the two adjacent switches)")
                return null
            }

            val adjacentSegmentHasNoSwitch = layoutSegments[i + searchIndexRange.step].switchId == null
            if (adjacentSegmentHasNoSwitch) {
                return IndexedValue(i, segment)
            }
        }

        logger.info("Could not find the edge segment for the switch=${existingSwitchId} with a free adjacent segment, searchIndexRange was $searchIndexRange")
        return null
    }

    fun tryToSnapOverlappingSwitchSegmentToNearbySegment(
        layoutAlignment: LayoutAlignment,
        switchLinkingSegment: SwitchLinkingSegment,
    ): SwitchLinkingSegment {
        val referencedLayoutSegment = layoutAlignment.segments[switchLinkingSegment.segmentIndex]

        val segmentIsSwitchFree = referencedLayoutSegment.switchId == null
        if (segmentIsSwitchFree) {
            return switchLinkingSegment
        }

        // Snapping towards the start of the location track.
        switchLinkingSegment.segmentIndex
            .takeIf { segmentIndex -> segmentIndex > 1 }
            ?.let { segmentIndex -> IntProgression.fromClosedRange(segmentIndex, 1, -1) }
            ?.let { negativeSearchIndexDirection ->
                findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
                    referencedLayoutSegment.switchId as IntId,
                    layoutAlignment.segments,
                    searchIndexRange = negativeSearchIndexDirection,
                )
            }
            ?.let { indexedExistingSwitchStartSegment ->
                val distanceToPreviousSwitchLineStart =
                    switchLinkingSegment.m - indexedExistingSwitchStartSegment.value.startM
                val hasAdjacentLayoutSegment = indexedExistingSwitchStartSegment.index > 0

                if (hasAdjacentLayoutSegment && distanceToPreviousSwitchLineStart <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS) {
                    return switchLinkingSegment.copy(
                        m = switchLinkingSegment.m - distanceToPreviousSwitchLineStart,
                        segmentIndex = indexedExistingSwitchStartSegment.index - 1
                    )
                }
            }

        // Snapping towards the end of the location track.
        switchLinkingSegment.segmentIndex
            .takeIf { segmentIndex -> segmentIndex < layoutAlignment.segments.lastIndex - 1 }
            ?.let { segmentIndex ->
                IntProgression.fromClosedRange(segmentIndex, layoutAlignment.segments.lastIndex - 1, 1)
            }
            ?.let { positiveSearchIndexDirection ->
                findExistingSwitchEdgeSegmentWithSwitchFreeAdjacentSegment(
                    referencedLayoutSegment.switchId as IntId,
                    layoutAlignment.segments,
                    searchIndexRange = positiveSearchIndexDirection
                )
            }
            ?.let { indexedExistingSwitchEndSegment ->
                val distanceToPreviousSwitchLineEnd =
                    indexedExistingSwitchEndSegment.value.endM - switchLinkingSegment.m
                val hasAdjacentLayoutSegment =
                    indexedExistingSwitchEndSegment.index < layoutAlignment.segments.lastIndex

                if (hasAdjacentLayoutSegment && distanceToPreviousSwitchLineEnd <= MAX_SWITCH_JOINT_OVERLAP_CORRECTION_AMOUNT_METERS) {
                    return switchLinkingSegment.copy(
                        m = switchLinkingSegment.m + distanceToPreviousSwitchLineEnd,
                        segmentIndex = indexedExistingSwitchEndSegment.index + 1
                    )
                }
            }

        // Couldn't snap, possibly due to too much overlap or adjacent switch segment(s) already contained another switch.
        return switchLinkingSegment
    }

    private fun getMeasurementMethod(id: IntId<GeometrySwitch>): MeasurementMethod? =
        geometryDao.getMeasurementMethodForSwitch(id)

    private fun verifyPlanNotHidden(id: IntId<GeometryPlan>) {
        if (geometryDao.fetchPlanVersion(id).let(geometryDao::getPlanHeader).isHidden) throw LinkingFailureException(
            message = "Cannot link a plan that is hidden", localizedMessageKey = "plan-hidden"
        )
    }
}

fun createSwitchLinkingParameters(
    suggestedSwitch: SuggestedSwitch,
    layoutSwitchId: IntId<TrackLayoutSwitch> = temporarySwitchId,
): SwitchLinkingParameters {
    return SwitchLinkingParameters(
        layoutSwitchId = layoutSwitchId,
        joints = suggestedSwitch.joints.map { suggestedSwitchJoint ->
            SwitchLinkingJoint(
                jointNumber = suggestedSwitchJoint.number,
                location = suggestedSwitchJoint.location,
                segments = suggestedSwitchJoint.matches.map { switchJointMatch ->
                    SwitchLinkingSegment(
                        locationTrackId = switchJointMatch.locationTrackId as IntId,
                        segmentIndex = switchJointMatch.segmentIndex,
                        m = switchJointMatch.m,
                    )
                },
                locationAccuracy = suggestedSwitchJoint.locationAccuracy,
            )
        },
        geometrySwitchId = null,
        switchStructureId = suggestedSwitch.switchStructure.id as IntId,
    )
}

private fun locationTrackHasTemporaryTopologicalSwitchConnection(
    locationTrack: LocationTrack,
    switchId: IntId<TrackLayoutSwitch> = temporarySwitchId,
): Boolean =
    locationTrack.topologyStartSwitch?.switchId == switchId || locationTrack.topologyEndSwitch?.switchId == switchId

private fun topologicalConnectionJointNumberToLocationTrackId(
    locationTrack: LocationTrack,
    switchId: IntId<TrackLayoutSwitch> = temporarySwitchId,
): Pair<JointNumber, IntId<LocationTrack>>? {
    return if (locationTrack.topologyStartSwitch?.switchId == switchId) {
        locationTrack.topologyStartSwitch.jointNumber to locationTrack.id as IntId
    } else if (locationTrack.topologyEndSwitch?.switchId == switchId) {
        locationTrack.topologyEndSwitch.jointNumber to locationTrack.id as IntId
    } else {
        null
    }
}

fun getSwitchBoundsFromTracks(
    tracks: Collection<Pair<LocationTrack, LayoutAlignment>>,
    switchId: IntId<TrackLayoutSwitch>,
): BoundingBox? = tracks.flatMap { (track, alignment) ->
    listOfNotNull(
        track.topologyStartSwitch?.let { ts -> if (ts.switchId == switchId) alignment.firstSegmentStart else null },
        track.topologyEndSwitch?.let { ts -> if (ts.switchId == switchId) alignment.lastSegmentEnd else null }) + alignment.segments.flatMap { segment ->
        if (segment.switchId != switchId) listOf() else listOfNotNull(
            if (segment.startJointNumber != null) segment.segmentStart else null,
            if (segment.endJointNumber != null) segment.segmentEnd else null
        )
    }
}.let(::boundingBoxAroundPointsOrNull)

data class LocationTrackChangesFromLinkingSwitch(
    val onlyDelinked: List<Pair<LocationTrack, LayoutAlignment>>,
    val onlyTopoLinkEdited: List<LocationTrack>,
    val alignmentLinkEdited: List<Pair<LocationTrack, LayoutAlignment>>,
)
