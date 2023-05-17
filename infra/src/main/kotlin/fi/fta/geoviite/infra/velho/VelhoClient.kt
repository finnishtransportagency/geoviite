package fi.fta.geoviite.infra.velho

import PVCode
import PVDocument
import PVId
import PVName
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.velho.PVDictionaryType.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

val defaultBlockTimeout: Duration = fi.fta.geoviite.infra.ratko.defaultResponseTimeout.plusMinutes(1L)
val reloginOffsetSeconds: Long = 60

const val SEARCH_API_V1_PATH = "/hakupalvelu/api/v1"
const val XML_FILE_SEARCH_PATH = "$SEARCH_API_V1_PATH/taustahaku/kohdeluokat"
const val XML_FILE_SEARCH_STATE_PATH = "$SEARCH_API_V1_PATH/taustahaku/tila"
const val XML_FILE_SEARCH_RESULTS_PATH = "$SEARCH_API_V1_PATH/taustahaku/tulokset"

const val MATERIAL_API_V1_PATH = "/aineistopalvelu/api/v1"
const val FILE_DATA_PATH = "$MATERIAL_API_V1_PATH/aineisto"

const val METADATA_API_V2_PATH = "/metatietopalvelu/api/v2"
const val DICTIONARIES_PATH = "$METADATA_API_V2_PATH/metatiedot/kohdeluokka/aineisto/aineisto"
const val REDIRECT_PATH = "$METADATA_API_V2_PATH/ohjaa"

const val PROJECT_REGISTRY_V1_PATH = "/projektirekisteri/api/v1"

@Component
@ConditionalOnBean(VelhoClientConfiguration::class)
class VelhoClient @Autowired constructor(
    val velhoClient: VelhoWebClient,
    val loginClient: VelhoLoginClient,
    val jsonMapper: ObjectMapper,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val accessToken: AtomicReference<PVAccessTokenHolder?> = AtomicReference(null)

    fun login(): PVAccessTokenHolder {
        logger.integrationCall("login")
        return loginClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .bodyToMono<PVAccessToken>()
            .block(defaultBlockTimeout)
            ?.let(::PVAccessTokenHolder)
            ?: throw IllegalStateException("Projektivelho login failed")
    }

    fun postXmlFileSearch(fetchStartTime: Instant, startOid: String): PVApiSearchStatus {
        logger.integrationCall("postXmlFileSearch",
            "fetchStartTime" to fetchStartTime, "startOid" to startOid)
        val json = searchJson(fetchStartTime, startOid, 100)
        return velhoClient
            .post()
            .uri("$XML_FILE_SEARCH_PATH?tagi=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .body(BodyInserters.fromValue(json))
            .retrieve()
            .bodyToMono<PVApiSearchStatus>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Projektivelho search failed")
    }

    fun fetchVelhoSearches(): List<PVApiSearchStatus> {
        logger.integrationCall("fetchVelhoSearches")
        return velhoClient
            .get()
            .uri("$XML_FILE_SEARCH_STATE_PATH?tagit=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<List<PVApiSearchStatus>>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching running searches from ProjektiVelho failed")
    }

    fun fetchSearchResults(searchId: PVId): PVApiSearchResult {
        logger.integrationCall("fetchSearchResults", "searchId" to searchId)
        return velhoClient
            .get()
            .uri("$XML_FILE_SEARCH_RESULTS_PATH/$searchId")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<PVApiSearchResult>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching search results failed. searchId=$searchId")
    }

    fun fetchFileMetadata(oid: Oid<PVDocument>): PVApiFile {
        logger.integrationCall("fetchFileMetadata", "oid" to oid)
        return velhoClient
            .get()
            .uri("$FILE_DATA_PATH/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<PVApiFile>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Metadata fetch failed. oid=$oid")
    }

    fun fetchFileContent(oid: Oid<PVDocument>, version: PVId): String {
        logger.integrationCall("fetchFileContent", "oid" to oid, "version" to version)
        return velhoClient
            .get()
            .uri("$FILE_DATA_PATH/${oid}/dokumentti?versio=${version}")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("File fetch failed. oid=$oid version=$version")
    }

    fun fetchDictionaries(): Map<PVDictionaryType, List<PVDictionaryEntry>> {
        logger.integrationCall("fetchDictionaries")
        return velhoClient
            .get()
            .uri(DICTIONARIES_PATH)
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout)
            ?.let { response ->
                jsonMapper.readTree(response).let { json ->
                    json.get("info").let {
                        it.get("x-velho-nimikkeistot").let { classes ->
                            PVDictionaryType.values().associateWith { type -> fetchDictionaryType(type, classes) }
                        }
                    }
                }
            } ?: emptyMap()
    }

    private fun fetchDictionaryType(dictionaryToGet: PVDictionaryType, classes: JsonNode) =
        classes.get(encodingTypeDictionary(dictionaryToGet)).let { asset ->
            val version = asset.get("uusin-nimikkeistoversio").intValue()
            println(asset.get("nimikkeistoversiot"))
            asset.get("nimikkeistoversiot").get(version.toString()).let { nodes ->
                nodes.fieldNames().asSequence().toList().map { code ->
                    PVDictionaryEntry(
                        code = PVCode(code),
                        name = PVName(nodes.get(code).get("otsikko").textValue()),
                    )
                }
            }
        }

    fun fetchRedirect(oid: String): PVApiRedirect? {
        logger.integrationCall("fetchRedirect", "oid" to oid)
        return velhoClient
            .get()
            .uri("$REDIRECT_PATH/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(Charsets.UTF_8)
            .retrieve()
            .bodyToMono<PVApiRedirect>()
            .block(defaultBlockTimeout)
    }

    fun fetchProject(oid: String): PVApiProject? {
        logger.integrationCall("fetchProject", "oid" to oid)
        return velhoClient
            .get()
            .uri("$PROJECT_REGISTRY_V1_PATH/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<PVApiProject>()
            .block(defaultBlockTimeout)
    }

    fun fetchProjectGroup(oid: String): PVApiProjectGroup? {
        logger.integrationCall("fetchProjectGroup")
        return velhoClient
            .get()
            .uri("$PROJECT_REGISTRY_V1_PATH/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<PVApiProjectGroup>()
            .block(defaultBlockTimeout)
    }

    fun fetchAssignment(oid: String): PVApiAssignment? {
        logger.integrationCall("fetchAssignment", "oid" to oid)
        return velhoClient
            .get()
            .uri("/aineistopalvelu/api/v1/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<PVApiAssignment>()
            .block(defaultBlockTimeout)
    }

    private fun fetchAccessToken(currentTime: Instant) =
        accessToken.updateAndGet { token ->
            if (token == null || token.expireTime.isBefore(currentTime.plusSeconds(reloginOffsetSeconds))) {
                login()
            } else token
        }?.token ?: throw IllegalStateException("Projektivelho login token can't be null after login")
}

fun encodingTypeDictionary(type: PVDictionaryType) = "aineisto/${when(type) {
    DOCUMENT_TYPE -> "dokumenttityyppi"
    MATERIAL_STATE -> "aineistotila"
    MATERIAL_CATEGORY -> "aineistolaji"
    MATERIAL_GROUP -> "aineistoryhma"
    TECHNICS_FIELD -> "tekniikka-ala"
}}"
