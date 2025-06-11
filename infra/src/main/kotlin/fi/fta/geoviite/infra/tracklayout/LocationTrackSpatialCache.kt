package fi.fta.geoviite.infra.tracklayout

import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Rectangle
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.lineLength
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class LocationTrackSpatialCache
@Autowired
constructor(
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
) {
    private val caches: ConcurrentHashMap<LayoutContext, ContextCache> = ConcurrentHashMap()

    fun get(context: LayoutContext): ContextCache {
        val all = locationTrackService.list(context).associateBy { it.id as IntId }
        return caches.compute(context) { _, cache -> refresh(cache ?: newCache(), all) }
            ?: error("Cache should have been created")
    }

    private fun newCache(): ContextCache =
        ContextCache(locationTrackDao::fetch, alignmentDao::fetch, alignmentDao::fetch)

    private fun refresh(cache: ContextCache, newTracks: Map<IntId<LocationTrack>, LocationTrack>): ContextCache {
        // TODO: GVT-3113 This could possibly be optimized by edges, since their geometries don't change

        val (staleTracks, currentTracks) =
            cache.items
                .asSequence()
                .partition { (id, track) -> newTracks[id]?.versionOrThrow != track.trackVersion }
                .let { (stale, current) ->
                    stale.associate { it.key to it.value } to current.associate { it.key to it.value }
                }

        // Remove all stale items (tracks that have been removed or updated)
        var newNet =
            staleTracks
                .flatMap { (_, entry) -> entry.segmentData }
                .fold(cache.network) { net, (segment, rect) -> net.delete(segment, rect) }

        // Add all new items (tracks that have been added or updated)
        fun addEntry(track: LocationTrack) =
            createEntry(track, alignmentDao.fetch(track.versionOrThrow)).also { entry ->
                newNet = entry.segmentData.fold(newNet) { net, (segment, rect) -> net.add(segment, rect) }
            }

        val newItems = newTracks.map { (id, track) -> id to (currentTracks[id] ?: addEntry(track)) }

        return ContextCache(locationTrackDao::fetch, alignmentDao::fetch, alignmentDao::fetch, newNet, newItems.toMap())
    }
}

data class SpatialCacheSegment(val locationTrackVersion: LayoutRowVersion<LocationTrack>, val segmentIndex: Int)

data class SpatialCacheEntry(
    val trackVersion: LayoutRowVersion<LocationTrack>,
    val segmentData: List<Pair<SpatialCacheSegment, Rectangle>>,
)

// TODO: GVT-3080
data class LocationTrackCacheHit(
    val track: LocationTrack,
    val geometry: LocationTrackGeometry,
    val closestPoint: AlignmentPoint,
    val distance: Double,
)

private val cacheHitComparator =
    compareBy<LocationTrackCacheHit>(
        { it.distance }, // Primary sort by distance
        { if (it.track.duplicateOf == null) 0 else 1 }, // Favor non-duplicates as tie-breaker
        { it.track.version?.id?.intValue }, // Stable ordering by ID
    )

data class ContextCache(
    private val getTrack: (LayoutRowVersion<LocationTrack>) -> LocationTrack,
    private val getGeometry: (LayoutRowVersion<LocationTrack>) -> DbLocationTrackGeometry,
    private val getAlignment: (RowVersion<LayoutAlignment>) -> LayoutAlignment,
    val network: RTree<SpatialCacheSegment, Rectangle> = RTree.star().create(),
    val items: Map<IntId<LocationTrack>, SpatialCacheEntry> = emptyMap(),
) {
    val size: Int
        get() = items.size

    fun getClosest(location: IPoint, thresholdMeters: Double = 100.0): List<LocationTrackCacheHit> =
        network
            .search(Geometries.point(location.x, location.y), thresholdMeters)
            .mapNotNull { entry -> createHit(entry.value(), location, thresholdMeters) }
            .sortedWith(cacheHitComparator)
            .distinctBy { it.track.id }

    fun getWithinBoundingBox(boundingBox: BoundingBox): List<Pair<LocationTrack, DbLocationTrackGeometry>> =
        network
            .search(Geometries.rectangle(boundingBox.x.min, boundingBox.y.min, boundingBox.x.max, boundingBox.y.max))
            .groupBy { hit -> hit.value().locationTrackVersion }
            .filter { (version, hits) ->
                val layoutSegments = getGeometry(version).segments
                hits.any { hit ->
                    layoutSegments[hit.value().segmentIndex].segmentPoints.any { point -> boundingBox.contains(point) }
                }
            }
            .keys
            .map { trackVersion -> getTrack(trackVersion) to getGeometry(trackVersion) }

    private fun createHit(
        segment: SpatialCacheSegment,
        location: IPoint,
        thresholdMeters: Double,
    ): LocationTrackCacheHit? {
        val geometry = getGeometry(segment.locationTrackVersion)
        val (layoutSegment, segmentM) = geometry.segmentsWithM[segment.segmentIndex]
        val closestPointM = layoutSegment.getClosestPointM(segmentM.min, location).first
        val closestPoint = layoutSegment.seekPointAtM(segmentM.min, closestPointM).point
        val distance = lineLength(location, closestPoint)
        return if (distance < thresholdMeters) {
            LocationTrackCacheHit(getTrack(segment.locationTrackVersion), geometry, closestPoint, distance)
        } else {
            null
        }
    }
}

private fun createEntry(track: LocationTrack, geometry: DbLocationTrackGeometry): SpatialCacheEntry {
    val segmentData =
        geometry.segments.mapIndexed { segmentIndex, segment ->
            val bbox = segment.boundingBox
            val entry = SpatialCacheSegment(track.versionOrThrow, segmentIndex)
            val rect = Geometries.rectangle(bbox.x.min, bbox.y.min, bbox.x.max, bbox.y.max)
            entry to rect
        }
    return SpatialCacheEntry(track.versionOrThrow, segmentData)
}
