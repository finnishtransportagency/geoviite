package fi.fta.geoviite.infra.math

import java.math.BigDecimal
import kotlin.math.*
import kotlin.math.IEEErem

enum class AngularUnit {
    RADIANS,
    GRADS,
}

fun degreesToRads(degrees: Double): Double = degrees * (PI / 180)

fun radsToDegrees(rads: Double): Double = rads / (PI / 180)

fun gradsToRads(grads: Double): Double = PI * grads / 200

fun radsToGrads(rads: Double): Double = 200 * rads / PI

/**
 * Direction in the center of 2 given directions (given in rads), on the side of the smaller angle. Returns normalized
 * angles in the range [-PI, PI).
 */
fun angleAvgRads(rads1: Double, rads2: Double): Double = interpolateAngleRads(rads1, rads2, 0.5)

/**
 * Interpolate between two angles, on the side of the smaller angle. Returns normalized angles in the range [-PI, PI).
 */
fun interpolateAngleRads(rads1: Double, rads2: Double, proportion: Double) =
    rotateAngle(rads1, normalizeDirectionRads(rads2 - rads1) * proportion)

/**
 * Checks if an angle (agnostic of unit) is inside the closed range [start,end], noting that the angle can flip
 * (indicated by end being less than start)
 */
fun angleIsBetween(start: Double, end: Double, value: Double): Boolean =
    if (end < start) !(value > end && value < start) else value in start..end

/**
 * The absolute difference between 2 angles, as rads, on in closed range [0,PI]. For all a and b, angleDiffRads(a, b) =
 * angleDiffRads(b, a)
 */
fun angleDiffRads(rads1: Double, rads2: Double): Double = abs(relativeAngle(rads1, rads2))

/**
 * Relative angle, in the half-closed range [-PI,PI). For all a and b, relativeAngle(a, b) = -relativeAngle(b, a),
 * except when the difference is exactly PI.
 */
fun relativeAngle(rads1: Double, rads2: Double): Double = normalizeDirectionRads(rads2 - rads1)

/** Rotates given angle around the origin by delta, looping around to give result at half-closed range [-PI,PI) */
fun rotateAngle(rads: Double, delta: Double): Double = normalizeDirectionRads(rads + delta)

/**
 * Converts math angle (rads) to geodetic angle:
 * - Math: [-PI,PI) counter-clockwise with 0 at positive X
 * - Geodetic: [0,2*PI) clockwise with 0 at positive Y
 */
fun radsMathToGeo(rads: Double) = normalizeGeodeticRads(0.5 * PI - rads)

/**
 * Converts geodetic angle (rads) to math angle:
 * - Math: [-PI,PI) counter-clockwise with 0 at positive X
 * - Geodetic: [0,2*PI) clockwise with 0 at positive Y
 */
fun radsGeoToMath(rads: Double) = normalizeDirectionRads(0.5 * PI - rads)

/** Normalize radian angle value into the half-closed range [-PI,PI) */
private fun normalizeDirectionRads(rads: Double): Double =
    rads.IEEErem(PI * 2.0).let { if (it < -PI) it + PI * 2.0 else it }.let { if (it == PI) -PI else it }

/** Normalize radian angle value into the half-closed range [0,2*PI) */
private fun normalizeGeodeticRads(rads: Double): Double = fract(rads / (PI * 2.0)) * (PI * 2.0)

private fun fract(x: Double) = x - floor(x)

fun radsToAngle(rads: Double, unit: AngularUnit): Angle {
    return when (unit) {
        AngularUnit.RADIANS -> Rads(BigDecimal.valueOf(rads))
        AngularUnit.GRADS -> Grads(BigDecimal.valueOf(radsToGrads(rads)))
    }
}

fun toAngle(value: BigDecimal, unit: AngularUnit): Angle {
    return when (unit) {
        AngularUnit.RADIANS -> Rads(value)
        AngularUnit.GRADS -> Grads(value)
    }
}

sealed class Angle {
    abstract val original: BigDecimal
    abstract val rads: Double

    /**
     * Turns an angle from typical geodetic representation (0=North & growing clockwise) into a typical math
     * representation (0=positive-X & growing counter-clockwise).
     */
    abstract fun geoToMath(): Angle

    /**
     * Turns an angle from typical math representation (0=positive-X & growing counter-clockwise) into a typical
     * geodetic representation (0=North & growing clockwise).
     */
    abstract fun mathToGeo(): Angle
}

data class Rads(override val original: BigDecimal) : Angle() {
    override val rads: Double by lazy { original.toDouble() }

    override fun geoToMath(): Angle {
        return Rads((0.5 * PI).toBigDecimal().minus(original))
    }

    override fun mathToGeo(): Angle {
        return Rads((0.5 * PI).toBigDecimal().minus(original))
    }
}

data class Grads(override val original: BigDecimal) : Angle() {
    override val rads: Double by lazy { gradsToRads(original.toDouble()) }

    override fun geoToMath(): Angle {
        return Grads(BigDecimal.valueOf(100).minus(original))
    }

    override fun mathToGeo(): Angle {
        return Grads(BigDecimal.valueOf(100).minus(original))
    }
}
