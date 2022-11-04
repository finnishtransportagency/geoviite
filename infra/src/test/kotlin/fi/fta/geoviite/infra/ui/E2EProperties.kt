package fi.fta.geoviite.infra.ui

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "geoviite.e2e")
data class E2EProperties (val url: String,
                          val password: String,
                          val username: String) {

}
