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
import fi.fta.geoviite.infra.geocoding.getSplitTargetTrackStartAndEndAddresses
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
import fi.fta.geoviite.infra.ratko.model.convertToRatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.convertToRatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.split.SplitTargetOperation
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import java.time.Instant

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
        pushableBranch: PushableLayoutBranch,
        splitsWithRatkoSourceTrack: List<Pair<Split, RatkoLocationTrack>>,
        publicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        val layoutContext = LayoutContext.of(pushableBranch.branch, PublicationState.OFFICIAL)

        val oidMapping =
            locationTrackDao.fetchExternalIds(
                layoutContext.branch,
                splitsWithRatkoSourceTrack.flatMap { (split, _) -> split.locationTracks },
            )

        return splitsWithRatkoSourceTrack.flatMap { (split, sourceRatkoLocationTrack) ->
            pushSingleSplit(layoutContext, pushableBranch, split, sourceRatkoLocationTrack, oidMapping, publicationTime)
        }
    }

    private fun pushSingleSplit(
        layoutContext: LayoutContext,
        pushableBranch: PushableLayoutBranch,
        split: Split,
        sourceRatkoLocationTrack: RatkoLocationTrack,
        oidMapping: Map<IntId<LocationTrack>, RatkoExternalId<LocationTrack>>,
        publicationTime: Instant,
    ): List<Oid<LocationTrack>> {
        // The state of the source track is refreshed before pushing any split target tracks as the geometry is being
        // referenced for target tracks from the source track (which may not be up-to-date within Ratko).
        val (sourceTrack, sourceTrackGeometry) = locationTrackService.getWithGeometry(split.sourceLocationTrackVersion)
        val sourceTrackExternalId = MainBranchRatkoExternalId(oidMapping[sourceTrack.id]?.oid.let(::requireNotNull))

        val sourceTrackGeocodingContext =
            geocodingService
                .getGeocodingContextAtMoment(pushableBranch.branch, sourceTrack.trackNumberId, publicationTime)
                .let(::requireNotNull)

        updateLocationTrack(
            branch = pushableBranch.branch,
            locationTrack = sourceTrack,
            locationTrackExternalId = sourceTrackExternalId,
            existingRatkoLocationTrack = sourceRatkoLocationTrack,
            changedKmNumbers = getAllKmNumbers(sourceTrackGeometry, sourceTrackGeocodingContext),
            moment = publicationTime,
            // Split source track is overridden to be IN_USE, as otherwise it would be set to DELETED.
            // (Which it actually already is in Geoviite, but a DELETED track cannot be referenced in Ratko).
            //
            // Split source track is also actually set to OLD instead of DELETED after a split has been pushed, but it
            // cannot be set to OLD before using it with split source tracks.
            locationTrackStateOverride = RatkoLocationTrackState.IN_USE,
        )

        val publishedSwitchJoints =
            split.publicationId.let(::requireNotNull).let { publicationId ->
                publicationDao.fetchPublishedSwitchJoints(publicationId, includeRemoved = false)
            }

        val pushedTargetLocationTrackOids =
            split.targetLocationTracks.map { splitTarget ->
                val targetLocationTrackExternalId =
                    oidMapping[splitTarget.locationTrackId]?.oid.let(::requireNotNull).let(::MainBranchRatkoExternalId)

                pushSplitTarget(
                    layoutContext,
                    sourceTrackExternalId,
                    sourceTrackGeometry,
                    sourceTrackGeocodingContext,
                    targetLocationTrackExternalId,
                    splitTarget,
                    split.relinkedSwitches,
                    publishedSwitchJoints,
                    publicationTime,
                )
            }

        // Source track state is set to OLD after all split related updates are made.
        updateLocationTrackProperties(
            branch = layoutContext.branch,
            locationTrack = sourceTrack,
            locationTrackExternalId = sourceTrackExternalId,
            locationTrackStateOverride = RatkoLocationTrackState.OLD,
        )

        return pushedTargetLocationTrackOids
    }

    private fun pushSplitTarget(
        layoutContext: LayoutContext,
        sourceTrackExternalId: MainBranchRatkoExternalId<LocationTrack>,
        sourceTrackGeometry: DbLocationTrackGeometry,
        sourceTrackGeocodingContext: GeocodingContext<ReferenceLineM>,
        targetLocationTrackExternalId: MainBranchRatkoExternalId<LocationTrack>,
        splitTarget: SplitTarget,
        splitRelinkedSwitches: List<IntId<LayoutSwitch>>,
        publishedSwitchJoints: Map<IntId<LayoutSwitch>, List<PublishedSwitchJoint>>,
        publicationTime: Instant,
    ): Oid<LocationTrack> {
        val existingRatkoLocationTrack = ratkoClient.getLocationTrack(RatkoOid(targetLocationTrackExternalId.oid))

        val (targetLocationTrack, targetGeometry) =
            locationTrackService.getWithGeometry(layoutContext, splitTarget.locationTrackId).let(::requireNotNull)

        // The split target is required to be using the same TrackNumber as the split source track,
        // meaning that the split source track geocoding context can also be used when geocoding the target.
        val targetStartAndEnd =
            getSplitTargetTrackStartAndEndAddresses(
                    sourceTrackGeocodingContext,
                    sourceTrackGeometry,
                    splitTarget,
                    targetGeometry,
                )
                .let { (start, end) -> requireNotNull(start) to requireNotNull(end) }

        val targetKmNumbers = getAllKmNumbers(targetGeometry, sourceTrackGeocodingContext)

        if (existingRatkoLocationTrack == null) {
            createLocationTrack(
                layoutContext.branch,
                targetLocationTrack,
                targetLocationTrackExternalId,
                publicationTime,
                locationTrackOidOfGeometry = sourceTrackExternalId,
            )
        } else {
            if (splitTarget.operation == SplitTargetOperation.TRANSFER) {
                // Due to a possibly mismatching geometry between Geoviite & Ratko, the track
                // kilometers containing relinked switches are updated for a TRANSFER target track.
                // If this is not done, the integration may fail further in the chain of operations
                // due to a missing switch joint address point in Ratko.
                pushSwitchKmsForSplitTransferTarget(
                    layoutContext,
                    splitRelinkedSwitches,
                    targetLocationTrack,
                    targetLocationTrackExternalId,
                    existingRatkoLocationTrack,
                    publicationTime,
                    publishedSwitchJoints,
                )
            }

            updateLocationTrack(
                layoutContext.branch,
                targetLocationTrack,
                targetLocationTrackExternalId,
                existingRatkoLocationTrack,
                targetKmNumbers,
                publicationTime,
                locationTrackOidOfGeometry = sourceTrackExternalId,
                splitStartAndEnd = targetStartAndEnd,
            )
        }

        return targetLocationTrackExternalId.oid
    }

    private fun pushSwitchKmsForSplitTransferTarget(
        layoutContext: LayoutContext,
        relinkedSwitches: List<IntId<LayoutSwitch>>,
        targetLocationTrack: LocationTrack,
        targetLocationTrackExternalId: MainBranchRatkoExternalId<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
        publicationTime: Instant,
        publishedSwitchJoints: Map<IntId<LayoutSwitch>, List<PublishedSwitchJoint>>,
    ) {
        val switchesToUpdate =
            relinkedSwitches.filter { relinkedSwitch -> relinkedSwitch in targetLocationTrack.switchIds }

        val trackKmsToUpdate =
            switchesToUpdate
                .flatMap { switchId ->
                    publishedSwitchJoints
                        .getValue(switchId)
                        .map { joint -> joint.address }
                        .map { address -> address.kmNumber }
                }
                .toSet()

        logger.info(
            "Updating geometry for transfer target track=${targetLocationTrack.id}, kmNumbers=$trackKmsToUpdate"
        )

        updateLocationTrack(
            layoutContext.branch,
            targetLocationTrack,
            targetLocationTrackExternalId,
            existingRatkoLocationTrack,
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

            locationTrackOidOfGeometry?.let { referencedGeometryOid ->
                val (startAddress, endAddress) = splitStartAndEnd.let(::requireNotNull)
                ratkoClient.patchLocationTrackPoints(
                    sourceTrackExternalId = referencedGeometryOid,
                    targetTrackExternalId = MainBranchRatkoExternalId(locationTrackExternalId.oid),
                    startAddress = startAddress,
                    endAddress = endAddress,
                )
            }
                ?: run {
                    deleteLocationTrackPoints(changedKmNumbers, locationTrackRatkoOid)
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
