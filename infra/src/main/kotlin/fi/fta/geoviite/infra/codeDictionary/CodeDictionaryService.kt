package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.aspects.GeoviiteService
import org.springframework.beans.factory.annotation.Autowired

@GeoviiteService
class CodeDictionaryService @Autowired constructor(private val codeDictionaryDao: CodeDictionaryDao) {
    fun getFeatureTypes(): List<FeatureType> {
        return codeDictionaryDao.getFeatureTypes()
    }
}
