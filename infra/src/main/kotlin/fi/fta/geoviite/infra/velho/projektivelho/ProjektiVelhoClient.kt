package fi.fta.geoviite.infra.velho.projektivelho

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.jsonMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import fi.fta.geoviite.infra.logging.integrationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.toEntity
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

val defaultBlockTimeout: Duration = fi.fta.geoviite.infra.ratko.defaultResponseTimeout.plusMinutes(1L)

data class AccessToken(
    val access_token: String,
    val expires_in: Int,
    val token_type: String,
)

data class ProjektiVelhoStatus(val isOnline: Boolean)

fun bulibuli(date: Instant) = """
{
    "asetukset": {
        "tyyppi": "kohdeluokkahaku",
        "koko": 10,
        "jarjesta": [
            [
                [
                    "aineisto/aineisto",
                    "tuorein-versio",
                    "muokattu"
                ],
                "nouseva"
            ],
            
            [
                [
                    "aineisto/aineisto",
                    "oid"
                ],
                "nouseva"
            ]
            
        ]
    },
    "lauseke": [
        "ja",
        [
            "suurempi-kuin",
            [
                "aineisto/aineisto",
                "tuorein-versio",
                "muokattu"
            ],
            "${date}"
        ],
        [
            "sisaltaa-tekstin",
            [
                "aineisto/aineisto",
                "tuorein-versio",
                "nimi"
            ],
            ".xml"
        ],
        [
            "joukossa",
            [
                "aineisto/aineisto",
                "metatiedot",
                "tekniikka-alat"
            ],
            [
                "tekniikka-ala/ta15"
            ]
        ],
        [
            "tai",
            [
                "yhtasuuri",
                [
                    "aineisto/aineisto",
                    "metatiedot",
                    "ryhma"
                ],
                "aineistoryhma/ar07"
            ]
        ]
    ],
    "kohdeluokat": [
        "aineisto/aineisto"
    ]
}
""".trimIndent()

data class Fetch(val hakutunniste: String)
data class FetchStatus(val tila: String, val hakutunniste: String)
data class MatchesResponse(val osumat: List<Match>)
data class Match(val oid: String)
data class LatestVersion(val versio: String, val nimi: String)
data class FileMetadata(@JsonProperty("tuorein-versio") val tuoreinVersio: LatestVersion)

@Component
@ConditionalOnBean(ProjektiVelhoClientConfiguration::class)
class ProjektiVelhoClient constructor(@Qualifier("projVelhoClient") private val client: WebClient, @Qualifier("loginClient") private val loginClient: WebClient) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var accessToken: AccessToken? = null
    private var fetchToken: Fetch? = null
    private var latestRes: Any? = null

    private val jsonMapper =
        jsonMapper { addModule(kotlinModule { configure(KotlinFeature.NullIsSameAsDefault, true) }) }
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

    @Scheduled(fixedRate = 3500000)
    fun fetchAccessToken(): AccessToken? {
        logger.integrationCall("blebleeblebleebleblee")

        val response = loginClient
            .post()
            .body(BodyInserters.fromFormData("grant_type", "client_credentials"))
            .retrieve()
            .toEntity<AccessToken>()
            .block(defaultBlockTimeout)
        accessToken = response?.body
        return accessToken
    }

    @Scheduled(fixedRate = 10000)
    fun pollVelhoFetch() =
        if (fetchToken != null) {
            val token = fetchToken
            fetchToken = null
            val res = accessToken?.let { at ->
                client
                    .get()
                    .uri("/hakupalvelu/api/v1/taustahaku/tila?tagit=aineisto")
                    .headers { header -> header.setBearerAuth(at.access_token) }
                    .retrieve()
                    .toEntity<List<FetchStatus>>()
                    .block(defaultBlockTimeout)
                    .let { res ->
                        res
                            ?.body
                            ?.firstOrNull { stat -> stat.hakutunniste == token?.hakutunniste && stat.tila == "valmis" }
                            ?.let {
                                fetchSearchResult(it.hakutunniste, at)
                            }
                    }
            }

            latestRes = res
            res
        } else null

    fun fetchSearchResult(searchId: String, accessToken: AccessToken) =
        client
            .get()
            .uri("/hakupalvelu/api/v1/taustahaku/tulokset/$searchId")
            .headers { header -> header.setBearerAuth(accessToken.access_token) }
            .retrieve()
            .bodyToMono<MatchesResponse>()
            .block(defaultBlockTimeout)
            .let { matches ->
                matches?.osumat?.map {
                    fetchMatchMetadata(it.oid)
                        .let { metadata ->
                            fetchFileContent(it.oid, metadata.tuoreinVersio.versio)
                        }
                }
            }


    fun fetchFilesFromVelho(): ResponseEntity<Fetch> {
        val ululu = bulibuli(LocalDate.of(2023, 1, 1).atTime(0,0).toInstant(ZoneOffset.UTC))
        val fetch = accessToken?.access_token?.let { at ->
            client
                .post()
                .uri("/hakupalvelu/api/v1/taustahaku/kohdeluokat?tagi=aineisto")
                .headers { header -> header.setBearerAuth(at) }
                .body(BodyInserters.fromValue(ululu))
                .retrieve()
                .toEntity<Fetch>()
                .block(defaultBlockTimeout)
        } ?: throw IllegalStateException("Voi ny, ei menny putkeen")
        fetchToken = fetch.body
        return fetch
    }

    fun fetchMatchMetadata(oid: String) =
        accessToken?.access_token?.let { at ->
            client
                .get()
                .uri("/aineistopalvelu/api/v1/aineisto/$oid")
                .headers { header -> header.setBearerAuth(at) }
                .retrieve()
                .bodyToMono<FileMetadata>()
                .block(defaultBlockTimeout)
        } ?: throw IllegalStateException("Ja taas meni metsään")

    fun fetchFileContent(oid: String, version: String) =
        accessToken?.access_token?.let { at ->
            client
                .get()
                .uri("/aineistopalvelu/api/v1/aineisto/${oid}/dokumentti?versio=${version}")
                .headers { header -> header.setBearerAuth(at) }
                .retrieve()
                .bodyToMono<String>()
                .block(defaultBlockTimeout)
        } ?: throw IllegalStateException("Ei tullu sulle tiedostoa tänään")

    fun fetchLatest() = latestRes
}
