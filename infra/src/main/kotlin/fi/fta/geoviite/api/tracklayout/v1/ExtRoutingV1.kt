package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(
    title = "Reitin osan päätepisteen tyyppi",
    type = "string",
    allowableValues = [FI_TRACK_POSITION, FI_SWITCH_ENDPOINT, FI_TRACK_END],
)
enum class ExtRouteEndpointTypeV1(val value: String) {
    TRACK_POSITION(FI_TRACK_POSITION),
    SWITCH(FI_SWITCH_ENDPOINT),
    TRACK_END(FI_TRACK_END);

    @JsonValue override fun toString() = value
}

@Schema(title = "Reitin osan kulkusuunta", type = "string", allowableValues = [FI_ASCENDING, FI_DESCENDING])
enum class ExtRouteDirectionV1(val value: String) {
    ASCENDING(FI_ASCENDING),
    DESCENDING(FI_DESCENDING);

    @JsonValue override fun toString() = value
}

@Schema(title = "Reitin osan päätepisteen tiedot")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtRouteSectionEndpointV1(
    @JsonProperty(ROUTE_ENDPOINT_TYPE) val type: ExtRouteEndpointTypeV1,
    @Schema(example = "1.2.246.578.3.117.197939") @JsonProperty(SWITCH_OID) val switchOid: ExtOidV1<LayoutSwitch>?,
    @Schema(type = "string", example = "0385+0909.031") @JsonProperty(TRACK_ADDRESS) val trackAddress: String?,
    val x: Double,
    val y: Double,
    @get:JsonProperty(ROUTE_ENDPOINT_M_VALUE) val mValue: BigDecimal,
)

@Schema(title = "Reitin osa")
data class ExtRouteSectionV1(
    @Schema(example = "1.2.246.578.3.10002.190119")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @Schema(example = "1.2.246.578.3.10001.188968")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(ROUTE_PART_START) val start: ExtRouteSectionEndpointV1,
    @JsonProperty(ROUTE_PART_END) val end: ExtRouteSectionEndpointV1,
    @JsonProperty(ROUTE_PART_DIRECTION) val direction: ExtRouteDirectionV1,
    @JsonProperty(ROUTE_PART_LENGTH) val length: BigDecimal,
)

@Schema(title = "Reitti")
data class ExtRouteV1(
    @JsonProperty(ROUTE_TOTAL_LENGTH) val totalLength: BigDecimal,
    @JsonProperty(ROUTE_PARTS) val sections: List<ExtRouteSectionV1>,
)

@Schema(title = "Vastaus: Reititys")
data class ExtRouteResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(ROUTE) val route: ExtRouteV1,
)
