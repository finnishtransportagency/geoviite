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

const val MODIFICATIONS_FROM_VERSION = "muutokset_versiosta"
const val TRACK_NETWORK_VERSION = "rataverkon_versio"

abstract class TrackNetworkResponseV1 {
    abstract val trackNetworkVersion: Uuid<Publication>
}

data class FullTrackNetworkResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) override val trackNetworkVersion: Uuid<Publication>
) : TrackNetworkResponseV1()

data class ModifiedTrackNetworkResponseV1(
    override val trackNetworkVersion: Uuid<Publication>,
    val modificationsFromVersion: Uuid<Publication>?,
) : TrackNetworkResponseV1()

data class LocationTrackRequestV1(
    val locationTrackOid: ApiRequestStringV1,
    val trackNetworkVersion: ApiRequestStringV1?,
    val modificationsFromVersion: ApiRequestStringV1?,
    val coordinateSystem: ApiRequestStringV1?,
)

data class ValidLocationTrackRequestV1(
    val locationTrackOid: ApiRequestStringV1,
    val trackNetworkVersion: ApiRequestStringV1,
    val modificationsFromVersion: Uuid<Publication>?,
    val coordinateSystem: ApiRequestStringV1,
)

data class TrackNetworkLocationTrackResponseV1(
    @JsonProperty(TRACK_NETWORK_VERSION) val trackNetworkVersion: Uuid<Publication>,
    @JsonProperty(MODIFICATIONS_FROM_VERSION) val modificationsFromVersion: Uuid<Publication>?,
    @JsonProperty("sijaintiraide") val locationTrack: LocationTrackResponseV1,
)

abstract class LocationTrackResponseV1

// Empty class so that the field exists in the returned JSON, but without any data.
class UnmodifiedLocationTrackResponseV1 : LocationTrackResponseV1()

data class FullLocationTrackResponseV1(
    @JsonProperty("ratanumero") val trackNumberName: TrackNumber,
    @JsonProperty("ratanumero_oid") val trackNumberOid: Oid<LayoutTrackNumber>,
    @JsonProperty("oid") val locationTrackOid: Oid<LocationTrack>,
    @JsonProperty("sijaintiraidetunnus") val locationTrackName: AlignmentName,
    @JsonProperty("tyyppi") val locationTrackType: ApiLocationTrackType,
    @JsonProperty("tila") val locationTrackState: ApiLocationTrackState,
    @JsonProperty("kuvaus") val locationTrackDescription: FreeText,
    @JsonProperty("omistaja") val locationTrackOwner: MetaDataName,
    @JsonProperty("alkusijainti") val startLocation: CenterLineGeometryPointV1,
    @JsonProperty("loppusijainti") val endLocation: CenterLineGeometryPointV1,
    @JsonProperty("koordinaatisto") val coordinateSystem: Srid,
) : LocationTrackResponseV1()
