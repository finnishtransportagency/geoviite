package fi.fta.geoviite.infra.inframodel

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.localization.LocalizationKey
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import java.io.InputStream
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
internal class InfraModelControllerIT @Autowired constructor(val infraModelService: InfraModelService) : DBTestBase() {

    @Test
    fun shouldFailIfFileIsEmpty() {
        val emptyFile = MockMultipartFile("file", null, null, null)
        val result = infraModelService.validateInfraModelFile(emptyFile, null)
        assertNull(result.geometryPlan)
        assertEquals(1, result.geometryValidationIssues.size)
        assertEquals(
            LocalizationKey.of(INFRAMODEL_PARSING_KEY_EMPTY),
            result.geometryValidationIssues[0].localizationKey,
        )
    }

    @Test
    fun shouldReturnNotNullPlanIdIfValidXmlIsPosted() {
        val dummyXmlFile: InputStream = DummyFileContent.dummyValidFileXmlAsInputStream()
        val validFile = MockMultipartFile("file", "dummy.xml", MediaType.TEXT_XML_VALUE, dummyXmlFile)
        assertNotNull(infraModelService.validateInfraModelFile(validFile, null))
    }

    @Test
    fun shouldFailIfNoXmlFileIsSent() {
        val textFile = MockMultipartFile("file", "dummy.txt", MediaType.TEXT_PLAIN_VALUE, "Hello World".toByteArray())
        val result = infraModelService.validateInfraModelFile(textFile, null)
        assertNull(result.geometryPlan)
        assertEquals(1, result.geometryValidationIssues.size)
        assertEquals(
            LocalizationKey.of("$INFRAMODEL_PARSING_KEY_PARENT.wrong-content-type"),
            result.geometryValidationIssues[0].localizationKey,
        )
    }

    @Test
    fun shouldFailIfInvalidXmlFileIsSent() {
        val dummyXmlFile: InputStream = DummyFileContent.dummyInvalidFileXmlAsInputStream()
        val invalidFile = MockMultipartFile("file", "dummy.xml", MediaType.TEXT_XML_VALUE, dummyXmlFile)
        val result = infraModelService.validateInfraModelFile(invalidFile, null)
        assertNull(result.geometryPlan)
        assertEquals(1, result.geometryValidationIssues.size)
    }
}
