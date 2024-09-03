package fi.fta.geoviite.infra.localization

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_BASIC
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

@GeoviiteController("/localization")
class LocalizationController @Autowired constructor(val localizationService: LocalizationService) {

    @GetMapping("/{language}.json")
    @PreAuthorize(AUTH_BASIC)
    fun getLocalization(@PathVariable("language") language: LocalizationLanguage): Any {
        return localizationService.getLocalization(language).let { translation ->
            ObjectMapper().readValue(translation.localization, Any::class.java)
        }
    }
}
