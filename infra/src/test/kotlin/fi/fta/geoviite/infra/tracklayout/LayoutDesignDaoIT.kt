package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutDesignDaoIT @Autowired constructor(private val layoutDesignDao: LayoutDesignDao): DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "design")
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
                insertAsIs
            ), layoutDesignDao.list().sortedBy(LayoutDesign::name)
        )
    }

    private fun layoutDesignSaveRequest(
        name: String,
        estimatedCompletion: LocalDate = LocalDate.parse("2035-01-02"),
        designState: DesignState = DesignState.ACTIVE,
    ) = LayoutDesignSaveRequest(FreeText(name), estimatedCompletion, designState)
}
