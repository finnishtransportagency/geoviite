package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DISABLED
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.util.assertInput
import fi.fta.geoviite.infra.util.parsePrefixedInt
import org.springframework.core.convert.converter.Converter

private const val SRID_PREFIX = "EPSG:"
private val sridRange: IntRange = 1024..32767

data class Srid @JsonCreator(mode = DISABLED) constructor(val code: Int) {

    @JsonCreator(mode = DELEGATING)
    constructor(value: String) : this(parsePrefixedInt(SRID_PREFIX, value))

    @JsonValue
    override fun toString(): String = SRID_PREFIX + code

    init {  assertInput<Srid>(code in sridRange) { "SRID (EPSG code) $code outside allowed range $sridRange" } }
}

class StringToSridConverter : Converter<String, Srid> {
    override fun convert(source: String): Srid = Srid(source)
}

class SridToStringConverter : Converter<Srid, String> {
    override fun convert(source: Srid): String = source.toString()
}
