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
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `all supported coordinate systems have working round-trip transformations`() {
        // A point in the Helsinki area in LAYOUT_SRID (ETRS89/TM35FIN)
        val original = Point(385123.0, 6672351.0)
        val tolerance = 0.01 // 10 mm — accommodates floating-point noise in degree-based (WGS84/ETRS89) round-trips

        ExtSridV1.SUPPORTED.forEach { srid ->
            val transformed = transformNonKKJCoordinate(LAYOUT_SRID, srid, original)
            val roundTripped = transformNonKKJCoordinate(srid, LAYOUT_SRID, transformed)
            assertEquals(original.x, roundTripped.x, tolerance, "Round-trip x failed for $srid")
            assertEquals(original.y, roundTripped.y, tolerance, "Round-trip y failed for $srid")
        }
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

