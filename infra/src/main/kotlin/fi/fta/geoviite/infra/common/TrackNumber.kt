package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

private val trackNumberLength = 2..30
private val trackNumberRegex = Regex("^[äÄöÖåÅA-Za-z0-9 ]+\$")

data class TrackNumber @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : Comparable<TrackNumber>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init { assertSanitized<TrackNumber>(value, trackNumberRegex, trackNumberLength, allowBlank = false) }

    override fun compareTo(other: TrackNumber): Int = value.compareTo(other.value)
}

class StringToTrackNumberConverter : Converter<String, TrackNumber> {
    override fun convert(source: String): TrackNumber = TrackNumber(source)
}

class TrackNumberToStringConverter : Converter<TrackNumber, String> {
    override fun convert(source: TrackNumber): String = source.value
}
