package fi.fta.geoviite.infra.authorization

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import fi.fta.geoviite.infra.configuration.staticDataCacheDuration
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getCode
import fi.fta.geoviite.infra.util.queryOptional
import java.util.*
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

const val AUTHORIZATION_ROLE_CACHE_SIZE = 100L

@Transactional(readOnly = true)
@Component
class AuthorizationDao(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
    @Value("\${geoviite.cache.enabled}") val cacheEnabled: Boolean,
) : DaoBase(jdbcTemplateParam) {

    // Caffeine cache does not store null values, but they should also not be re-fetched
    // => Codes are stored as optionals.
    private val roleCodeCache: Cache<AuthCode, Optional<Role>> =
        Caffeine.newBuilder()
            .maximumSize(AUTHORIZATION_ROLE_CACHE_SIZE)
            .expireAfterAccess(staticDataCacheDuration)
            .build()

    private val userGroupCache: Cache<AuthCode, Optional<Role>> =
        Caffeine.newBuilder()
            .maximumSize(AUTHORIZATION_ROLE_CACHE_SIZE)
            .expireAfterAccess(staticDataCacheDuration)
            .build()

    fun getRolesByRoleCodes(roleCodes: List<AuthCode>): List<Role> {
        return roleCodes.mapNotNull { roleCode -> getRoleByRoleCode(roleCode = roleCode) }
    }

    fun getRolesByUserGroups(ldapGroups: List<AuthCode>): List<Role> {
        return ldapGroups.mapNotNull { userGroup -> getRoleByUserGroup(userGroup = userGroup) }
    }

    fun getRoleByRoleCode(roleCode: AuthCode): Role? {
        val role =
            if (cacheEnabled) {
                roleCodeCache.get(roleCode) { code -> fetchRoleInternal(roleCode = code) }
            } else {
                fetchRoleInternal(roleCode = roleCode)
            }

        return role.orElse(null)
    }

    fun getRoleByUserGroup(userGroup: AuthCode): Role? {
        val role =
            if (cacheEnabled) {
                userGroupCache.get(userGroup) { group -> fetchRoleInternal(userGroup = group) }
            } else {
                fetchRoleInternal(userGroup = userGroup)
            }

        return role.orElse(null)
    }

    private fun fetchRoleInternal(roleCode: AuthCode? = null, userGroup: AuthCode? = null): Optional<Role> {
        check(roleCode != null || userGroup != null) { "Can't fetch role without name/group" }

        // language=SQL
        val sql =
            """
            select code from common.role 
            where (:role_code::varchar is null or code = :role_code) 
              and (:user_group::varchar is null or user_group = :user_group)
        """
                .trimIndent()
        val params = mapOf("role_code" to roleCode, "user_group" to userGroup)
        val role =
            jdbcTemplate.queryOptional(sql, params) { rs, _ ->
                val code: AuthCode = rs.getCode("code")
                Role(code = code, privileges = fetchRolePrivilegesInternal(code))
            }
        logger.daoAccess(FETCH, Role::class, listOfNotNull(roleCode, userGroup))
        return Optional.ofNullable(role)
    }

    private fun fetchRolePrivilegesInternal(code: AuthCode): List<Privilege> {
        val sql =
            """
            select privilege.code
            from common.role_privilege rp
              left join common.privilege on privilege.code = rp.privilege_code
            where rp.role_code = :role_code
        """
                .trimIndent()
        val params = mapOf("role_code" to code)
        val privileges = jdbcTemplate.query(sql, params) { rs, _ -> Privilege(code = rs.getCode("code")) }
        logger.daoAccess(FETCH, Privilege::class, privileges.map { p -> p.code })
        return privileges
    }
}
