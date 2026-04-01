package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.ratko.model.OperationalPointRatoType
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
    fun `getStationLinks returns correct links when connected through switches`() {
        val moment0 = testDBService.getDbTime()
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
        stationLinkService.getStationLinks(mainOfficialContext.context).also { links -> assertTrue(links.isEmpty()) }

        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        val op1 = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val op1Address = TrackMeter("0000+0020.000")
        val op2 = mainOfficialContext.save(operationalPoint("OP2", location = Point(50.0, 0.0)))
        val op2Address = TrackMeter("0000+0050.000")
        val op3 = mainOfficialContext.save(operationalPoint("OP3", location = Point(80.0, 0.0)))
        val op3Address = TrackMeter("0000+0080.000")

        // The switches connect to the operational points with the same number
        val switch1Id = mainOfficialContext.save(switch(operationalPointId = op1.id)).id
        val switch2Id = mainOfficialContext.save(switch(operationalPointId = op2.id)).id
        val switch3Id = mainOfficialContext.save(switch(operationalPointId = op3.id)).id

        // Track 1 does links 1-2 and 2-3
        val track1 =
            mainOfficialContext.save(
                locationTrack(tnVersion.id),
                trackGeometry(
                    // Edge 1 goes from switch1 to switch2 (not switch-internal geometry), connecting op1 to op2
                    edge(
                        startOuterSwitch = switchLinkYV(switch1Id, 3),
                        endOuterSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(Point(15.0, 5.0), Point(45.0, 5.0), calc = M_CALC.LAYOUT)),
                    ),
                    // Edge 2 is switch2 internal geometry
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 1),
                        endInnerSwitch = switchLinkYV(switch2Id, 2),
                        segments = listOf(segment(Point(45.0, 5.0), Point(55.0, 5.0), calc = M_CALC.LAYOUT)),
                    ),
                    // Edge 3 goes from switch2 to switch3 (not switch-internal geometry), connecting op2 to op3
                    // Make this one a little longer by adjusting the angle
                    edge(
                        startOuterSwitch = switchLinkYV(switch2Id, 2),
                        endOuterSwitch = switchLinkYV(switch3Id, 1),
                        segments = listOf(segment(Point(55.0, 5.0), Point(85.0, 15.0), calc = M_CALC.LAYOUT)),
                    ),
                ),
            )

        // Track 2 does the 2-3 link only and is shorter that the same link on track 1
        val track2 =
            mainOfficialContext.save(
                locationTrack(tnVersion.id),
                trackGeometry(
                    // The only edge goes from switch2 to switch3 (not switch-internal geometry)
                    edge(
                        startOuterSwitch = switchLinkYV(switch2Id, 2),
                        endOuterSwitch = switchLinkYV(switch3Id, 1),
                        segments = listOf(segment(Point(45.0, 2.0), Point(95.0, 2.0), calc = M_CALC.LAYOUT)),
                    )
                ),
            )

        // Create a publication to generate a reference-point for routing cache
        testDBService.createPublication()

        // Time after data init
        val moment1 = testDBService.getDbTime()

        val links = stationLinkService.getStationLinks(mainOfficialContext.context)
        links.also { links ->
            // Assert correct links are found, ignoring length (due to floating-point comparison)
            assertEquals(
                listOf(
                    StationLink(tnVersion, op1, op2, listOf(track1), op1Address, op2Address, 0.0),
                    StationLink(tnVersion, op2, op3, listOf(track1, track2), op2Address, op3Address, 0.0),
                ),
                links.map { it.copy(length = 0.0) },
            )
            // The first link length should be along track 1 as that's the only connection
            assertEquals(
                calculateDistance(LAYOUT_SRID, Point(20.0, 5.0), Point(50.0, 5.0)),
                links[0].length,
                LAYOUT_M_DELTA,
            )
            // The second link length should be along the shorter one: track 2 (only edge)
            assertEquals(
                // Track 2 covers the whole distance from OP2 to OP3
                calculateDistance(LAYOUT_SRID, Point(50.0, 2.0), Point(80.0, 2.0)),
                links[1].length,
                LAYOUT_M_DELTA,
            )
        }

        // Verify that the context-based fetch returns the exact same results as moment-based fetch
        assertEquals(links, stationLinkService.getStationLinks(LayoutBranch.main, moment1))

        // Verify that the 0-moment links is still empty
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
    }

    @Test
    fun `getStationLinks returns correct links for tracks directly connected to operational points`() {
        val moment0 = testDBService.getDbTime()
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
        stationLinkService.getStationLinks(mainOfficialContext.context).also { links -> assertTrue(links.isEmpty()) }

        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        val op1 = mainOfficialContext.save(operationalPoint("OP1", location = Point(10.0, 0.0)))
        val op1Address = TrackMeter("0000+0010.000")
        val op2 = mainOfficialContext.save(operationalPoint("OP2", location = Point(50.0, 0.0)))
        val op2Address = TrackMeter("0000+0050.000")
        val op3 = mainOfficialContext.save(operationalPoint("OP3", location = Point(90.0, 0.0)))
        val op3Address = TrackMeter("0000+0090.000")

        val track1 =
            mainOfficialContext.save(
                locationTrack(trackNumberId = tnVersion.id, operationalPointIds = setOf(op1.id, op2.id)),
                trackGeometry(edge(segments = listOf(segment(Point(5.0, 2.0), Point(55.0, 2.0), calc = M_CALC.LAYOUT)))),
            )

        // Track 2 connects OP2 and OP3 directly (no switches)
        val track2 =
            mainOfficialContext.save(
                locationTrack(trackNumberId = tnVersion.id, operationalPointIds = setOf(op2.id, op3.id)),
                trackGeometry(
                    edge(segments = listOf(segment(Point(45.0, 3.0), Point(95.0, 3.0), calc = M_CALC.LAYOUT)))
                ),
            )

        // Create a publication to generate a reference-point for routing cache
        testDBService.createPublication()

        // Time after data init
        val moment1 = testDBService.getDbTime()

        val links = stationLinkService.getStationLinks(mainOfficialContext.context)
        links.also { links ->
            // Assert correct links are found, ignoring length (due to floating-point comparison)
            assertEquals(
                listOf(
                    StationLink(tnVersion, op1, op2, listOf(track1), op1Address, op2Address, 0.0),
                    StationLink(tnVersion, op2, op3, listOf(track2), op2Address, op3Address, 0.0),
                ),
                links.map { it.copy(length = 0.0) },
            )
            // Verify length calculation for link 1 (OP1 to OP2)
            assertEquals(
                calculateDistance(LAYOUT_SRID, Point(10.0, 2.0), Point(50.0, 2.0)),
                links[0].length,
                LAYOUT_M_DELTA,
            )
            // Verify length calculation for link 2 (OP2 to OP3)
            assertEquals(
                calculateDistance(LAYOUT_SRID, Point(50.0, 3.0), Point(90.0, 3.0)),
                links[1].length,
                LAYOUT_M_DELTA,
            )
        }

        // Verify that the context-based fetch returns the exact same results as moment-based fetch
        assertEquals(links, stationLinkService.getStationLinks(LayoutBranch.main, moment1))

        // Verify that the 0-moment links is still empty
        stationLinkService.getStationLinks(LayoutBranch.main, moment0).also { links -> assertTrue(links.isEmpty()) }
    }

    @Test
    fun `getStationLinks returns correct links when the track doesn't cover the whole way`() {
        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(200.0, 0.0))
            )
        val op1Version = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val op1Address = TrackMeter("0000+0020.000")
        val op2Version = mainOfficialContext.save(operationalPoint("OP2", location = Point(180.0, 0.0)))
        val op2Address = TrackMeter("0000+0180.000")

        // The switches connect to the operational points with the same number
        val structure = switchStructureYV60_300_1_9()
        val switch1Id = mainOfficialContext.save(switch(structure.id, operationalPointId = op1Version.id)).id
        val switch2Id = mainOfficialContext.save(switch(structure.id, operationalPointId = op2Version.id)).id

        // Use switch joint locations for segments to get consistent length calculation:
        // |-- extraTrack1 -- <J1 switch1 J2> -- connectingTrack -- <J1 switch2 J2> -- extraTrack2 --|
        val start = Point(0.0, 5.0)
        val switch1Joint1Location = Point(30.0, 5.0)
        val switch1Joint2Location = switch1Joint1Location + structure.getJointLocation(JointNumber(2))
        val switch2Joint2Location = Point(170.0, 5.0)
        val switch2Joint1Location = switch2Joint2Location - structure.getJointLocation(JointNumber(2))
        val end = Point(200.0, 5.0)

        // We need a track from OP1 main location to the closest point on connecting track
        // It's not actually a connecting track itself (won't be mentioned in station-links)
        mainOfficialContext.save(
            locationTrack(tnVersion.id),
            trackGeometry(
                edge(
                    endOuterSwitch = switchLinkYV(switch1Id, 1),
                    segments = listOf(segment(start, switch1Joint1Location, calc = M_CALC.LAYOUT)),
                )
            ),
        )

        val connectingTrack =
            mainOfficialContext.save(
                locationTrack(tnVersion.id),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(switch1Id, 1),
                        endInnerSwitch = switchLinkYV(switch1Id, 2),
                        // Make segment length match the structure alignment for consistent length calculation
                        segments = listOf(segment(switch1Joint1Location, switch1Joint2Location, calc = M_CALC.LAYOUT)),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(switch1Id, 2),
                        endOuterSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(switch1Joint2Location, switch2Joint1Location, calc = M_CALC.LAYOUT)),
                    ),
                    edge(
                        startInnerSwitch = switchLinkYV(switch2Id, 1),
                        endInnerSwitch = switchLinkYV(switch2Id, 2),
                        segments = listOf(segment(switch2Joint1Location, switch2Joint2Location, calc = M_CALC.LAYOUT)),
                    ),
                ),
            )

        // A second extra track to complete the route to OP2 by
        mainOfficialContext.save(
            locationTrack(tnVersion.id),
            trackGeometry(
                // The only edge goes from switch2 to switch3 (not switch-internal geometry)
                edge(
                    startOuterSwitch = switchLinkYV(switch2Id, 2),
                    segments = listOf(segment(switch2Joint2Location, end, calc = M_CALC.LAYOUT)),
                )
            ),
        )

        // Create a publication to generate a reference-point for routing cache
        testDBService.createPublication()

        stationLinkService.getStationLinks(MainLayoutContext.official).also { links ->
            // Assert correct links are found, ignoring length (due to floating-point comparison)
            assertEquals(
                listOf(
                    StationLink(tnVersion, op1Version, op2Version, listOf(connectingTrack), op1Address, op2Address, 0.0)
                ),
                links.map { it.copy(length = 0.0) },
            )
            // Verify link length (longer than the connecting track!)
            assertEquals(
                calculateDistance(LAYOUT_SRID, Point(20.0, 5.0), Point(180.0, 5.0)),
                links[0].length,
                LAYOUT_M_DELTA,
            )
        }
    }

    @Test
    fun `getStationLinks does not produce links for OLP-type operational points`() {
        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        val op1 = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val olp =
            mainOfficialContext.save(
                operationalPoint("OLP1", location = Point(50.0, 0.0), ratoType = OperationalPointRatoType.OLP)
            )
        val op2 = mainOfficialContext.save(operationalPoint("OP2", location = Point(80.0, 0.0)))

        // Track connects OP1, OLP, and OP2 directly
        mainOfficialContext.save(
            locationTrack(trackNumberId = tnVersion.id, operationalPointIds = setOf(op1.id, olp.id, op2.id)),
            trackGeometry(edge(segments = listOf(segment(Point(15.0, 2.0), Point(85.0, 2.0), calc = M_CALC.LAYOUT)))),
        )

        testDBService.createPublication()

        stationLinkService.getStationLinks(mainOfficialContext.context).also { links ->
            // Only the OP1-OP2 link should exist; OLP should be excluded entirely
            assertEquals(
                listOf(op1 to op2),
                links.map { it.startOperationalPointVersion to it.endOperationalPointVersion },
            )
        }

        // Verify moment-based fetch also excludes OLP
        stationLinkService.getStationLinks(LayoutBranch.main, testDBService.getDbTime()).also { links ->
            // Only the OP1-OP2 link should exist; OLP should be excluded entirely
            assertEquals(
                listOf(op1 to op2),
                links.map { it.startOperationalPointVersion to it.endOperationalPointVersion },
            )
        }
    }

    @Test
    fun `getStationLinks handles tracks referencing deleted operational points`() {
        val tnVersion =
            mainOfficialContext.createLayoutTrackNumberAndReferenceLine(
                referenceLineGeometryOfPoints(Point(0.0, 0.0), Point(100.0, 0.0))
            )
        val op1 = mainOfficialContext.save(operationalPoint("OP1", location = Point(20.0, 0.0)))
        val deletedOp =
            mainOfficialContext.save(
                operationalPoint("DELETED_OP", location = Point(50.0, 0.0), state = OperationalPointState.DELETED)
            )
        val op2 = mainOfficialContext.save(operationalPoint("OP2", location = Point(80.0, 0.0)))

        // Track references two real OPs and one that has been deleted
        mainOfficialContext.save(
            locationTrack(trackNumberId = tnVersion.id, operationalPointIds = setOf(op1.id, deletedOp.id, op2.id)),
            trackGeometry(edge(segments = listOf(segment(Point(15.0, 2.0), Point(85.0, 2.0), calc = M_CALC.LAYOUT)))),
        )

        testDBService.createPublication()

        // Should not throw and should produce a link between the two existing OPs only
        stationLinkService.getStationLinks(mainOfficialContext.context).also { links ->
            assertEquals(
                listOf(op1 to op2),
                links.map { it.startOperationalPointVersion to it.endOperationalPointVersion },
            )
        }

        // Verify moment-based fetch also handles the broken reference gracefully
        stationLinkService.getStationLinks(LayoutBranch.main, testDBService.getDbTime()).also { links ->
            assertEquals(
                listOf(op1 to op2),
                links.map { it.startOperationalPointVersion to it.endOperationalPointVersion },
            )
        }
    }
}
