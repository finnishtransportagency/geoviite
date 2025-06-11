package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.AugLocationTrack
import fi.fta.geoviite.infra.tracklayout.ILayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.ILocationTrack
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
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

typealias RowVersionCache<T> = NullableCache<IntId<T>, LayoutRowVersion<T>>

typealias ReferenceCache<To, From> = NullableCache<IntId<To>, List<IntId<From>>>

/**
 * Validation context provides a layout world-view that combines the candidates of a publication set (selected objects
 * in the candidate context) to the baseline of a base layout. Each object only has one version that can exist in this
 * context.
 *
 * In addition, it caches the validation data for quicker access.
 */
class ValidationContext(
    val trackNumberDao: LayoutTrackNumberDao,
    val referenceLineDao: ReferenceLineDao,
    val kmPostDao: LayoutKmPostDao,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val alignmentDao: LayoutAlignmentDao,
    val switchDao: LayoutSwitchDao,
    val switchLibraryService: SwitchLibraryService,
    val publicationDao: PublicationDao,
    val geocodingService: GeocodingService,
    val splitService: SplitService,
    val publicationSet: ValidationVersions,
) {
    val target = publicationSet.target

    private val trackNumberVersionCache = RowVersionCache<LayoutTrackNumber>()
    private val referenceLineVersionCache = RowVersionCache<ReferenceLine>()
    private val kmPostVersionCache = RowVersionCache<LayoutKmPost>()
    private val locationTrackVersionCache = RowVersionCache<LocationTrack>()
    private val switchVersionCache = RowVersionCache<LayoutSwitch>()

    private val trackNumberKmPosts = ReferenceCache<LayoutTrackNumber, LayoutKmPost>()
    private val trackNumberLocationTracks = ReferenceCache<LayoutTrackNumber, LocationTrack>()
    private val switchTrackLinks = ReferenceCache<LayoutSwitch, LocationTrack>()
    private val trackDuplicateLinks = ReferenceCache<LocationTrack, LocationTrack>()

    private val geocodingContextKeys = NullableCache<IntId<LayoutTrackNumber>, GeocodingContextCacheKey>()
    private val switchNameCache = NameCache(::fetchSwitchesByName)
    private val trackNameCache = NameCache(::fetchLocationTracksByName)
    private val trackNumberNumberCache = NameCache(::fetchTrackNumbersByNumber)

    private val allUnfinishedSplits: List<Split> by lazy { splitService.findUnfinishedSplits(target.candidateBranch) }

    fun getTrackNumber(id: IntId<LayoutTrackNumber>): LayoutTrackNumber? =
        getObject(target.baseContext, id, publicationSet.trackNumbers, trackNumberDao, trackNumberVersionCache)

    fun getTrackNumbersByNumber(number: TrackNumber): List<LayoutTrackNumber> =
        trackNumberNumberCache.get(number).mapNotNull(::getTrackNumber)

    fun getReferenceLine(id: IntId<ReferenceLine>): ReferenceLine? =
        getObject(target.baseContext, id, publicationSet.referenceLines, referenceLineDao, referenceLineVersionCache)

    fun getReferenceLineWithAlignment(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment>? =
        getObject(target.baseContext, id, publicationSet.referenceLines, referenceLineDao, referenceLineVersionCache)
            ?.let { rl -> rl to alignmentDao.fetch(rl.getAlignmentVersionOrThrow()) }

    fun getKmPost(id: IntId<LayoutKmPost>): LayoutKmPost? =
        getObject(target.baseContext, id, publicationSet.kmPosts, kmPostDao, kmPostVersionCache)

    fun getLocationTrack(id: IntId<LocationTrack>): LocationTrack? =
        getObject(target.baseContext, id, publicationSet.locationTracks, locationTrackDao, locationTrackVersionCache)

    fun getAugLocationTrack(id: IntId<LocationTrack>): AugLocationTrack? =
        // TODO GVT-3080
        throw NotImplementedError("AugLocationTrack is not implemented in ValidationContext")

    fun getLocationTracksByName(name: AlignmentName): List<LocationTrack> =
        trackNameCache.get(name).mapNotNull(::getLocationTrack)

    fun getDuplicateTrackIds(trackId: IntId<LocationTrack>): List<IntId<LocationTrack>>? =
        trackDuplicateLinks.get(trackId) { id -> fetchLocationTrackDuplicateLinks(listOf(id))[id] }

    fun getDuplicateAugTracks(id: IntId<LocationTrack>): List<AugLocationTrack> =
        (getDuplicateTrackIds(id) ?: emptyList()).mapNotNull(::getAugLocationTrack)

    fun getAugLocationTrackWithGeometry(id: IntId<LocationTrack>): Pair<AugLocationTrack, LocationTrackGeometry>? =
        getAugLocationTrack(id)?.let { track -> track to alignmentDao.fetch(track.versionOrThrow) }

    fun getSwitch(id: IntId<LayoutSwitch>): LayoutSwitch? =
        getObject(target.baseContext, id, publicationSet.switches, switchDao, switchVersionCache)

    fun getSwitchesByName(name: SwitchName): List<LayoutSwitch> = switchNameCache.get(name).mapNotNull(::getSwitch)

    fun getReferenceLineByTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): ReferenceLine? =
        getReferenceLineIdByTrackNumber(trackNumberId)?.let(::getReferenceLine)

    fun getReferenceLineIdByTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): IntId<ReferenceLine>? =
        // the track number candidate might be a cancellation, in which case getTrackNumber hides it; but we still need
        // to check referential integrity. Thankfully a track number's referenceLineId is itself immutable, so this
        // can't give a wrong one.
        (getTrackNumber(trackNumberId) ?: getCandidateTrackNumber(trackNumberId))?.referenceLineId

    fun getTrackNumberIdByReferenceLine(referenceLineId: IntId<ReferenceLine>): IntId<LayoutTrackNumber>? =
        (getReferenceLine(referenceLineId) ?: getCandidateReferenceLine(referenceLineId))?.trackNumberId

    fun getKmPostsByTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): List<LayoutKmPost> =
        trackNumberKmPosts
            .get(trackNumberId) { id -> fetchKmPostIdsByTrackNumbers(listOf(id))[id] }
            ?.mapNotNull(::getKmPost) ?: emptyList()

    fun getLocationTracksByTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): List<LocationTrack> =
        trackNumberLocationTracks
            .get(trackNumberId) { id -> fetchLocationTrackIdsByTrackNumbers(listOf(id))[id] }
            ?.mapNotNull(::getLocationTrack) ?: emptyList()

    fun getAugLocationTracksByTrackNumber(trackNumberId: IntId<LayoutTrackNumber>): List<AugLocationTrack> =
        throw NotImplementedError() // TODO: GVT-3080

    fun getSwitchTrackLinks(switchId: IntId<LayoutSwitch>): List<IntId<LocationTrack>>? =
        switchTrackLinks.get(switchId) { id -> fetchSwitchTrackLinks(listOf(id))[id] }

    fun getSwitchTracksWithGeometries(id: IntId<LayoutSwitch>): List<Pair<AugLocationTrack, LocationTrackGeometry>> =
        getSwitchTrackLinks(id)?.mapNotNull(::getAugLocationTrackWithGeometry) ?: emptyList()

    fun getPotentiallyAffectedSwitchIds(trackId: IntId<LocationTrack>): List<IntId<LayoutSwitch>> {
        val track =
            if (locationTrackIsCancelled(trackId)) getCandidateLocationTrack(trackId) else getLocationTrack(trackId)
        val draftLinks = track?.switchIds ?: emptyList()
        val officialLinks =
            if (track == null || track.isDraft) {
                locationTrackVersionCache
                    .get(trackId) { id -> locationTrackDao.fetchVersion(target.baseContext, id) }
                    ?.let(locationTrackDao::fetch)
                    ?.switchIds ?: emptyList()
            } else {
                emptyList()
            }
        return (officialLinks + draftLinks).distinct()
    }

    fun getPotentiallyAffectedSwitches(trackId: IntId<LocationTrack>): List<LayoutSwitch> =
        getPotentiallyAffectedSwitchIds(trackId).mapNotNull(::getSwitch)

    fun getSwitchTrackLinks(geometry: LocationTrackGeometry): List<SwitchTrackLinking> =
        geometry.trackSwitchLinks
            .mapIndexed { index, link -> index to link }
            .groupBy({ (_, link) -> link.switchId })
            .map { (switchId, links) ->
                val switch = getSwitch(switchId)
                val name = switch?.name ?: getCandidateSwitch(switchId)?.name
                val structure = switch?.switchStructureId?.let(switchLibraryService::getSwitchStructure)
                SwitchTrackLinking(switchId, name, switch, structure, links, switchIsCancelled(switchId))
            }

    fun getPublicationSplits(): List<Split> =
        allUnfinishedSplits.filter { split -> publicationSet.containsSplit(split.id) }

    fun getUnfinishedSplits(): List<Split> =
        allUnfinishedSplits.filter { split -> split.publicationId != null || publicationSet.containsSplit(split.id) }

    fun getGeocodingContext(trackNumberId: IntId<LayoutTrackNumber>) =
        getGeocodingContextCacheKey(trackNumberId)?.let { key -> geocodingService.getGeocodingContext(key) }

    fun getGeocodingContextCacheKey(trackNumberId: IntId<LayoutTrackNumber>) =
        geocodingContextKeys.get(trackNumberId) { tnId ->
            geocodingService.getGeocodingContextCacheKey(tnId, publicationSet)
        }

    fun getAddressPoints(trackId: IntId<LocationTrack>): AlignmentAddresses? =
        getLocationTrack(trackId)?.let(::getAddressPoints)

    fun getAddressPoints(track: ILocationTrack): AlignmentAddresses? =
        getGeocodingContextCacheKey(track.trackNumberId)?.let { key ->
            geocodingService.getAddressPoints(key, track.versionOrThrow)
        }

    fun trackNumberIsCancelled(id: IntId<LayoutTrackNumber>) =
        objectIsCancelled(id, publicationSet.trackNumbers, trackNumberDao)

    fun referenceLineIsCancelled(id: IntId<ReferenceLine>) =
        objectIsCancelled(id, publicationSet.referenceLines, referenceLineDao)

    fun locationTrackIsCancelled(id: IntId<LocationTrack>) =
        objectIsCancelled(id, publicationSet.locationTracks, locationTrackDao)

    fun switchIsCancelled(id: IntId<LayoutSwitch>) = objectIsCancelled(id, publicationSet.switches, switchDao)

    fun kmPostIsCancelled(id: IntId<LayoutKmPost>) = objectIsCancelled(id, publicationSet.kmPosts, kmPostDao)

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
        preloadSwitchesByName(publicationSet.switches.map { v -> v.id })
        preloadSwitchTrackLinks(allSwitchIds)
    }

    fun preloadTrackNumberVersions(ids: List<IntId<LayoutTrackNumber>>) =
        preloadBaseVersions(target.baseContext, ids, trackNumberDao, trackNumberVersionCache)

    fun preloadReferenceLineVersions(ids: List<IntId<ReferenceLine>>) =
        preloadBaseVersions(target.baseContext, ids, referenceLineDao, referenceLineVersionCache)

    fun preloadKmPostVersions(ids: List<IntId<LayoutKmPost>>) =
        preloadBaseVersions(target.baseContext, ids, kmPostDao, kmPostVersionCache)

    fun preloadLocationTrackVersions(ids: List<IntId<LocationTrack>>) =
        preloadBaseVersions(target.baseContext, ids, locationTrackDao, locationTrackVersionCache)

    fun preloadSwitchVersions(ids: List<IntId<LayoutSwitch>>) =
        preloadBaseVersions(target.baseContext, ids, switchDao, switchVersionCache)

    fun preloadKmPostsByTrackNumbers(tnIds: List<IntId<LayoutTrackNumber>>) =
        trackNumberKmPosts.preload(tnIds, ::fetchKmPostIdsByTrackNumbers)

    fun preloadLocationTracksByTrackNumbers(tnIds: List<IntId<LayoutTrackNumber>>) =
        trackNumberLocationTracks.preload(tnIds, ::fetchLocationTrackIdsByTrackNumbers)

    fun preloadAssociatedTrackNumberAndReferenceLineVersions(versions: ValidationVersions) =
        preloadAssociatedTrackNumberAndReferenceLineVersions(
            trackNumberIds = versions.trackNumbers.map { v -> v.id },
            referenceLineIds = versions.referenceLines.map { v -> v.id },
            kmPostIds = versions.kmPosts.map { v -> v.id },
            trackIds = versions.locationTracks.map { v -> v.id },
        )

    fun preloadAssociatedTrackNumberAndReferenceLineVersions(
        trackNumberIds: List<IntId<LayoutTrackNumber>> = emptyList(),
        referenceLineIds: List<IntId<ReferenceLine>> = emptyList(),
        kmPostIds: List<IntId<LayoutKmPost>> = emptyList(),
        trackIds: List<IntId<LocationTrack>> = emptyList(),
    ): Unit =
        preloadTrackNumberAndReferenceLineVersions(
            collectAssociatedTrackNumberIds(trackNumberIds, referenceLineIds, kmPostIds, trackIds)
        )

    fun preloadTrackNumberAndReferenceLineVersions(ids: List<IntId<LayoutTrackNumber>>) =
        preloadTrackNumberVersions(ids).also {
            val referenceLineIds = ids.mapNotNull(::getTrackNumber).mapNotNull(LayoutTrackNumber::referenceLineId)
            preloadReferenceLineVersions(referenceLineIds)
        }

    fun preloadSwitchTrackLinks(switchIds: List<IntId<LayoutSwitch>>): Unit =
        switchTrackLinks.preload(switchIds, ::fetchSwitchTrackLinks)

    fun preloadTrackDuplicates(trackIds: List<IntId<LocationTrack>>): Unit =
        trackDuplicateLinks.preload(trackIds, ::fetchLocationTrackDuplicateLinks)

    fun preloadSwitchesByName(switchIds: List<IntId<LayoutSwitch>>) =
        switchNameCache.preload(switchIds.mapNotNull(::getSwitch).map(LayoutSwitch::name).distinct())

    fun fetchSwitchesByName(names: List<SwitchName>): Map<SwitchName, List<IntId<LayoutSwitch>>> {
        val baseVersions = switchDao.findNameDuplicates(target.baseContext, names)
        cacheBaseVersions(baseVersions.values.flatten(), switchVersionCache)
        return mapIdsByField(names, { s -> s.name }, publicationSet.switches, baseVersions, switchDao)
    }

    fun preloadLocationTracksByName(trackIds: List<IntId<LocationTrack>>) =
        trackNameCache.preload(trackIds.mapNotNull(::getLocationTrackName).distinct())

    fun fetchLocationTracksByName(names: List<AlignmentName>): Map<AlignmentName, List<IntId<LocationTrack>>> {
        val baseVersions = locationTrackDao.findNameDuplicates(target.baseContext, names)
        cacheBaseVersions(baseVersions.values.flatten(), locationTrackVersionCache)
        return mapIdsByField(
            names,
            { t -> getLocationTrackName(t.id as IntId) ?: AlignmentName("") },
            publicationSet.locationTracks,
            baseVersions,
            locationTrackDao,
        )
    }

    fun preloadTrackNumbersByNumber(trackNumberIds: List<IntId<LayoutTrackNumber>>) =
        trackNumberNumberCache.preload(
            trackNumberIds.mapNotNull(::getTrackNumber).map(LayoutTrackNumber::number).distinct()
        )

    private fun fetchTrackNumbersByNumber(
        numbers: List<TrackNumber>
    ): Map<TrackNumber, List<IntId<LayoutTrackNumber>>> {
        val baseVersions = trackNumberDao.findNumberDuplicates(target.baseContext, numbers)
        cacheBaseVersions(baseVersions.values.flatten(), trackNumberVersionCache)
        return mapIdsByField(numbers, { t -> t.number }, publicationSet.trackNumbers, baseVersions, trackNumberDao)
    }

    // Candidate searches that ignore context, for logging reference errors when the item doesn't
    // exist in the context
    // Versions are fetched lazily once, if needed
    fun getCandidateTrackNumber(id: IntId<LayoutTrackNumber>) = allDraftTrackNumbers[id]?.let(trackNumberDao::fetch)

    private val allDraftTrackNumbers: Map<IntId<LayoutTrackNumber>, LayoutRowVersion<LayoutTrackNumber>> by lazy {
        trackNumberDao.fetchCandidateVersions(target.candidateContext).associateBy { it.id }
    }

    fun getCandidateReferenceLine(id: IntId<ReferenceLine>) =
        allCandidateReferenceLines[id]?.let(referenceLineDao::fetch)

    private val allCandidateReferenceLines: Map<IntId<ReferenceLine>, LayoutRowVersion<ReferenceLine>> by lazy {
        referenceLineDao.fetchCandidateVersions(target.candidateContext).associateBy { it.id }
    }

    fun getCandidateSwitch(id: IntId<LayoutSwitch>) = allCandidateSwitches[id]?.let(switchDao::fetch)

    private val allCandidateSwitches: Map<IntId<LayoutSwitch>, LayoutRowVersion<LayoutSwitch>> by lazy {
        switchDao.fetchCandidateVersions(target.candidateContext).associateBy { it.id }
    }

    fun getCandidateLocationTrack(id: IntId<LocationTrack>) = allDraftLocationTracks[id]?.let(locationTrackDao::fetch)

    fun getCandidateAugLocationTrack(int: IntId<LocationTrack>): AugLocationTrack? =
        throw NotImplementedError() // TODO: GVT-3080

    private val allDraftLocationTracks: Map<IntId<LocationTrack>, LayoutRowVersion<LocationTrack>> by lazy {
        locationTrackDao.fetchCandidateVersions(target.candidateContext).associateBy { it.id }
    }

    private fun fetchKmPostIdsByTrackNumbers(
        tnIds: List<IntId<LayoutTrackNumber>>
    ): Map<IntId<LayoutTrackNumber>, List<IntId<LayoutKmPost>>> =
        kmPostDao.fetchVersionsForPublication(target, tnIds, publicationSet.kmPosts.map { v -> v.id }).mapValues {
            (_, versions) ->
            val officialVersions = versions.filterNot { v -> publicationSet.containsKmPost(v.id) }
            cacheBaseVersions(officialVersions, kmPostVersionCache)
            versions.map { v -> v.id }
        }

    private fun fetchLocationTrackIdsByTrackNumbers(
        tnIds: List<IntId<LayoutTrackNumber>>
    ): Map<IntId<LayoutTrackNumber>, List<IntId<LocationTrack>>> =
        locationTrackDao
            .fetchVersionsForPublication(target, tnIds, publicationSet.locationTracks.map { v -> v.id })
            .mapValues { (_, versions) ->
                val officialVersions = versions.filterNot { v -> publicationSet.containsLocationTrack(v.id) }
                cacheBaseVersions(officialVersions, locationTrackVersionCache)
                versions.map { v -> v.id }
            }

    private fun fetchSwitchTrackLinks(
        ids: List<IntId<LayoutSwitch>>
    ): Map<IntId<LayoutSwitch>, List<IntId<LocationTrack>>> =
        publicationDao
            .fetchLinkedLocationTracks(target, ids, publicationSet.locationTracks.map { v -> v.id })
            .mapValues { (_, versions) ->
                val officialVersions = versions.filterNot { v -> publicationSet.containsLocationTrack(v.id) }
                cacheBaseVersions(officialVersions, locationTrackVersionCache)
                versions.map { v -> v.id }
            }

    fun collectAssociatedTrackNumberIds(
        trackNumberIds: List<IntId<LayoutTrackNumber>> = emptyList(),
        referenceLineIds: List<IntId<ReferenceLine>> = emptyList(),
        kmPostIds: List<IntId<LayoutKmPost>> = emptyList(),
        trackIds: List<IntId<LocationTrack>> = emptyList(),
    ): List<IntId<LayoutTrackNumber>> =
        listOf(
                trackNumberIds,
                referenceLineIds.mapNotNull { id -> getReferenceLine(id)?.trackNumberId },
                kmPostIds.mapNotNull { id -> getKmPost(id)?.trackNumberId },
                trackIds.mapNotNull { id -> getLocationTrack(id)?.trackNumberId },
            )
            .flatten()
            .distinct()

    private fun fetchLocationTrackDuplicateLinks(
        ids: List<IntId<LocationTrack>>
    ): Map<IntId<LocationTrack>, List<IntId<LocationTrack>>> {
        val baseVersions =
            publicationDao
                .fetchBaseDuplicateTrackVersions(target.baseContext, ids)
                .mapValues { (_, vs) -> vs.filterNot { v -> publicationSet.containsLocationTrack(v.id) } }
                .also { duplicateMap -> cacheBaseVersions(duplicateMap.values.flatten(), locationTrackVersionCache) }
        val candidateTracks = publicationSet.locationTracks.map { v -> locationTrackDao.fetch(v) }
        return ids.associateWith { id ->
            val candidate = candidateTracks.filter { t -> t.duplicateOf == id }.map { t -> t.id as IntId }
            val base = baseVersions[id]?.map { v -> v.id } ?: emptyList()
            candidate + base
        }
    }
}

private fun <T : LayoutAsset<T>> objectIsCancelled(
    itemId: IntId<T>,
    publicationVersions: List<LayoutRowVersion<T>>,
    dao: ILayoutAssetDao<T, *>,
): Boolean = publicationVersions.find { v -> v.id == itemId }?.let(dao::fetch)?.isCancelled ?: false

private fun <T : LayoutAsset<T>> getObject(
    baseContext: LayoutContext,
    itemId: IntId<T>,
    publicationVersions: List<LayoutRowVersion<T>>,
    dao: ILayoutAssetDao<T, *>,
    versionCache: RowVersionCache<T>,
): T? {
    val publicationVersion = publicationVersions.find { v -> v.id == itemId }
    return if (publicationVersion != null) {
        val publicationObject = dao.fetch(publicationVersion)
        if (publicationObject.isCancelled) {
            dao.fetchVersion(MainLayoutContext.official, itemId)?.let(dao::fetch)
        } else publicationObject
    } else versionCache.get(itemId) { id -> dao.fetchVersion(baseContext, id) }?.let(dao::fetch)
}

private fun <T : LayoutAsset<T>> preloadBaseVersions(
    baseContext: LayoutContext,
    ids: List<IntId<T>>,
    dao: ILayoutAssetDao<T, *>,
    versionCache: RowVersionCache<T>,
) = cacheBaseVersions(dao.fetchVersions(baseContext, ids), versionCache)

private fun <T : LayoutAsset<T>> cacheBaseVersions(versions: List<LayoutRowVersion<T>>, cache: RowVersionCache<T>) =
    versions.filterNot { version -> cache.contains(version.id) }.associateBy { v -> v.id }.let(cache::putAll)

private fun <T : LayoutAsset<T>, Field> mapIdsByField(
    fields: List<Field>,
    getField: (T) -> Field,
    publicationVersions: List<LayoutRowVersion<T>>,
    matchingOfficialVersions: Map<Field, List<LayoutRowVersion<T>>>,
    dao: ILayoutAssetDao<T, *>,
): Map<Field, List<IntId<T>>> {
    return fields.associateWith { field ->
        val draftMatches =
            publicationVersions.mapNotNull { v ->
                val draftObject = dao.fetch(v)
                if (getField(draftObject) == field) draftObject.id as IntId else null
            }
        val officialMatches =
            matchingOfficialVersions[field]
                ?.filterNot { ov -> publicationVersions.any { pv -> pv.id == ov.id } }
                ?.map { v -> v.id } ?: emptyList()
        (draftMatches + officialMatches).distinct()
    }
}
