package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointAbbreviation
import fi.fta.geoviite.infra.tracklayout.OperationalPointName
import fi.fta.geoviite.infra.tracklayout.UicCode
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Toiminnallisen pisteen sijaintiraide")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtOperationalPointTrackV1(
    @Schema(example = "1.2.246.578.3.10002.189984")
    @JsonProperty(LOCATION_TRACK_OID)
    val locationTrackOid: ExtOidV1<LocationTrack>,
)

@Schema(name = "Toiminnallisen pisteen vaihde")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtOperationalPointSwitchV1(
    @Schema(example = "1.2.246.578.3.10002.189984")
    @JsonProperty(SWITCH_OID)
    val switchOid: ExtOidV1<LayoutSwitch>,
)

@Schema(name = "Toiminnallisen pisteen Rato-tyyppi")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtOperationalPointRatoTypeV1(
    @Schema(example = "LPO") @JsonProperty(CODE) val code: String,
    @Schema(example = "Liikennepaikan osa") @JsonProperty(TYPE_DESCRIPTION) val description: String,
)

@Schema(name = "Toiminnallisen pisteen RINF-tyyppi")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtOperationalPointRinfTypeV1(
    @Schema(example = "10") @JsonProperty(CODE) val code: String,
    @Schema(example = "Station") @JsonProperty(RINF_TYPE_DESCRIPTION) val description: String,
)

@Schema(name = "Toiminnallinen piste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtOperationalPointV1(
    @Schema(example = "1.2.246.578.3.139.310520")
    @JsonProperty(OPERATIONAL_POINT_OID)
    val operationalPointOid: ExtOidV1<OperationalPoint>,
    @Schema(example = "FI0000012345") @JsonProperty(RINF_ID) val rinfId: String?,
    @Schema(type = "string", example = "Pasila asema") @JsonProperty(NAME) val name: OperationalPointName,
    @Schema(type = "string", example = "Psl")
    @JsonProperty(ABBREVIATION)
    val abbreviation: OperationalPointAbbreviation?,
    @JsonProperty(STATE) val state: ExtOperationalPointStateV1,
    @JsonProperty(SOURCE) val source: ExtOperationalPointOriginV1,
    @JsonProperty(TYPE_RATO) val ratoType: ExtOperationalPointRatoTypeV1?,
    @JsonProperty(TYPE_RINF) val rinfType: ExtOperationalPointRinfTypeV1?,
    @Schema(example = "10") @JsonProperty(UIC_CODE) val uicCode: UicCode?,
    @JsonProperty(LOCATION) val location: ExtCoordinateV1?,
    @JsonProperty(TRACKS) val tracks: List<ExtOperationalPointTrackV1>,
    @JsonProperty(SWITCHES) val switches: List<ExtOperationalPointSwitchV1>,
    @JsonProperty(AREA) val area: ExtPolygonV1?,
)

@Schema(name = "Vastaus: Toiminnallinen piste")
data class ExtOperationalPointResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(OPERATIONAL_POINT) val operationalPoint: ExtOperationalPointV1,
)

@Schema(name = "Vastaus: Muutettu toiminnallinen piste")
data class ExtModifiedOperationalPointResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(OPERATIONAL_POINT) val operationalPoint: ExtOperationalPointV1,
)

@Schema(name = "Vastaus: Toiminnallisten pisteiden kokoelma")
data class ExtOperationalPointCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val layoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(OPERATIONAL_POINT_COLLECTION) val operationalPointCollection: List<ExtOperationalPointV1>,
)

@Schema(name = "Vastaus: Muutettu toiminnallisten pisteiden kokoelma")
data class ExtModifiedOperationalPointCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val layoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val layoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(OPERATIONAL_POINT_COLLECTION) val operationalPointCollection: List<ExtOperationalPointV1>,
)
