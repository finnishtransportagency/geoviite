package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue

private const val VERSION_SEPARATOR = "v"

data class RowVersion<T> @JsonCreator(mode = DISABLED) constructor(val id: IntId<T>, val version: Int) {
    private constructor(valuePair: Pair<IntId<T>, Int>) : this(valuePair.first, valuePair.second)

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(parseVersionValues(value))

    init {
        require(version > 0) { "Version numbers start at 1: version=$version" }
    }

    @JsonValue override fun toString(): String = "${id.intValue}$VERSION_SEPARATOR$version"

    fun next() = RowVersion(id, version + 1)

    fun previous() = if (version > 1) RowVersion(id, version - 1) else null
}

private fun <T> parseVersionValues(stringVersion: String): Pair<IntId<T>, Int> {
    val split = stringVersion.split(VERSION_SEPARATOR)
    require(split.size == 2) { "${RowVersion::class.simpleName} must contain 2 numbers: found ${split.size}" }
    return IntId<T>(split[0].toInt()) to split[1].toInt()
}
