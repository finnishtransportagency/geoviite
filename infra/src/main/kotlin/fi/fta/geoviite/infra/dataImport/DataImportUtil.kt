package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.configuration.USER_HEADER
import org.slf4j.MDC

enum class ImportUser {
    IM_IMPORT,
    CSV_IMPORT,
    SWITCH_LIB_IMPORT,
}

inline fun <reified T> withUser(user: ImportUser, op: () -> T): T {
    MDC.put(USER_HEADER, user.name)
    return try {
        op()
    } finally {
        MDC.remove(USER_HEADER)
    }
}
