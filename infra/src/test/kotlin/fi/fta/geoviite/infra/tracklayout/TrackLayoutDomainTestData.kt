package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IndexedId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.LayoutGeocodingContextCacheKey
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geography.transformFromLayoutToGKCoordinate
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.getSomeNullableValue
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.linking.switches.FittedSwitchJointMatch
import fi.fta.geoviite.infra.linking.switches.SuggestedSwitchJointMatchType
import fi.fta.geoviite.infra.map.GeometryAlignmentHeader
import fi.fta.geoviite.infra.map.MapAlignmentType
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.IPoint3DM
import fi.fta.geoviite.infra.math.IPoint3DZ
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.math.Point3DZ
import fi.fta.geoviite.infra.math.Point4DZM
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.PublishedVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchElementCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchElementLine
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

private const val SEED = 123321L

private val rand = Random(SEED)

fun switchStructureYV60_300_1_9(): SwitchStructure {
    return SwitchStructure(
        id = IntId(55),
        type = SwitchType("YV60-300-1:9-O"),
        presentationJointNumber = JointNumber(1),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(5), Point(16.615, 0.0)),
                SwitchJoint(JointNumber(2), Point(34.430, 0.0)),
                SwitchJoint(JointNumber(3), Point(34.321, -1.967)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(IndexedId(1, 1), Point(0.0, 0.0), Point(16.615, 0.0)),
                            SwitchElementLine(IndexedId(1, 2), Point(16.615, 0.0), Point(34.430, 0.0)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementCurve(IndexedId(2, 1), Point(0.0, 0.0), Point(33.128, -1.835), radius = 300.0),
                            SwitchElementLine(IndexedId(2, 2), Point(33.128, -1.835), Point(34.321, -1.967)),
                        ),
                ),
            ),
    )
}

fun switchStructureRR54_4x1_9() =
    SwitchStructure(
        id = IntId(133),
        type = SwitchType("RR54-4x1:9"),
        presentationJointNumber = JointNumber(5),
        joints =
            listOf(
                SwitchJoint(JointNumber(1), Point(-5.075, -1.142)),
                SwitchJoint(JointNumber(5), Point(0.0, 0.0)),
                SwitchJoint(JointNumber(2), Point(5.075, 1.142)),
                SwitchJoint(JointNumber(4), Point(-5.075, 1.142)),
                SwitchJoint(JointNumber(3), Point(5.075, -1.142)),
            ),
        alignments =
            listOf(
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                    elements =
                        listOf(
                            SwitchElementLine(IndexedId(3, 1), Point(-5.075, -1.142), Point(0.0, 0.0)),
                            SwitchElementLine(IndexedId(3, 2), Point(0.0, 0.0), Point(5.075, 1.142)),
                        ),
                ),
                SwitchAlignment(
                    jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                    elements =
                        listOf(
                            SwitchElementLine(IndexedId(4, 1), Point(-5.075, 1.142), Point(0.0, 0.0)),
                            SwitchElementLine(IndexedId(4, 2), Point(0.0, 0.0), Point(-5.075, 1.142)),
                        ),
                ),
            ),
    )

fun switchAndMatchingAlignments(
    trackNumberId: IntId<LayoutTrackNumber>,
    structure: SwitchStructure,
    draft: Boolean,
): Pair<LayoutSwitch, List<Pair<LocationTrack, LayoutAlignment>>> {
    val switchId = IntId<LayoutSwitch>(1)
    val jointLocations = mutableMapOf<JointNumber, Point>()
    val alignments =
        structure.alignments.map { alignment ->
            val alignmentPoints =
                alignment.jointNumbers.map { jointNumber ->
                    val point =
                        jointLocations.computeIfAbsent(jointNumber) { number ->
                            val joint = structure.joints.find { structureJoint -> structureJoint.number == number }
                            joint?.location ?: throw IllegalStateException("No such joint in structure")
                        }
                    jointNumber to point
                }
            locationTrackAndAlignment(
                trackNumberId,
                alignmentPoints.zipWithNext { start, end ->
                    val (startJoint, startPoint) = start
                    val (endJoint, endPoint) = end
                    val length = lineLength(startPoint, endPoint)
                    segment(
                        points(length.toInt(), startPoint.x..endPoint.x, startPoint.y..endPoint.y),
                        switchId = switchId,
                        startJointNumber = startJoint,
                        endJointNumber = endJoint,
                    )
                },
                draft = draft,
            )
        }
    val switch =
        switch(
            id = switchId,
            structureId = structure.id as IntId,
            joints = jointLocations.map { (number, point) -> LayoutSwitchJoint(number, point, null) },
            draft = draft,
            stateCategory = LayoutStateCategory.EXISTING,
        )
    return switch to alignments
}

fun segmentsFromSwitchStructure(
    start: IPoint,
    switchId: IntId<LayoutSwitch>,
    structure: SwitchStructure,
    line: List<Int>,
): List<LayoutSegment> {
    val expectedJoints = line.map(::JointNumber)
    val switchAlignment = requireNotNull(structure.alignments.find { a -> a.jointNumbers == expectedJoints })
    return segmentsFromSwitchAlignment(start, switchId, switchAlignment)
}

fun segmentsFromSwitchAlignment(
    start: IPoint,
    switchId: IntId<LayoutSwitch>,
    alignment: SwitchAlignment,
): List<LayoutSegment> {
    val jointNumbers = alignment.jointNumbers
    val elements = alignment.elements

    // There are 5 possibilities of switch line structures at the time of typing:
    // The start character * means one of the special cases where not every start/end joint number
    // is defined for all segments.
    //
    // 2 joints to one, two (*) or three (*) switch elements;
    // 3 joints to two elements;
    // 4 joints to three elements.
    return when (alignment.jointNumbers.size to alignment.elements.size) {
        2 to 2 ->
            listOf(
                segment(
                    points = toSegmentPoints(start + elements[0].start, start + elements[0].end),
                    switchId = switchId,
                    startJointNumber = jointNumbers[0],
                ),
                segment(
                    points = toSegmentPoints(start + elements[1].start, start + elements[1].end),
                    switchId = switchId,
                    endJointNumber = jointNumbers[1],
                ),
            )

        2 to 3 ->
            listOf(
                segment(
                    points = toSegmentPoints(start + elements[0].start, start + elements[0].end),
                    switchId = switchId,
                    startJointNumber = jointNumbers[0],
                ),
                segment(
                    points = toSegmentPoints(start + elements[1].start, start + elements[1].end),
                    switchId = switchId,
                ),
                segment(
                    points = toSegmentPoints(start + elements[2].start, start + elements[2].end),
                    switchId = switchId,
                    endJointNumber = jointNumbers[1],
                ),
            )

        else ->
            alignment.elements.mapIndexed { i, e ->
                segment(
                    points = toSegmentPoints(start + e.start, start + e.end),
                    switchId = switchId,
                    startJointNumber = alignment.jointNumbers[i],
                    endJointNumber = alignment.jointNumbers[i + 1],
                )
            }
    }
}

fun switchOwnerVayla(): SwitchOwner {
    return SwitchOwner(id = IntId(1), name = MetaDataName("Väylävirasto"))
}

fun points(
    count: Int,
    x: ClosedRange<Double>,
    y: ClosedRange<Double>,
    z: ClosedRange<Double>? = null,
    cant: ClosedRange<Double>? = null,
): List<SegmentPoint> {
    val points =
        (0 until count).map { i ->
            SegmentPoint(
                x = valueOnRange(x, i, count),
                y = valueOnRange(y, i, count),
                z = z?.let { zRange -> valueOnRange(zRange, i, count) },
                m = 0.0,
                cant = cant?.let { cantRange -> valueOnRange(cantRange, i, count) },
            )
        }
    var cumulativeM = 0.0
    return points.mapIndexed { index, point ->
        if (index > 0) cumulativeM += lineLength(points[index - 1], point)
        point.copy(m = cumulativeM)
    }
}

private fun valueOnRange(range: ClosedRange<Double>, index: Int, count: Int) =
    range.start + index * (range.endInclusive - range.start) / (count - 1)

fun trackNumber(
    number: TrackNumber = TrackNumber("100"),
    description: String = "Test Track number $number",
    draft: Boolean = false,
    state: LayoutState = LayoutState.IN_USE,
    id: IntId<LayoutTrackNumber>? = null,
    contextData: LayoutContextData<LayoutTrackNumber> = createMainContext(id, draft),
) =
    LayoutTrackNumber(
        number = number,
        description = TrackNumberDescription(description),
        state = state,
        contextData = contextData,
    )

fun trackNumberSaveRequest(
    number: TrackNumber = TrackNumber("100"),
    description: String = "Test Track number $number",
    state: LayoutState = LayoutState.IN_USE,
    startAddress: TrackMeter = TrackMeter.ZERO,
) =
    TrackNumberSaveRequest(
        number = number,
        description = TrackNumberDescription(description),
        state = state,
        startAddress = startAddress,
    )

fun referenceLineAndAlignment(
    trackNumberId: IntId<LayoutTrackNumber>,
    vararg segments: LayoutSegment,
    startAddress: TrackMeter = TrackMeter.ZERO,
    draft: Boolean = false,
): Pair<ReferenceLine, LayoutAlignment> =
    referenceLineAndAlignment(trackNumberId, segments.toList(), startAddress = startAddress, draft = draft)

fun referenceLineAndAlignment(
    trackNumberId: IntId<LayoutTrackNumber>,
    segments: List<LayoutSegment>,
    startAddress: TrackMeter = TrackMeter.ZERO,
    draft: Boolean = false,
): Pair<ReferenceLine, LayoutAlignment> {
    val alignment = alignment(segments)
    val referenceLine =
        referenceLine(trackNumberId = trackNumberId, alignment = alignment, startAddress = startAddress, draft = draft)
    return referenceLine to alignment
}

fun referenceLine(
    trackNumberId: IntId<LayoutTrackNumber>,
    alignment: LayoutAlignment? = null,
    startAddress: TrackMeter = TrackMeter.ZERO,
    id: IntId<ReferenceLine>? = null,
    alignmentVersion: RowVersion<LayoutAlignment>? = if (id != null) someRowVersion() else null,
    draft: Boolean = false,
    contextData: LayoutContextData<ReferenceLine> = createMainContext(id, draft),
) =
    ReferenceLine(
        trackNumberId = trackNumberId,
        startAddress = startAddress,
        sourceId = null,
        boundingBox = alignment?.boundingBox,
        segmentCount = alignment?.segments?.size ?: 0,
        length = alignment?.length ?: 0.0,
        alignmentVersion = alignmentVersion,
        contextData = contextData,
    )

private var locationTrackNameCounter = 0

fun locationTrackAndAlignment(
    vararg segments: LayoutSegment,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    id: IntId<LocationTrack>? = null,
    draft: Boolean,
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment(
        IntId(0),
        segments.toList(),
        id = id,
        name = name,
        description = description,
        draft = draft,
    )

fun locationTrackAndAlignment(
    trackNumberId: IntId<LayoutTrackNumber>,
    vararg segments: LayoutSegment,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    duplicateOf: IntId<LocationTrack>? = null,
    state: LocationTrackState = LocationTrackState.IN_USE,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
    topologyStartSwitch: TopologyLocationTrackSwitch? = null,
    topologyEndSwitch: TopologyLocationTrackSwitch? = null,
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment(
        trackNumberId,
        segments.toList(),
        name = name,
        description = description,
        duplicateOf = duplicateOf,
        state = state,
        id = id,
        draft = draft,
        topologyStartSwitch = topologyStartSwitch,
        topologyEndSwitch = topologyEndSwitch,
    )

fun locationTrackAndAlignment(
    trackNumberId: IntId<LayoutTrackNumber>,
    segments: List<LayoutSegment>,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
    name: String = "T001 ${locationTrackNameCounter++}",
    type: LocationTrackType = LocationTrackType.SIDE,
    description: String = "test-alignment 001",
    duplicateOf: IntId<LocationTrack>? = null,
    state: LocationTrackState = LocationTrackState.IN_USE,
    topologyStartSwitch: TopologyLocationTrackSwitch? = null,
    topologyEndSwitch: TopologyLocationTrackSwitch? = null,
): Pair<LocationTrack, LayoutAlignment> {
    val alignment = alignment(segments)
    val locationTrack =
        locationTrack(
            trackNumberId = trackNumberId,
            alignment = alignment,
            id = id,
            draft = draft,
            name = name,
            type = type,
            description = description,
            duplicateOf = duplicateOf,
            state = state,
            topologyStartSwitch = topologyStartSwitch,
            topologyEndSwitch = topologyEndSwitch,
        )
    return locationTrack to alignment
}

fun locationTrack(
    trackNumberId: IntId<LayoutTrackNumber>,
    alignment: LayoutAlignment? = null,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    type: LocationTrackType = LocationTrackType.SIDE,
    state: LocationTrackState = LocationTrackState.IN_USE,
    alignmentVersion: RowVersion<LayoutAlignment>? = if (id != null) someRowVersion() else null,
    topologicalConnectivity: TopologicalConnectivityType = TopologicalConnectivityType.NONE,
    topologyStartSwitch: TopologyLocationTrackSwitch? = null,
    topologyEndSwitch: TopologyLocationTrackSwitch? = null,
    duplicateOf: IntId<LocationTrack>? = null,
    ownerId: IntId<LocationTrackOwner> = IntId(1),
    contextData: LayoutContextData<LocationTrack> = createMainContext(id, draft),
    descriptionSuffix: LocationTrackDescriptionSuffix = LocationTrackDescriptionSuffix.NONE,
) =
    locationTrack(
        trackNumberId = trackNumberId,
        alignment = alignment,
        contextData = contextData,
        name = name,
        description = description,
        type = type,
        state = state,
        alignmentVersion = alignmentVersion,
        topologicalConnectivity = topologicalConnectivity,
        topologyStartSwitch = topologyStartSwitch,
        topologyEndSwitch = topologyEndSwitch,
        duplicateOf = duplicateOf,
        ownerId = ownerId,
        descriptionSuffix = descriptionSuffix,
    )

fun locationTrack(
    trackNumberId: IntId<LayoutTrackNumber>,
    alignment: LayoutAlignment? = null,
    contextData: LayoutContextData<LocationTrack>,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    type: LocationTrackType = LocationTrackType.SIDE,
    state: LocationTrackState = LocationTrackState.IN_USE,
    alignmentVersion: RowVersion<LayoutAlignment>? = null,
    topologicalConnectivity: TopologicalConnectivityType = TopologicalConnectivityType.NONE,
    topologyStartSwitch: TopologyLocationTrackSwitch? = null,
    topologyEndSwitch: TopologyLocationTrackSwitch? = null,
    duplicateOf: IntId<LocationTrack>? = null,
    ownerId: IntId<LocationTrackOwner> = IntId(1),
    descriptionSuffix: LocationTrackDescriptionSuffix = LocationTrackDescriptionSuffix.NONE,
) =
    LocationTrack(
        name = AlignmentName(name),
        descriptionBase = LocationTrackDescriptionBase(description),
        descriptionSuffix = descriptionSuffix,
        type = type,
        state = state,
        trackNumberId = trackNumberId,
        sourceId = null,
        boundingBox = alignment?.boundingBox,
        segmentCount = alignment?.segments?.size ?: 0,
        length = alignment?.length ?: 0.0,
        duplicateOf = duplicateOf,
        topologicalConnectivity = topologicalConnectivity,
        topologyStartSwitch = topologyStartSwitch,
        topologyEndSwitch = topologyEndSwitch,
        alignmentVersion = alignmentVersion,
        ownerId = ownerId,
        contextData = contextData,
    )

fun <T> someOid() = Oid<T>("${nextInt(10, 1000)}.${nextInt(10, 1000)}.${nextInt(10, 1000)}")

fun someAlignment() = alignment(someSegment())

fun alignmentFromPoints(vararg points: Point) = alignment(segment(*points))

fun alignment(vararg segments: LayoutSegment) = alignment(segments.toList())

fun alignment(segments: List<LayoutSegment>) = LayoutAlignment(segments = fixSegmentStarts(segments))

fun mapAlignment(vararg segments: PlanLayoutSegment) = mapAlignment(segments.toList())

fun mapAlignment(segments: List<PlanLayoutSegment>) =
    PlanLayoutAlignment(
        header =
            GeometryAlignmentHeader(
                id = StringId(),
                name = AlignmentName("test-alignment"),
                alignmentType = MapAlignmentType.LOCATION_TRACK,
                state = LayoutState.IN_USE,
                segmentCount = segments.size,
                trackNumberId = IntId(1),
                length = segments.map(PlanLayoutSegment::length).sum(),
                boundingBox = boundingBoxCombining(segments.mapNotNull(PlanLayoutSegment::boundingBox)),
            ),
        segments = segments,
    )

fun locationTrackWithTwoSwitches(
    trackNumberId: IntId<LayoutTrackNumber>,
    layoutSwitchId: IntId<LayoutSwitch>,
    otherLayoutSwitchId: IntId<LayoutSwitch>,
    locationTrackId: IntId<LocationTrack>? = null,
    draft: Boolean,
): Pair<LocationTrack, LayoutAlignment> {
    val segmentLength = 10.0
    val segments =
        (1..20).map { i ->
            segment(Point(i * segmentLength, 0.0), Point((i + 1) * segmentLength, 0.0), startM = i * segmentLength)
        }
    val (locationTrack, alignment) =
        locationTrackAndAlignment(
            trackNumberId = trackNumberId,
            segments = segments,
            id = locationTrackId,
            draft = draft,
        )
    return attachSwitches(
        locationTrack to alignment,
        layoutSwitchId to TargetSegmentStart(),
        otherLayoutSwitchId to TargetSegmentEnd(),
    )
}

fun attachSwitchToStart(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<LayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> =
    attachSwitchToStart(locationTrackAndAlignment.first, locationTrackAndAlignment.second, switchId)

fun attachSwitchToStart(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<LayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> {
    if (alignment.segments.count() < 3) throw IllegalArgumentException("Alignment must contain at least 3 segments")
    return locationTrack to
        alignment.copy(
            segments =
                alignment.segments.mapIndexed { index, segment ->
                    when (index) {
                        0 -> segment.copy(switchId = switchId, startJointNumber = JointNumber(1))

                        1 -> segment.copy(switchId = switchId)

                        2 -> segment.copy(switchId = switchId, endJointNumber = JointNumber(2))

                        else -> segment
                    }
                }
        )
}

fun attachSwitchToEnd(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<LayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> =
    attachSwitchToEnd(locationTrackAndAlignment.first, locationTrackAndAlignment.second, switchId)

fun attachSwitchToEnd(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<LayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> {
    val segmentCount = alignment.segments.count()
    if (segmentCount < 3) throw IllegalArgumentException("Alignment must contain at least 3 segments")
    return locationTrack to
        alignment.copy(
            segments =
                alignment.segments.mapIndexed { index, segment ->
                    when (index) {
                        segmentCount - 3 -> segment.copy(switchId = switchId, startJointNumber = JointNumber(2))

                        segmentCount - 2 -> segment.copy(switchId = switchId)

                        segmentCount - 1 -> segment.copy(switchId = switchId, endJointNumber = JointNumber(1))

                        else -> segment
                    }
                }
        )
}

fun attachSwitchToIndex(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<LayoutSwitch>,
    segmentIndex: Int,
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment.first to attachSwitchToIndex(locationTrackAndAlignment.second, switchId, segmentIndex)

fun attachSwitchToIndex(alignment: LayoutAlignment, switchId: IntId<LayoutSwitch>, segmentIndex: Int): LayoutAlignment {
    if (alignment.segments.count() < segmentIndex + 3)
        throw IllegalArgumentException("Alignment must contain at least ${segmentIndex + 3} segments")
    return alignment.copy(
        segments =
            alignment.segments.mapIndexed { index, segment ->
                when (index) {
                    segmentIndex -> segment.copy(switchId = switchId, startJointNumber = JointNumber(1))

                    segmentIndex + 1 -> segment.copy(switchId = switchId)

                    segmentIndex + 2 -> segment.copy(switchId = switchId, endJointNumber = JointNumber(2))

                    else -> segment
                }
            }
    )
}

fun attachSwitchToIndex(alignment: LayoutAlignment, switch: LayoutSwitch, segmentIndex: Int): LayoutAlignment {
    if (alignment.segments.count() < segmentIndex + 3) {
        throw IllegalArgumentException("Alignment must contain at least ${segmentIndex + 3} segments")
    }

    return alignment.copy(
        segments =
            alignment.segments.mapIndexed { index, segment ->
                when (index) {
                    segmentIndex -> segment.copy(switchId = switch.id as IntId, startJointNumber = JointNumber(1))

                    segmentIndex + 1 -> segment.copy(switchId = switch.id as IntId)

                    segmentIndex + 2 -> segment.copy(switchId = switch.id as IntId, endJointNumber = JointNumber(2))

                    else -> segment
                }
            }
    )
}

fun geocodingContext(
    referenceLinePoints: List<Point>,
    trackNumber: TrackNumber = TrackNumber("001"),
    startAddress: TrackMeter = TrackMeter.ZERO,
    kmPosts: List<LayoutKmPost> = listOf(),
) =
    alignment(segment(*referenceLinePoints.toTypedArray())).let { alignment ->
        GeocodingContext.create(
                trackNumber = trackNumber,
                startAddress = startAddress,
                referenceLineGeometry = alignment,
                kmPosts = kmPosts,
            )
            .geocodingContext
    }

abstract class TargetSegment

class TargetSegmentStart : TargetSegment()

data class TargetSegmentMiddle(val index: Int) : TargetSegment()

class TargetSegmentEnd : TargetSegment()

fun attachSwitches(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    vararg switchInfos: Pair<IntId<LayoutSwitch>, TargetSegment>,
): Pair<LocationTrack, LayoutAlignment> {
    return switchInfos.fold(locationTrackAndAlignment) { accLocationTrackAndAlignment, (switchId, targetSegment) ->
        when (targetSegment) {
            is TargetSegmentStart -> attachSwitchToStart(accLocationTrackAndAlignment, switchId)
            is TargetSegmentEnd -> attachSwitchToEnd(accLocationTrackAndAlignment, switchId)
            is TargetSegmentMiddle -> attachSwitchToIndex(accLocationTrackAndAlignment, switchId, targetSegment.index)
            else -> throw NotImplementedError()
        }
    }
}

fun segment(
    vararg points: IPoint,
    startM: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: GeometryElement? = null,
    switchId: IntId<LayoutSwitch>? = null,
    startJointNumber: JointNumber? = null,
    endJointNumber: JointNumber? = null,
    sourceStart: Double? = null,
) =
    segment(
        toSegmentPoints(to3DMPoints(points.asList())),
        startM = startM,
        source = source,
        sourceStart = sourceStart,
        sourceId = sourceId,
        switchId = switchId,
        startJointNumber = startJointNumber,
        endJointNumber = endJointNumber,
    )

fun segment(
    vararg points: Point3DZ,
    start: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: GeometryElement? = null,
) = segment(toSegmentPoints(to3DMPoints(points.asList(), start)), start, source, sourceId)

fun segment(
    vararg points: IPoint3DM,
    start: Double = points.first().m,
    source: GeometrySource = PLAN,
    sourceId: GeometryElement? = null,
) = segment(toSegmentPoints(points.asList()), start, source, sourceId)

fun segment(
    points: List<SegmentPoint>,
    startM: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: GeometryElement? = null,
    sourceStart: Double? = null,
    resolution: Int = 1,
    switchId: IntId<LayoutSwitch>? = null,
    startJointNumber: JointNumber? = null,
    endJointNumber: JointNumber? = null,
) =
    LayoutSegment(
        geometry = SegmentGeometry(segmentPoints = points, resolution = resolution),
        startM = startM,
        sourceId = sourceId?.id as IndexedId?,
        sourceStart = sourceStart,
        switchId = switchId,
        startJointNumber = startJointNumber,
        endJointNumber = endJointNumber,
        source = source,
    )

fun mapSegment(
    vararg points: Point3DM,
    startM: Double = points[0].m,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStart: Double? = null,
    source: GeometrySource = PLAN,
) =
    mapSegment(
        points = toSegmentPoints(points.asList()),
        start = startM,
        sourceId = sourceId,
        sourceStart = sourceStart,
        source = source,
    )

fun mapSegment(
    points: List<SegmentPoint>,
    start: Double = 0.0,
    resolution: Int = 1,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStart: Double? = null,
    source: GeometrySource = PLAN,
    id: DomainId<LayoutSegment> = StringId(),
) =
    PlanLayoutSegment(
        geometry = SegmentGeometry(segmentPoints = points, resolution = resolution),
        startM = start,
        pointCount = points.size,
        sourceId = sourceId,
        sourceStart = sourceStart,
        source = source,
        id = id,
    )

fun splitSegment(segment: LayoutSegment, numberOfParts: Int): List<LayoutSegment> {
    val allPoints = segment.alignmentPoints
    val indexRange = 0..allPoints.lastIndex
    val pointsPerSegment = allPoints.count() / numberOfParts.toDouble()
    return indexRange
        .groupBy { index -> (index / pointsPerSegment).toInt() }
        .map { (_, groupIndexRange) ->
            val points = allPoints.subList(0.coerceAtLeast(groupIndexRange.first() - 1), groupIndexRange.last() + 1)
            segment(points = points.map { Point(it) }.toTypedArray(), startM = points.first().m)
        }
}

fun toSegmentPoints(vararg points: IPoint) = toSegmentPoints(to3DMPoints(points.asList()))

fun toSegmentPoints(vararg points: Point3DZ) = toSegmentPoints(to3DMPoints(points.asList()))

fun toSegmentPoints(vararg points: IPoint3DM) = toSegmentPoints(points.asList())

fun toSegmentPoints(points: List<IPoint3DM>) =
    points.map { point ->
        SegmentPoint(
            x = point.x,
            y = point.y,
            z =
                when (point) {
                    is AlignmentPoint -> point.z
                    is IPoint3DZ -> point.z
                    else -> null
                },
            m = point.m - points.first().m,
            cant = (point as? AlignmentPoint)?.cant,
        )
    }

fun toAlignmentPoints(vararg points: IPoint) = toAlignmentPoints(to3DMPoints(points.asList()))

fun toAlignmentPoints(vararg points: Point3DZ) = toAlignmentPoints(to3DMPoints(points.asList()))

fun toAlignmentPoints(vararg points: IPoint3DM) = toAlignmentPoints(points.asList())

fun toAlignmentPoints(points: List<IPoint3DM>) =
    points.map { point ->
        AlignmentPoint(
            point.x,
            point.y,
            when (point) {
                is AlignmentPoint -> point.z
                is IPoint3DZ -> point.z
                else -> null
            },
            point.m,
            if (point is AlignmentPoint) point.cant else null,
        )
    }

fun to3DMPoints(points: List<IPoint>, start: Double = 0.0): List<IPoint3DM> {
    val pointsWithDistance =
        points.mapIndexed { index, point ->
            val distance = points.getOrNull(index - 1)?.let { prev -> lineLength(prev, point) } ?: 0.0
            point to distance
        }
    return pointsWithDistance.mapIndexed { index, (point, _) ->
        val m = pointsWithDistance.subList(0, index + 1).foldRight(start) { (_, distance), acc -> acc + distance }
        when (point) {
            is AlignmentPoint -> AlignmentPoint(point.x, point.y, point.z, m, point.cant)
            is Point3DZ -> Point4DZM(point.x, point.y, point.z, m)
            else -> Point3DM(point.x, point.y, m)
        }
    }
}

fun fixMValues(points: List<SegmentPoint>): List<SegmentPoint> {
    var m = 0.0
    return points.mapIndexed { i, p ->
        val previous = points.getOrNull(i - 1)
        if (previous != null) m += lineLength(previous, p)
        p.copy(m = m)
    }
}

fun someSegment() = segment(3, 10.0, 20.0, 10.0, 20.0)

fun segment(points: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    segment(points = rawPoints(points, minX, maxX, minY, maxY))

fun segment(points: Int, start: Point, end: Point) = segment(points, start.x, end.x, start.y, end.y)

fun segment(from: IPoint, to: IPoint): LayoutSegment {
    return segment(toSegmentPoints(to3DMPoints((listOf(from) + middlePoints(from, to) + listOf(to)).distinct())))
}

private fun middlePoints(from: IPoint, to: IPoint) =
    (1 until lineLength(from, to).toInt()).map { i -> (from + (to - from).normalized() * i.toDouble()) }

fun segments(from: IPoint, to: IPoint, segmentCount: Int): List<LayoutSegment> {
    return segments(from, to, lineLength(from, to) / segmentCount)
}

fun singleSegmentWithInterpolatedPoints(vararg points: IPoint): LayoutSegment =
    segment(
        toSegmentPoints(
            to3DMPoints(
                points.toList().zipWithNext { a, b -> listOf(a) + middlePoints(a, b) }.flatten() + points.last()
            )
        )
    )

fun segments(from: IPoint, to: IPoint, segmentLength: Double): List<LayoutSegment> {
    val dir = (to - from).normalized()
    val segmentCount = ceil(lineLength(from, to) / segmentLength).toInt()
    val endPoints =
        listOf(from) + (1 until segmentCount).map { i -> from + dir * segmentLength * i.toDouble() } + listOf(to)
    return segments(endPoints = endPoints.toTypedArray())
}

fun segments(vararg endPoints: IPoint): List<LayoutSegment> {
    assert(endPoints.count() >= 2) { "End points must contain at least two points" }
    var startLength = 0.0
    return endPoints.distinct().dropLast(1).mapIndexed { index, start ->
        val end = endPoints[index + 1]
        val segment = segment(start, end, startM = startLength)
        startLength += lineLength(start, end)
        segment
    }
}

fun switchFromDbStructure(
    name: String,
    switchStart: IPoint,
    structure: SwitchStructure,
    draft: Boolean = false,
): LayoutSwitch =
    switch(
        name = name,
        structureId = structure.id as IntId,
        draft = draft,
        joints =
            structure.joints.map { j ->
                LayoutSwitchJoint(number = j.number, location = switchStart + j.location, locationAccuracy = null)
            },
    )

private var switchNameCounter = 0

fun switch(
    structureId: IntId<SwitchStructure> = switchStructureYV60_300_1_9().id as IntId,
    joints: List<LayoutSwitchJoint> = listOf(),
    name: String = "TV${switchNameCounter++}",
    stateCategory: LayoutStateCategory = LayoutStateCategory.EXISTING,
    id: IntId<LayoutSwitch>? = null,
    draft: Boolean = false,
    ownerId: IntId<SwitchOwner>? = switchOwnerVayla().id,
    contextData: LayoutContextData<LayoutSwitch> = createMainContext(id, draft),
) =
    LayoutSwitch(
        sourceId = null,
        name = SwitchName(name),
        stateCategory = stateCategory,
        joints = joints,
        switchStructureId = structureId,
        trapPoint = false,
        ownerId = ownerId,
        source = GENERATED,
        contextData = contextData,
    )

fun <T : LayoutAsset<T>> createMainContext(id: IntId<T>?, draft: Boolean): LayoutContextData<T> =
    if (draft) {
        MainDraftContextData(
            if (id != null) IdentifiedAssetId(id) else TemporaryAssetId(),
            hasOfficial = false,
            originBranch = LayoutBranch.main,
        )
    } else {
        MainOfficialContextData(if (id != null) IdentifiedAssetId(id) else TemporaryAssetId())
    }

fun joints(seed: Int = 1, count: Int = 5) = (1..count).map { jointSeed -> switchJoint(seed * 100 + jointSeed) }

fun switchJoint(seed: Int) =
    LayoutSwitchJoint(
        number = JointNumber(1 + seed % 5),
        location = Point(seed * 0.01, 1000.0 + seed * 0.01),
        locationAccuracy = getSomeNullableValue<LocationAccuracy>(seed),
    )

fun switchJoint(number: Int, location: Point) =
    LayoutSwitchJoint(number = JointNumber(number), location = location, locationAccuracy = null)

fun kmPost(
    trackNumberId: IntId<LayoutTrackNumber>?,
    km: KmNumber,
    roughLayoutLocation: Point? = Point(1.0, 1.0),
    gkLocation: GeometryPoint? = null,
    draft: Boolean = false,
    state: LayoutState = LayoutState.IN_USE,
    gkLocationConfirmed: Boolean = false,
    gkLocationSource: KmPostGkLocationSource = KmPostGkLocationSource.MANUAL,
    sourceId: IntId<GeometryKmPost>? = null,
    contextData: LayoutContextData<LayoutKmPost> = createMainContext(null, draft),
): LayoutKmPost {

    return LayoutKmPost(
        trackNumberId = trackNumberId,
        kmNumber = km,
        state = state,
        sourceId = sourceId,
        contextData = contextData,
        gkLocation =
            if (gkLocation != null || roughLayoutLocation != null)
                LayoutKmPostGkLocation(
                    location =
                        if (gkLocation == null && roughLayoutLocation != null) {
                            transformFromLayoutToGKCoordinate(roughLayoutLocation)
                        } else gkLocation!!,
                    confirmed = gkLocationConfirmed,
                    source = gkLocationSource,
                )
            else null,
    )
}

fun segmentPoint(x: Double, y: Double, m: Double = 1.0) = SegmentPoint(x, y, null, m, null)

fun alignmentPoint(x: Double, y: Double, m: Double = 1.0) = AlignmentPoint(x, y, null, m, null)

fun rawPoints(count: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    toSegmentPoints(
        to3DMPoints(
            (1..count).map { pointNumber ->
                point2d(minX, maxX, minY, maxY, (pointNumber - 1).toDouble() / (count - 1))
            }
        )
    )

fun points(count: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    toAlignmentPoints(
        to3DMPoints(
            (1..count).map { pointNumber ->
                point2d(minX, maxX, minY, maxY, (pointNumber - 1).toDouble() / (count - 1))
            }
        )
    )

fun segmentPoint(
    minX: Double,
    maxX: Double,
    minY: Double,
    maxY: Double,
    m: Double,
    fraction: Double = rand.nextDouble(),
) = AlignmentPoint(valueBetween(minX, maxX, fraction), valueBetween(minY, maxY, fraction), null, m, null)

fun point2d(minX: Double, maxX: Double, minY: Double, maxY: Double, fraction: Double = rand.nextDouble()) =
    Point(valueBetween(minX, maxX, fraction), valueBetween(minY, maxY, fraction))

fun valueBetween(min: Double, max: Double, fraction: Double = rand.nextDouble()) = min + (max - min) * fraction

fun someKmNumber(): KmNumber {
    val allowedChars = ('A'..'Z').map(Char::toString)
    return KmNumber(nextInt(10000), if (Random.nextBoolean()) allowedChars.random() else null)
}

fun offsetAlignment(alignment: LayoutAlignment, amount: Point) =
    alignment.copy(segments = alignment.segments.map { origSegment -> offsetSegment(origSegment, amount) })

fun offsetSegment(segment: LayoutSegment, amount: Point): LayoutSegment {
    val newPoints = toSegmentPoints(*(segment.alignmentPoints.map { p -> p + amount }.toTypedArray()))
    return segment.copy(geometry = segment.geometry.withPoints(newPoints))
}

fun externalIdForLocationTrack(): Oid<LocationTrack> {
    val first = nextInt(100, 999)
    val second = nextInt(100, 999)
    val third = nextInt(100, 999)
    val fourth = nextInt(100, 999)

    return Oid("$first.$second.$third.$fourth")
}

fun externalIdForTrackNumber(): Oid<LayoutTrackNumber> {
    val first = nextInt(100, 999)
    val second = nextInt(100, 999)
    val third = nextInt(100, 999)
    val fourth = nextInt(100, 999)

    return Oid("$first.$second.$third.$fourth")
}

fun switchLinkingAtStart(
    locationTrackId: DomainId<LocationTrack>,
    alignment: LayoutAlignment,
    segmentIndex: Int,
    jointNumber: Int = 1,
) = switchLinkingAtStart(locationTrackId, alignment.segments, segmentIndex, jointNumber)

fun switchLinkingAtStart(
    locationTrackId: DomainId<LocationTrack>,
    segments: List<LayoutSegment>,
    segmentIndex: Int,
    jointNumber: Int = 1,
) = switchLinkingAt(locationTrackId, segmentIndex, segments[segmentIndex].alignmentPoints.first().m, jointNumber)

fun switchLinkingAtEnd(
    locationTrackId: DomainId<LocationTrack>,
    alignment: LayoutAlignment,
    segmentIndex: Int,
    jointNumber: Int = 1,
) = switchLinkingAtEnd(locationTrackId, alignment.segments, segmentIndex, jointNumber)

fun switchLinkingAtEnd(
    locationTrackId: DomainId<LocationTrack>,
    segments: List<LayoutSegment>,
    segmentIndex: Int,
    jointNumber: Int = 1,
) = switchLinkingAt(locationTrackId, segmentIndex, segments[segmentIndex].alignmentPoints.last().m, jointNumber)

fun switchLinkingAtHalf(
    locationTrackId: DomainId<LocationTrack>,
    alignment: LayoutAlignment,
    segmentIndex: Int,
    jointNumber: Int = 1,
) = switchLinkingAtHalf(locationTrackId, alignment.segments, segmentIndex, jointNumber)

fun switchLinkingAtHalf(
    locationTrackId: DomainId<LocationTrack>,
    segments: List<LayoutSegment>,
    segmentIndex: Int,
    jointNumber: Int = 1,
) =
    switchLinkingAt(
        locationTrackId,
        segmentIndex,
        segments[segmentIndex].let { s -> (s.endM + s.startM) / 2 },
        jointNumber,
    )

fun switchLinkingAt(locationTrackId: DomainId<LocationTrack>, segmentIndex: Int, m: Double, jointNumber: Int = 1) =
    FittedSwitchJointMatch(
        locationTrackId = locationTrackId as IntId<LocationTrack>,
        segmentIndex = segmentIndex,
        m = m,
        alignmentId = null,
        distance = 0.1,
        switchJoint = SwitchJoint(JointNumber(jointNumber), Point(0.0, 0.0)),
        distanceToAlignment = 0.1,
        matchType = SuggestedSwitchJointMatchType.LINE,
    )

fun layoutDesign(
    name: String = "foo",
    estimatedCompletion: LocalDate = LocalDate.parse("2022-02-02"),
    designState: DesignState = DesignState.ACTIVE,
) = LayoutDesignSaveRequest(LayoutDesignName(name), estimatedCompletion, designState)

fun <T> someRowVersion() = RowVersion(IntId<T>(1), 1)

fun geocodingContextCacheKey(
    trackNumberId: IntId<LayoutTrackNumber>,
    trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    referenceLineVersion: LayoutRowVersion<ReferenceLine>,
    vararg kmPostVersions: LayoutRowVersion<LayoutKmPost>,
) =
    LayoutGeocodingContextCacheKey(
        trackNumberId = trackNumberId,
        trackNumberVersion = trackNumberVersion,
        referenceLineVersion = referenceLineVersion,
        kmPostVersions = kmPostVersions.toList().sortedBy { rv -> rv.id.intValue },
    )

fun publishedVersions(
    trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>> = listOf(),
    referenceLines: List<LayoutRowVersion<ReferenceLine>> = listOf(),
    locationTracks: List<LayoutRowVersion<LocationTrack>> = listOf(),
    switches: List<LayoutRowVersion<LayoutSwitch>> = listOf(),
    kmPosts: List<LayoutRowVersion<LayoutKmPost>> = listOf(),
) = PublishedVersions(trackNumbers, referenceLines, locationTracks, switches, kmPosts)
