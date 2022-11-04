package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.random.Random


@ActiveProfiles("dev", "test")
@SpringBootTest
class LayoutSwitchServiceIT @Autowired constructor(
    private val switchService: LayoutSwitchService,
    private val switchLibraryService: SwitchLibraryService,
    private val switchDao: LayoutSwitchDao,
): ITTestBase() {
    @Test
    fun switchOwnerIsReturned() {
        val dummyOwner = SwitchOwner(id = IntId(4), name = MetaDataName("Cinia"))

        switchDao.insert(
            generateDummySwitch().copy(
                ownerId = dummyOwner.id
            )
        )

        assertEquals(dummyOwner, switchLibraryService.getSwitchOwners().first { o -> o.id == dummyOwner.id })
    }

    @Test
    fun whenAddingSwitchShouldReturnIt() {
        val dummySwitch = generateDummySwitch()
        val id = switchDao.insert(dummySwitch).id

        assertEquals(dummySwitch.externalId, switchService.getOfficial(id)!!.externalId)
    }

    @Test
    fun someSwitchesAreReturnedEvenWithoutParameters() {
        switchDao.insert(generateDummySwitch())
        assertTrue(switchService.listOfficial().isNotEmpty())
    }

    @Test
    fun switchIsReturnedByBoundingBox() {
        val dummySwitch = generateDummySwitch()
        switchDao.insert(dummySwitch)

        val bbox = BoundingBox(428125.0..428906.25, 7210156.25..7210937.5)
        val switches = getSwitches(switchService.switchFilter(bbox = bbox))

        assertTrue(switches.isNotEmpty())
        assertTrue(switches.any { s -> s.externalId == dummySwitch.externalId })
    }

    @Test
    fun switchOutsideBoundingBoxIsNotReturned() {
        val oid = Oid<TrackLayoutSwitch>(generateDummyExternalId())

        val switchOutsideBoundingBox = generateDummySwitch()

        switchDao.insert(switchOutsideBoundingBox)

        val bbox = BoundingBox(428125.0..431250.0, 7196875.0..7200000.0)
        val switches = getSwitches(switchService.switchFilter(bbox = bbox))

        assertFalse(switches.any { s -> s.externalId == oid })
    }

    @Test
    fun switchIsReturnedByName() {
        val dummySwitch = generateDummySwitch()
        switchDao.insert(dummySwitch)

        val switchesCompleteName = getSwitches(switchService.switchFilter(name = dummySwitch.name.value))
        val switchesPartialName = getSwitches(switchService.switchFilter(name = dummySwitch.name.substring(2)))

        assertTrue(switchesCompleteName.isNotEmpty())
        assertTrue(switchesPartialName.isNotEmpty())
        assertTrue(getSwitches().size >= switchesCompleteName.size)
        assertTrue(switchesCompleteName.any { s -> s.externalId == dummySwitch.externalId })
        assertTrue(switchesPartialName.any { s -> s.externalId == dummySwitch.externalId })
    }

    @Test
    fun returnsOnlySwitchesThatAreNotDeleted() {
        val inUseSwitch = generateDummySwitch().copy(stateCategory = LayoutStateCategory.EXISTING)
        val deletedSwitch = generateDummySwitch().copy(stateCategory = LayoutStateCategory.NOT_EXISTING)
        val inUseSwitchId = switchDao.insert(inUseSwitch)
        val deletedSwitchId = switchDao.insert(deletedSwitch)

        val switches = switchService.list(OFFICIAL)

        assertTrue(switches.any { it.id == inUseSwitchId.id })
        assertTrue(switches.none { it.id == deletedSwitchId.id })
    }

    @Test
    fun noSwitchesReturnedWithNonExistingName() {
        assertTrue(getSwitches(switchService.switchFilter(name = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenOffset() {
        repeat(2) {
            switchDao.insert(generateDummySwitch())
        }
        val switches = getSwitches()

        assertEquals(switches.size, getSwitches(switchService.switchFilter()).size)
        assertEquals(switches.size, switchService.pageSwitches(switches, 0, null, null).size)
        assertEquals(switches.size, switchService.pageSwitches(switches, 0, null, null).size)
        assertEquals(1, switchService.pageSwitches(switches, switches.lastIndex, null, null).size)
        assertEquals(0, switchService.pageSwitches(switches, switches.lastIndex + 10, null, null).size)
    }

    @Test
    fun numberOfSwitchesIsReturnedCorrectlyByGivenLimit() {
        repeat(2) {
            switchDao.insert(generateDummySwitch())
        }
        val switches = getSwitches()

        assertEquals(switches.size, switchService.pageSwitches(switches, 0, null, null).size)
        assertEquals(0, switchService.pageSwitches(switches, 0, 0, null).size)
        assertEquals(1, switchService.pageSwitches(switches, 0, 1, null).size)
        assertEquals(
            switches.size,
            switchService.pageSwitches(switches, 0, switches.lastIndex + 10, null).size
        )
    }

    @Test
    fun switchWithNoJointsIsReturnedFirst() {
        val idOfSwitchWithNoJoints = switchDao.insert(generateDummySwitch().copy(joints = listOf())).id
        val idOfRandomSwitch = switchDao.insert(generateDummySwitch()).id

        val switches = switchService.pageSwitches(getSwitches(), 0, null, Point(422222.2, 7222222.2))

        val indexOfRandomSwitch = switches.indexOfFirst { s -> s.id == idOfRandomSwitch }
        val indexOfSwitchWithNoJoints = switches.indexOfFirst { s -> s.id == idOfSwitchWithNoJoints }

        assertTrue(indexOfSwitchWithNoJoints >= 0)
        assertTrue(indexOfRandomSwitch >= 0)
        assertTrue(indexOfSwitchWithNoJoints < indexOfRandomSwitch)
    }

    @Test
    fun switchesAreSortedByComparisonPoint() {
        val idOfRandomSwitch = switchDao.insert(generateDummySwitch()).id

        val idOfSwitchLocatedAtComparisonPoint = switchDao.insert(
            generateDummySwitch().copy(
                joints = listOf(
                    TrackLayoutSwitchJoint(
                        JointNumber(1),
                        location = Point(422222.2, 7222222.2),
                        locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                    ),
                    TrackLayoutSwitchJoint(
                        JointNumber(5),
                        location = Point(428305.33617941965, 7210146.458099049),
                        locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                    )
                ),
            )
        ).id

        val switches = switchService.pageSwitches(getSwitches(), 0, null, Point(422222.2, 7222222.2))

        val indexOfRandomSwitch = switches.indexOfFirst { s -> s.id == idOfRandomSwitch }
        val indexOfSwitchLocatedAtComparisonPoint =
            switches.indexOfFirst { s -> s.id == idOfSwitchLocatedAtComparisonPoint }

        assertTrue(indexOfRandomSwitch >= 0)
        assertTrue(indexOfSwitchLocatedAtComparisonPoint >= 0)
        assertTrue(indexOfSwitchLocatedAtComparisonPoint < indexOfRandomSwitch)
    }

    @Test
    fun switchIsReturnedBySwitchType() {
        val dummySwitch = generateDummySwitch()
        val dummySwitchStructure = switchLibraryService.getSwitchStructure(dummySwitch.switchStructureId)
        val typeName = dummySwitchStructure.type.typeName

        switchDao.insert(dummySwitch)

        val switchesCompleteTypeName = getSwitches(switchService.switchFilter(switchType = typeName))
        val switchesPartialTypeName = getSwitches(switchService.switchFilter(switchType = typeName.substring(2)))

        assertTrue(switchesCompleteTypeName.isNotEmpty())
        assertTrue(switchesPartialTypeName.isNotEmpty())
        assertTrue(switchesCompleteTypeName.any { s -> s.externalId == dummySwitch.externalId })
        assertTrue(switchesPartialTypeName.any { s -> s.externalId == dummySwitch.externalId })
    }


    @Test
    fun noSwitchesReturnedWithNonExistingTypeName() {
        assertTrue(getSwitches(switchService.switchFilter(switchType = "abcdefghijklmnopqrstu")).isEmpty())
    }

    @Test
    fun returnsNullIfFetchingDraftOnlySwitchUsingOfficialFetch() {
        val draftSwitch = switchService.saveDraft(generateDummySwitch())

        assertNull(switchService.get(OFFICIAL, draftSwitch.id))
    }

    @Test
    fun throwsIfFetchingOfficialVersionOfDraftOnlySwitchUsingGetOrThrow() {
        val draftSwitch = switchService.saveDraft(generateDummySwitch())

        assertThrows<NoSuchEntityException> { switchService.getOrThrow(OFFICIAL, draftSwitch.id) }
    }

    private fun generateDummyExternalId(): String {
        val first = Random.nextInt(10, 999999999)
        val second = Random.nextInt(10, 999999999)
        val third = Random.nextInt(10, 999999999)

        return "$first.$second.$third"
    }

    private fun generateDummySwitch(): TrackLayoutSwitch {
        return TrackLayoutSwitch(
            name = SwitchName("ABC123"),
            switchStructureId = switchStructureYV60_300_1_9().id as IntId,
            stateCategory = LayoutStateCategory.EXISTING,
            joints = listOf(
                TrackLayoutSwitchJoint(
                    number = JointNumber(1),
                    location = Point(428423.66891764296, 7210292.096537605),
                    locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                ),
                TrackLayoutSwitchJoint(
                    number = JointNumber(5),
                    location = Point(428412.6499928745, 7210278.867815434),
                    locationAccuracy = LocationAccuracy.GEOMETRY_CALCULATED
                )
            ),
            externalId = Oid(generateDummyExternalId()),
            sourceId = null,
            trapPoint = true,
            ownerId = switchOwnerVayla().id,
            source = GeometrySource.GENERATED,
        )
    }

    private fun getSwitches(filter: (switch: TrackLayoutSwitch) -> Boolean) = switchService.list(OFFICIAL, filter)
    private fun getSwitches() = switchService.list(OFFICIAL)
}
