package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.GeoviiteOidDao
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.OidType
import fi.fta.geoviite.infra.error.SavingFailureException
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.util.FreeText
import kotlin.toString
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class OperationalPointService(val operatingPointDao: OperationalPointDao, private val geoviiteOidDao: GeoviiteOidDao) :
    LayoutAssetService<OperationalPoint, NoParams, OperationalPointDao>(operatingPointDao) {

    fun list(
        context: LayoutContext,
        locationBbox: BoundingBox? = null,
        polygonBbox: BoundingBox? = null,
        ids: List<IntId<OperationalPoint>>? = null,
    ): List<OperationalPoint> =
        dao.fetchMany(
            dao.fetchVersions(
                context,
                includeDeleted = true,
                locationBbox = locationBbox,
                polygonBbox = polygonBbox,
                ids = ids,
            )
        )

    @Transactional
    fun insert(branch: LayoutBranch, request: InternalOperationalPointSaveRequest): LayoutRowVersion<OperationalPoint> =
        saveDraft(
            branch,
            OperationalPoint(
                state = request.state,
                name = OperationalPointName(request.name.toString()),
                abbreviation = request.abbreviation?.toString()?.let(::OperationalPointAbbreviation),
                uicCode = request.uicCode,
                rinfType = request.rinfType,
                raideType = null,
                polygon = null,
                location = null,
                origin = OperationalPointOrigin.GEOVIITE,
                ratkoVersion = null,
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
                .also {
                    if (it.origin != OperationalPointOrigin.GEOVIITE)
                        throw SavingFailureException(
                            "Only operational points originating from Geoviite can be updated with this method. pointId=$id"
                        )
                }
                .copy(
                    state = request.state,
                    name = OperationalPointName(request.name.toString()),
                    abbreviation = request.abbreviation?.toString()?.let(::OperationalPointAbbreviation),
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
        saveDraft(
            branch,
            (dao.getOrThrow(branch.draft, id)
                .also {
                    if (it.origin != OperationalPointOrigin.RATKO)
                        throw SavingFailureException(
                            "Only operational points originating from Ratko can be updated with this method. pointId=$id"
                        )
                }
                .copy(rinfType = request.rinfType)),
        )

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

    fun getExternalIdsByBranch(id: IntId<OperationalPoint>): Map<LayoutBranch, Oid<OperationalPoint>> =
        dao.fetchExternalIdsByBranch(id).mapValues { (_, v) -> v.oid }

    @Transactional
    override fun publish(
        branch: LayoutBranch,
        version: LayoutRowVersion<OperationalPoint>,
    ): PublicationResultVersions<OperationalPoint> {
        val publishedVersion = publishInternal(branch, version)
        val presentId = dao.fetchExternalId(branch, version.id)
        if (presentId == null) {
            dao.insertExternalIdInExistingTransaction(
                branch,
                version.id,
                geoviiteOidDao.reserveOid(OidType.OPERATIONAL_POINT),
            )
        }
        return publishedVersion
    }

    @Transactional
    override fun deleteDraft(branch: LayoutBranch, id: IntId<OperationalPoint>): LayoutRowVersion<OperationalPoint> {
        val draftVersion = dao.fetchVersion(branch.draft, id)
        val draft = draftVersion?.let(dao::fetch)
        return if (draft?.origin != OperationalPointOrigin.RATKO || branch != LayoutBranch.main) {
            dao.deleteDraft(branch, id)
        } else {
            // avoid deleting ID row, which the DAO's #deleteDraft would do in case this is draft-only
            dao.deleteRow(LayoutRowId(id, branch.draft))

            val draftRatkoVersion = requireNotNull(draft.ratkoVersion)
            val officialRatkoVersion = get(branch.official, id)?.ratkoVersion
            if (officialRatkoVersion == null || officialRatkoVersion < draftRatkoVersion) {
                dao.insertRatkoPoint(id, draftRatkoVersion)
            }
            draftVersion
        }
    }

    private fun saveDraft(branch: LayoutBranch, point: OperationalPoint) = dao.save(asDraft(branch, point))

    fun idMatches(
        layoutContext: LayoutContext,
        searchTerm: FreeText,
        onlyIds: Collection<IntId<OperationalPoint>>? = null,
    ): ((term: String, item: OperationalPoint) -> Boolean) = idMatches(dao, layoutContext, searchTerm, onlyIds)

    override fun contentMatches(term: String, item: OperationalPoint, includeDeleted: Boolean) =
        (includeDeleted || item.exists) &&
            (item.name.contains(term, true) ||
                item.abbreviation.toString().contains(term, true) ||
                item.uicCode.toString().contains(term, true))
}
