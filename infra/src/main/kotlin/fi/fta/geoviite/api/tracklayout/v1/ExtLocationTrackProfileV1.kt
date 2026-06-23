package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(title = "Vastaus: Sijaintiraiteen pystygeometria")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackProfileResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVAL) val trackInterval: ExtProfileAddressRangeV1,
)

@Schema(title = "Vastaus: Muutettu sijaintiraiteen pystygeometria")
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

@Schema(title = "Pystygeometriaolio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileAddressRangeV1(
    @JsonProperty(INTERVAL_START) val start: String,
    @JsonProperty(INTERVAL_END) val end: String,
    @JsonProperty(INTERSECTION_POINTS) val intersectionPoints: List<ExtProfilePviPointV1>,
)

@Schema(title = "Taitepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfilePviPointV1(
    @JsonProperty(CURVED_SECTION_START) val curvedSectionStart: ExtProfileCurvedSectionEndpointV1,
    @JsonProperty(INTERSECTION_POINT) val intersectionPoint: ExtProfileIntersectionPointV1,
    @JsonProperty(CURVED_SECTION_END) val curvedSectionEnd: ExtProfileCurvedSectionEndpointV1,
    @Schema(description = "Pyöristyksen säde etumerkillä: Negatiivinen säde kääntyy alaspäin, positiivinen ylöspäin")
    @JsonProperty(ROUNDING_RADIUS)
    val roundingRadius: BigDecimal,
    @Schema(description = "Tangenttipisteiden etäisyys taitepisteestä") @JsonProperty(TANGENT) val tangent: BigDecimal,
    @JsonProperty(LINEAR_SECTION_BACKWARD) val linearSectionBackward: ExtProfileLinearSectionV1,
    @JsonProperty(LINEAR_SECTION_FORWARD) val linearSectionForward: ExtProfileLinearSectionV1,
    @JsonProperty(STATION_VALUES) val stationValues: ExtProfileStationValuesV1,
    @JsonProperty(PLAN_VERTICAL_COORDINATE_SYSTEM) val planVerticalCoordinateSystem: ExtVerticalCoordinateSystemV1?,
    @JsonProperty(PLAN_ELEVATION_MEASUREMENT_METHOD)
    val planElevationMeasurementMethod: ExtElevationMeasurementMethodV1?,
    @JsonProperty(REMARKS) val remarks: List<ExtProfileRemarkV1>,
)

@Schema(title = "Pyöristyksen alku-/loppupiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileCurvedSectionEndpointV1(
    @Schema(description = "Raiteen korkeus tangenttipisteen kohdalla suunnitelman pystykoordinaattijärjestelmässä")
    @JsonProperty(HEIGHT_ORIGINAL)
    val heightOriginal: BigDecimal,
    @Schema(description = "Raiteen korkeus tangenttipisteen kohdalla")
    @JsonProperty(HEIGHT_N2000)
    val heightN2000: BigDecimal?,
    @Schema(description = "Pituuskaltevuus desimaalilukuna (kulmakerroin), etumerkillä")
    @JsonProperty(GRADIENT)
    val gradient: BigDecimal?,
    @Schema(description = "Sijainti paikannuspohjassa")
    @JsonProperty(COORDINATE_LOCATION)
    val location: ExtAddressPointV1?,
)

@Schema(title = "Taitepiste (leikkauspiste)")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileIntersectionPointV1(
    @Schema(
        description =
            "Taitepisteen korkeus (huom. ei raiteen korkeus taitepisteen kohdalla) suunnitelman pystykoordinaattijärjestelmässä"
    )
    @JsonProperty(HEIGHT_ORIGINAL)
    val heightOriginal: BigDecimal,
    @Schema(description = "Taitepisteen korkeus (huom. ei raiteen korkeus taitepisteen kohdalla)")
    @JsonProperty(HEIGHT_N2000)
    val heightN2000: BigDecimal?,
    @Schema(description = "Sijainti paikannuspohjassa")
    @JsonProperty(COORDINATE_LOCATION)
    val location: ExtAddressPointV1,
)

@Schema(title = "Kaltevuusjakso")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileLinearSectionV1(
    @Schema(
        description =
            "Etäisyys pystygeometriaprofiilin päätyyn, tai seuraavaan taitepisteeseen, suunnitelman pystygeometriassa"
    )
    @JsonProperty(SECTION_LENGTH)
    val length: BigDecimal?,
    @Schema(description = "Kaltevuusjakson suoran osuuden pituus suunnitelman pystygeometriassa")
    @JsonProperty(LINEAR_PART_LENGTH)
    val linearPartLength: BigDecimal?,
)

@Schema(title = "Paaluluku")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileStationValuesV1(
    @Schema(
        description = "Paaluluku paikannuspohjan raiteella. Raiteen ulkopuolelle osuvan pisteen paaluluku jää tyhjäksi."
    )
    @JsonProperty(INTERVAL_START)
    val start: BigDecimal?,
    @Schema(
        description = "Paaluluku paikannuspohjan raiteella. Raiteen ulkopuolelle osuvan pisteen paaluluku jää tyhjäksi."
    )
    @JsonProperty(INTERSECTION_POINT)
    val intersectionPoint: BigDecimal?,
    @Schema(
        description = "Paaluluku paikannuspohjan raiteella. Raiteen ulkopuolelle osuvan pisteen paaluluku jää tyhjäksi."
    )
    @JsonProperty(INTERVAL_END)
    val end: BigDecimal?,
)

@Schema(title = "Huomio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtProfileRemarkV1(
    @JsonProperty(REMARK_CODE) val code: String,
    @JsonProperty(REMARK_DESCRIPTION) val description: String,
)
