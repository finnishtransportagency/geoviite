package fi.fta.geoviite.infra.util

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import java.net.URI
import org.apache.commons.validator.routines.UrlValidator

// Prefer java.net.URI over java.net.URL for string validation as per
// https://bugs.java.com/bugdatabase/view_bug.do?bug_id=4434494
// "...to address URI parsing in general, we introduced a new class called URI in Merlin (jdk1.4).
// People are encouraged to use URI for parsing and URI comparison, and leave URL class for
// accessing the URI itself, getting at the protocol handler, interacting with the protocol etc."
data class HttpsUri @JsonCreator(mode = DELEGATING) constructor(private val value: URI) :
    Comparable<HttpsUri>, CharSequence by value.toString() {

    constructor(stringValue: String) : this(URI(stringValue))

    companion object {
        val urlLength = 1..2000
        val httpsValidator = UrlValidator(arrayOf("https"))
    }

    init {
        assertLength(HttpsUri::class, value.toString(), urlLength)
        require(httpsValidator.isValid(value.toString())) {
            "Not a valid https url: \"${formatForException(value.toString())}\""
        }
    }

    @JsonValue override fun toString(): String = value.toString()

    override fun compareTo(other: HttpsUri): Int = toString().compareTo(other.toString())
}
