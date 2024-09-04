package fi.fta.geoviite.infra.math

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.pow

/**
 * Clothoid x/y estimations are done by estimating the integral via Fresnel functions:
 *
 * C(theta) = 1/1! - theta^2/2!*5 + theta^4/4!*9 - ... + (-1)^k*theta^(2k) / (2k)!*(4k+1) S(theta) = theta/1!*3 -
 * theta^3/3!*7 + theta^5/5!*11 - ... + (-1)^k*theta^(2k+1) / (2k+1)!*(4k+3)
 *
 * References:
 * <ul>
 * <li>http://precismultipla.altervista.org/ESU2/chap08.htm</li>
 * <li>https://pwayblog.com/2016/07/03/the-clothoid/</li>
 * </ul>
 */
private const val MAX_ITERATIONS = 6
private const val ACCURACY = 0.0000001 // About 1mm on 10km long clothoid segment

/** Precalculated static divisor elements for Fresnel C function (x-coordinate). Indexed by iteration (k: 0-based) */
private val fresnelDivisorsC: LongArray by lazy {
    (0..MAX_ITERATIONS).map { k -> fresnelTermSign(k) * fresnelCDivisor(k) }.toLongArray()
}

/** Precalculated static divisor elements for Fresnel S function (y-coordinate). Indexed by iteration (k: 0-based) */
private val fresnelDivisorsS: LongArray by lazy {
    (0..MAX_ITERATIONS).map { k -> fresnelTermSign(k) * fresnelSDivisor(k) }.toLongArray()
}

/**
 * Calculates the length of a clothoid
 *
 * @param constantA spiral flatness value (A = sqrt(R*L))
 * @param radiusStart curvature circle radius (R) for the starting point of the spiral segment
 * @param radiusEnd curvature circle radius (R) for the ending point of the spiral segment
 */
fun clothoidLength(constantA: Double, radiusStart: Double?, radiusEnd: Double?): Double {
    return abs(clothoidLengthAtRadius(constantA, radiusEnd) - clothoidLengthAtRadius(constantA, radiusStart))
}

/**
 * Calculates the length of a clothoid segment from start (R=INF) to a given radius value
 *
 * @param constantA spiral flatness value (A = sqrt(R*L))
 * @param radius curvature circle radius (R) for the ending point of the spiral segment
 */
fun clothoidLengthAtRadius(constantA: Double, radius: Double?): Double {
    return if (radius == null) 0.0 else constantA.pow(2.0) / radius
}

/**
 * Calculates the curvature radius of a clothoid segment at given distance from the origin
 *
 * @param constantA spiral flatness value (A = sqrt(R*L))
 * @param distance distance (L) from the spiral beginning (R=INF)
 */
fun clothoidRadiusAtLength(constantA: Double, distance: Double): Double {
    return constantA.pow(2.0) / distance
}

/**
 * Calculates the angle of the clothoid curvature at a given point along the curve
 *
 * @param radius spiral circle radius (R) at point L
 * @param length distance along the clothoid from the origin (L)
 */
fun clothoidTwistAtLength(radius: Double?, length: Double): Double {
    return if (radius != null && radius > 0.0 && length > 0.0) length / (2 * radius) else 0.0
}

/**
 * Uses the Fresnel C & S functions to approximate (X,Y) unit-value (0-1) offset
 *
 * @param constantA spiral flatness value (A = sqrt(R*L))
 * @param offset distance along the spiral, starting from origin curve from the start (R=INF)
 */
fun clothoidPointAtOffset(constantA: Double, offset: Double): Point {
    val theta = theta(constantA, offset)

    var iteration = 0
    var resultX = 0.0
    var resultY = 0.0

    do {
        val diffX = fresnelC(iteration, theta)
        val diffY = fresnelS(iteration, theta)

        resultX += diffX
        resultY += diffY

        iteration++
    } while (hypot(diffX, diffY) >= ACCURACY && iteration <= MAX_ITERATIONS)

    return Point(resultX * offset, resultY * offset)
}

/**
 * Calculates the theta-value for fresnel C & S functions
 *
 * @param constantA spiral flatness value (A = sqrt(R*L))
 * @param offset distance along the spiral curve from the start (R=INF)
 */
private fun theta(constantA: Double, offset: Double): Double {
    return offset.pow(2) / (2 * constantA.pow(2))
}

/**
 * Fresnel C function single iteration. Summing the results from k=0.. approaches the precise mathematical value for x
 * offset from spiral origin.
 *
 * @param k iteration number, starting from 0 (up to $MAX_ITERATIONS)
 * @param theta theta value, calculated for curve & desired length
 */
private fun fresnelC(k: Int, theta: Double): Double {
    if (k > MAX_ITERATIONS) throw IllegalArgumentException("Can't calculate iteration $k: max=$MAX_ITERATIONS")
    return theta.pow(2 * k) / fresnelDivisorsC[k]
}

/**
 * Fresnel S function single iteration. Summing the results from k=0.. approaches the precise mathematical value for y
 * offset from spiral origin.
 *
 * @param k iteration number, starting from 0 (up to $MAX_ITERATIONS)
 * @param theta theta value, calculated for curve & desired length
 */
private fun fresnelS(k: Int, theta: Double): Double {
    if (k > MAX_ITERATIONS) throw IllegalArgumentException("Can't calculate iteration $k: max=$MAX_ITERATIONS")
    return theta.pow(2 * k + 1) / fresnelDivisorsS[k]
}

private fun fresnelCDivisor(k: Int): Long {
    return factorial(2 * k) * (4 * k + 1)
}

private fun fresnelSDivisor(k: Int): Long {
    return factorial(2 * k + 1) * (4 * k + 3)
}

private fun fresnelTermSign(k: Int): Int {
    return if (k % 2 == 0) 1 else -1
}
