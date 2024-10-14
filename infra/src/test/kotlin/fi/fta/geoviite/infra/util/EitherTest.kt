package fi.fta.geoviite.infra.util

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class EitherTest {
    @Test
    fun `processPartitioned retains original order`() {
        assertEquals(
            listOf(1, 3, 3, 5, 5, 7),
            processPartitioned(
                (0..5).toList(),
                { n -> if (n % 2 == 0) Left(n) else Right(n) },
                { ns -> ns.map { n -> n + 1 } },
                { ns -> ns.map { n -> n + 2 } },
            ),
        )
    }
}
