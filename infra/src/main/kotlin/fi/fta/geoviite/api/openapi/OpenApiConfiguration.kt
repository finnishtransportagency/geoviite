package fi.fta.geoviite.api.openapi

import fi.fta.geoviite.infra.environmentInfo.EnvironmentInfo
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// Due to the unfortunate nature of the dev-env redirect, some of the mappings are duplicated.
@Configuration
@Profile("ext-api")
class OpenApiConfig
@Autowired
constructor(
    private val environmentInfo: EnvironmentInfo,
) {

    @Bean
    fun geoviiteApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("geoviite")
            .pathsToMatch("/geoviite/**")
            .pathsToExclude("/geoviite/dev/**")
            .addOpenApiCustomizer(geoviiteOpenApiCustomizer())
            .build()
    }

    @Bean
    @Profile("ext-api-dev-swagger")
    fun geoviiteDevApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("geoviite-dev")
            .pathsToMatch("/geoviite/dev/**")
            .addOpenApiCustomizer(geoviiteOpenApiCustomizer())
            .build()
    }

    @Bean
    fun geoviiteOpenApiCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            openApi.info(
                Info()
                    .title("Geoviite")
                    .description("Geoviitteen ulkoiset rajapinnat")
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name(environmentInfo.geoviiteSupportEmailAddress)
                            .email(environmentInfo.geoviiteSupportEmailAddress)
                    )
            )

            // Alphabetically sort paths & components for user friendliness.
            openApi.paths =
                Paths().apply {
                    openApi.paths.orEmpty().toSortedMap().forEach { (path, item) -> addPathItem(path, item) }
                }

            openApi.components?.schemas = openApi.components?.schemas?.toSortedMap()
        }
    }
}
