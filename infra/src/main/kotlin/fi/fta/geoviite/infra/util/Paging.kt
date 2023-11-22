package fi.fta.geoviite.infra.util

enum class SortOrder {
    ASCENDING, DESCENDING,
}

data class Page<T>(
    val totalCount: Int,
    val items: List<T>,
    val start: Int,
) {
    fun <S> map(mapper: (T) -> S) = Page(totalCount, items.map(mapper), start)
}

fun <T> page(items: List<T>, offset: Int, limit: Int?, comparator: Comparator<T>): Page<T> =
    Page(totalCount = items.size, items = pageToList(items, offset, limit, comparator), start = offset)

fun <T> pageToList(items: List<T>, offset: Int, limit: Int?, comparator: Comparator<T>): List<T> {
    val endIndex = limit?.let { lim -> minOf(offset + lim - 1, items.lastIndex) } ?: items.lastIndex
    return items.sortedWith(comparator).slice(offset..endIndex)
}
