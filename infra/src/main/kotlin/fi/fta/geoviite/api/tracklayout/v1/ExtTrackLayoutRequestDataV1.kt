package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.Resolution
import io.swagger.v3.oas.annotations.media.Schema

@Schema(type = "String", allowableValues = ["0.25", "1"], defaultValue = "1")
enum class ExtResolutionV1(@JsonValue val value: String) {
    QUARTER_METER("0.25"),
    ONE_METER("1");

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
        }
    }
}

@Schema(type = "String")
data class ExtMaybeTrackKmOrTrackMeterV1 @JsonCreator(mode = DELEGATING) constructor(val value: String) {
    init {
        require(value.length <= MAX_LENGTH) {
            "Track km or track address field length must be at most $MAX_LENGTH characters"
        }
    }

    override fun toString(): String {
        return value
    }

    companion object {
        const val MAX_LENGTH = 13
    }
}

data class ExtTrackKilometerIntervalFilterV1(
    val startAddress: TrackMeter?,
    val endAddress: TrackMeter?,
    val startKm: KmNumber?,
    val endKm: KmNumber?,
) {
    fun contains(address: TrackMeter): Boolean {
        val startAddressOk = startAddress == null || address >= startAddress
        val endAddressOk = endAddress == null || address <= endAddress

        val startKmOk = startKm == null || address.kmNumber >= startKm
        val endKmOk = endKm == null || address.kmNumber <= endKm

        return startAddressOk && endAddressOk && startKmOk && endKmOk
    }

    companion object {
        fun of(
            start: ExtMaybeTrackKmOrTrackMeterV1?,
            end: ExtMaybeTrackKmOrTrackMeterV1?,
        ): ExtTrackKilometerIntervalFilterV1 {
            val startAddress = start?.value?.takeIf { it.contains("+") }?.let(::TrackMeter)
            val endAddress = end?.value?.takeIf { it.contains("+") }?.let(::TrackMeter)

            return ExtTrackKilometerIntervalFilterV1(
                startAddress = startAddress,
                endAddress = endAddress,
                startKm = start?.value?.takeIf { startAddress == null }?.let(::KmNumber),
                endKm = end?.value?.takeIf { endAddress == null }?.let(::KmNumber),
            )
        }
    }
}
