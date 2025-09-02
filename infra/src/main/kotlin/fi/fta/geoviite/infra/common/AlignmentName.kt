package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

const val ALLOWED_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-_/!?"
const val ALLOWED_ALIGNMENT_NAME_LENGTH = 50
const val MAX_SPECIFIER_LENGTH = 5
const val MAX_LOCATION_TRACK_NAME_WHITESPACE = 2
const val MAX_LOCATION_TRACK_NAME_LENGTH =
    ALLOWED_ALIGNMENT_NAME_LENGTH +
        ALLOWED_TRACK_NUMBER_LENGTH +
        MAX_SPECIFIER_LENGTH +
        MAX_LOCATION_TRACK_NAME_WHITESPACE

data class AlignmentName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<AlignmentName>, CharSequence by value {

    companion object {
        val allowedLength = 1..ALLOWED_ALIGNMENT_NAME_LENGTH
        val sanitizer = StringSanitizer(AlignmentName::class, ALLOWED_CHARACTERS, allowedLength)

        fun ofUnsafe(value: String) = value.trim().let(sanitizer::sanitize).let(::AlignmentName)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: AlignmentName): Int = value.compareTo(other.value)
}

data class LocationTrackName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocationTrackName>, CharSequence by value {

    companion object {
        val allowedLength = 1..MAX_LOCATION_TRACK_NAME_LENGTH
        val sanitizer = StringSanitizer(LocationTrackName::class, ALLOWED_CHARACTERS, allowedLength)

        fun of(value: AlignmentName) = LocationTrackName(value.toString())
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LocationTrackName): Int = value.compareTo(other.value)
}
