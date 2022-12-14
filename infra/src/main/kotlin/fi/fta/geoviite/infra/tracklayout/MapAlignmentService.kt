package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

enum class AlignmentFetchType {
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
    ): List<MapAlignment<*>> {
        logger.serviceCall("getMapAlignments",
            "publishType" to publishType,
            "bbox" to bbox,
            "resolution" to resolution,
            "type" to type,
            "selectedId" to selectedId,
        )
        val trackNumbers = trackNumberService.mapById(publishType)
        val referenceLines = getMapReferenceLines(trackNumbers, publishType, bbox, resolution)
        val locationTracks =
            if (type != AlignmentFetchType.REFERENCE) getMapLocationTracks(publishType, bbox, resolution)
            else listOf()
        val selected = selectedId?.let { id ->
            if (locationTracks.any { t -> t.id == selectedId }) null
            else locationTrackService.get(publishType, id)
                ?.takeIf { t -> t.state != LayoutState.DELETED }
                ?.let { toMap(it, bbox, resolution) }
        }
        return (referenceLines + locationTracks + listOfNotNull(selected)).filter { ma -> ma.segments.isNotEmpty() }
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
    ) = locationTrackService.list(publishType).map { track -> toMap(track, bbox, resolution) }

    private fun getMapReferenceLines(
        trackNumbers: Map<IntId<TrackLayoutTrackNumber>, TrackLayoutTrackNumber>,
        publishType: PublishType,
        bbox: BoundingBox,
        resolution: Int,
    ) = referenceLineService.list(publishType).mapNotNull { line ->
        val trackNumber = trackNumbers[line.trackNumberId]
        if (trackNumber != null && trackNumber.state != LayoutState.DELETED)
            toMap(line, trackNumber, bbox, resolution)
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
