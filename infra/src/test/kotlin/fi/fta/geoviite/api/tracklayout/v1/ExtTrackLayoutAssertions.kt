package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import org.junit.jupiter.api.Assertions.assertEquals

fun assertExtStartAndEnd(
    expectedStart: Point,
    expectedEnd: Point,
    responseTrack: ExtTestLocationTrackV1,
) {

    assertEquals(expectedStart.x, requireNotNull(responseTrack.alkusijainti?.x), 0.0001)
    assertEquals(expectedStart.y, requireNotNull(responseTrack.alkusijainti.y), 0.0001)
    assertEquals(
        expectedEnd.x,
        requireNotNull(responseTrack.loppusijainti?.x),
        0.0001,
    )
    assertEquals(
        expectedEnd.y,
        requireNotNull(responseTrack.loppusijainti.y),
        0.0001,
    )
}

fun assertExtLocationTrackState(expectedState: LocationTrackState, stateName: String) {
    val expectedStateName =
        when (expectedState) {
            LocationTrackState.IN_USE -> "käytössä"
            LocationTrackState.BUILT -> "rakennettu"
            LocationTrackState.NOT_IN_USE -> "käytöstä poistettu"
            LocationTrackState.DELETED -> "poistettu"
        }

    assertEquals(expectedStateName, stateName)
}
