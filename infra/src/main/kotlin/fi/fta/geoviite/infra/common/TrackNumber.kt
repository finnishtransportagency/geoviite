package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

val trackNumberLength = 2..30
val trackNumberRegex = Regex("^[äÄöÖåÅA-Za-z0-9 ]+\$")

data class TrackNumber @JsonCreator(mode = DELEGATING) constructor(val value: String)
    : Comparable<TrackNumber>, CharSequence by value {
    init { assertSanitized<TrackNumber>(value, trackNumberRegex, trackNumberLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: TrackNumber): Int = value.compareTo(other.value)
}
