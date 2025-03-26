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

    private fun newCache(): ContextCache = ContextCache(locationTrackDao::fetch, alignmentDao::fetch)

    private fun refresh(cache: ContextCache, newTracks: Map<IntId<LocationTrack>, LocationTrack>): ContextCache {
        val (staleTracks, currentTracks) =
            cache.items
                .asSequence()
                .partition { (id, track) -> newTracks[id]?.alignmentVersion != track.alignmentVersion }
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
            createEntry(track, alignmentDao.fetch(track.getAlignmentVersionOrThrow())).also { entry ->
                newNet = entry.segmentData.fold(newNet) { net, (segment, rect) -> net.add(segment, rect) }
            }

        val newItems = newTracks.map { (id, track) -> id to (currentTracks[id] ?: addEntry(track)) }

        return ContextCache(locationTrackDao::fetch, alignmentDao::fetch, newNet, newItems.toMap())
    }
}

data class SpatialCacheSegment(
    val locationTrackVersion: LayoutRowVersion<LocationTrack>,
    val alignmentVersion: RowVersion<LayoutAlignment>,
    val segmentIndex: Int,
)

data class SpatialCacheEntry(
    val alignmentVersion: RowVersion<LayoutAlignment>,
    val segmentData: List<Pair<SpatialCacheSegment, Rectangle>>,
)

data class LocationTrackCacheHit(
    val track: LocationTrack,
    val alignment: LayoutAlignment,
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

    fun getWithinBoundingBox(boundingBox: BoundingBox): List<Pair<LocationTrack, LayoutAlignment>> =
        network
            .search(Geometries.rectangle(boundingBox.x.min, boundingBox.y.min, boundingBox.x.max, boundingBox.y.max))
            .groupBy { hit -> hit.value().locationTrackVersion to hit.value().alignmentVersion }
            .filter { (versions, hits) ->
                val layoutSegments = getAlignment(versions.second).segments
                hits.any { hit ->
                    layoutSegments[hit.value().segmentIndex].segmentPoints.any { point -> boundingBox.contains(point) }
                }
            }
            .keys
            .map { (locationTrack, alignment) -> getTrack(locationTrack) to getAlignment(alignment) }

    private fun createHit(
        segment: SpatialCacheSegment,
        location: IPoint,
        thresholdMeters: Double,
    ): LocationTrackCacheHit? {
        val alignment = getAlignment(segment.alignmentVersion)
        val layoutSegment = alignment.segments[segment.segmentIndex]
        val closestPointM = layoutSegment.getClosestPointM(location).first
        val closestPoint = layoutSegment.seekPointAtM(closestPointM).point
        val distance = lineLength(location, closestPoint)
        return if (distance < thresholdMeters) {
            LocationTrackCacheHit(getTrack(segment.locationTrackVersion), alignment, closestPoint, distance)
        } else {
            null
        }
    }
}

private fun createEntry(track: LocationTrack, alignment: LayoutAlignment): SpatialCacheEntry {
    val alignmentVersion = track.getAlignmentVersionOrThrow()
    val segmentData =
        alignment.segments.mapIndexed { segmentIndex, segment ->
            val bbox = segment.boundingBox!!
            val entry = SpatialCacheSegment(track.version!!, alignmentVersion, segmentIndex)
            val rect = Geometries.rectangle(bbox.x.min, bbox.y.min, bbox.x.max, bbox.y.max)
            entry to rect
        }
    return SpatialCacheEntry(alignmentVersion, segmentData)
}
