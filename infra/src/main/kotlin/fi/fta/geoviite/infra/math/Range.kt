package fi.fta.geoviite.infra.math

data class Range<T : Comparable<T>>(val min: T, val max: T) {
    constructor(range: ClosedFloatingPointRange<T>) : this(range.start, range.endInclusive)

    init {
        if (min > max) throw IllegalArgumentException("Range min cannot be greater than max: min=$min max=$max")
    }

    fun contains(value: T) = value in min..max
    fun overlaps(other: Range<T>) = min <= other.max && max >= other.min
}

fun <T : Comparable<T>> combine(ranges: List<Range<T>>): Range<T> =
    ranges.reduceRight { r, acc -> Range(minOf(r.min, acc.min), maxOf(r.max, acc.max)) }

fun <T : Comparable<T>> maxNonNull(o1: T?, o2: T?): T? =
    if (o1 == null) o2
    else if (o2 == null) o1
    else if (o1 > o2) o1
    else o2

fun <T : Comparable<T>> minNonNull(o1: T?, o2: T?): T? =
    if (o1 == null) o2
    else if (o2 == null) o1
    else if (o1 < o2) o1
    else o2
