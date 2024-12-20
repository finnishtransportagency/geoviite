package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.assertMainBranch
import fi.fta.geoviite.infra.geocoding.GeocodingService
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
import fi.fta.geoviite.infra.publication.PublicationDetails
import fi.fta.geoviite.infra.publication.PublicationLogService
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.publication.PublishedSwitch
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferDestinationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoBulkTransferStartRequest
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoTrackMeter
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitDao
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import java.time.Duration
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

open class RatkoPushException(val type: RatkoPushErrorType, val operation: RatkoOperation, cause: Exception? = null) :
    RuntimeException(cause)

class RatkoSwitchPushException(exception: RatkoPushException, val switch: TrackLayoutSwitch) :
    RatkoPushException(exception.type, exception.operation, exception)

class RatkoLocationTrackPushException(exception: RatkoPushException, val locationTrack: LocationTrack) :
    RatkoPushException(exception.type, exception.operation, exception)

class RatkoTrackNumberPushException(exception: RatkoPushException, val trackNumber: TrackLayoutTrackNumber) :
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

    fun pushChangesToRatko(layoutBranch: LayoutBranch) {
        assertMainBranch(layoutBranch)

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
                manageRatkoBulkTransfers(layoutBranch)
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
            ?.let { split -> pollBulkTransferStateUpdate(split, timeout) }
            .takeIf { split -> split?.bulkTransfer?.state == BulkTransferState.PENDING }
            ?.let { split -> beginNewBulkTransfer(branch, split, timeout) }
    }

    fun pollBulkTransferStateUpdate(split: Split, timeout: Duration): Split {
        if (split.bulkTransfer?.state != BulkTransferState.IN_PROGRESS) {
            logger.info(
                "Skipping bulk transfer state poll: split is not in progress (current state=${split.bulkTransfer?.state})"
            )

            return split
        }

        checkNotNull(split.bulkTransfer.ratkoBulkTransferId) {
            error("Was about to poll bulk transfer state for split=${split.id}, but bulkTransferId was null!")
        }

        val oldState = split.bulkTransfer.state
        val newState = ratkoClient.pollBulkTransferState(split.bulkTransfer.ratkoBulkTransferId, timeout)

        return if (newState != oldState) {
            logger.info("Updating split=${split.id} bulkTransferState from $oldState to $newState")
            splitDao.updateBulkTransfer(splitId = split.id, bulkTransferState = newState)
            splitService.getOrThrow(split.id)
        } else {
            logger.info("Split=${split.id} still has the same bulkTransferState=$oldState")
            split
        }
    }

    fun beginNewBulkTransfer(branch: LayoutBranch, split: Split, timeout: Duration) {
        val request = createBulkTransferStartRequest(branch, split)

        ratkoClient.startNewBulkTransfer(request, timeout).let { (bulkTransferId, bulkTransferState) ->
            splitDao.updateBulkTransfer(
                splitId = split.id,
                bulkTransferState = bulkTransferState,
                ratkoBulkTransferId = bulkTransferId,
            )
        }
    }

    fun createBulkTransferStartRequest(branch: LayoutBranch, split: Split): RatkoBulkTransferStartRequest {
        val layoutContext = LayoutContext.of(branch, PublicationState.OFFICIAL)

        val splitSourceLocationTrack = locationTrackService.getOrThrow(layoutContext, split.sourceLocationTrackId)

        val splitLocationTrackOidMap =
            locationTrackDao.fetchExternalIds(
                layoutContext.branch,
                listOf(
                        split.sourceLocationTrackId.let(::listOf),
                        split.targetLocationTracks.map { locationTrack -> locationTrack.locationTrackId },
                    )
                    .flatten(),
            )

        val geocodingContext =
            geocodingService
                .getGeocodingContext(layoutContext, splitSourceLocationTrack.trackNumberId)
                .let(::requireNotNull)

        val ratkoDestinationTracks =
            locationTrackService
                .getManyWithAlignments(layoutContext, split.targetLocationTracks.map { lt -> lt.locationTrackId })
                .map { (locationTrack, alignment) ->
                    val (start, end) = geocodingContext.getStartAndEnd(alignment)

                    RatkoBulkTransferDestinationTrack(
                        oid = requireNotNull(splitLocationTrackOidMap[locationTrack.id]),
                        startKmM = requireNotNull(start).address.let(::RatkoTrackMeter),
                        endKmM = requireNotNull(end).address.let(::RatkoTrackMeter),
                    )
                }

        return RatkoBulkTransferStartRequest(
            sourceLocationTrack = requireNotNull(splitLocationTrackOidMap[splitSourceLocationTrack.id]),
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
                    branch,
                    publishedLocationTrackChanges,
                    latestPublicationMoment,
                )

            val distinctJoints =
                switchChanges.map { switchChange ->
                    switchChange.copy(
                        changedJoints = switchChange.changedJoints.distinctBy { changedJoint -> changedJoint.number }
                    )
                }
            ratkoAssetService.pushSwitchChangesToRatko(branch, distinctJoints, latestPublicationMoment)

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
            val pushedRouteNumberOids =
                ratkoRouteNumberService.pushTrackNumberChangesToRatko(
                    layoutBranch,
                    publications.flatMap { it.allPublishedTrackNumbers },
                    lastPublicationTime,
                )

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(
                    layoutBranch,
                    publications.flatMap { it.allPublishedLocationTracks },
                    lastPublicationTime,
                )

            pushSwitchChanges(
                layoutBranch = layoutBranch,
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
        layoutBranch: LayoutBranch,
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
                        layoutBranch = layoutBranch,
                        locationTrackId = locationTrack.id,
                        filterByKmNumbers = locationTrack.changedKmNumbers,
                        moment = publicationTime,
                        extIds = extIds,
                    )
                }
                .map { switchChange -> toFakePublishedSwitch(layoutBranch, switchChange, publicationTime) }

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
        calculatedChangesService
            .getAllSwitchChangesByLocationTrackAtMoment(layoutBranch, locationTrackId, moment, extIds)
            .map { switchChanges ->
                switchChanges.copy(
                    changedJoints =
                        switchChanges.changedJoints.filter { changedJoint ->
                            filterByKmNumbers.contains(changedJoint.address.kmNumber)
                        }
                )
            }
            .filter { it.changedJoints.isNotEmpty() }

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
}
