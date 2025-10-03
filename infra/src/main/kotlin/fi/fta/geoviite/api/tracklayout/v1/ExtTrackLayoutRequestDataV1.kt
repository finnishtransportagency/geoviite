package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.METERS_MAX_DECIMAL_DIGITS
import fi.fta.geoviite.infra.common.METERS_MAX_INTEGER_DIGITS
import fi.fta.geoviite.infra.common.TRACK_METER_SEPARATOR
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AddressFilter
import fi.fta.geoviite.infra.geocoding.AddressLimit
import fi.fta.geoviite.infra.geocoding.KmLimit
import fi.fta.geoviite.infra.geocoding.Resolution
import fi.fta.geoviite.infra.geocoding.TrackMeterLimit
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

fun createAddressFilter(start: ExtMaybeTrackKmOrTrackMeterV1?, end: ExtMaybeTrackKmOrTrackMeterV1?): AddressFilter {
    val startLimit = start?.value?.let(::toAddressLimit)
    val endLimit = end?.value?.let(::toAddressLimit)

    if (!AddressFilter.limitsAreInOrder(startLimit, endLimit)) {
        throw ExtInvalidAddressPointFilterOrderV1("start was strictly after end (start > end)")
    }

    return AddressFilter(start = startLimit, end = endLimit)
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

private fun toAddressLimit(rawAddressLimit: String): AddressLimit {
    return rawAddressLimit
        .takeIf { it.contains(TRACK_METER_SEPARATOR) }
        ?.let(::trackMeterOrThrow)
        ?.let(::TrackMeterLimit) ?: rawAddressLimit.let(::kmNumberOrThrow).let(::KmLimit)
}
