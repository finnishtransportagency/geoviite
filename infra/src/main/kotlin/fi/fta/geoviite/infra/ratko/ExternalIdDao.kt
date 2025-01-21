package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutRowId
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLayoutBranch
import fi.fta.geoviite.infra.util.getLayoutRowId
import fi.fta.geoviite.infra.util.getOid
import fi.fta.geoviite.infra.util.queryOptional
import java.time.Instant
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

interface IExternalIdDao<T : LayoutAsset<T>> {
    fun getExternalIdChangeTime(): Instant

    fun insertExternalIdInExistingTransaction(branch: LayoutBranch, id: IntId<T>, oid: Oid<T>)

    fun fetchExternalId(branch: LayoutBranch, id: IntId<T>): Oid<T>?

    fun fetchExternalIds(branch: LayoutBranch, ids: List<IntId<T>>? = null): Map<IntId<T>, Oid<T>>

    fun fetchExternalIdsByBranch(id: IntId<T>): Map<LayoutBranch, Oid<T>>

    fun lookupByExternalId(oid: Oid<T>): LayoutRowId<T>?
}

class ExternalIdDao<T : LayoutAsset<T>>(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    val extIdTable: String,
    val extIdVersionTable: String,
) : DaoBase(jdbcTemplateParam), IExternalIdDao<T> {
    override fun getExternalIdChangeTime(): Instant = fetchLatestChangeTimeFromTable(extIdVersionTable)

    override fun insertExternalIdInExistingTransaction(branch: LayoutBranch, id: IntId<T>, oid: Oid<T>) {
        val sql =
            """
            insert into $extIdTable (id, layout_context_id, design_id, external_id)
            values (:id, :layout_context_id, :design_id, :external_id)
        """
                .trimIndent()
        jdbcTemplate.update(
            sql,
            mapOf(
                "id" to id.intValue,
                "design_id" to branch.designId?.intValue,
                "layout_context_id" to branch.official.toSqlString(),
                "external_id" to oid.toString(),
            ),
        )
    }

    override fun fetchExternalId(branch: LayoutBranch, id: IntId<T>): Oid<T>? {
        val sql = """select external_id from $extIdTable where id = :id and design_id is not distinct from :design_id"""
        return jdbcTemplate.queryOptional(sql, mapOf("id" to id.intValue, "design_id" to branch.designId?.intValue)) {
            rs,
            _ ->
            rs.getOid<T>("external_id")
        }
    }

    override fun fetchExternalIds(branch: LayoutBranch, ids: List<IntId<T>>?): Map<IntId<T>, Oid<T>> {
        if (ids != null && ids.isEmpty()) {
            return mapOf()
        }
        val idsInclusionFragment = if (ids == null) "true" else "(id = any(array[:ids]))"

        val sql =
            """
            select id, external_id from $extIdTable
            where design_id is not distinct from :design_id
              and $idsInclusionFragment"""
        return jdbcTemplate
            .query(sql, mapOf("design_id" to branch.designId?.intValue, "ids" to ids?.map { it.intValue })) { rs, _ ->
                rs.getIntId<T>("id") to rs.getOid<T>("external_id")
            }
            .associate { it }
    }

    override fun fetchExternalIdsByBranch(id: IntId<T>): Map<LayoutBranch, Oid<T>> {
        val sql = """select design_id, external_id from $extIdTable where id = :id"""
        return jdbcTemplate
            .query(sql, mapOf("id" to id.intValue)) { rs, _ ->
                rs.getLayoutBranch("design_id") to rs.getOid<T>("external_id")
            }
            .associate { it }
    }

    override fun lookupByExternalId(oid: Oid<T>): LayoutRowId<T>? {
        val sql = """select id, design_id, false as draft from $extIdTable where external_id = :external_id"""
        return jdbcTemplate.queryOptional(sql, mapOf("external_id" to oid.toString())) { rs, _ ->
            rs.getLayoutRowId("id", "design_id", "draft")
        }
    }
}
