package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.formatForException
import java.math.BigDecimal
import java.math.RoundingMode
import java.math.RoundingMode.DOWN
import java.math.RoundingMode.HALF_UP
import java.math.RoundingMode.UP
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.pow

const val TRACK_METER_SEPARATOR = "+"
const val DEFAULT_TRACK_METER_DECIMALS = 3

data class KmNumber @JsonCreator(mode = DISABLED) constructor(val number: Int, val extension: String? = null) :
    Comparable<KmNumber> {
    private constructor(values: Pair<Int, String?>) : this(values.first, values.second)

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parseKmNumberParts(value))

    companion object {
        val extensionLength = 1..2
        private const val EXTENSION_CHARACTERS = "A-Z"
        private val extensionSanitizer = StringSanitizer(KmNumber::class, EXTENSION_CHARACTERS, extensionLength)
        val ZERO = KmNumber(0)
    }

    private val stringValue: String by lazy { "${number.toString().padStart(4, '0')}${extension ?: ""}" }

    @JsonValue override fun toString(): String = stringValue

    init {
        extension?.let(extensionSanitizer::assertSanitized)
    }

    override fun compareTo(other: KmNumber): Int {
        val kmComparison = number - other.number
        return if (kmComparison != 0) kmComparison else compareValues(extension, other.extension)
    }

    fun isPrimary(): Boolean = extension?.let { it == "A" } ?: true
}

const val METERS_MAX_INTEGER_DIGITS = 4
const val METERS_MAX_DECIMAL_DIGITS = 6

private fun limitScale(meters: BigDecimal) =
    if (meters.scale() < 0) {
        meters.setScale(0)
    } else if (meters.scale() > METERS_MAX_DECIMAL_DIGITS) {
        meters.setScale(METERS_MAX_DECIMAL_DIGITS, HALF_UP)
    } else {
        meters
    }

interface ITrackMeter : Comparable<ITrackMeter> {
    val kmNumber: KmNumber
    val meters: BigDecimal

    fun format(): String = formatTrackMeter(kmNumber, meters)

    fun formatFixedDecimals(decimals: Int = 3): String =
        formatTrackMeter(kmNumber, meters.setScale(decimals, RoundingMode.HALF_UP))

    fun formatDropDecimals(decimals: Int = 0): String = formatTrackMeter(kmNumber, metersFloor(decimals))

    fun formatRoundDecimals(decimals: Int = 0): String = formatTrackMeter(kmNumber, metersRound(decimals))

    fun metersFloor(decimals: Int = 0): BigDecimal = meters.setScale(decimals, DOWN)

    fun metersCeil(decimals: Int = 0): BigDecimal = meters.setScale(decimals, UP)

    fun metersRound(decimals: Int = 0): BigDecimal = meters.setScale(decimals, HALF_UP)

    fun decimalCount(): Int = meters.scale()

    fun isSame(other: ITrackMeter, decimals: Int? = null): Boolean =
        (decimals?.let { d -> compare(this, other, d) } ?: this.compareTo(other)) == 0

    override operator fun compareTo(other: ITrackMeter): Int = compare(this, other)
}

data class TrackMeter
@JsonCreator(mode = DISABLED)
constructor(override val kmNumber: KmNumber, override val meters: BigDecimal) : ITrackMeter {
    /**
     * Returns true if the meters value has no decimals.
     * - TrackMeter("1234+1234.1234").hasIntegerPrecision() == false
     * - TrackMeter("1234+1234.0000").hasIntegerPrecision() == false
     * - TrackMeter("1234+1234").hasIntegerPrecision() == true
     */
    fun hasIntegerPrecision() = meters.scale() <= 0

    /**
     * Returns true if any decimals on the meters value are zeroes (or there are none)
     * - TrackMeter("1234+1234.1234").matchesIntegerValue() == false
     * - TrackMeter("1234+1234.0000").matchesIntegerValue() == true
     * - TrackMeter("1234+1234").matchesIntegerValue() == true
     */
    fun matchesIntegerValue() = meters.stripTrailingZeros().scale() <= 0

    private constructor(values: Pair<KmNumber, BigDecimal>) : this(values.first, values.second)

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parseTrackMeterParts(value))

    companion object {
        private val maxMeter = BigDecimal.valueOf(10.0.pow(METERS_MAX_INTEGER_DIGITS))
        private val metersDecimalsValidRange = 0..METERS_MAX_DECIMAL_DIGITS

        val ZERO = TrackMeter(KmNumber.ZERO, BigDecimal.ZERO)

        fun capMeters(value: BigDecimal): BigDecimal = maxOf(minOf(value, maxMeter), -maxMeter)

        fun isMetersValid(v: BigDecimal): Boolean {
            return -maxMeter <= v && v < maxMeter
        }

        fun isMetersValid(v: LineM<*>) = isMetersValid(v.distance)

        fun isMetersValid(v: Double) = isMetersValid(BigDecimal.valueOf(v))

        fun isMetersValid(v: Int) = isMetersValid(v.toBigDecimal())
    }

    init {
        require(isMetersValid(meters)) { "Track address meters outside valid range: km=$kmNumber value=$meters" }
        require(meters.scale() in metersDecimalsValidRange) {
            "Track address meters have too many decimals: km=$kmNumber value=$meters"
        }
    }

    constructor(kmNumber: KmNumber, meters: Int) : this(kmNumber, meters.toBigDecimal())

    constructor(kmNumber: KmNumber, meters: Double, decimals: Int) : this(kmNumber, round(meters, decimals))

    constructor(kmNumber: KmNumber, meters: String) : this(kmNumber, meters.toBigDecimal())

    constructor(kmNumber: String, meters: Int) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: String, meters: Double, decimals: Int) : this(KmNumber(kmNumber), meters, decimals)

    constructor(kmNumber: String, meters: String) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: String, meters: BigDecimal) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: Int, meters: Int) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: Int, meters: Double, decimals: Int) : this(KmNumber(kmNumber), meters, decimals)

    constructor(kmNumber: Int, meters: String) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: Int, meters: BigDecimal) : this(KmNumber(kmNumber), meters)

    constructor(kmNumber: Int, extension: String, meters: Int) : this(KmNumber(kmNumber, extension), meters)

    constructor(
        kmNumber: Int,
        extension: String,
        meters: Double,
        decimals: Int,
    ) : this(KmNumber(kmNumber, extension), meters, decimals)

    constructor(kmNumber: Int, extension: String, meters: String) : this(KmNumber(kmNumber, extension), meters)

    constructor(kmNumber: Int, extension: String, meters: BigDecimal) : this(KmNumber(kmNumber, extension), meters)

    override fun toString() = format()

    fun floor(decimals: Int = 0) = TrackMeter(kmNumber, metersFloor(decimals))

    fun ceil(decimals: Int = 0) = TrackMeter(kmNumber, metersCeil(decimals))

    fun round(decimals: Int = 0) = TrackMeter(kmNumber, metersRound(decimals))

    operator fun plus(metersDelta: Int) = plus(metersDelta.toBigDecimal())

    operator fun plus(metersDelta: Double) = plus(metersDelta, meters.scale())

    operator fun plus(metersDelta: BigDecimal) = TrackMeter(kmNumber, meters + metersDelta)

    fun plus(metersDelta: Double, decimals: Int) = plus(round(metersDelta, decimals))

    operator fun minus(metersDelta: Int) = minus(metersDelta.toBigDecimal())

    operator fun minus(metersDelta: Double) = minus(metersDelta, meters.scale())

    operator fun minus(metersDelta: BigDecimal) = TrackMeter(kmNumber, meters - metersDelta)

    fun minus(metersDelta: Double, decimals: Int) = minus(round(metersDelta, decimals))

    fun stripTrailingZeroes() = TrackMeter(kmNumber, limitScale(meters.stripTrailingZeros()))
}

private fun getMetersFormat(decimals: Int) =
    meterFormats[decimals] ?: throw IllegalStateException("No meters format defined for scale $decimals")

private val meterFormats: Map<Int, DecimalFormat> by lazy {
    (0..METERS_MAX_DECIMAL_DIGITS).associateWith { decimals ->
        val decimalsPart = if (decimals > 0) ".${"0".repeat(decimals)}" else ""
        val format = DecimalFormat("0000$decimalsPart")
        format.decimalFormatSymbols = decimalSymbols
        format
    }
}
private val decimalSymbols: DecimalFormatSymbols by lazy {
    val symbols = DecimalFormatSymbols()
    symbols.decimalSeparator = '.'
    symbols
}

private fun parseTrackMeterParts(value: String): Pair<KmNumber, BigDecimal> {
    val parts = value.split(TRACK_METER_SEPARATOR)
    return KmNumber(parts[0]) to limitScale(parts[1].toBigDecimal())
}

private fun parseKmNumberParts(kmString: String): Pair<Int, String?> {
    val letterStart = kmString.indexOfFirst { c -> !c.isDigit() }
    val numberPart = if (letterStart > 0) kmString.substring(0, letterStart) else kmString
    val number =
        numberPart.toIntOrNull()
            ?: throw IllegalArgumentException("KM-number doesn't have a number: ${formatForException(kmString)}")
    val letterPart = if (letterStart > 0) kmString.substring(letterStart).uppercase() else null
    return number to letterPart
}

fun formatTrackMeter(kmNumber: KmNumber, meters: BigDecimal): String =
    "$kmNumber$TRACK_METER_SEPARATOR${getMetersFormat(meters.scale()).format(meters)}"

fun compare(trackMeter1: ITrackMeter, trackMeter2: ITrackMeter): Int {
    val kmNumberComparison = trackMeter1.kmNumber.compareTo(trackMeter2.kmNumber)
    return if (kmNumberComparison != 0) kmNumberComparison else compareValues(trackMeter1.meters, trackMeter2.meters)
}

fun compare(trackMeter1: ITrackMeter, trackMeter2: ITrackMeter, decimals: Int): Int {
    return compareValuesBy(trackMeter1, trackMeter2, { tm -> tm.kmNumber }, { tm -> tm.metersRound(decimals) })
}
