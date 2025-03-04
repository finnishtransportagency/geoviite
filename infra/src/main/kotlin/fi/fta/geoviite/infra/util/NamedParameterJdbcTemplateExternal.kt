package fi.fta.geoviite.infra.util

import currentUser
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.error.NoSuchEntityException
import java.sql.ResultSet
import org.springframework.jdbc.core.ParameterizedPreparedStatementSetter
import org.springframework.jdbc.core.RowMapper
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

inline fun <reified T> NamedParameterJdbcTemplate.queryNotNull(
    sql: String,
    params: Map<String, *>,
    noinline mapper: (rs: ResultSet, index: Int) -> T?,
): List<T> = query(sql, params, mapper).mapNotNull { i -> i }

inline fun <reified T> NamedParameterJdbcTemplate.queryOptional(
    sql: String,
    params: Map<String, *>,
    noinline mapper: (rs: ResultSet, index: Int) -> T,
): T? {
    val result = query(sql, params, mapper)
    if (result.size > 1) error("Cannot have more than one ${T::class.simpleName} with same ID")
    return result.firstOrNull()
}

inline fun <reified T> NamedParameterJdbcTemplate.queryOne(
    sql: String,
    params: Map<String, *> = mapOf<String, Any>(),
    identifier: String = "value",
    noinline mapper: (rs: ResultSet, index: Int) -> T,
): T = queryOptional(sql, params, mapper) ?: throw NoSuchEntityException(T::class, identifier)

fun NamedParameterJdbcTemplate.setUser() = setUser(currentUser.get())

fun NamedParameterJdbcTemplate.setPgroutingPath() {
    update("set local search_path to pgrouting", mapOf<String, Any>())
}

fun NamedParameterJdbcTemplate.setUser(userName: UserName) {
    // set doesn't work with parameters, but it's validated in UserName constructor
    update("set local geoviite.edit_user = '$userName';", mapOf<String, Any>())
}

fun <T> NamedParameterJdbcTemplate.batchUpdateIndexed(
    sql: String,
    items: List<T>,
    paramSetter: ParameterizedPreparedStatementSetter<Pair<Int, T>>,
) = batchUpdate(sql, items.mapIndexed { index, item -> index to item }, paramSetter)

fun <T> NamedParameterJdbcTemplate.batchUpdate(
    sql: String,
    items: List<T>,
    paramSetter: ParameterizedPreparedStatementSetter<T>,
): Array<IntArray> = jdbcTemplate.batchUpdate(sql, items, items.size, paramSetter)

fun <T> NamedParameterJdbcTemplate.query(sql: String, rowMapper: RowMapper<T>) =
    query(sql, emptyMap<String, Any>(), rowMapper)
