package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Sijaintiraide")
data class ExtLocationTrackV1(
    @JsonProperty(LOCATION_TRACK_OID) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty(LOCATION_TRACK_NAME) val locationTrackName: AlignmentName,
    @JsonProperty(TRACK_NUMBER) val trackNumberName: TrackNumber,
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(TYPE) val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty(STATE) val locationTrackState: ExtLocationTrackStateV1,
    @JsonProperty(DESCRIPTION) val locationTrackDescription: FreeText,
    @JsonProperty(OWNER) val locationTrackOwner: MetaDataName,
    @JsonProperty(START_LOCATION) val startLocation: ExtAddressPointV1?,
    @JsonProperty(END_LOCATION) val endLocation: ExtAddressPointV1?,
)

@Schema(name = "Vastaus: Sijaintiraide")
data class ExtLocationTrackResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Muutettu sijaintiraide")
data class ExtModifiedLocationTrackResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(LOCATION_TRACK) val locationTrack: ExtLocationTrackV1,
)

@Schema(name = "Vastaus: Sijaintiraidegeometria")
data class ExtLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(LOCATION_TRACK_OID) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu sijaintiraidegeometria")
data class ExtLocationTrackModifiedGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(LOCATION_TRACK_OID) val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)
