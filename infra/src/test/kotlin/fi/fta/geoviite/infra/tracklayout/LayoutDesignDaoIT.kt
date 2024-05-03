package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.geometry.PlanPhase
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
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
    fun `insert, update and list work`() {
        val abcId = layoutDesignDao.insert(layoutDesign("aa bee see"))
        val insertAsIs = layoutDesign("foo bar")
        val insertAsIsId = layoutDesignDao.insert(insertAsIs)
        val update = layoutDesign(
            "abc",
            abcId,
            LocalDate.parse("2024-01-01"),
            PlanPhase.RAILWAY_CONSTRUCTION_PLAN,
            DesignState.COMPLETED,
        )
        layoutDesignDao.update(update)
        assertEquals(
            listOf(update, insertAsIs.copy(id = insertAsIsId)), layoutDesignDao.list().sortedBy(LayoutDesign::name)
        )
    }

    private fun layoutDesign(
        name: String,
        id: DomainId<LayoutDesign> = StringId(),
        estimatedCompletion: LocalDate = LocalDate.parse("2035-01-02"),
        planPhase: PlanPhase = PlanPhase.RAILWAY_PLAN,
        designState: DesignState = DesignState.ACTIVE,
    ) = LayoutDesign(id, FreeText(name), estimatedCompletion, planPhase, designState)
}
