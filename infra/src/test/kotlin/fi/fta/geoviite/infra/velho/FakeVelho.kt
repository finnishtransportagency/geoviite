package fi.fta.geoviite.infra.velho

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.inframodel.TESTFILE_CLOTHOID_AND_PARABOLA
import fi.fta.geoviite.infra.inframodel.classpathResourceToString
import org.mockserver.client.ForwardChainExpectation
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Service
import java.time.Instant

@ConditionalOnProperty("geoviite.projektivelho.test-port")
@Service
class FakeVelhoService @Autowired constructor(@Value("\${geoviite.projektivelho.test-port:}") private val testPort: Int) {
    fun start(): FakeVelho = FakeVelho(testPort)
}

data class VelhoSearchStatus(
    @JsonProperty("tila") val state: String,
    @JsonProperty("hakutunniste") val searchId: String,
    @JsonProperty("alkuaika") val startTime: String,
    @JsonProperty("hakutunniste-voimassa") val validFor: Long
)

data class VelhoFile(
    @JsonProperty("tuorein-versio") val latestVersion: VelhoLatestVersion,
    @JsonProperty("metatiedot") val metadata: Metadata
)

data class VelhoLatestVersion(
    @JsonProperty("versio") val version: String,
    @JsonProperty("nimi") val name: String,
    @JsonProperty("muokattu") val changeTime: String
)

class FakeVelho (port: Int) {
    private val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(port)
    private val jsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun stop() {
        mockServer.stop()
    }

    fun search() {
        post("/hakupalvelu/api/v1/taustahaku/kohdeluokat").respond(okJson(VelhoSearchStatus(
            "",
            "123",
            Instant.now().minusSeconds(5).toString(),
            3600
        )))
    }

    fun searchStatus(searchId: String) {
        get("/hakupalvelu/api/v1/taustahaku/tila").respond(okJson(listOf(VelhoSearchStatus(
            "valmis", searchId, Instant.now().minusSeconds(5).toString(), 3600
        ))))
    }

    fun searchResults(searchId: String, oids: List<String>) {
        get("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId").respond(okJson(SearchResult(
            matches = oids.map {  (Match(
                oid = it
            )) }
        )))
    }

    fun fileMetadata(oid: String, version: String) {
        get("/aineistopalvelu/api/v1/aineisto/$oid").respond(okJson(VelhoFile(
            latestVersion = VelhoLatestVersion(
                version,
                "test.xml",
                Instant.now().toString()
            ),
            metadata = Metadata(
                null,
                "test",
                null,
                null,
                null,
                null,
                null,
            )
        )))
    }
    fun fileContent(oid: String) {
        get("/aineistopalvelu/api/v1/aineisto/${oid}/dokumentti").respond(HttpResponse.response().withBody(
            classpathResourceToString(TESTFILE_CLOTHOID_AND_PARABOLA)))
    }

    fun login() {
        post("/oauth2/token").respond(okJson(AccessToken("mock-token", 3600, "test")))
    }

    private fun get(url: String, times: Times? = null): ForwardChainExpectation =
        expectation(url, "GET", null, null, times)

    private fun post(
        url: String,
        body: Any? = null,
        bodyMatchType: MatchType? = null,
        times: Times? = null,
    ): ForwardChainExpectation =
        expectation(url, "POST", body, bodyMatchType, times)

    private fun expectation(
        url: String,
        method: String,
        body: Any?,
        bodyMatchType: MatchType?,
        times: Times?,
    ): ForwardChainExpectation =
        mockServer.`when`(request(url).withMethod(method).apply {
            if (body != null) {
                this.withBody(JsonBody.json(body, bodyMatchType ?: MatchType.ONLY_MATCHING_FIELDS))
            }
        }, times ?: Times.unlimited())

    private fun okJson(body: Any) =
        HttpResponse.response(jsonMapper.writeValueAsString(body))
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
}

