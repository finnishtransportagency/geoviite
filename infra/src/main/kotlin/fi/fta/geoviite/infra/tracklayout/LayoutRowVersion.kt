package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.formatForException

private const val VERSION_SEPARATOR = "v"

private val intLength = 1..(Int.MAX_VALUE.toString().length)

data class LayoutRowId<T>(val intValue: Int)

data class LayoutRowVersion<T> @JsonCreator(mode = DISABLED) constructor(val rowId: LayoutRowId<T>, val version: Int) {
    private constructor(values: Pair<LayoutRowId<T>, Int>) : this(values.first, values.second)

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parseVersionValues(value))

    init {
        require(version > 0) { "Version numbers start at 1: version=$version" }
    }

    @JsonValue override fun toString(): String = "${rowId.intValue}$VERSION_SEPARATOR$version"

    fun next() = LayoutRowVersion(rowId, version + 1)

    fun previous() = if (version > 1) LayoutRowVersion(rowId, version - 1) else null
}

private fun <T> parseVersionValues(stringVersion: String): Pair<LayoutRowId<T>, Int> {
    val split = stringVersion.split(VERSION_SEPARATOR)
    require(split.size == 2) { "${LayoutRowVersion::class.simpleName} must contain 2 numbers: found ${split.size}" }
    split.forEach { i ->
        require(i.length in intLength) {
            "Invalid ${LayoutRowVersion::class.simpleName} number: ${formatForException(i)}"
        }
    }
    return LayoutRowId<T>(split[1].toInt()) to split[2].toInt()
}
