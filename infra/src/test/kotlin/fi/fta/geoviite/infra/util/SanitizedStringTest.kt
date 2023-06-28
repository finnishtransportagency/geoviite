package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

class SanitizedStringTest {

    @Test
    fun codeCantContainIllegalChars() {
        assertDoesNotThrow { Code("LEGAL-M123.3_0") }
        assertThrows<InputValidationException> { Code("") }
        assertThrows<InputValidationException> { Code("Ä1") }
        assertThrows<InputValidationException> { Code("A A") }
        assertThrows<InputValidationException> { Code("ILLEGAL'") }
        assertThrows<InputValidationException> { Code("ILLEGAL`") }
        assertThrows<InputValidationException> { Code("ILLEGAL´") }
        assertThrows<InputValidationException> { Code("ILLEGAL*") }
        assertThrows<InputValidationException> { Code("ILLEGAL+") }
        assertThrows<InputValidationException> { Code("ILLEGAL(") }
        assertThrows<InputValidationException> { Code("ILLEGAL)") }
        assertThrows<InputValidationException> { Code("ILLEGAL<") }
        assertThrows<InputValidationException> { Code("ILLEGAL>") }
        assertThrows<InputValidationException> { Code("ILLEGAL!") }
        assertThrows<InputValidationException> { Code("ILLEGAL=") }
        assertThrows<InputValidationException> { Code("ILLEGAL?") }
        assertThrows<InputValidationException> { Code("ILLEGAL/") }
        assertThrows<InputValidationException> { Code("ILLEGAL:") }
        assertThrows<InputValidationException> { Code("ILLEGAL;") }
        assertThrows<InputValidationException> { Code("ILLEGAL\\") }
        assertThrows<InputValidationException> { Code("ILLEGAL\"") }
        assertThrows<InputValidationException> { Code("ILLEGAL\n") }
        assertThrows<InputValidationException> { Code("ILLEGAL\tCode") }
    }

    @Test
    fun freeTextCantContainIllegalChars() {
        assertDoesNotThrow { FreeText("Legal Free text: * -> 'asdf' (Äö/å) _-–\\ +123465790?!") } // both - and –
        assertDoesNotThrow { FreeText("") }
        assertThrows<InputValidationException> { FreeText("Illegal`") }
        assertThrows<InputValidationException> { FreeText("Illegal´") }
        assertThrows<InputValidationException> { FreeText("Illegal=") }
        assertThrows<InputValidationException> { FreeText("Illegal\"") }
        assertThrows<InputValidationException> { FreeText("Illegal\n") }
        assertThrows<InputValidationException> { FreeText("Illegal\tName") }
    }

}
