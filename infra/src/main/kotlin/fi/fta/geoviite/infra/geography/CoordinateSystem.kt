package fi.fta.geoviite.infra.geography

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.util.assertSanitized
import org.springframework.core.convert.converter.Converter

data class CoordinateSystem(
    val srid: Srid,
    val name: CoordinateSystemName,
    val aliases: List<CoordinateSystemName>,
)

val csNameLength = 1..100
val csNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/(),]+\$")

fun mapByNameOrAlias(coordinateSystems: List<CoordinateSystem>) = coordinateSystems
    .flatMap { cs ->
        val allNames = cs.aliases + cs.aliases.map(CoordinateSystemName::uppercase) + cs.name + cs.name.uppercase()
        allNames.distinct().map { name -> name to cs.srid }
    }
    .associate { it }

data class CoordinateSystemName @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(val value: String)
    : CharSequence by value {
    @JsonValue
    override fun toString(): String = value

    fun uppercase(): CoordinateSystemName = CoordinateSystemName(value.uppercase())

    init { assertSanitized<CoordinateSystemName>(value, csNameRegex, csNameLength, allowBlank = false) }
}

class StringToCoordinateSystemNameConverter : Converter<String, CoordinateSystemName> {
    override fun convert(source: String): CoordinateSystemName = CoordinateSystemName(source)
}

class CoordinateSystemNameToStringConverter : Converter<CoordinateSystemName, String> {
    override fun convert(source: CoordinateSystemName): String = source.toString()
}
