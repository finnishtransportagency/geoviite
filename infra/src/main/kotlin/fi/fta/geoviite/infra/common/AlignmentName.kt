package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.StringSanitizer

data class AlignmentName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<AlignmentName>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-_/!?"
        val allowedLength = 1..50
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
