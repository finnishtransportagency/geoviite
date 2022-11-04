package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.configuration.CACHE_ROLES
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.*
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AuthorizationDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun getRole(roleCode: Code): Role =
        getRoleInternal(roleCode = roleCode, userGroup = null)
            ?: throw IllegalStateException("No such role: roleCode=${roleCode.value}")

    @Cacheable(CACHE_ROLES, sync = true)
    @Transactional
    fun getRoleByUserGroup(ldapGroup: Code): Role? =
        getRoleInternal(roleCode = null, userGroup = ldapGroup)

    private fun getRoleInternal(roleCode: Code? = null, userGroup: Code? = null): Role? {
        if (roleCode == null && userGroup == null) throw IllegalStateException("Can't fetch role without name/group")

        val sql = """
            select code, name from common.role 
            where (:role_code::varchar is null or code = :role_code) 
              and (:user_group::varchar is null or user_group = :user_group)
        """.trimIndent()
        val params = mapOf(
            "role_code" to roleCode?.value,
            "user_group" to userGroup?.value,
        )
        val role = jdbcTemplate.queryOptional(sql, params) { rs, _ ->
            val code: Code = rs.getCode("code")
            Role(
                code = code,
                name = AuthName(rs.getString("name")),
                privileges = getRolePrivileges(code),
            )
        }
        logger.daoAccess(FETCH, Role::class, listOfNotNull(roleCode, userGroup))
        return role
    }

    private fun getRolePrivileges(code: Code): List<Privilege> {
        val sql = """
            select privilege.code, privilege.name, privilege.description
            from common.role_privilege rp
              left join common.privilege on privilege.code = rp.privilege_code
            where rp.role_code = :role_code
        """.trimIndent()
        val params = mapOf("role_code" to code.value)
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
