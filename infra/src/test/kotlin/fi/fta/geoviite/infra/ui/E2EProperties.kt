package fi.fta.geoviite.infra.ui

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "geoviite.e2e")
data class E2EProperties(val url: String, val password: String, val username: String)
