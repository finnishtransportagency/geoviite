import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

enum class PVDocumentStatus {
    NOT_IM,
    SUGGESTED,
    REJECTED,
    ACCEPTED,
}

data class PVProjectGroup(val oid: Oid<PVProjectGroup>, val name: PVDictionaryName, val state: PVDictionaryName)

data class PVProject(val oid: Oid<PVProject>, val name: PVDictionaryName, val state: PVDictionaryName)

data class PVAssignment(val oid: Oid<PVAssignment>, val name: PVDictionaryName, val state: PVDictionaryName)

data class PVDocumentRejection(
    val id: IntId<PVDocumentRejection>,
    val documentVersion: RowVersion<PVDocument>,
    val reason: LocalizationKey,
)

data class PVDocument(
    val id: IntId<PVDocument>,
    val oid: Oid<PVDocument>,
    val name: FileName,
    val description: FreeText?,
    val type: PVDictionaryName,
    val state: PVDictionaryName,
    val category: PVDictionaryName,
    val group: PVDictionaryName,
    val modified: Instant,
    val status: PVDocumentStatus,
)

data class PVDocumentHeader(
    val project: PVProject?,
    val projectGroup: PVProjectGroup?,
    val assignment: PVAssignment?,
    val document: PVDocument,
)
