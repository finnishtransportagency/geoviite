package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.tracklayout.AlignmentPoint
import fi.fta.geoviite.infra.tracklayout.AnyM
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.SegmentPoint
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

fun factorial(n: Int): Long {
    if (n < 0) throw IllegalArgumentException("Can't do factorial for $n (<0)")
    return if (n == 0) 1
    else
        (1..n).fold(1L) { acc, i ->
            val result = acc * i
            if (result / i != acc) throw IllegalArgumentException("Integer overflow in calculating factorial for $n")
            result
        }
}

fun roundTo1Decimal(value: Double): BigDecimal = round(value, 1)

fun roundTo3Decimals(value: Double): BigDecimal = round(value, 3)

fun roundTo3Decimals(value: LineM<*>): BigDecimal = round(value.distance, 3)

fun roundTo3Decimals(value: BigDecimal): BigDecimal = round(value, 3)

fun roundTo6Decimals(value: Double): BigDecimal = round(value, 6)

fun round(value: Double, scale: Int): BigDecimal =
    if (!value.isFinite()) throw IllegalArgumentException("Cannot round $value")
    else round(BigDecimal.valueOf(value), scale)

fun round(value: LineM<*>, scale: Int) = round(value.distance, scale)

fun round(value: BigDecimal, scale: Int): BigDecimal = value.setScale(scale, RoundingMode.HALF_UP)

fun interpolate(value1: Double?, value2: Double?, portion: Double) =
    if (value1 == null || value2 == null) null else interpolate(value1, value2, portion)

fun interpolate(value1: Double, value2: Double, portion: Double): Double =
    if (value1.isFinite() && value2.isFinite()) value1 + (value2 - value1) * portion
    else throw IllegalArgumentException("Cannot interpolate between $value1 and $value2")

fun <M : AnyM<M>> interpolate(value1: LineM<M>, value2: LineM<M>, portion: Double): LineM<M> =
    LineM(interpolate(value1.distance, value2.distance, portion))

fun interpolateToPoint(value1: IPoint, value2: IPoint, portion: Double): Point =
    Point(x = interpolate(value1.x, value2.x, portion), y = interpolate(value1.y, value2.y, portion))

fun interpolateToSegmentPoint(value1: SegmentPoint, value2: SegmentPoint, portion: Double) =
    SegmentPoint(
        x = interpolate(value1.x, value2.x, portion),
        y = interpolate(value1.y, value2.y, portion),
        z = interpolate(value1.z, value2.z, portion),
        m = interpolate(value1.m, value2.m, portion),
        cant = interpolate(value1.cant, value2.cant, portion),
    )

fun <M : AnyM<M>> interpolateToAlignmentPoint(value1: AlignmentPoint<M>, value2: AlignmentPoint<M>, portion: Double) =
    AlignmentPoint(
        x = interpolate(value1.x, value2.x, portion),
        y = interpolate(value1.y, value2.y, portion),
        z = interpolate(value1.z, value2.z, portion),
        m = interpolate(value1.m, value2.m, portion),
        cant = interpolate(value1.cant, value2.cant, portion),
    )

fun isSame(value1: Double?, value2: Double?, delta: Double) =
    (value1 == null && value2 == null) || (value1 != null && value2 != null && isSame(value1, value2, delta))

fun isSame(value1: Double, value2: Double, delta: Double) = abs(value1 - value2) < delta

fun <M : AnyM<M>> isSame(value1: LineM<M>?, value2: LineM<M>?, delta: Double) =
    isSame(value1?.distance, value2?.distance, delta)
