package fi.fta.geoviite.infra.geometry

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.UNSAFE_REPLACEMENT

data class PlanName @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        /**
         * Bytes of character in canonical decomposition form differ from the default form (precomposed). We encountered
         * these in some IM files, probably produced by some 3D design software.
         *
         * https://www.ibm.com/docs/en/cobol-zos/6.3?topic=functions-ulength
         */
        const val umlautsCanonicalDecomposition = "aäöåÄÖÅ"

        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "\\p{L}\\p{N}${umlautsCanonicalDecomposition}_\\-+~., /()$UNSAFE_REPLACEMENT"
        val sanitizer = StringSanitizer(FileName::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value
}
