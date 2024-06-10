package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.formatForException

private const val VERSION_SEPARATOR = "v"

private val intLength = 1..(Int.MAX_VALUE.toString().length)

data class TableRowId<T>(val intValue: Int) {
    override fun toString(): String = "ROW_$intValue"
}

data class RowVersion<T> @JsonCreator(mode = DISABLED) constructor(val id: TableRowId<T>, val version: Int) {
    private constructor(valuePair: Pair<TableRowId<T>, Int>) : this(valuePair.first, valuePair.second)
    @JsonCreator(mode = DELEGATING)
    constructor(value: String) : this(parseVersionValues(value))

    init { require(version > 0) { "Version numbers start at 1: version=$version" } }

    @JsonValue
    override fun toString(): String = "$id$VERSION_SEPARATOR$version"

    fun next() = RowVersion(id, version + 1)
    fun previous() = if (version > 1) RowVersion(id, version - 1) else null
}

private fun <T> parseVersionValues(stringVersion: String): Pair<TableRowId<T>, Int> {
    val split = stringVersion.split(VERSION_SEPARATOR)
    require(split.size == 2) { "${RowVersion::class.simpleName} must contain 2 numbers: found ${split.size}" }
    split.forEach { i ->
        require(i.length in intLength) { "Invalid ${RowVersion::class.simpleName} number: ${formatForException(i)}" }
    }
    return TableRowId<T>(split[0].toInt()) to split[1].toInt()
}
