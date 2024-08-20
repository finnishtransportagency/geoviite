package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.math.lineLength
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.util.getPoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LinkingDaoIT @Autowired constructor(
    private val linkingDao: LinkingDao,
) : DBTestBase() {

    @Test
    fun noSwitchBoundsAreFoundWhenNotLinkedToTracks() {
        val switchId = mainDraftContext.createSwitch().id
        assertEquals(null, linkingDao.getSwitchBoundsFromTracks(MainLayoutContext.official, switchId))
        assertEquals(null, linkingDao.getSwitchBoundsFromTracks(MainLayoutContext.draft, switchId))
    }

    @Test
    fun switchBoundsAreFoundFromTracks() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val switchId = mainDraftContext.createSwitch().id

        val point1 = Point(10.0, 10.0)
        val point2 = Point(12.0, 10.0)
        val point3_1 = Point(10.0, 12.0)
        val point3_2 = Point(10.0, 13.0)

        // Linked from the start only -> second point shouldn't matter
        mainDraftContext.insert(
            locationTrack(
                trackNumberId = tnId,
                topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
            ),
            alignment(segment(point1, point1 + Point(5.0, 5.0))),
        )
        // Linked from the end only -> first point shouldn't matter
        mainDraftContext.insert(
            locationTrack(
                trackNumberId = tnId,
                topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(2)),
            ),
            alignment(segment(point2 - Point(5.0, 5.0), point2)),
        )
        // Linked by segment ends -> both points matter
        mainDraftContext.insert(
            locationTrack(tnId),
            alignment(
                segment(
                    point3_1, point3_2,
                    switchId = switchId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                )
            ),
        )
        assertEquals(
            null,
            linkingDao.getSwitchBoundsFromTracks(MainLayoutContext.official, switchId),
        )
        assertEquals(
            boundingBoxAroundPoints(point1, point2, point3_1, point3_2),
            linkingDao.getSwitchBoundsFromTracks(MainLayoutContext.draft, switchId),
        )
    }

    data class TriangulationPoint(
        val kkjPoint: Point,
        val kkjPointTransformed: Point,
        val tm35Point: Point,
    )

    @Test
    fun triangulationNetworkIsSane() {
        val sql = """
            select  
              postgis.st_x(coord_kkj) kkj_x,
              postgis.st_y(coord_kkj) kkj_y,
              postgis.st_x(postgis.st_transform(coord_kkj, 3067)) kkj_x_transformed,
              postgis.st_y(postgis.st_transform(coord_kkj, 3067)) kkj_y_transformed,
              postgis.st_x(coord_etrs) tm35_x,
              postgis.st_y(coord_etrs) tm35_y
            from common.kkj_etrs_triangle_corner_point
        """.trimIndent()
        val points = jdbc.query(sql, mapOf<String, Any>()) { rs, _ -> TriangulationPoint(
            kkjPoint = rs.getPoint("kkj_x", "kkj_y"),
            kkjPointTransformed = rs.getPoint("kkj_x_transformed", "kkj_y_transformed"),
            tm35Point = rs.getPoint("tm35_x", "tm35_y"),
        ) }

        assertFalse(points.isEmpty())
        points.forEach { point ->
            val diff = lineLength(point.kkjPointTransformed, point.tm35Point)
            assertTrue(
                diff < 10, // This needs a little slack, since postgis transform isn't accurate. That's why the network exists.
                "Triangulation corner points should be (approximately) the same in both coordinate systems: diff=$diff point=$point",
            )
        }
    }
}
