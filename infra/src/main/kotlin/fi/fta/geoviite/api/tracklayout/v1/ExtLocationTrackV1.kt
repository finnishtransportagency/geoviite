package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Sijaintiraide")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackV1(
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @Schema(type = "string", example = "003") @JsonProperty(LOCATION_TRACK_NAME) val locationTrackName: AlignmentName,
    @Schema(type = "string", example = "HKI 001") @JsonProperty(TRACK_NUMBER) val trackNumberName: TrackNumber,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(TYPE) val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty(STATE) val locationTrackState: ExtLocationTrackStateV1,
    @Schema(type = "string", example = "Helsinki raide: 001 V001 - Puskin")
    @JsonProperty(DESCRIPTION)
    val locationTrackDescription: FreeText,
    @Schema(type = "string", example = "Väylävirasto") @JsonProperty(OWNER) val locationTrackOwner: MetaDataName,
    @JsonProperty(START_LOCATION) val startLocation: ExtAddressPointV1?,
    @JsonProperty(END_LOCATION) val endLocation: ExtAddressPointV1?,
)

@Schema(name = "Vastaus: Sijaintiraide")
data class ExtLocationTrackResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(LOCATION_TRACK) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Muutettu sijaintiraide")
data class ExtModifiedLocationTrackResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(LOCATION_TRACK) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Sijaintiraidegeometria")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVAL) val trackInterval: ExtCenterLineTrackIntervalV1?,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidegeometria")
data class ExtLocationTrackModifiedGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtModifiedCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Sijaintiraidekokoelma")
data class ExtLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(LOCATION_TRACK_COLLECTION) val locationTrackCollection: List<ExtLocationTrackV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidekokoelma")
data class ExtModifiedLocationTrackCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(LOCATION_TRACK_COLLECTION) val locationTrackCollection: List<ExtLocationTrackV1>,
)
