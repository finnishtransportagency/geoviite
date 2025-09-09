package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.METERS_MAX_DECIMAL_DIGITS
import fi.fta.geoviite.infra.common.METERS_MAX_INTEGER_DIGITS
import fi.fta.geoviite.infra.common.TRACK_METER_SEPARATOR
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

    companion object {
        private const val MAX_ALLOWED_KM_INTEGERS = 4
        private const val SEPARATOR = 1 // Track address "+" and decimal "."

        val MAX_LENGTH =
            MAX_ALLOWED_KM_INTEGERS +
                KmNumber.extensionLength.endInclusive +
                SEPARATOR +
                METERS_MAX_INTEGER_DIGITS +
                SEPARATOR +
                METERS_MAX_DECIMAL_DIGITS
    }
}

data class ExtTrackKilometerIntervalFilterV1(
    val startAddress: TrackMeter?,
    val endAddress: TrackMeter?,
    val startKm: KmNumber?,
    val endKm: KmNumber?,
) {
    init {
        if (startAddress != null && endAddress != null && startAddress > endAddress) {
            throw ExtInvalidAddressPointFilterOrderV1("start track address was after end track address")
        }

        if (startAddress != null && endKm != null && startAddress.kmNumber > endKm) {
            throw ExtInvalidAddressPointFilterOrderV1("start track address was after end track km")
        }

        if (startKm != null && endAddress != null && startKm > endAddress.kmNumber) {
            throw ExtInvalidAddressPointFilterOrderV1("start km was after end track address")
        }

        if (startKm != null && endKm != null && startKm > endKm) {
            throw ExtInvalidAddressPointFilterOrderV1("start km was after end km")
        }
    }

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
            val startAddress = start?.value?.takeIf { it.contains(TRACK_METER_SEPARATOR) }?.let(::trackMeterOrThrow)
            val endAddress = end?.value?.takeIf { it.contains(TRACK_METER_SEPARATOR) }?.let(::trackMeterOrThrow)

            return ExtTrackKilometerIntervalFilterV1(
                startAddress = startAddress,
                endAddress = endAddress,
                startKm = start?.value?.takeIf { startAddress == null }?.let(::kmNumberOrThrow),
                endKm = end?.value?.takeIf { endAddress == null }?.let(::kmNumberOrThrow),
            )
        }
    }
}

private fun kmNumberOrThrow(input: String): KmNumber {
    return try {
        KmNumber(input)
    } catch (e: IllegalArgumentException) {
        throw ExtInvalidKmNumberV1("could not create km number", e)
    }
}

private fun trackMeterOrThrow(input: String): TrackMeter {
    return try {
        TrackMeter(input)
    } catch (e: IllegalArgumentException) {
        throw ExtInvalidTrackMeterV1("could not create track meter", e)
    }
}
