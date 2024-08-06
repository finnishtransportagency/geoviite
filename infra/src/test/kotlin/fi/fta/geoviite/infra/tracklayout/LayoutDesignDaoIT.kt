package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.assertContains
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutDesignDaoIT @Autowired constructor(private val layoutDesignDao: LayoutDesignDao) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `insert, update, list and fetch work`() {
        val insertAsIs = layoutDesignDao.insert(layoutDesignSaveRequest("foo bar")).let(layoutDesignDao::fetch)
        val update = layoutDesignDao.insert(layoutDesignSaveRequest("aa bee see")).let(layoutDesignDao::fetch)
        layoutDesignDao.update(
            update.id,
            LayoutDesignSaveRequest(
                name = FreeText("abc"),
                estimatedCompletion = LocalDate.parse("2024-01-02"),
                designState = update.designState
            )
        )
        assertEquals(
            listOf(
                update.copy(name = FreeText("abc"), estimatedCompletion = LocalDate.parse("2024-01-02")),
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
        assertThrows<IllegalArgumentException> {
            layoutDesignDao.insert(layoutDesignSaveRequest("foo bar"))
        }
        assertThrows<IllegalArgumentException> {
            layoutDesignDao.insert(layoutDesignSaveRequest("FOO BAR"))
        }
        assertThrows<IllegalArgumentException> {
            layoutDesignDao.insert(layoutDesignSaveRequest("foo barbar"))
        }
        assertDoesNotThrow { layoutDesignDao.insert(layoutDesignSaveRequest("boo too far")) }
    }

    @Test
    fun `update() throws if name exists on non-deleted design ignoring case`() {
        val design = layoutDesignDao.insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE)).let(layoutDesignDao::fetch)
        layoutDesignDao.insert(layoutDesignSaveRequest("boo too far", designState = DesignState.COMPLETED)).let(layoutDesignDao::fetch)
        layoutDesignDao.insert(layoutDesignSaveRequest("way too far", designState = DesignState.DELETED)).let(layoutDesignDao::fetch)

        assertThrows<IllegalArgumentException> {
            layoutDesignDao.update(
                design.id,
                LayoutDesignSaveRequest(
                    name = FreeText("boo too far"),
                    estimatedCompletion = design.estimatedCompletion,
                    designState = design.designState
                )
            )
        }
        assertThrows<IllegalArgumentException> {
            layoutDesignDao.update(
                design.id,
                LayoutDesignSaveRequest(
                    name = FreeText("BOO TOO FAR"),
                    estimatedCompletion = design.estimatedCompletion,
                    designState = design.designState
                )
            )
        }
        assertDoesNotThrow {
            layoutDesignDao.update(
                design.id,
                LayoutDesignSaveRequest(
                    name = FreeText("way too far"),
                    estimatedCompletion = design.estimatedCompletion,
                    designState = design.designState
                )
            )
        }
    }

    @Test
    fun `list() respects includeCompletedAndDeleted`() {
        val design1 = layoutDesignDao.insert(layoutDesignSaveRequest("foo bar", designState = DesignState.ACTIVE)).let(layoutDesignDao::fetch)
        val design2 = layoutDesignDao.insert(layoutDesignSaveRequest("aa bee see", designState = DesignState.DELETED)).let(layoutDesignDao::fetch)
        val design3 = layoutDesignDao.insert(layoutDesignSaveRequest("aa bee dee", designState = DesignState.COMPLETED)).let(layoutDesignDao::fetch)

        assertEquals(
            listOf(
                design1,
            ),
            layoutDesignDao.list()
        )

        val listWithDeleted = layoutDesignDao.list(includeDeleted = true)
        assertContains(listWithDeleted, design1)
        assertContains(listWithDeleted, design2)
        assertEquals(2, listWithDeleted.size)

        val listWithCompleted = layoutDesignDao.list(includeCompleted = true)
        assertContains(listWithCompleted, design1)
        assertContains(listWithCompleted, design3)
        assertEquals(2, listWithCompleted.size)

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
    ) = LayoutDesignSaveRequest(FreeText(name), estimatedCompletion, designState)
}
