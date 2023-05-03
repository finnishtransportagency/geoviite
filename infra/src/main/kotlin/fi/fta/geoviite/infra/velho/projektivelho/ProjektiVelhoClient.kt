package fi.fta.geoviite.infra.velho.projektivelho

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.logging.integrationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.time.Duration
import java.time.Instant

val defaultBlockTimeout: Duration = fi.fta.geoviite.infra.ratko.defaultResponseTimeout.plusMinutes(1L)
val reloginoffsetSeconds: Long = 100

data class AccessTokenHolder(
    val token: String,
    val expireTime: Instant,
)

data class AccessToken(
    val access_token: String,
    val expires_in: Long,
    val token_type: String,
)

data class ProjektiVelhoStatus(val isOnline: Boolean)

@Component
@ConditionalOnBean(ProjektiVelhoClientConfiguration::class)
class ProjektiVelhoClient constructor(
    @Qualifier("projVelhoClient") private val client: WebClient,
    @Qualifier("loginClient") private val loginClient: WebClient
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var accessToken: AccessTokenHolder? = null

    private val jsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    fun logIn(): AccessTokenHolder {
        logger.integrationCall("logIn")
        return loginClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .bodyToMono<AccessToken>()
            .block(defaultBlockTimeout)
            ?.let { tokenFromVelho -> AccessTokenHolder(tokenFromVelho.access_token, Instant.now().plusSeconds(tokenFromVelho.expires_in)) }
            ?: throw IllegalStateException("Projektivelho login failed")
    }

    fun fetchVelhoSearches(fetchToken: String) =
        client
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tila?tagit=aineisto")
            .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
            .retrieve()
            .bodyToMono<List<SearchStatus>>()
            .block(defaultBlockTimeout)

    fun fetchMatchesResponse(searchId: String) =
            client
                .get()
                .uri("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId")
                .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
                .retrieve()
                .bodyToMono<MatchesResponse>()
                .block(defaultBlockTimeout)


    fun postSearch(fetchStartTime: Instant, startOid: String): SearchStatus {
        val json = searchJson( fetchStartTime, startOid, 100)
        return client
                .post()
                .uri("/hakupalvelu/api/v1/taustahaku/kohdeluokat?tagi=aineisto")
                .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
                .body(BodyInserters.fromValue(json))
                .retrieve()
                .bodyToMono<SearchStatus>()
                .block(defaultBlockTimeout) ?: throw IllegalStateException("Projektivelho search failed")
    }

    fun fetchMatchMetadata(oid: String) =
            client
                .get()
                .uri("/aineistopalvelu/api/v1/aineisto/$oid")
                .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
                .retrieve()
                .bodyToMono<FileMetadata>()
                .block(defaultBlockTimeout)

    fun fetchFileContent(oid: String, version: String, filename: String, changeTime: Instant) =
            client
                .get()
                .uri("/aineistopalvelu/api/v1/aineisto/${oid}/dokumentti?versio=${version}")
                .headers { header -> header.setBearerAuth(fetchAccessToken(Instant.now())) }
                .retrieve()
                .bodyToMono<String>()
                .block(defaultBlockTimeout)
                ?.let { content -> ProjektiVelhoFile(filename, oid, content, changeTime) }

    private fun fetchAccessToken(currentTime: Instant): String {
        if (accessToken == null || accessToken?.expireTime?.isAfter(currentTime.plusSeconds(reloginoffsetSeconds)) ?: false)
        {
            accessToken = logIn()
        }
        return accessToken?.token ?: throw IllegalStateException("Projektivelho login token can't null after login")
    }
}
