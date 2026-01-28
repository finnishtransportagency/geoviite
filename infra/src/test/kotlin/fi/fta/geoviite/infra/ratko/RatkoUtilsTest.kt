package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatkoUtilsTest {

    @Test
    fun `should reformat switch structure string without handedness`() {
        switchStructures.forEach { switchStructure ->
            val ratkoFormat = asSwitchTypeString(switchStructure.type)
            if (switchStructure.type.parts.hand == SwitchHand.NONE) {
                assertEquals(ratkoFormat, switchStructure.type.toString())
            } else {
                assertEquals(ratkoFormat, switchStructure.type.toString().dropLast(2))
            }
        }
    }

    @Test
    fun `should format unique Ratko switch types from Geoviite types`() {
        val ratkoTypes =
            switchStructures.mapNotNull { switchStructure ->
                // Include non-left-handed switches only to pick only one of each YV/KV etc. switch
                // type
                if (switchStructure.type.parts.hand != SwitchHand.LEFT) asSwitchTypeString(switchStructure.type)
                else null
            }
        assertEquals(ratkoTypes, ratkoTypes.distinct())
    }

    @Test
    fun `combine path should retain given slashes`() {
        assertEquals("/api/xxx/zzz/12345/", combinePaths("/api", "xxx/zzz", "12345/"))
    }

    @Test
    fun `combine path should prevent double slashes`() {
        assertEquals("api/xxx/12345", combinePaths("api", "/xxx", "12345"))
    }

    @Test
    fun `Ratko fake OIDs should distinct from real OIDs`() {
        val realRatkoOid = RatkoOid<LocationTrack>("1.2.246.578.3.10002.189425")
        val realGeoviiteOid =  RatkoOid<LocationTrack>("1.2.246.578.13.1.2.3")
        val fakeOid = RatkoFakeOidGenerator().generateFakeRatkoOID<LocationTrack>(LOCATION_TRACK_FAKE_OID_CONTEXT, 13654)
        val fakeOidInGeoviiteFormat = Oid<LocationTrack>(fakeOid.id)
        assertFalse(isFakeOID(realRatkoOid), "Real OID is seen as a fake OID")
        assertFalse(isFakeOID(realGeoviiteOid), "Real OID is seen as a fake OID")
        assertTrue(isFakeOID(fakeOid), "Fake OID is seen as a real OID")
    }
}
