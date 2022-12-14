package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.authorization.AUTH_ALL_READ
import fi.fta.geoviite.infra.logging.apiCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/code-dictionary")
class CodeDictionaryController(private val codeDictionaryService: CodeDictionaryService) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @PreAuthorize(AUTH_ALL_READ)
    @GetMapping("/feature-types")
    fun getFeatureTypes(): List<FeatureType> {
        logger.apiCall("getFeatureTypes")
        return codeDictionaryService.getFeatureTypes()
    }
}
