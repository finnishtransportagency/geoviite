import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import java.time.Instant

enum class VelhoDocumentStatus {
    REJECTED,
    ACCEPTED,
    PENDING,
}

val velhoCodeRegex = Regex("^[A-Za-z0-9_\\-./]+\$")
val velhoCodeLength = 3..50
val velhoNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*]*\$")

data class VelhoCode @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<VelhoCode>, CharSequence by value {
    init { assertSanitized<VelhoCode>(value, velhoCodeRegex, velhoCodeLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: VelhoCode): Int = value.compareTo(other.value)
}

data class VelhoName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<VelhoName>, CharSequence by value {
    init { assertSanitized<VelhoName>(value, velhoNameRegex) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: VelhoName): Int = value.compareTo(other.value)
}

data class VelhoEncoding(val code: VelhoCode, val name: VelhoName)

data class VelhoProject(val group: VelhoName, val name: VelhoName)

data class VelhoDocument(
    val name: FileName,
    val description: FreeText,
    val type: VelhoEncoding,
    val modified: Instant,
    val status: VelhoDocumentStatus,
)

data class VelhoDocumentHeader(
    val id: IntId<VelhoDocument>,
    val project: VelhoProject,
    val assignment: VelhoName,
    val materialGroup: VelhoName,
    val document: VelhoDocument,
)
