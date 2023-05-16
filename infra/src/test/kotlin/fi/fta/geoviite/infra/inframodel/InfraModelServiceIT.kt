package fi.fta.geoviite.infra.inframodel

import assertPlansMatch
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.util.FileName
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
): ITTestBase() {

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

    fun getMockedMultipartFile(fileLocation: String): MockMultipartFile = MockMultipartFile(
        "file",
        "file.xml",
        MediaType.TEXT_XML_VALUE,
        classpathResourceToString(fileLocation).byteInputStream()
    )
}
