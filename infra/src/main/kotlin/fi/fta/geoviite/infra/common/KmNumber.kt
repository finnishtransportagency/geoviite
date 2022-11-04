package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.math.round
import fi.fta.geoviite.infra.util.assertSanitized
import fi.fta.geoviite.infra.util.formatForException
import org.springframework.core.convert.converter.Converter
import java.math.BigDecimal
import java.math.RoundingMode.*
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.pow

const val TRACK_METER_SEPARATOR = "+"

const val DEFAULT_TRACK_METER_DECIMALS = 3

private val extensionLength = 1..2
private val extensionRegex = Regex("^[A-Z]*\$")

private fun getMetersFormat(decimals: Int) = meterFormats[decimals]
    ?: throw IllegalStateException("No meters format defined for scale $decimals")

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

data class KmNumber(
    val number: Int,
    val extension: String? = null,
) : Comparable<KmNumber> {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(stringValue: String): KmNumber = parseKmNumber(stringValue)

        val ZERO = KmNumber(0)
    }

    private val stringValue: String by lazy {
        "${number.toString().padStart(4, '0')}${extension ?: ""}"
    }

    @JsonValue
    override fun toString(): String = stringValue


    init {
        extension?.let {
            assertSanitized<KmNumber>(it, extensionRegex, extensionLength, allowBlank = false)
        }
    }

    override fun compareTo(other: KmNumber): Int = stringValue.compareTo(other.stringValue)

    fun isPrimary(): Boolean = extension?.let { it == "A" } ?: true
}

fun parseKmNumber(kmString: String): KmNumber {
    val letterStart = kmString.indexOfFirst { c -> !c.isDigit() }
    val numberPart = if (letterStart > 0) kmString.substring(0, letterStart) else kmString
    val number = numberPart.toIntOrNull()
        ?: throw IllegalArgumentException("KM-number doesn't have a number: ${formatForException(kmString)}")
    val letterPart = if (letterStart > 0) kmString.substring(letterStart).uppercase() else null
    return KmNumber(number, letterPart)
}

fun formatTrackMeter(kmNumber: KmNumber, meters: BigDecimal): String =
    "$kmNumber$TRACK_METER_SEPARATOR${getMetersFormat(meters.scale()).format(meters)}"

fun compare(trackMeter1: ITrackMeter, trackMeter2: ITrackMeter): Int {
    return compareValuesBy(trackMeter1, trackMeter2, { tm -> tm.kmNumber }, { tm -> tm.meters })
}

fun compare(trackMeter1: ITrackMeter, trackMeter2: ITrackMeter, decimals: Int): Int {
    return compareValuesBy(trackMeter1, trackMeter2, { tm -> tm.kmNumber }, { tm -> tm.metersRound(decimals) })
}

private const val METERS_MAX_INTEGER_DIGITS = 4
private const val METERS_MAX_DECIMAL_DIGITS = 6
private fun limitScale(meters: BigDecimal) =
    if (meters.scale() < 0) meters.setScale(0)
    else if (meters.scale() > METERS_MAX_DECIMAL_DIGITS) meters.setScale(METERS_MAX_DECIMAL_DIGITS, HALF_UP)
    else meters

private val maxMeter = BigDecimal.valueOf(10.0.pow(METERS_MAX_INTEGER_DIGITS))
private val metersValidRange = -maxMeter..maxMeter
private val metersDecimalsValidRange = 0..METERS_MAX_DECIMAL_DIGITS

interface ITrackMeter : Comparable<ITrackMeter> {
    val kmNumber: KmNumber
    val meters: BigDecimal

    fun format(): String = formatTrackMeter(kmNumber, meters)
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

data class TrackMeter(override val kmNumber: KmNumber, override val meters: BigDecimal) : ITrackMeter {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(value: String): TrackMeter {
            val parts = value.split(TRACK_METER_SEPARATOR)
            return TrackMeter(parseKmNumber(parts[0]), limitScale(parts[1].toBigDecimal()))
        }

        val ZERO = TrackMeter(KmNumber.ZERO, BigDecimal.ZERO)
    }

    init {
        require(meters in metersValidRange) {
            "Track address meters outside valid range: value=$meters"
        }
        require(meters.scale() in metersDecimalsValidRange) {
            "Track address meters have too many decimals: value=$meters"
        }
    }

    constructor(kmNumber: KmNumber, meters: Int) : this(kmNumber, meters.toBigDecimal())
    constructor(kmNumber: KmNumber, meters: Double, decimals: Int) : this(kmNumber, round(meters, decimals))
    constructor(kmNumber: KmNumber, meters: String) : this(kmNumber, meters.toBigDecimal())
    constructor(kmNumber: String, meters: Int) : this(parseKmNumber(kmNumber), meters)
    constructor(kmNumber: String, meters: Double, decimals: Int) : this(parseKmNumber(kmNumber), meters, decimals)
    constructor(kmNumber: String, meters: String) : this(parseKmNumber(kmNumber), meters)
    constructor(kmNumber: String, meters: BigDecimal) : this(parseKmNumber(kmNumber), meters)
    constructor(kmNumber: Int, meters: Int) : this(KmNumber(kmNumber), meters)
    constructor(kmNumber: Int, meters: Double, decimals: Int) : this(KmNumber(kmNumber), meters, decimals)
    constructor(kmNumber: Int, meters: String) : this(KmNumber(kmNumber), meters)
    constructor(kmNumber: Int, meters: BigDecimal) : this(KmNumber(kmNumber), meters)
    constructor(kmNumber: Int, extension: String, meters: Int) : this(KmNumber(kmNumber, extension), meters)
    constructor(kmNumber: Int, extension: String, meters: Double, decimals: Int) : this(KmNumber(kmNumber, extension), meters, decimals)
    constructor(kmNumber: Int, extension: String, meters: String) : this(KmNumber(kmNumber, extension), meters)
    constructor(kmNumber: Int, extension: String, meters: BigDecimal) : this(KmNumber(kmNumber, extension), meters)

    @Override
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
}

class StringToKmNumberConverter : Converter<String, KmNumber> {
    override fun convert(source: String): KmNumber = parseKmNumber(source)
}

class KmNumberToStringConverter : Converter<KmNumber, String> {
    override fun convert(source: KmNumber): String = source.toString()
}

class StringToTrackMeterConverter : Converter<String, TrackMeter> {
    override fun convert(source: String): TrackMeter = TrackMeter.create(source)
}

class TrackMeterToStringConverter : Converter<TrackMeter, String> {
    override fun convert(source: TrackMeter): String = source.toString()
}
