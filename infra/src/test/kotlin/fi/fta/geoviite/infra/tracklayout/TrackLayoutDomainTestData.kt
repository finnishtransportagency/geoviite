package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.getSomeNullableValue
import fi.fta.geoviite.infra.getSomeValue
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.GeometrySource.GENERATED
import fi.fta.geoviite.infra.tracklayout.GeometrySource.PLAN
import fi.fta.geoviite.infra.util.FreeText
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
        joints = listOf(
            SwitchJoint(JointNumber(1), Point(0.0, 0.0)),
            SwitchJoint(JointNumber(5), Point(16.615, 0.0)),
            SwitchJoint(JointNumber(2), Point(34.430, 0.0)),
            SwitchJoint(JointNumber(3), Point(34.321, -1.967)),
        ),
        alignments = listOf(
            SwitchAlignment(
                jointNumbers = listOf(JointNumber(1), JointNumber(5), JointNumber(2)),
                elements = listOf(
                    SwitchElementLine(
                        IndexedId(1, 1),
                        Point(0.0, 0.0),
                        Point(16.615, 0.0)
                    ),
                    SwitchElementLine(
                        IndexedId(1, 2),
                        Point(16.615, 0.0),
                        Point(34.430, 0.0)
                    )
                )
            ),
            SwitchAlignment(
                jointNumbers = listOf(JointNumber(1), JointNumber(3)),
                elements = listOf(
                    SwitchElementCurve(
                        IndexedId(2, 1),
                        Point(0.0, 0.0),
                        Point(33.128, -1.835),
                        radius = 300.0
                    ),
                    SwitchElementLine(
                        IndexedId(2, 2),
                        Point(33.128, -1.835),
                        Point(34.321, -1.967)
                    )
                )
            )
        )
    )
}

fun switchAndMatchingAlignments(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    structure: SwitchStructure
): Pair<TrackLayoutSwitch, List<Pair<LocationTrack, LayoutAlignment>>> {
    val switchId = IntId<TrackLayoutSwitch>(1)
    val jointLocations = mutableMapOf<JointNumber, Point>()
    val alignments = structure.alignments.map { alignment ->
        val alignmentPoints = alignment.jointNumbers.map { jointNumber ->
            val point = jointLocations.computeIfAbsent(jointNumber) { number ->
                val joint = structure.joints.find { structureJoint -> structureJoint.number == number }
                joint?.location ?: throw IllegalStateException("No such joint in structure")
            }
            jointNumber to point
        }
        val points = toTrackLayoutPoints(*(alignmentPoints.map { (_, point) -> point }.toTypedArray()))
        locationTrackAndAlignment(
            trackNumberId,
            segment(points).copy(
                switchId = switchId,
                startJointNumber = alignmentPoints.first().first,
                endJointNumber = alignmentPoints.last().first,
            )
        )
    }
    val switch = switch(
        structureId = structure.id as IntId,
        joints = jointLocations.map { (number, point) ->
            TrackLayoutSwitchJoint(number, point, null)
        },
    ).copy(id = switchId)
    return switch to alignments
}

fun switchOwnerVayla(): SwitchOwner {
    return SwitchOwner(
        id = IntId(1),
        name = MetaDataName("Väylävirasto"),
    )
}

fun points(
    count: Int,
    x: ClosedRange<Double>,
    y: ClosedRange<Double>,
    z: ClosedRange<Double>? = null,
    cant: ClosedRange<Double>? = null,
): List<LayoutPoint> {
    val points = (0 until count).map { i ->
        LayoutPoint(
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
    externalId: Oid<TrackLayoutTrackNumber>? = Oid(
        "${nextInt(10, 1000)}.${nextInt(10, 1000)}.${nextInt(10, 1000)}"
    ),
    state: LayoutState = LayoutState.IN_USE,
    id: DomainId<TrackLayoutTrackNumber> = StringId(),
) = TrackLayoutTrackNumber(
    id = id,
    number = number,
    description = FreeText(description),
    state = state,
    externalId = externalId,
).let { tn -> if (draft) draft(tn) else tn }

fun referenceLineAndAlignment(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    vararg segments: LayoutSegment,
): Pair<ReferenceLine, LayoutAlignment> =
    referenceLineAndAlignment(trackNumberId, segments.toList())


fun referenceLineAndAlignment(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    segments: List<LayoutSegment>,
    startAddress: TrackMeter = TrackMeter.ZERO,
): Pair<ReferenceLine, LayoutAlignment> {
    val alignment = alignment(segments)
    val referenceLine = referenceLine(trackNumberId = trackNumberId, alignment = alignment, startAddress = startAddress)
    return referenceLine to alignment
}

fun referenceLine(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    alignment: LayoutAlignment? = null,
    startAddress: TrackMeter = TrackMeter.ZERO,
    alignmentVersion: RowVersion<LayoutAlignment>? = null,
) = ReferenceLine(
    trackNumberId = trackNumberId,
    startAddress = startAddress,
    sourceId = null,
    boundingBox = alignment?.boundingBox,
    segmentCount = alignment?.segments?.size ?: 0,
    length = alignment?.length ?: 0.0,
    alignmentVersion = alignmentVersion,
)

fun locationTrackAndAlignment(
    vararg segments: LayoutSegment,
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment(IntId(0), segments.toList())


fun locationTrackAndAlignment(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    vararg segments: LayoutSegment,
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment(trackNumberId, segments.toList())


fun locationTrackAndAlignment(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    segments: List<LayoutSegment>,
    id: IntId<LocationTrack>? = null,
    draft: Draft<LocationTrack>? = null,
): Pair<LocationTrack, LayoutAlignment> {
    val alignment = alignment(segments)
    val locationTrack = locationTrack(trackNumberId, alignment, id, draft)
    return locationTrack to alignment
}

fun locationTrack(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    alignment: LayoutAlignment? = null,
    id: IntId<LocationTrack>? = null,
    draft: Draft<LocationTrack>? = null,
    name: String = "T001",
    description: String = "test-alignment 001",
    type: LocationTrackType = LocationTrackType.SIDE,
    state: LayoutState = LayoutState.IN_USE,
    externalId: Oid<LocationTrack>? = someOid(),
    alignmentVersion: RowVersion<LayoutAlignment>? = null,
) = LocationTrack(
    name = AlignmentName(name),
    description = FreeText(description),
    type = type,
    state = state,
    externalId = externalId,
    trackNumberId = trackNumberId,
    sourceId = null,
    boundingBox = alignment?.boundingBox,
    segmentCount = alignment?.segments?.size ?: 0,
    length = alignment?.length ?: 0.0,
    draft = draft,
    duplicateOf = null,
    topologicalConnectivity = TopologicalConnectivityType.START,
    topologyStartSwitch = null,
    topologyEndSwitch = null,
    alignmentVersion = alignmentVersion,
).let { lt -> if (id != null) lt.copy(id = id) else lt }

fun <T> someOid() = Oid<T>(
    "${nextInt(10, 1000)}.${nextInt(10, 1000)}.${nextInt(10, 1000)}"
)
fun alignment(vararg segments: LayoutSegment) = alignment(segments.toList())

fun alignment(segments: List<LayoutSegment>) =
    LayoutAlignment(
        segments = fixStartDistances(segments),
        sourceId = null,
    )

fun fixStartDistances(segments: List<LayoutSegment>): List<LayoutSegment> {
    var distance = 0.0
    return segments.map { s -> s.copy(start = distance).also { distance += s.length } }
}

fun locationTrackWithTwoSwitches(
    trackNumberId: IntId<TrackLayoutTrackNumber>,
    layoutSwitchId: IntId<TrackLayoutSwitch>,
    otherLayoutSwitchId: IntId<TrackLayoutSwitch>,
    locationTrackId: IntId<LocationTrack>? = null,
): Pair<LocationTrack, LayoutAlignment> {
    val segmentLength = 10.0
    val segments = (1..20).map { i ->
        segment(
            Point(i * segmentLength, 0.0),
            Point((i + 1) * segmentLength, 0.0),
            start = i * segmentLength
        )
    }
    val (locationTrack, alignment) = locationTrackAndAlignment(
        trackNumberId = trackNumberId,
        segments = segments,
        id = locationTrackId
    )
    return attachSwitches(
        locationTrack to alignment,
        layoutSwitchId to TargetSegmentStart(),
        otherLayoutSwitchId to TargetSegmentEnd()
    )
}

fun attachSwitchToStart(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<TrackLayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> = attachSwitchToStart(
    locationTrackAndAlignment.first, locationTrackAndAlignment.second, switchId
)

fun attachSwitchToStart(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>
): Pair<LocationTrack, LayoutAlignment> {
    if (alignment.segments.count() < 3)
        throw IllegalArgumentException("Alignment must contain at least 3 segments")
    return locationTrack to alignment.copy(
        segments = alignment.segments.mapIndexed { index, segment ->
            when (index) {
                0 -> segment.copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1)
                )
                1 -> segment.copy(
                    switchId = switchId
                )
                2 -> segment.copy(
                    switchId = switchId,
                    endJointNumber = JointNumber(2)
                )
                else -> segment
            }
        }
    )
}

fun attachSwitchToEnd(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<TrackLayoutSwitch>,
): Pair<LocationTrack, LayoutAlignment> = attachSwitchToEnd(
    locationTrackAndAlignment.first, locationTrackAndAlignment.second, switchId
)

fun attachSwitchToEnd(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>
): Pair<LocationTrack, LayoutAlignment> {
    val segmentCount = alignment.segments.count()
    if (segmentCount < 3)
        throw IllegalArgumentException("Alignment must contain at least 3 segments")
    return locationTrack to alignment.copy(
        segments = alignment.segments.mapIndexed { index, segment ->
            when (index) {
                segmentCount - 3 -> segment.copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(2)
                )
                segmentCount - 2 -> segment.copy(
                    switchId = switchId
                )
                segmentCount - 1 -> segment.copy(
                    switchId = switchId,
                    endJointNumber = JointNumber(1)
                )
                else -> segment
            }
        }
    )
}

fun attachSwitchToIndex(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    switchId: IntId<TrackLayoutSwitch>,
    segmentIndex: Int
): Pair<LocationTrack, LayoutAlignment> =
    locationTrackAndAlignment.first to attachSwitchToIndex(
        locationTrackAndAlignment.second, switchId, segmentIndex
    )

fun attachSwitchToIndex(
    alignment: LayoutAlignment,
    switchId: IntId<TrackLayoutSwitch>,
    segmentIndex: Int
): LayoutAlignment {
    if (alignment.segments.count() < segmentIndex + 3)
        throw IllegalArgumentException("Alignment must contain at least ${segmentIndex + 3} segments")
    return alignment.copy(
        segments = alignment.segments.mapIndexed { index, segment ->
            when (index) {
                segmentIndex -> segment.copy(
                    switchId = switchId,
                    startJointNumber = JointNumber(1)
                )
                segmentIndex + 1 -> segment.copy(
                    switchId = switchId
                )
                segmentIndex + 2 -> segment.copy(
                    switchId = switchId,
                    endJointNumber = JointNumber(2)
                )
                else -> segment
            }
        }
    )
}

fun attachSwitchToIndex(
    alignment: LayoutAlignment,
    switch: TrackLayoutSwitch,
    segmentIndex: Int
): LayoutAlignment {
    if (alignment.segments.count() < segmentIndex + 3)
        throw IllegalArgumentException("Alignment must contain at least ${segmentIndex + 3} segments")


    return alignment.copy(
        segments = alignment.segments.mapIndexed { index, segment ->
            when (index) {
                segmentIndex -> segment.copy(
                    switchId = switch.id,
                    startJointNumber = JointNumber(1)
                )
                segmentIndex + 1 -> segment.copy(
                    switchId = switch.id
                )
                segmentIndex + 2 -> segment.copy(
                    switchId = switch.id,
                    endJointNumber = JointNumber(2)
                )
                else -> segment
            }
        }
    )
}

fun geocodingContext(
    referenceLinePoints: List<Point>,
    trackNumberId: IntId<TrackLayoutTrackNumber> = IntId(1),
    startAddress: TrackMeter = TrackMeter.ZERO,
    kmPosts: List<TrackLayoutKmPost> = listOf(),
) = alignment(segment(*referenceLinePoints.toTypedArray())).let { alignment ->
    GeocodingContext.create(
        trackNumber = trackNumber(id = trackNumberId),
        startAddress = startAddress,
        referenceLineGeometry = alignment,
        kmPosts = kmPosts,
    )
}

abstract class TargetSegment
class TargetSegmentStart : TargetSegment()
data class TargetSegmentMiddle(val index: Int) : TargetSegment()
class TargetSegmentEnd : TargetSegment()

fun attachSwitches(
    locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    vararg switchInfos: Pair<IntId<TrackLayoutSwitch>, TargetSegment>
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
    start: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
    switchId: IntId<TrackLayoutSwitch>? = null,
    startJointNumber: JointNumber? = null,
    endJointNumber: JointNumber? = null,
) = segment(
    toTrackLayoutPoints(to3DMPoints(points.asList())),
    start = start,
    source = source,
    sourceId = sourceId,
    switchId = switchId,
    startJointNumber = startJointNumber,
    endJointNumber = endJointNumber
)

fun segment(
    vararg points: Point3DZ,
    start: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
) = segment(toTrackLayoutPoints(to3DMPoints(points.asList())), start, source, sourceId)

fun segment(
    vararg points: IPoint3DM,
    start: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
) = segment(toTrackLayoutPoints(points.asList()), start, source, sourceId)

fun segment(
    points: List<LayoutPoint>,
    start: Double = 0.0,
    source: GeometrySource = PLAN,
    sourceId: DomainId<GeometryElement>? = null,
    sourceStart: Double? = null,
    resolution: Int = 1,
    switchId: IntId<TrackLayoutSwitch>? = null,
    startJointNumber: JointNumber? = null,
    endJointNumber: JointNumber? = null,
) = LayoutSegment(
    geometry = SegmentGeometry(
        points = points,
        resolution = resolution,
    ),
    sourceId = sourceId,
    sourceStart = sourceStart,
    start = start,
    switchId = switchId,
    startJointNumber = startJointNumber,
    endJointNumber = endJointNumber,
    source = source,
)

fun splitSegment(segment: LayoutSegment, numberOfParts: Int): List<LayoutSegment> {
    val allPoints = segment.points
    val indexRange = 0..allPoints.lastIndex
    val pointsPerSegment = allPoints.count() / numberOfParts.toDouble()
    return indexRange
        .groupBy { index -> (index / pointsPerSegment).toInt() }
        .map { (_, groupIndexRange) ->
            val points = allPoints.subList(
                0.coerceAtLeast(groupIndexRange.first() - 1),
                groupIndexRange.last() + 1
            )
            segment(
                points = points.map { Point(it) }.toTypedArray(),
                start = points.first().m
            )
        }
}

fun toTrackLayoutPoints(vararg points: IPoint) = toTrackLayoutPoints(to3DMPoints(points.asList()))
fun toTrackLayoutPoints(vararg points: Point3DZ) = toTrackLayoutPoints(to3DMPoints(points.asList()))
fun toTrackLayoutPoints(vararg points: IPoint3DM) = toTrackLayoutPoints(points.asList())
fun toTrackLayoutPoints(points: List<IPoint3DM>) = points.map { point ->
    LayoutPoint(
        point.x,
        point.y,
        when (point) {
            is LayoutPoint -> point.z
            is IPoint3DZ -> point.z
            else -> null
        },
        point.m,
        if (point is LayoutPoint) point.cant else null,
    )
}

fun to3DMPoints(points: List<IPoint>): List<IPoint3DM> {
    val pointsWithDistance = points.mapIndexed { index, point ->
        val distance = points.getOrNull(index - 1)?.let { prev -> lineLength(prev, point) } ?: 0.0
        point to distance
    }
    return pointsWithDistance.mapIndexed { index, (point, _) ->
        val m = pointsWithDistance.subList(0, index + 1).foldRight(0.0) { (_, distance), acc -> acc + distance }
        when (point) {
            is LayoutPoint -> LayoutPoint(point.x, point.y, point.z, m, point.cant)
            is Point3DZ -> Point4DZM(point.x, point.y, point.z, m)
            else -> Point3DM(point.x, point.y, m)
        }
    }
}

fun fixMValues(points: List<LayoutPoint>): List<LayoutPoint> {
    var m = 0.0
    return points.mapIndexed { i, p ->
        val previous = points.getOrNull(i-1)
        if (previous != null) m += lineLength(previous, p)
        p.copy(m = m)
    }
}

fun fixSegmentStarts(segments: List<LayoutSegment>): List<LayoutSegment> {
    var start = 0.0
    return segments.mapIndexed { index, segment ->
        val previous = segments.getOrNull(index - 1)
        if (previous != null) start += previous.length
        segment.copy(start = start)
    }
}

fun someSegment() = segment(3, 10.0, 20.0, 10.0, 20.0)

fun segment(points: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    segment(points = points(points, minX, maxX, minY, maxY))

fun segment(from: IPoint, to: IPoint): LayoutSegment {
    val middlePoints = (1 until lineLength(from, to).toInt())
        .map { i -> (from + (to - from).normalized() * i.toDouble()) }
    return segment(toTrackLayoutPoints(to3DMPoints((listOf(from) + middlePoints + listOf(to)).distinct())))
}

fun segments(from: IPoint, to: IPoint, segmentCount: Int): List<LayoutSegment> {
    return segments(from, to, lineLength(from, to) / segmentCount)
}

fun segments(from: IPoint, to: IPoint, segmentLength: Double): List<LayoutSegment> {
    val dir = (to - from).normalized()
    val segmentCount = ceil(lineLength(from, to) / segmentLength).toInt()
    val endPoints = listOf(from) +
            (1 until segmentCount).map { i ->
                from + dir * segmentLength * i.toDouble()
            } +
            listOf(to)
    return segments(endPoints = endPoints.toTypedArray())
}

fun segments(vararg endPoints: IPoint): List<LayoutSegment> {
    assert(endPoints.count() >= 2) { "End points must contain at least two points" }
    var startLength = 0.0
    return endPoints.distinct().dropLast(1).mapIndexed { index, start ->
        val end = endPoints[index + 1]
        val segment = segment(start, end, start = startLength)
        startLength += lineLength(start, end)
        segment
    }
}


fun switch(
    seed: Int = 1,
    structureId: IntId<SwitchStructure> = switchStructureYV60_300_1_9().id as IntId,
    joints: List<TrackLayoutSwitchJoint> = joints(seed),
    name: String = "TV$seed",
) = TrackLayoutSwitch(
    externalId = null,
    sourceId = null,
    name = SwitchName(name),
    stateCategory = getSomeValue(seed),
    joints = joints,
    switchStructureId = structureId,
    trapPoint = false,
    ownerId = switchOwnerVayla().id,
    source = GENERATED,
)

fun joints(seed: Int = 1, count: Int = 5) = (1..count).map { jointSeed -> switchJoint(seed * 100 + jointSeed) }

fun switchJoint(seed: Int) = TrackLayoutSwitchJoint(
    number = JointNumber(1 + seed % 5),
    location = Point(seed * 0.01, 1000.0 + seed * 0.01),
    locationAccuracy = getSomeNullableValue<LocationAccuracy>(seed),
)

fun kmPost(
    trackNumberId: IntId<TrackLayoutTrackNumber>?,
    km: KmNumber,
    location: IPoint? = Point(1.0, 1.0),
    state: LayoutState = LayoutState.IN_USE,
) = TrackLayoutKmPost(
    trackNumberId = trackNumberId,
    kmNumber = km,
    location = location?.toPoint(),
    state = state,
    sourceId = null,
)

fun point(x: Double, y: Double, m: Double = 1.0) =
    LayoutPoint(x, y, null, m, null)

fun points(count: Int, minX: Double, maxX: Double, minY: Double, maxY: Double) =
    toTrackLayoutPoints(to3DMPoints((1..count).map { pointNumber ->
        point2d(minX, maxX, minY, maxY, (pointNumber - 1).toDouble() / (count - 1))
    }))

fun point(minX: Double, maxX: Double, minY: Double, maxY: Double, m: Double, fraction: Double = rand.nextDouble()) =
    LayoutPoint(valueBetween(minX, maxX, fraction), valueBetween(minY, maxY, fraction), null, m, null)

fun point2d(minX: Double, maxX: Double, minY: Double, maxY: Double, fraction: Double = rand.nextDouble()) =
    Point(valueBetween(minX, maxX, fraction), valueBetween(minY, maxY, fraction))

fun valueBetween(min: Double, max: Double, fraction: Double = rand.nextDouble()) = min + (max - min) * fraction

fun someKmNumber(): KmNumber {
    val allowedChars = ('A'..'Z').map(Char::toString)
    return KmNumber(nextInt(10000), if (Random.nextBoolean()) allowedChars.random() else null)
}

fun offsetAlignment(alignment: LayoutAlignment, amount: Point) =
    alignment.copy(
        segments = alignment.segments.map { origSegment -> offsetSegment(origSegment, amount) }
    )

fun offsetSegment(segment: LayoutSegment, amount: Point): LayoutSegment {
    val newPoints = toTrackLayoutPoints(*(segment.points.map { p -> p + amount }.toTypedArray()))
    return segment.withPoints(points = newPoints)
}

fun externalIdForLocationTrack(): Oid<LocationTrack> {
    val first = nextInt(100, 999)
    val second = nextInt(100, 999)
    val third = nextInt(100, 999)
    val fourth = nextInt(100, 999)

    return Oid("$first.$second.$third.$fourth")
}

fun externalIdForTrackNumber(): Oid<TrackLayoutTrackNumber> {
    val first = nextInt(100, 999)
    val second = nextInt(100, 999)
    val third = nextInt(100, 999)
    val fourth = nextInt(100, 999)

    return Oid("$first.$second.$third.$fourth")
}
