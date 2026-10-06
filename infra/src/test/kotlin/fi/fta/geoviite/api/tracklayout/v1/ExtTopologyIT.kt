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
import fi.fta.geoviite.infra.tracklayout.graph.OidSwitchRef
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
 * The request contract tests hold already. Each graph test verifies both the nano topology and its deterministic micro
 * level simplification from the same published track layout.
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

        val micro = api.topology.getAtVersion(publication.uuid, TOPOLOGY_RESOLUTION_PARAM to "mikro")
        assertEquals("mikro", micro.topologia.graafin_resoluutio)

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
            topologyResolutions.forEach { resolution ->
                val response =
                    api.topology.getAtVersion(
                        layout.publication.uuid,
                        COORDINATE_SYSTEM to coordinateSystem,
                        TOPOLOGY_RESOLUTION_PARAM to resolution,
                    )

                assertEquals(coordinateSystem, response.koordinaatisto)
                assertEquals(resolution, response.topologia.graafin_resoluutio)
                assertLocations(expected, response.topologia.solmut.map { node -> node.sijainti })
            }
        }
    }

    @Test
    fun `Single track produces one edge between two track end nodes`() {
        val layout = simpleTrackLayout()

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
        topologiesAtVersion(layout.publication).forEach { (resolution, topology) ->
            val edge = topology.kaaret.single()
            assertEquals(dbEdge.uuid.toString(), edge.id, resolution)
            assertEquals(listOf(layout.trackOid), edge.raiteet.map { it.oid }, resolution)
            assertEquals(1000.0, edge.pituus, 0.001, resolution)

            assertEquals(2, topology.solmut.size, resolution)
            topology.solmut.forEach { node ->
                assertEquals("raiteen_paa", node.tyyppi, resolution)
                assertTrue(node.vaihteet.isEmpty(), "$resolution: A track end node has no switches")
            }
            assertEquals(
                setOf(dbEdge.startNode.node.uuid.toString(), dbEdge.endNode.node.uuid.toString()),
                topology.solmut.map { node -> node.id }.toSet(),
                resolution,
            )
            assertEquals(
                topology.solmut.map { it.id }.toSet(),
                setOf(edge.alkusolmu, edge.loppusolmu),
                resolution,
            )
        }
    }

    @Test
    fun `Track end node allows only a U-turn on its own edge`() {
        val layout = simpleTrackLayout()

        topologiesAtVersion(layout.publication).forEach { (resolution, topology) ->
            val edge = topology.kaaret.single()
            topology.solmut.forEach { node ->
                val transition = node.kulkusuunnat.single()
                assertEquals(edge.id, transition.kaari_sisaan.id, resolution)
                assertEquals(edge.id, transition.kaari_ulos.id, resolution)
                assertTrue(
                    transition.kaari_sisaan.suunta != transition.kaari_ulos.suunta,
                    "$resolution: A U-turn enters and leaves the same edge in opposite directions",
                )
            }
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

        topologiesAtVersion(publication).forEach { (resolution, topology) ->
            val switchNodes = topology.solmut.filter { it.tyyppi == "vaihde" }
            assertTrue(switchNodes.isNotEmpty(), "$resolution: Expected at least one switch node")
            switchNodes.forEach { node ->
                val switchReference = node.vaihteet.single()
                assertEquals(ids.switch.oid.toString(), switchReference.oid, resolution)
                assertTrue(
                    switchReference.vaihdepiste in structure.joints.map { it.number.intValue },
                    "$resolution: Joint ${switchReference.vaihdepiste} should belong to the switch structure",
                )
            }
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

        topologiesAtVersion(publication).forEach { (resolution, topology) ->
            assertEquals(
                structure.alignments
                    .map { alignment ->
                        switchLineUuid(OidSwitchRef(ids.switch.oid), alignment.jointNumbers).toString()
                    }
                    .toSet(),
                topology.kaaret.map { edge -> edge.id }.toSet(),
                resolution,
            )
            assertEquals(
                structure.alignments
                    .flatMap { alignment -> listOf(alignment.jointNumbers.first(), alignment.jointNumbers.last()) }
                    .distinct()
                    .size,
                topology.solmut.size,
                resolution,
            )
        }
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

        topologiesAtVersion(publication).forEach { (resolution, topology) ->
            assertEquals(
                setOf(1, 2),
                topology.solmut.flatMap { node -> node.vaihteet.map { reference -> reference.vaihdepiste } }.toSet(),
                resolution,
            )
            assertTrue(
                topology.solmut.all { node -> node.vaihteet.single().oid == switchOid.toString() },
                resolution,
            )
            assertEquals(2, topology.solmut.size, resolution)
            assertEquals(1, topology.kaaret.size, resolution)
        }
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

        topologiesAtVersion(publication).forEach { (resolution, topology) ->
            val nodesByJoint = topology.solmut.associateBy { node -> node.vaihteet.single().vaihdepiste }
            val startNode = nodesByJoint.getValue(start.intValue)
            val endNode = nodesByJoint.getValue(end.intValue)

            assertEquals(setOf(start.intValue, end.intValue), nodesByJoint.keys, resolution)
            nodesByJoint.values.forEach { node ->
                assertEquals(ids.switch.oid.toString(), node.vaihteet.single().oid, resolution)
            }
            assertEquals(startLocation.x, startNode.sijainti.x, resolution)
            assertEquals(startLocation.y, startNode.sijainti.y, resolution)
            assertEquals(endLocation.x, endNode.sijainti.x, resolution)
            assertEquals(endLocation.y, endNode.sijainti.y, resolution)

            val edge = topology.kaaret.single()
            assertEquals(
                switchLineUuid(OidSwitchRef(ids.switch.oid), linkedAlignment.jointNumbers).toString(),
                edge.id,
                resolution,
            )
            assertEquals(startNode.id, edge.alkusolmu, resolution)
            assertEquals(endNode.id, edge.loppusolmu, resolution)
            assertEquals(
                listOf(ids.tracks.single().oid.toString()),
                edge.raiteet.map { track -> track.oid },
                resolution,
            )
        }
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

        topologiesAtVersion(publication).forEach { (resolution, topology) ->
            // Every transition must connect edges that actually exist and must be listed only once.
            val edgeIds = topology.kaaret.map { it.id }.toSet()
            topology.solmut.forEach { node ->
                node.kulkusuunnat.forEach { transition ->
                    assertTrue(
                        edgeIds.contains(transition.kaari_sisaan.id),
                        "$resolution: Unknown incoming edge in $node",
                    )
                    assertTrue(
                        edgeIds.contains(transition.kaari_ulos.id),
                        "$resolution: Unknown outgoing edge in $node",
                    )
                }
                assertEquals(
                    node.kulkusuunnat,
                    node.kulkusuunnat.distinct(),
                    "$resolution: The same transition must not be listed twice",
                )
            }
        }
    }

    @Test
    fun `Combined switch node allows travel from the first port to the second port`() {
        val fixture = publishCombinedSwitchNodeFixture()

        topologiesAtVersion(fixture.publication).forEach { (resolution, topology) ->
            assertCombinedPortTransitions(topology, incomingFromFirstPort = true, resolution)
        }
    }

    @Test
    fun `Combined switch node allows travel from the second port to the first port`() {
        val fixture = publishCombinedSwitchNodeFixture()

        topologiesAtVersion(fixture.publication).forEach { (resolution, topology) ->
            assertCombinedPortTransitions(topology, incomingFromFirstPort = false, resolution)
        }
    }

    @Test
    fun `Combined node of the joint 1 of two switches allows travel between both ports`() {
        val fixture = publishCombinedSwitchNodeFixture(firstSwitchJoint = 1, secondSwitchJoint = 1)

        topologiesAtVersion(fixture.publication).forEach { (resolution, topology) ->
            assertCombinedNode(
                topology = topology,
                expectedSwitches =
                    setOf(fixture.firstSwitchOid.toString() to 1, fixture.secondSwitchOid.toString() to 1),
                expectedFirstPortEdges = 2,
                expectedSecondPortEdges = 2,
                resolution = resolution,
            )
        }
    }

    @Test
    fun `Combined node of the joint 1 and the joint 2 of two switches allows travel between both ports`() {
        val fixture = publishCombinedSwitchNodeFixture(firstSwitchJoint = 1, secondSwitchJoint = 2)

        topologiesAtVersion(fixture.publication).forEach { (resolution, topology) ->
            assertCombinedNode(
                topology = topology,
                expectedSwitches =
                    setOf(fixture.firstSwitchOid.toString() to 1, fixture.secondSwitchOid.toString() to 2),
                expectedFirstPortEdges = 2,
                expectedSecondPortEdges = 1,
                resolution = resolution,
            )
        }
    }

    @Test
    fun `Graph is internally consistent`() {
        val layout = simpleTrackLayout()

        topologiesAtVersion(layout.publication).forEach { (resolution, topology) ->
            val nodeIds = topology.solmut.map { it.id }
            assertEquals(nodeIds, nodeIds.distinct(), "$resolution: The same node must not be listed twice")

            val edgeIds = topology.kaaret.map { it.id }
            assertEquals(edgeIds, edgeIds.distinct(), "$resolution: The same edge must not be listed twice")

            topology.kaaret.forEach { edge ->
                assertTrue(nodeIds.contains(edge.alkusolmu), "$resolution: Edge ${edge.id} has unknown start node")
                assertTrue(nodeIds.contains(edge.loppusolmu), "$resolution: Edge ${edge.id} has unknown end node")
                assertTrue(edge.pituus > 0.0, "$resolution: Edge ${edge.id} should have a positive length")
                assertEquals(
                    edge.raiteet,
                    edge.raiteet.distinct(),
                    "$resolution: The same location track must not be listed twice on an edge",
                )
            }
        }
    }

    @Test
    fun `Published version keeps producing the same topology after newer publications`() {
        initUser()
        val layout = simpleTrackLayout()
        val topologiesAtV1 = topologiesAtVersion(layout.publication)

        // A newer publication adds a separate track, which must not change the older version's topology.
        initUser()
        val (otherTrackId, _) =
            mainDraftContext.saveWithOid(
                locationTrack(layout.trackNumberId),
                trackGeometryOfSegments(segment(Point(500.0, 0.0), Point(500.0, 1000.0))),
            )
        testDBService.publish(locationTracks = listOf(otherTrackId))

        assertEquals(topologiesAtV1, topologiesAtVersion(layout.publication))
    }

    private data class CombinedSwitchNodeFixture(
        val publication: Publication,
        val firstSwitchOid: Oid<LayoutSwitch>,
        val secondSwitchOid: Oid<LayoutSwitch>,
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

    private fun topologiesAtVersion(publication: Publication): List<Pair<String, ExtTestTopologyV1>> =
        topologyResolutions.map { resolution ->
            resolution to
                api.topology.getAtVersion(publication.uuid, TOPOLOGY_RESOLUTION_PARAM to resolution).topologia.also {
                    topology ->
                    assertEquals(resolution, topology.graafin_resoluutio)
                }
        }

    private fun assertCombinedPortTransitions(
        topology: ExtTestTopologyV1,
        incomingFromFirstPort: Boolean,
        resolution: String,
    ) {
        val node = topology.solmut.single { candidate -> candidate.vaihteet.size == 2 }
        val (firstPortEdges, secondPortEdges) = combinedPortEdges(topology, node)
        val incomingEdges = if (incomingFromFirstPort) firstPortEdges else secondPortEdges
        val outgoingEdges = if (incomingFromFirstPort) secondPortEdges else firstPortEdges
        val expectedTransitions = transitionsBetween(node, incomingEdges, outgoingEdges)
        val incomingEdgeIds = incomingEdges.mapTo(mutableSetOf(), ExtTestTopologyEdgeV1::id)
        val actualTransitions =
            node.kulkusuunnat
                .map(::TopologyTransitionKey)
                .filter { transition -> transition.incomingEdge in incomingEdgeIds }
                .toSet()

        assertEquals(
            expectedTransitions,
            actualTransitions,
            "$resolution: arriving along either leg of one switch port must allow continuing along every leg of the other",
        )
    }

    private fun assertCombinedNode(
        topology: ExtTestTopologyV1,
        expectedSwitches: Set<Pair<String, Int>>,
        expectedFirstPortEdges: Int,
        expectedSecondPortEdges: Int,
        resolution: String,
    ) {
        val node = topology.solmut.single { candidate -> candidate.vaihteet.size == 2 }
        val (firstPortEdges, secondPortEdges) = combinedPortEdges(topology, node)
        val expectedTransitions =
            transitionsBetween(node, firstPortEdges, secondPortEdges) +
                transitionsBetween(node, secondPortEdges, firstPortEdges)

        assertEquals(
            expectedSwitches,
            node.vaihteet.map { reference -> reference.oid to reference.vaihdepiste }.toSet(),
            "$resolution: the combined node must contain both switch ports",
        )
        assertEquals(
            expectedFirstPortEdges,
            firstPortEdges.size,
            "$resolution: unexpected first switch port edge count",
        )
        assertEquals(
            expectedSecondPortEdges,
            secondPortEdges.size,
            "$resolution: unexpected second switch port edge count",
        )
        assertEquals(
            expectedTransitions,
            node.kulkusuunnat.map(::TopologyTransitionKey).toSet(),
            "$resolution: only transitions between the two switch ports must be allowed",
        )
    }

    private fun combinedPortEdges(
        topology: ExtTestTopologyV1,
        node: ExtTestTopologyNodeV1,
    ): Pair<List<ExtTestTopologyEdgeV1>, List<ExtTestTopologyEdgeV1>> {
        val nodesById = topology.solmut.associateBy(ExtTestTopologyNodeV1::id)
        val edgesByOtherNode =
            topology.kaaret
                .filter { edge -> edge.alkusolmu == node.id || edge.loppusolmu == node.id }
                .map { edge ->
                    val otherNodeId = if (edge.alkusolmu == node.id) edge.loppusolmu else edge.alkusolmu
                    edge to nodesById.getValue(otherNodeId)
                }
        return edgesByOtherNode
            .filter { (_, otherNode) -> otherNode.sijainti.x < node.sijainti.x }
            .map { (edge, _) -> edge } to
            edgesByOtherNode
                .filter { (_, otherNode) -> otherNode.sijainti.x > node.sijainti.x }
                .map { (edge, _) -> edge }
    }

    private fun transitionsBetween(
        node: ExtTestTopologyNodeV1,
        incomingEdges: List<ExtTestTopologyEdgeV1>,
        outgoingEdges: List<ExtTestTopologyEdgeV1>,
    ): Set<TopologyTransitionKey> =
        incomingEdges
            .flatMap { incomingEdge ->
                outgoingEdges.map { outgoingEdge ->
                    TopologyTransitionKey(
                        incomingEdge = incomingEdge.id,
                        incomingDirection = if (incomingEdge.alkusolmu == node.id) "laskeva" else "nouseva",
                        outgoingEdge = outgoingEdge.id,
                        outgoingDirection = if (outgoingEdge.alkusolmu == node.id) "nouseva" else "laskeva",
                    )
                }
            }
            .toSet()

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
            firstSwitchOid = firstSwitchOid,
            secondSwitchOid = secondSwitchOid,
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

    private val topologyResolutions = listOf("nano", "mikro")
}
