package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.assertMainBranch
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
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoPlanId
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoSplit
import fi.fta.geoviite.infra.ratko.model.existingRatkoPlan
import fi.fta.geoviite.infra.ratko.model.newRatkoPlan
import fi.fta.geoviite.infra.split.BulkTransferState
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import java.time.Duration
import java.time.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.transaction.support.TransactionTemplate

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
    private val ratkoOperationalPointDao: RatkoOperationalPointDao,
    private val splitService: SplitService,
    private val publicationDao: PublicationDao,
    private val layoutDesignDao: LayoutDesignDao,
    private val transactionTemplate: TransactionTemplate,
    private val ratkoLocalService: RatkoLocalService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(240)

    fun updateOperationalPointsFromRatko() {
        lockDao.runWithLock(DatabaseLock.RATKO_OPERATING_POINTS_FETCH, databaseLockDuration) {
            val points = ratkoClient.fetchOperationalPoints()
            transactionTemplate.execute {
                ratkoOperationalPointDao.updateOperationalPoints(points)
                ratkoLocalService.updateLayoutPointsFromIntegrationTable()
            }
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
                val lastPublicationMoment = ratkoPushDao.getLatestPushedPublicationMoment(layoutBranch)

                // Inclusive search, therefore the already pushed one is also returned
                val publications =
                    publicationLogService
                        .fetchPublications(layoutBranch, lastPublicationMoment)
                        .filter { it.publicationTime > lastPublicationMoment }
                        .map { publicationLogService.getPublicationDetails(it.id) }

                if (publications.isNotEmpty()) {
                    pushChanges(
                        layoutBranch,
                        publications,
                        calculatedChangesService.getAllOidsWithInheritance(layoutBranch),
                    )
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
        val extIds = extIdsProvided ?: calculatedChangesService.getAllOidsWithInheritance(branch)

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
                } else {
                    PushableMainBranch
                }

            val pushedRouteNumberOids =
                ratkoRouteNumberService.pushTrackNumberChangesToRatko(
                    pushableBranch,
                    publications.flatMap { it.allPublishedTrackNumbers },
                    lastPublicationTime,
                )

            val publicationsToSplits =
                publications.mapNotNull { publication ->
                    publication.split?.id?.let { splitId -> publication to splitService.getOrThrow(splitId) }
                }

            val locationTrackKilometersPushedInSplits =
                publicationsToSplits
                    .map { (publication, split) ->
                        RatkoSplit(
                            publication,
                            split,
                            ensureSplitSourceTrackExistsInRatko(
                                publication.layoutBranch.branch,
                                split,
                                lastPublicationTime,
                            ),
                        )
                    }
                    .let { splitsToPush ->
                        ratkoLocationTrackService.pushSplits(pushableBranch.branch, splitsToPush, lastPublicationTime)
                    }

            val locationTrackIdsPushedInSplits = publicationsToSplits.flatMap { (_, split) -> split.locationTracks }
            val locationTracksToPush =
                publications
                    .flatMap { it.allPublishedLocationTracks }
                    .filter { locationTrack -> locationTrack.id !in locationTrackIdsPushedInSplits }

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(
                    pushableBranch,
                    locationTracksToPush,
                    lastPublicationTime,
                )

            val changedKilometersOverride =
                locationTrackKilometersPushedInSplits
                    .map { locationTrackKilometers -> locationTrackKilometers.id to locationTrackKilometers.kilometers }
                    .toMap()

            pushSwitchChanges(
                layoutBranch = pushableBranch,
                publishedSwitches = publications.flatMap { it.allPublishedSwitches },
                publishedLocationTracks = publications.flatMap { it.allPublishedLocationTracks },
                publicationTime = lastPublicationTime,
                extIds = extIds,
                changedKilometersOverride = changedKilometersOverride,
            )

            ratkoPushDao.updatePushStatus(ratkoPushId, RatkoPushStatus.IN_PROGRESS_M_VALUES)

            try {
                ratkoRouteNumberService.forceRedraw(
                    pushedRouteNumberOids.map { RatkoOid<RatkoRouteNumber>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for route numbers $pushedRouteNumberOids")
            }

            val locationTrackOidsPushedInSplits =
                locationTrackKilometersPushedInSplits.map { locationTrackKilometers -> locationTrackKilometers.oid }
            try {
                ratkoLocationTrackService.forceRedraw(
                    listOf(locationTrackOidsPushedInSplits, pushedLocationTrackOids)
                        .flatten()
                        .map { RatkoOid<RatkoLocationTrack>(it) }
                        .toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }

            publicationsToSplits.forEach { (_, split) ->
                splitService.updateSplit(split.id, bulkTransferState = BulkTransferState.DONE)
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
        changedKilometersOverride: Map<IntId<LocationTrack>, Set<KmNumber>>,
    ) {
        // Location track points are always removed per kilometre.
        // However, there is a slight chance that points used by switches (according to Geoviite)
        // will not match with the ones in Ratko.
        // Therefore, Geoviite will also update all switches with joints in the danger zone.
        val locationTrackSwitchChanges =
            publishedLocationTracks
                .flatMap { locationTrack ->
                    val filterByKmNumbers =
                        changedKilometersOverride.getOrDefault(locationTrack.id, locationTrack.changedKmNumbers)
                    getSwitchChangesByLocationTrack(
                        layoutBranch = layoutBranch.branch,
                        locationTrackId = locationTrack.id,
                        filterByKmNumbers = filterByKmNumbers,
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

        val existingRatkoId = layoutDesignDao.fetchRatkoId(designBranch.designId)
        val ratkoId =
            if (existingRatkoId == null) {
                createPlanInRatko(lastPublicationDesign, designBranch.designId)
            } else {
                updatePlanInRatkoIfNeeded(
                    rangePublications,
                    designBranch,
                    lastPublicationDesignVersion,
                    lastPublicationDesign,
                    existingRatkoId,
                )
                existingRatkoId
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

    private fun ensureSplitSourceTrackExistsInRatko(
        branch: LayoutBranch,
        split: Split,
        publicationTime: Instant,
    ): RatkoLocationTrack {
        val splitSourceTrackOid = locationTrackService.getExternalIdsByBranch(split.sourceLocationTrackId)[branch]

        requireNotNull(splitSourceTrackOid) {
            "Split source track must have an external id (oid) defined when split of it is being pushed"
        }

        val existingRatkoLocationTrack = ratkoClient.getLocationTrack(RatkoOid(splitSourceTrackOid))
        return if (existingRatkoLocationTrack == null) {
            logger.info(
                "Split source track unexpectedly not found from Ratko! Source track will be created for splitId=${split.id}, sourceLocationTrackId=${split.sourceLocationTrackId}"
            )

            val splitSourceLocationTrack =
                locationTrackService.get(MainLayoutContext.official, split.sourceLocationTrackId).let(::requireNotNull)

            ratkoLocationTrackService.createLocationTrack(
                branch,
                splitSourceLocationTrack,
                MainBranchRatkoExternalId(splitSourceTrackOid),
                publicationTime,
            )

            // If the source track is still not found from Ratko, something went really wrong.
            ratkoClient.getLocationTrack(RatkoOid(splitSourceTrackOid)).let(::requireNotNull)
        } else {
            existingRatkoLocationTrack
        }
    }

    fun getSignalAsset(x: Int, y: Int, z: Int, cluster: Boolean) = ratkoClient.getSignalAsset(x, y, z, cluster)
}
