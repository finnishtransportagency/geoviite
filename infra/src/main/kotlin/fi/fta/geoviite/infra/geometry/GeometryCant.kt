package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.RotationDirection
import fi.fta.geoviite.infra.geometry.CantTransitionType.BIQUADRATIC_PARABOLA
import fi.fta.geoviite.infra.geometry.CantTransitionType.LINEAR
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.biquadraticSTransition
import java.math.BigDecimal

enum class CantRotationPoint {
    LEFT,
    RIGHT,
    INSIDE_RAIL,
    CENTER,
}

enum class CantTransitionType {
    LINEAR,
    BIQUADRATIC_PARABOLA,
}

data class GeometryCant(
    val name: PlanElementName,
    val description: PlanElementName,
    val gauge: BigDecimal,
    val rotationPoint: CantRotationPoint?,
    val points: List<GeometryCantPoint>,
) {
    init {
        if (name.isBlank()) throw IllegalArgumentException("Cant should have a non-blank name")
    }

    fun getCantValue(station: Double): Double? {
        var next = points.find { cp -> cp.station.toDouble() >= station }
        var previous = points.findLast { cp -> cp.station.toDouble() <= station }
        next = next ?: previous
        previous = previous ?: next
        return if (next != null && previous != null) calculateGeometryPointCantValue(previous, next, station) else null
    }
}

fun calculateGeometryPointCantValue(previous: GeometryCantPoint, next: GeometryCantPoint, station: Double): Double {
    val offset = station - previous.station.toDouble()
    val lengthBtwStations: Double = (next.station - previous.station).toDouble()
    val cantTotalDelta = (next.appliedCant - previous.appliedCant).toDouble()
    val cantDelta =
        if (lengthBtwStations <= 0.0) 0.0
        else
            when (previous.transitionType) {
                LINEAR -> cantTotalDelta * (offset / lengthBtwStations)
                BIQUADRATIC_PARABOLA -> biquadraticSTransition(offset, cantTotalDelta, lengthBtwStations)
            }
    return previous.appliedCant.toDouble() + cantDelta
}

data class GeometryCantPoint(
    val station: BigDecimal,
    val appliedCant: BigDecimal,
    val curvature: RotationDirection,
    val transitionType: CantTransitionType,
)
