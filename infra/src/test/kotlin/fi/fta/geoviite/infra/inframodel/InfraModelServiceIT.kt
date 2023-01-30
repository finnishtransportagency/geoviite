package fi.fta.geoviite.infra.inframodel

import assertPlansMatch
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.geometry.GeometryDao
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
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
        jdbc.execute("delete from geometry.plan_file") { it.execute() }
    }

    @Test
    fun simpleFileIsWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_SIMPLE)

        val (parsedPlan, _) = infraModelService.validateInputFileAndParseInfraModel(file)
        val planId = infraModelService.saveInfraModel(file, null, null)

        assertPlansMatch(parsedPlan, geometryDao.fetchPlan(planId))
    }

    @Test
    fun differentSpiralsAreWrittenAndReadCorrectly() {
        val file = getMockedMultipartFile(TESTFILE_CLOTHOID_AND_PARABOLA)

        val (parsedPlan, _) = infraModelService.validateInputFileAndParseInfraModel(file)
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
