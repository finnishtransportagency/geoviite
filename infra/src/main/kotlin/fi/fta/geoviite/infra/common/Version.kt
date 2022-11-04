package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.core.convert.converter.Converter


private const val VERSION_SEPARATOR = "d"

data class Version constructor(val official: Int?, val draft: Int?): Comparable<Version> {

    companion object {
        @JvmStatic
        @JsonCreator
        fun create(value: String): Version = stringToVersion(value)

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

class StringToVersionConverter : Converter<String, Version> {
    override fun convert(source: String): Version = stringToVersion(source)
}

class VersionToStringConverter : Converter<Version, String> {
    override fun convert(source: Version): String = source.toString()
}

private fun intToString(version: Int, digits: Int) = version.toString().padStart(digits, '0')

private fun stringToVersion(stringVersion: String): Version {
    val split = stringVersion.split(VERSION_SEPARATOR)
    require(split.size in 1..2) { "String must contain 1 or 2 numbers: found ${split.size}" }
    return Version(
        official = if (split[0].isNotBlank()) split[0].toInt() else null,
        draft = if (split.size > 1) split[1].toInt() else null,
    )
}
