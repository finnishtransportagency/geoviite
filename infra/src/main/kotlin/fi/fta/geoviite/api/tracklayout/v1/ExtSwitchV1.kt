package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Vaihteen raidelinkki")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtSwitchTrackLinkV1(
    @JsonProperty(LOCATION_TRACK_OID) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty(SWITCH_JOINTS) val joints: List<ExtSwitchTrackJointV1>,
)

@Schema(name = "Vaihdepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtSwitchJointV1(
    @JsonProperty(SWITCH_JOINT_NUMBER) val jointNumber: Int,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtCoordinateV1,
)

@Schema(name = "Vaihdepiste raiteella")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtSwitchTrackJointV1(
    @JsonProperty(SWITCH_JOINT_NUMBER) val jointNumber: Int,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtAddressPointV1,
)

@Schema(name = "Vaihde")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtSwitchV1(
    @JsonProperty(SWITCH_OID) val switchOid: Oid<LayoutSwitch>,
    @JsonProperty(SWITCH_NAME) val switchName: SwitchName,
    @JsonProperty(TYPE) val type: SwitchType,
    @JsonProperty(SWITCH_HAND) val hand: ExtSwitchHandV1,
    @JsonProperty(PRESENTATION_JOINT_NUMBER) val presentationJointNumber: Int,
    @JsonProperty(STATE_CATEGORY) val stateCategory: ExtSwitchStateV1,
    @JsonProperty(OWNER) val owner: MetaDataName,
    @JsonProperty(TRAP_POINT) val trapPoint: ExtSwitchTrapPointV1,
    @JsonProperty(SWITCH_JOINTS) val switchJoints: List<ExtSwitchJointV1>,
    @JsonProperty(SWITCH_TRACK_LINKS) val trackLinks: List<ExtSwitchTrackLinkV1>,
)

@Schema(name = "Vastaus: Vaihde")
data class ExtSwitchResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(SWITCH) val switch: ExtSwitchV1,
)

@Schema(name = "Vastaus: Muutettu vaihde")
data class ExtModifiedSwitchResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(SWITCH) val switch: ExtSwitchV1,
)

@Schema(name = "Vastaus: Vaihdekokoelma")
data class ExtSwitchCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(SWITCH_COLLECTION) val switchCollection: List<ExtSwitchV1>,
)

@Schema(name = "Vastaus: Muutettu vaihdekokoelma")
data class ExtModifiedSwitchCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(SWITCH_COLLECTION) val switchCollection: List<ExtSwitchV1>,
)
