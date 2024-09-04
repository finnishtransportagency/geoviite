package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.SpringContextUtility
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.flywaydb.core.internal.resolver.ChecksumCalculator
import org.flywaydb.core.internal.resource.filesystem.FileSystemResource
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.SingleConnectionDataSource

abstract class CsvMigration : BaseJavaMigration() {

    val logger: Logger = LoggerFactory.getLogger(this::class.java)

    open val importEnabled: Boolean by lazy { SpringContextUtility.getProperty("geoviite.data.import") }

    private val fileList by lazy { getFiles() }
    private val fileNames by lazy { fileList.joinToString(", ") { it.file.absolutePath } }

    final override fun migrate(context: Context?) =
        withImportUser(ImportUser.CSV_IMPORT) {
            logger.info("CSV Import files: $fileNames")
            if (importEnabled && fileList.all { f -> f.file.exists() }) {
                logger.info("Running CSV import: version=$version description=$description")
                try {
                    val connection =
                        context?.connection ?: throw IllegalStateException("Can't run imports without DB connection")
                    val jdbcTemplate = NamedParameterJdbcTemplate(SingleConnectionDataSource(connection, true))
                    migrate(jdbcTemplate)
                } catch (e: Exception) {
                    logger.error("CSV import failed: $e", e)
                    throw e
                }
            } else {
                logger.info(
                    "Not running CSV import: version=$version description=$description enabled=$importEnabled filesExist=${fileList.map { f -> "${f.file.absolutePath}:${f.file.exists()}" }}"
                )
            }
        }

    /**
     * We could checksum the data files, but if we check it via flyway, we'd have to keep the initial files available
     * infinitely in production.
     */
    override fun getChecksum(): Int? {
        if (importEnabled && fileList.all { f -> f.file.exists() }) {
            // Note: We're not using Flyway to verify these checksums, but we log it anyhow for
            // debugging
            val checksums = fileList.map { f -> calculateChecksum(f) }
            logger.info("CSV import checksum - imports enabled: files=[$fileNames] checksums=$checksums")
        } else if (importEnabled) {
            logger.warn("CSV import checksum - imports enabled, but required files don't exist: files=[$fileNames]")
        } else {
            logger.info("CSV import checksum - imports disabled")
        }

        return 9 // Increment to force recreate
    }

    abstract fun migrate(jdbcTemplate: NamedParameterJdbcTemplate)

    abstract fun getFiles(): List<CsvFile<*>>

    private fun calculateChecksum(file: CsvFile<*>): Int =
        ChecksumCalculator.calculate(FileSystemResource(null, file.filePath, Charsets.UTF_8, false))
}
