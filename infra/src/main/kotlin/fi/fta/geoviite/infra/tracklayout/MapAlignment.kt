package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.Version
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.tracklayout.MapAlignmentSource.LAYOUT
import fi.fta.geoviite.infra.tracklayout.MapAlignmentType.LOCATION_TRACK
import fi.fta.geoviite.infra.tracklayout.MapAlignmentType.REFERENCE_LINE
import fi.fta.geoviite.infra.util.FreeText

enum class MapAlignmentSource {
    GEOMETRY,
    LAYOUT,
}
enum class MapAlignmentType {
    LOCATION_TRACK,
    REFERENCE_LINE,
}
/**
 * Simplified alignment is a version of ${TrackLayoutAlignment} that has had it's geometry reduced for UI.
 * As such, it doesn't comply with all the validity requirements of actual alignments,
 * depending on how much it was simplified.
 */
data class MapAlignment<T>(
    val name: AlignmentName,
    val description: FreeText?,
    val alignmentSource: MapAlignmentSource,
    val alignmentType: MapAlignmentType,
    val type: LocationTrackType?,
    val state: LayoutState,
    val segments: List<MapSegment>,
    val trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    val sourceId: DomainId<GeometryAlignment>?,
    val id: DomainId<T>,
    val boundingBox: BoundingBox?,
    val length: Double,
    val dataType: DataType,
    val segmentCount: Int,
    val version: Version,
)

data class MapSegment(
    val pointCount: Int,
    val points: List<LayoutPoint>,
    val sourceId: DomainId<GeometryElement>?,
    val sourceStart: Double?,
    val boundingBox: BoundingBox?,
    val resolution: Int,
    val start: Double,
    val length: Double,
    val source: GeometrySource,
    val id: DomainId<LayoutSegment>,
)

fun toMapAlignment(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment?,
    segmentSimplification: (LayoutAlignment) -> List<MapSegment>
) = MapAlignment(
    name = AlignmentName(trackNumber.number.value),
    description = trackNumber.description,
    alignmentSource = LAYOUT,
    alignmentType = REFERENCE_LINE,
    type = null,
    state = trackNumber.state,
    segments = alignment?.let { a -> segmentSimplification(a) } ?: listOf(),
    trackNumberId = trackNumber.id,
    sourceId = alignment?.sourceId,
    id = referenceLine.id,
    boundingBox = alignment?.boundingBox,
    length = alignment?.length ?: 0.0,
    dataType = referenceLine.dataType,
    segmentCount = alignment?.segments?.size ?: 0,
    version = referenceLine.version,
)

fun toMapAlignment(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment?,
    segmentSimplification: (LayoutAlignment) -> List<MapSegment>
) = MapAlignment(
    name = locationTrack.name,
    description = locationTrack.description,
    alignmentSource = LAYOUT,
    alignmentType = LOCATION_TRACK,
    type = locationTrack.type,
    state = locationTrack.state,
    segments = alignment?.let { a -> segmentSimplification(a) } ?: listOf(),
    trackNumberId = locationTrack.trackNumberId,
    sourceId = alignment?.sourceId,
    id = locationTrack.id,
    boundingBox = alignment?.boundingBox,
    length = alignment?.length ?: 0.0,
    dataType = locationTrack.dataType,
    segmentCount = alignment?.segments?.size ?: 0,
    version = locationTrack.version,
)

fun toMapSegment(
    segment: LayoutSegment,
    resolution: Int = segment.resolution,
    points: List<LayoutPoint> = segment.points,
) = MapSegment(
    pointCount = segment.points.size,
    points = points,
    sourceId = segment.sourceId,
    sourceStart = segment.sourceStart,
    boundingBox = segment.boundingBox,
    resolution = resolution,
    start = segment.start,
    length = segment.length,
    id = segment.id,
    source = segment.source,
)

fun simplify(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment?,
) = toMapAlignment(trackNumber, referenceLine, alignment, segmentSimplification())

fun simplify(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment?,
) = toMapAlignment(locationTrack, alignment, segmentSimplification())

fun simplify(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment?,
    resolution: Int,
    bbox: BoundingBox,
) = toMapAlignment(trackNumber, referenceLine, alignment, segmentSimplification(bbox, resolution))

fun simplify(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment?,
    resolution: Int,
    bbox: BoundingBox,
) = toMapAlignment(locationTrack, alignment, segmentSimplification(bbox, resolution))

private fun segmentSimplification(bbox: BoundingBox, maxResolution: Int) = {
        alignment: LayoutAlignment -> simplifyAll(maxResolution, filterSegments(alignment, bbox))
}
private fun segmentSimplification() = { alignment: LayoutAlignment ->
    simplifyToSegmentEnds(alignment.segments)
}

fun filterSegments(alignment: LayoutAlignment, bbox: BoundingBox) =
    if (bbox.intersects(alignment.boundingBox)) alignment.segments.filter { s -> bbox.intersects(s.boundingBox) }
    else listOf()

fun simplifyToSegmentEnds(segments: List<LayoutSegment>) = segments.map { segment ->
    toMapSegment(segment, Int.MAX_VALUE, listOf(segment.points.first(), segment.points.last()))
}

/**
 * Reduces ${TrackLayoutAlignment} geometry to given resolution.
 * Resolution 0 means dropping the geometry entirely.
 */
fun simplifyAll(resolution: Int, segments: List<LayoutSegment>) =
    when {
        resolution < 0 -> throw IllegalArgumentException("Resolution should be 0 or higher")
        resolution == 0 || segments.isEmpty() -> listOf()
        resolution == 1 || segments.any { s -> s.resolution >= resolution } -> segments.map(::toMapSegment)
        else -> simplify(resolution, segments)
    }


fun simplify(maxResolution: Int, segments: List<LayoutSegment>): List<MapSegment> {
    return segments.map { segment ->
        val divisor: Int = if (maxResolution < segment.resolution) 1 else maxResolution / segment.resolution
        val points = segment.points.filterIndexed { index, _ ->
            index == segment.points.lastIndex || index % divisor == 0
        }
        toMapSegment(segment, segment.resolution * divisor, points)
    }
}
