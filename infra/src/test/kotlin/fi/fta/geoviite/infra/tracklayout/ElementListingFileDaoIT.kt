package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.util.FileName
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class ElementListingFileDaoIT @Autowired constructor(private val elementListingFileDao: ElementListingFileDao) :
    DBTestBase() {

    @BeforeEach
    fun setUp() {
        jdbc.update("delete from layout.element_listing_file where id = 1", mapOf<String, Any>())
    }

    @Test
    fun `File is correctly upserted and fetched`() {
        assertNull(elementListingFileDao.getElementListingFile())
        assertEquals(Instant.EPOCH, elementListingFileDao.getLastFileListingTime())

        val originalFile =
            ElementListingFile(
                name = FileName("test file name 1"),
                content =
                    """
                col1, col2, col3
                abc, def, ghi
                123, 456, 789
            """
                        .trimIndent(),
            )
        elementListingFileDao.upsertElementListingFile(originalFile)
        assertEquals(originalFile, elementListingFileDao.getElementListingFile())
        val originalChangeTime = elementListingFileDao.getLastFileListingTime()
        assertTrue(originalChangeTime > Instant.EPOCH)

        val updatedFile =
            ElementListingFile(
                name = FileName("new test file name 2"),
                content =
                    """
                col1, col2, col3
                abc2, def2, ghi2
                something, else, here 
            """
                        .trimIndent(),
            )
        elementListingFileDao.upsertElementListingFile(updatedFile)
        assertEquals(updatedFile, elementListingFileDao.getElementListingFile())
        val updatedChangeTime = elementListingFileDao.getLastFileListingTime()
        assertTrue(updatedChangeTime > originalChangeTime)
    }
}
