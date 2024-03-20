package fi.fta.geoviite.infra.linking


import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.boundingBoxAroundPoints
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class LinkingDaoIT @Autowired constructor(
    private val linkingDao: LinkingDao,
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
) : DBTestBase() {


    @Test
    fun noSwitchBoundsAreFoundWhenNotLinkedToTracks() {
        val switch = switchService.getOrThrow(DRAFT, switchService.saveDraft(switch(1, draft = true)).id)
        assertEquals(null, linkingDao.getSwitchBoundsFromTracks(OFFICIAL, switch.id as IntId))
        assertEquals(null, linkingDao.getSwitchBoundsFromTracks(DRAFT, switch.id as IntId))
    }

    @Test
    fun switchBoundsAreFoundFromTracks() {
        val trackNumber = getOrCreateTrackNumber(TrackNumber("123"))
        val tnId = trackNumber.id as IntId
        val switch = switchService.getOrThrow(DRAFT, switchService.saveDraft(switch(1, draft = true)).id)

        val point1 = Point(10.0, 10.0)
        val point2 = Point(12.0, 10.0)
        val point3_1 = Point(10.0, 12.0)
        val point3_2 = Point(10.0, 13.0)

        // Linked from the start only -> second point shouldn't matter
        locationTrackService.saveDraft(
            locationTrack(tnId, externalId = someOid(), draft = true).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(1)),
            ), alignment(segment(point1, point1 + Point(5.0, 5.0)))
        )
        // Linked from the end only -> first point shouldn't matter
        locationTrackService.saveDraft(
            locationTrack(tnId, externalId = null, draft = true).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(2)),
            ), alignment(segment(point2 - Point(5.0, 5.0), point2))
        )
        // Linked by segment ends -> both points matter
        locationTrackService.saveDraft(
            locationTrack(tnId, externalId = someOid(), draft = true),
            alignment(
                segment(point3_1, point3_2).copy(
                    switchId = switch.id as IntId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                )
            ),
        )
        assertEquals(null, linkingDao.getSwitchBoundsFromTracks(OFFICIAL, switch.id as IntId))
        assertEquals(
            boundingBoxAroundPoints(point1, point2, point3_1, point3_2),
            linkingDao.getSwitchBoundsFromTracks(DRAFT, switch.id as IntId),
        )
    }
}
