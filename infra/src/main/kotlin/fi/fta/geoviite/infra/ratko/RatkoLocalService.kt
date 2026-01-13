package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutBranchType
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
import kotlin.collections.contains
import kotlin.collections.forEach
import kotlin.collections.get
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
        val ratkoPointsWithVersions = ratkoOperationalPointDao.listWithVersions()
        val ratkoPointsByOid =
            ratkoPointsWithVersions.associateBy { (point) -> point.externalId.cast<OperationalPoint>() }
        val layoutPoints =
            operationalPointDao.list(LayoutBranch.main.draft, true).filter { point ->
                point.origin == OperationalPointOrigin.RATKO
            }

        val layoutPointOids = upsertIdsForRatkoPoints(ratkoPointsWithVersions)
        upsertObjectsForRatkoPoints(ratkoPointsWithVersions, layoutPointOids, layoutPoints)
        val deletedPoints = markRemovedPointsAsDeleted(layoutPoints, layoutPointOids, ratkoPointsByOid)
        updatePossiblyUpdatedPoints(
            layoutPoints.filterNot { deletedPoints.contains(it.id) },
            layoutPointOids,
            ratkoPointsByOid,
        )
    }

    @Transactional(readOnly = true)
    fun fetchCurrentRatkoPushError(branchType: LayoutBranchType): RatkoPushErrorAndDetails? =
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

    private fun upsertIdsForRatkoPoints(
        ratkoPointsWithVersions: List<Pair<RatkoOperationalPoint, Int>>
    ): Map<IntId<OperationalPoint>, Oid<OperationalPoint>> {
        val existingIds = operationalPointDao.fetchExternalIds(LayoutBranch.main).mapValues { (_, extId) -> extId.oid }
        val existingOidsSet = existingIds.values.toSet()
        val createdIds =
            ratkoPointsWithVersions
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

    private fun updatePossiblyUpdatedPoints(
        possiblyUpdatedPoints: List<OperationalPoint>,
        layoutPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
        ratkoPointsByOid: Map<Oid<OperationalPoint>, Pair<RatkoOperationalPoint, Int>>,
    ) {
        possiblyUpdatedPoints.forEach { layoutPoint ->
            val extId = layoutPointOids.getValue(layoutPoint.id as IntId)
            ratkoPointsByOid[extId]?.let { (currentRatkoPoint, currentRatkoPointVersion) ->
                if (currentRatkoPointVersion > requireNotNull(layoutPoint.ratkoVersion)) {
                    val savedRatkoPoint = ratkoOperationalPointDao.fetch(extId.cast(), layoutPoint.ratkoVersion)
                    if (ratkoOperationalPointContentDiffers(currentRatkoPoint, savedRatkoPoint)) {
                        operationalPointDao.save(asMainDraft(layoutPoint.copy(ratkoVersion = currentRatkoPointVersion)))
                    }
                }
            }
        }
    }

    private fun markRemovedPointsAsDeleted(
        layoutPoints: List<OperationalPoint>,
        originalLayoutPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
        ratkoPointsByOid: Map<Oid<OperationalPoint>, Pair<RatkoOperationalPoint, Int>>,
    ): Set<IntId<OperationalPoint>> {
        val deleted =
            layoutPoints.filter { layoutPoint ->
                layoutPoint.state != OperationalPointState.DELETED &&
                    originalLayoutPointOids.contains(layoutPoint.id) &&
                    !ratkoPointsByOid.contains(originalLayoutPointOids[layoutPoint.id])
            }
        deleted.forEach { layoutPoint ->
            operationalPointDao.save(asMainDraft(layoutPoint.copy(state = OperationalPointState.DELETED)))
        }
        return deleted.map { it.id as IntId }.toSet()
    }

    private fun ratkoOperationalPointContentDiffers(a: RatkoOperationalPoint, b: RatkoOperationalPoint) =
        a.name != b.name ||
            a.abbreviation != b.abbreviation ||
            a.type != b.type ||
            a.location != b.location ||
            a.uicCode != b.uicCode

    private fun upsertObjectsForRatkoPoints(
        ratkoPointsWithVersions: List<Pair<RatkoOperationalPoint, Int>>,
        layoutPointOids: Map<IntId<OperationalPoint>, Oid<OperationalPoint>>,
        layoutPoints: List<OperationalPoint>,
    ) {
        val layoutPointsByOid = layoutPointOids.entries.associate { (k, v) -> v to k }
        ratkoPointsWithVersions.forEach { (ratkoPoint, ratkoPointVersion) ->
            val id = layoutPointsByOid[ratkoPoint.externalId.cast()]
            if (id != null && layoutPoints.none { layoutPoint -> layoutPoint.id == id }) {
                operationalPointDao.insertRatkoPoint(id, ratkoPointVersion)
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
