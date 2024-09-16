package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.logging.integrationCall
import java.time.Duration
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType.*
import org.springframework.http.client.reactive.*
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient

val defaultResponseTimeout: Duration = Duration.ofMinutes(5L)
val maxFileSize: Int = 100 * 1024 * 1024

class PVWebClient(val client: WebClient) : WebClient by client

class PVLoginWebClient(val client: WebClient) : WebClient by client

@Configuration
@ConditionalOnProperty(name = ["geoviite.projektivelho.enabled"], havingValue = "true", matchIfMissing = false)
class PVClientConfiguration
@Autowired
constructor(
    @Value("\${geoviite.projektivelho.url:}") private val projektiVelhoBaseUrl: String,
    @Value("\${geoviite.projektivelho.auth_url:}") private val projektiVelhoAuthUrl: String,
    @Value("\${geoviite.projektivelho.client_id:}") private val projektiVelhoUsername: String,
    @Value("\${geoviite.projektivelho.secret_key:}") private val projektiVelhoPassword: String,
) {

    private val logger: Logger = LoggerFactory.getLogger(PVClient::class.java)

    @Bean
    fun pvLoginWebClient(): PVLoginWebClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout)

        val webClientBuilder =
            WebClient.builder()
                .clientConnector(ReactorClientHttpConnector(httpClient))
                .baseUrl(projektiVelhoAuthUrl)
                .filter(logRequest())
                .filter(logResponse())
                .defaultHeader(CONTENT_TYPE, APPLICATION_FORM_URLENCODED_VALUE)
                .defaultHeaders { header -> header.setBasicAuth(projektiVelhoUsername, projektiVelhoPassword) }

        return PVLoginWebClient(webClientBuilder.build())
    }

    @Bean
    fun pvWebClient(): PVWebClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout).secure().compress(true)

        val connector = ReactorClientHttpConnector(httpClient.followRedirect(true))
        val webClientBuilder =
            WebClient.builder()
                .clientConnector(connector)
                .baseUrl(projektiVelhoBaseUrl)
                .filter(logRequest())
                .filter(logResponse())
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .codecs { codecs -> codecs.defaultCodecs().maxInMemorySize(maxFileSize) }

        return PVWebClient(webClientBuilder.build())
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
