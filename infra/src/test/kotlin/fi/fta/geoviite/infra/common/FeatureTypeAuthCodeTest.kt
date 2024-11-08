package fi.fta.geoviite.infra.common

import fi.fta.geoviite.infra.error.InputValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class FeatureTypeAuthCodeTest {

    @Test
    fun featureTypeCodeAllowsValidValues() {
        assertDoesNotThrow { FeatureTypeCode("123") }
        assertDoesNotThrow { FeatureTypeCode("456") }
    }

    @Test
    fun featureTypeCodeOnlyAllowsNumbers() {
        assertThrows<InputValidationException> { FeatureTypeCode("12A") }
    }

    @Test
    fun featureTypeCodeOnlyAllowsThreeCharacters() {
        assertThrows<InputValidationException> { FeatureTypeCode("") }
        assertThrows<InputValidationException> { FeatureTypeCode("1") }
        assertThrows<InputValidationException> { FeatureTypeCode("12") }
        assertThrows<InputValidationException> { FeatureTypeCode("1234") }
    }
}
