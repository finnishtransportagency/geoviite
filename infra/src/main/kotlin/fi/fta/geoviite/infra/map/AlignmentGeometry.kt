package fi.fta.geoviite.infra.map

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geography.bufferedPolygonForLineStringPoints
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.AugLocationTrack
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
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
    abstract val name: AlignmentName
    abstract val state: StateType
    abstract val alignmentSource: MapAlignmentSource
    abstract val alignmentType: MapAlignmentType
    abstract val length: Double
    abstract val boundingBox: BoundingBox?
    abstract val segmentCount: Int
}

data class GeometryAlignmentHeader(
    override val id: DomainId<GeometryAlignment>,
    override val trackNumberId: DomainId<LayoutTrackNumber>?,
    override val name: AlignmentName,
    override val state: LayoutState,
    override val alignmentType: MapAlignmentType,
    override val length: Double,
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
    override val length: Double,
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
    override val length: Double,
    override val boundingBox: BoundingBox?,
    override val segmentCount: Int,
) : AlignmentHeader<LocationTrack, LocationTrackState>() {
    override val alignmentSource = MapAlignmentSource.LAYOUT
    override val alignmentType = MapAlignmentType.LOCATION_TRACK
}

data class AlignmentPolyLine<T>(
    val id: DomainId<T>,
    val alignmentType: MapAlignmentType,
    val points: List<AlignmentPoint>,
) : Loggable {
    override fun toLog(): String = logFormat("id" to id, "type" to alignmentType, "points" to points.size)
}

fun toAlignmentHeader(trackNumber: LayoutTrackNumber, referenceLine: ReferenceLine, alignment: LayoutAlignment?) =
    ReferenceLineHeader(
        id = referenceLine.id.also { require(it is IntId) } as IntId,
        version = requireNotNull(referenceLine.version),
        trackNumberId = referenceLine.trackNumberId,
        name = AlignmentName(trackNumber.number.toString()),
        state = trackNumber.state,
        length = alignment?.length ?: 0.0,
        segmentCount = referenceLine.segmentCount,
        boundingBox = alignment?.boundingBox,
    )

fun toAlignmentHeader(locationTrack: AugLocationTrack, alignment: DbLocationTrackGeometry) =
    LocationTrackHeader(
        id = locationTrack.id.also { require(it is IntId) } as IntId,
        version = requireNotNull(locationTrack.version),
        trackNumberId = locationTrack.trackNumberId,
        duplicateOf = locationTrack.duplicateOf,
        name = locationTrack.name,
        state = locationTrack.state,
        trackType = locationTrack.type,
        length = alignment.length,
        segmentCount = locationTrack.segmentCount,
        boundingBox = alignment.boundingBox,
    )

fun getSegmentBorderMValues(alignment: IAlignment): List<Double> =
    alignment.segmentMValues.map { s -> s.min } + alignment.length

fun <T> toAlignmentPolyLine(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment,
    resolution: Int? = null,
    bbox: BoundingBox? = null,
    includeSegmentEndPoints: Boolean,
) = AlignmentPolyLine(id, type, simplify(alignment, resolution, bbox, includeSegmentEndPoints))

const val ALIGNMENT_POLYGON_BUFFER = 10.0
const val ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION = 100

fun toPolygon(alignment: IAlignment, polygonBufferSize: Double = ALIGNMENT_POLYGON_BUFFER): List<IPoint> {
    val simplifiedAlignment =
        simplify(
            alignment = alignment,
            resolution = ALIGNMENT_POLYGON_SIMPLIFICATION_RESOLUTION,
            bbox = null,
            includeSegmentEndPoints = true,
        )
    return bufferedPolygonForLineStringPoints(simplifiedAlignment, polygonBufferSize, LAYOUT_SRID)
}

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
            } else {
                if (includeSegmentEndPoints)
                    listOf(
                        s.segmentPoints.first().toAlignmentPoint(m.min),
                        s.segmentPoints.last().toAlignmentPoint(m.max),
                    )
                else emptyList()
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
