package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.DataType
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.linking.switches.LayoutSwitchSaveRequest
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory.EXISTING
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao.LocationTrackIdentifiers
import java.time.Instant
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

@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchServiceIT
@Autowired
constructor(
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackService: LocationTrackService,
    private val switchDao: LayoutSwitchDao,
) : DBTestBase() {
    @BeforeEach
    fun cleanup() {
        testDBService.clearLayoutTables()
    }

    @Test
    fun switchOwnerIsReturned() {
        val owner = SwitchOwner(id = IntId(4), name = MetaDataName("Cinia"))

        switchDao.save(switch(ownerId = owner.id, draft = false))

        assertEquals(owner, switchLibraryService.getSwitchOwners().first { o -> o.id == owner.id })
    }

    @Test
    fun whenAddingSwitchShouldReturnIt() {
        val switch = switch(draft = false, stateCategory = EXISTING)
        val id = switchDao.save(switch).id

        val fetched = switchService.get(MainLayoutContext.official, id)!!
        assertMatches(switch, fetched, contextMatch = false)
        assertEquals(id, fetched.id)
    }

    @Test
    fun someSwitchesAreReturnedEvenWithoutParameters() {
        switchDao.save(switch(draft = false, stateCategory = EXISTING))
        assertTrue(switchService.list(MainLayoutContext.official).isNotEmpty())
    }

    @Test
    fun switchIsReturnedByBoundingBox() {
        val switch =
            switch(
                joints =
                    listOf(
                        switchJoint(1, Point(428423.66891764296, 7210292.096537605)),
                        switchJoint(5, Point(428412.6499928745, 7210278.867815434)),
                    ),
                draft = false,
                stateCategory = EXISTING,
            )
        val id = switchDao.save(switch).id

        val bbox = BoundingBox(428125.0..428906.25, 7210156.25..7210937.5)
        val switches = getSwitches(switchFilter(bbox = bbox))

        assertTrue(switches.isNotEmpty())
        assertTrue(switches.any { s -> s.id == id })
    }

    @Test
    fun switchOutsideBoundingBoxIsNotReturned() {
        val switchOutsideBoundingBox =
            switch(
                joints =
                    listOf(
                        switchJoint(1, Point(428423.66891764296, 7210292.096537605)),
                        switchJoint(5, Point(428412.6499928745, 7210278.867815434)),
                    ),
                draft = false,
            )

        val id = switchDao.save(switchOutsideBoundingBox).id

        val bbox = BoundingBox(428125.0..431250.0, 7196875.0..7200000.0)
        val switches = getSwitches(switchFilter(bbox = bbox))

        assertFalse(switches.any { s -> s.id == id })
    }

    @Test
    fun switchIsReturnedByName() {
        val name = testDBService.getUnusedSwitchName()
        val switch = switch(name = name.toString(), draft = false, stateCategory = EXISTING)

        val id = switchDao.save(switch).id

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
        val inUseSwitch =
            switch(
                name = testDBService.getUnusedSwitchName().toString(),
                stateCategory = LayoutStateCategory.EXISTING,
                draft = false,
            )
        val deletedSwitch =
            switch(
                name = testDBService.getUnusedSwitchName().toString(),
                stateCategory = LayoutStateCategory.NOT_EXISTING,
                draft = false,
            )
        val inUseSwitchId = switchDao.save(inUseSwitch)
        val deletedSwitchId = switchDao.save(deletedSwitch)

        val switches = switchService.list(MainLayoutContext.official)

        assertTrue(switches.any { it.id == inUseSwitchId.id })
        assertTrue(switches.none { it.id == deletedSwitchId.id })
    }

    @Test
    fun noSwitchesReturnedWithNonExistingName() {
        assertTrue(getSwitches(switchFilter(namePart = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenOffset() {
        repeat(2) { mainOfficialContext.createSwitch() }
        val switches = getSwitches()

        assertEquals(switches.size, getSwitches(switchFilter()).size)
        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(1, pageSwitches(switches, switches.lastIndex, null, null).items.size)
        assertEquals(0, pageSwitches(switches, switches.lastIndex + 10, null, null).items.size)
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenLimit() {
        repeat(2) { mainOfficialContext.createSwitch() }
        val switches = getSwitches()

        assertEquals(switches.size, pageSwitches(switches, 0, null, null).items.size)
        assertEquals(0, pageSwitches(switches, 0, 0, null).items.size)
        assertEquals(1, pageSwitches(switches, 0, 1, null).items.size)
        assertEquals(switches.size, pageSwitches(switches, 0, switches.lastIndex + 10, null).items.size)
    }

    @Test
    fun switchWithNoJointsIsReturnedFirst() {
        val idOfSwitchWithNoJoints =
            mainOfficialContext
                .insert(
                    switch(
                        name = testDBService.getUnusedSwitchName().toString(),
                        stateCategory = EXISTING,
                        joints = listOf(),
                    )
                )
                .id
        val idOfRandomSwitch = mainOfficialContext.createSwitch().id

        val switches = pageSwitches(getSwitches(), 0, null, Point(422222.2, 7222222.2)).items

        val indexOfRandomSwitch = switches.indexOfFirst { s -> s.id == idOfRandomSwitch }
        val indexOfSwitchWithNoJoints = switches.indexOfFirst { s -> s.id == idOfSwitchWithNoJoints }

        assertTrue(indexOfSwitchWithNoJoints >= 0)
        assertTrue(indexOfRandomSwitch >= 0)
        assertTrue(indexOfSwitchWithNoJoints < indexOfRandomSwitch)
    }

    @Test
    fun switchesAreSortedByComparisonPoint() {
        val idOfRandomSwitch =
            mainOfficialContext
                .createSwitch(
                    joints =
                        listOf(
                            LayoutSwitchJoint(
                                number = JointNumber(1),
                                role = SwitchJointRole.MAIN,
                                location = Point(428305.33617941965, 7210146.458099049),
                                locationAccuracy = null,
                            ),
                            LayoutSwitchJoint(
                                number = JointNumber(5),
                                role = SwitchJointRole.MATH,
                                location = Point(422222.2, 7222222.2),
                                locationAccuracy = null,
                            ),
                        )
                )
                .id

        val idOfSwitchLocatedAtComparisonPoint =
            mainOfficialContext
                .insert(
                    switch(
                        name = testDBService.getUnusedSwitchName().toString(),
                        stateCategory = EXISTING,
                        joints =
                            listOf(
                                LayoutSwitchJoint(
                                    number = JointNumber(1),
                                    role = SwitchJointRole.MAIN,
                                    location = Point(422222.2, 7222222.2),
                                    locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED,
                                ),
                                LayoutSwitchJoint(
                                    number = JointNumber(5),
                                    role = SwitchJointRole.MATH,
                                    location = Point(428305.33617941965, 7210146.458099049),
                                    locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED,
                                ),
                            ),
                    )
                )
                .id

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
        val switch = switchDao.fetch(switchDao.save(switch(draft = false, stateCategory = EXISTING)))
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val typeName = structure.type.typeName

        val switchesCompleteTypeName = getSwitches(switchFilter(switchType = typeName))
        val switchesPartialTypeName = getSwitches(switchFilter(switchType = typeName.substring(2)))

        assertTrue(switchesCompleteTypeName.isNotEmpty())
        assertTrue(switchesPartialTypeName.isNotEmpty())
        assertTrue(switchesCompleteTypeName.any { s -> s.id == switch.id })
        assertTrue(switchesPartialTypeName.any { s -> s.id == switch.id })
    }

    @Test
    fun noSwitchesReturnedWithNonExistingTypeName() {
        assertTrue(getSwitches(switchFilter(switchType = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun returnsNullIfFetchingDraftOnlySwitchUsingOfficialFetch() {
        val draftSwitch = switchService.saveDraft(LayoutBranch.main, switch(draft = true))
        assertNull(switchService.get(MainLayoutContext.official, draftSwitch.id))
    }

    @Test
    fun throwsIfFetchingOfficialVersionOfDraftOnlySwitchUsingGetOrThrow() {
        val draftSwitch = switchService.saveDraft(LayoutBranch.main, switch(draft = true))
        assertThrows<NoSuchEntityException> { switchService.getOrThrow(MainLayoutContext.official, draftSwitch.id) }
    }

    @Test
    fun switchConnectedLocationTracksFound() {
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id,
            )
        val (_, withStartLink) =
            insertDraft(
                locationTrack(tnId, draft = true)
                    .copy(topologyStartSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(1))),
                alignment(someSegment()),
                someOid(),
            )
        val (_, withEndLink) =
            insertDraft(
                locationTrack(tnId, draft = true)
                    .copy(topologyEndSwitch = TopologyLocationTrackSwitch(switch.id as IntId, JointNumber(2))),
                alignment(someSegment()),
            )
        val (_, withSegmentLink) =
            insertDraft(
                locationTrack(tnId, draft = true),
                alignment(
                    someSegment()
                        .copy(
                            switchId = switch.id as IntId,
                            startJointNumber = JointNumber(1),
                            endJointNumber = JointNumber(2),
                        )
                ),
                someOid(),
            )
        assertEquals(
            listOf<LocationTrackIdentifiers>(),
            switchDao.findLocationTracksLinkedToSwitch(MainLayoutContext.official, switch.id as IntId),
        )
        val result = switchDao.findLocationTracksLinkedToSwitch(MainLayoutContext.draft, switch.id as IntId)
        assertEquals(listOf(withStartLink, withEndLink, withSegmentLink), result.sortedBy { ids -> ids.id.intValue })
    }

    @Test
    fun `Getting location tracks of switches returns empty list if argument is an empty list`() {
        assertTrue(switchDao.findLocationTracksLinkedToSwitches(MainLayoutContext.official, emptyList()).isEmpty())
    }

    @Test
    fun shouldReturnLocationTracksThatAreLinkedToSwitchAtMoment() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId = mainOfficialContext.insert(switch()).id

        val locationTrack1Oid = someOid<LocationTrack>()
        val locationTrack1 =
            mainOfficialContext.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    name = "LT 1",
                    topologyStartSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(1)),
                ),
                alignment(someSegment()),
            )
        locationTrackService.insertExternalId(LayoutBranch.main, locationTrack1.id, locationTrack1Oid)

        val locationTrack2 =
            mainOfficialContext.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    name = "LT 2",
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(2)),
                ),
                alignment(someSegment()),
            )

        val locationTrack3Oid = someOid<LocationTrack>()
        val locationTrack3 =
            mainOfficialContext.insert(
                locationTrack(
                    trackNumberId = trackNumberId,
                    name = "LT 3",
                    topologyEndSwitch = TopologyLocationTrackSwitch(switchId, JointNumber(2)),
                ),
                alignment(
                    someSegment()
                        .copy(switchId = switchId, startJointNumber = JointNumber(1), endJointNumber = JointNumber(2))
                ),
            )
        locationTrackService.insertExternalId(LayoutBranch.main, locationTrack3.id, locationTrack3Oid)

        val linkedLocationTracks =
            switchDao.findLocationTracksLinkedToSwitchAtMoment(
                LayoutBranch.main,
                switchId,
                JointNumber(1),
                Instant.now(),
            )
        assertEquals(2, linkedLocationTracks.size)

        assertTrue(
            linkedLocationTracks.contains(
                LocationTrackIdentifiers(rowVersion = locationTrack1, externalId = locationTrack1Oid)
            )
        )

        assertTrue(
            linkedLocationTracks.contains(
                LocationTrackIdentifiers(rowVersion = locationTrack3, externalId = locationTrack3Oid)
            )
        )

        assertTrue(linkedLocationTracks.none { lt -> lt.rowVersion == locationTrack2 })
    }

    @Test
    fun getSwitchLinksTopologicalConnections() {
        val switch = switch(IntId(1), joints = listOf(switchJoint(1, Point(1.0, 1.0))), draft = false)
        val switchVersion = switchDao.save(switch)
        val joint1Point = switch.getJoint(JointNumber(1))!!.location
        val (locationTrack, alignment) =
            locationTrackAndAlignment(
                mainDraftContext.createLayoutTrackNumber().id,
                segment(joint1Point - 1.0, joint1Point),
                draft = true,
            )
        val locationTrackVersion =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack.copy(topologyEndSwitch = TopologyLocationTrackSwitch(switchVersion.id, JointNumber(1))),
                alignment,
            )
        val connections = switchService.getSwitchJointConnections(MainLayoutContext.draft, switchVersion.id)

        assertEquals(
            listOf(LayoutSwitchJointMatch(locationTrackVersion.id, joint1Point)),
            connections.first { connection -> connection.number == JointNumber(1) }.accurateMatches,
        )
    }

    @Test
    fun switchIdIsReturnedWhenAddingNewSwitch() {
        val switch =
            LayoutSwitchSaveRequest(
                SwitchName("XYZ-987"),
                IntId(5),
                EXISTING,
                ownerId = IntId(3),
                trapPoint = null,
                draftOid = null,
            )
        val switchId = switchService.insertSwitch(LayoutBranch.main, switch)
        val fetchedSwitch = switchService.get(MainLayoutContext.draft, switchId)!!
        assertNull(switchService.get(MainLayoutContext.official, switchId))

        assertEquals(DataType.STORED, fetchedSwitch.dataType)
        assertEquals(switch.name, fetchedSwitch.name)
        assertEquals(switch.stateCategory, fetchedSwitch.stateCategory)
        assertEquals(switch.switchStructureId, fetchedSwitch.switchStructureId)
    }

    private fun insertDraft(
        locationTrack: LocationTrack,
        alignment: LayoutAlignment,
        oid: Oid<LocationTrack>? = null,
    ): Pair<LocationTrack, LocationTrackIdentifiers> {
        val version = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, alignment)
        if (oid != null) {
            locationTrackService.insertExternalId(LayoutBranch.main, version.id, oid)
        }
        val track = locationTrackService.getOrThrow(MainLayoutContext.draft, version.id)
        val identifiers = LocationTrackIdentifiers(version, oid)
        return track to identifiers
    }

    private fun getSwitches(filter: (Pair<LayoutSwitch, SwitchStructure>) -> Boolean): List<LayoutSwitch> =
        switchService.listWithStructure(MainLayoutContext.official).filter(filter).map { (s, _) -> s }

    private fun getSwitches() = switchService.listWithStructure(MainLayoutContext.official)
}
