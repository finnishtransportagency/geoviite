package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

class FileNameTest {
    @Test
    fun fileNameAllowsValidValues() {
        assertDoesNotThrow { FileName("abc") }
        assertDoesNotThrow { FileName("123") }
        assertDoesNotThrow { FileName("_-.") }
        // Normal (two byte version) of letter "ä"
        assertDoesNotThrow { FileName("ä") }
        assertDoesNotThrow { FileName("my-awesome-file-äö.2022-08-18.xml") }
        // Three byte version of umlauts, these may exist in some file names
        assertDoesNotThrow { FileName("aaäöåÄÖÅ") }
    }
}
