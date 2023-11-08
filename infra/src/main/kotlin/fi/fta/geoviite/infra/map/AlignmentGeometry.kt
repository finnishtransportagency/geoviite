package fi.fta.geoviite.infra.map

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.*
import kotlin.math.roundToInt

enum class MapAlignmentSource {
    GEOMETRY,
    LAYOUT,
}

enum class MapAlignmentType {
    LOCATION_TRACK,
    REFERENCE_LINE,
}

data class AlignmentHeader<T>(
    val id: DomainId<T>,
    val version: RowVersion<T>?,
    val trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    val duplicateOf: IntId<LocationTrack>?,

    val name: AlignmentName,
    val state: LayoutState,
    val alignmentSource: MapAlignmentSource,
    val alignmentType: MapAlignmentType,
    val trackType: LocationTrackType?,

    val length: Double,
    val boundingBox: BoundingBox?,
    val segmentCount: Int,
)

data class AlignmentPolyLine<T>(
    val id: DomainId<T>,
    val alignmentType: MapAlignmentType,
    val points: List<LayoutPoint>,
) : Loggable {
    override fun toLog(): String = logFormat("id" to id, "type" to alignmentType, "points" to points.size)
}

fun toAlignmentHeader(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment?,
) = AlignmentHeader(
    id = referenceLine.id,
    version = referenceLine.version,
    trackNumberId = referenceLine.trackNumberId,
    duplicateOf = null,
    name = AlignmentName(trackNumber.number.toString()),
    state = trackNumber.state,
    alignmentSource = MapAlignmentSource.LAYOUT,
    alignmentType = MapAlignmentType.REFERENCE_LINE,
    trackType = null,
    length = alignment?.length ?: 0.0,
    segmentCount = referenceLine.segmentCount,
    boundingBox = alignment?.boundingBox,
)

fun toAlignmentHeader(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment?,
) = AlignmentHeader(
    id = locationTrack.id,
    version = locationTrack.version,
    trackNumberId = locationTrack.trackNumberId,
    duplicateOf = locationTrack.duplicateOf,
    name = locationTrack.name,
    state = locationTrack.state,
    alignmentSource = MapAlignmentSource.LAYOUT,
    alignmentType = MapAlignmentType.LOCATION_TRACK,
    trackType = locationTrack.type,
    length = alignment?.length ?: 0.0,
    segmentCount = locationTrack.segmentCount,
    boundingBox = alignment?.boundingBox,
)

fun getSegmentBorderMValues(alignment: IAlignment): List<Double> =
    alignment.segments.map { s -> s.startM } + alignment.length

fun <T> toAlignmentPolyLine(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
) = AlignmentPolyLine(id, type, simplify(alignment, resolution, bbox))

fun simplify(
    alignment: IAlignment,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
): List<LayoutPoint> {
    val segments =
        if (bbox == null) alignment.segments
        else if (!bbox.intersects(alignment.boundingBox)) listOf()
        else alignment.segments.filter { s -> s.boundingBox?.intersects(bbox) ?: false }
    var previousM = Double.NEGATIVE_INFINITY
    return segments.flatMapIndexed { sIndex, s ->
        val isLastSegment = sIndex == segments.lastIndex
        val lastPointIndex = s.segmentPoints.lastIndex
        val bboxContains = { index: Int -> bbox == null || s.segmentPoints.getOrNull(index)?.let(bbox::contains) ?: false }
        s.segmentPoints.mapIndexedNotNull { pIndex, p ->
            val resolutionHit = resolution == null
                    || ((p.m + s.startM - previousM).roundToInt() >= resolution)
                    || (isLastSegment && pIndex == lastPointIndex)
            val bboxHit = bboxContains(pIndex)
            // Always take the first point on either side of the bbox to extend the line appropriately
            val outOfBboxExtension = !bboxHit && (bboxContains(pIndex-1) || bboxContains(pIndex+1))
            if (outOfBboxExtension || (resolutionHit && bboxHit)) {
                previousM = s.startM + p.m
                p.toLayoutPoint(s.startM)
            } else null
        }
    }.let { points -> if (points.size >= 2) points else listOf() }
}
