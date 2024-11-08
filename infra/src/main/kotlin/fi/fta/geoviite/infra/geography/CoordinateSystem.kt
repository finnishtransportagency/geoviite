package fi.fta.geoviite.infra.geography

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.util.StringSanitizer

data class CoordinateSystem(val srid: Srid, val name: CoordinateSystemName, val aliases: List<CoordinateSystemName>)

data class CoordinateSystemName @JsonCreator(mode = DELEGATING) constructor(private val value: String) :
    CharSequence by value {

    companion object {
        private val allowedLength = 1..100
        private const val ALLOWED_CHARACTERS = "A-ZÄÖÅa-zäöå0-9 _\\-/(),"
        val sanitizer = StringSanitizer(CoordinateSystemName::class, ALLOWED_CHARACTERS, allowedLength)
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    fun uppercase(): CoordinateSystemName = CoordinateSystemName(value.uppercase())
}

fun mapByNameOrAlias(coordinateSystems: List<CoordinateSystem>) =
    coordinateSystems
        .flatMap { cs ->
            val allNames = cs.aliases + cs.aliases.map(CoordinateSystemName::uppercase) + cs.name + cs.name.uppercase()
            allNames.distinct().map { name -> name to cs.srid }
        }
        .associate { it }
