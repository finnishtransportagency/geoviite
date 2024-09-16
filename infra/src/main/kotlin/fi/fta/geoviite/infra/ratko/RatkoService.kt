package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.assertMainBranch
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
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublishedLocationTrack
import fi.fta.geoviite.infra.publication.PublishedSwitch
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
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
    private val publicationService: PublicationService,
    private val calculatedChangesService: CalculatedChangesService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val lockDao: LockDao,
    private val ratkoOperatingPointDao: RatkoOperatingPointDao,
    private val splitService: SplitService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(120)

    fun updateOperatingPointsFromRatko() {
        lockDao.runWithLock(DatabaseLock.RATKO_OPERATING_POINTS_FETCH, databaseLockDuration) {
            val points = ratkoClient.fetchOperatingPoints()
            ratkoOperatingPointDao.updateOperatingPoints(points)
        }
    }

    fun pushChangesToRatko(layoutBranch: LayoutBranch, retryFailed: Boolean = true) {
        assertMainBranch(layoutBranch)

        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {

            // Kill off any pushes that have been stuck for too long, as it's likely failed and
            // state is hanging in DB
            ratkoPushDao.finishStuckPushes()

            if (!retryFailed && previousPushStateIn(RatkoPushStatus.FAILED)) {
                logger.info("Ratko push cancelled because previous push is failed")
            } else if (!ratkoClient.getRatkoOnlineStatus().isOnline) {
                logger.info("Ratko push cancelled because ratko connection is offline")
            } else {
                val lastPublicationMoment = ratkoPushDao.getLatestPushedPublicationMoment()

                // Inclusive search, therefore the already pushed one is also returned
                val publications =
                    publicationService
                        .fetchPublications(layoutBranch, lastPublicationMoment)
                        .filter { it.publicationTime > lastPublicationMoment }
                        .map { publicationService.getPublicationDetails(it.id) }

                if (publications.isNotEmpty()) {
                    pushChanges(layoutBranch, publications)
                }

                if (ratkoClientConfiguration.bulkTransfersEnabled) {
                    manageRatkoBulkTransfers(layoutBranch)
                }
            }
        }
    }

    fun manageRatkoBulkTransfers(branch: LayoutBranch) {
        assertMainBranch(branch)

        splitService
            .findUnfinishedSplits(branch)
            .filter { split -> split.publicationId != null && split.publicationTime != null }
            .sortedWith(compareBy { split -> split.publicationTime ?: Instant.MAX })
            .firstOrNull()
            ?.let { split -> pollBulkTransferStateUpdate(branch, split) }
            .takeIf { split ->
                split?.bulkTransferState in listOf(BulkTransferState.PENDING, BulkTransferState.TEMPORARY_FAILURE)
            }
            ?.let { split -> beginNewBulkTransfer(branch, split) }
    }

    fun pollBulkTransferStateUpdate(branch: LayoutBranch, split: Split): Split {
        if (split.bulkTransferState != BulkTransferState.IN_PROGRESS) {
            logger.info(
                "Skipping bulk transfer state poll: split is not in progress (current state=${split.bulkTransferState})"
            )

            return split
        }

        checkNotNull(split.bulkTransferId) {
            error("Was about to poll bulk transfer state for split=${split.id}, but bulkTransferId was null!")
        }

        val oldState = split.bulkTransferState
        val newState = ratkoClient.pollBulkTransferState(split.bulkTransferId)

        return if (newState != oldState) {
            logger.info("Updating split=${split.id} bulkTransferState from $oldState to $newState")
            splitService.updateSplit(split.id, newState)
            splitService.getOrThrow(split.id)
        } else {
            logger.info("Split=${split.id} still has the same bulkTransferState=$oldState")
            split
        }
    }

    fun beginNewBulkTransfer(branch: LayoutBranch, split: Split) {
        ratkoClient.startNewBulkTransfer(split).let { (bulkTransferId, bulkTransferState) ->
            splitService.updateSplit(
                splitId = split.id,
                bulkTransferState = bulkTransferState,
                bulkTransferId = bulkTransferId,
            )
        }
    }

    fun pushLocationTracksToRatko(branch: LayoutBranch, locationTrackChanges: Collection<LocationTrackChange>) {
        assertMainBranch(branch)

        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {
            val previousPush = ratkoPushDao.fetchPreviousPush()
            check(previousPush.status == RatkoPushStatus.SUCCESSFUL) {
                "Push all publications before pushing location track point manually"
            }

            check(ratkoClient.getRatkoOnlineStatus().isOnline) { "Ratko is offline" }

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
                        id = locationTrack.id as IntId,
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

    private fun pushChanges(layoutBranch: LayoutBranch, publications: List<PublicationDetails>) {
        val ratkoPushId = ratkoPushDao.startPushing(publications.map { it.id })
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
                if (ratkoClient.getRatkoOnlineStatus().isOnline) RatkoPushStatus.FAILED
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
    ) =
        calculatedChangesService
            .getAllSwitchChangesByLocationTrackAtMoment(layoutBranch, locationTrackId, moment)
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
            id = switch.id as IntId,
            version = checkNotNull(switch.version) { "Switch missing version, id=${switchChange.switchId}" },
            trackNumberIds = emptySet(), // Ratko integration doesn't care about this field
            name = switch.name,
            operation = Operation.MODIFY,
            changedJoints = switchChange.changedJoints,
        )
    }
}
