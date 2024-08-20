package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

val allowedFreeTextCases =
    listOf(
        "",
        "Legal Free text: * -> 'asdf' (Äö/å) _-–\\ +123465790?!",
    )

val illegalFreeTextCases =
    listOf(
        "Illegal`",
        "Illegal´",
        "Illegal=",
        "Illegal\"",
        "Illegal\tName",
    )

val freeTextWithNewLineCases =
    listOf(
        "\nStarting line break is allowed",
        "Ending line break is allowed\n",
        "Legal Free text with newline in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
        "Legal Free text with multiple newlines \n in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
    )

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
        allowedFreeTextCases.forEach { allowedFreeText ->
            assertDoesNotThrow { FreeText(allowedFreeText) }
        }

        (illegalFreeTextCases + freeTextWithNewLineCases).forEach { illegalFreeText ->
            assertThrows<InputValidationException> { FreeText(illegalFreeText) }
        }
    }

    @Test
    fun freeTextWithNewLinesCanContainNewLines() {
        (allowedFreeTextCases + freeTextWithNewLineCases).forEach { allowedFreeTextWithNewlines ->
            assertDoesNotThrow { FreeTextWithNewLines(allowedFreeTextWithNewlines) }
        }
    }

    @Test
    fun freeTextWithNewLinesCantContainIllegalChars() {
        illegalFreeTextCases.forEach { illegalFreeText ->
            assertThrows<InputValidationException> { FreeText(illegalFreeText) }
        }
    }
}
