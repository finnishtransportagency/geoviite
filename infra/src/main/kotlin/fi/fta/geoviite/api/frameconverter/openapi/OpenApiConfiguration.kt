package fi.fta.geoviite.api.frameconverter.openapi

import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

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
        return GroupedOpenApi.builder().group("geoviite").pathsToMatch("/geoviite/**").build()
    }

    //    @Bean
    //    fun rataVkmApi(): GroupedOpenApi {
    //        return GroupedOpenApi.builder().group("rata-vkm").pathsToMatch("/rata-vkm/**").build()
    //    }

    // TODO Missing ext-api profile check
    @RestController
    @RequestMapping("/rata-vkm/v3/api-docs")
    class StaticApiController {
        @GetMapping
        fun getRataVkm(): ResponseEntity<String> {
            val staticOpenApiJson = javaClass.getResource("/static/openapi-rata-vkm-v1.yml")?.readText() ?: "{}"
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(staticOpenApiJson)
        }
    }
}

// @Configuration
// @EnableWebMvc
// class SwaggerConfig : WebMvcConfigurer {

//    @Bean
//    fun swaggerUiConfig1(): SwaggerUiConfigProperties {
//        val config = SwaggerUiConfigProperties()

        //        config.urls =
        //            setOf(AbstractSwaggerUiConfigProperties.SwaggerUrl("geoviite", "/geoviite/v3/api-docs/geoviite",
        // "API 1"))
        ////        config.path = "/path1/swagger-ui"
        //
        //        config.isEnabled = true

//        return config
//    }

    //    @Bean
    //    fun swaggerUiConfig2(): SwaggerUiConfigProperties {
    //        val config = SwaggerUiConfigProperties()
    //        config.urls =
    //            setOf(AbstractSwaggerUiConfigProperties.SwaggerUrl("rata-vkm", "/rata-vkm/v3/api-docs/rata-vkm", "API
    // 2"))
    //        config.path = "/path2/swagger-ui"
    //        return config
    //    }
// }
