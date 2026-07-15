package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutDesign
import fi.fta.geoviite.infra.tracklayout.LayoutDesignService

inline fun <reified T : LayoutAsset<T>> idLookup(dao: IExternalIdDao<T>, oid: Oid<T>): IntId<T> =
    dao.lookupByExternalId(oid)?.id ?: throwOidTargetNotFound(oid)

inline fun <reified T : LayoutAsset<T>> oidLookup(dao: IExternalIdDao<T>, branch: LayoutBranch, id: IntId<T>): Oid<T> =
    dao.fetchExternalId(branch, id)?.oid ?: throwOidNotFound(branch, id)

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

fun coordinateSystem(extCoordinateSystem: ExtSridV1?): Srid = extCoordinateSystem?.value ?: LAYOUT_SRID

fun resolution(extResolution: ExtResolutionV1?): Resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
