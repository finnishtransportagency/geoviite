import com.fasterxml.jackson.annotation.JsonInclude
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestApi
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import tools.jackson.databind.json.JsonMapper

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(
    classes = [InfraApplication::class],
    properties =
        [
            "geoviite.skip-auth=false",
            "geoviite.ext-api.public-hosts=test.host.foobar,some.other.test.host",
            "geoviite.ext-api.private-hosts=private.test.host,some-other-private.test.host",
        ],
)
@AutoConfigureMockMvc
class ExtApiAuthIT @Autowired constructor(mockMvc: MockMvc) {

    private val mapper =
        JsonMapper()
            .rebuild()
            .changeDefaultPropertyInclusion { it.withValueInclusion(JsonInclude.Include.NON_NULL) }
            .build()

    val testApi = TestApi(mapper, mockMvc)

    @Test
    fun `Public rata-vkm redirect works with overridden host`() {
        listOf("test.host.foobar", "some.other.test.host").forEach { host ->
            val headers = HttpHeaders()
            headers.set("X-Forwarded-Host", host)
            testApi.doGetWithParamsWithoutBody("/rata-vkm", emptyMap(), HttpStatus.FOUND, headers)
        }
    }

    @Test
    fun `Public rata-vkm redirect does not work with the default url when the host is overridden`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "localhost")
        testApi.doGetWithParamsWithoutBody("/rata-vkm", emptyMap(), HttpStatus.UNAUTHORIZED, headers)
    }

    @Test
    fun `Private rata-vkm redirect works with overridden host`() {
        listOf("private.test.host", "some-other-private.test.host").forEach { host ->
            val headers = HttpHeaders()
            headers.set("X-Forwarded-Host", host)
            testApi.doGetWithParamsWithoutBody("/rata-vkm", emptyMap(), HttpStatus.FOUND, headers)
        }
    }

    @Test
    fun `Private rata-vkm redirect does not work with the default host when the host is overridden`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "localhost")
        testApi.doGetWithParamsWithoutBody("/rata-vkm", emptyMap(), HttpStatus.UNAUTHORIZED, headers)
    }

    @Test
    fun `Private ext-api redirect works with overridden host`() {
        listOf("private.test.host", "some-other-private.test.host").forEach { host ->
            val headers = HttpHeaders()
            headers.set("X-Forwarded-Host", host)
            testApi.doGetWithParamsWithoutBody("/geoviite", emptyMap(), HttpStatus.FOUND, headers)
        }
    }

    @Test
    fun `Private ext-api redirect does not work with the default host when the host is overridden`() {
        val headers = HttpHeaders()
        headers.set("X-Forwarded-Host", "localhost")
        testApi.doGetWithParamsWithoutBody("/geoviite", emptyMap(), HttpStatus.UNAUTHORIZED, headers)
    }
}
