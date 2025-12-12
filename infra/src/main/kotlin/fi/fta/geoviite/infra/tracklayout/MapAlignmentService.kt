package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.map.AlignmentPolyLine
import fi.fta.geoviite.infra.map.MapAlignmentType
import fi.fta.geoviite.infra.map.MapAlignmentType.LOCATION_TRACK
import fi.fta.geoviite.infra.map.MapAlignmentType.REFERENCE_LINE
import fi.fta.geoviite.infra.map.getSegmentBorderMValues
import fi.fta.geoviite.infra.map.toAlignmentHeader
import fi.fta.geoviite.infra.map.toAlignmentPolyLine
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.combineContinuous
import org.springframework.transaction.annotation.Transactional

enum class AlignmentFetchType {
    LOCATION_TRACKS,
    REFERENCE_LINES,
    ALL,
}

data class MapAlignmentHighlight<T, M : AlignmentM<M>>(
    val id: IntId<T>,
    val type: MapAlignmentType,
    val ranges: List<Range<LineM<M>>>,
)

data class MapAlignmentEndPoints<M : AlignmentM<M>>(
    val start: List<AlignmentPoint<M>>,
    val end: List<AlignmentPoint<M>>,
)

@GeoviiteService
class MapAlignmentService(
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    @Transactional(readOnly = true)
    fun getAlignmentPolyLines(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        resolution: Int,
        type: AlignmentFetchType,
        includeSegmentEndPoints: Boolean = false,
        minLength: Double? = null,
        locationTrackIds: Set<IntId<LocationTrack>>? = null,
    ): List<AlignmentPolyLine<*, *>> {
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLinePolyLines(layoutContext, bbox, resolution, includeSegmentEndPoints)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else
                getLocationTrackPolyLines(
                    layoutContext,
                    bbox,
                    resolution,
                    includeSegmentEndPoints,
                    minLength,
                    locationTrackIds,
                )

        return (referenceLines + locationTracks).filter { pl -> pl.points.isNotEmpty() }
    }

    fun getAlignmentPolyline(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
        bbox: BoundingBox,
        resolution: Int,
    ): AlignmentPolyLine<LocationTrack, LocationTrackM>? {
        return locationTrackService
            .getWithGeometry(layoutContext, id)
            ?.takeIf { (t, _) -> t.state != LocationTrackState.DELETED }
            ?.let { (track, geometry) ->
                toAlignmentPolyLine(
                    track.id,
                    LOCATION_TRACK,
                    geometry,
                    resolution,
                    bbox,
                    includeSegmentEndPoints = false,
                )
            }
    }

    fun getSectionsWithoutLinking(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        type: AlignmentFetchType,
    ): List<MapAlignmentHighlight<*, *>> {
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS)
                listOf<MapAlignmentHighlight<LocationTrack, LocationTrackM>>()
            else getReferenceLineMissingLinkings(layoutContext, bbox)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES)
                listOf<MapAlignmentHighlight<ReferenceLine, ReferenceLineM>>()
            else getLocationTrackMissingLinkings(layoutContext, bbox)
        return referenceLines + locationTracks
    }

    fun getSectionsWithoutProfile(
        layoutContext: LayoutContext,
        ids: List<IntId<LocationTrack>>,
    ): List<MapAlignmentHighlight<LocationTrack, LocationTrackM>> {
        return alignmentDao
            .fetchLocationTrackProfileInfos(layoutContext, ids, false)
            .groupBy { it.id }
            .map { (id, profileInfos) ->
                MapAlignmentHighlight(
                    id = id,
                    type = LOCATION_TRACK,
                    ranges = combineContinuous(profileInfos.map { i -> i.mRange }),
                )
            }
    }

    @Transactional(readOnly = true)
    fun getReferenceLineHeaders(
        layoutContext: LayoutContext,
        referenceLineIds: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine, LayoutState>> {
        val referenceLines = referenceLineService.getManyWithGeometries(layoutContext, referenceLineIds)
        val trackNumbers =
            trackNumberService
                .getMany(layoutContext, referenceLines.map { (rl, _) -> rl.trackNumberId })
                .associateBy(LayoutTrackNumber::id)
        return referenceLines.map { (line, alignment) ->
            val trackNumber =
                requireNotNull(trackNumbers[line.trackNumberId]) {
                    "ReferenceLine in DB must have a TrackNumber: line=${line.id} trackNumberId=${line.trackNumberId}"
                }
            toAlignmentHeader(trackNumber, line, alignment)
        }
    }

    fun getLocationTrackHeaders(
        layoutContext: LayoutContext,
        locationTrackIds: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack, LocationTrackState>> {
        return locationTrackService.getManyWithGeometries(layoutContext, locationTrackIds).map { (track, geometry) ->
            toAlignmentHeader(track, geometry)
        }
    }

    fun getLocationTrackSegmentMValues(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): List<LineM<LocationTrackM>> {
        val (_, alignment) = locationTrackService.getWithGeometryOrThrow(layoutContext, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getReferenceLineSegmentMValues(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): List<LineM<ReferenceLineM>> {
        val (_, alignment) = referenceLineService.getWithGeometryOrThrow(layoutContext, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getLocationTrackEnds(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
    ): MapAlignmentEndPoints<LocationTrackM> {
        val (_, alignment) = locationTrackService.getWithGeometryOrThrow(layoutContext, id)
        return getEndPoints(alignment)
    }

    fun getReferenceLineEnds(
        layoutContext: LayoutContext,
        id: IntId<ReferenceLine>,
    ): MapAlignmentEndPoints<ReferenceLineM> {
        val (_, alignment) = referenceLineService.getWithGeometryOrThrow(layoutContext, id)
        return getEndPoints(alignment)
    }

    private fun getLocationTrackPolyLines(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        resolution: Int,
        includeSegmentEndPoints: Boolean,
        minLength: Double? = null,
        locationTrackIds: Set<IntId<LocationTrack>>? = null,
    ): List<AlignmentPolyLine<LocationTrack, LocationTrackM>> =
        locationTrackService
            .listWithGeometries(
                layoutContext,
                includeDeleted = false,
                boundingBox = bbox,
                minLength = minLength,
                locationTrackIds = locationTrackIds,
            )
            .map { (track, geometry) ->
                toAlignmentPolyLine(track.id, LOCATION_TRACK, geometry, resolution, bbox, includeSegmentEndPoints)
            }

    private fun getReferenceLinePolyLines(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        resolution: Int,
        includeSegmentEndPoints: Boolean,
    ): List<AlignmentPolyLine<ReferenceLine, ReferenceLineM>> {
        val trackNumbers = trackNumberService.mapById(layoutContext)
        return referenceLineService
            .listWithGeometries(layoutContext, includeDeleted = false, boundingBox = bbox)
            .mapNotNull { (line, alignment) ->
                val trackNumber = trackNumbers[line.trackNumberId]
                if (trackNumber != null)
                    toAlignmentPolyLine(line.id, REFERENCE_LINE, alignment, resolution, bbox, includeSegmentEndPoints)
                else null
            }
    }

    private fun getLocationTrackMissingLinkings(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack, LocationTrackM>> =
        locationTrackService.listWithGeometries(layoutContext, boundingBox = bbox, includeDeleted = false).mapNotNull {
            (track, geometry) ->
            getMissingLinkings(track.id, LOCATION_TRACK, geometry)
        }

    private fun getReferenceLineMissingLinkings(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<ReferenceLine, ReferenceLineM>> {
        return referenceLineService
            .listWithGeometries(layoutContext, boundingBox = bbox, includeDeleted = false)
            .mapNotNull { (line, alignment) -> getMissingLinkings(line.id, REFERENCE_LINE, alignment) }
    }
}

private fun <T, M : AlignmentM<M>> getMissingLinkings(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: IAlignment<M>,
): MapAlignmentHighlight<T, M>? =
    getMissingLinkingRanges(alignment)
        .takeIf { list -> list.isNotEmpty() }
        ?.let { ranges -> MapAlignmentHighlight(id as IntId, type, ranges) }

private fun <M : AlignmentM<M>> getMissingLinkingRanges(alignment: IAlignment<M>): List<Range<LineM<M>>> =
    combineContinuous(alignment.segmentsWithM.mapNotNull { (s, m) -> m.takeIf { s.sourceId == null } })

private fun <M : AlignmentM<M>> getEndPoints(alignment: IAlignment<M>): MapAlignmentEndPoints<M> =
    MapAlignmentEndPoints(alignment.takeFirst(2), alignment.takeLast(2))
