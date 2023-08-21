package fi.fta.geoviite.infra.locale

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
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
@RequestMapping("/locale")
class LocaleController @Autowired constructor(
    val localeService: LocaleService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @GetMapping("/{language}.json")
    @PreAuthorize(AUTH_ALL_READ)
    fun getLocale(@PathVariable("language") language: String): Any {
        logger.apiCall("getLocale", "language" to language)
        return localeService.getLocale(language)
    }
}
