package fi.fta.geoviite.infra.codeDictionary

import fi.fta.geoviite.infra.logging.serviceCall
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class CodeDictionaryService @Autowired constructor(private val codeDictionaryDao: CodeDictionaryDao) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun getFeatureTypes(): List<FeatureType> {
        logger.serviceCall("fetchFeatureTypes")
        return codeDictionaryDao.getFeatureTypes()
    }

}
