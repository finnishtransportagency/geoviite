package fi.fta.geoviite.infra.math

import kotlin.math.*

fun circleArcAngle(radius: Double, chord: Double): Double {
    // Derived from law of cosines:
    // c = sqrt(a^2+b^2 - 2ab cos(alpha))
    // a = radius, b = radius, c = chord -> alpha = angle
    return acos(1 - (chord.pow(2) / (2 * radius.pow(2))))
}

fun circleArcLength(radius: Double, chord: Double): Double {
    val diameter = 2 * radius
    return diameter * asin(chord / diameter)
}

fun circleArcLength(radius: Double): Double {
    return 2.0 * PI * radius
}

fun circleSubArcLength(radius: Double, angle: Double): Double {
    return angle * radius
}

/**
 * Calculates Y-coordinate corresponding given X-coordinate for a point on the circe arc. If radius is positive, the
 * center is assumed to be above the arc -> use lower half of the arc (negative Y). If radius is negative, the center is
 * assumed to be below the arc -> use upper half of the arc (positive Y).
 */
fun circleArcYAtX(center: Point, radius: Double, x: Double): Double {
    val absRadius = abs(radius)
    if (x in (center.x - absRadius)..(center.x + absRadius)) {
        val angleFromCenter = asin((x - center.x) / abs(radius))
        return center.y - cos(angleFromCenter) * radius
    } else {
        throw IllegalArgumentException("X outside of circe arc")
    }
}
