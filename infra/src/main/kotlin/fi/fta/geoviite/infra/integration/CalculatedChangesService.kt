package fi.fta.geoviite.infra.integration

import ChangeContext
import LazyMap
import com.fasterxml.jackson.annotation.JsonIgnore
import createTypedContext
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.InheritanceFromPublicationInMain
import fi.fta.geoviite.infra.publication.PreparedPublicationRequest
import fi.fta.geoviite.infra.publication.PublicationCause
import fi.fta.geoviite.infra.publication.PublicationResult
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.publication.PublicationValidationService
import fi.fta.geoviite.infra.publication.ValidateTransition
import fi.fta.geoviite.infra.publication.ValidationTarget
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.util.mapNonNullValues
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

data class TrackNumberChange(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val changedKmNumbers: Set<KmNumber>,
    val isStartChanged: Boolean,
    val isEndChanged: Boolean,
)

data class LocationTrackChange(
    val locationTrackId: IntId<LocationTrack>,
    val changedKmNumbers: Set<KmNumber>,
    val isStartChanged: Boolean,
    val isEndChanged: Boolean,
) {
    companion object {
        fun create(locationTrackId: IntId<LocationTrack>, addressChanges: AddressChanges?) =
            LocationTrackChange(
                locationTrackId = locationTrackId,
                changedKmNumbers = addressChanges?.changedKmNumbers ?: emptySet(),
                isStartChanged = addressChanges?.startPointChanged ?: true,
                isEndChanged = addressChanges?.endPointChanged ?: true,
            )
    }
}

data class SwitchJointChange(
    val number: JointNumber,
    val isRemoved: Boolean,
    val address: TrackMeter,
    val point: Point,
    val locationTrackId: IntId<LocationTrack>,
    val locationTrackExternalId: Oid<LocationTrack>?,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val trackNumberExternalId: Oid<LayoutTrackNumber>?,
)

data class SwitchChange(val switchId: IntId<LayoutSwitch>, val changedJoints: List<SwitchJointChange>)

data class DirectChanges(
    val kmPostChanges: List<IntId<LayoutKmPost>>,
    val referenceLineChanges: List<IntId<ReferenceLine>>,
    val trackNumberChanges: List<TrackNumberChange>,
    val locationTrackChanges: List<LocationTrackChange>,
    val switchChanges: List<SwitchChange>,
)

data class IndirectChanges(
    val trackNumberChanges: List<TrackNumberChange>,
    val locationTrackChanges: List<LocationTrackChange>,
    val switchChanges: List<SwitchChange>,
) {
    companion object {
        fun empty(): IndirectChanges = IndirectChanges(listOf(), listOf(), listOf())
    }

    @JsonIgnore
    fun isEmpty(): Boolean = trackNumberChanges.isEmpty() && locationTrackChanges.isEmpty() && switchChanges.isEmpty()
}

data class CalculatedChanges(val directChanges: DirectChanges, val indirectChanges: IndirectChanges) {
    companion object {
        fun empty() =
            CalculatedChanges(DirectChanges(listOf(), listOf(), listOf(), listOf(), listOf()), IndirectChanges.empty())

        fun onlyIndirect(indirectChanges: IndirectChanges) =
            CalculatedChanges(DirectChanges(listOf(), listOf(), listOf(), listOf(), listOf()), indirectChanges)
    }

    init {
        checkDuplicates(
            directChanges.kmPostChanges,
            { it },
            "Duplicate km posts in direct changes, directChanges=${directChanges.kmPostChanges}",
        )

        checkDuplicates(
            directChanges.referenceLineChanges,
            { it },
            "Duplicate reference lines in direct changes, directChanges=${directChanges.referenceLineChanges}",
        )

        val trackNumberChanges = (directChanges.trackNumberChanges + indirectChanges.trackNumberChanges)
        checkDuplicates(
            trackNumberChanges,
            { it.trackNumberId },
            "Duplicate track numbers in direct and indirect changes, directChanges=${directChanges.trackNumberChanges} indirectChanges=${indirectChanges.trackNumberChanges}",
        )

        val locationTrackChanges = (directChanges.locationTrackChanges + indirectChanges.locationTrackChanges)
        checkDuplicates(
            locationTrackChanges,
            { it.locationTrackId },
            "Duplicate location tracks in direct and indirect changes, directChanges=${directChanges.locationTrackChanges} indirectChanges=${indirectChanges.locationTrackChanges}",
        )

        val switchChanges = (directChanges.switchChanges + indirectChanges.switchChanges)
        checkDuplicates(
            switchChanges,
            { it.switchId },
            "Duplicate switches in direct and indirect changes, directChanges=${directChanges.switchChanges} indirectChanges=${indirectChanges.switchChanges}",
        )
    }

    private fun <T, R> checkDuplicates(changes: Collection<T>, groupingBy: (v: T) -> R, message: String) {
        check(changes.groupingBy(groupingBy).eachCount().all { it.value == 1 }) { message }
    }
}

@GeoviiteService
class CalculatedChangesService(
    val addressChangesService: AddressChangesService,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val switchDao: LayoutSwitchDao,
    val switchService: LayoutSwitchService,
    val switchLibraryService: SwitchLibraryService,
    val trackNumberDao: LayoutTrackNumberDao,
    val trackNumberService: LayoutTrackNumberService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val kmPostDao: LayoutKmPostDao,
    val geocodingService: GeocodingService,
    val alignmentDao: LayoutAlignmentDao,
    val publicationValidationService: PublicationValidationService,
) {
    fun getCalculatedChanges(versions: ValidationVersions): CalculatedChanges {
        val changeContext = createChangeContext(versions)
        val extIds = getAllOidsWithInheritance(versions.target.baseBranch)

        val (trackNumberChanges, changedLocationTrackIdsByTrackNumbers) =
            calculateTrackNumberChanges(versions.trackNumbers.map { it.id }, changeContext)

        val kPRLTrackNumberIds =
            (versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v).trackNumberId } +
                    versions.referenceLines.map { v -> referenceLineDao.fetch(v).trackNumberId })
                .distinct()

        val (kPRLTrackNumberChanges, changedLocationTrackIdsByKPRL) =
            calculateTrackNumberChanges(kPRLTrackNumberIds, changeContext)

        val directLocationTrackChanges =
            calculateLocationTrackChanges(versions.locationTracks.map { v -> v.id }, changeContext)

        val locationTrackChangesByTrackNumbers =
            calculateLocationTrackChanges(
                (changedLocationTrackIdsByTrackNumbers + changedLocationTrackIdsByKPRL).distinct(),
                changeContext,
            )

        val directSwitchChanges = asDirectSwitchChanges(versions.switches.map { v -> v.id })

        val (switchChangesByLocationTracks, locationTrackChangesBySwitches) =
            getSwitchChangesByGeometryChanges(
                mergeLocationTrackChanges(directLocationTrackChanges, locationTrackChangesByTrackNumbers),
                changeContext,
                extIds,
            )

        val (indirectDirectTrackNumberChanges, indirectTrackNumberChanges) =
            kPRLTrackNumberChanges.partition { indirectChange ->
                trackNumberChanges.any { indirectChange.trackNumberId == it.trackNumberId }
            }

        val (indirectDirectLocationTrackChanges, indirectLocationTrackChanges) =
            mergeLocationTrackChanges(locationTrackChangesByTrackNumbers, locationTrackChangesBySwitches).partition {
                indirectChange ->
                directLocationTrackChanges.any { indirectChange.locationTrackId == it.locationTrackId }
            }

        val (indirectDirectSwitchChanges, indirectSwitchChanges) =
            switchChangesByLocationTracks.partition { indirectChange ->
                directSwitchChanges.any { indirectChange.switchId == it.switchId }
            }

        return CalculatedChanges(
            directChanges =
                DirectChanges(
                    kmPostChanges = versions.kmPosts.map { it.id },
                    referenceLineChanges = versions.referenceLines.map { it.id },
                    trackNumberChanges = mergeTrackNumberChanges(trackNumberChanges, indirectDirectTrackNumberChanges),
                    locationTrackChanges =
                        mergeLocationTrackChanges(directLocationTrackChanges, indirectDirectLocationTrackChanges),
                    switchChanges = mergeSwitchChanges(directSwitchChanges, indirectDirectSwitchChanges),
                ),
            indirectChanges =
                IndirectChanges(
                    locationTrackChanges = indirectLocationTrackChanges,
                    switchChanges = indirectSwitchChanges,
                    trackNumberChanges = indirectTrackNumberChanges,
                ),
        )
    }

    @Transactional(readOnly = true)
    fun getCalculatedChangesForMainToDesignInheritance(
        branch: DesignBranch,
        trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
        referenceLines: List<LayoutRowVersion<ReferenceLine>>,
        locationTracks: List<LayoutRowVersion<LocationTrack>>,
        switches: List<LayoutRowVersion<LayoutSwitch>>,
        kmPosts: List<LayoutRowVersion<LayoutKmPost>>,
    ): IndirectChanges {
        val allOids = getAllOids(branch)
        return if (allOids.isEmpty()) IndirectChanges.empty()
        else {
            val validationVersions =
                ValidationVersions(
                    ValidateTransition(InheritanceFromPublicationInMain(branch)),
                    trackNumbers = getNonOverriddenVersions(branch, trackNumbers, trackNumberDao),
                    referenceLines = getNonOverriddenVersions(branch, referenceLines, referenceLineDao),
                    locationTracks = getNonOverriddenVersions(branch, locationTracks, locationTrackDao),
                    switches = getNonOverriddenVersions(branch, switches, switchDao),
                    kmPosts = getNonOverriddenVersions(branch, kmPosts, kmPostDao),
                    splits = listOf(),
                )
            getCalculatedChanges(validationVersions).indirectChanges.let {
                filterIndirectChangesByOidPresence(it, allOids)
            }
        }
    }

    fun combineInheritedChangesAndFinishedMerges(
        mainPublication: PreparedPublicationRequest,
        inheritedChanges: Map<DesignBranch, IndirectChanges>,
        mainPublicationResult: PublicationResult,
    ): List<PreparedPublicationRequest> {
        val completedTrackNumbers = getCompletedByBranch(mainPublicationResult.trackNumbers)
        val completedReferenceLines = getCompletedByBranch(mainPublicationResult.referenceLines)
        val completedLocationTracks = getCompletedByBranch(mainPublicationResult.locationTracks)
        val completedSwitches = getCompletedByBranch(mainPublicationResult.switches)
        val completedKmPosts = getCompletedByBranch(mainPublicationResult.kmPosts)

        return (inheritedChanges.keys +
                completedTrackNumbers.keys +
                completedReferenceLines.keys +
                completedLocationTracks.keys +
                completedSwitches.keys +
                completedKmPosts.keys)
            .map { branch ->
                combineInheritedChangesAndFinishedMergesInBranch(
                    inheritedChanges[branch] ?: IndirectChanges.empty(),
                    branch,
                    completedTrackNumbers[branch] ?: listOf(),
                    completedReferenceLines[branch] ?: listOf(),
                    completedLocationTracks[branch] ?: listOf(),
                    completedSwitches[branch] ?: listOf(),
                    completedKmPosts[branch] ?: listOf(),
                    mainPublication,
                )
            }
    }

    private fun combineInheritedChangesAndFinishedMergesInBranch(
        inheritedChanges: IndirectChanges,
        branch: DesignBranch,
        completedTrackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
        completedReferenceLines: List<LayoutRowVersion<ReferenceLine>>,
        completedLocationTracks: List<LayoutRowVersion<LocationTrack>>,
        completedSwitches: List<LayoutRowVersion<LayoutSwitch>>,
        completedKmPosts: List<LayoutRowVersion<LayoutKmPost>>,
        mainPublication: PreparedPublicationRequest,
    ): PreparedPublicationRequest {
        val versions =
            mergeInheritedChangeVersionsWithCompletedMergeVersions(
                getInheritedChangeVersions(branch, inheritedChanges),
                completedTrackNumbers = completedTrackNumbers,
                completedReferenceLines = completedReferenceLines,
                completedLocationTracks = completedLocationTracks,
                completedSwitches = completedSwitches,
                completedKmPosts = completedKmPosts,
            )

        val directChanges =
            DirectChanges(
                completedKmPosts.map { it.id },
                completedReferenceLines.map { it.id },
                mergeTrackNumberChanges(
                    inheritedChanges.trackNumberChanges,
                    completedTrackNumbers.map { v -> TrackNumberChange(v.id, setOf(), false, false) },
                ),
                mergeLocationTrackChanges(
                    inheritedChanges.locationTrackChanges,
                    completedLocationTracks.map { v -> LocationTrackChange(v.id, setOf(), false, false) },
                ),
                mergeSwitchChanges(
                    inheritedChanges.switchChanges,
                    completedSwitches.map { v -> SwitchChange(v.id, listOf()) },
                ),
            )
        val indirectChanges =
            IndirectChanges(
                inheritedChanges.trackNumberChanges.filter { indirect ->
                    completedTrackNumbers.none { indirect.trackNumberId == it.id }
                },
                inheritedChanges.locationTrackChanges.filter { indirect ->
                    completedLocationTracks.none { indirect.locationTrackId == it.id }
                },
                inheritedChanges.switchChanges.filter { indirect ->
                    completedSwitches.none { indirect.switchId == it.id }
                },
            )

        return PreparedPublicationRequest(
            branch,
            versions,
            CalculatedChanges(directChanges, indirectChanges),
            mainPublication.message,
            PublicationCause.CALCULATED_CHANGE,
        )
    }

    private fun <T : LayoutAsset<T>> getCompletedByBranch(
        versions: List<PublicationResultVersions<T>>
    ): Map<DesignBranch, List<LayoutRowVersion<T>>> =
        versions.mapNotNull { it.completed }.groupBy({ it.first }, { it.second })

    private fun getInheritedChangeVersions(
        inheritorBranch: DesignBranch,
        changes: IndirectChanges,
    ): ValidationVersions =
        ValidationVersions(
            ValidateTransition(InheritanceFromPublicationInMain(inheritorBranch)),
            trackNumbers =
                trackNumberDao
                    .getMany(inheritorBranch.official, changes.trackNumberChanges.map { it.trackNumberId })
                    .map { requireNotNull(it.version) },
            referenceLines = listOf(),
            locationTracks =
                locationTrackDao
                    .getMany(inheritorBranch.official, changes.locationTrackChanges.map { it.locationTrackId })
                    .map { requireNotNull(it.version) },
            switches =
                switchDao.getMany(inheritorBranch.official, changes.switchChanges.map { it.switchId }).map {
                    requireNotNull(it.version)
                },
            kmPosts = listOf(),
            splits = listOf(),
        )

    private fun processSwitchJointChangesByLocationTrackKmChange(
        switchJointChanges: List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>>,
        locationTrackId: IntId<LocationTrack>,
        trackNumberId: IntId<LayoutTrackNumber>,
        filterKmNumbers: Collection<KmNumber>,
        extIds: AllOids,
    ) =
        switchJointChanges.mapNotNull { (switch, changeData) ->
            val joints =
                changeData
                    .filter { change -> filterKmNumbers.contains(change.address.kmNumber) }
                    .map { change ->
                        SwitchJointChange(
                            number = change.joint.number,
                            isRemoved = false,
                            address = change.address,
                            point = change.point.toPoint(),
                            locationTrackId = locationTrackId,
                            locationTrackExternalId = extIds.locationTracks[locationTrackId],
                            trackNumberId = trackNumberId,
                            trackNumberExternalId = extIds.trackNumbers[trackNumberId],
                        )
                    }
            if (joints.isEmpty()) null else SwitchChange(switchId = switch, changedJoints = joints)
        }

    private fun getAllSwitchJointChanges(
        locationTrack: LocationTrack,
        fetchSwitchById: (id: IntId<LayoutSwitch>) -> LayoutSwitch?,
        getGeocodingContext: (id: IntId<LayoutTrackNumber>) -> GeocodingContext?,
    ): List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>> {
        val geometry = alignmentDao.fetch(locationTrack.versionOrThrow)
        val trackNumberId = locationTrack.trackNumberId
        val geocodingContext = getGeocodingContext(trackNumberId)

        return geocodingContext?.let { context ->
            getSwitchJointChanges(
                geometry = geometry,
                geocodingContext = context,
                fetchSwitch = fetchSwitchById,
                fetchStructure = switchLibraryService::getSwitchStructure,
            )
        } ?: emptyList()
    }

    fun getSwitchChangesFromChangedLocationTrackKms(
        fetchLocationTrackById: (id: IntId<LocationTrack>) -> LocationTrack?,
        fetchSwitchById: (id: IntId<LayoutSwitch>) -> LayoutSwitch?,
        getGeocodingContext: (id: IntId<LayoutTrackNumber>) -> GeocodingContext?,
        locationTrackId: IntId<LocationTrack>,
        filterKmNumbers: Collection<KmNumber>,
        extIds: AllOids,
    ): List<SwitchChange> {
        val locationTrack =
            fetchLocationTrackById(locationTrackId)
                ?: throw NoSuchEntityException(LocationTrack::class, locationTrackId)
        val allChanges = getAllSwitchJointChanges(locationTrack, fetchSwitchById, getGeocodingContext)
        return processSwitchJointChangesByLocationTrackKmChange(
            allChanges,
            locationTrackId,
            locationTrack.trackNumberId,
            filterKmNumbers,
            extIds,
        )
    }

    fun getSwitchChangesFromChangedLocationTrackKmsByMoment(
        layoutBranch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        moment: Instant,
        extIds: AllOids,
        filterKmNumbers: Collection<KmNumber>,
    ): List<SwitchChange> =
        getSwitchChangesFromChangedLocationTrackKms(
            fetchLocationTrackById = { id -> locationTrackService.getOfficialAtMoment(layoutBranch, id, moment) },
            fetchSwitchById = { id -> switchService.getOfficialAtMoment(layoutBranch, id, moment) },
            getGeocodingContext = { id -> geocodingService.getGeocodingContextAtMoment(layoutBranch, id, moment) },
            extIds = extIds,
            locationTrackId = locationTrackId,
            filterKmNumbers = filterKmNumbers,
        )

    fun getChangedSwitchesFromChangedLocationTrackKms(
        validationVersions: ValidationVersions,
        locationTrackChange: LocationTrackChange,
    ): List<IntId<LayoutSwitch>> {
        val fetchLocationTrackById = { id: IntId<LocationTrack> ->
            getObjectFromValidationVersions(
                validationVersions.locationTracks,
                locationTrackDao,
                validationVersions.target,
                id,
            )
        }
        val fetchSwitchById = { id: IntId<LayoutSwitch> ->
            getObjectFromValidationVersions(validationVersions.switches, switchDao, validationVersions.target, id)
        }
        val getGeocodingContext = { id: IntId<LayoutTrackNumber> ->
            geocodingService
                .getGeocodingContextCacheKey(id, validationVersions)
                ?.let(geocodingService::getGeocodingContext)
        }
        val changes =
            getSwitchChangesFromChangedLocationTrackKms(
                fetchLocationTrackById,
                fetchSwitchById,
                getGeocodingContext,
                extIds = AllOids.empty(),
                locationTrackId = locationTrackChange.locationTrackId,
                filterKmNumbers = locationTrackChange.changedKmNumbers,
            )
        return changes.map { it.switchId }
    }

    private fun calculateTrackNumberChanges(
        trackNumberIds: Collection<IntId<LayoutTrackNumber>>,
        changeContext: ChangeContext,
    ): Pair<List<TrackNumberChange>, List<IntId<LocationTrack>>> {
        val (tnChanges, affectedTracks) = trackNumberIds.map { id -> getTrackNumberChange(id, changeContext) }.unzip()
        return tnChanges to affectedTracks.flatten().distinct()
    }

    private fun getTrackNumberChange(
        trackNumberId: IntId<LayoutTrackNumber>,
        changeContext: ChangeContext,
    ): Pair<TrackNumberChange, List<IntId<LocationTrack>>> {
        val beforeContext = changeContext.getGeocodingContextBefore(trackNumberId)
        val afterContext = changeContext.getGeocodingContextAfter(trackNumberId)
        val addressChanges =
            getAddressChanges(beforeContext?.referenceLineAddresses, afterContext?.referenceLineAddresses)
        val trackNumberChange =
            TrackNumberChange(
                trackNumberId = trackNumberId,
                changedKmNumbers = addressChanges.changedKmNumbers,
                isStartChanged = addressChanges.startPointChanged,
                isEndChanged = addressChanges.endPointChanged,
            )
        // Affected tracks are those that aren't directly changed, but are changed by the geocoding
        // context change
        // That is: all those that used the former one as ones that started using new context must
        // have changed anyhow
        val affectedTracks =
            beforeContext?.let { context ->
                calculateOverlappingLocationTracks(
                    geocodingContext = context,
                    kilometers = addressChanges.changedKmNumbers,
                    locationTracks =
                        changeContext
                            .getTrackNumberTracksBefore(trackNumberId)
                            .map(locationTrackService::getWithGeometry),
                )
            } ?: listOf()
        return trackNumberChange to affectedTracks
    }

    private fun calculateLocationTrackChanges(
        trackIds: Collection<IntId<LocationTrack>>,
        changeContext: ChangeContext,
    ): List<LocationTrackChange> =
        trackIds.map { trackId ->
            val trackBefore = changeContext.locationTracks.getBefore(trackId)
            val trackAfter = changeContext.locationTracks.getAfter(trackId)
            val addressChanges =
                addressChangesService.getAddressChanges(
                    beforeTrack = trackBefore,
                    afterTrack = trackAfter,
                    beforeContextKey = trackBefore?.let { t -> changeContext.geocodingKeysBefore[t.trackNumberId] },
                    afterContextKey = changeContext.geocodingKeysAfter[trackAfter.trackNumberId],
                )
            LocationTrackChange.create(trackId, addressChanges)
        }

    private fun getSwitchChangesByLocationTrack(
        trackId: IntId<LocationTrack>,
        changeContext: ChangeContext,
        extIds: AllOids,
    ): List<SwitchChange> {
        val (oldLocationTrack, oldGeometry) =
            changeContext.locationTracks.beforeVersion(trackId)?.let(locationTrackService::getWithGeometry)
                ?: (null to null)

        val (newLocationTrack, newGeometry) =
            changeContext.locationTracks.afterVersion(trackId).let(locationTrackService::getWithGeometry)

        val newTrackNumber =
            newLocationTrack.let { track -> changeContext.trackNumbers.getAfterIfExists(track.trackNumberId) }

        val oldGeocodingContext =
            oldLocationTrack?.trackNumberId?.let { tnId -> changeContext.getGeocodingContextBefore(tnId) }

        val newGeocodingContext =
            newLocationTrack.trackNumberId.let { tnId -> changeContext.getGeocodingContextAfter(tnId) }

        val oldSwitches =
            oldGeometry?.let { geometry ->
                oldGeocodingContext?.let { geocodingContext ->
                    getSwitchJointChanges(
                        geometry = geometry,
                        geocodingContext = geocodingContext,
                        fetchSwitch = changeContext.switches::getBefore,
                        fetchStructure = switchLibraryService::getSwitchStructure,
                    )
                }
            } ?: emptyList()

        val newSwitches =
            newGeocodingContext?.let { context ->
                getSwitchJointChanges(
                    geometry = newGeometry,
                    geocodingContext = context,
                    fetchSwitch = changeContext.switches::getAfterIfExists,
                    fetchStructure = switchLibraryService::getSwitchStructure,
                )
            } ?: emptyList()

        val deletedSwitches =
            findSwitchJointDifferences(oldSwitches, newSwitches) { switch, joint, _ ->
                    switch.joint.number == joint.number
                }
                .mapNotNull { switch ->
                    oldLocationTrack?.let {
                        SwitchChange(
                            switchId = switch.first,
                            changedJoints =
                                switch.second.map { changeData ->
                                    SwitchJointChange(
                                        number = changeData.joint.number,
                                        isRemoved = true,
                                        address = changeData.address,
                                        point = changeData.point.toPoint(),
                                        locationTrackId = oldLocationTrack.id as IntId,
                                        locationTrackExternalId = extIds.locationTracks[oldLocationTrack.id],
                                        trackNumberId = oldLocationTrack.trackNumberId,
                                        trackNumberExternalId = extIds.trackNumbers[oldLocationTrack.trackNumberId],
                                    )
                                },
                        )
                    }
                }

        val changedSwitches =
            findSwitchJointDifferences(newSwitches, oldSwitches) { switch, joint, address ->
                    switch.joint == joint && switch.address == address
                }
                .map { switch ->
                    SwitchChange(
                        switchId = switch.first,
                        changedJoints =
                            switch.second.map { changeData ->
                                SwitchJointChange(
                                    number = changeData.joint.number,
                                    isRemoved = false,
                                    address = changeData.address,
                                    point = changeData.point.toPoint(),
                                    locationTrackId = newLocationTrack.id as IntId,
                                    locationTrackExternalId = extIds.locationTracks[newLocationTrack.id],
                                    trackNumberId = newLocationTrack.trackNumberId,
                                    trackNumberExternalId = newTrackNumber?.id?.let { id -> extIds.trackNumbers[id] },
                                )
                            },
                    )
                }

        return (deletedSwitches + changedSwitches)
            .filter { it.changedJoints.isNotEmpty() }
            .groupBy { it.switchId }
            .values
            .flatten()
    }

    private fun getSwitchChangesByGeometryChanges(
        locationTracksChanges: Collection<LocationTrackChange>,
        changeContext: ChangeContext,
        extIds: AllOids,
    ): Pair<List<SwitchChange>, List<LocationTrackChange>> {
        val switchChanges =
            mergeSwitchChanges(
                locationTracksChanges
                    .flatMap { locationTrackChange ->
                        getSwitchChangesByLocationTrack(locationTrackChange.locationTrackId, changeContext, extIds)
                    }
                    .filter { switchChange -> switchChange.changedJoints.isNotEmpty() }
            )

        val locationTrackGeometryChanges =
            locationTracksChanges.map { locationTrackChange ->
                val locationTrackJointChanges =
                    switchChanges.flatMap { switchChange ->
                        switchChange.changedJoints
                            .filterNot { it.isRemoved }
                            .filter { cj -> cj.locationTrackId == locationTrackChange.locationTrackId }
                    }

                val locationTrack = changeContext.locationTracks.getAfter(locationTrackChange.locationTrackId)
                val geocodingCacheKey = changeContext.geocodingKeysAfter[locationTrack.trackNumberId]
                val addresses =
                    geocodingCacheKey?.let { cacheKey ->
                        geocodingService.getAddressPoints(cacheKey, locationTrack.versionOrThrow)
                    }

                LocationTrackChange(
                    locationTrackId = locationTrackChange.locationTrackId,
                    changedKmNumbers = locationTrackJointChanges.map { it.address.kmNumber }.toSet(),
                    isStartChanged =
                        locationTrackJointChanges.any { change ->
                            addresses?.startPoint?.address?.isSame(change.address) == true
                        },
                    isEndChanged =
                        locationTrackJointChanges.any { change ->
                            addresses?.endPoint?.address?.isSame(change.address) == true
                        },
                )
            }

        return switchChanges to locationTrackGeometryChanges
    }

    private fun createChangeContext(versions: ValidationVersions) =
        ChangeContext(
            geocodingService = geocodingService,
            trackNumbers = createTypedContext(versions.target.baseContext, trackNumberDao, versions.trackNumbers),
            referenceLines = createTypedContext(versions.target.baseContext, referenceLineDao, versions.referenceLines),
            kmPosts = createTypedContext(versions.target.baseContext, kmPostDao, versions.kmPosts),
            locationTracks = createTypedContext(versions.target.baseContext, locationTrackDao, versions.locationTracks),
            switches = createTypedContext(versions.target.baseContext, switchDao, versions.switches),
            geocodingKeysBefore =
                LazyMap { id: IntId<LayoutTrackNumber> ->
                    geocodingService.getGeocodingContextCacheKey(versions.target.baseContext, id)
                },
            geocodingKeysAfter =
                LazyMap { id: IntId<LayoutTrackNumber> -> geocodingService.getGeocodingContextCacheKey(id, versions) },
            getTrackNumberTracksBefore = { trackNumberId: IntId<LayoutTrackNumber> ->
                locationTrackDao.fetchVersions(versions.target.baseContext, false, trackNumberId)
            },
        )

    fun getAllOidsWithInheritance(layoutBranch: LayoutBranch) =
        AllOids(
            mapNonNullValues(trackNumberDao.fetchExternalIdsWithInheritance(layoutBranch)) { (_, v) -> v.oid },
            mapNonNullValues(locationTrackDao.fetchExternalIdsWithInheritance(layoutBranch)) { (_, v) -> v.oid },
            mapNonNullValues(switchDao.fetchExternalIdsWithInheritance(layoutBranch)) { (_, v) -> v.oid },
        )

    fun getAllOids(layoutBranch: LayoutBranch) =
        AllOids(
            mapNonNullValues(trackNumberDao.fetchExternalIds(layoutBranch)) { (_, v) -> v.oid },
            mapNonNullValues(locationTrackDao.fetchExternalIds(layoutBranch)) { (_, v) -> v.oid },
            mapNonNullValues(switchDao.fetchExternalIds(layoutBranch)) { (_, v) -> v.oid },
        )

    private fun <T : LayoutAsset<T>> getNonOverriddenVersions(
        branch: DesignBranch,
        versions: List<LayoutRowVersion<T>>,
        dao: LayoutAssetDao<T, *>,
    ): List<LayoutRowVersion<T>> {
        val overriddenIds =
            dao.getMany(branch.official, versions.map { asset -> asset.id })
                .mapNotNull { asset -> asset.takeIf { it.layoutContext.branch == branch }?.id as? IntId }
                .toSet()
        return versions.filter { version -> !overriddenIds.contains(version.id) }
    }
}

private fun asDirectSwitchChanges(switchIds: Collection<IntId<LayoutSwitch>>) =
    switchIds.map { switchId -> SwitchChange(switchId = switchId, changedJoints = emptyList()) }

private fun getSwitchJointChanges(
    geometry: LocationTrackGeometry,
    geocodingContext: GeocodingContext,
    fetchSwitch: (switchId: IntId<LayoutSwitch>) -> LayoutSwitch?,
    fetchStructure: (structureId: IntId<SwitchStructure>) -> SwitchStructure,
): List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>> {
    // TODO: GVT-2929 previously this filtered topology links out if they were not presentation joints - check vs main
    // TODO: GVT-2929 it never did the same to segment joints, so the solution might have been partial
    // TODO: (comment from main) Use presentation joint to filter joints to update because
    // TODO: (comment from main) - that is joint number that is normally used to connect tracks and switch topologically
    // TODO: (comment from main) - and Ratko may not want other joint numbers in this case
    val switchChanges =
        geometry.trackSwitchLinks.mapNotNull { link ->
            val joint = fetchSwitch(link.switchId)?.getJoint(link.jointNumber)
            val address = geocodingContext.getAddress(link.location)?.first
            if (joint != null && address != null) {
                link.switchId to SwitchJointDataHolder(joint, address, link.location.toPoint())
            } else {
                null
            }
        }
    return switchChanges.groupBy({ it.first }, { it.second }).toList()
}

private fun mergeLocationTrackChanges(vararg changeLists: Collection<LocationTrackChange>) =
    changeLists
        .flatMap { it }
        .groupBy { it.locationTrackId }
        .map { (locationTrackId, changes) ->
            val mergedKmMs = changes.flatMap(LocationTrackChange::changedKmNumbers).toSet()
            LocationTrackChange(
                locationTrackId = locationTrackId,
                changedKmNumbers = mergedKmMs,
                isStartChanged = changes.any { it.isStartChanged },
                isEndChanged = changes.any { it.isEndChanged },
            )
        }

private fun mergeSwitchChanges(vararg changeLists: Collection<SwitchChange>) =
    changeLists
        .flatMap { it }
        .groupBy { it.switchId }
        .map { (switchId, changes) ->
            val mergedJoints =
                changes.flatMap { it.changedJoints }.distinctBy { joint -> joint.number to joint.locationTrackId }
            SwitchChange(switchId = switchId, changedJoints = mergedJoints)
        }

private fun mergeTrackNumberChanges(vararg changeLists: Collection<TrackNumberChange>) =
    changeLists
        .flatMap { it }
        .groupBy { it.trackNumberId }
        .map { (trackNumberId, changes) ->
            val mergedKmMs = changes.flatMap(TrackNumberChange::changedKmNumbers).toSet()
            TrackNumberChange(
                trackNumberId = trackNumberId,
                changedKmNumbers = mergedKmMs,
                isStartChanged = changes.any { it.isStartChanged },
                isEndChanged = changes.any { it.isEndChanged },
            )
        }

private fun geometryContainsKilometer(
    geocodingContext: GeocodingContext,
    geometry: DbLocationTrackGeometry,
    kilometers: Set<KmNumber>,
): Boolean {
    val startAddress = geometry.firstSegmentStart?.let(geocodingContext::getAddress)?.first
    val endAddress = geometry.lastSegmentEnd?.let(geocodingContext::getAddress)?.first
    return if (startAddress != null && endAddress != null) {
        kilometers.any { kilometer -> kilometer in startAddress.kmNumber..endAddress.kmNumber }
    } else false
}

private fun calculateOverlappingLocationTracks(
    geocodingContext: GeocodingContext,
    kilometers: Set<KmNumber>,
    locationTracks: Collection<Pair<LocationTrack, DbLocationTrackGeometry>>,
) =
    locationTracks
        .filter { (_, alignment) -> geometryContainsKilometer(geocodingContext, alignment, kilometers) }
        .map { (locationTrack, _) -> locationTrack.id as IntId }

private data class SwitchJointDataHolder(val joint: LayoutSwitchJoint, val address: TrackMeter, val point: Point)

private fun findSwitchJointDifferences(
    list1: List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>>,
    list2: List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>>,
    compare: (switch: SwitchJointDataHolder, joint: LayoutSwitchJoint, address: TrackMeter) -> Boolean,
): List<Pair<IntId<LayoutSwitch>, List<SwitchJointDataHolder>>> {
    return list1
        .map { switch1 ->
            val switchId1 = switch1.first

            list2
                .find { (switchId2, _) -> switchId2 == switchId1 }
                ?.let { (_, switch) ->
                    switchId1 to
                        switch1.second.filterNot { (joint, address) -> switch.any { compare(it, joint, address) } }
                } ?: switch1
        }
        .filter { it.second.isNotEmpty() }
}

data class AllOids(
    val trackNumbers: Map<IntId<LayoutTrackNumber>, Oid<LayoutTrackNumber>>,
    val locationTracks: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
    val switches: Map<IntId<LayoutSwitch>, Oid<LayoutSwitch>>,
) {
    companion object {
        fun empty() = AllOids(mapOf(), mapOf(), mapOf())
    }

    fun isEmpty() = trackNumbers.isEmpty() && locationTracks.isEmpty() && switches.isEmpty()
}

private fun filterIndirectChangesByOidPresence(indirectChanges: IndirectChanges, oids: AllOids) =
    IndirectChanges(
        trackNumberChanges =
            indirectChanges.trackNumberChanges.filter { change -> oids.trackNumbers.containsKey(change.trackNumberId) },
        locationTrackChanges =
            indirectChanges.locationTrackChanges.filter { change ->
                oids.locationTracks.containsKey(change.locationTrackId)
            },
        switchChanges = indirectChanges.switchChanges.filter { change -> oids.switches.containsKey(change.switchId) },
    )

private fun mergeInheritedChangeVersionsWithCompletedMergeVersions(
    inheritedChangeVersions: ValidationVersions,
    completedTrackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
    completedReferenceLines: List<LayoutRowVersion<ReferenceLine>>,
    completedLocationTracks: List<LayoutRowVersion<LocationTrack>>,
    completedSwitches: List<LayoutRowVersion<LayoutSwitch>>,
    completedKmPosts: List<LayoutRowVersion<LayoutKmPost>>,
): ValidationVersions {
    return ValidationVersions(
        inheritedChangeVersions.target,
        trackNumbers = (completedTrackNumbers + inheritedChangeVersions.trackNumbers).distinctBy { it.id },
        locationTracks = (completedLocationTracks + inheritedChangeVersions.locationTracks).distinctBy { it.id },
        referenceLines = completedReferenceLines,
        switches = (completedSwitches + inheritedChangeVersions.switches).distinctBy { it.id },
        kmPosts = completedKmPosts,
        splits = listOf(),
    )
}

private fun <T : LayoutAsset<T>> getObjectFromValidationVersions(
    versions: List<LayoutRowVersion<T>>,
    dao: LayoutAssetDao<T, *>,
    target: ValidationTarget,
    id: IntId<T>,
): T? = (versions.find { it.id == id } ?: dao.fetchVersion(target.baseContext, id))?.let(dao::fetch)
