package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class LocalizationKey @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocalizationKey>, CharSequence by value {

    companion object {
        val sanitizer = Regex("^[A-Za-z0-9_\\-.]+\$")
        val allowedLength = 1..100
    }

    init { assertSanitized<LocalizationKey>(value, sanitizer, allowedLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: LocalizationKey): Int = value.compareTo(other.value)
}
