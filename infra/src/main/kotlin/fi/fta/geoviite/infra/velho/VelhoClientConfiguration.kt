package fi.fta.geoviite.infra.velho

import fi.fta.geoviite.infra.logging.integrationCall
import io.netty.handler.logging.LogLevel
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
import reactor.netty.transport.logging.AdvancedByteBufFormat
import java.time.Duration

val defaultResponseTimeout: Duration = Duration.ofMinutes(5L)
val maxFileSize: Int = 100*1024*1024

class VelhoWebClient(
    val client: WebClient
): WebClient by client

class VelhoLoginClient(
    val client: WebClient
): WebClient by client

@Configuration
@ConditionalOnProperty(prefix = "geoviite.projektivelho", name = ["enabled"], havingValue = "true")
class VelhoClientConfiguration @Autowired constructor(
    @Value("\${geoviite.projektivelho.url:}") private val projektiVelhoBaseUrl: String,
    @Value("\${geoviite.projektivelho.login_url:}") private val projektiVelhoLoginUrl: String,
    @Value("\${geoviite.projektivelho.client_id:}") private val projektiVelhoUsername: String,
    @Value("\${geoviite.projektivelho.client_secret:}") private val projektiVelhoPassword: String,
) {

    private val logger: Logger = LoggerFactory.getLogger(VelhoClient::class.java)

    @Bean
    fun loginClient(): VelhoLoginClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout)

        val webClientBuilder = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(projektiVelhoLoginUrl)
            .filter(logRequest())
            .filter(logResponse())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .defaultHeaders { header -> header.setBasicAuth(projektiVelhoUsername, projektiVelhoPassword) }

        return VelhoLoginClient(webClientBuilder.build())
    }

    @Bean
    fun projVelhoClient(): VelhoWebClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout).secure().compress(true).wiretap("CLIENT", LogLevel.INFO, AdvancedByteBufFormat.TEXTUAL)

        val webClientBuilder = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient.followRedirect(true)))
            .baseUrl(projektiVelhoBaseUrl)
            .filter(logRequest())
            .filter(logResponse())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader(HttpHeaders.ACCEPT_ENCODING, "gzip")
            .codecs { codecs ->
                codecs.defaultCodecs()
                    .maxInMemorySize(maxFileSize)
            }

        return VelhoWebClient(webClientBuilder.build())
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

