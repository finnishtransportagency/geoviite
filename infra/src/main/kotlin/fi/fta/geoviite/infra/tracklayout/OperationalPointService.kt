package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class OperationalPointService(val operatingPointDao: OperationalPointDao) :
    LayoutAssetService<OperationalPoint, NoParams, OperationalPointDao>(operatingPointDao) {

    fun listNear(context: LayoutContext, bbox: BoundingBox): List<OperationalPoint> =
        dao.fetchMany(
            dao.fetchVersions(context, includeDeleted = false, searchBox = SearchOperationalPointsByLocation(bbox))
        )

    @Transactional
    fun update(
        context: LayoutContext,
        id: IntId<OperationalPoint>,
        request: InternalOperationalPointSaveRequest,
    ): LayoutRowVersion<OperationalPoint> =
        dao.save(
            dao.getOrThrow(context, id)
                .copy(
                    state = request.state,
                    name = request.name,
                    abbreviation = request.abbreviation,
                    uicCode = request.uicCode,
                    rinfType = request.rinfType,
                )
        )

    @Transactional
    fun update(
        context: LayoutContext,
        id: IntId<OperationalPoint>,
        request: ExternalOperationalPointSaveRequest,
    ): LayoutRowVersion<OperationalPoint> = dao.save(dao.getOrThrow(context, id).copy(rinfType = request.rinfType))

    @Transactional
    fun updateLocation(
        context: LayoutContext,
        id: IntId<OperationalPoint>,
        request: Point,
    ): LayoutRowVersion<OperationalPoint> = dao.save(dao.getOrThrow(context, id).copy(location = request))

    @Transactional
    fun updateArea(
        context: LayoutContext,
        id: IntId<OperationalPoint>,
        request: Polygon,
    ): LayoutRowVersion<OperationalPoint> = dao.save(dao.getOrThrow(context, id).copy(area = request))
}
