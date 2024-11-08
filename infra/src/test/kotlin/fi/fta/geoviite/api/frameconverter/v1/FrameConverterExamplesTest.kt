package fi.fta.geoviite.api.frameconverter.v1

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.core.io.ClassPathResource

class FrameConverterExamplesTest {
    private val objectMapper = jacksonObjectMapper()

    @Test
    fun `Example JSON request files should be deserializable`() {
        val exampleJsonDirectory = "static/frameconverter/examples"

        val tests =
            mapOf(
                CoordinateToTrackAddressRequestV1::class to "example-request-coordinate-to-track-address.json",
                TrackAddressToCoordinateRequestV1::class to "example-request-track-address-to-coordinate.json",
            )

        tests.forEach { (clazz, filename) ->
            assertDoesNotThrow {
                val jsonString = ClassPathResource("$exampleJsonDirectory/$filename").file
                val typeReference = objectMapper.typeFactory.constructCollectionType(List::class.java, clazz.java)

                objectMapper.readValue(jsonString, typeReference)
            }
        }
    }
}
