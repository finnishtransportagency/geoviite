package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema

const val ROUTING = "reititys"
const val ROUTE = "reitti"
const val ROUTE_PARTS = "reitin_osat"
const val ROUTE_PART_START = "alku"
const val ROUTE_PART_END = "loppu"
const val ROUTE_PART_DIRECTION = "suunta"
const val ROUTE_PART_LENGTH = "pituus"
const val ROUTE_TOTAL_LENGTH = "pituus"
const val ROUTE_ENDPOINT_TYPE = "tyyppi"
const val ROUTE_ENDPOINT_M_VALUE = "m_arvo"
const val START_X = "sijainti_alku_x"
const val START_Y = "sijainti_alku_y"
const val END_X = "sijainti_loppu_x"
const val END_Y = "sijainti_loppu_y"

enum class ExtRouteEndpointTypeV1 {
    @JsonProperty("sijainti_raiteella") SIJAINTI_RAITEELLA,
    @JsonProperty("vaihde") VAIHDE,
    @JsonProperty("raiteen_pää") RAITEEN_PAA,
}

enum class ExtRouteDirectionV1 {
    @JsonProperty("nouseva") NOUSEVA,
    @JsonProperty("laskeva") LASKEVA,
}

@Schema(title = "Reitin osan päätepisteen tiedot")
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExtRouteSectionEndpointV1(
    @JsonProperty(ROUTE_ENDPOINT_TYPE) val tyyppi: ExtRouteEndpointTypeV1,
    @Schema(example = "1.2.246.578.3.117.197939") @JsonProperty(SWITCH_OID) val vaihdeOid: ExtOidV1<LayoutSwitch>?,
    @Schema(type = "string", example = "0385+0909.031") @JsonProperty(TRACK_ADDRESS) val rataosoite: String?,
    val x: Double,
    val y: Double,
    @get:JsonProperty(ROUTE_ENDPOINT_M_VALUE) val mArvo: Double,
)

@Schema(title = "Reitin osa")
data class ExtRouteSectionV1(
    @Schema(example = "1.2.246.578.3.10002.190119")
    @JsonProperty(LOCATION_TRACK_OID)
    val sijaintiraideOid: ExtOidV1<LocationTrack>,
    @Schema(example = "1.2.246.578.3.10001.188968")
    @JsonProperty(TRACK_NUMBER_OID)
    val ratanumeroOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(ROUTE_PART_START) val alku: ExtRouteSectionEndpointV1,
    @JsonProperty(ROUTE_PART_END) val loppu: ExtRouteSectionEndpointV1,
    @JsonProperty(ROUTE_PART_DIRECTION) val suunta: ExtRouteDirectionV1,
    @JsonProperty(ROUTE_PART_LENGTH) val pituus: Double,
)

@Schema(title = "Reitti")
data class ExtRouteV1(
    @JsonProperty(ROUTE_TOTAL_LENGTH) val pituus: Double,
    @JsonProperty(ROUTE_PARTS) val reitinOsat: List<ExtRouteSectionV1>,
)

@Schema(title = "Vastaus: Reititys")
data class ExtRouteResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val rataverkonVersio: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val koordinaatisto: ExtSridV1,
    @JsonProperty(ROUTE) val reitti: ExtRouteV1,
)
