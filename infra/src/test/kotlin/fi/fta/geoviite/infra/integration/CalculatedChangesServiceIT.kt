package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.linking.FittedSwitch
import fi.fta.geoviite.infra.linking.FittedSwitchJoint
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest(properties = ["geoviite.cache.enabled=true"])
class CalculatedChangesServiceIT @Autowired constructor(
    val calculatedChangesService: CalculatedChangesService,
    val switchDao: LayoutSwitchDao,
    val locationTrackDao: LocationTrackDao,
    val locationTrackService: LocationTrackService,
    val referenceLineDao: ReferenceLineDao,
    val referenceLineService: ReferenceLineService,
    val layoutAlignmentDao: LayoutAlignmentDao,
    val layoutTrackNumberDao: LayoutTrackNumberDao,
    val layoutKmPostDao: LayoutKmPostDao,
    val switchService: LayoutSwitchService,
    val switchLinkingService: SwitchLinkingService,
    val alignmentDao: LayoutAlignmentDao,
    val kmPostService: LayoutKmPostService,
    val trackNumberservice: LayoutTrackNumberService,
) : DBTestBase() {

    @BeforeEach
    fun setup() {
        locationTrackDao.deleteDrafts()
        referenceLineDao.deleteDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchDao.deleteDrafts()
    }

    @Test
    fun callingWithoutDataReturnsNoCalculatedChanges() {
        // Insert test data to make sure that there is some data in DB
        insertTestData()

        val changes = getCalculatedChanges()

        assertTrue(changes.directChanges.trackNumberChanges.isEmpty())
        assertTrue(changes.directChanges.locationTrackChanges.isEmpty())
        assertTrue(changes.directChanges.switchChanges.isEmpty())
        assertTrue(changes.directChanges.referenceLineChanges.isEmpty())
        assertTrue(changes.directChanges.kmPostChanges.isEmpty())

        assertTrue(changes.indirectChanges.trackNumberChanges.isEmpty())
        assertTrue(changes.indirectChanges.locationTrackChanges.isEmpty())
        assertTrue(changes.indirectChanges.switchChanges.isEmpty())
    }

    @Test
    fun locationTrackGeometryChangeGeneratesIndirectlySwitchChanges() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        // Move alignment
        // - addresses should change
        // - switch change should be calculated
        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack3.id as IntId),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack3.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(6),
                    KmNumber(7),
                    KmNumber(8)
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )

        assertContainsSwitchJoint152Change(
            changes.indirectChanges.switchChanges,
            testData.switches.first().id,
            locationTrack3.id
        )
    }

    @Test
    fun addingTopologyEndSwitchGeneratesIndirectlySwitchChanges() {
        val testData = insertTestData(
            kmPostData = listOf(
                KmNumber(0) to Point(0.0, 0.0),
                KmNumber(1) to Point(1000.0, 0.0)
            ),
            locationTrackData = listOf(
                Line(Point(0.0, 0.0), Point(100.0, 0.0)),

                // tracks for YV switch
                Line(Point(100.0, 0.0), Point(200.0, 0.0)),
                Line(Point(100.0, 0.0), Point(200.0, 20.0)),
            ),
            switchData = listOf(
                SwitchData(
                    Point(100.0, 0.0),
                    locationTrackIndexA = 1,
                    locationTrackIndexB = 2
                )
            )
        )
        val (locationTrack1, alignment1) = testData.locationTracksAndAlignments[0]
        val switch = testData.switches[0]

        // Manually remove topology switch as it is automatically added when creating test data
        val (updatedLocationTrack, updatedAlignment) = removeTopologySwitchesFromLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            locationTrackService
        ).let { (id, version) ->
            val (_, publishedVersion) = locationTrackService.publish(ValidationVersion(id, version))
            locationTrackService.getWithAlignment(publishedVersion)
        }

        // Set topology switch info
        addTopologyEndSwitchIntoLocationTrackAndUpdate(
            updatedLocationTrack,
            updatedAlignment,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack1.id as IntId),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(KmNumber(0)),
                isStartChanged = false,
                isEndChanged = true
            )
        )

        assertEquals(1, changes.indirectChanges.switchChanges.size)
        changes.indirectChanges.switchChanges.forEach { switchChange ->
            assertEquals(switch.id, switchChange.switchId)
            assertEquals(1, switchChange.changedJoints.size)
            switchChange.changedJoints.forEach { joint ->
                assertEquals(
                    joint.copy(
                        number = JointNumber(1),
                        isRemoved = false,
                        point = Point(alignment1.end!!),
                        address = TrackMeter("0", "100.000"),
                        locationTrackId = locationTrack1.id as IntId,
                    ),
                    joint
                )
            }
        }
    }


    @Test
    fun addingTopologyStartSwitchGeneratesIndirectlySwitchChanges() {
        val testData = insertTestData(
            kmPostData = listOf(
                KmNumber(0) to Point(0.0, 0.0),
                KmNumber(1) to Point(1000.0, 0.0)
            ),
            locationTrackData = listOf(
                // NOTICE: This track starts from the switch. It is not aligned properly
                // but should be OK for this test.
                Line(Point(100.0, 0.0), Point(150.0, 50.0)),

                // tracks for YV switch
                Line(Point(100.0, 0.0), Point(200.0, 0.0)),
                Line(Point(100.0, 0.0), Point(200.0, 20.0)),
            ),
            switchData = listOf(
                SwitchData(
                    Point(100.0, 0.0),
                    locationTrackIndexA = 1,
                    locationTrackIndexB = 2
                )
            )
        )
        val (locationTrack1, alignment1) = testData.locationTracksAndAlignments[0]
        val switch = testData.switches[0]

        // Manually remove topology switch as it is automatically added when creating test data
        val (updatedLocationTrack, updatedAlignment) = removeTopologySwitchesFromLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            locationTrackService
        ).let { (id, version) ->
            val (_, publishedVersion) = locationTrackService.publish(ValidationVersion(id, version))
            locationTrackService.getWithAlignment(publishedVersion)
        }

        // Set topology switch info
        addTopologyStartSwitchIntoLocationTrackAndUpdate(
            updatedLocationTrack,
            updatedAlignment,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack1.id as IntId),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(KmNumber(0)),
                isStartChanged = true,
                isEndChanged = false
            )
        )

        assertEquals(1, changes.indirectChanges.switchChanges.size)
        changes.indirectChanges.switchChanges.forEach { switchChange ->
            assertEquals(switch.id, switchChange.switchId)
            assertEquals(1, switchChange.changedJoints.size)
            switchChange.changedJoints.forEach { joint ->
                assertEquals(
                    joint.copy(
                        number = JointNumber(1),
                        isRemoved = false,
                        point = Point(alignment1.start!!),
                        address = TrackMeter("0", "100.000"),
                        locationTrackId = locationTrack1.id as IntId,
                    ),
                    joint
                )
            }
        }
    }

    @Test
    fun addingNonPresentationPointTopologySwitchesShouldNotGenerateSwitchChanges() {
        val sequence = System.currentTimeMillis().toString().takeLast(8)

        val testData = insertTestData(
            kmPostData = listOf(
                KmNumber(0) to Point(0.0, 0.0),
                KmNumber(1) to Point(1000.0, 0.0)
            ),
            locationTrackData = listOf(
                // NOTICE: This track does not locate near to the switch
                // but topology switch link is added manually afterwards
                Line(Point(0.0, 0.0), Point(85.0, 0.0)),

                // tracks for YV switch
                Line(Point(100.0, 0.0), Point(200.0, 0.0)),
                Line(Point(100.0, 0.0), Point(200.0, 20.0)),
            ),
            switchData = listOf(
                SwitchData(
                    Point(100.0, 0.0),
                    locationTrackIndexA = 1,
                    locationTrackIndexB = 2,
                    name = "switch-A $sequence",
                ),
                SwitchData(
                    Point(100.0, 0.0),
                    locationTrackIndexA = 1,
                    locationTrackIndexB = 2,
                    name = "switch-B $sequence",
                )
            )
        )
        val (locationTrack1, alignment1) = testData.locationTracksAndAlignments[0]

        // Set topology switch info
        val (updatedLocationTrack, updatedAlignment) = addTopologyStartSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            testData.switches[0].id as IntId,
            JointNumber(5), // Use non-presentation joint number
            locationTrackService = locationTrackService
        ).let { locationTrackService.getWithAlignment(it.rowVersion) }

        addTopologyEndSwitchIntoLocationTrackAndUpdate(
            updatedLocationTrack,
            updatedAlignment,
            testData.switches[1].id as IntId,
            JointNumber(3), // Use non-presentation joint number
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack1.id as IntId),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(),
                isStartChanged = false,
                isEndChanged = false
            )
        )
        assertTrue(changes.indirectChanges.switchChanges.isEmpty())
    }


    @Test
    fun removingTopologyEndSwitchGeneratesIndirectlySwitchChanges() {
        val testData = insertTestData(
            kmPostData = listOf(
                KmNumber(0) to Point(0.0, 0.0),
                KmNumber(1) to Point(1000.0, 0.0)
            ),
            locationTrackData = listOf(
                Line(Point(0.0, 0.0), Point(100.0, 0.0)),

                // tracks for YV switch
                Line(Point(100.0, 0.0), Point(200.0, 0.0)),
                Line(Point(100.0, 0.0), Point(200.0, 20.0)),
            ),
            switchData = listOf(
                SwitchData(
                    Point(100.0, 0.0),
                    locationTrackIndexA = 1,
                    locationTrackIndexB = 2
                )
            )
        )
        val (locationTrack1, alignment1) = testData.locationTracksAndAlignments[0]
        val switch = testData.switches[0]

        // Add a topology switch to generate base state
        val (updatedLocationTrack, updatedAlignment) = addTopologyEndSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        ).let { (id, version) ->
            val (_, publishedVersion) = locationTrackService.publish(ValidationVersion(id, version))
            locationTrackService.getWithAlignment(publishedVersion)
        }

        // Then remove the topology switch info
        removeTopologySwitchesFromLocationTrackAndUpdate(
            updatedLocationTrack,
            updatedAlignment,
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(
                locationTrack1.id as IntId
            ),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(),
                isStartChanged = false,
                isEndChanged = false
            )
        )

        assertEquals(1, changes.indirectChanges.switchChanges.size)
        changes.indirectChanges.switchChanges.forEach { switchChange ->
            assertEquals(switch.id, switchChange.switchId)
            assertEquals(1, switchChange.changedJoints.size)
            switchChange.changedJoints.forEach { joint ->
                assertEquals(
                    joint.copy(
                        number = JointNumber(1),
                        isRemoved = true,
                        point = Point(alignment1.end!!),
                        address = TrackMeter("0", "100.000"),
                        locationTrackId = locationTrack1.id as IntId,
                    ),
                    joint
                )
            }
        }
    }


    @Test
    fun allChangedLocationTracksExistInIndirectSwitchChange() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, alignment4) = testData.locationTracksAndAlignments[3]

        // Move alignment
        // - addresses should change
        // - switch change should be calculated
        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )
        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack4,
            alignment4,
            { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack3.id as IntId, locationTrack4.id as IntId),
        )

        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack3.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(6),
                    KmNumber(7),
                    KmNumber(8)
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )
        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack4.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(7),
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )
        assertContainsSwitchJoint13Change(
            changes.indirectChanges.switchChanges,
            testData.switches.first().id,
            locationTrack4.id
        )

        assertContainsSwitchJoint152Change(
            changes.indirectChanges.switchChanges,
            testData.switches.first().id,
            locationTrack3.id
        )
    }

    @Test
    fun shouldNotGenerateIndirectSwitchChangesIfGeometryChangeIsNotInAddressRange() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        // Move first 200m only (kilometer 0006)
        // - addresses should change
        // - there should be NO calculated switch changes
        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point -> if (point.m < 200) point + 2.0 else point },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack3.id as IntId),
        )

        assertEquals(
            changes.directChanges.locationTrackChanges,
            listOf(
                LocationTrackChange(
                    locationTrackId = locationTrack3.id as IntId<LocationTrack>,
                    changedKmNumbers = setOf(KmNumber(6)),
                    isStartChanged = true,
                    isEndChanged = false
                ),
            ),
        )
        assertTrue(changes.indirectChanges.switchChanges.isEmpty())
    }

    @Test
    fun referenceLineChangeGeneratesIndirectLocationTrackChanges() {
        val testData = insertTestData()
        val (locationTrack1, _) = testData.locationTracksAndAlignments[0]
        val (referenceLine, referenceLineAlignment) = testData.referenceLineAndAlignment

        // Move first kilometer only (kilometer 5)
        // - addresses should change
        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine,
            referenceLineAlignment,
            { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges(
            referenceLineIds = listOf(referenceLine.id as IntId),
        )

        assertContains(changes.directChanges.referenceLineChanges, referenceLine.id)

        assertContains(
            changes.indirectChanges.trackNumberChanges, TrackNumberChange(
                trackNumberId = referenceLine.trackNumberId,
                changedKmNumbers = setOf(KmNumber(5)),
                isStartChanged = true,
                isEndChanged = false
            )
        )
        assertContains(
            changes.indirectChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(KmNumber(5)),
                isStartChanged = true,
                isEndChanged = false
            )
        )
    }


    @Test
    fun referenceLineChangeGeneratesIndirectlyLocationTrackChangesThatGenerateIndirectlySwitchChanges() {
        val testData = insertTestData()
        val (locationTrack1, _) = testData.locationTracksAndAlignments[0]
        val (locationTrack3, _) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, _) = testData.locationTracksAndAlignments[3]
        val (referenceLine, referenceLineAlignment) = testData.referenceLineAndAlignment

        // Move points from kilometers 6 and 7
        // - ref line addresses should change
        // - addresses of location tracks 1, 3 and 4 should be changed
        // - switch geom is changed
        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine,
            referenceLineAlignment,
            { point ->
                if (point.m > 1000 && point.m < 2900)
                // make reference line wavy
                    point + Point(0.0, cos((point.m - 1000) / (2900 - 1000) * PI) * 5)
                else
                    point
            },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges(
            referenceLineIds = listOf(referenceLine.id as IntId),
        )

        assertContains(changes.directChanges.referenceLineChanges, referenceLine.id)

        assertContains(
            changes.indirectChanges.trackNumberChanges, TrackNumberChange(
                trackNumberId = referenceLine.trackNumberId,
                changedKmNumbers = setOf(
                    KmNumber(6),
                    KmNumber(7)
                ),
                isStartChanged = false,
                isEndChanged = false,
            )
        )
        assertContains(
            changes.indirectChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(
                    KmNumber(6),
                ),
                isStartChanged = false,
                isEndChanged = true
            )
        )
        assertContains(
            changes.indirectChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack3.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(6),
                    KmNumber(7),
                ),
                isStartChanged = true,
                isEndChanged = false
            )
        )
        assertContains(
            changes.indirectChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack4.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(7),
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )

        assertContainsSwitchJoint13Change(
            changes.indirectChanges.switchChanges,
            testData.switches.first().id,
            locationTrack4.id
        )

        assertContainsSwitchJoint152Change(
            changes.indirectChanges.switchChanges,
            testData.switches.first().id,
            locationTrack3.id
        )
    }

    @Test
    fun shouldCombineSwitchChangesAndGeometryChanges() {
        val testData = insertTestData()
        val switch = testData.switches.first()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        moveSwitchPoints(
            switch,
            { point -> point + 0.5 },
            switchService,
        )

        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            switchIds = listOf(switch.id as IntId),
            locationTrackIds = listOf(locationTrack3.id as IntId<LocationTrack>),
        )

        assertEquals(1, changes.directChanges.switchChanges.size)

        assertContainsSwitchJoint152Change(
            changes.directChanges.switchChanges,
            testData.switches.first().id,
            locationTrack3.id
        )
    }

    @Test
    fun switchLinkingGeneratesDirectSwitchChangesOnly() {
        val testData = insertTestData()
        val switch = testData.switches.first()

        moveSwitchPoints(
            switch,
            { point -> point + 0.5 },
            switchService,
        )

        val changes = getCalculatedChanges(
            switchIds = listOf(switch.id as IntId),
        )

        assertTrue(changes.directChanges.switchChanges.all { it.switchId == switch.id && it.changedJoints.isEmpty() })
        assertTrue(changes.indirectChanges.switchChanges.isEmpty())
        assertTrue(changes.indirectChanges.locationTrackChanges.isEmpty())
    }

    @Test
    fun `should not calculate anything when there aren't direct changes`() {
        val testData = insertTestData()
        val (referenceLine, alignment) = testData.referenceLineAndAlignment

        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine = referenceLine,
            alignment = alignment,
            moveFunc = { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges()

        assertEquals(0, changes.directChanges.kmPostChanges.size)
        assertEquals(0, changes.directChanges.referenceLineChanges.size)
        assertEquals(0, changes.directChanges.trackNumberChanges.size)
        assertEquals(0, changes.directChanges.locationTrackChanges.size)
        assertEquals(0, changes.directChanges.switchChanges.size)

        assertEquals(0, changes.indirectChanges.trackNumberChanges.size)
        assertEquals(0, changes.indirectChanges.locationTrackChanges.size)
        assertEquals(0, changes.indirectChanges.switchChanges.size)
    }

    @Test
    fun `km post changes should be included in calculated changes`() {
        val testData = insertTestData()
        val kmPost = testData.kmPosts.first()
        kmPostService.saveDraft(
            kmPost.copy(
                kmNumber = KmNumber(kmPost.kmNumber.number, "A")
            )
        )
        val changes = getCalculatedChanges(kmPostIds = listOf(kmPost.id as IntId))

        assertEquals(1, changes.directChanges.kmPostChanges.size)
        assertContains(changes.directChanges.kmPostChanges, kmPost.id)
        assertEquals(1, changes.indirectChanges.trackNumberChanges.size)
        assertEquals(kmPost.trackNumberId, changes.indirectChanges.trackNumberChanges[0].trackNumberId)
        assertEquals(0, changes.directChanges.trackNumberChanges.size)
    }

    @Test
    fun `changing km post should indirectly cause track number changes`() {
        val testData = insertTestData()
        val kmPost = testData.kmPosts.first()
        val location = kmPost.location

        assertNotNull(location)
        moveKmPostLocation(
            kmPost = kmPost,
            location = location + 2.0,
            kmPostService = kmPostService,
        )

        val changes = getCalculatedChanges(kmPostIds = listOf(kmPost.id as IntId))
        assertEquals(1, changes.indirectChanges.trackNumberChanges.size)
        assertEquals(kmPost.trackNumberId, changes.indirectChanges.trackNumberChanges[0].trackNumberId)
        assertEquals(0, changes.directChanges.trackNumberChanges.size)
    }

    @Test
    fun `changing km post should indirectly cause track number changes that cause location track changes`() {
        val testData = insertTestData()
        val kmPost = testData.kmPosts[2]
        val (locationTrack3, _) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, _) = testData.locationTracksAndAlignments[3]

        val location = kmPost.location

        assertNotNull(location)
        moveKmPostLocation(
            kmPost = kmPost,
            location = location + 2.0,
            kmPostService = kmPostService,
        )

        val changes = getCalculatedChanges(kmPostIds = listOf(kmPost.id as IntId))
        val indirectLocationTrackChanges = changes.indirectChanges.locationTrackChanges

        assertTrue(indirectLocationTrackChanges.any { it.locationTrackId == locationTrack3.id })
        assertTrue(indirectLocationTrackChanges.any { it.locationTrackId == locationTrack4.id })
        assertEquals(0, changes.directChanges.locationTrackChanges.size)
    }

    @Test
    fun `changing km post should indirectly cause track number changes that cause location track changes that cause switch changes`() {
        val testData = insertTestData()
        val kmPost = testData.kmPosts[2]
        val (locationTrack3, _) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, _) = testData.locationTracksAndAlignments[3]
        val switch = testData.switches.first()

        val location = kmPost.location

        assertNotNull(location)
        moveKmPostLocation(
            kmPost = kmPost,
            location = location + 2.0,
            kmPostService = kmPostService,
        )

        val changes = getCalculatedChanges(kmPostIds = listOf(kmPost.id as IntId))

        assertEquals(1, changes.indirectChanges.switchChanges.size)
        assertEquals(0, changes.directChanges.switchChanges.size)
        assertContainsSwitchJoint152Change(
            changes = changes.indirectChanges.switchChanges,
            switchId = switch.id,
            locationTrackId = locationTrack3.id
        )

        assertContainsSwitchJoint13Change(
            changes = changes.indirectChanges.switchChanges,
            switchId = switch.id,
            locationTrackId = locationTrack4.id
        )
    }

    @Test
    fun `reference line changes should be included in calculated changes`() {
        val testData = insertTestData()
        val (referenceLine, _) = testData.referenceLineAndAlignment
        referenceLineService.saveDraft(
            referenceLine.copy(
                startAddress = TrackMeter(0, 500)
            )
        )

        val changes = getCalculatedChanges(referenceLineIds = listOf(referenceLine.id as IntId))

        assertEquals(1, changes.directChanges.referenceLineChanges.size)
        assertContains(changes.directChanges.referenceLineChanges, referenceLine.id)
        assertEquals(1, changes.indirectChanges.trackNumberChanges.size)
        assertEquals(referenceLine.trackNumberId, changes.indirectChanges.trackNumberChanges[0].trackNumberId)
        assertEquals(0, changes.directChanges.trackNumberChanges.size)
    }

    @Test
    fun `changing reference line should indirectly cause track number changes`() {
        val testData = insertTestData()
        val (referenceLine, alignment) = testData.referenceLineAndAlignment

        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine = referenceLine,
            alignment = alignment,
            moveFunc = { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges(referenceLineIds = listOf(referenceLine.id as IntId))
        assertEquals(1, changes.indirectChanges.trackNumberChanges.size)
        assertEquals(changes.indirectChanges.trackNumberChanges[0].trackNumberId, referenceLine.trackNumberId)
        assertEquals(0, changes.directChanges.trackNumberChanges.size)
    }

    @Test
    fun `changing reference line should indirectly cause track number changes that cause location track changes`() {
        val testData = insertTestData()
        val (referenceLine, alignment) = testData.referenceLineAndAlignment
        val (locationTrack, _) = testData.locationTracksAndAlignments[0]

        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine = referenceLine,
            alignment = alignment,
            moveFunc = { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges(referenceLineIds = listOf(referenceLine.id as IntId))
        assertEquals(1, changes.indirectChanges.locationTrackChanges.size)
        assertEquals(locationTrack.id, changes.indirectChanges.locationTrackChanges[0].locationTrackId)
        assertEquals(0, changes.directChanges.locationTrackChanges.size)
    }

    @Test
    fun `track number changes should be included in calculated changes`() {
        val testData = insertTestData()
        val trackNumber = testData.trackNumber
        trackNumberservice.saveDraft(
            trackNumber.copy(
                description = FreeText(UUID.randomUUID().toString())
            )
        )

        val changes = getCalculatedChanges(trackNumberIds = listOf(trackNumber.id as IntId))
        assertEquals(1, changes.directChanges.trackNumberChanges.size)
        assertEquals(trackNumber.id, changes.directChanges.trackNumberChanges[0].trackNumberId)
        assertEquals(0, changes.indirectChanges.trackNumberChanges.size)
    }

    @Test
    fun `location track changes should be included in calculated changes`() {
        val testData = insertTestData()
        val (locationTrack, alignment) = testData.locationTracksAndAlignments[2]

        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack = locationTrack,
            alignment = alignment,
            moveFunc = { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(locationTrackIds = listOf(locationTrack.id as IntId))
        assertEquals(1, changes.directChanges.locationTrackChanges.size)
        assertEquals(locationTrack.id, changes.directChanges.locationTrackChanges[0].locationTrackId)
        assertEquals(0, changes.indirectChanges.locationTrackChanges.size)
    }

    @Test
    fun `changing location track should indirectly cause switch changes`() {
        val testData = insertTestData()
        val (locationTrack, alignment) = testData.locationTracksAndAlignments[2]
        val switch = testData.switches.first()

        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack = locationTrack,
            alignment = alignment,
            moveFunc = { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(locationTrackIds = listOf(locationTrack.id as IntId))
        assertEquals(1, changes.indirectChanges.switchChanges.size)
        assertContainsSwitchJoint152Change(
            changes = changes.indirectChanges.switchChanges,
            switchId = switch.id,
            locationTrackId = locationTrack.id,
        )
        assertEquals(0, changes.directChanges.switchChanges.size)
    }

    @Test
    fun `switch changes should be included in calculated changes`() {
        val testData = insertTestData()
        val switch = testData.switches.first()
        switchService.saveDraft(
            switch.copy(
                name = SwitchName(UUID.randomUUID().toString())
            )
        )

        val changes = getCalculatedChanges(switchIds = listOf(switch.id as IntId))
        assertEquals(1, changes.directChanges.switchChanges.size)
        assertEquals(switch.id, changes.directChanges.switchChanges[0].switchId)
        assertEquals(0, changes.indirectChanges.switchChanges.size)
    }

    @Test
    fun `indirect track number changes should be combined with direct track number changes`() {
        val testData = insertTestData()
        val (referenceLine, alignment) = testData.referenceLineAndAlignment

        val trackNumber = testData.trackNumber
        trackNumberservice.saveDraft(
            trackNumber.copy(
                description = FreeText(UUID.randomUUID().toString())
            )
        )

        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine,
            alignment,
            { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService
        )

        val changes = getCalculatedChanges(
            trackNumberIds = listOf(trackNumber.id as IntId),
            referenceLineIds = listOf(referenceLine.id as IntId)
        )

        assertEquals(1, changes.directChanges.trackNumberChanges.size)
        assertContains(
            changes.directChanges.trackNumberChanges, TrackNumberChange(
                trackNumberId = trackNumber.id as IntId,
                changedKmNumbers = setOf(KmNumber(5)),
                isStartChanged = true,
                isEndChanged = false
            )
        )

        assertEquals(0, changes.indirectChanges.trackNumberChanges.size)
    }

    @Test
    fun `indirect location track changes should be combined with direct location track changes`() {
        val testData = insertTestData()
        val (referenceLine, referenceLineAlignment) = testData.referenceLineAndAlignment
        val (locationTrack, locationTrackAlignment) = testData.locationTracksAndAlignments[0]

        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack = locationTrack,
            alignment = locationTrackAlignment,
            moveFunc = { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        moveReferenceLineGeometryPointsAndUpdate(
            referenceLine = referenceLine,
            alignment = referenceLineAlignment,
            moveFunc = { point -> if (point.m < 900) point - 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = getCalculatedChanges(
            locationTrackIds = listOf(locationTrack.id as IntId),
            referenceLineIds = listOf(referenceLine.id as IntId)
        )

        assertEquals(1, changes.directChanges.locationTrackChanges.size)
        assertContains(
            changes.directChanges.locationTrackChanges, LocationTrackChange(
                locationTrackId = locationTrack.id as IntId,
                changedKmNumbers = setOf(KmNumber(5), KmNumber(6)),
                isStartChanged = true,
                isEndChanged = true
            )
        )

        assertEquals(0, changes.indirectChanges.locationTrackChanges.size)
    }

    @Test
    fun `indirect switch changes should be combined with direct switch changes`() {
        val testData = insertTestData()
        val (locationTrack, alignment) = testData.locationTracksAndAlignments[2]
        val switch = testData.switches.first()

        switchService.saveDraft(
            switch.copy(
                name = SwitchName(UUID.randomUUID().toString())
            )
        )

        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack = locationTrack,
            alignment = alignment,
            moveFunc = { point -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = getCalculatedChanges(
            switchIds = listOf(switch.id as IntId),
            locationTrackIds = listOf(locationTrack.id as IntId)
        )

        assertEquals(1, changes.directChanges.switchChanges.size)
        assertContainsSwitchJoint152Change(
            changes = changes.directChanges.switchChanges,
            switchId = switch.id,
            locationTrackId = locationTrack.id
        )

        assertEquals(0, changes.indirectChanges.switchChanges.size)
    }


    data class TestData(
        val trackNumber: TrackLayoutTrackNumber,
        val locationTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
        val referenceLineAndAlignment: Pair<ReferenceLine, LayoutAlignment>,
        val kmPosts: List<TrackLayoutKmPost>,
        val switches: List<TrackLayoutSwitch>,
        val changeTime: Instant,
    )

    data class SwitchData(
        val location: IPoint,
        val locationTrackIndexA: Int,
        val locationTrackIndexB: Int,
        val name: String? = null,
    )

    /**
     * Reference line
     * -----------------------------
     *
     * Km posts
     * 5      6      7      8      9
     *
     * Location track 1
     *    --------
     *
     * Location track 2
     *                        ----
     *
     * Location track 3 (switch 1-5-2)
     *            ---------------
     *
     * Location track 4 (switch 1-3)
     *                   --
     *
     */
    fun insertTestData(): TestData {
        return insertTestData(
            kmPostData = listOf(
                KmNumber(5) to Point(0.0, 0.0),
                KmNumber(6) to Point(1000.0, 0.0),
                KmNumber(7) to Point(2000.0, 0.0),
                KmNumber(8) to Point(3000.0, 0.0),
                KmNumber(9) to Point(4000.0, 0.0)
            ),
            locationTrackData = listOf(
                Line(Point(500.0, 0.0), Point(1500.0, 0.0)),
                Line(Point(3200.0, 0.0), Point(3800.0, 0.0)),
                Line(Point(1500.0, 0.0), Point(3500.0, 0.0)),
                Line(Point(2490.0, 0.0), Point(2700.0, 20.0)),
            ),
            switchData = listOf(
                SwitchData(
                    Point(2500.0, 0.0),
                    locationTrackIndexA = 2,
                    locationTrackIndexB = 3
                )
            )
        )
    }

    fun insertTestData(
        kmPostData: List<Pair<KmNumber, IPoint>> = listOf(),
        locationTrackData: List<Line> = listOf(),
        switchData: List<SwitchData> = listOf()
    ): TestData {
        val sequence = System.currentTimeMillis().toString().takeLast(8)
        val refPoint = Point(350000.0, 7000000.0) // any point in Finland

        val trackNumber = layoutTrackNumberDao.fetch(
            layoutTrackNumberDao.insert(
                trackNumber(TrackNumber("TEST TN $sequence"))
            ).rowVersion
        )
        val kmPosts = kmPostData.map { (kmNumber, location) ->
            layoutKmPostDao.fetch(
                layoutKmPostDao.insert(
                    kmPost(
                        trackNumberId = trackNumber.id as IntId,
                        km = kmNumber,
                        location = refPoint + location
                    )
                ).rowVersion
            )
        }
        val referenceLineGeometryVersion = layoutAlignmentDao.insert(
            alignment(
                segment(
                    kmPosts.first().location as Point,
                    kmPosts.last().location as Point
                )
            )
        )
        val referenceLineGeometry = layoutAlignmentDao.fetch(referenceLineGeometryVersion)
        val referenceLine = referenceLineDao.fetch(
            referenceLineDao.insert(
                referenceLine(
                    trackNumber.id as IntId<TrackLayoutTrackNumber>,
                    alignment = referenceLineGeometry,
                    startAddress = TrackMeter(
                        kmNumber = kmPosts.first().kmNumber,
                        meters = BigDecimal.ZERO,
                    )
                ).copy(
                    alignmentVersion = referenceLineGeometryVersion
                )
            ).rowVersion
        )

        var locationTrackSequence = 0
        val locationTracksAndAlignments = locationTrackData.map { line ->
            val locationTrackGeometryVersion = layoutAlignmentDao.insert(
                alignment(
                    segments(
                        refPoint + line.start,
                        refPoint + line.end,
                        10.0
                    )
                )
            )
            val locationTrackGeometry = layoutAlignmentDao.fetch(locationTrackGeometryVersion)
            val locationTrack = locationTrackDao.fetch(
                locationTrackDao.insert(
                    locationTrack(
                        trackNumberId = trackNumber.id as IntId,
                        alignment = locationTrackGeometry,
                        name = "TEST LocTr $sequence ${locationTrackSequence++}"
                    ).copy(
                        alignmentVersion = locationTrackGeometryVersion
                    )
                ).rowVersion
            )
            locationTrack to locationTrackGeometry
        }

        val switches = switchData.map { switch ->
            linkTestSwitch(
                refPoint + switch.location,
                locationTracksAndAlignments[switch.locationTrackIndexA],
                locationTracksAndAlignments[switch.locationTrackIndexB],
                switch.name,
            )
        }

        val publishedLocationTracksAndAlignments = locationTracksAndAlignments.map { (locationTrack, _) ->
            val id = locationTrack.id as IntId
            val rowVersion = locationTrackDao.fetchDraftVersionOrThrow(id)
            val (edited, editedAlignment) = locationTrackService.getWithAlignment(rowVersion)
            if (edited.isDraft) {
                val publishResponse = locationTrackService.publish(ValidationVersion(id, rowVersion))
                locationTrackService.getWithAlignment(publishResponse.rowVersion)
            } else edited to editedAlignment
        }
        val publishedSwitches = switches.map { switch ->
            val id = switch.id as IntId
            val rowVersion = switchDao.fetchDraftVersionOrThrow(id)
            val edited = switchDao.fetch(rowVersion)
            if (edited.isDraft) {
                val publishResponse = switchService.publish(ValidationVersion(id, rowVersion))
                switchDao.fetch(publishResponse.rowVersion)
            } else edited
        }

        return TestData(
            trackNumber = trackNumber,
            locationTracksAndAlignments = publishedLocationTracksAndAlignments,
            referenceLineAndAlignment = referenceLine to referenceLineGeometry,
            kmPosts = kmPosts,
            switches = publishedSwitches,
            changeTime = maxOf(locationTrackService.getChangeTime(), switchService.getChangeTime()),
        )
    }

    fun linkTestSwitch(
        switchLocation: IPoint,
        trackA: Pair<LocationTrack, LayoutAlignment>,
        trackB: Pair<LocationTrack, LayoutAlignment>,
        name: String?,
    ): TrackLayoutSwitch {
        val switch = switchDao.fetch(
            switchDao.insert(
                switch(
                    name = name ?: "${trackA.first.name}-${trackB.first.name}",
                    joints = listOf()
                )
            ).rowVersion
        )

        val (locationTrackA, alignmentA) = trackA
        val (locationTrackB, alignmentB) = trackB
        val segIndexA = alignmentA.findClosestSegmentIndex(switchLocation) as Int
        val segIndexB = alignmentB.findClosestSegmentIndex(switchLocation) as Int

        val suggestedFitting =
            FittedSwitch(
                joints = listOf(
                    FittedSwitchJoint(
                        number = JointNumber(1),
                        location = firstPoint(alignmentA, segIndexA).toPoint(),
                        matches = listOf(
                            switchLinkingAtStart(locationTrackA.id, alignmentA, segIndexA),
                            switchLinkingAtStart(locationTrackB.id, alignmentB, segIndexB),
                        ),
                        locationAccuracy = null
                    ),
                    FittedSwitchJoint(
                        number = JointNumber(5),
                        location = lastPoint(alignmentA, segIndexA).toPoint(),
                        matches = listOf(
                            switchLinkingAtEnd(locationTrackA.id, alignmentA, segIndexA),
                        ),
                        locationAccuracy = null
                    ),
                    FittedSwitchJoint(
                        number = JointNumber(2),
                        location = lastPoint(alignmentA, segIndexA + 2).toPoint(),
                        matches = listOf(
                            switchLinkingAtEnd(locationTrackA.id, alignmentA, segIndexA + 2),
                        ),
                        locationAccuracy = null
                    ),
                    FittedSwitchJoint(
                        number = JointNumber(3),
                        location = lastPoint(alignmentB, segIndexB + 1).toPoint(),
                        matches = listOf(
                            switchLinkingAtEnd(locationTrackB.id, alignmentB, segIndexB + 1),
                        ),
                        locationAccuracy = null
                    )
                ),
                geometrySwitchId = null,
                switchStructureId = switch.switchStructureId,
                alignmentEndPoint = null,
                name = SwitchName("abc"),
            )
        switchLinkingService.saveSwitchLinking(
            switchLinkingService.matchFittedSwitch(
                suggestedFitting, switch.id as IntId
            ), switch.id as IntId
        )
        return switch
    }

    private fun firstPoint(alignment: LayoutAlignment, segmentIndex: Int) =
        alignment.segments[segmentIndex].alignmentPoints.first()

    private fun lastPoint(alignment: LayoutAlignment, segmentIndex: Int) =
        alignment.segments[segmentIndex].alignmentPoints.last()

    private fun assertContainsSwitchJoint152Change(
        changes: List<SwitchChange>,
        switchId: DomainId<TrackLayoutSwitch>,
        locationTrackId: DomainId<LocationTrack>,
    ) {
        val switchChange = changes.find { it.switchId == switchId }
        assertNotNull(switchChange)
        listOf(1, 5, 2).forEach { jointNumber ->
            val joint = switchChange.changedJoints.find { change ->
                change.number == JointNumber(jointNumber) && change.locationTrackId == locationTrackId
            }

            assertNotNull(joint)
        }
    }

    private fun assertContainsSwitchJoint13Change(
        changes: List<SwitchChange>,
        switchId: DomainId<TrackLayoutSwitch>,
        locationTrackId: DomainId<LocationTrack>,
    ) {
        val switchChange = changes.find { it.switchId == switchId }
        assertNotNull(switchChange)

        listOf(1, 3).forEach { jointNumber ->
            val joint = switchChange.changedJoints.find { change ->
                change.number == JointNumber(jointNumber) && change.locationTrackId == locationTrackId
            }

            assertNotNull(joint)
        }
    }

    private fun getCalculatedChanges(
        locationTrackIds: List<IntId<LocationTrack>> = emptyList(),
        kmPostIds: List<IntId<TrackLayoutKmPost>> = emptyList(),
        referenceLineIds: List<IntId<ReferenceLine>> = emptyList(),
        switchIds: List<IntId<TrackLayoutSwitch>> = emptyList(),
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>> = emptyList(),
    ): CalculatedChanges {
        val publicationVersions = ValidationVersions(
            locationTracks = locationTrackDao.fetchPublicationVersions(locationTrackIds),
            kmPosts = layoutKmPostDao.fetchPublicationVersions(kmPostIds),
            referenceLines = referenceLineDao.fetchPublicationVersions(referenceLineIds),
            switches = switchDao.fetchPublicationVersions(switchIds),
            trackNumbers = layoutTrackNumberDao.fetchPublicationVersions(trackNumberIds),
        )

        return calculatedChangesService.getCalculatedChanges(publicationVersions)
    }
}
