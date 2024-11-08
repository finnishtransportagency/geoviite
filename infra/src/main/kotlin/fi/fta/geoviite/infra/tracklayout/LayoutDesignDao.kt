package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.error.DuplicateDesignNameException
import fi.fta.geoviite.infra.error.getPSQLExceptionConstraintAndDetailOrRethrow
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.DbTable
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLocalDate
import fi.fta.geoviite.infra.util.getRowVersion
import fi.fta.geoviite.infra.util.queryOne
import fi.fta.geoviite.infra.util.setUser
import fi.fta.geoviite.infra.util.toDbId
import java.time.Instant
import org.postgresql.util.PSQLException
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
@Transactional(readOnly = true)
class LayoutDesignDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun fetch(id: IntId<LayoutDesign>): LayoutDesign {
        val sql =
            """
            select id, name, estimated_completion, design_state
            from layout.design
            where id = :id
        """
                .trimIndent()
        return jdbcTemplate.queryOne(sql, mapOf("id" to id.intValue)) { rs, _ ->
            LayoutDesign(
                rs.getIntId("id"),
                LayoutDesignName(rs.getString("name")),
                rs.getLocalDate("estimated_completion"),
                rs.getEnum("design_state"),
            )
        }
    }

    fun list(includeCompleted: Boolean = false, includeDeleted: Boolean = false): List<LayoutDesign> {
        val sql =
            """
            select id, name, estimated_completion, design_state
            from layout.design
            where design_state = 'ACTIVE'::layout.design_state 
              or :include_completed is true and design_state = 'COMPLETED'::layout.design_state
              or :include_deleted is true and design_state = 'DELETED'::layout.design_state
        """
                .trimIndent()
        return jdbcTemplate.query(
            sql,
            mapOf("include_completed" to includeCompleted, "include_deleted" to includeDeleted),
        ) { rs, _ ->
            LayoutDesign(
                rs.getIntId("id"),
                LayoutDesignName(rs.getString("name")),
                rs.getLocalDate("estimated_completion"),
                rs.getEnum("design_state"),
            )
        }
    }

    @Transactional
    fun update(id: DomainId<LayoutDesign>, design: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        jdbcTemplate.setUser()
        val params =
            mapOf(
                "id" to toDbId(id).intValue,
                "name" to design.name,
                "estimated_completion" to design.estimatedCompletion,
                "design_state" to design.designState.name,
            )

        val sql =
            """
            update layout.design
            set name = :name,
                estimated_completion = :estimated_completion,
                design_state = :design_state::layout.design_state
            where id = :id
            returning id, version
        """
                .trimIndent()
        val response =
            jdbcTemplate.queryForObject(sql, params) { rs, _ -> rs.getRowVersion<LayoutDesign>("id", "version") }
                ?: error { "Failed to generate ID for new row version of updated layout design" }
        logger.daoAccess(AccessType.UPDATE, LayoutDesign::class, response)
        return response.id
    }

    @Transactional
    fun insert(design: LayoutDesignSaveRequest): IntId<LayoutDesign> {
        jdbcTemplate.setUser()
        val sql =
            """
            insert into layout.design (name, estimated_completion, design_state)
            values (:name, :estimated_completion, :design_state::layout.design_state)
            returning id, version
        """
                .trimIndent()
        val response =
            jdbcTemplate.queryForObject(
                sql,
                mapOf(
                    "name" to design.name,
                    "estimated_completion" to design.estimatedCompletion,
                    "design_state" to design.designState.name,
                ),
            ) { rs, _ ->
                rs.getRowVersion<LayoutDesign>("id", "version")
            } ?: error { "Failed to generate ID for new layout design" }
        logger.daoAccess(AccessType.INSERT, LayoutDesign::class, response)
        return response.id
    }

    fun getChangeTime(): Instant {
        return fetchLatestChangeTime(DbTable.LAYOUT_DESIGN)
    }
}

private val duplicateNameErrorRegex = Regex("""Key \(lower\(name::text\)\)=\(([^,]+)\) conflicts with existing key""")

fun asDuplicateNameException(e: DataIntegrityViolationException): DuplicateDesignNameException? =
    e.cause
        .let { cause -> cause as? PSQLException }
        ?.let { cause -> getPSQLExceptionConstraintAndDetailOrRethrow(cause) }
        ?.let { (constraint, detail) ->
            duplicateNameErrorRegex
                .matchAt(detail, 0)
                ?.let { match -> match.groups[1]?.value }
                ?.let { name -> constraint to name }
        }
        ?.let { (constraint, name) ->
            if (constraint == "layout_design_unique_name") {
                DuplicateDesignNameException(name, e)
            } else {
                null
            }
        }
