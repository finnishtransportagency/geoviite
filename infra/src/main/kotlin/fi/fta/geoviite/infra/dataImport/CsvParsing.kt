package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.equalsIgnoreCaseAndWhitespace
import fi.fta.geoviite.infra.util.measureAndCollect
import org.opengis.referencing.crs.CoordinateReferenceSystem
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

val LOG: Logger = LoggerFactory.getLogger(CsvFile::class.java)

val RATKO_SRID = Srid(4326)

// Prepare math-transform, rather than recreating on every conversion
val RATKO_TO_LAYOUT_TRANSFORM = Transformation(RATKO_SRID, LAYOUT_SRID)
const val MIN_ANGLE_DIFFERENCE_TO_DETECT_CONNECTION_SEGMENT = PI/32
const val MAX_SUM_ANGLE_DIFFERENCE_TO_DETECT_CONNECTION_SEGMENT = PI/32
const val LAYOUT_METER_LENGTH_WARNING_THRESHOLD = 5.0
const val MAX_METERS_FILTERED_TIGHT = 100
const val MAX_METERS_FILTERED_LOOSE = 10000
const val MAX_IMPORT_POINT_ANGLE_CHANGE = PI / 4
const val MAX_DISTANCE_TO_MATCH_TRACK_END_POINT = 1.0

enum class KmPostColumns { TRACK_NUMBER_EXTERNAL_ID, NUMBER, GEOMETRY, STATE }

fun createKmPostsFromCsv(
    kmPostsFile: CsvFile<KmPostColumns>,
    trackNumberIdMapping: Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>>,
): List<TrackLayoutKmPost> {
    return kmPostsFile.parseLines { line ->
        val trackNumberId = trackNumberIdMapping.getValue(line.getOid(KmPostColumns.TRACK_NUMBER_EXTERNAL_ID))
        TrackLayoutKmPost(
            kmNumber = parseKmNumber(line.get(KmPostColumns.NUMBER)),
            location = ratkoToGvtPoint(line.getPoint(KmPostColumns.GEOMETRY)),
            state = line.getEnum(KmPostColumns.STATE),
            sourceId = null,
            trackNumberId = trackNumberId,
        )
    }
}

enum class TrackNumberColumns { EXTERNAL_ID, NUMBER, DESCRIPTION, STATE }

fun createTrackNumbersFromCsv(trackNumberFile: CsvFile<TrackNumberColumns>): List<TrackLayoutTrackNumber> {
    return trackNumberFile.parseLines { line ->
        TrackLayoutTrackNumber(
            number = TrackNumber(line.get(TrackNumberColumns.NUMBER)),
            description = FreeText(line.get(TrackNumberColumns.DESCRIPTION)),
            state = line.getEnum(TrackNumberColumns.STATE),
            externalId = line.getOid(TrackNumberColumns.EXTERNAL_ID),
        )
    }
}

enum class ReferenceLineColumns {
    TRACK_NUMBER_EXTERNAL_ID,
    START_KM_M,
    GEOMETRY,
    KM_M,
    RESOLUTION,
}

data class AlignmentCsvMetaData<T>(
    val alignmentOid: Oid<T>,
    val metadataOid: Oid<AlignmentCsvMetaData<T>>?,
    val startMeter: TrackMeter,
    val endMeter: TrackMeter,
    val createdYear: Int,
    val geometry: AlignmentImportGeometry?,
    val originalCrs: String,
    val measurementMethod: String,
    val fileName: FileName,
    val planAlignmentName: AlignmentName,
    val id: IntId<AlignmentCsvMetaData<T>>? = null,
) {

    init {
        require (startMeter < endMeter) {
            "Alignment metadata must start before it ends: start=$startMeter end=$endMeter alignment=$metadataOid"
        }
    }
}

enum class LocationTrackColumns {
    EXTERNAL_ID,
    TRACK_NUMBER_EXTERNAL_ID,
    TYPE,
    NAME,
    DESCRIPTION,
    STATE,
    GEOMETRY,
    KM_M,
    RESOLUTION,
    DUPLICATE_OF_EXTERNAL_ID,
    TOPOLOGICAL_CONNECTIVITY
}

data class CsvReferenceLine(
    val referenceLine: ReferenceLine,
    val alignment: LayoutAlignment,
    val segmentMetadataIds: List<IntId<AlignmentCsvMetaData<ReferenceLine>>?>,
)

fun createReferenceLinesFromCsv(
    file: CsvFile<ReferenceLineColumns>,
    metadataMap: Map<Oid<ReferenceLine>, List<AlignmentCsvMetaData<ReferenceLine>>>,
    trackNumbers: Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>
): Sequence<CsvReferenceLine> {
    return file.parseLinesStreaming { line ->
        val resolution = line.getInt(ReferenceLineColumns.RESOLUTION)
        val trackNumberExtId = Oid<TrackLayoutTrackNumber>(line.get(ReferenceLineColumns.TRACK_NUMBER_EXTERNAL_ID))
        val referenceLineExtId = Oid<ReferenceLine>(line.get(ReferenceLineColumns.TRACK_NUMBER_EXTERNAL_ID))
        val metadata = metadataMap[referenceLineExtId] ?: listOf()
        val (points, connectionSegmentIndices) = measureAndCollect("parsing->toAddressPoints") {
            toAddressPoints(
                "trackNumber=$trackNumberExtId",
                resolution,
                line.getLinestringPoints(ReferenceLineColumns.GEOMETRY).map { point -> ratkoToGvtPoint(point) },
                parseKmMeterList(line.get(ReferenceLineColumns.KM_M)),
            )
        }
        if (points.size < 2) {
            LOG.warn("Cannot create reference line as there's no points: trackNumber=$trackNumberExtId points=${points.size}")
            null
        } else {
            val segmentRanges = measureAndCollect("parsing->combineMetadataToSegments") {
                combineMetadataToSegments(listOf(), metadata, points, kkjToEtrsTriangulationTriangles)
            }
            val (segments, metadataIds) = measureAndCollect("parsing->createSegments") {
                createSegments(segmentRanges, points, resolution, connectionSegmentIndices)
            }
            val alignment = LayoutAlignment(segments, sourceId = null)
            val referenceLine = ReferenceLine(
                trackNumberId = trackNumbers[trackNumberExtId]
                    ?: throw IllegalArgumentException("No such track-number in DB: externalId=$trackNumberExtId"),
                startAddress = TrackMeter.create(line.get(ReferenceLineColumns.START_KM_M)),
                sourceId = null,
                length = alignment.length,
                segmentCount = alignment.segments.size,
                boundingBox = alignment.boundingBox,
            )
            CsvReferenceLine(referenceLine, alignment, metadataIds)
        }
    }
}

data class AlignmentImportGeometry(
    val id: IntId<GeometryAlignment>,
    val coordinateSystemSrid: Srid?,
    val elements: List<GeometryElement>,
)

data class CsvLocationTrack(
    val locationTrack: LocationTrack,
    val layoutAlignment: LayoutAlignment,
    val segmentMetadataIds: List<IntId<AlignmentCsvMetaData<LocationTrack>>?>,
    val duplicateOfExternalId: Oid<LocationTrack>?,
)

data class SwitchLinkConnectionPoints (
    val startOfTrack: TopologyLocationTrackSwitch?,
    val endOfTrack: TopologyLocationTrackSwitch?,
    val withinTrack: List<AlignmentSwitchLink>,
)

enum class SwitchLinkConnectionPointGroup { START, END, MID }

fun separateOutClosestToEndpoint(locationTrackId: Oid<LocationTrack>, endPoint: Point, linkableToEnd: MutableList<AlignmentSwitchLink>, min: Boolean): AlignmentSwitchLink? {
    val withinToleranceToEndPoint = linkableToEnd
        .filter { link -> distance(endPoint, link.linkPoints[0].location!!) < MAX_DISTANCE_TO_MATCH_TRACK_END_POINT }
    val closestToEndpoint = if (withinToleranceToEndPoint.isNotEmpty()) {
        if (withinToleranceToEndPoint.size > 1) {
            LOG.warn("Multiple switch links within tolerance to start point of location track $locationTrackId: ${
                withinToleranceToEndPoint.joinToString { link -> link.switchOid.stringValue }
            }")
            if (min) {
                withinToleranceToEndPoint.minBy { link -> link.startMeter }
            } else {
                withinToleranceToEndPoint.maxBy { link -> link.startMeter }
            }
        } else
            withinToleranceToEndPoint.firstOrNull()
    } else
        null
    if (closestToEndpoint != null) {
        linkableToEnd.remove(closestToEndpoint)
    }
    return closestToEndpoint
}

fun separateOutSwitchLinkConnectionPoints(locationTrackId: Oid<LocationTrack>, switchLinks: List<AlignmentSwitchLink>, start: AddressPoint, end: AddressPoint): SwitchLinkConnectionPoints {
    val linkableToEnd: MutableList<AlignmentSwitchLink> = mutableListOf()
    val notLinkableToEnd: MutableList<AlignmentSwitchLink> = mutableListOf()
    switchLinks.forEach { link ->
        (if (!link.startMeter.isSame(link.endMeter) || link.linkPoints[0].location == null) {
            notLinkableToEnd
        } else {
            linkableToEnd
        }).add(link)
    }
    val closestToStart = separateOutClosestToEndpoint(locationTrackId, start.point.toPoint(), linkableToEnd, true)
    val closestToEnd = separateOutClosestToEndpoint(locationTrackId, end.point.toPoint(), linkableToEnd, true)

    return SwitchLinkConnectionPoints(
        closestToStart?.let { link ->
            TopologyLocationTrackSwitch(link.switchId, link.linkPoints[0].jointNumber)
        },
        closestToEnd?.let { link ->
            TopologyLocationTrackSwitch(link.switchId, link.linkPoints[0].jointNumber)
        },
        notLinkableToEnd + linkableToEnd
    )
}

fun createLocationTracksFromCsv(
    alignmentsFile: CsvFile<LocationTrackColumns>,
    metadataMap: Map<Oid<LocationTrack>, List<AlignmentCsvMetaData<LocationTrack>>>,
    switchLinksMap: Map<Oid<LocationTrack>, List<AlignmentSwitchLink>>,
    trackNumbers: Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>
): Sequence<CsvLocationTrack> {
    return alignmentsFile.parseLinesStreaming { line ->
        val resolution = line.getInt(LocationTrackColumns.RESOLUTION)
        val trackNumberExtId = Oid<TrackLayoutTrackNumber>(line.get(LocationTrackColumns.TRACK_NUMBER_EXTERNAL_ID))
        val alignmentExtId = Oid<LocationTrack>(line.get(LocationTrackColumns.EXTERNAL_ID))
        val state = enumValueOf<LayoutState>(line.get(LocationTrackColumns.STATE))
        val metadata = metadataMap[alignmentExtId] ?: listOf()
        val switchLinks =
            if (state != LayoutState.DELETED && switchLinksMap.containsKey(alignmentExtId)) switchLinksMap[alignmentExtId]!!
            else listOf()
        val (points, connectionSegmentIndices) = measureAndCollect("parsing->toAddressPoints") {
            toAddressPoints(
                "locationTrack=$alignmentExtId",
                resolution,
                line.getLinestringPoints(LocationTrackColumns.GEOMETRY).map { point -> ratkoToGvtPoint(point) },
                parseKmMeterList(line.get(LocationTrackColumns.KM_M)),
            )
        }
        if (points.size < 2) {
            LOG.warn("Cannot create location track as there's no points: locationTrack=$alignmentExtId points=${points.size}")
            null
        } else {
            val switchLinkGroups = separateOutSwitchLinkConnectionPoints(alignmentExtId, switchLinks, points.first(), points.last())

            val segmentRanges = measureAndCollect("parsing->combineMetadataToSegments") {
                combineMetadataToSegments(switchLinkGroups.withinTrack, metadata, points, kkjToEtrsTriangulationTriangles)
            }
            val (segments, metadataIds) = measureAndCollect("parsing->createSegments") {
                createSegments(segmentRanges, points, resolution, connectionSegmentIndices)
            }
            val alignment = LayoutAlignment(segments, sourceId = null)
            val track = LocationTrack(
                trackNumberId = trackNumbers[trackNumberExtId] ?: throw IllegalStateException(
                    "No TrackNumber found for LocationTrack: track=$alignmentExtId trackNumber=$trackNumberExtId"
                ),
                externalId = alignmentExtId,
                name = AlignmentName(line.get(LocationTrackColumns.NAME)),
                description = FreeText(line.get(LocationTrackColumns.DESCRIPTION)),
                type = line.getEnum(LocationTrackColumns.TYPE),
                state = state,
                sourceId = null,
                boundingBox = alignment.boundingBox,
                segmentCount = alignment.segments.size,
                length = alignment.length,
                duplicateOf = null,
                topologicalConnectivity = line.getEnum(LocationTrackColumns.TOPOLOGICAL_CONNECTIVITY),
                // TODO: GVT-1482
                topologyStartSwitch = switchLinkGroups.startOfTrack,
                topologyEndSwitch = switchLinkGroups.endOfTrack,
            )
            CsvLocationTrack(
                locationTrack = track,
                layoutAlignment = alignment,
                segmentMetadataIds = metadataIds,
                duplicateOfExternalId = line.getOidOrNull(LocationTrackColumns.DUPLICATE_OF_EXTERNAL_ID)
            )
        }
    }
}

fun toAddressPoints(
    logId: String,
    resolution: Int,
    points: List<Point>,
    addresses: List<TrackMeter>,
): Pair<List<AddressPoint>, List<Int>> {
    require (points.size == addresses.size) {
        "Lists for Points and their TrackMeters don't match: $logId"
    }
    require (points.size >= 2) {
        "Point collection has less than 2 points: $logId"
    }
    require (addresses.first() <= addresses.last()) {
        "Addresses not in increasing order: $logId"
    }

    var lastPoint: IPoint? = null
    var lastAddress: TrackMeter? = null
    var lastDirection: Double? = null

    var lastAngle = 0.0
    var outputIndex = 0
    val connectionSegmentIndices = mutableListOf<Int>()

    val filteredIndices = getFilteredIndices(logId, resolution, points, addresses)
    if (filteredIndices == null) { LOG.error("Can't fix convoluted line: $logId") }
    else if (filteredIndices.isNotEmpty()) {
        val filteredCount = filteredIndices.map(::size).sum()
        LOG.warn(
            "Filtering out points due to rough turns: $logId " +
                    "total=${points.size} " +
                    "filtered=$filteredCount " +
                    "filterPortion=${round(filteredCount.toDouble() / points.size.toDouble(), 4)} " +
                    "totalKmM=${describeRange(addresses, 0..points.lastIndex)} " +
                    "filteredKmM=[${filteredIndices.joinToString(",") { f -> describeRange(addresses, f) }}]"
        )
    }
    val longMeterIndices = mutableListOf<Int>()

    val addressPoints = points.mapIndexedNotNull { index, point ->
        if (filteredIndices != null && filteredIndices.any { f -> f.contains(index) }) {
            null
        } else {
            require(lastAddress?.let { a -> addresses[index] > a } ?: true) {
                "Convoluted address: $logId previous=${lastAddress} next=${addresses[index]} prevPoint=$lastPoint point=$point"
            }
            require (lastPoint?.let { lp -> lp.x != point.x || lp.y != point.y } ?: true) {
                "Duplicate points in alignment: $logId index=$index previous=$lastPoint next=$point"
            }
            if (lastPoint?.let { last -> lineLength(last, point) > LAYOUT_METER_LENGTH_WARNING_THRESHOLD } == true) {
                longMeterIndices.add(index)
            }
            val ap = AddressPoint(to3DM(point, lastPoint), addresses[index])
            lastPoint?.let { lp ->
                val direction = directionBetweenPoints(lp, point)
                val angle = lastDirection?.let { ld -> relativeAngle(ld, direction) }
                // Anything that looks like a sufficiently sharp chicane across a single point pair is likely a data
                // artifact and not an actual physical turn; mark them as generated connection segments. We ignore
                // connection segments in the first point-pair (whose index would be 1), symmetrically with how we'll
                // never see ones in the last point-pair.
                if (outputIndex > 2
                    && angle != null
                    && abs(lastAngle) > MIN_ANGLE_DIFFERENCE_TO_DETECT_CONNECTION_SEGMENT
                    && abs(angle) > MIN_ANGLE_DIFFERENCE_TO_DETECT_CONNECTION_SEGMENT
                    && abs(lastAngle + angle) < MAX_SUM_ANGLE_DIFFERENCE_TO_DETECT_CONNECTION_SEGMENT
                ) {
                    // index of the point that ends the connection segment
                    connectionSegmentIndices.add(outputIndex - 1)
                }
                lastDirection = direction
                if (angle != null) lastAngle = angle
            }
            lastPoint = point
            lastAddress = addresses[index]
            outputIndex++
            ap
        }
    }

    if (longMeterIndices.isNotEmpty()) {
        LOG.warn("Long meters in alignment: $logId meters=${longMeterIndices.map { i -> addresses[i] }}")
    }

    return addressPoints to connectionSegmentIndices
}

fun getFilteredIndices(
    logId: String,
    resolution: Int,
    points: List<IPoint>,
    addresses: List<TrackMeter>,
): List<ClosedRange<Int>>? {
    val maxFilteredTight = max(1, MAX_METERS_FILTERED_TIGHT / resolution)
    val maxFilteredLoose = max(1, MAX_METERS_FILTERED_LOOSE / resolution)

    val filtered = mutableListOf<ClosedRange<Int>>()
    var index = 0
    var previousPoint: IPoint? = null
    var previousAddress: TrackMeter? = null
    var previousAngle: Double? = null

    while (index in 0..points.lastIndex) {

        val point = points[index]
        val address = addresses[index]
        val angle = if (previousPoint != null) directionBetweenPoints(previousPoint, point) else null

        // Remove any duplicate points (by coordinate or address)
        if (isPointDuplicate(previousPoint, point) || isAddressDuplicate(previousAddress, address)) {
            LOG.debug("Filtering duplicate point: $logId index=$index $previousAddress=$previousPoint $address=$point")
            filtered.add(index..index)
            index++
        }
        // Basic case: everything looks good
        else if (angle == null || layoutAngleChangeOk(previousAngle, angle)) {
            previousPoint = points[index]
            previousAddress = addresses[index]
            previousAngle = angle
            index++
        }
        // The angle jumps -> try to find a filtering solution that fixes it
        else {
            val solution = resolveConvolutedLine(points, addresses, index, maxFilteredTight)
                ?: resolveConvolutedLine(points, addresses, index, maxFilteredLoose)
            if (solution == null) {
                LOG.error("Can't fix convoluted line: $logId " +
                        "errorIndex=$index resolution=$resolution " +
                        "points_ctx50=${points.subList(max(0, index-50), min(points.size, index+50))} "
                )
                return null
            } else {
                LOG.debug("Resolving convoluted alignment by filtering points: $logId " +
                        "count=${size(solution)} errorIndex=$index filtered=${describeRange(addresses, solution)}")
                filtered.add(solution)
                val pointBeforeFiltered = points.getOrNull(solution.start - 1)
                val pointAfterFiltered = points.getOrNull(solution.endInclusive + 1)
                val addressAfterFiltered = addresses.getOrNull(solution.endInclusive + 1)
                index = solution.endInclusive + 2
                previousPoint = pointAfterFiltered
                previousAddress = addressAfterFiltered
                previousAngle = if (pointBeforeFiltered != null && pointAfterFiltered != null) {
                    directionBetweenPoints(pointBeforeFiltered, pointAfterFiltered)
                } else null
            }
        }
    }
    return filtered
}

private fun isPointDuplicate(previousPoint: IPoint?, point: IPoint): Boolean =
    previousPoint?.let { prev -> lineLength(prev, point) <= 0.0001 } ?: false

private fun isAddressDuplicate(previousAddress: TrackMeter?, address: TrackMeter): Boolean =
    previousAddress?.let { prev -> address.compareTo(prev) == 0 } ?: false

private fun describeRange(addresses: List<TrackMeter>, range: ClosedRange<Int>) =
    if (range.start == range.endInclusive) "(${range.start})${addresses[range.start]}"
    else "(${range.start}..${range.endInclusive})${addresses[range.start]}..${addresses[range.endInclusive]}"

private fun layoutAngleChangeOk(angle1: Double?, angle2: Double) =
    angle1 == null || angleDiffRads(angle1, angle2) <= MAX_IMPORT_POINT_ANGLE_CHANGE

private fun size(range: ClosedRange<Int>) = 1 + range.endInclusive - range.start

private fun resolveConvolutedLine(
    points: List<IPoint>,
    addresses: List<TrackMeter>,
    errorIndex: Int,
    maxFiltered: Int,
): ClosedRange<Int>? {
    require(errorIndex in 1..points.lastIndex) {
        "Impossible continuity error: index=$errorIndex points=[0,${points.lastIndex}]"
    }
    val forwardSolution = resolveConvolutedLineForward(points, addresses, errorIndex, maxFiltered)
    val backwardSolution = resolveConvolutedLineBackward(points, addresses, errorIndex, maxFiltered)
    LOG.debug("Filtering solution: errorIndex=$errorIndex forward=$forwardSolution backward=$backwardSolution")
    return if (forwardSolution == null) {
        backwardSolution
    } else if (backwardSolution == null) {
        forwardSolution
    } else if (size(forwardSolution) <= size(backwardSolution)) {
        forwardSolution
    } else {
        backwardSolution
    }
}

private fun resolveConvolutedLineForward(
    points: List<IPoint>,
    addresses: List<TrackMeter>,
    errorIndex: Int,
    maxFiltered: Int,
): ClosedRange<Int>? {
    val lastGoodPoint = points[errorIndex-1]
    return points.getOrNull(errorIndex-2)
        ?.let { preceding -> directionBetweenPoints(preceding, lastGoodPoint) }
        ?.let { direction ->
            // Find the first point that agrees with the initial direction and filter out everything before that
            (errorIndex+1..(errorIndex + maxFiltered + 1))
                .find { i ->
                    if (i > points.lastIndex) true
                    else if (isPointDuplicate(lastGoodPoint, points[i])) false
                    else if (isAddressDuplicate(addresses[errorIndex-1], addresses[i])) false
                    else isNextPointOk(lastGoodPoint, direction, points[i])
                }
                ?.let { i -> errorIndex until i }
        }
}

private fun resolveConvolutedLineBackward(
    points: List<IPoint>,
    addresses: List<TrackMeter>,
    errorIndex: Int,
    maxFiltered: Int,
): ClosedRange<Int>? {
    val errorPoint = points[errorIndex]
    return points.getOrNull(errorIndex+1)
        ?.let { following -> directionBetweenPoints(following, errorPoint) }
        ?.let { directionToError ->
            // When filtering backwards, the first point might be ok to keep, if the issue is a long direction change
            val lastOkIndex =
                if (isNextPointOk(errorPoint, directionToError, points[errorIndex-1])) errorIndex-1
                else errorIndex
            val lastOkPoint = points[lastOkIndex]
            val direction =
                if (lastOkIndex == errorIndex) directionToError
                else directionBetweenPoints(errorPoint, lastOkPoint)
            // Find the first point that agrees with the initial direction and filter out everything before that
            (lastOkIndex-2 downTo lastOkIndex-maxFiltered-1)
                .find { i ->
                    if (i < 0) true
                    else if (isPointDuplicate(lastOkPoint, points[i])) false
                    else if (isAddressDuplicate(addresses[lastOkIndex], addresses[i])) false
                    // When filtering backwards, it's not enough that the found start-point is OK, the direction before it must also match
                    // This doesn't apply to forward filtering, because the points after the end-point will be checked afterwards anyhow
                    else isNextPointOk(lastOkPoint, direction, points[i], points.getOrNull(i-1))
                }
                ?.let { i -> i+1 until lastOkIndex }
        }
}

private fun isNextPointOk(
    prevPoint: IPoint,
    prevAngle: Double,
    nextPoint: IPoint,
    followingPoint: IPoint? = null,
): Boolean {
    val nextAngle = directionBetweenPoints(prevPoint, nextPoint)
    val followingAngle = followingPoint?.let { following -> directionBetweenPoints(nextPoint, following) }
    return layoutAngleChangeOk(prevAngle, nextAngle)
            && (followingAngle == null || layoutAngleChangeOk(nextAngle, followingAngle))
}

data class AddressPoint(val point: Point3DM, val trackMeter: TrackMeter)

enum class AlignmentSwitchLinkColumns { ALIGNMENT_EXTERNAL_ID, SWITCH_EXTERNAL_ID, JOINTS, KM_M, /*GEOM,*/ }

data class AlignmentSwitchLink(
    val alignmentOid: Oid<LocationTrack>,
    val switchOid: Oid<TrackLayoutSwitch>,
    val switchId: IntId<TrackLayoutSwitch>,
    val linkPoints: List<AlignmentSwitchLinkPoint>
) {
    init {
        if (linkPoints.isEmpty()) throw IllegalStateException("Alignment switch link must have at least 1 point")
    }

    val startMeter: TrackMeter by lazy { linkPoints.first().trackMeter }
    val endMeter: TrackMeter by lazy { linkPoints.last().trackMeter }

    fun getNextMeter(meter: TrackMeter): TrackMeter? = linkPoints.find { p -> p.trackMeter > meter }?.trackMeter
    fun getJointNumber(meter: TrackMeter): JointNumber? = linkPoints.find { p -> p.trackMeter.isSame(meter) }?.jointNumber
}

data class AlignmentSwitchLinkPoint(
    val jointNumber: JointNumber,
    val trackMeter: TrackMeter,
    val location: Point?,
)

data class SwitchLinkingInfo(
    val switchId: IntId<TrackLayoutSwitch>,
    val switchStructureId: IntId<SwitchStructure>,
    val joints: Map<JointNumber, Point>,
)

fun createAlignmentSwitchLinks(
    linkFile: CsvFile<AlignmentSwitchLinkColumns>,
    linkingInfos: Map<Oid<TrackLayoutSwitch>, SwitchLinkingInfo>,
    switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<AlignmentSwitchLink> {
    return linkFile.parseLines { line ->
        val alignmentOid = line.getOid<LocationTrack>(AlignmentSwitchLinkColumns.ALIGNMENT_EXTERNAL_ID)
        val switchOid = line.getOid<TrackLayoutSwitch>(AlignmentSwitchLinkColumns.SWITCH_EXTERNAL_ID)
        val switchLinkingInfo = linkingInfos[switchOid] ?: return@parseLines null
        val switchStructure = switchStructures[switchLinkingInfo.switchStructureId]
            ?: throw IllegalArgumentException("Switch structure ID ${switchLinkingInfo.switchStructureId} not found")

        val joints = line.get(AlignmentSwitchLinkColumns.JOINTS)
            .split(",")
            .map { joint -> JointNumber(joint.toInt()) }
            .filter { joint -> switchStructure.joints.any { sj -> sj.number == joint } }
        if (joints.isEmpty()) {
            return@parseLines null
        }
        val trackMeters = line.get(AlignmentSwitchLinkColumns.KM_M)
            .split(",")
            .map(TrackMeter::create)
        if (joints.size != trackMeters.size) {
            throw IllegalStateException("Joint/km_m values not matched: alignment=$alignmentOid switch=$switchOid")
        }
        AlignmentSwitchLink(
            alignmentOid = alignmentOid,
            switchOid = switchOid,
            switchId = switchLinkingInfo.switchId,
            linkPoints = joints.mapIndexed { index, jointNumber ->
                if (switchLinkingInfo.joints[jointNumber] == null) {
                    LOG.warn("Switch link joint not in joint table: switch=$switchOid joint=$jointNumber")
                }
                AlignmentSwitchLinkPoint(
                    jointNumber = jointNumber,
                    trackMeter = trackMeters[index],
                    location = switchLinkingInfo.joints[jointNumber]
                )
            }
        )
    }
}

enum class AlignmentMetaColumns {
    ALIGNMENT_EXTERNAL_ID,
    ASSET_EXTERNAL_ID,
    TRACK_ADDRESS_START,
    TRACK_ADDRESS_END,
    CREATED_YEAR,
    MEASUREMENT_METHOD,
    FILE_NAME,
    ALIGNMENT_NAME,
    ORIGINAL_CRS,
}

inline fun <reified T> createAlignmentMetadataFromCsv(
    metadataFile: CsvFile<AlignmentMetaColumns>,
    crossinline geometryProvider: (fileName: FileName, alignmentName: AlignmentName) -> AlignmentImportGeometry?,
): List<AlignmentCsvMetaData<T>> {
    return metadataFile.parseLines { line ->
        val alignmentOid = line.getOid<T>(AlignmentMetaColumns.ALIGNMENT_EXTERNAL_ID)
        val metaDataOid = line.getOidOrNull<AlignmentCsvMetaData<T>>(AlignmentMetaColumns.ASSET_EXTERNAL_ID)
        val startMeter = TrackMeter.create(line.get(AlignmentMetaColumns.TRACK_ADDRESS_START))
        val endMeter = TrackMeter.create(line.get(AlignmentMetaColumns.TRACK_ADDRESS_END))
        require (startMeter < endMeter) {
            "Invalid ${T::class.simpleName} metadata range (start >= end): " +
                    "start=$startMeter end=$endMeter alignment=$alignmentOid metadata=$metaDataOid"
        }
        val fileName = line.get(AlignmentMetaColumns.FILE_NAME).let(::FileName)
        val alignmentName = line.get(AlignmentMetaColumns.ALIGNMENT_NAME).let(::AlignmentName)
        val geometry = geometryProvider(fileName, alignmentName)
        AlignmentCsvMetaData(
            alignmentOid = alignmentOid,
            metadataOid = metaDataOid,
            startMeter = startMeter,
            endMeter = endMeter,
            createdYear = line.getInt(AlignmentMetaColumns.CREATED_YEAR),
            geometry = geometry,
            originalCrs = line.get(AlignmentMetaColumns.ORIGINAL_CRS),
            measurementMethod = line.get(AlignmentMetaColumns.MEASUREMENT_METHOD),
            fileName = fileName,
            planAlignmentName = alignmentName,
        )
    }
}


fun <T> combineMetadataToSegments(
    switchLinks: List<AlignmentSwitchLink> = listOf(),
    alignmentMetadata: List<AlignmentCsvMetaData<T>> = listOf(),
    points: List<AddressPoint>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>,
): List<SegmentCsvMetaDataRange<T>> {
    val adjustedAlignmentMetadata = validateAndAdjustAlignmentCsvMetaData(
        points.first().trackMeter,
        points.last().trackMeter,
        alignmentMetadata,
    )
    val elementMetadatas = adjustedAlignmentMetadata.flatMap { metadata ->
        getGeometryElementRanges(points, metadata, kkjToEtrsTriangulationTriangles)
    }
    val expandedMetadata = adjustMetadataToSwitchLinks(elementMetadatas, switchLinks)
    return segmentCsvMetadata(points, expandedMetadata, switchLinks)
}

fun <T> createSegments(
    segmentRanges: List<SegmentCsvMetaDataRange<T>>,
    points: List<AddressPoint>,
    resolution: Int,
    connectionSegmentIndices: List<Int>,
): Pair<List<LayoutSegment>, List<IntId<AlignmentCsvMetaData<T>>?>> {
    val segmentedPoints = dividePointsToSegments(points, segmentRanges, HashSet(connectionSegmentIndices))
    var start = 0.0
    return segmentedPoints.map { (segmentPoints, metadataRange) ->
        val segment = createLayoutSegment(segmentPoints, metadataRange, start, resolution)
        start += segment.length
        segment
    } to segmentedPoints.map { (_, metadataRange) -> metadataRange.metadata.metadata?.metadataId }
}

fun <T> createLayoutSegment(
    segmentPoints: List<Point3DM>,
    range: SegmentFullMetaDataRange<T>,
    startLength: Double,
    resolution: Int,
): LayoutSegment {
    val metadata = range.metadata
    val srid = metadata.metadata?.geometrySrid
    val sourceElement = if (srid != null) metadata.metadata.geometryElement else null
    val sourceStart = if (srid != null && sourceElement != null) {
        sourceElement.getLengthUntil(transformCoordinate(LAYOUT_SRID, srid, segmentPoints.first()))
    } else null
    return LayoutSegment(
        points = toLayoutPoints(segmentPoints),
        sourceId = metadata.metadata?.geometryElement?.id,
        sourceStart = sourceStart,
        resolution = resolution,
        switchId = metadata.switchLink?.switchId,
        startJointNumber = metadata.switchLink?.getJointNumber(metadata.meters.start),
        endJointNumber = metadata.switchLink?.getJointNumber(metadata.meters.endInclusive),
        start = startLength,
        source = if (range.connectionSegment) GeometrySource.GENERATED else GeometrySource.IMPORTED,
    )
}

data class SegmentCsvMetaDataRange<T>(
    val meters: ClosedRange<TrackMeter>,
    val metadata: ElementCsvMetadata<T>?,
    val switchLink: AlignmentSwitchLink?,
) {
    fun isBefore(meter: TrackMeter) = meter >= meters.endInclusive
}

data class SegmentFullMetaDataRange<T>(
    val metadata: SegmentCsvMetaDataRange<T>,
    val connectionSegment: Boolean,
)

fun <T> emptyCsvMetaData(range: ClosedRange<TrackMeter>) =
    SegmentCsvMetaDataRange<T>(range, null, null)

/**
 * Maps switch links to modified track meter range. E.g. the new range of
 * a single point switch is generated by the address of that single point and
 * the next address of any switch or metadata. In that way we have some range
 * for single point switches, and we are able to generate segments.
 *
 * For multi-point switches this function generates a range to switch link
 * pair for each joint range.
 */
fun getSwitchLinkTrackMeterRanges(
    switchLinks: List<AlignmentSwitchLink>,
    metadataRanges: List<ClosedRange<TrackMeter>>,
    allTrackMeters: List<TrackMeter>,
): Map<ClosedRange<TrackMeter>, AlignmentSwitchLink> {
    val trackStart = allTrackMeters.first()
    val trackEnd = allTrackMeters.last()

    val rangeDelimitingTrackMeters = switchLinks.flatMap { switchLink ->
        listOf(switchLink.startMeter, switchLink.endMeter)
    } + metadataRanges.flatMap { metadataRange ->
        listOf(metadataRange.start, metadataRange.endInclusive)
    }
    val sortedRangeDelimitingTrackMeters = rangeDelimitingTrackMeters.distinct().sorted()

    return switchLinks.flatMap { switchLink ->
        val isSinglePointSwitch = switchLink.startMeter.isSame(switchLink.endMeter)
        if (isSinglePointSwitch) {
            // We need to come up with a non-empty track meter range to have a segment to link the switch to, but
            // it's better for the range to be very short rather than possibly stretch for kilometers out to the next
            // switch or metadata range start/end; so just take the adjacent track meter.
            val isLastTrackMeter = switchLink.endMeter.isSame(sortedRangeDelimitingTrackMeters.last())
            val range = if (isLastTrackMeter) {
                val previousTrackMeter =
                    allTrackMeters.findLast { trackMeter -> trackMeter < switchLink.startMeter }
                (previousTrackMeter ?: trackStart)..switchLink.endMeter
            } else {
                val nextTrackMeter = allTrackMeters.find { trackMeter -> trackMeter > switchLink.endMeter }
                switchLink.startMeter..(nextTrackMeter ?: trackEnd)
            }
            listOf(
                range to switchLink
            )
        } else {
            // Map each switch joint range to switch link
            val jointRanges = switchLink.linkPoints.dropLast(1).map { linkPoint ->
                val nextTrackMeter = switchLink.getNextMeter(linkPoint.trackMeter)
                    ?: throw Exception("Cannot read next track meter!")
                linkPoint.trackMeter..nextTrackMeter
            }
            jointRanges.map { range -> range to switchLink }
        }
    }.toMap()
}

fun <T> adjustMetadataToSwitchLinks(
    metadata: List<ElementCsvMetadata<T>>,
    switchLinks: List<AlignmentSwitchLink>,
): List<ElementCsvMetadata<T>> {
    val allSwitchLinkAddresses = switchLinks.flatMap { sl -> listOf(sl.startMeter, sl.endMeter) }
    return metadata.mapNotNull { md ->
        val adjustedStart = getAdjustedAddress(md.startMeter, allSwitchLinkAddresses)
        val adjustedEnd = getAdjustedAddress(md.endMeter, allSwitchLinkAddresses)
        if (adjustedStart == md.startMeter && adjustedEnd == md.endMeter) md
        else if (adjustedStart >= adjustedEnd) null
        else md.copy(startMeter = adjustedStart, endMeter = adjustedEnd)
    }
}

fun getAdjustedAddress(point: TrackMeter, snapPoints: List<TrackMeter>): TrackMeter =
    snapPoints.find { snap -> point.ceil() == snap.ceil() || point.floor() == snap.floor() } ?: point

fun <T> segmentCsvMetadata(
    points: List<AddressPoint>,
    metadata: List<ElementCsvMetadata<T>>,
    switchLinks: List<AlignmentSwitchLink>,
): List<SegmentCsvMetaDataRange<T>> {
    val switchLinkByRange = getSwitchLinkTrackMeterRanges(
        switchLinks,
        metadata.map { md -> md.startMeter..md.endMeter },
        points.map { point -> point.trackMeter }
    )
    val switchLinkRanges = switchLinkByRange.keys.sortedBy { it.start }
    val segmentRanges: MutableList<SegmentCsvMetaDataRange<T>> = mutableListOf()
    var currentMeter = points.first().trackMeter
    val endMeter = points.last().trackMeter

    var metaDataIndex = 0
    var switchIndex = 0
    while (currentMeter < endMeter) {
        while (switchIndex < switchLinkRanges.size && switchLinkRanges[switchIndex].endInclusive <= currentMeter) switchIndex++
        while (metaDataIndex < metadata.size && metadata[metaDataIndex].endMeter <= currentMeter) metaDataIndex++

        val nextSwitchRange = switchLinkRanges.getOrNull(switchIndex)
        val nextSwitchMeter = when {
            nextSwitchRange == null -> null
            nextSwitchRange.start > currentMeter -> nextSwitchRange.start
            else -> nextSwitchRange.endInclusive
        }
        val nextMetadata = metadata.getOrNull(metaDataIndex)
        val nextMetadataMeter = nextMetadata?.getNextMeter(currentMeter)
        val nextMeter = minNonNull(nextSwitchMeter, nextMetadataMeter) ?: endMeter

        val switchLinkInCurrentMeter =
            if (nextSwitchRange != null && nextSwitchRange.contains(currentMeter)) switchLinkByRange[nextSwitchRange]
            else null

        val segmentEndMeter = if (nextMeter > endMeter) endMeter else nextMeter
        segmentRanges.add(
            SegmentCsvMetaDataRange(
                meters = currentMeter..segmentEndMeter,
                metadata = metadata.getOrNull(metaDataIndex)?.takeIf { md -> md.includes(currentMeter) },
                switchLink = switchLinkInCurrentMeter,
            )
        )
        currentMeter = segmentEndMeter
    }

    return segmentRanges.flatMap(::breakRangeByKmLimits)
}

data class ElementCsvMetadata<T>(
    val metadataId: IntId<AlignmentCsvMetaData<T>>,
    val startMeter: TrackMeter,
    val endMeter: TrackMeter,
    val createdYear: Int,
    val geometryElement: GeometryElement?,
    val geometrySrid: Srid?,
) {
    init {
        require (startMeter < endMeter) {
            "Element metadata must start before it ends: start=$startMeter end=$endMeter element=$geometryElement"
        }
    }

    fun includes(meter: TrackMeter): Boolean = meter >= startMeter && meter < endMeter
    fun getNextMeter(meter: TrackMeter): TrackMeter? =
        if (meter < startMeter) startMeter
        else if (meter < endMeter) endMeter
        else null
}

fun <T> noElementsCsvMetadata(alignmentMetaData: AlignmentCsvMetaData<T>) = ElementCsvMetadata(
    metadataId = alignmentMetaData.id
        ?: throw IllegalArgumentException("Alignment metadata needs to have an ID"),
    startMeter = alignmentMetaData.startMeter,
    endMeter = alignmentMetaData.endMeter,
    createdYear = alignmentMetaData.createdYear,
    geometryElement = null,
    geometrySrid = null,
)

fun <T> getGeometryElementRanges(
    allPoints: List<AddressPoint>,
    alignment: AlignmentCsvMetaData<T>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>
): List<ElementCsvMetadata<T>> {
    val planSrid = alignment.geometry?.coordinateSystemSrid
    val elements = alignment.geometry?.elements ?: return listOf(noElementsCsvMetadata(alignment))
    val sourceSrid = planSrid ?: return listOf(noElementsCsvMetadata(alignment))

    val points = allPoints.filter { p -> p.trackMeter in alignment.startMeter..alignment.endMeter }
    if (points.size < 2) return listOf(noElementsCsvMetadata(alignment))

    try {
        LOG.debug("Fetching SRID transformation: " +
                "source=$sourceSrid " +
                "target=$LAYOUT_SRID " +
                "alignment=${alignment.alignmentOid} " +
                "geom=${alignment.geometry.id}"
        )
        val transform = Transformation(sourceSrid, LAYOUT_SRID, kkjToEtrsTriangulationTriangles)
        val firstElementPoint = transform.transform(elements.first().start)
        val lastElementPoint = transform.transform(elements.last().end)
        val dirElements = directionBetweenPoints(firstElementPoint, lastElementPoint)
        val dirPoints = directionBetweenPoints(points.first().point, points.last().point)

        val debugString = "alignment=${alignment.alignmentOid} meta=${alignment.metadataOid} " +
                "foundAlignment=${alignment.geometry.id} " +
                "rangeFirst=${points.first()} rangeLast=${points.last()} " +
                "dirElements=$dirElements dirPoints=$dirPoints"

        if (lineLength(firstElementPoint, points.first().point) > lineLength(firstElementPoint, points.last().point)) {
            LOG.warn("Geometry elements' start is closer to points' end: $debugString")
        }
        if (lineLength(lastElementPoint, points.last().point) > lineLength(lastElementPoint, points.first().point)) {
            LOG.warn("Geometry elements' end is closer to points' start: $debugString")
        }
        if (abs(angleDiffRads(dirElements, dirPoints)) > PI / 2) {
            LOG.warn("Geometry elements don't go in the same direction as layout alignment points: $debugString")
        }
        var previousElementEnd: PointSeekResult? = null
        var lastPickedIndex = 0
        val mapped = elements.mapNotNull { e ->
            val elementStart = transform.transform(e.start)
            val elementEnd = transform.transform(e.end)
            val start = previousElementEnd ?: findPoint(points, elementStart, lastPickedIndex)
            val end = if (start != null) findPoint(points, elementEnd, start.index) else null

            val result = if (start == null || end == null) {
                LOG.warn("No elements on alignment: $debugString")
                null
            } else if (max(distance(elementStart, elementEnd), 10.0) < min(start.distance, end.distance)) {
                LOG.debug("Ignoring element that's too far from the metadata segment: element=${e.id} $debugString")
                null // Ignore elements that are too far from the alignment
            } else if (start.trackMeter > end.trackMeter) {
                LOG.warn("Element appears to go in order of decreasing km+m: element=${e.id} $debugString")
                null
            } else if (start.trackMeter.isSame(end.trackMeter)) {
                // Closest point to start is the same as the closest to the end -> element outside segment
                LOG.debug("Ignoring element that's before/after the metadata segment: element=${e.id} $debugString")
                null
            } else {
                lastPickedIndex = end.index
                ElementCsvMetadata(
                    metadataId = alignment.id
                        ?: throw IllegalArgumentException("Alignment metadata needs to have an ID"),
                    startMeter = start.trackMeter,
                    endMeter = end.trackMeter,
                    createdYear = alignment.createdYear,
                    geometryElement = e,
                    geometrySrid = sourceSrid,
                )
            }

            previousElementEnd = end
            result
        }
        validateElementRanges(debugString, alignment.startMeter, alignment.endMeter, mapped)
        return mapped.ifEmpty { listOf(noElementsCsvMetadata(alignment)) }
    } catch (e: CoordinateTransformationException) {
        LOG.error(
            "Failed to link geometry element to layout due to coordinate transformation failure: " +
                    "alignment=${alignment.alignmentOid} " +
                    "meta=${alignment.metadataOid} " +
                    "foundAlignment=${alignment.geometry.id} " +
                    "sourceSrid=$sourceSrid " +
                    "targetSrid=$LAYOUT_SRID", e
        )
        return listOf(noElementsCsvMetadata(alignment))
    }
}

fun <T> validateElementRanges(
    debug: String,
    startMeter: TrackMeter,
    endMeter: TrackMeter,
    elements: List<ElementCsvMetadata<T>>,
) {
    if (elements.isEmpty()) {
        LOG.warn("Geometry element mapping failed - no elements found: $debug")
    } else {
        LOG.debug(
            "Geometry elements mapped: $debug" +
                    "elements=${elements.map { e -> e.startMeter..e.endMeter to e.geometryElement?.id }}"
        )
        if (elements.first().startMeter != startMeter) {
            LOG.warn(
                "Gap in element metadata range start: $debug " +
                        "alignmentMetadataStart=$startMeter " +
                        "firstElementMetadataStart=${elements.first().startMeter} "
            )
        }
        var previous = elements.first().startMeter
        for (e in elements) {
            if (previous < e.startMeter) {
                LOG.warn("Gap between element metadata: $debug prev=$previous next=${e.startMeter}")
            }
            if (previous > e.startMeter) {
                throw IllegalStateException("Overlapping element metadata: $debug prev=$previous next=${e.startMeter}")
            }
            previous = e.endMeter
        }
        if (elements.last().endMeter != endMeter) {
            LOG.warn(
                "Gap in element metadata range end: $debug " +
                        "alignmentMetadataEnd=$endMeter " +
                        "lastElementMetadataEnd=${elements.last().endMeter} "
            )
        }
    }
}

private data class PointSeekResult(
    val trackMeter: TrackMeter,
    val point: Point3DM,
    val distance: Double,
    val index: Int,
)

private fun findPoint(points: List<AddressPoint>, target: Point, startIndex: Int): PointSeekResult? {
    var point: AddressPoint? = null
    var distance: Double = Double.MAX_VALUE
    var pointIndex: Int = startIndex

    for (index in startIndex..points.lastIndex) {
        val nextPoint = points[index]
        val nextPointDistance = distance(nextPoint.point, target)
        if (nextPointDistance < distance) {
            distance = nextPointDistance
            point = nextPoint
            pointIndex = index
        }
    }
    return point?.let { p -> PointSeekResult(p.trackMeter, p.point, distance, pointIndex) }
}

// Calculating real distances would be too slow, but an approximation is enough here
private fun distance(source: IPoint, target: IPoint) = lineLength(source, target)

fun <T> breakRangeByKmLimits(range: SegmentCsvMetaDataRange<T>): List<SegmentCsvMetaDataRange<T>> {
    val ranges: MutableList<SegmentCsvMetaDataRange<T>> = mutableListOf()
    var start = range.meters.start
    while (isMultiKm(start, range.meters.endInclusive)) {
        val end = TrackMeter(
            kmNumber = KmNumber(start.kmNumber.number + 1),
            meters = BigDecimal.ZERO,
        )
        ranges.add(range.copy(meters = start..end))
        start = end
    }
    ranges.add(range.copy(meters = start..range.meters.endInclusive))
    return ranges
}

fun isMultiKm(start: TrackMeter, end: TrackMeter): Boolean {
    val kmDiff = end.kmNumber.number - start.kmNumber.number
    return kmDiff > 1 || (kmDiff > 0 && end.meters > BigDecimal.ZERO)
}

fun <T> dividePointsToSegments(
    points: List<AddressPoint>,
    segmentRanges: List<SegmentCsvMetaDataRange<T>>,
    connectionSegmentIndices: Set<Int>,
): List<Pair<List<Point3DM>, SegmentFullMetaDataRange<T>>> {

    validateSegmentRanges(points.first().trackMeter, points.last().trackMeter, segmentRanges)

    var currentPoints: MutableList<Point3DM> = mutableListOf()
    var rangeIndex = 0
    var currentRangeStartMeter = points.first().trackMeter
    val segments: MutableList<Pair<List<Point3DM>, SegmentFullMetaDataRange<T>>> = mutableListOf()
    // the first point-pair of an alignment is never identified as a connection segment, so false is safe to start with
    var currentRangeStartWasConnectionSegmentPoint = false

    points.forEachIndexed { pointIndex, (point, trackMeter) ->
        require(rangeIndex <= segmentRanges.size) { "Segment point distribution over-indexed" }

        val currentRange = segmentRanges[rangeIndex]

        // Finish previous range (if it has points) at the new point, even if it's not included in range
        if (currentPoints.isNotEmpty() || currentRange.meters.contains(trackMeter)) currentPoints.add(point)

        // Connection segments are identified by their end point and always exactly one point-pair long
        val connectionSegmentStart = connectionSegmentIndices.contains(pointIndex + 1)
        val connectionSegmentEnd = connectionSegmentIndices.contains(pointIndex)
        val rangeEnd = currentRange.isBefore(trackMeter)
                || pointIndex == points.lastIndex
                || connectionSegmentStart
                || connectionSegmentEnd

        if (rangeEnd) {
            if (currentPoints.isNotEmpty()) {
                val originalMeters = currentRange.meters
                val startMeter =
                    if (currentRangeStartWasConnectionSegmentPoint) currentRangeStartMeter else originalMeters.start
                val endMeter =
                    if (connectionSegmentStart || connectionSegmentEnd) trackMeter else originalMeters.endInclusive

                segments.add(
                    currentPoints to SegmentFullMetaDataRange(
                        // re-split the range so it knows about being split by connection segments
                        currentRange.copy(meters = startMeter..endMeter),
                        connectionSegmentEnd
                    )
                )
            }

            currentPoints = if (pointIndex == points.lastIndex) mutableListOf() else mutableListOf(point)
            currentRangeStartMeter = trackMeter
            currentRangeStartWasConnectionSegmentPoint = connectionSegmentStart || connectionSegmentEnd
        }

        // Skip forward to the next range, if the current one is done
        while (pointIndex < points.lastIndex && segmentRanges[rangeIndex].isBefore(trackMeter)) rangeIndex++

    }

    require(currentPoints.isEmpty()) {
        "Segment point distribution had points left over: current=$currentPoints ranges=$segmentRanges all=$points"
    }

    return segments
}

fun <T> validateSegmentRanges(
    start: TrackMeter,
    end: TrackMeter,
    ranges: List<SegmentCsvMetaDataRange<T>>,
) {
    require(ranges.first().meters.start.isSame(start)) {
        "Segment ranges start doesn't match first point: range=${ranges.first().meters} start=${start}"
    }
    require(ranges.last().meters.endInclusive.isSame(end)) {
        "Segment ranges end doesn't match last point: range=${ranges.last().meters} end=${end}"
    }

    ranges.forEachIndexed { index: Int, (range, _) ->
        require(range.start <= range.endInclusive) { "Segment range broken: index=$index range=$range" }
        ranges.getOrNull(index + 1)?.let { next ->
            require(range.endInclusive.isSame(next.meters.start)) {
                "Segment ranges not continuous: index=$index range=$range next=${next.meters}"
            }
        }
    }
}

fun <T> validateAndAdjustAlignmentCsvMetaData(
    alignmentStart: TrackMeter,
    alignmentEnd: TrackMeter,
    metadata: List<AlignmentCsvMetaData<T>>,
): List<AlignmentCsvMetaData<T>> {
    return metadata.mapIndexedNotNull { index, md ->
        val prev = metadata.getOrNull(index - 1)
        require(prev == null || prev.startMeter <= md.startMeter) {
            "Metadata not in order: " +
                    "oid=${md.alignmentOid} " +
                    "previous=${prev?.startMeter}..${prev?.endMeter} " +
                    "next=${md.startMeter}..${md.endMeter}"
        }
        if (prev != null && prev.endMeter > md.startMeter)
            LOG.warn("Metadata overlaps: " +
                    "oid=${md.alignmentOid} " +
                    "current=${prev.startMeter}..${prev.endMeter} " +
                    "next=${md.startMeter}..${md.endMeter}"
            )
        val result = if (md.startMeter >= alignmentEnd && md.endMeter <= alignmentStart) {
            LOG.warn("Metadata outside alignment points: " +
                    "oid=${md.alignmentOid} " +
                    "alignment=$alignmentStart..$alignmentEnd " +
                    "metadata=${md.startMeter}..${md.endMeter}"
            )
            null
        } else {
            val adjustedStart =
                if (index == 0 && (md.startMeter <= alignmentStart || md.startMeter.ceil() == alignmentStart.ceil())) alignmentStart
                else if (prev != null && prev.endMeter > md.startMeter) prev.endMeter
                else md.startMeter
            val adjustedEnd =
                if (index == metadata.lastIndex && (md.endMeter >= alignmentEnd || md.endMeter.floor() == alignmentEnd.floor())) alignmentEnd
                else md.endMeter
            if (adjustedStart >= adjustedEnd) {
                LOG.warn("Metadata rejected as it won't have any points left")
                null
            } else if (adjustedStart != md.startMeter || adjustedEnd != md.endMeter) {
                LOG.info("Adjusting alignment metadata start/end: " +
                        "oid=${md.alignmentOid} " +
                        "adjusted=${adjustedStart}..${adjustedEnd} " +
                        "alignment=$alignmentStart..$alignmentEnd " +
                        "metadata=${md.startMeter}..${md.endMeter} " +
                        "previousEnd=${prev?.endMeter}"
                )
                md.copy(startMeter = adjustedStart, endMeter = adjustedEnd)
            } else md
        }
        result
    }
}

fun toLayoutPoints(points: List<Point3DM>): List<LayoutPoint> {
    var accumulator = 0.0 // Accumulate m-values through segment length
    return points.mapIndexed { index, point ->
        if (index > 0) accumulator += point.m
        LayoutPoint(x = point.x, y = point.y, z = null, m = accumulator, cant = null)
    }
}

fun parseKmMeterList(kmMeterString: String): List<TrackMeter> = parseArrayString(kmMeterString).map(TrackMeter::create)

fun parseArrayString(arrayString: String): List<String> {
    if (!arrayString.startsWith("{") || !arrayString.endsWith("}")) {
        throw IllegalArgumentException("Invalid array-string: $arrayString")
    }
    return arrayString.substring(1, arrayString.length - 1).split(",")
}

enum class SwitchColumns { EXTERNAL_ID, TYPE, HAND, NAME, STATE_CATEGORY, TRAP_POINT, OWNER }
enum class SwitchJointColumns { SWITCH_EXTERNAL_ID, NUMBER, GEOMETRY, ACCURACY }

fun createSwitchesFromCsv(
    switchesFile: CsvFile<SwitchColumns>,
    jointsFile: CsvFile<SwitchJointColumns>,
    switchStructuresByType: Map<SwitchType, SwitchStructure>,
    switchOwners: List<SwitchOwner>,
): List<TrackLayoutSwitch> {
    val allJoints = createSwitchJointsFromCsv(jointsFile)
    return switchesFile.parseLines { line ->
        val externalId: Oid<TrackLayoutSwitch> = line.getOid(SwitchColumns.EXTERNAL_ID)
        val typeName = line.get(SwitchColumns.TYPE)
        val switchTypeRequiresHandedness = tryParseSwitchType(typeName)
            .let { switchType -> if (switchType != null) switchTypeRequiresHandedness(switchType.parts.baseType) else false }
        val hand = line.getEnumOrNull<SwitchHand>(SwitchColumns.HAND)
        val fullTypeName = hand.let {
            if (it != null && it != SwitchHand.NONE && switchTypeRequiresHandedness) "$typeName-${it.abbreviation}"
            else typeName
        }
        val switchType = tryParseSwitchType(fullTypeName)
        val switchStructure = switchStructuresByType[switchType]
        val name = SwitchName(line.get(SwitchColumns.NAME))
        if (switchStructure == null) {
            LOG.warn("Switch structure not found: name=$name type=$typeName fullTypeName=$fullTypeName")
            return@parseLines null
        }
        val joints = (allJoints[externalId] ?: listOf())
            .filter { joint -> switchStructure.joints.any { sj -> sj.number == joint.number } }
        val ownerName = line.getNonEmpty(SwitchColumns.OWNER)
        val switchOwner = ownerName?.let {
            switchOwners.firstOrNull { o -> equalsIgnoreCaseAndWhitespace(o.name.value, ownerName) }
        }

        TrackLayoutSwitch(
            externalId = externalId,
            name = name,
            switchStructureId = switchStructure.id as IntId,
            stateCategory = line.getEnum(SwitchColumns.STATE_CATEGORY),
            joints = joints,
            sourceId = null,
            trapPoint = line.getBooleanOrNull(SwitchColumns.TRAP_POINT),
            ownerId = switchOwner?.id,
            source = GeometrySource.IMPORTED,
        )
    }
}

/**
 * If there are multiple joints with the same joint number per a switch,
 * only last joint of each number is kept.
 */
fun filterOutDuplicateJointNumber(map: Map<Oid<TrackLayoutSwitch>, List<TrackLayoutSwitchJoint>>): Map<Oid<TrackLayoutSwitch>, List<TrackLayoutSwitchJoint>> {
    return map.map { (switchOid, joints) ->
        switchOid to joints.filterIndexed { index, jointToCheck ->
            val sameJointExistsLater = joints.drop(index + 1).any { joint -> jointToCheck.number == joint.number }
            !sameJointExistsLater
        }
    }.toMap()
}

fun createSwitchJointsFromCsv(
    jointsFile: CsvFile<SwitchJointColumns>,
): Map<Oid<TrackLayoutSwitch>, List<TrackLayoutSwitchJoint>> {
    return jointsFile.parseLines { line ->
        val switchId: Oid<TrackLayoutSwitch> = line.getOid(SwitchJointColumns.SWITCH_EXTERNAL_ID)
        // Joints without a number should be disregarded -> set to null for filtering out later
        val numberOrNull = line.getIntOrNull(SwitchJointColumns.NUMBER)
        if (numberOrNull == null) null else
            switchId to TrackLayoutSwitchJoint(
                number = JointNumber(numberOrNull),
                location = ratkoToGvtPoint(line.getPoint(SwitchJointColumns.GEOMETRY)),
                locationAccuracy = line.getEnumOrNull<LocationAccuracy>(SwitchJointColumns.ACCURACY),
            )
    }
        .groupBy({ pair -> pair.first }, { pair -> pair.second })
        .let(::filterOutDuplicateJointNumber)
}

fun ratkoToGvtPoint(ratkoPoint: Point) = RATKO_TO_LAYOUT_TRANSFORM.transform(ratkoPoint)

fun to3DM(point: Point, previousPoint: IPoint?, ref: CoordinateReferenceSystem = LAYOUT_CRS) = Point3DM(
    x = point.x,
    y = point.y,
    m = previousPoint?.let { prev -> calculateDistance(ref, prev, point) } ?: 0.0,
)
