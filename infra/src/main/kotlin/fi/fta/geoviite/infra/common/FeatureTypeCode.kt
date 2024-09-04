package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

data class FeatureTypeCode @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    CharSequence by value {

    companion object {
        private val sanitizer = Regex("^\\d{3}\$")
        private val allowedLength = 3..3
    }

    init {
        assertSanitized<FeatureTypeCode>(value, sanitizer, allowedLength)
    }

    @JsonValue override fun toString(): String = value
}
