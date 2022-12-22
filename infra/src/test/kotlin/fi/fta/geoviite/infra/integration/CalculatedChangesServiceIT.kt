package fi.fta.geoviite.infra.integration

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.math.IPoint
import fi.fta.geoviite.infra.math.Line
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.cos
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
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
) : ITTestBase() {

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
        val testData = insertTestData()

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(),
            switchIds = listOf(),
            startMoment = testData.changeTime.plus(-1, ChronoUnit.DAYS),
            endMoment = testData.changeTime,
        )
        assertTrue(changes.trackNumberChanges.isEmpty())
        assertTrue(changes.locationTracksChanges.isEmpty())
        assertTrue(changes.switchChanges.isEmpty())
    }

    @Test
    fun directChangesAreIncludedInCalculatedChanges() {
        val testData = insertTestData()

        val locationTrack1 = testData.locationTracksAndAlignments[0].first
        val locationTrack2 = testData.locationTracksAndAlignments[1].first

        val switch = testData.switches.first()
        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack1.id as IntId, locationTrack2.id as IntId),
            switchIds = listOf(switch.id as IntId),
            // using latest change time means that address changes
            // should not exist and therefore there should not be additional changes
            startMoment = testData.changeTime,
            endMoment = testData.changeTime,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId<LocationTrack>,
                isStartChanged = false,
                isEndChanged = false,
            )
        )
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack2.id as IntId<LocationTrack>,
                isStartChanged = false,
                isEndChanged = false,
            )
        )

        assertTrue(changes.switchChanges.all { it.switchId == switch.id && it.changedJoints.isEmpty() })
    }

    @Test
    fun locationTrackGeometryChangeGeneratesSwitchChanges() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        // Move alignment
        // - addresses should change
        // - switch change should be calculated
        val updatedChangeTime = moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point, _ -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack3.id as IntId),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updatedChangeTime,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
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
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack3.id as IntId
        )
    }

    @Test
    fun addingTopologyEndSwitchGeneratesSwitchChanges() {
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
        val baseStateMoment = removeTopologySwitchesFromLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            locationTrackService
        )

        // Set topology switch info
        val updatedStateMoment = addTopologyEndSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack1.id as IntId),
            switchIds = listOf(),
            startMoment = baseStateMoment,
            endMoment = updatedStateMoment,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(KmNumber(0)),
                isStartChanged = false,
                isEndChanged = true
            )
        )

        assertEquals(1, changes.switchChanges.size)
        changes.switchChanges.forEach { switchChange ->
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
    fun addingTopologyStartSwitchGeneratesSwitchChanges() {
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
        val baseStateMoment = removeTopologySwitchesFromLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            locationTrackService
        )

        // Set topology switch info
        val updatedStateMoment = addTopologyStartSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack1.id as IntId),
            switchIds = listOf(),
            startMoment = baseStateMoment,
            endMoment = updatedStateMoment,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(KmNumber(0)),
                isStartChanged = true,
                isEndChanged = false
            )
        )

        assertEquals(1, changes.switchChanges.size)
        changes.switchChanges.forEach { switchChange ->
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
                    locationTrackIndexB = 2
                )
            )
        )
        val (locationTrack1, alignment1) = testData.locationTracksAndAlignments[0]
        val switch = testData.switches[0]

        // Set topology switch info
        addTopologyStartSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(5), // Use non-presentation joint number
            locationTrackService = locationTrackService
        )
        val lastUpdateTime = addTopologyEndSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(3), // Use non-presentation joint number
            locationTrackService = locationTrackService
        )


        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack1.id as IntId),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = lastUpdateTime,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(),
                isStartChanged = false,
                isEndChanged = false
            )
        )
        assertTrue(changes.switchChanges.isEmpty())
    }


    @Test
    fun removingTopologyEndSwitchGeneratesSwitchChanges() {
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
        val baseStateMoment = addTopologyEndSwitchIntoLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            switch.id as IntId,
            JointNumber(1),
            locationTrackService = locationTrackService
        )

        // Then remove the topology switch info
        val updatedStateMoment = removeTopologySwitchesFromLocationTrackAndUpdate(
            locationTrack1,
            alignment1,
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(
                locationTrack1.id as IntId
            ),
            switchIds = listOf(),
            startMoment = baseStateMoment,
            endMoment = updatedStateMoment,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId,
                changedKmNumbers = setOf(),
                isStartChanged = false,
                isEndChanged = false
            )
        )

        assertEquals(1, changes.switchChanges.size)
        changes.switchChanges.forEach { switchChange ->
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
    fun allChangedLocationTracksExistInSwitchChange() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, alignment4) = testData.locationTracksAndAlignments[3]

        // Move alignment
        // - addresses should change
        // - switch change should be calculated
        moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point, _ -> point + 2.0 },
            locationTrackService = locationTrackService
        )
        val updateMoment = moveLocationTrackGeometryPointsAndUpdate(
            locationTrack4,
            alignment4,
            { point, _ -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack3.id as IntId, locationTrack4.id as IntId),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
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
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack4.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(7),
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )
        assertContainsSwitchJoint13Change(
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack4.id as IntId
        )

        assertContainsSwitchJoint152Change(
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack3.id as IntId
        )
    }

    @Test
    fun shouldNotGenerateSwitchChangesIfGeometryChangeIsNotInAddressRange() {
        val testData = insertTestData()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        // Move first 200m only (kilometer 0006)
        // - addresses should change
        // - there should be NO calculated switch changes
        val updateMoment = moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point, length -> if (length < 200) point + 2.0 else point },
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack3.id as IntId),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertTrue(changes.trackNumberChanges.isEmpty())
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack3.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(KmNumber(6)),
                isStartChanged = true,
                isEndChanged = false
            )
        )
        assertTrue(changes.switchChanges.isEmpty())
    }


    @Test
    @Disabled //Fixing this later
    fun referenceLineChangeGeneratesLocationTrackChanges() {
        val testData = insertTestData()
        val (locationTrack1, _) = testData.locationTracksAndAlignments[0]
        val (referenceLine, referenceLineAlignment) = testData.referenceLineAndAlignment

        // Move first 900m only (kilometer 5)
        // - addresses should change
        val updateMoment = moveReferenceLineGeometryPointsAndUpdate(
            referenceLine,
            referenceLineAlignment,
            { point, length -> if (length < 900) point + 2.0 else point },
            referenceLineService = referenceLineService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(referenceLine.trackNumberId),
            locationTrackIds = listOf(),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertContains(
            changes.trackNumberChanges, TrackNumberChange(
                trackNumberId = referenceLine.trackNumberId,
                changedKmNumbers = setOf(KmNumber(5)),
                isStartChanged = true,
                isEndChanged = false
            )
        )
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(KmNumber(5)),
                isStartChanged = true,
                isEndChanged = false
            )
        )
        assertTrue(changes.switchChanges.isEmpty())
    }


    @Test
    fun referenceLineChangeGeneratesLocationTrackChangesThatGenerateSwitchChanges() {
        val testData = insertTestData()
        val (locationTrack1, _) = testData.locationTracksAndAlignments[0]
        val (locationTrack3, _) = testData.locationTracksAndAlignments[2]
        val (locationTrack4, _) = testData.locationTracksAndAlignments[3]
        val (referenceLine, referenceLineAlignment) = testData.referenceLineAndAlignment

        // Move points from kilometers 6 and 7
        // - ref line addresses should change
        // - addresses of location tracks 1, 3 and 4 should be changed
        // - switch geom is changed
        val updateMoment = moveReferenceLineGeometryPointsAndUpdate(
            referenceLine,
            referenceLineAlignment,
            { point, length ->
                if (length >= 1000 && length < 2900)
                // make reference line wavy
                    point + Point(0.0, cos((length - 1000) / (2900 - 1000) * PI) * 5)
                else
                    point
            },
            referenceLineService = referenceLineService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(referenceLine.trackNumberId),
            locationTrackIds = listOf(),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertContains(
            changes.trackNumberChanges, TrackNumberChange(
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
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack1.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(6),
                ),
                isStartChanged = false,
                isEndChanged = true
            )
        )
        assertContains(
            changes.locationTracksChanges, LocationTrackChange(
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
            changes.locationTracksChanges, LocationTrackChange(
                locationTrackId = locationTrack4.id as IntId<LocationTrack>,
                changedKmNumbers = setOf(
                    KmNumber(7),
                ),
                isStartChanged = true,
                isEndChanged = true
            )
        )

        assertContainsSwitchJoint13Change(
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack4.id as IntId
        )

        assertContainsSwitchJoint152Change(
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack3.id as IntId
        )
    }

    @Test
    fun shouldCombineDirectSwitchChangesAndGeometryChanges() {
        val testData = insertTestData()
        val switch = testData.switches.first()
        val (locationTrack3, alignment3) = testData.locationTracksAndAlignments[2]

        moveSwitchPoints(
            switch,
            { point -> point + 0.5 },
            switchService,
        )

        val updateMoment = moveLocationTrackGeometryPointsAndUpdate(
            locationTrack3,
            alignment3,
            { point, _ -> point + 2.0 },
            locationTrackService = locationTrackService
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(locationTrack3.id as IntId<LocationTrack>),
            switchIds = listOf(),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertEquals(1, changes.switchChanges.size)
        assertContainsSwitchJoint152Change(
            changes.switchChanges,
            testData.switches.first().id as IntId,
            locationTrack3.id as IntId
        )
    }

    @Test
    fun switchLinkingGeneratesSwitchChangesOnly() {
        val testData = insertTestData()
        val switch = testData.switches.first()

        val (_, updateMoment) = moveSwitchPoints(
            switch,
            { point -> point + 0.5 },
            switchService,
        )

        val changes = calculatedChangesService.getCalculatedChangesBetween(
            trackNumberIds = listOf(),
            locationTrackIds = listOf(),
            switchIds = listOf(switch.id as IntId),
            startMoment = testData.changeTime,
            endMoment = updateMoment,
        )

        assertTrue(changes.switchChanges.all { it.switchId == switch.id && it.changedJoints.isEmpty() })
        assertTrue(changes.trackNumberChanges.isEmpty())
        assertTrue(changes.locationTracksChanges.isEmpty())
    }


    data class TestData(
        val locationTracksAndAlignments: List<Pair<LocationTrack, LayoutAlignment>>,
        val referenceLineAndAlignment: Pair<ReferenceLine, LayoutAlignment>,
        val kmPosts: List<TrackLayoutKmPost>,
        val switches: List<TrackLayoutSwitch>,
        val changeTime: Instant
    )

    data class SwitchData(
        val location: IPoint,
        val locationTrackIndexA: Int,
        val locationTrackIndexB: Int
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
                trackNumber(
                    TrackNumber("TEST TN $sequence")
                )
            )
        )
        val kmPosts = kmPostData.map { (kmNumber, location) ->
            layoutKmPostDao.fetch(
                layoutKmPostDao.insert(
                    kmPost(
                        trackNumberId = trackNumber.id as IntId,
                        km = kmNumber,
                        location = refPoint + location
                    )
                )
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
            )
        )

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
                        name = "TEST LocTr $sequence"
                    ).copy(
                        alignmentVersion = locationTrackGeometryVersion
                    )
                )
            )
            locationTrack to locationTrackGeometry
        }

        val switches = switchData.map { switch ->
            linkTestSwitch(
                refPoint + switch.location,
                locationTracksAndAlignments[switch.locationTrackIndexA],
                locationTracksAndAlignments[switch.locationTrackIndexB],
            )
        }

        val publishedLocationTracksAndAlignments = locationTracksAndAlignments.map { (locationTrack, _) ->
            val id = locationTrack.id as IntId
            val rowVersion = locationTrackDao.fetchDraftVersionOrThrow(id)
            val (edited, editedAlignment) = locationTrackService.getWithAlignment(rowVersion)
            if (edited.draft != null) {
                val publishedVersion = locationTrackService.publish(PublicationVersion(id, rowVersion))
                locationTrackService.getWithAlignment(publishedVersion)
            } else edited to editedAlignment
        }
        val publishedSwitches = switches.map { switch ->
            val id = switch.id as IntId
            val rowVersion = switchDao.fetchDraftVersionOrThrow(id)
            val edited = switchDao.fetch(rowVersion)
            if (edited.draft != null) {
                val publishedVersion = switchService.publish(PublicationVersion(id, rowVersion))
                switchDao.fetch(publishedVersion)
            }
            else edited
        }

        return TestData(
            locationTracksAndAlignments = publishedLocationTracksAndAlignments,
            referenceLineAndAlignment = referenceLine to referenceLineGeometry,
            kmPosts = kmPosts,
            switches = publishedSwitches,
            changeTime = listOf(
                locationTrackService.getChangeTime(),
                switchService.getChangeTime()
            ).maxOrNull() as Instant
        )
    }

    fun linkTestSwitch(
        switchLocation: IPoint,
        trackA: Pair<LocationTrack, LayoutAlignment>,
        trackB: Pair<LocationTrack, LayoutAlignment>,
    ): TrackLayoutSwitch {
        val switch = switchDao.fetch(
            switchDao.insert(
                switch(
                    joints = listOf()
                )
            )
        )

        val (locationTrackA, alignmentA) = trackA
        val (locationTrackB, alignmentB) = trackB
        val segIndexA = alignmentA.findClosestSegmentIndex(switchLocation) as Int
        val segIndexB = alignmentB.findClosestSegmentIndex(switchLocation) as Int

        switchLinkingService.saveSwitchLinking(
            SwitchLinkingParameters(
                layoutSwitchId = switch.id as IntId<TrackLayoutSwitch>,
                joints = listOf(
                    SwitchLinkingJoint(
                        jointNumber = JointNumber(1),
                        location = Point(alignmentA.segments[segIndexA].points.first()),
                        segments = listOf(
                            SwitchLinkingSegment(
                                locationTrackId = locationTrackA.id as IntId<LocationTrack>,
                                segmentIndex = segIndexA,
                                segmentM = 0.0
                            ),
                            SwitchLinkingSegment(
                                locationTrackId = locationTrackB.id as IntId<LocationTrack>,
                                segmentIndex = segIndexB,
                                segmentM = 0.0
                            ),
                        ),
                        locationAccuracy = null
                    ),
                    SwitchLinkingJoint(
                        jointNumber = JointNumber(5),
                        location = Point(alignmentA.segments[segIndexA].points.last()),
                        segments = listOf(
                            SwitchLinkingSegment(
                                locationTrackId = locationTrackA.id as IntId<LocationTrack>,
                                segmentIndex = segIndexA,
                                segmentM = 10.0
                            ),
                        ),
                        locationAccuracy = null
                    ),
                    SwitchLinkingJoint(
                        jointNumber = JointNumber(2),
                        location = Point(alignmentA.segments[segIndexA + 2].points.last()),
                        segments = listOf(
                            SwitchLinkingSegment(
                                locationTrackId = locationTrackA.id as IntId<LocationTrack>,
                                segmentIndex = segIndexA + 1,
                                segmentM = 10.0
                            ),
                        ),
                        locationAccuracy = null
                    ),
                    SwitchLinkingJoint(
                        jointNumber = JointNumber(3),
                        location = Point(alignmentB.segments[segIndexB + 1].points.last()),
                        segments = listOf(
                            SwitchLinkingSegment(
                                locationTrackId = locationTrackB.id as IntId<LocationTrack>,
                                segmentIndex = segIndexB + 1,
                                segmentM = 10.0
                            ),
                        ),
                        locationAccuracy = null
                    )
                ),
                geometrySwitchId = null,
                switchStructureId = switch.switchStructureId,
            )
        )
        return switch
    }

    private fun assertContainsSwitchJoint152Change(
        changes: List<SwitchChange>,
        switchId: IntId<TrackLayoutSwitch>,
        locationTrackId: IntId<LocationTrack>,
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
        switchId: IntId<TrackLayoutSwitch>,
        locationTrackId: IntId<LocationTrack>,
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

}
