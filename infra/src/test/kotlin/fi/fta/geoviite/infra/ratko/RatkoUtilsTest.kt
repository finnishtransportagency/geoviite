package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.dataImport.switchStructures
import fi.fta.geoviite.infra.switchLibrary.SwitchHand
import fi.fta.geoviite.infra.switchLibrary.SwitchNationality
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class RatkoUtilsTest {

    @Test
    fun `should reformat switch structure string without handedness`() {
        switchStructures.forEach { switchStructure ->
            val ratkoFormat = asSwitchTypeString(switchStructure.type)
            if (
                switchStructure.type.parts.hand == SwitchHand.NONE ||
                    switchStructure.type.parts.baseType.nationality != SwitchNationality.FINNISH
            ) {
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
}
