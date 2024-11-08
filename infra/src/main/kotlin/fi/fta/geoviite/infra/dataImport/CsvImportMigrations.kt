package fi.fta.geoviite.infra.dataImport

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

/**
 * These migrations must be retained for flyway versioning, but their implementation is intentionally removed.
 *
 * This initial data import was done with a migration on version v1.0.0 data model. If a new initial import is ever
 * needed, it can be accomplished by either:
 * - Running the import with 1.0.0 and then upgrading the system to current version
 * - Implementing a new import on the current model and (if desired) flattening versioned migrations
 */
@Suppress("unused", "ClassName")
class V11_01__Csv_import_track_numbers : CsvMigration() {
    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        throw NotImplementedError("Initial data migration not supported in this version.")
    }

    override fun getFiles() = listOf<CsvFile<*>>()
}

@Suppress("unused", "ClassName")
class V14_01__Csv_import_km_posts : CsvMigration() {
    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        throw NotImplementedError("Initial data migration not supported in this version.")
    }

    override fun getFiles() = listOf<CsvFile<*>>()
}

@Suppress("unused", "ClassName")
class V14_02__Csv_import_switches : CsvMigration() {
    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        throw NotImplementedError("Initial data migration not supported in this version.")
    }

    override fun getFiles() = listOf<CsvFile<*>>()
}

@Suppress("unused", "ClassName")
class V14_03__Csv_import_reference_lines : CsvMigration() {
    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        throw NotImplementedError("Initial data migration not supported in this version.")
    }

    override fun getFiles() = listOf<CsvFile<*>>()
}

@Suppress("unused", "ClassName")
class V14_04__Csv_import_location_tracks : CsvMigration() {
    override fun migrate(jdbcTemplate: NamedParameterJdbcTemplate) {
        throw NotImplementedError("Initial data migration not supported in this version.")
    }

    override fun getFiles() = listOf<CsvFile<*>>()
}
