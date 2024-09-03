package fi.fta.geoviite.infra.geometry

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
class VerticalGeometryListingFileDaoIT
@Autowired
constructor(private val verticalGeometryListingFileDao: VerticalGeometryListingFileDao) : DBTestBase() {

    @BeforeEach
    fun setUp() {
        jdbc.update("delete from layout.vertical_geometry_listing_file where id = 1", mapOf<String, Any>())
    }

    @Test
    fun `Entire track network vertical geometry file is correctly upserted and fetched`() {
        assertNull(verticalGeometryListingFileDao.getVerticalGeometryListingFile())
        assertEquals(Instant.EPOCH, verticalGeometryListingFileDao.getLastFileListingTime())

        val originalFile =
            VerticalGeometryListingFile(
                name = FileName("vertical geometry test file name 1"),
                content =
                    """
                vert_geom_header1, vert_geom_header2, vert_geom_header3
                vert_geom_col1_val1, vert_geom_col2_val1, vert_geom_col3_val1
                vert_geom_col1_val2, vert_geom_col2_val2, vert_geom_col3_val2
            """
                        .trimIndent(),
            )
        verticalGeometryListingFileDao.upsertVerticalGeometryListingFile(originalFile)
        assertEquals(originalFile, verticalGeometryListingFileDao.getVerticalGeometryListingFile())
        val originalChangeTime = verticalGeometryListingFileDao.getLastFileListingTime()
        assertTrue(originalChangeTime > Instant.EPOCH)

        val updatedFile =
            VerticalGeometryListingFile(
                name = FileName("vertical geometry test file name 2"),
                content =
                    """
                vert_geom_header1, vert_geom_header2, vert_geom_header3
                vert_geom_col1_val1_new, vert_geom_col2_val1_new, vert_geom_col3_val1_new
                vert_geom_col1_val2_new, vert_geom_col2_val2_new, vert_geom_col3_val2_new
            """
                        .trimIndent(),
            )
        verticalGeometryListingFileDao.upsertVerticalGeometryListingFile(updatedFile)
        assertEquals(updatedFile, verticalGeometryListingFileDao.getVerticalGeometryListingFile())
        val updatedChangeTime = verticalGeometryListingFileDao.getLastFileListingTime()
        assertTrue(updatedChangeTime > originalChangeTime)
    }
}
