package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getEnum
import fi.fta.geoviite.infra.util.getFreeText
import fi.fta.geoviite.infra.util.getInstant
import fi.fta.geoviite.infra.util.getIntId
import fi.fta.geoviite.infra.util.getLocalDate
import fi.fta.geoviite.infra.util.setUser
import fi.fta.geoviite.infra.util.toDbId
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.sql.Timestamp

@Component
@Transactional(readOnly = true)
class LayoutDesignDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    fun list(): List<LayoutDesign> {
        val sql = """
            select id, name, estimated_completion, plan_phase, design_state
            from layout.design
        """.trimIndent()
        return jdbcTemplate.query(sql) { rs, _ ->
            LayoutDesign(
                rs.getIntId("id"),
                rs.getFreeText("name"),
                rs.getLocalDate("estimated_completion"),
                rs.getEnum("plan_phase"),
                rs.getEnum("design_state"),
            )
        }
    }

    @Transactional
    fun update(design: LayoutDesign) {
        jdbcTemplate.setUser()
        val sql = """
            update layout.design
            set name = :name,
                estimated_completion = :estimated_completion,
                plan_phase = :plan_phase::geometry.plan_phase,
                design_state = :design_state::layout.design_state
            where id = :id
        """.trimIndent()
        jdbcTemplate.update(
            sql, mapOf(
                "id" to toDbId(design.id).intValue,
                "name" to design.name,
                "estimated_completion" to design.estimatedCompletion,
                "plan_phase" to design.planPhase.name,
                "design_state" to design.designState.name,
            )
        )
    }

    @Transactional
    fun insert(design: LayoutDesign): IntId<LayoutDesign> {
        jdbcTemplate.setUser()
        val sql = """
            insert into layout.design (name, estimated_completion, plan_phase, design_state)
            values (:name, :estimated_completion, :plan_phase::geometry.plan_phase, :design_state::layout.design_state)
            returning id
        """.trimIndent()
        return jdbcTemplate.query(
            sql, mapOf(
                "name" to design.name,
                "estimated_completion" to design.estimatedCompletion,
                "plan_phase" to design.planPhase.name,
                "design_state" to design.designState.name,
            )
        ) { rs, _ -> rs.getIntId<LayoutDesign>("id") }[0]
    }
}
