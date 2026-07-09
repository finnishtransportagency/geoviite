package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtRoutingIT @Autowired constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) :
    DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Simple route on a single track returns correct section with sijainti_raiteella endpoints`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 1000.0)
        val (trackNumberId, trackNumberOid) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(start, end)),
            )
        val (trackId, trackOid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment(start, end)))
        val publication = testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        val response = api.routing.get(startX = 0.0, startY = 100.0, endX = 0.0, endY = 900.0)

        assertEquals(publication.uuid.toString(), response.rataverkon_versio)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
        assertTrue(response.reitti.pituus > 0.0)

        val section = response.reitti.reitin_osat.single()
        assertEquals(trackOid.toString(), section.sijaintiraide_oid)
        assertEquals(trackNumberOid.toString(), section.ratanumero_oid)
        assertEquals("nouseva", section.suunta)
        assertTrue(section.pituus > 0.0)

        assertEquals("sijainti_raiteella", section.alku.tyyppi)
        assertNull(section.alku.vaihde_oid)
        assertNotNull(section.alku.rataosoite)
        assertTrue(section.alku.m_arvo > 0.0)

        assertEquals("sijainti_raiteella", section.loppu.tyyppi)
        assertNull(section.loppu.vaihde_oid)
        assertNotNull(section.loppu.rataosoite)
        assertTrue(section.loppu.m_arvo > section.alku.m_arvo)
    }

    @Test
    fun `Route starting at the beginning of a track produces raiteen_paa start endpoint`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 1000.0)
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(start, end)),
            )
        val (trackId, _) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment(start, end)))
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        // Start exactly at track origin → m=0 → raiteen_pää
        val response = api.routing.get(startX = 0.0, startY = 0.0, endX = 0.0, endY = 500.0)

        val section = response.reitti.reitin_osat.single()
        assertEquals("raiteen_pää", section.alku.tyyppi)
        assertEquals("sijainti_raiteella", section.loppu.tyyppi)
    }

    @Test
    fun `Route through a switch produces vaihde endpoints at the switch`() {
        val structure = switchStructureYV60_300_1_9()
        val joint1 = switchJoint(1, Point(0.0, 0.0))
        val joint2 = switchJoint(2, Point(100.0, 0.0))
        val ids = extTestDataService.insertSwitchAndTracks(mainDraftContext, listOf(joint1 to joint2), structure)
        extTestDataService.publishInMain(listOf(ids))

        // Route from a point on track[0] through the switch to a point on the same track
        // Since both tracks share the same topology, route from before to after the switch
        val response = api.routing.get(startX = 0.0, startY = 0.0, endX = 100.0, endY = 0.0)

        assertTrue(response.reitti.reitin_osat.isNotEmpty())
        val endpointsWithVaihde =
            response.reitti.reitin_osat.flatMap { listOf(it.alku, it.loppu) }.filter { it.tyyppi == "vaihde" }
        assertTrue(endpointsWithVaihde.isNotEmpty(), "Expected at least one vaihde endpoint")
        endpointsWithVaihde.forEach { endpoint -> assertNotNull(endpoint.vaihde_oid) }
    }

    @Test
    fun `Returns 204 when start coordinates are too far from any track`() {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 1000.0)
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(start, end)),
            )
        val (trackId, _) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment(start, end)))
        testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))

        // These coordinates are far from the track
        api.routing.assertNoRoute(startX = 999999.0, startY = 999999.0, endX = 0.0, endY = 500.0)
    }

    @Test
    fun `Returns 400 for invalid rataverkon_versio format`() {
        api.routing.getWithExpectedError(
            startX = 0.0,
            startY = 0.0,
            endX = 0.0,
            endY = 100.0,
            TRACK_LAYOUT_VERSION to "not-a-uuid",
            httpStatus = HttpStatus.BAD_REQUEST,
        )
    }

    @Test
    fun `Returns 404 for non-existing rataverkon_versio`() {
        testDBService.publish() // ensure at least one publication exists
        api.routing.getWithExpectedError(
            startX = 0.0,
            startY = 0.0,
            endX = 0.0,
            endY = 100.0,
            TRACK_LAYOUT_VERSION to "00000000-0000-0000-0000-000000000000",
            httpStatus = HttpStatus.NOT_FOUND,
        )
    }
}
