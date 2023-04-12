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
            .groupBy { it.id }
            .map {
                MapAlignmentHighlight(
                    id = it.key,
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

    fun getSectionsByPlans(
        publishType: PublishType,
        bbox: BoundingBox,
    ): List<MapAlignmentPlanHighlight<LocationTrack>> {
        logger.serviceCall("getSectionsWithoutProfile", "publishType" to publishType, "bbox" to bbox)
        return alignmentDao.fetchPlanInfoForSegmentsInBoundingBox<LocationTrack>(publishType, bbox)
            .groupBy { it.id }
            .mapNotNull {
                val ranges = it.value.fold(mutableMapOf<Int, PlanRange?>()) { acc, info ->
                    val prevIndex = info.alignmentId.index - 1
                    if (!acc.contains(prevIndex) || acc.get(prevIndex)?.planId != info.planId) acc.put(
                        info.alignmentId.index,
                        PlanRange(info.points.first().m + info.segmentStart, info.points.last().m + info.segmentStart, info.planId)
                    )
                    else {
                        val prev = acc.remove(prevIndex)
                        prev?.let {
                            acc.put(
                                info.alignmentId.index,
                                PlanRange(prev.min, info.points.last().m + info.segmentStart, info.planId)
                            )
                        }
                    }
                    acc
                }.values.filterNotNull().toList()
                MapAlignmentPlanHighlight(
                    id = it.key,
                    ranges = ranges
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
