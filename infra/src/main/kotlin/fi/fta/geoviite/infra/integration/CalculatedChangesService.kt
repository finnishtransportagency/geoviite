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
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.InheritanceFromPublicationInMain
import fi.fta.geoviite.infra.publication.ValidateTransition
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutAssetDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import java.time.Instant
import org.springframework.transaction.annotation.Transactional

data class TrackNumberChange(
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
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
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val trackNumberExternalId: Oid<TrackLayoutTrackNumber>?,
)

data class SwitchChange(val switchId: IntId<TrackLayoutSwitch>, val changedJoints: List<SwitchJointChange>)

data class DirectChanges(
    val kmPostChanges: List<IntId<TrackLayoutKmPost>>,
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
) {
    fun getCalculatedChanges(versions: ValidationVersions): CalculatedChanges {
        val changeContext = createChangeContext(versions)
        val extIds = getAllOids(versions.target.baseBranch)

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
        trackNumbers: List<LayoutRowVersion<TrackLayoutTrackNumber>>,
        referenceLines: List<LayoutRowVersion<ReferenceLine>>,
        locationTracks: List<LayoutRowVersion<LocationTrack>>,
        switches: List<LayoutRowVersion<TrackLayoutSwitch>>,
        kmPosts: List<LayoutRowVersion<TrackLayoutKmPost>>,
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

    fun getAllSwitchChangesByLocationTrackAtMoment(
        layoutBranch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        moment: Instant,
        extIds: AllOids,
    ): List<SwitchChange> {
        val (locationTrack, alignment) =
            locationTrackService.getOfficialWithAlignmentAtMoment(layoutBranch, locationTrackId, moment)
                ?: throw NoSuchEntityException(LocationTrack::class, locationTrackId)

        val trackNumberId = locationTrack.trackNumberId
        val trackNumber =
            trackNumberService.getOfficialAtMoment(layoutBranch, trackNumberId, moment)
                ?: throw NoSuchEntityException(TrackLayoutTrackNumber::class, trackNumberId)

        val currentGeocodingContext = geocodingService.getGeocodingContextAtMoment(layoutBranch, trackNumberId, moment)

        val switches =
            currentGeocodingContext?.let { context ->
                getSwitchJointChanges(
                    locationTrack = locationTrack,
                    alignment = alignment,
                    geocodingContext = context,
                    fetchSwitch = { switchId -> switchService.getOfficialAtMoment(layoutBranch, switchId, moment) },
                    fetchStructure = switchLibraryService::getSwitchStructure,
                )
            } ?: emptyList()

        return switches.map { (switch, changeData) ->
            SwitchChange(
                switchId = switch,
                changedJoints =
                    changeData.map { change ->
                        SwitchJointChange(
                            number = change.joint.number,
                            isRemoved = false,
                            address = change.address,
                            point = change.point.toPoint(),
                            locationTrackId = locationTrackId,
                            locationTrackExternalId = extIds.locationTracks[locationTrack.id],
                            trackNumberId = trackNumberId,
                            trackNumberExternalId = extIds.trackNumbers[trackNumber.id],
                        )
                    },
            )
        }
    }

    private fun calculateTrackNumberChanges(
        trackNumberIds: Collection<IntId<TrackLayoutTrackNumber>>,
        changeContext: ChangeContext,
    ): Pair<List<TrackNumberChange>, List<IntId<LocationTrack>>> {
        val (tnChanges, affectedTracks) = trackNumberIds.map { id -> getTrackNumberChange(id, changeContext) }.unzip()
        return tnChanges to affectedTracks.flatten().distinct()
    }

    private fun getTrackNumberChange(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
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
                            .map(locationTrackService::getWithAlignment),
                )
            } ?: listOf()
        return trackNumberChange to affectedTracks
    }

    private fun calculateLocationTrackChanges(
        trackIds: Collection<IntId<LocationTrack>>,
        changeContext: ChangeContext,
    ) =
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
        val (oldLocationTrack, oldAlignment) =
            changeContext.locationTracks.beforeVersion(trackId)?.let(locationTrackService::getWithAlignment)
                ?: (null to null)

        val (newLocationTrack, newAlignment) =
            changeContext.locationTracks.afterVersion(trackId).let(locationTrackService::getWithAlignment)

        val newTrackNumber =
            newLocationTrack.let { track -> changeContext.trackNumbers.getAfterIfExists(track.trackNumberId) }

        val oldGeocodingContext =
            oldLocationTrack?.trackNumberId?.let { tnId -> changeContext.getGeocodingContextBefore(tnId) }

        val newGeocodingContext =
            newLocationTrack.trackNumberId.let { tnId -> changeContext.getGeocodingContextAfter(tnId) }

        val oldSwitches =
            oldAlignment?.let { alignment ->
                oldGeocodingContext?.let { geocodingContext ->
                    getSwitchJointChanges(
                        locationTrack = oldLocationTrack,
                        alignment = alignment,
                        geocodingContext = geocodingContext,
                        fetchSwitch = changeContext.switches::getBefore,
                        fetchStructure = switchLibraryService::getSwitchStructure,
                    )
                }
            } ?: emptyList()

        val newSwitches =
            newGeocodingContext?.let { context ->
                getSwitchJointChanges(
                    locationTrack = newLocationTrack,
                    alignment = newAlignment,
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
                        geocodingService.getAddressPoints(cacheKey, locationTrack.getAlignmentVersionOrThrow())
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
                LazyMap { id: IntId<TrackLayoutTrackNumber> ->
                    geocodingService.getGeocodingContextCacheKey(versions.target.baseContext, id)
                },
            geocodingKeysAfter =
                LazyMap { id: IntId<TrackLayoutTrackNumber> ->
                    geocodingService.getGeocodingContextCacheKey(id, versions)
                },
            getTrackNumberTracksBefore = { trackNumberId: IntId<TrackLayoutTrackNumber> ->
                locationTrackDao.fetchVersions(versions.target.baseContext, false, trackNumberId)
            },
        )

    fun getAllOids(layoutBranch: LayoutBranch) =
        AllOids(
            trackNumberDao.fetchExternalIds(layoutBranch),
            locationTrackDao.fetchExternalIds(layoutBranch),
            switchDao.fetchExternalIds(layoutBranch),
        )

    private fun <T : LayoutAsset<T>> getNonOverriddenVersions(
        branch: DesignBranch,
        versions: List<LayoutRowVersion<T>>,
        dao: LayoutAssetDao<T>,
    ): List<LayoutRowVersion<T>> {
        val overriddenIds =
            dao.getMany(branch.official, versions.map { asset -> asset.id })
                .mapNotNull { asset -> asset.takeIf { it.layoutContext.branch == branch }?.id as? IntId }
                .toSet()
        return versions.filter { version -> !overriddenIds.contains(version.id) }
    }
}

private fun asDirectSwitchChanges(switchIds: Collection<IntId<TrackLayoutSwitch>>) =
    switchIds.map { switchId -> SwitchChange(switchId = switchId, changedJoints = emptyList()) }

private fun getSwitchJointChanges(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    geocodingContext: GeocodingContext,
    fetchSwitch: (switchId: IntId<TrackLayoutSwitch>) -> TrackLayoutSwitch?,
    fetchStructure: (structureId: IntId<SwitchStructure>) -> SwitchStructure,
): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>> {
    val switchChanges =
        alignment.segments
            .asSequence()
            .mapNotNull { segment -> if (segment.switchId is IntId) segment to fetchSwitch(segment.switchId) else null }
            .mapNotNull { (segment, switch) ->
                if (switch == null) null
                else
                    (switch.id as IntId) to
                        findMatchingJoints(segment = segment, switch = switch, geocodingContext = geocodingContext)
            }
            .toList()

    val topologySwitches =
        topologySwitchLinks(locationTrack, alignment).mapNotNull { (topologySwitch, location) ->
            getTopologySwitchJointDataHolder(topologySwitch, location, geocodingContext, fetchSwitch, fetchStructure)
        }

    return (switchChanges + topologySwitches)
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, value) -> value.flatten() }
        .toList()
}

private fun getTopologySwitchJointDataHolder(
    topologySwitch: TopologyLocationTrackSwitch,
    point: IPoint,
    geocodingContext: GeocodingContext,
    fetchSwitch: (switchId: IntId<TrackLayoutSwitch>) -> TrackLayoutSwitch?,
    fetchStructure: (structureId: IntId<SwitchStructure>) -> SwitchStructure,
): Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>? {
    val switch = fetchSwitch(topologySwitch.switchId) ?: return null
    // Use presentation joint to filter joints to update because
    // - that is joint number that is normally used to connect tracks and switch topologically
    // - and Ratko may not want other joint numbers in this case
    val presentationJointNumber = fetchStructure(switch.switchStructureId).presentationJointNumber
    val address = geocodingContext.getAddress(point)?.first
    val joint =
        requireNotNull(switch.getJoint(topologySwitch.jointNumber)) {
            "Topology switch contains invalid joint number: $topologySwitch"
        }
    return if (presentationJointNumber == joint.number && address != null) {
        topologySwitch.switchId to listOf(SwitchJointDataHolder(address = address, point = point, joint = joint))
    } else null
}

private fun topologySwitchLinks(track: LocationTrack, alignment: LayoutAlignment) =
    listOfNotNull(
        switchIdAndLocation(track.topologyStartSwitch, alignment.firstSegmentStart),
        switchIdAndLocation(track.topologyEndSwitch, alignment.lastSegmentEnd),
    )

private fun switchIdAndLocation(topologySwitch: TopologyLocationTrackSwitch?, location: SegmentPoint?) =
    if (topologySwitch != null && location != null) topologySwitch to location.toPoint() else null

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

private fun alignmentContainsKilometer(
    geocodingContext: GeocodingContext,
    alignment: LayoutAlignment,
    kilometers: Set<KmNumber>,
): Boolean {
    val startAddress = alignment.start?.let(geocodingContext::getAddress)?.first
    val endAddress = alignment.end?.let(geocodingContext::getAddress)?.first
    return if (startAddress != null && endAddress != null) {
        kilometers.any { kilometer -> kilometer in startAddress.kmNumber..endAddress.kmNumber }
    } else false
}

private fun calculateOverlappingLocationTracks(
    geocodingContext: GeocodingContext,
    kilometers: Set<KmNumber>,
    locationTracks: Collection<Pair<LocationTrack, LayoutAlignment>>,
) =
    locationTracks
        .filter { (_, alignment) -> alignmentContainsKilometer(geocodingContext, alignment, kilometers) }
        .map { (locationTrack, _) -> locationTrack.id as IntId }

private fun findMatchingJoints(segment: LayoutSegment, switch: TrackLayoutSwitch, geocodingContext: GeocodingContext) =
    switch.joints.mapNotNull { joint ->
        val segmentPoint =
            when (joint.number) {
                segment.startJointNumber -> segment.alignmentStart
                segment.endJointNumber -> segment.alignmentEnd
                else -> null
            }

        if (segmentPoint == null) null
        else
            geocodingContext.getAddress(segmentPoint)?.let { (address) ->
                SwitchJointDataHolder(address = address, point = segmentPoint, joint = joint)
            }
    }

private data class SwitchJointDataHolder(val joint: TrackLayoutSwitchJoint, val address: TrackMeter, val point: IPoint)

private fun findSwitchJointDifferences(
    list1: List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>>,
    list2: List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>>,
    compare: (switch: SwitchJointDataHolder, joint: TrackLayoutSwitchJoint, address: TrackMeter) -> Boolean,
): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>> {
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
    val trackNumbers: Map<IntId<TrackLayoutTrackNumber>, Oid<TrackLayoutTrackNumber>>,
    val locationTracks: Map<IntId<LocationTrack>, Oid<LocationTrack>>,
    val switches: Map<IntId<TrackLayoutSwitch>, Oid<TrackLayoutSwitch>>,
) {
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
