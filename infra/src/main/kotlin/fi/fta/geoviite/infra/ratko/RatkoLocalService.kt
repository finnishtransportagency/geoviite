package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.configuration.CACHE_RATKO_HEALTH_STATUS
import fi.fta.geoviite.infra.integration.RatkoAssetType.LOCATION_TRACK
import fi.fta.geoviite.infra.integration.RatkoAssetType.SWITCH
import fi.fta.geoviite.infra.integration.RatkoAssetType.TRACK_NUMBER
import fi.fta.geoviite.infra.integration.RatkoPushError
import fi.fta.geoviite.infra.integration.RatkoPushErrorWithAsset
import fi.fta.geoviite.infra.publication.PublicationDetails
import fi.fta.geoviite.infra.publication.PublicationLogService
import fi.fta.geoviite.infra.ratko.RatkoClient.RatkoStatus
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPoint
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.OperationalPointOrigin
import fi.fta.geoviite.infra.tracklayout.OperationalPointState
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.transaction.annotation.Transactional

data class RatkoPushErrorAndDetails(val error: RatkoPushErrorWithAsset, val publication: PublicationDetails?)

@GeoviiteService
class RatkoLocalService
@Autowired
constructor(
    private val ratkoClient: RatkoClient?,
    private val ratkoPushDao: RatkoPushDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val ratkoOperationalPointDao: RatkoOperationalPointDao,
    private val operationalPointDao: OperationalPointDao,
    private val publicationLogService: PublicationLogService,
) {

    @Cacheable(CACHE_RATKO_HEALTH_STATUS, sync = true)
    fun getRatkoOnlineStatus(): RatkoStatus {
        return ratkoClient?.getRatkoOnlineStatus() ?: RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null)
    }

    @Transactional
    fun updateLayoutPointsFromIntegrationTable() {
        val latestRatkoVersions = ratkoOperationalPointDao.listLatestVersions()
        val ratkoPointsByOid = latestRatkoVersions.associateBy { ratkoVersion ->
            ratkoVersion.point.externalId.cast<OperationalPoint>()
        }
        val layoutPoints =
            operationalPointDao.list(LayoutBranch.main.draft, true).filter { point ->
                point.origin == OperationalPointOrigin.RATKO
            }

        val nonDeletedVersions = latestRatkoVersions.filter { !it.deleted }
        val layoutPointOids = updateAndFetchRatkoOperationalPointOids(nonDeletedVersions)
        insertNewLayoutOperationalPoints(nonDeletedVersions, layoutPointOids, layoutPoints)
        updateExistingLayoutOperationalPoints(layoutPoints, layoutPointOids, ratkoPointsByOid)
    }

    @Transactional(readOnly = true)
    fun fetchCurrentRatkoPushError(): RatkoPushErrorAndDetails? =
        ratkoPushDao.getCurrentRatkoPushError()?.let { (ratkoError, publicationId) ->
            val asset = getErrorAssetOrThrow(ratkoError)

            val errorWithAsset =
                RatkoPushErrorWithAsset(
                    ratkoError.id,
                    ratkoError.ratkoPushId,
                    ratkoError.errorType,
                    ratkoError.operation,
                    ratkoError.assetType,
                    asset,
                )
            RatkoPushErrorAndDetails(errorWithAsset, publicationLogService.getPublicationDetails(publicationId))
        }

    private fun updateAndFetchRatkoOperationalPointOids(
        ratkoPointVersions: List<RatkoOperationalPointVersion>
    ): Map<IntId<OperationalPoint>, Oid<OperationalPoint>> {
        val existingIds = operationalPointDao.fetchExternalIds(LayoutBranch.main).mapValues { (_, extId) -> extId.oid }
        val existingOidsSet = existingIds.values.toSet()
        val createdIds =
            ratkoPointVersions
                .filter { (ratkoPoint) -> !existingOidsSet.contains(ratkoPoint.externalId.cast()) }
                .associate { (ratkoPoint) ->
                    createOperationalPointIdForRatkoPoint(ratkoPoint) to ratkoPoint.externalId.cast<OperationalPoint>()
                }
        return existingIds + createdIds
    }

    private fun createOperationalPointIdForRatkoPoint(point: RatkoOperationalPoint): IntId<OperationalPoint> {
        val id = operationalPointDao.createId()
        operationalPointDao.insertExternalIdInExistingTransaction(LayoutBranch.main, id, point.externalId.cast())
        return id
    }

    private fun updateExistingLayoutOperationalPoints(
        layoutPoints: List<OperationalPoint>,
        layoutPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
        ratkoPointsByOid: Map<Oid<OperationalPoint>, RatkoOperationalPointVersion>,
    ) {
        layoutPoints
            .mapNotNull { layoutPoint ->
                layoutPointOids[layoutPoint.id as IntId]
                    ?.let { extId -> extId to ratkoPointsByOid[extId] }
                    ?.let { (extId, ratkoData) ->
                        if (ratkoData != null && layoutPoint.ratkoVersion != ratkoData.version)
                            Triple(layoutPoint, extId, ratkoData)
                        else null
                    }
            }
            .forEach { (layoutPoint, extId, ratkoPointVersion) ->
                when {
                    // Deletion
                    ratkoPointVersion.deleted && layoutPoint.state != OperationalPointState.DELETED -> {
                        operationalPointDao.save(
                            asMainDraft(
                                layoutPoint.copy(
                                    state = OperationalPointState.DELETED,
                                    ratkoVersion = ratkoPointVersion.version,
                                )
                            )
                        )
                    }
                    // Restore
                    !ratkoPointVersion.deleted && layoutPoint.state == OperationalPointState.DELETED -> {
                        operationalPointDao.save(
                            asMainDraft(
                                layoutPoint.copy(
                                    state = OperationalPointState.IN_USE,
                                    ratkoVersion = ratkoPointVersion.version,
                                )
                            )
                        )
                    }
                    // Normal update
                    layoutPoint.ratkoVersion != null -> {
                        val savedRatkoPoint = ratkoOperationalPointDao.fetch(extId.cast(), layoutPoint.ratkoVersion)
                        if (ratkoOperationalPointContentDiffers(ratkoPointVersion.point, savedRatkoPoint)) {
                            operationalPointDao.save(
                                asMainDraft(layoutPoint.copy(ratkoVersion = ratkoPointVersion.version))
                            )
                        }
                    }
                }
            }
    }

    private fun ratkoOperationalPointContentDiffers(a: RatkoOperationalPoint, b: RatkoOperationalPoint) =
        a.name != b.name ||
            a.abbreviation != b.abbreviation ||
            a.type != b.type ||
            a.location != b.location ||
            a.uicCode != b.uicCode

    private fun insertNewLayoutOperationalPoints(
        ratkoPointVersions: List<RatkoOperationalPointVersion>,
        layoutPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
        layoutPoints: List<OperationalPoint>,
    ) {
        val layoutPointsByOid = layoutPointOids.entries.associate { (k, v) -> v to k }
        ratkoPointVersions.forEach { (ratkoPoint, ratkoPointVersion) ->
            val id = layoutPointsByOid[ratkoPoint.externalId.cast()]
            if (id != null && layoutPoints.none { layoutPoint -> layoutPoint.id == id }) {
                operationalPointDao.insertRatkoPoint(id, ratkoPointVersion, OperationalPointState.IN_USE)
            }
        }
    }

    private fun getErrorAssetOrThrow(ratkoError: RatkoPushError<*>): LayoutAsset<*> {
        val asset =
            when (ratkoError.assetType) {
                TRACK_NUMBER ->
                    trackNumberService.get(MainLayoutContext.official, ratkoError.assetId as IntId<LayoutTrackNumber>)

                LOCATION_TRACK ->
                    locationTrackService.get(MainLayoutContext.official, ratkoError.assetId as IntId<LocationTrack>)

                SWITCH -> switchService.get(MainLayoutContext.official, ratkoError.assetId as IntId<LayoutSwitch>)
            }
        checkNotNull(asset) { "No asset found for id! ${ratkoError.assetType} ${ratkoError.assetId}" }
        return asset
    }
}
