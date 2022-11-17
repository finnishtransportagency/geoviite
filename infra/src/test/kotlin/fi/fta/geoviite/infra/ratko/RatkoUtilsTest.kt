package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.dataImport.switchStructures
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RatkoUtilsTest {

    @Test
    fun `should reformat switch structure string without handedness`() {
        switchStructures.forEach { switchStructure ->
            val ratkoFormat = asSwitchTypeString(switchStructure.type)
            if (switchStructure.type.parts.hand == null) {
                assertEquals(ratkoFormat, switchStructure.type.toString())
            } else {
                assertEquals(ratkoFormat, switchStructure.type.toString().dropLast(2))
            }

        }
    }

}
