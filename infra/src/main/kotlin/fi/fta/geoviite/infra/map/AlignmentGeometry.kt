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

fun getSegmentBorderMValues(alignment: IAlignment) =
    alignment.segments.map { s -> s.startM } + alignment.length

fun <T> toAlignmentPolyLine(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment,
    resolution: Int ? = null,
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
    var previousInBbox = false
    return segments
        .flatMap { s -> s.points.filter { p ->
            val result =
                (resolution == null || (p.m - previousM).roundToInt() >= resolution || (p.m == alignment.length && previousM < p.m))
                        && (bbox == null || previousInBbox || bbox.contains(p))
            if (result) {
                previousM = p.m
                previousInBbox = bbox == null || bbox.contains(p)
            }
            result
        } }
        .let { points -> if (points.size >= 2) points else listOf() }
}
