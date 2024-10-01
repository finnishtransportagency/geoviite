package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutDesignDaoIT @Autowired constructor(private val layoutDesignDao: LayoutDesignDao) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `insert, update, list and fetch work`() {
        val insertAsIs = layoutDesignDao.insert(layoutDesignSaveRequest("foo bar")).let(layoutDesignDao::fetch)
        val update = layoutDesignDao.insert(layoutDesignSaveRequest("aa bee see")).let(layoutDesignDao::fetch)
        layoutDesignDao.update(
            update.id,
            LayoutDesignSaveRequest(
                name = LayoutDesignName("abc"),
                estimatedCompletion = LocalDate.parse("2024-01-02"),
                designState = update.designState,
            ),
        )
        assertEquals(
            listOf(
                update.copy(name = LayoutDesignName("abc"), estimatedCompletion = LocalDate.parse("2024-01-02")),
                insertAsIs,
            ),
            layoutDesignDao.list().sortedBy(LayoutDesign::name),
        )
    }

    @Test
    fun `insert() throws if name exists on non-deleted design ignoring case`() {
        layoutDesignDao.insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE))
        layoutDesignDao.insert(layoutDesignSaveRequest("foo barbar", designState = DesignState.COMPLETED))
        layoutDesignDao.insert(layoutDesignSaveRequest("boo too far", designState = DesignState.DELETED))
        val exception =
            assertThrows<DataIntegrityViolationException> { layoutDesignDao.insert(layoutDesignSaveRequest("foo bar")) }
        assertNotNull(asDuplicateNameException(exception))

        val exception2 =
            assertThrows<DataIntegrityViolationException> { layoutDesignDao.insert(layoutDesignSaveRequest("FOO BAR")) }
        assertNotNull(asDuplicateNameException(exception2))

        val exception3 =
            assertThrows<DataIntegrityViolationException> {
                layoutDesignDao.insert(layoutDesignSaveRequest("foo barbar"))
            }
        assertNotNull(asDuplicateNameException(exception3))

        assertDoesNotThrow { layoutDesignDao.insert(layoutDesignSaveRequest("boo too far")) }
    }

    @Test
    fun `update() throws if name exists on non-deleted design ignoring case`() {
        val design =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE))
                .let(layoutDesignDao::fetch)
        layoutDesignDao
            .insert(layoutDesignSaveRequest("boo too far", designState = DesignState.COMPLETED))
            .let(layoutDesignDao::fetch)
        layoutDesignDao
            .insert(layoutDesignSaveRequest("way too far", designState = DesignState.DELETED))
            .let(layoutDesignDao::fetch)

        val exception =
            assertThrows<DataIntegrityViolationException> {
                layoutDesignDao.update(
                    design.id,
                    LayoutDesignSaveRequest(
                        name = LayoutDesignName("boo too far"),
                        estimatedCompletion = design.estimatedCompletion,
                        designState = design.designState,
                    ),
                )
            }
        assertNotNull(asDuplicateNameException(exception))

        val exception2 =
            assertThrows<DataIntegrityViolationException> {
                layoutDesignDao.update(
                    design.id,
                    LayoutDesignSaveRequest(
                        name = LayoutDesignName("BOO TOO FAR"),
                        estimatedCompletion = design.estimatedCompletion,
                        designState = design.designState,
                    ),
                )
            }
        assertNotNull(asDuplicateNameException(exception2))

        assertDoesNotThrow {
            layoutDesignDao.update(
                design.id,
                LayoutDesignSaveRequest(
                    name = LayoutDesignName("way too far"),
                    estimatedCompletion = design.estimatedCompletion,
                    designState = design.designState,
                ),
            )
        }
    }

    @Test
    fun `list() respects includeCompleted`() {
        val design1 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE))
                .let(layoutDesignDao::fetch)
        val design2 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("aa bee dee", designState = DesignState.COMPLETED))
                .let(layoutDesignDao::fetch)

        val listWithCompleted = layoutDesignDao.list(includeCompleted = true)
        assertContains(listWithCompleted, design1)
        assertContains(listWithCompleted, design2)
        assertEquals(2, listWithCompleted.size)
    }

    @Test
    fun `list() respects includeDeleted`() {
        val design1 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE))
                .let(layoutDesignDao::fetch)
        val design2 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("aa bee see", designState = DesignState.DELETED))
                .let(layoutDesignDao::fetch)

        val listWithDeleted = layoutDesignDao.list(includeDeleted = true)
        assertContains(listWithDeleted, design1)
        assertContains(listWithDeleted, design2)
        assertEquals(2, listWithDeleted.size)
    }

    @Test
    fun `list() respects includeCompleted and includeDeleted`() {
        val design1 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE))
                .let(layoutDesignDao::fetch)
        val design2 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("aa bee see", designState = DesignState.DELETED))
                .let(layoutDesignDao::fetch)
        val design3 =
            layoutDesignDao
                .insert(layoutDesignSaveRequest("aa bee dee", designState = DesignState.COMPLETED))
                .let(layoutDesignDao::fetch)

        assertEquals(listOf(design1), layoutDesignDao.list(includeCompleted = false, includeDeleted = false))

        val listWithDeletedAndCompleted = layoutDesignDao.list(includeCompleted = true, includeDeleted = true)
        assertContains(listWithDeletedAndCompleted, design1)
        assertContains(listWithDeletedAndCompleted, design2)
        assertContains(listWithDeletedAndCompleted, design3)
        assertEquals(3, listWithDeletedAndCompleted.size)
    }

    private fun layoutDesignSaveRequest(
        name: String,
        estimatedCompletion: LocalDate = LocalDate.parse("2035-01-02"),
        designState: DesignState = DesignState.ACTIVE,
    ) = LayoutDesignSaveRequest(LayoutDesignName(name), estimatedCompletion, designState)
}
