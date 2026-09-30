package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonCreator.Mode.DELEGATING
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.graph.TopologyDetailLevel
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal
import java.util.UUID

@Schema(
    title = "Topologiagraafin resoluutio",
    type = "string",
    allowableValues = [FI_NANO, FI_MICRO],
    defaultValue = FI_NANO,
)
enum class ExtTopologyResolutionV1(val value: String) {
    NANO(FI_NANO),
    MICRO(FI_MICRO);

    @JsonValue override fun toString() = value

    fun toDetailLevel(): TopologyDetailLevel =
        when (this) {
            NANO -> TopologyDetailLevel.NANO
            MICRO -> TopologyDetailLevel.MICRO
        }

    companion object {
        private val byValue = entries.associateBy(ExtTopologyResolutionV1::value)

        fun fromValue(value: String): ExtTopologyResolutionV1 =
            byValue[value] ?: throw ExtInvalidTopologyResolutionV1(value)
    }
}

@Schema(title = "Topologiasolmun tyyppi", type = "string", allowableValues = [FI_TRACK_END, FI_SWITCH_ENDPOINT])
enum class ExtTopologyNodeTypeV1(val value: String) {
    TRACK_END(FI_TRACK_END),
    SWITCH(FI_SWITCH_ENDPOINT);

    @JsonValue override fun toString() = value
}

@Schema(title = "Topologiakaaren kulkusuunta", type = "string", allowableValues = [FI_ASCENDING, FI_DESCENDING])
enum class ExtTopologyDirectionV1(val value: String) {
    ASCENDING(FI_ASCENDING),
    DESCENDING(FI_DESCENDING);

    @JsonValue override fun toString() = value
}

@Schema(description = "Topologiasolmun yksilöivä tunniste", type = "string", format = "uuid")
data class ExtTopologyNodeIdV1(val value: UUID) {
    @JsonValue override fun toString() = value.toString()

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(UUID.fromString(value))
}

@Schema(description = "Topologiakaaren yksilöivä tunniste", type = "string", format = "uuid")
data class ExtTopologyEdgeIdV1(val value: UUID) {
    @JsonValue override fun toString() = value.toString()

    @JsonCreator(mode = DELEGATING) constructor(value: String) : this(UUID.fromString(value))
}

@Schema(title = "Topologiakaaren sijaintiraideviittaus")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyLocationTrackReferenceV1(
    @Schema(example = "1.2.246.578.3.10002.191167")
    @JsonProperty(TOPOLOGY_REFERENCE_OID)
    val oid: ExtOidV1<LocationTrack>
)

@Schema(title = "Topologiasolmun vaihdeviittaus")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologySwitchReferenceV1(
    @Schema(example = "1.2.246.578.3.117.198700") @JsonProperty(TOPOLOGY_REFERENCE_OID) val oid: ExtOidV1<LayoutSwitch>,
    @Schema(example = "1") @JsonProperty(TOPOLOGY_SWITCH_JOINT) val jointNumber: Int,
)

@Schema(title = "Suunnattu topologiakaariviittaus")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyEdgeReferenceV1(
    @JsonProperty(TOPOLOGY_ID) val id: ExtTopologyEdgeIdV1,
    @JsonProperty(TOPOLOGY_DIRECTION) val direction: ExtTopologyDirectionV1,
)

@Schema(title = "Sallittu siirtymä topologiasolmun kautta")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyTransitionV1(
    @JsonProperty(TOPOLOGY_INCOMING_EDGE) val incomingEdge: ExtTopologyEdgeReferenceV1,
    @JsonProperty(TOPOLOGY_OUTGOING_EDGE) val outgoingEdge: ExtTopologyEdgeReferenceV1,
)

@Schema(title = "Topologiakaari")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyEdgeV1(
    @JsonProperty(TOPOLOGY_ID) val id: ExtTopologyEdgeIdV1,
    @JsonProperty(TOPOLOGY_EDGE_START_NODE) val startNode: ExtTopologyNodeIdV1,
    @JsonProperty(TOPOLOGY_EDGE_END_NODE) val endNode: ExtTopologyNodeIdV1,
    @JsonProperty(TOPOLOGY_EDGE_LENGTH) val length: BigDecimal,
    @JsonProperty(TRACKS) val tracks: List<ExtTopologyLocationTrackReferenceV1>,
)

@Schema(title = "Topologiasolmu")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyNodeV1(
    @JsonProperty(TOPOLOGY_ID) val id: ExtTopologyNodeIdV1,
    @JsonProperty(TYPE) val type: ExtTopologyNodeTypeV1,
    @JsonProperty(SWITCHES) val switches: List<ExtTopologySwitchReferenceV1>,
    @JsonProperty(LOCATION) val location: ExtCoordinateV1,
    @JsonProperty(TOPOLOGY_TRANSITIONS) val transitions: List<ExtTopologyTransitionV1>,
)

@Schema(title = "Rataverkon topologia")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyV1(
    @JsonProperty(TOPOLOGY_RESOLUTION) val resolution: ExtTopologyResolutionV1,
    @JsonProperty(TOPOLOGY_EDGES) val edges: List<ExtTopologyEdgeV1>,
    @JsonProperty(TOPOLOGY_NODES) val nodes: List<ExtTopologyNodeV1>,
)

@Schema(title = "Vastaus: Rataverkon topologia")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTopologyResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TOPOLOGY) val topology: ExtTopologyV1,
)
