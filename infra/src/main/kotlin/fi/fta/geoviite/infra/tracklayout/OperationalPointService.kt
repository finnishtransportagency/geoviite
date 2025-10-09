package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class OperationalPointService(val operatingPointDao: OperationalPointDao) :
    LayoutAssetService<OperationalPoint, NoParams, OperationalPointDao>(operatingPointDao) {

    fun list(
        context: LayoutContext,
        bbox: BoundingBox? = null,
        ids: List<IntId<OperationalPoint>>? = null,
    ): List<OperationalPoint> =
        dao.fetchMany(
            dao.fetchVersions(
                context,
                includeDeleted = false,
                searchBox = if (bbox == null) null else SearchOperationalPointsByLocation(bbox),
                ids = ids,
            )
        )

    @Transactional
    fun insert(branch: LayoutBranch, request: InternalOperationalPointSaveRequest): LayoutRowVersion<OperationalPoint> =
        saveDraft(
            branch,
            OperationalPoint(
                state = request.state,
                name = request.name,
                abbreviation = request.abbreviation,
                uicCode = request.uicCode,
                rinfType = request.rinfType,
                raideType = null,
                polygon = null,
                location = null,
                origin = OperationalPointOrigin.GEOVIITE,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
            ),
        )

    @Transactional
    fun update(
        branch: LayoutBranch,
        id: IntId<OperationalPoint>,
        request: InternalOperationalPointSaveRequest,
    ): LayoutRowVersion<OperationalPoint> =
        saveDraft(
            branch,
            dao.getOrThrow(branch.draft, id)
                .copy(
                    state = request.state,
                    name = request.name,
                    abbreviation = request.abbreviation,
                    uicCode = request.uicCode,
                    rinfType = request.rinfType,
                ),
        )

    @Transactional
    fun update(
        branch: LayoutBranch,
        id: IntId<OperationalPoint>,
        request: ExternalOperationalPointSaveRequest,
    ): LayoutRowVersion<OperationalPoint> =
        saveDraft(branch, (dao.getOrThrow(branch.draft, id).copy(rinfType = request.rinfType)))

    @Transactional
    fun updateLocation(
        branch: LayoutBranch,
        id: IntId<OperationalPoint>,
        request: Point,
    ): LayoutRowVersion<OperationalPoint> =
        saveDraft(branch, (dao.getOrThrow(branch.draft, id).copy(location = request)))

    @Transactional
    fun updatePolygon(
        branch: LayoutBranch,
        id: IntId<OperationalPoint>,
        request: Polygon,
    ): LayoutRowVersion<OperationalPoint> =
        saveDraft(branch, (dao.getOrThrow(branch.draft, id).copy(polygon = request)))

    private fun saveDraft(branch: LayoutBranch, point: OperationalPoint) = dao.save(asDraft(branch, point))
}
