import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import java.time.Instant
import kotlin.reflect.KClass

class LazyMap<K, V>(private val compute: (K) -> V) {
    private val map = mutableMapOf<K, V>()

    operator fun get(key: K): V = map.getOrPut(key) { compute(key) }
}

class ChangeContext(
    private val geocodingService: GeocodingService,
    val trackNumbers: TypedChangeContext<LayoutTrackNumber>,
    val referenceLines: TypedChangeContext<ReferenceLine>,
    val kmPosts: TypedChangeContext<LayoutKmPost>,
    val locationTracks: TypedChangeContext<LocationTrack>,
    val switches: TypedChangeContext<LayoutSwitch>,
    val geocodingKeysBefore: LazyMap<IntId<LayoutTrackNumber>, GeocodingContextCacheKey?>,
    val geocodingKeysAfter: LazyMap<IntId<LayoutTrackNumber>, GeocodingContextCacheKey?>,
    val getTrackNumberTracksBefore: (trackNumberId: IntId<LayoutTrackNumber>) -> List<LayoutRowVersion<LocationTrack>>,
) {

    fun getGeocodingContextBefore(id: IntId<LayoutTrackNumber>) =
        geocodingKeysBefore[id]?.let(geocodingService::getGeocodingContext)

    fun getGeocodingContextAfter(id: IntId<LayoutTrackNumber>) =
        geocodingKeysAfter[id]?.let(geocodingService::getGeocodingContext)
}

inline fun <reified T : LayoutAsset<T>> createTypedContext(
    baseContext: LayoutContext,
    dao: LayoutAssetDao<T>,
    versions: List<LayoutRowVersion<T>>,
): TypedChangeContext<T> =
    createTypedContext(
        dao,
        { id -> dao.fetchVersion(baseContext, id) },
        { id -> versions.find { v -> v.id == id } ?: dao.fetchVersion(baseContext, id) },
    )

inline fun <reified T : LayoutAsset<T>> createTypedContext(
    branch: LayoutBranch,
    dao: LayoutAssetDao<T>,
    before: Instant,
    after: Instant,
): TypedChangeContext<T> =
    createTypedContext(
        dao,
        { id -> dao.fetchOfficialVersionAtMoment(branch, id, before) },
        { id -> dao.fetchOfficialVersionAtMoment(branch, id, after) },
    )

inline fun <reified T : LayoutAsset<T>> createTypedContext(
    dao: LayoutAssetDao<T>,
    noinline getBeforeVersion: (id: IntId<T>) -> LayoutRowVersion<T>?,
    noinline getAfterVersion: (id: IntId<T>) -> LayoutRowVersion<T>?,
) = TypedChangeContext(T::class, dao, LazyMap(getBeforeVersion), LazyMap(getAfterVersion))

class TypedChangeContext<T : LayoutAsset<T>>(
    private val klass: KClass<T>,
    private val dao: LayoutAssetDao<T>,
    private val beforeVersions: LazyMap<IntId<T>, LayoutRowVersion<T>?>,
    private val afterVersions: LazyMap<IntId<T>, LayoutRowVersion<T>?>,
) {
    fun beforeVersion(id: IntId<T>) = beforeVersions[id]

    fun afterVersion(id: IntId<T>) = afterVersions[id] ?: throw NoSuchEntityException(klass, id)

    private fun afterVersionIfExists(id: IntId<T>) = afterVersions[id]

    fun getBefore(id: IntId<T>) = beforeVersion(id)?.let(dao::fetch)

    fun getAfter(id: IntId<T>) = dao.fetch(afterVersion(id))

    fun getAfterIfExists(id: IntId<T>) = afterVersionIfExists(id)?.let(dao::fetch)
}
