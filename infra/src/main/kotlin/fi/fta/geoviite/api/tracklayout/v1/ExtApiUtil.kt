package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset

inline fun <reified T : LayoutAsset<T>> idLookup(dao: IExternalIdDao<T>, oid: Oid<T>): IntId<T> =
    dao.lookupByExternalId(oid)?.id ?: throwOidNotFound(oid)

inline fun <reified T : LayoutAsset<T>> oidLookup(dao: IExternalIdDao<T>, branch: LayoutBranch, id: IntId<T>): Oid<T> =
    dao.fetchExternalId(branch, id)?.oid ?: throwOidNotFound(branch, id)
