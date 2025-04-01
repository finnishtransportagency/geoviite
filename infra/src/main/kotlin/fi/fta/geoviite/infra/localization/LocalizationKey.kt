package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

data class LocalizationKey @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocalizationKey>, CharSequence by value {

    companion object {
        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "A-Za-z0-9_\\-."
        val sanitizer = StringSanitizer(LocalizationKey::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LocalizationKey): Int = value.compareTo(other.value)
}

data class LocalizationParams
@JsonCreator(mode = JsonCreator.Mode.DELEGATING)
constructor(@JsonValue val params: Map<String, String>) {

    companion object {
        val allowedLength = 1..100
        const val KEY_ALLOWED_CHARACTERS = "a-zA-Z0-9_\\s\\-"
        val keySanitizer = StringSanitizer(LocalizationParams::class, KEY_ALLOWED_CHARACTERS, allowedLength)
        val empty = LocalizationParams(emptyMap())
    }

    init {
        params.forEach { (key, _) -> keySanitizer.sanitize(key) }
    }

    fun get(key: String) = params[key] ?: ""

    operator fun plus(other: LocalizationParams) = LocalizationParams(params + other.params)
}

fun localizationParams(params: Map<String, Any?>): LocalizationParams =
    LocalizationParams(params.mapValues { it.value?.toString() ?: "" })

fun localizationParams(vararg params: Pair<String, Any?>): LocalizationParams = localizationParams(mapOf(*params))
