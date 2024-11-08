package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

private const val GEOMETRY_CHANGE_BATCH_SIZE = 10

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
            if (geocodingContext == null || unprocessedChange.oldAlignmentVersion == null) {
                listOf()
            } else {
                summarizeAlignmentChanges(
                    geocodingContext = geocodingContext,
                    oldAlignment = alignmentDao.fetch(unprocessedChange.oldAlignmentVersion),
                    newAlignment = alignmentDao.fetch(unprocessedChange.newAlignmentVersion),
                )
            },
        )
    }
}
