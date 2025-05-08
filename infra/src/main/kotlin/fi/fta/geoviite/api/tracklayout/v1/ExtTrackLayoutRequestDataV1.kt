package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.geocoding.Resolution
import io.swagger.v3.oas.annotations.media.Schema

@Schema(type = "String", allowableValues = ["0.25", "1", "10", "100"], defaultValue = "1")
enum class ExtResolutionV1(@JsonValue val value: String) {
    QUARTER_METER("0.25"),
    ONE_METER("1"),
    TEN_METERS("10"),
    HUNDRED_METERS("100");

    override fun toString(): String {
        return this.value
    }

    companion object {
        private val map = ExtResolutionV1.entries.associateBy(ExtResolutionV1::value)

        fun fromValue(value: String): ExtResolutionV1 {
            return map[value] ?: error("Invalid resolution $value")
        }
    }

    fun toResolution(): Resolution {
        return when (this) {
            QUARTER_METER -> Resolution.QUARTER_METER
            ONE_METER -> Resolution.ONE_METER
            TEN_METERS -> Resolution.TEN_METERS
            HUNDRED_METERS -> Resolution.HUNDRED_METERS
        }
    }
}
