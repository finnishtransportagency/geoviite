package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.ratko.IExternalIdDao

interface IExternallyIdentifiedLayoutAssetDao<T : LayoutAsset<T>> : LayoutAssetReader<T>, IExternalIdDao<T> {
    fun getByExternalId(oid: Oid<T>): T? {
        return lookupByExternalId(oid)?.let { rowByOid -> get(rowByOid.context, rowByOid.id) }
    }

    fun getByExternalIds(context: LayoutContext, oids: List<Oid<T>>): Map<Oid<T>, T?> {
        val oidMapping = lookupByExternalIds(oids)
        val oidToNonNullId = oidMapping.mapNotNull { (oid, rowId) -> rowId?.let { oid to rowId.id } }.toMap()
        val assets = getMany(context, oidToNonNullId.values.toList()).associateBy { asset -> asset.id }

        return oids.associateWith { oid -> oidToNonNullId[oid]?.let { id -> assets[id] } }
    }
}
