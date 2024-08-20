package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URL
import org.apache.commons.validator.routines.UrlValidator

val urlLength = 1..2000
val httpsValidator = UrlValidator(arrayOf("https"))

data class HttpsUrl @JsonCreator(mode = DELEGATING) constructor(private val value: URL) :
    Comparable<HttpsUrl>, CharSequence by value.toString() {
    constructor(stringValue: String) : this(URL(stringValue))

    init {
        assertLength<HttpsUrl>(value.toString(), urlLength)
        require(httpsValidator.isValid(value.toString())) {
            "Not a valid https url: \"${formatForException(value.toString())}\""
        }
    }

    @JsonValue override fun toString(): String = value.toString()

    override fun compareTo(other: HttpsUrl): Int = toString().compareTo(other.toString())
}
