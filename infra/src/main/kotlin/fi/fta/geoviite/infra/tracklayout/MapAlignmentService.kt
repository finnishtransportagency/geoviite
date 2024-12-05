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

data class MapAlignmentHighlight<T>(val id: IntId<T>, val type: MapAlignmentType, val ranges: List<Range<Double>>)

data class MapAlignmentEndPoints(val start: List<AlignmentPoint>, val end: List<AlignmentPoint>)

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
    ): List<AlignmentPolyLine<*>> {
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLinePolyLines(layoutContext, bbox, resolution, includeSegmentEndPoints)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else getLocationTrackPolyLines(layoutContext, bbox, resolution, includeSegmentEndPoints)

        return (referenceLines + locationTracks).filter { pl -> pl.points.isNotEmpty() }
    }

    fun getAlignmentPolyline(
        layoutContext: LayoutContext,
        id: IntId<LocationTrack>,
        bbox: BoundingBox,
        resolution: Int,
    ): AlignmentPolyLine<LocationTrack>? {
        return locationTrackService
            .getWithAlignment(layoutContext, id)
            ?.takeIf { (t, _) -> t.state != LocationTrackState.DELETED }
            ?.let { (track, alignment) ->
                toAlignmentPolyLine(
                    track.id,
                    LOCATION_TRACK,
                    alignment,
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
    ): List<MapAlignmentHighlight<*>> {
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLineMissingLinkings(layoutContext, bbox)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else getLocationTrackMissingLinkings(layoutContext, bbox)
        return referenceLines + locationTracks
    }

    fun getSectionsWithoutProfile(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        return alignmentDao
            .fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(layoutContext, bbox, false)
            .groupBy { it.id }
            .map { (id, profileInfos) ->
                MapAlignmentHighlight(
                    id = id,
                    type = LOCATION_TRACK,
                    ranges =
                        profileInfos
                            .fold(mutableMapOf<Int, Range<Double>>()) { acc, info ->
                                val prev = acc.remove(info.alignmentId.index - 1)
                                acc[info.alignmentId.index] = Range(prev?.min ?: info.segmentStartM, info.segmentEndM)
                                acc
                            }
                            .values
                            .toList(),
                )
            }
    }

    @Transactional(readOnly = true)
    fun getReferenceLineHeaders(
        layoutContext: LayoutContext,
        referenceLineIds: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine, LayoutState>> {
        val referenceLines = referenceLineService.getManyWithAlignments(layoutContext, referenceLineIds)
        val trackNumbers =
            trackNumberService
                .getMany(layoutContext, referenceLines.map { (rl, _) -> rl.trackNumberId })
                .associateBy(TrackLayoutTrackNumber::id)
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
        return locationTrackService.getManyWithAlignments(layoutContext, locationTrackIds).map { (track, alignment) ->
            toAlignmentHeader(track, alignment)
        }
    }

    fun getLocationTrackSegmentMValues(layoutContext: LayoutContext, id: IntId<LocationTrack>): List<Double> {
        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(layoutContext, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getReferenceLineSegmentMValues(layoutContext: LayoutContext, id: IntId<ReferenceLine>): List<Double> {
        val (_, alignment) = referenceLineService.getWithAlignmentOrThrow(layoutContext, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getLocationTrackEnds(layoutContext: LayoutContext, id: IntId<LocationTrack>): MapAlignmentEndPoints {
        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(layoutContext, id)
        return getEndPoints(alignment)
    }

    fun getReferenceLineEnds(layoutContext: LayoutContext, id: IntId<ReferenceLine>): MapAlignmentEndPoints {
        val (_, alignment) = referenceLineService.getWithAlignmentOrThrow(layoutContext, id)
        return getEndPoints(alignment)
    }

    private fun getLocationTrackPolyLines(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        resolution: Int,
        includeSegmentEndPoints: Boolean,
    ): List<AlignmentPolyLine<LocationTrack>> =
        locationTrackService.listWithAlignments(layoutContext, includeDeleted = false, boundingBox = bbox).map {
            (track, alignment) ->
            toAlignmentPolyLine(track.id, LOCATION_TRACK, alignment, resolution, bbox, includeSegmentEndPoints)
        }

    private fun getReferenceLinePolyLines(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
        resolution: Int,
        includeSegmentEndPoints: Boolean,
    ): List<AlignmentPolyLine<*>> {
        val trackNumbers = trackNumberService.mapById(layoutContext)
        return referenceLineService
            .listWithAlignments(layoutContext, includeDeleted = false, boundingBox = bbox)
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
    ): List<MapAlignmentHighlight<LocationTrack>> =
        locationTrackService.listWithAlignments(layoutContext, boundingBox = bbox, includeDeleted = false).mapNotNull {
            (track, alignment) ->
            getMissingLinkings(track.id, LOCATION_TRACK, alignment)
        }

    private fun getReferenceLineMissingLinkings(
        layoutContext: LayoutContext,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<ReferenceLine>> {
        return referenceLineService
            .listWithAlignments(layoutContext, boundingBox = bbox, includeDeleted = false)
            .mapNotNull { (line, alignment) -> getMissingLinkings(line.id, REFERENCE_LINE, alignment) }
    }
}

private fun <T> getMissingLinkings(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: LayoutAlignment,
): MapAlignmentHighlight<T>? =
    getMissingLinkingRanges(alignment)
        .takeIf { list -> list.isNotEmpty() }
        ?.let { ranges -> MapAlignmentHighlight(id as IntId, type, ranges) }

private fun getMissingLinkingRanges(alignment: LayoutAlignment): List<Range<Double>> =
    combineContinuous(alignment.segmentsWithM.mapNotNull { (s, m) -> m.takeIf { s.sourceId == null } })

private fun getEndPoints(alignment: LayoutAlignment): MapAlignmentEndPoints =
    MapAlignmentEndPoints(alignment.takeFirst(2), alignment.takeLast(2))
