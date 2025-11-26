package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.ratko.IExternalIdDao
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAsset

inline fun <reified T : LayoutAsset<T>> idLookup(dao: IExternalIdDao<T>, oid: Oid<T>): IntId<T> =
    dao.lookupByExternalId(oid)?.id ?: throwOidNotFound(oid)

inline fun <reified T : LayoutAsset<T>> oidLookup(dao: IExternalIdDao<T>, branch: LayoutBranch, id: IntId<T>): Oid<T> =
    dao.fetchExternalId(branch, id)?.oid ?: throwOidNotFound(branch, id)

fun coordinateSystem(extCoordinateSystem: ExtSridV1?): Srid = extCoordinateSystem?.value ?: LAYOUT_SRID

fun resolution(extResolution: ExtResolutionV1?): Resolution = extResolution?.toResolution() ?: Resolution.ONE_METER
