import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import java.time.Instant


val projektiVelhoIdLength = 1..50
val projektiVelhoIdRegex = Regex("^[A-Za-z0-9_\\-./]+\$")
data class PVId @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<PVId>, CharSequence by value {
    init { assertSanitized<PVId>(value, projektiVelhoIdRegex, projektiVelhoIdLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVId): Int = value.compareTo(other.value)
}

val projektiVelhoCodeLength = 1..50
val projektiVelhoCodeRegex = Regex("^[A-ZÄÖÅa-zäöå0-9_\\-./]+\$")
data class PVCode @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<PVCode>, CharSequence by value {
    init { assertSanitized<PVCode>(value, projektiVelhoCodeRegex, projektiVelhoCodeLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVCode): Int = value.compareTo(other.value)
}

val projektiVelhoNameLength = 1..100
val projektiVelhoNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*]*\$")
data class PVName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<PVName>, CharSequence by value {
    init { assertSanitized<PVName>(value, projektiVelhoNameRegex, projektiVelhoNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: PVName): Int = value.compareTo(other.value)
}

enum class PVDocumentStatus {
    NOT_IM,
    IMPORTED,
    REJECTED,
    ACCEPTED,
}

data class PVProjectGroup(val oid: Oid<PVProjectGroup>, val name: PVName)

data class PVProject(val oid: Oid<PVProject>, val group: PVProjectGroup, val name: PVName)

data class PVAssignment(val oid: Oid<PVAssignment>, val name: PVName)

data class PVDocument(
    val id: IntId<PVDocument>,
    val oid: Oid<PVDocument>,
    val name: FileName,
    val description: FreeText?,
    val type: PVName,
    val state: PVName,
    val category: PVName,
    val group: PVName,
    val modified: Instant,
    val status: PVDocumentStatus,
)

data class PVDocumentHeader(
    // TODO: GVT-1680 These should be non-null
    val project: PVProject?,
    val assignment: PVAssignment?,
    val document: PVDocument,
)
