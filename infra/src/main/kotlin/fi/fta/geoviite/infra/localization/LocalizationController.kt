package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/localization")
class LocalizationController @Autowired constructor(
    val localizationService: LocalizationService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/{language}.json")
    @PreAuthorize(AUTH_BASIC)
    fun getLocalization(@PathVariable("language") language: LocalizationLanguage): Any {
        logger.apiCall("getLocalization", "language" to language)
        return localizationService.getLocalization(language).let { translation ->
            ObjectMapper().readValue(translation.localization, Any::class.java)
        }
    }
}
