package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import kotlin.test.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PVDocumentServiceIT
@Autowired
constructor(private val pvDocumentService: PVDocumentService, private val pvDao: PVDao) : DBTestBase() {

    @BeforeEach
    fun setup() {
        testDBService.clearProjektivelhoTables()
    }

    @Test
    fun `Changing document status works`() {
        insertTestDictionary(pvDao)
        val document = insertDocumentMetaWithStatus(pvDao, Oid("1.2.3.4.5"), PVDocumentStatus.SUGGESTED)
        val id = pvDocumentService.updateDocumentStatus(document.id, PVDocumentStatus.ACCEPTED)
        val header = pvDocumentService.getDocumentHeader(id)
        assertEquals(document.id, id)
        assertEquals(PVDocumentStatus.ACCEPTED, header.document.status)
    }

    @Test
    fun `Changing document statuses with empty list works`() {
        insertTestDictionary(pvDao)
        val ids = pvDocumentService.updateDocumentsStatuses(emptyList(), PVDocumentStatus.NOT_IM)
        assertEquals(0, ids.size)
    }
}
