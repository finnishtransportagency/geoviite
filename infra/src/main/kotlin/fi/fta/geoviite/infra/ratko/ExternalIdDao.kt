package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RatkoExternalId
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutBranch
import fi.fta.geoviite.infra.util.getLayoutRowIdOrNull
import fi.fta.geoviite.infra.util.getOid
import fi.fta.geoviite.infra.util.getRatkoExternalId
import fi.fta.geoviite.infra.util.getRatkoExternalIdOrNull
import fi.fta.geoviite.infra.util.queryOptional
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

interface IExternalIdDao<T : LayoutAsset<T>> {
    fun getExternalIdChangeTime(): Instant

    fun savePlanItemIdInExistingTransaction(branch: DesignBranch, id: IntId<T>, planItemId: RatkoPlanItemId)

    fun insertExternalIdInExistingTransaction(branch: LayoutBranch, id: IntId<T>, oid: Oid<T>)

    fun fetchExternalId(branch: LayoutBranch, id: IntId<T>): RatkoExternalId<T>?

    fun fetchExternalIdsWithInheritance(
        branch: LayoutBranch,
        ids: List<IntId<T>>? = null,
    ): Map<IntId<T>, RatkoExternalId<T>>

    fun fetchExternalIds(branch: LayoutBranch, ids: List<IntId<T>>? = null): Map<IntId<T>, RatkoExternalId<T>>

    fun fetchExternalIdsByBranch(id: IntId<T>): Map<LayoutBranch, RatkoExternalId<T>>

    fun lookupByExternalId(oid: Oid<T>): LayoutRowId<T>?

    fun lookupByExternalIds(oids: List<Oid<T>>): Map<Oid<T>, LayoutRowId<T>?>
}

class ExternalIdDao<T : LayoutAsset<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val extIdTable: String,
    val extIdVersionTable: String,
) : DaoBase(jdbcTemplateParam), IExternalIdDao<T> {
    override fun getExternalIdChangeTime(): Instant = fetchLatestChangeTimeFromTable(extIdVersionTable)

    override fun savePlanItemIdInExistingTransaction(branch: DesignBranch, id: IntId<T>, planItemId: RatkoPlanItemId) {
        val sql =
            """
            update $extIdTable
            set plan_item_id = :plan_item_id
            where id = :id and layout_context_id = :layout_context_id
        """
                .trimIndent()
        jdbcTemplate
            .update(
                sql,
                mapOf(
                    "id" to id.intValue,
                    "layout_context_id" to branch.official.toSqlString(),
                    "plan_item_id" to planItemId.intValue,
                ),
            )
            .also { logger.daoAccess(AccessType.UPSERT, RatkoPlanItemId::class, id) }
    }

    override fun insertExternalIdInExistingTransaction(branch: LayoutBranch, id: IntId<T>, oid: Oid<T>) {
        val sql =
            """
            insert into $extIdTable (id, layout_context_id, design_id, external_id)
            values (:id, :layout_context_id, :design_id, :external_id)
        """
                .trimIndent()
        jdbcTemplate
            .update(
                sql,
                mapOf(
                    "id" to id.intValue,
                    "design_id" to branch.designId?.intValue,
                    "layout_context_id" to branch.official.toSqlString(),
                    "external_id" to oid.toString(),
                ),
            )
            .also { logger.daoAccess(AccessType.UPSERT, RatkoPlanItemId::class, id) }
    }

    override fun fetchExternalId(branch: LayoutBranch, id: IntId<T>): RatkoExternalId<T>? {
        val sql =
            """
               select external_id, plan_item_id
               from $extIdTable
               where id = :id and design_id is not distinct from :design_id;
            """
                .trimIndent()
        return jdbcTemplate
            .queryOptional(sql, mapOf("id" to id.intValue, "design_id" to branch.designId?.intValue)) { rs, _ ->
                rs.getRatkoExternalIdOrNull<T>("external_id", "plan_item_id")
            }
            .also { logger.daoAccess(AccessType.FETCH, RatkoExternalId::class, id) }
    }

    override fun fetchExternalIdsWithInheritance(
        branch: LayoutBranch,
        ids: List<IntId<T>>?,
    ): Map<IntId<T>, RatkoExternalId<T>> = fetchExternalIds(branch, ids, withInheritance = true)

    override fun fetchExternalIds(branch: LayoutBranch, ids: List<IntId<T>>?): Map<IntId<T>, RatkoExternalId<T>> =
        fetchExternalIds(branch, ids, withInheritance = false)

    private fun fetchExternalIds(
        branch: LayoutBranch,
        ids: List<IntId<T>>?,
        withInheritance: Boolean,
    ): Map<IntId<T>, RatkoExternalId<T>> {
        if (ids != null && ids.isEmpty()) {
            return mapOf()
        }
        val designIdFragment =
            if (withInheritance) "(design_id is null or design_id is not distinct from :design_id)"
            else "design_id is not distinct from :design_id"
        val idsInclusionFragment = if (ids == null) "true" else "(id = any(array[:ids]))"

        val sql =
            """
            select id, external_id, plan_item_id from $extIdTable t
            where $designIdFragment and $idsInclusionFragment
              and not (:design_id::int is not null
                       and design_id is null
                       and exists (select * from $extIdTable overrider
                                   where overrider.id = t.id and overrider.design_id = :design_id))"""
        return jdbcTemplate
            .query(sql, mapOf("design_id" to branch.designId?.intValue, "ids" to ids?.map { it.intValue })) { rs, _ ->
                rs.getIntId<T>("id") to rs.getRatkoExternalId<T>("external_id", "plan_item_id")
            }
            .associate { it }
    }

    override fun fetchExternalIdsByBranch(id: IntId<T>): Map<LayoutBranch, RatkoExternalId<T>> {
        val sql = """select design_id, external_id, plan_item_id from $extIdTable where id = :id"""
        return jdbcTemplate
            .query(sql, mapOf("id" to id.intValue)) { rs, _ ->
                rs.getLayoutBranch("design_id") to rs.getRatkoExternalId<T>("external_id", "plan_item_id")
            }
            .associate { it }
            .also { logger.daoAccess(AccessType.FETCH, RatkoExternalId::class, "ALL") }
    }

    override fun lookupByExternalId(oid: Oid<T>): LayoutRowId<T>? {
        return lookupByExternalIds(listOf(oid)).values.firstOrNull()
    }

    override fun lookupByExternalIds(oids: List<Oid<T>>): Map<Oid<T>, LayoutRowId<T>?> {
        if (oids.isEmpty()) return emptyMap()

        val sql =
            """
            select external_id, id, design_id, false as draft 
            from $extIdTable 
            where external_id = any(:external_ids::varchar[])
      """

        val params = mapOf("external_ids" to oids.map { it.toString() }.toTypedArray())

        return jdbcTemplate
            .query(sql, params) { rs, _ ->
                val oid = rs.getOid<T>("external_id")
                val layoutRowId = rs.getLayoutRowIdOrNull<T>("id", "design_id", "draft")

                oid to layoutRowId
            }
            .toMap()
            .also { logger.daoAccess(AccessType.VERSION_FETCH, "lookupByExternalIds", oids) }
    }
}
