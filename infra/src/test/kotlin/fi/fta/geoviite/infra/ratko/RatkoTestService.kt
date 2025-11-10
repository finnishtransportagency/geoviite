package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.model.RatkoOperationalPointParse
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean

@GeoviiteService
@ConditionalOnBean(RatkoService::class)
class RatkoTestService(
    private val trackNumberDao: LayoutTrackNumberDao,
    private val ratkoOperationalPointDao: RatkoOperationalPointDao,
    private val operationalPointDao: OperationalPointDao,
    private val ratkoLocalService: RatkoLocalService,
) : DBTestBase() {

    private val trackNumberOidForOperationalPoints = "73454.3572.73.1356.753"

    fun setupRatkoOperationalPoints(vararg points: RatkoOperationalPointParse): List<IntId<OperationalPoint>> {
        return transactional {
            trackNumberDao.insertExternalIdInExistingTransaction(
                LayoutBranch.main,
                trackNumberDao.save(trackNumber()).id,
                Oid(trackNumberOidForOperationalPoints),
            )
            ratkoOperationalPointDao.updateOperationalPoints(
                points.map { it.copy(trackNumberExternalId = Oid(trackNumberOidForOperationalPoints)) }
            )
            val pointIds: List<IntId<OperationalPoint>> = points.map { operationalPointDao.createId() }
            points.zip(pointIds).forEach { (point, pointId) ->
                operationalPointDao.insertExternalIdInExistingTransaction(
                    LayoutBranch.main,
                    pointId,
                    point.externalId.cast(),
                )
            }
            ratkoLocalService.updateLayoutPointsFromIntegrationTable()
            pointIds
        }
    }

    fun updateRatkoOperationalPoints(vararg points: RatkoOperationalPointParse) {
        ratkoOperationalPointDao.updateOperationalPoints(
            points.map { it.copy(trackNumberExternalId = Oid(trackNumberOidForOperationalPoints)) }
        )
        transactional { ratkoLocalService.updateLayoutPointsFromIntegrationTable() }
    }
}
