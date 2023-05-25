package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.logging.integrationCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED_VALUE
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.http.client.reactive.*
import org.springframework.http.codec.json.Jackson2JsonDecoder
import org.springframework.http.codec.json.Jackson2JsonEncoder
import org.springframework.web.reactive.function.client.ClientRequest
import org.springframework.web.reactive.function.client.ExchangeFilterFunction
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import reactor.netty.http.client.HttpClient
import java.time.Duration

val defaultResponseTimeout: Duration = Duration.ofMinutes(5L)
val maxFileSize: Int = 100*1024*1024

class PVWebClient(
    val client: WebClient
): WebClient by client

class PVLoginClient(
    val client: WebClient
): WebClient by client

@Configuration
@ConditionalOnProperty(prefix = "geoviite.projektivelho", name = ["enabled"], havingValue = "true")
class PVClientConfiguration @Autowired constructor(
    @Value("\${geoviite.projektivelho.url:}") private val projektiVelhoBaseUrl: String,
    @Value("\${geoviite.projektivelho.login_url:}") private val projektiVelhoLoginUrl: String,
    @Value("\${geoviite.projektivelho.client_id:}") private val projektiVelhoUsername: String,
    @Value("\${geoviite.projektivelho.client_secret:}") private val projektiVelhoPassword: String,
    private val objectMapper: ObjectMapper,
) {

    private val logger: Logger = LoggerFactory.getLogger(ProjektiVelhoClient::class.java)

    @Bean
    fun loginClient(): PVLoginClient {
        val httpClient = HttpClient.create().responseTimeout(defaultResponseTimeout)

        val webClientBuilder = WebClient.builder()
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .baseUrl(projektiVelhoLoginUrl)
            .filter(logRequest())
            .filter(logResponse())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, APPLICATION_FORM_URLENCODED_VALUE)
            .defaultHeaders { header -> header.setBasicAuth(projektiVelhoUsername, projektiVelhoPassword) }
            .codecs { codecs ->
                codecs.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper, APPLICATION_JSON))
                codecs.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper, APPLICATION_JSON))
            }

        return PVLoginClient(webClientBuilder.build())
    }

    @Bean
    fun projVelhoClient(): PVWebClient {
        val httpClient = HttpClient.create()
            .responseTimeout(defaultResponseTimeout)
            .secure()
            .compress(true)

        val connector = ReactorClientHttpConnector(httpClient.followRedirect(true))
        val webClientBuilder = WebClient.builder()
            .clientConnector(connector)
            .baseUrl(projektiVelhoBaseUrl)
            .filter(logRequest())
            .filter(logResponse())
            .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
            .codecs { codecs ->
                codecs.defaultCodecs() .maxInMemorySize(maxFileSize)
                codecs.defaultCodecs().jackson2JsonEncoder(Jackson2JsonEncoder(objectMapper, APPLICATION_JSON))
                codecs.defaultCodecs().jackson2JsonDecoder(Jackson2JsonDecoder(objectMapper, APPLICATION_JSON))
            }

        return PVWebClient(webClientBuilder.build())
    }

    //
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