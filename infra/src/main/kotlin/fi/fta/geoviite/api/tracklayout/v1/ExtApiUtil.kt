package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService
import kotlin.reflect.KClass

inline fun <reified T : LayoutAsset<T>> idLookup(dao: IExternalIdDao<T>, oid: Oid<T>): IntId<T> =
    dao.lookupByExternalId(oid)?.id ?: throwOidTargetNotFound(oid)

fun branchByDesignOid(layoutDesignService: LayoutDesignService, oid: ExtOidV1<LayoutDesign>?): LayoutBranch =
    if (oid == null) LayoutBranch.main
    else {
        LayoutBranch.design((layoutDesignService.getByOid(oid.value) ?: throwOidTargetNotFound(oid.value)).id as IntId)
    }

/**
 * The OIDs to report for an asset: its OID in the requested branch, plus its main-branch OID separately when the
 * requested branch is a design branch (null when the requested branch is the main branch itself).
 */
data class BranchOidsV1<T : LayoutAsset<T>>(val oid: Oid<T>, val officialOid: Oid<T>?)

/**
 * Resolves the OIDs to report for an asset looked up by [requestOid]. An asset with no OID in a design branch is not
 * part of the design's externally published state, and is reported as not found.
 */
inline fun <reified T : LayoutAsset<T>> branchOids(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    requestOid: Oid<T>,
    id: IntId<T>,
): BranchOidsV1<T> {
    val oids = dao.fetchExternalIdsByBranch(id)
    val officialOid = oids[LayoutBranch.main]?.oid
    return if (branch == LayoutBranch.main)
        BranchOidsV1(officialOid ?: throwOidNotFoundInBranch(requestOid, branch), null)
    else BranchOidsV1(oids[branch]?.oid ?: throwOidNotFoundInBranch(requestOid, branch), officialOid)
}

/** OID references from an ext API response to other assets (e.g. a location track's track number reference). */
class ExtOidReferencesV1<T : LayoutAsset<T>>(
    val assetType: KClass<T>,
    val branch: LayoutBranch,
    private val extIds: Map<IntId<T>, RatkoExternalId<T>>,
) {
    fun getOrNull(id: DomainId<T>): Oid<T>? = extIds[id]?.oid

    fun get(id: DomainId<T>): Oid<T> =
        getOrNull(id) ?: error("${assetType.simpleName} OID not found: branch=$branch id=$id")
}

/**
 * Resolves the OIDs by which to refer to other assets in an ext API response. Pass null [ids] to resolve references to
 * all assets of the type.
 */
inline fun <reified T : LayoutAsset<T>> oidReferences(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    ids: Collection<IntId<T>>? = null,
): ExtOidReferencesV1<T> = ExtOidReferencesV1(T::class, branch, dao.fetchExternalIdsWithInheritance(branch, ids))

/** Resolves the OID by which to refer to a single other asset in an ext API response. */
inline fun <reified T : LayoutAsset<T>> oidReference(
    dao: IExternalIdDao<T>,
    branch: LayoutBranch,
    id: IntId<T>,
): Oid<T> = oidReferences(dao, branch, listOf(id)).get(id)

fun coordinateSystem(extCoordinateSystem: ExtSridV1?): Srid = extCoordinateSystem?.value ?: LAYOUT_SRID

fun resolution(extResolution: ExtResolutionV1?): Resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
