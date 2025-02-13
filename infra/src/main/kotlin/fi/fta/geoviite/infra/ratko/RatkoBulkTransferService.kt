package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.getSplitTargetTrackStartAndEndAddresses
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferCreateRequest
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferDestinationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoTrackMeter
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
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

@GeoviiteService
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoBulkTransferService
@Autowired
constructor(
    private val ratkoClient: RatkoClient,
    private val ratkoPushDao: RatkoPushDao,
    private val splitDao: SplitDao, // TODO use service level instead?
    private val splitService: SplitService,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao, // TODO Use a service?
    private val locationTrackService: LocationTrackService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
            ?.takeIf(::previousRatkoPushHasNotFailed)
            .takeIf { split -> split?.bulkTransfer?.state == BulkTransferState.PENDING }
            ?.let { split -> beginNewBulkTransfer(branch, split, timeout) }
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

    fun previousRatkoPushHasNotFailed(split: Split): Boolean {
        return ratkoPushDao.fetchPreviousPush().let { previousPush ->
            if (previousPush.status == RatkoPushStatus.FAILED) {
                logger.info(
                    "Previous ratkoPushId=${previousPush.id}) has FAILED," +
                        "skipping bulk transfer creation for splitId=${split.id}"
                )

                false
            } else {
                true
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
                // TODO Should this use dao directly?
                splitDao.updateBulkTransfer(splitId = split.id, bulkTransferState = BulkTransferState.FAILED)
            }
        }
    }
}
