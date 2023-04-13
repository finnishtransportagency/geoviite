package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Range
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

enum class AlignmentFetchType {
    LOCATIONTRACK,
    REFERENCE,
    ALL,
}

data class MapAlignmentHighlight<T>(
    val id: IntId<T>,
    val ranges: List<Range<Double>>,
)

data class PlanRange(
    val min: Double,
    val max: Double,
    val planId: IntId<GeometryPlan>,
)
data class MapAlignmentPlanHighlight<T>(
    val id: IntId<T>,
    val ranges: List<PlanRange>,
)

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
        val referenceLines =
            if (type != AlignmentFetchType.LOCATIONTRACK) {
                getMapReferenceLines(trackNumbers, publishType, bbox, resolution)
            }
            else listOf()
        val locationTracks =
            if (type != AlignmentFetchType.REFERENCE) getMapLocationTracks(publishType, bbox, resolution)
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

    fun getSectionsWithoutProfile(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentHighlight<LocationTrack>> {
        logger.serviceCall("getSectionsWithoutProfile", "publishType" to publishType, "bbox" to bbox)
        return alignmentDao.fetchProfileInfoForSegmentsInBoundingBox<LocationTrack>(publishType, bbox)
            .filter { !it.hasProfile }
            .groupBy { it.rowVersion }
            .map {
                val alignment = locationTrackService.getWithAlignment(it.key)
                val ranges = mutableListOf<Range<Double>?>()
                alignment.second.segments.forEach { segment ->
                    val info = it.value.find { info -> info.alignmentId == segment.id }
                    if (info == null) ranges.add(null)
                    else if (ranges.isEmpty() || ranges.last() == null) ranges.add(Range(segment.startM, segment.endM))
                    else if (ranges.last()!!.max == segment.startM) ranges.set(ranges.lastIndex, Range(ranges.last()!!.min, segment.endM))
                }
                MapAlignmentHighlight(
                    id = it.key.id,
                    ranges = ranges.filterNotNull()
                )
            }
    }

    fun getSectionsByPlans(
        publishType: PublishType,
        ids: List<IntId<LocationTrack>>
    ): List<MapAlignmentPlanHighlight<LocationTrack>> {
        logger.serviceCall("getSectionsWithoutProfile", "publishType" to publishType, "ids" to ids)
        return alignmentDao.fetchPlanInfoForSegmentsInBoundingBox<LocationTrack>(publishType, ids)
            .groupBy { it.rowVersion }
            .mapNotNull {
                val alignment = locationTrackService.getWithAlignment(it.key)
                val ranges = mutableListOf<PlanRange?>()
                alignment.second.segments.forEach {segment ->
                    val info = it.value.find { info -> info.alignmentId == segment.id }
                    if (info == null) ranges.add(null)
                    else if (ranges.isEmpty() || ranges.last() == null || ranges.last()?.planId != info.planId)
                        ranges.add(PlanRange(segment.startM, segment.endM, info.planId))
                    else
                        ranges.set(ranges.lastIndex, PlanRange(ranges.last()!!.min, segment.endM, info.planId))
                }
                MapAlignmentPlanHighlight(
                    id = it.key.id,
                    ranges = ranges.filterNotNull()
                ) }
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
