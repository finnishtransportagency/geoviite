package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Polygon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class OperationalPointServiceIT @Autowired constructor(private val operationalPointService: OperationalPointService) :
    DBTestBase() {
    @BeforeEach
    fun cleanup() {
        testDBService.deleteFromTables(
            "layout",
            "operational_point_id",
            "operational_point_external_id",
            "operational_point_version",
            "operational_point",
        )
    }

    @Test
    fun `list() can find points by id or bbox or both`() {
        val a = operationalPointService.insert(LayoutBranch.main, saveRequest("a"))
        val b = operationalPointService.insert(LayoutBranch.main, saveRequest("b"))
        operationalPointService.insert(LayoutBranch.main, saveRequest("c"))
        assertEquals(
            listOf(a, b),
            operationalPointService
                .list(mainDraftContext.context, ids = listOf(a.id, b.id))
                .sortedBy { it.name.toString() }
                .map { it.version },
        )
        operationalPointService.updateLocation(LayoutBranch.main, a.id, Point(123.0, 456.0))
        operationalPointService.updateLocation(LayoutBranch.main, b.id, Point(223.0, 456.0))

        assertEquals(
            listOf(a.id),
            operationalPointService
                .list(mainDraftContext.context, bbox = BoundingBox(100.0..200.0, 400.0..500.0))
                .sortedBy { it.name.toString() }
                .map { it.id },
        )

        assertEquals(
            listOf(a.id),
            operationalPointService
                .list(
                    mainDraftContext.context,
                    ids = listOf(a.id, b.id),
                    bbox = BoundingBox(100.0..200.0, 400.0..500.0),
                )
                .sortedBy { it.name.toString() }
                .map { it.id },
        )

        assertEquals(
            listOf<DomainId<OperationalPoint>>(),
            operationalPointService
                .list(mainDraftContext.context, ids = listOf(b.id), bbox = BoundingBox(100.0..200.0, 400.0..500.0))
                .sortedBy { it.name.toString() }
                .map { it.id },
        )
    }

    @Test
    fun `locations and areas can be saved`() {
        val a = operationalPointService.insert(LayoutBranch.main, saveRequest("a")).id
        val location = Point(123.4, 567.8)
        val area =
            Polygon(
                Point(100.0, 550.0),
                Point(130.0, 550.0),
                Point(130.0, 580.0),
                Point(100.0, 580.0),
                Point(100.0, 550.0),
            )
        operationalPointService.updateLocation(LayoutBranch.main, a, location)
        operationalPointService.updatePolygon(LayoutBranch.main, a, area)
        val updated = operationalPointService.get(mainDraftContext.context, a)!!
        assertEquals(location, updated.location)
        assertEquals(area, updated.polygon)
    }

    private fun saveRequest(
        name: String = "name",
        abbreviation: String = name,
        rinfType: Int = 10,
        state: OperationalPointState = OperationalPointState.IN_USE,
        uicCode: String = "10101",
    ) =
        InternalOperationalPointSaveRequest(
            OperationalPointName(name),
            OperationalPointAbbreviation(abbreviation),
            rinfType,
            state,
            UicCode(uicCode),
        )
}
