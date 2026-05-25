package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackNumber
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class TrackBoundaryChangeIT
@Autowired
constructor(
    private val splitService: SplitService,
    private val locationTrackService: LocationTrackService,
    private val splitDao: SplitDao,
) : DBTestBase() {
    @BeforeEach
    fun setup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun `move along ascending track`() {
        val trackNumber = testDBService.save(trackNumber()).id

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
            splitService.saveTrackBoundaryChange(
                LayoutBranch.Companion.main,
                shorteningTrack.id,
                lengtheningTrack.id,
                switch2,
                JointNumber(1),
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val split = splitDao.getOrThrow(splitId)
        assertEquals(shorteningTrack, split.sourceLocationTrackVersion)
        assertEquals(1, split.targetLocationTracks.size)
        assertEquals(lengtheningTrack.id, split.targetLocationTracks[0].locationTrackId)
        assertEquals(SplitAdministrativeChangeType.BOUNDARY_CHANGE, split.administrativeChangeType)

        assertEquals(3, newLengthenedGeometry.edges.size)
        assertEquals(switchLinkYV(switch2, 1), newLengthenedGeometry.edges.last().endNode.switchOut)
        assertEquals(switchLinkYV(switch2, 1), newShortenedGeometry.edges.first().startNode.switchIn)
        assertEquals(4, newShortenedGeometry.edges.size)
    }

    @Test
    fun `move in descending direction on short track`() {
        val trackNumber = testDBService.save(trackNumber()).id

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
            splitService.saveTrackBoundaryChange(
                LayoutBranch.Companion.main,
                shorteningTrack.id,
                lengtheningTrack.id,
                switch1,
                JointNumber(1),
            )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        val split = splitDao.getOrThrow(splitId)
        assertEquals(shorteningTrack, split.sourceLocationTrackVersion)
        assertEquals(1, split.targetLocationTracks.size)
        assertEquals(lengtheningTrack.id, split.targetLocationTracks[0].locationTrackId)
        assertEquals(SplitAdministrativeChangeType.BOUNDARY_CHANGE, split.administrativeChangeType)
        assertEquals(switchLinkYV(switch1, 1), newLengthenedGeometry.edges.first().startNode.switchIn)

        assertEquals(2, newLengthenedGeometry.edges.size)
        assertEquals(0, newShortenedGeometry.edges.size)
    }

    @Test
    fun `shortened track remains with no geometry`() {
        val trackNumber = testDBService.save(trackNumber()).id

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

        splitService.saveTrackBoundaryChange(
            LayoutBranch.Companion.main,
            shorteningTrack.id,
            lengtheningTrack.id,
            switch3,
            JointNumber(1),
        )
        val newLengthenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, lengtheningTrack.id).second
        val newShortenedGeometry =
            locationTrackService.getWithGeometryOrThrow(LayoutBranch.Companion.main.draft, shorteningTrack.id).second
        assertEquals(5, newLengthenedGeometry.edges.size)
        assertEquals(0, newShortenedGeometry.edges.size)
    }
}
