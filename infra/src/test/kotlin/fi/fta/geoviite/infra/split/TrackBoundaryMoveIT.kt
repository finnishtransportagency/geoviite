package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.error.TrackBoundaryMoveFailureException
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveCounterpart
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryMoveDirection
import fi.fta.geoviite.infra.trackBoundaryMove.BoundaryOrientation
import fi.fta.geoviite.infra.trackBoundaryMove.SwitchJointId
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveDao
import fi.fta.geoviite.infra.trackBoundaryMove.TrackBoundaryMoveService
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkRR
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
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
) : DBTestBase() {
    @BeforeEach
    fun setup() {
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

        val splitId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.Companion.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                switch = switch2,
                switchJoint = JointNumber(1),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val savedBoundaryMove = trackBoundaryMoveDao.getOrThrow(splitId)
        assertEquals(shorteningTrack, savedBoundaryMove.shortenedLocationTrack)
        assertEquals(lengtheningTrack, savedBoundaryMove.lengthenedLocationTrack)

        assertEquals(3, newLengthenedGeometry.edges.size)
        assertEquals(switchLinkYV(switch2, 1), newLengthenedGeometry.edges.last().endNode.switchOut)
        assertEquals(switchLinkYV(switch2, 1), newShortenedGeometry.edges.first().startNode.switchIn)
        assertEquals(4, newShortenedGeometry.edges.size)
    }

    @Test
    fun `move in descending direction on short track`() {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id

        val switch1 = testDBService.save(switch()).id

        val shorteningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.01, 0.0), Point(10.0, 0.0))),
                        startInnerSwitch = switchLinkYV(switch1, 1),
                        endInnerSwitch = switchLinkYV(switch1, 3),
                    )
                ),
            )

        val lengtheningTrack =
            testDBService.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch1, 3),
                    )
                ),
            )

        val splitId =
            trackBoundaryMoveService.saveTrackBoundaryMove(
                LayoutBranch.Companion.main,
                shorteningTrackId = shorteningTrack.id,
                lengtheningTrackId = lengtheningTrack.id,
                switch = switch1,
                switchJoint = JointNumber(1),
                boundaryMoveDirection = BoundaryMoveDirection.ASCENDING,
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val savedBoundaryMove = trackBoundaryMoveDao.getOrThrow(splitId)
        assertEquals(shorteningTrack, savedBoundaryMove.shortenedLocationTrack)
        assertEquals(lengtheningTrack, savedBoundaryMove.lengthenedLocationTrack)
        assertEquals(switchLinkYV(switch1, 1), newLengthenedGeometry.edges.first().startNode.switchIn)

        assertEquals(2, newLengthenedGeometry.edges.size)
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `shortened track remains with no geometry`() {
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
                ),
            )

        trackBoundaryMoveService.saveTrackBoundaryMove(
            LayoutBranch.Companion.main,
            shorteningTrackId = shorteningTrack.id,
            lengtheningTrackId = lengtheningTrack.id,
            switch = switch3,
            switchJoint = JointNumber(1),
            boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
        )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        assertEquals(5, newLengthenedGeometry.edges.size)
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
            switch = switch1,
            switchJoint = JointNumber(1),
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
    fun `counterpart options excludes tracks on a different track number`() {
        val trackNumberA = mainDraftContext.createLayoutTrackNumber().id
        val trackNumberB = mainDraftContext.createLayoutTrackNumber().id

        val headTrack =
            testDBService.save(
                locationTrack(trackNumberA),
                trackGeometry(edge(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))))),
            )
        testDBService.save(
            locationTrack(trackNumberB),
            trackGeometry(edge(listOf(segment(Point(10.0, 0.0), Point(20.0, 0.0))))),
        )

        assertEquals(
            emptyList<BoundaryMoveCounterpart>(),
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
                switch = unrelatedSwitch,
                switchJoint = JointNumber(1),
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
                switch = setup.connectingSwitch,
                switchJoint = JointNumber(2),
                boundaryMoveDirection = BoundaryMoveDirection.DESCENDING,
            )
        }
    }

    private data class ConnectedTracks(
        val lengtheningTrack: LayoutRowVersion<LocationTrack>,
        val shorteningTrack: LayoutRowVersion<LocationTrack>,
        // The lengthening track ends and the shortening track starts at switch1 joint 2: it is the joint already
        // connecting the two tracks.
        val connectingSwitch: IntId<LayoutSwitch>,
        // switch2 is on the shortening track only; its joint 1 is a valid target to move the boundary to.
        val shorteningOnlySwitch: IntId<LayoutSwitch>,
    )

    // Two tracks meeting at switch1 joint 2, laid out as one physically continuous track. The lengthening track holds
    // switch1; the shortening track holds switch2.
    private fun saveConnectedTracks(): ConnectedTracks {
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switch1 = testDBService.save(switch()).id
        val switch2 = testDBService.save(switch()).id

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
                ),
            )

        return ConnectedTracks(
            lengtheningTrack = lengtheningTrack,
            shorteningTrack = shorteningTrack,
            connectingSwitch = switch1,
            shorteningOnlySwitch = switch2,
        )
    }
}
