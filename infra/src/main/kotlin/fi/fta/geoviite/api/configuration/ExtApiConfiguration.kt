package fi.fta.geoviite.api.configuration

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.stereotype.Component

@Component
@ConfigurationProperties(prefix = "geoviite.ext-api")
data class ExtApiConfiguration(var enabled: Boolean = false, var urlPathPrefixes: List<String> = emptyList())
