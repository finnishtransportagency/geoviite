package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.FullRatkoExternalId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.publication.PublishedSwitchJoint
import fi.fta.geoviite.infra.ratko.model.PushableLayoutBranch
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodes
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoSplit
import fi.fta.geoviite.infra.ratko.model.RatkoSplitSourceTrack
import fi.fta.geoviite.infra.ratko.model.RatkoSplitTargetTrack
import fi.fta.geoviite.infra.ratko.model.convertToRatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.convertToRatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.split.getSplitTargetTrackStartAndEndAddresses
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutSegmentMetadata
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
    private val publicationDao: PublicationDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushSplits(
        branch: LayoutBranch,
        ratkoSplits: List<RatkoSplit>,
        lastPublicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        val oidMapping =
            locationTrackDao.fetchExternalIds(
                branch,
                ratkoSplits.flatMap { ratkoSplit -> ratkoSplit.split.locationTracks },
            )

        return ratkoSplits.flatMap { ratkoSplit ->
            val (sourceTrack, sourceTrackGeometry) =
                locationTrackService.getWithGeometry(ratkoSplit.split.sourceLocationTrackVersion)

            val ratkoSplitSourceTrack =
                RatkoSplitSourceTrack(
                    track = sourceTrack,
                    geometry = sourceTrackGeometry,
                    externalId = oidMapping.getValue(sourceTrack.id as IntId).oid.let(::MainBranchRatkoExternalId),
                    existingRatkoLocationTrack = ratkoSplit.ratkoSourceTrack,
                    geocodingContext =
                        geocodingService
                            .getGeocodingContextAtMoment(branch, sourceTrack.trackNumberId, lastPublicationTime)
                            .let(::requireNotNull),
                )

            pushSingleSplit(branch, ratkoSplit, ratkoSplitSourceTrack, oidMapping, lastPublicationTime)
        }
    }

    private fun pushSingleSplit(
        branch: LayoutBranch,
        ratkoSplit: RatkoSplit,
        splitSourceTrack: RatkoSplitSourceTrack,
        oidMapping: Map<IntId<LocationTrack>, RatkoExternalId<LocationTrack>>,
        publicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        // The state of the source track is refreshed before pushing any split target tracks as the geometry is being
        // referenced for target tracks from the source track (which may not be up-to-date within Ratko).
        updateLocationTrack(
            branch = branch,
            locationTrack = splitSourceTrack.track,
            locationTrackExternalId = splitSourceTrack.externalId,
            existingRatkoLocationTrack = splitSourceTrack.existingRatkoLocationTrack,
            changedKmNumbers = getAllKmNumbers(splitSourceTrack.geometry, splitSourceTrack.geocodingContext),
            moment = publicationTime,
            // Split source track is overridden to be IN_USE, as otherwise it would be set to DELETED.
            // (Which it actually already is in Geoviite, but a DELETED track cannot be referenced in Ratko).
            //
            // Split source track is also actually set to OLD instead of DELETED after a split has been pushed, but
            // it
            // cannot be set to OLD before using it with split source tracks.
            locationTrackStateOverride = RatkoLocationTrackState.IN_USE,
        )

        val publishedSwitchJoints =
            publicationDao.fetchPublishedSwitchJoints(ratkoSplit.publication.id, includeRemoved = false)

        val pushedTargetLocationTrackOids =
            ratkoSplit.split.targetLocationTracks
                .map { splitTarget ->
                    val (targetLocationTrack, targetGeometry) =
                        locationTrackService
                            .getWithGeometry(
                                LayoutContext.of(branch, PublicationState.OFFICIAL),
                                splitTarget.locationTrackId,
                            )
                            .let(::requireNotNull)

                    val targetLocationTrackExternalId =
                        oidMapping.getValue(splitTarget.locationTrackId).oid.let(::MainBranchRatkoExternalId)

                    RatkoSplitTargetTrack(
                        track = targetLocationTrack,
                        geometry = targetGeometry,
                        externalId = targetLocationTrackExternalId,
                        existingRatkoLocationTrack =
                            ratkoClient.getLocationTrack(RatkoOid(targetLocationTrackExternalId.oid)),
                        splitTarget = splitTarget,
                    )
                }
                .map { splitTargetTrack ->
                    pushSplitTarget(
                        branch,
                        splitSourceTrack,
                        splitTargetTrack,
                        ratkoSplit.split.relinkedSwitches,
                        publishedSwitchJoints,
                        publicationTime,
                    )
                }

        // Source track state is set to OLD after all split related updates are made.
        updateLocationTrackProperties(
            branch = branch,
            locationTrack = splitSourceTrack.track,
            locationTrackExternalId = splitSourceTrack.externalId,
            locationTrackStateOverride = RatkoLocationTrackState.OLD,
        )

        return pushedTargetLocationTrackOids
    }

    private fun pushSplitTarget(
        branch: LayoutBranch,
        splitSourceTrack: RatkoSplitSourceTrack,
        splitTargetTrack: RatkoSplitTargetTrack,
        splitRelinkedSwitches: List<IntId<LayoutSwitch>>,
        publishedSwitchJoints: Map<IntId<LayoutSwitch>, List<PublishedSwitchJoint>>,
        publicationTime: Instant,
    ): Oid<LocationTrack> {
        // The split target is required to be using the same TrackNumber as the split source track,
        // meaning that the split source track geocoding context can also be used when geocoding the target.
        val targetStartAndEnd =
            getSplitTargetTrackStartAndEndAddresses(
                    splitSourceTrack.geocodingContext,
                    splitSourceTrack.geometry,
                    splitTargetTrack.splitTarget,
                    splitTargetTrack.geometry,
                )
                .let { (start, end) -> requireNotNull(start) to requireNotNull(end) }

        val targetKmNumbers = getAllKmNumbers(splitTargetTrack.geometry, splitSourceTrack.geocodingContext)

        if (splitTargetTrack.existingRatkoLocationTrack == null) {
            createLocationTrack(
                branch,
                splitTargetTrack.track,
                splitTargetTrack.externalId,
                publicationTime,
                locationTrackOidOfGeometry = splitSourceTrack.externalId,
            )
        } else {
            if (splitTargetTrack.splitTarget.operation == SplitTargetOperation.TRANSFER) {
                // Due to a possibly mismatching geometry between Geoviite & Ratko, the track
                // kilometers containing relinked switches are updated for a TRANSFER target track.
                // If this is not done, the integration may fail further in the chain of operations
                // due to a missing switch joint address point in Ratko.
                pushSwitchKmsForSplitTransferTarget(
                    branch,
                    splitTargetTrack,
                    splitRelinkedSwitches,
                    publicationTime,
                    splitSourceTrack.geocodingContext,
                )
            }

            updateLocationTrack(
                branch,
                splitTargetTrack.track,
                splitTargetTrack.externalId,
                splitTargetTrack.existingRatkoLocationTrack.let(::requireNotNull),
                targetKmNumbers,
                publicationTime,
                locationTrackOidOfGeometry = splitSourceTrack.externalId,
                splitStartAndEnd = targetStartAndEnd,
            )
        }

        return splitTargetTrack.externalId.oid
    }

    private fun pushSwitchKmsForSplitTransferTarget(
        branch: LayoutBranch,
        splitTargetTrack: RatkoSplitTargetTrack,
        relinkedSwitches: List<IntId<LayoutSwitch>>,
        publicationTime: Instant,
        geocodingContext: GeocodingContext<ReferenceLineM>,
    ) {
        val switchesToUpdate =
            relinkedSwitches.filter { relinkedSwitch -> relinkedSwitch in splitTargetTrack.track.switchIds }

        val trackKmsToUpdate =
            switchesToUpdate
                .flatMap { switchId ->
                    splitTargetTrack.geometry.getSwitchLocations(switchId).mapNotNull { (_, point) ->
                        geocodingContext.toAddressPoint(point)?.first?.address?.kmNumber
                    }
                }
                .toSet()

        logger.info(
            "Updating geometry for transfer target track=${splitTargetTrack.track.id}, kmNumbers=$trackKmsToUpdate"
        )

        updateLocationTrack(
            branch,
            splitTargetTrack.track,
            splitTargetTrack.externalId,
            splitTargetTrack.existingRatkoLocationTrack.let(::requireNotNull),
            trackKmsToUpdate,
            publicationTime,
        )
    }

    fun pushLocationTrackChangesToRatko(
        branch: PushableLayoutBranch,
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
            .map { (locationTrack, changedKmNumbers) ->
                val externalId =
                    getFullExtIdAndManagePlanItem(
                        branch,
                        locationTrack.id as IntId,
                        locationTrack.designAssetState,
                        ratkoClient,
                        locationTrackDao::fetchExternalId,
                        locationTrackDao::savePlanItemId,
                    )
                requireNotNull(externalId) { "OID required for location track, lt=${locationTrack.id}" }
                try {
                    ratkoClient.getLocationTrack(RatkoOid(externalId.oid))?.let { existingLocationTrack ->
                        if (
                            locationTrack.state == LocationTrackState.DELETED ||
                                locationTrack.designAssetState == DesignAssetState.CANCELLED
                        ) {
                            deleteLocationTrack(
                                branch = branch.branch,
                                locationTrack = locationTrack,
                                locationTrackExternalId = externalId,
                                existingRatkoLocationTrack = existingLocationTrack,
                                moment = publicationTime,
                            )
                        } else {
                            updateLocationTrack(
                                branch = branch.branch,
                                locationTrack = locationTrack,
                                locationTrackExternalId = externalId,
                                existingRatkoLocationTrack = existingLocationTrack,
                                changedKmNumbers = changedKmNumbers,
                                moment = publicationTime,
                            )
                        }
                    }
                        ?: if (
                            locationTrack.state != LocationTrackState.DELETED &&
                                locationTrack.designAssetState != DesignAssetState.CANCELLED
                        ) {
                            createLocationTrack(branch.branch, locationTrack, externalId, publicationTime)
                        } else {
                            null
                        }
                } catch (ex: RatkoPushException) {
                    throw RatkoLocationTrackPushException(ex, locationTrack)
                }
                externalId.oid
            }
    }

    private fun getDesignOrInheritedTrackNumberOid(
        branch: LayoutBranch,
        trackNumberId: IntId<LayoutTrackNumber>,
    ): Oid<LayoutTrackNumber> {
        val ids = trackNumberDao.fetchExternalIdsByBranch(trackNumberId)
        return checkNotNull((ids[branch] ?: ids[LayoutBranch.main])?.oid) {
            "Official track number without oid, id=$trackNumberId"
        }
    }

    private fun getExternalId(branch: LayoutBranch, locationTrackId: IntId<LocationTrack>): Oid<LocationTrack>? =
        locationTrackDao.fetchExternalId(branch, locationTrackId)?.oid

    fun forceRedraw(locationTrackOids: Set<RatkoOid<RatkoLocationTrack>>) {
        if (locationTrackOids.isNotEmpty()) {
            ratkoClient.forceRatkoToRedrawLocationTrack(locationTrackOids)
        }
    }

    fun createLocationTrack(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        moment: Instant,
        locationTrackOidOfGeometry: MainBranchRatkoExternalId<LocationTrack>? = null,
    ) {
        try {
            val (addresses, jointPoints) = getLocationTrackPoints(branch, locationTrack, moment)

            val ratkoNodes = convertToRatkoNodeCollection(addresses)
            val trackNumberOid = getDesignOrInheritedTrackNumberOid(branch, locationTrack.trackNumberId)

            val duplicateOfOidLocationTrack =
                locationTrack.duplicateOf?.let { duplicateId -> getExternalId(branch, duplicateId) }
            val owner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId)

            val ratkoLocationTrack =
                convertToRatkoLocationTrack(
                    locationTrack = locationTrack,
                    locationTrackExternalId = locationTrackExternalId,
                    trackNumberOid = trackNumberOid,
                    nodeCollection = ratkoNodes,
                    duplicateOfOid = duplicateOfOidLocationTrack,
                    owner = owner,
                )
            checkNotNull(
                ratkoClient.newLocationTrack(ratkoLocationTrack, locationTrackOidOfGeometry?.oid?.let(::RatkoOid))
            ) {
                "Did not receive oid from Ratko for location track $ratkoLocationTrack"
            }

            val switchPoints =
                jointPoints.filterNot { jp ->
                    jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
                }

            val midPoints = (addresses.midPoints + switchPoints).sortedBy { p -> p.address }

            locationTrackOidOfGeometry?.let { referencedGeometryOid ->
                logger.info(
                    "Referenced geometry from track=$referencedGeometryOid was already set for ${locationTrackExternalId.oid}"
                )
            } ?: createLocationTrackPoints(RatkoOid(locationTrackExternalId.oid.toString()), midPoints)

            if (branch is MainBranch) {
                val allPoints = listOf(addresses.startPoint) + midPoints + listOf(addresses.endPoint)
                createLocationTrackMetadata(
                    branch,
                    locationTrack,
                    locationTrackExternalId,
                    allPoints,
                    trackNumberOid,
                    moment,
                )
            }
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.CREATE, ex)
        }
    }

    private fun createLocationTrackPoints(
        locationTrackOid: RatkoOid<RatkoLocationTrack>,
        addressPoints: Collection<AddressPoint<LocationTrackM>>,
    ) =
        toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
            ratkoClient.createLocationTrackPoints(locationTrackOid, points)
        }

    private fun createLocationTrackMetadata(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        alignmentPoints: List<AddressPoint<LocationTrackM>>,
        trackNumberOid: Oid<LayoutTrackNumber>,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>? = null,
    ) {
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, locationTrack.trackNumberId, moment)

        alignmentDao
            .fetchMetadata(locationTrack.getVersionOrThrow())
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
                                    locationTrackExternalId = locationTrackExternalId,
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
        points: List<AddressPoint<LocationTrackM>>,
        seek: TrackMeter,
        rounding: AddressRounding,
    ): AddressPoint<LocationTrackM> =
        when (rounding) {
            AddressRounding.UP -> points.find { p -> p.address >= seek }
            AddressRounding.DOWN -> points.findLast { p -> p.address <= seek }
        } ?: error("No address point found: seek=$seek rounding=$rounding")

    private fun deleteLocationTrack(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
        moment: Instant,
    ) {
        try {
            val deletedEndsPoints =
                existingRatkoLocationTrack.nodecollection?.let(::toNodeCollectionMarkingEndpointsNotInUse)

            updateLocationTrackProperties(
                branch = branch,
                locationTrack = locationTrack,
                locationTrackExternalId = locationTrackExternalId,
                changedNodeCollection = deletedEndsPoints,
            )

            ratkoClient.deleteLocationTrackPoints(RatkoOid(locationTrackExternalId.oid), null)
        } catch (ex: RatkoPushException) {
            throw ex
        } catch (ex: Exception) {
            throw RatkoPushException(RatkoPushErrorType.INTERNAL, RatkoOperation.DELETE, ex)
        }
    }

    private fun updateLocationTrack(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
        changedKmNumbers: Set<KmNumber>,
        moment: Instant,
        locationTrackStateOverride: RatkoLocationTrackState? = null,
        locationTrackOidOfGeometry: MainBranchRatkoExternalId<LocationTrack>? = null,
        splitStartAndEnd: Pair<TrackMeter, TrackMeter>? = null,
    ) {
        try {
            val locationTrackRatkoOid = RatkoOid<RatkoLocationTrack>(locationTrackExternalId.oid)
            val trackNumberOid = getDesignOrInheritedTrackNumberOid(branch, locationTrack.trackNumberId)

            val (addresses, jointPoints) = getLocationTrackPoints(branch, locationTrack, moment)
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
                locationTrack = locationTrack,
                locationTrackExternalId = locationTrackExternalId,
                changedNodeCollection = updatedEndPointNodeCollection,
                locationTrackStateOverride = locationTrackStateOverride,
            )

            if (locationTrackOidOfGeometry != null) {
                val (startAddress, endAddress) = splitStartAndEnd.let(::requireNotNull)
                ratkoClient.patchLocationTrackPoints(
                    sourceTrackExternalId = locationTrackOidOfGeometry,
                    targetTrackExternalId = MainBranchRatkoExternalId(locationTrackExternalId.oid),
                    startAddress = startAddress,
                    endAddress = endAddress,
                )
            } else {
                val allKms = addresses.allPoints.asSequence().map { point -> point.address.kmNumber }.toSet()
                val minTrackKm = allKms.min()
                val maxTrackKm = allKms.max()

                // This happens for example when a km-post has been removed in the "middle" of a track.
                val removedKmsWithinTrackBoundaries =
                    changedKmNumbers
                        .filterNot { changedKm -> changedKm in allKms }
                        .filter { kmNumber -> kmNumber > minTrackKm && kmNumber < maxTrackKm }
                        .toSet()

                deleteLocationTrackPoints(removedKmsWithinTrackBoundaries, locationTrackRatkoOid)
                updateLocationTrackGeometry(locationTrackOid = locationTrackRatkoOid, newPoints = changedMidPoints)
            }

            if (branch is MainBranch) {
                createLocationTrackMetadata(
                    branch = branch,
                    locationTrack = locationTrack,
                    locationTrackExternalId = locationTrackExternalId,
                    alignmentPoints = listOf(addresses.startPoint) + changedMidPoints + listOf(addresses.endPoint),
                    trackNumberOid = trackNumberOid,
                    moment = moment,
                    changedKmNumbers = changedKmNumbers,
                )
            }
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
        newPoints: Collection<AddressPoint<LocationTrackM>>,
    ) =
        toRatkoPointsGroupedByKm(newPoints).forEach { points ->
            ratkoClient.updateLocationTrackPoints(locationTrackOid, points)
        }

    private fun updateLocationTrackProperties(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        changedNodeCollection: RatkoNodes? = null,
        locationTrackStateOverride: RatkoLocationTrackState? = null,
    ) {
        val trackNumberOid = getDesignOrInheritedTrackNumberOid(branch, locationTrack.trackNumberId)
        val duplicateOfOidLocationTrack =
            locationTrack.duplicateOf?.let { duplicateId -> getExternalId(branch, duplicateId) }
        val owner = locationTrackService.getLocationTrackOwner(locationTrack.ownerId)

        val ratkoLocationTrack =
            convertToRatkoLocationTrack(
                locationTrack = locationTrack,
                locationTrackExternalId = locationTrackExternalId,
                trackNumberOid = trackNumberOid,
                nodeCollection = changedNodeCollection,
                duplicateOfOid = duplicateOfOidLocationTrack,
                owner = owner,
                locationTrackStateOverride = locationTrackStateOverride,
            )

        ratkoClient.updateLocationTrackProperties(ratkoLocationTrack)
    }

    private fun getLocationTrackPoints(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        moment: Instant,
    ): Pair<AlignmentAddresses<LocationTrackM>, List<AddressPoint<LocationTrackM>>> {
        val geocodingContext =
            geocodingService
                .getGeocodingContextCacheKey(branch, locationTrack.trackNumberId, moment)
                ?.let(geocodingService::getGeocodingContext)

        checkNotNull(geocodingContext) {
            "Missing geocoding context, trackNumberId=${locationTrack.trackNumberId} moment=$moment"
        }

        val geometry = alignmentDao.fetch(locationTrack.getVersionOrThrow())
        val addresses =
            checkNotNull(geocodingContext.getAddressPoints(geometry)) {
                "Cannot calculate addresses for location track, id=${locationTrack.id}"
            }

        return addresses to geocodingContext.getSwitchPoints(geometry)
    }
}

private fun getAllKmNumbers(
    geometry: DbLocationTrackGeometry,
    geocodingContext: GeocodingContext<ReferenceLineM>,
): Set<KmNumber> {
    return geocodingContext
        .getAddressPoints(geometry)
        .let(::requireNotNull)
        .allPoints
        .asSequence()
        .map { addressPoint -> addressPoint.address.kmNumber }
        .toSet()
}
