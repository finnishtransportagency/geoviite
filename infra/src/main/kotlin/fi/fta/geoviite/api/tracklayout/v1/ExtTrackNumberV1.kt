package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Ratanumero")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackNumberV1(
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @JsonProperty(DESCRIPTION) val trackNumberDescription: TrackNumberDescription,
    @JsonProperty(STATE) val trackNumberState: ExtTrackNumberStateV1,
    @JsonProperty(START_LOCATION) val startLocation: ExtAddressPointV1?,
    @JsonProperty(END_LOCATION) val endLocation: ExtAddressPointV1?,
)

@Schema(name = "Vastaus: Ratanumero")
data class ExtTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Muutettu ratanumero")
data class ExtModifiedTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Ratanumerogeometria")
data class ExtTrackNumberGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Muutettu ratanumerogeometria")
data class ExtTrackNumberkModifiedGeometryResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(TRACK_INTERVALS) val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Vastaus: Ratanumerokokoelma")
data class ExtTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)

@Schema(name = "Vastaus: Muutettu ratanumerokokoelma")
data class ExtModifiedTrackNumberCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_COLLECTION) val trackNumberCollection: List<ExtTrackNumberV1>,
)
