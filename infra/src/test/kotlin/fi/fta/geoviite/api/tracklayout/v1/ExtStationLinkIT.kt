package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.TestLayoutContext
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_M_DELTA
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.M_CALC
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.operationalPoint
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtStationLinkIT
@Autowired
constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) : DBTestBase() {
    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Station links API returns correct object versions`() {

        val baseVersion = extTestDataService.publishInMain().uuid
        val baseResponse =
            api.stationLinkCollection.get().also { response ->
                assertEquals(baseVersion.toString(), response.rataverkon_versio)
                assertEquals(emptyList<ExtTestStationLinkV1>(), response.liikennepaikkavalit)
                // Verify that versioned fetch returns the same data
                assertEquals(response, api.stationLinkCollection.getAtVersion(baseVersion))
            }

        initUser() // Re-initUser after the API calls (thread context reset)
        val (tnId, tnOid, trackNumber) = createOfficialTrackNumber(Point(0.0, 0.0), Point(200.0, 0.0))

        // Create two operational points & connected switches
        val (op1Id, op1Oid, op1) = createOperationalPoint(mainDraftContext, "OP1", Point(50.0, 0.0))
        val (op2Id, op2Oid, op2) = createOperationalPoint(mainDraftContext, "OP2", Point(150.0, 0.0))
        val switch1Id = mainDraftContext.save(switch(operationalPointId = op1Id)).id
        val switch2Id = mainDraftContext.save(switch(operationalPointId = op2Id)).id

        // Create connecting track
        val (track1Id, track1Oid) =
            createConnectionTrack(mainDraftContext, tnId, switch1Id, switch2Id, Point(55.0, 0.0), Point(145.0, 0.0))

        val version1 =
            extTestDataService
                .publishInMain(
                    operationalPoints = listOf(op1Id, op2Id),
                    switches = listOf(switch1Id, switch2Id),
                    locationTracks = listOf(track1Id),
                )
                .uuid

        val response1 =
            api.stationLinkCollection.get().also { response ->
                assertEquals(version1.toString(), response.rataverkon_versio)
                assertEquals(1, response.liikennepaikkavalit.size)
                // Verify the created station link
                assertStationLinkMatches(
                    response.liikennepaikkavalit[0],
                    tnOid to trackNumber,
                    op1Oid to op1,
                    op2Oid to op2,
                    listOf(track1Oid),
                    calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
                )
                // Verify that versioned fetch returns the same data
                assertEquals(response, api.stationLinkCollection.getAtVersion(version1))
            }

        initUser() // Re-initUser after the API calls (thread context reset)

        // Add another track connecting the same points with a longer track
        val (track2Id, track2Oid) =
            createConnectionTrack(mainDraftContext, tnId, switch1Id, switch2Id, Point(54.0, 1.0), Point(146.0, 1.0))

        val updatedVersion = extTestDataService.publishInMain(locationTracks = listOf(track2Id)).uuid

        // Verify updated state shows both tracks
        api.stationLinkCollection.get().also { response ->
            assertEquals(updatedVersion.toString(), response.rataverkon_versio)
            assertEquals(1, response.liikennepaikkavalit.size)
            // Verify the created station link
            assertStationLinkMatches(
                response.liikennepaikkavalit[0],
                tnOid to trackNumber,
                op1Oid to op1,
                op2Oid to op2,
                listOf(track1Oid, track2Oid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
            // Verify that versioned fetch returns the same data
            assertEquals(response, api.stationLinkCollection.getAtVersion(updatedVersion))
        }

        // Verify old versions remain unchanged
        assertEquals(baseResponse, api.stationLinkCollection.getAtVersion(baseVersion))
        assertEquals(response1, api.stationLinkCollection.getAtVersion(version1))
    }

    @Test
    fun `API does not contain draft links`() {
        val (tnId, tnOid, trackNumber) = createOfficialTrackNumber(Point(0.0, 0.0), Point(200.0, 0.0))

        // Create two operational points & connected switches
        val (op1Id, op1Oid, op1) = createOperationalPoint(mainDraftContext, "OP1", Point(50.0, 0.0))
        val (op2Id, op2Oid, op2) = createOperationalPoint(mainDraftContext, "OP2", Point(150.0, 0.0))
        val switch1Id = mainDraftContext.save(switch(operationalPointId = op1Id)).id
        val switch2Id = mainDraftContext.save(switch(operationalPointId = op2Id)).id

        val baseVersion =
            extTestDataService
                .publishInMain(operationalPoints = listOf(op1Id, op2Id), switches = listOf(switch1Id, switch2Id))
                .uuid

        api.stationLinkCollection.get().also { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            assertEquals(emptyList<ExtTestStationLinkV1>(), response.liikennepaikkavalit)
        }

        initUser() // Re-initUser after the API calls (thread context reset)
        val (trackId, trackOid) =
            createConnectionTrack(mainDraftContext, tnId, switch1Id, switch2Id, Point(50.0, 0.0), Point(150.0, 0.0))

        // Draft track should not appear in API
        api.stationLinkCollection.get().also { response ->
            assertEquals(baseVersion.toString(), response.rataverkon_versio)
            assertEquals(emptyList<ExtTestStationLinkV1>(), response.liikennepaikkavalit)
        }

        initUser() // Re-initUser after the API calls (thread context reset)
        extTestDataService.publishInMain(locationTracks = listOf(trackId)).uuid

        // Now the link should appear
        api.stationLinkCollection.get().also { response ->
            assertEquals(1, response.liikennepaikkavalit.size)
            assertStationLinkMatches(
                response.liikennepaikkavalit[0],
                tnOid to trackNumber,
                op1Oid to op1,
                op2Oid to op2,
                listOf(trackOid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
        }
    }

    @Test
    fun `Deleted links not returned`() {
        val (tnId, tnOid, trackNumber) = createOfficialTrackNumber(Point(0.0, 0.0), Point(200.0, 0.0))

        // Create two operational points & connected switches
        val (op1Id, op1Oid, op1) = createOperationalPoint(mainOfficialContext, "OP1", Point(50.0, 0.0))
        val (op2Id, op2Oid, op2) = createOperationalPoint(mainOfficialContext, "OP2", Point(150.0, 0.0))
        val switch1Id = mainOfficialContext.save(switch(operationalPointId = op1Id)).id
        val switch2Id = mainOfficialContext.save(switch(operationalPointId = op2Id)).id

        val (trackId, trackOid) =
            createConnectionTrack(mainOfficialContext, tnId, switch1Id, switch2Id, Point(55.0, 0.0), Point(145.0, 0.0))

        val baseVersion = extTestDataService.publishInMain().uuid

        // Verify link exists
        api.stationLinkCollection.get().also { response ->
            assertEquals(1, response.liikennepaikkavalit.size)
            assertStationLinkMatches(
                response.liikennepaikkavalit[0],
                tnOid to trackNumber,
                op1Oid to op1,
                op2Oid to op2,
                listOf(trackOid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
        }

        initUser() // Re-initUser after the API calls (thread context reset)
        mainDraftContext.mutate(trackId) { track -> track.copy(state = LocationTrackState.DELETED) }
        val deletedVersion = extTestDataService.publishInMain(locationTracks = listOf(trackId)).uuid

        // Link should no longer appear
        api.stationLinkCollection.get().also { response ->
            assertEquals(deletedVersion.toString(), response.rataverkon_versio)
            assertEquals(emptyList<ExtTestStationLinkV1>(), response.liikennepaikkavalit)
        }

        // Old version should still show the link
        api.stationLinkCollection.getAtVersion(baseVersion).also { response ->
            assertEquals(1, response.liikennepaikkavalit.size)
            assertStationLinkMatches(
                response.liikennepaikkavalit[0],
                tnOid to trackNumber,
                op1Oid to op1,
                op2Oid to op2,
                listOf(trackOid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
        }
    }

    @Test
    fun `API returns correct values for multiple track numbers`() {
        val (tn1Id, tn1Oid, trackNumber1) = createOfficialTrackNumber(Point(0.0, 0.0), Point(200.0, 0.0))
        val (tn2Id, tn2Oid, trackNumber2) = createOfficialTrackNumber(Point(0.0, 0.0), Point(200.0, 0.0))

        val (op1Id, op1Oid, op1) = createOperationalPoint(mainOfficialContext, "OP1", Point(50.0, 0.0))
        val (op2Id, op2Oid, op2) = createOperationalPoint(mainOfficialContext, "OP2", Point(150.0, 0.0))
        val switch1Id = mainOfficialContext.save(switch(operationalPointId = op1Id)).id
        val switch2Id = mainOfficialContext.save(switch(operationalPointId = op2Id)).id

        val (_, tn1Track1Oid) =
            createConnectionTrack(mainOfficialContext, tn1Id, switch1Id, switch2Id, Point(55.0, 0.0), Point(145.0, 0.0))
        val (_, tn1Track2Oid) =
            createConnectionTrack(mainOfficialContext, tn1Id, switch1Id, switch2Id, Point(55.0, 0.0), Point(145.0, 0.0))
        val (_, tn2TrackOid) =
            createConnectionTrack(mainOfficialContext, tn2Id, switch1Id, switch2Id, Point(55.0, 0.0), Point(145.0, 0.0))

        val layoutVersion = extTestDataService.publishInMain().uuid

        // Verify API returns separate link objects for each track number
        api.stationLinkCollection.get().also { response ->
            assertEquals(layoutVersion.toString(), response.rataverkon_versio)
            assertEquals(2, response.liikennepaikkavalit.size)
            assertStationLinkMatches(
                response.liikennepaikkavalit[0],
                tn1Oid to trackNumber1,
                op1Oid to op1,
                op2Oid to op2,
                listOf(tn1Track1Oid, tn1Track2Oid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
            assertStationLinkMatches(
                response.liikennepaikkavalit[1],
                tn2Oid to trackNumber2,
                op1Oid to op1,
                op2Oid to op2,
                listOf(tn2TrackOid),
                calculateDistance(LAYOUT_SRID, Point(50.0, 0.0), Point(150.0, 0.0)),
            )
        }
    }

    private fun assertStationLinkMatches(
        link: ExtTestStationLinkV1,
        expectedTn: Pair<Oid<LayoutTrackNumber>, TrackNumber>,
        expectedStartOp: Pair<Oid<OperationalPoint>, OperationalPoint>,
        expectedEndOp: Pair<Oid<OperationalPoint>, OperationalPoint>,
        expectedTracksOids: List<Oid<LocationTrack>>,
        expectedLength: Double,
    ) {
        assertEquals(expectedTn.first.toString(), link.ratanumero_oid)
        assertEquals(expectedTn.second.toString(), link.ratanumero)

        expectedStartOp.also { (oid, op) -> assertOperationalPointMatches(oid, op, link.alku) }
        expectedEndOp.also { (oid, op) -> assertOperationalPointMatches(oid, op, link.loppu) }

        assertEquals(expectedTracksOids.map { it.toString() }, link.raiteet.map { it.sijaintiraide_oid })

        assertEquals(expectedLength, link.pituus, LAYOUT_M_DELTA)
    }

    private fun assertOperationalPointMatches(
        oid: Oid<OperationalPoint>,
        op: OperationalPoint,
        endPoint: ExtTestStationLinkEndpointV1,
    ) {
        assertEquals(oid.toString(), endPoint.toiminnallinen_piste_oid)
        assertEquals(op.name.toString(), endPoint.nimi)
    }

    private fun createOfficialTrackNumber(
        start: Point,
        end: Point,
    ): Triple<IntId<LayoutTrackNumber>, Oid<LayoutTrackNumber>, TrackNumber> =
        testDBService.getUnusedTrackNumber().let { trackNumber ->
            val geometry = referenceLineGeometry(segment(start, end))
            val tnId = mainOfficialContext.createLayoutTrackNumberAndReferenceLine(geometry, trackNumber).id
            val tnOid = mainOfficialContext.generateOid(tnId)
            return Triple(tnId, tnOid, trackNumber)
        }

    private fun createOperationalPoint(
        context: TestLayoutContext,
        name: String,
        location: Point,
    ): Triple<IntId<OperationalPoint>, Oid<OperationalPoint>, OperationalPoint> =
        context.save(operationalPoint(name, location = location)).let { version ->
            Triple(version.id, context.generateOid(version.id), testDBService.fetch(version))
        }

    private fun createConnectionTrack(
        context: TestLayoutContext,
        tnId: IntId<LayoutTrackNumber>,
        switch1Id: IntId<LayoutSwitch>,
        switch2Id: IntId<LayoutSwitch>,
        start: Point,
        end: Point,
    ): Pair<IntId<LocationTrack>, Oid<LocationTrack>> =
        context
            .save(
                locationTrack(tnId),
                trackGeometry(
                    edge(
                        startOuterSwitch = switchLinkYV(switch1Id, 1),
                        endOuterSwitch = switchLinkYV(switch2Id, 1),
                        segments = listOf(segment(start, end, calc = M_CALC.LAYOUT)),
                    )
                ),
            )
            .let { Pair(it.id, context.generateOid(it.id)) }
}
