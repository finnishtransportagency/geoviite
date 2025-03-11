package fi.fta.geoviite.api.tracklayout.v1

import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType

data class ApiLocationTrackType(val locationTrackType: LocationTrackType) {

    @JsonValue
    override fun toString(): String {
        return when (locationTrackType) {
            LocationTrackType.MAIN -> "pääraide"
            LocationTrackType.SIDE -> "sivuraide"
            LocationTrackType.TRAP -> "turvaraide"
            LocationTrackType.CHORD -> "kujaraide"
        }
    }
}

data class ApiLocationTrackState(val locationTrackState: LocationTrackState) {
    @JsonValue
    override fun toString(): String {
        return when (locationTrackState) {
            LocationTrackState.BUILT -> "rakennettu"
            LocationTrackState.IN_USE -> "käytössä"
            LocationTrackState.NOT_IN_USE -> "käytöstä poistettu"
            LocationTrackState.DELETED -> "poistettu"
        }
    }
}
