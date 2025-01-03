package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue

@JsonInclude(JsonInclude.Include.NON_NULL)
data class RatkoLocationTrack(
    val id: String?,
    val routenumber: RatkoOid<RatkoRouteNumber>?,
    val nodecollection: RatkoNodes?,
    val name: String,
    val description: String,
    val type: RatkoLocationTrackType,
    val state: RatkoLocationTrackState,
    val rowMetadata: RatkoMetadata = RatkoMetadata(),
    val duplicateOf: String?,
    val topologicalConnectivity: RatkoTopologicalConnectivityType,
    val owner: String?,
    val isPlanContext: Boolean,
    val planItemIds: List<Int>?,
)

enum class RatkoLocationTrackType(@get:JsonValue val value: String) {
    MAIN("pääraide"),
    SIDE("sivuraide"),
    TRAP("turvaraide"),
    CHORD("kujaraide"),
    NULL("Ei määritelty"),
}

enum class RatkoLocationTrackState(@get:JsonValue val value: String) {
    BUILT("BUILT"),
    DELETED("DELETED"),
    NOT_IN_USE("NOT IN USE"),
    PLANNED("PLANNED"),
    IN_USE("IN USE"),
    @Suppress("unused") OLD("OLD"),
}

enum class RatkoTopologicalConnectivityType(@get:JsonValue val value: String) {
    NONE("NONE"),
    START("START"),
    END("END"),
    START_AND_END("START_AND_END"),
}
