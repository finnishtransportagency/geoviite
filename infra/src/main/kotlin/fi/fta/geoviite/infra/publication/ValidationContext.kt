package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import java.util.concurrent.ConcurrentHashMap

class NullableCache<K, V> {
    val map: ConcurrentHashMap<K, NullableValue<V>> = ConcurrentHashMap()

    fun get(key: K, getter: (K) -> V?): V? = map.computeIfAbsent(key) { k -> NullableValue(getter(k)) }.value

    fun preload(keys: List<K>, getter: (List<K>) -> Map<K, V?>) {
        val missingKeys = keys.filterNot(map::containsKey)
        val preloaded = if (missingKeys.isNotEmpty()) getter(missingKeys) else mapOf()
        // Also cache the "not found" results
        val notFound = missingKeys.filterNot(preloaded::containsKey)
        putAll(preloaded + notFound.associateWith { null })
    }

    fun putAll(values: Map<K, V?>): Unit = map.putAll(values.mapValues { (_, v) -> NullableValue(v) })

    fun contains(key: K): Boolean = map.containsKey(key)

    // Holder for nullable value so computeIfAbsent doesn't re-resolve a missing result
    data class NullableValue<T>(val value: T?)
}

class NameCache<Field, T : LayoutAsset<T>>(val fetch: (List<Field>) -> Map<Field, List<IntId<T>>>) {
    val map: ConcurrentHashMap<Field, List<IntId<T>>> = ConcurrentHashMap()

    fun get(name: Field) = map.computeIfAbsent(name) { n -> fetch(listOf(n))[n] ?: emptyList() }

    fun preload(names: List<Field>) {
        val missing = names.filterNot(map::containsKey)
        if (missing.isNotEmpty()) map.putAll(fetch(missing))
    }
}

typealias RowVersionCache<T> = NullableCache<IntId<T>, RowVersion<T>>
typealias ReferenceCache<To, From> = NullableCache<IntId<To>, List<IntId<From>>>

/**
 * Validation context provides a layout world-view that combines the drafts of a publication set (selected drafts) to
 * the baseline of official layout. Each object only has one version that can exist in this context:
 *
 * In addition, it caches the validation data for quicker access.
 */
class ValidationContext(
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val kmPostDao: LayoutKmPostDao,
    val locationTrackDao: LocationTrackDao,
    val alignmentDao: LayoutAlignmentDao,
    val switchDao: LayoutSwitchDao,
    val switchLibraryService: SwitchLibraryService,
    val publicationDao: PublicationDao,
    val geocodingService: GeocodingService,
    val publicationSet: ValidationVersions,
) {
    private val trackNumberVersionCache = RowVersionCache<TrackLayoutTrackNumber>()
    private val referenceLineVersionCache = RowVersionCache<ReferenceLine>()
    private val kmPostVersionCache = RowVersionCache<TrackLayoutKmPost>()
    private val locationTrackVersionCache = RowVersionCache<LocationTrack>()
    private val switchVersionCache = RowVersionCache<TrackLayoutSwitch>()

    private val trackNumberKmPosts = ReferenceCache<TrackLayoutTrackNumber, TrackLayoutKmPost>()
    private val trackNumberLocationTracks = ReferenceCache<TrackLayoutTrackNumber, LocationTrack>()
    private val switchTrackLinks = ReferenceCache<TrackLayoutSwitch, LocationTrack>()
    private val trackDuplicateLinks = ReferenceCache<LocationTrack, LocationTrack>()

    private val geocodingContextKeys = NullableCache<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey>()
    private val switchNameCache = NameCache(::fetchSwitchesByName)
    private val trackNameCache = NameCache(::fetchLocationTracksByName)
    private val trackNumberNumberCache = NameCache(::fetchTrackNumbersByNumber)

    fun getTrackNumber(id: IntId<TrackLayoutTrackNumber>): TrackLayoutTrackNumber? =
        getObject(id, publicationSet.trackNumbers, trackNumberDao, trackNumberVersionCache)

    fun getTrackNumbersByNumber(number: TrackNumber): List<TrackLayoutTrackNumber> =
        trackNumberNumberCache.get(number).mapNotNull(::getTrackNumber)

    fun getReferenceLine(id: IntId<ReferenceLine>): ReferenceLine? =
        getObject(id, publicationSet.referenceLines, referenceLineDao, referenceLineVersionCache)

    fun getReferenceLineWithAlignment(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? =
        getObject(id, publicationSet.referenceLines, referenceLineDao, referenceLineVersionCache)?.let { rl ->
            rl to alignmentDao.fetch(requireNotNull(rl.alignmentVersion) {
                "Reference line in DB should have an alignment: id=$id"
            })
        }

    fun getKmPost(id: IntId<TrackLayoutKmPost>): TrackLayoutKmPost? =
        getObject(id, publicationSet.kmPosts, kmPostDao, kmPostVersionCache)

    fun getLocationTrack(id: IntId<LocationTrack>): LocationTrack? =
        getObject(id, publicationSet.locationTracks, locationTrackDao, locationTrackVersionCache)

    fun getLocationTracksByName(name: AlignmentName): List<LocationTrack> =
        trackNameCache.get(name).mapNotNull(::getLocationTrack)

    fun getDuplicateTrackIds(trackId: IntId<LocationTrack>): List<IntId<LocationTrack>>? =
        trackDuplicateLinks.get(trackId) { id -> fetchLocationTrackDuplicateLinks(listOf(id))[id] }

    fun getDuplicateTracks(id: IntId<LocationTrack>): List<LocationTrack> =
        (getDuplicateTrackIds(id) ?: emptyList()).mapNotNull(::getLocationTrack)

    fun getLocationTrackWithAlignment(id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? =
        getLocationTrack(id)?.let { track ->
            track to requireNotNull(track.alignmentVersion?.let(alignmentDao::fetch)) {
                "LocationTrack in DB needs to have an alignment: id=$id"
            }
        }

    fun getSwitch(id: IntId<TrackLayoutSwitch>): TrackLayoutSwitch? =
        getObject(id, publicationSet.switches, switchDao, switchVersionCache)

    fun getSwitchesByName(name: SwitchName): List<TrackLayoutSwitch> =
        switchNameCache.get(name).mapNotNull(::getSwitch)

    fun getReferenceLineByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): ReferenceLine? =
        getReferenceLineIdByTrackNumber(trackNumberId)?.let(::getReferenceLine)

    fun getReferenceLineIdByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): IntId<ReferenceLine>? =
        getTrackNumber(trackNumberId)?.referenceLineId

    fun getKmPostsByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): List<TrackLayoutKmPost> =
        trackNumberKmPosts
            .get(trackNumberId) { id -> fetchKmPostIdsByTrackNumbers(listOf(id))[id] }
            ?.mapNotNull(::getKmPost) ?: emptyList()

    fun getLocationTracksByTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>): List<LocationTrack> =
        trackNumberLocationTracks
            .get(trackNumberId) { id -> fetchLocationTrackIdsByTrackNumbers(listOf(id))[id] }
            ?.mapNotNull(::getLocationTrack) ?: emptyList()

    fun getSwitchTrackLinks(switchId: IntId<TrackLayoutSwitch>): List<IntId<LocationTrack>>? =
        switchTrackLinks.get(switchId) { id -> fetchSwitchTrackLinks(listOf(id))[id] }

    fun getSwitchTracksWithAlignments(id: IntId<TrackLayoutSwitch>): List<Pair<LocationTrack, LayoutAlignment>> =
        getSwitchTrackLinks(id)?.mapNotNull(::getLocationTrackWithAlignment) ?: emptyList()

    fun getPotentiallyAffectedSwitchIds(trackId: IntId<LocationTrack>): List<IntId<TrackLayoutSwitch>> {
        val track = getLocationTrack(trackId)
        val draftLinks = track?.switchIds ?: emptyList()
        val officialLinks = if (track == null || track.isDraft) {
            locationTrackVersionCache
                .get(trackId, locationTrackDao::fetchOfficialVersion)
                ?.let(locationTrackDao::fetch)?.switchIds ?: emptyList()
        } else emptyList()
        return (officialLinks + draftLinks).distinct()
    }

    fun getPotentiallyAffectedSwitches(trackId: IntId<LocationTrack>): List<TrackLayoutSwitch> =
        getPotentiallyAffectedSwitchIds(trackId).mapNotNull(::getSwitch)

    fun getSegmentSwitches(alignment: LayoutAlignment): List<SegmentSwitch> = alignment.segments
        .mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
        .groupBy({ (switchId, _) -> switchId }, { (_, segment) -> segment })
        .map { (switchId, segments) ->
            val switch = getSwitch(switchId)
            val name = switch?.name ?: getDraftSwitch(switchId)?.name
            val structure = switch?.switchStructureId?.let(switchLibraryService::getSwitchStructure)
            SegmentSwitch(switchId, name, switch, structure, segments)
        }

    fun getTopologicallyConnectedSwitches(track: LocationTrack): List<Pair<SwitchName, TrackLayoutSwitch?>> =
        listOfNotNull(track.topologyStartSwitch?.switchId, track.topologyEndSwitch?.switchId).map { switchId ->
            val switch = getSwitch(switchId)
            // If there's no draft either, we have a referential integrity error
            val name = switch?.name ?: requireNotNull(getDraftSwitch(switchId)).name
            name to switch
        }

    fun getGeocodingContext(trackNumberId: IntId<TrackLayoutTrackNumber>) =
        getGeocodingContextCacheKey(trackNumberId)?.let { key ->
            geocodingService.getGeocodingContext(key)
        }

    fun getGeocodingContextCacheKey(trackNumberId: IntId<TrackLayoutTrackNumber>) =
        geocodingContextKeys.get(trackNumberId) { tnId ->
            geocodingService.getGeocodingContextCacheKey(tnId, publicationSet)
        }

    fun preloadByPublicationSet() {
        preloadAssociatedTrackNumberAndReferenceLineVersions(publicationSet)

        val trackNumberIds = publicationSet.getTrackNumberIds()
        preloadTrackNumbersByNumber(trackNumberIds)

        val locationTrackIds = publicationSet.getLocationTrackIds()
        preloadLocationTrackVersions(locationTrackIds)
        preloadLocationTracksByTrackNumbers(trackNumberIds)
        preloadTrackDuplicates(locationTrackIds)
        preloadLocationTracksByName(locationTrackIds)

        preloadKmPostVersions(publicationSet.getKmPostIds())
        preloadKmPostsByTrackNumbers(trackNumberIds)

        val linkedSwitchIds = locationTrackIds.flatMap(::getPotentiallyAffectedSwitchIds)
        val allSwitchIds = (publicationSet.getSwitchIds() + linkedSwitchIds).distinct()
        preloadSwitchVersions(allSwitchIds)
        preloadSwitchesByName(publicationSet.switches.map { v -> v.officialId })
        preloadSwitchTrackLinks(allSwitchIds)
    }

    fun preloadTrackNumberVersions(ids: List<IntId<TrackLayoutTrackNumber>>) =
        preloadOfficialVersions(ids, trackNumberDao, trackNumberVersionCache)

    fun preloadReferenceLineVersions(ids: List<IntId<ReferenceLine>>) =
        preloadOfficialVersions(ids, referenceLineDao, referenceLineVersionCache)

    fun preloadKmPostVersions(ids: List<IntId<TrackLayoutKmPost>>) =
        preloadOfficialVersions(ids, kmPostDao, kmPostVersionCache)

    fun preloadLocationTrackVersions(ids: List<IntId<LocationTrack>>) =
        preloadOfficialVersions(ids, locationTrackDao, locationTrackVersionCache)

    fun preloadSwitchVersions(ids: List<IntId<TrackLayoutSwitch>>) =
        preloadOfficialVersions(ids, switchDao, switchVersionCache)

    fun preloadKmPostsByTrackNumbers(tnIds: List<IntId<TrackLayoutTrackNumber>>) =
        trackNumberKmPosts.preload(tnIds, ::fetchKmPostIdsByTrackNumbers)

    fun preloadLocationTracksByTrackNumbers(tnIds: List<IntId<TrackLayoutTrackNumber>>) =
        trackNumberLocationTracks.preload(tnIds, ::fetchLocationTrackIdsByTrackNumbers)

    fun preloadAssociatedTrackNumberAndReferenceLineVersions(versions: ValidationVersions) =
        preloadAssociatedTrackNumberAndReferenceLineVersions(
            trackNumberIds = versions.trackNumbers.map { v -> v.officialId },
            referenceLineIds = versions.referenceLines.map { v -> v.officialId },
            kmPostIds = versions.kmPosts.map { v -> v.officialId },
            trackIds = versions.locationTracks.map { v -> v.officialId },
        )

    fun preloadAssociatedTrackNumberAndReferenceLineVersions(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>> = emptyList(),
        referenceLineIds: List<IntId<ReferenceLine>> = emptyList(),
        kmPostIds: List<IntId<TrackLayoutKmPost>> = emptyList(),
        trackIds: List<IntId<LocationTrack>> = emptyList(),
    ): Unit = preloadTrackNumberAndReferenceLineVersions(
        collectAssociatedTrackNumberIds(trackNumberIds, referenceLineIds, kmPostIds, trackIds)
    )

    fun preloadTrackNumberAndReferenceLineVersions(ids: List<IntId<TrackLayoutTrackNumber>>) =
        preloadTrackNumberVersions(ids).also {
            val referenceLineIds = ids.mapNotNull(::getTrackNumber).mapNotNull(TrackLayoutTrackNumber::referenceLineId)
            preloadReferenceLineVersions(referenceLineIds)
        }

    fun preloadSwitchTrackLinks(switchIds: List<IntId<TrackLayoutSwitch>>): Unit =
        switchTrackLinks.preload(switchIds, ::fetchSwitchTrackLinks)

    fun preloadTrackDuplicates(trackIds: List<IntId<LocationTrack>>): Unit =
        trackDuplicateLinks.preload(trackIds, ::fetchLocationTrackDuplicateLinks)

    fun preloadSwitchesByName(switchIds: List<IntId<TrackLayoutSwitch>>) = switchNameCache.preload(
        switchIds.mapNotNull(::getSwitch).map(TrackLayoutSwitch::name).distinct(),
    )

    fun fetchSwitchesByName(names: List<SwitchName>): Map<SwitchName, List<IntId<TrackLayoutSwitch>>> {
        val officialVersions = switchDao.findOfficialNameDuplicates(names)
        cacheOfficialVersions(officialVersions.values.flatten(), switchVersionCache)
        return mapIdsByField(names, { s -> s.name }, publicationSet.switches, officialVersions, switchDao)
    }

    fun preloadLocationTracksByName(trackIds: List<IntId<LocationTrack>>) = trackNameCache.preload(
        trackIds.mapNotNull(::getLocationTrack).map(LocationTrack::name).distinct(),
    )

    fun fetchLocationTracksByName(names: List<AlignmentName>): Map<AlignmentName, List<IntId<LocationTrack>>> {
        val officialVersions = locationTrackDao.findOfficialNameDuplicates(names)
        cacheOfficialVersions(officialVersions.values.flatten(), locationTrackVersionCache)
        return mapIdsByField(names, { t -> t.name }, publicationSet.locationTracks, officialVersions, locationTrackDao)
    }

    fun preloadTrackNumbersByNumber(trackNumberIds: List<IntId<TrackLayoutTrackNumber>>) = trackNumberNumberCache.preload(
        trackNumberIds.mapNotNull(::getTrackNumber).map(TrackLayoutTrackNumber::number).distinct()
    )

    private fun fetchTrackNumbersByNumber(numbers: List<TrackNumber>): Map<TrackNumber, List<IntId<TrackLayoutTrackNumber>>> {
        val officialVersions = trackNumberDao.findOfficialNumberDuplicates(numbers)
        cacheOfficialVersions(officialVersions.values.flatten(), trackNumberVersionCache)
        return mapIdsByField(numbers, { t -> t.number }, publicationSet.trackNumbers, officialVersions, trackNumberDao)
    }

    // Draft searches that ignore context, for logging reference errors when the item doesn't exist in the context
    // Versions are fetched lazily once, if needed
    fun getDraftTrackNumber(id: IntId<TrackLayoutTrackNumber>) = allDraftTrackNumbers[id]?.let(trackNumberDao::fetch)
    private val allDraftTrackNumbers: Map<IntId<TrackLayoutTrackNumber>, RowVersion<TrackLayoutTrackNumber>> by lazy {
        trackNumberDao.fetchPublicationVersions().associate { v -> v.officialId to v.validatedAssetVersion }
    }

    fun getDraftSwitch(id: IntId<TrackLayoutSwitch>) = allDraftSwitches[id]?.let(switchDao::fetch)
    private val allDraftSwitches: Map<IntId<TrackLayoutSwitch>, RowVersion<TrackLayoutSwitch>> by lazy {
        switchDao.fetchPublicationVersions().associate { v -> v.officialId to v.validatedAssetVersion }
    }

    fun getDraftLocationTrack(id: IntId<LocationTrack>) = allDraftLocationTracks[id]?.let(locationTrackDao::fetch)
    private val allDraftLocationTracks: Map<IntId<LocationTrack>, RowVersion<LocationTrack>> by lazy {
        locationTrackDao.fetchPublicationVersions().associate { v -> v.officialId to v.validatedAssetVersion }
    }

    private fun fetchKmPostIdsByTrackNumbers(
        tnIds: List<IntId<TrackLayoutTrackNumber>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<IntId<TrackLayoutKmPost>>> = kmPostDao
        .fetchVersionsForPublication(tnIds, publicationSet.kmPosts.map { v -> v.officialId })
        .mapValues { (_, versions) ->
            val officialVersions = versions.filterNot { v -> publicationSet.containsKmPost(v.officialId) }
            cacheOfficialVersions(officialVersions.map { v -> v.validatedAssetVersion }, kmPostVersionCache)
            versions.map { v -> v.officialId }
        }

    private fun fetchLocationTrackIdsByTrackNumbers(
        tnIds: List<IntId<TrackLayoutTrackNumber>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<IntId<LocationTrack>>> = locationTrackDao
        .fetchVersionsForPublication(tnIds, publicationSet.locationTracks.map { v -> v.officialId })
        .mapValues { (_, versions) ->
            val officialVersions = versions.filterNot { v -> publicationSet.containsLocationTrack(v.officialId) }
            cacheOfficialVersions(officialVersions.map { v -> v.validatedAssetVersion }, locationTrackVersionCache)
            versions.map { v -> v.officialId }
        }

    private fun fetchSwitchTrackLinks(
        ids: List<IntId<TrackLayoutSwitch>>,
    ): Map<IntId<TrackLayoutSwitch>, List<IntId<LocationTrack>>> = publicationDao
        .fetchLinkedLocationTracks(ids, publicationSet.locationTracks.map { v -> v.officialId })
        .mapValues { (_, versions) ->
            val officialVersions = versions.filterNot { v -> publicationSet.containsLocationTrack(v.officialId) }
            cacheOfficialVersions(officialVersions.map { v -> v.validatedAssetVersion }, locationTrackVersionCache)
            versions.map { v -> v.officialId }
        }

    fun collectAssociatedTrackNumberIds(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>> = emptyList(),
        referenceLineIds: List<IntId<ReferenceLine>> = emptyList(),
        kmPostIds: List<IntId<TrackLayoutKmPost>> = emptyList(),
        trackIds: List<IntId<LocationTrack>> = emptyList(),
    ): List<IntId<TrackLayoutTrackNumber>> = listOf(
        trackNumberIds,
        referenceLineIds.mapNotNull { id -> getReferenceLine(id)?.trackNumberId },
        kmPostIds.mapNotNull { id -> getKmPost(id)?.trackNumberId },
        trackIds.mapNotNull { id -> getLocationTrack(id)?.trackNumberId },
    ).flatten().distinct()

    private fun fetchLocationTrackDuplicateLinks(
        ids: List<IntId<LocationTrack>>
    ): Map<IntId<LocationTrack>, List<IntId<LocationTrack>>> {
        val officialVersions = publicationDao
            .fetchOfficialDuplicateTrackVersions(ids)
            .mapValues { (_, versions) -> versions.filterNot { v -> publicationSet.containsLocationTrack(v.id) } }
            .also { duplicateMap -> cacheOfficialVersions(duplicateMap.values.flatten(), locationTrackVersionCache) }
        val draftTracks = publicationSet.locationTracks.map { v -> locationTrackDao.fetch(v.validatedAssetVersion) }
        return ids.associateWith { id ->
            val draft = draftTracks.filter { t -> t.duplicateOf == id }.map { t -> t.id as IntId }
            val official = officialVersions[id]?.map { v -> v.id } ?: emptyList()
            draft + official
        }
    }
}

private fun <T : LayoutAsset<T>> getObject(
    id: IntId<T>,
    publicationVersions: List<ValidationVersion<T>>,
    dao: ILayoutAssetDao<T>,
    versionCache: RowVersionCache<T>,
): T? {
    val version = publicationVersions.find { v -> v.officialId == id }?.validatedAssetVersion
        ?: versionCache.get(id, dao::fetchOfficialVersion)
    return version?.let(dao::fetch)
}

private fun <T : LayoutAsset<T>> preloadOfficialVersions(
    ids: List<IntId<T>>,
    dao: ILayoutAssetDao<T>,
    versionCache: RowVersionCache<T>,
) = cacheOfficialVersions(dao.fetchOfficialVersions(ids), versionCache)

private fun <T : LayoutAsset<T>> cacheOfficialVersions(versions: List<RowVersion<T>>, cache: RowVersionCache<T>) {
    cache.putAll(versions.filterNot { (id) -> cache.contains(id) }.associateBy { v -> v.id })
}

private fun <T : LayoutAsset<T>, Field> mapIdsByField(
    fields: List<Field>,
    getField: (T) -> Field,
    publicationVersions: List<ValidationVersion<T>>,
    matchingOfficialVersions: Map<Field, List<RowVersion<T>>>,
    dao: ILayoutAssetDao<T>,
): Map<Field, List<IntId<T>>> {
    return fields.associateWith { field ->
        val draftMatches = publicationVersions.mapNotNull { v ->
            val draftObject = dao.fetch(v.validatedAssetVersion)
            if (getField(draftObject) == field) draftObject.id as IntId else null
        }
        val officialMatches = matchingOfficialVersions[field]
            ?.filterNot { ov -> publicationVersions.any { pv -> pv.officialId == ov.id } }
            ?.map { v -> v.id } ?: emptyList()
        (draftMatches + officialMatches).distinct()
    }
}
