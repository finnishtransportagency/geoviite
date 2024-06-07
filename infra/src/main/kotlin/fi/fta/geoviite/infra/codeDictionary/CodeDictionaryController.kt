package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.aspects.GeoviiteController
import fi.fta.geoviite.infra.authorization.AUTH_VIEW_GEOMETRY
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping

@GeoviiteController
@RequestMapping("/code-dictionary")
class CodeDictionaryController(private val codeDictionaryService: CodeDictionaryService) {

    @PreAuthorize(AUTH_VIEW_GEOMETRY)
    @GetMapping("/feature-types")
    fun getFeatureTypes(): List<FeatureType> {
        return codeDictionaryService.getFeatureTypes()
    }
}
