package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.RotationDirection.CCW
import fi.fta.geoviite.infra.geometry.CantRotationPoint.INSIDE_RAIL
import fi.fta.geoviite.infra.geometry.CantTransitionType.BIQUADRATIC_PARABOLA
import fi.fta.geoviite.infra.geometry.CantTransitionType.LINEAR
import fi.fta.geoviite.infra.inframodel.PlanElementName
import java.math.BigDecimal
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GeometryCantTest {

    @Test
    fun shouldReturnNullIfAlignmentHasNoCantStations() {
        val dummyCant = createCant(listOf())
        val stationLength = 1166.108648
        assertEquals(null, dummyCant.getCantValue(stationLength))
    }

    @Test
    fun shouldReturnLastValueIfStationIsGreaterThanStationPoints() {
        val dummyCant =
            createCant(
                listOf(
                    createCantPoint(station = 509.117345, cant = 0.046),
                    createCantPoint(station = 539.117345, cant = 0.0),
                )
            )
        val stationLength = 1166.108648
        assertEquals(0.00, dummyCant.getCantValue(stationLength))
    }

    @Test
    fun shouldReturnFirstValueIfStationIsLessThanStationPoints() {
        val dummyCant =
            createCant(
                listOf(
                    createCantPoint(station = 509.117345, cant = 0.046),
                    createCantPoint(station = 539.117345, cant = 0.0),
                )
            )
        val stationLength = 500.0
        assertEquals(0.046, dummyCant.getCantValue(stationLength))
    }

    @Test
    fun biquadraticSCurveCantsMakeSense() {
        val cant =
            createCant(
                listOf(
                    createCantPoint(station = 100.0, cant = 0.0, transitionType = BIQUADRATIC_PARABOLA),
                    createCantPoint(station = 200.0, cant = 0.2),
                )
            )
        assertEquals(0.0, cant.getCantValue(100.0)!!, 0.000001)
        assertEquals(0.1, cant.getCantValue(150.0)!!, 0.000001)
        assertEquals(0.2, cant.getCantValue(200.0)!!, 0.000001)

        // First half should grow slower than than linear
        val beforeHalfCant = cant.getCantValue(125.0)!!
        assertTrue(beforeHalfCant > 0.0, "Expected: $beforeHalfCant > 0.0")
        assertTrue(beforeHalfCant < 0.05, "Expected: $beforeHalfCant < 0.05")

        // Second half should grow faster than linear
        val afterHalfCant = cant.getCantValue(175.0)!!
        assertTrue(afterHalfCant > 0.15, "Expected: $afterHalfCant > 0.0")
        assertTrue(afterHalfCant < 0.2, "Expected: $afterHalfCant < 0.05")
    }

    @Test
    fun shouldReturnCantIfCurrentStationLengthIsBetweenCantStations() {
        val firstCantPoint = createCantPoint(station = 509.117345, cant = 0.046)
        val secondCantPoint = createCantPoint(station = 539.117345, cant = 0.0)
        val station = 532.234724
        assertEquals(0.010553, calculateGeometryPointCantValue(firstCantPoint, secondCantPoint, station), 0.000001)
    }

    private fun createCant(cantPoints: List<GeometryCantPoint>): GeometryCant =
        GeometryCant(
            name = PlanElementName("dummyCantObject"),
            description = PlanElementName("for testing purposes"),
            gauge = BigDecimal("1.524"),
            rotationPoint = INSIDE_RAIL,
            points = cantPoints,
        )

    private fun createCantPoint(station: Double, cant: Double, transitionType: CantTransitionType = LINEAR) =
        GeometryCantPoint(
            station = station.toBigDecimal(),
            appliedCant = cant.toBigDecimal(),
            curvature = CCW,
            transitionType = transitionType,
        )
}
