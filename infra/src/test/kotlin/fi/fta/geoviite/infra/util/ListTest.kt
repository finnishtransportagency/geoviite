package fi.fta.geoviite.infra.util

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ListTest {

    @Test
    fun `rangesOfConsecutiveIndicesOf returns proper ranges`() {
        assertEquals(listOf<IntRange>(), rangesOfConsecutiveIndicesOf(false, listOf()))
        assertEquals(listOf<IntRange>(), rangesOfConsecutiveIndicesOf(false, listOf(true)))
        assertEquals(listOf<IntRange>(), rangesOfConsecutiveIndicesOf(false, listOf(true, true, true)))
        assertEquals(listOf<IntRange>(), rangesOfConsecutiveIndicesOf(false, listOf(true, true, true), 1))
        assertEquals(listOf(0..0), rangesOfConsecutiveIndicesOf(false, listOf(false)))
        assertEquals(listOf(1..1), rangesOfConsecutiveIndicesOf(false, listOf(true, false)))
        assertEquals(listOf(0..0), rangesOfConsecutiveIndicesOf(false, listOf(false, true)))
        assertEquals(listOf(1..1), rangesOfConsecutiveIndicesOf(false, listOf(true, false, true)))
        assertEquals(listOf(0..1, 3..3), rangesOfConsecutiveIndicesOf(false, listOf(false, false, true, false)))
        assertEquals(listOf(0..2, 3..4), rangesOfConsecutiveIndicesOf(false, listOf(false, false, true, false), 1))
        assertEquals(listOf(0..3, 3..5), rangesOfConsecutiveIndicesOf(false, listOf(false, false, true, false), 2))
    }

    @Test
    fun `chunkBySizes chunks by sizes`() {
        assertEquals(
            listOf(listOf(1), listOf(2, 3), listOf(4, 5, 6), listOf(7, 8, 9, 10)),
            chunkBySizes((1..10).toList(), listOf(1, 2, 3, 4)),
        )
    }
}
