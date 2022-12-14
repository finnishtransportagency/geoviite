import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.PublicationVersion
import fi.fta.geoviite.infra.tracklayout.*
import java.time.Instant

class LazyMap<K, V>(private val compute: (K) -> V) {
    private val map = mutableMapOf<K, V>()
    operator fun get(key: K): V = map.getOrPut(key) { compute(key) }
}

class ChangeContext(
    private val geocodingService: GeocodingService,
    val trackNumbers: TypedChangeContext<TrackLayoutTrackNumber>,
    val referenceLines: TypedChangeContext<ReferenceLine>,
    val kmPosts: TypedChangeContext<TrackLayoutKmPost>,
    val locationTracks: TypedChangeContext<LocationTrack>,
    val switches: TypedChangeContext<TrackLayoutSwitch>,

    val geocodingKeysBefore: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    val geocodingKeysAfter: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,

    val getTrackNumberTracksBefore: (trackNumberId: IntId<TrackLayoutTrackNumber>) -> List<RowVersion<LocationTrack>>,
) {

    fun getGeocodingContextBefore(id: IntId<TrackLayoutTrackNumber>) =
        geocodingKeysBefore[id]?.let(geocodingService::getGeocodingContext)

    fun getGeocodingContextAfter(id: IntId<TrackLayoutTrackNumber>) =
        geocodingKeysAfter[id]?.let(geocodingService::getGeocodingContext)
}

fun <T: Draftable<T>> createTypedContext(dao: DraftableDaoBase<T>, versions: List<PublicationVersion<T>>) =
    createTypedContext(
        dao,
        { id -> dao.fetchVersion(id, OFFICIAL) },
        { id -> versions.find { v -> v.officialId == id }?.draftVersion ?: dao.fetchVersionOrThrow(id, OFFICIAL) },
    )

fun <T: Draftable<T>> createTypedContext(dao: DraftableDaoBase<T>, before: Instant, after: Instant) =
    createTypedContext(
        dao,
        { id -> dao.fetchOfficialVersionAtMoment(id, before) },
        { id -> dao.fetchOfficialVersionAtMomentOrThrow(id, after) },
    )

fun <T: Draftable<T>> createTypedContext(
    dao: DraftableDaoBase<T>,
    getBeforeVersion: (id: IntId<T>) -> RowVersion<T>?,
    getAfterVersion: (id: IntId<T>) -> RowVersion<T>,
) = TypedChangeContext(dao, LazyMap(getBeforeVersion), LazyMap(getAfterVersion))

class TypedChangeContext<T : Draftable<T>>(
    private val dao: DraftableDaoBase<T>,
    private val beforeVersions: LazyMap<IntId<T>, RowVersion<T>?>,
    private val afterVersions: LazyMap<IntId<T>, RowVersion<T>>,
) {
    fun beforeVersion(id: IntId<T>) = beforeVersions[id]
    fun afterVersion(id: IntId<T>) = afterVersions[id]

    fun getBefore(id: IntId<T>) = beforeVersion(id)?.let(dao::fetch)
    fun getAfter(id: IntId<T>) = dao.fetch(afterVersion(id))
}
