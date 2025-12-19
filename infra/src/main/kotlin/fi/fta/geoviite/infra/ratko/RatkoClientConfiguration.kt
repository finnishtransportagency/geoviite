package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.logging.copyThreadContextToReactiveResponseThread
import fi.fta.geoviite.infra.logging.integrationCall
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

val defaultResponseTimeout: Duration = Duration.ofMinutes(5L)

class RatkoWebClient(val client: WebClient) : WebClient by client

@Configuration
@ConditionalOnProperty(
    name = ["geoviite.ratko-fake-oid-generator.enabled"],
    havingValue = "true",
    matchIfMissing = false,
)
class RatkoFakeOidGeneratorConfiguration

@Configuration
@ConditionalOnProperty(name = ["geoviite.ratko.enabled"], havingValue = "true", matchIfMissing = false)
class RatkoClientConfiguration
@Autowired
constructor(
    @Value("\${geoviite.ratko.url:}") private val ratkoBaseUrl: String,
    @Value("\${geoviite.ratko.username:}") private val basicAuthUsername: String,
    @Value("\${geoviite.ratko.password:}") private val basicAuthPassword: String,
    @Value("\${geoviite.ratko.bulk-transfers-enabled:}") val bulkTransfersEnabled: Boolean,
) {

    private val logger: Logger = LoggerFactory.getLogger(RatkoClient::class.java)

    @Bean
    fun webClient(): RatkoWebClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout)

        val webClientBuilder =
            WebClient.builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(ratkoBaseUrl)
                .filter(logRequest())
                .filter(logResponse())
                .filter(copyThreadContextToReactiveResponseThread())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }

        if (basicAuthUsername.isNotBlank() && basicAuthPassword.isNotBlank()) {
            webClientBuilder.defaultHeaders { header -> header.setBasicAuth(basicAuthUsername, basicAuthPassword) }
        }

        return RatkoWebClient(webClientBuilder.build())
    }

    private fun logRequest(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofRequestProcessor { clientRequest: ClientRequest ->
            logger.integrationCall(clientRequest)
            Mono.just(clientRequest)
        }
    }

    private fun logResponse(): ExchangeFilterFunction {
        return ExchangeFilterFunction.ofResponseProcessor { clientResponse ->
            logger.integrationCall(clientResponse)
            Mono.just(clientResponse)
        }
    }
}
