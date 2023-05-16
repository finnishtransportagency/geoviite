package fi.fta.geoviite.infra.velho

import VelhoCode
import VelhoId
import VelhoName
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.logging.integrationCall
import fi.fta.geoviite.infra.velho.VelhoDictionaryType.*
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

@Component
@ConditionalOnBean(VelhoClientConfiguration::class)
class VelhoClient @Autowired constructor(
    val velhoClient: VelhoWebClient,
    val loginClient: VelhoLoginClient
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val accessToken: AtomicReference<AccessTokenHolder?> = AtomicReference(null)
    private val jsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun login(): AccessTokenHolder {
        logger.integrationCall("login")
        return loginClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .bodyToMono<AccessToken>()
            .block(defaultBlockTimeout)
            ?.let(::AccessTokenHolder)
            ?: throw IllegalStateException("Projektivelho login failed")
    }

    fun postXmlFileSearch(fetchStartTime: Instant, startOid: String): SearchStatus {
        val json = searchJson(fetchStartTime, startOid, 100)
        return velhoClient
            .post()
            .uri("/hakupalvelu/api/v1/taustahaku/kohdeluokat?tagi=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .body(BodyInserters.fromValue(json))
            .retrieve()
            .bodyToMono<SearchStatus>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Projektivelho search failed")
    }

    fun fetchVelhoSearches() =
        velhoClient
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tila?tagit=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<List<SearchStatus>>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching running searches from ProjektiVelho failed")

    fun fetchSearchResults(searchId: VelhoId) =
        velhoClient
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<SearchResult>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching search results failed. searchId=$searchId")

    fun fetchFileMetadata(oid: Oid<ProjektiVelhoFile>) =
        velhoClient
            .get()
            .uri("/aineistopalvelu/api/v1/aineisto/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<File>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Metadata fetch failed. oid=$oid")

    fun fetchFileContent(oid: Oid<ProjektiVelhoFile>, version: VelhoId) =
        velhoClient
            .get()
            .uri("/aineistopalvelu/api/v1/aineisto/${oid}/dokumentti?versio=${version}")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("File fetch failed. oid=$oid version=$version")

    fun fetchDictionaries(): Map<VelhoDictionaryType, List<DictionaryEntry>> =
        velhoClient
            .get()
            .uri("/metatietopalvelu/api/v2/metatiedot/kohdeluokka/aineisto/aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout)
            ?.let { response ->
                jsonMapper.readTree(response).let { json ->
                    json.get("info").let {
                        it.get("x-velho-nimikkeistot").let { classes ->
                            VelhoDictionaryType.values().associateWith { type -> fetchDictionaryType(type, classes) }
                        }
                    }
                }
            } ?: emptyMap()

    private fun fetchDictionaryType(dictionaryToGet: VelhoDictionaryType, classes: JsonNode) =
        classes.get(encodingTypeDictionary(dictionaryToGet)).let { asset ->
            val version = asset.get("uusin-nimikkeistoversio").intValue()
            asset.get("nimikkeistoversiot").get(version.toString()).let { nodes ->
                nodes.fieldNames().asSequence().toList().map { code ->
                    DictionaryEntry(
                        code = VelhoCode(code),
                        name = VelhoName(nodes.get(code).get("otsikko").textValue()),
                    )
                }
            }
        }

    private fun encodingTypeDictionary(type: VelhoDictionaryType) = "aineisto/${when(type) {
        DOCUMENT_TYPE -> "dokumenttityyppi"
        MATERIAL_STATE -> "aineistotila"
        MATERIAL_CATEGORY -> "aineistolaji"
        MATERIAL_GROUP -> "aineistoryhma"
        TECHNICS_FIELD -> "tekniikka-ala"
    }}"

    fun fetchRedirect(oid: String): Redirect? =
        velhoClient
            .get()
            .uri("/metatietopalvelu/api/v2/ohjaa/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .accept(MediaType.APPLICATION_JSON)
            .acceptCharset(Charsets.UTF_8)
            .retrieve()
            .bodyToMono<Redirect>()
            .block(defaultBlockTimeout)

    fun fetchProject(oid: String): Project? =
        velhoClient
            .get()
            .uri("/projektirekisteri/api/v1/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<Project>()
            .block(defaultBlockTimeout)

    fun fetchProjectGroup(oid: String): ProjectGroup? =
        velhoClient
            .get()
            .uri("/projektirekisteri/api/v1/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<ProjectGroup>()
            .block(defaultBlockTimeout)

    fun fetchAssignment(oid: String): Assignment? =
        velhoClient
            .get()
            .uri("/aineistopalvelu/api/v1/kohde/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<Assignment>()
            .block(defaultBlockTimeout)

    private fun fetchAccessToken(currentTime: Instant) =
        accessToken.updateAndGet { token ->
            if (token == null || token.expireTime.isBefore(currentTime.plusSeconds(reloginOffsetSeconds))) {
                login()
            } else token
        }?.token ?: throw IllegalStateException("Projektivelho login token can't be null after login")
}
