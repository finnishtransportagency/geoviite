package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.FeatureTypeCode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class AuthCodeDictionaryServiceIT @Autowired constructor(private val codeDictionaryService: CodeDictionaryService) :
    DBTestBase() {

    @Test
    fun fetchFeatureTypesWorks() {
        assertEquals(43, codeDictionaryService.getFeatureTypes().count())
    }

    @Test
    fun fetchFeatureTypeHasValues() {
        assertTrue(codeDictionaryService.getFeatureTypes().any { t -> t.code == FeatureTypeCode("111") })
        assertTrue(codeDictionaryService.getFeatureTypes().any { t -> t.code == FeatureTypeCode("121") })
        assertTrue(codeDictionaryService.getFeatureTypes().any { t -> t.code == FeatureTypeCode("281") })
    }
}
