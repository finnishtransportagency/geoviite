package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.MATERIAL
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.PROJECT
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.UnsafeString

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

data class PVDictionaryCode @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String) :
    Comparable<PVDictionaryCode>, CharSequence by value {

    companion object {
        val allowedLength = 1..50
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9\\-/"
        val sanitizer = StringSanitizer(PVDictionaryCode::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVDictionaryCode): Int = value.compareTo(other.value)
}

data class PVDictionaryName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: String) :
    Comparable<PVDictionaryName>, CharSequence by value {

    companion object {
        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\-–+().,'/*"
        val sanitizer = StringSanitizer(PVDictionaryName::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    constructor(unsafeString: UnsafeString) : this(sanitizer.sanitize(unsafeString.unsafeValue))

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PVDictionaryName): Int = value.compareTo(other.value)
}
