package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.PublicationVersion
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
    val historyDao: TrackLayoutHistoryDao,
    val geocodingService: GeocodingService,
    val alignmentDao: LayoutAlignmentDao,
) {
    fun getCalculatedChangesBetween(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        locationTrackIds: List<IntId<LocationTrack>>,
        switchIds: List<IntId<TrackLayoutSwitch>>,
        startMoment: Instant,
        endMoment: Instant,
    ): CalculatedChanges {
        val contextKeysBefore = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, startMoment)
        }
        val contextKeysAfter = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, endMoment)
        }

        val (trackNumberChanges, affectedLocationTracksIds) =
            calculateTrackNumberChanges(trackNumberIds, contextKeysBefore, contextKeysAfter)
        val trackChanges =
            getLocationTrackChanges((locationTrackIds+affectedLocationTracksIds).distinct(), startMoment, endMoment)
        val locationTrackGeometryChanges =
            calculateLocationTrackChanges(trackChanges, contextKeysBefore, contextKeysAfter)

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

    fun getCalculatedChangesInDraft(versions: PublicationVersions): CalculatedChanges {
        val contextKeysBefore = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, OFFICIAL)
        }
        val contextKeysAfter = LazyMap { id: IntId<TrackLayoutTrackNumber> ->
            geocodingService.getGeocodingContextCacheKey(id, versions)
        }

        val allTrackNumberIds = (
                versions.trackNumbers.map { v -> v.officialId }
                        + versions.kmPosts.map { v -> kmPostDao.fetch(v.draftVersion).trackNumberId!! }
                        + versions.referenceLines.map { v -> referenceLineDao.fetch(v.draftVersion).trackNumberId }
                ).distinct()

        val (trackNumberChanges, affectedLocationTracksIds) =
            calculateTrackNumberChanges(allTrackNumberIds, contextKeysBefore, contextKeysAfter)
        val trackChanges =
            getLocationTrackChanges(versions, affectedLocationTracksIds)
        val locationTrackGeometryChanges =
            calculateLocationTrackChanges(trackChanges, contextKeysBefore, contextKeysAfter)

        val directSwitchChanges = getDirectSwitchChanges(versions.switches.map { v -> v.officialId })

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

//    private fun calculateTrackNumberChangesSinceMoment(
//        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
//        moment: Instant
//    ): Pair<List<TrackNumberChange>, List<List<IntId<LocationTrack>>>> {
//        val (tnChanges, affectedTracks) = trackNumberIds.map { trackNumberId ->
//            getTrackNumberChange(
//                trackNumberId,
//                geocodingService.getGeocodingContextCacheKey(trackNumberId, moment),
//                geocodingService.getGeocodingContextCacheKey(trackNumberId, OFFICIAL),
//            )
//        }.unzip()
//        return tnChanges to affectedTracks
//    }
//
//    private fun calculateTrackNumberChangesInDraft(
//        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
//        publicationVersions: PublicationVersions,
//    ): Pair<List<TrackNumberChange>, List<List<IntId<LocationTrack>>>> {
//        val (tnChanges, affectedTracks) = trackNumberIds.map { trackNumberId ->
//            getTrackNumberChange(
//                trackNumberId,
//                geocodingService.getGeocodingContextCacheKey(trackNumberId, OFFICIAL),
//                geocodingService.getGeocodingContextCacheKey(trackNumberId, publicationVersions),
//            )
//        }.unzip()
//        return tnChanges to affectedTracks
//    }

    private fun calculateTrackNumberChanges(
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        keysBefore: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
        keysAfter: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): Pair<List<TrackNumberChange>, List<IntId<LocationTrack>>> {
        val (tnChanges, affectedTracks) = trackNumberIds.map { id ->
            getTrackNumberChange(id, keysBefore[id], keysAfter[id])
        }.unzip()
        return tnChanges to affectedTracks.flatten().distinct()
    }

    private fun getTrackNumberChange(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        beforeContextKey: GeocodingContextCacheKey?,
        afterContextKey: GeocodingContextCacheKey?,
    ): Pair<TrackNumberChange, List<IntId<LocationTrack>>> {
        val addressChanges = getAddressChanges(
            beforeContextKey?.let(geocodingService::getReferenceLineAddressPoints),
            afterContextKey?.let(geocodingService::getReferenceLineAddressPoints),
        )
        val trackNumberChange = TrackNumberChange(
            trackNumberId = trackNumberId,
            trackNumberVersion = ,
            changedKmNumbers = addressChanges.changedKmNumbers,
            isStartChanged = addressChanges.startPointChanged,
            isEndChanged = addressChanges.endPointChanged,
        )
        val affectedTracks = beforeContextKey?.let(geocodingService::getGeocodingContext)?.let { context ->
            calculateOverlappingLocationTracks(
                geocodingContext = context,
                kilometers = addressChanges.changedKmNumbers,
                locationTracks = locationTrackService.listWithAlignments(OFFICIAL, trackNumberId),
            )
        } ?: listOf()
        return trackNumberChange to affectedTracks
    }

//    private fun calculateLocationTrackChangesSinceMoment(
//        locationTrackIds: List<IntId<LocationTrack>>,
//        moment: Instant,
//    ) = locationTrackIds.map { locationTrackId ->
//        LocationTrackChange.create(
//            locationTrackId,
//            addressChangesService.getAddressChangesSinceMoment(locationTrackId, moment)
//        )
//    }

    private fun calculateLocationTrackChanges(
        trackChanges: List<RowChange<LocationTrack>>,
        keysBefore: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
        keysAfter: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ) = trackChanges.mapNotNull { trackChange ->
        val addressChanges = addressChangesService.getAddressChanges(trackChange, keysBefore, keysAfter)
        if (trackChange.before == trackChange.after && !addressChanges.isChanged()) {
            null
        } else {
            LocationTrackChange.create(trackChange.id, trackChange.after, addressChanges)
        }
    }

    private fun getSwitchChangesByLocationTrack(
        rowChange: RowChange<LocationTrack>,
//        locationTrackId: IntId<LocationTrack>,
//        moment: Instant?
        keysBefore: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
        keysAfter: LazyMap<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<SwitchChange> {
//        val currentPublishType = if (moment == null) DRAFT else OFFICIAL

        val (oldLocationTrack, oldAlignment) = rowChange.before?.let(locationTrackService::getWithAlignment) ?: (null to null)
        val (currentLocationTrack, currentAlignment) = locationTrackService.getWithAlignment(rowChange.after)
//        val (oldLocationTrack, oldAlignment) = if (moment == null) {
//            locationTrackService.getWithAlignment(OFFICIAL, locationTrackId)
//        } else {
//            historyDao.fetchLocationTrackAtMoment(locationTrackId, moment)?.let { locationTrack ->
//                locationTrack.alignmentVersion?.let { alignmentVersion ->
//                    locationTrack to alignmentDao.fetch(alignmentVersion)
//                }
//            }
//        } ?: (null to null)

        val oldTrackNumber = oldLocationTrack?.let {
            if (moment == null) trackNumberService.get(OFFICIAL, oldLocationTrack.trackNumberId)
            else historyDao.fetchTrackNumberAtMoment(oldLocationTrack.trackNumberId, moment)
        }

//        val (currentLocationTrack, currentAlignment) = locationTrackService.getWithAlignmentOrThrow(
//            currentPublishType,
//            locationTrackId
//        )

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
                    switchVersion = ,
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
                switchVersion = ,
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
                locationTrackVersion = ,
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

    fun getLocationTrackChanges(ids: List<IntId<LocationTrack>>, startMoment: Instant, endMoment: Instant) =
        ids.map { id -> RowChange(
            id = id,
            before = locationTrackDao.fetchOfficialVersionAtMoment(id, startMoment),
            after = locationTrackDao.fetchOfficialVersionAtMoment(id, endMoment)
                ?: throw NoSuchEntityException(LocationTrack::class, id),
        ) }

    fun getLocationTrackChanges(
        versions: PublicationVersions,
        affectedLocationTracksIds: List<IntId<LocationTrack>>,
    ): List<RowChange<LocationTrack>> {
        val tracksWithAddressChanges: List<RowChange<LocationTrack>> = affectedLocationTracksIds
            .filterNot(versions::containsLocationTrack)
            .map { id -> officialNoChange(id, locationTrackDao) }
        return tracksWithAddressChanges + versionChanges(versions.locationTracks, locationTrackDao)
    }
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

data class RowChange<T>(val id: IntId<T>, val before: RowVersion<T>?, val after: RowVersion<T>)

fun <T : Draftable<T>> versionChanges(drafts: List<PublicationVersion<T>>, dao: DraftableDaoBase<T>) =
    drafts.map { v -> RowChange(v.officialId, dao.fetchOfficialVersion(v.officialId), v.draftVersion) }

fun <T : Draftable<T>> officialNoChange(id: IntId<T>, dao: DraftableDaoBase<T>) =
    dao.fetchOfficialVersionOrThrow(id).let { v -> RowChange(id, v, v) }

class LazyMap<K, V>(private val compute: (K) -> V) {
    private val map = mutableMapOf<K, V>()
    operator fun get(key: K): V? = map.getOrPut(key) { compute(key) }
}
