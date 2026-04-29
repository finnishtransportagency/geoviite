package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(name = "Vastaus: Sijaintiraiteen pystygeometria")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackProfileResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVAL) val trackInterval: ExtProfileAddressRangeV1,
)

@Schema(name = "Vastaus: Muutettu sijaintiraiteen pystygeometria")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackModifiedProfileResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtProfileAddressRangeV1>,
)

@Schema(name = "Pystygeometriaolio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileAddressRangeV1(
    @JsonProperty(INTERVAL_START) val start: String?,
    @JsonProperty(INTERVAL_END) val end: String?,
    @JsonProperty(INTERSECTION_POINTS) val intersectionPoints: List<ExtProfilePviPointV1>,
)

@Schema(name = "Taitepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfilePviPointV1(
    @JsonProperty(CURVED_SECTION_START) val curvedSectionStart: ExtProfileCurvedSectionEndpointV1,
    @JsonProperty(INTERSECTION_POINT) val intersectionPoint: ExtProfileIntersectionPointV1,
    @JsonProperty(CURVED_SECTION_END) val curvedSectionEnd: ExtProfileCurvedSectionEndpointV1,
    @JsonProperty(ROUNDING_RADIUS) val roundingRadius: BigDecimal,
    @JsonProperty(TANGENT) val tangent: BigDecimal?,
    @JsonProperty(LINEAR_SECTION_BACKWARD) val linearSectionBackward: ExtProfileLinearSectionV1,
    @JsonProperty(LINEAR_SECTION_FORWARD) val linearSectionForward: ExtProfileLinearSectionV1,
    @JsonProperty(STATION_VALUES) val stationValues: ExtProfileStationValuesV1,
    @Suppress("NonAsciiCharacters")
    @JsonProperty(PLAN_VERTICAL_COORDINATE_SYSTEM)
    val planVerticalCoordinateSystem: String?,
    @JsonProperty(PLAN_ELEVATION_MEASUREMENT_METHOD) val planElevationMeasurementMethod: String?,
    @JsonProperty(REMARKS) val remarks: List<ExtProfileRemarkV1>,
)

@Schema(name = "Pyöristyksen alku-/loppupiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileCurvedSectionEndpointV1(
    @Suppress("NonAsciiCharacters") @JsonProperty(HEIGHT_ORIGINAL) val heightOriginal: BigDecimal,
    @JsonProperty(HEIGHT_N2000) val heightN2000: BigDecimal?,
    @JsonProperty(GRADIENT) val gradient: BigDecimal,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtProfileLocationV1,
)

@Schema(name = "Taitepiste (leikkauspiste)")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileIntersectionPointV1(
    @Suppress("NonAsciiCharacters") @JsonProperty(HEIGHT_ORIGINAL) val heightOriginal: BigDecimal,
    @JsonProperty(HEIGHT_N2000) val heightN2000: BigDecimal?,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtProfileLocationV1,
)

@Schema(name = "Sijainti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileLocationV1(
    @JsonProperty(TRACK_ADDRESS) val trackAddress: String?,
    @JsonProperty("x") val x: BigDecimal?,
    @JsonProperty("y") val y: BigDecimal?,
)

@Schema(name = "Kaltevuusjakso")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileLinearSectionV1(
    @JsonProperty(SECTION_LENGTH) val length: BigDecimal?,
    @JsonProperty(LINEAR_PART_LENGTH) val linearPartLength: BigDecimal?,
)

@Schema(name = "Paaluluku")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileStationValuesV1(
    @JsonProperty(INTERVAL_START) val start: BigDecimal?,
    @JsonProperty(INTERSECTION_POINT) val intersectionPoint: BigDecimal?,
    @JsonProperty(INTERVAL_END) val end: BigDecimal?,
)

@Schema(name = "Huomio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileRemarkV1(
    @JsonProperty(REMARK_CODE) val code: String,
    @JsonProperty(REMARK_DESCRIPTION) val description: String,
)
