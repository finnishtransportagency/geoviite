package fi.fta.geoviite.infra.inframodel

import assertPlansMatch
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.GeometryDao
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class InfraModelServiceIT @Autowired constructor(
    val infraModelService: InfraModelService,
    val geometryDao: GeometryDao,
): DBTestBase() {

    @BeforeEach
    fun clearPlanFiles() {
        jdbc.execute("delete from geometry.plan_file where true") { it.execute() }
    }

    @Test
    fun simpleFileIsWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun differentSpiralsAreWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_CLOTHOID_AND_PARABOLA)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun duplicatePlanCausesParsingException() {
        val file = getMockedMultipartFile(TESTFILE_CLOTHOID_AND_PARABOLA)

        infraModelService.saveInfraModel(file, null, null)
        val exception = assertThrows<InframodelParsingException> {
            infraModelService.saveInfraModel(file, null, null)
        }
        assertTrue(exception.message?.contains("InfraModel file exists already") ?: false)
    }

    @Test
    fun `InfraModel with empty rotationPoint value in the Cant-field can be saved to and fetched from the database`() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE_WITHOUT_CANT_ROTATION_POINT)

        val parsedPlan = infraModelService.parseInfraModel(toInfraModelFile(file, null))
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    fun getMockedMultipartFile(fileLocation: String): MockMultipartFile = MockMultipartFile(
        "file",
        "file.xml",
        MediaType.TEXT_XML_VALUE,
        classpathResourceToString(fileLocation).byteInputStream()
    )
}
