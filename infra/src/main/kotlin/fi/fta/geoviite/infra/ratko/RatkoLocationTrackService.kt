package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.ratko.model.*
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Service
import java.time.Instant

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoLocationTrackService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushLocationTrackChangesToRatko(
        publishedLocationTracks: Collection<PublishedLocationTrack>,
        publicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        return publishedLocationTracks.groupBy { it.version.id }.map { (_, locationTracks) ->
            val newestVersion = locationTracks.maxBy { it.version.version }.version
            locationTrackDao.fetch(newestVersion) to locationTracks.flatMap { it.changedKmNumbers }.toSet()
        }.sortedWith(
            compareBy(
                { sortByNullDuplicateOfFirst(it.first.duplicateOf) },
                { sortByDeletedStateFirst(it.first.state) })
        ).map { (layoutLocationTrack, changedKmNumbers) ->
            val externalId =
                requireNotNull(layoutLocationTrack.externalId) { "OID required for location track, lt=${layoutLocationTrack.id}" }
            try {
                ratkoClient.getLocationTrack(RatkoOid(externalId))?.let { existingLocationTrack ->
                    if (layoutLocationTrack.state == LocationTrackState.DELETED) {
                        deleteLocationTrack(
                            layoutLocationTrack = layoutLocationTrack,
                            existingRatkoLocationTrack = existingLocationTrack,
                            moment = publicationTime,
                        )
                    } else {
                        updateLocationTrack(
                            layoutLocationTrack = layoutLocationTrack,
                            existingRatkoLocationTrack = existingLocationTrack,
                            changedKmNumbers = changedKmNumbers,
                            moment = publicationTime
                        )
                    }
                } ?: createLocationTrack(layoutLocationTrack, publicationTime)
            } catch (ex: RatkoPushException) {
                throw RatkoLocationTrackPushException(ex, layoutLocationTrack)
            }
            externalId
        }
    }

    private fun getTrackNumberOid(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): Oid<TrackLayoutTrackNumber> {
        return trackNumberDao.fetchOfficialVersionAtMomentOrThrow(trackNumberId, moment)
            .let { version -> trackNumberDao.fetch(version) }
            .let { layoutTrackNumber ->
                checkNotNull(layoutTrackNumber.externalId) {
                    "Official track number without oid, id=$trackNumberId"
                }
            }
    }

    private fun getExternalId(locationTrackId: IntId<LocationTrack>, moment: Instant): Oid<LocationTrack>? {
        return locationTrackDao.fetchOfficialVersionAtMomentOrThrow(locationTrackId, moment).let { version ->
            locationTrackDao.fetch(version).externalId
        }
    }

    fun forceRedraw(locationTrackOids: Set<RatkoOid<RatkoLocationTrack>>) {
        if (locationTrackOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawLocationTrack(locationTrackOids)
        }
    }

    private fun createLocationTrack(layoutLocationTrack: LocationTrack, moment: Instant) {
        logger.serviceCall(
            "createRatkoLocationTrack",
            "layoutLocationTrack" to layoutLocationTrack,
            "moment" to moment,
        )

        val (addresses, jointPoints) = getLocationTrackPoints(layoutLocationTrack, moment)

        val ratkoNodes = convertToRatkoNodeCollection(addresses)
        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId, moment)

        val duplicateOfOidLocationTrack = layoutLocationTrack.duplicateOf?.let { duplicateId ->
            getExternalId(duplicateId, moment)
        }

        val ratkoLocationTrack = convertToRatkoLocationTrack(locationTrack = layoutLocationTrack,
            trackNumberOid = trackNumberOid,
            nodeCollection = ratkoNodes,
            duplicateOfOid = duplicateOfOidLocationTrack,
            descriptionGetter = { locationTrack ->
                locationTrackService.getFullDescription(PublicationState.OFFICIAL, locationTrack, LocalizationLanguage.FI).toString()
            })
        val locationTrackOid = ratkoClient.newLocationTrack(ratkoLocationTrack)
        checkNotNull(locationTrackOid) {
            "Did not receive oid from Ratko for location track $ratkoLocationTrack"
        }

        val switchPoints = jointPoints.filterNot { jp ->
            jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
        }

        val midPoints = (addresses.midPoints + switchPoints).sortedBy { p -> p.address }
        createLocationTrackPoints(locationTrackOid, midPoints)
        val layoutLocationTrackWithOid = layoutLocationTrack.copy(externalId = Oid(locationTrackOid.id))
        val allPoints = listOf(addresses.startPoint) + midPoints + listOf(addresses.endPoint)
        createLocationTrackMetadata(layoutLocationTrackWithOid, allPoints, trackNumberOid, moment)
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
        moment: Instant,
        changedKmNumbers: Set<KmNumber>? = null,
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrackMetadata",
            "layoutLocationTrack" to layoutLocationTrack,
            "trackNumberOid" to trackNumberOid,
            "moment" to moment,
            "changedKmNumbers" to changedKmNumbers,
        )
        requireNotNull(layoutLocationTrack.externalId) {
            "Cannot update location track metadata without location track oid, id=${layoutLocationTrack.id}"
        }
        requireNotNull(layoutLocationTrack.alignmentVersion) {
            "Location track is missing geometry, id=${layoutLocationTrack.id}"
        }

        val geocodingContext = geocodingService.getGeocodingContextAtMoment(layoutLocationTrack.trackNumberId, moment)

        alignmentDao.fetchMetadata(layoutLocationTrack.alignmentVersion)
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
                val startTrackMeter = geocodingContext?.getAddress(metadata.startPoint)?.first
                val endTrackMeter = geocodingContext?.getAddress(metadata.endPoint)?.first

                if (startTrackMeter != null && endTrackMeter != null) {
                    val origMetaDataRange = startTrackMeter..endTrackMeter
                    val splitAddressRanges = if (changedKmNumbers == null) listOf(origMetaDataRange)
                    else geocodingContext.cutRangeByKms(origMetaDataRange, changedKmNumbers)

                    val splitMetaDataAssets = splitAddressRanges.mapNotNull { addressRange ->
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

                        // Ignore metadata where the address range is under 1m, since there are no address points for it
                        if (startPoint.address + 1 <= endPoint.address) convertToRatkoMetadataAsset(
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
    ): AddressPoint = when (rounding) {
        AddressRounding.UP -> points.find { p -> p.address >= seek }
        AddressRounding.DOWN -> points.findLast { p -> p.address <= seek }
    } ?: error("No address point found: seek=$seek rounding=$rounding")

    private fun deleteLocationTrack(
        layoutLocationTrack: LocationTrack,
        existingRatkoLocationTrack: RatkoLocationTrack,
        moment: Instant,
    ) {
        logger.serviceCall(
            "deleteLocationTrack",
            "layoutLocationTrack" to layoutLocationTrack,
            "existingRatkoLocationTrack" to existingRatkoLocationTrack,
            "moment" to moment
        )

        requireNotNull(layoutLocationTrack.externalId) {
            "Cannot delete location track without oid, id=${layoutLocationTrack.id}"
        }

        val deletedEndsPoints =
            existingRatkoLocationTrack.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

        updateLocationTrackProperties(
            layoutLocationTrack = layoutLocationTrack,
            moment = moment,
            changedNodeCollection = deletedEndsPoints,
        )

        ratkoClient.deleteLocationTrackPoints(RatkoOid(layoutLocationTrack.externalId), null)
    }

    private fun updateLocationTrack(
        layoutLocationTrack: LocationTrack,
        existingRatkoLocationTrack: RatkoLocationTrack,
        changedKmNumbers: Set<KmNumber>,
        moment: Instant,
    ) {
        logger.serviceCall(
            "updateRatkoLocationTrack",
            "layoutLocationTrack" to layoutLocationTrack,
            "existingRatkoLocationTrack" to existingRatkoLocationTrack,
            "changedKmNumbers" to changedKmNumbers,
            "moment" to moment
        )

        requireNotNull(layoutLocationTrack.externalId) {
            "Cannot update location track without oid, id=${layoutLocationTrack.id}"
        }

        val locationTrackOid = RatkoOid<RatkoLocationTrack>(layoutLocationTrack.externalId)
        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId, moment)

        val (addresses, jointPoints) = getLocationTrackPoints(layoutLocationTrack, moment)
        val existingStartNode = existingRatkoLocationTrack.nodecollection?.getStartNode()
        val existingEndNode = existingRatkoLocationTrack.nodecollection?.getEndNode()

        val updatedEndPointNodeCollection = getEndPointNodeCollection(
            alignmentAddresses = addresses,
            changedKmNumbers = changedKmNumbers,
            existingStartNode = existingStartNode,
            existingEndNode = existingEndNode,
        )

        val switchPoints = jointPoints.filterNot { jp ->
            jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
        }

        val changedMidPoints =
            (addresses.midPoints + switchPoints).filter { p -> changedKmNumbers.contains(p.address.kmNumber) }
                .sortedBy { p -> p.address }

        //Update location track end points before deleting anything, otherwise old end points will stay in use
        updateLocationTrackProperties(
            layoutLocationTrack = layoutLocationTrack,
            moment = moment,
            changedNodeCollection = updatedEndPointNodeCollection,
        )

        deleteLocationTrackPoints(changedKmNumbers, locationTrackOid)

        updateLocationTrackGeometry(
            locationTrackOid = locationTrackOid,
            newPoints = changedMidPoints,
        )

        createLocationTrackMetadata(
            layoutLocationTrack = layoutLocationTrack,
            alignmentPoints = listOf(addresses.startPoint) + changedMidPoints + listOf(addresses.endPoint),
            trackNumberOid = trackNumberOid,
            moment = moment,
            changedKmNumbers = changedKmNumbers,
        )
    }

    private fun deleteLocationTrackPoints(
        changedKmNumbers: Set<KmNumber>,
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
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
        moment: Instant,
        changedNodeCollection: RatkoNodes? = null,
    ) {
        val trackNumberOid = getTrackNumberOid(layoutLocationTrack.trackNumberId, moment)
        val duplicateOfOidLocationTrack = layoutLocationTrack.duplicateOf?.let { duplicateId ->
            getExternalId(duplicateId, moment)
        }

        val ratkoLocationTrack = convertToRatkoLocationTrack(locationTrack = layoutLocationTrack,
            trackNumberOid = trackNumberOid,
            nodeCollection = changedNodeCollection,
            duplicateOfOid = duplicateOfOidLocationTrack,
            descriptionGetter = { locationTrack ->
                locationTrackService.getFullDescription(PublicationState.OFFICIAL, locationTrack, LocalizationLanguage.FI).toString()
            })

        ratkoClient.updateLocationTrackProperties(ratkoLocationTrack)
    }

    private fun getLocationTrackPoints(
        locationTrack: LocationTrack,
        moment: Instant,
    ): Pair<AlignmentAddresses, List<AddressPoint>> {
        val alignmentVersion = requireNotNull(locationTrack.alignmentVersion) {
            "Location track missing alignment, id=${locationTrack.id}"
        }

        val geocodingContext = geocodingService.getGeocodingContextCacheKey(
            locationTrack.trackNumberId,
            moment,
        )?.let(geocodingService::getGeocodingContext)

        checkNotNull(geocodingContext) {
            "Missing geocoding context, trackNumberId=${locationTrack.trackNumberId} moment=$moment"
        }

        val alignment = alignmentDao.fetch(alignmentVersion)
        val addresses = checkNotNull(geocodingContext.getAddressPoints(alignment)) {
            "Cannot calculate addresses for location track, id=${locationTrack.id}"
        }

        return addresses to geocodingContext.getSwitchPoints(alignment)
    }
}
