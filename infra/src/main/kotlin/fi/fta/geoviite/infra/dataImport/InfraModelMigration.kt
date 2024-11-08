package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.SpringContextUtility
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * These migrations must be retained for flyway versioning, but their implementation is intentionally removed.
 *
 * This initial data import was done with a migration on version v1.0.0 data model. If a new initial import is ever
 * needed, it can be accomplished by either:
 * - Running the import with 1.0.0 and then upgrading the system to current version
 * - Implementing a new import on the current model and (if desired) flattening versioned migrations
 */
@Suppress("unused", "ClassName")
class V12_01__InfraModelMigration : BaseJavaMigration() {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private val importEnabled: Boolean by lazy { SpringContextUtility.getProperty("geoviite.data.import") }

    override fun migrate(context: Context?) =
        withImportUser(ImportUser.IM_IMPORT) {
            if (importEnabled) {
                throw NotImplementedError("Initial inframodel migration not supported in this version.")
            } else {
                logger.info("InfraModel import disabled")
            }
        }
}
