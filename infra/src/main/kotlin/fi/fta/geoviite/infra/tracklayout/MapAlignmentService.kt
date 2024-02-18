package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.map.*
import fi.fta.geoviite.infra.map.MapAlignmentType.LOCATION_TRACK
import fi.fta.geoviite.infra.map.MapAlignmentType.REFERENCE_LINE
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.combineContinuous
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

enum class AlignmentFetchType {
    LOCATION_TRACKS,
    REFERENCE_LINES,
    ALL,
}

data class MapAlignmentHighlight<T>(
    val id: IntId<T>,
    val type: MapAlignmentType,
    val ranges: List<Range<Double>>,
)

data class MapAlignmentEndPoints(
    val start: List<AlignmentPoint>,
    val end: List<AlignmentPoint>,
)

@Service
class MapAlignmentService(
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun getAlignmentPolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
        type: AlignmentFetchType,
    ): List<AlignmentPolyLine<*>> {
        logger.serviceCall("getAlignmentPolyLines",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
        )
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLinePolyLines(publishType, bbox, resolution)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else getLocationTrackPolyLines(publishType, bbox, resolution)

        return (referenceLines + locationTracks).filter { pl -> pl.points.isNotEmpty() }
    }

    fun getAlignmentPolyline(
        id: IntId<LocationTrack>,
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ): AlignmentPolyLine<LocationTrack>? {
        return locationTrackService
            .getWithAlignment(publishType, id)
            ?.takeIf { (t, _) -> t.state != LayoutState.DELETED }
            ?.let { (track, alignment) -> toAlignmentPolyLine(track.id, LOCATION_TRACK, alignment, resolution, bbox) }
    }

    fun getSectionsWithoutLinking(
        publishType: PublishType,
        bbox: BoundingBox,
        type: AlignmentFetchType,
    ): List<MapAlignmentHighlight<*>> {
        logger.serviceCall( "getSectionsWithoutLinking",
            "publishType" to publishType, "bbox" to bbox, "type" to type
        )
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLineMissingLinkings(publishType, bbox)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else getLocationTrackMissingLinkings(publishType, bbox)
        return referenceLines + locationTracks
    }

    fun getSectionsWithoutProfile(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        logger.serviceCall("getSectionsWithoutProfile", "publishType" to publishType, "bbox" to bbox)
        return alignmentDao
            .fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(publishType, bbox)
            .filter { !it.hasProfile }
            .groupBy { it.id }
            .map { (id, profileInfos) ->
                MapAlignmentHighlight(
                    id = id,
                    type = LOCATION_TRACK,
                    ranges = profileInfos.fold(mutableMapOf<Int, Range<Double>>()) { acc, info ->
                        val prev = acc.remove(info.alignmentId.index - 1)
                        acc[info.alignmentId.index] = Range(prev?.min ?: info.segmentStartM, info.segmentEndM)
                        acc
                    }.values.toList()
                )
            }
    }

    @Transactional(readOnly = true)
    fun getReferenceLineHeaders(
        publishType: PublishType,
        referenceLineIds: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine>> {
        val referenceLines = referenceLineService.getManyWithAlignments(publishType, referenceLineIds)
        val trackNumbers = trackNumberService
            .getMany(publishType, referenceLines.map { (rl, _) -> rl.trackNumberId })
            .associateBy(TrackLayoutTrackNumber::id)
        return referenceLines.map { (line, alignment) ->
            val trackNumber = requireNotNull(trackNumbers[line.trackNumberId]) {
                "ReferenceLine in DB must have a TrackNumber: line=${line.id} trackNumberId=${line.trackNumberId}"
            }
            toAlignmentHeader(trackNumber, line, alignment)
        }
    }

    fun getLocationTrackHeaders(
        publishType: PublishType,
        locationTrackIds: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack>> {
        return locationTrackService
            .getManyWithAlignments(publishType, locationTrackIds)
            .map { (track, alignment) -> toAlignmentHeader(track, alignment) }
    }

    fun getLocationTrackSegmentMValues(publishType: PublishType, id: IntId<LocationTrack>): List<Double> {
        logger.serviceCall("getLocationTrackSegmentMValues", "publishType" to publishType, "id" to id)
        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(publishType, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getReferenceLineSegmentMValues(publishType: PublishType, id: IntId<ReferenceLine>): List<Double> {
        logger.serviceCall("getReferenceLineSegmentMValues", "publishType" to publishType, "id" to id)
        val (_, alignment) = referenceLineService.getWithAlignmentOrThrow(publishType, id)
        return getSegmentBorderMValues(alignment)
    }

    fun getLocationTrackEnds(publishType: PublishType, id: IntId<LocationTrack>): MapAlignmentEndPoints {
        logger.serviceCall("getLocationTrackEnds", "publishType" to publishType, "id" to id)
        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(publishType, id)
        return getEndPoints(alignment)
    }

    fun getReferenceLineEnds(publishType: PublishType, id: IntId<ReferenceLine>): MapAlignmentEndPoints {
        logger.serviceCall("getReferenceLineEnds", "publishType" to publishType, "id" to id)
        val (_, alignment) = referenceLineService.getWithAlignmentOrThrow(publishType, id)
        return getEndPoints(alignment)
    }

    private fun getLocationTrackPolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ): List<AlignmentPolyLine<LocationTrack>> = locationTrackService
        .listWithAlignments(publishType, includeDeleted = false)
        .map { (track, alignment) -> toAlignmentPolyLine(track.id, LOCATION_TRACK, alignment, resolution, bbox) }

    private fun getReferenceLinePolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ): List<AlignmentPolyLine<*>> {
        val trackNumbers = trackNumberService.mapById(publishType)
        return referenceLineService
            .listWithAlignments(publishType, includeDeleted = false)
            .mapNotNull { (line, alignment) ->
                val trackNumber = trackNumbers[line.trackNumberId]
                if (trackNumber != null) toAlignmentPolyLine(line.id, REFERENCE_LINE, alignment, resolution, bbox)
                else null
            }
    }

    private fun getLocationTrackMissingLinkings(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> = locationTrackService
        .listWithAlignments(publishType, boundingBox = bbox, includeDeleted = false)
        .mapNotNull { (track, alignment) -> getMissingLinkings(track.id, LOCATION_TRACK, alignment) }

    private fun getReferenceLineMissingLinkings(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<ReferenceLine>> {
        return referenceLineService
            .listWithAlignments(publishType, boundingBox = bbox, includeDeleted = false)
            .mapNotNull { (line, alignment) -> getMissingLinkings(line.id, REFERENCE_LINE, alignment) }
    }
}

private fun <T> getMissingLinkings(
    id: DomainId<T>,
    type: MapAlignmentType,
    alignment: LayoutAlignment,
): MapAlignmentHighlight<T>? = getMissingLinkingRanges(alignment)
    .takeIf { list -> list.isNotEmpty() }
    ?.let { ranges -> MapAlignmentHighlight(id as IntId, type, ranges) }

private fun getMissingLinkingRanges(alignment: LayoutAlignment): List<Range<Double>> =
    combineContinuous(alignment.segments.filter { s -> s.sourceId == null }.map { s -> Range(s.startM, s.endM) })

private fun getEndPoints(alignment: LayoutAlignment): MapAlignmentEndPoints =
    MapAlignmentEndPoints(alignment.takeFirst(2), alignment.takeLast(2))
