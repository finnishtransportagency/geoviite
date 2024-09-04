package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class Oid<T> @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        private val allowedLength = 5..50
        private val sanitizer = Regex("^\\d+(\\.\\d+){2,9}\$")
    }

    init {
        assertSanitized<Oid<T>>(value, sanitizer, allowedLength)
    }

    @JsonValue override fun toString(): String = value
}
