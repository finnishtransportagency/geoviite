package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.configuration.CACHE_RATKO_HEALTH_STATUS
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.linking.Publication
import fi.fta.geoviite.infra.linking.PublicationHeader
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.model.RatkoLocationTrack
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.ratko.model.RatkoRouteNumber
import fi.fta.geoviite.infra.ratko.model.RatkoSwitchAsset
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.cache.annotation.Cacheable
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration

open class RatkoPushException(val type: RatkoPushErrorType, val operation: RatkoOperation, val responseBody: String) : RuntimeException()

class RatkoSwitchPushException(exception: RatkoPushException, val switch: TrackLayoutSwitch) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody)

class RatkoLocationTrackPushException(exception: RatkoPushException, val locationTrack: LocationTrack) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody)

class RatkoTrackNumberPushException(exception: RatkoPushException, val trackNumber: TrackLayoutTrackNumber) :
    RatkoPushException(exception.type, exception.operation, exception.responseBody)

@Service
@ConditionalOnBean(RatkoClientConfiguration::class)
class RatkoService @Autowired constructor(
    private val ratkoClient: RatkoClient,
    private val ratkoLocationTrackService: RatkoLocationTrackService,
    private val ratkoRouteNumberService: RatkoRouteNumberService,
    private val ratkoAssetService: RatkoAssetService,
    private val ratkoPushDao: RatkoPushDao,
    private val calculatedChangesService: CalculatedChangesService,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val lockDao: LockDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val ratkoSchedulerUserName = UserName("RATKO_SCHEDULER")

    @Scheduled(cron = "0 * * * * *")
    fun scheduledRatkoPush() {
        logger.serviceCall("scheduledRatkoPush")
        val previousPush = ratkoPushDao.fetchPreviousPush()
        //Auto publish if the previous publication has been successful OR it has failed due to connection error
        if (previousPush == null
            || previousPush.status == RatkoPushStatus.SUCCESSFUL
            || previousPush.status == RatkoPushStatus.CONNECTION_ISSUE
        ) {
            pushChangesToRatko(ratkoSchedulerUserName)
        } else {
            logger.info(
                "Scheduled Ratko push cancelled because not all conditions are met: {}",
                previousPush
            )
        }
    }

    fun getNewRouteNumberTrackOid(): RatkoOid<RatkoRouteNumber>? {
        logger.serviceCall("getNewRouteNumberTrackOid")
        return ratkoClient.getNewRouteNumberOid()
    }

    fun getNewLocationTrackOid(): RatkoOid<RatkoLocationTrack>? {
        logger.serviceCall("getNewLocationTrackOid")
        return ratkoClient.getNewLocationTrackOid()
    }

    fun getNewSwitchOid(): RatkoOid<RatkoSwitchAsset>? {
        logger.serviceCall("getNewSwitchOid")
        return ratkoClient.getNewSwitchOid()
    }

    fun pushChangesToRatko(userName: UserName) {
        lockDao.runWithLock(DatabaseLock.RATKO, Duration.ofMinutes(120)) {
            logger.serviceCall("pushChangesToRatko")

            val publishes = ratkoPushDao.fetchUnpublishedLayoutPublishes()
            if (publishes.isNotEmpty()) {
                if (ratkoClient.ratkoIsOnline()) {
                    pushChanges(userName, publishes)
                }
            }
        }
    }

    private fun pushChanges(
        userName: UserName,
        publishes: List<PublicationHeader>
    ) {
        val ratkoPushId = ratkoPushDao.startPushing(userName, publishes.map { it.id })
        try {
            val latestSuccessfulPushMoment = ratkoPushDao.getLatestSuccessfulPushMoment()
            val calculatedChanges = calculatedChangesService.getCalculatedChangesSince(
                trackNumberIds = publishes.flatMap { it.trackNumbers },
                locationTrackIds = publishes.flatMap { it.locationTracks },
                switchIds = publishes.flatMap { it.switches },
                moment = latestSuccessfulPushMoment
            )

            val pushedRouteNumberOids =
                ratkoRouteNumberService.pushTrackNumberChangesToRatko(calculatedChanges.trackNumberChanges)
            val pushedLocationTrackOids =
                ratkoLocationTrackService.pushLocationTrackChangesToRatko(calculatedChanges.locationTracksChanges)
            ratkoAssetService.pushSwitchChangesToRatko(calculatedChanges.switchChanges)

            try {
                ratkoRouteNumberService.forceRedraw(pushedRouteNumberOids.map { RatkoOid(it) })
            } catch (_: Exception) {
                logger.warn("Failed to push M values for route numbers $pushedRouteNumberOids")
            }

            try {
                ratkoLocationTrackService.forceRedraw(pushedLocationTrackOids.map { RatkoOid(it) })
            } catch (_: Exception) {
                logger.warn("Failed to push M values for location tracks $pushedLocationTrackOids")
            }

            ratkoPushDao.finishPushing(
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
            val pushStatus =
                if (ratkoClient.ratkoIsOnline()) RatkoPushStatus.FAILED else RatkoPushStatus.CONNECTION_ISSUE

            ratkoPushDao.finishPushing(
                user = userName,
                pushId = ratkoPushId,
                status = pushStatus,
            )

            throw ex
        }
    }

    fun getRatkoPushError(publishId: IntId<Publication>): RatkoPushErrorWithAsset? {
        val ratkoError = ratkoPushDao.getLatestRatkoPushErrorFor(publishId)
        return ratkoError?.let {
            val asset = when (it.assetType) {
                RatkoAssetType.TRACK_NUMBER -> trackNumberService.getOfficial(it.assetId as IntId<TrackLayoutTrackNumber>)
                RatkoAssetType.LOCATION_TRACK -> locationTrackService.getOfficial(it.assetId as IntId<LocationTrack>)
                RatkoAssetType.SWITCH -> switchService.getOfficial(it.assetId as IntId<TrackLayoutSwitch>)
            }
            checkNotNull(asset) { "No asset found for id! ${ratkoError.assetType} ${ratkoError.assetId}" }

            RatkoPushErrorWithAsset(
                it.ratkoPushId,
                it.ratkoPushErrorType,
                it.operation,
                it.assetType,
                asset
            )
        }
    }

    @Cacheable(CACHE_RATKO_HEALTH_STATUS, sync = true)
    fun ratkoIsOnline(): Boolean {
        return ratkoClient.ratkoIsOnline()
    }

}
