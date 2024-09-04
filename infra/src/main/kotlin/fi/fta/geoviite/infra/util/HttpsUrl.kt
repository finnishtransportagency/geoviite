package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URL
import org.apache.commons.validator.routines.UrlValidator

data class HttpsUrl @JsonCreator(mode = DELEGATING) constructor(private val value: URL) :
    Comparable<HttpsUrl>, CharSequence by value.toString() {

    constructor(stringValue: String) : this(URL(stringValue))

    companion object {
        val urlLength = 1..2000
        val httpsValidator = UrlValidator(arrayOf("https"))
    }

    init {
        assertLength(HttpsUrl::class, value.toString(), urlLength)
        require(httpsValidator.isValid(value.toString())) {
            "Not a valid https url: \"${formatForException(value.toString())}\""
        }
    }

    @JsonValue override fun toString(): String = value.toString()

    override fun compareTo(other: HttpsUrl): Int = toString().compareTo(other.toString())
}
