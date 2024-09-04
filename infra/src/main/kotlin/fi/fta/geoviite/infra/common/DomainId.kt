package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IdType.INDEXED
import fi.fta.geoviite.infra.common.IdType.INT
import fi.fta.geoviite.infra.common.IdType.STRING
import fi.fta.geoviite.infra.util.formatForException
import java.util.*

private const val SEPARATOR = "_"

enum class IdType(val shortName: String) {
    STRING("STR"),
    INT("INT"),
    INDEXED("IDX");

    fun typeMatches(source: String): Boolean {
        return source.startsWith(shortName + SEPARATOR)
    }

    fun clipType(source: String): String {
        return source.drop(shortName.length + SEPARATOR.length)
    }
}

private inline fun <reified T> parsingError(source: String): Nothing =
    throw IllegalArgumentException("Invalid ${T::class.simpleName}: ${formatForException(source)}")

sealed class DomainId<T> {
    companion object {
        val stringLength = 5..100

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): DomainId<T> = tryParse(source) ?: parsingError<DomainId<T>>(source)

        private fun <T> tryParse(source: String): DomainId<T>? =
            source
                .takeIf { v -> v.length in stringLength }
                ?.let { v ->
                    when (getType(v)) {
                        INT -> IntId.tryParse(v)
                        STRING -> StringId.tryParse(v)
                        INDEXED -> IndexedId.tryParse(v)
                        null -> null
                    }
                }

        @JvmStatic
        protected fun getType(source: String): IdType? = IdType.entries.firstOrNull { t -> t.typeMatches(source) }
    }
}

data class StringId<T>(val stringValue: String = "${UUID.randomUUID()}") : DomainId<T>() {
    companion object {
        val stringLength = 5..100 // Min: STR_x

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): StringId<T> = tryParse(source) ?: parsingError<IntId<T>>(source)

        fun <T> tryParse(source: String): StringId<T>? =
            source
                .takeIf { s -> s.length in stringLength && getType(s) == STRING }
                ?.let { STRING.clipType(source) }
                ?.let(::StringId)
    }

    @JsonValue override fun toString(): String = "${STRING.shortName}$SEPARATOR$stringValue"
}

data class IntId<T>(val intValue: Int) : DomainId<T>() {
    companion object {
        val stringLength = 5..(4 + Int.MAX_VALUE.toString().length) // Min: INT_0

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): IntId<T> = tryParse(source) ?: parsingError<IntId<T>>(source)

        fun <T> tryParse(source: String): IntId<T>? =
            source
                .takeIf { s -> s.length in stringLength && getType(s) == INT }
                ?.let { INT.clipType(source).toIntOrNull() }
                ?.let(::IntId)
    }

    @JsonValue override fun toString(): String = "${INT.shortName}$SEPARATOR$intValue"
}

data class IndexedId<T>(val parentId: Int, val index: Int) : DomainId<T>() {
    companion object {
        val stringLength = 7..(5 + 2 * Int.MAX_VALUE.toString().length) // Min: IDX_0_0

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): IndexedId<T> = tryParse(source) ?: parsingError<IndexedId<T>>(source)

        fun <T> tryParse(source: String): IndexedId<T>? =
            source
                .takeIf { s -> s.length in stringLength && getType(s) == INDEXED }
                ?.let { s ->
                    val parts = INDEXED.clipType(s).split(SEPARATOR).map(String::toInt)
                    val parent = parts.getOrNull(0)
                    val index = parts.getOrNull(1)
                    if (parts.size == 2 && parent != null && index != null) IndexedId(parent, index) else null
                }
    }

    @JsonValue override fun toString(): String = "${INDEXED.shortName}$SEPARATOR$parentId$SEPARATOR$index"
}
