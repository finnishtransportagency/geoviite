package fi.fta.geoviite.infra.util

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class PagingTest {
    @Test
    fun `page limit and offset are sensible`() {
        val items = (0..10).toList().reversed()
        val order: Comparator<Int> = Comparator.naturalOrder()
        assertEquals((0..3).toList(), page(items, 0, 4, order).items)
        assertEquals((4..7).toList(), page(items, 4, 4, order).items)
        assertEquals((8..10).toList(), page(items, 8, 4, order).items)
    }

    @Test
    fun `pageAndRest limit and offset are sensible`() {
        val items = (0..10).toList().reversed()
        val order: Comparator<Int> = Comparator.naturalOrder()
        assertEquals(PageAndRest(Page(11, (0..3).toList(), 0), (4..10).toList()), pageAndRest(items, 0, 4, order))
        assertEquals(
            PageAndRest(Page(11, (4..7).toList(), 4), (0..3).toList() + (8..10).toList()),
            pageAndRest(items, 4, 4, order),
        )
        assertEquals(PageAndRest(Page(11, (8..10).toList(), 8), (0..7).toList()), pageAndRest(items, 8, 4, order))
    }
}
