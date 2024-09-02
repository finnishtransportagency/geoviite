package fi.fta.geoviite.api.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "geoviite.integration-api")
data class IntegrationApiConfiguration(
    var enabled: Boolean = false,
    var urlPathPrefixes: List<String> = emptyList(),
)
