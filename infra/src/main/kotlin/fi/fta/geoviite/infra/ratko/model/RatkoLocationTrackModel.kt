package fi.fta.geoviite.infra.ratko.model

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.MainBranchRatkoExternalId
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.publication.PublicationDetails
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitTarget
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM

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
    OLD("OLD"),
}

enum class RatkoTopologicalConnectivityType(@get:JsonValue val value: String) {
    NONE("NONE"),
    START("START"),
    END("END"),
    START_AND_END("START_AND_END"),
}

data class RatkoSplitSourceTrack(
    val track: LocationTrack,
    val geometry: DbLocationTrackGeometry,
    val externalId: MainBranchRatkoExternalId<LocationTrack>,
    val existingRatkoLocationTrack: RatkoLocationTrack,
    val geocodingContext: GeocodingContext<ReferenceLineM>,
)

data class RatkoSplitTargetTrack(
    val track: LocationTrack,
    val geometry: DbLocationTrackGeometry,
    val externalId: MainBranchRatkoExternalId<LocationTrack>,
    val existingRatkoLocationTrack: RatkoLocationTrack?,
    val splitTarget: SplitTarget,
)

data class RatkoSplit(
    val publication: PublicationDetails,
    val split: Split,
    val ratkoSourceTrack: RatkoLocationTrack,
)
