package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.util.rangesOfConsecutiveIndicesOf
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Duration
import kotlin.math.hypot

private const val GEOMETRY_CHANGE_BATCH_SIZE = 10
private const val MINIMUM_M_DISTANCE_SEPARATING_ALIGNMENT_CHANGE_SUMMARIES = 10.0

@Component
class PublicationGeometryChangeRemarksUpdateService(
    private val lockDao: LockDao,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun processPublication(publicationId: IntId<Publication>) {
        publicationDao.fetchUnprocessedGeometryChangeRemarks(publicationId).forEach(::processOne)
    }

    fun updateUnprocessedGeometryChangeRemarks() {
        lockDao.runWithLock(DatabaseLock.PUBLICATION_GEOMETRY_CHANGE_CALCULATION, Duration.ofMinutes(60)) {
            var unprocessedRemarksWereLeft = true
            while (unprocessedRemarksWereLeft) {
                val unprocessed = publicationDao.fetchUnprocessedGeometryChangeRemarks(GEOMETRY_CHANGE_BATCH_SIZE)
                logger.info("Processing pending publication change remarks batch: count=${unprocessed.size}")
                unprocessedRemarksWereLeft = unprocessed.isNotEmpty()
                if (unprocessed.isNotEmpty()) processBatch(unprocessed)
            }
        }
    }

    private fun processBatch(unprocessedBatch: List<PublicationDao.UnprocessedGeometryChange>) {
        val range = " ${unprocessedBatch.first().publicationId}-${unprocessedBatch.last().publicationId}"
        logger.info("processing geometry change remarks for publicationIds $range")
        unprocessedBatch.forEach(::processOne)
        logger.info("finished processing geometry change remarks for publicationIds $range")
    }

    private fun processOne(unprocessedChange: PublicationDao.UnprocessedGeometryChange) {
        logger.info(
            "Processing publication change remarks for location track ${unprocessedChange.locationTrackId} in publication ${unprocessedChange.publicationId}"
        )
        val geocodingContext =
            geocodingService.getGeocodingContextAtMoment(
                unprocessedChange.branch,
                unprocessedChange.trackNumberId,
                unprocessedChange.publicationTime,
            )
        publicationDao.upsertGeometryChangeSummaries(
            unprocessedChange.publicationId,
            unprocessedChange.locationTrackId,
            if (geocodingContext == null || unprocessedChange.oldTrackVersion == null) {
                listOf()
            } else {
                summarizeAlignmentChanges(
                    geocodingContext = geocodingContext,
                    oldGeometry = alignmentDao.fetch(unprocessedChange.oldTrackVersion),
                    newGeometry = alignmentDao.fetch(unprocessedChange.newTrackVersion),
                )
            },
        )
    }
}

private data class ComparisonPoints(
    val mOnReferenceLine: Double,
    val oldPointIndex: Int,
    val oldPoint: IPoint,
    val newPoint: IPoint,
) {
    val roughDistance = hypot(oldPoint.x - newPoint.x, oldPoint.y - newPoint.y)

    fun distance() = calculateDistance(LAYOUT_SRID, oldPoint, newPoint)
}

private fun getChangedAlignmentRanges(
    old: LocationTrackGeometry,
    new: LocationTrackGeometry,
): List<List<LayoutSegment>> {
    val newIndexByGeometryId =
        new.segments.mapIndexed { i, s -> i to s }.associate { (index, segment) -> segment.geometry.id to index }
    val changedOldSegmentIndexRanges =
        rangesOfConsecutiveIndicesOf(
            false,
            old.segments.map { segment -> newIndexByGeometryId.containsKey(segment.geometry.id) },
        )
    return changedOldSegmentIndexRanges.map { oldSegmentIndexRange ->
        old.segments.subList(oldSegmentIndexRange.start, oldSegmentIndexRange.endInclusive + 1)
    }
}

fun summarizeAlignmentChanges(
    geocodingContext: GeocodingContext,
    oldGeometry: LocationTrackGeometry,
    newGeometry: LocationTrackGeometry,
    changeThreshold: Double = 1.0,
): List<GeometryChangeSummary> {
    val changedRanges = getChangedAlignmentRanges(oldGeometry, newGeometry)
    return changedRanges
        .mapNotNull { oldSegments ->
            val oldPoints = oldSegments.flatMap { segment -> segment.segmentPoints }
            val changedPoints =
                oldPoints
                    .mapIndexed { index, oldPoint -> index to oldPoint }
                    .parallelStream()
                    .map { (index, oldPoint) ->
                        geocodingContext.getAddressAndM(oldPoint)?.let { (address, mOnReferenceLine) ->
                            geocodingContext
                                .getTrackLocation(newGeometry, address)
                                ?.let { newAddressPoint ->
                                    ComparisonPoints(mOnReferenceLine, index, oldPoint, newAddressPoint.point)
                                }
                                ?.let { comparison ->
                                    if (comparison.roughDistance < changeThreshold) null else comparison
                                }
                        }
                    }
                    .toList()
                    .filterNotNull()
            val changedPointsRangesFirstIndices =
                changedPoints
                    .zipWithNext { a, b ->
                        b.mOnReferenceLine - a.mOnReferenceLine >
                            MINIMUM_M_DISTANCE_SEPARATING_ALIGNMENT_CHANGE_SUMMARIES
                    }
                    .mapIndexedNotNull { index, jump -> (index + 1).takeIf { jump } }
            val changedPointsRanges =
                (listOf(0) + changedPointsRangesFirstIndices + changedPoints.size).zipWithNext { a, b -> a to b }

            if (changedPoints.isEmpty()) null
            else
                changedPointsRanges.mapNotNull { (from, to) ->
                    val start = changedPoints[from]
                    val end = changedPoints[to - 1]
                    val startAddress = geocodingContext.getAddress(oldPoints[start.oldPointIndex])?.first
                    val endAddress = geocodingContext.getAddress(oldPoints[end.oldPointIndex])?.first

                    if (startAddress == null || endAddress == null) null
                    else
                        GeometryChangeSummary(
                            end.mOnReferenceLine - start.mOnReferenceLine,
                            changedPoints.subList(from, to).maxByOrNull { it.roughDistance }?.distance() ?: 0.0,
                            startAddress,
                            endAddress,
                        )
                }
        }
        .flatten()
}
