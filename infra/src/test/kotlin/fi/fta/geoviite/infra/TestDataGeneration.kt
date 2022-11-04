package fi.fta.geoviite.infra

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import kotlin.math.PI
import kotlin.math.abs

inline fun <reified T : Enum<T>> getSomeValue(index: Int): T {
    val values = enumValues<T>()
    return values[index % values.size]
}

inline fun <reified T : Enum<T>> getSomeNullableValue(index: Int): T? {
    val values: List<T?> = enumValues<T>().toList() + null
    return values[index % values.size]
}

fun <T> getSomeOid(seed: Int): Oid<T> = Oid<T>(
    "${abs(seed % 1000)}.${abs(seed * 2 % 1000)}.${abs(seed * 3 % 1000)}"
)

//UI requires OID with 5 segments
fun <T> getSomeOid2(seed: Int): Oid<T> = Oid<T>(
    "${abs(seed % 1000)}.${abs(seed * 2 % 1000)}.${abs(seed * 3 % 1000)}.${abs(seed * 4 % 1000)}.${abs(seed * 5 % 1000)}"
)

fun segmentsToCsv(segments: List<LayoutSegment>) {
    segments.forEachIndexed { index, segment ->
        val points = segment.points.joinToString(",") { p -> "${p.x} ${p.y}" }
        println("$index,\"LINESTRING($points)\"")
    }
}

fun smoothOut(points: List<Point>, limit: Double = PI / 32): List<Point> {
    val result = points.flatMapIndexed { index, point ->
        val previous = points.getOrNull(index - 1)
        val next = points.getOrNull(index + 1)
        if (previous == null || next == null) {
            listOf(point)
        } else {
            val prevAngle = directionBetweenPoints(previous, point)
            val nextAngle = directionBetweenPoints(point, next)
            if (angleDiffRads(prevAngle, nextAngle) > limit) {
                listOf(
                    pointInDirection(previous, 2 * lineLength(previous, point) / 3, prevAngle),
                    pointInDirection(point, lineLength(point, next) / 3, nextAngle)
                )
            } else {
                listOf(point)
            }
        }
    }
    return if (result != points) smoothOut(result) else result
}
