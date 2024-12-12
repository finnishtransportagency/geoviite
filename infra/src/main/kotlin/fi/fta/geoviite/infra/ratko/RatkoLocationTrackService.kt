package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodes
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.convertToRatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.convertToRatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegmentMetadata
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import java.time.Instant
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoLocationTrackService
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingService: GeocodingService,
) {

    fun pushLocationTrackChangesToRatko(
        branch: LayoutBranch,
        publishedLocationTracks: Collection<PublishedLocationTrack>,
        publicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        return publishedLocationTracks
            .groupBy { it.id }
            .map { (_, locationTracks) ->
                val newestVersion = locationTracks.maxBy { it.version.version }.version
                locationTrackDao.fetch(newestVersion) to locationTracks.flatMap { it.changedKmNumbers }.toSet()
            }
            .sortedWith(
                compareBy(
                    { sortByNullDuplicateOfFirst(it.first.duplicateOf) },
                    { sortByDeletedStateFirst(it.first.state) },
                )
            )
            .map { (layoutLocationTrack, changedKmNumbers) ->
                val externalId =
                    requireNotNull(locationTrackDao.fetchExternalId(branch, layoutLocationTrack.id as IntId)) {
                        "OID required for location track, lt=${layoutLocationTrack.id}"
                    }
                try {
                    ratkoClient.getLocationTrack(RatkoOid(externalId))?.let { existingLocationTrack ->
                        if (layoutLocationTrack.state == LocationTrackState.DELETED) {
                            deleteLocationTrack(
                                branch = branch,
                                layoutLocationTrack = layoutLocationTrack,
                                locationTrackOid = externalId,
                                existingRatkoLocationTrack = existingLocationTrack,
                                moment = publicationTime,
                            )
                        } else {
                            updateLocationTrack(
                                branch = branch,
                                layoutLocationTrack = layoutLocationTrack,
                                locationTrackOid = externalId,
                                existingRatkoLocationTrack = existingLocationTrack,
                                changedKmNumbers = changedKmNumbers,
                                moment = publicationTime,
                            )
                        }
                    }
                        ?: if (layoutLocationTrack.state != LocationTrackState.DELETED) {
                            createLocationTrack(branch, layoutLocationTrack, externalId, publicationTime)
                        } else {
                            null
                        }
                } catch (ex: RatkoPushException) {
                    throw RatkoLocationTrackPushException(ex, layoutLocationTrack)
                }
                externalId
            }
    }

    private fun getTrackNumberOid(
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): Oid<TrackLayoutTrackNumber> =
        checkNotNull(trackNumberDao.fetchExternalId(branch, trackNumberId)) {
            "Official track number without oid, id=$trackNumberId"
        }

    private fun getExternalId(branch: LayoutBranch, locationTrackId: IntId<LocationTrack>): Oid<LocationTrack>? =
        locationTrackDao.fetchExternalId(branch, locationTrackId)

    fun forceRedraw(locationTrackOids: Set<RatkoOid<RatkoLocationTrack>>) {
        if (locationTrackOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawLocationTrack(locationTrackOids)
        }
    }

    private fun createLocationTrack(
        branch: LayoutBranch,
        layoutLocationTrack: LocationTrack,
        locationTrackOid: Oid<LocationTrack>,
        moment: Instant,
    ) {
        try {
            val (addresses, jointPoints) = getLocationTrackPoints(branch, layoutLocationTrack, moment)

            val ratkoNodes = convertToRatkoNodeCollection(addresses)
            val trackNumberOid = getTrackNumberOid(branch, layoutLocationTrack.trackNumberId)

            val duplicateOfOidLocationTrack =
                layoutLocationTrack.duplicateOf?.let { duplicateId -> getExternalId(branch, duplicateId) }
            val owner = locationTrackService.getLocationTrackOwner(layoutLocationTrack.ownerId)

            val ratkoLocationTrack =
                convertToRatkoLocationTrack(
                    locationTrack = layoutLocationTrack,
                    locationTrackOid = locationTrackOid,
                    trackNumberOid = trackNumberOid,
                    nodeCollection = ratkoNodes,
                    duplicateOfOid = duplicateOfOidLocationTrack,
                    descriptionGetter = { locationTrack ->
                        locationTrackService
                            .getFullDescription(branch.official, locationTrack, LocalizationLanguage.FI)
                            .toString()
                    },
                    owner = owner,
                )
            val locationTrackOid = ratkoClient.newLocationTrack(ratkoLocationTrack)
            checkNotNull(locationTrackOid) { "Did not receive oid from Ratko for location track $ratkoLocationTrack" }

            val switchPoints =
                jointPoints.filterNot { jp ->
                    jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
                }

            val midPoints = (addresses.midPoints + switchPoints).sortedBy { p -> p.address }
            createLocationTrackPoints(locationTrackOid, midPoints)
            val allPoints = listOf(addresses.startPoint) + midPoints + listOf(addresses.endPoint)
            createLocationTrackMetadata(
                branch,
                layoutLocationTrack,
                Oid(locationTrackOid.id),
                allPoints,
                trackNumberOid,
                moment,
            )
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.CREATE, ex)
        }
    }

    private fun createLocationTrackPoints(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        addressPoints: Collection<AddressPoint>,
    ) =
        toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
            ratkoClient.createLocationTrackPoints(locationTrackOid, points)
        }

    private fun createLocationTrackMetadata(
        branch: LayoutBranch,
        layoutLocationTrack: LocationTrack,
        locationTrackOid: Oid<LocationTrack>,
        alignmentPoints: List<AddressPoint>,
        trackNumberOid: Oid<TrackLayoutTrackNumber>,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>? = null,
    ) {
        val geocodingContext =
            geocodingService.getGeocodingContextAtMoment(branch, layoutLocationTrack.trackNumberId, moment)

        alignmentDao
            .fetchMetadata(layoutLocationTrack.getAlignmentVersionOrThrow())
            .fold(mutableListOf<LayoutSegmentMetadata>()) { acc, metadata ->
                val previousMetadata = acc.lastOrNull()

                if (
                    previousMetadata == null ||
                        previousMetadata.isEmpty() ||
                        !previousMetadata.hasSameMetadata(metadata)
                ) {
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
                    val splitAddressRanges =
                        if (changedKmNumbers == null) listOf(origMetaDataRange)
                        else geocodingContext.cutRangeByKms(origMetaDataRange, changedKmNumbers)

                    val splitMetaDataAssets =
                        splitAddressRanges.mapNotNull { addressRange ->
                            val startPoint =
                                findAddressPoint(
                                    points = alignmentPoints,
                                    seek = addressRange.start,
                                    rounding = AddressRounding.UP,
                                )

                            val endPoint =
                                findAddressPoint(
                                    points = alignmentPoints,
                                    seek = addressRange.endInclusive,
                                    rounding = AddressRounding.DOWN,
                                )

                            val splitMetaData =
                                metadata.copy(
                                    startPoint = startPoint.point.toPoint(),
                                    endPoint = endPoint.point.toPoint(),
                                )

                            // Ignore metadata where the address range is under 1m, since there are
                            // no address points for it
                            if (startPoint.address + 1 <= endPoint.address)
                                convertToRatkoMetadataAsset(
                                    trackNumberOid = trackNumberOid,
                                    locationTrackOid = locationTrackOid,
                                    segmentMetadata = splitMetaData,
                                    startTrackMeter = startPoint.address,
                                    endTrackMeter = endPoint.address,
                                )
                            else null
                        }

                    splitMetaDataAssets.forEach { metadataAsset ->
                        ratkoClient.newAsset<RatkoMetadataAsset>(metadataAsset)
                    }
                }
            }
    }

    private enum class AddressRounding {
        UP,
        DOWN,
    }

    private fun findAddressPoint(
        points: List<AddressPoint>,
        seek: TrackMeter,
        rounding: AddressRounding,
    ): AddressPoint =
        when (rounding) {
            AddressRounding.UP -> points.find { p -> p.address >= seek }
            AddressRounding.DOWN -> points.findLast { p -> p.address <= seek }
        } ?: error("No address point found: seek=$seek rounding=$rounding")

    private fun deleteLocationTrack(
        branch: LayoutBranch,
        layoutLocationTrack: LocationTrack,
        locationTrackOid: Oid<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
        moment: Instant,
    ) {
        try {
            val deletedEndsPoints =
                existingRatkoLocationTrack.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

            updateLocationTrackProperties(
                branch = branch,
                layoutLocationTrack = layoutLocationTrack,
                locationTrackOid = locationTrackOid,
                moment = moment,
                changedNodeCollection = deletedEndsPoints,
            )

            ratkoClient.deleteLocationTrackPoints(RatkoOid(locationTrackOid), null)
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.DELETE, ex)
        }
    }

    private fun updateLocationTrack(
        branch: LayoutBranch,
        layoutLocationTrack: LocationTrack,
        locationTrackOid: Oid<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
        changedKmNumbers: Set<KmNumber>,
        moment: Instant,
    ) {
        try {
            val locationTrackRatkoOid = RatkoOid<RatkoLocationTrack>(locationTrackOid)
            val trackNumberOid = getTrackNumberOid(branch, layoutLocationTrack.trackNumberId)

            val (addresses, jointPoints) = getLocationTrackPoints(branch, layoutLocationTrack, moment)
            val existingStartNode = existingRatkoLocationTrack.nodecollection?.getStartNode()
            val existingEndNode = existingRatkoLocationTrack.nodecollection?.getEndNode()

            val updatedEndPointNodeCollection =
                getEndPointNodeCollection(
                    alignmentAddresses = addresses,
                    changedKmNumbers = changedKmNumbers,
                    existingStartNode = existingStartNode,
                    existingEndNode = existingEndNode,
                )

            val switchPoints =
                jointPoints.filterNot { jp ->
                    jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
                }

            val changedMidPoints =
                (addresses.midPoints + switchPoints)
                    .filter { p -> changedKmNumbers.contains(p.address.kmNumber) }
                    .sortedBy { p -> p.address }

            // Update location track end points before deleting anything, otherwise old end points
            // will stay in use
            updateLocationTrackProperties(
                branch = branch,
                layoutLocationTrack = layoutLocationTrack,
                locationTrackOid = locationTrackOid,
                moment = moment,
                changedNodeCollection = updatedEndPointNodeCollection,
            )

            deleteLocationTrackPoints(changedKmNumbers, locationTrackRatkoOid)

            updateLocationTrackGeometry(locationTrackOid = locationTrackRatkoOid, newPoints = changedMidPoints)

            createLocationTrackMetadata(
                branch = branch,
                layoutLocationTrack = layoutLocationTrack,
                locationTrackOid = locationTrackOid,
                alignmentPoints = listOf(addresses.startPoint) + changedMidPoints + listOf(addresses.endPoint),
                trackNumberOid = trackNumberOid,
                moment = moment,
                changedKmNumbers = changedKmNumbers,
            )
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.UPDATE, ex)
        }
    }

    private fun deleteLocationTrackPoints(
        changedKmNumbers: Set<KmNumber>,
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
    ) {
        changedKmNumbers.forEach { kmNumber -> ratkoClient.deleteLocationTrackPoints(locationTrackOid, kmNumber) }
    }

    private fun updateLocationTrackGeometry(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        newPoints: Collection<AddressPoint>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.updateLocationTrackPoints(locationTrackOid, points)
        }

    private fun updateLocationTrackProperties(
        branch: LayoutBranch,
        layoutLocationTrack: LocationTrack,
        locationTrackOid: Oid<LocationTrack>,
        moment: Instant,
        changedNodeCollection: RatkoNodes? = null,
    ) {
        val trackNumberOid = getTrackNumberOid(branch, layoutLocationTrack.trackNumberId)
        val duplicateOfOidLocationTrack =
            layoutLocationTrack.duplicateOf?.let { duplicateId -> getExternalId(branch, duplicateId) }
        val owner = locationTrackService.getLocationTrackOwner(layoutLocationTrack.ownerId)

        val ratkoLocationTrack =
            convertToRatkoLocationTrack(
                locationTrack = layoutLocationTrack,
                locationTrackOid = locationTrackOid,
                trackNumberOid = trackNumberOid,
                nodeCollection = changedNodeCollection,
                duplicateOfOid = duplicateOfOidLocationTrack,
                descriptionGetter = { locationTrack ->
                    locationTrackService
                        .getFullDescription(branch.official, locationTrack, LocalizationLanguage.FI)
                        .toString()
                },
                owner = owner,
            )

        ratkoClient.updateLocationTrackProperties(ratkoLocationTrack)
    }

    private fun getLocationTrackPoints(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        moment: Instant,
    ): Pair<AlignmentAddresses, List<AddressPoint>> {
        val geocodingContext =
            geocodingService
                .getGeocodingContextCacheKey(branch, locationTrack.trackNumberId, moment)
                ?.let(geocodingService::getGeocodingContext)

        checkNotNull(geocodingContext) {
            "Missing geocoding context, trackNumberId=${locationTrack.trackNumberId} moment=$moment"
        }

        val alignment = alignmentDao.fetch(locationTrack.getAlignmentVersionOrThrow())
        val addresses =
            checkNotNull(geocodingContext.getAddressPoints(alignment)) {
                "Cannot calculate addresses for location track, id=${locationTrack.id}"
            }

        return addresses to geocodingContext.getSwitchPoints(alignment)
    }
}
