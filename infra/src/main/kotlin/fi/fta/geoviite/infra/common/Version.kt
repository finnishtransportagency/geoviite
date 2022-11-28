package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue


private const val VERSION_SEPARATOR = "d"

data class Version @JsonCreator(mode = DISABLED) constructor(val official: Int?, val draft: Int?): Comparable<Version> {
    private constructor(valuePair: Pair<Int?, Int?>) : this(valuePair.first, valuePair.second)
    @JsonCreator(mode = DELEGATING)
    constructor(value: String) : this(parseVersionValues(value))

    companion object {
        val NONE = Version(null, null)
    }

    init {
        require(official == null || official > 0) { "Version numbers start at 1: official=$official" }
        require(draft == null || draft > 0) { "Version numbers start at 1: draft=$draft" }
    }

    @JsonValue
    override fun toString(): String {
        val baseVersion = intToString(official ?: 0, 4)
        return if (draft != null) {
            "$baseVersion$VERSION_SEPARATOR${intToString(draft, 3)}"
        } else {
            baseVersion
        }
    }

    override fun compareTo(other: Version): Int =
        compareValuesBy(this, other, { v -> v.official ?: 0 }, { v -> v.draft ?: 0 })
}

private fun intToString(version: Int, digits: Int) = version.toString().padStart(digits, '0')

private fun parseVersionValues(stringVersion: String): Pair<Int?, Int?> {
    val split = stringVersion.split(VERSION_SEPARATOR)
    require(split.size in 1..2) { "String must contain 1 or 2 numbers: found ${split.size}" }
    val official = if (split[0].isNotBlank()) split[0].toInt() else null
    val draft = if (split.size > 1) split[1].toInt() else null
    return official to draft
}
