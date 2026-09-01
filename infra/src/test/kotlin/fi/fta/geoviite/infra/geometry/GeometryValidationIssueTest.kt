package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.localization.LocalizationKey
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import tools.jackson.databind.json.JsonMapper

@ActiveProfiles("dev", "test", "nodb", "backend")
@SpringBootTest
class GeometryValidationIssueTest @Autowired constructor(val mapper: JsonMapper) {

    @Test
    fun `serializes to flat object with params map`() {
        val issue =
            GeometryValidationIssue(
                localizationKey = LocalizationKey.of("infra-model.validation.alignment.duplicate-name"),
                issueType = GeometryIssueType.OBSERVATION_MAJOR,
                localizationParams = mapOf("alignmentName" to "AL1"),
            )
        val json = mapper.writeValueAsString(issue)
        val node = mapper.readTree(json)

        assertEquals("infra-model.validation.alignment.duplicate-name", node["localizationKey"].asString())
        assertEquals("OBSERVATION_MAJOR", node["issueType"].asString())
        assertEquals("AL1", node["localizationParams"]["alignmentName"].asString())
        // Verify old flat fields are gone
        assertTrue(node["alignmentName"] == null, "alignmentName must not be a top-level field")
    }

    @Test
    fun `empty localizationParams serializes as empty object not null`() {
        val issue =
            GeometryValidationIssue(
                localizationKey = LocalizationKey.of("infra-model.validation.alignment.no-reference-lines"),
                issueType = GeometryIssueType.VALIDATION_ERROR,
            )
        val json = mapper.writeValueAsString(issue)
        val node = mapper.readTree(json)

        assertTrue(node["localizationParams"].isObject)
        assertEquals(0, node["localizationParams"].size())
    }
}
