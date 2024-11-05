package fi.fta.geoviite.infra.util

// Nulls are "last", e.g., 0, 1, 2, null
fun <T : Comparable<T>> nullsLastComparator(a: T?, b: T?) =
    if (a == null && b == null) 0 else if (a == null) 1 else if (b == null) -1 else a.compareTo(b)

fun <T : Comparable<T>> nullsFirstComparator(a: T?, b: T?) =
    if (a == null && b == null) 0 else if (a == null) -1 else if (b == null) 1 else a.compareTo(b)

fun rangesOfConsecutiveIndicesOf(
    value: Boolean,
    ts: List<Boolean>,
    offsetRangeEndsBy: Int = 0,
): List<ClosedRange<Int>> =
    sequence {
            yield(!value)
            yieldAll(ts.asSequence())
            yield(!value)
        }
        .zipWithNext()
        .mapIndexedNotNull { i, (a, b) -> if (a != b) i else null }
        .chunked(2)
        .map { c -> c[0] until c[1] + offsetRangeEndsBy }
        .toList()

fun <T> chunkBySizes(list: List<T>, sizes: List<Int>): List<List<T>> {
    val starts = sizes.scan(0) { acc, size -> acc + size }
    return sizes.zip(starts) { size, start -> list.subList(start, start + size) }
}

fun <T, R> processFlattened(lists: List<List<T>>, process: (listIn: List<T>) -> List<R>): List<List<R>> =
    chunkBySizes(process(lists.flatten()), lists.map { it.size })
