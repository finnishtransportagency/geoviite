package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.logging.AccessType.VERSION_FETCH
import fi.fta.geoviite.infra.logging.daoAccess
import fi.fta.geoviite.infra.util.FetchType.MULTI
import fi.fta.geoviite.infra.util.FetchType.SINGLE
import java.sql.ResultSet
import java.time.Instant
import kotlin.reflect.KClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

enum class FetchType {
    SINGLE,
    MULTI,
}

fun idOrIdsEqualSqlFragment(fetchType: FetchType) =
    when (fetchType) {
        MULTI -> "in (:ids)"
        SINGLE -> "= :id"
    }

fun idOrIdsSqlFragment(fetchType: FetchType) =
    when (fetchType) {
        MULTI -> "unnest (array[:ids])"
        SINGLE -> "(values (:id))"
    }

enum class LayoutAssetTable(val dbTable: DbTable, val idTable: String, layoutContextFunction: String) {
    LAYOUT_ASSET_TRACK_NUMBER(DbTable.LAYOUT_TRACK_NUMBER, "layout.track_number_id", "track_number_in_layout_context"),
    LAYOUT_ASSET_REFERENCE_LINE(
        DbTable.LAYOUT_REFERENCE_LINE,
        "layout.reference_line_id",
        "reference_line_in_layout_context",
    ),
    LAYOUT_ASSET_LOCATION_TRACK(
        DbTable.LAYOUT_LOCATION_TRACK,
        "layout.location_track_id",
        "location_track_in_layout_context",
    ),
    LAYOUT_ASSET_SWITCH(DbTable.LAYOUT_SWITCH, "layout.switch_id", "switch_in_layout_context"),
    LAYOUT_ASSET_KM_POST(DbTable.LAYOUT_KM_POST, "layout.km_post_id", "km_post_in_layout_context");

    val fullLayoutContextFunction: String = "layout.${layoutContextFunction}"
    val fullName: String = dbTable.fullName
    val versionTable: String = dbTable.versionTable
}

enum class DbTable(schema: String, table: String, sortColumns: List<String> = listOf("id")) {
    COMMON_SWITCH_STRUCTURE("common", "switch_structure"),
    LAYOUT_ALIGNMENT("layout", "alignment"),
    LAYOUT_LOCATION_TRACK("layout", "location_track"),
    LAYOUT_REFERENCE_LINE("layout", "reference_line"),
    LAYOUT_SWITCH("layout", "switch"),
    LAYOUT_KM_POST("layout", "km_post", listOf("track_number_id", "km_number")),
    LAYOUT_TRACK_NUMBER("layout", "track_number"),
    LAYOUT_DESIGN("layout", "design"),
    OPERATING_POINT("layout", "operating_point"),
    GEOMETRY_PLAN("geometry", "plan"),
    GEOMETRY_PLAN_PROJECT("geometry", "plan_project"),
    GEOMETRY_PLAN_AUTHOR("geometry", "plan_author"),
    GEOMETRY_ALIGNMENT("geometry", "alignment"),
    GEOMETRY_SWITCH("geometry", "switch"),
    GEOMETRY_KM_POST("geometry", "km_post", listOf("track_number_id", "km_number")),
    GEOMETRY_TRACK_NUMBER("geometry", "track_number"),
    PROJEKTIVELHO_DOCUMENT("projektivelho", "document"),
    PUBLICATION_SPLIT("publication", "split");

    val fullName: String = "$schema.$table"
    val versionTable = "$schema.${table}_version"
    val orderBy: String = sortColumns.joinToString(",")

    // language=SQL
    val changeTimeSql = "select max(change_time) change_time from $versionTable"

    // language=SQL
    val singleRowVersionSql = "select id, version from $fullName where id ${idOrIdsEqualSqlFragment(SINGLE)}"

    // language=SQL
    val multiRowVersionSql = "select id, version from $fullName where id ${idOrIdsEqualSqlFragment(MULTI)}"

    // language=SQL
    val rowVersionsSql = "select id, version from $fullName order by $orderBy"
}

open class DaoBase(private val jdbcTemplateParam: NamedParameterJdbcTemplate?) {

    protected val logger: Logger = LoggerFactory.getLogger(this::class.java)

    /**
     * The template from DI is nullable so that we can configure to run without DB when needed (i.e. unit tests) For
     * actual code, use this non-null variable. It will throw on first use if DB-initialization is not done.
     */
    protected val jdbcTemplate: NamedParameterJdbcTemplate by lazy {
        jdbcTemplateParam ?: throw IllegalStateException("Database connection not initialized")
    }

    protected fun <T> fetchRowVersion(id: IntId<T>, table: DbTable): RowVersion<T> {
        logger.daoAccess(VERSION_FETCH, "fetchRowVersion", "id" to id, "table" to table.fullName)
        return queryRowVersion(table.singleRowVersionSql, id)
    }

    protected fun <T> fetchManyRowVersions(ids: List<IntId<T>>, table: DbTable): List<RowVersion<T>> {
        logger.daoAccess(VERSION_FETCH, "fetchManyRowVersions", "id" to ids, "table" to table.fullName)
        return if (ids.isEmpty()) {
            emptyList()
        } else {
            jdbcTemplate.query(table.multiRowVersionSql, mapOf("ids" to ids.map { it.intValue }), ::toRowVersion)
        }
    }

    protected fun <T> fetchRowVersions(table: DbTable): List<RowVersion<T>> {
        logger.daoAccess(VERSION_FETCH, "fetchRowVersions", "table" to table.fullName)
        return jdbcTemplate.query(table.rowVersionsSql, mapOf<String, Any>(), ::toRowVersion)
    }

    protected fun fetchLatestChangeTime(table: DbTable): Instant {
        return jdbcTemplate
            .query(table.changeTimeSql, mapOf<String, Any>()) { rs, _ -> rs.getInstantOrNull("change_time") }
            .firstOrNull() ?: Instant.EPOCH
    }

    protected fun <T> createListString(items: List<T>, mapping: (t: T) -> Double?) =
        when {
            items.none { i -> mapping(i) != null } -> null
            else -> items.joinToString(",") { i -> mapping(i)?.let(Double::toString) ?: "null" }
        }

    protected fun <T> queryRowVersion(sql: String, id: IntId<T>): RowVersion<T> =
        jdbcTemplate.queryOne(sql, mapOf("id" to id.intValue), id.toString(), ::toRowVersion)

    protected fun <T> queryRowVersionOrNull(sql: String, id: IntId<T>): RowVersion<T>? =
        jdbcTemplate.queryOptional(sql, mapOf("id" to id.intValue), ::toRowVersionOrNull)

    protected fun <T> toRowVersion(rs: ResultSet, index: Int): RowVersion<T> = rs.getRowVersion("id", "version")

    protected fun <T> toRowVersionOrNull(rs: ResultSet, index: Int): RowVersion<T> = rs.getRowVersion("id", "version")
}

inline fun <reified T, reified S> getOne(id: DomainId<T>, result: List<S>) = requireOne(T::class, id, result)

inline fun <reified T, reified S> getOne(rowVersion: RowVersion<T>, result: List<S>) =
    requireOne(T::class, rowVersion, result)

inline fun <reified T, reified S> getOptional(rowVersion: RowVersion<T>, result: List<S>): S? =
    requireOneOrNull(T::class, rowVersion, result)

inline fun <reified T, reified S> getOptional(id: DomainId<T>, result: List<S>): S? =
    requireOneOrNull(T::class, id, result)

fun <T> requireOne(clazz: KClass<*>, id: Any, result: List<T>): T =
    requireOneOrNull(clazz, id, result) ?: throw NoSuchEntityException(clazz, id.toString())

fun <T> requireOneOrNull(clazz: KClass<*>, id: Any, result: List<T>): T? {
    require(result.size <= 1) { "Found more than one (${result.size}) ${clazz.simpleName} with identifier $id" }
    return result.firstOrNull()
}

fun <T, S> getOne(name: String, id: DomainId<T>, result: List<S>): S {
    if (result.isEmpty()) throw NoSuchEntityException(name, id)
    else if (result.size > 1) error("Found more than one $name with id: $id")
    return result.first()
}

inline fun <reified T> toDbId(id: DomainId<T>): IntId<T> =
    if (id is IntId) id else throw NoSuchEntityException(T::class, id)

fun <T> toDbId(clazz: KClass<*>, id: DomainId<T>): IntId<T> =
    if (id is IntId) id else throw NoSuchEntityException(clazz, id)
