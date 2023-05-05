package fi.fta.geoviite.infra.velho

import fi.fta.geoviite.infra.logging.integrationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

val defaultBlockTimeout: Duration = fi.fta.geoviite.infra.ratko.defaultResponseTimeout.plusMinutes(1L)
val reloginoffsetSeconds: Long = 60

@Component
@ConditionalOnBean(VelhoClientConfiguration::class)
class VelhoClient @Autowired constructor(
    webClientHolder: VelhoWebClient,
    loginClientHolder: VelhoLoginClient
) {
    private val client = webClientHolder.client
    private val loginClient = loginClientHolder.client
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val accessToken: AtomicReference<AccessTokenHolder?> = AtomicReference(null)

    fun login(): AccessTokenHolder {
        logger.integrationCall("login")
        return loginClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .bodyToMono<AccessToken>()
            .block(defaultBlockTimeout)
            ?.let { tokenFromVelho ->
                AccessTokenHolder(
                    tokenFromVelho.accessToken,
                    Instant.now().plusSeconds(tokenFromVelho.expiresIn)
                )
            }
            ?: throw IllegalStateException("Projektivelho login failed")
    }

    fun postXmlFileSearch(fetchStartTime: Instant, startOid: String): SearchStatus {
        val json = searchJson(fetchStartTime, startOid, 100)
        return client
            .post()
            .uri("/hakupalvelu/api/v1/taustahaku/kohdeluokat?tagi=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .body(BodyInserters.fromValue(json))
            .retrieve()
            .bodyToMono<SearchStatus>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Projektivelho search failed")
    }

    fun fetchVelhoSearches() =
        client
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tila?tagit=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<List<SearchStatus>>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching running searches from ProjektiVelho failed")

    fun fetchSearchResults(searchId: String) =
        client
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<SearchResult>()
            .block(defaultBlockTimeout)
            ?: throw IllegalStateException("Fetching search results failed. searchId=$searchId")

    fun fetchFileMetadata(oid: String) =
        client
            .get()
            .uri("/aineistopalvelu/api/v1/aineisto/$oid")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<File>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("Metadata fetch failed. oid=$oid")

    fun fetchFileContent(oid: String, version: String) =
        client
            .get()
            .uri("/aineistopalvelu/api/v1/aineisto/${oid}/dokumentti?versio=${version}")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<String>()
            .block(defaultBlockTimeout) ?: throw IllegalStateException("File fetch failed. oid=$oid version=$version")

    private fun fetchAccessToken(currentTime: Instant): String {
        val token = accessToken.get()
        if (token == null || token.expireTime.isBefore(currentTime.plusSeconds(reloginoffsetSeconds))) {
            accessToken.set(login())
        }
        return accessToken.get()?.token
            ?: throw IllegalStateException("Projektivelho login token can't be null after login")
    }
}
