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
import fi.fta.geoviite.infra.common.SwitchNameParts
import fi.fta.geoviite.infra.common.SwitchNamePrefix
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
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.linking.TrackNumberSaveRequest
import fi.fta.geoviite.infra.linking.switches.FittedSwitchJointMatch
import fi.fta.geoviite.infra.linking.switches.RelativeDirection
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
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.boundingBoxCombining
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.publication.Change
import fi.fta.geoviite.infra.publication.PublishedVersions
import fi.fta.geoviite.infra.ratko.model.OperationalPointType
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureCurve
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureData
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureLine
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.util.FreeText
import java.time.LocalDate
import kotlin.math.ceil
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

private const val SEED = 123321L

private val rand = Random(SEED)

fun switchStructureYV60_300_1_9(): SwitchStructure {
    return SwitchStructure(
        version = RowVersion(IntId(55), 1),
        data =
            SwitchStructureData(
                type = SwitchType.of("YV60-300-1:9-O"),
                presentationJointNumber = JointNumber(1),
                joints =
                    setOf(
                        SwitchStructureJoint(JointNumber(1), Point(0.0, 0.0)),
                        SwitchStructureJoint(JointNumber(5), Point(16.615, 0.0)),
                        SwitchStructureJoint(JointNumber(2), Point(34.430, 0.0)),
                        SwitchStructureJoint(JointNumber(3), Point(34.321, -1.967)),
                    ),
                alignments =
                    listOf(
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                            elements =
                                listOf(
                                    SwitchStructureLine(Point(0.0, 0.0), Point(16.615, 0.0)),
                                    SwitchStructureLine(Point(16.615, 0.0), Point(34.430, 0.0)),
                                ),
                        ),
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                            elements =
                                listOf(
                                    SwitchStructureCurve(Point(0.0, 0.0), Point(33.128, -1.835), radius = 300.0),
                                    SwitchStructureLine(Point(33.128, -1.835), Point(34.321, -1.967)),
                                ),
                        ),
                    ),
            ),
    )
}

fun switchStructureRR54_4x1_9() =
    SwitchStructure(
        version = RowVersion(IntId(133), 1),
        data =
            SwitchStructureData(
                type = SwitchType.of("RR54-4x1:9"),
                presentationJointNumber = JointNumber(5),
                joints =
                    setOf(
                        SwitchStructureJoint(JointNumber(1), Point(-5.075, -1.142)),
                        SwitchStructureJoint(JointNumber(5), Point(0.0, 0.0)),
                        SwitchStructureJoint(JointNumber(2), Point(5.075, 1.142)),
                        SwitchStructureJoint(JointNumber(4), Point(-5.075, 1.142)),
                        SwitchStructureJoint(JointNumber(3), Point(5.075, -1.142)),
                    ),
                alignments =
                    listOf(
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                            elements =
                                listOf(
                                    SwitchStructureLine(Point(-5.075, -1.142), Point(0.0, 0.0)),
                                    SwitchStructureLine(Point(0.0, 0.0), Point(5.075, 1.142)),
                                ),
                        ),
                        SwitchStructureAlignment(
                            jointNumbers = listOf(JointNumber(4), JointNumber(5), JointNumber(3)),
                            elements =
                                listOf(
                                    SwitchStructureLine(Point(-5.075, 1.142), Point(0.0, 0.0)),
                                    SwitchStructureLine(Point(0.0, 0.0), Point(-5.075, 1.142)),
                                ),
                        ),
                    ),
            ),
    )

fun switchAndMatchingAlignments(
    trackNumberId: IntId<LayoutTrackNumber>,
    structure: SwitchStructure,
    draft: Boolean,
): Pair<LayoutSwitch, List<Pair<LocationTrack, LocationTrackGeometry>>> {
    val switchId = IntId<LayoutSwitch>(1)
    val jointLocations = mutableMapOf<JointNumber, Point>()
    val tracks =
        structure.alignments.map { alignment ->
            val alignmentPoints =
                alignment.jointNumbers.map { jointNumber ->
                    val point =
                        jointLocations.computeIfAbsent(jointNumber) { number ->
                            val joint = structure.joints.find { structureJoint -> structureJoint.number == number }
                            requireNotNull(joint?.location) { "No such joint in structure" }
                        }
                    jointNumber to point
                }
            val track = locationTrack(trackNumberId, draft = draft)
            val geometry =
                trackGeometry(
                    alignmentPoints.zipWithNext { start, end ->
                        val (startJoint, startPoint) = start
                        val (endJoint, endPoint) = end
                        val length = lineLength(startPoint, endPoint)
                        edge(
                            startInnerSwitch = switchLinkYV(switchId, startJoint.intValue),
                            endInnerSwitch = switchLinkYV(switchId, endJoint.intValue),
                            segments =
                                listOf(
                                    segment(points(length.toInt(), startPoint.x..endPoint.x, startPoint.y..endPoint.y))
                                ),
                        )
                    }
                )
            track to geometry
        }
    val switch =
        switch(
            id = switchId,
            structureId = structure.id,
            joints =
                jointLocations.map { (number, point) ->
                    LayoutSwitchJoint(number, SwitchJointRole.of(structure, number), point, null)
                },
            draft = draft,
            stateCategory = LayoutStateCategory.EXISTING,
        )
    return switch to tracks
}

fun edgesFromSwitchStructure(
    start: IPoint,
    switchId: IntId<LayoutSwitch>,
    structure: SwitchStructure,
    line: List<Int>,
): List<LayoutEdge> {
    val expectedJoints = line.map(::JointNumber)
    val alignment = requireNotNull(structure.alignments.find { a -> a.jointNumbers == expectedJoints })

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
                edge(
                    startInnerSwitch = SwitchLink(switchId, jointNumbers[0], structure),
                    endInnerSwitch = SwitchLink(switchId, jointNumbers[1], structure),
                    segments =
                        listOf(
                            segment(toSegmentPoints(start + elements[0].start, start + elements[0].end)),
                            segment(toSegmentPoints(start + elements[1].start, start + elements[1].end)),
                        ),
                )
            )

        2 to 3 ->
            listOf(
                edge(
                    startInnerSwitch = SwitchLink(switchId, jointNumbers[0], structure),
                    endInnerSwitch = SwitchLink(switchId, jointNumbers[1], structure),
                    segments =
                        listOf(
                            segment(toSegmentPoints(start + elements[0].start, start + elements[0].end)),
                            segment(toSegmentPoints(start + elements[1].start, start + elements[1].end)),
                            segment(toSegmentPoints(start + elements[2].start, start + elements[2].end)),
                        ),
                )
            )

        else ->
            combineEdges(
                alignment.elements.mapIndexed { i, e ->
                    edge(
                        startInnerSwitch = SwitchLink(switchId, jointNumbers[i], structure),
                        endInnerSwitch = SwitchLink(switchId, jointNumbers[i + 1], structure),
                        segments = listOf(segment(toSegmentPoints(start + e.start, start + e.end))),
                    )
                }
            )
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
                m = LineM<SegmentM>(0.0),
                cant = cant?.let { cantRange -> valueOnRange(cantRange, i, count) },
            )
        }
    var cumulativeM = LineM<SegmentM>(0.0)
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
        length = alignment?.length ?: LineM<ReferenceLineM>(0.0),
        alignmentVersion = alignmentVersion,
        contextData = contextData,
    )

private var locationTrackNameCounter = 0

fun locationTrackAndGeometry(
    vararg segments: LayoutSegment,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    id: IntId<LocationTrack>? = null,
    draft: Boolean,
): Pair<LocationTrack, LocationTrackGeometry> =
    locationTrackAndGeometry(
        IntId(0),
        segments.toList(),
        id = id,
        name = name,
        description = description,
        draft = draft,
    )

fun locationTrackAndGeometry(
    trackNumberId: IntId<LayoutTrackNumber>,
    vararg segments: LayoutSegment,
    name: String = "T001 ${locationTrackNameCounter++}",
    description: String = "test-alignment 001",
    duplicateOf: IntId<LocationTrack>? = null,
    state: LocationTrackState = LocationTrackState.IN_USE,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
): Pair<LocationTrack, LocationTrackGeometry> =
    locationTrackAndGeometry(
        trackNumberId,
        segments.toList(),
        name = name,
        description = description,
        duplicateOf = duplicateOf,
        state = state,
        id = id,
        draft = draft,
    )

fun locationTrackAndGeometry(
    trackNumberId: IntId<LayoutTrackNumber>,
    segments: List<LayoutSegment>,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
    name: String = "T001 ${locationTrackNameCounter++}",
    type: LocationTrackType = LocationTrackType.SIDE,
    description: String = "test-alignment 001",
    duplicateOf: IntId<LocationTrack>? = null,
    state: LocationTrackState = LocationTrackState.IN_USE,
    ownerId: IntId<LocationTrackOwner> = IntId(1),
): Pair<LocationTrack, LocationTrackGeometry> {
    val geometry = trackGeometryOfSegments(segments)
    val locationTrack =
        locationTrack(
            trackNumberId = trackNumberId,
            geometry = geometry,
            id = id,
            draft = draft,
            name = name,
            type = type,
            description = description,
            duplicateOf = duplicateOf,
            state = state,
            ownerId = ownerId,
        )
    return locationTrack to geometry
}

fun locationTrack(
    trackNumberId: IntId<LayoutTrackNumber>,
    geometry: LocationTrackGeometry = TmpLocationTrackGeometry.empty,
    id: IntId<LocationTrack>? = null,
    draft: Boolean = false,
    name: String = "T001 ${locationTrackNameCounter++}",
    nameStructure: LocationTrackNameStructure = trackNameStructure(name, LocationTrackNamingScheme.FREE_TEXT, null),
    description: String = "test-alignment 001",
    descriptionStructure: LocationTrackDescriptionStructure = trackDescriptionStructure(description),
    type: LocationTrackType = LocationTrackType.SIDE,
    state: LocationTrackState = LocationTrackState.IN_USE,
    topologicalConnectivity: TopologicalConnectivityType = TopologicalConnectivityType.NONE,
    duplicateOf: IntId<LocationTrack>? = null,
    ownerId: IntId<LocationTrackOwner> = IntId(1),
    contextData: LayoutContextData<LocationTrack> = createMainContext(id, draft),
    startSwitch: IntId<LayoutSwitch>? = null,
    endSwitch: IntId<LayoutSwitch>? = null,
) =
    locationTrack(
        trackNumberId = trackNumberId,
        geometry = geometry,
        contextData = contextData,
        name = name,
        nameStructure = nameStructure,
        description = description,
        descriptionStructure = descriptionStructure,
        type = type,
        state = state,
        topologicalConnectivity = topologicalConnectivity,
        duplicateOf = duplicateOf,
        ownerId = ownerId,
        startSwitch = startSwitch,
        endSwitch = endSwitch,
    )

fun trackDescriptionStructure(
    descriptionBase: String = "Test track description",
    descriptionSuffix: LocationTrackDescriptionSuffix = LocationTrackDescriptionSuffix.NONE,
) = LocationTrackDescriptionStructure(LocationTrackDescriptionBase(descriptionBase), descriptionSuffix)

fun trackNameStructure(
    freeText: String = "T001 ${locationTrackNameCounter++}",
    scheme: LocationTrackNamingScheme = LocationTrackNamingScheme.FREE_TEXT,
    specifier: LocationTrackNameSpecifier? = null,
): LocationTrackNameStructure =
    LocationTrackNameStructure.of(
        scheme = scheme,
        freeText = LocationTrackNameFreeTextPart(freeText),
        specifier = specifier,
    )

fun locationTrack(
    trackNumberId: IntId<LayoutTrackNumber>,
    geometry: LocationTrackGeometry = TmpLocationTrackGeometry.empty,
    contextData: LayoutContextData<LocationTrack>,
    name: String = "T001 ${locationTrackNameCounter++}",
    nameStructure: LocationTrackNameStructure = trackNameStructure(name),
    description: String = "test-alignment 001",
    descriptionStructure: LocationTrackDescriptionStructure = trackDescriptionStructure(description),
    type: LocationTrackType = LocationTrackType.SIDE,
    state: LocationTrackState = LocationTrackState.IN_USE,
    topologicalConnectivity: TopologicalConnectivityType = TopologicalConnectivityType.NONE,
    duplicateOf: IntId<LocationTrack>? = null,
    ownerId: IntId<LocationTrackOwner> = IntId(1),
    startSwitch: IntId<LayoutSwitch>? = null,
    endSwitch: IntId<LayoutSwitch>? = null,
) =
    LocationTrack(
        name = AlignmentName(name),
        nameStructure = nameStructure,
        description = FreeText(description),
        descriptionStructure = descriptionStructure,
        type = type,
        state = state,
        trackNumberId = trackNumberId,
        sourceId = null,
        boundingBox = geometry.boundingBox,
        segmentCount = geometry.segments.size,
        length = geometry.length,
        startSwitchId = startSwitch,
        endSwitchId = endSwitch,
        duplicateOf = duplicateOf,
        topologicalConnectivity = topologicalConnectivity,
        ownerId = ownerId,
        contextData = contextData,
    )

private var operationalPointNameCounter = 0
private var operationalPointAbbreviationCounter = 0
private var uicCodeCounter = 0

fun operationalPoint(
    name: String = "Operational point ${operationalPointNameCounter++}",
    abbreviation: String = "OP${operationalPointAbbreviationCounter++}",
    uicCode: String = "${100000 + uicCodeCounter++}",
    rinfType: Int? = null,
    raideType: OperationalPointType? = null,
    location: Point? = null,
    polygon: Polygon? = null,
    draft: Boolean = false,
    state: OperationalPointState = OperationalPointState.IN_USE,
    origin: OperationalPointOrigin = OperationalPointOrigin.RATKO,
    id: IntId<OperationalPoint>? = null,
    contextData: LayoutContextData<OperationalPoint> = createMainContext(id, draft),
) =
    OperationalPoint(
        OperationalPointName(name),
        OperationalPointAbbreviation(abbreviation),
        UicCode(uicCode),
        rinfType,
        raideType,
        polygon,
        location,
        state,
        origin,
        contextData,
    )

fun <T> someOid() = Oid<T>("${nextInt(10, 1000)}.${nextInt(10, 1000)}.${nextInt(10, 1000)}")

fun someAlignment() = alignment(someSegment())

fun someTrackGeometry() = trackGeometryOfSegments(someSegment())

fun alignmentFromPoints(vararg points: Point) = alignment(segment(*points))

fun alignment(vararg segments: LayoutSegment) = alignment(segments.toList())

fun alignment(segments: List<LayoutSegment>) = LayoutAlignment(segments = segments)

fun trackGeometryOfSegments(vararg segments: LayoutSegment): TmpLocationTrackGeometry =
    trackGeometryOfSegments(segments.toList())

fun trackGeometryOfSegments(segments: List<LayoutSegment>): TmpLocationTrackGeometry =
    if (segments.isEmpty()) TmpLocationTrackGeometry.empty
    else
        trackGeometry(
            listOf(
                TmpLayoutEdge(
                    startNode = PlaceHolderNodeConnection,
                    endNode = PlaceHolderNodeConnection,
                    segments = segments,
                )
            )
        )

class BuildTrackTopology {
    constructor() {
        edges = listOf()
    }

    private constructor(edges: List<BuildTrackTopologyEdge>) {
        this.edges = edges
    }

    private val edges: List<BuildTrackTopologyEdge>

    fun edge(
        startInnerSwitch: SwitchLink? = null,
        startOuterSwitch: SwitchLink? = null,
        endInnerSwitch: SwitchLink? = null,
        endOuterSwitch: SwitchLink? = null,
    ) =
        BuildTrackTopology(
            edges = edges + BuildTrackTopologyEdge(startInnerSwitch, startOuterSwitch, endInnerSwitch, endOuterSwitch)
        )

    fun build(trackId: IntId<LocationTrack>? = null): TmpLocationTrackGeometry {
        var x = 0.0
        return TmpLocationTrackGeometry.of(
            edges.map { edgeInfo ->
                x += 2.0
                edge(
                    listOf(segment(Point(x, 0.0), Point(x + 2.0, 0.0))),
                    edgeInfo.startInnerSwitch,
                    edgeInfo.startOuterSwitch,
                    edgeInfo.endInnerSwitch,
                    edgeInfo.endOuterSwitch,
                )
            },
            trackId,
        )
    }
}

private data class BuildTrackTopologyEdge(
    val startInnerSwitch: SwitchLink?,
    val startOuterSwitch: SwitchLink?,
    val endInnerSwitch: SwitchLink?,
    val endOuterSwitch: SwitchLink?,
)

fun trackGeometry(vararg edges: LayoutEdge, trackId: IntId<LocationTrack>? = null): TmpLocationTrackGeometry =
    trackGeometry(edges.toList(), trackId)

fun trackGeometry(edges: List<LayoutEdge>, trackId: IntId<LocationTrack>? = null): TmpLocationTrackGeometry =
    TmpLocationTrackGeometry.of(edges, trackId)

fun edge(
    segments: List<LayoutSegment>,
    startInnerSwitch: SwitchLink? = null,
    startOuterSwitch: SwitchLink? = null,
    endInnerSwitch: SwitchLink? = null,
    endOuterSwitch: SwitchLink? = null,
) =
    TmpLayoutEdge(
        startNode =
            if (startInnerSwitch != null || startOuterSwitch != null)
                NodeConnection.switch(inner = startInnerSwitch, outer = startOuterSwitch)
            else PlaceHolderNodeConnection,
        endNode =
            if (endInnerSwitch != null || endOuterSwitch != null)
                NodeConnection.switch(inner = endInnerSwitch, outer = endOuterSwitch)
            else PlaceHolderNodeConnection,
        segments = segments,
    )

fun verticalEdge(startPoint: IPoint, segmentCount: Int = 3, pointOffset: Double = 10.0): LayoutEdge {
    return edge(
        (0..<segmentCount).map { idx ->
            val start = startPoint + Point(idx * pointOffset, 0.0)
            val end = start + Point(pointOffset, 0.0)
            segment(start, end)
        }
    )
}

fun switchLinkYV(switchId: IntId<LayoutSwitch>, jointNumber: Int) =
    SwitchLink(switchId, JointNumber(jointNumber), switchStructureYV60_300_1_9())

fun switchLinkRR(switchId: IntId<LayoutSwitch>, jointNumber: Int) =
    SwitchLink(switchId, JointNumber(jointNumber), switchStructureRR54_4x1_9())

fun switchLinkKV(switchId: IntId<LayoutSwitch>, jointNumber: Int) =
    SwitchLink(
        switchId,
        when (jointNumber) {
            1 -> SwitchJointRole.MAIN
            2 -> SwitchJointRole.CONNECTION
            3 -> SwitchJointRole.CONNECTION
            4 -> SwitchJointRole.CONNECTION
            5 -> SwitchJointRole.MATH
            6 -> SwitchJointRole.MATH
            else -> throw IllegalArgumentException("Invalid joint number for KV: $jointNumber")
        },
        JointNumber(jointNumber),
    )

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
                length = LineM(segments.sumOf(PlanLayoutSegment::length)),
                boundingBox = boundingBoxCombining(segments.mapNotNull(PlanLayoutSegment::boundingBox)),
            ),
        segments = segments,
    )

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

fun segment(
    vararg points: IPoint,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStartM: Double? = null,
) =
    segment(
        toSegmentPoints(to3DMPoints(points.asList())),
        source = source,
        sourceStartM = sourceStartM,
        sourceId = sourceId,
    )

fun segment(
    points: List<SegmentPoint>,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStartM: Double? = null,
    resolution: Int = 1,
) =
    LayoutSegment(
        geometry = SegmentGeometry(segmentPoints = points, resolution = resolution),
        sourceId = sourceId as IndexedId?,
        sourceStartM = sourceStartM?.let(LayoutSegment::sourceStartM),
        source = source,
    )

fun mapSegment(
    vararg points: Point3DM<SegmentM>,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStartM: Double? = null,
    source: GeometrySource = PLAN,
) =
    mapSegment(
        points = toSegmentPoints(points.asList()),
        sourceId = sourceId,
        sourceStartM = sourceStartM,
        source = source,
    )

fun mapSegment(
    points: List<SegmentPoint>,
    resolution: Int = 1,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStartM: Double? = null,
    source: GeometrySource = PLAN,
    id: DomainId<LayoutSegment> = StringId(),
) =
    PlanLayoutSegment(
        geometry = SegmentGeometry(segmentPoints = points, resolution = resolution),
        pointCount = points.size,
        sourceId = sourceId,
        sourceStartM = sourceStartM?.let(LayoutSegment::sourceStartM),
        source = source,
        id = id,
    )

fun splitSegment(segment: LayoutSegment, numberOfParts: Int): List<LayoutSegment> {
    val allPoints = segment.segmentPoints
    val indexRange = 0..allPoints.lastIndex
    val pointsPerSegment = allPoints.count() / numberOfParts.toDouble()
    return indexRange
        .groupBy { index -> (index / pointsPerSegment).toInt() }
        .map { (_, groupIndexRange) ->
            val points = allPoints.subList(0.coerceAtLeast(groupIndexRange.first() - 1), groupIndexRange.last() + 1)
            segment(points = points.map { Point(it) }.toTypedArray())
        }
}

fun toSegmentPoints(vararg points: IPoint) = toSegmentPoints(to3DMPoints(points.asList()))

fun toSegmentPoints(vararg points: Point3DZ) = toSegmentPoints(to3DMPoints(points.asList()))

fun toSegmentPoints(vararg points: IPoint3DM<SegmentM>) = toSegmentPoints(points.asList())

fun toSegmentPoints(points: List<IPoint3DM<SegmentM>>) =
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

fun <M : AlignmentM<M>> toAlignmentPoints(vararg points: IPoint) = toAlignmentPoints(to3DMPoints<M>(points.asList()))

fun <M : AlignmentM<M>> toAlignmentPoints(vararg points: Point3DZ) = toAlignmentPoints(to3DMPoints<M>(points.asList()))

fun <M : AlignmentM<M>> toAlignmentPoints(vararg points: IPoint3DM<M>) = toAlignmentPoints(points.asList())

fun <M : AlignmentM<M>> toAlignmentPoints(points: List<IPoint3DM<M>>) =
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

fun <M : AnyM<M>> to3DMPoints(points: List<IPoint>, start: Double = 0.0): List<IPoint3DM<M>> {
    val pointsWithDistance =
        points.mapIndexed { index, point ->
            val distance = points.getOrNull(index - 1)?.let { prev -> lineLength(prev, point) } ?: 0.0
            point to distance
        }
    return pointsWithDistance.mapIndexed { index, (point, _) ->
        val m = pointsWithDistance.subList(0, index + 1).foldRight(start) { (_, distance), acc -> acc + distance }
        when (point) {
            is AlignmentPoint<*> -> AlignmentPoint(point.x, point.y, point.z, LineM(m), point.cant)
            is Point3DZ -> Point4DZM(point.x, point.y, point.z, LineM(m))
            else -> Point3DM(point.x, point.y, m)
        }
    }
}

fun fixMValues(points: List<SegmentPoint>): List<SegmentPoint> {
    var m = LineM<SegmentM>(0.0)
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

fun segment(from: IPoint, to: IPoint, startM: Double = 0.0): LayoutSegment {
    return segment(
        toSegmentPoints(to3DMPoints((listOf(from) + middlePoints(from, to) + listOf(to)).distinct(), startM))
    )
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
    return endPoints.distinct().dropLast(1).mapIndexed { index, start -> segment(start, endPoints[index + 1]) }
}

fun switchFromDbStructure(
    name: String,
    switchStart: IPoint,
    structure: SwitchStructure,
    draft: Boolean = false,
): LayoutSwitch =
    switch(
        name = name,
        structureId = structure.id,
        draft = draft,
        joints =
            structure.joints.map { j ->
                LayoutSwitchJoint(
                    number = j.number,
                    role = SwitchJointRole.of(structure, j.number),
                    location = switchStart + j.location,
                    locationAccuracy = null,
                )
            },
    )

private var switchNameCounter = 0

fun switch(
    structureId: IntId<SwitchStructure> = switchStructureYV60_300_1_9().id,
    joints: List<LayoutSwitchJoint> = listOf(),
    name: String = "TV${switchNameCounter++}",
    stateCategory: LayoutStateCategory = LayoutStateCategory.EXISTING,
    id: IntId<LayoutSwitch>? = null,
    draft: Boolean = false,
    ownerId: IntId<SwitchOwner> = switchOwnerVayla().id,
    contextData: LayoutContextData<LayoutSwitch> = createMainContext(id, draft),
    draftOid: Oid<LayoutSwitch>? = null,
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
        draftOid = draftOid,
    )

fun parsedSwitchName(prefix: String, shortNumberPart: String) =
    SwitchNameParts(SwitchNamePrefix(prefix), SwitchName(shortNumberPart))

fun <T : LayoutAsset<T>> createMainContext(id: IntId<T>?, draft: Boolean): LayoutContextData<T> =
    if (draft) {
        MainDraftContextData(
            if (id != null) IdentifiedAssetId(id) else TemporaryAssetId(),
            originBranch = LayoutBranch.main,
        )
    } else {
        MainOfficialContextData(if (id != null) IdentifiedAssetId(id) else TemporaryAssetId())
    }

fun joints(seed: Int = 1, count: Int = 5) = (1..count).map { jointSeed -> switchJoint(seed * 100 + jointSeed) }

fun switchJoint(seed: Int) =
    LayoutSwitchJoint(
        number = JointNumber(1 + seed % 5),
        role = getSomeValue(seed),
        location = Point(seed * 0.01, 1000.0 + seed * 0.01),
        locationAccuracy = getSomeNullableValue<LocationAccuracy>(seed),
    )

fun switchJoint(
    number: Int,
    location: Point,
    type: SwitchJointRole = SwitchJointRole.of(switchStructureYV60_300_1_9(), JointNumber(number)),
    accuracy: LocationAccuracy? = null,
) = LayoutSwitchJoint(number = JointNumber(number), role = type, location = location, locationAccuracy = accuracy)

fun kmPost(
    trackNumberId: IntId<LayoutTrackNumber>?,
    km: KmNumber,
    gkLocation: LayoutKmPostGkLocation? = null,
    draft: Boolean = false,
    state: LayoutState = LayoutState.IN_USE,
    sourceId: IntId<GeometryKmPost>? = null,
    contextData: LayoutContextData<LayoutKmPost> = createMainContext(null, draft),
): LayoutKmPost {
    return LayoutKmPost(
        trackNumberId = trackNumberId,
        kmNumber = km,
        state = state,
        sourceId = sourceId,
        contextData = contextData,
        gkLocation = gkLocation,
    )
}

fun kmPostGkLocation(
    gkLocation: GeometryPoint,
    gkLocationSource: KmPostGkLocationSource = KmPostGkLocationSource.MANUAL,
    gkLocationConfirmed: Boolean = false,
) = LayoutKmPostGkLocation(location = gkLocation, confirmed = gkLocationConfirmed, source = gkLocationSource)

fun kmPostGkLocation(x: Double, y: Double) = kmPostGkLocation(Point(x, y))

fun kmPostGkLocation(
    roughLayoutLocation: Point,
    gkLocationSource: KmPostGkLocationSource = KmPostGkLocationSource.FROM_LAYOUT,
    gkLocationConfirmed: Boolean = false,
) =
    LayoutKmPostGkLocation(
        location = transformFromLayoutToGKCoordinate(roughLayoutLocation),
        confirmed = gkLocationConfirmed,
        source = gkLocationSource,
    )

fun segmentPoint(x: Double, y: Double, m: Double = 1.0) = SegmentPoint(x, y, null, LineM(m), null)

fun <M : AlignmentM<M>> alignmentPoint(x: Double, y: Double, m: Double = 1.0) =
    AlignmentPoint(x, y, null, LineM<M>(m), null)

fun locationTrackPoint(x: Double, y: Double, m: Double) = AlignmentPoint(x, y, null, LineM<LocationTrackM>(m), null)

fun rawPoints(count: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    toSegmentPoints(
        to3DMPoints(
            (1..count).map { pointNumber ->
                point2d(minX, maxX, minY, maxY, (pointNumber - 1).toDouble() / (count - 1))
            }
        )
    )

fun <M : AlignmentM<M>> points(count: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    toAlignmentPoints(
        to3DMPoints<M>(
            (1..count).map { pointNumber ->
                point2d(minX, maxX, minY, maxY, (pointNumber - 1).toDouble() / (count - 1))
            }
        )
    )

fun point2d(minX: Double, maxX: Double, minY: Double, maxY: Double, fraction: Double = rand.nextDouble()) =
    Point(valueBetween(minX, maxX, fraction), valueBetween(minY, maxY, fraction))

fun valueBetween(min: Double, max: Double, fraction: Double = rand.nextDouble()) = min + (max - min) * fraction

fun someKmNumber(): KmNumber {
    val allowedChars = ('A'..'Z').map(Char::toString)
    return KmNumber(nextInt(10000), if (Random.nextBoolean()) allowedChars.random() else null)
}

fun offsetAlignment(alignment: LayoutAlignment, amount: Point) =
    alignment.copy(segments = alignment.segments.map { origSegment -> offsetSegment(origSegment, amount) })

fun offsetGeometry(geometry: LocationTrackGeometry, amount: Point): LocationTrackGeometry =
    TmpLocationTrackGeometry.of(edges = geometry.edges.map { edge -> offsetEdge(edge, amount) }, geometry.trackId)

fun offsetEdge(edge: LayoutEdge, amount: Point): LayoutEdge {
    val newSegments = edge.segments.map { segment -> offsetSegment(segment, amount) }
    return TmpLayoutEdge(startNode = edge.startNode, endNode = edge.endNode, segments = newSegments)
}

fun offsetSegment(segment: LayoutSegment, amount: Point): LayoutSegment {
    val newPoints = toSegmentPoints(*(segment.segmentPoints.map { p -> p + amount }.toTypedArray()))
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

fun externalIdForSwitch(): Oid<LayoutSwitch> {
    val first = nextInt(100, 999)
    val second = nextInt(100, 999)
    val third = nextInt(100, 999)
    val fourth = nextInt(100, 999)

    return Oid("$first.$second.$third.$fourth")
}

fun switchLinkingAtStart(
    locationTrackId: DomainId<LocationTrack>,
    geometry: LocationTrackGeometry,
    segmentIndex: Int,
    jointNumber: Int,
) = switchLinkingAtStart(locationTrackId, geometry.segmentMValues, segmentIndex, jointNumber)

fun switchLinkingAtStart(
    locationTrackId: DomainId<LocationTrack>,
    segmentMs: List<Range<LineM<LocationTrackM>>>,
    segmentIndex: Int,
    jointNumber: Int,
) = switchLinkingAt(locationTrackId, segmentIndex, segmentMs[segmentIndex].min, jointNumber)

fun switchLinkingAtEnd(
    locationTrackId: DomainId<LocationTrack>,
    geometry: LocationTrackGeometry,
    segmentIndex: Int,
    jointNumber: Int,
) = switchLinkingAtEnd(locationTrackId, geometry.segmentMValues, segmentIndex, jointNumber)

fun switchLinkingAtEnd(
    locationTrackId: DomainId<LocationTrack>,
    segmentMs: List<Range<LineM<LocationTrackM>>>,
    segmentIndex: Int,
    jointNumber: Int,
) = switchLinkingAt(locationTrackId, segmentIndex, segmentMs[segmentIndex].max, jointNumber)

fun switchLinkingAtHalf(
    locationTrackId: DomainId<LocationTrack>,
    geometry: LocationTrackGeometry,
    segmentIndex: Int,
    jointNumber: Int,
) = switchLinkingAtHalf(locationTrackId, geometry.segmentMValues, segmentIndex, jointNumber)

fun switchLinkingAtHalf(
    locationTrackId: DomainId<LocationTrack>,
    segmentMs: List<Range<LineM<LocationTrackM>>>,
    segmentIndex: Int,
    jointNumber: Int,
) =
    switchLinkingAt(
        locationTrackId,
        segmentIndex,
        segmentMs[segmentIndex].let { m -> (m.max + m.min) / 2 },
        jointNumber,
    )

fun switchLinkingAt(
    locationTrackId: DomainId<LocationTrack>,
    segmentIndex: Int,
    m: LineM<LocationTrackM>,
    jointNumber: Int,
) =
    FittedSwitchJointMatch(
        locationTrackId = locationTrackId as IntId<LocationTrack>,
        segmentIndex = segmentIndex,
        mOnTrack = m,
        distance = 0.1,
        switchJoint = SwitchStructureJoint(JointNumber(jointNumber), Point(0.0, 0.0)),
        distanceToAlignment = 0.1,
        matchType = SuggestedSwitchJointMatchType.LINE,
        direction = RelativeDirection.Along,
        location = Point(0.0, 0.0),
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
    trackNumbers: List<Change<LayoutRowVersion<LayoutTrackNumber>>> = listOf(),
    referenceLines: List<Change<LayoutRowVersion<ReferenceLine>>> = listOf(),
    locationTracks: List<Change<LayoutRowVersion<LocationTrack>>> = listOf(),
    switches: List<Change<LayoutRowVersion<LayoutSwitch>>> = listOf(),
    kmPosts: List<Change<LayoutRowVersion<LayoutKmPost>>> = listOf(),
    operationalPoints: List<Change<LayoutRowVersion<OperationalPoint>>> = listOf(),
) = PublishedVersions(trackNumbers, referenceLines, locationTracks, switches, kmPosts, operationalPoints)

fun operationalPoint(
    name: String = "name",
    abbreviation: String = name,
    rinfType: Int? = 10,
    state: OperationalPointState = OperationalPointState.IN_USE,
    uicCode: String? = "1234",
    location: Point = Point(10.0, 10.0),
    polygon: Polygon = Polygon(Point(0.0, 0.0), Point(20.0, 0.0), Point(20.0, 20.0), Point(0.0, 20.0), Point(0.0, 0.0)),
    origin: OperationalPointOrigin = OperationalPointOrigin.GEOVIITE,
    draft: Boolean = true,
    contextData: LayoutContextData<OperationalPoint> = createMainContext(null, draft),
): OperationalPoint =
    OperationalPoint(
        name = OperationalPointName(name),
        OperationalPointAbbreviation(abbreviation),
        rinfType = rinfType,
        state = state,
        uicCode = uicCode?.let(::UicCode),
        location = location,
        raideType = null,
        polygon = polygon,
        origin = origin,
        contextData = contextData,
    )
