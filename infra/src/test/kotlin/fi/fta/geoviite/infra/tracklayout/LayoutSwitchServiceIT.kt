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
import fi.fta.geoviite.infra.math.Polygon
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchOwner
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao.LocationTrackIdentifiers
import fi.fta.geoviite.infra.util.FreeText
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
        val switch = switch(draft = false, stateCategory = LayoutStateCategory.EXISTING)
        val id = switchDao.save(switch).id

        val fetched = switchService.get(MainLayoutContext.official, id)!!
        assertMatches(switch, fetched, contextMatch = false)
        assertEquals(id, fetched.id)
    }

    @Test
    fun someSwitchesAreReturnedEvenWithoutParameters() {
        switchDao.save(switch(draft = false, stateCategory = LayoutStateCategory.EXISTING))
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
                stateCategory = LayoutStateCategory.EXISTING,
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
        val switch = switch(name = name.toString(), draft = false, stateCategory = LayoutStateCategory.EXISTING)

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
                .save(
                    switch(
                        name = testDBService.getUnusedSwitchName().toString(),
                        stateCategory = LayoutStateCategory.EXISTING,
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
                .save(
                    switch(
                        name = testDBService.getUnusedSwitchName().toString(),
                        stateCategory = LayoutStateCategory.EXISTING,
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
        val switch =
            switchDao.fetch(switchDao.save(switch(draft = false, stateCategory = LayoutStateCategory.EXISTING)))
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
                locationTrack(tnId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switch.id as IntId, 1)),
                        endNode = PlaceHolderNodeConnection,
                        segments = listOf(someSegment()),
                    )
                ),
                someOid(),
            )
        val (_, withEndLink) =
            insertDraft(
                locationTrack(tnId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = PlaceHolderNodeConnection,
                        endNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switch.id as IntId, 2)),
                        segments = listOf(someSegment()),
                    )
                ),
            )
        val (_, withSegmentLink) =
            insertDraft(
                locationTrack(tnId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = switchLinkYV(switch.id as IntId, 1), outer = null),
                        endNode = NodeConnection.switch(inner = switchLinkYV(switch.id as IntId, 2), outer = null),
                        segments = listOf(someSegment()),
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
    fun `findLocationTracksLinkedToSwitchAtMoment works`() {
        val trackNumberId = mainOfficialContext.createLayoutTrackNumber().id
        val switchId = mainOfficialContext.save(switch()).id

        val locationTrack1Oid = someOid<LocationTrack>()
        val locationTrack1 =
            mainOfficialContext.save(
                locationTrack(trackNumberId = trackNumberId, name = "LT 1"),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switchId, 1)),
                        endNode = PlaceHolderNodeConnection,
                        segments = listOf(someSegment()),
                    )
                ),
            )
        locationTrackService.insertExternalId(LayoutBranch.main, locationTrack1.id, locationTrack1Oid)

        val locationTrack2 =
            mainOfficialContext.save(
                locationTrack(trackNumberId = trackNumberId, name = "LT 2"),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = PlaceHolderNodeConnection,
                        endNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switchId, 2)),
                        segments = listOf(someSegment()),
                    )
                ),
            )

        val locationTrack3Oid = someOid<LocationTrack>()
        val locationTrack3 =
            mainOfficialContext.save(
                locationTrack(trackNumberId = trackNumberId, name = "LT 3"),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = switchLinkYV(switchId, 1), outer = null),
                        endNode = NodeConnection.switch(inner = switchLinkYV(switchId, 2), outer = null),
                        segments = listOf(someSegment()),
                    )
                ),
            )
        locationTrackService.insertExternalId(LayoutBranch.main, locationTrack3.id, locationTrack3Oid)

        // add confuser draft; should have no effect
        mainDraftContext.save(mainOfficialContext.fetch(locationTrack1.id)!!)
        val linkedLocationTracks =
            switchDao.findLocationTracksLinkedToSwitchAtMoment(LayoutBranch.main, switchId, testDBService.getDbTime())
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
        val trackVersion =
            mainDraftContext.save(
                locationTrack(mainDraftContext.createLayoutTrackNumber().id),
                trackGeometry(
                    edge(
                        endOuterSwitch = switchLinkYV(switchVersion.id, 1),
                        segments = listOf(segment(joint1Point - 1.0, joint1Point)),
                    )
                ),
            )
        val connections = switchService.getSwitchJointConnections(MainLayoutContext.draft, switchVersion.id)

        assertEquals(
            listOf(LayoutSwitchJointMatch(trackVersion.id, joint1Point)),
            connections.first { connection -> connection.number == JointNumber(1) }.accurateMatches,
        )
    }

    @Test
    fun switchIdIsReturnedWhenAddingNewSwitch() {
        val switch =
            LayoutSwitchSaveRequest(
                SwitchName("XYZ-987"),
                IntId(5),
                LayoutStateCategory.EXISTING,
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

    @Test
    fun `deleteDraft clears references if the switch is draft-only, but not if official exists`() {
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val draftOnlySwitch = mainDraftContext.save(switch()).id
        val officialSwitch = mainOfficialContext.save(switch())
        mainDraftContext.copyFrom(officialSwitch)

        val locationTrack =
            mainDraftContext.save(
                locationTrack(trackNumber),
                trackGeometry(
                    edge(
                        startInnerSwitch = switchLinkYV(draftOnlySwitch, 1),
                        endInnerSwitch = switchLinkYV(draftOnlySwitch, 2),
                        endOuterSwitch = switchLinkYV(officialSwitch.id, 1),
                        segments = listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                    ),
                    edge(
                        startOuterSwitch = switchLinkYV(draftOnlySwitch, 2),
                        startInnerSwitch = switchLinkYV(officialSwitch.id, 1),
                        endInnerSwitch = switchLinkYV(officialSwitch.id, 2),
                        segments = listOf(segment(Point(1.0, 0.0), Point(2.0, 0.0))),
                    ),
                ),
            )

        assertEquals(
            listOf(
                switchLinkYV(draftOnlySwitch, 1),
                switchLinkYV(draftOnlySwitch, 2),
                switchLinkYV(officialSwitch.id, 1),
                switchLinkYV(officialSwitch.id, 2),
            ),
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, locationTrack.id)
                .second
                .trackSwitchLinks
                .map { l -> l.link },
        )

        switchService.deleteDraft(LayoutBranch.main, draftOnlySwitch)
        switchService.deleteDraft(LayoutBranch.main, officialSwitch.id)

        assertEquals(
            listOf(switchLinkYV(officialSwitch.id, 1), switchLinkYV(officialSwitch.id, 2)),
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, locationTrack.id)
                .second
                .trackSwitchLinks
                .map { l -> l.link },
        )
    }

    @Test
    fun `idMatches finds switches even if ids or oids need trimming`() {
        val switch1 = mainOfficialContext.save(switch()).let { mainOfficialContext.fetch(it.id) }!!
        val switch2 = mainOfficialContext.save(switch()).let { mainOfficialContext.fetch(it.id) }!!
        val switch2oid = externalIdForSwitch()
        switchService.insertExternalIdForSwitch(LayoutBranch.main, switch2.id as IntId, switch2oid)

        val intIdTerm = FreeText(" ${switch1.id} ")
        val intIdMatchFunction = switchService.idMatches(MainLayoutContext.official, intIdTerm, null)

        val oidTerm = FreeText(" $switch2oid ")
        val oidMatchFunction = switchService.idMatches(MainLayoutContext.official, oidTerm, null)

        assertTrue(intIdMatchFunction(intIdTerm.toString(), switch1))
        assertTrue(oidMatchFunction(oidTerm.toString(), switch2))
    }

    @Test
    fun `findSwitchesRelatedToOperationalPoint happy cases`() {
        val poly = Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
        // a goes from ( 0,  0) to (10, 10)
        // b goes from ( 5,  5) to (15, 15)
        // c goes from (11, 11) to (21, 21) (so it doesn't intersect with a at all)
        val opA = mainOfficialContext.save(operationalPoint("a", polygon = poly)).id
        val opB = mainOfficialContext.save(operationalPoint("b", polygon = poly.moveBy(Point(5.0, 5.0)))).id
        val opC = mainOfficialContext.save(operationalPoint("c", polygon = poly.moveBy(Point(11.0, 11.0)))).id

        fun switch(operationalPointId: IntId<OperationalPoint>? = null, vararg locations: Point) =
            mainOfficialContext
                .save(
                    switch(
                        joints =
                            locations.mapIndexed { index, location ->
                                LayoutSwitchJoint(JointNumber(index), SwitchJointRole.MAIN, location, null)
                            },
                        operationalPointId = operationalPointId,
                    )
                )
                .id

        val switchOnlyInA = switch(null, Point(2.0, 2.0))
        val switchOnlyInB = switch(null, Point(12.0, 7.0))
        val switchOnlyInC = switch(null, Point(17.0, 17.0))
        val switchInABOverlap = switch(null, Point(7.0, 7.0))
        val switchInAAndB = switch(null, Point(2.0, 2.0), Point(12.0, 7.0))
        val switchInAAndC = switch(null, Point(2.0, 2.0), Point(17.0, 17.0))

        val switchAOnlyInA = switch(opA, Point(2.0, 2.0))
        val switchAOnlyInB = switch(opA, Point(12.0, 7.0))
        val switchAOnlyInC = switch(opA, Point(17.0, 17.0))
        val switchAInABOverlap = switch(opA, Point(7.0, 7.0))
        val switchAInAAndB = switch(opA, Point(2.0, 2.0), Point(12.0, 7.0))
        val switchANoJoints = switch(opA)
        val switchAElsewhere = switch(opA, Point(30.0, 30.0))

        fun s(switchId: IntId<LayoutSwitch>, vararg operationalPointIds: IntId<OperationalPoint>) =
            SwitchWithOperationalPointPolygonInclusions(switchId, operationalPointIds.toList())

        fun sortResults(r: List<SwitchWithOperationalPointPolygonInclusions>) =
            r.map { sp -> sp.copy(withinPolygon = sp.withinPolygon.sortedBy { it.intValue }) }
                .sortedBy { it.switchId.intValue }

        assertEquals(
            sortResults(
                listOf(
                    s(switchOnlyInA, opA),
                    s(switchInABOverlap, opA, opB),
                    s(switchInAAndB, opA, opB),
                    s(switchAOnlyInA, opA),
                    s(switchAOnlyInB, opB),
                    s(switchAOnlyInC, opC),
                    s(switchAInABOverlap, opA, opB),
                    s(switchAInAAndB, opA, opB),
                    s(switchANoJoints),
                    s(switchAElsewhere),
                    s(switchInAAndC, opA, opC),
                )
            ),
            sortResults(switchService.findSwitchesRelatedToOperationalPoint(mainOfficialContext.context, opA)),
        )

        assertEquals(
            sortResults(
                listOf(
                    s(switchOnlyInB, opB),
                    s(switchInABOverlap, opA, opB),
                    s(switchInAAndB, opA, opB),
                    s(switchAOnlyInB, opB),
                    s(switchAInABOverlap, opA, opB),
                    s(switchAInAAndB, opA, opB),
                )
            ),
            sortResults(switchService.findSwitchesRelatedToOperationalPoint(mainOfficialContext.context, opB)),
        )

        assertEquals(
            sortResults(listOf(s(switchOnlyInC, opC), s(switchAOnlyInC, opC), s(switchInAAndC, opA, opC))),
            sortResults(switchService.findSwitchesRelatedToOperationalPoint(mainOfficialContext.context, opC)),
        )
    }

    @Test
    fun `findSwitchesRelatedToOperationalPoint ignores NOT_EXISTING switches`() {
        val op =
            operationalPoint(
                polygon =
                    Polygon(Point(0.0, 0.0), Point(10.0, 0.0), Point(10.0, 10.0), Point(0.0, 10.0), Point(0.0, 0.0))
            )
        val operationalPoint = mainDraftContext.save(op).id
        val inAreaSwitchObject =
            switch(joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 10.0), null)))
        val outOfAreaLinkedSwitchObject =
            switch(
                joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(20.0, 20.0), null)),
                operationalPointId = operationalPoint,
            )

        val inAreaExisting = mainDraftContext.save(inAreaSwitchObject).id
        val outOfAreaLinkedExisting = mainDraftContext.save(outOfAreaLinkedSwitchObject).id
        mainDraftContext.save(inAreaSwitchObject.copy(stateCategory = LayoutStateCategory.NOT_EXISTING))
        mainDraftContext.save(outOfAreaLinkedSwitchObject.copy(stateCategory = LayoutStateCategory.NOT_EXISTING))

        assertEquals(
            listOf(
                SwitchWithOperationalPointPolygonInclusions(inAreaExisting, listOf(operationalPoint)),
                SwitchWithOperationalPointPolygonInclusions(outOfAreaLinkedExisting, listOf()),
            ),
            switchService.findSwitchesRelatedToOperationalPoint(mainDraftContext.context, operationalPoint),
        )
    }

    @Test
    fun `deleteSwitchLinks removes switch references from location tracks when set to true`() {
        // Create track number and switch
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id,
            )
        val switchId = switch.id as IntId

        // Create location track with switch link
        val (track, _) =
            insertDraft(
                locationTrack(tnId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switchId, 1)),
                        endNode = PlaceHolderNodeConnection,
                        segments = listOf(someSegment()),
                    )
                ),
            )

        // Verify that the location track has switch reference before deletion
        val (_, geometryBeforeDelete) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, track.id as IntId)
        val startNodeBefore = geometryBeforeDelete.edges.first().startNode
        assertTrue(startNodeBefore.node.containsSwitch(switchId), "Track should have switch link before deletion")

        // Create switch save request with NOT_EXISTING state
        val switchSaveRequest =
            LayoutSwitchSaveRequest(
                name = switch.name,
                switchStructureId = switch.switchStructureId,
                stateCategory = LayoutStateCategory.NOT_EXISTING,
                ownerId = switch.ownerId,
                trapPoint = switch.trapPoint,
                draftOid = switch.draftOid,
            )

        // Update switch with deleteSwitchLinks = true
        switchService.updateSwitch(LayoutBranch.main, switchId, switchSaveRequest, deleteSwitchLinks = true)

        // Verify that the location track no longer has switch reference
        val (_, geometryAfterDelete) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, track.id as IntId)
        val startNodeAfter = geometryAfterDelete.edges.first().startNode
        assertFalse(
            startNodeAfter.node.containsSwitch(switchId),
            "Switch reference should be removed from location track"
        )
    }

    @Test
    fun `deleteSwitchLinks keeps switch references when set to false`() {
        // Create track number and switch
        val tnId = mainDraftContext.createLayoutTrackNumber().id
        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id,
            )
        val switchId = switch.id as IntId

        // Create location track with switch link
        val (track, _) =
            insertDraft(
                locationTrack(tnId, draft = true),
                trackGeometry(
                    TmpLayoutEdge(
                        startNode = NodeConnection.switch(inner = null, outer = switchLinkYV(switchId, 1)),
                        endNode = PlaceHolderNodeConnection,
                        segments = listOf(someSegment()),
                    )
                ),
            )

        // Verify that the location track has switch reference before update
        val (_, geometryBeforeUpdate) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, track.id as IntId)
        val startNodeBefore = geometryBeforeUpdate.edges.first().startNode
        assertTrue(startNodeBefore.node.containsSwitch(switchId), "Track should have switch link before update")

        // Create switch save request with NOT_EXISTING state
        val switchSaveRequest =
            LayoutSwitchSaveRequest(
                name = switch.name,
                switchStructureId = switch.switchStructureId,
                stateCategory = LayoutStateCategory.NOT_EXISTING,
                ownerId = switch.ownerId,
                trapPoint = switch.trapPoint,
                draftOid = switch.draftOid,
            )

        // Update switch with deleteSwitchLinks = false
        switchService.updateSwitch(LayoutBranch.main, switchId, switchSaveRequest, deleteSwitchLinks = false)

        // Verify that the location track still has switch reference
        val (_, geometryAfterUpdate) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, track.id as IntId)
        val startNodeAfter = geometryAfterUpdate.edges.first().startNode
        assertTrue(
            startNodeAfter.node.containsSwitch(switchId),
            "Switch reference should be kept in location track when deleteSwitchLinks is false"
        )
    }

    @Test
    fun `deleteSwitchLinks cannot be set to true when stateCategory is not NOT_EXISTING`() {
        // Create switch
        val switch =
            switchService.getOrThrow(
                MainLayoutContext.draft,
                switchService.saveDraft(LayoutBranch.main, switch(draft = true)).id,
            )
        val switchId = switch.id as IntId

        // Create switch save request with EXISTING state
        val switchSaveRequest =
            LayoutSwitchSaveRequest(
                name = switch.name,
                switchStructureId = switch.switchStructureId,
                stateCategory = LayoutStateCategory.EXISTING,
                ownerId = switch.ownerId,
                trapPoint = switch.trapPoint,
                draftOid = switch.draftOid,
            )

        // Verify that updating with deleteSwitchLinks = true throws an exception
        val exception =
            assertThrows<IllegalArgumentException> {
                switchService.updateSwitch(LayoutBranch.main, switchId, switchSaveRequest, deleteSwitchLinks = true)
            }
        assertEquals(
            "deleteSwitchLinks can only be set to true when stateCategory is NOT_EXISTING",
            exception.message
        )
    }

    private fun insertDraft(
        locationTrack: LocationTrack,
        geometry: LocationTrackGeometry,
        oid: Oid<LocationTrack>? = null,
    ): Pair<LocationTrack, LocationTrackIdentifiers> {
        val version = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry)
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
