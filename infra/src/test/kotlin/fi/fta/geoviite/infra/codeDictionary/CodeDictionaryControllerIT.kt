package fi.fta.geoviite.infra.codeDictionary

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.infra.ITTestBase
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import kotlin.text.Charsets.UTF_8

@ActiveProfiles("dev", "test")
@SpringBootTest
@AutoConfigureMockMvc
class CodeDictionaryControllerIT @Autowired constructor(
    val objectMapper: ObjectMapper,
    val mockMvc: MockMvc,
): ITTestBase() {

    @Test
    fun getFeatureTypesWorks() {
        val result: MvcResult = mockMvc
            .perform(get("/code-dictionary/feature-types"))
            .andExpect(status().isOk)
            .andReturn()
        val responseAsObject: List<FeatureType> = objectMapper.readValue(result.response.getContentAsString(UTF_8))
        assertTrue(responseAsObject.isNotEmpty())
    }
}
