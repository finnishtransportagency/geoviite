package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

private val oidLength = 5..50
private val oidRegex = Regex("^\\d+(\\.\\d+){2,9}\$")

data class Oid<T> @JsonCreator(mode = DELEGATING) constructor(val stringValue: String) {

    @JsonValue
    override fun toString(): String = stringValue

    init { assertSanitized<Oid<T>>(stringValue, oidRegex, oidLength, allowBlank = false) }
}

class StringToOidConverter<T> : Converter<String, Oid<T>> {
    override fun convert(source: String): Oid<T> = Oid(source)
}

class OidToStringConverter<T> : Converter<Oid<T>, String> {
    override fun convert(source: Oid<T>): String = source.stringValue
}
