package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.parsePrefixedInt
import org.springframework.core.convert.converter.Converter

private const val JOINT_PREFIX = "JOINT_"

data class JointNumber @JsonCreator(mode = DISABLED) constructor(val intValue: Int) : Comparable<JointNumber> {

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    constructor(value: String) : this(parsePrefixedInt(JOINT_PREFIX, value))

    @JsonValue
    override fun toString(): String = JOINT_PREFIX + intValue

    init {
        require(intValue >= 0) { "Invalid joint number: \"$intValue\"" }
    }

    override fun compareTo(other: JointNumber): Int = intValue.compareTo(other.intValue)
}

class StringToJointNumberConverter : Converter<String, JointNumber> {
    override fun convert(source: String): JointNumber = JointNumber(source)
}

class JointNumberToStringConverter : Converter<JointNumber, String> {
    override fun convert(source: JointNumber): String = source.toString()
}
