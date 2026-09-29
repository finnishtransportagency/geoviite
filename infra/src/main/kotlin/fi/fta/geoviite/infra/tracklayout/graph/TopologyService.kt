package fi.fta.geoviite.infra.tracklayout.graph

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.configuration.ManualCacheStatsProvider
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.MICRO
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel.NANO

private const val TOPOLOGY_CACHE_SIZE = 10L

@GeoviiteService
class TopologyService : ManualCacheStatsProvider {

    private val topologyCache: Cache<Publication, CachedTopology> =
        Caffeine.newBuilder().maximumSize(TOPOLOGY_CACHE_SIZE).recordStats().build()

    override fun cacheStats() = mapOf("layout-topology" to topologyCache.stats())

    fun getTopology(version: Publication, detailLevel: TopologyDetailLevel): Topology =
        topologyCache.get(version, ::createTopology).let { cachedTopology ->
            when (detailLevel) {
                NANO -> cachedTopology.nano
                MICRO -> cachedTopology.micro
            }
        }

    private fun createTopology(version: Publication): CachedTopology = CachedTopology(Topology())
}

/**
 * Holds the topology of a single track layout version. The nano level topology is the full-detail base, from which the
 * coarser micro level topology is derived only if it is actually requested.
 */
private data class CachedTopology(
    val nano: Topology
) {
    val micro: Topology by lazy { TODO("GVT-3703: simplify the nano level topology into the micro level topology") }
}
