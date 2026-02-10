package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.assertLength

data class OperationalPoint(
    val name: OperationalPointName,
    val abbreviation: OperationalPointAbbreviation?,
    val uicCode: UicCode?,
    val rinfType: OperationalPointRinfType?,
    val raideType: OperationalPointRaideType?,
    val polygon: Polygon?,
    val location: Point?,
    val state: OperationalPointState,
    val origin: OperationalPointOrigin,
    val ratkoVersion: Int?,
    @JsonIgnore override val contextData: LayoutContextData<OperationalPoint>,
) : LayoutAsset<OperationalPoint>(contextData) {
    override fun toLog(): String =
        logFormat("id" to id, "version" to version, "context" to contextData::class.simpleName, "name" to name)

    override fun withContext(contextData: LayoutContextData<OperationalPoint>): OperationalPoint =
        copy(contextData = contextData)

    @JsonIgnore val exists = !state.isRemoved()
}

const val maxOperationalPointNameLength = 150
const val maxOperationalPointAbbreviationLength = 50

// operational point name and abbreviation fields only have the same restrictions as the database has for them,
// as Ratko is the master of their actual content
data class OperationalPointName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<OperationalPointName>, CharSequence by value {
    init {
        assertLength(OperationalPointName::class, value, allowedLength)
    }

    companion object {
        val allowedLength = 1..maxOperationalPointNameLength

        fun isSanitized(value: String) = value.length in allowedLength
    }

    override fun compareTo(other: OperationalPointName): Int = value.compareTo(other.value)

    @JsonValue override fun toString(): String = value
}

data class OperationalPointAbbreviation @JsonCreator(mode = DELEGATING) constructor(private val value: String) {
    init {
        assertLength(OperationalPointAbbreviation::class, value, 1..maxOperationalPointAbbreviationLength)
    }

    companion object {
        val allowedLength = 1..maxOperationalPointAbbreviationLength

        fun isSanitized(value: String) = value.length in allowedLength
    }

    @JsonValue override fun toString(): String = value
}

enum class OperationalPointOrigin {
    RATKO,
    GEOVIITE,
}

enum class OperationalPointState {
    IN_USE,
    DELETED;

    fun isRemoved() = this == DELETED
}

data class UicCode @JsonCreator(mode = DELEGATING) constructor(private val value: String) {
    companion object {
        val allowedLength = 1..20
        val sanitizer = StringSanitizer(UicCode::class, "0-9", allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    fun toInt() = value.toInt()

    @JsonValue override fun toString(): String = value
}

const val ALLOWED_OPERATIONAL_POINT_NAME_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-_/!?"

data class OperationalPointInputName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<OperationalPointInputName>, CharSequence by value {
    companion object {
        val allowedLength = 1..maxOperationalPointNameLength
        val sanitizer =
            StringSanitizer(OperationalPointInputName::class, ALLOWED_OPERATIONAL_POINT_NAME_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: OperationalPointInputName): Int = value.compareTo(other.value)
}

data class OperationalPointInputAbbreviation @JsonCreator(mode = DELEGATING) constructor(private val value: String) {
    companion object {
        val allowedLength = 1..maxOperationalPointAbbreviationLength
        val sanitizer =
            StringSanitizer(
                OperationalPointInputAbbreviation::class,
                ALLOWED_OPERATIONAL_POINT_NAME_CHARACTERS,
                allowedLength,
            )
    }

    init {
        sanitizer.assertSanitized(value)
        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value
}

data class InternalOperationalPointSaveRequest(
    val name: OperationalPointInputName,
    val abbreviation: OperationalPointInputAbbreviation?,
    val rinfType: OperationalPointRinfType,
    val state: OperationalPointState,
    val uicCode: UicCode,
)

data class ExternalOperationalPointSaveRequest(val rinfType: OperationalPointRinfType?)

enum class OperationalPointRinfType {
    STATION,
    SMALL_STATION,
    PASSENGER_TERMINAL,
    FREIGHT_TERMINAL,
    DEPOT_OR_WORKSHOP,
    TRAIN_TECHNICAL_SERVICES,
    PASSENGER_STOP,
    JUNCTION,
    BORDER_POINT,
    SHUNTING_YARD,
    TECHNICAL_CHANGE,
    SWITCH,
    PRIVATE_SIDING,
    DOMESTIC_BORDER_POINT,
    OVER_CROSSING,
}

data class OperationalPointRinfTypeWithCode(val type: OperationalPointRinfType, val code: Int)
