package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.assertNotNull

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

    val responseStart = TrackMeter(interval.alkuosoite).meters.toDouble()
    val responseEnd = TrackMeter(interval.loppuosoite).meters.toDouble()

    assertEquals(startM, responseStart, delta)
    assertEquals(endM, responseEnd, delta)

    // Also check that the start/end points are included in the response's address point list as well.
    assertEquals(startM, interval.pisteet.first().rataosoite!!.let(::TrackMeter).meters.toDouble(), delta)
    assertEquals(endM, interval.pisteet.last().rataosoite!!.let(::TrackMeter).meters.toDouble(), delta)

    interval.pisteet.drop(1).dropLast(1).forEach { point ->
        // Middle points should be exactly divisible by resolution
        assertEquals(0.0, (TrackMeter(point.rataosoite!!).meters % resolution).toDouble(), delta)
    }
}

fun assertAddressRange(trackNumber: ExtTestTrackNumberV1, start: String, end: String) {
    assertEquals(start, trackNumber.alkusijainti?.rataosoite)
    assertEquals(end, trackNumber.loppusijainti?.rataosoite)
}

fun assertIntervalMatches(
    interval: ExtTestGeometryIntervalV1?,
    startAddress: String,
    endAddress: String,
    geometry: IAlignment<*>,
    pointCount: Int,
    start: IPoint = geometry.start!!,
    end: IPoint = geometry.end!!,
) {
    assertNotNull(interval, "Interval is null: expected=[$startAddress..$endAddress]")
    val getError = { field: String, expectedValue: Any? ->
        "Interval $field incorrect: expected=$expectedValue interval=[$startAddress..$endAddress]"
    }
    assertEquals(startAddress, interval.alkuosoite, getError("start address", startAddress))
    assertEquals(endAddress, interval.loppuosoite, getError("end address", endAddress))
    assertIntervalPointsMatch(interval.pisteet, startAddress, endAddress, geometry, pointCount, start, end, getError)
}

fun assertIntervalMatches(
    interval: ExtTestModifiedGeometryIntervalV1?,
    changeType: ExtGeometryChangeTypeV1,
    startAddress: String,
    endAddress: String,
    geometry: IAlignment<*>,
    pointCount: Int,
    start: IPoint = geometry.start!!,
    end: IPoint = geometry.end!!,
) {
    assertNotNull(interval, "Interval is null: expected=[$startAddress..$endAddress]")
    assertEquals(changeType.value, interval.muutostyyppi)
    val getError = { field: String, expectedValue: Any? ->
        "Interval $field incorrect: expected=$expectedValue interval=[$startAddress..$endAddress]"
    }
    assertEquals(startAddress, interval.alkuosoite, getError("start address", startAddress))
    assertEquals(endAddress, interval.loppuosoite, getError("end address", endAddress))
    assertIntervalPointsMatch(interval.pisteet, startAddress, endAddress, geometry, pointCount, start, end, getError)
}

fun assertIntervalPointsMatch(
    points: List<ExtTestAddressPointV1>,
    startAddress: String,
    endAddress: String,
    geometry: IAlignment<*>,
    pointCount: Int,
    start: IPoint,
    end: IPoint,
    getError: (field: String, expectedValue: Any?) -> String,
) {
    assertEquals(start.x, points.first().x, COORDINATE_DELTA, getError("start x coordinate", start.x))
    assertEquals(start.y, points.first().y, COORDINATE_DELTA, getError("start y coordinate", start.y))
    assertEquals(end.x, points.last().x, COORDINATE_DELTA, getError("end x coordinate", end.x))
    assertEquals(end.y, points.last().y, COORDINATE_DELTA, getError("end y coordinate", end.y))
    var previousAddress: TrackMeter? = null
    points.forEachIndexed { i, p ->
        val point = Point(p.x, p.y)
        val pointOnLine = geometry.getClosestPoint(point)!!.first
        assertEquals(
            0.0,
            lineLength(point, pointOnLine),
            COORDINATE_DELTA,
            getError("point $i distance to line geometry", 0.0),
        )
        assertNotNull(p.rataosoite, getError("point should have an address at index $i", "not null"))
        previousAddress =
            TrackMeter(p.rataosoite).also { address ->
                assertTrue(
                    address >= TrackMeter(startAddress),
                    getError("address before interval start at index $i", "$startAddress before $address"),
                )
                assertTrue(
                    address <= TrackMeter(endAddress),
                    getError("address after interval end at index $i", "$endAddress after $address"),
                )
                previousAddress?.also {
                    assertTrue(address > it, getError("address ordering at index $i", "$it before $address"))
                }
            }
    }
    assertEquals(pointCount, points.size, getError("point count", pointCount))
}

fun assertEmptyInterval(interval: ExtTestGeometryIntervalV1?, startAddress: String, endAddress: String) {
    assertNotNull(interval)
    assertEquals(startAddress, interval.alkuosoite)
    assertEquals(endAddress, interval.loppuosoite)
    assertTrue(interval.pisteet.isEmpty())
}

fun assertEmptyInterval(interval: ExtTestModifiedGeometryIntervalV1?, startAddress: String, endAddress: String) {
    assertNotNull(interval)
    assertEquals(startAddress, interval.alkuosoite)
    assertEquals(endAddress, interval.loppuosoite)
    assertTrue(interval.pisteet.isEmpty())
    assertEquals(ExtGeometryChangeTypeV1.GEOMETRY.value, interval.muutostyyppi)
}
