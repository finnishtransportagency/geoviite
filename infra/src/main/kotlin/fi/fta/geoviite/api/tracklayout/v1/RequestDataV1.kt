package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING

data class ApiRequestStringV1 @JsonCreator(mode = DELEGATING) constructor(val value: String) {

    init {
        require(value.length <= MAX_LENGTH) { "String field length must be at most $MAX_LENGTH characters" }
    }

    override fun toString(): String {
        return value
    }

    companion object {
        const val MAX_LENGTH = 200
    }
}
