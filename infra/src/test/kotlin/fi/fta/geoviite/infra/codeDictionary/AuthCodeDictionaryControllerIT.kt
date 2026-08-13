package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.DBTestBase
import kotlin.text.Charsets.UTF_8
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.readValue

@ActiveProfiles("dev", "test", "backend")
@SpringBootTest
@AutoConfigureMockMvc
class AuthCodeDictionaryControllerIT @Autowired constructor(val objectMapper: JsonMapper, val mockMvc: MockMvc) :
    DBTestBase() {

    @Test
    fun getFeatureTypesWorks() {
        val result: MvcResult =
            mockMvc.perform(get("/code-dictionary/feature-types")).andExpect(status().isOk).andReturn()
        val responseAsObject: List<FeatureType> = objectMapper.readValue(result.response.getContentAsString(UTF_8))
        assertTrue(responseAsObject.isNotEmpty())
    }
}
