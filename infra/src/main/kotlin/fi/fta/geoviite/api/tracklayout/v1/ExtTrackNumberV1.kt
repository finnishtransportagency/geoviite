package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Ratanumero")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackNumberV1(
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @Schema(type = "string", example = "001") @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @Schema(type = "string", example = "Helsinki - Kirkkonummi (PR) - Karjaa - Turku")
    @JsonProperty(DESCRIPTION)
    val trackNumberDescription: TrackNumberDescription,
    @JsonProperty(STATE) val trackNumberState: ExtTrackNumberStateV1,
    @JsonProperty(START_LOCATION) val startLocation: ExtAddressPointV1?,
    @JsonProperty(END_LOCATION) val endLocation: ExtAddressPointV1?,
)

@Schema(name = "Vastaus: Ratanumero")
data class ExtTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Muutettu ratanumero")
data class ExtModifiedTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Ratanumerogeometria")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackNumberGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVAL) val trackInterval: ExtCenterLineTrackIntervalV1?,
)

@Schema(name = "Vastaus: Muutettu ratanumerogeometria")
data class ExtTrackNumberModifiedGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Ratanumerokokoelma")
data class ExtTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)

@Schema(name = "Vastaus: Muutettu ratanumerokokoelma")
data class ExtModifiedTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)
