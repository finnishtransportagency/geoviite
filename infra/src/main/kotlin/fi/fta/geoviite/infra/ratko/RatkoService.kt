package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.configuration.CACHE_RATKO_HEALTH_STATUS
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.*
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

open class RatkoPushException(
    val type: RatkoPushErrorType,
    val operation: RatkoOperation,
    val responseBody: String,
    cause: Exception? = null,
) : RuntimeException(cause)

class RatkoSwitchPushException(exception: RatkoPushException, val switch: TrackLayoutSwitch) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody, exception)

class RatkoLocationTrackPushException(exception: RatkoPushException, val locationTrack: LocationTrack) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody, exception)

class RatkoTrackNumberPushException(exception: RatkoPushException, val trackNumber: TrackLayoutTrackNumber) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody, exception)

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val ratkoLocationTrackService: RatkoLocationTrackService,
    private val ratkoRouteNumberService: RatkoRouteNumberService,
    private val ratkoAssetService: RatkoAssetService,
    private val ratkoPushDao: RatkoPushDao,
    private val publicationService: PublicationService,
    private val calculatedChangesService: CalculatedChangesService,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val lockDao: LockDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val ratkoSchedulerUserName = UserName("RATKO_SCHEDULER")
    private val databaseLockDuration = Duration.ofMinutes(120)

    @Scheduled(cron = "0 * * * * *")
    fun scheduledRatkoPush() {
        logger.serviceCall("scheduledRatkoPush")
        // Don't retry failed on auto-push
        pushChangesToRatko(ratkoSchedulerUserName, retryFailed = false)
    }

    fun pushChangesToRatko(userName: UserName, retryFailed: Boolean = true) {
        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {
            logger.serviceCall("pushChangesToRatko")

            // Kill off any pushes that have been stuck for too long, as it's likely failed and state is hanging in DB
            ratkoPushDao.finishStuckPushes(userName)

            if (!retryFailed && previousPushStateIn(RatkoPushStatus.FAILED)) {
                logger.info("Ratko push cancelled because previous push is failed")
            } else if (!ratkoClient.getRatkoOnlineStatus().isOnline) {
                logger.info("Ratko push cancelled because ratko connection is offline")
            } else {
                val lastPublicationMoment = ratkoPushDao.getLatestPushedPublicationMoment()

                //Inclusive search, therefore the already pushed one is also returned
                val publications = publicationService.fetchPublications(lastPublicationMoment)
                    .filter { it.publicationTime > lastPublicationMoment }
                    .map { publicationService.getPublicationDetails(it.id) }

                if (publications.isNotEmpty()) {
                    pushChanges(userName, publications)
                }
            }
        }
    }

    fun pushLocationTracksToRatko(userName: UserName, locationTrackChanges: List<LocationTrackChange>) {
        lockDao.runWithLock(DatabaseLock.RATKO, databaseLockDuration) {
            logger.serviceCall("pushLocationTracksToRatko")

            val previousPush = ratkoPushDao.fetchPreviousPush()
            check(previousPush.status == RatkoPushStatus.SUCCESSFUL) {
                "Push all publications before pushing location track point manually"
            }

            check(ratkoClient.getRatkoOnlineStatus().isOnline) {
                "Ratko is offline"
            }

            // Here, we only care about current moment, but fix it to the latest publication DB time, pushed or not
            val latestPublicationMoment = ratkoPushDao.getLatestPublicationMoment()
            val switchChanges = locationTrackChanges
                .flatMap { locationTrackChange ->
                    getSwitchChangesByLocationTrack(
                        locationTrackId = locationTrackChange.locationTrackId,
                        filterByKmNumbers = locationTrackChange.changedKmNumbers,
                        moment = latestPublicationMoment
                    )
                }.map { switchChange -> mapToFakePublishedSwitch(switchChange, latestPublicationMoment) }

            val publishedLocationTrackChanges = locationTrackChanges
                .map { locationTrackChange ->
                    val locationTrack = locationTrackService.getOfficialAtMoment(
                        locationTrackChange.locationTrackId,
                        latestPublicationMoment
                    )
                    checkNotNull(locationTrack) {
                        "No location track exists with id ${locationTrackChange.locationTrackId} and timestamp $latestPublicationMoment"
                    }

                    //Fake PublishedLocationTrack, Ratko integration is built around published items
                    PublishedLocationTrack(
                        version = checkNotNull(locationTrack.version) {
                            "Location track missing version, id=${locationTrackChange.locationTrackId}"
                        },
                        name = locationTrack.name,
                        trackNumberId = locationTrack.trackNumberId,
                        operation = Operation.MODIFY,
                        changedKmNumbers = locationTrackChange.changedKmNumbers
                    )
                }

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(publishedLocationTrackChanges)

            ratkoAssetService.pushSwitchChangesToRatko(switchChanges)

            try {
                ratkoLocationTrackService.forceRedraw(
                    pushedLocationTrackOids.filterNotNull().map { RatkoOid<RatkoLocationTrack>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }
        }
    }

    private fun previousPushStateIn(vararg states: RatkoPushStatus?): Boolean {
        val previousPush = ratkoPushDao.fetchPreviousPush()
        return states.any { state -> previousPush.status == state }
    }

    private fun pushChanges(userName: UserName, publications: List<PublicationDetails>) {
        val ratkoPushId = ratkoPushDao.startPushing(userName, publications.map { it.id })
        try {
            val pushedRouteNumberOids =
                ratkoRouteNumberService.pushTrackNumberChangesToRatko(publications.flatMap { it.allPublishedTrackNumbers })

            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(publications.flatMap { it.allPublishedLocationTracks })

            pushSwitchChanges(publications)

            ratkoPushDao.updatePushStatus(
                user = userName,
                pushId = ratkoPushId,
                status = RatkoPushStatus.IN_PROGRESS_M_VALUES,
            )

            try {
                ratkoRouteNumberService.forceRedraw(
                    pushedRouteNumberOids.filterNotNull().map { RatkoOid<RatkoRouteNumber>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for route numbers $pushedRouteNumberOids")
            }

            try {
                ratkoLocationTrackService.forceRedraw(
                    pushedLocationTrackOids.filterNotNull().map { RatkoOid<RatkoLocationTrack>(it) }.toSet()
                )
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }

            ratkoPushDao.updatePushStatus(
                user = userName,
                pushId = ratkoPushId,
                status = RatkoPushStatus.SUCCESSFUL,
            )
        } catch (ex: Exception) {
            when (ex) {
                is RatkoTrackNumberPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.TRACK_NUMBER,
                        ex.trackNumber.id as IntId,
                        ex.responseBody,
                    )

                is RatkoLocationTrackPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.LOCATION_TRACK,
                        ex.locationTrack.id as IntId,
                        ex.responseBody,
                    )

                is RatkoSwitchPushException ->
                    ratkoPushDao.insertRatkoPushError(
                        ratkoPushId,
                        ex.type,
                        ex.operation,
                        RatkoAssetType.SWITCH,
                        ex.switch.id as IntId,
                        ex.responseBody,
                    )
            }

            //dummy check if Ratko is online
            val pushStatus = if (ratkoClient.getRatkoOnlineStatus().isOnline) RatkoPushStatus.FAILED
            else RatkoPushStatus.CONNECTION_ISSUE

            ratkoPushDao.updatePushStatus(
                user = userName,
                pushId = ratkoPushId,
                status = pushStatus,
            )

            throw ex
        }
    }

    private fun pushSwitchChanges(publications: List<PublicationDetails>) {
        val publicationMoment = publications.maxOf { it.publicationTime }

        //Location track points are always removed per kilometre.
        //However, there is a slight chance that points used by switches (according to Geoviite)
        // will not match with the ones in Ratko.
        //Therefore, Geoviite will also update all switches with joints in the danger zone.
        val locationTrackSwitchChanges = publications
            .flatMap { it.allPublishedLocationTracks }
            .flatMap { locationTrack ->
                getSwitchChangesByLocationTrack(
                    locationTrackId = locationTrack.version.id,
                    filterByKmNumbers = locationTrack.changedKmNumbers,
                    moment = publicationMoment
                )
            }.map { switchChange -> mapToFakePublishedSwitch(switchChange, publicationMoment) }

        val switchChanges = publications.flatMap { it.allPublishedSwitches } + locationTrackSwitchChanges
        ratkoAssetService.pushSwitchChangesToRatko(switchChanges)
    }

    fun getRatkoPushError(publishId: IntId<Publication>): RatkoPushErrorWithAsset? {
        return ratkoPushDao.getLatestRatkoPushErrorFor(publishId)?.let { ratkoError ->
            val asset = when (ratkoError.assetType) {
                RatkoAssetType.TRACK_NUMBER -> trackNumberService.getOfficial(ratkoError.assetId as IntId<TrackLayoutTrackNumber>)
                RatkoAssetType.LOCATION_TRACK -> locationTrackService.getOfficial(ratkoError.assetId as IntId<LocationTrack>)
                RatkoAssetType.SWITCH -> switchService.getOfficial(ratkoError.assetId as IntId<TrackLayoutSwitch>)
            }
            checkNotNull(asset) { "No asset found for id! ${ratkoError.assetType} ${ratkoError.assetId}" }

            RatkoPushErrorWithAsset(
                ratkoError.id,
                ratkoError.ratkoPushId,
                ratkoError.errorType,
                ratkoError.operation,
                ratkoError.assetType,
                asset
            )
        }
    }

    @Cacheable(CACHE_RATKO_HEALTH_STATUS, sync = true)
    fun getRatkoOnlineStatus(): RatkoClient.RatkoStatus {
        return ratkoClient.getRatkoOnlineStatus()
    }

    private fun getSwitchChangesByLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        filterByKmNumbers: Collection<KmNumber>,
        moment: Instant,
    ) = calculatedChangesService.getAllSwitchChangesByLocationTrack(locationTrackId, moment)
        .map { switchChanges ->
            switchChanges.copy(
                changedJoints = switchChanges.changedJoints.filter { changedJoint ->
                    filterByKmNumbers.contains(changedJoint.address.kmNumber)
                }
            )
        }.filter { it.changedJoints.isNotEmpty() }

    private fun mapToFakePublishedSwitch(switchChange: SwitchChange, moment: Instant): PublishedSwitch {
        val switch = switchService.getOfficialAtMoment(switchChange.switchId, moment)
        checkNotNull(switch) { "No switch exists with id ${switchChange.switchId} and timestamp $moment" }

        //Fake PublishedSwitch, Ratko integration is built around published items
        return PublishedSwitch(
            version = checkNotNull(switch.version) { "Switch missing version, id=${switchChange.switchId}" },
            trackNumberIds = emptySet(), //Ratko integration doesn't care about this field
            name = switch.name,
            operation = Operation.MODIFY,
            changedJoints = switchChange.changedJoints
        )
    }
}
