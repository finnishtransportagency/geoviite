package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.configuration.CACHE_FEATURE_TYPES
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getFeatureTypeCode
import fi.fta.geoviite.infra.util.getFreeText
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class CodeDictionaryDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Cacheable(CACHE_FEATURE_TYPES, sync = true)
    fun getFeatureTypes(): List<FeatureType> {
        val sql = "select code, description from common.feature_type"
        val params = emptyMap<String, Int>()
        val result =
            jdbcTemplate.query(sql, params) { rs, _ ->
                FeatureType(code = rs.getFeatureTypeCode("code"), description = rs.getFreeText("description"))
            }
        logger.daoAccess(FETCH, FeatureType::class, result.map { r -> r.code })
        return result
    }
}
