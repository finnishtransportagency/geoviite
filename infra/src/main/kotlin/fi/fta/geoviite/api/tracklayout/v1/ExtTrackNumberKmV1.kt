package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

@Schema(name = "Ratanumeron ratakilometrit")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackNumberKmsV1(
    @Schema(type = "string", example = "001") @JsonProperty(TRACK_NUMBER) val trackNumber: TrackNumber,
    @Schema(example = "1.2.246.578.13.123.456")
    @JsonProperty(TRACK_NUMBER_OID)
    val trackNumberOid: ExtOidV1<LayoutTrackNumber>,
    @JsonProperty(TRACK_KMS) val trackKms: List<ExtTrackKmV1>,
)

@Schema(name = "Ratakilometri")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtTrackKmV1(
    @JsonProperty(TYPE) val type: ExtTrackKmTypeV1,
    @Schema(type = "string", format = "km-number", example = "0012") @JsonProperty(KM_NUMBER) val kmNumber: KmNumber,
    @Schema(type = "string", format = "decimal", example = "1234.123") @JsonProperty(KM_START_M) val startM: BigDecimal,
    @Schema(type = "string", format = "decimal", example = "2345.456") @JsonProperty(KM_END_M) val endM: BigDecimal,
    @JsonProperty(OFFICIAL_LOCATION) val officialLocation: ExtKmPostOfficialLocationV1?,
    @JsonProperty(COORDINATE_LOCATION) val location: ExtCoordinateV1,
) {
    @Schema(type = "number", format = "decimal", example = "1111.333")
    @JsonProperty(KM_LENGTH)
    val kmLength = endM - startM
}

@Schema(name = "Vastaus: Ratanumeron ratakilometrit")
data class ExtTrackKmsResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER_KMS) val trackNumberKms: ExtTrackNumberKmsV1,
)

@Schema(name = "Vastaus: Ratanumerokokoelman ratakilometrit")
data class ExtTrackKmsCollectionResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: ExtLayoutVersionV1,
    @JsonProperty(COORDINATE_SYSTEM) val coordinateSystem: ExtSridV1,
    @JsonProperty(TRACK_NUMBER_KMS_COLLECTION) val trackNumberKms: List<ExtTrackNumberKmsV1>,
)
