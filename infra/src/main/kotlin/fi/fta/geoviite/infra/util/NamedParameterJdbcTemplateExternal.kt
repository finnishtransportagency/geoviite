package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.authorization.getCurrentUserName
import fi.fta.geoviite.infra.error.NoSuchEntityException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.sql.ResultSet

inline fun <reified T> NamedParameterJdbcTemplate.queryOptional(
    sql: String,
    params: Map<String, *>,
    noinline mapper: (rs: ResultSet, index: Int) -> T,
): T? {
    val result = query(sql, params, mapper)
    if (result.size > 1) throw IllegalStateException("Cannot have more than one ${T::class.simpleName} with same ID")
    return result.firstOrNull()
}

inline fun <reified T> NamedParameterJdbcTemplate.queryOne(
    sql: String,
    params: Map<String, *>,
    identifier: String,
    noinline mapper: (rs: ResultSet, index: Int) -> T,
): T = queryOptional(sql, params, mapper) ?: throw NoSuchEntityException(T::class, identifier)

fun NamedParameterJdbcTemplate.setUser() = setUser(getCurrentUserName())

fun NamedParameterJdbcTemplate.setUser(userName: UserName) {
    // set doesn't work with parameters, but it's validated in UserName constructor
    update("set local geoviite.edit_user = '${userName.value}';", mapOf<String, Any>())
}
