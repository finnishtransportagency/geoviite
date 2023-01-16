package fi.fta.geoviite.infra.integration

import ChangeContext
import LazyMap
import createTypedContext
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.PublicationVersions
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import org.springframework.stereotype.Service
import java.time.Instant


data class TrackNumberChange(
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val changedKmNumbers: Set<KmNumber> = emptySet(),
    val isStartChanged: Boolean,
    val isEndChanged: Boolean,
)

data class LocationTrackChange(
    val locationTrackId: IntId<LocationTrack>,
    val changedKmNumbers: Set<KmNumber> = emptySet(),
    val isStartChanged: Boolean,
    val isEndChanged: Boolean
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
    val trackNumberExternalId: Oid<TrackLayoutTrackNumber>?
)

data class SwitchChange(
    val switchId: IntId<TrackLayoutSwitch>,
    val changedJoints: List<SwitchJointChange>
)

data class CalculatedChanges(
    val trackNumberChanges: List<TrackNumberChange>,
    val locationTracksChanges: List<LocationTrackChange>,
    val switchChanges: List<SwitchChange>
)

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
    val kmPostService: LayoutKmPostService,
    val geocodingService: GeocodingService,
    val alignmentDao: LayoutAlignmentDao,
) {
    fun getCalculatedChangesBetween(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        locationTrackIds: List<IntId<LocationTrack>>,
        switchIds: List<IntId<TrackLayoutSwitch>>,
        startMoment: Instant,
        endMoment: Instant,
    ) = getCalculatedChanges(createChangeContext(startMoment, endMoment), trackNumberIds, locationTrackIds, switchIds)

    fun getCalculatedChangesInDraft(versions: PublicationVersions): CalculatedChanges {
        val changeContext = createChangeContext(versions)
        // KM-Post & reference line changes are seen as changes to a single whole
        val trackNumberIds = (
                versions.trackNumbers.map { v -> v.officialId }
                        + versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.draftVersion).trackNumberId }
                        + versions.referenceLines.map { v -> referenceLineDao.fetch(v.draftVersion).trackNumberId }
                ).distinct()
        val locationTrackIds = versions.locationTracks.map { v -> v.officialId }
        val switchIds = versions.switches.map { v -> v.officialId }
        return getCalculatedChanges(changeContext, trackNumberIds, locationTrackIds, switchIds)
    }

    fun getCalculatedChanges(
        changeContext: ChangeContext,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        locationTrackIds: List<IntId<LocationTrack>>,
        switchIds: List<IntId<TrackLayoutSwitch>>,
    ): CalculatedChanges {
        val (trackNumberChanges, affectedLocationTracksIds) = calculateTrackNumberChanges(trackNumberIds, changeContext)
        val combinedLocationTrackIds = (locationTrackIds+affectedLocationTracksIds).distinct()
        val locationTrackGeometryChanges = calculateLocationTrackChanges(combinedLocationTrackIds, changeContext)

        val directSwitchChanges = getDirectSwitchChanges(switchIds)

        val (switchChangesByGeometryChanges, locationTrackSwitchChanges) =
            getSwitchChangesByGeometryChanges(locationTrackGeometryChanges, changeContext)

        val switchChanges = mergeSwitchChanges(directSwitchChanges, switchChangesByGeometryChanges)
        val locationTrackChanges = mergeLocationTrackChanges(locationTrackGeometryChanges, locationTrackSwitchChanges)

        return CalculatedChanges(
            trackNumberChanges = trackNumberChanges,
            locationTracksChanges = locationTrackChanges,
            switchChanges = switchChanges,
        )
    }

    fun getAllSwitchChangesByLocationTrackChange(
        locationTrackChanges: List<LocationTrackChange>,
        moment: Instant,
    ) = locationTrackChanges.flatMap { locationTrackChange ->
        val locationTrackId = locationTrackChange.locationTrackId
        val (locationTrack, alignment) = locationTrackService.getOfficialWithAlignmentAtMoment(locationTrackId, moment)
            ?: throw NoSuchEntityException(LocationTrack::class, locationTrackId)

        val trackNumberId = locationTrack.trackNumberId
        val trackNumber = trackNumberService.getOfficialAtMoment(trackNumberId, moment)
            ?: throw NoSuchEntityException(TrackLayoutTrackNumber::class, trackNumberId)

        val currentGeocodingContext = geocodingService.getGeocodingContextAtMoment(locationTrack.trackNumberId, moment)

        val switches = currentGeocodingContext?.let { context ->
            getSwitchJointChanges(
                segments = alignment.segments,
                geocodingContext = context
            ) { switchId -> switchService.getOfficialAtMoment(switchId, moment) }
        } ?: emptyList()

        switches
            .map { (switchId, switchData) ->
                switchId to switchData.filter { locationTrackChange.changedKmNumbers.contains(it.address.kmNumber) }
            }
            .filter { it.second.isNotEmpty() }
            .map { switch ->
                SwitchChange(
                    switchId = switch.first,
                    changedJoints = switch.second.map { changeData ->
                        SwitchJointChange(
                            number = changeData.joint.number,
                            isRemoved = false,
                            address = changeData.address,
                            point = changeData.point.toPoint(),
                            locationTrackId = locationTrackId,
                            locationTrackExternalId = locationTrack.externalId,
                            trackNumberId = trackNumberId,
                            trackNumberExternalId = trackNumber.externalId
                        )
                    }
                )
            }
    }

    private fun calculateTrackNumberChanges(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        changeContext: ChangeContext,
    ): Pair<List<TrackNumberChange>, List<IntId<LocationTrack>>> {
        val (tnChanges, affectedTracks) = trackNumberIds
            .map { id -> getTrackNumberChange(id, changeContext) }
            .unzip()
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

    private fun calculateLocationTrackChanges(trackIds: List<IntId<LocationTrack>>, changeContext: ChangeContext) =
        trackIds.map { trackId ->
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

        val oldTrackNumber = oldLocationTrack?.let { track -> changeContext.trackNumbers.getBefore(track.trackNumberId) }
        val newTrackNumber = newLocationTrack.let { track -> changeContext.trackNumbers.getAfter(track.trackNumberId) }

        val oldGeocodingContext = oldLocationTrack?.trackNumberId
            ?.let { tnId -> changeContext.getGeocodingContextBefore(tnId) }
        val newGeocodingContext = newLocationTrack.trackNumberId
            .let { tnId -> changeContext.getGeocodingContextAfter(tnId) }

        val oldTopologySwitches =
            if (oldLocationTrack != null && oldAlignment != null && oldGeocodingContext != null)
                getTopologySwitchJoints(
                    oldLocationTrack,
                    oldAlignment,
                    getSwitchPresentationJoint = { switchId ->
                        val switch = changeContext.switches.getBefore(switchId)
                            ?: throw NoSuchEntityException(TrackLayoutSwitch::class, switchId)
                        switchLibraryService.getSwitchStructure(switch.switchStructureId).presentationJointNumber
                    },
                    getAddress = { point -> oldGeocodingContext.getAddress(point)?.first }
                )
            else listOf()

        val oldSwitches = (oldAlignment?.segments
            ?.let { segments ->
                oldGeocodingContext?.let { geocodingContext ->
                    getSwitchJointChanges(
                        segments = segments,
                        geocodingContext = geocodingContext
                    ) { switchId -> changeContext.switches.getBefore(switchId) }
                }
            } ?: emptyList()) +
                oldTopologySwitches

        val newTopologySwitches = if (newGeocodingContext != null)
            getTopologySwitchJoints(
                newLocationTrack,
                newAlignment,
                getSwitchPresentationJoint = { switchId ->
                    val switch = changeContext.switches.getAfter(switchId)
                    switchLibraryService.getSwitchStructure(switch.switchStructureId).presentationJointNumber
                },
                getAddress = { point -> newGeocodingContext.getAddress(point)?.first })
        else listOf()

        val newSwitches = (newGeocodingContext?.let { context ->
            getSwitchJointChanges(
                segments = newAlignment.segments,
                geocodingContext = context
            ) { switchId -> changeContext.switches.getAfter(switchId) }
        } ?: emptyList()) +
                newTopologySwitches

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
                            trackNumberExternalId = oldTrackNumber?.externalId
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
                        trackNumberExternalId = newTrackNumber.externalId
                    )
                }
            )
        }

        return (deletedSwitches + changedSwitches)
            .filter { it.changedJoints.isNotEmpty() }
            .groupBy { it.switchId }
            .values
            .flatten()
            .toList()
    }

    private fun getSwitchChangesByGeometryChanges(
        locationTracksChanges: List<LocationTrackChange>,
        changeContext: ChangeContext,
    ): Pair<List<SwitchChange>, List<LocationTrackChange>> {
        val switchChanges = locationTracksChanges.flatMap { locationTrackChange ->
            getSwitchChangesByLocationTrack(locationTrackChange.locationTrackId, changeContext)
                .filter { switchChange -> switchChange.changedJoints.isNotEmpty() }
        }

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

    fun createChangeContext(publicationVersions: PublicationVersions) = ChangeContext(
        geocodingService = geocodingService,
        trackNumbers = createTypedContext(trackNumberDao, publicationVersions.trackNumbers),
        referenceLines = createTypedContext(referenceLineDao, publicationVersions.referenceLines),
        kmPosts = createTypedContext(kmPostDao, publicationVersions.kmPosts),
        locationTracks = createTypedContext(locationTrackDao, publicationVersions.locationTracks),
        switches = createTypedContext(switchDao, publicationVersions.switches),
        geocodingKeysBefore = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, OFFICIAL)
        },
        geocodingKeysAfter = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, publicationVersions)
        },
        getTrackNumberTracksBefore = { trackNumberId: IntId<TrackLayoutTrackNumber> ->
            locationTrackDao.fetchVersions(OFFICIAL, false, trackNumberId)
        },
    )

    fun createChangeContext(before: Instant, after: Instant) = ChangeContext(
        geocodingService = geocodingService,
        trackNumbers = createTypedContext(trackNumberDao, before, after),
        referenceLines = createTypedContext(referenceLineDao, before, after),
        kmPosts = createTypedContext(kmPostDao, before, after),
        locationTracks = createTypedContext(locationTrackDao, before, after),
        switches = createTypedContext(switchDao, before, after),
        geocodingKeysBefore = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, before)
        },
        geocodingKeysAfter = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, after)
        },
        getTrackNumberTracksBefore = { id: IntId<TrackLayoutTrackNumber> ->
            locationTrackDao.fetchOfficialVersionsAtMoment(id, before)
        },
    )
}

private fun getDirectSwitchChanges(switchIds: List<IntId<TrackLayoutSwitch>>) =
    switchIds.map { switchId -> SwitchChange(switchId = switchId, changedJoints = emptyList()) }

private fun getSwitchJointChanges(
    segments: List<LayoutSegment>,
    geocodingContext: GeocodingContext,
    fetchSwitch: (switchId: IntId<TrackLayoutSwitch>) -> TrackLayoutSwitch?
): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>> {
    return segments
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
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, value) -> value.flatten() }
        .toList()
}

private fun getTopologySwitchJoints(
    locationTrack: LocationTrack,
    alignment: LayoutAlignment,
    getSwitchPresentationJoint: (switchId: IntId<TrackLayoutSwitch>) -> JointNumber,
    getAddress: (point: IPoint) -> TrackMeter?,
): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>> {
    return topologySwitchLinks(locationTrack, alignment).mapNotNull { (topologySwitch, location) ->
        val address = getAddress(location)
        val isPresentationJoint = getSwitchPresentationJoint(topologySwitch.switchId) == topologySwitch.jointNumber
        if (address != null && isPresentationJoint) getTopologySwitchJoints(topologySwitch, location, address)
        else null
    }
}

private fun topologySwitchLinks(track: LocationTrack, alignment: LayoutAlignment) = listOfNotNull(
    switchIdAndLocation(track.topologyStartSwitch, alignment.start),
    switchIdAndLocation(track.topologyEndSwitch, alignment.end),
)

private fun switchIdAndLocation(topologySwitch: TopologyLocationTrackSwitch?, location: LayoutPoint?) =
    if (topologySwitch != null && location != null) topologySwitch to location.toPoint()
    else null

private fun getTopologySwitchJoints(
    topologySwitch: TopologyLocationTrackSwitch,
    location: Point,
    address: TrackMeter
): Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>> {
    return topologySwitch.switchId to listOf(
        SwitchJointDataHolder(
            joint = TrackLayoutSwitchJoint(
                number = topologySwitch.jointNumber,
                location = location,
                locationAccuracy = null
            ),
            point = location,
            address = address
        )
    )
}

private fun mergeLocationTrackChanges(
    vararg changeLists: List<LocationTrackChange>,
): List<LocationTrackChange> {
    return changeLists
        .flatMap { it }
        .groupBy { it.locationTrackId }
        .map { (locationTrackId, changes) ->
            val mergedKmMs = changes.flatMap(LocationTrackChange::changedKmNumbers).toSet()
            LocationTrackChange(
                locationTrackId = locationTrackId,
                changedKmNumbers = mergedKmMs,
                isStartChanged = changes.any { it.isStartChanged },
                isEndChanged = changes.any { it.isEndChanged }
            )
        }
}

fun mergeSwitchChanges(
    vararg changeLists: List<SwitchChange>,
): List<SwitchChange> {
    return changeLists
        .flatMap { it }
        .groupBy { it.switchId }
        .map { (switchId, changes) ->
            val mergedJoints = changes.flatMap(SwitchChange::changedJoints).distinct()
            SwitchChange(
                switchId = switchId,
                changedJoints = mergedJoints
            )
        }
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
    locationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): List<IntId<LocationTrack>> = locationTracks
    .filter { (_, alignment) -> alignmentContainsKilometer(geocodingContext, alignment, kilometers) }
    .map { (locationTrack, _) -> locationTrack.id as IntId<LocationTrack> }

private fun findMatchingJoints(
    segment: LayoutSegment,
    switch: TrackLayoutSwitch,
    geocodingContext: GeocodingContext,
) = switch.joints.mapNotNull { joint ->
    val segmentPoint = when (joint.number) {
        segment.startJointNumber -> segment.points.first()
        segment.endJointNumber -> segment.points.last()
        else -> null
    }

    if (segmentPoint == null) null
    else geocodingContext.getAddress(segmentPoint)?.first?.let { address ->
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
    val point: IPoint
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
                }
                ?: switch1
        }
        .filter { it.second.isNotEmpty() }
}
