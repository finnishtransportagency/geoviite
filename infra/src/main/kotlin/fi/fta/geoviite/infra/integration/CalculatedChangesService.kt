package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
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
    val locationTrackService: LocationTrackService,
    val switchService: LayoutSwitchService,
    val switchLibraryService: SwitchLibraryService,
    val trackNumberService: LayoutTrackNumberService,
    val referenceLineService: ReferenceLineService,
    val kmPostService: LayoutKmPostService,
    val historyDao: TrackLayoutHistoryDao,
    val geocodingService: GeocodingService,
) {
    fun getCalculatedChangesSince(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        locationTrackIds: List<IntId<LocationTrack>>,
        switchIds: List<IntId<TrackLayoutSwitch>>,
        moment: Instant
    ): CalculatedChanges {
        val (trackNumberChanges, affectedLocationTracksIds) = calculateTrackNumberChangesSinceMoment(
            trackNumberIds,
            moment,
        )
        val locationTrackGeometryChanges = calculateLocationTrackChangesSinceMoment(
            (locationTrackIds + affectedLocationTracksIds.flatten()).distinct(),
            moment,
        )

        val directSwitchChanges = getDirectSwitchChanges(switchIds)

        val (switchChangesByGeometryChanges, locationTrackSwitchChanges) = getSwitchChangesByGeometryChanges(
            locationTracksChanges = locationTrackGeometryChanges,
            moment = moment,
        )

        val switchChanges = mergeSwitchChanges(directSwitchChanges, switchChangesByGeometryChanges)
        val locationTrackChanges = mergeLocationTrackChanges(locationTrackGeometryChanges, locationTrackSwitchChanges)

        return CalculatedChanges(
            trackNumberChanges = trackNumberChanges,
            locationTracksChanges = locationTrackChanges,
            switchChanges = switchChanges,
        )
    }

    fun getCalculatedChangesInDraft(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        referenceLineIds: List<IntId<ReferenceLine>>,
        kmPostIds: List<IntId<TrackLayoutKmPost>>,
        locationTrackIds: List<IntId<LocationTrack>>,
        switchIds: List<IntId<TrackLayoutSwitch>>,
    ): CalculatedChanges {
        val allTrackNumberIds = (
                trackNumberIds
                        + kmPostIds.map { id -> kmPostService.getDraft(id).trackNumberId as IntId }
                        + referenceLineIds.map { id -> referenceLineService.getDraft(id).trackNumberId }
                ).distinct()

        val (trackNumberChanges, affectedLocationTracksIds) = calculateTrackNumberChangesInDraft(allTrackNumberIds)
        val locationTrackGeometryChanges = calculateLocationTrackChangesInDraft(
            (locationTrackIds + affectedLocationTracksIds.flatten()).distinct(),
        )

        val directSwitchChanges = getDirectSwitchChanges(switchIds)

        val (switchChangesByGeometryChanges, locationTrackSwitchChanges) = getSwitchChangesByGeometryChanges(
            locationTracksChanges = locationTrackGeometryChanges,
            moment = null
        )

        val switchChanges = mergeSwitchChanges(directSwitchChanges, switchChangesByGeometryChanges)
        val locationTrackChanges = mergeLocationTrackChanges(locationTrackGeometryChanges, locationTrackSwitchChanges)

        return CalculatedChanges(
            trackNumberChanges = trackNumberChanges,
            locationTracksChanges = locationTrackChanges,
            switchChanges = switchChanges,
        )
    }

    private fun calculateTrackNumberChangesSinceMoment(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        moment: Instant
    ): Pair<List<TrackNumberChange>, List<List<IntId<LocationTrack>>>> {
        val (tnChanges, affectedTracks) = trackNumberIds.map { trackNumberId ->
            getTrackNumberChange(
                trackNumberId,
                addressChangesService.getGeocodingContextAtMoment(trackNumberId, moment),
                addressChangesService.getGeocodingContextAtMoment(trackNumberId),
            )
        }.unzip()
        return tnChanges to affectedTracks
    }

    private fun calculateTrackNumberChangesInDraft(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): Pair<List<TrackNumberChange>, List<List<IntId<LocationTrack>>>> {
        val (tnChanges, affectedTracks) = trackNumberIds.map { trackNumberId ->
            getTrackNumberChange(
                trackNumberId,
                geocodingService.getGeocodingContext(OFFICIAL, trackNumberId),
                geocodingService.getGeocodingContext(DRAFT, trackNumberId),
            )
        }.unzip()
        return tnChanges to affectedTracks
    }

    private fun getTrackNumberChange(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        beforeContext: GeocodingContext?,
        afterContext: GeocodingContext?,
    ): Pair<TrackNumberChange, List<IntId<LocationTrack>>> {
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
        val affectedTracks = geocodingService.getGeocodingContext(OFFICIAL, trackNumberId)?.let { context ->
            calculateOverlappingLocationTracks(
                geocodingContext = context,
                kilometers = addressChanges.changedKmNumbers,
                locationTracks = locationTrackService.listWithAlignments(OFFICIAL, trackNumberId),
            )
        } ?: listOf()
        return trackNumberChange to affectedTracks
    }

    private fun calculateLocationTrackChangesSinceMoment(
        locationTrackIds: List<IntId<LocationTrack>>,
        moment: Instant,
    ) = locationTrackIds.map { locationTrackId ->
        LocationTrackChange.create(
            locationTrackId,
            addressChangesService.getAddressChangesSinceMoment(locationTrackId, moment)
        )
    }

    private fun calculateLocationTrackChangesInDraft(
        locationTrackIds: List<IntId<LocationTrack>>,
    ) = locationTrackIds.map { locationTrackId ->
        LocationTrackChange.create(
            locationTrackId,
            addressChangesService.getAddressChangesInDraft(locationTrackId)
        )
    }

    private fun getSwitchChangesByLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        moment: Instant?
    ): List<SwitchChange> {
        val currentPublishType = if (moment == null) DRAFT else OFFICIAL

        val (oldLocationTrack, oldAlignment) = if (moment == null) {
            locationTrackService.getWithAlignment(OFFICIAL, locationTrackId)
        } else {
            historyDao.fetchLocationTrackAtMoment(locationTrackId, moment)?.let { locationTrack ->
                locationTrack.alignmentVersion?.let { alignmentVersion ->
                    locationTrack to historyDao.fetchLayoutAlignmentVersion(alignmentVersion)
                }
            }
        } ?: (null to null)

        val oldTrackNumber = oldLocationTrack?.let {
            if (moment == null) trackNumberService.get(OFFICIAL, oldLocationTrack.trackNumberId)
            else historyDao.fetchTrackNumberAtMoment(oldLocationTrack.trackNumberId, moment)
        }

        val (currentLocationTrack, currentAlignment) = locationTrackService.getWithAlignmentOrThrow(
            currentPublishType,
            locationTrackId
        )

        val currentTrackNumber = trackNumberService.getOrThrow(
            currentPublishType,
            currentLocationTrack.trackNumberId
        )

        val currentGeocodingContext = geocodingService.getGeocodingContext(
            currentPublishType,
            currentLocationTrack.trackNumberId
        )

        val oldGeocodingContext = oldLocationTrack?.trackNumberId?.let { trackNumberId ->
            if (moment == null) geocodingService.getGeocodingContext(OFFICIAL, trackNumberId)
            else addressChangesService.getGeocodingContextAtMoment(trackNumberId, moment)
        }

        val oldTopologySwitches =
            if (oldLocationTrack != null && oldAlignment != null && oldGeocodingContext != null)
                getTopologySwitchJoints(
                    oldLocationTrack,
                    oldAlignment,
                    getSwitchPresentationJoint = { switchId ->
                        val switch = if (moment == null) switchService.get(OFFICIAL, switchId)
                        else historyDao.getSwitchAtMoment(switchId, moment)
                        switchLibraryService.getSwitchStructure(switch!!.switchStructureId).presentationJointNumber
                    },
                    getAddress = { point -> oldGeocodingContext.getAddress(point)!!.first }
                )
            else listOf()

        val oldSwitches = (oldAlignment?.segments
            ?.let { segments ->
                oldGeocodingContext?.let { context ->
                    getSwitchJointChanges(
                        segments = segments,
                        geocodingContext = context
                    ) { switchId ->
                        if (moment == null) switchService.get(OFFICIAL, switchId)
                        else historyDao.getSwitchAtMoment(switchId, moment)
                    }
                }
            } ?: emptyList()) +
                oldTopologySwitches

        val currentTopologySwitches = if (currentGeocodingContext != null)
            getTopologySwitchJoints(
                currentLocationTrack,
                currentAlignment,
                getSwitchPresentationJoint = { switchId ->
                    val switch = switchService.get(currentPublishType, switchId)
                    switchLibraryService.getSwitchStructure(switch!!.switchStructureId).presentationJointNumber
                },
                getAddress = { point -> currentGeocodingContext.getAddress(point)!!.first })
        else listOf()

        val currentSwitches = (currentGeocodingContext?.let { context ->
            getSwitchJointChanges(
                segments = currentAlignment.segments,
                geocodingContext = context
            ) { switchId -> switchService.get(currentPublishType, switchId) }
        } ?: emptyList()) +
                currentTopologySwitches

        val deletedSwitches = findSwitchJointDifferences(oldSwitches, currentSwitches) { switch, joint, _ ->
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

        val changedSwitches = findSwitchJointDifferences(currentSwitches, oldSwitches) { switch, joint, address ->
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
                        locationTrackId = currentLocationTrack.id as IntId,
                        locationTrackExternalId = currentLocationTrack.externalId,
                        trackNumberId = currentLocationTrack.trackNumberId,
                        trackNumberExternalId = currentTrackNumber.externalId
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
        moment: Instant?
    ): Pair<List<SwitchChange>, List<LocationTrackChange>> {
        val switchChanges = locationTracksChanges.flatMap { locationTrackChange ->
            getSwitchChangesByLocationTrack(locationTrackChange.locationTrackId, moment).filter { switchChange ->
                switchChange.changedJoints.isNotEmpty()
            }
        }

        val locationTrackGeometryChanges = locationTracksChanges.map { locationTrackChange ->
            val locationTrackJointChanges = switchChanges.flatMap { switchChange ->
                switchChange.changedJoints
                    .filterNot { it.isRemoved }
                    .filter { cj -> cj.locationTrackId == locationTrackChange.locationTrackId }
            }

            val addresses = geocodingService.getAddressPoints(locationTrackChange.locationTrackId, OFFICIAL)

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

    private fun getDirectSwitchChanges(switchIds: List<IntId<TrackLayoutSwitch>>) = switchIds.map { switchId ->
        SwitchChange(
            switchId = switchId,
            changedJoints = emptyList()
        )
    }

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
        getAddress: (point: IPoint) -> TrackMeter
    ): List<Pair<IntId<TrackLayoutSwitch>, List<SwitchJointDataHolder>>> {
        val topologySwitchAndLocationPairs = listOf(
            locationTrack.topologyStartSwitch to alignment.start,
            locationTrack.topologyEndSwitch to alignment.end
        )
        return topologySwitchAndLocationPairs.mapNotNull { (topologySwitch, location) ->
            if (topologySwitch != null && location != null && getSwitchPresentationJoint(topologySwitch.switchId) == topologySwitch.jointNumber)
                getTopologySwitchJoints(
                    topologySwitch,
                    Point(location),
                    getAddress(location)
                )
            else null

        }
    }

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

private fun mergeSwitchChanges(
    vararg changeLists: List<SwitchChange>,
): List<SwitchChange> {
    return changeLists
        .flatMap { it }
        .groupBy { it.switchId }
        .map { (switchId, changes) ->
            val mergedJoints = changes.flatMap(SwitchChange::changedJoints).distinct()
            SwitchChange(switchId = switchId, changedJoints = mergedJoints)
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
