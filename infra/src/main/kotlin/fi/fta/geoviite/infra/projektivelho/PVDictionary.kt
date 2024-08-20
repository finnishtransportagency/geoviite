package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.MATERIAL
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.PROJECT
import fi.fta.geoviite.infra.util.assertSanitized

enum class PVDictionaryGroup {
    MATERIAL,
    PROJECT,
}

enum class PVDictionaryType(val group: PVDictionaryGroup) {
    DOCUMENT_TYPE(MATERIAL), // dokumenttityyppi
    MATERIAL_STATE(MATERIAL), // aineistotila
    MATERIAL_CATEGORY(MATERIAL), // aineistolaji
    MATERIAL_GROUP(MATERIAL), // ainestoryhmä
    TECHNICS_FIELD(MATERIAL), // teknikka-ala
    PROJECT_STATE(PROJECT), // projektin tila
}

val pvDictionaryCodeLength = 1..50
val pvDictionaryCodeRegex = Regex("^[A-ZÄÖÅa-zäöå0-9\\-/]+\$")

data class PVDictionaryCode
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(private val value: String) : Comparable<PVDictionaryCode>, CharSequence by value {
    init {
        assertSanitized<PVDictionaryCode>(value, pvDictionaryCodeRegex, pvDictionaryCodeLength)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVDictionaryCode): Int = value.compareTo(other.value)
}

val pvDictionaryNameLength = 1..100
val pvDictionaryNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*]*\$")

data class PVDictionaryName
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(private val value: String) : Comparable<PVDictionaryName>, CharSequence by value {
    init {
        assertSanitized<PVDictionaryName>(value, pvDictionaryNameRegex, pvDictionaryNameLength)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVDictionaryName): Int = value.compareTo(other.value)
}

data class PVDictionaryEntry(val code: PVDictionaryCode, val name: PVDictionaryName) {
    constructor(code: String, name: String) : this(PVDictionaryCode(code), PVDictionaryName(name))
}
