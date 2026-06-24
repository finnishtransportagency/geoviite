package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.error.InputValidationException
import fi.fta.geoviite.infra.error.UnsupportedSridException
import fi.fta.geoviite.infra.geography.ETRS89_SRID
import fi.fta.geoviite.infra.geography.ETRS89_TM35FIN_NE_SRID
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
        assertDoesNotThrow { ExtSridV1(ETRS89_TM35FIN_NE_SRID) }
        assertDoesNotThrow { ExtSridV1(WGS_84_SRID) }
        assertDoesNotThrow { ExtSridV1(ETRS89_SRID) }
        assertDoesNotThrow { ExtSridV1(FIN_GK25_SRID) }
    }

    @Test
    fun `all GK-FIN coordinate systems are accepted`() {
        ExtSridV1.SUPPORTED.filter { it.code in 3873..3885 }.forEach { srid ->
            assertDoesNotThrow { ExtSridV1(srid) }
        }
    }

    @Test
    fun `all legacy ETRS-GK-FIN coordinate systems are accepted`() {
        (3126..3138).forEach { code ->
            assertDoesNotThrow { ExtSridV1(Srid(code)) }
        }
    }

    @Test
    fun `all KKJ coordinate systems are rejected`() {
        kkjSrids.forEach { kkjSrid -> assertThrows<UnsupportedSridException> { ExtSridV1(kkjSrid) } }
    }

    @Test
    fun `valid SRID not in allowlist is rejected`() {
        // EPSG:3006 is Swedish SWEREF99 TM — valid range, transformable, but not a supported API coordinate system
        assertThrows<UnsupportedSridException> { ExtSridV1(Srid(3006)) }
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
