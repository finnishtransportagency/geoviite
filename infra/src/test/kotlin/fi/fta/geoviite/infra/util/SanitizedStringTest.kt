package fi.fta.geoviite.infra.util

import fi.fta.geoviite.infra.error.InputValidationException
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows

val allowedFreeTextCases =
    listOf(
        "",
        "Legal Free text: * -> 'asdf' (Äö/å) _-–\\ +123465790?!",
        "legal with quote\"",
        "legal ´`@%=",
        "legal \tName",
    )

val illegalFreeTextCases = listOf("Illegal^", "Illegal|")

val freeTextWithNewLineCases =
    listOf(
        "\nStarting line break is allowed",
        "Ending line break is allowed\n",
        "Legal Free text with newline in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
        "Legal Free text with multiple newlines \n in the middle: * -> 'asdf' \n (Äö/å) _-–\\ +123465790?!",
    )

class SanitizedStringTest {

    @Test
    fun `FreeText can't contain illegal chars`() {
        allowedFreeTextCases.forEach { allowedFreeText -> assertDoesNotThrow { FreeText(allowedFreeText) } }

        (illegalFreeTextCases + freeTextWithNewLineCases).forEach { illegalFreeText ->
            assertThrows<InputValidationException> { FreeText(illegalFreeText) }
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
            assertThrows<InputValidationException> { FreeText(illegalFreeText) }
        }
    }

    @Test
    fun `FreeTextWithNewLines canonizes line breaks`() {
        val text = FreeTextWithNewLines.of("Windows\r\nline break")
        assertEquals("Windows\nline break", text.toString())
    }

    @Test
    fun `Escaping new lines from FreeTextWithNewLines produces escaped output`() {
        val escapedNewLines = FreeTextWithNewLines.of("\nSome\n\nthing \n").escapeNewLines()
        assertEquals("\\nSome\\n\\nthing \\n", escapedNewLines.toString())
    }

    @Test
    fun `StringSanitizer allows only legal characters`() {
        val legalCharacters = "ABCFÖ1-3_"
        val legalLength = 1..10
        val sanitizer = StringSanitizer(SanitizedStringTest::class, legalCharacters, legalLength)
        // Legal characters pass
        assertSanitized(sanitizer, "ABCFÖ123_")
        assertSanitized(sanitizer, "C_2")
        // Wrong characters filtered out
        assertNotSanitized(sanitizer, "AGB", "AB")
        assertNotSanitized(sanitizer, "abc2Af", "2A")
        assertNotSanitized(sanitizer, "4", "")
        assertNotSanitized(sanitizer, "A-B", "AB")
        // OK length
        assertSanitized(sanitizer, "A")
        assertSanitized(sanitizer, "AAAAAAAAAA")
        // Too long
        assertNotSanitized(sanitizer, "AAAAAAAAAAA", "AAAAAAAAAA")
        // Too short - cannot be fixed by filtering
        assertNotSanitized(sanitizer, "", "")
    }

    private fun assertSanitized(sanitizer: StringSanitizer, value: String) {
        assertTrue(sanitizer.isSanitized(value))
        assertDoesNotThrow { sanitizer.assertSanitized(value) }
        assertEquals(value, sanitizer.sanitize(value))
    }

    private fun assertNotSanitized(sanitizer: StringSanitizer, value: String, sanitizedValue: String) {
        assertFalse(sanitizer.isSanitized(value))
        assertThrows<InputValidationException> { sanitizer.assertSanitized(value) }
        assertEquals(sanitizedValue, sanitizer.sanitize(value))
    }
}
