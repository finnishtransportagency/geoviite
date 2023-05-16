package fi.fta.geoviite.infra.velho

import VelhoCode
import VelhoId
import VelhoName
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.inframodel.TESTFILE_CLOTHOID_AND_PARABOLA
import fi.fta.geoviite.infra.inframodel.classpathResourceToString
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import org.mockserver.client.ForwardChainExpectation
import org.mockserver.integration.ClientAndServer
import org.mockserver.matchers.MatchType
import org.mockserver.matchers.Times
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse
import org.mockserver.model.JsonBody
import org.mockserver.model.MediaType
import java.time.Instant

//data class VelhoSearchStatus(
//    @JsonProperty("tila") val state: String,
//    @JsonProperty("hakutunniste") val searchId: String,
//    @JsonProperty("alkuaika") val startTime: Instant,
//    @JsonProperty("hakutunniste-voimassa") val validFor: Long
//)
//
//data class VelhoFile(
//    @JsonProperty("tuorein-versio") val latestVersion: VelhoLatestVersion,
//    @JsonProperty("metatiedot") val metadata: Metadata
//)
//
//data class VelhoLatestVersion(
//    @JsonProperty("versio") val version: String,
//    @JsonProperty("nimi") val name: String,
//    @JsonProperty("muokattu") val changeTime: Instant
//)

class FakeVelho(port: Int, val jsonMapper: ObjectMapper): AutoCloseable {
    private val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(port)

    override fun close() {
        mockServer.stop()
    }

    fun search() {
        post("/hakupalvelu/api/v1/taustahaku/kohdeluokat").respond(okJson(
            SearchStatus("", VelhoId("123"), Instant.now().minusSeconds(5), 3600)
        ))
    }

    fun searchStatus(searchId: VelhoId) {
        get("/hakupalvelu/api/v1/taustahaku/tila").respond(okJson(listOf(
            SearchStatus("valmis", searchId, Instant.now().minusSeconds(5), 3600)
        )))
    }

    fun searchResults(searchId: VelhoId, oids: List<String>) {
        get("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId").respond(okJson(SearchResult(
            matches = oids.map { oid ->
                Match(oid = Oid(oid), assignmentOid = Oid(oid))
            }
        )))
    }

    fun fileMetadata(oid: String, version: String) {
        get("/aineistopalvelu/api/v1/aineisto/$oid").respond(okJson(File(
            latestVersion = LatestVersion(VelhoId(version), FileName("test.xml"), Instant.now()),
            metadata = Metadata(
                description = FreeText("test"),
                documentType = VelhoCode("dokumenttityyppi/dt01"),
                materialState = VelhoCode("aineistotila/tila01"),
                materialCategory = VelhoCode("aineistolaji/al00"),
                materialGroup = VelhoCode("aineistoryhma/ar00"),
                technicalFields = listOf(),
                containsPersonalInfo = null,
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

