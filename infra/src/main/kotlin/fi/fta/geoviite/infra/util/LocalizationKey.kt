package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

val localizationKeyRegex = Regex("^[A-Za-z0-9_\\-.]+\$")
val localizationKeyLength = 1..100

data class LocalizationKey @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocalizationKey>, CharSequence by value {
    init {
        assertSanitized<LocalizationKey>(value, localizationKeyRegex, localizationKeyLength)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LocalizationKey): Int = value.compareTo(other.value)
}
