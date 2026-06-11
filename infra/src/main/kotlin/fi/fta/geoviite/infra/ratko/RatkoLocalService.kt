package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.integration.RatkoPushErrorResponse
import fi.fta.geoviite.infra.ratko.RatkoClient.RatkoStatus
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.OperationalPointOrigin
import fi.fta.geoviite.infra.tracklayout.OperationalPointState
import fi.fta.geoviite.infra.tracklayout.asMainDraft
import java.util.concurrent.atomic.AtomicReference
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class RatkoLocalService
@Autowired
constructor(
    private val ratkoClient: RatkoClient?,
    private val ratkoPushDao: RatkoPushDao,
    private val ratkoOperationalPointDao: RatkoOperationalPointDao,
    private val operationalPointDao: OperationalPointDao,
) {

    private val logger = LoggerFactory.getLogger(this::class.java)
    private val latestStatus = AtomicReference(RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null))

    // Broad catch is intentional: keeps the scheduler alive regardless of what escapes getRatkoOnlineStatus()
    @Suppress("TooGenericExceptionCaught")
    fun refreshOnlineStatus() {
        val status =
            try {
                ratkoClient?.getRatkoOnlineStatus() ?: RatkoStatus(RatkoConnectionStatus.NOT_CONFIGURED, null)
            } catch (e: Exception) {
                logger.warn("Unexpected exception during Ratko health check", e)
                RatkoStatus(RatkoConnectionStatus.OFFLINE, null)
            }
        latestStatus.set(status)
    }

    fun getRatkoOnlineStatus(): RatkoStatus = latestStatus.get()

    @Transactional
    fun updateLayoutPointsFromIntegrationTable() {
        val latestRatkoVersions = ratkoOperationalPointDao.listLatestVersions()
        val ratkoPointsByOid = latestRatkoVersions.associateBy { ratkoVersion -> ratkoVersion.point.externalId }
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
    fun fetchCurrentRatkoPushError(): RatkoPushErrorResponse? =
        ratkoPushDao.getCurrentRatkoPushError()?.let { (ratkoError, publicationId) ->
            RatkoPushErrorResponse(ratkoError, publicationId)
        }

    private fun updateAndFetchRatkoOperationalPointOids(
        ratkoPointVersions: List<RatkoOperationalPointVersion>
    ): Map<IntId<OperationalPoint>, Oid<OperationalPoint>> {
        val existingIds = operationalPointDao.fetchExternalIds(LayoutBranch.main).mapValues { (_, extId) -> extId.oid }
        val existingOidsSet = existingIds.values.toSet()
        val createdIds =
            ratkoPointVersions
                .filter { (ratkoPoint) -> !existingOidsSet.contains(ratkoPoint.externalId) }
                .associate { (ratkoPoint) ->
                    createOperationalPointIdForRatkoPoint(ratkoPoint) to ratkoPoint.externalId
                }
        return existingIds + createdIds
    }

    private fun createOperationalPointIdForRatkoPoint(point: RatkoOperationalPoint): IntId<OperationalPoint> {
        val id = operationalPointDao.createId()
        operationalPointDao.insertExternalIdInExistingTransaction(LayoutBranch.main, id, point.externalId)
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
                        val savedRatkoPoint = ratkoOperationalPointDao.fetch(extId, layoutPoint.ratkoVersion)
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
            val id = layoutPointsByOid[ratkoPoint.externalId]
            if (id != null && layoutPoints.none { layoutPoint -> layoutPoint.id == id }) {
                operationalPointDao.insertRatkoPoint(id, ratkoPointVersion, OperationalPointState.IN_USE)
            }
        }
    }
}
