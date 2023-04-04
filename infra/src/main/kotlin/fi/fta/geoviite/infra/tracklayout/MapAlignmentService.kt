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
                getMapReferenceLines(trackNumbers, publishType, bbox, resolution)
            }
            else listOf()
        val locationTracks =
            if (type != AlignmentFetchType.REFERENCE) getMapLocationTracks(publishType, bbox, resolution)
            else listOf()
        val selected = selectedId?.let { id ->
            if (locationTracks.any { t -> t.second.id == selectedId }) null
            else locationTrackService.get(publishType, id)
                ?.takeIf { t -> t.state != LayoutState.DELETED }
                ?.let { t -> t.alignmentVersion to toMap(t, bbox, resolution) }
        }
        return (referenceLines + locationTracks + listOfNotNull(selected))
            .filter { ma -> ma.second.segments.isNotEmpty() }
            .map { (alignmentVersion, mapAlignment) ->
                if (includePlanExtraInfo) {
                    requireNotNull(alignmentVersion) { "Alignment version required for fetching geometry profile" }
                    includePlanInfo(alignmentVersion, mapAlignment)
                } else mapAlignment
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

    private fun <T>includePlanInfo(
        alignmentVersion: RowVersion<LayoutAlignment>,
        mapAlignment: MapAlignment<T>
    ): MapAlignment<T> {
        val segmentProfileInformation = alignmentDao.fetchSegmentPlanInfo(alignmentVersion)
        return mapAlignment.copy(
            segments = mapAlignment.segments.map { segment ->
                val additionalSegmentData = segmentProfileInformation.find { it.id == segment.id }
                segment.copy(
                    hasProfile = additionalSegmentData?.hasProfile,
                    planId = additionalSegmentData?.planId,
                )
            }
        )
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
