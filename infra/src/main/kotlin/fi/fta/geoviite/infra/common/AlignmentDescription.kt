package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

const val ALLOWED_DESCRIPTION_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\\\\\-–—+().,'\"/<>:;!?&"

data class LocationTrackDescriptionBase @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<LocationTrackDescriptionBase>, CharSequence by value {

    companion object {
        val allowedLength = 1..256
        val sanitizer =
            StringSanitizer(LocationTrackDescriptionBase::class, ALLOWED_DESCRIPTION_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: LocationTrackDescriptionBase): Int = value.compareTo(other.value)
}

data class TrackNumberDescription @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<TrackNumberDescription>, CharSequence by value {

    companion object {
        val allowedLength = 1..100
        val sanitizer = StringSanitizer(TrackNumberDescription::class, ALLOWED_DESCRIPTION_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: TrackNumberDescription): Int = value.compareTo(other.value)
}
