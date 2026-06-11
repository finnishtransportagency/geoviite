package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.PartialTrackBoundaryMoveRevertException
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.error.TrackBoundaryMoveFailureException
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationInMain
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.PublicationValidationService
import fi.fta.geoviite.infra.publication.TrackBoundaryMovePublicationGroup
import fi.fta.geoviite.infra.publication.publicationRequest
import fi.fta.geoviite.infra.publication.publicationRequestIds
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveCounterpart
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveDirection
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveDisabledReason
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryOrientation
import fi.fta.geoviite.infra.trackBoundaryMove.SwitchJointId
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMove
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveDao
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveService
import fi.fta.geoviite.infra.tracklayout.EdgeContentKey
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.combineEdges
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.offsetGeometry
import fi.fta.geoviite.infra.tracklayout.referenceLineGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkRR
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackNumber
import kotlin.test.assertContains
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class TrackBoundaryMoveIT
@Autowired
constructor(
    private val trackBoundaryMoveService: TrackBoundaryMoveService,
    private val locationTrackService: LocationTrackService,
    private val trackBoundaryMoveDao: TrackBoundaryMoveDao,
    private val publicationService: PublicationService,
    private val publicationValidationService: PublicationValidationService,
    private val splitDao: SplitDao,
    private val splitService: SplitService,
    private val locationTrackDao: LocationTrackDao,
    private val splitTestDataService: SplitTestDataService,
) : DBTestBase() {
    @BeforeEach
    fun setup() {
        testDBService.clearPublicationTables()
        testDBService.clearLayoutTables()
    }

    @Test
    fun `move along ascending track`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        // three switches, all laid out to one physically continuous track, with each having just a joint 1 on the left,
        // 2 on the right, and out-of-switch segments in between. lengtheningTrack contains switch 1, shorteningTrack
        // contains switches 2 and 3.

        val switch1 = testDBService.save(switch()).id
        val switch2 = testDBService.save(switch()).id
        val switch3 = testDBService.save(switch()).id

        val lengtheningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))),
                        endOuterSwitch = switchLinkYV(switch1, 1),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch1, 1),
                        endInnerSwitch = switchLinkYV(switch1, 2),
                    ),
                ),
            )

        val shorteningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch1, 2),
                        endOuterSwitch = switchLinkYV(switch2, 1),
                    ),
                    edge(
                        listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch2, 1),
                        endInnerSwitch = switchLinkYV(switch2, 2),
                    ),
                    edge(
                        listOf(segment(Point(40.0, 0.0), Point(50.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch2, 2),
                        endOuterSwitch = switchLinkYV(switch3, 1),
                    ),
                    edge(
                        listOf(segment(Point(50.0, 0.0), Point(60.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch3, 1),
                        endInnerSwitch = switchLinkYV(switch3, 3),
                    ),
                    edge(
                        listOf(segment(Point(60.0, 0.0), Point(70.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch3, 3),
                    ),
                ),
            )

        val trackBoundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.Companion.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(switch2, JointNumber(1)),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val savedBoundaryMove = trackBoundaryMoveDao.getOrThrow(trackBoundaryMoveId)
        assertEquals(shorteningTrack, savedBoundaryMove.shortenedLocationTrack)
        assertEquals(lengtheningTrack, savedBoundaryMove.lengthenedLocationTrack)
        // the only moved edge touches switches 1 and 2 through outer links only, so nothing gets relinked
        assertEquals(emptyList<IntId<LayoutSwitch>>(), savedBoundaryMove.relinkedSwitches)

        assertEquals(3, newLengthenedGeometry.edges.size)
        assertEquals(switchLinkYV(switch2, 1), newLengthenedGeometry.edges.last().endNode.switchOut)
        assertEquals(switchLinkYV(switch2, 1), newShortenedGeometry.edges.first().startNode.switchIn)
        assertEquals(4, newShortenedGeometry.edges.size)
    }

    @Test
    fun `move in descending direction on short track`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        // The shortening track lies entirely inside the switch, so the whole track gets moved and its switch relinked.
        val (switchVersion, straightSwitchEdges, turningSwitchEdges) =
            splitTestDataService.createSwitchAndGeometry(Point(0.0, 0.0))
        mainOfficialContext.save(locationTrack(trackNumber), trackGeometry(turningSwitchEdges))
        val switch1 = switchVersion.id
        val switchEnd = straightSwitchEdges.last().lastSegmentEnd

        val shorteningTrack = testDBService.save(locationTrack(trackNumber), trackGeometry(straightSwitchEdges))

        val lengtheningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(switchEnd.x, switchEnd.y), Point(switchEnd.x + 10.0, switchEnd.y))),
                        startOuterSwitch = switchLinkYV(switch1, 2),
                    )
                ),
            )

        val trackBoundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.Companion.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(switch1, JointNumber(1)),
                boundaryMoveDirection = BoundaryMoveDirection.ASCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val savedBoundaryMove = trackBoundaryMoveDao.getOrThrow(trackBoundaryMoveId)
        assertEquals(shorteningTrack, savedBoundaryMove.shortenedLocationTrack)
        assertEquals(lengtheningTrack, savedBoundaryMove.lengthenedLocationTrack)
        assertEquals(listOf(switch1), savedBoundaryMove.relinkedSwitches)
        assertEquals(switch1, newLengthenedGeometry.edges.first().startNode.switchIn?.id)

        assertEquals(straightSwitchEdges.size + 1, newLengthenedGeometry.edges.size)
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `shortened track remains with no geometry`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        // three switches, all laid out to one physically continuous track. lengtheningTrack contains switch 1,
        // shorteningTrack contains switches 2 and 3; switch 2 is inside the moved edge range and gets relinked.

        val switch1 = testDBService.save(switch()).id
        val (switch2Version, switch2StraightEdges, switch2TurningEdges) =
            splitTestDataService.createSwitchAndGeometry(Point(30.0, 0.0))
        mainOfficialContext.save(locationTrack(trackNumber), trackGeometry(switch2TurningEdges))
        val switch2 = switch2Version.id
        val switch2End = switch2StraightEdges.last().lastSegmentEnd
        val switch3 = testDBService.save(switch()).id

        val lengtheningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))),
                        endOuterSwitch = switchLinkYV(switch1, 1),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch1, 1),
                        endInnerSwitch = switchLinkYV(switch1, 2),
                    ),
                ),
            )

        val shorteningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    combineEdges(
                        listOf(
                            edge(
                                listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                                startOuterSwitch = switchLinkYV(switch1, 2),
                            )
                        ) +
                            switch2StraightEdges +
                            edge(
                                listOf(
                                    segment(
                                        Point(switch2End.x, switch2End.y),
                                        Point(switch2End.x + 10.0, switch2End.y),
                                    )
                                ),
                                endOuterSwitch = switchLinkYV(switch3, 1),
                            )
                    )
                ),
            )

        val trackBoundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.Companion.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(switch3, JointNumber(1)),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        assertEquals(listOf(switch2), trackBoundaryMoveDao.getOrThrow(trackBoundaryMoveId).relinkedSwitches)
        assertEquals(4 + switch2StraightEdges.size, newLengthenedGeometry.edges.size)
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `combine unconnected tracks with combinable geometries`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        val switch1 = testDBService.save(switch()).id
        // initially topologically disconnected tracks
        val lengtheningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val shorteningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1))
                ),
            )
        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.Companion.main,
            shorteningTrackId = shorteningTrack.id,
            lengtheningTrackId = lengtheningTrack.id,
            upToSwitchJoint = SwitchJointId(switch1, JointNumber(1)),
            boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
        )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        assertEquals(1, newLengthenedGeometry.edges.size)
        assertEquals(Point(0.0, 0.0), newLengthenedGeometry.edges[0].start.toPoint())
        assertEquals(Point(20.0, 0.0), newLengthenedGeometry.edges[0].end.toPoint())
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `upToSwitchJoint=null with descending move appends shortening geometry`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id

        val lengtheningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1)),
                edge(
                    listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch1, 1),
                    endInnerSwitch = switchLinkYV(switch1, 2),
                ),
            )
        val shorteningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))), startOuterSwitch = switchLinkYV(switch1, 2))
            )

        val lengtheningTrack = testDBService.save(locationTrack(trackNumber), lengtheningGeometry)
        val shorteningTrack = testDBService.save(locationTrack(trackNumber), shorteningGeometry)

        val boundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                upToSwitchJoint = null,
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, shorteningTrack.id).second
        assertSameEdgeContent(lengtheningGeometry.edges + shorteningGeometry.edges, newLengthenedGeometry.edges)
        assertEquals(0, newShortenedGeometry.edges.size)
        // the moved edge only touches switch1 through an outer link, so nothing needed relinking
        assertEquals(
            emptyList<IntId<LayoutSwitch>>(),
            trackBoundaryMoveDao.getOrThrow(boundaryMoveId).relinkedSwitches,
        )
    }

    @Test
    fun `upToSwitchJoint=null with ascending move prepends shortening geometry`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id

        val shorteningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(0.0, 0.0), Point(20.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1))
            )
        val lengtheningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))), startOuterSwitch = switchLinkYV(switch1, 1))
            )

        val shorteningTrack = testDBService.save(locationTrack(trackNumber), shorteningGeometry)
        val lengtheningTrack = testDBService.save(locationTrack(trackNumber), lengtheningGeometry)

        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.main,
            shorteningTrackId = shorteningTrack.id,
            lengtheningTrackId = lengtheningTrack.id,
            upToSwitchJoint = null,
            boundaryMoveDirection = BoundaryMoveDirection.ASCENDING,
        )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, shorteningTrack.id).second
        assertSameEdgeContent(shorteningGeometry.edges + lengtheningGeometry.edges, newLengthenedGeometry.edges)
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `location tracks get grouped per their boundary move`() {
        val setup = saveConnectedTracks()

        val boundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = setup.shorteningTrack.id,
                lengtheningTrackId = setup.lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )

        val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
        val expectedGroup = TrackBoundaryMovePublicationGroup(boundaryMoveId)

        val affectedTrackIds = listOf(setup.shorteningTrack.id, setup.lengtheningTrack.id)
        candidates.locationTracks
            .filter { candidate -> candidate.id in affectedTrackIds }
            .also { filtered -> assertEquals(2, filtered.size) }
            .forEach { candidate -> assertEquals(expectedGroup, candidate.publicationGroup) }
    }

    @Test
    fun `successive boundary moves are grouped and stamped with their own publication`() {
        // Two independent boundary moves, each saved -> verified as a publication group -> published in turn. The
        // second move only exists after the first has been published, so the candidate groups and the publication ids
        // stamped onto the boundary moves must stay distinct between the rounds.
        repeat(2) {
            val setup = savePublishableConnectedTracks()
            val boundaryMoveId =
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            assertEquals(null, trackBoundaryMoveDao.getOrThrow(boundaryMoveId).publicationId)

            val affectedTrackIds = listOf(setup.shorteningTrack.id, setup.lengtheningTrack.id)
            val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
            candidates.locationTracks
                .filter { candidate -> candidate.id in affectedTrackIds }
                .also { filtered -> assertEquals(2, filtered.size) }
                .forEach { candidate ->
                    assertEquals(TrackBoundaryMovePublicationGroup(boundaryMoveId), candidate.publicationGroup)
                }

            // Publishing the two affected tracks pulls the boundary move into the publication automatically.
            val publicationResult =
                publicationService.publishManualPublication(
                    LayoutBranch.main,
                    publicationRequest(locationTracks = affectedTrackIds),
                )
            assertEquals(publicationResult.publicationId, trackBoundaryMoveDao.getOrThrow(boundaryMoveId).publicationId)
        }
    }

    @Test
    fun `saving a boundary move in a design branch is an error`() {
        val setup = savePublishableConnectedTracks()
        val designBranch = testDBService.createDesignBranch()

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    designBranch,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.branch-not-main"), exception.localizationKey)
    }

    @Test
    fun `track with unpublished changes cannot be part of a boundary move`() {
        val setup = savePublishableConnectedTracks()
        val (track, geometry) =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, setup.shorteningTrack.id)
        locationTrackService.saveDraft(LayoutBranch.main, track, offsetGeometry(geometry, Point(0.0, 0.0)))

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.track-draft-exists"), exception.localizationKey)
    }

    @Test
    fun `track with no geometry cannot be part of a boundary move`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val shorteningTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val emptyTrack = mainOfficialContext.save(locationTrack(trackNumber), TmpLocationTrackGeometry.empty)

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = shorteningTrack.id,
                    lengtheningTrackId = emptyTrack.id,
                    upToSwitchJoint = null,
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.no-geometry"), exception.localizationKey)
    }

    @Test
    fun `track that is part of an unpublished split cannot be part of a boundary move`() {
        val setup = savePublishableConnectedTracks()
        splitDao.saveSplit(setup.shorteningTrack, emptyList(), emptyList(), emptyList())

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.track-part-of-split"), exception.localizationKey)
    }

    @Test
    fun `track in a published but still unfinished split cannot be part of a boundary move`() {
        // A split stays unfinished after publication until its bulk transfer is done, and must keep blocking boundary
        // moves on its tracks for that whole time.
        val setup = savePublishableConnectedTracks()
        val splitId = splitDao.saveSplit(setup.lengtheningTrack, emptyList(), emptyList(), emptyList())
        splitDao.updateSplit(splitId, publicationId = setup.publicationId)

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.track-part-of-split"), exception.localizationKey)
    }

    @Test
    fun `track that is part of an unpublished boundary move cannot be part of another boundary move`() {
        val setup = savePublishableConnectedTracks()
        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.main,
            shorteningTrackId = setup.shorteningTrack.id,
            lengtheningTrackId = setup.lengtheningTrack.id,
            upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
            boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
        )

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = null,
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(
            LocalizationKey.of("error.track-boundary-move.track-part-of-boundary-move"),
            exception.localizationKey,
        )
    }

    @Test
    fun `track whose switch is part of an unpublished split cannot be part of a boundary move`() {
        val setup = savePublishableConnectedTracks()
        // A split on an unrelated track, but whose relinked switches include a switch on the shortening track.
        val otherTrackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val otherTrack =
            mainOfficialContext.save(
                locationTrack(otherTrackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 100.0), Point(10.0, 100.0))))),
            )
        splitDao.saveSplit(otherTrack, emptyList(), listOf(setup.shorteningOnlySwitch), emptyList())

        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.switches-part-of-split"), exception.localizationKey)
    }

    @Test
    fun `splitting a track that is part of an unpublished boundary move is an error`() {
        val setup = savePublishableConnectedTracks()
        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.main,
            shorteningTrackId = setup.shorteningTrack.id,
            lengtheningTrackId = setup.lengtheningTrack.id,
            upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
            boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
        )

        val exception =
            assertThrows<SplitFailureException> {
                splitService.split(LayoutBranch.main, SplitRequest(setup.shorteningTrack.id, emptyList()))
            }
        assertEquals(
            LocalizationKey.of("error.split.source-track-in-unpublished-boundary-move"),
            exception.localizationKey,
        )
    }

    @Test
    fun `counterpart options flag counterparts with unpublished changes`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val headTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val draftCounterpart =
            mainDraftContext.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(10.5, 0.0), Point(20.0, 0.0))))),
            )

        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = draftCounterpart.id,
                    orientation = BoundaryOrientation.HEAD_FIRST,
                    connectingSwitchJoint = null,
                    disabledReasons = listOf(BoundaryMoveDisabledReason.TRACK_DRAFT_EXISTS),
                )
            ),
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, headTrack.id),
        )
    }

    @Test
    fun `counterpart options flag counterparts in unpublished splits and boundary moves`() {
        val setup = savePublishableConnectedTracks()
        splitDao.saveSplit(setup.shorteningTrack, emptyList(), emptyList(), emptyList())

        val options =
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(
                LayoutBranch.main.draft,
                setup.lengtheningTrack.id,
            )
        assertEquals(
            listOf(BoundaryMoveDisabledReason.PART_OF_SPLIT),
            options.single { option -> option.trackId == setup.shorteningTrack.id }.disabledReasons,
        )
    }

    @Test
    fun `publication validation catches geometry changes made after the boundary move`() {
        val setup = savePublishableConnectedTracks()

        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.main,
            shorteningTrackId = setup.shorteningTrack.id,
            lengtheningTrackId = setup.lengtheningTrack.id,
            upToSwitchJoint = SwitchJointId(setup.shorteningOnlySwitch, JointNumber(1)),
            boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
        )

        // The boundary move itself preserves the combined geometry of the two tracks. Tamper with the lengthened
        // track's geometry afterwards so that the combined geometry no longer matches what it was before the move.
        val (lengthenedTrack, lengthenedGeometry) =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, setup.lengtheningTrack.id)
        locationTrackService.saveDraft(
            LayoutBranch.main,
            lengthenedTrack,
            offsetGeometry(lengthenedGeometry, Point(0.0, 5.0)),
        )

        val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
        val validation =
            publicationValidationService.validatePublicationCandidates(
                candidates,
                publicationRequestIds(locationTracks = listOf(setup.shorteningTrack.id, setup.lengtheningTrack.id)),
            )
        val issues = validation.validatedAsPublicationUnit.locationTracks.flatMap { it.issues }

        assertTrue(
            issues.any {
                it.localizationKey == LocalizationKey.of("validation.layout.track-boundary-move.changed-geometry")
            },
            "Expected a track boundary move changed-geometry error, but got: $issues",
        )
    }

    @Test
    fun `counterpart options for tracks meeting without a connecting switch`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        val trackA =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val trackB =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(10.5, 0.0), Point(20.0, 0.0))))),
            )

        val optionsFromA =
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, trackA.id)
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = trackB.id,
                    orientation = BoundaryOrientation.HEAD_FIRST,
                    connectingSwitchJoint = null,
                )
            ),
            optionsFromA,
        )

        val optionsFromB =
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, trackB.id)
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = trackA.id,
                    orientation = BoundaryOrientation.COUNTERPART_FIRST,
                    connectingSwitchJoint = null,
                )
            ),
            optionsFromB,
        )
    }

    @Test
    fun `counterpart options for tracks meeting at a switch boundary`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id

        // headTrack ends inside switch1 at joint 2; counterpartTrack starts with an outer (topological) link to it.
        val headTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))),
                        endOuterSwitch = switchLinkYV(switch1, 1),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch1, 1),
                        endInnerSwitch = switchLinkYV(switch1, 2),
                    ),
                ),
            )
        val counterpartTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch1, 2),
                    )
                ),
            )

        val options = trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, headTrack.id)
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = counterpartTrack.id,
                    orientation = BoundaryOrientation.HEAD_FIRST,
                    connectingSwitchJoint = SwitchJointId(switch1, JointNumber(2)),
                )
            ),
            options,
        )

        val optionsFromCounterpart =
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, counterpartTrack.id)
        // counterpart's first edge starts with an outer link to switch1 joint2 only (no inner), so the linking joint
        // from its perspective is the outer one.
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = headTrack.id,
                    orientation = BoundaryOrientation.COUNTERPART_FIRST,
                    connectingSwitchJoint = SwitchJointId(switch1, JointNumber(2)),
                )
            ),
            optionsFromCounterpart,
        )
    }

    @Test
    fun `counterpart options for tracks meeting within a switch`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id

        val trackA =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                        startInnerSwitch = switchLinkRR(switch1, 1),
                        endInnerSwitch = switchLinkRR(switch1, 5),
                    )
                ),
            )
        val trackB =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startInnerSwitch = switchLinkRR(switch1, 5),
                        endInnerSwitch = switchLinkRR(switch1, 2),
                    )
                ),
            )

        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackB.id,
                    BoundaryOrientation.HEAD_FIRST,
                    SwitchJointId(switch1, JointNumber(5)),
                )
            ),
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, trackA.id),
        )
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackA.id,
                    BoundaryOrientation.COUNTERPART_FIRST,
                    SwitchJointId(switch1, JointNumber(5)),
                )
            ),
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, trackB.id),
        )
    }

    @Test
    fun `counterpart options for tracks meeting at head-to-head switches`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id
        val switch2 = testDBService.save(switch()).id

        // headTrack ends inside switch1 at joint 2, with an outer link to switch2 joint 1 at the same boundary node.
        // counterpartTrack starts inside switch2 at joint 1, with an outer link to switch1 joint 2.
        val headTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1)),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch1, 1),
                        endInnerSwitch = switchLinkYV(switch1, 2),
                        endOuterSwitch = switchLinkYV(switch2, 1),
                    ),
                ),
            )
        val counterpartTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch2, 1),
                        startOuterSwitch = switchLinkYV(switch1, 2),
                        endInnerSwitch = switchLinkYV(switch2, 2),
                    ),
                    edge(
                        listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch2, 2),
                    ),
                ),
            )

        val options = trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, headTrack.id)
        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = counterpartTrack.id,
                    orientation = BoundaryOrientation.HEAD_FIRST,
                    // head's inner switch at the boundary is switch1 joint 2 (its outer is switch2 joint 1)
                    connectingSwitchJoint = SwitchJointId(switch1, JointNumber(2)),
                )
            ),
            options,
        )
    }

    @Test
    fun `counterpart options flag tracks on a different track number`() {
        val trackNumberA = mainDraftContext.createLayoutTrackNumber().id
        val trackNumberB = mainDraftContext.createLayoutTrackNumber().id

        val headTrack =
            testDBService.save(
                locationTrack(trackNumberA),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val otherTrackNumberTrack =
            testDBService.save(
                locationTrack(trackNumberB),
                trackGeometry(edge(listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))))),
            )

        assertEquals(
            listOf(
                BoundaryMoveCounterpart(
                    trackId = otherTrackNumberTrack.id,
                    orientation = BoundaryOrientation.HEAD_FIRST,
                    connectingSwitchJoint = null,
                    disabledReasons = listOf(BoundaryMoveCounterpartDisabledReason.ON_DIFFERENT_TRACK_NUMBER),
                )
            ),
            trackBoundaryMoveService.getBoundaryMoveCounterpartOptions(LayoutBranch.main.draft, headTrack.id),
        )
    }

    @Test
    fun `moving the boundary to a joint that is on neither track is an error`() {
        val setup = saveConnectedTracks()
        // A switch that exists but isn't linked to either track.
        val unrelatedSwitch = testDBService.save(switch()).id

        assertThrows<TrackBoundaryMoveFailureException> {
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = setup.shorteningTrack.id,
                lengtheningTrackId = setup.lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(unrelatedSwitch, JointNumber(1)),
                boundaryMoveDirection = BoundaryMoveDirection.ASCENDING,
            )
        }
    }

    @Test
    fun `moving the boundary to the joint already connecting the tracks is an error`() {
        val setup = saveConnectedTracks()

        // The connecting joint sits on both tracks, so moving the boundary there wouldn't move it at all.
        assertThrows<TrackBoundaryMoveFailureException> {
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = setup.shorteningTrack.id,
                lengtheningTrackId = setup.lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(setup.connectingSwitch, JointNumber(2)),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        }
    }

    @Test
    fun `boundary move relinks inner switches of the moved edges`() {
        val (boundaryMoveId, switchId) = saveBoundaryMoveOverRelinkableSwitch()
        assertEquals(listOf(switchId), trackBoundaryMoveDao.getOrThrow(boundaryMoveId).relinkedSwitches)
    }

    @Test
    fun `relinked switches get grouped per their boundary move in publication candidates`() {
        val (boundaryMoveId, switchId) = saveBoundaryMoveOverRelinkableSwitch()

        // relinking the switch drafted it, so it shows up as a publication candidate grouped with the move
        val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
        val switchCandidate = candidates.switches.single { candidate -> candidate.id == switchId }
        assertEquals(TrackBoundaryMovePublicationGroup(boundaryMoveId), switchCandidate.publicationGroup)
    }

    @Test
    fun `boundary move relinked switches are pulled into revert requests with the move`() {
        val (boundaryMoveId, switchId) = saveBoundaryMoveOverRelinkableSwitch()
        val move = trackBoundaryMoveDao.getOrThrow(boundaryMoveId)

        val dependenciesOfTrack =
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequestIds(locationTracks = listOf(move.shortenedLocationTrack.id)),
            )
        assertContains(dependenciesOfTrack.switches, switchId)

        val dependenciesOfSwitch =
            publicationService.getRevertRequestDependencies(
                LayoutBranch.main,
                publicationRequestIds(switches = listOf(switchId)),
            )
        assertContains(dependenciesOfSwitch.locationTracks, move.shortenedLocationTrack.id)
        assertContains(dependenciesOfSwitch.locationTracks, move.lengthenedLocationTrack.id)
    }

    @Test
    fun `reverting a boundary move without its relinked switches throws`() {
        val (boundaryMoveId, switchId) = saveBoundaryMoveOverRelinkableSwitch()
        val move = trackBoundaryMoveDao.getOrThrow(boundaryMoveId)
        val moveTracks = listOf(move.shortenedLocationTrack.id, move.lengthenedLocationTrack.id)

        assertThrows<PartialTrackBoundaryMoveRevertException> {
            publicationService.revertPublicationCandidates(
                LayoutBranch.main,
                publicationRequestIds(locationTracks = moveTracks),
            )
        }
        assertNotNull(trackBoundaryMoveDao.get(boundaryMoveId))

        publicationService.revertPublicationCandidates(
            LayoutBranch.main,
            publicationRequestIds(locationTracks = moveTracks, switches = listOf(switchId)),
        )
        assertNull(trackBoundaryMoveDao.get(boundaryMoveId))
    }

    @Test
    fun `publication validation requires relinked switches to be staged with the boundary move`() {
        val (_, switchId) = saveBoundaryMoveOverRelinkableSwitch()
        val candidates = publicationService.collectPublicationCandidates(PublicationInMain)
        val trackIds = candidates.locationTracks.map { candidate -> candidate.id }
        val missingSwitchesKey = LocalizationKey.of("validation.layout.track-boundary-move.missing-switches")

        val issuesWithoutSwitch =
            publicationValidationService
                .validatePublicationCandidates(candidates, publicationRequestIds(locationTracks = trackIds))
                .validatedAsPublicationUnit
                .locationTracks
                .flatMap { candidate -> candidate.issues }
        assertTrue(
            issuesWithoutSwitch.any { issue -> issue.localizationKey == missingSwitchesKey },
            "Expected a track boundary move missing-switches error, but got: $issuesWithoutSwitch",
        )

        val issuesWithSwitch =
            publicationValidationService
                .validatePublicationCandidates(
                    candidates,
                    publicationRequestIds(locationTracks = trackIds, switches = listOf(switchId)),
                )
                .validatedAsPublicationUnit
                .locationTracks
                .flatMap { candidate -> candidate.issues }
        assertTrue(
            issuesWithSwitch.none { issue -> issue.localizationKey == missingSwitchesKey },
            "Expected no track boundary move missing-switches error, but got: $issuesWithSwitch",
        )
    }

    @Test
    fun `boundary move over a switch that cannot be relinked is an error`() {
        val setup = saveConnectedTracks()

        // upToSwitchJoint=null moves all of the shortening track's edges, including the ones holding the unlinkable
        // switch2 as an inner switch
        val exception =
            assertThrows<TrackBoundaryMoveFailureException> {
                trackBoundaryMoveService.saveTrackBoundaryMove(
                    LayoutBranch.main,
                    shorteningTrackId = setup.shorteningTrack.id,
                    lengtheningTrackId = setup.lengtheningTrack.id,
                    upToSwitchJoint = null,
                    boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
                )
            }
        assertEquals(LocalizationKey.of("error.track-boundary-move.switch-linking-failed"), exception.localizationKey)

        // the whole move was rolled back: no boundary move exists and the tracks kept their geometries
        assertEquals(
            emptyList<TrackBoundaryMove>(),
            trackBoundaryMoveService.findUnpublishedBoundaryMoves(LayoutBranch.main),
        )
        val unchangedShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.main.draft, setup.shorteningTrack.id).second
        assertSameEdgeContent(setup.shorteningGeometry.edges, unchangedShortenedGeometry.edges)
    }

    private fun saveBoundaryMoveOverRelinkableSwitch(): Pair<IntId<TrackBoundaryMove>, IntId<LayoutSwitch>> {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val (switchVersion, straightSwitchEdges, turningSwitchEdges) =
            splitTestDataService.createSwitchAndGeometry(Point(10.0, 0.0))
        mainOfficialContext.save(locationTrack(trackNumber), trackGeometry(turningSwitchEdges))

        val lengtheningTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        val switchEnd = straightSwitchEdges.last().lastSegmentEnd
        val shorteningTrack =
            mainOfficialContext.save(
                locationTrack(trackNumber),
                trackGeometry(
                    combineEdges(
                        straightSwitchEdges +
                            edge(
                                listOf(segment(Point(switchEnd.x, switchEnd.y), Point(switchEnd.x + 10.0, switchEnd.y)))
                            )
                    )
                ),
            )

        val boundaryMoveId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                upToSwitchJoint = SwitchJointId(switchVersion.id, JointNumber(2)),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        return boundaryMoveId to switchVersion.id
    }

    private fun unlinkableSwitch() =
        switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(1000.0, 1000.0), null)))

    private data class ConnectedTracks(
        val lengtheningTrack: LayoutRowVersion<LocationTrack>,
        val lengtheningGeometry: TmpLocationTrackGeometry,
        val shorteningTrack: LayoutRowVersion<LocationTrack>,
        val shorteningGeometry: TmpLocationTrackGeometry,
        // The lengthening track ends and the shortening track starts at switch1 joint 2: it is the joint already
        // connecting the two tracks.
        val connectingSwitch: IntId<LayoutSwitch>,
        // switch2 is on the shortening track only; its joint 1 is a valid target to move the boundary to.
        val shorteningOnlySwitch: IntId<LayoutSwitch>,
        // The publication that made the tracks official, when they were saved as publishable.
        val publicationId: IntId<Publication>? = null,
    )

    // Two tracks meeting at switch1 joint 2, laid out as one physically continuous track. The lengthening track holds
    // switch1; the shortening track holds switch2.
    private fun saveConnectedTracks(): ConnectedTracks {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id
        val switch2 = testDBService.save(unlinkableSwitch()).id

        val lengtheningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1)),
                edge(
                    listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch1, 1),
                    endInnerSwitch = switchLinkYV(switch1, 2),
                ),
            )

        val shorteningGeometry =
            trackGeometry(
                edge(
                    listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch1, 2),
                    endOuterSwitch = switchLinkYV(switch2, 1),
                ),
                edge(
                    listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch2, 1),
                    endInnerSwitch = switchLinkYV(switch2, 2),
                ),
            )

        val lengtheningTrack = testDBService.save(locationTrack(trackNumber), lengtheningGeometry)
        val shorteningTrack = testDBService.save(locationTrack(trackNumber), shorteningGeometry)

        return ConnectedTracks(
            lengtheningTrack = lengtheningTrack,
            lengtheningGeometry = lengtheningGeometry,
            shorteningTrack = shorteningTrack,
            shorteningGeometry = shorteningGeometry,
            connectingSwitch = switch1,
            shorteningOnlySwitch = switch2,
        )
    }

    private fun savePublishableConnectedTracks(): ConnectedTracks {
        val trackNumberId =
            mainOfficialContext
                .save(
                    trackNumber(testDBService.getUnusedTrackNumber()),
                    referenceLineGeometry(segment(Point(0.0, 0.0), Point(40.0, 0.0))),
                )
                .id
        val switch1 = mainOfficialContext.createSwitch().id
        val switch2 = mainOfficialContext.createSwitch().id

        val lengtheningGeometry =
            trackGeometry(
                edge(listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))), endOuterSwitch = switchLinkYV(switch1, 1)),
                edge(
                    listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch1, 1),
                    endInnerSwitch = switchLinkYV(switch1, 2),
                ),
            )

        val shorteningGeometry =
            trackGeometry(
                edge(
                    listOf(segment(Point(20.0, 0.0), Point(30.0, 0.0))),
                    startOuterSwitch = switchLinkYV(switch1, 2),
                    endOuterSwitch = switchLinkYV(switch2, 1),
                ),
                edge(
                    listOf(segment(Point(30.0, 0.0), Point(40.0, 0.0))),
                    startInnerSwitch = switchLinkYV(switch2, 1),
                    endInnerSwitch = switchLinkYV(switch2, 2),
                ),
            )

        val lengtheningTrack = mainDraftContext.save(locationTrack(trackNumberId), lengtheningGeometry)
        val shorteningTrack = mainDraftContext.save(locationTrack(trackNumberId), shorteningGeometry)

        mainDraftContext.generateOid(lengtheningTrack.id)
        mainDraftContext.generateOid(shorteningTrack.id)

        val publicationResult =
            publicationService.publishManualPublication(
                LayoutBranch.main,
                publicationRequest(locationTracks = listOf(lengtheningTrack.id, shorteningTrack.id)),
            )

        return ConnectedTracks(
            lengtheningTrack = locationTrackDao.fetchVersionOrThrow(LayoutBranch.main.official, lengtheningTrack.id),
            lengtheningGeometry = lengtheningGeometry,
            shorteningTrack = locationTrackDao.fetchVersionOrThrow(LayoutBranch.main.official, shorteningTrack.id),
            shorteningGeometry = shorteningGeometry,
            connectingSwitch = switch1,
            shorteningOnlySwitch = switch2,
            publicationId = publicationResult.publicationId,
        )
    }

    private fun assertSameEdgeContent(expected: List<LayoutEdge>, actual: List<LayoutEdge>) =
        assertEquals(
            TmpLocationTrackGeometry.of(expected, IntId(0)).edges.map(EdgeContentKey::of),
            TmpLocationTrackGeometry.of(actual, IntId(0)).edges.map(EdgeContentKey::of),
        )
}
