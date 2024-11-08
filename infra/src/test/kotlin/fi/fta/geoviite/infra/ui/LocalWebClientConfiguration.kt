package fi.fta.geoviite.infra.ui

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

class LocalHostWebClient(val client: WebClient) : WebClient by client

@Configuration
class LocalWebClientConfiguration {
    @Bean
    fun localHostWebClient(): LocalHostWebClient {
        val webClientBuilder = WebClient.builder().baseUrl("http://localhost:9001")
        return LocalHostWebClient(webClientBuilder.build())
    }
}
