package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class TrackNumber @JsonCreator(mode = DELEGATING) constructor(val value: String) :
    Comparable<TrackNumber>, CharSequence by value {

    companion object {
        val allowedLength = 2..30
        val sanitizer = Regex("^[äÄöÖåÅA-Za-z0-9 ]+\$")
    }

    init {
        assertSanitized<TrackNumber>(value, sanitizer, allowedLength, allowBlank = false)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: TrackNumber): Int = value.compareTo(other.value)
}
