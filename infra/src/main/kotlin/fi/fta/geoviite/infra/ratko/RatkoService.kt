package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.getSplitTargetTrackStartAndEndAddresses
import fi.fta.geoviite.infra.integration.AllOids
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LocationTrackChange
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.integration.RatkoAssetType
import fi.fta.geoviite.infra.integration.RatkoOperation
import fi.fta.geoviite.infra.integration.RatkoPushErrorType
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.SwitchChange
import fi.fta.geoviite.infra.publication.Operation
import fi.fta.geoviite.infra.publication.PublicationDao
import fi.fta.geoviite.infra.publication.PublicationDetails
import fi.fta.geoviite.infra.publication.PublicationLogService
import fi.fta.geoviite.infra.publication.PublishedInDesign
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.publication.PublishedSwitch
import fi.fta.geoviite.infra.ratko.model.PushableDesignBranch
import fi.fta.geoviite.infra.ratko.model.PushableLayoutBranch
import fi.fta.geoviite.infra.ratko.model.PushableMainBranch
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferCreateRequest
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferDestinationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoPlanId
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoTrackMeter
import fi.fta.geoviite.infra.ratko.model.existingRatkoPlan
import fi.fta.geoviite.infra.ratko.model.newRatkoPlan
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import java.time.Duration
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpStatus
import org.springframework.web.reactive.function.client.WebClientResponseException

open class RatkoPushException(val type: RatkoPushErrorType, val operation: RatkoOperation, cause: Exception? = null) :
    RuntimeException(cause)

class RatkoSwitchPushException(exception: RatkoPushException, val switch: LayoutSwitch) :
    RatkoPushException(exception.type, exception.operation, exception)

class RatkoLocationTrackPushException(exception: RatkoPushException, val locationTrack: LocationTrack) :
    RatkoPushException(exception.type, exception.operation, exception)

class RatkoTrackNumberPushException(exception: RatkoPushException, val trackNumber: LayoutTrackNumber) :
    RatkoPushException(exception.type, exception.operation, exception)

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoService
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val ratkoLocationTrackService: RatkoLocationTrackService,
    private val ratkoRouteNumberService: RatkoRouteNumberService,
    private val ratkoAssetService: RatkoAssetService,
    private val ratkoPushDao: RatkoPushDao,
    private val ratkoClientConfiguration: RatkoClientConfiguration,
    private val publicationLogService: PublicationLogService,
    private val calculatedChangesService: CalculatedChangesService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val lockDao: LockDao,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
    private val splitService: SplitService,
    private val publicationDao: PublicationDao,
    private val layoutDesignDao: LayoutDesignDao,
    private val splitDao: SplitDao, // TODO use service level instead?
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(120)

    fun updateOperatingPointsFromRatko() {
        lockDao.runWithLock(DatabaseLock.RATKO_OPERATING_POINTS_FETCH, databaseLockDuration) {
            val points = ratkoClient.fetchOperatingPoints()
            ratkoOperatingPointDao.updateOperatingPoints(points)
        }
    }

    fun pushDesignChangesToRatko() {
        layoutDesignDao
            .list()
            .filter { design -> layoutDesignDao.designHasPublications(design.id as IntId) }
            .forEach { design -> pushChangesToRatko(DesignBranch.of(design.id as IntId)) }
    }

    fun pushChangesToRatko(layoutBranch: LayoutBranch) {
        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {

            // Kill off any pushes that have been stuck for too long, as it's likely failed and
            // state is hanging in DB
            ratkoPushDao.finishStuckPushes()

            if (previousPushStateIn(RatkoPushStatus.FAILED)) {
                logger.info("Ratko push cancelled because previous push is failed")
            } else if (ratkoClient.getRatkoOnlineStatus().connectionStatus != RatkoConnectionStatus.ONLINE) {
                logger.info("Ratko push cancelled because ratko connection is offline")
            } else {
                val lastPublicationMoment = ratkoPushDao.getLatestPushedPublicationMoment()

                // Inclusive search, therefore the already pushed one is also returned
                val publications =
                    publicationLogService
                        .fetchPublications(layoutBranch, lastPublicationMoment)
                        .filter { it.publicationTime > lastPublicationMoment }
                        .map { publicationLogService.getPublicationDetails(it.id) }

                if (publications.isNotEmpty()) {
                    pushChanges(layoutBranch, publications, calculatedChangesService.getAllOids(layoutBranch))
                }

                //                if (ratkoClientConfiguration.bulkTransfersEnabled) { // TODO
                // Enable this
                if (layoutBranch == LayoutBranch.main) {
                    manageRatkoBulkTransfers(layoutBranch)
                }

                //                }
            }
        }
    }

    fun manageRatkoBulkTransfers(branch: LayoutBranch, timeout: Duration = defaultBlockTimeout) {
        assertMainBranch(branch)

        splitService
            .findUnfinishedSplits(branch)
            .filter { split -> split.publicationId != null && split.publicationTime != null }
            .sortedWith(compareBy { split -> split.publicationTime ?: Instant.MAX })
            .firstOrNull()
            ?.let { split ->
                if (split.bulkTransfer?.state == BulkTransferState.CREATED && split.bulkTransfer.expeditedStart) {
                    expediteBulkTransferStart(split, timeout)
                } else {
                    split
                }
            }
            ?.let { split -> pollBulkTransferStateUpdate(split, timeout) }
            .takeIf { split -> split?.bulkTransfer?.state == BulkTransferState.PENDING }
            ?.let { split -> beginNewBulkTransfer(branch, split, timeout) }
    }

    fun expediteBulkTransferStart(split: Split, timeout: Duration): Split {
        requireNotNull(split.bulkTransfer) {
            "Bulk transfer expedited start was ran for a split which had bulkTransfer=null, splitId=${split.id}"
        }

        requireNotNull(split.bulkTransfer.ratkoBulkTransferId) {
            "Was about to expedite bulk transfer starting, but ratkoBulkTransferId was null for splitId=${split.id}!"
        }

        require(split.bulkTransfer.state == BulkTransferState.CREATED) {
            "Bulk transfer expedited start can only be accomplished to bulk transfers with CREATED status, " +
                "splitId=${split.id} has bulkTransferState=${split.bulkTransfer.state}"
        }

        withBulkTransferHttpErrorHandler(split, "expedited start") {
            //            ratkoClient.forceStartBulkTransfer(split.bulkTransfer.ratkoBulkTransferId,
            // timeout) // TODO Set the timeout based on input instead
            ratkoClient.forceStartBulkTransfer(
                split.bulkTransfer.ratkoBulkTransferId,
                Duration.ofMinutes(60),
            ) // TODO Set the timeout based on input instead again
            splitDao.updateBulkTransfer(
                splitId = split.id,
                temporaryFailure = false,
                bulkTransferState = BulkTransferState.IN_PROGRESS,
            )
        }

        return splitService.getOrThrow(split.id)
    }

    fun withBulkTransferHttpErrorHandler(split: Split, requestType: String, httpRequest: () -> Unit) {
        try {
            httpRequest()
        } catch (ex: IllegalStateException) {
            if (ex.cause is java.util.concurrent.TimeoutException) {
                logger.info("Bulk transfer request timed out, requestType=$requestType: $ex")
                handleTimeoutException(split)
            } else {
                throw ex
            }
        } catch (ex: WebClientResponseException) {
            logger.info("Bulk transfer request web client exception, requestType=$requestType: $ex")
            handleWebClientBulkTransferResponseException(split, ex)
        } catch (ex: Exception) {
            logger.info("Unhandled bulk transfer request exception, requestType=$requestType")
            throw ex
        }
    }

    fun pollBulkTransferStateUpdate(split: Split, timeout: Duration): Split {
        requireNotNull(split.bulkTransfer) {
            "Bulk transfer poll was ran for a split which had bulkTransfer=null, splitId=${split.id}"
        }

        val statesToPollDuring = listOf(BulkTransferState.CREATED, BulkTransferState.IN_PROGRESS)

        if (!statesToPollDuring.contains(split.bulkTransfer.state)) {
            logger.info(
                "Skipping bulk transfer state poll: split is not in a state that should be polled, " +
                    "splitId=${split.id}, bulkTransferState=${split.bulkTransfer.state})"
            )

            return split
        }

        checkNotNull(split.bulkTransfer.ratkoBulkTransferId) {
            "Was about to poll bulk transfer state for split, but ratkoBulkTransferId was null for splitId=${split.id}!"
        }

        withBulkTransferHttpErrorHandler(split, "poll") {
            ratkoClient.pollBulkTransferState(split.bulkTransfer.ratkoBulkTransferId, timeout).let {
                (polledState, response) ->
                val previouslyInProgress = split.bulkTransfer.state == BulkTransferState.IN_PROGRESS
                val bulkTransferState =
                    when {
                        // Do not overwrite an IN_PROGRESS bulk transfer state back to CREATED.
                        //
                        // the IN_PROGRESS state will be assigned when starting the bulk transfer
                        // forcefully.
                        //
                        // (Ratko status poll api may return data similar to CREATED even when the
                        // bulk
                        // transfer has been already started by the forceful start api)
                        previouslyInProgress && polledState == BulkTransferState.CREATED ->
                            BulkTransferState.IN_PROGRESS

                        else -> polledState
                    }

                splitDao.updateBulkTransfer(
                    splitId = split.id,
                    temporaryFailure = false,
                    bulkTransferState = bulkTransferState,
                    ratkoStartTime = response.locationTrackChange.startTime,
                    ratkoEndTime = response.locationTrackChange.endTime,
                    assetsTotal = response.locationTrackChange.assetsToMove,
                    assetsMoved = response.locationTrackChangeAssetsAmount,
                    trexAssetsTotal = response.locationTrackChange.trexAssets,
                    trexAssetsRemaining = response.remainingTrexAssets,
                )
            }
        }

        return splitService.getOrThrow(split.id)
    }

    fun beginNewBulkTransfer(branch: LayoutBranch, split: Split, timeout: Duration) {
        val request = newBulkTransferCreateRequest(branch, split)

        withBulkTransferHttpErrorHandler(split, "create") {
            ratkoClient.sendBulkTransferCreateRequest(request, timeout).let { (bulkTransferId, bulkTransferState) ->
                logger.info(
                    "Bulk transfer create request completed for splitId=${split.id}, bulkTransferId=$bulkTransferId"
                )

                splitDao.updateBulkTransfer(
                    splitId = split.id,
                    bulkTransferState = bulkTransferState,
                    ratkoBulkTransferId = bulkTransferId,
                    temporaryFailure = false,
                )
            }
        }
    }

    fun handleTimeoutException(split: Split) {
        if (!requireNotNull(split.bulkTransfer).temporaryFailure) {
            logger.info("Setting bulk transfer to temporarily failed for splitId=${split.id}")
            splitDao.updateBulkTransfer(splitId = split.id, temporaryFailure = true)
        } else {
            logger.info("Bulk transfer was already set to temporarily failed for splitId=${split.id}")
        }
    }

    fun handleWebClientBulkTransferResponseException(split: Split, ex: WebClientResponseException) {
        when (ex.statusCode) {
            HttpStatus.BAD_GATEWAY,
            HttpStatus.SERVICE_UNAVAILABLE,
            HttpStatus.GATEWAY_TIMEOUT -> {
                logger.info("Received HTTP temporary failure status, statusCode=${ex.statusCode}, splitId=${split.id}")
                logger.info("Response body: ${ex.responseBodyAsString}")
                logger.info("Setting split bulk transfer temporary failure status to true")
                splitDao.updateBulkTransfer(splitId = split.id, temporaryFailure = true)
            }
            else -> {
                logger.info("Unhandled or fatal HTTP error, statusCode=${ex.statusCode}")
                logger.info("Response body: ${ex.responseBodyAsString}")
                logger.info("Setting split bulk transfer state to ${BulkTransferState.FAILED}, splitId=${split.id}")
                splitDao.updateBulkTransfer(splitId = split.id, bulkTransferState = BulkTransferState.FAILED)
            }
        }
    }

    fun newBulkTransferCreateRequest(branch: LayoutBranch, split: Split): RatkoBulkTransferCreateRequest {
        val layoutContext = LayoutContext.of(branch, PublicationState.OFFICIAL)
        //        val sourceTrack = locationTrackService.getOrThrow(layoutContext,
        // split.sourceLocationTrackId)
        val (sourceTrack, sourceTrackAlignment) =
            locationTrackService.getWithAlignmentOrThrow(layoutContext, split.sourceLocationTrackId)

        val splitLocationTrackExternalIdMap =
            locationTrackDao.fetchExternalIds(
                layoutContext.branch,
                listOf(
                        split.sourceLocationTrackId.let(::listOf),
                        split.targetLocationTracks.map { locationTrack -> locationTrack.locationTrackId },
                    )
                    .flatten(),
            )

        val geocodingContext =
            requireNotNull(geocodingService.getGeocodingContext(layoutContext, sourceTrack.trackNumberId)) {
                "Geocoding context creating failed for layoutContext=$layoutContext, trackNumberId=${sourceTrack.trackNumberId}"
            }

        //        val splitTargetAlignments =
        //            locationTrackService.getAlignmentsForTracks()
        //                .getManyWithAlignments(layoutContext, split.targetLocationTracks.map { lt
        // -> lt.locationTrackId })
        //                .associateBy({ (track, _) -> track.id }, { trackAndAlignment ->
        // trackAndAlignment })

        //                .associateBy { trackAlignmentPair -> }
        //                .map { (locationTrack, alignment) ->
        ////                    val (start, end) = geocodingContext.getStartAndEnd(alignment)
        //                    val (startAddress, endAddress) =
        //                        getSplitTargetTrackStartAndEndAddresses(geocodingContext,
        // sourceTrackAlignment, )
        //
        //                    RatkoBulkTransferDestinationTrack(
        //                        oid =
        // requireNotNull(splitLocationTrackExternalIdMap[locationTrack.id]).oid,
        //                        startKmM = requireNotNull(start).address.let(::RatkoTrackMeter),
        //                        endKmM = requireNotNull(end).address.let(::RatkoTrackMeter),
        //                    )
        //                }

        val ratkoDestinationTracks =
            split.targetLocationTracks.map { splitTarget ->
                val (_, targetAlignment) =
                    locationTrackService.getWithAlignmentOrThrow(layoutContext, splitTarget.locationTrackId)

                val (startAddress, endAddress) =
                    getSplitTargetTrackStartAndEndAddresses(
                        geocodingContext,
                        sourceTrackAlignment,
                        splitTarget,
                        targetAlignment,
                    )

                RatkoBulkTransferDestinationTrack(
                    oid = requireNotNull(splitLocationTrackExternalIdMap[splitTarget.locationTrackId]).oid,
                    startKmM = requireNotNull(startAddress).let(::RatkoTrackMeter),
                    endKmM = requireNotNull(endAddress).let(::RatkoTrackMeter),
                )
            }

        return RatkoBulkTransferCreateRequest(
            sourceLocationTrack = requireNotNull(splitLocationTrackExternalIdMap[sourceTrack.id]).oid,
            destinationLocationTracks = ratkoDestinationTracks,
        )
    }

    fun retryLatestFailedPush(): Unit =
        // TODO Make sure this works in a world where there are multiple branches
        ratkoPushDao.fetchPreviousPush().let { previousPush ->
            check(previousPush.status == RatkoPushStatus.FAILED) {
                "Previous push is not in failed state, but in ${previousPush.status}"
            }

            ratkoPushDao.updatePushStatus(previousPush.id, RatkoPushStatus.MANUAL_RETRY)
        }

    fun pushLocationTracksToRatko(
        branch: LayoutBranch,
        locationTrackChanges: Collection<LocationTrackChange>,
        extIdsProvided: AllOids?,
    ) {
        assertMainBranch(branch)
        val pushableBranch = PushableMainBranch
        val extIds = extIdsProvided ?: calculatedChangesService.getAllOids(branch)

        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {
            val previousPush = ratkoPushDao.fetchPreviousPush()
            check(previousPush.status == RatkoPushStatus.SUCCESSFUL) {
                "Push all publications before pushing location track point manually"
            }

            check(ratkoClient.getRatkoOnlineStatus().connectionStatus == RatkoConnectionStatus.ONLINE) {
                "Ratko is offline"
            }

            // Here, we only care about current moment, but fix it to the latest publication DB
            // time, pushed or not
            val latestPublicationMoment = ratkoPushDao.getLatestPublicationMoment()
            val switchChanges =
                locationTrackChanges
                    .flatMap { locationTrackChange ->
                        getSwitchChangesByLocationTrack(
                            layoutBranch = branch,
                            locationTrackId = locationTrackChange.locationTrackId,
                            filterByKmNumbers = locationTrackChange.changedKmNumbers,
                            moment = latestPublicationMoment,
                            extIds = extIds,
                        )
                    }
                    .map { switchChange -> toFakePublishedSwitch(branch, switchChange, latestPublicationMoment) }

            val publishedLocationTrackChanges =
                locationTrackChanges.map { locationTrackChange ->
                    val locationTrack =
                        locationTrackService.getOfficialAtMoment(
                            branch = branch,
                            id = locationTrackChange.locationTrackId,
                            moment = latestPublicationMoment,
                        )
                    checkNotNull(locationTrack) {
                        "No location track exists with id ${locationTrackChange.locationTrackId} and timestamp $latestPublicationMoment"
                    }

                    // Fake PublishedLocationTrack, Ratko integration is built around published
                    // items
                    PublishedLocationTrack(
                        version =
                            checkNotNull(locationTrack.version) {
                                "Location track missing version, id=${locationTrackChange.locationTrackId}"
                            },
                        name = locationTrack.name,
                        trackNumberId = locationTrack.trackNumberId,
                        operation = Operation.MODIFY,
                        changedKmNumbers = locationTrackChange.changedKmNumbers,
                    )
                }

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(
                    pushableBranch,
                    publishedLocationTrackChanges,
                    latestPublicationMoment,
                )

            val distinctJoints =
                switchChanges.map { switchChange ->
                    switchChange.copy(
                        changedJoints = switchChange.changedJoints.distinctBy { changedJoint -> changedJoint.number }
                    )
                }
            ratkoAssetService.pushSwitchChangesToRatko(pushableBranch, distinctJoints, latestPublicationMoment)

            try {
                ratkoLocationTrackService.forceRedraw(
                    pushedLocationTrackOids.map { RatkoOid<RatkoLocationTrack>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }

            logger.info("Ratko push ready")
        }
    }

    private fun previousPushStateIn(vararg states: RatkoPushStatus?): Boolean {
        val previousPush = ratkoPushDao.fetchPreviousPush()
        return states.any { state -> previousPush.status == state }
    }

    private fun pushChanges(layoutBranch: LayoutBranch, publications: List<PublicationDetails>, extIds: AllOids) {
        val ratkoPushId = ratkoPushDao.startPushing(publications.map { it.id })
        logger.info("Starting ratko push id=$ratkoPushId")

        val lastPublicationTime = publications.maxOf { it.publicationTime }
        try {
            val pushableBranch =
                if (layoutBranch is DesignBranch) {
                    updatePlan(layoutBranch, publications)
                } else PushableMainBranch

            val pushedRouteNumberOids =
                ratkoRouteNumberService.pushTrackNumberChangesToRatko(
                    pushableBranch,
                    publications.flatMap { it.allPublishedTrackNumbers },
                    lastPublicationTime,
                )

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(
                    pushableBranch,
                    publications.flatMap { it.allPublishedLocationTracks },
                    lastPublicationTime,
                )

            pushSwitchChanges(
                layoutBranch = pushableBranch,
                publishedSwitches = publications.flatMap { it.allPublishedSwitches },
                publishedLocationTracks = publications.flatMap { it.allPublishedLocationTracks },
                publicationTime = lastPublicationTime,
                extIds = extIds,
            )

            ratkoPushDao.updatePushStatus(ratkoPushId, RatkoPushStatus.IN_PROGRESS_M_VALUES)

            try {
                ratkoRouteNumberService.forceRedraw(
                    pushedRouteNumberOids.map { RatkoOid<RatkoRouteNumber>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for route numbers $pushedRouteNumberOids")
            }

            try {
                ratkoLocationTrackService.forceRedraw(
                    pushedLocationTrackOids.map { RatkoOid<RatkoLocationTrack>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }

            ratkoPushDao.updatePushStatus(ratkoPushId, RatkoPushStatus.SUCCESSFUL)
        } catch (ex: Exception) {
            when (ex) {
                is RatkoTrackNumberPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.TRACK_NUMBER,
                        ex.trackNumber.id as IntId,
                    )

                is RatkoLocationTrackPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.LOCATION_TRACK,
                        ex.locationTrack.id as IntId,
                    )

                is RatkoSwitchPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.SWITCH,
                        ex.switch.id as IntId,
                    )
            }

            // dummy check if Ratko is online
            val pushStatus =
                if (ratkoClient.getRatkoOnlineStatus().connectionStatus == RatkoConnectionStatus.ONLINE)
                    RatkoPushStatus.FAILED
                else RatkoPushStatus.CONNECTION_ISSUE

            ratkoPushDao.updatePushStatus(ratkoPushId, pushStatus)

            throw ex
        }
    }

    private fun pushSwitchChanges(
        layoutBranch: PushableLayoutBranch,
        publishedSwitches: Collection<PublishedSwitch>,
        publishedLocationTracks: List<PublishedLocationTrack>,
        publicationTime: Instant,
        extIds: AllOids,
    ) {
        // Location track points are always removed per kilometre.
        // However, there is a slight chance that points used by switches (according to Geoviite)
        // will not match with the ones in Ratko.
        // Therefore, Geoviite will also update all switches with joints in the danger zone.
        val locationTrackSwitchChanges =
            publishedLocationTracks
                .flatMap { locationTrack ->
                    getSwitchChangesByLocationTrack(
                        layoutBranch = layoutBranch.branch,
                        locationTrackId = locationTrack.id,
                        filterByKmNumbers = locationTrack.changedKmNumbers,
                        moment = publicationTime,
                        extIds = extIds,
                    )
                }
                .map { switchChange -> toFakePublishedSwitch(layoutBranch.branch, switchChange, publicationTime) }

        val switchChanges = publishedSwitches + locationTrackSwitchChanges
        ratkoAssetService.pushSwitchChangesToRatko(layoutBranch, switchChanges, publicationTime)
    }

    private fun getSwitchChangesByLocationTrack(
        layoutBranch: LayoutBranch,
        locationTrackId: IntId<LocationTrack>,
        filterByKmNumbers: Collection<KmNumber>,
        moment: Instant,
        extIds: AllOids,
    ) =
        calculatedChangesService.getSwitchChangesFromChangedLocationTrackKmsByMoment(
            layoutBranch,
            locationTrackId,
            moment,
            extIds,
            filterByKmNumbers,
        )

    private fun toFakePublishedSwitch(
        layoutBranch: LayoutBranch,
        switchChange: SwitchChange,
        moment: Instant,
    ): PublishedSwitch {
        val switch = switchService.getOfficialAtMoment(layoutBranch, switchChange.switchId, moment)
        checkNotNull(switch) { "No switch exists with id ${switchChange.switchId} and timestamp $moment" }

        // Fake PublishedSwitch, Ratko integration is built around published items
        return PublishedSwitch(
            version = checkNotNull(switch.version) { "Switch missing version, id=${switchChange.switchId}" },
            trackNumberIds = emptySet(), // Ratko integration doesn't care about this field
            name = switch.name,
            operation = Operation.MODIFY,
            changedJoints = switchChange.changedJoints,
        )
    }

    private fun updatePlan(
        designBranch: DesignBranch,
        rangePublications: List<PublicationDetails>,
    ): PushableDesignBranch {
        val lastPublicationDesignVersion =
            requireNotNull(rangePublications.last().layoutBranch as? PublishedInDesign) {
                    "Expected publication ${rangePublications.last().id} to be published in a design"
                }
                .designVersion
        val lastPublicationDesign =
            layoutDesignDao.fetchVersion(RowVersion(designBranch.designId, lastPublicationDesignVersion))

        // An interrupted push can leave the design itself knowing its Ratko ID, but the most recent
        // publication pointing at a version before it was saved; so always look up the current ID
        val presentRatkoId = layoutDesignDao.fetch(designBranch.designId).ratkoId
        val ratkoId =
            if (presentRatkoId == null) {
                createPlanInRatko(lastPublicationDesign, designBranch.designId)
            } else {
                updatePlanInRatkoIfNeeded(
                    rangePublications,
                    designBranch,
                    lastPublicationDesignVersion,
                    lastPublicationDesign,
                    presentRatkoId,
                )
                presentRatkoId
            }
        return PushableDesignBranch(designBranch, ratkoId)
    }

    private fun updatePlanInRatkoIfNeeded(
        rangePublications: List<PublicationDetails>,
        designBranch: DesignBranch,
        lastPublicationDesignVersion: Int,
        lastPublicationDesign: LayoutDesign,
        presentRatkoId: RatkoPlanId,
    ) {
        val firstPublicationId = rangePublications.first().id
        val previouslyPublishedDesignVersion =
            publicationDao.getPreviouslyPublishedDesignVersion(firstPublicationId, designBranch.designId)
        if (previouslyPublishedDesignVersion != lastPublicationDesignVersion) {
            ratkoClient.updatePlan(existingRatkoPlan(lastPublicationDesign, presentRatkoId))
        }
    }

    private fun createPlanInRatko(lastPublicationDesign: LayoutDesign, designId: IntId<LayoutDesign>): RatkoPlanId =
        requireNotNull(ratkoClient.createPlan(newRatkoPlan(lastPublicationDesign))) { "Expected plan ID from Ratko" }
            .also { ratkoId -> layoutDesignDao.initializeRatkoId(designId, ratkoId) }
}
