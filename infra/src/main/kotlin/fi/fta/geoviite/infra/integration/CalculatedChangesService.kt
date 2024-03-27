package fi.fta.geoviite.infra.integration

import ChangeContext
import LazyMap
import createTypedContext
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import org.springframework.stereotype.Service
import java.time.Instant

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
        fun create(
            locationTrackId: IntId<LocationTrack>,
            addressChanges: AddressChanges?,
        ) = LocationTrackChange(
            locationTrackId = locationTrackId,
            changedKmNumbers = addressChanges?.changedKmNumbers ?: emptySet(),
            isStartChanged = addressChanges?.startPointChanged ?: true,
            isEndChanged = addressChanges?.endPointChanged ?: true
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

data class SwitchChange(
    val switchId: IntId<TrackLayoutSwitch>,
    val changedJoints: List<SwitchJointChange>,
)

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
)

data class CalculatedChanges(
    val directChanges: DirectChanges,
    val indirectChanges: IndirectChanges,
) {
    init {
        checkDuplicates(
            directChanges.kmPostChanges,
            { it },
            "Duplicate km posts in direct changes, directChanges=${directChanges.kmPostChanges}"
        )

        checkDuplicates(
            directChanges.referenceLineChanges,
            { it },
            "Duplicate reference lines in direct changes, directChanges=${directChanges.referenceLineChanges}"
        )

        val trackNumberChanges = (directChanges.trackNumberChanges + indirectChanges.trackNumberChanges)
        checkDuplicates(
            trackNumberChanges,
            { it.trackNumberId },
            "Duplicate track numbers in direct and indirect changes, directChanges=${directChanges.trackNumberChanges} indirectChanges=${indirectChanges.trackNumberChanges}"
        )

        val locationTrackChanges = (directChanges.locationTrackChanges + indirectChanges.locationTrackChanges)
        checkDuplicates(
            locationTrackChanges,
            { it.locationTrackId },
            "Duplicate location tracks in direct and indirect changes, directChanges=${directChanges.locationTrackChanges} indirectChanges=${indirectChanges.locationTrackChanges}"
        )

        val switchChanges = (directChanges.switchChanges + indirectChanges.switchChanges)
        checkDuplicates(
            switchChanges,
            { it.switchId },
            "Duplicate switches in direct and indirect changes, directChanges=${directChanges.switchChanges} indirectChanges=${indirectChanges.switchChanges}"
        )
    }

    private fun <T, R> checkDuplicates(changes: Collection<T>, groupingBy: (v: T) -> R, message: String) {
        check(changes.groupingBy(groupingBy).eachCount().all { it.value == 1 }) { message }
    }
}

@Service
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

        val (trackNumberChanges, changedLocationTrackIdsByTrackNumbers) = calculateTrackNumberChanges(
            versions.trackNumbers.map { it.officialId },
            changeContext,
        )

        val kPRLTrackNumberIds = (versions.kmPosts.mapNotNull { v ->
            kmPostDao.fetch(v.validatedAssetVersion).trackNumberId
        } + versions.referenceLines.map { v ->
            referenceLineDao.fetch(v.validatedAssetVersion).trackNumberId
        }).distinct()

        val (kPRLTrackNumberChanges, changedLocationTrackIdsByKPRL) = calculateTrackNumberChanges(
            kPRLTrackNumberIds,
            changeContext,
        )

        val directLocationTrackChanges = calculateLocationTrackChanges(
            versions.locationTracks.map { v -> v.officialId },
            changeContext,
        )

        val locationTrackChangesByTrackNumbers = calculateLocationTrackChanges(
            (changedLocationTrackIdsByTrackNumbers + changedLocationTrackIdsByKPRL).distinct(),
            changeContext,
        )

        val directSwitchChanges = asDirectSwitchChanges(versions.switches.map { v -> v.officialId })

        val (switchChangesByLocationTracks, locationTrackChangesBySwitches) = getSwitchChangesByGeometryChanges(
            mergeLocationTrackChanges(directLocationTrackChanges, locationTrackChangesByTrackNumbers),
            changeContext,
        )

        val (indirectDirectTrackNumberChanges, indirectTrackNumberChanges) = kPRLTrackNumberChanges.partition { indirectChange ->
            trackNumberChanges.any { indirectChange.trackNumberId == it.trackNumberId }
        }

        val (indirectDirectLocationTrackChanges, indirectLocationTrackChanges) = mergeLocationTrackChanges(
            locationTrackChangesByTrackNumbers,
            locationTrackChangesBySwitches,
        ).partition { indirectChange ->
            directLocationTrackChanges.any { indirectChange.locationTrackId == it.locationTrackId }
        }

        val (indirectDirectSwitchChanges, indirectSwitchChanges) = switchChangesByLocationTracks.partition { indirectChange ->
            directSwitchChanges.any { indirectChange.switchId == it.switchId }
        }

        return CalculatedChanges(
            directChanges = DirectChanges(
                kmPostChanges = versions.kmPosts.map { it.officialId },
                referenceLineChanges = versions.referenceLines.map { it.officialId },
                trackNumberChanges = mergeTrackNumberChanges(trackNumberChanges, indirectDirectTrackNumberChanges),
                locationTrackChanges = mergeLocationTrackChanges(
                    directLocationTrackChanges,
                    indirectDirectLocationTrackChanges,
                ),
                switchChanges = mergeSwitchChanges(directSwitchChanges, indirectDirectSwitchChanges),
            ),
            indirectChanges = IndirectChanges(
                locationTrackChanges = indirectLocationTrackChanges,
                switchChanges = indirectSwitchChanges,
                trackNumberChanges = indirectTrackNumberChanges,
            )
        )
    }

    fun getAllSwitchChangesByLocationTrackAtMoment(
        locationTrackId: IntId<LocationTrack>,
        moment: Instant,
    ): List<SwitchChange> {
        val (locationTrack, alignment) = locationTrackService.getOfficialWithAlignmentAtMoment(locationTrackId, moment)
            ?: throw NoSuchEntityException(LocationTrack::class, locationTrackId)

        val trackNumberId = locationTrack.trackNumberId
        val trackNumber = trackNumberService.getOfficialAtMoment(trackNumberId, moment)
            ?: throw NoSuchEntityException(TrackLayoutTrackNumber::class, trackNumberId)

        val currentGeocodingContext = geocodingService.getGeocodingContextAtMoment(trackNumberId, moment)

        val switches = currentGeocodingContext?.let { context ->
            getSwitchJointChanges(
                locationTrack = locationTrack,
                alignment = alignment,
                geocodingContext = context,
                fetchSwitch = { switchId -> switchService.getOfficialAtMoment(switchId, moment) },
                fetchStructure = switchLibraryService::getSwitchStructure,
            )
        } ?: emptyList()

        return switches.map { (switch, changeData) ->
            SwitchChange(
                switchId = switch,
                changedJoints = changeData.map { change ->
                    SwitchJointChange(
                        number = change.joint.number,
                        isRemoved = false,
                        address = change.address,
                        point = change.point.toPoint(),
                        locationTrackId = locationTrackId,
                        locationTrackExternalId = locationTrack.externalId,
                        trackNumberId = trackNumberId,
                        trackNumberExternalId = trackNumber.externalId,
                    )
                }
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
        val addressChanges = getAddressChanges(
            beforeContext?.referenceLineAddresses,
            afterContext?.referenceLineAddresses,
        )
        val trackNumberChange = TrackNumberChange(
            trackNumberId = trackNumberId,
            changedKmNumbers = addressChanges.changedKmNumbers,
            isStartChanged = addressChanges.startPointChanged,
            isEndChanged = addressChanges.endPointChanged,
        )
        // Affected tracks are those that aren't directly changed, but are changed by the geocoding context change
        // That is: all those that used the former one as ones that started using new context must have changed anyhow
        val affectedTracks = beforeContext?.let { context ->
            calculateOverlappingLocationTracks(
                geocodingContext = context,
                kilometers = addressChanges.changedKmNumbers,
                locationTracks = changeContext.getTrackNumberTracksBefore(trackNumberId)
                    .map(locationTrackService::getWithAlignment),
            )
        } ?: listOf()
        return trackNumberChange to affectedTracks
    }

    private fun calculateLocationTrackChanges(
        trackIds: Collection<IntId<LocationTrack>>,
        changeContext: ChangeContext,
    ) = trackIds.map { trackId ->
        val trackBefore = changeContext.locationTracks.getBefore(trackId)
        val trackAfter = changeContext.locationTracks.getAfter(trackId)
        val addressChanges = addressChangesService.getAddressChanges(
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
    ): List<SwitchChange> {
        val (oldLocationTrack, oldAlignment) = changeContext.locationTracks.beforeVersion(trackId)
            ?.let(locationTrackService::getWithAlignment) ?: (null to null)

        val (newLocationTrack, newAlignment) = changeContext.locationTracks.afterVersion(trackId)
            .let(locationTrackService::getWithAlignment)

        val oldTrackNumber = oldLocationTrack?.let { track ->
            changeContext.trackNumbers.getBefore(track.trackNumberId)
        }

        val newTrackNumber = newLocationTrack.let { track ->
            changeContext.trackNumbers.getAfterIfExists(track.trackNumberId)
        }

        val oldGeocodingContext = oldLocationTrack?.trackNumberId?.let { tnId ->
            changeContext.getGeocodingContextBefore(tnId)
        }

        val newGeocodingContext = newLocationTrack.trackNumberId.let { tnId ->
            changeContext.getGeocodingContextAfter(tnId)
        }

        val oldSwitches = oldAlignment?.let { alignment ->
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

        val newSwitches = newGeocodingContext?.let { context ->
            getSwitchJointChanges(
                locationTrack = newLocationTrack,
                alignment = newAlignment,
                geocodingContext = context,
                fetchSwitch = changeContext.switches::getAfterIfExists,
                fetchStructure = switchLibraryService::getSwitchStructure,
            )
        } ?: emptyList()

        val deletedSwitches = findSwitchJointDifferences(oldSwitches, newSwitches) { switch, joint, _ ->
            switch.joint.number == joint.number
        }.mapNotNull { switch ->
            oldLocationTrack?.let {
                SwitchChange(
                    switchId = switch.first,
                    changedJoints = switch.second.map { changeData ->
                        SwitchJointChange(
                            number = changeData.joint.number,
                            isRemoved = true,
                            address = changeData.address,
                            point = changeData.point.toPoint(),
                            locationTrackId = oldLocationTrack.id as IntId,
                            locationTrackExternalId = oldLocationTrack.externalId,
                            trackNumberId = oldLocationTrack.trackNumberId,
                            trackNumberExternalId = oldTrackNumber?.externalId,
                        )
                    }
                )
            }
        }

        val changedSwitches = findSwitchJointDifferences(newSwitches, oldSwitches) { switch, joint, address ->
            switch.joint == joint && switch.address == address
        }.map { switch ->
            SwitchChange(
                switchId = switch.first,
                changedJoints = switch.second.map { changeData ->
                    SwitchJointChange(
                        number = changeData.joint.number,
                        isRemoved = false,
                        address = changeData.address,
                        point = changeData.point.toPoint(),
                        locationTrackId = newLocationTrack.id as IntId,
                        locationTrackExternalId = newLocationTrack.externalId,
                        trackNumberId = newLocationTrack.trackNumberId,
                        trackNumberExternalId = newTrackNumber?.externalId,
                    )
                }
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
    ): Pair<List<SwitchChange>, List<LocationTrackChange>> {
        val switchChanges = mergeSwitchChanges(
            locationTracksChanges
                .flatMap { locationTrackChange ->
                    getSwitchChangesByLocationTrack(locationTrackChange.locationTrackId, changeContext)
                }.filter { switchChange ->
                    switchChange.changedJoints.isNotEmpty()
                }
        )

        val locationTrackGeometryChanges = locationTracksChanges.map { locationTrackChange ->
            val locationTrackJointChanges = switchChanges.flatMap { switchChange ->
                switchChange.changedJoints
                    .filterNot { it.isRemoved }
                    .filter { cj -> cj.locationTrackId == locationTrackChange.locationTrackId }
            }

            val locationTrack = changeContext.locationTracks.getAfter(locationTrackChange.locationTrackId)
            val geocodingCacheKey = changeContext.geocodingKeysAfter[locationTrack.trackNumberId]
            val addresses = geocodingCacheKey?.let { cacheKey ->
                geocodingService.getAddressPoints(cacheKey, locationTrack.getAlignmentVersionOrThrow())
            }

            LocationTrackChange(
                locationTrackId = locationTrackChange.locationTrackId,
                changedKmNumbers = locationTrackJointChanges.map { it.address.kmNumber }.toSet(),
                isStartChanged = locationTrackJointChanges.any { change ->
                    addresses?.startPoint?.address?.isSame(change.address) == true
                },
                isEndChanged = locationTrackJointChanges.any { change ->
                    addresses?.endPoint?.address?.isSame(change.address) == true
                },
            )
        }

        return switchChanges to locationTrackGeometryChanges
    }

    private fun createChangeContext(versions: ValidationVersions) = ChangeContext(
        geocodingService = geocodingService,
        trackNumbers = createTypedContext(trackNumberDao, versions.trackNumbers),
        referenceLines = createTypedContext(referenceLineDao, versions.referenceLines),
        kmPosts = createTypedContext(kmPostDao, versions.kmPosts),
        locationTracks = createTypedContext(locationTrackDao, versions.locationTracks),
        switches = createTypedContext(switchDao, versions.switches),
        geocodingKeysBefore = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, OFFICIAL)
        },
        geocodingKeysAfter = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, versions)
        },
        getTrackNumberTracksBefore = { trackNumberId: IntId<TrackLayoutTrackNumber> ->
            locationTrackDao.fetchVersions(OFFICIAL, false, trackNumberId)
        },
    )
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
    val switchChanges = alignment.segments
        .asSequence()
        .mapNotNull { segment ->
            if (segment.switchId is IntId) segment to fetchSwitch(segment.switchId)
            else null
        }
        .mapNotNull { (segment, switch) ->
            if (switch == null) null
            else (switch.id as IntId) to findMatchingJoints(
                segment = segment,
                switch = switch,
                geocodingContext = geocodingContext,
            )
        }
        .toList()

    val topologySwitches = topologySwitchLinks(locationTrack, alignment).mapNotNull { (topologySwitch, location) ->
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
    val switch = fetchSwitch(topologySwitch.switchId)
        ?: return null
    // Use presentation joint to filter joints to update because
    // - that is joint number that is normally used to connect tracks and switch topologically
    // - and Ratko may not want other joint numbers in this case
    val presentationJointNumber = fetchStructure(switch.switchStructureId).presentationJointNumber
    val address = geocodingContext.getAddress(point)?.first
    val joint = requireNotNull(switch.getJoint(topologySwitch.jointNumber)) {
        "Topology switch contains invalid joint number: $topologySwitch"
    }
    return if (presentationJointNumber == joint.number && address != null) {
        topologySwitch.switchId to listOf(SwitchJointDataHolder(address = address, point = point, joint = joint))
    } else null
}

private fun topologySwitchLinks(track: LocationTrack, alignment: LayoutAlignment) = listOfNotNull(
    switchIdAndLocation(track.topologyStartSwitch, alignment.firstSegmentStart),
    switchIdAndLocation(track.topologyEndSwitch, alignment.lastSegmentEnd),
)

private fun switchIdAndLocation(topologySwitch: TopologyLocationTrackSwitch?, location: SegmentPoint?) =
    if (topologySwitch != null && location != null) topologySwitch to location.toPoint()
    else null

private fun mergeLocationTrackChanges(
    vararg changeLists: Collection<LocationTrackChange>,
) = changeLists
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

private fun mergeSwitchChanges(
    vararg changeLists: Collection<SwitchChange>,
) = changeLists
    .flatMap { it }
    .groupBy { it.switchId }
    .map { (switchId, changes) ->
        val mergedJoints = changes.flatMap { it.changedJoints }.distinctBy { joint -> joint.number to joint.locationTrackId }
        SwitchChange(switchId = switchId, changedJoints = mergedJoints)
    }

private fun mergeTrackNumberChanges(
    vararg changeLists: Collection<TrackNumberChange>,
) = changeLists
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
) = locationTracks
    .filter { (_, alignment) -> alignmentContainsKilometer(geocodingContext, alignment, kilometers) }
    .map { (locationTrack, _) -> locationTrack.id as IntId }

private fun findMatchingJoints(
    segment: LayoutSegment,
    switch: TrackLayoutSwitch,
    geocodingContext: GeocodingContext,
) = switch.joints.mapNotNull { joint ->
    val segmentPoint = when (joint.number) {
        segment.startJointNumber -> segment.alignmentStart
        segment.endJointNumber -> segment.alignmentEnd
        else -> null
    }

    if (segmentPoint == null) null
    else geocodingContext.getAddress(segmentPoint)?.let { (address) ->
        SwitchJointDataHolder(
            address = address,
            point = segmentPoint,
            joint = joint,
        )
    }
}

private data class SwitchJointDataHolder(
    val joint: TrackLayoutSwitchJoint,
    val address: TrackMeter,
    val point: IPoint,
)

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
                    switchId1 to switch1.second.filterNot { (joint, address) ->
                        switch.any { compare(it, joint, address) }
                    }
                } ?: switch1
        }.filter { it.second.isNotEmpty() }
}
