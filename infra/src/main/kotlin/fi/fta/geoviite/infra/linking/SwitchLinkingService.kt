package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.geography.Transformation
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.RowVersion
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

private const val TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT = 0.5
private const val TOLERANCE_JOINT_LOCATION_NEW_POINT = 0.01
private const val TOLERANCE_JOINT_LOCATION_SAME_POINT = 0.001

fun calculateLayoutSwitchJoints(
    geomSwitch: GeometrySwitch,
    switchStructure: SwitchStructure,
    planSrid: Srid,
): List<SwitchJoint>? {
    val fromPlanToLayoutTransformation = Transformation(planSrid, LAYOUT_SRID)

    val layoutJointPoints = geomSwitch.joints.map { geomJoint ->
        SwitchJoint(
            number = geomJoint.number,
            location = fromPlanToLayoutTransformation.transform(geomJoint.location),
        )
    }
    val switchLocationDelta = calculateSwitchLocationDeltaOrNull(layoutJointPoints, switchStructure)
    return if (switchLocationDelta != null) {
        switchStructure.alignmentJoints.map { joint ->
            SwitchJoint(
                number = joint.number,
                location = transformSwitchPoint(switchLocationDelta, joint.location)
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

    val closestSegmentIndex = alignment.findClosestSegmentIndex(jointLocation)
        ?: return listOf()
    val possibleSegmentIndices = IntRange(
        (closestSegmentIndex - 1).coerceAtLeast(0),
        (closestSegmentIndex + 1).coerceAtMost(alignment.segments.lastIndex)
    )

    return alignment.segments
        .slice(possibleSegmentIndices)
        .flatMapIndexed { index, segment ->
            val segmentIndex = possibleSegmentIndices.first + index

            val startPoint = segment.points.first()
            val endPoint = segment.points.last()

            val startMatches = startPoint.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)
            val endMatches = endPoint.isSame(jointLocation, TOLERANCE_JOINT_LOCATION_SEGMENT_END_POINT)

            listOfNotNull(
                //Check if segment's start point is within tolerance
                if (startMatches) {
                    SuggestedSwitchJointMatch(
                        locationTrackId = locationTrack.id,
                        segmentIndex = segmentIndex,
                        layoutSwitchId = segment.switchId,
                        segmentM = startPoint.m,
                        matchType = SuggestedSwitchJointMatchType.START,
                        switchJoint = joint,
                        distance = lineLength(startPoint, jointLocation),
                        alignmentId = locationTrack.alignmentVersion?.id,
                    )
                } else null,

                //Check if segment's end point is within tolerance
                if (endMatches) {
                    SuggestedSwitchJointMatch(
                        locationTrackId = locationTrack.id,
                        segmentIndex = segmentIndex,
                        layoutSwitchId = segment.switchId,
                        segmentM = endPoint.m,
                        matchType = SuggestedSwitchJointMatchType.END,
                        switchJoint = joint,
                        distance = lineLength(endPoint, jointLocation),
                        alignmentId = locationTrack.alignmentVersion?.id,
                    )
                } else null
            ) + segment.points.mapIndexedNotNull { pIdx, point ->
                segment.points.getOrNull(pIdx - 1)?.let { previousPoint ->
                    val closestAlignmentPoint = closestPointOnLine(previousPoint, point, jointLocation)
                    val jointDistanceToAlignment = lineLength(closestAlignmentPoint, jointLocation)

                    if (jointDistanceToAlignment < tolerance) {
                        SuggestedSwitchJointMatch(
                            locationTrackId = locationTrack.id,
                            segmentIndex = segmentIndex,
                            layoutSwitchId = segment.switchId,
                            segmentM = segment.getLengthUntil(closestAlignmentPoint).first,
                            matchType = SuggestedSwitchJointMatchType.LINE,
                            switchJoint = joint,
                            distance = jointDistanceToAlignment,
                            alignmentId = locationTrack.alignmentVersion?.id,
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
    getMeasurementMethod: (segment: IndexedId<LayoutSegment>) -> MeasurementMethod?,
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

    val suggestedJoints = (matchByJoint + jointsWithoutMatches)
        .map { (joint, matches) ->
            val locationAccuracy = switchJointLocationAccuracy(matches, getMeasurementMethod, geometrySwitch)

            SuggestedSwitchJoint(
                number = joint.number,
                location = joint.location,
                matches = matches,
                locationAccuracy = locationAccuracy
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
    endJoints: Map<LocationTrack, Pair<SwitchJoint, SwitchJoint>>
) = matchesByLocationTrack
    .flatMap { (lt, matches) ->
        matches
            .groupBy { it.switchJoint }
            .map { (joint, jointMatches) ->
                getBestMatchesForJoint(
                    jointMatches = jointMatches,
                    isFirstJoint = endJoints[lt]?.first == joint,
                    isLastJoint = endJoints[lt]?.second == joint
                )
            }
            .mapNotNull { filteredMatches -> filteredMatches.minByOrNull { it.distance } }
    }
    .groupBy { it.switchJoint }

private fun getBestMatchesForJoint(
    jointMatches: List<SuggestedSwitchJointMatch>,
    isFirstJoint: Boolean,
    isLastJoint: Boolean
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
    matchesByLocationTrack
        .mapValues { (_, joints) ->
            val jointsSortedByMatchLength = joints.sortedWith(compareBy({ it.segmentIndex }, { it.segmentM }))
            val min = jointsSortedByMatchLength.first().switchJoint
            val max = jointsSortedByMatchLength.last().switchJoint
            min to max
        }

private fun switchJointLocationAccuracy(
    matches: List<SuggestedSwitchJointMatch>,
    getMeasurementMethod: (match: IndexedId<LayoutSegment>) -> MeasurementMethod?,
    geometrySwitch: GeometrySwitch?
): LocationAccuracy? {
    val worstMatchMeasurementMethod =
        matches.mapNotNull { match -> match.segmentId() }
            .distinct()
            .map(getMeasurementMethod)
            .maxByOrNull(::measurementMethodQualityRank)
    return if (geometrySwitch == null) {
        if (worstMatchMeasurementMethod == null) {
            null
        } else {
            LocationAccuracy.GEOMETRY_CALCULATED
        }
    } else {
        worstMatchMeasurementMethod?.let(::mapMeasurementMethodToLocationAccuracy)
    }
}

private fun measurementMethodQualityRank(mm: MeasurementMethod?) = when (mm) {
    MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY -> 0
    MeasurementMethod.OFFICIALLY_MEASURED_GEODETICALLY -> 1
    MeasurementMethod.UNVERIFIED_DESIGNED_GEOMETRY -> 2
    MeasurementMethod.TRACK_INSPECTION -> 3
    MeasurementMethod.DIGITIZED_AERIAL_IMAGE -> 4
    null -> 5
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

    val alignmentJointNumberPairs = switchAlignment.jointNumbers
        .flatMapIndexed { index1, jointNumber1 ->
            switchAlignment.jointNumbers
                .mapIndexed { index2, jointNumber2 ->
                    if (index2 > index1) jointNumber1 to jointNumber2
                    else null
                }
                .filterNotNull()
        }
        .sortedBy { (jointNumber1, jointNumber2) ->
            // Prefer full alignments, then alignments from the presentation point
            if (jointNumber1 == switchAlignment.jointNumbers.first() &&
                jointNumber2 == switchAlignment.jointNumbers.last()
            ) 0
            else if (jointNumber1 == switchStructure.presentationJointNumber) 1
            else 2
        }

    val alignmentPoints = alignment.allPoints()
    //Find the "start" point for switch joint
    //It's usually the first or the last point of alignment
    val startPointIndex = alignmentPoints
        .indexOfFirst { point -> point.isSame(location, TOLERANCE_JOINT_LOCATION_NEW_POINT) }

    if (startPointIndex == -1) return null

    val switchEndPointSearchIndexes = if (ascending) (startPointIndex..alignmentPoints.lastIndex)
    else (startPointIndex downTo 0)

    val startPoint = alignmentPoints[startPointIndex]

    return alignmentJointNumberPairs.firstNotNullOfOrNull { (startJointNumber, endJointNumber) ->
        val startJointLocation = switchStructure.getJointLocation(startJointNumber)
        val endJointLocation = switchStructure.getJointLocation(endJointNumber)
        val switchJointLength = lineLength(startJointLocation, endJointLocation)

        switchEndPointSearchIndexes
            .firstOrNull { endPointIndex ->
                val pointsLength = lineLength(alignmentPoints[endPointIndex], startPoint)
                pointsLength > switchJointLength
            }
            ?.let { endPointIndex ->
                alignmentPoints.getOrNull(if (ascending) endPointIndex - 1 else endPointIndex + 1)
                    ?.let { previousEndPoint ->
                        val endPoint = alignmentPoints[endPointIndex]
                        val lengthToPreviousEndPoint = lineLength(previousEndPoint, startPoint)
                        val lengthToEndPoint = lineLength(endPoint, startPoint)

                        val proportion =
                            (switchJointLength - lengthToPreviousEndPoint) / (lengthToEndPoint - lengthToPreviousEndPoint)
                        interpolate(previousEndPoint, endPoint, proportion)
                    }
            }
            ?.let { endPoint ->
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
                suggestedSwitchJoint.number == jointNumberToCheck &&
                        suggestedSwitchJoint.matches.any { match ->
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
    getMeasurementMethod: (segment: IndexedId<LayoutSegment>) -> MeasurementMethod?,
): SuggestedSwitch? {
    val mappedAlignments = alignmentMappings.map { mapping ->
        alignmentById[mapping.locationTrackId]
            ?: throw IllegalArgumentException("Alignment with id ${mapping.locationTrackId} is not provided")
    }

    alignmentMappings
        .forEach { alignmentMapping ->
            val switchTransformation = if (alignmentMapping.ascending == null)
                inferSwitchTransformationBothDirection(
                    locationTrackEndpoint.location,
                    switchStructure,
                    alignmentMapping.switchAlignmentId,
                    alignmentById[alignmentMapping.locationTrackId]!!.second,
                ) else
                inferSwitchTransformation(
                    locationTrackEndpoint.location,
                    switchStructure,
                    alignmentMapping.switchAlignmentId,
                    alignmentById[alignmentMapping.locationTrackId]!!.second,
                    alignmentMapping.ascending
                )

            if (switchTransformation != null) {
                val jointsInLayoutSpace = switchStructure.joints.map { joint ->
                    joint.copy(location = transformSwitchPoint(switchTransformation, joint.location))
                }

                val alignments = (nearbyAlignments + mappedAlignments)
                    .distinctBy { (locationTrack, _) -> locationTrack.id }

                val suggestedSwitch = createSuggestedSwitch(
                    jointsInLayoutSpace = jointsInLayoutSpace,
                    switchStructure = switchStructure,
                    alignments = alignments,
                    geometrySwitch = null,
                    geometryPlanId = null,
                    alignmentEndPoint = locationTrackEndpoint,
                    getMeasurementMethod = getMeasurementMethod
                )

                return if (hasAllMappedAlignments(alignmentMappings, suggestedSwitch))
                    suggestedSwitch
                else
                    null
            }
        }

    return null
}


fun clearSwitchInformationFromSegments(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> {
    val newSegments = alignment.segments.map { segment ->
        if (segment.switchId == layoutSwitchId) {
            segment.copy(
                switchId = null,
                endJointNumber = null,
                startJointNumber = null
            )
        } else {
            segment
        }
    }
    val overrideStartPoint =
        locationTrack.startPoint is EndPointSwitch && newSegments.first().switchId == null
    val overrideEndPoint =
        locationTrack.endPoint is EndPointSwitch && newSegments.last().switchId == null
    val newAlignment = alignment.withSegments(newSegments)
    return locationTrack.copy(
        startPoint = if (overrideStartPoint) null else locationTrack.startPoint,
        endPoint = if (overrideEndPoint) null else locationTrack.endPoint,
    ) to newAlignment
}

fun updateAlignmentSegmentsWithSwitchLinking(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    matchingJoints: List<SwitchLinkingJoint>,
): Pair<LocationTrack, LayoutAlignment> {
    val segmentIndexRange = matchingJoints
        .flatMap { joint -> joint.segments }
        .let { segments ->
            val min = segments.minOf { segment -> segment.segmentIndex }
            val max = segments.maxOf { segment -> segment.segmentIndex }
            min..max
        }

    val overriddenSwitches = alignment.segments.mapIndexedNotNull { index, segment ->
        if (index in segmentIndexRange) segment.switchId
        else null
    }.distinct()

    val segmentsWithNewSwitch = alignment.segments
        .map { segment ->
            if (overriddenSwitches.contains(segment.switchId)) clearSwitchInformation(segment)
            else segment
        }
        .mapIndexed { index, segment ->
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
                    .sortedWith(compareBy({ it.segments.first().segmentIndex }, { it.segments.first().segmentM }))

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

    val newSegments = combineAdjacentSegmentJointNumbers(segmentsWithNewSwitch, layoutSwitchId)

    val newStartPoint = newSegments.first().switchId.let { switchId ->
        if (switchId != null) EndPointSwitch(switchId as IntId<TrackLayoutSwitch>) else null
    }
    val newEndPoint = newSegments.last().switchId.let { switchId ->
        if (switchId != null) EndPointSwitch(switchId as IntId<TrackLayoutSwitch>) else null
    }
    return locationTrack.copy(
        startPoint = newStartPoint ?: locationTrack.startPoint,
        endPoint = newEndPoint ?: locationTrack.endPoint,
    ) to alignment.withSegments(newSegments)
}

private fun filterMatchingJointsBySwitchAlignment(
    switchStructure: SwitchStructure,
    matchingJoints: List<SwitchLinkingJoint>,
    locationTrackId: DomainId<LocationTrack>
): List<SwitchLinkingJoint> {
    val locationTrackSwitchJoints = matchingJoints
        .map { joint ->
            joint.copy(segments = joint.segments.filter { segment -> segment.locationTrackId == locationTrackId })
        }
        .filter { it.segments.isNotEmpty() }

    val switchStructureJointNumbers = switchStructure.alignments
        .firstOrNull { alignment ->
            //Switch alignment is determined by "etujatkos" and "takajatkos"
            locationTrackSwitchJoints.any { joint -> joint.jointNumber == alignment.jointNumbers.first() } &&
                    locationTrackSwitchJoints.any { joint -> joint.jointNumber == alignment.jointNumbers.last() }
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
) = linkingJoints
    .foldIndexed(mutableListOf<LayoutSegment>()) { index, acc, linkingJoint ->
        val jointNumber = linkingJoint.jointNumber
        val previousSegment = acc.lastOrNull()?.also { acc.removeLast() } ?: segment
        val suggestedPointLength = linkingJoint.segments.first().segmentM

        if (isSame(segment.points.first().m, suggestedPointLength, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
            //Check if suggested point is start point
            acc.add(setStartJointNumber(segment, layoutSwitchId, jointNumber))
        } else if (isSame(segment.points.last().m, suggestedPointLength, TOLERANCE_JOINT_LOCATION_SAME_POINT)) {
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
                suggestedPointLength,
                TOLERANCE_JOINT_LOCATION_NEW_POINT
            )

            //Handle cases differently when there are multiple joint matches in a single segment
            if (linkingJoints.size == 1) {
                acc.add(
                    if (isFirstSegment) clearSwitchInformation(startSplitSegment)
                    else if (isLastSegment) setEndJointNumber(startSplitSegment, layoutSwitchId, jointNumber)
                    else startSplitSegment.copy(
                        switchId = layoutSwitchId, startJointNumber = null, endJointNumber = null
                    )
                )
                endSplitSegment?.let {
                    acc.add(
                        if (isFirstSegment) setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                        else if (isLastSegment) clearSwitchInformation(endSplitSegment)
                        else setStartJointNumber(endSplitSegment, layoutSwitchId, jointNumber)
                    )
                }
            } else {
                when (index) {
                    //First joint match
                    0 -> {
                        acc.add(
                            if (isFirstSegment) clearSwitchInformation(startSplitSegment)
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
                                if (isLastSegment) clearSwitchInformation(endSplitSegment)
                                else endSplitSegment.copy(
                                    switchId = layoutSwitchId,
                                    startJointNumber = null,
                                    endJointNumber = null
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

private fun clearSwitchInformation(segment: LayoutSegment) =
    segment.copy(switchId = null, startJointNumber = null, endJointNumber = null)

private fun setStartJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = jointNumber, endJointNumber = null)

private fun setEndJointNumber(segment: LayoutSegment, switchId: IntId<TrackLayoutSwitch>, jointNumber: JointNumber) =
    segment.copy(switchId = switchId, startJointNumber = null, endJointNumber = jointNumber)

private fun combineAdjacentSegmentJointNumbers(
    layoutSegments: List<List<LayoutSegment>>,
    switchId: IntId<TrackLayoutSwitch>
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


@Service
class SwitchLinkingService @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val linkingDao: LinkingDao,
    private val geometryDao: GeometryDao,
    private val switchLibraryService: SwitchLibraryService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getSuggestedSwitches(bbox: BoundingBox): List<SuggestedSwitch> {
        logger.serviceCall("getSuggestedSwitches", "bbox" to bbox)
        val missing = linkingDao.getMissingLayoutSwitchLinkings(bbox)
        val result = missing.mapNotNull { missingLayoutSwitchLinking ->
            // Transform joints to layout space and calculate missing joints
            val geomSwitch = geometryDao.getSwitch(missingLayoutSwitchLinking.geometrySwitchId)
            val structure = geomSwitch.switchStructureId?.let(switchLibraryService::getSwitchStructure)
            // TODO: There is a missing switch here, but current logic doesn't support non-typed suggestions
            if (structure == null) null
            else calculateLayoutSwitchJoints(geomSwitch, structure, missingLayoutSwitchLinking.planSrid)
                ?.let { calculatedJoints ->
                    val switchBoundingBox = boundingBoxAroundPoints(calculatedJoints.map { it.location }) * 1.5
                    val nearAlignmentIds = locationTrackDao.fetchVersionsNear(DRAFT, switchBoundingBox)
                    val locationTrackIds = (nearAlignmentIds + missingLayoutSwitchLinking.locationTrackIds).distinct()
                    val alignments = locationTrackIds.map { version ->
                        locationTrackService.getWithAlignment(version)
                    }

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

    fun getSuggestedSwitch(createParams: SuggestedSwitchCreateParams): SuggestedSwitch? {
        logger.serviceCall("getSuggestedSwitch", "createParams" to createParams)

        val switchStructure =
            createParams.switchStructureId?.let(switchLibraryService::getSwitchStructure) ?: return null
        val locationTrackIds = createParams.alignmentMappings.map { mapping -> mapping.locationTrackId }
        val areaSize = switchStructure.bbox.width.coerceAtLeast(switchStructure.bbox.height) * 2.0
        val switchAreaBbox = BoundingBox(
            Point(0.0, 0.0),
            Point(areaSize, areaSize)
        ).centerAt(createParams.locationTrackEndpoint.location)
        val nearbyLocationTracks = locationTrackService.listNearWithAlignments(DRAFT, switchAreaBbox)

        return createSuggestedSwitch(
            createParams.locationTrackEndpoint,
            switchStructure,
            createParams.alignmentMappings,
            nearbyLocationTracks,
            locationTrackIds.associateWith { id -> locationTrackService.getWithAlignment(DRAFT, id) },
            getMeasurementMethod = this::getMeasurementMethod,
        )
    }

    @Transactional
    fun saveSwitchLinking(linkingParameters: SwitchLinkingParameters): RowVersion<TrackLayoutSwitch> {
        clearSwitchInformationFromSegments(linkingParameters.layoutSwitchId)
        val switchId = updateLayoutSwitch(linkingParameters)
        updateSwitchLinkingIntoSegments(linkingParameters)
        return switchId
    }

    private fun clearSwitchInformationFromSegments(layoutSwitchId: IntId<TrackLayoutSwitch>) {
        linkingDao.findLocationTracksLinkedToSwitch(layoutSwitchId)
            .forEach { (id, _) ->
                val (locationTrack, alignment) = locationTrackService.getWithAlignment(DRAFT, id)
                val (updatedLocationTrack, updatedAlignment) = clearSwitchInformationFromSegments(
                    locationTrack,
                    alignment,
                    layoutSwitchId,
                )
                locationTrackService.saveDraft(updatedLocationTrack, updatedAlignment)
            }
    }

    fun insertSwitch(request: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.serviceCall("insertSwitch", "request" to request)

        val switch = TrackLayoutSwitch(
            name = request.name,
            switchStructureId = request.switchStructureId,
            stateCategory = request.stateCategory,
            joints = listOf(),
            externalId = null,
            sourceId = null,
            trapPoint = null,
            ownerId = request.ownerId,
            source = GeometrySource.GENERATED,
        )
        return switchService.saveDraft(switch).id
    }

    fun updateSwitch(id: IntId<TrackLayoutSwitch>, switch: TrackLayoutSwitchSaveRequest): IntId<TrackLayoutSwitch> {
        logger.serviceCall("updateSwitch", "id" to id, "switch" to switch)
        if (switch.stateCategory == LayoutStateCategory.NOT_EXISTING) {
            clearSwitchInformationFromSegments(id)
        }

        val trackLayoutSwitch = switchService.getDraft(id).copy(
            id = id,
            name = switch.name,
            switchStructureId = switch.switchStructureId,
            stateCategory = switch.stateCategory,
        )
        return switchService.saveDraft(trackLayoutSwitch).id
    }

    fun deleteDraftSwitch(switchId: IntId<TrackLayoutSwitch>): IntId<TrackLayoutSwitch> {
        logger.serviceCall("deleteDraftSwitch", "switchId" to switchId)
        clearSwitchInformationFromSegments(switchId)
        return switchService.deleteUnpublishedDraft(switchId).id
    }

    private fun updateLayoutSwitch(linkingParameters: SwitchLinkingParameters): RowVersion<TrackLayoutSwitch> {
        val layoutSwitch = switchService.getDraft(linkingParameters.layoutSwitchId)
        val newGeometrySwitchId = linkingParameters.geometrySwitchId ?: layoutSwitch.sourceId
        val newJoints = linkingParameters.joints.map { linkingJoint ->
            TrackLayoutSwitchJoint(
                number = linkingJoint.jointNumber,
                location = linkingJoint.location,
                locationAccuracy = linkingJoint.locationAccuracy
            )
        }
        val newLayoutSwitch = layoutSwitch.copy(
            sourceId = newGeometrySwitchId,
            switchStructureId = linkingParameters.switchStructureId,
            joints = newJoints,
            source = if (newGeometrySwitchId != null) GeometrySource.PLAN else GeometrySource.GENERATED,
        )
        return switchService.saveDraft(newLayoutSwitch)
    }

    private fun updateSwitchLinkingIntoSegments(linkingParameters: SwitchLinkingParameters) {
        val switchStructure = switchLibraryService.getSwitchStructure(linkingParameters.switchStructureId)

        val switchJointsByLocationTrack = linkingParameters.joints
            .flatMap { joint -> joint.segments.map { segment -> segment.locationTrackId } }
            .distinct()
            .associateWith { locationTrackId ->
                filterMatchingJointsBySwitchAlignment(switchStructure, linkingParameters.joints, locationTrackId)
            }
            .filter { it.value.isNotEmpty() }

        switchJointsByLocationTrack
            .map { (locationTrackId, switchJoints) ->
                val (locationTrack, alignment) = locationTrackService.getWithAlignment(DRAFT, locationTrackId)

                updateAlignmentSegmentsWithSwitchLinking(
                    locationTrack = locationTrack,
                    alignment = alignment,
                    layoutSwitchId = linkingParameters.layoutSwitchId,
                    matchingJoints = switchJoints,
                )
            }
            .forEach { (locationTrack, alignment) ->
                locationTrackService.saveDraft(locationTrack, alignment)
            }
    }


    private fun getMeasurementMethod(id: IndexedId<LayoutSegment>): MeasurementMethod? =
        geometryDao.getMeasurementMethodForLayoutSegment(id)

}
