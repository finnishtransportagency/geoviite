package fi.fta.geoviite.api.openapi

import fi.fta.geoviite.infra.environmentInfo.EnvironmentInfo
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

// Due to the unfortunate nature of the dev-env redirect, some of the mappings are duplicated.
@Configuration
@Profile("ext-api")
class OpenApiConfiguration @Autowired constructor(private val environmentInfo: EnvironmentInfo) {

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
    fun rataVkmApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("rata-vkm")
            .pathsToMatch("/rata-vkm/**")
            .pathsToExclude("/rata-vkm/dev/**")
            .addOpenApiCustomizer(rataVkmOpenApiCustomizer())
            .build()
    }

    @Bean
    @Profile("ext-api-dev-swagger")
    fun rataVkmDevApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("rata-vkm-dev")
            .pathsToMatch("/rata-vkm/dev/**")
            .addOpenApiCustomizer(rataVkmOpenApiCustomizer())
            .build()
    }

    // Server URL is set to empty string so that swagger-ui uses the current page's origin,
    // making "Try it out" work regardless of which domain the user is on.
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

            openApi.servers(listOf(Server().apply { url = "" }))

            // Alphabetically sort paths & components for user friendliness.
            openApi.paths =
                Paths().apply {
                    openApi.paths.orEmpty().toSortedMap().forEach { (path, item) -> addPathItem(path, item) }
                }

            openApi.components?.schemas = openApi.components?.schemas?.toSortedMap()
        }
    }

    fun rataVkmOpenApiCustomizer(): OpenApiCustomizer {
        return OpenApiCustomizer { openApi ->
            openApi.info(
                Info()
                    .title("Rata-VKM")
                    .description(
                        "Rataverkon viitekehysmuuntimella voi muuntaa koordinaatiston sijainteja " +
                            "rataosoitejärjestelmän sijainneiksi sekä toisin päin. Sijaintien muuntamiseen " +
                            "käytetään Geoviite-järjestelmän rataverkon tietoja.\n\n" +
                            "Geoviitteen yhtenäisen rataverkon vastaavuutta maastoon rakennettuun rataverkkoon " +
                            "ei voida taata, joten viitekehysmuuntimen palauttamia tietoa ei tule käyttää " +
                            "suurta tarkkuutta vaativiin tehtäviin, esim. uusien suunnitelmien pohjatiedoksi.\n\n" +
                            "Ongelmatilanteessa ota yhteys ylläpitoon: ${environmentInfo.geoviiteSupportEmailAddress}"
                    )
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name(environmentInfo.geoviiteSupportEmailAddress)
                            .email(environmentInfo.geoviiteSupportEmailAddress)
                    )
            )

            openApi.servers(listOf(Server().apply { url = "" }))

            // Alphabetically sort paths & components for user friendliness.
            // Trailing slash variants are excluded since each path is mapped both with and without trailing slash.
            openApi.paths =
                Paths().apply {
                    openApi.paths.orEmpty()
                        .filter { (path, _) -> !path.endsWith("/") }
                        .toSortedMap()
                        .forEach { (path, item) -> addPathItem(path, item) }
                }

            openApi.components?.schemas = openApi.components?.schemas?.toSortedMap()
        }
    }
}
