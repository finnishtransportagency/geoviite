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
    val metadataOid: Oid<AlignmentCsvMetaData<T>>,
    val startMeter: TrackMeter,
    val endMeter: TrackMeter,
    val createdYear: Int,
    val geometry: AlignmentImportGeometry?,
    val metadataSrid: Srid?,
) {
    init {
        if (startMeter > endMeter) {
            LOG.error("Alignment metadata must start before it ends. startMeter > endMeter: $startMeter > $endMeter oid: $metadataOid")
        }
    }
}


enum class ReferenceLineMetaColumns {
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

fun getEpsgCodeOrNull(str: String) =
    if (str.startsWith("EPSG:")) Srid(str.substring(5).toInt()) else null

fun createReferenceLineMetadataFromCsv(
    metadataFile: CsvFile<ReferenceLineMetaColumns>,
    geometryProvider: (fileName: FileName, alignmentName: AlignmentName) -> AlignmentImportGeometry?,
): List<AlignmentCsvMetaData<ReferenceLine>> {
    return metadataFile.parseLines { line ->
        val fileName = line.getNonEmpty(ReferenceLineMetaColumns.FILE_NAME)
        val alignmentName = line.getNonEmpty(ReferenceLineMetaColumns.ALIGNMENT_NAME)
        val geometry =
            if (fileName != null && alignmentName != null) geometryProvider(
                FileName(fileName),
                AlignmentName(alignmentName)
            )
            else null

        AlignmentCsvMetaData(
            alignmentOid = Oid(line.get(ReferenceLineMetaColumns.ALIGNMENT_EXTERNAL_ID)),
            metadataOid = Oid(line.get(ReferenceLineMetaColumns.ALIGNMENT_EXTERNAL_ID)),
            startMeter = TrackMeter.create(line.get(ReferenceLineMetaColumns.TRACK_ADDRESS_START)),
            endMeter = TrackMeter.create(line.get(ReferenceLineMetaColumns.TRACK_ADDRESS_END)),
            createdYear = line.getInt(ReferenceLineMetaColumns.CREATED_YEAR),
            geometry = geometry,
            metadataSrid = line.getNonEmpty(ReferenceLineMetaColumns.ORIGINAL_CRS)
                ?.let { getEpsgCodeOrNull(it) }
        )
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

fun createReferenceLinesFromCsv(
    file: CsvFile<ReferenceLineColumns>,
    metadataMap: Map<Oid<ReferenceLine>, List<AlignmentCsvMetaData<ReferenceLine>>>,
    trackNumbers: Map<Oid<TrackLayoutTrackNumber>, IntId<TrackLayoutTrackNumber>>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>
): Sequence<Pair<ReferenceLine, LayoutAlignment>> {
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
            val valid = measureAndCollect("parsing->validateMetadata") {
                validateAlignmentCsvMetaData(points.first().trackMeter, points.last().trackMeter, metadata)
            }
            val segments = measureAndCollect("parsing->createSegments") {
                createSegments(
                    points, resolution, kkjToEtrsTriangulationTriangles, valid,
                    connectionSegmentIndices = connectionSegmentIndices
                )
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
            referenceLine to alignment
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
    val duplicateOfExternalId: Oid<LocationTrack>?,
)

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
        val switchLinks = switchLinksMap[alignmentExtId] ?: listOf()
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
            val valid = measureAndCollect("parsing->validateMetadata") {
                validateAlignmentCsvMetaData(points.first().trackMeter, points.last().trackMeter, metadata)
            }
            val segments = measureAndCollect("parsing->createSegments") {
                createSegments(points, resolution, kkjToEtrsTriangulationTriangles, valid, switchLinks, connectionSegmentIndices)
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

                )
            CsvLocationTrack(
                locationTrack = track,
                layoutAlignment = alignment,
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
    val switchId: IntId<TrackLayoutSwitch>,
    val linkPoints: List<AlignmentSwitchLinkPoint>
) {
    init {
        if (linkPoints.isEmpty()) throw IllegalStateException("Alignment switch link must have at least 1 point")
    }

    val startMeter: TrackMeter by lazy { linkPoints.first().trackMeter }
    val endMeter: TrackMeter by lazy { linkPoints.last().trackMeter }

    fun includes(meter: TrackMeter): Boolean = meter >= startMeter && meter < endMeter
    fun getNextMeter(meter: TrackMeter): TrackMeter? = linkPoints.find { p -> p.trackMeter > meter }?.trackMeter
    fun getJointNumber(meter: TrackMeter): JointNumber? = linkPoints.find { p -> p.trackMeter.isSame(meter) }?.jointNumber
}

data class AlignmentSwitchLinkPoint(
    val jointNumber: JointNumber,
    val trackMeter: TrackMeter,
)

data class SwitchLinkingIds(
    val switchId: IntId<TrackLayoutSwitch>,
    val switchStructureId: IntId<SwitchStructure>,
)

fun createAlignmentSwitchLinks(
    linkFile: CsvFile<AlignmentSwitchLinkColumns>,
    switchIds: Map<Oid<TrackLayoutSwitch>, SwitchLinkingIds>,
    switchStructures: Map<IntId<SwitchStructure>, SwitchStructure>,
): List<AlignmentSwitchLink> {
    return linkFile.parseLines { line ->
        val alignmentOid = line.getOid<LocationTrack>(AlignmentSwitchLinkColumns.ALIGNMENT_EXTERNAL_ID)
        val switchOid = line.getOid<TrackLayoutSwitch>(AlignmentSwitchLinkColumns.SWITCH_EXTERNAL_ID)
        val switchIdPair = switchIds[switchOid] ?: return@parseLines null
        val switchStructure = switchStructures[switchIdPair.switchStructureId]
            ?: throw IllegalArgumentException("Switch structure ID ${switchIdPair.switchStructureId} not found")

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
            switchId = switchIdPair.switchId,
            linkPoints = joints.mapIndexed { index, jointNumber ->
                AlignmentSwitchLinkPoint(
                    jointNumber = jointNumber,
                    trackMeter = trackMeters[index],
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
    FILE_NAME,
    ALIGNMENT_NAME,
    ORIGINAL_CRS,
}

fun createAlignmentMetadataFromCsv(
    metadataFile: CsvFile<AlignmentMetaColumns>,
    geometryProvider: (fileName: String, alignmentName: String) -> AlignmentImportGeometry?,
): List<AlignmentCsvMetaData<LocationTrack>> {
    return metadataFile.parseLines { line ->
        val fileName = line.getNonEmpty(AlignmentMetaColumns.FILE_NAME)
        val alignmentName = line.getNonEmpty(AlignmentMetaColumns.ALIGNMENT_NAME)
        val geometry =
            if (fileName != null && alignmentName != null) geometryProvider(fileName, alignmentName)
            else null
        AlignmentCsvMetaData(
            alignmentOid = Oid(line.get(AlignmentMetaColumns.ALIGNMENT_EXTERNAL_ID)),
            metadataOid = Oid(line.get(AlignmentMetaColumns.ASSET_EXTERNAL_ID)),
            startMeter = TrackMeter.create(line.get(AlignmentMetaColumns.TRACK_ADDRESS_START)),
            endMeter = TrackMeter.create(line.get(AlignmentMetaColumns.TRACK_ADDRESS_END)),
            createdYear = line.getInt(AlignmentMetaColumns.CREATED_YEAR),
            geometry = geometry,
            metadataSrid = line.getNonEmpty(AlignmentMetaColumns.ORIGINAL_CRS)
                ?.let { getEpsgCodeOrNull(it) }
        )
    }
}

fun <T> createSegments(
    points: List<AddressPoint>,
    resolution: Int,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>,
    alignmentMetadata: List<AlignmentCsvMetaData<T>> = listOf(),
    switchLinks: List<AlignmentSwitchLink> = listOf(),
    connectionSegmentIndices: List<Int> = listOf(),
): List<LayoutSegment> {

    val elementMetadatas = alignmentMetadata.flatMapIndexed { index, metadata ->
        val extended =
            if (index == 0 &&
                points.first().trackMeter.ceil().isSame(metadata.startMeter.ceil())) {
                metadata.copy(startMeter = points.first().trackMeter)
            } else if (index == alignmentMetadata.lastIndex &&
                points.last().trackMeter.floor().isSame(metadata.endMeter.floor())) {
                metadata.copy(endMeter = points.last().trackMeter)
            } else metadata
        getGeometryElementRanges(points, extended, kkjToEtrsTriangulationTriangles)
    }
    val metadataSegments = segmentCsvMetadata(points, elementMetadatas, switchLinks)
    val segmentedPoints = dividePointsToSegments(points, metadataSegments, HashSet(connectionSegmentIndices))

    var start = 0.0
    return segmentedPoints.map { (segmentPoints, metadata) ->
        val segment = createLayoutSegment(segmentPoints, metadata, start, resolution)
        start += segment.length
        segment
    }
}

fun createLayoutSegment(
    segmentPoints: List<Point3DM>,
    range: SegmentFullMetaDataRange,
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

data class SegmentCsvMetaDataRange(
    val meters: ClosedRange<TrackMeter>,
    val metadata: ElementCsvMetadata?,
    val switchLink: AlignmentSwitchLink?,
) {
    fun isBefore(meter: TrackMeter) = meter >= meters.endInclusive
}

data class SegmentFullMetaDataRange(
    val metadata: SegmentCsvMetaDataRange,
    val connectionSegment: Boolean,
)

fun emptyCsvMetaData(range: ClosedRange<TrackMeter>) = SegmentCsvMetaDataRange(range, null, null)

/**
 * Moves the start/end of metadata to the location of nearby switch joint,
 * if that joint is close enough.
 */
fun expandMetadataEndingsBySwitchJointLocations(
    metadataCollection: List<ElementCsvMetadata>,
    switchAddressRanges: List<ClosedRange<TrackMeter>>,
    addressesAreCloseEnough: (metadataAddress: TrackMeter, switchAddress: TrackMeter) -> Boolean
): List<ElementCsvMetadata> {
    return metadataCollection.map { metadata ->
        val start = switchAddressRanges.find { switchKmRange ->
            addressesAreCloseEnough(metadata.startMeter, switchKmRange.start)
        }?.start ?: metadata.startMeter
        val end = switchAddressRanges.find { switchKmRange ->
            addressesAreCloseEnough(metadata.endMeter, switchKmRange.endInclusive)
        }?.endInclusive ?: metadata.endMeter
        metadata.copy(
            startMeter = start,
            endMeter = end
        )
    }
}

/**
 * Maps switch links to modified track meter range. E.g. the new range of
 * a single point switch is generated by the address of that single point and
 * the next address of any switch or metadata. In that way we have some range
 * for single point switches and we are able to generate segments.
 *
 * For multi point switches this function generates a range to switch link
 * pair for each joint range.
 */
fun getSwitchLinkTrackMeterRanges(
    switchLinks: List<AlignmentSwitchLink>,
    metadataRanges: List<ClosedRange<TrackMeter>>,
    trackStart: TrackMeter,
    trackEnd: TrackMeter
): Map<ClosedRange<TrackMeter>, AlignmentSwitchLink> {
    val allTrackMeters = switchLinks.flatMap { switchLink ->
        listOf(switchLink.startMeter, switchLink.endMeter)
    } + metadataRanges.flatMap { metadataRange ->
        listOf(metadataRange.start, metadataRange.endInclusive)
    }
    val sortedTrackMeters = allTrackMeters.distinct().sorted()

    return switchLinks.flatMap { switchLink ->
        val isSinglePointSwitch = switchLink.startMeter.isSame(switchLink.endMeter)
        if (isSinglePointSwitch) {
            // Expand a single address to a range from that single point to
            // the address of the next switch/metadata/end of track.
            val isLastTrackMeter = switchLink.endMeter.isSame(sortedTrackMeters.last())
            val range = if (isLastTrackMeter) {
                val previousTrackMeter =
                    sortedTrackMeters.findLast { trackMeter -> trackMeter < switchLink.startMeter }
                (previousTrackMeter ?: trackStart)..switchLink.endMeter
            } else {
                val nextTrackMeter = sortedTrackMeters.find { trackMeter -> trackMeter > switchLink.endMeter }
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

fun segmentCsvMetadata(
    points: List<AddressPoint>,
    metadata: List<ElementCsvMetadata>,
    switchLinks: List<AlignmentSwitchLink>,
): List<SegmentCsvMetaDataRange> {
    val expandedMetadata = expandMetadataEndingsBySwitchJointLocations(
        metadata,
        switchLinks.map { switchLink ->
            switchLink.startMeter..switchLink.endMeter
        }
    ) { metadataAddress, switchJointAddress ->
        if (switchJointAddress < metadataAddress) {
            // check if the metadata address should include previous switch address
            // e.g. metadata address 001+0752 should be expanded to switch address 001+0751.343
            metadataAddress.ceil() == switchJointAddress.ceil()
        } else {
            // e.g. metadata address 001+0813 should be expanded to switch address 001+0813.512
            metadataAddress.floor() == switchJointAddress.floor()
        }
    }
    val switchLinkByRange = getSwitchLinkTrackMeterRanges(
        switchLinks,
        expandedMetadata.map { md -> md.startMeter..md.endMeter },
        points.first().trackMeter,
        points.last().trackMeter
    )
    val switchLinkRanges = switchLinkByRange.keys.sortedBy { it.start }
    val segmentRanges: MutableList<SegmentCsvMetaDataRange> = mutableListOf()
    var currentMeter = points.first().trackMeter
    val endMeter = points.last().trackMeter

    var metaDataIndex = 0
    var switchIndex = 0
    while (currentMeter < endMeter) {
        while (switchIndex < switchLinkRanges.size && switchLinkRanges[switchIndex].endInclusive <= currentMeter) switchIndex++
        while (metaDataIndex < expandedMetadata.size && expandedMetadata[metaDataIndex].endMeter <= currentMeter) metaDataIndex++

        val nextSwitchRange = switchLinkRanges.getOrNull(switchIndex)
        val nextSwitchMeter = when {
            nextSwitchRange == null -> null
            nextSwitchRange.start > currentMeter -> nextSwitchRange.start
            else -> nextSwitchRange.endInclusive
        }
        val nextMetadata = expandedMetadata.getOrNull(metaDataIndex)
        val nextMetadataMeter = nextMetadata?.getNextMeter(currentMeter)
        val nextMeter = minNonNull(nextSwitchMeter, nextMetadataMeter) ?: endMeter

        val switchLinkInCurrentMeter =
            if (nextSwitchRange != null && nextSwitchRange.contains(currentMeter)) switchLinkByRange[nextSwitchRange]
            else null

        segmentRanges.add(
            SegmentCsvMetaDataRange(
                meters = currentMeter..nextMeter,
                metadata = expandedMetadata.getOrNull(metaDataIndex)?.takeIf { md -> md.includes(currentMeter) },
                switchLink = switchLinkInCurrentMeter,
            )
        )
        currentMeter = nextMeter
    }

    return segmentRanges.flatMap(::breakRangeByKmLimits)
}

data class ElementCsvMetadata(
    val startMeter: TrackMeter,
    val endMeter: TrackMeter,
    val createdYear: Int,
    val geometryElement: GeometryElement?,
    val geometrySrid: Srid?,
) {
    init {
        if (startMeter > endMeter) LOG.error("Alignment metadata must start before it ends. startMeter > endMeter: $startMeter > $endMeter")
    }

    fun includes(meter: TrackMeter): Boolean = meter >= startMeter && meter < endMeter
    fun getNextMeter(meter: TrackMeter): TrackMeter? =
        if (meter < startMeter) startMeter
        else if (meter < endMeter) endMeter
        else null
}

fun <T> noElementsCsvMetadata(alignment: AlignmentCsvMetaData<T>) = ElementCsvMetadata(
    startMeter = alignment.startMeter,
    endMeter = alignment.endMeter,
    createdYear = alignment.createdYear,
    geometryElement = null,
    geometrySrid = null,
)

fun <T> getGeometryElementRanges(
    allPoints: List<AddressPoint>,
    alignment: AlignmentCsvMetaData<T>,
    kkjToEtrsTriangulationTriangles: List<KKJtoETRSTriangle>
): List<ElementCsvMetadata> {
    val planSrid = alignment.geometry?.coordinateSystemSrid
    val elements = alignment.geometry?.elements ?: return listOf(noElementsCsvMetadata(alignment))
    val sourceSrid = planSrid ?: return listOf(noElementsCsvMetadata(alignment))

    val points = allPoints.filter { p -> p.trackMeter in alignment.startMeter..alignment.endMeter }
    if (points.size < 2) return listOf(noElementsCsvMetadata(alignment))

    try {
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
        val result = mapped.filterIndexed { i, element ->
            element.startMeter < element.endMeter && (i == 0 || mapped[i - 1].endMeter <= element.startMeter)
        }
        return result.ifEmpty { listOf(noElementsCsvMetadata(alignment)) }
    } catch (e: CoordinateTransformationException) {
        LOG.error(
            "Failed to link geometry element to layout due to coordinate transformation failure: " +
                    "alignment=${alignment.alignmentOid} meta=${alignment.metadataOid} " +
                    "foundAlignment=${alignment.geometry.id} " +
                    "sourceSrid=$sourceSrid targetSrid=$LAYOUT_SRID", e
        )
        return listOf(noElementsCsvMetadata(alignment))
    }
}

fun validateElementRanges(
    debug: String,
    startMeter: TrackMeter,
    endMeter: TrackMeter,
    elements: List<ElementCsvMetadata>,
) {
    if (elements.isEmpty()) {
        LOG.error("Geometry element mapping failed - no elements found: $debug")
    } else {
        LOG.debug(
            "Geometry elements mapped: $debug" +
                    "elements=${elements.map { e -> e.startMeter..e.endMeter to e.geometryElement?.id }}"
        )
        if (elements.first().startMeter != startMeter) {
            LOG.warn("Gap in metadata range start: rangeStart=$startMeter first=${elements.first().startMeter} $debug")
        }
        var previous = elements.first().startMeter
        for (e in elements) {
            if (previous < e.startMeter) {
                LOG.warn("Gap between metadata elements: prev=$previous next=${e.startMeter} $debug")
            }
            if (previous > e.startMeter) {
                throw IllegalStateException("Overlapping elements: prev=$previous next=${e.startMeter} $debug")
            }
            if (e.startMeter > e.endMeter) {
                throw IllegalStateException("Convoluted element: start=${e.startMeter} end=${e.endMeter} $debug")
            }
            previous = e.endMeter
        }
        if (elements.last().endMeter != endMeter) {
            LOG.warn("Gap in metadata range end: last=${elements.last().endMeter} rangeEnd=$endMeter $debug")
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

fun breakRangeByKmLimits(range: SegmentCsvMetaDataRange): List<SegmentCsvMetaDataRange> {
    val ranges: MutableList<SegmentCsvMetaDataRange> = mutableListOf()
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

fun dividePointsToSegments(
    points: List<AddressPoint>,
    segmentRanges: List<SegmentCsvMetaDataRange>,
    connectionSegmentIndices: Set<Int> = setOf(),
): List<Pair<List<Point3DM>, SegmentFullMetaDataRange>> {

    validateSegmentRanges(points.first().trackMeter, points.last().trackMeter, segmentRanges)

    var currentPoints: MutableList<Point3DM> = mutableListOf()
    var rangeIndex = 0
    val segments: MutableList<Pair<List<Point3DM>, SegmentFullMetaDataRange>> = mutableListOf()

    points.forEachIndexed { pointIndex, (point, trackMeter) ->
        require(rangeIndex <= segmentRanges.size) { "Segment point distribution over-indexed" }

        val currentRange = segmentRanges[rangeIndex]

        // Finish previous range (if it has points) at the new point, even if it's not included in range
        if (currentPoints.isNotEmpty() || currentRange.meters.contains(trackMeter)) currentPoints.add(point)

        // Connection segments are identified by their end point and always exactly one point-pair long
        val connectionSegment = connectionSegmentIndices.contains(pointIndex)
        val rangeEnd = currentRange.isBefore(trackMeter)
                || pointIndex == points.lastIndex
                || connectionSegment
                || connectionSegmentIndices.contains(pointIndex + 1)

        if (rangeEnd) {
            if (currentPoints.isNotEmpty()) segments.add(currentPoints to SegmentFullMetaDataRange(currentRange, connectionSegment))
            currentPoints = if (pointIndex == points.lastIndex) mutableListOf() else mutableListOf(point)
        }

        // Skip forward to the next range, if the current one is done
        while (pointIndex < points.lastIndex && segmentRanges[rangeIndex].isBefore(trackMeter)) rangeIndex++

    }

    require(currentPoints.isEmpty()) {
        "Segment point distribution had points left over: current=$currentPoints ranges=$segmentRanges all=$points"
    }

    return segments
}

fun validateSegmentRanges(
    start: TrackMeter,
    end: TrackMeter,
    ranges: List<SegmentCsvMetaDataRange>,
) {
    if (ranges.first().meters.start > start) {
        LOG.error("Segment ranges don't include first point: range=${ranges.first().meters} start=${start}")
    } else if (ranges.first().meters.start < start) {
        LOG.warn("Segment ranges start doesn't match first point: range=${ranges.first().meters} start=${start}")
    }
    if (ranges.last().meters.endInclusive < end) {
        LOG.error("Segment ranges don't include last point: range=${ranges.last().meters} end=${end}")
    } else if (ranges.last().meters.endInclusive > end) {
        LOG.warn("Segment ranges end doesn't match last point: range=${ranges.last().meters} end=${end}")
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

fun <T> validateAlignmentCsvMetaData(
    alignmentStart: TrackMeter,
    alignmentEnd: TrackMeter,
    metadata: List<AlignmentCsvMetaData<T>>,
): List<AlignmentCsvMetaData<T>> {
    return metadata
        // Overlap validation requires metadatas to have at least some semblance of being in order, so
        // sanity-sort them here by startMeter
        .mapIndexedNotNull { index, md ->
            val prev = metadata.getOrNull(index - 1)
            require(prev == null || prev.startMeter <= md.startMeter) {
                "Metadata not in order: " +
                        "oid=${md.alignmentOid} " +
                        "previous=${prev?.startMeter}..${prev?.endMeter} " +
                        "next=${md.startMeter}..${md.endMeter}"
            }
            if (prev != null && prev.endMeter > md.startMeter)
                LOG.error("Metadatas overlap: " +
                        "oid=${md.alignmentOid} " +
                        "current=${prev.startMeter}..${prev.endMeter} " +
                        "next=${md.startMeter}..${md.endMeter}"
                )
            val result = if (md.startMeter >= md.endMeter) {
                LOG.warn("Metadata concerns 0 meters: " +
                        "oid=${md.alignmentOid} " +
                        "alignment=$alignmentStart..$alignmentEnd " +
                        "metadata=${md.startMeter}..${md.endMeter}"
                )
                null
            } else if (md.startMeter >= alignmentEnd && md.endMeter <= alignmentStart) {
                LOG.error("Metadata outside alignment points: " +
                        "oid=${md.alignmentOid} " +
                        "alignment=$alignmentStart..$alignmentEnd " +
                        "metadata=${md.startMeter}..${md.endMeter}"
                )
                null
            } else if (md.startMeter < alignmentStart || md.endMeter > alignmentEnd) {
                LOG.warn("Metadata start/end out of alignment bounds: " +
                        "oid=${md.alignmentOid} " +
                        "alignment=$alignmentStart..$alignmentEnd " +
                        "metadata=${md.startMeter}..${md.endMeter}"
                )
            md.copy(
                startMeter = maxOf(alignmentStart, md.startMeter),
                endMeter = minOf(alignmentEnd, md.endMeter),
            )
        } else md
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
