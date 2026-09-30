package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.api.ExtApiTestDataServiceV1
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.InfraApplication
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.graph.switchLineUuid
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchJoint
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchStructureYV60_300_1_9
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
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
class ExtTopologyIT
@Autowired
constructor(
    mockMvc: MockMvc,
    private val extTestDataService: ExtApiTestDataServiceV1,
    private val locationTrackService: LocationTrackService,
) : DBTestBase() {

    private val api = ExtTrackLayoutTestApiService(mockMvc)

    @BeforeEach
    fun cleanup() {
        testDBService.clearAllTables()
    }

    @Test
    fun `Returns 400 for an unknown resolution`() {
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
    fun `Returns 400 for an unsupported coordinate system`() {
        testDBService.publish()
        api.topology.getWithExpectedError(COORDINATE_SYSTEM to "EPSG:9999", httpStatus = HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Returns 400 for an invalid track layout version format`() {
        testDBService.publish()
        api.topology.getWithExpectedError(TRACK_LAYOUT_VERSION to "not-a-uuid", httpStatus = HttpStatus.BAD_REQUEST)
    }

    @Test
    fun `Returns 404 for a non-existing track layout version`() {
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

    @Test
    fun `Topology node coordinates are transformed to the requested coordinate system`() {
        val layout =
            simpleTrackLayout(
                start = Point(385782.89, 6672277.83),
                end = Point(385882.89, 6672277.83),
            )
        val expectedLocations =
            mapOf(
                "EPSG:3067" to listOf(Point(385782.89, 6672277.83), Point(385882.89, 6672277.83)),
                "EPSG:4326" to listOf(Point(24.9414003, 60.1713788), Point(24.9432013, 60.1714068)),
                "EPSG:4258" to listOf(Point(24.9414003, 60.1713788), Point(24.9432013, 60.1714068)),
                "EPSG:5048" to listOf(Point(385782.89, 6672277.83), Point(385882.89, 6672277.83)),
            )

        expectedLocations.forEach { (coordinateSystem, expected) ->
            val response = api.topology.getAtVersion(layout.publication.uuid, COORDINATE_SYSTEM to coordinateSystem)

            assertEquals(coordinateSystem, response.koordinaatisto)
            assertLocations(expected, response.topologia.solmut.map { node -> node.sijainti })
        }
    }

    @Test
    fun `Single track produces one edge between two track end nodes`() {
        val layout = simpleTrackLayout()

        val topology = api.topology.getAtVersion(layout.publication.uuid).topologia

        val edge = topology.kaaret.single()
        val dbEdge =
            locationTrackService
                .listOfficialWithGeometryAtMoment(
                    layout.publication.layoutBranch.branch,
                    layout.publication.publicationTime,
                )
                .single()
                .second
                .edges
                .single()
        assertEquals(dbEdge.uuid.toString(), edge.id)
        assertEquals(listOf(layout.trackOid), edge.raiteet.map { it.oid })
        assertEquals(1000.0, edge.pituus, 0.001)

        assertEquals(2, topology.solmut.size)
        topology.solmut.forEach { node ->
            assertEquals("raiteen_paa", node.tyyppi)
            assertTrue(node.vaihteet.isEmpty(), "A track end node has no switches")
        }
        assertEquals(
            setOf(dbEdge.startNode.node.uuid.toString(), dbEdge.endNode.node.uuid.toString()),
            topology.solmut.map { node -> node.id }.toSet(),
        )
        assertEquals(topology.solmut.map { it.id }.toSet(), setOf(edge.alkusolmu, edge.loppusolmu))
    }

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

    @Test
    fun `Every fully linked switch structure alignment produces an edge`() {
        val structure = switchStructureYV60_300_1_9()
        val ids =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                structure.alignments.map { alignment ->
                    val start = alignment.jointNumbers.first()
                    val end = alignment.jointNumbers.last()
                    switchJoint(start.intValue, structure.getJointLocation(start)) to
                        switchJoint(end.intValue, structure.getJointLocation(end))
                },
                structure,
            )
        val publication = extTestDataService.publishInMain(listOf(ids))

        val topology = api.topology.getAtVersion(publication.uuid).topologia

        assertEquals(
            structure.alignments
                .map { alignment -> switchLineUuid(ids.switch.oid, alignment.jointNumbers).toString() }
                .toSet(),
            topology.kaaret.map { edge -> edge.id }.toSet(),
        )
        assertEquals(
            structure.alignments
                .flatMap { alignment -> listOf(alignment.jointNumbers.first(), alignment.jointNumbers.last()) }
                .distinct()
                .size,
            topology.solmut.size,
        )
    }

    @Test
    fun `Internal switch alignment joints are not returned as topology nodes`() {
        val structure = switchStructureYV60_300_1_9()
        val joint1 = switchJoint(1, structure.getJointLocation(JointNumber(1)))
        val joint5 = switchJoint(5, structure.getJointLocation(JointNumber(5)))
        val joint2 = switchJoint(2, structure.getJointLocation(JointNumber(2)))
        val (switchId, switchOid) =
            mainDraftContext.saveWithOid(switch(structure.id, joints = listOf(joint1, joint5, joint2)))
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(segment(joint1.location, joint2.location)),
            )
        val (trackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(trackNumberId),
                trackGeometry(
                    edge(
                        segments = listOf(segment(joint1.location, joint5.location)),
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        endInnerSwitch = switchLinkYV(switchId, 5),
                    ),
                    edge(
                        segments = listOf(segment(joint5.location, joint2.location)),
                        startInnerSwitch = switchLinkYV(switchId, 5),
                        endInnerSwitch = switchLinkYV(switchId, 2),
                    ),
                ),
            )
        val publication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                locationTracks = listOf(trackId),
                switches = listOf(switchId),
            )

        val topology = api.topology.getAtVersion(publication.uuid).topologia

        assertEquals(
            setOf(1, 2),
            topology.solmut.flatMap { node -> node.vaihteet.map { reference -> reference.vaihdepiste } }.toSet(),
        )
        assertTrue(topology.solmut.all { node -> node.vaihteet.single().oid == switchOid.toString() })
        assertEquals(2, topology.solmut.size)
        assertEquals(1, topology.kaaret.size)
    }

    @Test
    fun `Partially linked switch returns only linked joint nodes and alignment`() {
        val structure = switchStructureYV60_300_1_9()
        val linkedAlignment = structure.alignments.first()
        val start = linkedAlignment.jointNumbers.first()
        val end = linkedAlignment.jointNumbers.last()
        val startLocation = structure.getJointLocation(start)
        val endLocation = structure.getJointLocation(end)
        val ids =
            extTestDataService.insertSwitchAndTracks(
                mainDraftContext,
                listOf(switchJoint(start.intValue, startLocation) to switchJoint(end.intValue, endLocation)),
                structure,
            )
        val publication = extTestDataService.publishInMain(listOf(ids))

        val topology = api.topology.getAtVersion(publication.uuid).topologia
        val nodesByJoint = topology.solmut.associateBy { node -> node.vaihteet.single().vaihdepiste }
        val startNode = nodesByJoint.getValue(start.intValue)
        val endNode = nodesByJoint.getValue(end.intValue)

        assertEquals(setOf(start.intValue, end.intValue), nodesByJoint.keys)
        nodesByJoint.values.forEach { node -> assertEquals(ids.switch.oid.toString(), node.vaihteet.single().oid) }
        assertEquals(startLocation.x, startNode.sijainti.x)
        assertEquals(startLocation.y, startNode.sijainti.y)
        assertEquals(endLocation.x, endNode.sijainti.x)
        assertEquals(endLocation.y, endNode.sijainti.y)

        val edge = topology.kaaret.single()
        assertEquals(switchLineUuid(ids.switch.oid, linkedAlignment.jointNumbers).toString(), edge.id)
        assertEquals(startNode.id, edge.alkusolmu)
        assertEquals(endNode.id, edge.loppusolmu)
        assertEquals(listOf(ids.tracks.single().oid.toString()), edge.raiteet.map { track -> track.oid })
    }

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
                node.kulkusuunnat,
                node.kulkusuunnat.distinct(),
                "The same transition must not be listed twice",
            )
        }
    }

    @Test
    fun `Combined switch node allows travel from the first port to the second port`() {
        // Setup
        val fixture = publishCombinedSwitchNodeFixture()

        // Execute
        val topology = api.topology.getAtVersion(fixture.publication.uuid).topologia

        // Verify
        val node = topology.solmut.single { node -> node.vaihteet.size == 2 }
        val firstSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.firstSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.firstSwitchOid, alignment.jointNumbers).toString() }
        val secondSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.secondSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.secondSwitchOid, alignment.jointNumbers).toString() }
        val firstSwitchEdges = firstSwitchEdgeIds.map { edgeId -> topology.kaaret.single { edge -> edge.id == edgeId } }
        val secondSwitchEdges = secondSwitchEdgeIds.map { edgeId ->
            topology.kaaret.single { edge -> edge.id == edgeId }
        }
        val expectedTransitions =
            firstSwitchEdges
                .flatMap { incomingEdge ->
                    secondSwitchEdges.map { outgoingEdge ->
                        TopologyTransitionKey(
                            incomingEdge = incomingEdge.id,
                            incomingDirection = if (incomingEdge.alkusolmu == node.id) "laskeva" else "nouseva",
                            outgoingEdge = outgoingEdge.id,
                            outgoingDirection = if (outgoingEdge.alkusolmu == node.id) "nouseva" else "laskeva",
                        )
                    }
                }
                .toSet()
        val actualTransitions =
            node.kulkusuunnat
                .map(::TopologyTransitionKey)
                .filter { transition -> transition.incomingEdge in firstSwitchEdgeIds }
                .toSet()
        assertEquals(
            expectedTransitions,
            actualTransitions,
            "Arriving along any switch line of the first port should allow continuing along every line of the second",
        )
    }

    @Test
    fun `Combined switch node allows travel from the second port to the first port`() {
        // Setup
        val fixture = publishCombinedSwitchNodeFixture()

        // Execute
        val topology = api.topology.getAtVersion(fixture.publication.uuid).topologia

        // Verify
        val node = topology.solmut.single { node -> node.vaihteet.size == 2 }
        val firstSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.firstSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.firstSwitchOid, alignment.jointNumbers).toString() }
        val secondSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.secondSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.secondSwitchOid, alignment.jointNumbers).toString() }
        val firstSwitchEdges = firstSwitchEdgeIds.map { edgeId -> topology.kaaret.single { edge -> edge.id == edgeId } }
        val secondSwitchEdges = secondSwitchEdgeIds.map { edgeId ->
            topology.kaaret.single { edge -> edge.id == edgeId }
        }
        val expectedTransitions =
            secondSwitchEdges
                .flatMap { incomingEdge ->
                    firstSwitchEdges.map { outgoingEdge ->
                        TopologyTransitionKey(
                            incomingEdge = incomingEdge.id,
                            incomingDirection = if (incomingEdge.alkusolmu == node.id) "laskeva" else "nouseva",
                            outgoingEdge = outgoingEdge.id,
                            outgoingDirection = if (outgoingEdge.alkusolmu == node.id) "nouseva" else "laskeva",
                        )
                    }
                }
                .toSet()
        val actualTransitions =
            node.kulkusuunnat
                .map(::TopologyTransitionKey)
                .filter { transition -> transition.incomingEdge in secondSwitchEdgeIds }
                .toSet()
        assertEquals(
            expectedTransitions,
            actualTransitions,
            "Arriving along any switch line of the second port should allow continuing along every line of the first",
        )
    }

    @Test
    fun `Combined node of the joint 1 of two switches allows travel between both ports`() {
        // Setup
        val fixture = publishCombinedSwitchNodeFixture(firstSwitchJoint = 1, secondSwitchJoint = 1)

        // Execute
        val topology = api.topology.getAtVersion(fixture.publication.uuid).topologia

        // Verify
        val node = topology.solmut.single { node -> node.vaihteet.size == 2 }
        val firstSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.firstSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.firstSwitchOid, alignment.jointNumbers).toString() }
        val secondSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.secondSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.secondSwitchOid, alignment.jointNumbers).toString() }
        val firstSwitchEdges = firstSwitchEdgeIds.map { edgeId -> topology.kaaret.single { edge -> edge.id == edgeId } }
        val secondSwitchEdges = secondSwitchEdgeIds.map { edgeId ->
            topology.kaaret.single { edge -> edge.id == edgeId }
        }
        val expectedTransitions =
            listOf(firstSwitchEdges to secondSwitchEdges, secondSwitchEdges to firstSwitchEdges)
                .flatMap { (incomingEdges, outgoingEdges) ->
                    incomingEdges.flatMap { incomingEdge ->
                        outgoingEdges.map { outgoingEdge ->
                            TopologyTransitionKey(
                                incomingEdge = incomingEdge.id,
                                incomingDirection = if (incomingEdge.alkusolmu == node.id) "laskeva" else "nouseva",
                                outgoingEdge = outgoingEdge.id,
                                outgoingDirection = if (outgoingEdge.alkusolmu == node.id) "nouseva" else "laskeva",
                            )
                        }
                    }
                }
                .toSet()
        assertEquals(
            setOf(fixture.firstSwitchOid.toString() to 1, fixture.secondSwitchOid.toString() to 1),
            node.vaihteet.map { reference -> reference.oid to reference.vaihdepiste }.toSet(),
            "The node should combine the joint 1 of both switches",
        )
        assertEquals(
            2,
            firstSwitchEdges.size,
            "Both switch lines of the first switch should meet at its joint 1",
        )
        assertEquals(
            2,
            secondSwitchEdges.size,
            "Both switch lines of the second switch should meet at its joint 1",
        )
        assertEquals(
            expectedTransitions,
            node.kulkusuunnat.map(::TopologyTransitionKey).toSet(),
            "Every leg of either switch should connect to both legs of the other switch, but not to its own switch",
        )
    }

    @Test
    fun `Combined node of the joint 1 and the joint 2 of two switches allows travel between both ports`() {
        // Setup
        val fixture = publishCombinedSwitchNodeFixture(firstSwitchJoint = 1, secondSwitchJoint = 2)

        // Execute
        val topology = api.topology.getAtVersion(fixture.publication.uuid).topologia

        // Verify
        val node = topology.solmut.single { node -> node.vaihteet.size == 2 }
        val firstSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.firstSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.firstSwitchOid, alignment.jointNumbers).toString() }
        val secondSwitchEdgeIds =
            fixture.structure.alignments
                .filter { alignment -> alignment.jointNumbers.contains(JointNumber(fixture.secondSwitchJoint)) }
                .map { alignment -> switchLineUuid(fixture.secondSwitchOid, alignment.jointNumbers).toString() }
        val firstSwitchEdges = firstSwitchEdgeIds.map { edgeId -> topology.kaaret.single { edge -> edge.id == edgeId } }
        val secondSwitchEdges = secondSwitchEdgeIds.map { edgeId ->
            topology.kaaret.single { edge -> edge.id == edgeId }
        }
        val expectedTransitions =
            listOf(firstSwitchEdges to secondSwitchEdges, secondSwitchEdges to firstSwitchEdges)
                .flatMap { (incomingEdges, outgoingEdges) ->
                    incomingEdges.flatMap { incomingEdge ->
                        outgoingEdges.map { outgoingEdge ->
                            TopologyTransitionKey(
                                incomingEdge = incomingEdge.id,
                                incomingDirection = if (incomingEdge.alkusolmu == node.id) "laskeva" else "nouseva",
                                outgoingEdge = outgoingEdge.id,
                                outgoingDirection = if (outgoingEdge.alkusolmu == node.id) "nouseva" else "laskeva",
                            )
                        }
                    }
                }
                .toSet()
        assertEquals(
            setOf(fixture.firstSwitchOid.toString() to 1, fixture.secondSwitchOid.toString() to 2),
            node.vaihteet.map { reference -> reference.oid to reference.vaihdepiste }.toSet(),
            "The node should combine the joint 1 of the first switch and the joint 2 of the second",
        )
        assertEquals(
            2,
            firstSwitchEdges.size,
            "Both switch lines of the first switch should meet at its joint 1",
        )
        assertEquals(
            1,
            secondSwitchEdges.size,
            "Only the through line of the second switch should reach its joint 2",
        )
        assertEquals(
            expectedTransitions,
            node.kulkusuunnat.map(::TopologyTransitionKey).toSet(),
            "Both legs of the first switch should connect to the through line of the second, but not to each other",
        )
    }

    @Test
    fun `Graph is internally consistent`() {
        val layout = simpleTrackLayout()

        val topology = api.topology.getAtVersion(layout.publication.uuid).topologia

        val nodeIds = topology.solmut.map { it.id }
        assertEquals(nodeIds, nodeIds.distinct(), "The same node must not be listed twice")

        val edgeIds = topology.kaaret.map { it.id }
        assertEquals(edgeIds, edgeIds.distinct(), "The same edge must not be listed twice")

        topology.kaaret.forEach { edge ->
            assertTrue(nodeIds.contains(edge.alkusolmu), "Edge ${edge.id} refers to an unknown start node")
            assertTrue(nodeIds.contains(edge.loppusolmu), "Edge ${edge.id} refers to an unknown end node")
            assertTrue(edge.pituus > 0.0, "Edge ${edge.id} should have a positive length")
            assertEquals(
                edge.raiteet,
                edge.raiteet.distinct(),
                "The same location track must not be listed twice on an edge",
            )
        }
    }

    @Test
    fun `Published version keeps producing the same topology after newer publications`() {
        initUser()
        val layout = simpleTrackLayout()
        val topologyAtV1 = api.topology.getAtVersion(layout.publication.uuid).topologia

        // A newer publication adds a separate track, which must not change the older version's topology.
        initUser()
        val (otherTrackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(layout.trackNumberId),
                trackGeometryOfSegments(segment(Point(500.0, 0.0), Point(500.0, 1000.0))),
            )
        testDBService.publish(locationTracks = listOf(otherTrackId))

        assertEquals(topologyAtV1, api.topology.getAtVersion(layout.publication.uuid).topologia)
    }

    private data class CombinedSwitchNodeFixture(
        val publication: Publication,
        val structure: SwitchStructure,
        val firstSwitchOid: Oid<LayoutSwitch>,
        val secondSwitchOid: Oid<LayoutSwitch>,
        val firstSwitchJoint: Int,
        val secondSwitchJoint: Int,
    )

    private data class TopologyTransitionKey(
        val incomingEdge: String,
        val incomingDirection: String,
        val outgoingEdge: String,
        val outgoingDirection: String,
    ) {
        constructor(
            transition: ExtTestTopologyTransitionV1
        ) : this(
            incomingEdge = transition.kaari_sisaan.id,
            incomingDirection = transition.kaari_sisaan.suunta,
            outgoingEdge = transition.kaari_ulos.id,
            outgoingDirection = transition.kaari_ulos.suunta,
        )
    }

    /**
     * Two fully linked YV switches sharing a combined node: the [firstSwitchJoint] of the first switch and the
     * [secondSwitchJoint] of the second sit in the same node. Both switches get all of their joints, a location track
     * along each switch line, and an external location track continuing past every joint outside the combined node.
     */
    private fun publishCombinedSwitchNodeFixture(
        firstSwitchJoint: Int = 1,
        secondSwitchJoint: Int = 1,
    ): CombinedSwitchNodeFixture {
        val structure = switchStructureYV60_300_1_9()
        val firstJoints = yvJointLayout(firstSwitchJoint, direction = -1.0)
        val secondJoints = yvJointLayout(secondSwitchJoint, direction = 1.0)
        val firstExternalEnds = externalTrackEnds(firstJoints, firstSwitchJoint, direction = -1.0)
        val secondExternalEnds = externalTrackEnds(secondJoints, secondSwitchJoint, direction = 1.0)
        val (firstSwitchId, firstSwitchOid) = saveYvSwitch(structure, firstJoints)
        val (secondSwitchId, secondSwitchOid) = saveYvSwitch(structure, secondJoints)

        val allPoints = firstJoints.values + secondJoints.values + firstExternalEnds.values + secondExternalEnds.values
        val (trackNumberId, _) =
            mainDraftContext.saveWithOid(
                trackNumber(testDBService.getUnusedTrackNumber()),
                referenceLineGeometry(
                    segment(allPoints.minBy { point -> point.x }, allPoints.maxBy { point -> point.x })
                ),
            )
        val firstTrackIds =
            saveYvSwitchTracks(
                trackNumberId = trackNumberId,
                switchId = firstSwitchId,
                otherSwitchId = secondSwitchId,
                nodeJoint = firstSwitchJoint,
                otherNodeJoint = secondSwitchJoint,
                joints = firstJoints,
                externalEnds = firstExternalEnds,
            )
        val secondTrackIds =
            saveYvSwitchTracks(
                trackNumberId = trackNumberId,
                switchId = secondSwitchId,
                otherSwitchId = firstSwitchId,
                nodeJoint = secondSwitchJoint,
                otherNodeJoint = firstSwitchJoint,
                joints = secondJoints,
                externalEnds = secondExternalEnds,
            )
        val publication =
            testDBService.publish(
                trackNumbers = listOf(trackNumberId),
                locationTracks = firstTrackIds + secondTrackIds,
                switches = listOf(firstSwitchId, secondSwitchId),
            )

        return CombinedSwitchNodeFixture(
            publication = publication,
            structure = structure,
            firstSwitchOid = firstSwitchOid,
            secondSwitchOid = secondSwitchOid,
            firstSwitchJoint = firstSwitchJoint,
            secondSwitchJoint = secondSwitchJoint,
        )
    }

    /**
     * Joint locations of a YV switch whose [nodeJoint] sits in the combined node at the origin. The rest of the switch
     * extends away from the node along [direction], which is -1.0 for the left and 1.0 for the right hand switch.
     */
    private fun yvJointLayout(nodeJoint: Int, direction: Double): Map<Int, Point> =
        when (nodeJoint) {
            1 ->
                mapOf(
                    1 to Point(0.0, 0.0),
                    2 to Point(direction * 20.0, 0.0),
                    3 to Point(direction * 20.0, direction * 5.0),
                )
            2 ->
                mapOf(
                    2 to Point(0.0, 0.0),
                    1 to Point(direction * 20.0, 0.0),
                    3 to Point(direction * 40.0, direction * 5.0),
                )
            else ->
                throw IllegalArgumentException(
                    "The combined node fixture only supports the joints 1 and 2: joint=$nodeJoint"
                )
        }

    /** End points of the external tracks that continue past every joint that is not in the combined node. */
    private fun externalTrackEnds(joints: Map<Int, Point>, nodeJoint: Int, direction: Double): Map<Int, Point> =
        joints
            .filterKeys { joint -> joint != nodeJoint }
            .mapValues { (_, point) -> Point(point.x + direction * 20.0, point.y) }

    private fun saveYvSwitch(structure: SwitchStructure, joints: Map<Int, Point>) =
        mainDraftContext.saveWithOid(
            switch(structure.id, joints = joints.map { (joint, point) -> switchJoint(joint, point) })
        )

    /**
     * Saves the location tracks of one YV switch: one along each switch line and one continuing past every joint
     * outside the combined node. The switch line tracks also link to [otherSwitchId] at the shared [nodeJoint].
     */
    private fun saveYvSwitchTracks(
        trackNumberId: IntId<LayoutTrackNumber>,
        switchId: IntId<LayoutSwitch>,
        otherSwitchId: IntId<LayoutSwitch>,
        nodeJoint: Int,
        otherNodeJoint: Int,
        joints: Map<Int, Point>,
        externalEnds: Map<Int, Point>,
    ): List<IntId<LocationTrack>> {
        val otherSwitchLink = switchLinkYV(otherSwitchId, otherNodeJoint)
        val switchLineTracks =
            listOf(2, 3).map { endJoint ->
                saveTrack(
                    trackNumberId,
                    edge(
                        segments = listOf(segment(joints.getValue(1), joints.getValue(endJoint))),
                        startInnerSwitch = switchLinkYV(switchId, 1),
                        startOuterSwitch = if (nodeJoint == 1) otherSwitchLink else null,
                        endInnerSwitch = switchLinkYV(switchId, endJoint),
                        endOuterSwitch = if (nodeJoint == endJoint) otherSwitchLink else null,
                    ),
                )
            }
        val externalTracks = externalEnds.map { (joint, endPoint) ->
            saveTrack(
                trackNumberId,
                edge(
                    segments = listOf(segment(joints.getValue(joint), endPoint)),
                    startOuterSwitch = switchLinkYV(switchId, joint),
                ),
            )
        }
        return switchLineTracks + externalTracks
    }

    private fun saveTrack(trackNumberId: IntId<LayoutTrackNumber>, edge: LayoutEdge): IntId<LocationTrack> =
        mainDraftContext.saveWithOid(locationTrack(trackNumberId), trackGeometry(edge)).first

    private data class SimpleTrackLayout(
        val publication: Publication,
        val trackNumberId: IntId<LayoutTrackNumber>,
        val trackOid: String,
    )

    private fun simpleTrackLayout(
        start: Point = Point(0.0, 0.0),
        end: Point = Point(0.0, 1000.0),
    ): SimpleTrackLayout {
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

    private fun assertLocations(expected: List<Point>, actual: List<ExtTestCoordinateV1>) {
        assertEquals(expected.size, actual.size)
        expected.forEach { expectedLocation ->
            assertTrue(
                actual.any { actualLocation ->
                    kotlin.math.abs(actualLocation.x - expectedLocation.x) < 0.000001 &&
                        kotlin.math.abs(actualLocation.y - expectedLocation.y) < 0.000001
                },
                "Expected location $expectedLocation in $actual",
            )
        }
    }
}
