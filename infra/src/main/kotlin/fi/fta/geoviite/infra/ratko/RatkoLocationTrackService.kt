package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoLocationTrackService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val locationTrackService: LocationTrackService,
    private val layoutAlignmentDao: LayoutAlignmentDao,
    private val layoutTrackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushLocationTrackChangesToRatko(locationTrackChanges: List<LocationTrackChange>): List<Oid<LocationTrack>> {

        return locationTrackChanges
            .map { change -> change to locationTrackService.getOrThrow(PublishType.OFFICIAL, change.locationTrackId) }
            .sortedWith(
                compareBy(
                    { sortByNullDuplicateOfFirst(it.second.duplicateOf) },
                    { sortByDeletedStateFirst(it.second.state) }
                )
            )
            .mapNotNull { (locationTrackChange, locationTrack) ->
                locationTrack.externalId?.also { externalId ->
                    try {
                        ratkoClient.getLocationTrack(RatkoOid(externalId))
                            ?.let { existingLocationTrack ->
                                if (locationTrack.state == LayoutState.DELETED) {
                                    deleteLocationTrack(locationTrack)
                                } else {
                                    updateLocationTrack(
                                        layoutLocationTrack = locationTrack,
                                        existingRatkoLocationTrack = existingLocationTrack,
                                        locationTrackChange = locationTrackChange
                                    )
                                }
                            } ?: createLocationTrack(locationTrack)
                    } catch (ex: RatkoPushException) {
                        throw RatkoLocationTrackPushException(ex, locationTrack)
                    }
                }
            }
    }

    private fun getTrackNumberOid(trackNumberId: IntId<TrackLayoutTrackNumber>): Oid<TrackLayoutTrackNumber> {
        return layoutTrackNumberDao.fetchOfficialVersionOrThrow(trackNumberId)
            .let { version -> layoutTrackNumberDao.fetch(version) }
            .let { layoutTrackNumber ->
                checkNotNull(layoutTrackNumber.externalId) {
                    "Found track number without oid with id $trackNumberId"
                }
            }
    }

    fun getExternalId(locationTrackId: IntId<LocationTrack>): Oid<LocationTrack>? {
        return locationTrackService.getOrThrow(PublishType.OFFICIAL, locationTrackId).externalId
    }

    fun forceRedraw(locationTrackOids: List<RatkoOid<RatkoLocationTrack>>) {
        if (locationTrackOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawLocationTrack(locationTrackOids)
        }
    }

    private fun createLocationTrack(layoutLocationTrack: LocationTrack) {
        logger.serviceCall("createRatkoLocationTrack", "layoutLocationTrack" to layoutLocationTrack)

        val addresses = geocodingService.getAddressPoints(layoutLocationTrack, PublishType.OFFICIAL)
        checkNotNull(addresses) {
            "Cannot update location track without location track address points $layoutLocationTrack"
        }

        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId)

        val ratkoNodes = convertToRatkoNodeCollection(addresses)
        val duplicateOfOidLocationTrack = layoutLocationTrack.duplicateOf?.let(::getExternalId)
        val ratkoLocationTrack = convertToRatkoLocationTrack(
            locationTrack = layoutLocationTrack,
            trackNumberOid = trackNumberOid,
            nodeCollection = ratkoNodes,
            duplicateOfOid = duplicateOfOidLocationTrack,
        )
        val locationTrackOid = ratkoClient.newLocationTrack(ratkoLocationTrack)
        checkNotNull(locationTrackOid) {
            "Did not receive oid from Ratko $ratkoLocationTrack"
        }
        createLocationTrackPoints(locationTrackOid, addresses.midPoints)
        val layoutLocationTrackWithOid = layoutLocationTrack.copy(externalId = Oid(locationTrackOid.id))
        createLocationTrackMetadata(layoutLocationTrackWithOid, trackNumberOid) { _, _ -> true }
    }

    private fun createLocationTrackPoints(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        addressPoints: List<AddressPoint>
    ) = toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
        ratkoClient.createLocationTrackPoints(locationTrackOid, points)
    }

    private fun createLocationTrackMetadata(
        layoutLocationTrack: LocationTrack,
        trackNumberOid: Oid<TrackLayoutTrackNumber>,
        includeMetadata: (startTrackMeter: TrackMeter, endTrackMeter: TrackMeter) -> Boolean
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrackMetadata",
            "layoutLocationTrack" to layoutLocationTrack
        )
        requireNotNull(layoutLocationTrack.externalId) {
            "Cannot update location track metadata without location track oid $layoutLocationTrack"
        }
        requireNotNull(layoutLocationTrack.alignmentVersion)

        layoutAlignmentDao.fetchMetadata(layoutLocationTrack.alignmentVersion.id)
            .fold(mutableListOf<LayoutSegmentMetadata>()) { acc, metadata ->
                val previousMetadata = acc.lastOrNull()

                if (previousMetadata == null || previousMetadata.isEmpty() || !previousMetadata.hasSameMetadata(metadata)) {
                    acc.add(metadata)
                } else {
                    acc[acc.lastIndex] = previousMetadata.copy(endPoint = metadata.endPoint)
                }
                acc
            }
            .filterNot { it.isEmpty() }
            .forEach { metadata ->
                val startTrackMeter = geocodingService.getTrackAddress(
                    trackNumberId = layoutLocationTrack.trackNumberId,
                    location = metadata.startPoint,
                    publishType = PublishType.OFFICIAL
                )?.first

                val endTrackMeter = geocodingService.getTrackAddress(
                    trackNumberId = layoutLocationTrack.trackNumberId,
                    location = metadata.endPoint,
                    publishType = PublishType.OFFICIAL
                )?.first

                if (startTrackMeter != null
                    && endTrackMeter != null
                    && includeMetadata(startTrackMeter, endTrackMeter)
                ) {
                    val metadataAsset = convertToRatkoMetadataAsset(
                        trackNumberOid = trackNumberOid,
                        locationTrackOid = layoutLocationTrack.externalId,
                        segmentMetadata = metadata,
                        startTrackMeter = startTrackMeter,
                        endTrackMeter = endTrackMeter
                    )

                    val locationTrackOid = RatkoOid<RatkoLocationTrack>(layoutLocationTrack.externalId)

                    ratkoClient.getLocationTrack(locationTrackOid)?.let { existingRatkoLocationTrack ->
                        val newLocationTrackPoints =
                            findPointsNotInLocationTrack(metadataAsset.locations, existingRatkoLocationTrack)

                        if (newLocationTrackPoints.isNotEmpty()) {
                            ratkoClient.updateLocationTrackPoints(locationTrackOid, newLocationTrackPoints)
                        }

                        ratkoClient.newAsset<RatkoMetadataAsset>(metadataAsset)
                    }
                }
            }
    }

    private fun deleteLocationTrack(layoutLocationTrack: LocationTrack) {
        logger.serviceCall("deleteLocationTrack", "layoutLocationTrack" to layoutLocationTrack)
        requireNotNull(layoutLocationTrack.externalId) { "Cannot delete location track without oid $layoutLocationTrack" }

        val addresses = geocodingService.getAddressPoints(layoutLocationTrack, PublishType.OFFICIAL)
        checkNotNull(addresses) {
            "Cannot delete location track without location track address points $layoutLocationTrack"
        }

        val deletedEndsPoints = getEndPointNodeCollection(
            alignmentAddresses = addresses,
            startChanged = true,
            endChanged = true,
            pointState = RatkoPointStates.NOT_IN_USE
        )

        updateLocationTrackProperties(layoutLocationTrack, deletedEndsPoints)

        ratkoClient.deleteLocationTrackPoints(RatkoOid(layoutLocationTrack.externalId), null)
    }

    private fun updateLocationTrack(
        layoutLocationTrack: LocationTrack,
        existingRatkoLocationTrack: RatkoLocationTrack,
        locationTrackChange: LocationTrackChange
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrack",
            "layoutLocationTrack" to layoutLocationTrack,
            "existingRatkoLocationTrack" to existingRatkoLocationTrack,
            "locationTrackChange" to locationTrackChange,
        )

        requireNotNull(layoutLocationTrack.externalId) { "Cannot update location track without oid $layoutLocationTrack" }
        val locationTrackOid = RatkoOid<RatkoLocationTrack>(layoutLocationTrack.externalId)

        val addresses = geocodingService.getAddressPoints(layoutLocationTrack, PublishType.OFFICIAL)
        checkNotNull(addresses) {
            "Cannot update location track without location track address points $layoutLocationTrack"
        }

        val existingStartNode = existingRatkoLocationTrack.nodecollection?.getStartNode()
        val existingEndNode = existingRatkoLocationTrack.nodecollection?.getEndNode()

        val updatedEndPointNodeCollection = getEndPointNodeCollection(
            alignmentAddresses = addresses,
            startChanged = locationTrackChange.isStartChanged,
            endChanged = locationTrackChange.isEndChanged,
            existingStartNode = existingStartNode,
            existingEndNode = existingEndNode,
        )

        deleteLocationTrackPoints(locationTrackChange.changedKmNumbers, locationTrackOid)

        updateLocationTrackProperties(layoutLocationTrack, updatedEndPointNodeCollection)

        updateLocationTrackGeometry(
            locationTrackOid = locationTrackOid,
            newPoints = addresses.midPoints.filter { p ->
                locationTrackChange.changedKmNumbers.contains(p.address.kmNumber)
            },
        )

        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId)

        createLocationTrackMetadata(layoutLocationTrack, trackNumberOid) { startTrackMeter, endTrackMeter ->
            locationTrackChange.changedKmNumbers.any { changedKm -> changedKm in startTrackMeter.kmNumber..endTrackMeter.kmNumber }
        }
    }

    private fun deleteLocationTrackPoints(
        changedKmNumbers: Set<KmNumber>,
        locationTrackOid: RatkoOid<RatkoLocationTrack>
    ) {
        changedKmNumbers.forEach { kmNumber ->
            ratkoClient.deleteLocationTrackPoints(locationTrackOid, kmNumber)
        }
    }

    private fun updateLocationTrackGeometry(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        newPoints: List<AddressPoint>
    ) = toRatkoPointsGroupedByKm(newPoints).forEach { points ->
        ratkoClient.updateLocationTrackPoints(locationTrackOid, points)
    }

    private fun updateLocationTrackProperties(
        layoutLocationTrack: LocationTrack,
        changedNodeCollection: RatkoNodes? = null
    ) {
        requireNotNull(layoutLocationTrack.externalId) { "Cannot update location track properties without oid $layoutLocationTrack" }
        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId)
        val duplicateOfOidLocationTrack = layoutLocationTrack.duplicateOf?.let(::getExternalId)
        val ratkoLocationTrack = convertToRatkoLocationTrack(
            locationTrack = layoutLocationTrack,
            trackNumberOid = trackNumberOid,
            nodeCollection = changedNodeCollection,
            duplicateOfOid = duplicateOfOidLocationTrack,
        )

        ratkoClient.updateLocationTrackProperties(ratkoLocationTrack)
    }

    private fun findPointsNotInLocationTrack(
        locations: List<RatkoAssetLocation>,
        existingRatkoLocationTrack: RatkoLocationTrack,
    ): List<RatkoPoint> {
        val existingStartTrackMeter = existingRatkoLocationTrack.nodecollection?.getStartNode()?.point?.kmM
        val existingEndTrackMeter = existingRatkoLocationTrack.nodecollection?.getEndNode()?.point?.kmM

        return if (existingStartTrackMeter == null || existingEndTrackMeter == null) emptyList()
        else locations
            .flatMap { location -> location.nodecollection.nodes.map { node -> node.point } }
            .filter { point ->
                existingStartTrackMeter < point.kmM && point.kmM < existingEndTrackMeter
            }
    }

}
