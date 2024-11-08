package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.geometry.MetaDataName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class MetaDataNameTest {
    @Test
    fun metaDataNameAllowsValidValues() {
        assertDoesNotThrow { MetaDataName("123") }
        assertDoesNotThrow { MetaDataName("abc") }
        assertDoesNotThrow { MetaDataName("ABC") }
        assertDoesNotThrow { MetaDataName("123/abc") }
        assertDoesNotThrow { MetaDataName("1.2.3") }
        assertDoesNotThrow { MetaDataName("xxx, yyy") }
        assertDoesNotThrow { MetaDataName("xxx&yyy") }
        assertDoesNotThrow { MetaDataName("xxx+yyy") }
        assertDoesNotThrow { MetaDataName("xxx-yyy") }
        assertDoesNotThrow { MetaDataName("äö") }
        assertDoesNotThrow { MetaDataName("ÄÖ") }
        assertDoesNotThrow { MetaDataName("21.2 / (Jan 31 2022 16:48:38)") }
    }
}
