import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.assertSanitized
import fi.fta.geoviite.infra.velho.FileStatus
import java.time.Instant


val velhoIdLength = 1..50
val velhoIdRegex = Regex("^[A-Za-z0-9_\\-./]+\$")
data class VelhoId @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<VelhoId>, CharSequence by value {
    init { assertSanitized<VelhoId>(value, velhoIdRegex, velhoIdLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: VelhoId): Int = value.compareTo(other.value)
}

val velhoCodeLength = 1..50
val velhoCodeRegex = Regex("^[A-ZÄÖÅa-zäöå0-9_\\-./]+\$")
data class VelhoCode @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<VelhoCode>, CharSequence by value {
    init { assertSanitized<VelhoCode>(value, velhoCodeRegex, velhoCodeLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: VelhoCode): Int = value.compareTo(other.value)
}

val velhoNameLength = 1..100
val velhoNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*]*\$")
data class VelhoName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String)
    : Comparable<VelhoName>, CharSequence by value {
    init { assertSanitized<VelhoName>(value, velhoNameRegex, velhoNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: VelhoName): Int = value.compareTo(other.value)
}

//data class VelhoEncoding(val type: VelhoEncodingType, val code: VelhoCode, val name: VelhoName)

data class VelhoProjectGroup(val oid: Oid<VelhoProjectGroup>, val name: VelhoName)

data class VelhoProject(val oid: Oid<VelhoProject>, val group: VelhoProjectGroup, val name: VelhoName)

data class VelhoAssignment(val oid: Oid<VelhoAssignment>, val name: VelhoName)

data class VelhoDocument(
    val id: IntId<VelhoDocument>,
    val oid: Oid<VelhoDocument>,
    val name: FileName,
    val description: FreeText?,
    val type: VelhoName,
    val modified: Instant,
    val status: FileStatus,
)

data class VelhoDocumentHeader(
    val project: VelhoProject,
    val assignment: VelhoAssignment,
    val materialGroup: VelhoName,
    val document: VelhoDocument,
)
