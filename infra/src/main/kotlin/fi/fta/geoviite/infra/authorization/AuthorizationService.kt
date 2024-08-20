package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.util.Code
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

const val LDAP_GROUP_GEOVIITE_PREFIX = "geoviite_"
const val DESIRED_ROLE_COOKIE_NAME = "desiredRole"

enum class IntegrationApiUserType(val roleCode: Code) {
    LOCAL(Code("api-private")),
    PUBLIC(Code("api-public")),
    PRIVATE(Code("api-private")),
}

@GeoviiteService
class AuthorizationService @Autowired constructor(private val authorizationDao: AuthorizationDao) {

    val defaultRoleCodeOrder by lazy {
        listOf(
            Code("operator"),
            Code("team"),
            Code("authority"),
            Code("consultant"),
            Code("browser"),
        )
    }

    fun getRole(roleCode: Code): Role? {
        return authorizationDao.getRoleByRoleCode(roleCode)
    }

    fun getRoles(roleCodes: List<Code>): List<Role> {
        return authorizationDao.getRolesByRoleCodes(roleCodes)
    }

    @Transactional(readOnly = true)
    fun getRolesByUserGroups(ldapGroupNames: List<Code>): List<Role> {
        return ldapGroupNames
            .filter { groupName -> groupName.startsWith(LDAP_GROUP_GEOVIITE_PREFIX) }
            .let { geoviiteUserGroups -> authorizationDao.getRolesByUserGroups(geoviiteUserGroups) }
    }

    fun getDefaultRole(userRoles: List<Role>): Role {
        check(userRoles.isNotEmpty()) { "There must be at least one available user role!" }

        return defaultRoleCodeOrder
            .asSequence()
            .mapNotNull { defaultRoleCode ->
                userRoles.find { userRole -> userRole.code == defaultRoleCode }
            }
            .firstOrNull() ?: userRoles.first()
    }
}
