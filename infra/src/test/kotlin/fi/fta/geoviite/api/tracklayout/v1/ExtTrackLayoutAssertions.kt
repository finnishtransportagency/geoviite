package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals

fun assertExtStartAndEnd(
    expectedStart: Point,
    expectedEnd: Point,
    responseStart: ExtTestAddressPointV1,
    responseEnd: ExtTestAddressPointV1,
) {
    assertEquals(expectedStart.x, responseStart.x, 0.0001)
    assertEquals(expectedStart.y, responseStart.y, 0.0001)
    assertEquals(expectedEnd.x, responseEnd.x, 0.0001)
    assertEquals(expectedEnd.y, responseEnd.y, 0.0001)
}

fun assertExtLayoutState(expectedState: LayoutState, stateName: String) {
    val expectedStateName =
        when (expectedState) {
            LayoutState.IN_USE -> "käytössä"
            LayoutState.NOT_IN_USE -> "käytöstä poistettu"
            LayoutState.DELETED -> "poistettu"
        }

    assertEquals(expectedStateName, stateName)
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

fun assertGeometryIntervalAddressResolution(
    interval: ExtTestGeometryIntervalV1,
    resolution: BigDecimal,
    startM: Double,
    endM: Double,
) {
    val delta = 0.0001

    assertEquals(startM, TrackMeter(interval.alku.rataosoite!!).meters.toDouble(), delta)
    assertEquals(endM, TrackMeter(interval.loppu.rataosoite!!).meters.toDouble(), delta)

    interval.pisteet.forEachIndexed { i, point ->
        // Middle points should be exactly divisible by resolution
        assertEquals(0.0, (TrackMeter(point.rataosoite!!).meters % resolution).toDouble(), delta)
    }
}
