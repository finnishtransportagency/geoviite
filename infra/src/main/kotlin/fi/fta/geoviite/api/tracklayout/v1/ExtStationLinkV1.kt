package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointName
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Liikennepaikkavälin päätepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtStationLinkEndpointV1(
    @Schema(example = "1.2.246.578.3.139.310516")
    @JsonProperty(OPERATIONAL_POINT_OID)
    val operationalPointOid: ExtOidV1<OperationalPoint>,
    @Schema(type = "string", example = "Helsinki asema") @JsonProperty(NAME) val name: OperationalPointName,
)

@Schema(name = "Liikennepaikkavälin sijaintiraide")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtStationLinkTrackV1(
    @Schema(example = "1.2.246.578.3.10002.192346")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>
)

@Schema(name = "Liikennepaikkaväli")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtStationLinkV1(
    @Schema(example = "001") @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @Schema(example = "1.2.246.578.3.10001.188907")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(STATION_LINK_START) val start: ExtStationLinkEndpointV1,
    @JsonProperty(STATION_LINK_END) val end: ExtStationLinkEndpointV1,
    @Schema(example = "3057.5") @JsonProperty(STATION_LINK_LENGTH) val length: Double,
    @JsonProperty(TRACKS) val tracks: List<ExtStationLinkTrackV1>,
)

@Schema(name = "Vastaus: Liikennepaikkavälit")
data class ExtStationLinkCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(STATION_LINK_COLLECTION) val connectionCollection: List<ExtStationLinkV1>,
)
