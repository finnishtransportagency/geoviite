package fi.fta.geoviite.infra.common

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.ratko.model.RatkoPlanItemId
import fi.fta.geoviite.infra.util.assertSanitized

data class Oid<T> @JsonCreator(mode = DELEGATING) constructor(private val value: String) : CharSequence by value {

    companion object {
        private val allowedLength = 5..50
        private val sanitizer = Regex("^\\d+(\\.\\d+){2,9}\$")
    }

    init {
        assertSanitized<Oid<T>>(value, sanitizer, allowedLength)
    }

    @JsonValue override fun toString(): String = value
}

class RatkoExternalId<T>(val oid: Oid<T>, val planItemId: RatkoPlanItemId?)

sealed class FullRatkoExternalId<T>(open val oid: Oid<T>)

data class MainBranchRatkoExternalId<T>(override val oid: Oid<T>) : FullRatkoExternalId<T>(oid)

data class DesignRatkoExternalId<T>(override val oid: Oid<T>, val planItemId: RatkoPlanItemId) :
    FullRatkoExternalId<T>(oid)
