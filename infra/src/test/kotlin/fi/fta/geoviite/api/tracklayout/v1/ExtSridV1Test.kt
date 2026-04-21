package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.error.UnsupportedSridException
import fi.fta.geoviite.infra.geography.ETRS89_TM35FIN_SRID
import fi.fta.geoviite.infra.geography.FIN_GK25_SRID
import fi.fta.geoviite.infra.geography.WGS_84_SRID
import fi.fta.geoviite.infra.geography.kkjSrids
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class ExtSridV1Test {

    @Test
    fun `common supported coordinate systems are accepted`() {
        assertDoesNotThrow { ExtSridV1(ETRS89_TM35FIN_SRID) }
        assertDoesNotThrow { ExtSridV1(WGS_84_SRID) }
        assertDoesNotThrow { ExtSridV1(FIN_GK25_SRID) }
    }

    @Test
    fun `all KKJ coordinate systems are rejected`() {
        kkjSrids.forEach { kkjSrid ->
            assertThrows<UnsupportedSridException> { ExtSridV1(kkjSrid) }
        }
    }

    @Test
    fun `KKJ coordinate systems are rejected when parsed from string`() {
        assertThrows<UnsupportedSridException> { ExtSridV1("EPSG:2393") }
    }

    @Test
    fun `malformed SRID string is rejected`() {
        assertThrows<InputValidationException> { ExtSridV1("BOGUS") }
    }

    @Test
    fun `SRID code outside allowed range is rejected`() {
        assertThrows<InputValidationException> { ExtSridV1("EPSG:99999") }
    }
}
