package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.util.DaoBase
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional


open class TruncateDbDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    open fun truncateSchema(schemaName: String) {

        val procedureSql = """
            create or replace function common.truncate_tables(schema_name in varchar) returns void as $$
            declare
                statements cursor for
                    select tablename from pg_tables
                    where schemaname = 'schema_name';
            begin
                for stmt in statements loop
                    execute 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
                end loop;
            end;
            $$ language plpgsql;
        """.trimIndent()
        jdbcTemplate.update(procedureSql, mapOf<String, String>())

        //mapOf("schema_name" to schemaName)
        jdbcTemplate.query(
            "select common.truncate_tables(:schema_name) is null",
            mapOf("schema_name" to schemaName)
        ) { rs, _ -> 1 }
    }

    @Transactional
    open fun truncateTables(schema: String, vararg tables: String) {
        // Temporarily disable all triggers
        jdbcTemplate.execute("SET session_replication_role = replica") { it.execute() }
        try {
            tables.forEach { table ->
                jdbcTemplate.update(
                    "DELETE FROM ${schema}.${table};", mapOf("table_name" to table, "schema_name" to schema)
                )
            }
        } finally {
            jdbcTemplate.execute("set session_replication_role = DEFAULT") { it.execute() }
        }
    }
}
