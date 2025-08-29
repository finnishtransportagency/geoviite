import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.api.tracklayout.v1.COORDINATE_SYSTEM_PARAM
import fi.fta.geoviite.api.tracklayout.v1.ExtAddressPointV1
import fi.fta.geoviite.api.tracklayout.v1.ExtTrackNumberStateV1
import fi.fta.geoviite.api.tracklayout.v1.MODIFICATIONS_FROM_VERSION
import fi.fta.geoviite.api.tracklayout.v1.TRACK_LAYOUT_VERSION
import fi.fta.geoviite.api.tracklayout.v1.TRACK_NUMBER_PARAM
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Vastaus: Ratanumero")
data class ExtTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_PARAM) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Vastaus: Muutettu ratanumero")
data class ExtModifiedTrackNumberResponseV1(
    @JsonProperty(TRACK_LAYOUT_VERSION) val trackLayoutVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>,
    @JsonProperty(COORDINATE_SYSTEM_PARAM) val coordinateSystem: Srid,
    @JsonProperty(TRACK_NUMBER_PARAM) val trackNumber: ExtTrackNumberV1,
)

@Schema(name = "Ratanumero")
data class ExtTrackNumberV1(
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("ratanumero") val trackNumber: TrackNumber,
    @JsonProperty("kuvaus") val trackNumberDescription: TrackNumberDescription,
    @JsonProperty("tila") val trackNumberState: ExtTrackNumberStateV1,
    @JsonProperty("alkusijainti") val startLocation: ExtAddressPointV1?,
    @JsonProperty("loppusijainti") val endLocation: ExtAddressPointV1?,
)
