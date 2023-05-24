package fi.fta.geoviite.infra.projektivelho

import PVCode
import PVId
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
class PVApiSerializationTest @Autowired constructor(val mapper: ObjectMapper) {

    @Test
    fun `SearchStatus is serialized and deserialized correctly`() {
        val state = "valmis"
        val searchId = PVId("asdf123")
        val startTime = Instant.now()
        val validFor = 123456L
        val json = """{"tila":"$state","hakutunniste":"$searchId","alkuaika":"$startTime","hakutunniste-voimassa":$validFor}"""
        val data = PVApiSearchStatus(state, searchId, startTime, validFor)

        assertEquals(json, toJson(data))
        assertEquals(data, toObject<PVApiSearchStatus>(json))
    }

    @Test
    fun `Metadata is serialized and deserialized correctly`() {
        val materialState = PVCode("matstate/some01")
        val description = FreeText("some description")
        val materialCategory = PVCode("matcat/some01")
        val documentType = PVCode("doctype/some01")
        val materialGroup = PVCode("matgrp/some01")
        val technicalFields = listOf<PVCode>()
        val containsPersonalInfo = null

        val json = """{"tila":"$materialState","kuvaus":"$description","laji":"$materialCategory","dokumenttityyppi":"$documentType","ryhma":"$materialGroup","tekniikka-alat":[],"sisaltaa-henkilotietoja":null}"""
        val data = PVApiFileMetadata(
            materialState,
            description,
            materialCategory,
            documentType,
            materialGroup,
            technicalFields,
            containsPersonalInfo
        )

        assertEquals(json, toJson(data))
        assertEquals(data, toObject<PVApiFileMetadata>(json))
    }

    private fun toJson(value: Any): String = mapper.writeValueAsString(value)
    private inline fun <reified T> toObject(value: String): T = mapper.readValue<T>(value)
}