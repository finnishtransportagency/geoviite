package fi.fta.geoviite.infra.map

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
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

data class AlignmentHeader<T, U>(
    val id: DomainId<T>,
    val version: LayoutRowVersion<T>?,
    val trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    val duplicateOf: IntId<LocationTrack>?,
    val name: AlignmentName,
    val state: U,
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
    val points: List<AlignmentPoint>,
) : Loggable {
    override fun toLog(): String = logFormat("id" to id, "type" to alignmentType, "points" to points.size)
}

fun toAlignmentHeader(trackNumber: TrackLayoutTrackNumber, referenceLine: ReferenceLine, alignment: LayoutAlignment?) =
    AlignmentHeader(
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

fun toAlignmentHeader(locationTrack: LocationTrack, alignment: LayoutAlignment?) =
    AlignmentHeader(
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
    alignment.segmentMs.map { s -> s.min } + alignment.length

fun <T> toAlignmentPolyLine(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
    includeSegmentEndPoints: Boolean,
) = AlignmentPolyLine(id, type, simplify(alignment, resolution, bbox, includeSegmentEndPoints))

fun simplify(
    alignment: IAlignment,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
    includeSegmentEndPoints: Boolean,
): List<AlignmentPoint> {
    val segments = bbox?.let(alignment::filterSegmentsByBbox) ?: alignment.segmentsWithM
    var previousM = Double.NEGATIVE_INFINITY
    val isOverResolution = { mValue: Double -> resolution?.let { r -> (mValue - previousM).roundToInt() >= r } ?: true }
    return segments
        .flatMapIndexed { sIndex, (s, m) ->
            val isEndPoint = { pIndex: Int ->
                val isTrackEndPoint =
                    (sIndex == 0 && pIndex == 0) ||
                        (sIndex == segments.lastIndex && pIndex == s.segmentPoints.lastIndex)
                val isSegmentStartPoint = pIndex == 0
                isTrackEndPoint || includeSegmentEndPoints && isSegmentStartPoint
            }
            val isSegmentEndPoint = { pIndex: Int -> pIndex == 0 || pIndex == s.segmentPoints.lastIndex }
            val bboxContains = { pIndex: Int ->
                bbox == null || s.segmentPoints.getOrNull(pIndex)?.let(bbox::contains) ?: false
            }
            s.segmentPoints.mapIndexedNotNull { pIndex, p ->
                if (isPointIncluded(pIndex, p.m + m.min, isEndPoint, isOverResolution, bboxContains)) {
                    if (!isSegmentEndPoint(pIndex)) {
                        // segment end points should be additional points,
                        // so increase m-counter only when handling middle points
                        previousM = m.min + p.m
                    }
                    p.toAlignmentPoint(m.min)
                } else null
            }
        }
        .let { points -> if (points.size >= 2) points else listOf() }
}

private fun isPointIncluded(
    index: Int,
    m: Double,
    isEndPoint: (index: Int) -> Boolean,
    isOverResolution: (m: Double) -> Boolean,
    bboxContains: (index: Int) -> Boolean,
): Boolean {
    val isInsideBbox = bboxContains(index)
    return if (!isInsideBbox) {
        // Outside the box, take the first points on either side to extend the line out
        bboxContains(index - 1) || bboxContains(index + 1)
    } else {
        // Inside the box, take points by resolution + always include endpoints
        isOverResolution(m) || isEndPoint(index)
    }
}
