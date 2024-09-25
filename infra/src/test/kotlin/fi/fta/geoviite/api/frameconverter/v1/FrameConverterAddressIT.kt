package fi.fta.geoviite.api.frameconverter.v1

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

private val BASE_URLS = listOf("/rata-vkm/v1", "/rata-vkm/dev/v1")
private val PARTIAL_API_PATHS = listOf("/rataosoitteet", "/koordinaatit")

private val API_URLS = BASE_URLS.flatMap { base -> PARTIAL_API_PATHS.map { apiPath -> "$base$apiPath" } }
private val API_URLS_WITH_TRAILING_SLASHES = API_URLS.map { url -> "$url/" }

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class FrameConverterAddressIT @Autowired constructor(mockMvc: MockMvc) : DBTestBase() {

    private val api = FrameConverterTestApiService(mockMvc)

    @Test
    fun `GET URLs should work`() {
        API_URLS.forEach { url ->
            val featureCollection = api.fetchFeatureCollectionSingle(url)
            assertNotNull(featureCollection.features[0].properties?.get("virheet"), "$url did not work")
        }
    }

    @Test
    fun `GET URLs with trailing slashes should work`() {
        API_URLS_WITH_TRAILING_SLASHES.forEach { url ->
            val featureCollection = api.fetchFeatureCollectionSingle(url)
            assertNotNull(featureCollection.features[0].properties?.get("virheet"), "$url did not work")
        }
    }

    @Test
    fun `POST URLs should work`() {
        API_URLS.forEach { url ->
            val featureCollection = api.fetchFeatureCollectionBatch(url, listOf())
            assertEquals(featureCollection.features.size, 0, "$url did not work")
        }
    }

    @Test
    fun `POST URLs with trailing slashes should work`() {
        API_URLS_WITH_TRAILING_SLASHES.forEach { url ->
            val featureCollection = api.fetchFeatureCollectionBatch(url, listOf())
            assertEquals(featureCollection.features.size, 0, "$url did not work")
        }
    }
}
