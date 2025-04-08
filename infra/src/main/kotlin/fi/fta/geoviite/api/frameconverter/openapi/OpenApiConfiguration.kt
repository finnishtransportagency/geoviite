package fi.fta.geoviite.api.frameconverter.openapi

import fi.fta.geoviite.api.aspects.GeoviiteExtApiController
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping

// Disabling the automatically generated openapi structure in (springdoc.api-docs.enabled=false)
// also disables serving the swagger-ui. By creating default configuration objects, the swagger ui
// will work correctly.
// @Configuration
// class OpenApiConfiguration {

//    @Bean
//    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
//    fun springDocConfiguration(): SpringDocConfiguration? {
//        return SpringDocConfiguration()
//    }
//
//    @Bean
//    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
//    fun springDocConfigProperties(): SpringDocConfigProperties {
//        return SpringDocConfigProperties()
//    }
//
//    @Bean
//    @ConditionalOnProperty(name = ["geoviite.ext-api.enabled"], havingValue = "true")
//    fun objectMapperProvider(props: SpringDocConfigProperties): ObjectMapperProvider {
//        return ObjectMapperProvider(props)
//    }
// }

@Configuration
class OpenApiConfig {
    @Bean
    fun geoviiteApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("geoviite")
            .pathsToMatch("/geoviite/**")
            .pathsToExclude("/geoviite/dev/**")
            .addOpenApiCustomizer(geoviiteOpenApiCustomiser())
            .build()
    }

    @Bean
    //     TODO Missing @Profile
    fun geoviiteDevApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("geoviite-dev")
            .pathsToMatch("/geoviite/dev/**")
            .addOpenApiCustomizer(geoviiteOpenApiCustomiser())
            .build()
    }

    @Bean
    fun geoviiteOpenApiCustomiser(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            openApi.info(
                Info()
                    .title("Geoviite")
                    .description("Geoviitteen ulkoiset rajapinnat")
                    .version("1.0.0")
                    .contact(
                        Contact().name("geoviite.support@solita.fi").email("geoviite.support@solita.fi")
                    ) // TODO Easily possible to get as an env var now
            )
        }
    }

    @GeoviiteExtApiController(["/rata-vkm/v3/api-docs"])
    class StaticRataVkmApiController {
        @GetMapping
        fun getRataVkm(): ResponseEntity<String> {
            val staticOpenApiJson = javaClass.getResource("/static/openapi-rata-vkm-v1.yml")?.readText() ?: "{}"
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(staticOpenApiJson)
        }
    }

    @GeoviiteExtApiController(["/rata-vkm/dev/v3/api-docs"])
    class StaticRataVkmDevApiController {
        @GetMapping
        fun getRataVkmDev(): ResponseEntity<String> {
            val staticOpenApiJson = javaClass.getResource("/static/openapi-rata-vkm-v1-dev.yml")?.readText() ?: "{}"
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(staticOpenApiJson)
        }
    }
}
