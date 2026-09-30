package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpStatus
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

/**
 * Integration test for the topology API.
 *
 * The request contract tests hold already. The nano level graph tests are the TDD targets of GVT-3743: they describe
 * the response that the spec requires and are enabled one by one as the topology is implemented. Their combined state
 * is the measure of how complete the nano level implementation is.
 */
@ActiveProfiles("dev", "test", "ext-api")
@SpringBootTest(classes = [InfraApplication::class])
@AutoConfigureMockMvc
class ExtTopologyIT @Autowired constructor(mockMvc: MockMvc, private val extTestDataService: ExtApiTestDataServiceV1) :
    DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Returns 400 for an unknown resoluutio`() {
        testDBService.publish()
        val error =
            api.topology.getWithExpectedError(
                TOPOLOGY_RESOLUTION_PARAM to "piko",
                httpStatus = HttpStatus.BAD_REQUEST,
            )
        assertTrue(
            error.virheviesti.contains("nano") && error.virheviesti.contains("mikro"),
            "Error message should list the allowed resolutions: ${error.virheviesti}",
        )
    }

    @Test
    fun `Returns 400 for an unsupported koordinaatisto`() {
        testDBService.publish()
        api.topology.getWithExpectedError(COORDINATE_SYSTEM to "EPSG:9999", httpStatus = HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Returns 400 for an invalid rataverkon_versio format`() {
        testDBService.publish()
        api.topology.getWithExpectedError(TRACK_LAYOUT_VERSION to "not-a-uuid", httpStatus = HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Returns 404 for a non-existing rataverkon_versio`() {
        testDBService.publish()
        api.topology.getWithExpectedError(
            TRACK_LAYOUT_VERSION to "00000000-0000-0000-0000-000000000000",
            httpStatus = HttpStatus.NOT_FOUND,
        )
    }

    @Test
    fun `Returns 404 for a design publication version`() {
        initUser()
        val designBranch = testDBService.createDesignBranch()
        val (trackNumberId, _) =
            testDBService
                .testContext(designBranch, PublicationState.DRAFT)
                .saveWithOid(
                    trackNumber(testDBService.getUnusedTrackNumber()),
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(0.0, 1000.0))),
                )
        val designPublication = testDBService.publish(designBranch, trackNumbers = listOf(trackNumberId))

        // The topology is only formed from MAIN branch versions, so a design version is an unknown version here.
        api.topology.getWithExpectedError(
            TRACK_LAYOUT_VERSION to designPublication.uuid.toString(),
            httpStatus = HttpStatus.NOT_FOUND,
        )
    }

    @Test
    fun `Response echoes the requested version, coordinate system and resolution`() {
        val publication = simpleTrackLayout().publication

        val response = api.topology.getAtVersion(publication.uuid)
        assertEquals(publication.uuid.toString(), response.rataverkon_versio)
        assertEquals(LAYOUT_SRID.toString(), response.koordinaatisto)
        assertEquals("nano", response.topologia.graafin_resoluutio, "nano is the default resolution")

        val explicitNano = api.topology.getAtVersion(publication.uuid, TOPOLOGY_RESOLUTION_PARAM to "nano")
        assertEquals("nano", explicitNano.topologia.graafin_resoluutio)

        val transformed = api.topology.getAtVersion(publication.uuid, COORDINATE_SYSTEM to "EPSG:4326")
        assertEquals("EPSG:4326", transformed.koordinaatisto)
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Single track produces one edge between two track end nodes`() {
        val layout = simpleTrackLayout()

        val topology = api.topology.getAtVersion(layout.publication.uuid).topologia

        val edge = topology.kaaret.single()
        assertEquals(listOf(layout.trackOid), edge.raiteet.map { it.oid })
        assertEquals(1000.0, edge.pituus, 0.001)

        assertEquals(2, topology.solmut.size)
        topology.solmut.forEach { node ->
            assertEquals("raiteen_paa", node.tyyppi)
            assertTrue(node.vaihteet.isEmpty(), "A track end node has no switches")
        }
        assertEquals(topology.solmut.map { it.id }.toSet(), setOf(edge.alkusolmu, edge.loppusolmu))
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Track end node allows only a U-turn on its own edge`() {
        val layout = simpleTrackLayout()

        val topology = api.topology.getAtVersion(layout.publication.uuid).topologia
        val edge = topology.kaaret.single()

        topology.solmut.forEach { node ->
            val transition = node.kulkusuunnat.single()
            assertEquals(edge.id, transition.kaari_sisaan.id)
            assertEquals(edge.id, transition.kaari_ulos.id)
            assertTrue(
                transition.kaari_sisaan.suunta != transition.kaari_ulos.suunta,
                "A U-turn enters and leaves the same edge in opposite directions",
            )
        }
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Switch node reports the switch OID and joint number`() {
        val structure = switchStructureYV60_300_1_9()
        val ids =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(100.0, 0.0))),
                structure,
            )
        val publication = extTestDataService.publishInMain(listOf(ids))

        val topology = api.topology.getAtVersion(publication.uuid).topologia

        val switchNodes = topology.solmut.filter { it.tyyppi == "vaihde" }
        assertTrue(switchNodes.isNotEmpty(), "Expected at least one switch node")
        switchNodes.forEach { node ->
            val switchReference = node.vaihteet.single()
            assertEquals(ids.switch.oid.toString(), switchReference.oid)
            assertTrue(
                switchReference.vaihdepiste in structure.joints.map { it.number.intValue },
                "Joint ${switchReference.vaihdepiste} should belong to the switch structure",
            )
        }
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Switch transitions follow the switch structure lines`() {
        val structure = switchStructureYV60_300_1_9()
        val ids =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(1, Point(0.0, 0.0)) to switchJoint(2, Point(100.0, 0.0))),
                structure,
            )
        val publication = extTestDataService.publishInMain(listOf(ids))

        val topology = api.topology.getAtVersion(publication.uuid).topologia

        // Every transition must connect edges that actually exist and must be listed only once.
        val edgeIds = topology.kaaret.map { it.id }.toSet()
        topology.solmut.forEach { node ->
            node.kulkusuunnat.forEach { transition ->
                assertTrue(edgeIds.contains(transition.kaari_sisaan.id), "Unknown incoming edge in $node")
                assertTrue(edgeIds.contains(transition.kaari_ulos.id), "Unknown outgoing edge in $node")
            }
            assertEquals(
                node.kulkusuunnat.size,
                node.kulkusuunnat.distinct().size,
                "The same transition must not be listed twice",
            )
        }
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Graph is internally consistent`() {
        val layout = simpleTrackLayout()

        val topology = api.topology.getAtVersion(layout.publication.uuid).topologia

        val nodeIds = topology.solmut.map { it.id }
        assertEquals(nodeIds.size, nodeIds.distinct().size, "The same node must not be listed twice")

        val edgeIds = topology.kaaret.map { it.id }
        assertEquals(edgeIds.size, edgeIds.distinct().size, "The same edge must not be listed twice")

        topology.kaaret.forEach { edge ->
            assertTrue(nodeIds.contains(edge.alkusolmu), "Edge ${edge.id} refers to an unknown start node")
            assertTrue(nodeIds.contains(edge.loppusolmu), "Edge ${edge.id} refers to an unknown end node")
            assertTrue(edge.pituus > 0.0, "Edge ${edge.id} should have a positive length")
            assertEquals(
                edge.raiteet.size,
                edge.raiteet.distinct().size,
                "The same location track must not be listed twice on an edge",
            )
        }
    }

    @Disabled("GVT-3743: the nano level topology is not built yet")
    @Test
    fun `Published version keeps producing the same topology after newer publications`() {
        val layout = simpleTrackLayout()
        val topologyAtV1 = api.topology.getAtVersion(layout.publication.uuid).topologia

        // A newer publication adds a separate track, which must not change the older version's topology.
        val (otherTrackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(layout.trackNumberId),
                trackGeometryOfSegments(segment(Point(500.0, 0.0), Point(500.0, 1000.0))),
            )
        testDBService.publish(locationTracks = listOf(otherTrackId))

        assertEquals(topologyAtV1, api.topology.getAtVersion(layout.publication.uuid).topologia)
    }

    private data class SimpleTrackLayout(
        val publication: Publication,
        val trackNumberId: IntId<LayoutTrackNumber>,
        val trackOid: String,
    )

    private fun simpleTrackLayout(): SimpleTrackLayout {
        val start = Point(0.0, 0.0)
        val end = Point(0.0, 1000.0)
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(start, end)),
            )
        val (trackId, trackOid) =
            mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometryOfSegments(segment(start, end)))
        val publication = testDBService.publish(trackNumbers = listOf(trackNumberId), locationTracks = listOf(trackId))
        return SimpleTrackLayout(publication, trackNumberId, trackOid.toString())
    }
}
