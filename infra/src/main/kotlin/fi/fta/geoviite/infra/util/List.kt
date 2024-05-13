package fi.fta.geoviite.infra.util

//Nulls are "last", e.g., 0, 1, 2, null
fun <T : Comparable<T>> nullsLastComparator(a: T?, b: T?) =
    if (a == null && b == null) 0
    else if (a == null) 1
    else if (b == null) -1
    else a.compareTo(b)

fun <T : Comparable<T>> nullsFirstComparator(a: T?, b: T?) =
    if (a == null && b == null) 0
    else if (a == null) -1
    else if (b == null) 1
    else a.compareTo(b)

fun rangesOfConsecutiveIndicesOf(
    value: Boolean,
    ts: List<Boolean>,
    offsetRangeEndsBy: Int = 0,
): List<ClosedRange<Int>> =
    sequence { yield(!value); yieldAll(ts.asSequence()); yield(!value) }
        .zipWithNext()
        .mapIndexedNotNull { i, (a, b) -> if (a != b) i else null }
        .chunked(2)
        .map { c -> c[0]..c[1] + offsetRangeEndsBy }
        .toList()

fun <T> List<T>.conditionalFilter(condition: Boolean, predicate: (T) -> Boolean): List<T> {
    return if (condition) this.filter(predicate) else this
}

fun <T, R : Comparable<R>> List<T>.conditionalSortedBy(condition: Boolean, selector: (T) -> R?): List<T> {
    return if (condition) this.sortedBy(selector) else this
}
