package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.springframework.core.convert.converter.Converter

/**
 * Bytes of character in canonical decomposition form differ from the default form
 * (precomposed). We encountered these in some IM files, probably produced by
 * some 3D design software.
 *
 * https://www.ibm.com/docs/en/cobol-zos/6.3?topic=functions-ulength
 */
const val umlautsCanonicalDecomposition = "aäöåÄÖÅ"

val fileNameRegex = Regex("^[\\p{L}\\p{N}${umlautsCanonicalDecomposition}_\\-+., /]+\$")
val fileNameLength = 1..100

data class FileName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String) :
    Comparable<FileName>, CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    init {
        assertSanitized<FileName>(
            value, fileNameRegex, fileNameLength
        )
    }

    override fun compareTo(other: FileName): Int = value.compareTo(other.value)
}

class StringToFileNameConverter : Converter<String, FileName> {
    override fun convert(source: String): FileName = FileName(source)
}

class FileNameToStringConverter : Converter<FileName, String> {
    override fun convert(source: FileName): String = source.toString()
}
