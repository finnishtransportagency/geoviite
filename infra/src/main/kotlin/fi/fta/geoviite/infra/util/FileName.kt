package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.springframework.web.multipart.MultipartFile

enum class KnownFileSuffix {
    CSV,
    XML,
}

val FILENAME_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("Europe/Helsinki"))

data class FileName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    Comparable<FileName>, CharSequence by value {

    companion object {
        /**
         * Bytes of character in canonical decomposition form differ from the default form (precomposed). We encountered
         * these in some IM files, probably produced by some 3D design software.
         *
         * https://www.ibm.com/docs/en/cobol-zos/6.3?topic=functions-ulength
         */
        private const val umlautsCanonicalDecomposition = "aäöåÄÖÅ"

        val allowedLength = 1..100
        const val ALLOWED_CHARACTERS = "\\p{L}\\p{N}${umlautsCanonicalDecomposition}_\\-+~., /()$UNSAFE_REPLACEMENT"
        val sanitizer = StringSanitizer(FileName::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    constructor(file: MultipartFile) : this(file.originalFilename?.takeIf(String::isNotBlank) ?: file.name)

    constructor(
        unsafeString: UnsafeString
    ) : this(if (unsafeString.unsafeValue.isBlank()) "-" else sanitizer.sanitize(unsafeString.unsafeValue))

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: FileName): Int = value.compareTo(other.value)

    fun withSuffix(suffix: KnownFileSuffix): FileName =
        suffix.name
            .lowercase()
            .let { suffixName -> if (value.endsWith(".$suffixName", true)) value else "$value.$suffixName" }
            .let(::FileName)
}
