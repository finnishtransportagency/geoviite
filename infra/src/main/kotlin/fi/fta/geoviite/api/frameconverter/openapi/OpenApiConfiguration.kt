package fi.fta.geoviite.api.frameconverter.openapi

import org.springdoc.core.configuration.SpringDocConfiguration
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.providers.ObjectMapperProvider
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

// Disabling the automatically generated openapi structure in (springdoc.api-docs.enabled=false)
// also disables serving the swagger-ui. By creating default configuration objects, the swagger ui
// will work correctly.
@Configuration
class OpenApiConfiguration {

    @Bean
    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
    fun springDocConfiguration(): SpringDocConfiguration? {
        return SpringDocConfiguration()
    }

    @Bean
    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
    fun springDocConfigProperties(): SpringDocConfigProperties {
        return SpringDocConfigProperties()
    }

    @Bean
    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
    fun objectMapperProvider(props: SpringDocConfigProperties): ObjectMapperProvider {
        return ObjectMapperProvider(props)
    }
}
