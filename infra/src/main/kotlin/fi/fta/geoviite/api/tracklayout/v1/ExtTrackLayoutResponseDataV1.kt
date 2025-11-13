package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.api.tracklayout.v1.ExtKmPostLocationConfirmedV1.CONFIRMED
import fi.fta.geoviite.api.tracklayout.v1.ExtKmPostLocationConfirmedV1.NOT_CONFIRMED
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostGkLocation
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(name = "Sijaintiraiteen tyyppi", type = "string")
enum class ExtLocationTrackTypeV1(val value: String) {
    MAIN("pääraide"),
    SIDE("sivuraide"),
    TRAP("turvaraide"),
    CHORD("kujaraide");

    @JsonValue override fun toString() = value

    companion object {
        fun of(locationTrackType: LocationTrackType): ExtLocationTrackTypeV1 {
            return when (locationTrackType) {
                LocationTrackType.MAIN -> MAIN
                LocationTrackType.SIDE -> SIDE
                LocationTrackType.TRAP -> TRAP
                LocationTrackType.CHORD -> CHORD
            }
        }
    }
}

@Schema(name = "Sijaintiraiteen tila", type = "string")
enum class ExtLocationTrackStateV1(val value: String) {
    BUILT("rakennettu"),
    IN_USE("käytössä"),
    NOT_IN_USE("käytöstä poistettu"),
    DELETED("poistettu");

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(locationTrackState: LocationTrackState): ExtLocationTrackStateV1 {
            return when (locationTrackState) {
                LocationTrackState.BUILT -> BUILT
                LocationTrackState.IN_USE -> IN_USE
                LocationTrackState.NOT_IN_USE -> NOT_IN_USE
                LocationTrackState.DELETED -> DELETED
            }
        }
    }
}

@Schema(name = "Ratanumeron tila", type = "string")
enum class ExtTrackNumberStateV1(val value: String) {
    IN_USE("käytössä"),
    NOT_IN_USE("käytöstä poistettu"),
    DELETED("poistettu");

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(trackNumberState: LayoutState): ExtTrackNumberStateV1 {
            return when (trackNumberState) {
                LayoutState.IN_USE -> IN_USE
                LayoutState.NOT_IN_USE -> NOT_IN_USE
                LayoutState.DELETED -> DELETED
            }
        }
    }
}

@Schema(name = "Vaihteen tilakategoria", type = "string")
enum class ExtSwitchStateV1(val value: String) {
    EXISTING("olemassaoleva kohde"),
    NOT_EXISTING("poistunut kohde");

    @JsonValue fun jsonValue() = value

    companion object {
        fun of(switchState: LayoutStateCategory): ExtSwitchStateV1 {
            return when (switchState) {
                LayoutStateCategory.EXISTING -> EXISTING
                LayoutStateCategory.NOT_EXISTING -> NOT_EXISTING
            }
        }
    }
}

@Schema(name = "Vaihteen kätisyys", type = "string")
enum class ExtSwitchHandV1(val value: String) {
    RIGHT("oikea"),
    LEFT("vasen"),
    NONE("ei määritelty");

    @JsonValue override fun toString() = value

    companion object {
        fun of(hand: SwitchHand): ExtSwitchHandV1 {
            return when (hand) {
                SwitchHand.RIGHT -> RIGHT
                SwitchHand.LEFT -> LEFT
                SwitchHand.NONE -> NONE
            }
        }
    }
}

@Schema(name = "Vaihteen turvavaihde status", type = "string")
enum class ExtSwitchTrapPointV1(val value: String) {
    YES("kyllä"),
    NO("ei"),
    UNKNOWN("ei tiedossa");

    @JsonValue override fun toString() = value

    companion object {
        fun of(isTrapPoint: Boolean?): ExtSwitchTrapPointV1 {
            return when (isTrapPoint) {
                null -> UNKNOWN
                true -> YES
                false -> NO
            }
        }
    }
}

@Schema(name = "Ratakilometrin tyyppi", type = "string")
enum class ExtTrackKmTypeV1(val value: String) {
    TRACK_NUMBER_START("ratanumeron alku"),
    KM_POST("tasakilometripiste");

    @JsonValue override fun toString() = value
}

@Schema(name = "Tasakilometripisteen virallisen sijainnin vahvistus", type = "string")
enum class ExtKmPostLocationConfirmedV1(val value: String) {
    CONFIRMED("vahvistettu"),
    NOT_CONFIRMED("ei vahvistettu");

    @JsonValue override fun toString() = value
}

@Schema(name = "Koordinaattisijainti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtCoordinateV1(val x: Double, val y: Double) {
    constructor(coordinate: IPoint) : this(coordinate.x, coordinate.y)
}

@Schema(name = "Tasakilometripisteen virallinen sijainti")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtKmPostOfficialLocationV1(
    val x: Double,
    val y: Double,
    @JsonProperty(COORDINATE_SYSTEM) val srid: Srid,
    @JsonProperty(OFFICIAL_LOCATION_CONFIRMED) val confirmed: ExtKmPostLocationConfirmedV1,
) {
    constructor(
        geometryPoint: GeometryPoint,
        confirmed: Boolean,
    ) : this(geometryPoint.x, geometryPoint.y, geometryPoint.srid, if (confirmed) CONFIRMED else NOT_CONFIRMED)

    constructor(location: LayoutKmPostGkLocation) : this(location.location, location.confirmed)
}

@Schema(name = "Osoitepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtAddressPointV1(val x: Double, val y: Double, @JsonProperty(TRACK_ADDRESS) val trackAddress: String?) {

    constructor(x: Double, y: Double, address: TrackMeter?) : this(x, y, address?.formatFixedDecimals(3))

    constructor(point: AlignmentEndPoint) : this(point.point.x, point.point.y, point.address)
}

@Schema(name = "Osoiteväli")
data class ExtCenterLineTrackIntervalV1(
    @JsonProperty("alkuosoite") val startAddress: String,
    @JsonProperty("loppuosoite") val endAddress: String,
    @JsonProperty("pisteet") val addressPoints: List<ExtAddressPointV1>,
)
