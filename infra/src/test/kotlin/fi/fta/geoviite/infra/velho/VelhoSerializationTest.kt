package fi.fta.geoviite.infra.velho

import VelhoId
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class VelhoSerializationTest @Autowired constructor(val mapper: ObjectMapper) {

    @Test
    fun `SearchStatus is serialized and deserialized correctly`() {
        val state = "valmis"
        val searchId = VelhoId("asdf123")
        val startTime = Instant.now()
        val validFor = 123456L
        val json = """{"tila":"$state","hakutunniste":"$searchId","alkuaika":"$startTime","hakutunniste-voimassa":$validFor}"""
        val data = SearchStatus(state, searchId, startTime, validFor)

        assertEquals(json, toJson(data))
        assertEquals(data, toObject<SearchStatus>(json))
    }

    @Test
    fun `Metadata is serialized and deserialized correctly`() {
//        @JsonProperty("tila") val materialState: VelhoCode,
//        @JsonProperty("kuvaus") val description: FreeText?,
//        @JsonProperty("laji") val materialCategory: VelhoCode?,
//        @JsonProperty("dokumenttityyppi") val documentType: VelhoCode,
//        @JsonProperty("ryhma") val materialGroup: VelhoCode,
//        @JsonProperty("tekniikka-alat") val technicalFields: List<VelhoCode>,
//        @JsonProperty("sisaltaa-henkilotietoja") val containsPersonalInfo: Boolean?

        val state = "valmis"
        val searchId = VelhoId("asdf123")
        val startTime = Instant.now()
        val validFor = 123456L
        val json = """{"tila":"$state","hakutunniste":"$searchId","alkuaika":"$startTime","hakutunniste-voimassa":$validFor}"""
        val data = SearchStatus(state, searchId, startTime, validFor)

        assertEquals(json, toJson(data))
        assertEquals(data, toObject<SearchStatus>(json))
    }

    private fun toJson(value: Any): String = mapper.writeValueAsString(value)
    private inline fun <reified T> toObject(value: String): T = mapper.readValue<T>(value)
}