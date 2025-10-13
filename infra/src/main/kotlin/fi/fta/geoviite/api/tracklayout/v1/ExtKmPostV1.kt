package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(name = "Tasakilometripiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtKmPostV1(
    @JsonProperty(KM_POST_OID) val kmPostOid: Oid<LayoutKmPost>,
    @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(KM_NUMBER) val kmNumber: KmNumber,
    @JsonProperty(STATE) val state: ExtKmPostStateV1,
    @JsonProperty(KM_LENGTH) val kmLength: BigDecimal?,
    @JsonProperty(OFFICIAL_LOCATION) val officialLocation: ExtSridCoordinateV1,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtCoordinateV1,
)

@Schema(name = "Vastaus: Tasakilometripiste")
data class ExtKmPostResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(KM_POST) val kmPost: ExtKmPostV1,
)

@Schema(name = "Vastaus: Muutettu tasakilometripiste")
data class ExtModifiedKmPostResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(KM_POST) val kmPost: ExtKmPostV1,
)

@Schema(name = "Vastaus: Tasakilometripistekokoelma")
data class ExtKmPostCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(KM_POST_COLLECTION) val kmPostCollection: List<ExtKmPostV1>,
)

@Schema(name = "Vastaus: Muutettu tasakilometripistekokoelma")
data class ExtModifiedKmPostCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION_FROM) val trackLayoutVersionFrom: Uuid<Publication>,
    @JsonProperty(TRACK_LAYOUT_VERSION_TO) val trackLayoutVersionTo: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(KM_POST_COLLECTION) val kmPostCollection: List<ExtKmPostV1>,
)
