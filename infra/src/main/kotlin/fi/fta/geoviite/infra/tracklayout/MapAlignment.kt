package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryElement
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point3DM
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
    override val segments: List<MapSegment>,
    val trackNumberId: DomainId<TrackLayoutTrackNumber>?,
    val sourceId: DomainId<GeometryAlignment>?,
    override val id: DomainId<T>,
    override val boundingBox: BoundingBox?,
    override val length: Double,
    val segmentCount: Int,
    val version: RowVersion<T>?,
    val duplicateOf: IntId<LocationTrack>?,
): IAlignment

data class MapSegment(
    @JsonIgnore
    override val geometry: SegmentGeometry,
    val pointCount: Int,
    override val sourceId: DomainId<GeometryElement>?,
    override val sourceStart: Double?,
    override val source: GeometrySource,
    override val id: DomainId<LayoutSegment>,
    val planId: DomainId<GeometryPlan>? = null,
    val hasProfile: Boolean? = null,
): ISegment, ISegmentGeometry by geometry

data class MapSegmentPlanData<T>(
    val id: IntId<T>,
    val alignmentId: IndexedId<LayoutSegment>,
    val points: List<LayoutPoint>,
    val segmentStart: Double,
    val hasProfile: Boolean
)

fun toMapAlignment(
    trackNumber: TrackLayoutTrackNumber,
    referenceLine: ReferenceLine,
    alignment: LayoutAlignment?,
    segmentSimplification: (LayoutAlignment) -> List<MapSegment>
) = MapAlignment(
    name = AlignmentName(trackNumber.number.toString()),
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
    segmentCount = alignment?.segments?.size ?: 0,
    version = referenceLine.version,
    duplicateOf = null,
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
    segmentCount = alignment?.segments?.size ?: 0,
    version = locationTrack.version,
    duplicateOf = locationTrack.duplicateOf
)

fun toMapSegment(
    segmentFields: ISegmentFields,
    newGeometry: SegmentGeometry,
    originalPointCount: Int,
) = MapSegment(
    geometry = newGeometry,
    pointCount = originalPointCount,
    sourceId = segmentFields.sourceId,
    sourceStart = segmentFields.sourceStart,
    id = segmentFields.id,
    source = segmentFields.source,
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

fun <T> simplify(
    alignment: MapAlignment<T>,
    resolution: Int,
) = alignment.copy(segments = simplifyAll(resolution, alignment.segments))

private fun segmentSimplification(bbox: BoundingBox, maxResolution: Int) = { alignment: LayoutAlignment ->
    simplifyAll(maxResolution, filterSegments(alignment, bbox))
}

private fun segmentSimplification() = { alignment: LayoutAlignment ->
    simplifyToSegmentEnds(alignment.segments)
}

fun filterSegments(alignment: LayoutAlignment, bbox: BoundingBox) =
    if (bbox.intersects(alignment.boundingBox)) filterSegments(alignment.segments, bbox)
    else listOf()

fun filterSegments(segments: List<ISegment>, bbox: BoundingBox) =
    segments.filter { s -> bbox.intersects(s.boundingBox) }

fun simplifyToSegmentEnds(segments: List<ISegment>) = segments.map { segment -> toMapSegment(
    segmentFields = segment,
    newGeometry = SegmentGeometry(Int.MAX_VALUE, listOf(segment.points.first(), segment.points.last())),
    originalPointCount = segment.points.size,
) }

/**
 * Reduces ${TrackLayoutAlignment} geometry to given resolution.
 * Resolution 0 means dropping the geometry entirely.
 */
fun simplifyAll(resolution: Int, segments: List<ISegment>) =
    when {
        resolution < 0 -> throw IllegalArgumentException("Resolution should be 0 or higher")
        resolution == 0 || segments.isEmpty() -> listOf()
        resolution == 1 || segments.all { s -> s.resolution >= resolution } -> segments.map { segment ->
            if (segment is MapSegment) segment
            else toMapSegment(segment, segment.geometry, segment.points.size)
        }
        else -> simplify(resolution, segments)
    }


fun simplify(maxResolution: Int, segments: List<ISegment>): List<MapSegment> {
    return segments.map { segment ->
        val divisor: Int = if (maxResolution < segment.resolution) 1 else maxResolution / segment.resolution
        val points = segment.points.filterIndexed { index, _ ->
            index == segment.points.lastIndex || index % divisor == 0
        }
        toMapSegment(segment, SegmentGeometry(segment.resolution * divisor, points), segment.points.size)
    }
}
