package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.Point
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class StationLinkServiceIT @Autowired constructor(private val stationLinkService: StationLinkService) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `getStationLinks returns correct links when fetched with timestamp`() {
        val moment0 = testDBService.getDbTime()
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }

        val tnId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(
                    referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
                )
                .id
        val op1Id = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0))).id
        val op2Id = mainOfficialContext.save(operationalPoint("OP2", location = Point(50.0, 0.0))).id
        val op3Id = mainOfficialContext.save(operationalPoint("OP3", location = Point(80.0, 0.0))).id

        val switch1Id = mainOfficialContext.save(switch(operationalPointId = op1Id)).id
        val switch2Id = mainOfficialContext.save(switch(operationalPointId = op2Id)).id
        val switch3Id = mainOfficialContext.save(switch(operationalPointId = op3Id)).id

        // Track 1 does links 1-2 and 2-3
        val track1Id =
            mainOfficialContext
                .save(
                    locationTrack(tnId),
                    trackGeometry(
                        edge(
                            startOuterSwitch = switchLinkYV(switch1Id, 3),
                            endOuterSwitch = switchLinkYV(switch2Id, 1),
                            segments = listOf(segment(Point(30.0, 5.0), Point(40.0, 5.0))),
                        ),
                        edge(
                            startInnerSwitch = switchLinkYV(switch2Id, 1),
                            endInnerSwitch = switchLinkYV(switch2Id, 2),
                            segments = listOf(segment(Point(40.0, 5.0), Point(45.0, 5.0))),
                        ),
                        edge(
                            startOuterSwitch = switchLinkYV(switch2Id, 2),
                            endOuterSwitch = switchLinkYV(switch3Id, 1),
                            segments = listOf(segment(Point(45.0, 5.0), Point(85.0, 5.0))),
                        ),
                    ),
                )
                .id

        // Track 2 does the 2-3 link only and is shorter that the same link on track 1
        val track2Id =
            mainOfficialContext
                .save(
                    locationTrack(tnId),
                    trackGeometry(
                        edge(
                            startOuterSwitch = switchLinkYV(switch2Id, 2),
                            endOuterSwitch = switchLinkYV(switch3Id, 1),
                            segments = listOf(segment(Point(46.0, 5.0), Point(84.0, 5.0))),
                        )
                    ),
                )
                .id

        val moment1 = testDBService.getDbTime()
        stationLinkService.getStationLinks(LayoutBranch.main, moment1).also { links ->
            // Assert correct links are found, ignoring length (due to floating-point comparison)
            assertEquals(
                listOf(
                    StationLink(tnId, op1Id, op2Id, listOf(track1Id), 0.0),
                    StationLink(tnId, op2Id, op3Id, listOf(track1Id, track2Id), 0.0),
                ),
                links.map { it.copy(length = 0.0) },
            )
            // Assert correct lengths
            assertEquals(
                10.0 +
                    calculateDistance(LAYOUT_SRID, Point(30.0, 5.0), Point(20.0, 0.0)) +
                    calculateDistance(LAYOUT_SRID, Point(40.0, 5.0), Point(50.0, 0.0)),
                links[0].length,
                LAYOUT_M_DELTA,
            )
            assertEquals(
                38.0 +
                    calculateDistance(LAYOUT_SRID, Point(46.0, 5.0), Point(50.0, 0.0)) +
                    calculateDistance(LAYOUT_SRID, Point(84.0, 5.0), Point(80.0, 0.0)),
                links[1].length,
                LAYOUT_M_DELTA,
            )
        }

        // Verify that the 0-moment links is still empty
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
    }
}
