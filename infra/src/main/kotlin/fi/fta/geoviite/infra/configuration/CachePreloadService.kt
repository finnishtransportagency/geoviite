package fi.fta.geoviite.infra.configuration

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.tracklayout.*
import java.time.Duration
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@GeoviiteService
class CachePreloadService(
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val layoutKmPostDao: LayoutKmPostDao,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val geometryDao: GeometryDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun loadLayoutCache() {
        listOf(layoutTrackNumberDao, referenceLineDao, locationTrackDao, switchDao, layoutKmPostDao)
            .parallelStream()
            .forEach { dao -> refreshCache(dao) }
    }

    fun loadPlanHeaderCache() {
        refreshCache("PlanHeader", geometryDao::preloadHeaderCache)
    }

    fun loadAlignmentCache() {
        refreshCache("SegmentGeometries", alignmentDao::preloadSegmentGeometries)
        refreshCache("Alignment", alignmentDao::preloadAlignmentCache)
    }

    private fun <T : LayoutAsset<T>> refreshCache(dao: LayoutAssetDao<T>) =
        refreshCache(dao.table.name, dao::preloadCache)

    private fun refreshCache(name: String, refresh: () -> Unit) {
        logger.info("Refreshing cache: name=$name")
        val start = Instant.now()
        refresh()
        logger.info("Cache refreshed: name=$name duration=${Duration.between(start, Instant.now()).toMillis()}ms")
    }
}
