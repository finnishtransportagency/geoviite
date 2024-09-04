package fi.fta.geoviite.infra.util

enum class SortOrder {
    ASCENDING,
    DESCENDING,
}

data class Page<T>(val totalCount: Int, val items: List<T>, val start: Int) {
    fun <S> map(mapper: (T) -> S) = Page(totalCount, items.map(mapper), start)
}

data class PageAndRest<T>(val page: Page<T>, val rest: List<T>)

fun <T> page(items: List<T>, offset: Int, limit: Int?, comparator: Comparator<T>): Page<T> =
    Page(totalCount = items.size, items = pageToList(items, offset, limit, comparator), start = offset)

fun <T> pageToList(items: List<T>, offset: Int, limit: Int?, comparator: Comparator<T>): List<T> {
    val endIndex = getEndIndex(limit, offset, items)
    return items.sortedWith(comparator).slice(offset..endIndex)
}

private fun getEndIndex(limit: Int?, offset: Int, items: List<*>) =
    limit?.let { lim -> minOf(offset + lim - 1, items.lastIndex) } ?: items.lastIndex

fun <T> pageAndRest(items: List<T>, offset: Int, limit: Int?, comparator: Comparator<T>): PageAndRest<T> {
    val endIndex = getEndIndex(limit, offset, items)
    val sorted = items.sortedWith(comparator)
    return PageAndRest(
        Page(totalCount = items.size, items = sorted.slice(offset..endIndex), start = offset),
        sorted.slice(0 until offset) + sorted.slice(endIndex + 1 until sorted.size),
    )
}
