package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType.VERSION_FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.tracklayout.ChangeTimes
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import java.time.Instant
import kotlin.reflect.KClass

enum class DbTable(schema: String, table: String, sortColumns: List<String> = listOf("id")) {

    LAYOUT_ALIGNMENT("layout", "alignment"),
    LAYOUT_LOCATION_TRACK("layout", "location_track"),
    LAYOUT_REFERENCE_LINE("layout", "reference_line"),
    LAYOUT_SWITCH("layout", "switch"),
    LAYOUT_KM_POST("layout", "km_post", listOf("track_number_id", "km_number")),
    LAYOUT_TRACK_NUMBER("layout", "track_number"),

    GEOMETRY_PLAN("geometry", "plan"),
    GEOMETRY_ALIGNMENT("geometry", "alignment"),
    GEOMETRY_SWITCH("geometry", "switch"),
    GEOMETRY_KM_POST("geometry", "km_post", listOf("track_number_id", "km_number")),
    GEOMETRY_TRACK_NUMBER("geometry", "track_number");

    val fullName: String = "$schema.$table"
    val versionTable = "$schema.${table}_version"
    val draftLink: String = "draft_of_${table}_id"
    val orderBy: String = sortColumns.joinToString(",")
}

open class DaoBase(private val jdbcTemplateParam: NamedParameterJdbcTemplate?) {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The template from DI is nullable so that we can configure to run without DB when needed (ie. unit tests)
     * For actual code, use this non-null variable. It will throw on first use if DB-initialization is not done.
     */
    protected val jdbcTemplate: NamedParameterJdbcTemplate by lazy {
        jdbcTemplateParam ?: throw IllegalStateException("Database connection not initialized")
    }

    protected fun <T> fetchRowVersion(id: IntId<T>, table: DbTable): RowVersion<T> {
        logger.daoAccess(VERSION_FETCH, "fetchRowVersion", "id" to id, "table" to table.fullName)
        return queryRowVersion("select id, version from ${table.fullName} where id = :id", id)
    }


    protected fun <T> fetchRowVersions(table: DbTable): List<RowVersion<T>> {
        logger.daoAccess(VERSION_FETCH, "fetchRowVersions", "table" to table.fullName)
        val sql = "select id, version from ${table.fullName} order by ${table.orderBy}"
        return jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ ->
            rs.getRowVersion("id", "version")
        }
    }

    protected fun <T> fetchChangeTimes(id: IntId<T>, table: DbTable): ChangeTimes {
        val sql = """
            select 
              greatest(main_row.change_time, draft_row.change_time) as change_time,
              case when main_row.draft then null else main_row.change_time end as official_change_time, 
              case when main_row.draft then main_row.change_time else draft_row.change_time end as draft_change_time,
              version1.change_time as creation_time
            from ${table.fullName} main_row
              left join ${table.fullName} draft_row on draft_row.${table.draftLink} = main_row.id
              inner join ${table.versionTable} version1 on main_row.id = version1.id and version1.version = 1 
            where (main_row.${table.draftLink} is null) 
              and (main_row.id = :id or draft_row.id = :id)
        """.trimMargin()
        return jdbcTemplate.queryForObject(sql, mapOf("id" to id.intValue)) { rs, _ ->
            ChangeTimes(
                created = rs.getInstant("creation_time"),
                changed = rs.getInstant("change_time"),
                officialChanged = rs.getInstantOrNull("official_change_time"),
                draftChanged = rs.getInstantOrNull("draft_change_time"),
            )
        } ?: throw IllegalStateException("Failed to fetch change times: id=$id table=$table")
    }

    protected fun fetchLatestChangeTime(table: DbTable): Instant {
        val sql = "select max(change_time) change_time from ${table.versionTable}"
        return jdbcTemplate.query(sql, mapOf<String, Any>()) { rs, _ -> rs.getInstantOrNull("change_time") }
            .firstOrNull()
            ?: Instant.ofEpochSecond(0)
    }

    protected fun <T> createListString(items: List<T>, mapping: (t: T) -> Double?) = when {
        items.none { i -> mapping(i) != null } -> null
        else -> items.joinToString(",") { i -> mapping(i)?.let(Double::toString) ?: "null" }
    }

    protected fun <T> queryRowVersion(sql: String, id: IntId<T>): RowVersion<T> =
        jdbcTemplate.queryOne(sql, mapOf("id" to id.intValue), id.toString()) { rs, _ ->
            rs.getRowVersion("id", "version")
        }

    protected fun <T> queryRowVersionOrNull(sql: String, id: IntId<T>): RowVersion<T>? =
        jdbcTemplate.queryOptional(sql, mapOf("id" to id.intValue)) { rs, _ ->
            rs.getRowVersionOrNull("id", "version")
        }
}

inline fun <reified T, reified S> getOne(id: DomainId<T>, result: List<S>) =
    getOptional(id, result) ?: throw NoSuchEntityException(T::class, id)

inline fun <reified T, reified S> getOptional(id: DomainId<T>, result: List<S>): S? {
    if (result.size > 1) {
        val idDesc = if (S::class == T::class) "id $id" else "${T::class.simpleName}.id $id"
        throw IllegalStateException("Found more than one ${S::class.simpleName} with same $idDesc")
    }
    return result.firstOrNull()
}

fun <T, S> getOne(name: String, id: DomainId<T>, result: List<S>): S {
    if (result.isEmpty()) throw NoSuchEntityException(name, id)
    else if (result.size > 1) {
        throw IllegalStateException("Found more than one $name with id: $id")
    }
    return result.first()
}

inline fun <reified T> toDbId(id: DomainId<T>): IntId<T> =
    if (id is IntId) id
    else throw NoSuchEntityException(T::class, id)

fun <T> toDbId(clazz: KClass<*>, id: DomainId<T>): IntId<T> =
    if (id is IntId) id
    else throw NoSuchEntityException(clazz, id)
