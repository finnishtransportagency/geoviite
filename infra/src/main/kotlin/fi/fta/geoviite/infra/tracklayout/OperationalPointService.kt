package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.GeoviiteOidDao
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.OidType
import fi.fta.geoviite.infra.error.SavingFailureException
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.publication.PublicationResultVersions
import fi.fta.geoviite.infra.ratko.model.OperationalPointRatoType
import fi.fta.geoviite.infra.util.FreeText
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class OperationalPointService(
    val operationalPointDao: OperationalPointDao,
    private val geoviiteOidDao: GeoviiteOidDao,
    private val locationTrackService: LocationTrackService,
    private val switchService: LayoutSwitchService,
    private val switchDao: LayoutSwitchDao,
) : LayoutAssetService<OperationalPoint, NoParams, OperationalPointDao>(operationalPointDao) {

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
                ratoType = null,
                polygon = null,
                location = null,
                origin = OperationalPointOrigin.GEOVIITE,
                ratkoVersion = null,
                hasExternalChanges = false,
                contextData = LayoutContextData.newDraft(branch, dao.createId()),
                rinfIdOverride = request.rinfIdOverride,
                rinfIdGenerated = null,
            ),
        )

    @Transactional
    fun update(
        branch: LayoutBranch,
        id: IntId<OperationalPoint>,
        request: InternalOperationalPointUpdateRequest,
    ): LayoutRowVersion<OperationalPoint> {
        val version =
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
                        rinfIdOverride = request.rinfIdOverride,
                    ),
            )
        if (request.severLinks) {
            clearOperationalPointReferences(branch, version.id)
        }
        return version
    }

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
                .copy(rinfType = request.rinfType, rinfIdOverride = request.rinfIdOverride)),
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
        if (
            dao.getRinfIdGenerated(version.id) == null &&
                dao.fetch(version).let { it.ratoType != OperationalPointRatoType.OLP && it.rinfIdOverride == null }
        ) {
            dao.setRinfIdGenerated(version.id, dao.generateRinfId())
        }
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
        // If removal also breaks references, clear them out first
        if (dao.fetchVersion(branch.official, id) == null) {
            clearOperationalPointReferences(branch, id)
        }

        val draftVersion = dao.fetchVersion(branch.draft, id)
        val draft = draftVersion?.let(dao::fetch)
        return if (draft?.origin != OperationalPointOrigin.RATKO || branch != LayoutBranch.main) {
            dao.deleteDraft(branch, id)
        } else {
            val draftRatkoVersion = requireNotNull(draft.ratkoVersion)
            val official = get(branch.official, id)

            if (official != null) {
                if (draftRatkoVersion != official.ratkoVersion || draft.state != official.state) {
                    dao.save(
                        asDraft(LayoutBranch.main, official.copy(ratkoVersion = draftRatkoVersion, state = draft.state))
                    )
                } else {
                    dao.deleteRow(LayoutRowId(id, branch.draft))
                }
            } else {
                // avoid deleting ID row, which the DAO's #deleteDraft would do since this is draft-only
                dao.deleteRow(LayoutRowId(id, branch.draft))
                dao.insertRatkoPoint(id, draftRatkoVersion, draft.state)
            }
            draftVersion
        }
    }

    override fun mergeToMainBranch(
        fromBranch: DesignBranch,
        id: IntId<OperationalPoint>,
    ): LayoutRowVersion<OperationalPoint> {
        val designBranchPoint = fetchAndCheckForMerging(fromBranch, id).first
        val mainBranchPoint = dao.fetchVersion(MainLayoutContext.draft, id)?.let(operationalPointDao::fetch)
        val isRatkoPoint = mainBranchPoint?.origin == OperationalPointOrigin.RATKO
        return dao.save(
            asMainDraft(
                // If an operational point is synced from Ratko, its ratko version and state are handled by the Ratko
                // sync. Ratko sync only runs in main, so that data should be used over whatever exists in the design.
                // If the operational point is from Geoviite, we can just take the state info from the design branch.
                designBranchPoint.copy(
                    ratkoVersion = if (isRatkoPoint) mainBranchPoint.ratkoVersion else null,
                    state = if (isRatkoPoint) mainBranchPoint.state else designBranchPoint.state,
                )
            )
        )
    }

    private fun clearOperationalPointReferences(branch: LayoutBranch, id: IntId<OperationalPoint>) {
        val linkedTracks = locationTrackService.getOperationalPointTracks(branch.draft, id).assigned
        if (linkedTracks.isNotEmpty()) {
            locationTrackService.unlinkFromOperationalPoint(branch, linkedTracks, id)
        }

        switchDao.getSwitchesLinkedToOperationalPoint(branch.draft, id).forEach { switchId ->
            val switch = switchService.getOrThrow(branch.draft, switchId)
            switchService.saveDraft(branch, switch.copy(operationalPointId = null))
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
                item.uicCode.toString().contains(term, true) ||
                item.rinfId.toString().contains(term, true))
}
