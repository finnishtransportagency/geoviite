package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.tracklayout.LayoutState
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

@Schema(name = "Osoitepiste")
@JsonInclude(JsonInclude.Include.ALWAYS)
data class ExtAddressPointV1(val x: Double, val y: Double, @JsonProperty("rataosoite") val trackAddress: String?) {

    constructor(x: Double, y: Double, address: TrackMeter?) : this(x, y, address?.formatFixedDecimals(3))

    constructor(point: AlignmentEndPoint) : this(point.point.x, point.point.y, point.address)
}

@Schema(name = "Osoiteväli")
data class ExtCenterLineTrackIntervalV1(
    @JsonProperty("alkuosoite") val startAddress: String,
    @JsonProperty("loppuosoite") val endAddress: String,
    @JsonProperty("pisteet") val addressPoints: List<ExtAddressPointV1>,
)
