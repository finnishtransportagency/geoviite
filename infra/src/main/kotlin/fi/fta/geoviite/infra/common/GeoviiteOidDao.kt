package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getOid
import fi.fta.geoviite.infra.util.queryOne
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component

enum class OidType {
    OPERATIONAL_POINT
}

enum class OidSequenceState {
    ACTIVE,
    DEPRECATED,
}

@Component
class GeoviiteOidDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {
    fun <T> reserveOid(type: OidType): Oid<T> {
        val sql =
            """
                select common.generate_oid(:type) as oid
            """
                .trimIndent()
        return jdbcTemplate.queryOne(sql, mapOf("type" to type.name)) { rs, _ -> rs.getOid("oid") }
    }
}
