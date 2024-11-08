package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.parsePrefixedInt

data class JointNumber @JsonCreator(mode = DISABLED) constructor(val intValue: Int) : Comparable<JointNumber> {
    @JsonCreator(mode = DELEGATING)
    constructor(value: String) : this(parsePrefixedInt<JointNumber>(JOINT_PREFIX, value))

    companion object {
        private const val JOINT_PREFIX = "JOINT_"
    }

    init {
        require(intValue >= 0) { "Invalid joint number: \"$intValue\"" }
    }

    @JsonValue override fun toString(): String = JOINT_PREFIX + intValue

    override fun compareTo(other: JointNumber): Int = intValue.compareTo(other.intValue)
}
