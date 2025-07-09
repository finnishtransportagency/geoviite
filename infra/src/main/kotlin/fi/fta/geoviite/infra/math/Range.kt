package fi.fta.geoviite.infra.math

data class Range<T : Comparable<T>>(val min: T, val max: T) {
    constructor(range: ClosedFloatingPointRange<T>) : this(range.start, range.endInclusive)

    init {
        require(min <= max) { "Range min cannot be greater than max: min=$min max=$max" }
    }

    fun contains(value: T) = value in min..max

    fun contains(other: Range<T>) = contains(other.min) && contains(other.max)

    fun overlaps(other: Range<T>) = min <= other.max && max >= other.min

    fun overlapsExclusive(other: Range<T>) = min < other.max && max > other.min

    fun intersection(other: Range<T>): Range<T>? {
        val minimum = if (min < other.min) other.min else min
        val maximum = if (max > other.max) other.max else max
        return if (minimum <= maximum) Range(minimum, maximum) else null
    }

    fun <R : Comparable<R>> map(f: (value: T) -> R): Range<R> = Range(f(min), f(max))
}

fun <T : Comparable<T>> combineContinuous(ranges: List<Range<T>>): List<Range<T>> {
    val result = mutableListOf<Range<T>>()
    var current: Range<T>? = null
    ranges.forEach { r ->
        current = current?.let { c -> if (r.min > c.max) r.also { result.add(c) } else combine(c, r) } ?: r
    }
    current?.let(result::add)
    return result
}

fun <T : Comparable<T>> combine(vararg ranges: Range<T>): Range<T> = combine(ranges.toList())

fun <T : Comparable<T>> combine(ranges: List<Range<T>>): Range<T> =
    ranges.reduceRight { r, acc -> Range(minOf(r.min, acc.min), maxOf(r.max, acc.max)) }

fun <T : Comparable<T>> maxNonNull(o1: T?, o2: T?): T? =
    if (o1 == null) o2 else if (o2 == null) o1 else if (o1 > o2) o1 else o2

fun <T : Comparable<T>> minNonNull(o1: T?, o2: T?): T? =
    if (o1 == null) o2 else if (o2 == null) o1 else if (o1 < o2) o1 else o2

fun minimumDistance(range1: Range<Double>, range2: Range<Double>): Double {
    val (min1, max1) = range1
    val (min2, max2) = range2

    return if (max2 < min1) {
        max2 - min1
    } else if (min2 > max1) {
        min2 - max1
    } else 0.0
}
