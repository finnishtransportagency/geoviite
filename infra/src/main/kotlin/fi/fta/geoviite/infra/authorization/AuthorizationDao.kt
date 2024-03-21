package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.configuration.CACHE_ROLES
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class AuthorizationDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    fun getRoles(roleCodes: List<Code>): List<Role> {
        return checkNotNull(getRolesInternal(roleCodes = roleCodes, userGroups = null)) {
            "No roles found: roleCodes=$roleCodes"
        }
    }

    @Cacheable(CACHE_ROLES, sync = true)
    fun getRolesByUserGroups(ldapGroups: List<Code>): List<Role>? {
        return getRolesInternal(roleCodes = null, userGroups = ldapGroups)
    }

    private fun getRolesInternal(roleCodes: List<Code>? = null, userGroups: List<Code>? = null): List<Role>? {
        if (roleCodes == null && userGroups == null) throw IllegalStateException("Can't fetch roles without names/groups")

        //language=SQL
        val sql = """
            select code, name from common.role 
              where (:role_codes = '' or code = any(string_to_array(:role_codes, ',')::varchar[]))
              and (:user_groups = '' or user_group = any(string_to_array(:user_groups, ',')::varchar[]))
        """.trimIndent()

        val params = mapOf(
            "role_codes" to (roleCodes?.joinToString(",") ?: ""),
            "user_groups" to (userGroups?.joinToString(",") ?: ""),
        )
        val roles = jdbcTemplate.query(sql, params) { rs, _ ->
            val code: Code = rs.getCode("code")
            Role(
                code = code,
                name = AuthName(rs.getString("name")),
                privileges = getRolePrivileges(code),
            )
        }
        logger.daoAccess(FETCH, Role::class, listOfNotNull(roleCodes, userGroups))

        return if (roles.isEmpty()) {
            null
        } else {
            roles
        }
    }

    private fun getRolePrivileges(code: Code): List<Privilege> {
        val sql = """
            select privilege.code, privilege.name, privilege.description
            from common.role_privilege rp
              left join common.privilege on privilege.code = rp.privilege_code
            where rp.role_code = :role_code
        """.trimIndent()
        val params = mapOf("role_code" to code)
        val privileges = jdbcTemplate.query(sql, params) { rs, _ ->
            Privilege(
                code = rs.getCode("code"),
                name = AuthName(rs.getString("name")),
                description = rs.getFreeText("description"),
            )
        }
        logger.daoAccess(FETCH, Privilege::class, privileges.map { p -> p.code })
        return privileges
    }
}
