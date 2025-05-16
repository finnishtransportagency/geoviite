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
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.AlignmentAddresses
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.getSplitTargetTrackStartAndEndAddresses
import fi.fta.geoviite.infra.geometry.CreateVerticalGeometryListingCsvTask
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.ratko.model.PushableLayoutBranch
import fi.fta.geoviite.infra.ratko.model.PushableMainBranch
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrackState
import fi.fta.geoviite.infra.ratko.model.RatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.RatkoNodes
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.convertToRatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.convertToRatkoMetadataAsset
import fi.fta.geoviite.infra.ratko.model.convertToRatkoNodeCollection
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSegmentMetadata
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
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
    createVerticalGeometryListingCsvTask: CreateVerticalGeometryListingCsvTask,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun pushSplits(splits: List<Pair<Split, RatkoLocationTrack>>, publicationTime: Instant): List<Oid<LocationTrack>> {
        val layoutContext = LayoutContext.of(LayoutBranch.main, PublicationState.OFFICIAL)
        val pushableBranch = PushableMainBranch

        val oidMapping =
            locationTrackDao.fetchExternalIds(
                layoutContext.branch,
                splits.flatMap { (split, _) -> split.locationTracks },
            )

        val asd =
            splits.map { (split, existingRatkoLocationTrack) ->

                // TODO Doesn't seem like the correct place
                val (sourceTrack, sourceTrackAlignment) =
                    locationTrackService.getWithAlignment(split.sourceLocationTrackVersion)

                val sourceTrackGeocodingContext =
                    geocodingService.getGeocodingContext(layoutContext, sourceTrack.trackNumberId).let(::requireNotNull)

                // Refresh the state of the source track
                updateEntireLocationTrack(
                    pushableBranch,
                    layoutContext,
                    publicationTime,
                    split.sourceLocationTrackVersion,
                    existingRatkoLocationTrack,
                )

                split.targetLocationTracks.map { splitTarget ->
                    val externalLocationTrackId =
                        MainBranchRatkoExternalId(
                            oid = oidMapping[splitTarget.locationTrackId]?.oid.let(::requireNotNull)
                        )

                    when (splitTarget.operation) {
                        // TODO Tässä tuli vastaan pulma, että ratkossa oli jo OID olemassa n+1 ajokerralla.
                        // Mikäköhän mahtaisi olla oikea toimintaperiaate...
                        //                        SplitTargetOperation.CREATE -> {
                        //                            createLocationTrack(
                        //                                layoutContext.branch,
                        //                                locationTrackDao.get(layoutContext,
                        // splitTarget.locationTrackId).let(::requireNotNull),
                        //                                externalLocationTrackId,
                        //                                publicationTime,
                        //                                sendGeometry = false,
                        //                            )
                        //                        }

                        SplitTargetOperation.CREATE,
                        SplitTargetOperation.OVERWRITE,
                        SplitTargetOperation.TRANSFER -> {

                            // TODO Ensimmäisellä CREATE kutsulla tämä tod näk epäonnistuu?
                            // -> Pitää iffitellä oliko se olemassa ja kutsua mahdollisesti createa silloin
                            val existingRatkoLocationTrack =
                                ratkoClient.getLocationTrack(RatkoOid(externalLocationTrackId.oid))

                            // TODO Move the existing fetch up the chain so that the operation to be executed is already
                            // known here
                            if (existingRatkoLocationTrack == null) {
                                createLocationTrack(
                                    layoutContext.branch,
                                    locationTrackDao
                                        .get(layoutContext, splitTarget.locationTrackId)
                                        .let(::requireNotNull),
                                    externalLocationTrackId,
                                    publicationTime,
                                    sendGeometry = false,
                                )
                            } else {
                                val alignmentAddresses =
                                    geocodingService.getAddressPoints(layoutContext, splitTarget.locationTrackId)

                                requireNotNull(alignmentAddresses) {
                                    "Address points were undetermined, lt=${splitTarget.locationTrackId}"
                                }

                                val allKmNumbers =
                                    alignmentAddresses.allPoints
                                        .asSequence()
                                        .map { addressPoint -> addressPoint.address.kmNumber }
                                        .toSet()

                                updateLocationTrack(
                                    layoutContext.branch,
                                    locationTrackDao
                                        .get(layoutContext, splitTarget.locationTrackId)
                                        .let(::requireNotNull),
                                    externalLocationTrackId,
                                    existingRatkoLocationTrack,
                                    allKmNumbers,
                                    publicationTime,
                                    sendGeometry = false,
                                )
                            }
                        }
                    }

                    // TODO Change the call to only get the alignment?
                    // TODO ...or better: batch the call with getAlignmentsForTracks or something
                    val (_, splitTargetAlignment) =
                        locationTrackService
                            .getWithAlignment(layoutContext, splitTarget.locationTrackId)
                            .let(::requireNotNull)

                    val (startAddress, endAddress) =
                        getSplitTargetTrackStartAndEndAddresses(
                            sourceTrackGeocodingContext,
                            sourceTrackAlignment,
                            splitTarget,
                            splitTargetAlignment,
                        )

                    ratkoClient.patchLocationTrackPoints(
                        sourceTrackOid = RatkoOid(oidMapping[split.sourceLocationTrackId]?.oid.let(::requireNotNull)),
                        targetTrackOid = RatkoOid(oidMapping[splitTarget.locationTrackId]?.oid.let(::requireNotNull)),
                        startAddress = requireNotNull(startAddress),
                        endAddress = requireNotNull(endAddress),
                    )
                }

                // TODO Create a named function for setting the state to "OLD"?
                updateLocationTrackProperties(
                    branch = layoutContext.branch,
                    locationTrack = sourceTrack,
                    locationTrackExternalId =
                        MainBranchRatkoExternalId(
                            oidMapping[sourceTrack.id]?.oid.let(::requireNotNull)
                        ), // TODO Should probably not be created here
                    locationTrackStateOverride = RatkoLocationTrackState.OLD,
                )
            }

        return emptyList() // TODO
    }

    private fun updateEntireLocationTrack(
        pushableBranch: PushableLayoutBranch,
        layoutContext: LayoutContext, // TODO Only one of these should be enough right?
        publicationTime: Instant,
        locationTrackVersion: LayoutRowVersion<LocationTrack>,
        existingRatkoLocationTrack: RatkoLocationTrack,
    ) {
        val sourceLocationTrack = locationTrackDao.fetch(locationTrackVersion)

        val externalId =
            getFullExtIdAndManagePlanItem(
                branch = pushableBranch,
                sourceLocationTrack.id as IntId,
                sourceLocationTrack.designAssetState,
                ratkoClient,
                locationTrackDao::fetchExternalId,
                locationTrackDao::savePlanItemId,
            )

        requireNotNull(externalId) { "OID required for location track, lt=${sourceLocationTrack.id}" }

        // TODO These could be passed and fetched with the actual time (similarly to what the
        // updateLocationTrack is doing)
        val alignmentAddresses = geocodingService.getAddressPoints(layoutContext, sourceLocationTrack.id)
        requireNotNull(alignmentAddresses) { "Address points were undetermined, lt=${sourceLocationTrack.id}" }

        val allKmNumbers =
            alignmentAddresses.allPoints.asSequence().map { addressPoint -> addressPoint.address.kmNumber }.toSet()

        updateLocationTrack(
            branch = pushableBranch.branch,
            locationTrack = sourceLocationTrack,
            locationTrackExternalId = externalId,
            existingRatkoLocationTrack = existingRatkoLocationTrack,
            changedKmNumbers = allKmNumbers,
            moment = publicationTime,
            locationTrackStateOverride = RatkoLocationTrackState.IN_USE,
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

    private fun createLocationTrack(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        moment: Instant,
        sendGeometry: Boolean = true, // TODO Should probably not be here
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
                    descriptionGetter = { track ->
                        locationTrackService.getFullDescription(branch.official, track, LocalizationLanguage.FI)
                    },
                    owner = owner,
                )
            checkNotNull(ratkoClient.newLocationTrack(ratkoLocationTrack)) {
                "Did not receive oid from Ratko for location track $ratkoLocationTrack"
            }

            val switchPoints =
                jointPoints.filterNot { jp ->
                    jp.address == addresses.startPoint.address || jp.address == addresses.endPoint.address
                }

            val midPoints = (addresses.midPoints + switchPoints).sortedBy { p -> p.address }
            if (sendGeometry) {
                createLocationTrackPoints(RatkoOid(locationTrackExternalId.oid.toString()), midPoints)
            } else {
                logger.info("Did not send geometry for locationTrack=${locationTrackExternalId.oid}")
            }

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
        addressPoints: Collection<AddressPoint>,
    ) =
        toRatkoPointsGroupedByKm(addressPoints).forEach { points ->
            ratkoClient.createLocationTrackPoints(locationTrackOid, points)
        }

    private fun createLocationTrackMetadata(
        branch: LayoutBranch,
        locationTrack: LocationTrack,
        locationTrackExternalId: FullRatkoExternalId<LocationTrack>,
        alignmentPoints: List<AddressPoint>,
        trackNumberOid: Oid<LayoutTrackNumber>,
        moment: Instant,
        changedKmNumbers: Set<KmNumber>? = null,
    ) {
        val geocodingContext = geocodingService.getGeocodingContextAtMoment(branch, locationTrack.trackNumberId, moment)

        alignmentDao
            .fetchMetadata(locationTrack.getAlignmentVersionOrThrow())
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
        sendGeometry: Boolean = true,
        locationTrackStateOverride: RatkoLocationTrackState? = null,
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

            if (sendGeometry) { // TODO Remove the true from here, it will always send geometry!
                deleteLocationTrackPoints(changedKmNumbers, locationTrackRatkoOid)
                updateLocationTrackGeometry(locationTrackOid = locationTrackRatkoOid, newPoints = changedMidPoints)
            } else {
                logger.info("Skipping geometry update for locationTrack=${locationTrack.id}")
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
        newPoints: Collection<AddressPoint>,
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
                descriptionGetter = { track ->
                    locationTrackService.getFullDescription(branch.official, track, LocalizationLanguage.FI)
                },
                owner = owner,
                locationTrackStateOverride = locationTrackStateOverride,
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
