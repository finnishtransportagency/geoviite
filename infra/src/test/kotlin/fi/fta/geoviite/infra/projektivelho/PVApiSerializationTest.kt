package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import fi.fta.geoviite.infra.util.UnsafeString
import java.time.Instant
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "nodb")
@SpringBootTest
@AutoConfigureMockMvc(addFilters = false)
class PVApiSerializationTest @Autowired constructor(val mapper: ObjectMapper) {

    @Test
    fun `SearchStatus is serialized and deserialized correctly`() {
        val state = PVApiSearchState.valmis
        val searchId = PVId("asdf123")
        val startTime = Instant.now()
        val validFor = 123456L
        val json =
            """{"tila":"$state","hakutunniste":"$searchId","alkuaika":"$startTime","hakutunniste-voimassa":$validFor}"""
        val data = PVApiSearchStatus(state, searchId, startTime, validFor)

        assertEquals(json, toJson(data))
        assertEquals(data, toObject<PVApiSearchStatus>(json))
    }

    @Test
    fun `Metadata is deserialized correctly`() {
        val materialState = PVDictionaryCode("matstate/some01")
        val description = UnsafeString("some description")
        val materialCategory = PVDictionaryCode("matcat/some01")
        val documentType = PVDictionaryCode("doctype/some01")
        val materialGroup = PVDictionaryCode("matgrp/some01")
        val technicalFields = listOf<PVDictionaryCode>()
        val containsPersonalInfo = null

        val json =
            """{"tila":"$materialState","kuvaus":"$description","laji":"$materialCategory","dokumenttityyppi":"$documentType","ryhma":"$materialGroup","tekniikka-alat":[],"sisaltaa-henkilotietoja":null}"""
        val data =
            PVApiDocumentMetadata(
                materialState,
                description,
                materialCategory,
                documentType,
                materialGroup,
                technicalFields,
                containsPersonalInfo,
            )

        // Test only deserialization, as we never serialize these anyhow and unsafe string is not
        // serializable
        assertEquals(data, toObject<PVApiDocumentMetadata>(json))
    }

    private fun toJson(value: Any): String = mapper.writeValueAsString(value)

    private inline fun <reified T> toObject(value: String): T = mapper.readValue<T>(value)
}
