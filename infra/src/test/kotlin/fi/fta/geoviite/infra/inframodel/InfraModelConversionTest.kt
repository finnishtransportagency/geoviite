package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class InfraModelConversionTest {

    @Test
    fun `ProfAlignGroupNumber is read from IM_ProfileGroup feature into GeometryProfile groupNumber`() {
        val xmlString = classpathResourceToString(TESTFILE_SIMPLE)
        val infraModel = toInfraModel(toInfraModelFile(FileName("test.xml"), xmlString))
        val profile = infraModel.alignmentGroups.flatMap { it.alignments }.firstNotNullOfOrNull { it.profile }
        assertNotNull(profile)
        val geometryProfile = toGvtProfile(profile!!)
        assertNotNull(geometryProfile)
        assertEquals("1", geometryProfile!!.groupNumber)
    }

    @Test
    fun `GeometryProfile groupNumber is null when IM_ProfileGroup feature is absent`() {
        val profAlign =
            InfraModelProfAlign403(
                name = "tp",
                elements = listOf(InfraModelPvi403("start", "100.0 10.0"), InfraModelPvi403("end", "200.0 20.0")),
                features = emptyList(),
            )
        val profile = InfraModelProfile403(profAlign, emptyList())
        val geometryProfile = toGvtProfile(profile)
        assertNotNull(geometryProfile)
        assertNull(geometryProfile!!.groupNumber)
    }
}
