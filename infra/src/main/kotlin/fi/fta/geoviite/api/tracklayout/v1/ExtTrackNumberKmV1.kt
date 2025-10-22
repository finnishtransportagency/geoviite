package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(name = "Ratanumeron kilometrit")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackNumberKmsV1(
    @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @JsonProperty(TRACK_NUMBER_OID) val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty(TRACK_KMS) val trackKms: List<ExtTrackKmV1>,
)

@Schema(name = "Ratakilometri")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackKmV1(
    @JsonProperty(TYPE) val type: ExtTrackKmTypeV1,
    @JsonProperty(KM_NUMBER) val kmNumber: KmNumber,
    @JsonProperty(KM_START_M) val startM: BigDecimal,
    @JsonProperty(KM_END_M) val endM: BigDecimal,
    @JsonProperty(OFFICIAL_LOCATION) val officialLocation: ExtSridCoordinateV1?,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtCoordinateV1,
) {
    @JsonProperty(KM_LENGTH) val kmLength = endM - startM
}

@Schema(name = "Vastaus: Ratanumeron ratakilometrit")
data class ExtTrackKmsResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_KMS) val trackNumberKms: ExtTrackNumberKmsV1,
)

@Schema(name = "Vastaus: Ratanumeroiden ratakilometrit")
data class ExtTrackKmsCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_KMS_COLLECTION) val trackNumberKms: List<ExtTrackNumberKmsV1>,
)
