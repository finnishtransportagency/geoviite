package fi.fta.geoviite.infra.tracklayout

import com.github.davidmoten.rtree2.RTree
import com.github.davidmoten.rtree2.geometry.Geometries
import com.github.davidmoten.rtree2.geometry.Rectangle
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.math.lineLength
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

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
        return caches.compute(context) { _, currentCache ->
            val changeTime = locationTrackService.getChangeTime()
            val cache = currentCache ?: newCache()
            if (cache.changeTime == null || cache.changeTime < changeTime) {
                val all = locationTrackService.list(context).associateBy { it.id as IntId }
                refresh(cache, all, changeTime)
            } else cache
        } ?: error("Cache should have been created")
    }

    private fun newCache(): ContextCache = ContextCache(locationTrackDao::fetch, alignmentDao::fetch)

    private fun refresh(
        cache: ContextCache,
        newTracks: Map<IntId<LocationTrack>, LocationTrack>,
        changeTime: Instant
    ): ContextCache {
        // TODO: GVT-3113 This could possibly be optimized by edges, since their geometries don't change

        val (staleTracks, currentTracks) =
            cache.items
                .asSequence()
                .partition { (id, track) -> newTracks[id]?.getVersionOrThrow() != track.trackVersion }
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
            createEntry(track, alignmentDao.fetch(track.getVersionOrThrow())).also { entry ->
                newNet = entry.segmentData.fold(newNet) { net, (segment, rect) -> net.add(segment, rect) }
            }

        val newItems = newTracks.map { (id, track) -> id to (currentTracks[id] ?: addEntry(track)) }

        return ContextCache(locationTrackDao::fetch, alignmentDao::fetch, newNet, newItems.toMap(), changeTime)
    }
}

data class SpatialCacheSegment(val locationTrackVersion: LayoutRowVersion<LocationTrack>, val segmentIndex: Int)

data class SpatialCacheEntry(
    val trackVersion: LayoutRowVersion<LocationTrack>,
    val segmentData: List<Pair<SpatialCacheSegment, Rectangle>>,
)

data class LocationTrackCacheHit(
    val track: LocationTrack,
    val geometry: DbLocationTrackGeometry,
    val closestPoint: AlignmentPoint<LocationTrackM>,
    val distance: Double,
) {
    fun getEdge(): Pair<DbLayoutEdge, Range<LineM<LocationTrackM>>> =
        geometry.getEdgeAtM(closestPoint.m)?.let { (e, r) -> e as DbLayoutEdge to r }
            ?: error("Closest point is outside of track geometry")
}

private val cacheHitComparator =
    compareBy<LocationTrackCacheHit>(
        { it.distance }, // Primary sort by distance
        { if (it.track.duplicateOf == null) 0 else 1 }, // Favor non-duplicates as tie-breaker
        { it.track.version?.id?.intValue }, // Stable ordering by ID
    )

data class ContextCache(
    private val getTrack: (LayoutRowVersion<LocationTrack>) -> LocationTrack,
    private val getGeometry: (LayoutRowVersion<LocationTrack>) -> DbLocationTrackGeometry,
    val network: RTree<SpatialCacheSegment, Rectangle> = RTree.star().create(),
    val items: Map<IntId<LocationTrack>, SpatialCacheEntry> = emptyMap(),
    val changeTime: Instant? = null,
) {
    val size: Int
        get() = items.size

    fun getClosest(location: IPoint, thresholdMeters: Double = 100.0): List<LocationTrackCacheHit> =
        network
            .search(Geometries.point(location.x, location.y), thresholdMeters)
            .let { entries ->
                var currentMin: Double = Double.MAX_VALUE
                var hit: LocationTrackCacheHit? = null
                entries.forEach { entry ->
                    val segment = entry.value()
                    val geometry = getGeometry(segment.locationTrackVersion)
                    val bbox = geometry.boundingBox!!
                    val insideBbox = bbox.contains(location)
                    val bboxDistance =
                        if (!insideBbox)
                            listOf(
                                    lineLength(location, Point(bbox.min.x, bbox.min.y)),
                                    lineLength(location, Point(bbox.min.x, bbox.max.y)),
                                    lineLength(location, Point(bbox.max.x, bbox.min.y)),
                                    lineLength(location, Point(bbox.max.x, bbox.max.y)),
                                )
                                .min()
                        else 0.0
                    if (insideBbox || bboxDistance < currentMin) {
                        val (layoutSegment, segmentM) = geometry.getSegmentWithM(segment.segmentIndex)
                        val closestPointM = layoutSegment.getClosestPointM(segmentM.min, location).first
                        val closestPoint = layoutSegment.seekPointAtM(segmentM.min, closestPointM).point
                        val distance = lineLength(location, closestPoint)
                        if (distance < currentMin && distance < thresholdMeters) {
                            currentMin = distance
                            hit =
                                LocationTrackCacheHit(
                                    getTrack(segment.locationTrackVersion), geometry, closestPoint, distance)
                        }
                    }
                }
                listOf(hit)
            }
            .filterNotNull()
            .sortedWith(cacheHitComparator)
            .distinctBy { it.track.id }

    fun getClosest2(location: IPoint, thresholdMeters: Double = 100.0): List<LocationTrackCacheHit> =
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
        val (layoutSegment, segmentM) = geometry.getSegmentWithM(segment.segmentIndex)
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
            val entry = SpatialCacheSegment(track.getVersionOrThrow(), segmentIndex)
            val rect = Geometries.rectangle(bbox.x.min, bbox.y.min, bbox.x.max, bbox.y.max)
            entry to rect
        }
    return SpatialCacheEntry(track.getVersionOrThrow(), segmentData)
}
