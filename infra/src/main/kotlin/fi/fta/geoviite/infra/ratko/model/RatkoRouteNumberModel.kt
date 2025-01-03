package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoRouteNumber(
    val id: String?,
    val nodecollection: RatkoNodes?,
    val name: String,
    val description: String,
    val state: RatkoRouteNumberState,
    val rowMetadata: RatkoMetadata = RatkoMetadata(),
    val isPlanContext: Boolean,
    val planItemIds: List<Int>?,
)

data class RatkoRouteNumberState(val name: RatkoRouteNumberStateType)

enum class RatkoRouteNumberStateType(@get:JsonValue val state: String) {
    VALID("VALID"),
    NOT_IN_USE("NOT IN USE"),
    NOT_VALID("NOT VALID"),
}
