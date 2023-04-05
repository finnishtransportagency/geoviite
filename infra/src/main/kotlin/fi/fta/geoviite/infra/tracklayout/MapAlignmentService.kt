package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

enum class AlignmentFetchType {
    LOCATIONTRACK,
    REFERENCE,
    ALL,
}
@Service
class MapAlignmentService(
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val referenceLineService: ReferenceLineService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getMapAlignments(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
        type: AlignmentFetchType,
        selectedId: IntId<LocationTrack>?,
        includePlanExtraInfo: Boolean,
    ): List<MapAlignment<*>> {
        logger.serviceCall("getMapAlignments",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "selectedId" to selectedId,
            "includePlanExtraInfo" to includePlanExtraInfo,
        )
        val trackNumbers = trackNumberService.mapById(publishType)
        val referenceLines =
            if (type != AlignmentFetchType.LOCATIONTRACK) {
                getMapReferenceLines(trackNumbers, publishType, bbox, resolution).map { it.second }
            }
            else listOf()
        val locationTracks =
            if (type != AlignmentFetchType.REFERENCE) getMapLocationTracks(publishType, bbox, resolution).map { it.second }
            else listOf()
        val selected = selectedId?.let { id ->
            if (locationTracks.any { t -> t.id == selectedId }) null
            else locationTrackService.get(publishType, id)
                ?.takeIf { t -> t.state != LayoutState.DELETED }
                ?.let { t -> toMap(t, bbox, resolution) }
        }
        return (referenceLines + locationTracks + listOfNotNull(selected))
            .filter { ma -> ma.segments.isNotEmpty() }
    }

    data class HighlightRange(
        val start: Double,
        val end: Double,
    )
    data class MapAlignmentHighlight<T>(
        val id: IntId<T>,
        val ranges: List<HighlightRange>
    )

    fun getProfileInfo(
        publishType: PublishType,
        ids: List<IntId<LocationTrack>>
    ): List<MapAlignmentHighlight<LocationTrack>> {
        logger.serviceCall("getProfileInfo", "publishType" to publishType, "ids" to ids)
        val tracks = locationTrackService.getMany(publishType, ids)
        return tracks.mapNotNull { locationTrack ->
            locationTrack.alignmentVersion?.let {
                MapAlignmentHighlight(locationTrack.id as IntId, getSectionsWithoutProfile(
                    locationTrack.alignmentVersion
                ))
            }
        }
    }


    fun getMapReferenceLine(
        publishType: PublishType,
        id: IntId<ReferenceLine>,
    ): MapAlignment<ReferenceLine>? {
        logger.serviceCall(
            "getMapReferenceLine",
            "publishType" to publishType, "id" to id
        )
        val referenceLine = referenceLineService.get(publishType, id)
        return referenceLine?.let { toMap(publishType, referenceLine) }
    }

    fun getMapLocationTrack(
        publishType: PublishType,
        id: IntId<LocationTrack>,
    ): MapAlignment<LocationTrack>? {
        logger.serviceCall(
            "getMapLocationTrack",
            "publishType" to publishType, "id" to id
        )
        val locationTrack = locationTrackService.get(publishType, id)
        return locationTrack?.let { toMap(locationTrack) }
    }

    private fun getMapLocationTracks(
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ) = locationTrackService.list(publishType).map { track -> track.alignmentVersion to toMap(track, bbox, resolution) }

    private fun getSectionsWithoutProfile(
        alignmentVersion: RowVersion<LayoutAlignment>,
    ): MutableList<HighlightRange> {
        val alignment = alignmentDao.fetch(alignmentVersion)
        val segmentProfileInformation = alignmentDao.fetchSegmentPlanInfo(alignmentVersion)
        return alignment.segments.foldIndexed(mutableListOf()) { index, acc, segment ->
            val profileInfo = segmentProfileInformation.find { prof -> prof.id == segment.id }
            if (profileInfo == null || !profileInfo.hasProfile) {
                if (acc.isEmpty()) {
                    acc.add(HighlightRange(segment.startM, segment.endM))
                } else if (acc.last().end == alignment.segments.get(index - 1).endM) {
                    acc.set(acc.size - 1, HighlightRange(acc.last().start, segment.endM))
                } else {
                    acc.add(HighlightRange(segment.startM, segment.endM))
                }
            }
            acc
        }
    }

    private fun getMapReferenceLines(
        trackNumbers: Map<IntId<TrackLayoutTrackNumber>, TrackLayoutTrackNumber>,
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ) = referenceLineService.list(publishType).mapNotNull { line ->
        val trackNumber = trackNumbers[line.trackNumberId]
        if (trackNumber != null && trackNumber.state != LayoutState.DELETED)
            line.alignmentVersion to toMap(line, trackNumber, bbox, resolution)
        else null
    }

    private fun toMap(
        track: LocationTrack,
        bbox: BoundingBox,
        resolution: Int,
    ) = simplify(
        locationTrack = track,
        alignment = track.alignmentVersion?.let(alignmentDao::fetch),
        resolution = resolution,
        bbox = bbox,
    )

    private fun toMap(
        track: LocationTrack,
    ) = simplify(
        locationTrack = track,
        alignment = track.alignmentVersion?.let(alignmentDao::fetch),
    )

    private fun toMap(
        publishType: PublishType,
        line: ReferenceLine,
    ) = simplify(
        trackNumber = trackNumberService.getOrThrow(publishType, line.trackNumberId),
        referenceLine = line,
        alignment = line.alignmentVersion?.let(alignmentDao::fetch),
    )

    private fun toMap(
        line: ReferenceLine,
        trackNumber: TrackLayoutTrackNumber,
        bbox: BoundingBox,
        resolution: Int,
    ) = simplify(
        trackNumber = trackNumber,
        referenceLine = line,
        alignment = line.alignmentVersion?.let(alignmentDao::fetch),
        resolution = resolution,
        bbox = bbox,
    )
}
