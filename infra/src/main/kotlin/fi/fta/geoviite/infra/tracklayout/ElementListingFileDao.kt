package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.util.DaoBase
import fi.fta.geoviite.infra.util.FileName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

data class ElementListingFile(val name: FileName, val content: String)

@Transactional(readOnly = true)
@Component
class ElementListingFileDao @Autowired constructor(
    jdbcTemplateParam: NamedParameterJdbcTemplate?,
) : DaoBase(jdbcTemplateParam) {

    @Transactional
    fun upsertElementListingFile(file: ElementListingFile) {
        TODO()
    }

    fun getElementListingFile(): ElementListingFile {
        TODO()
    }
}
