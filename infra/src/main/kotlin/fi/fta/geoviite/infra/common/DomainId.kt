package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IdType.INDEXED
import fi.fta.geoviite.infra.common.IdType.INT
import fi.fta.geoviite.infra.common.IdType.STRING
import fi.fta.geoviite.infra.util.formatForException
import org.springframework.core.convert.converter.Converter
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

private inline fun <reified T> parsingError(source: String): Nothing = throw IllegalArgumentException(
    "Invalid ${T::class.simpleName}: ${formatForException(source)}"
)

sealed class DomainId<T> {
    companion object {
        val stringLength = 5..100

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): DomainId<T> = tryParse(source) ?: parsingError<DomainId<T>>(source)

        private fun <T> tryParse(source: String): DomainId<T>? = source
            .takeIf { v -> v.length in stringLength }
            ?.let { v ->
                println("parsing $source")
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

        fun <T> tryParse(source: String): StringId<T>? = source
            .also { println("parsing (string) $source") }
            .takeIf { s -> s.length in stringLength && getType(s) == STRING }
            ?.let { STRING.clipType(source) }
            ?.let(::StringId)
    }

    @JsonValue
    override fun toString(): String = "${STRING.shortName}$SEPARATOR$stringValue"
}

data class IntId<T>(val intValue: Int) : DomainId<T>() {
    companion object {
        val stringLength = 5..(4 + Int.MAX_VALUE.toString().length) // Min: INT_0

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): IntId<T> = tryParse(source) ?: parsingError<IntId<T>>(source)

        fun <T> tryParse(source: String): IntId<T>? = source
            .also { println("parsing (int) $source") }
            .takeIf { s -> s.length in stringLength && getType(s) == INT }
            ?.let { INT.clipType(source).toIntOrNull() }
            ?.let(::IntId)
    }

    @JsonValue
    override fun toString(): String = "${INT.shortName}$SEPARATOR$intValue"
}

data class IndexedId<T>(val parentId: Int, val index: Int) : DomainId<T>() {
    companion object {
        val stringLength = 7..(5 + 2 * Int.MAX_VALUE.toString().length) // Min: IDX_0_0

        @JvmStatic
        @JsonCreator
        fun <T> parse(source: String): IndexedId<T> = tryParse(source) ?: parsingError<IndexedId<T>>(source)

        fun <T> tryParse(source: String): IndexedId<T>? = source
            .also { println("parsing (indexed) $source") }
            .takeIf { s -> s.length in stringLength && getType(s) == INDEXED }
            ?.let { s ->
                val raw = INDEXED.clipType(s)
                val parent = getIntPart(raw, 0)
                val index = getIntPart(raw, 1)
                if (parent != null && index != null) IndexedId(parent, index) else null
            }

        private fun getIntPart(source: String, index: Int): Int? = splitInts(source).getOrNull(index)

        private fun splitInts(source: String): List<Int> = source.split(SEPARATOR).map(String::toInt)
    }

    @JsonValue
    override fun toString(): String = "${INDEXED.shortName}$SEPARATOR$parentId$SEPARATOR$index"
}

class StringToDomainIdConverter<T> : Converter<String, DomainId<T>> {
    override fun convert(source: String): DomainId<T> = DomainId.parse(source)
}

class DomainIdToStringConverter<T> : Converter<DomainId<T>, String> {
    override fun convert(source: DomainId<T>): String = source.toString()
}

class StringToStringIdConverter<T> : Converter<String, StringId<T>> {
    override fun convert(source: String): StringId<T> = StringId.parse(source)
}

class StringIdToStringConverter<T> : Converter<StringId<T>, String> {
    override fun convert(source: StringId<T>): String = source.toString()
}

class StringToIntIdConverter<T> : Converter<String, IntId<T>> {
    override fun convert(source: String): IntId<T> = IntId.parse(source)
}

class IntIdToStringConverter<T> : Converter<IntId<T>, String> {
    override fun convert(source: IntId<T>): String = source.toString()
}

class StringToIndexedIdConverter<T> : Converter<String, IndexedId<T>> {
    override fun convert(source: String): IndexedId<T> = IndexedId.parse(source)
}

class IndexedIdToStringConverter<T> : Converter<IndexedId<T>, String> {
    override fun convert(source: IndexedId<T>): String = source.toString()
}
