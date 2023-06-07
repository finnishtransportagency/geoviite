package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.util.FileName
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertNull

@ActiveProfiles("dev", "test")
@SpringBootTest
class ElementListingFileDaoIT @Autowired constructor(
    private val elementListingFileDao: ElementListingFileDao,
): ITTestBase() {

    @BeforeEach
    fun setUp() {
        jdbc.update("delete from layout.element_listing_file where id = 1", mapOf<String, Any>())
    }

    @Test
    fun `File is correctly upserted and fetched`() {
        val originalFile = ElementListingFile(
            name = FileName("test file name 1"),
            content = """
                col1, col2, col3
                abc, def, ghi
                123, 456, 789
            """.trimIndent()
        )
        assertNull(elementListingFileDao.getElementListingFile())
        elementListingFileDao.upsertElementListingFile(originalFile)
        assertEquals(originalFile, elementListingFileDao.getElementListingFile())

        val updatedFile = ElementListingFile(
            name = FileName("new test file name 2"),
            content = """
                col1, col2, col3
                abc2, def2, ghi2
                something, else, here 
            """.trimIndent()
        )
        elementListingFileDao.upsertElementListingFile(updatedFile)
        assertEquals(updatedFile, elementListingFileDao.getElementListingFile())
    }
}
