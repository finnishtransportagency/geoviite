package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.UnsafeString
import java.time.Instant

enum class PVDocumentStatus {
    NOT_IM,
    SUGGESTED,
    REJECTED,
    ACCEPTED,
}

data class PVProjectName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String) :
    Comparable<PVProjectName>, CharSequence by value {

    companion object {
        val length = 1..200
        val sanitizer = StringSanitizer(PVProjectName::class, FreeText.ALLOWED_CHARACTERS, length)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    constructor(
        unsafeString: UnsafeString
    ) : this(if (unsafeString.unsafeValue.isBlank()) "-" else sanitizer.sanitize(unsafeString.unsafeValue))

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVProjectName): Int = value.compareTo(other.value)
}

data class PVProjectGroup(val oid: Oid<PVProjectGroup>, val name: PVProjectName, val state: PVDictionaryName)

data class PVProject(val oid: Oid<PVProject>, val name: PVProjectName, val state: PVDictionaryName)

data class PVAssignment(val oid: Oid<PVAssignment>, val name: PVProjectName, val state: PVDictionaryName)

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
