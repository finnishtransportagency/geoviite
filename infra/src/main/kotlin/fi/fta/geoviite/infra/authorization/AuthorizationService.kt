package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.aspects.GeoviiteService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

const val LDAP_GROUP_GEOVIITE_PREFIX = "geoviite_"
const val DESIRED_ROLE_COOKIE_NAME = "desiredRole"

enum class ExtApiUserType(val roleCode: AuthCode) {
    LOCAL(AuthCode("api-private")),
    PUBLIC(AuthCode("api-public")),
    PRIVATE(AuthCode("api-private")),
}

@GeoviiteService
class AuthorizationService @Autowired constructor(private val authorizationDao: AuthorizationDao) {

    val defaultRoleCodeOrder by lazy {
        listOf(
            AuthCode("operator"),
            AuthCode("team"),
            AuthCode("authority"),
            AuthCode("consultant"),
            AuthCode("browser"),
        )
    }

    fun getRole(roleCode: AuthCode): Role? {
        return authorizationDao.getRoleByRoleCode(roleCode)
    }

    fun getRoles(roleCodes: List<AuthCode>): List<Role> {
        return authorizationDao.getRolesByRoleCodes(roleCodes)
    }

    @Transactional(readOnly = true)
    fun getRolesByUserGroups(ldapGroupNames: List<AuthCode>): List<Role> {
        return ldapGroupNames
            .filter { groupName -> groupName.startsWith(LDAP_GROUP_GEOVIITE_PREFIX) }
            .let { geoviiteUserGroups -> authorizationDao.getRolesByUserGroups(geoviiteUserGroups) }
    }

    fun getDefaultRole(userRoles: List<Role>): Role {
        check(userRoles.isNotEmpty()) { "There must be at least one available user role!" }

        return defaultRoleCodeOrder
            .asSequence()
            .mapNotNull { defaultRoleCode -> userRoles.find { userRole -> userRole.code == defaultRoleCode } }
            .firstOrNull() ?: userRoles.first()
    }
}
