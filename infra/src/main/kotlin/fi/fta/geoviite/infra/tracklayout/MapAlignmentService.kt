package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
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

@Service
class MapAlignmentService(
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getAlignmentPolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
        type: AlignmentFetchType,
        selectedId: IntId<LocationTrack>?,
    ): List<AlignmentPolyLine<*>> {
        logger.serviceCall("getAlignmentPolyLines",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "selectedId" to selectedId,
        )
        val referenceLines =
            if (type == AlignmentFetchType.LOCATION_TRACKS) listOf()
            else getReferenceLinePolyLines(publishType, bbox, resolution)
        val locationTracks =
            if (type == AlignmentFetchType.REFERENCE_LINES) listOf()
            else getLocationTrackPolyLines(publishType, bbox, resolution)
        val selected = selectedId?.let { id ->
            if (locationTracks.any { t -> t.id == selectedId }) null
            else locationTrackService.get(publishType, id)
                ?.takeIf { t -> t.state != LayoutState.DELETED }
                ?.let { toAlignmentPolyLine(it.id, LOCATION_TRACK, it.alignmentVersion, bbox, resolution) }
        }
        return (referenceLines + locationTracks + listOfNotNull(selected)).filter { pl -> pl.points.isNotEmpty() }
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
        return alignmentDao.fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(publishType, bbox)
            .filter { !it.hasProfile }
            .groupBy { it.id }
            .map {
                MapAlignmentHighlight(
                    id = it.key,
                    type = LOCATION_TRACK,
                    ranges = it.value.fold(mutableMapOf<Int, Range<Double>>()) { acc, info ->
                        if (!acc.contains(info.alignmentId.index - 1)) acc.put(
                            info.alignmentId.index,
                            Range(info.points.first().m + info.segmentStart, info.points.last().m + info.segmentStart)
                        )
                        else {
                            val prev = acc.remove(info.alignmentId.index - 1)
                            prev?.let {
                                acc.put(
                                    info.alignmentId.index,
                                    Range(prev.min, info.points.last().m + info.segmentStart)
                                )
                            }
                        }
                        acc
                    }.values.toList()
            ) }
    }

    fun getReferenceLineHeaders(
        publishType: PublishType,
        referenceLineIds: List<IntId<ReferenceLine>>,
    ): List<AlignmentHeader<ReferenceLine>> {
        val refererenceLines = referenceLineService.getMany(publishType, referenceLineIds)
        val trackNumbers = trackNumberService
            .getMany(publishType, refererenceLines.map(ReferenceLine::trackNumberId))
            .associateBy(TrackLayoutTrackNumber::id)
        return refererenceLines.map { line ->
            val trackNumber = requireNotNull(trackNumbers[line.trackNumberId]) {
                "ReferenceLine in DB must have an existing TrackNumber"
            }
            toAlignmentHeader(trackNumber, line, line.alignmentVersion?.let(alignmentDao::fetch))
        }
    }

    fun getLocationTrackHeaders(
        publishType: PublishType,
        locationTrackIds: List<IntId<LocationTrack>>,
    ): List<AlignmentHeader<LocationTrack>> {
        return locationTrackService.getMany(publishType, locationTrackIds).map { track ->
            toAlignmentHeader(track, track.alignmentVersion?.let(alignmentDao::fetch))
        }
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

    fun getLocationTrackEnds(publishType: PublishType, id: IntId<LocationTrack>): List<LayoutPoint> {
        logger.serviceCall("getLocationTrackEnds", "publishType" to publishType, "id" to id)
        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(publishType, id)
        return getEndPoints(alignment)
    }

    fun getReferenceLineEnds(publishType: PublishType, id: IntId<ReferenceLine>): List<LayoutPoint> {
        logger.serviceCall("getReferenceLineEnds", "publishType" to publishType, "id" to id)
        val (_, alignment) = referenceLineService.getWithAlignmentOrThrow(publishType, id)
        return getEndPoints(alignment)
    }

    private fun getLocationTrackPolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ) = locationTrackService.list(publishType).filter(LocationTrack::exists).mapNotNull { locationTrack ->
        toAlignmentPolyLine(locationTrack.id, LOCATION_TRACK, locationTrack.alignmentVersion, bbox, resolution)
    }

    private fun getReferenceLinePolyLines(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ): List<AlignmentPolyLine<*>> {
        val trackNumbers = trackNumberService.mapById(publishType)
        return referenceLineService.list(publishType).mapNotNull { line ->
            val trackNumber = trackNumbers[line.trackNumberId]
            if (trackNumber != null && trackNumber.state != LayoutState.DELETED)
                toAlignmentPolyLine(line.id, REFERENCE_LINE, line.alignmentVersion, bbox, resolution)
            else null
        }
    }

    private fun toAlignmentPolyLine(
        id: DomainId<*>,
        type: MapAlignmentType,
        alignmentVersion: RowVersion<LayoutAlignment>?,
        bbox: BoundingBox,
        resolution: Int,
    ) = alignmentVersion?.let(alignmentDao::fetch)?.let { alignment ->
        toAlignmentPolyLine(id, type, alignment, resolution, bbox)
    }

    private fun getLocationTrackMissingLinkings(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> = locationTrackService.list(publishType)
        .filter { t -> t.exists && bbox.intersects(t.boundingBox) }
        .mapNotNull { track -> getMissingLinkings(track.id, LOCATION_TRACK, track.alignmentVersion) }

    private fun getReferenceLineMissingLinkings(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<ReferenceLine>> {
        val trackNumbers = trackNumberService.mapById(publishType)
        return referenceLineService.list(publishType)
            .filter { rl -> trackNumbers[rl.trackNumberId]?.exists ?: false && bbox.intersects(rl.boundingBox) }
            .mapNotNull { rl -> getMissingLinkings(rl.id, REFERENCE_LINE, rl.alignmentVersion) }
    }

    private fun <T> getMissingLinkings(
        id: DomainId<T>,
        type: MapAlignmentType,
        alignmentVersion: RowVersion<LayoutAlignment>?,
    ): MapAlignmentHighlight<T>? {
        val ranges = alignmentVersion?.let(alignmentDao::fetch)?.let(::getMissingLinkingRanges) ?: listOf()
        return if (ranges.isEmpty()) null else MapAlignmentHighlight(id as IntId, type, ranges)
    }

    private fun getMissingLinkingRanges(alignment: LayoutAlignment): List<Range<Double>> =
        combineContinuous(alignment.segments.filter { s -> s.sourceId == null }.map { s -> Range(s.startM, s.endM) })

    private fun getEndPoints(alignment: LayoutAlignment) =
        (alignment.segments.firstOrNull()?.points?.take(2) ?: listOf()) +
                (alignment.segments.lastOrNull()?.points?.takeLast(2) ?: listOf())
}
