package fi.fta.geoviite.infra.map

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geography.bufferedPolygonForLineStringPoints
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.MIN_POLYGON_POINTS
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.PlanLayoutAlignmentM
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.segmentToAlignmentM
import fi.fta.geoviite.infra.util.produceIf
import fi.fta.geoviite.infra.util.rangesOfConsecutiveIndicesOf
import kotlin.math.roundToInt

enum class MapAlignmentSource {
    GEOMETRY,
    LAYOUT,
}

enum class MapAlignmentType {
    LOCATION_TRACK,
    REFERENCE_LINE,
}

sealed class AlignmentHeader<AlignmentType, StateType> {
    abstract val id: DomainId<AlignmentType>
    abstract val trackNumberId: DomainId<LayoutTrackNumber>?
    abstract val state: StateType
    abstract val alignmentSource: MapAlignmentSource
    abstract val alignmentType: MapAlignmentType
    abstract val length: LineM<*>
    abstract val boundingBox: BoundingBox?
    abstract val segmentCount: Int
    abstract val name: AlignmentName
}

data class GeometryAlignmentHeader(
    override val id: DomainId<GeometryAlignment>,
    override val trackNumberId: DomainId<LayoutTrackNumber>?,
    override val name: AlignmentName,
    override val state: LayoutState,
    override val alignmentType: MapAlignmentType,
    override val length: LineM<PlanLayoutAlignmentM>,
    override val boundingBox: BoundingBox?,
    override val segmentCount: Int,
) : AlignmentHeader<GeometryAlignment, LayoutState>() {
    override val alignmentSource = MapAlignmentSource.GEOMETRY
}

data class ReferenceLineHeader(
    override val id: IntId<ReferenceLine>,
    val version: LayoutRowVersion<ReferenceLine>,
    override val trackNumberId: IntId<LayoutTrackNumber>,
    override val name: AlignmentName,
    override val state: LayoutState,
    override val length: LineM<ReferenceLineM>,
    override val boundingBox: BoundingBox?,
    override val segmentCount: Int,
) : AlignmentHeader<ReferenceLine, LayoutState>() {
    override val alignmentSource = MapAlignmentSource.LAYOUT
    override val alignmentType = MapAlignmentType.REFERENCE_LINE
}

data class LocationTrackHeader(
    override val id: IntId<LocationTrack>,
    val version: LayoutRowVersion<LocationTrack>,
    override val trackNumberId: IntId<LayoutTrackNumber>,
    val duplicateOf: IntId<LocationTrack>?,
    override val name: AlignmentName,
    override val state: LocationTrackState,
    val trackType: LocationTrackType,
    override val length: LineM<LocationTrackM>,
    override val boundingBox: BoundingBox?,
    override val segmentCount: Int,
) : AlignmentHeader<LocationTrack, LocationTrackState>() {
    override val alignmentSource = MapAlignmentSource.LAYOUT
    override val alignmentType = MapAlignmentType.LOCATION_TRACK
}

data class AlignmentPolyLine<T, M : AlignmentM<M>>(
    val id: DomainId<T>,
    val alignmentType: MapAlignmentType,
    val points: List<AlignmentPoint<M>>,
) : Loggable {
    override fun toLog(): String = logFormat("id" to id, "type" to alignmentType, "points" to points.size)
}

fun toAlignmentHeader(trackNumber: LayoutTrackNumber, referenceLine: ReferenceLine, geometry: ReferenceLineGeometry?) =
    ReferenceLineHeader(
        id = referenceLine.id.also { require(it is IntId) } as IntId,
        version = requireNotNull(referenceLine.version),
        trackNumberId = referenceLine.trackNumberId,
        name = AlignmentName(trackNumber.number.toString()),
        state = trackNumber.state,
        length = geometry?.length ?: LineM(0.0),
        segmentCount = referenceLine.segmentCount,
        boundingBox = geometry?.boundingBox,
    )

fun toAlignmentHeader(locationTrack: LocationTrack, geometry: DbLocationTrackGeometry) =
    LocationTrackHeader(
        id = locationTrack.id.also { require(it is IntId) } as IntId,
        version = requireNotNull(locationTrack.version),
        trackNumberId = locationTrack.trackNumberId,
        duplicateOf = locationTrack.duplicateOf,
        name = locationTrack.name,
        state = locationTrack.state,
        trackType = locationTrack.type,
        length = geometry.length,
        segmentCount = locationTrack.segmentCount,
        boundingBox = geometry.boundingBox,
    )

fun <M : AlignmentM<M>> getSegmentBorderMValues(alignment: IAlignment<M>): List<LineM<M>> =
    alignment.segmentMValues.map { s -> s.min } + alignment.length

fun <T, M : AlignmentM<M>> toAlignmentPolyLine(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment<M>,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
    includeSegmentEndPoints: Boolean,
) = AlignmentPolyLine(id, type, simplify(alignment, resolution, bbox, includeSegmentEndPoints))

const val ALIGNMENT_POLYGON_BUFFER = 10.0
const val ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION = 100

fun <M : AlignmentM<M>> toPolygon(
    alignment: IAlignment<M>,
    polygonBufferSize: Double = ALIGNMENT_POLYGON_BUFFER,
): Polygon? {
    val simplifiedAlignment =
        simplify(
            alignment = alignment,
            resolution = ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION,
            bbox = null,
            includeSegmentEndPoints = true,
        )
    val points = bufferedPolygonForLineStringPoints(simplifiedAlignment, polygonBufferSize, LAYOUT_SRID)
    return produceIf(points.size >= MIN_POLYGON_POINTS) { Polygon(points) }
}

fun <M : AlignmentM<M>> simplify(
    alignment: IAlignment<M>,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
    includeSegmentEndPoints: Boolean,
): List<AlignmentPoint<M>> {
    val segments = bbox?.let(alignment::filterSegmentsByBbox) ?: alignment.segmentsWithM
    var previousM = LineM<M>(0.0)
    val isOverResolution = { mValue: LineM<M> ->
        resolution?.let { r -> (mValue - previousM).distance.roundToInt() >= r } ?: true
    }
    val rv = mutableListOf<AlignmentPoint<M>>()
    segments.forEachIndexed { sIndex, (s, m) ->
        if (sIndex == 0 || sIndex == segments.lastIndex || isOverResolution(m.max)) {
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

            rangesOfConsecutiveIndicesOf(true, s.segmentPoints.indices.map(bboxContains)).forEach { range ->
                if (range.first > 0) {
                    rv.add(s.segmentPoints[range.first - 1].toAlignmentPoint(m.min))
                }
                range.forEach { index ->
                    val point = s.segmentPoints[index]
                    val pointM = point.m.segmentToAlignmentM(m.min)
                    if (isOverResolution(pointM) || isEndPoint(index)) {
                        if (!isSegmentEndPoint(index)) {
                            // segment end points should be additional points,
                            // so increase m-counter only when handling middle points
                            previousM = pointM
                        }
                        rv.add(point.toAlignmentPoint(m.min))
                    }
                }
                if (range.endInclusive < s.segmentPoints.lastIndex) {
                    rv.add(s.segmentPoints[range.endInclusive + 1].toAlignmentPoint(m.min))
                }
            }
        } else {
            if (includeSegmentEndPoints) {
                rv.add(s.segmentPoints.first().toAlignmentPoint(m.min))
                rv.add(s.segmentPoints.last().toAlignmentPoint(m.min))
            }
        }
    }
    return if (rv.size >= 2) rv else listOf()
}

private fun <M : AlignmentM<M>> isPointIncluded(
    index: Int,
    m: LineM<M>,
    isEndPoint: (index: Int) -> Boolean,
    isOverResolution: (m: LineM<M>) -> Boolean,
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
