package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.event.ContextRefreshedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.concurrent.Executors

@Component
class PublicationGeometryChangeRemarksUpdateService constructor(
    private val lockDao: LockDao,
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val executor =
        Executors.newSingleThreadExecutor { task -> Thread(task).also { it.name = "geometry change remarks updater" } }

    @EventListener(ContextRefreshedEvent::class)
    fun enqueueUpdateAllUnprocessedGeometryChangeRemarks() {
        executor.execute(::updateUnprocessedGeometryChangeRemarks)
    }

    fun processPublication(publicationId: IntId<Publication>) {
        publicationDao.fetchUnprocessedGeometryChangeRemarks(publicationId).forEach { unprocessed ->
            processOne(unprocessed)
        }
    }

    private fun updateUnprocessedGeometryChangeRemarks() {
        lockDao.runWithLock(DatabaseLock.PUBLICATION_GEOMETRY_CHANGE_CALCULATION, Duration.ofMinutes(1)) {
            var unprocessedRemarksWereLeft = true
            while (unprocessedRemarksWereLeft) {
                val unprocessed = publicationDao.fetchUnprocessedGeometryChangeRemarks(null)
                unprocessedRemarksWereLeft = unprocessed.isNotEmpty()
                if (unprocessed.isNotEmpty())
                    processBatch(unprocessed)
            }
        }
    }

    private fun processBatch(unprocessedBatch:  List<PublicationDao.UnprocessedGeometryChange>) {
        logger.info("processing ${unprocessedBatch.size} geometry change remarks")
        unprocessedBatch.forEach(::processOne)
        logger.info("finished processing ${unprocessedBatch.size} geometry change remarks")
    }

    private fun processOne(unprocessedChange: PublicationDao.UnprocessedGeometryChange) {
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(
            unprocessedChange.trackNumberId,
            unprocessedChange.publicationTime
        )
        publicationDao.upsertGeometryChangeSummaries(
            unprocessedChange.publicationId,
            unprocessedChange.locationTrackId,
            if (geocodingContext == null || unprocessedChange.oldAlignmentVersion == null) listOf() else summarizeAlignmentChanges(
                geocodingContext = geocodingContext,
                oldAlignment = alignmentDao.fetch(unprocessedChange.oldAlignmentVersion),
                newAlignment = alignmentDao.fetch(unprocessedChange.newAlignmentVersion)
            )
        )
    }
}
