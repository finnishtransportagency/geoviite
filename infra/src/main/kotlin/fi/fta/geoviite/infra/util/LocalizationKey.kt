package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.core.convert.converter.Converter

val localizationKeyRegex = Regex("^[A-Za-z0-9_\\-.]+\$")
val localizationKeyLength = 1..100

data class LocalizationKey @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<LocalizationKey>, CharSequence by value
{
    @JsonValue
    override fun toString(): String = value
    init {
        assertSanitized<LocalizationKey>(value, localizationKeyRegex, localizationKeyLength)
    }
    override fun compareTo(other: LocalizationKey): Int = value.compareTo(other.value)
}

class StringToLocalizationKeyConverter : Converter<String, LocalizationKey> {
    override fun convert(source: String): LocalizationKey = LocalizationKey(source)
}

class LocalizationKeyToStringConverter : Converter<LocalizationKey, String> {
    override fun convert(source: LocalizationKey): String = source.toString()
}
