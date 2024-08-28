package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.authorization.AuthCode
import fi.fta.geoviite.infra.error.InputValidationException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

val allowedFreeTextCases = listOf(
    "",
    "Legal Free text: * -> 'asdf' (Äö/å) _-–\\ +123465790?!",
    "legal with quote\"",
    "legal ´`@%=",
    "legal \tName",
)

val illegalFreeTextCases = listOf(
    "Illegal^",
    "Illegal|",
)

val freeTextWithNewLineCases = listOf(
    "\nStarting line break is allowed",
    "Ending line break is allowed\n",
    "Legal Free text with newline in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
    "Legal Free text with multiple newlines \n in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
)

class SanitizedStringTest {

    @Test
    fun `FreeText can't contain illegal chars`() {
        allowedFreeTextCases.forEach { allowedFreeText ->
            assertDoesNotThrow { FreeText(allowedFreeText) }
        }

        (illegalFreeTextCases + freeTextWithNewLineCases).forEach { illegalFreeText ->
            assertThrows<InputValidationException> {
                FreeText(illegalFreeText)
            }
        }
    }

    @Test
    fun `FreeTextWithNewLines can contain line breaks`() {
        (allowedFreeTextCases + freeTextWithNewLineCases).forEach { allowedFreeTextWithNewlines ->
            assertDoesNotThrow { FreeTextWithNewLines.of(allowedFreeTextWithNewlines) }
        }
    }

    @Test
    fun `FreeTextWithNewLines can't contain illegal chars`() {
        illegalFreeTextCases.forEach { illegalFreeText ->
            assertThrows<InputValidationException> {
                FreeText(illegalFreeText)
            }
        }
    }

    @Test
    fun `FreeTextWithNewLines canonizes line breaks`() {
        val text = FreeTextWithNewLines.of("Windows\r\nline break")
        assertEquals("Windows\nline break", text.toString())
    }
}
