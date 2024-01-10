package fi.fta.geoviite.infra.linking


import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.KkjTm35finTriangulationDao
import fi.fta.geoviite.infra.geography.TriangulationDirection
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.testdata.createSwitchAndAlignments
import fi.fta.geoviite.infra.ui.testdata.locationTrackAndAlignmentForGeometryAlignment
import fi.fta.geoviite.infra.util.LocalizationKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@ActiveProfiles("dev", "test")
@SpringBootTest
class SwitchLinkingServiceIT @Autowired constructor(
    private val switchLinkingService: SwitchLinkingService,
    private val switchDao: LayoutSwitchDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val locationTrackService: LocationTrackService,
    private val geometryDao: GeometryDao,
    private val switchStructureDao: SwitchStructureDao,
    private val kkjTm35FinTriangulationDao: KkjTm35finTriangulationDao,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchLibraryService: SwitchLibraryService,
    ) : DBTestBase() {

    lateinit var switchStructure: SwitchStructure
    lateinit var switchAlignment_1_5_2: SwitchAlignment

    @BeforeEach
    fun setup() {
        switchStructure = switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" }!!
        switchAlignment_1_5_2 =
            switchStructure.alignments.find { alignment -> alignment.jointNumbers.contains(JointNumber(5)) }
                ?: throw IllegalStateException("Invalid switch structure")
    }

    @BeforeEach
    fun cleanup() {
        deleteFromTables("layout", "switch_joint", "switch", "location_track")
    }

    @Test()
    fun getSuggestedSwitchesWorks() {
        switchLinkingService.getSuggestedSwitches(
            BoundingBox(
                x = Range(500000.0, 600000.0),
                y = Range(6900000.0, 7000000.0),
            )
        )
    }

    @Test
    fun updatingSwitchLinkingChangesSourceToGenerated() {
        val insertedSwitch = switchDao.fetch(
            switchDao.insert(switch(665)).rowVersion
        )
        val switchLinkingParameters =
            SwitchLinkingParameters(
                layoutSwitchId = insertedSwitch.id as IntId,
                joints = emptyList(),
                geometrySwitchId = null,
                switchStructureId = insertedSwitch.switchStructureId
            )
        val rowVersion = switchLinkingService.saveSwitchLinking(switchLinkingParameters).rowVersion
        val switch = switchDao.fetch(rowVersion)
        assertEquals(switch.source, GeometrySource.GENERATED)
    }

    @Test
    fun linkingExistingGeometrySwitchGetsSwitchAccuracyForJoints() {
        setupJointLocationAccuracyTest()
        val suggestedSwitch = switchLinkingService.getSuggestedSwitches(BoundingBox(
            x = Range(0.0, 100.0),
            y = Range(0.0, 100.0),
        ))[0]
        for (joint in suggestedSwitch.joints.map { j -> j.number }) {
            assertJointPointLocationAccuracy(suggestedSwitch, joint, LocationAccuracy.DIGITIZED_AERIAL_IMAGE)
        }
    }

    @Test
    fun linkingManualSwitchGetsGeometryCalculatedAccuracy() {
        val suggestedSwitchCreateParams = setupJointLocationAccuracyTest()
        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(suggestedSwitchCreateParams)!!

        for (joint in suggestedSwitch.joints.map { j -> j.number }) {
            assertJointPointLocationAccuracy(suggestedSwitch, joint, LocationAccuracy.GEOMETRY_CALCULATED)
        }
    }

    @Test
    fun `should match with first switch alignment match only`() {
        var startLength = 0.0
        val segments = (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength)
                .also { s -> startLength += s.length }
        }

        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (locationTrack, locationTrackAlignment) = locationTrackAndAlignment(trackNumberId, segments)
        val locationTrackId = locationTrackService.saveDraft(locationTrack, locationTrackAlignment)

        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(665)).rowVersion)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 9.5, y = 9.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].startM,
                    )
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(2),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].endM,
                    )
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(3),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].endM,
                    )
                ),
            ),
        )

        switchLinkingService.saveSwitchLinking(
            SwitchLinkingParameters(
                layoutSwitchId = insertedSwitch.id as IntId,
                joints = linkingJoints,
                geometrySwitchId = null,
                switchStructureId = insertedSwitch.switchStructureId
            )
        )

        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, locationTrackId.id)
        val joint12Segment = alignment.segments[1]

        assertEquals(JointNumber(1), joint12Segment.startJointNumber)
        assertEquals(JointNumber(2), joint12Segment.endJointNumber)

        assertTrue(alignment.segments.none {
            it.endJointNumber == JointNumber(3) || it.startJointNumber == JointNumber(3)
        })
    }

    @Test
    fun `should filter out switch matches that do not match with switch structure alignment`() {
        var startLength = 0.0
        val segments = (1..5).map { num ->
            val start = (num - 1).toDouble() * 10.0
            val end = start + 10.0
            segment(Point(start, start), Point(end, end), startM = startLength)
                .also { s -> startLength += s.length }
        }

        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (locationTrack, locationTrackAlignment) = locationTrackAndAlignment(trackNumberId, segments)
        val locationTrackId = locationTrackService.saveDraft(locationTrack, locationTrackAlignment)

        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(665)).rowVersion)

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 9.5, y = 9.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].startM,
                    )
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(5),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].endM,
                    )
                ),
            ),
            SwitchLinkingJoint(
                JointNumber(3),
                Point(x = 20.0, y = 20.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        m = segments[1].endM,
                    )
                ),
            ),
        )

        switchLinkingService.saveSwitchLinking(
            SwitchLinkingParameters(
                layoutSwitchId = insertedSwitch.id as IntId,
                joints = linkingJoints,
                geometrySwitchId = null,
                switchStructureId = insertedSwitch.switchStructureId
            )
        )

        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, locationTrackId.id)
        val joint12Segment = alignment.segments[1]

        assertEquals(JointNumber(1), joint12Segment.startJointNumber)
        assertEquals(JointNumber(3), joint12Segment.endJointNumber)

        assertTrue(alignment.segments.none {
            it.endJointNumber == JointNumber(5) || it.startJointNumber == JointNumber(5)
        })
    }

    private fun createAndLinkSwitch(
        seed: Int = 123768,
        linkedJoints: List<SwitchLinkingJoint>,
    ): TrackLayoutSwitch {
        return switch(
            seed = seed,
            joints = listOf(),
            stateCategory = LayoutStateCategory.EXISTING,
        )
            .let { switch -> switchDao.insert(switch).rowVersion }
            .let { switchRowVersion -> switchDao.fetch(switchRowVersion) }
            .let { storedSwitch ->
                switchLinkingService.saveSwitchLinking(SwitchLinkingParameters(
                    layoutSwitchId = storedSwitch.id as IntId,
                    joints = linkedJoints,
                    geometrySwitchId = null,
                    switchStructureId = storedSwitch.switchStructureId,
                ))
            }
            .let { switchDaoResponse -> switchDao.fetch(switchDaoResponse.rowVersion) }
    }

    private data class LocationTracksWithLinkedSwitch (
        val straightTrack: LocationTrack,
        val straightTrackAlignment: LayoutAlignment,

        val divertingTrack: LocationTrack,
        val divertingTrackAlignment: LayoutAlignment,

        val linkedSwitch: TrackLayoutSwitch,
    )

    private fun createLocationTracksWithLinkedSwitch(
        seed: Int = 12345,
    ): LocationTracksWithLinkedSwitch {
        val (straightTrack, straightAlignment) = createDraftLocationTrackFromLayoutSegments(
            listOf(
                segment(Point(0.0, 0.0), Point(20.0, 0.0)),  // Example: Beginning of the track
                segment(Point(20.0, 0.0), Point(40.0, 0.0)), // Example: first switch start joint (1) -> joint (5)
                segment(Point(40.0, 0.0), Point(60.0, 0.0)), // Example: first switch joint (5) -> end joint (2), will be split when linking the overlapping switch
                segment(Point(60.0, 0.0), Point(80.0, 0.0)), // Example: second switch joint (1) WITHOUT OVERLAP -> joint (5)
                segment(Point(80.0, 0.0), Point(100.0, 0.0)), // Example: joint (5) -> end joint (2)
                segment(Point(100.0, 0.0), Point(120.0, 0.0)), // Example:  Rest of the track
            )
        )

        val (divertingTrack, divertingAlignment) = createDraftLocationTrackFromLayoutSegments(
            listOf(segment(Point(20.0, 0.0), Point(60.0, 60.0)))
        )

        val switchJoints = listOf(

            // Continuing track
            SwitchLinkingJoint(
                JointNumber(1),
                Point(20.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(straightTrack.id, straightAlignment, 1),
                    switchLinkingAtStart(divertingTrack.id, divertingAlignment, 0),
                ),
            ),

            SwitchLinkingJoint(
                JointNumber(5),
                Point(40.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(straightTrack.id, straightAlignment, 2),
                ),
            ),

            SwitchLinkingJoint(
                JointNumber(2),
                Point(60.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(straightTrack.id, straightAlignment, 2),
                ),
            ),

            // Diverting track
            SwitchLinkingJoint(
                JointNumber(3),
                Point(100.0, 60.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(divertingTrack.id, divertingAlignment, 0),
                ),
            ),
        )

        val linkedSwitch = createAndLinkSwitch(
            seed = seed,
            linkedJoints = switchJoints,
        )

        val (linkedStraightTrack, linkedStraightTrackAlignment)
                = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, straightTrack.id as IntId)

        val (linkedDivertingTrack, linkedDivertingTrackAlignment)
            = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, divertingTrack.id as IntId)

        // No segment splits are excepted to have happened.
        assertEquals(straightAlignment.segments.size, linkedStraightTrackAlignment.segments.size)

        assertEquals(null, linkedStraightTrackAlignment.segments[0].switchId)
        assertEquals(null, linkedStraightTrackAlignment.segments[0].startJointNumber)
        assertEquals(null, linkedStraightTrackAlignment.segments[0].endJointNumber)

        assertEquals(linkedSwitch.id, linkedStraightTrackAlignment.segments[1].switchId)
        assertEquals(switchJoints[0].jointNumber, linkedStraightTrackAlignment.segments[1].startJointNumber)
        assertEquals(switchJoints[1].jointNumber, linkedStraightTrackAlignment.segments[1].endJointNumber)

        assertEquals(linkedSwitch.id, linkedStraightTrackAlignment.segments[2].switchId)
        assertEquals(switchJoints[1].jointNumber, linkedStraightTrackAlignment.segments[2].startJointNumber)
        assertEquals(switchJoints[2].jointNumber, linkedStraightTrackAlignment.segments[2].endJointNumber)

        assertEquals(null, linkedStraightTrackAlignment.segments[3].switchId)
        assertEquals(null, linkedStraightTrackAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedStraightTrackAlignment.segments[3].endJointNumber)

        // The diverting track segments should not have been split either.
        assertEquals(1, linkedDivertingTrackAlignment.segments.size)
        assertEquals(linkedSwitch.id, linkedDivertingTrackAlignment.segments[0].switchId)
        assertEquals(switchJoints[0].jointNumber, linkedDivertingTrackAlignment.segments[0].startJointNumber)
        assertEquals(switchJoints[3].jointNumber, linkedDivertingTrackAlignment.segments[0].endJointNumber)

        return LocationTracksWithLinkedSwitch(
            straightTrack = linkedStraightTrack,
            straightTrackAlignment = linkedStraightTrackAlignment,

            divertingTrack = linkedDivertingTrack,
            divertingTrackAlignment = linkedDivertingTrackAlignment,

            linkedSwitch = linkedSwitch,
        )
    }

    @Test
    fun `Switch linking slight overlap correction should not remove previous switch linking`() {
        val switchOverlapAmount = 4.99
        val testLocation = createLocationTracksWithLinkedSwitch()

        val (secondDiversionTrack, secondDiversionAlignment) = createDraftLocationTrackFromLayoutSegments(
            // The second diversion track's starting point and the related switch is purposefully built to overlap the first switch.
            listOf(segment(Point(60.0 - switchOverlapAmount, 0.0), Point(100.0, 100.0)))
        )

        val overlappingSwitchJoints = listOf(
            // Continuing track
            SwitchLinkingJoint(
                JointNumber(1),
                Point(60.0 - switchOverlapAmount, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = testLocation.straightTrack.id as IntId,
                        segmentIndex = 2,
                        m = testLocation.straightTrackAlignment.segments[2].endM - switchOverlapAmount,
                    ),

                    switchLinkingAtStart(secondDiversionTrack.id, secondDiversionAlignment, 0),
                ),
            ),

            SwitchLinkingJoint(
                JointNumber(5),
                Point(80.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtStart(testLocation.straightTrack.id, testLocation.straightTrackAlignment, 4),
                ),
            ),

            SwitchLinkingJoint(
                JointNumber(2),
                Point(100.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(testLocation.straightTrack.id, testLocation.straightTrackAlignment, 4),
                ),
            ),

            // Diverting track
            SwitchLinkingJoint(
                JointNumber(3),
                Point(100.0, 100.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    switchLinkingAtEnd(testLocation.divertingTrack.id, testLocation.divertingTrackAlignment, 0),
                ),
            ),
        )

        val newSwitch = createAndLinkSwitch(
            seed = 98765,
            linkedJoints = overlappingSwitchJoints,
        )

        val (_, overlapLinkedStraightAlignment)
            = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, testLocation.straightTrack.id as IntId)

        // The overlapping segment has not been split, the next segment is used.
        assertEquals(testLocation.straightTrackAlignment.segments.size, overlapLinkedStraightAlignment.segments.size)

        assertEquals(null, overlapLinkedStraightAlignment.segments[0].switchId)
        assertEquals(null, overlapLinkedStraightAlignment.segments[0].startJointNumber)
        assertEquals(null, overlapLinkedStraightAlignment.segments[0].endJointNumber)

        // Previously existing switch segments should stay the same.
        assertEquals(testLocation.linkedSwitch.id, overlapLinkedStraightAlignment.segments[1].switchId)
        assertEquals(testLocation.straightTrackAlignment.segments[1].startJointNumber, overlapLinkedStraightAlignment.segments[1].startJointNumber)
        assertEquals(testLocation.straightTrackAlignment.segments[1].endJointNumber, overlapLinkedStraightAlignment.segments[1].endJointNumber)

        assertEquals(testLocation.linkedSwitch.id, overlapLinkedStraightAlignment.segments[2].switchId)
        assertEquals(testLocation.straightTrackAlignment.segments[2].startJointNumber, overlapLinkedStraightAlignment.segments[2].startJointNumber)
        assertEquals(testLocation.straightTrackAlignment.segments[2].endJointNumber, overlapLinkedStraightAlignment.segments[2].endJointNumber)

        // New switch
        assertEquals(newSwitch.id, overlapLinkedStraightAlignment.segments[3].switchId)
        assertEquals(overlappingSwitchJoints[0].jointNumber, overlapLinkedStraightAlignment.segments[3].startJointNumber)
        assertEquals(overlappingSwitchJoints[1].jointNumber, overlapLinkedStraightAlignment.segments[3].endJointNumber)

        assertEquals(newSwitch.id, overlapLinkedStraightAlignment.segments[4].switchId)
        assertEquals(overlappingSwitchJoints[1].jointNumber, overlapLinkedStraightAlignment.segments[4].startJointNumber)
        assertEquals(overlappingSwitchJoints[2].jointNumber, overlapLinkedStraightAlignment.segments[4].endJointNumber)

        assertEquals(null, overlapLinkedStraightAlignment.segments[5].switchId)
        assertEquals(null, overlapLinkedStraightAlignment.segments[5].startJointNumber)
        assertEquals(null, overlapLinkedStraightAlignment.segments[5].endJointNumber)
    }

    @Test
    fun `Switch linking slight overlap correction should work with multiple overlapping segments`() {
        val (testLocationTrack, testAlignment) = createDraftLocationTrackFromLayoutSegments(
            listOf(
                segment(Point(0.0, 0.0), Point(10.0, 0.0)),
                segment(Point(10.0, 0.0), Point(20.0, 0.0)),
                segment(Point(20.0, 0.0), Point(21.0, 0.0)),
                segment(Point(21.0, 0.0), Point(22.0, 0.0)),
                segment(Point(22.0, 0.0), Point(23.0, 0.0)),
                segment(Point(23.0, 0.0), Point(24.0, 0.0)),
                segment(Point(24.0, 0.0), Point(25.0, 0.0)),
                segment(Point(25.0, 0.0), Point(40.0, 0.0)),
                segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                segment(Point(60.0, 0.0), Point(80.0, 0.0)),
                segment(Point(80.0, 0.0), Point(90.0, 0.0)),
            )
        )

        val existingSwitchJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(21.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 3))
            ),

            SwitchLinkingJoint(
                JointNumber(5),
                Point(40.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 8))
            ),

            SwitchLinkingJoint(
                JointNumber(2),
                Point(60.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 9))
            ),
        )

        val existingSwitch = createAndLinkSwitch(
            seed = 98765_1,
            linkedJoints = existingSwitchJoints
        )

        val overlappingSwitchJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(0.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 0))
            ),

            SwitchLinkingJoint(
                JointNumber(5),
                Point(10.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1))
            ),

            SwitchLinkingJoint(
                JointNumber(2),
                Point(25.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 7))
            ),
        )

        val linkedSwitchWithOverlap = createAndLinkSwitch(
            seed = 98765_2,
            linkedJoints = overlappingSwitchJoints
        )

        val (_, linkedTestAlignment)
                = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, testLocationTrack.id as IntId)

        assertEquals(testAlignment.segments.size, linkedTestAlignment.segments.size)

        assertEquals(existingSwitchJoints[0].jointNumber, linkedTestAlignment.segments[0].startJointNumber)
        assertEquals(existingSwitchJoints[1].jointNumber, linkedTestAlignment.segments[0].endJointNumber)

        assertEquals(existingSwitchJoints[1].jointNumber, linkedTestAlignment.segments[1].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[1].endJointNumber)

        assertEquals(null, linkedTestAlignment.segments[2].startJointNumber)
        assertEquals(existingSwitchJoints[2].jointNumber, linkedTestAlignment.segments[2].endJointNumber)

        (0..2).forEach { segmentIndex ->
            assertEquals(linkedSwitchWithOverlap.id, linkedTestAlignment.segments[segmentIndex].switchId)
        }

        (3..9).forEach { segmentIndex ->
            assertEquals(existingSwitch.id, linkedTestAlignment.segments[segmentIndex].switchId)
        }

        (4..7).forEach { segmentIndex ->
            assertEquals(null, linkedTestAlignment.segments[segmentIndex].startJointNumber)
            assertEquals(null, linkedTestAlignment.segments[segmentIndex].endJointNumber)
        }

        assertEquals(overlappingSwitchJoints[0].jointNumber, linkedTestAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[3].endJointNumber)

        assertEquals(overlappingSwitchJoints[1].jointNumber, linkedTestAlignment.segments[8].startJointNumber)
        assertEquals(overlappingSwitchJoints[2].jointNumber, linkedTestAlignment.segments[8].endJointNumber)

        assertEquals(overlappingSwitchJoints[2].jointNumber, linkedTestAlignment.segments[9].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[9].endJointNumber)

        assertEquals(null, linkedTestAlignment.segments[10].switchId)
        assertEquals(null, linkedTestAlignment.segments[10].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[10].endJointNumber)
    }

    @Test
    fun `Switch linking slight overlap correction should work regardless of the joint number order`() {
        val overlapAmount = 4.99

        listOf(
            Triple(JointNumber(1), JointNumber(5), JointNumber(2)),
            Triple(JointNumber(2), JointNumber(5), JointNumber(1)),
        ).forEachIndexed { index, (firstJointNumber, secondJointNumber, thirdJointNumber) ->

            val (testLocationTrack, testAlignment) = createDraftLocationTrackFromLayoutSegments(
                listOf(
                    segment(Point(0.0, 0.0), Point(20.0, 0.0)),
                    segment(Point(20.0, 0.0), Point(40.0, 0.0)),
                    segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                    segment(Point(60.0, 0.0), Point(80.0, 0.0)),
                    segment(Point(80.0, 0.0), Point(100.0, 0.0)),
                    segment(Point(100.0, 0.0), Point(120.0, 0.0)),
                )
            )

            val existingSwitchJoints = listOf(
                SwitchLinkingJoint(
                    JointNumber(1),
                    Point(20.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1))
                ),

                SwitchLinkingJoint(
                    JointNumber(5),
                    Point(40.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2))
                ),

                SwitchLinkingJoint(
                    JointNumber(2),
                    Point(60.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2))
                )
            )

            val existingLayoutSwitch = createAndLinkSwitch(
                seed = index,
                linkedJoints = existingSwitchJoints,
            )

            val linkedSwitchWithOverlap = createAndLinkSwitch(
                seed = 1000 + index,
                linkedJoints = listOf(
                    SwitchLinkingJoint(
                        firstJointNumber,
                        Point(60.0 - overlapAmount, 0.0),
                        LocationAccuracy.DESIGNED_GEOLOCATION,
                        segments = listOf(SwitchLinkingSegment(
                            locationTrackId = testLocationTrack.id as IntId,
                            segmentIndex = 2,
                            m = testAlignment.segments[2].endM - overlapAmount,
                        )),
                    ),

                    SwitchLinkingJoint(
                        secondJointNumber,
                        Point(80.0, 0.0),
                        LocationAccuracy.DESIGNED_GEOLOCATION,
                        segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 4))
                    ),

                    SwitchLinkingJoint(
                        thirdJointNumber,
                        Point(100.0, 0.0),
                        LocationAccuracy.DESIGNED_GEOLOCATION,
                        segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 5))
                    ),
                )
            )

            val (_, linkedTestAlignment)
                    = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, testLocationTrack.id as IntId)

            assertEquals(null, linkedTestAlignment.segments[0].switchId)
            assertEquals(null, linkedTestAlignment.segments[0].startJointNumber)
            assertEquals(null, linkedTestAlignment.segments[0].endJointNumber)

            (1..2).forEach { segmentIndex ->
                assertEquals(existingLayoutSwitch.id, linkedTestAlignment.segments[segmentIndex].switchId)
            }

            assertEquals(existingSwitchJoints[0].jointNumber, linkedTestAlignment.segments[1].startJointNumber)
            assertEquals(existingSwitchJoints[1].jointNumber, linkedTestAlignment.segments[1].endJointNumber)

            assertEquals(existingSwitchJoints[1].jointNumber, linkedTestAlignment.segments[2].startJointNumber)
            assertEquals(existingSwitchJoints[2].jointNumber, linkedTestAlignment.segments[2].endJointNumber)

            (3..5).forEach { segmentIndex ->
                assertEquals(linkedSwitchWithOverlap.id, linkedTestAlignment.segments[segmentIndex].switchId)
            }

            assertEquals(firstJointNumber, linkedTestAlignment.segments[3].startJointNumber)
            assertEquals(secondJointNumber, linkedTestAlignment.segments[3].endJointNumber)

            assertEquals(secondJointNumber, linkedTestAlignment.segments[4].startJointNumber)
            assertEquals(thirdJointNumber, linkedTestAlignment.segments[4].endJointNumber)

            assertEquals(thirdJointNumber, linkedTestAlignment.segments[5].startJointNumber)
            assertEquals(null, linkedTestAlignment.segments[5].endJointNumber)
        }
    }

    @Test
    fun `Switch linking slight overlap correction should override the original switch when the overlap correction limit is exceeded`() {
        val moreThanAllowedOverlap = 5.01

        val (testLocationTrack, testAlignment) = createDraftLocationTrackFromLayoutSegments(
            listOf(
                segment(Point(0.0, 0.0), Point(20.0, 0.0)),
                segment(Point(20.0, 0.0), Point(40.0, 0.0)),
                segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                segment(Point(60.0, 0.0), Point(80.0, 0.0)),
                segment(Point(80.0, 0.0), Point(100.0, 0.0)),
                segment(Point(100.0, 0.0), Point(120.0, 0.0)),
            )
        )

        val linkedSwitch = createAndLinkSwitch(
            seed = 98765_1,
            linkedJoints = listOf(
                SwitchLinkingJoint(
                    JointNumber(1),
                    Point(20.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1))
                ),

                SwitchLinkingJoint(
                    JointNumber(5),
                    Point(40.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2))
                ),

                SwitchLinkingJoint(
                    JointNumber(2),
                    Point(60.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    segments = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2))
                ),
            )
        )

        val (_, linkedTestAlignmentBeforeTryingOverlap)
                = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, testLocationTrack.id as IntId)

        (1..2).forEach { segmentIndex ->
            assertEquals(linkedSwitch.id, linkedTestAlignmentBeforeTryingOverlap.segments[segmentIndex].switchId)
        }

        val jointsForSwitchWithTooMuchOverlap = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(60.0 - moreThanAllowedOverlap, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(SwitchLinkingSegment(
                    locationTrackId = testLocationTrack.id as IntId,
                    segmentIndex = 2,
                    m = testAlignment.segments[2].endM - moreThanAllowedOverlap,
                )),
            ),

            SwitchLinkingJoint(
                JointNumber(5),
                Point(80.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 4))
            ),

            SwitchLinkingJoint(
                JointNumber(2),
                Point(100.0, 0.0),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 5))
            ),
        )

        val linkedSwitchWithTooMuchOverlap = createAndLinkSwitch(
            seed = 98765_2,
            linkedJoints = jointsForSwitchWithTooMuchOverlap,
        )

        val (_, linkedTestAlignment)
                = locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, testLocationTrack.id as IntId)

        // The original alignment is expected to have been split at the desired starting point of the new switch,
        // as it was not possible to snap it to a nearby segment without overlap.
        assertEquals(testAlignment.segments.size + 1, linkedTestAlignment.segments.size)

        (1..2).forEach { segmentIndex ->
            assertEquals(null, linkedTestAlignment.segments[segmentIndex].switchId)
        }

        (3..6).forEach { segmentIndex ->
            assertEquals(linkedSwitchWithTooMuchOverlap.id, linkedTestAlignment.segments[segmentIndex].switchId)
        }

        assertEquals(jointsForSwitchWithTooMuchOverlap[0].jointNumber, linkedTestAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[4].endJointNumber)

        assertEquals(null, linkedTestAlignment.segments[4].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[4].endJointNumber)

        assertEquals(jointsForSwitchWithTooMuchOverlap[1].jointNumber, linkedTestAlignment.segments[5].startJointNumber)
        assertEquals(jointsForSwitchWithTooMuchOverlap[2].jointNumber, linkedTestAlignment.segments[5].endJointNumber)

        assertEquals(jointsForSwitchWithTooMuchOverlap[2].jointNumber, linkedTestAlignment.segments[6].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[6].endJointNumber)
    }
    private fun shiftSegmentGeometry(source: LayoutSegment, switchId: DomainId<TrackLayoutSwitch>?, shiftVector: Point) = source.copy(
        geometry = SegmentGeometry(source.geometry.resolution,
            source.geometry.segmentPoints.map { sp -> sp.copy(x = sp.x + shiftVector.x, y = sp.y + shiftVector.y) }),
        switchId = switchId
    )

    private fun shiftSwitch(source: TrackLayoutSwitch, name: String, shiftVector: Point) = source.copy(
        id = StringId(),
        joints = source.joints.map { joint -> joint.copy(location = joint.location + shiftVector) },
        name = SwitchName(name)
    )

    @Test
    fun `validateRelinkingTrack relinks okay cases and gives validation errors about bad ones`() {
        val trackNumberId = getUnusedTrackNumberId()
        referenceLineDao.insert(
            referenceLine(
                trackNumberId,
                alignmentVersion = alignmentDao.insert(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
            )
        )

        // slightly silly way to make a through track with several switches on a track: Start with a template and
        // paste it over several times
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) = switchAndMatchingAlignments(trackNumberId, switchStructure)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val shift0 = Point(0.0, 0.0)
        val shift1 = templateThroughTrackSegments.last().segmentPoints.last().let { p -> Point(p.x, p.y) } + Point(10.0, 0.0)
        val shift2 = shift1 + shift1
        fun shiftTrack(template: List<LayoutSegment>, switchId: DomainId<TrackLayoutSwitch>?, shiftVector: Point) =
            template.map { segment -> shiftSegmentGeometry(segment, switchId, shiftVector)}

        // through track has three switches; first one is linked OK, second one is linkable but will cause a validation
        // error as as there are two branching tracks, third one can't be linked as there is no branching track
        val okSwitch = switchDao.insert(shiftSwitch(templateSwitch, "ok", shift0))
        val okButValidationErrorSwitch =
            switchDao.insert(shiftSwitch(templateSwitch, "ok but val", shift1))
        val unsaveableSwitch =
            switchDao.insert(shiftSwitch(templateSwitch, "unsaveable", shift2))
        val throughTrackAlignment = alignment(
            listOf(
                listOf(segment(Point(-10.0, 0.0), Point(0.0, 0.0))),
                shiftTrack(templateThroughTrackSegments, okSwitch.id, shift0),
                // spacer track segment
                shiftTrack(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))), null, shift1 - Point(10.0, 0.0)),
                shiftTrack(templateThroughTrackSegments, okButValidationErrorSwitch.id, shift1),
                // spacer track segment 2
                shiftTrack(listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))), null, shift2 - Point(10.0, 0.0)),
                shiftTrack(templateThroughTrackSegments, unsaveableSwitch.id, shift2),
            ).flatten())
        val throughTrack = locationTrackService.saveDraft(
            locationTrack(trackNumberId, name = "through track"), throughTrackAlignment
        )

        locationTrackService.saveDraft(
            locationTrack(trackNumberId, name = "ok branching track"),
            alignment(shiftTrack(templateBranchingTrackSegments, null, shift0))
        )
        // linkable, but will cause a validation error due to being wrongly marked as a duplicate
        locationTrackService.saveDraft(
            locationTrack(trackNumberId, name = "bad branching track", duplicateOf = throughTrack.id),
            alignment(shiftTrack(templateBranchingTrackSegments, null, shift1))
        )
        val validationResult = switchLinkingService.validateRelinkingTrack(throughTrack.id)
        assertEquals(
            listOf(
                SwitchRelinkingResult(
                    id = okSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(Point(0.0, 0.0), TrackMeter("0000+0000.000")),
                    validationErrors = listOf(),
                ), SwitchRelinkingResult(
                    id = okButValidationErrorSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(shift1, TrackMeter("0000+0044.430")),
                    validationErrors = listOf(
                        PublishValidationError(
                            type = PublishValidationErrorType.WARNING,
                            localizationKey = LocalizationKey("validation.layout.switch.track-linkage.switch-alignment-only-connected-to-duplicate"),
                            params = LocalizationParams(mapOf("locationTracks" to "1-3", "switch" to "ok but val"))
                        )
                    ),
                ), SwitchRelinkingResult(
                    id = unsaveableSwitch.id,
                    successfulSuggestion = null,
                    validationErrors = listOf(),
                )
            ), validationResult
        )
    }

    private fun createDraftLocationTrackFromLayoutSegments(layoutSegments: List<LayoutSegment>): Pair<LocationTrack, LayoutAlignment> {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id

        val (locationTrack, alignment) = locationTrackAndAlignment(trackNumberId, layoutSegments)
        val locationTrackId = locationTrackService.saveDraft(locationTrack, alignment).id

        return locationTrackService.getWithAlignmentOrThrow(PublishType.DRAFT, locationTrackId)
    }

    private fun setupJointLocationAccuracyTest(): SuggestedSwitchCreateParams {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (switch, switchAlignments) = createSwitchAndAlignments(
            "fooSwitch",
            switchStructure,
            0.01, // avoid plan1's bounding box becoming degenerate by slightly rotating the main track
            Point(50.0, 50.0),
            trackNumberId
        )

        val plan1 = makeAndSavePlan(
            trackNumberId, MeasurementMethod.DIGITIZED_AERIAL_IMAGE,
            switches = listOf(switch),
            alignments = listOf(switchAlignments[0])
        )

        val plan2 = makeAndSavePlan(
            trackNumberId, null,
            alignments = listOf(switchAlignments[1])
        )

        val trackNumberIds =
            (plan1.alignments + plan2.alignments).map { a ->
                val (locationTrack, alignment) = locationTrackAndAlignmentForGeometryAlignment(
                    trackNumberId,
                    a,
                    kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.KKJ_TO_TM35FIN),
                    kkjTm35FinTriangulationDao.fetchTriangulationNetwork(TriangulationDirection.TM35FIN_TO_KKJ)
                )
                locationTrackService.saveDraft(locationTrack, alignment)
            }
        val mainLocationTrackId = trackNumberIds[0].id

        return SuggestedSwitchCreateParams(
            LocationTrackEndpoint(
                locationTrackId = mainLocationTrackId,
                location = Point(50.0, 50.0),
                updateType = LocationTrackPointUpdateType.START_POINT
            ),
            switchStructure.id as IntId,
            listOf(
                SuggestedSwitchCreateParamsAlignmentMapping(
                    switchAlignment_1_5_2.id as StringId,
                    mainLocationTrackId,
                    true
                )
            )
        )
    }

    private fun makeAndSavePlan(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        measurementMethod: MeasurementMethod?,
        alignments: List<GeometryAlignment> = listOf(),
        switches: List<GeometrySwitch> = listOf(),
    ): GeometryPlan = geometryDao.fetchPlan(
        geometryDao.insertPlan(
            plan(
                trackNumberId = trackNumberId,
                alignments = alignments,
                switches = switches,
                measurementMethod = measurementMethod,
                srid = LAYOUT_SRID
            ), testFile(), null
        )
    )

    private fun assertJointPointLocationAccuracy(
        switch: SuggestedSwitch,
        jointNumber: JointNumber,
        locationAccuracy: LocationAccuracy?,
    ) = assertEquals(locationAccuracy, switch.joints.find { j -> j.number == jointNumber }!!.locationAccuracy)

}
