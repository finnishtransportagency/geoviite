package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
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
        // TODO: GVT-2442 test that this actually works
        // Also cache the "not found" results
        val notFound = missingKeys.filterNot(preloaded::containsKey)
        putAll(preloaded + notFound.associateWith { null })
    }

    fun putAll(values: Map<K, V?>): Unit = map.putAll(values.mapValues { (_, v) -> NullableValue(v) })

    fun contains(key: K): Boolean = map.containsKey(key)

    // Holder for nullable value so computeIfAbsent doesn't re-resolve a missing result
    data class NullableValue<T>(val value: T?)
}

typealias RowVersionCache<T> = NullableCache<IntId<T>, RowVersion<T>>

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
    private val trackNumberVersionCache: RowVersionCache<TrackLayoutTrackNumber> = RowVersionCache()
    private val referenceLineVersionCache: RowVersionCache<ReferenceLine> = RowVersionCache()
    private val kmPostVersionCache: RowVersionCache<TrackLayoutKmPost> = RowVersionCache()
    private val locationTrackVersionCache: RowVersionCache<LocationTrack> = RowVersionCache()
    private val switchVersionCache: RowVersionCache<TrackLayoutSwitch> = RowVersionCache()

    private val trackNumberKmPosts: NullableCache<IntId<TrackLayoutTrackNumber>, List<IntId<TrackLayoutKmPost>>> = NullableCache()
    private val trackNumberLocationTracks: NullableCache<IntId<TrackLayoutTrackNumber>, List<IntId<LocationTrack>>> = NullableCache()

    private val switchTrackLinks: NullableCache<IntId<TrackLayoutSwitch>, List<IntId<LocationTrack>>> = NullableCache()
    private val trackDuplicateLinks: NullableCache<IntId<LocationTrack>, List<IntId<LocationTrack>>> = NullableCache()
    private val geocodingContextKeys: NullableCache<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey> = NullableCache()

    val switchNameCache: NullableCache<SwitchName, List<IntId<TrackLayoutSwitch>>> = NullableCache()
    val trackNameCache: NullableCache<AlignmentName, List<IntId<LocationTrack>>> = NullableCache()
    val trackNumberNumberCache: NullableCache<TrackNumber, List<IntId<TrackLayoutTrackNumber>>> = NullableCache()

    fun getTrackNumber(id: IntId<TrackLayoutTrackNumber>): TrackLayoutTrackNumber? =
        getObject(id, publicationSet.trackNumbers, trackNumberDao, trackNumberVersionCache)

    fun getTrackNumbersByNumber(number: TrackNumber): List<TrackLayoutTrackNumber> =
        trackNumberNumberCache.get(number) { n ->
            throw IllegalStateException("TrackNumber numbers should have been preloaded before use: number=$n")
        }?.mapNotNull(::getTrackNumber) ?: emptyList()

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

    fun getLocationTrackWithAlignment(id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment>? =
        getLocationTrack(id)?.let { track ->
            track to requireNotNull(track.alignmentVersion?.let(alignmentDao::fetch)) {
                "LocationTrack in DB needs to have an alignment: id=$id"
            }
        }

    fun getSwitch(id: IntId<TrackLayoutSwitch>): TrackLayoutSwitch? =
        getObject(id, publicationSet.switches, switchDao, switchVersionCache)

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
        val officialLinks = if (track == null || track.isDraft()) {
            getOfficialLocationTrack(trackId)?.switchIds ?: emptyList()
        } else emptyList()
        return (officialLinks + draftLinks).distinct()
    }

    fun getPotentiallyAffectedSwitches(trackId: IntId<LocationTrack>): List<TrackLayoutSwitch> =
        getPotentiallyAffectedSwitchIds(trackId).mapNotNull(::getSwitch)

    fun getSwitchesByName(name: SwitchName): List<TrackLayoutSwitch> = switchNameCache.get(name) { n ->
        throw IllegalStateException("Switch names should have been preloaded before use: name=$n")
    }?.mapNotNull(::getSwitch) ?: emptyList()

    fun getSegmentSwitches(alignment: LayoutAlignment): List<SegmentSwitch> = alignment.segments
        .mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
        .groupBy({ (switchId, _) -> switchId }, { (_, segment) -> segment })
        .map { (switchId, segments) ->
            val switch = getSwitch(switchId)
            val name = switch?.name ?: getDraftSwitch(switchId).name
            val structure = switch?.switchStructureId?.let(switchLibraryService::getSwitchStructure)
            SegmentSwitch(switchId, name, switch, structure, segments)
        }

    fun getTopologicallyConnectedSwitches(track: LocationTrack): List<Pair<SwitchName, TrackLayoutSwitch?>> =
        listOfNotNull(track.topologyStartSwitch?.switchId, track.topologyEndSwitch?.switchId).map { switchId ->
            val switch = getSwitch(switchId)
            val name = switch?.name ?: getDraftSwitch(switchId).name
            name to switch
        }

    fun getLocationTracksByName(name: AlignmentName): List<LocationTrack> = trackNameCache.get(name) { n ->
        throw IllegalStateException("Track names should have been preloaded before use: name=$n")
    }?.mapNotNull(::getLocationTrack) ?: emptyList()

    fun getDuplicateTrackIds(trackId: IntId<LocationTrack>): List<IntId<LocationTrack>>? =
        trackDuplicateLinks.get(trackId) { id -> fetchLocationTrackDuplicateLinks(listOf(id))[id] }

    fun getDuplicateTracks(id: IntId<LocationTrack>): List<LocationTrack> =
        (getDuplicateTrackIds(id) ?: emptyList()).mapNotNull(::getLocationTrack)

    fun getGeocodingContext(trackNumberId: IntId<TrackLayoutTrackNumber>) =
        getGeocodingContextCacheKey(trackNumberId)?.let { key ->
            geocodingService.getGeocodingContext(key)
        }

    fun getGeocodingContextCacheKey(trackNumberId: IntId<TrackLayoutTrackNumber>) =
        geocodingContextKeys.get(trackNumberId) { tnId ->
            geocodingService.getGeocodingContextCacheKey(tnId, publicationSet)
        }

    // TODO: GVT-2442 track-switch validation wants the official ones to validate that no switch was left trackless. Include caching?
    fun getOfficialLocationTrack(id: IntId<LocationTrack>) = locationTrackDao.get(OFFICIAL, id)

    // TODO: GVT-2442 Draft searches that ignore context, for logging reference errors when the item doesn't exist in the context. Include caching?
    fun getDraftTrackNumber(id: IntId<TrackLayoutTrackNumber>) = trackNumberDao.getOrThrow(DRAFT, id)
    fun getDraftSwitch(id: IntId<TrackLayoutSwitch>) = switchDao.getOrThrow(DRAFT, id)
    fun getDraftLocationTrack(id: IntId<LocationTrack>) = locationTrackDao.getOrThrow(DRAFT, id)

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
        preloadOfficialVersions(ids, publicationSet.trackNumbers, trackNumberDao, trackNumberVersionCache)

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

    fun preloadReferenceLineVersions(ids: List<IntId<ReferenceLine>>) =
        preloadOfficialVersions(ids, publicationSet.referenceLines, referenceLineDao, referenceLineVersionCache)

    fun preloadKmPostVersions(ids: List<IntId<TrackLayoutKmPost>>) =
        preloadOfficialVersions(ids, publicationSet.kmPosts, kmPostDao, kmPostVersionCache)

    fun preloadLocationTrackVersions(ids: List<IntId<LocationTrack>>) =
        preloadOfficialVersions(ids, publicationSet.locationTracks, locationTrackDao, locationTrackVersionCache)

    fun preloadSwitchVersions(ids: List<IntId<TrackLayoutSwitch>>) =
        preloadOfficialVersions(ids, publicationSet.switches, switchDao, switchVersionCache)

    fun preloadSwitchTrackLinks(switchIds: List<IntId<TrackLayoutSwitch>>): Unit =
        switchTrackLinks.preload(switchIds, ::fetchSwitchTrackLinks)

    fun preloadTrackDuplicates(trackIds: List<IntId<LocationTrack>>): Unit =
        trackDuplicateLinks.preload(trackIds, ::fetchLocationTrackDuplicateLinks)

    fun preloadSwitchesByName(comparedSwitchIds: List<IntId<TrackLayoutSwitch>>) {
        val names = comparedSwitchIds.mapNotNull(::getSwitch).map(TrackLayoutSwitch::name).distinct()
        val officialVersions = switchDao.findOfficialNameDuplicates(names)
        cacheOfficialVersions(officialVersions.values.flatten(), switchVersionCache)
        val nameIndex = mapIdsByField(names, { s -> s.name }, publicationSet.switches, officialVersions, switchDao)
        switchNameCache.putAll(nameIndex)
    }

    fun preloadLocationTracksByName(comparedTrackIds: List<IntId<LocationTrack>>) {
        val names = comparedTrackIds.mapNotNull(::getLocationTrack).map(LocationTrack::name).distinct()
        val officialVersions = locationTrackDao.findOfficialNameDuplicates(names)
        cacheOfficialVersions(officialVersions.values.flatten(), locationTrackVersionCache)
        val nameIndex = mapIdsByField(names, { t -> t.name }, publicationSet.locationTracks, officialVersions, locationTrackDao)
        trackNameCache.putAll(nameIndex)
    }

    fun preloadTrackNumbersByNumber(comparedTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>) {
        val names = comparedTrackNumberIds.mapNotNull(::getTrackNumber).map(TrackLayoutTrackNumber::number).distinct()
        val officialVersions = trackNumberDao.findOfficialNumberDuplicates(names)
        cacheOfficialVersions(officialVersions.values.flatten(), trackNumberVersionCache)
        val nameIndex = mapIdsByField(names, { t -> t.number }, publicationSet.trackNumbers, officialVersions, trackNumberDao)
        trackNumberNumberCache.putAll(nameIndex)
    }

    private fun fetchKmPostIdsByTrackNumbers(
        tnIds: List<IntId<TrackLayoutTrackNumber>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<IntId<TrackLayoutKmPost>>> = kmPostDao
        // TODO: GVT-2442 The sql behind this could be simplified at the cost of making this more complex:
        //  - fetch the official version with links
        //  - override by publication set, noting their respective links
        .fetchVersionsForPublication(tnIds, publicationSet.kmPosts.map { v -> v.officialId })
        .mapValues { (_, versions) ->
            cacheOfficialValidationVersions(versions, kmPostVersionCache)
            versions.map { v -> v.officialId }
        }

    private fun fetchLocationTrackIdsByTrackNumbers(
        tnIds: List<IntId<TrackLayoutTrackNumber>>,
    ): Map<IntId<TrackLayoutTrackNumber>, List<IntId<LocationTrack>>> = locationTrackDao
        // TODO: GVT-2442 The sql behind this could be simplified at the cost of making this more complex:
        //  - fetch the official version with links
        //  - override by publication set, noting their respective links
        .fetchVersionsForPublication(tnIds, publicationSet.locationTracks.map { v -> v.officialId })
        .mapValues { (_, versions) ->
            cacheOfficialValidationVersions(versions, locationTrackVersionCache)
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

    private fun fetchSwitchTrackLinks(
        ids: List<IntId<TrackLayoutSwitch>>,
    ): Map<IntId<TrackLayoutSwitch>, List<IntId<LocationTrack>>> {
        val draftTrackIds = publicationSet.locationTracks.map { v -> v.officialId }
        // TODO: GVT-2442 The sql behind this could be simplified at the cost of making this more complex:
        //  - fetch the official version with links
        //  - override by publication set
        //  - filter (using LocationTrack.switchIds) to drop tracks where links are removed in draft
        return publicationDao.fetchLinkedLocationTracks(ids, draftTrackIds).mapValues { (_, trackVersions) ->
            // We get the versions on the same fetch, so cache those as well
            val firstSeenVersions = trackVersions
                .filterNot { v -> publicationSet.containsLocationTrack(v.officialId) || locationTrackVersionCache.contains(v.officialId) }
                .associate { v -> v.officialId to v.validatedAssetVersion }
            locationTrackVersionCache.putAll(firstSeenVersions)
            trackVersions.map { v -> v.officialId }
        }
    }

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

    private fun <T : Draftable<T>> getObject(
        id: IntId<T>,
        publicationVersions: List<ValidationVersion<T>>,
        dao: IDraftableObjectDao<T>,
        versionCache: RowVersionCache<T>,
    ): T? {
        val version = publicationVersions.find { v -> v.officialId == id }?.validatedAssetVersion
            ?: versionCache.get(id, dao::fetchOfficialVersion)
        return version?.let(dao::fetch)
    }

    private fun <T : Draftable<T>> preloadOfficialVersions(
        ids: List<IntId<T>>,
        publicationVersions: List<ValidationVersion<T>>,
        dao: IDraftableObjectDao<T>,
        versionCache: RowVersionCache<T>,
    ) {
        val missingIds = ids.filterNot { id ->
            publicationVersions.any { v -> v.officialId == id } || versionCache.contains(id)
        }
        cacheOfficialVersions(dao.fetchOfficialVersions(missingIds), versionCache)
    }

    private fun <T : Draftable<T>> cacheOfficialValidationVersions(
        versions: List<ValidationVersion<T>>,
        cache: RowVersionCache<T>,
    ) = cacheOfficialVersions(
        versions = versions.filter { v -> v.isOfficial() }.map { v -> v.validatedAssetVersion },
        cache = cache,
    )

    private fun <T : Draftable<T>> cacheOfficialVersions(versions: List<RowVersion<T>>, cache: RowVersionCache<T>) {
        cache.putAll(versions.filterNot { (id) -> cache.contains(id) }.distinct().associateBy { v -> v.id })
    }

    private fun <T : Draftable<T>, Field> mapIdsByField(
        fields: List<Field>,
        getField: (T) -> Field,
        publicationVersions: List<ValidationVersion<T>>,
        matchingOfficialVersions: Map<Field, List<RowVersion<T>>>,
        dao: IDraftableObjectDao<T>,
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
}
