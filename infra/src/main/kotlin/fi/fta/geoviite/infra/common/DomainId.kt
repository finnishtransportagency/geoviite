package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.IdType.*
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

sealed class DomainId<T> {
    companion object {
        @JvmStatic
        @JsonCreator
        fun <T> create(value: String): DomainId<T> = stringToId(value)
    }

    @JsonValue
    fun stringFormat(): String = idToString(this)
}

data class StringId<T>(val stringValue: String = "${UUID.randomUUID()}") : DomainId<T>() {
    companion object {
        @JvmStatic
        @JsonCreator
        fun <T> create(value: String): StringId<T> = stringToStringId(value)
    }

    override fun toString(): String = stringIdToString(this)
}

data class IntId<T>(val intValue: Int) : DomainId<T>() {
    companion object {
        @JvmStatic
        @JsonCreator
        fun <T> create(value: String): IntId<T> = stringToIntId(value)
    }

    override fun toString(): String = intIdToString(this)
}

data class IndexedId<T>(val parentId: Int, val index: Int) : DomainId<T>() {
    companion object {
        @JvmStatic
        @JsonCreator
        fun <T> create(value: String): IndexedId<T> = stringToIndexedId(value)
    }

    override fun toString(): String = indexedIdToString(this)
}

private fun getType(source: String): IdType? = IdType.values().firstOrNull { t -> t.typeMatches(source) }

private fun joinInts(vararg ints: Int): String = ints.joinToString(SEPARATOR)

private fun splitInts(source: String): List<Int> = source.split(SEPARATOR).map(String::toInt)

private fun getIntPart(source: String, index: Int): Int? = splitInts(source).getOrNull(index)

fun <T> stringToId(source: String): DomainId<T> = getType(source)?.let { type ->
    when (type) {
        STRING -> stringToStringId(source)
        INT -> stringToIntId(source)
        INDEXED -> stringToIndexedId(source)
    }
} ?: throw throw IllegalArgumentException("Invalid indexed id: ${formatForException(source)}")

fun <T> stringToStringId(source: String): StringId<T> =
    if (getType(source) == STRING) StringId(STRING.clipType(source))
    else throw IllegalArgumentException("Invalid string id: ${formatForException(source)}")

fun <T> stringToIntId(source: String): IntId<T> =
    if (getType(source) == INT) IntId(INT.clipType(source).toInt())
    else throw IllegalArgumentException("Invalid int id: ${formatForException(source)}")

fun <T> stringToIndexedId(source: String): IndexedId<T> = if (getType(source) == INDEXED) {
    val raw = INDEXED.clipType(source)
    IndexedId(
        getIntPart(raw, 0) ?: throw IllegalArgumentException("Invalid indexed id: ${formatForException(source)}"),
        getIntPart(raw, 1) ?: throw IllegalArgumentException("Invalid indexed id: ${formatForException(source)}"))
} else throw IllegalArgumentException("Invalid indexed id: ${formatForException(source)}")


fun <T> idToString(source: DomainId<T>): String = when (source) {
    is StringId -> stringIdToString(source)
    is IntId -> intIdToString(source)
    is IndexedId -> indexedIdToString(source)
}

fun <T> stringIdToString(source: StringId<T>): String =
    "${STRING.shortName}$SEPARATOR${source.stringValue}"

fun <T> intIdToString(source: IntId<T>): String =
    "${INT.shortName}$SEPARATOR${source.intValue}"

fun <T> indexedIdToString(source: IndexedId<T>): String =
    "${INDEXED.shortName}$SEPARATOR${joinInts(source.parentId, source.index)}"

class StringToDomainIdConverter<T> : Converter<String, DomainId<T>> {
    override fun convert(source: String): DomainId<T> = stringToId(source)
}

class DomainIdToStringConverter<T> : Converter<DomainId<T>, String> {
    override fun convert(source: DomainId<T>): String = idToString(source)
}

class StringToStringIdConverter<T> : Converter<String, StringId<T>> {
    override fun convert(source: String): StringId<T> = stringToStringId(source)
}

class StringIdToStringConverter<T> : Converter<StringId<T>, String> {
    override fun convert(source: StringId<T>): String = stringIdToString(source)
}

class StringToIntIdConverter<T> : Converter<String, IntId<T>> {
    override fun convert(source: String): IntId<T> = stringToIntId(source)
}

class IntIdToStringConverter<T> : Converter<IntId<T>, String> {
    override fun convert(source: IntId<T>): String = intIdToString(source)
}

class StringToIndexedIdConverter<T> : Converter<String, IndexedId<T>> {
    override fun convert(source: String): IndexedId<T> = stringToIndexedId(source)
}

class IndexedIdToStringConverter<T> : Converter<IndexedId<T>, String> {
    override fun convert(source: IndexedId<T>): String = indexedIdToString(source)
}
