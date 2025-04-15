package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import java.util.UUID

data class Uuid<T> @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(private val value: UUID) {
    constructor(value: String) : this(UUID.fromString(value))

    @JsonValue override fun toString(): String = value.toString()
}
