package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geography.FIN_GK25_SRID
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import java.math.BigDecimal
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class PlanLayoutCacheIT @Autowired constructor(private val planlayoutCache: PlanLayoutCache) : DBTestBase() {

    @Test
    fun `plan with km posts in layout coordinates can be converted to layout`() {
        val plan =
            plan(
                srid = LAYOUT_SRID,
                kmPosts =
                    listOf(
                        GeometryKmPost(
                            staBack = BigDecimal("1003.440894"),
                            staAhead = BigDecimal("854.711894"),
                            staInternal = BigDecimal("854.711894"),
                            kmNumber = KmNumber(1),
                            description = PlanElementName("1"),
                            state = PlanState.PROPOSED,
                            location = Point(x = 395044.761, y = 6693887.738),
                        )
                    ),
            )
        val layout = planlayoutCache.transformToLayoutPlan(plan).first!!
        val kmPost = layout.kmPosts[0]
        val gkLocation = kmPost.gkLocation!!
        assertEquals(25505351.859, gkLocation.location.x, 0.001)
        assertEquals(6695054.460, gkLocation.location.y, 0.001)
        assertEquals(FIN_GK25_SRID, gkLocation.location.srid)
    }

    @Test
    fun `plan with km posts in KKJ coordinates can be converted to layout`() {
        val kkjSrid = Srid(2392)
        val kmPostKkjLocation = Point(2560707.529, 6695694.106)
        val plan =
            plan(
                srid = kkjSrid,
                alignments =
                    listOf(
                        geometryAlignment(
                            geometryLine(start = kmPostKkjLocation - Point(10.0, 10.0), end = kmPostKkjLocation),
                            geometryLine(start = kmPostKkjLocation, end = kmPostKkjLocation + Point(10.0, 5.0)),
                        )
                    ),
                kmPosts =
                    listOf(
                        GeometryKmPost(
                            staBack = BigDecimal("1003.440894"),
                            staAhead = BigDecimal("854.711894"),
                            staInternal = BigDecimal("854.711894"),
                            kmNumber = KmNumber(1),
                            description = PlanElementName("1"),
                            state = PlanState.PROPOSED,
                            location = kmPostKkjLocation,
                        )
                    ),
            )
        val layout = planlayoutCache.transformToLayoutPlan(plan).first!!
        val kmPost = layout.kmPosts[0]
        val gkLocation = kmPost.gkLocation!!
        assertEquals(25505351.859, gkLocation.location.x, 0.001)
        assertEquals(6695054.460, gkLocation.location.y, 0.001)
        assertEquals(FIN_GK25_SRID, gkLocation.location.srid)
    }
}
