package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
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

    fun pushLocationTrackChangesToRatko(publishedLocationTracks: Collection<PublishedLocationTrack>): List<Oid<LocationTrack>> {
        return publishedLocationTracks
            .groupBy { it.version.id }
            .map { (_, locationTracks) ->
                val newestVersion = locationTracks.maxBy { it.version.version }.version
                locationTrackService.get(newestVersion) to locationTracks.flatMap { it.changedKmNumbers }.toSet()
            }.let { locationTracks ->
                locationTracks
                    .sortedWith(
                        compareBy(
                            { sortByNullDuplicateOfFirst(it.first.duplicateOf) },
                            { sortByDeletedStateFirst(it.first.state) }
                        )
                    ).mapNotNull { (layoutLocationTrack, changedKmNumbers) ->
                        layoutLocationTrack.externalId?.also { externalId ->
                            try {
                                ratkoClient.getLocationTrack(RatkoOid(externalId))
                                    ?.let { existingLocationTrack ->
                                        if (layoutLocationTrack.state == LayoutState.DELETED) {
                                            deleteLocationTrack(layoutLocationTrack, existingLocationTrack)
                                        } else {
                                            updateLocationTrack(
                                                layoutLocationTrack = layoutLocationTrack,
                                                existingRatkoLocationTrack = existingLocationTrack,
                                                changedKmNumbers = changedKmNumbers
                                            )
                                        }
                                    } ?: createLocationTrack(layoutLocationTrack)
                            } catch (ex: RatkoPushException) {
                                throw RatkoLocationTrackPushException(ex, layoutLocationTrack)
                            }
                        }
                    }
            }
    }

    private fun getTrackNumberOid(trackNumberId: IntId<TrackLayoutTrackNumber>): Oid<TrackLayoutTrackNumber> {
        return layoutTrackNumberDao.fetchOfficialVersionOrThrow(trackNumberId)
            .let { version -> layoutTrackNumberDao.fetch(version) }
            .let { layoutTrackNumber ->
                checkNotNull(layoutTrackNumber.externalId) {
                    "Official track number without oid, id=$trackNumberId"
                }
            }
    }

    fun getExternalId(locationTrackId: IntId<LocationTrack>): Oid<LocationTrack>? {
        return locationTrackService.getOrThrow(PublishType.OFFICIAL, locationTrackId).externalId
    }

    fun forceRedraw(locationTrackOids: Set<RatkoOid<RatkoLocationTrack>>) {
        if (locationTrackOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawLocationTrack(locationTrackOids)
        }
    }

    private fun createLocationTrack(layoutLocationTrack: LocationTrack) {
        logger.serviceCall("createRatkoLocationTrack", "layoutLocationTrack" to layoutLocationTrack)
        requireNotNull(layoutLocationTrack.alignmentVersion) {
            "Cannot update location track without alignment $layoutLocationTrack"
        }

        val (geocoding, addresses) = getGeocodingContextAndAlignmentAddresses(layoutLocationTrack)
        val alignment = layoutAlignmentDao.fetch(layoutLocationTrack.alignmentVersion)

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
            "Did not receive oid from Ratko for location track $ratkoLocationTrack"
        }

        val switchPoints = geocoding.getSwitchPoints(alignment).filterNot { sp ->
            sp.address == addresses.startPoint.address || sp.address == addresses.endPoint.address
        }

        val midPoints = (addresses.midPoints + switchPoints).sortedBy { p -> p.address }
        createLocationTrackPoints(locationTrackOid, midPoints)
        val layoutLocationTrackWithOid = layoutLocationTrack.copy(externalId = Oid(locationTrackOid.id))
        val allPoints = listOf(addresses.startPoint) + midPoints + listOf(addresses.endPoint)
        createLocationTrackMetadata(layoutLocationTrackWithOid, allPoints, trackNumberOid)
    }

    private fun createLocationTrackPoints(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        addressPoints: Collection<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
        ratkoClient.createLocationTrackPoints(locationTrackOid, points)
    }

    private fun createLocationTrackMetadata(
        layoutLocationTrack: LocationTrack,
        alignmentPoints: List<AddressPoint>,
        trackNumberOid: Oid<TrackLayoutTrackNumber>,
        changedKmNumbers: Set<KmNumber>? = null,
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrackMetadata",
            "layoutLocationTrack" to layoutLocationTrack,
            "trackNumberOid" to trackNumberOid,
            "changedKmNumbers" to changedKmNumbers,
        )
        requireNotNull(layoutLocationTrack.externalId) {
            "Cannot update location track metadata without location track oid, id=${layoutLocationTrack.id}"
        }
        requireNotNull(layoutLocationTrack.alignmentVersion) {
            "Location track is missing geometry, id=${layoutLocationTrack.id}"
        }

        layoutAlignmentDao.fetchMetadata(layoutLocationTrack.alignmentVersion)
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
                val geocodingContext =
                    geocodingService.getGeocodingContext(PublishType.OFFICIAL, layoutLocationTrack.trackNumberId)
                val startTrackMeter = geocodingContext?.getAddress(metadata.startPoint)?.first
                val endTrackMeter = geocodingContext?.getAddress(metadata.endPoint)?.first

                if (startTrackMeter != null && endTrackMeter != null) {
                    val origMetaDataRange = startTrackMeter..endTrackMeter
                    val splitAddressRanges = if (changedKmNumbers == null) listOf(origMetaDataRange)
                    else geocodingContext.cutRangeByKms(origMetaDataRange, changedKmNumbers)

                    val splitMetaDataAssets = splitAddressRanges
                        // Ignore metadata where the address range is under 1m, since there are no address points for it
                        .filter { addressRange -> !addressRange.start.isSame(addressRange.endInclusive, 0) }
                        .mapNotNull { addressRange ->
                            val startPoint = findAddressPoint(
                                points = alignmentPoints,
                                seek = addressRange.start,
                                rounding = AddressRounding.UP,
                            )

                            val endPoint = findAddressPoint(
                                points = alignmentPoints,
                                seek = addressRange.endInclusive,
                                rounding = AddressRounding.DOWN,
                            )

                            val splitMetaData = metadata.copy(
                                startPoint = startPoint.point.toPoint(),
                                endPoint = endPoint.point.toPoint(),
                            )

                            // If we only have 1 point in the interval, don't send it as it covers no length
                            if (startPoint.address < endPoint.address) convertToRatkoMetadataAsset(
                                trackNumberOid = trackNumberOid,
                                locationTrackOid = layoutLocationTrack.externalId,
                                segmentMetadata = splitMetaData,
                                startTrackMeter = startPoint.address,
                                endTrackMeter = endPoint.address,
                            ) else null
                        }

                    splitMetaDataAssets.forEach { metadataAsset ->
                        ratkoClient.newAsset<RatkoMetadataAsset>(metadataAsset)
                    }
                }
            }
    }

    private enum class AddressRounding { UP, DOWN }

    private fun findAddressPoint(
        points: List<AddressPoint>,
        seek: TrackMeter,
        rounding: AddressRounding,
    ): AddressPoint =
        when (rounding) {
            AddressRounding.UP -> points.find { p -> p.address >= seek }
            AddressRounding.DOWN -> points.findLast { p -> p.address <= seek }
        } ?: throw IllegalStateException("No address point found: seek=$seek rounding=$rounding")

    private fun deleteLocationTrack(
        layoutLocationTrack: LocationTrack,
        existingRatkoLocationTrack: RatkoLocationTrack,
    ) {
        logger.serviceCall("deleteLocationTrack", "layoutLocationTrack" to layoutLocationTrack)
        requireNotNull(layoutLocationTrack.externalId) { "Cannot delete location track without oid, id=${layoutLocationTrack.id}" }

        val deletedEndsPoints =
            existingRatkoLocationTrack.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

        updateLocationTrackProperties(layoutLocationTrack, deletedEndsPoints)

        ratkoClient.deleteLocationTrackPoints(RatkoOid(layoutLocationTrack.externalId), null)
    }

    private fun updateLocationTrack(
        layoutLocationTrack: LocationTrack,
        existingRatkoLocationTrack: RatkoLocationTrack,
        changedKmNumbers: Set<KmNumber>,
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrack",
            "layoutLocationTrack" to layoutLocationTrack,
            "existingRatkoLocationTrack" to existingRatkoLocationTrack,
            "changedKmNumbers" to changedKmNumbers,
        )

        requireNotNull(layoutLocationTrack.externalId) { "Cannot update location track without oid $layoutLocationTrack" }
        requireNotNull(layoutLocationTrack.alignmentVersion) { "Cannot update location track without alignment, id=${layoutLocationTrack.id}" }

        val locationTrackOid = RatkoOid<RatkoLocationTrack>(layoutLocationTrack.externalId)
        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId)

        val (geocoding, addresses) = getGeocodingContextAndAlignmentAddresses(layoutLocationTrack)

        val existingStartNode = existingRatkoLocationTrack.nodecollection?.getStartNode()
        val existingEndNode = existingRatkoLocationTrack.nodecollection?.getEndNode()

        val updatedEndPointNodeCollection = getEndPointNodeCollection(
            alignmentAddresses = addresses,
            changedKmNumbers = changedKmNumbers,
            existingStartNode = existingStartNode,
            existingEndNode = existingEndNode,
        )

        //Update location track end points before deleting anything, otherwise old end points will stay in use
        updateLocationTrackProperties(layoutLocationTrack, updatedEndPointNodeCollection)

        deleteLocationTrackPoints(changedKmNumbers, locationTrackOid)

        val alignment = layoutAlignmentDao.fetch(layoutLocationTrack.alignmentVersion)
        val switchPoints = geocoding.getSwitchPoints(alignment).filterNot { sp ->
            sp.address == addresses.startPoint.address || sp.address == addresses.endPoint.address
        }

        val changedMidPoints = (addresses.midPoints + switchPoints)
            .filter { p -> changedKmNumbers.contains(p.address.kmNumber) }
            .sortedBy { p -> p.address }

        updateLocationTrackGeometry(
            locationTrackOid = locationTrackOid,
            newPoints = changedMidPoints,
        )

        createLocationTrackMetadata(
            layoutLocationTrack,
            listOf(addresses.startPoint) + changedMidPoints + listOf(addresses.endPoint),
            trackNumberOid,
            changedKmNumbers,
        )
    }

    private fun getGeocodingContextAndAlignmentAddresses(layoutLocationTrack: LocationTrack): Pair<GeocodingContext, AlignmentAddresses> {
        val geocoding = geocodingService.getGeocodingContext(PublishType.OFFICIAL, layoutLocationTrack.trackNumberId)
        checkNotNull(geocoding) {
            "Cannot update location track without geocoding context $layoutLocationTrack"
        }
        val addresses = geocodingService.getAddressPoints(layoutLocationTrack.id as IntId, PublishType.OFFICIAL)
        checkNotNull(addresses) {
            "Cannot update location track without location track address points $layoutLocationTrack"
        }
        return Pair(geocoding, addresses)
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
        newPoints: Collection<AddressPoint>,
    ) = toRatkoPointsGroupedByKm(newPoints).forEach { points ->
        ratkoClient.updateLocationTrackPoints(locationTrackOid, points)
    }

    private fun updateLocationTrackProperties(
        layoutLocationTrack: LocationTrack,
        changedNodeCollection: RatkoNodes? = null
    ) {
        requireNotNull(layoutLocationTrack.externalId) { "Cannot update location track properties without oid, id=${layoutLocationTrack.id}" }
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
}
