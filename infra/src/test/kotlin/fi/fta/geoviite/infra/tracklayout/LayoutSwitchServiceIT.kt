package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.linking.TrackLayoutSwitchSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao.LocationTrackIdentifiers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchServiceIT @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackService: LocationTrackService,
    private val switchDao: LayoutSwitchDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "switch_joint", "switch", "location_track")
    }

    @Test
    fun switchOwnerIsReturned() {
        val owner = SwitchOwner(id = IntId(4), name = MetaDataName("Cinia"))

        switchDao.insert(switch(ownerId = owner.id, draft = false))

        assertEquals(owner, switchLibraryService.getSwitchOwners().first { o -> o.id == owner.id })
    }

    @Test
    fun whenAddingSwitchShouldReturnIt() {
        val switch = switch(draft = false)
        val id = switchDao.insert(switch).id

        val fetched = switchService.get(OFFICIAL, id)!!
        assertMatches(switch, fetched, contextMatch = false)
        assertEquals(id, fetched.id)
    }

    @Test
    fun someSwitchesAreReturnedEvenWithoutParameters() {
        switchDao.insert(switch(draft = false))
        assertTrue(switchService.list(OFFICIAL).isNotEmpty())
    }

    @Test
    fun switchIsReturnedByBoundingBox() {
        val switch = switch(
            joints = listOf(
                switchJoint(1, Point(428423.66891764296, 7210292.096537605)),
                switchJoint(5, Point(428412.6499928745, 7210278.867815434)),
            ),
            draft = false,
        )
        val id = switchDao.insert(switch).id

        val bbox = BoundingBox(428125.0..428906.25, 7210156.25..7210937.5)
        val switches = getSwitches(switchFilter(bbox = bbox))

        assertTrue(switches.isNotEmpty())
        assertTrue(switches.any { s -> s.id == id })
    }

    @Test
    fun switchOutsideBoundingBoxIsNotReturned() {
        val switchOutsideBoundingBox = switch(
            joints = listOf(
                switchJoint(1, Point(428423.66891764296, 7210292.096537605)),
                switchJoint(5, Point(428412.6499928745, 7210278.867815434)),
            ),
            draft = false,
        )

        val id = switchDao.insert(switchOutsideBoundingBox).id

        val bbox = BoundingBox(428125.0..431250.0, 7196875.0..7200000.0)
        val switches = getSwitches(switchFilter(bbox = bbox))

        assertFalse(switches.any { s -> s.id == id })
    }

    @Test
    fun switchIsReturnedByName() {
        val name = getUnusedSwitchName()
        val switch = switch(name = name.toString(), draft = false)

        val id = switchDao.insert(switch).id

        val switchesCompleteName = getSwitches(switchFilter(namePart = name.toString()))
        val switchesPartialName = getSwitches(switchFilter(namePart = name.substring(2)))

        assertTrue(switchesCompleteName.isNotEmpty())
        assertTrue(switchesPartialName.isNotEmpty())
        assertTrue(getSwitches().size >= switchesCompleteName.size)
        assertTrue(switchesCompleteName.any { s -> s.id == id })
        assertTrue(switchesPartialName.any { s -> s.id == id })
    }

    @Test
    fun returnsOnlySwitchesThatAreNotDeleted() {
        val inUseSwitch = switch(
            name = getUnusedSwitchName().toString(),
            stateCategory = LayoutStateCategory.EXISTING,
            draft = false,
        )
        val deletedSwitch = switch(
            name = getUnusedSwitchName().toString(),
            stateCategory = LayoutStateCategory.NOT_EXISTING,
            draft = false,
        )
        val inUseSwitchId = switchDao.insert(inUseSwitch)
        val deletedSwitchId = switchDao.insert(deletedSwitch)

        val switches = switchService.list(OFFICIAL)

        assertTrue(switches.any { it.id == inUseSwitchId.id })
        assertTrue(switches.none { it.id == deletedSwitchId.id })
    }

    @Test
    fun noSwitchesReturnedWithNonExistingName() {
        assertTrue(getSwitches(switchFilter(namePart = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenOffset() {
        repeat(2) { switchDao.insert(switch(name = getUnusedSwitchName().toString(), draft = false)) }
        val switches = getSwitches()

        assertEquals(switches.size, getSwitches(switchFilter()).size)
        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(1, pageSwitches(switches, switches.lastIndex, null, null).items.size)
        assertEquals(0, pageSwitches(switches, switches.lastIndex + 10, null, null).items.size)
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenLimit() {
        repeat(2) { switchDao.insert(switch(name = getUnusedSwitchName().toString(), draft = false)) }
        val switches = getSwitches()

        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(0, pageSwitches(switches, 0, 0, null).items.size)
        assertEquals(1, pageSwitches(switches, 0, 1, null).items.size)
        assertEquals(
            switches.size, pageSwitches(switches, 0, switches.lastIndex + 10, null).items.size
        )
    }

    @Test
    fun switchWithNoJointsIsReturnedFirst() {
        val idOfSwitchWithNoJoints = switchDao.insert(
            switch(
                name = getUnusedSwitchName().toString(),
                joints = listOf(),
                draft = false,
            )
        ).id
        val idOfRandomSwitch = switchDao.insert(
            switch(
                name = getUnusedSwitchName().toString(),
                draft = false,
            )
        ).id

        val switches = pageSwitches(getSwitches(), 0, null, Point(422222.2, 7222222.2)).items

        val indexOfRandomSwitch = switches.indexOfFirst { s -> s.id == idOfRandomSwitch }
        val indexOfSwitchWithNoJoints = switches.indexOfFirst { s -> s.id == idOfSwitchWithNoJoints }

        assertTrue(indexOfSwitchWithNoJoints >= 0)
        assertTrue(indexOfRandomSwitch >= 0)
        assertTrue(indexOfSwitchWithNoJoints < indexOfRandomSwitch)
    }

    @Test
    fun switchesAreSortedByComparisonPoint() {
        val idOfRandomSwitch = switchDao.insert(switch(name = getUnusedSwitchName().toString(), draft = false)).id

        val idOfSwitchLocatedAtComparisonPoint = switchDao.insert(
            switch(
                name = getUnusedSwitchName().toString(),
                joints = listOf(
                    TrackLayoutSwitchJoint(
                        JointNumber(1),
                        location = Point(422222.2, 7222222.2),
                        locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                    ), TrackLayoutSwitchJoint(
                        JointNumber(5),
                        location = Point(428305.33617941965, 7210146.458099049),
                        locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                    )
                ),
                draft = false,
            )
        ).id

        val switches = pageSwitches(getSwitches(), 0, null, Point(422222.2, 7222222.2)).items

        val indexOfRandomSwitch = switches.indexOfFirst { s -> s.id == idOfRandomSwitch }
        val indexOfSwitchLocatedAtComparisonPoint =
            switches.indexOfFirst { s -> s.id == idOfSwitchLocatedAtComparisonPoint }

        assertTrue(indexOfRandomSwitch >= 0)
        assertTrue(indexOfSwitchLocatedAtComparisonPoint >= 0)
        assertTrue(indexOfSwitchLocatedAtComparisonPoint < indexOfRandomSwitch)
    }

    @Test
    fun switchIsReturnedBySwitchType() {
        val switch = switch(draft = false)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val typeName = structure.type.typeName

        switchDao.insert(switch)

        val switchesCompleteTypeName = getSwitches(switchFilter(switchType = typeName))
        val switchesPartialTypeName = getSwitches(switchFilter(switchType = typeName.substring(2)))

        assertTrue(switchesCompleteTypeName.isNotEmpty())
        assertTrue(switchesPartialTypeName.isNotEmpty())
        assertTrue(switchesCompleteTypeName.any { s -> s.externalId == switch.externalId })
        assertTrue(switchesPartialTypeName.any { s -> s.externalId == switch.externalId })
    }

    @Test
    fun noSwitchesReturnedWithNonExistingTypeName() {
        assertTrue(getSwitches(switchFilter(switchType = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun returnsNullIfFetchingDraftOnlySwitchUsingOfficialFetch() {
        val draftSwitch = switchService.saveDraft(LayoutBranch.main, switch(draft = true))
        assertNull(switchService.get(OFFICIAL, draftSwitch.id))
    }

    @Test
    fun throwsIfFetchingOfficialVersionOfDraftOnlySwitchUsingGetOrThrow() {
        val draftSwitch = switchService.saveDraft(LayoutBranch.main, switch(draft = true))
        assertThrows<NoSuchEntityException> { switchService.getOrThrow(OFFICIAL, draftSwitch.id) }
    }

    @Test
    fun switchConnectedLocationTracksFound() {
        val trackNumber = getOrCreateTrackNumber(TrackNumber("123"))
        val tnId = trackNumber.id as IntId
        val switch = switchService.get(DRAFT, switchService.saveDraft(LayoutBranch.main, switch(1, draft = true)).id)!!
        val (_, withStartLink) = insertDraft(
            locationTrack(tnId, externalId = someOid(), draft = true).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(1)),
            ), alignment(someSegment())
        )
        val (_, withEndLink) = insertDraft(
            locationTrack(tnId, externalId = null, draft = true).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(2)),
            ), alignment(someSegment())
        )
        val (_, withSegmentLink) = insertDraft(
            locationTrack(tnId, externalId = someOid(), draft = true),
            alignment(
                someSegment().copy(
                    switchId = switch.id as IntId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                )
            ),
        )
        assertEquals(
            listOf<LocationTrackIdentifiers>(),
            switchDao.findLocationTracksLinkedToSwitch(OFFICIAL, switch.id as IntId),
        )
        val result = switchDao.findLocationTracksLinkedToSwitch(DRAFT, switch.id as IntId)
        assertEquals(
            listOf(withStartLink, withEndLink, withSegmentLink),
            result.sortedBy { ids -> ids.rowVersion.id.intValue },
        )
    }

    @Test
    fun `Getting location tracks of switches returns empty list if argument is an empty list`() {
        assertTrue(switchDao.findLocationTracksLinkedToSwitches(OFFICIAL, emptyList()).isEmpty())
    }

    @Test
    fun shouldReturnLocationTracksThatAreLinkedToSwitchAtMoment() {
        val trackNumber = getOrCreateTrackNumber(TrackNumber("123"))
        val trackNumberId = trackNumber.id as IntId
        val switch = switchDao.fetch(switchDao.insert(switch(1, draft = false)).rowVersion)

        val locationTrack1Oid = someOid<LocationTrack>()
        val alignment1Version = alignmentDao.insert(alignment(someSegment()))
        val locationTrack1 = locationTrackDao.insert(
            locationTrack(
                alignment = alignmentDao.fetch(alignment1Version),
                trackNumberId = trackNumberId,
                externalId = locationTrack1Oid,
                alignmentVersion = alignment1Version,
                name = "LT 1",
                draft = false,
            ).copy(
                topologyStartSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(1)),
            )
        )

        val alignment2Version = alignmentDao.insert(alignment(someSegment()))
        val locationTrack2 = locationTrackDao.insert(
            locationTrack(
                alignment = alignmentDao.fetch(alignment2Version),
                trackNumberId = trackNumberId,
                alignmentVersion = alignment2Version,
                name = "LT 2",
                draft = false,
            ).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(2)),
            )
        )

        val alignment3Version = alignmentDao.insert(
            alignment(
                someSegment().copy(
                    switchId = switch.id as IntId,
                    startJointNumber = JointNumber(1),
                    endJointNumber = JointNumber(2),
                )
            )
        )

        val locationTrack3Oid = someOid<LocationTrack>()
        val locationTrack3 = locationTrackDao.insert(
            locationTrack(
                alignment = alignmentDao.fetch(alignment3Version),
                trackNumberId = trackNumberId,
                externalId = locationTrack3Oid,
                alignmentVersion = alignment3Version,
                name = "LT 3",
                draft = false,
            ).copy(
                topologyEndSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(2)),
            )
        )

        val linkedLocationTracks = switchDao.findLocationTracksLinkedToSwitchAtMoment(
            switch.id as IntId, JointNumber(1), Instant.now()
        )
        assertEquals(2, linkedLocationTracks.size)

        assertTrue(
            linkedLocationTracks.contains(
                LocationTrackIdentifiers(
                    rowVersion = locationTrack1.rowVersion, externalId = locationTrack1Oid
                )
            )
        )

        assertTrue(
            linkedLocationTracks.contains(
                LocationTrackIdentifiers(
                    rowVersion = locationTrack3.rowVersion, externalId = locationTrack3Oid
                )
            )
        )

        assertTrue(linkedLocationTracks.none { lt ->
            lt.rowVersion == locationTrack2.rowVersion
        })
    }

    @Test
    fun getSwitchLinksTopologicalConnections() {
        val switch = switch(123, IntId(1), draft = false)
        val switchVersion = switchDao.insert(switch)
        val joint1Point = switch.getJoint(JointNumber(1))!!.location
        val (locationTrack, alignment) = locationTrackAndAlignment(
            getUnusedTrackNumberId(), segment(joint1Point - 1.0, joint1Point), draft = true
        )
        val locationTrackVersion = locationTrackService.saveDraft(
            locationTrack.copy(topologyEndSwitch = TopologyLocationTrackSwitch(switchVersion.id, JointNumber(1))),
            alignment
        )
        val connections = switchService.getSwitchJointConnections(DRAFT, switchVersion.id)

        assertEquals(
            listOf(TrackLayoutSwitchJointMatch(locationTrackVersion.id, joint1Point)),
            connections.first { connection -> connection.number == JointNumber(1) }.accurateMatches
        )
    }

    @Test
    fun switchIdIsReturnedWhenAddingNewSwitch() {
        val switch = TrackLayoutSwitchSaveRequest(
            SwitchName("XYZ-987"),
            IntId(5),
            LayoutStateCategory.EXISTING,
            ownerId = IntId(3),
            trapPoint = null,
        )
        val switchId = switchService.insertSwitch(switch)
        val fetchedSwitch = switchService.get(DRAFT, switchId)!!
        assertNull(switchService.get(OFFICIAL, switchId))

        assertEquals(DataType.STORED, fetchedSwitch.dataType)
        assertEquals(switch.name, fetchedSwitch.name)
        assertEquals(switch.stateCategory, fetchedSwitch.stateCategory)
        assertEquals(switch.switchStructureId, fetchedSwitch.switchStructureId)
    }

    private fun insertDraft(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
    ): Pair<LocationTrack, LocationTrackIdentifiers> {
        val (id, version) = locationTrackService.saveDraft(locationTrack, alignment)
        return locationTrackService.get(DRAFT, id)!! to LocationTrackIdentifiers(version, locationTrack.externalId)
    }

    private fun getSwitches(filter: (Pair<TrackLayoutSwitch, SwitchStructure>) -> Boolean): List<TrackLayoutSwitch> =
        switchService.listWithStructure(OFFICIAL).filter(filter).map { (s, _) -> s }

    private fun getSwitches() = switchService.listWithStructure(OFFICIAL)
}
