package fi.fta.geoviite.infra.projektivelho

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

const val SAMPLE_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

class FakeProjektiVelho(port: Int, val jsonMapper: ObjectMapper): AutoCloseable {
    private val mockServer: ClientAndServer = ClientAndServer.startClientAndServer(port)

    override fun close() {
        mockServer.stop()
    }

    fun search() {
        post(XML_FILE_SEARCH_PATH).respond(okJsonSerialized(
            PVApiSearchStatus(
                PVApiSearchState.kaynnissa,
                PVId("123"),
                Instant.now().minusSeconds(5),
                3600,
            )
        ))
    }

    fun fetchDictionaries(group: PVDictionaryGroup, dictionaries: Map<PVDictionaryType, List<PVDictionaryEntry>>) {
        get(encodingGroupUrl(group), Times.exactly(1)).respond(okJson("""{
          "info": {
            "x-velho-nimikkeistot": {
              ${dictionaries.entries.joinToString(",") { (type, data) -> dictionaryJson(type, data) }}
            }
          }
        }""".trimIndent()))
    }

    private fun dictionaryJson(type: PVDictionaryType, entries: List<PVDictionaryEntry>): String {
        return """
            "${encodingTypeDictionary(type)}": {
              "uusin-nimikkeistoversio": 1,
              "nimikkeistoversiot": {
                "1": {
                  ${entries.joinToString(",", transform = ::dictionaryEntryJson)}
                }
              }
            }
        """.trimIndent()
    }
    private fun dictionaryEntryJson(entry: PVDictionaryEntry): String = """
        "${entry.code}": {
          "otsikko": "${entry.name}",
          "aineistoryhmat": [
            "aineistoryhma/ar07"
          ]
        }
    """.trimIndent()

    fun searchStatus(searchId: PVId) {
        get("$XML_FILE_SEARCH_STATE_PATH/$searchId").respond(okJsonSerialized(
            PVApiSearchStatus(
                PVApiSearchState.valmis,
                searchId,
                Instant.now().minusSeconds(5),
                3600,
            )
        ))
    }

    fun searchResults(searchId: PVId, matches: List<PVApiMatch>) {
        get("$XML_FILE_SEARCH_RESULTS_PATH/$searchId").respond(okJsonSerialized(PVApiSearchResult(matches)))
    }

    fun fileMetadata(
        oid: Oid<PVDocument>,
        version: PVId,
        description: String = "test description",
        documentType: PVDictionaryCode = PVDictionaryCode("dokumenttityyppi/dt01"),
        materialState: PVDictionaryCode = PVDictionaryCode("aineistotila/tila01"),
        materialCategory: PVDictionaryCode = PVDictionaryCode("aineistolaji/al00"),
        materialGroup: PVDictionaryCode = PVDictionaryCode("aineistoryhma/ar00"),
    ) {
        get("$FILE_DATA_PATH/$oid").respond(okJsonSerialized(
            PVApiDocument(
            latestVersion = PVApiLatestVersion(version, FileName("test.xml"), Instant.now()),
            metadata = PVApiDocumentMetadata(
                description = FreeText(description),
                documentType = documentType,
                materialState = materialState,
                materialCategory = materialCategory,
                materialGroup = materialGroup,
                technicalFields = listOf(),
                containsPersonalInfo = null,
            )
        )
        ))
    }

    fun fileContent(oid: Oid<PVDocument>) {
        get("$FILE_DATA_PATH/${oid}/dokumentti").respond(HttpResponse.response().withBody(
            classpathResourceToString(TESTFILE_CLOTHOID_AND_PARABOLA)))
    }

    fun login() {
        post("/oauth2/token").respond(okJsonSerialized(
            PVAccessToken(PVBearerToken(SAMPLE_TOKEN), 3600, BearerTokenType.Bearer)
        ))
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

    private fun okJsonSerialized(body: Any) = okJson(jsonMapper.writeValueAsString(body))

    private fun okJson(body: String) =
        HttpResponse.response(body)
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
}
