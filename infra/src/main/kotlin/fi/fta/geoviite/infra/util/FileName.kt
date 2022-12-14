package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue

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

data class FileName @JsonCreator(mode = DELEGATING) constructor(private val value: String)
    : Comparable<FileName>, CharSequence by value {
    init { assertSanitized<FileName>(value, fileNameRegex, fileNameLength) }

    @JsonValue
    override fun toString(): String = value
    override fun compareTo(other: FileName): Int = value.compareTo(other.value)
}
