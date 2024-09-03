package fi.fta.geoviite.infra.switchLibrary

import fi.fta.geoviite.infra.configuration.CACHE_COMMON_SWITCH_OWNER
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.logging.AccessType
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getIntId
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class SwitchOwnerDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Cacheable(CACHE_COMMON_SWITCH_OWNER, sync = true)
    fun fetchSwitchOwners(): List<SwitchOwner> {
        val sql =
            """
            select
                id,
                name
            from common.switch_owner
        """
                .trimIndent()
        val switchOwners =
            jdbcTemplate.query(sql) { rs, _ ->
                SwitchOwner(id = rs.getIntId("id"), name = MetaDataName(rs.getString("name")))
            }
        logger.daoAccess(AccessType.FETCH, SwitchOwner::class, switchOwners.map { it.id })
        return switchOwners
    }
}
