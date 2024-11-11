package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.MATERIAL
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.PROJECT
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.DOCUMENT_TYPE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_CATEGORY
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_GROUP
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.PROJECT_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.TECHNICS_FIELD
import fi.fta.geoviite.infra.util.UnsafeString
import fi.fta.geoviite.infra.util.formatForLog
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference
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

val defaultBlockTimeout: Duration = fi.fta.geoviite.infra.ratko.defaultResponseTimeout.plusMinutes(1L)
val reLoginOffset: Duration = Duration.ofSeconds(60)

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

const val PROJECT_REGISTRY_V2_PATH = "/projektirekisteri/api/v2"
const val ASSIGNMENT_PATH = "$PROJECT_REGISTRY_V2_PATH/toimeksianto"
const val PROJECT_PATH = "$PROJECT_REGISTRY_V2_PATH/projekti"
const val PROJECT_GROUP_PATH = "$PROJECT_REGISTRY_V2_PATH/projektijoukko"

@Component
@ConditionalOnBean(PVClientConfiguration::class)
class PVClient
@Autowired
constructor(val pvWebClient: PVWebClient, val pvLoginWebClient: PVLoginWebClient, val jsonMapper: ObjectMapper) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val accessToken: AtomicReference<PVAccessToken?> = AtomicReference(null)

    fun login(): PVAccessToken {
        logger.integrationCall("login")
        return pvLoginWebClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .bodyToMono<PVAccessToken>()
            .block(defaultBlockTimeout) ?: error("ProjektiVelho login failed")
    }

    fun postXmlFileSearch(fetchStartTime: Instant, startOid: Oid<PVDocument>?): PVApiSearchStatus {
        logger.integrationCall("postXmlFileSearch", "fetchStartTime" to fetchStartTime, "startOid" to startOid)
        val json = jsonMapper.writeValueAsString(searchJson(fetchStartTime, startOid, 100))
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

    fun fetchFileMetadata(oid: Oid<PVDocument>): PVApiDocument {
        logger.integrationCall("fetchFileMetadata", "oid" to oid)
        return getMandatory<PVApiDocument>("$FILE_DATA_PATH/$oid")
    }

    fun fetchFileContent(oid: Oid<PVDocument>, version: PVId): String? {
        logger.integrationCall("fetchFileContent", "oid" to oid, "version" to version)
        return getOptional<String>("$FILE_DATA_PATH/$oid/dokumentti?versio=$version").also { content ->
            if (content == null) logger.warn("File content was null! oid=$oid")
        }
    }

    fun fetchDictionaries(): Map<PVDictionaryType, List<PVApiDictionaryEntry>> =
        fetchDictionaries(MATERIAL) + fetchDictionaries(PROJECT)

    fun fetchDictionaries(group: PVDictionaryGroup): Map<PVDictionaryType, List<PVApiDictionaryEntry>> {
        logger.integrationCall("fetchDictionaries")
        return getJsonOptional(encodingGroupUrl(group))?.let { json ->
            json.get("info").let { infoNode ->
                infoNode.get("x-velho-nimikkeistot").let { classes ->
                    PVDictionaryType.entries
                        .filter { t -> t.group == group }
                        .associateWith { type -> fetchDictionaryType(type, classes) }
                }
            }
        } ?: emptyMap()
    }

    private fun fetchDictionaryType(dictionaryToGet: PVDictionaryType, classes: JsonNode): List<PVApiDictionaryEntry> =
        classes.get(encodingTypeDictionary(dictionaryToGet)).let { asset ->
            val version = asset.get("uusin-nimikkeistoversio").intValue()
            asset.get("nimikkeistoversiot").get(version.toString()).let { nodes ->
                nodes.fieldNames().asSequence().toList().map { code ->
                    PVApiDictionaryEntry(
                        code = PVDictionaryCode(code),
                        name = UnsafeString(nodes.get(code).get("otsikko").textValue()),
                    )
                }
            }
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
        if (ex.statusCode.value() == 404) {
            logger.warn(
                "Could not GET ${T::class.simpleName} from ProjektiVelho: " +
                    "$message status=${ex.statusCode} result=${ex.message.let(::formatForLog)}"
            )
            Mono.empty()
        } else {
            Mono.error(ex)
        }
    }

    private fun getJsonOptional(uri: String): JsonNode? = getOptional<String>(uri)?.let(jsonMapper::readTree)

    private inline fun <reified Out : Any> getMandatory(uri: String): Out =
        requireNotNull(getOptional<Out>(uri)) { "GET failed with null result: outType=${Out::class}: uri=$uri" }

    private inline fun <reified Out : Any> getOptional(
        uri: String,
        crossinline onError: (ex: WebClientResponseException) -> Mono<Out> = { ex -> Mono.error(ex) },
    ): Out? =
        pvWebClient
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
    ): Out =
        requireNotNull(postOptionalReturn(uri, body, onError)) {
            "POST failed with null result: inType=${In::class.simpleName} outType=${Out::class.simpleName} uri=$uri"
        }

    private inline fun <reified In : Any, reified Out : Any> postOptionalReturn(
        uri: String,
        body: In,
        crossinline onError: (ex: WebClientResponseException) -> Mono<Out> = { ex -> Mono.error(ex) },
    ): Out? =
        pvWebClient
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
        accessToken
            .updateAndGet { token ->
                if (token == null || token.expireTime.isBefore(currentTime.plus(reLoginOffset))) {
                    login()
                } else {
                    token
                }
            }
            ?.accessToken ?: error("ProjektiVelho login token can't be null after login")
}

fun encodingTypeDictionary(type: PVDictionaryType) = "${encodingGroupPath(type.group)}/${encodingTypePath(type)}"

fun encodingGroupUrl(group: PVDictionaryGroup) =
    when (group) {
        MATERIAL -> MATERIAL_DICTIONARIES_PATH
        PROJECT -> PROJECT_DICTIONARIES_PATH
    }

fun encodingGroupPath(group: PVDictionaryGroup) =
    when (group) {
        MATERIAL -> "aineisto"
        PROJECT -> "projekti"
    }

fun encodingTypePath(type: PVDictionaryType) =
    when (type) {
        DOCUMENT_TYPE -> "dokumenttityyppi"
        MATERIAL_STATE -> "aineistotila"
        MATERIAL_CATEGORY -> "aineistolaji"
        MATERIAL_GROUP -> "aineistoryhma"
        TECHNICS_FIELD -> "tekniikka-ala"
        PROJECT_STATE -> "tila"
    }
