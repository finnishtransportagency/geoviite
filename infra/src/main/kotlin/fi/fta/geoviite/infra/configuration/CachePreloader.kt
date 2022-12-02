package fi.fta.geoviite.infra.configuration

import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

const val CACHE_WARMUP_DELAY = 1000L
const val CACHE_RELOAD_INTERVAL = 45 * 60 * 1000L

@ConditionalOnWebApplication
@Component
class CachePreloader(
    @Value("\${geoviite.cache.enabled}") private val cacheEnabled: Boolean,
    @Value("\${geoviite.cache.preload}") private val cachePreloadEnabled: Boolean,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val layoutKmPostDao: LayoutKmPostDao,
    private val switchDao: LayoutSwitchDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchStructureDao: SwitchStructureDao,
    private val geometryDao: GeometryDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Scheduled(fixedDelay = CACHE_RELOAD_INTERVAL, initialDelay = CACHE_WARMUP_DELAY)
    fun scheduleReload() {
        if (cacheEnabled && cachePreloadEnabled) {
            switchStructureDao.fetchSwitchStructures()
            refreshCache("TrackNumber", layoutTrackNumberDao::fetchAllVersions, layoutTrackNumberDao::fetch)
            refreshCache("ReferenceLine", referenceLineDao::fetchAllVersions, referenceLineDao::fetch)
            refreshCache("LocationTrack", locationTrackDao::fetchAllVersions, locationTrackDao::fetch)
            refreshCache("Alignment", alignmentDao::fetchVersions, alignmentDao::fetch)
            refreshCache("Switch", switchDao::fetchAllVersions, switchDao::fetch)
            refreshCache("KM-Post", layoutKmPostDao::fetchAllVersions, layoutKmPostDao::fetch)
            refreshCache("PlanHeader", geometryDao::fetchPlanVersions, geometryDao::fetchPlanHeader)
        }
    }

    private fun <T,S> refreshCache(
        name: String,
        fetchVersions: () -> List<RowVersion<T>>,
        fetchRow: (RowVersion<T>) -> S,
    ) {
        logger.info("Refreshing cache: name=$name")
        val start = Instant.now()
        fetchVersions().forEach { version -> fetchRow(version) }
        logger.info("Cache refreshed: name=$name duration=${Duration.between(start, Instant.now()).toMillis()}ms")
    }
}
