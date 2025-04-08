package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonProperty
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.util.FreeText
import io.swagger.v3.oas.annotations.media.Schema
import java.math.BigDecimal

const val MODIFICATIONS_FROM_VERSION = "muutokset_versiosta"
const val TRACK_NETWORK_VERSION = "rataverkon_versio"

data class ExtLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty("sijaintiraide") val locationTrack: ExtLocationTrackV1,
)

data class ExtModifiedLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>?,
    @JsonProperty("sijaintiraide") val locationTrack: ExtLocationTrackV1,
)

data class ExtLocationTrackV1(
    @JsonProperty("ratanumero") val trackNumberName: TrackNumber,
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("oid") val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    @JsonProperty("tyyppi") val locationTrackType: ExtLocationTrackTypeV1,
    @JsonProperty("tila") val locationTrackState: ExtLocationTrackStateV1,
    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    @JsonProperty("alkusijainti") val startLocation: ExtCenterLineGeometryPointV1,
    @JsonProperty("loppusijainti") val endLocation: ExtCenterLineGeometryPointV1,
    @JsonProperty("koordinaatisto") val coordinateSystem: Srid,
)

data class ExtLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Muutettu sijaintiraidegeometria")
data class ExtModifiedLocationTrackGeometryResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>?,
    @JsonProperty("osoitevalit") val trackIntervals: List<ExtCenterLineTrackIntervalV1>,
)

@Schema(name = "Osoitepiste")
data class ExtCenterLineGeometryPointV1( // TODO Rename
    val x: Double,
    val y: Double,
    @JsonProperty("ratakilometri") val kmNumber: KmNumber,
    @JsonProperty("ratametri") val trackMeter: BigDecimal,
) {
    companion object {
        fun of(addressPoint: AddressPoint): ExtCenterLineGeometryPointV1 {
            return ExtCenterLineGeometryPointV1(
                addressPoint.point.x,
                addressPoint.point.y,
                addressPoint.address.kmNumber,
                addressPoint.address.meters,
            )
        }
    }
}
