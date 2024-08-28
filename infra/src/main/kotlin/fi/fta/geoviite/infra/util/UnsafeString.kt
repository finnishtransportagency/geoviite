package fi.fta.geoviite.infra.util

import org.springframework.web.util.HtmlUtils

const val sanitizedCharacters = "';\\--"
data class UnsafeString(val value: String, val maxLength: Int) {
val sanitized: String by lazy {
    escapeSql
}
    override fun toString(): String = HtmlUtils.htmlEscape(value)
}
