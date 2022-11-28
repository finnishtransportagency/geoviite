package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized

private val oidLength = 5..50
private val oidRegex = Regex("^\\d+(\\.\\d+){2,9}\$")

data class Oid<T> @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    init { assertSanitized<Oid<T>>(value, oidRegex, oidLength, allowBlank = false) }

    @JsonValue
    override fun toString(): String = value
}
