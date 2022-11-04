package fi.fta.geoviite.infra.ui.util

import fi.fta.geoviite.infra.util.DaoBase
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.transaction.annotation.Transactional


open class TruncateDbDao(jdbcTemplateParam: NamedParameterJdbcTemplate?) : DaoBase(jdbcTemplateParam) {

    @Transactional
    open fun truncateSchema(schemaName: String) {

        val procedureSql = """
            CREATE OR REPLACE FUNCTION common.truncate_tables(schema_name IN VARCHAR) RETURNS void AS $$
            DECLARE
                statements CURSOR FOR
                    SELECT tablename FROM pg_tables
                    WHERE schemaname = 'schema_name';
            BEGIN
                FOR stmt IN statements LOOP
                    EXECUTE 'TRUNCATE TABLE ' || quote_ident(stmt.tablename) || ' CASCADE;';
                END LOOP;
            END;
            $$ LANGUAGE plpgsql;
        """.trimIndent()
        jdbcTemplate.update(procedureSql, mapOf<String,String>())

        //mapOf("schema_name" to schemaName)
        jdbcTemplate.query("select common.truncate_tables(:schema_name) is null", mapOf("schema_name" to schemaName)){rs, _ -> 1}
    }

    @Transactional
    open fun truncateTables(schema: String, vararg tables: String) {
        tables.forEach { table ->
            jdbcTemplate.update("TRUNCATE TABLE ${schema}.${table} CASCADE;",
                mapOf("table_name" to table, "schema_name" to schema)) }

    }
}
