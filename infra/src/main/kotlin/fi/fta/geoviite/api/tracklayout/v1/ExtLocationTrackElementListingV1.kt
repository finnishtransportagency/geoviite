package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.geometry.TrackGeometryElementType
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(title = "Vastaus: Sijaintiraiteen geometriaelementit")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackElementListingResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(OFFICIAL_LOCATION_TRACK_OID)
    val officialLocationTrackOid: ExtOidV1<LocationTrack>?,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtElementAddressIntervalV1>,
)

@Schema(title = "Vastaus: Sijaintiraiteen geometriaelementtien muutokset")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtLocationTrackElementListingModificationsResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty(OFFICIAL_LOCATION_TRACK_OID)
    val officialLocationTrackOid: ExtOidV1<LocationTrack>?,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtElementAddressIntervalV1>,
)

@Schema(title = "Geometriaelementtien osoiteväli")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtElementAddressIntervalV1(
    @JsonProperty(INTERVAL_START) val start: String,
    @JsonProperty(INTERVAL_END) val end: String,
    @JsonProperty(GEOMETRY_ELEMENTS) val elements: List<ExtGeometryElementV1>,
)

@Schema(title = "Geometriaelementti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryElementV1(
    @Schema(description = "Geometriaelementin tyyppi") @JsonProperty(TYPE) val type: ExtGeometryElementTypeV1,
    @Schema(description = "Elementin alkupiste pyydetyssä koordinaattijärjestelmässä")
    @JsonProperty(GEOMETRY_ELEMENT_LOCATION_START)
    val locationStart: ExtAddressPointV1,
    @Schema(description = "Elementin loppupiste pyydetyssä koordinaattijärjestelmässä")
    @JsonProperty(GEOMETRY_ELEMENT_LOCATION_END)
    val locationEnd: ExtAddressPointV1,
    @Schema(description = "Elementin pituus metreinä") @JsonProperty(GEOMETRY_ELEMENT_LENGTH) val length: BigDecimal,
    @Schema(
        description =
            "Koordinaatit suunnitelman alkuperäisessä järjestelmässä, null jos segmentti ei ole linkitetty suunnitelmaan"
    )
    @JsonProperty(PLAN_REFERENCE)
    val planCoordinates: ExtGeometryPlanCoordinatesV1?,
    @Schema(description = "Kaarresäde elementin alussa ja lopussa")
    @JsonProperty(GEOMETRY_ELEMENT_RADIUS)
    val radius: ExtGeometryElementRadiusV1?,
    @Schema(description = "Kallistus elementin alussa ja lopussa")
    @JsonProperty(GEOMETRY_ELEMENT_CANT)
    val cant: ExtGeometryElementCantV1?,
    @Schema(description = "Suuntakulma elementin alussa ja lopussa (gooni)")
    @JsonProperty(GEOMETRY_ELEMENT_DIRECTION)
    val direction: ExtGeometryElementDirectionV1,
    @Schema(description = "Elementtiin liittyvät huomiot")
    @JsonProperty(REMARKS)
    val notes: List<ExtGeometryElementNoteV1>,
)

@Schema(title = "Koordinaatit suunnitelman alkuperäisessä järjestelmässä")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryPlanCoordinatesV1(
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: String?,
    @JsonProperty(GEOMETRY_ELEMENT_LOCATION_START) val locationStart: ExtCoordinateV1,
    @JsonProperty(GEOMETRY_ELEMENT_LOCATION_END) val locationEnd: ExtCoordinateV1,
)

@Schema(title = "Geometriaelementin kaarresäde")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryElementRadiusV1(
    @Schema(description = "Kaarresäde elementin alussa (metriä)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal?,
    @Schema(description = "Kaarresäde elementin lopussa (metriä)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal?,
)

@Schema(title = "Geometriaelementin kallistus")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryElementCantV1(
    @Schema(description = "Kallistus elementin alussa (millimetriä)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal?,
    @Schema(description = "Kallistus elementin lopussa (millimetriä)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal?,
)

@Schema(title = "Geometriaelementin suuntakulma")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryElementDirectionV1(
    @Schema(description = "Suuntakulma elementin alussa (gooni, arvoalue 0-400, 0 = pohjoinen)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal,
    @Schema(description = "Suuntakulma elementin lopussa (gooni, arvoalue 0-400, 0 = pohjoinen)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal,
)

@Schema(title = "Geometriaelementin huomio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryElementNoteV1(
    @JsonProperty(REMARK_CODE) val code: String,
    @JsonProperty(REMARK_DESCRIPTION) val description: String,
)

const val FI_SUORA = "suora"
const val FI_KAARI = "kaari"
const val FI_SIIRTYMAKAARI = "siirtymakaari"
const val FI_SIIRTYMAKAARI_HELMERT = "siirtymakaari_helmert"
const val FI_EI_ELEMENTTIA = "ei_elementtia"

@Schema(
    title = "Geometriaelementin tyyppi",
    type = "string",
    allowableValues = [FI_SUORA, FI_KAARI, FI_SIIRTYMAKAARI, FI_SIIRTYMAKAARI_HELMERT, FI_EI_ELEMENTTIA],
)
enum class ExtGeometryElementTypeV1(val value: String) {
    LINE(FI_SUORA),
    CURVE(FI_KAARI),
    CLOTHOID(FI_SIIRTYMAKAARI),
    BIQUADRATIC_PARABOLA(FI_SIIRTYMAKAARI_HELMERT),
    MISSING_SECTION(FI_EI_ELEMENTTIA);

    @JsonValue override fun toString() = value

    companion object {
        fun of(type: TrackGeometryElementType): ExtGeometryElementTypeV1 =
            when (type) {
                TrackGeometryElementType.LINE -> LINE
                TrackGeometryElementType.CURVE -> CURVE
                TrackGeometryElementType.CLOTHOID -> CLOTHOID
                TrackGeometryElementType.BIQUADRATIC_PARABOLA -> BIQUADRATIC_PARABOLA
                TrackGeometryElementType.MISSING_SECTION -> MISSING_SECTION
            }
    }
}
