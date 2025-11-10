package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.ratko.model.OperationalPointRaideType
import fi.fta.geoviite.infra.util.StringSanitizer

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

    @JsonValue override fun toString(): String = value
}

const val ALLOWED_OPERATIONAL_POINT_NAME_CHARACTERS = "A-Za-zÄÖÅäöå0-9 \\-_/!?"

data class OperationalPointName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<OperationalPointName>, CharSequence by value {
    companion object {
        val allowedLength = 1..150
        val sanitizer =
            StringSanitizer(OperationalPointName::class, ALLOWED_OPERATIONAL_POINT_NAME_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
        //        sanitizer.assertTrimmed(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: OperationalPointName): Int = value.compareTo(other.value)
}

data class OperationalPointAbbreviation @JsonCreator(mode = DELEGATING) constructor(private val value: String) {
    companion object {
        val allowedLength = 0..20
        val sanitizer =
            StringSanitizer(
                OperationalPointAbbreviation::class,
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
    val name: OperationalPointName,
    val abbreviation: OperationalPointAbbreviation,
    val rinfType: OperationalPointRinfType,
    val state: OperationalPointState,
    val uicCode: UicCode,
)

data class ExternalOperationalPointSaveRequest(val rinfType: OperationalPointRinfType)

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
