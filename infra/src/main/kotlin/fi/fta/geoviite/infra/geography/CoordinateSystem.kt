package fi.fta.geoviite.infra.geography

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.util.assertSanitized

data class CoordinateSystem(
    val srid: Srid,
    val name: CoordinateSystemName,
    val aliases: List<CoordinateSystemName>,
)

val csNameLength = 1..100
val csNameRegex = Regex("^[A-ZÄÖÅa-zäöå0-9 _\\-/(),]+\$")

data class CoordinateSystemName
@JsonCreator(mode = DELEGATING)
constructor(private val value: String) : CharSequence by value {
    init {
        assertSanitized<CoordinateSystemName>(value, csNameRegex, csNameLength, allowBlank = false)
    }

    @JsonValue override fun toString(): String = value

    fun uppercase(): CoordinateSystemName = CoordinateSystemName(value.uppercase())
}

fun mapByNameOrAlias(coordinateSystems: List<CoordinateSystem>) =
    coordinateSystems
        .flatMap { cs ->
            val allNames =
                cs.aliases +
                    cs.aliases.map(CoordinateSystemName::uppercase) +
                    cs.name +
                    cs.name.uppercase()
            allNames.distinct().map { name -> name to cs.srid }
        }
        .associate { it }
