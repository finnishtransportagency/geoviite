package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.module.SimpleModule
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.get
import com.github.tomakehurst.wiremock.client.WireMock.post
import com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo
import com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.inframodel.TESTFILE_CLOTHOID_AND_PARABOLA
import fi.fta.geoviite.infra.inframodel.classpathResourceToString
import fi.fta.geoviite.infra.util.UnsafeString
import java.time.Instant

const val SAMPLE_TOKEN =
    "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"

private class UnsafeSerializer : JsonSerializer<UnsafeString>() {
    override fun serialize(value: UnsafeString, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeString(value.unsafeValue)
    }
}

class FakeProjektiVelho(port: Int, val jsonMapper: ObjectMapper) : AutoCloseable {
    private val wireMock: WireMockServer = WireMockServer(options().port(port))

    init {
        val module = SimpleModule("TestUnsafeSerializer")
        module.addSerializer(UnsafeString::class.java, UnsafeSerializer())
        jsonMapper.registerModule(module)
        wireMock.start()
    }

    override fun close() {
        wireMock.stop()
    }

    fun search() {
        wireMock.stubFor(
            post(urlPathEqualTo(XML_FILE_SEARCH_PATH))
                .willReturn(
                    okJsonSerialized(
                        PVApiSearchStatus(PVApiSearchState.kaynnissa, PVId("123"), Instant.now().minusSeconds(5), 3600)
                    )
                )
        )
    }

    fun fetchDictionaries(group: PVDictionaryGroup, dictionaries: Map<PVDictionaryType, List<PVApiDictionaryEntry>>) {
        wireMock.stubFor(
            get(urlEqualTo(encodingGroupUrl(group)))
                .willReturn(
                    okJson(
                        """{
          "info": {
            "x-velho-nimikkeistot": {
              ${dictionaries.entries.joinToString(",") { (type, data) -> dictionaryJson(type, data) }}
            }
          }
        }"""
                            .trimIndent()
                    )
                )
        )
    }

    private fun dictionaryJson(type: PVDictionaryType, entries: List<PVApiDictionaryEntry>): String {
        return """
            "${encodingTypeDictionary(type)}": {
              "uusin-nimikkeistoversio": 1,
              "nimikkeistoversiot": {
                "1": {
                  ${entries.joinToString(",", transform = ::dictionaryEntryJson)}
                }
              }
            }
        """
            .trimIndent()
    }

    private fun dictionaryEntryJson(entry: PVApiDictionaryEntry): String =
        """
        "${entry.code}": {
          "otsikko": "${entry.name}",
          "aineistoryhmat": [
            "aineistoryhma/ar07"
          ]
        }
    """
            .trimIndent()

    fun searchStatus(searchId: PVId) {
        wireMock.stubFor(
            get(urlEqualTo("$XML_FILE_SEARCH_STATE_PATH/$searchId"))
                .willReturn(
                    okJsonSerialized(
                        PVApiSearchStatus(PVApiSearchState.valmis, searchId, Instant.now().minusSeconds(5), 3600)
                    )
                )
        )
    }

    fun searchResults(searchId: PVId, matches: List<PVApiMatch>) {
        wireMock.stubFor(
            get(urlEqualTo("$XML_FILE_SEARCH_RESULTS_PATH/$searchId"))
                .willReturn(okJsonSerialized(PVApiSearchResult(matches)))
        )
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
        wireMock.stubFor(
            get(urlEqualTo("$FILE_DATA_PATH/$oid"))
                .willReturn(
                    okJsonSerialized(
                        PVApiDocument(
                            latestVersion = PVApiLatestVersion(version, UnsafeString("test.xml"), Instant.now(), 1000),
                            metadata =
                                PVApiDocumentMetadata(
                                    description = UnsafeString(description),
                                    documentType = documentType,
                                    materialState = materialState,
                                    materialCategory = materialCategory,
                                    materialGroup = materialGroup,
                                    technicalFields = listOf(),
                                    containsPersonalInfo = null,
                                ),
                        )
                    )
                )
        )
    }

    fun fileContent(oid: Oid<PVDocument>) {
        wireMock.stubFor(
            get(urlPathEqualTo("$FILE_DATA_PATH/${oid}/dokumentti"))
                .willReturn(
                    aResponse().withStatus(200).withBody(classpathResourceToString(TESTFILE_CLOTHOID_AND_PARABOLA))
                )
        )
    }

    fun login() {
        wireMock.stubFor(
            post(urlEqualTo("/oauth2/token"))
                .willReturn(okJsonSerialized(PVAccessToken(PVBearerToken(SAMPLE_TOKEN), 3600, BearerTokenType.Bearer)))
        )
    }

    private fun okJsonSerialized(body: Any) = okJson(jsonMapper.writeValueAsString(body))

    private fun okJson(body: String) =
        aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)
}
