package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PublicationLogFormattingTest {
    @Test
    fun `formatLocation works`() {
        val location = Point(1.0, 2.0001)
        val formatted = formatLocation(location)
        assertEquals(formatted, "1.000 E, 2.000 N")
    }
}
