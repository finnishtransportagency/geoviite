package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

const val ALLOWED_TRACK_NUMBER_LENGTH = 30

data class TrackNumber @JsonCreator(mode = DELEGATING) constructor(val value: String) :
    Comparable<TrackNumber>, CharSequence by value {

    companion object {
        val allowedLength = 2..ALLOWED_TRACK_NUMBER_LENGTH
        const val ALLOWED_CHARACTERS = "äÄöÖåÅA-Za-z0-9 "
        val sanitizer = StringSanitizer(TrackNumber::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: TrackNumber): Int = value.compareTo(other.value)
}
