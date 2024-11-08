package fi.fta.geoviite.infra.util

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

val linebreakNormalizationTestCases =
    listOf(
        "string without linebreak is not modified" to "string without linebreak is not modified",
        "ending unix linebreak is not modified\n" to "ending unix linebreak is not modified\n",
        "ending Windows linebreak is modified to unix style\r\n" to
            "ending Windows linebreak is modified to unix style\n",
        "ending Legacy linebreak is modified to unix style\r" to "ending Legacy linebreak is modified to unix style\n",
        "Legacy linebreak modification doesn't result \r in additional linebreaks\r\r\n" to
            "Legacy linebreak modification doesn't result \n in additional linebreaks\n\n",
        "\r\nMultiple \nlinebreaks are \r\n\r\ncorrectly modified to unix style\r" to
            "\nMultiple \nlinebreaks are \n\ncorrectly modified to unix style\n",
    )

class StringUtilsTest {

    @Test
    fun linebreaksAreCorrectlyNormalized() {
        linebreakNormalizationTestCases.forEach { (testCase, expectedResult) ->
            val modifiedString = normalizeLinebreaksToUnixFormat(testCase)

            assertEquals(
                expectedResult,
                modifiedString,
                "Test failed with expected: $expectedResult, modified: $modifiedString",
            )
        }
    }
}
