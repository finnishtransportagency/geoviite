package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVDictionaryCode
import PVDictionaryEntry
import PVDictionaryGroup
import PVDictionaryGroup.MATERIAL
import PVDictionaryGroup.PROJECT
import PVDictionaryName
import PVDictionaryType
import PVDictionaryType.MATERIAL_CATEGORY
import PVDocument
import PVProject
import PVProjectGroup
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.util.formatForLog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
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
const val DICTIONARIES_PATH = "$METADATA_API_V2_PATH/metatiedot/kohdeluokka"
const val MATERIAL_DICTIONARIES_PATH = "$DICTIONARIES_PATH/aineisto/aineisto"
const val PROJECT_DICTIONARIES_PATH = "$DICTIONARIES_PATH/projekti/projekti"
const val REDIRECT_PATH = "$METADATA_API_V2_PATH/ohjaa"

const val PROJECT_REGISTRY_V1_PATH = "/projektirekisteri/api/v1"
const val ASSIGNMENT_PATH = "$PROJECT_REGISTRY_V1_PATH/toimeksianto"
const val PROJECT_PATH = "$PROJECT_REGISTRY_V1_PATH/projekti"
const val PROJECT_GROUP_PATH = "$PROJECT_REGISTRY_V1_PATH/projektijoukko"

@Component
@ConditionalOnBean(PVClientConfiguration::class)
class ProjektiVelhoClient @Autowired constructor(
    val velhoClient: PVWebClient,
    val loginClient: PVLoginClient,
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

    fun postXmlFileSearch(fetchStartTime: Instant, startOid: Oid<PVDocument>?): PVApiSearchStatus {
        logger.integrationCall("postXmlFileSearch",
            "fetchStartTime" to fetchStartTime, "startOid" to startOid)
        val json = searchJson(fetchStartTime, startOid, 100)
        return postMandatoryReturn<String, PVApiSearchStatus>("$XML_FILE_SEARCH_PATH?tagi=aineisto", json)
    }

    fun fetchVelhoSearchStatus(id: PVId): PVApiSearchStatus {
        logger.integrationCall("fetchVelhoSearchStatus", "id" to id)
        return getMandatory<PVApiSearchStatus>("$XML_FILE_SEARCH_STATE_PATH/$id")
    }

    fun fetchVelhoSearches(): List<PVApiSearchStatus> {
        logger.integrationCall("fetchVelhoSearches")
        return getMandatory<List<PVApiSearchStatus>>("$XML_FILE_SEARCH_STATE_PATH?tagit=aineisto")
    }

    fun fetchSearchResults(searchId: PVId): PVApiSearchResult {
        logger.integrationCall("fetchSearchResults", "searchId" to searchId)
        return getMandatory<PVApiSearchResult>("$XML_FILE_SEARCH_RESULTS_PATH/$searchId")
    }

    fun fetchFileMetadata(oid: Oid<PVDocument>): PVApiFile {
        logger.integrationCall("fetchFileMetadata", "oid" to oid)
        return getMandatory<PVApiFile>("$FILE_DATA_PATH/$oid")
    }

    fun fetchFileContent(oid: Oid<PVDocument>, version: PVId): String {
        logger.integrationCall("fetchFileContent", "oid" to oid, "version" to version)
        return getMandatory<String>("$FILE_DATA_PATH/${oid}/dokumentti?versio=${version}")
    }

    fun fetchDictionaries(): Map<PVDictionaryType, List<PVDictionaryEntry>> =
        fetchDictionaries(MATERIAL) + fetchDictionaries(PROJECT)

    fun fetchDictionaries(group: PVDictionaryGroup): Map<PVDictionaryType, List<PVDictionaryEntry>> {
        logger.integrationCall("fetchDictionaries")
        return getJsonOptional(encodingGroupUrl(group))?.let { json ->
            json.get("info").let { infoNode ->
                infoNode.get("x-velho-nimikkeistot").let { classes ->
                    PVDictionaryType.values()
                        .filter { t -> t.group == group }
                        .associateWith { type -> fetchDictionaryType(type, classes) }
                }
            }
        } ?: emptyMap()
    }

    private fun fetchDictionaryType(dictionaryToGet: PVDictionaryType, classes: JsonNode) =
        classes.get(encodingTypeDictionary(dictionaryToGet)).let { asset ->
            val version = asset.get("uusin-nimikkeistoversio").intValue()
            asset.get("nimikkeistoversiot").get(version.toString()).let { nodes ->
                nodes.fieldNames().asSequence().toList().map { code ->
                    PVDictionaryEntry(
                        code = PVDictionaryCode(code),
                        name = PVDictionaryName(nodes.get(code).get("otsikko").textValue()),
                    )
                }
            }
        }

    fun fetchRedirect(oid: Oid<PVApiRedirect>): PVApiRedirect {
        logger.integrationCall("fetchRedirect", "oid" to oid)
        return getMandatory<PVApiRedirect>("$REDIRECT_PATH/$oid")
    }

    fun fetchProject(oid: Oid<PVProject>): PVApiProject? {
        logger.integrationCall("fetchProject", "oid" to oid)
        return getOptional<PVApiProject>("$PROJECT_PATH/$oid", get404toNull("oid=$oid"))
    }

    fun fetchProjectGroup(oid: Oid<PVProjectGroup>): PVApiProjectGroup? {
        logger.integrationCall("fetchProjectGroup", "oid" to oid)
        return getOptional<PVApiProjectGroup>("$PROJECT_GROUP_PATH/$oid", get404toNull("oid=$oid"))
    }

    fun fetchAssignment(oid: Oid<PVAssignment>): PVApiAssignment? {
        logger.integrationCall("fetchAssignment", "oid" to oid)
        return getOptional<PVApiAssignment>("$ASSIGNMENT_PATH/$oid", get404toNull("oid=$oid"))
    }

    private inline fun <reified T> get404toNull(message: String): (ex: WebClientResponseException) -> Mono<T> = { ex ->
        if (ex.rawStatusCode == 404) {
            logger.warn("Could not GET ${T::class.simpleName} from ProjektiVelho: " +
                    "$message status=${ex.statusCode} result=${ex.message?.let(::formatForLog) ?: "" }")
            Mono.empty()
        } else Mono.error(ex)
    }

    private fun getJsonOptional(uri: String): JsonNode? = getOptional<String>(uri)?.let(jsonMapper::readTree)

    private inline fun <reified Out : Any> getMandatory(uri: String): Out =
        requireNotNull(getOptional<Out>(uri)) { "GET failed with null result: outType=${Out::class}: uri=$uri" }

    private inline fun <reified Out : Any> getOptional(
        uri: String,
        crossinline onError: (ex: WebClientResponseException) -> Mono<Out> = { ex -> Mono.error(ex) },
    ): Out? = velhoClient
        .get()
        .uri(uri)
        .headers(::setBearerAuth)
        .retrieve()
        .bodyToMono<Out>()
        .onErrorResume(WebClientResponseException::class.java) { ex -> onError(ex) }
        .block(defaultBlockTimeout)

    private inline fun <reified In : Any, reified Out : Any> postMandatoryReturn(
        uri: String,
        body: In,
        crossinline onError: (ex: WebClientResponseException) -> Mono<Out> = { ex -> Mono.error(ex) },
    ): Out = requireNotNull(postOptionalReturn(uri, body, onError)) {
        "POST failed with null result: inType=${In::class.simpleName} outType=${Out::class.simpleName} uri=$uri"
    }

    private inline fun <reified In : Any, reified Out : Any> postOptionalReturn(
        uri: String,
        body: In,
        crossinline onError: (ex: WebClientResponseException) -> Mono<Out> = { ex -> Mono.error(ex) },
    ): Out? = velhoClient
        .post()
        .uri(uri)
        .headers(::setBearerAuth)
        .body(BodyInserters.fromValue(body))
        .retrieve()
        .bodyToMono<Out>()
        .onErrorResume(WebClientResponseException::class.java) { ex -> onError(ex) }
        .block(defaultBlockTimeout)

    private fun setBearerAuth(headers: HttpHeaders) = headers.setBearerAuth(fetchAccessToken(Instant.now()).toString())

    private fun fetchAccessToken(currentTime: Instant) =
        accessToken.updateAndGet { token ->
            if (token == null || token.expireTime.isBefore(currentTime.plusSeconds(reloginOffsetSeconds))) {
                login()
            } else token
        }?.token ?: throw IllegalStateException("Projektivelho login token can't be null after login")
}

fun encodingTypeDictionary(type: PVDictionaryType) =
    "${encodingGroupPath(type.group)}/${encodingTypePath(type)}"

fun encodingGroupUrl(group: PVDictionaryGroup) = when (group) {
    MATERIAL -> MATERIAL_DICTIONARIES_PATH
    PROJECT -> PROJECT_DICTIONARIES_PATH
}

fun encodingGroupPath(group: PVDictionaryGroup) = when(group) {
    MATERIAL -> "aineisto"
    PROJECT -> "projekti"
}
fun encodingTypePath(type: PVDictionaryType) = when(type) {
    PVDictionaryType.DOCUMENT_TYPE -> "dokumenttityyppi"
    PVDictionaryType.MATERIAL_STATE -> "aineistotila"
    MATERIAL_CATEGORY -> "aineistolaji"
    PVDictionaryType.MATERIAL_GROUP -> "aineistoryhma"
    PVDictionaryType.TECHNICS_FIELD -> "tekniikka-ala"
    PVDictionaryType.PROJECT_STATE -> "tila"
}

data class PVAccessTokenHolder(
    val token: PVBearerToken,
    val expireTime: Instant,
) {
    constructor(token: PVAccessToken) : this(token.accessToken, Instant.now().plusSeconds(token.expiresIn))
}
