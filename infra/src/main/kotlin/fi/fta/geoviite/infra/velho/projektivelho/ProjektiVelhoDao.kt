package fi.fta.geoviite.infra.velho.projektivelho

import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Transactional(readOnly = true)
@Component
class ProjektiVelhoDao {
    fun insertFile(fileName: String, content: String, timestamp: Instant) {
        val sql = """
            insert into integrations.projektivelho_file(
                filename,
                content,
                change_time
            )
        """.trimIndent()
    }
}
