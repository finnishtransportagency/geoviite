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
    @Schema(description = "Geometriaelementin tyyppi") @JsonProperty(TYPE) val type: ExtElementTypeV1,
    @Schema(description = "Elementin alkupiste sijaintiraiteen paikannuspohjassa")
    @JsonProperty(ELEMENT_LOCATION_START)
    val locationStart: ExtAddressPointV1,
    @Schema(description = "Elementin loppupiste sijaintiraiteen paikannuspohjassa")
    @JsonProperty(ELEMENT_LOCATION_END)
    val locationEnd: ExtAddressPointV1,
    @Schema(description = "Elementin pituus metreinä") @JsonProperty(ELEMENT_LENGTH) val length: BigDecimal,
    @Schema(description = "Viite lähdesuunnitelmaan, null jos segmentti ei ole linkitetty suunnitelmaan")
    @JsonProperty(PLAN_REFERENCE)
    val plan: ExtGeometryPlanReferenceV1?,
    @Schema(description = "Kaarresäde elementin alussa ja lopussa")
    @JsonProperty(ELEMENT_RADIUS)
    val radius: ExtElementRadiusV1?,
    @Schema(description = "Kallistus elementin alussa ja lopussa")
    @JsonProperty(ELEMENT_CANT)
    val cant: ExtElementCantV1?,
    @Schema(description = "Suuntakulma elementin alussa ja lopussa (gooni)")
    @JsonProperty(ELEMENT_DIRECTION)
    val direction: ExtElementDirectionV1,
    @Schema(description = "Elementtiin liittyvät huomiot") @JsonProperty(REMARKS) val notes: List<ExtElementNoteV1>,
)

@Schema(title = "Suunnitelman koordinaattiviite")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtGeometryPlanReferenceV1(
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: String?,
    @JsonProperty(ELEMENT_LOCATION_START) val locationStart: ExtPlanCoordinateV1,
    @JsonProperty(ELEMENT_LOCATION_END) val locationEnd: ExtPlanCoordinateV1,
)

@Schema(title = "Suunnitelman koordinaatti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtPlanCoordinateV1(val x: Double, val y: Double)

@Schema(title = "Kaarresäde")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtElementRadiusV1(
    @Schema(description = "Kaarresäde elementin alussa (metriä)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal?,
    @Schema(description = "Kaarresäde elementin lopussa (metriä)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal?,
)

@Schema(title = "Kallistus")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtElementCantV1(
    @Schema(description = "Kallistus elementin alussa (millimetriä)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal?,
    @Schema(description = "Kallistus elementin lopussa (millimetriä)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal?,
)

@Schema(title = "Suuntakulma")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtElementDirectionV1(
    @Schema(description = "Suuntakulma elementin alussa (gooni)")
    @JsonProperty(ELEMENT_START_VALUE)
    val startValue: BigDecimal,
    @Schema(description = "Suuntakulma elementin lopussa (gooni)")
    @JsonProperty(ELEMENT_END_VALUE)
    val endValue: BigDecimal,
)

@Schema(title = "Elementtihuomio")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtElementNoteV1(
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
enum class ExtElementTypeV1(val value: String) {
    LINE(FI_SUORA),
    CURVE(FI_KAARI),
    CLOTHOID(FI_SIIRTYMAKAARI),
    BIQUADRATIC_PARABOLA(FI_SIIRTYMAKAARI_HELMERT),
    MISSING_SECTION(FI_EI_ELEMENTTIA);

    @JsonValue override fun toString() = value

    companion object {
        fun of(type: TrackGeometryElementType): ExtElementTypeV1 =
            when (type) {
                TrackGeometryElementType.LINE -> LINE
                TrackGeometryElementType.CURVE -> CURVE
                TrackGeometryElementType.CLOTHOID -> CLOTHOID
                TrackGeometryElementType.BIQUADRATIC_PARABOLA -> BIQUADRATIC_PARABOLA
                TrackGeometryElementType.MISSING_SECTION -> MISSING_SECTION
            }
    }
}
