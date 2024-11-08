package fi.fta.geoviite.infra.inframodel

import kotlin.test.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class InfraModelFileTest {

    @Test
    fun censoringAuthorWorks() {
        val xmlString = classpathResourceToString(TESTFILE_SIMPLE)
        assertTrue(xmlString.contains("Geoviite Test Author"))
        assertTrue(xmlString.contains("example@vayla.fi"))
        val censored = censorAuthorIdentifyingInfo(xmlString)
        assertFalse(censored.contains("Geoviite Test Author"))
        assertFalse(censored.contains("example@vayla.fi"))
    }
}
