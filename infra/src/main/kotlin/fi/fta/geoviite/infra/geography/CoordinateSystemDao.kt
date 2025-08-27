package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.configuration.CACHE_ANY_COORDINATE_SYSTEMS
import fi.fta.geoviite.infra.configuration.CACHE_GVT_COORDINATE_SYSTEMS
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType.FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.getSrid
import fi.fta.geoviite.infra.util.getStringListFromString
import org.springframework.cache.annotation.Cacheable
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Transactional(readOnly = true)
@Component
class CoordinateSystemDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Cacheable(CACHE_GVT_COORDINATE_SYSTEMS, sync = true)
    fun fetchApplicationCoordinateSystems(): List<CoordinateSystem> {
        val sql =
            """
                select 
                  srid,
                  name,
                  array_to_string(aliases, ',') as aliases_str
                from common.coordinate_system
            """
                .trimIndent()
        val systems =
            jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
                CoordinateSystem(
                    srid = rs.getSrid("srid"),
                    name = CoordinateSystemName(rs.getString("name")),
                    aliases = rs.getStringListFromString("aliases_str").map(::CoordinateSystemName),
                )
            }
        logger.daoAccess(FETCH, CoordinateSystem::class, systems.map { r -> r.srid })
        return systems
    }

    @Cacheable(CACHE_ANY_COORDINATE_SYSTEMS, sync = true)
    fun fetchCoordinateSystem(srid: Srid): CoordinateSystem {
        val sql =
            """
                select 
                  ref.srid,
                  cs.name as cs_name,
                  split_part(ref.srtext, '"', 2) as sr_name,
                  coalesce(array_to_string(cs.aliases, ','), '') as aliases_str
                from postgis.spatial_ref_sys ref 
                  left join common.coordinate_system cs on cs.srid = ref.srid
                where ref.srid = :srid 
            """
                .trimIndent()
        val system =
            jdbcTemplate
                .query(sql, mapOf("srid" to srid.code)) { rs, _ ->
                    CoordinateSystem(
                        srid = rs.getSrid("srid"),
                        name = CoordinateSystemName(rs.getString("cs_name") ?: rs.getString("sr_name")),
                        aliases =
                            rs.getStringListFromString("aliases_str")
                                .filter(String::isNotBlank)
                                .map(::CoordinateSystemName),
                    )
                }
                .firstOrNull() ?: throw NoSuchEntityException(CoordinateSystem::class, srid.toString())
        logger.daoAccess(FETCH, CoordinateSystem::class, srid)
        return system
    }
}
