package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Sijaintiraiteen hallinnollinen muutos")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtAdministrativeTrackChangeV1(
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(SOURCE_LOCATION_TRACK_OID)
    val sourceLocationTrackOid: ExtOidV1<LocationTrack>,
    @Schema(type = "string", example = "003")
    @JsonProperty(SOURCE_LOCATION_TRACK_NAME)
    val sourceLocationTrackName: AlignmentName,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TARGET_LOCATION_TRACK_OID)
    val targetLocationTrackOid: ExtOidV1<LocationTrack>,
    @Schema(type = "string", example = "003")
    @JsonProperty(TARGET_LOCATION_TRACK_NAME)
    val targetLocationTrackName: AlignmentName,
    @Schema(type = "string", example = "0012+0123.456") @JsonProperty("alkuosoite") val startAddress: String,
    @Schema(type = "string", example = "0012+0123.456") @JsonProperty("loppuosoite") val endAddress: String,
    @Schema(type = "string", example = "lisätty uutena raiteena") @JsonProperty(DESCRIPTION) val description: FreeText,
)

@Schema(name = "Sijaintiraiteen hallinnollinen muutos")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtAdministrativeTrackChangesV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: ExtLayoutVersionV1,
    @Schema(type = "string", example = "001") @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    val changeType: ExtAdministrativeTrackChangeTypeV1,
    @JsonProperty(CHANGE_COLLECTION) val changes: List<ExtAdministrativeTrackChangeV1>,
)

@Schema(name = "Vastaus: Sijaintiraiteen hallinnolliset muutokset")
data class ExtAdministrativeTrackChangeResponcseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: ExtLayoutVersionV1,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: ExtLayoutVersionV1,
    @JsonProperty(ADMINISTRATIVE_TRACK_CHANGES) val administrativeChanges: List<ExtAdministrativeTrackChangesV1>,
)
