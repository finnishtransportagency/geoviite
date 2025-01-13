package fi.fta.geoviite.infra.util

fun <T> compare(a: T, b: T, comparators: List<Comparator<T>>): Int {
    if (comparators.isEmpty()) return 0
    else {
        val result = comparators.first().compare(a, b)
        return if (result != 0) result else compare(a, b, comparators.drop(1))
    }
}

class CombinedComparator<T>(vararg val comparators: Comparator<T>) : Comparator<T> {
    val comparatorList by lazy { comparators.asList() }

    override fun compare(a: T, b: T): Int {
        return compare(a, b, comparatorList)
    }
}
