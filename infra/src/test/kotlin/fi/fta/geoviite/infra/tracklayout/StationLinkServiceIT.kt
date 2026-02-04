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
    fun `getStationLinks returns correct links`() {
        val moment0 = testDBService.getDbTime()
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
        stationLinkService.getStationLinks(mainOfficialContext.context).also { links -> assertTrue(links.isEmpty()) }

        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        val op1Version = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val op2Version = mainOfficialContext.save(operationalPoint("OP2", location = Point(50.0, 0.0)))
        val op3Version = mainOfficialContext.save(operationalPoint("OP3", location = Point(80.0, 0.0)))

        // The switches connect to the operational points with the same number
        val switch1Id = mainOfficialContext.save(switch(operationalPointId = op1Version.id)).id
        val switch2Id = mainOfficialContext.save(switch(operationalPointId = op2Version.id)).id
        val switch3Id = mainOfficialContext.save(switch(operationalPointId = op3Version.id)).id

        // Track 1 does links 1-2 and 2-3
        val track1Version =
            mainOfficialContext.save(
                locationTrack(tnVersion.id),
                trackGeometry(
                    // Edge 1 goes from switch1 to switch2 (not switch-internal geometry)
                    edge(
                        startOuterSwitch = switchLinkYV(switch1Id, 3),
                        endOuterSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(30.0, 5.0), Point(40.0, 5.0), calc = M_CALC.LAYOUT)),
                    ),
                    // Edge 2 is switch2 internal geometry
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 1),
                        endInnerSwitch = switchLinkYV(switch2Id, 2),
                        segments = listOf(segment(Point(40.0, 5.0), Point(45.0, 5.0), calc = M_CALC.LAYOUT)),
                    ),
                    // Edge 3 goes from switch2 to switch3 (not switch-internal geometry)
                    edge(
                        startOuterSwitch = switchLinkYV(switch2Id, 2),
                        endOuterSwitch = switchLinkYV(switch3Id, 1),
                        segments = listOf(segment(Point(45.0, 5.0), Point(85.0, 5.0), calc = M_CALC.LAYOUT)),
                    ),
                ),
            )

        // Track 2 does the 2-3 link only and is shorter that the same link on track 1
        val track2Version =
            mainOfficialContext.save(
                locationTrack(tnVersion.id),
                trackGeometry(
                    // The only edge goes from switch2 to switch3 (not switch-internal geometry)
                    edge(
                        startOuterSwitch = switchLinkYV(switch2Id, 2),
                        endOuterSwitch = switchLinkYV(switch3Id, 1),
                        segments = listOf(segment(Point(47.0, 2.0), Point(83.0, 2.0), calc = M_CALC.LAYOUT)),
                    )
                ),
            )

        val moment1 = testDBService.getDbTime()
        val momentLinks = stationLinkService.getStationLinks(LayoutBranch.main, moment1)
        momentLinks.also { links ->
            // Assert correct links are found, ignoring length (due to floating-point comparison)
            assertEquals(
                listOf(
                    StationLink(tnVersion, op1Version, op2Version, listOf(track1Version), 0.0),
                    StationLink(tnVersion, op2Version, op3Version, listOf(track1Version, track2Version), 0.0),
                ),
                links.map { it.copy(length = 0.0) },
            )
            // The first link length should be along track 1 as that's the only connection
            assertEquals(
                // Direct line from OP1 to closest point on track1
                calculateDistance(LAYOUT_SRID, Point(20.0, 0.0), Point(30.0, 5.0)) +
                    // From there, along the track to the location that is closest to OP2
                    calculateDistance(LAYOUT_SRID, Point(30.0, 5.0), Point(50.0, 5.0)) +
                    // From there, direct line from there to OP2
                    calculateDistance(LAYOUT_SRID, Point(50.0, 5.0), Point(50.0, 0.0)),
                links[0].length,
                LAYOUT_M_DELTA,
            )
            // The second link length should be along the shorter one: track 2 (only edge)
            assertEquals(
                // Direct line from OP2 to closest point on track2
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(50.0, 2.0)) +
                    // From there, along the track to the location that is closest to OP3
                    calculateDistance(LAYOUT_SRID, Point(50.0, 2.0), Point(80.0, 2.0)) +
                    // From there, direct line from there to OP2
                    calculateDistance(LAYOUT_SRID, Point(80.0, 2.0), Point(80.0, 0.0)),
                links[1].length,
                LAYOUT_M_DELTA,
            )
        }

        // Verify that the context-based fetch returns the exact same results as moment-based fetch
        val contextLinks = stationLinkService.getStationLinks(mainOfficialContext.context)
        assertEquals(momentLinks, contextLinks)

        // Verify that the 0-moment links is still empty
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
    }
}
