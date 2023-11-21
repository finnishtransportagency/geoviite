package fi.fta.geoviite.infra.authorization

import fi.fta.geoviite.infra.util.Code
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val LDAP_GROUP_GEOVIITE_PREFIX = "geoviite_"

@Service
class AuthorizationService @Autowired constructor(private val authorizationDao: AuthorizationDao) {

    fun getRole(roleCode: Code): Role {
        return authorizationDao.getRole(roleCode)
    }

    @Transactional(readOnly = true)
    fun getRoleByUserGroups(ldapGroupNames: List<Code>): Role? {
        return ldapGroupNames
            .filter { groupName -> groupName.startsWith(LDAP_GROUP_GEOVIITE_PREFIX) }
            .firstNotNullOfOrNull { groupName -> authorizationDao.getRoleByUserGroup(groupName) }
    }
}
