package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertInput
import fi.fta.geoviite.infra.util.parsePrefixedInt

data class Srid @JsonCreator(mode = DISABLED) constructor(val code: Int) {

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parsePrefixedInt<Srid>(SRID_PREFIX, value))

    companion object {
        private const val SRID_PREFIX = "EPSG:"
        private val sridRange: IntRange = 1024..32767
    }

    init {
        assertInput(Srid::class, code in sridRange, code.toString()) {
            "SRID (EPSG code) $code outside allowed range $sridRange"
        }
    }

    @JsonValue override fun toString(): String = SRID_PREFIX + code
}
