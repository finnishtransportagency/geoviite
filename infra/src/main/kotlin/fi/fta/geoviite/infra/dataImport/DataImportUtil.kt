package fi.fta.geoviite.infra.dataImport

import fi.fta.geoviite.infra.authorization.UserName
import withUser

enum class ImportUser {
    IM_IMPORT,
    CSV_IMPORT,
    SWITCH_LIB_IMPORT,
}

inline fun <reified T> withImportUser(user: ImportUser, noinline op: () -> T): T = withUser(UserName.of(user.name), op)
