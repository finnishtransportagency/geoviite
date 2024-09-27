package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.linking.switches.matchFittedSwitchToTracks
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchJoint
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndAlignment
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchAndMatchingAlignments
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtEnd
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtStart
import fi.fta.geoviite.infra.ui.testdata.createSwitchAndAlignments
import fi.fta.geoviite.infra.ui.testdata.locationTrackAndAlignmentForGeometryAlignment
import fi.fta.geoviite.infra.ui.testdata.switchJoint
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test")
@SpringBootTest
class SwitchLinkingServiceIT
@Autowired
constructor(
    private val switchLinkingService: SwitchLinkingService,
    private val switchDao: LayoutSwitchDao,
    private val locationTrackService: LocationTrackService,
    private val geometryDao: GeometryDao,
    private val switchStructureDao: SwitchStructureDao,
    private val transformationService: CoordinateTransformationService,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackDao: LocationTrackDao,
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
        testDBService.clearLayoutTables()
    }

    @Test
    fun updatingSwitchLinkingChangesSourceToGenerated() {
        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(draft = false)).rowVersion)
        val fittedSwitch =
            FittedSwitch(
                joints = emptyList(),
                switchStructure = switchLibraryService.getSwitchStructure(insertedSwitch.switchStructureId),
            )
        val suggestedSwitch =
            matchFittedSwitchToTracks(
                fittedSwitch,
                switchLinkingService.findLocationTracksForMatchingSwitchToTracks(
                    LayoutBranch.main,
                    fittedSwitch,
                    insertedSwitch.id as IntId,
                ),
                insertedSwitch.id as IntId,
            )
        val rowVersion =
            switchLinkingService
                .saveSwitchLinking(LayoutBranch.main, suggestedSwitch, insertedSwitch.id as IntId)
                .rowVersion
        val switch = switchDao.fetch(rowVersion)
        assertEquals(switch.source, GeometrySource.GENERATED)
    }

    @Test
    fun linkingExistingGeometrySwitchGetsSwitchAccuracyForJoints() {
        val geometrySwitchId = setupJointLocationAccuracyTest()
        val suggestedSwitch =
            (switchLinkingService.getSuggestedSwitch(LayoutBranch.main, geometrySwitchId)
                    as GeometrySwitchSuggestionSuccess)
                .switch
        for (joint in suggestedSwitch.joints.map { j -> j.number }) {
            assertJointPointLocationAccuracy(suggestedSwitch, joint, LocationAccuracy.DIGITIZED_AERIAL_IMAGE)
        }
    }

    @Test
    fun linkingManualSwitchGetsGeometryCalculatedAccuracy() {
        setupJointLocationAccuracyTest()
        val layoutSwitchId = switchDao.insert(switch(structureId = switchStructure.id as IntId)).id
        val suggestedSwitch =
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(50.0, 50.0), layoutSwitchId)!!

        for (joint in suggestedSwitch.joints.map { j -> j.number }) {
            assertJointPointLocationAccuracy(suggestedSwitch, joint, LocationAccuracy.GEOMETRY_CALCULATED)
        }
    }

    @Test
    fun `should match with first switch alignment match only`() {
        var startLength = 0.0
        val segments =
            (1..5).map { num ->
                val start = (num - 1).toDouble() * 10.0
                val end = start + 10.0
                segment(Point(start, start), Point(end, end), startM = startLength).also { s ->
                    startLength += s.length
                }
            }

        val locationTrackId =
            mainDraftContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumberId = mainDraftContext.createLayoutTrackNumber().id,
                        segments = segments,
                    )
                )
                .id

        val insertedSwitch = mainOfficialContext.insertAndFetch(switch())

        val linkingJoints =
            listOf(
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(x = 9.5, y = 9.5),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].startM,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(x = 20.0, y = 20.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].endM,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(x = 20.0, y = 20.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].endM,
                            )
                        ),
                ),
            )

        val fittedSwitch =
            FittedSwitch(
                joints = linkingJoints,
                switchStructure = switchLibraryService.getSwitchStructure(insertedSwitch.switchStructureId),
            )
        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            matchFittedSwitchToTracks(
                fittedSwitch,
                switchLinkingService.findLocationTracksForMatchingSwitchToTracks(
                    LayoutBranch.main,
                    fittedSwitch,
                    insertedSwitch.id as IntId,
                ),
                insertedSwitch.id as IntId,
            ),
            insertedSwitch.id as IntId,
        )

        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, locationTrackId)
        val joint12Segment = alignment.segments[1]

        assertEquals(JointNumber(1), joint12Segment.startJointNumber)
        assertEquals(JointNumber(2), joint12Segment.endJointNumber)

        assertTrue(
            alignment.segments.none { it.endJointNumber == JointNumber(3) || it.startJointNumber == JointNumber(3) }
        )
    }

    @Test
    fun `should filter out switch matches that do not match with switch structure alignment`() {
        var startLength = 0.0
        val segments =
            (1..5).map { num ->
                val start = (num - 1).toDouble() * 10.0
                val end = start + 10.0
                segment(Point(start, start), Point(end, end), startM = startLength).also { s ->
                    startLength += s.length
                }
            }

        val locationTrackId =
            mainDraftContext
                .insert(
                    locationTrackAndAlignment(
                        trackNumberId = mainDraftContext.createLayoutTrackNumber().id,
                        segments = segments,
                    )
                )
                .id

        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(draft = false)).rowVersion)

        val linkingJoints =
            listOf(
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(x = 9.5, y = 9.5),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].startM,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(x = 20.0, y = 20.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].endM,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(x = 20.0, y = 20.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = locationTrackId,
                                segmentIndex = 1,
                                m = segments[1].endM,
                            )
                        ),
                ),
            )

        val fittedSwitch =
            FittedSwitch(
                joints = linkingJoints,
                switchStructure = switchLibraryService.getSwitchStructure(insertedSwitch.switchStructureId),
            )
        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            matchFittedSwitchToTracks(
                fittedSwitch,
                switchLinkingService.findLocationTracksForMatchingSwitchToTracks(
                    LayoutBranch.main,
                    fittedSwitch,
                    insertedSwitch.id as IntId,
                ),
                insertedSwitch.id as IntId,
            ),
            insertedSwitch.id as IntId,
        )

        val (_, alignment) = locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, locationTrackId)
        val joint12Segment = alignment.segments[1]

        assertEquals(JointNumber(1), joint12Segment.startJointNumber)
        assertEquals(JointNumber(3), joint12Segment.endJointNumber)

        assertTrue(
            alignment.segments.none { it.endJointNumber == JointNumber(5) || it.startJointNumber == JointNumber(5) }
        )
    }

    private fun createAndLinkSwitch(linkedJoints: List<FittedSwitchJoint>): TrackLayoutSwitch {
        return switch(joints = listOf(), stateCategory = LayoutStateCategory.EXISTING)
            .let(mainOfficialContext::insertAndFetch)
            .let { storedSwitch ->
                val fittedSwitch =
                    FittedSwitch(
                        switchStructure = switchLibraryService.getSwitchStructure(storedSwitch.switchStructureId),
                        joints = linkedJoints,
                    )
                switchLinkingService.saveSwitchLinking(
                    LayoutBranch.main,
                    matchFittedSwitchToTracks(
                        fittedSwitch,
                        switchLinkingService.findLocationTracksForMatchingSwitchToTracks(
                            LayoutBranch.main,
                            fittedSwitch,
                            storedSwitch.id as IntId,
                        ),
                        storedSwitch.id as IntId,
                    ),
                    storedSwitch.id as IntId,
                )
            }
            .let { switchDaoResponse -> switchDao.fetch(switchDaoResponse.rowVersion) }
    }

    private data class LocationTracksWithLinkedSwitch(
        val straightTrack: LocationTrack,
        val straightTrackAlignment: LayoutAlignment,
        val divertingTrack: LocationTrack,
        val divertingTrackAlignment: LayoutAlignment,
        val linkedSwitch: TrackLayoutSwitch,
    )

    private fun createLocationTracksWithLinkedSwitch(seed: Int = 12345): LocationTracksWithLinkedSwitch {
        val (straightTrack, straightAlignment) =
            createDraftLocationTrackFromLayoutSegments(
                listOf(
                    segment(Point(0.0, 0.0), Point(20.0, 0.0)), // Example: Beginning of the track
                    segment(Point(20.0, 0.0), Point(40.0, 0.0)), // Example: first switch start joint (1) -> joint (5)
                    segment(
                        Point(40.0, 0.0),
                        Point(60.0, 0.0),
                    ), // Example: first switch joint (5) -> end joint (2), will be split when
                    // linking the overlapping switch
                    segment(
                        Point(60.0, 0.0),
                        Point(80.0, 0.0),
                    ), // Example: second switch joint (1) WITHOUT OVERLAP -> joint (5)
                    segment(Point(80.0, 0.0), Point(100.0, 0.0)), // Example: joint (5) -> end joint (2)
                    segment(Point(100.0, 0.0), Point(120.0, 0.0)), // Example:  Rest of the track
                )
            )

        val (divertingTrack, divertingAlignment) =
            createDraftLocationTrackFromLayoutSegments(listOf(segment(Point(20.0, 0.0), Point(60.0, 60.0))))

        val switchJoints =
            listOf(

                // Continuing track
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(20.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtStart(straightTrack.id, straightAlignment, 1),
                            switchLinkingAtStart(divertingTrack.id, divertingAlignment, 0),
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(40.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(straightTrack.id, straightAlignment, 2)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(60.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtEnd(straightTrack.id, straightAlignment, 2)),
                ),

                // Diverting track
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(100.0, 60.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtEnd(divertingTrack.id, divertingAlignment, 0)),
                ),
            )

        val linkedSwitch = createAndLinkSwitch(linkedJoints = switchJoints)

        val (linkedStraightTrack, linkedStraightTrackAlignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, straightTrack.id as IntId)

        val (linkedDivertingTrack, linkedDivertingTrackAlignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, divertingTrack.id as IntId)

        // No segment splits are excepted to have happened.
        assertEquals(straightAlignment.segments.size, linkedStraightTrackAlignment.segments.size)

        assertEquals(null, linkedStraightTrackAlignment.segments[0].switchId)
        assertEquals(null, linkedStraightTrackAlignment.segments[0].startJointNumber)
        assertEquals(null, linkedStraightTrackAlignment.segments[0].endJointNumber)

        assertEquals(linkedSwitch.id, linkedStraightTrackAlignment.segments[1].switchId)
        assertEquals(switchJoints[0].number, linkedStraightTrackAlignment.segments[1].startJointNumber)
        assertEquals(switchJoints[1].number, linkedStraightTrackAlignment.segments[1].endJointNumber)

        assertEquals(linkedSwitch.id, linkedStraightTrackAlignment.segments[2].switchId)
        assertEquals(switchJoints[1].number, linkedStraightTrackAlignment.segments[2].startJointNumber)
        assertEquals(switchJoints[2].number, linkedStraightTrackAlignment.segments[2].endJointNumber)

        assertEquals(null, linkedStraightTrackAlignment.segments[3].switchId)
        assertEquals(null, linkedStraightTrackAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedStraightTrackAlignment.segments[3].endJointNumber)

        // The diverting track segments should not have been split either.
        assertEquals(1, linkedDivertingTrackAlignment.segments.size)
        assertEquals(linkedSwitch.id, linkedDivertingTrackAlignment.segments[0].switchId)
        assertEquals(switchJoints[0].number, linkedDivertingTrackAlignment.segments[0].startJointNumber)
        assertEquals(switchJoints[3].number, linkedDivertingTrackAlignment.segments[0].endJointNumber)

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

        val (secondDiversionTrack, secondDiversionAlignment) =
            createDraftLocationTrackFromLayoutSegments(
                // The second diversion track's starting point and the related switch is
                // purposefully built to overlap the first switch.
                listOf(segment(Point(60.0 - switchOverlapAmount, 0.0), Point(100.0, 100.0)))
            )

        val overlappingSwitchJoints =
            listOf(
                // Continuing track
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(60.0 - switchOverlapAmount, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = testLocation.straightTrack.id as IntId,
                                segmentIndex = 2,
                                m = testLocation.straightTrackAlignment.segments[2].endM - switchOverlapAmount,
                            ),
                            switchLinkingAtStart(secondDiversionTrack.id, secondDiversionAlignment, 0),
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(80.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtStart(testLocation.straightTrack.id, testLocation.straightTrackAlignment, 4)
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(100.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtEnd(testLocation.straightTrack.id, testLocation.straightTrackAlignment, 4)
                        ),
                ),

                // Diverting track
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(100.0, 100.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtEnd(testLocation.divertingTrack.id, testLocation.divertingTrackAlignment, 0)
                        ),
                ),
            )

        val newSwitch = createAndLinkSwitch(linkedJoints = overlappingSwitchJoints)

        val (_, overlapLinkedStraightAlignment) =
            locationTrackService.getWithAlignmentOrThrow(
                MainLayoutContext.draft,
                testLocation.straightTrack.id as IntId,
            )

        // The overlapping segment has not been split, the next segment is used.
        assertEquals(testLocation.straightTrackAlignment.segments.size, overlapLinkedStraightAlignment.segments.size)

        assertEquals(null, overlapLinkedStraightAlignment.segments[0].switchId)
        assertEquals(null, overlapLinkedStraightAlignment.segments[0].startJointNumber)
        assertEquals(null, overlapLinkedStraightAlignment.segments[0].endJointNumber)

        // Previously existing switch segments should stay the same.
        assertEquals(testLocation.linkedSwitch.id, overlapLinkedStraightAlignment.segments[1].switchId)
        assertEquals(
            testLocation.straightTrackAlignment.segments[1].startJointNumber,
            overlapLinkedStraightAlignment.segments[1].startJointNumber,
        )
        assertEquals(
            testLocation.straightTrackAlignment.segments[1].endJointNumber,
            overlapLinkedStraightAlignment.segments[1].endJointNumber,
        )

        assertEquals(testLocation.linkedSwitch.id, overlapLinkedStraightAlignment.segments[2].switchId)
        assertEquals(
            testLocation.straightTrackAlignment.segments[2].startJointNumber,
            overlapLinkedStraightAlignment.segments[2].startJointNumber,
        )
        assertEquals(
            testLocation.straightTrackAlignment.segments[2].endJointNumber,
            overlapLinkedStraightAlignment.segments[2].endJointNumber,
        )

        // New switch
        assertEquals(newSwitch.id, overlapLinkedStraightAlignment.segments[3].switchId)
        assertEquals(overlappingSwitchJoints[0].number, overlapLinkedStraightAlignment.segments[3].startJointNumber)
        assertEquals(overlappingSwitchJoints[1].number, overlapLinkedStraightAlignment.segments[3].endJointNumber)

        assertEquals(newSwitch.id, overlapLinkedStraightAlignment.segments[4].switchId)
        assertEquals(overlappingSwitchJoints[1].number, overlapLinkedStraightAlignment.segments[4].startJointNumber)
        assertEquals(overlappingSwitchJoints[2].number, overlapLinkedStraightAlignment.segments[4].endJointNumber)

        assertEquals(null, overlapLinkedStraightAlignment.segments[5].switchId)
        assertEquals(null, overlapLinkedStraightAlignment.segments[5].startJointNumber)
        assertEquals(null, overlapLinkedStraightAlignment.segments[5].endJointNumber)
    }

    @Test
    fun `Switch linking slight overlap correction should work with multiple overlapping segments`() {
        val (testLocationTrack, testAlignment) =
            createDraftLocationTrackFromLayoutSegments(
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

        val existingSwitchJoints =
            listOf(
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(21.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 3)),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(40.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 8)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(60.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 9)),
                ),
            )

        val existingSwitch = createAndLinkSwitch(linkedJoints = existingSwitchJoints)

        val overlappingSwitchJoints =
            listOf(
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(0.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 0)),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(10.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(25.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 7)),
                ),
            )

        val linkedSwitchWithOverlap = createAndLinkSwitch(linkedJoints = overlappingSwitchJoints)

        val (_, linkedTestAlignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

        assertEquals(testAlignment.segments.size, linkedTestAlignment.segments.size)

        assertEquals(existingSwitchJoints[0].number, linkedTestAlignment.segments[0].startJointNumber)
        assertEquals(existingSwitchJoints[1].number, linkedTestAlignment.segments[0].endJointNumber)

        assertEquals(existingSwitchJoints[1].number, linkedTestAlignment.segments[1].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[1].endJointNumber)

        assertEquals(null, linkedTestAlignment.segments[2].startJointNumber)
        assertEquals(existingSwitchJoints[2].number, linkedTestAlignment.segments[2].endJointNumber)

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

        assertEquals(overlappingSwitchJoints[0].number, linkedTestAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[3].endJointNumber)

        assertEquals(overlappingSwitchJoints[1].number, linkedTestAlignment.segments[8].startJointNumber)
        assertEquals(overlappingSwitchJoints[2].number, linkedTestAlignment.segments[8].endJointNumber)

        assertEquals(overlappingSwitchJoints[2].number, linkedTestAlignment.segments[9].startJointNumber)
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
            )
            .forEachIndexed { index, (firstJointNumber, secondJointNumber, thirdJointNumber) ->
                val (testLocationTrack, testAlignment) =
                    createDraftLocationTrackFromLayoutSegments(
                        listOf(
                            segment(Point(0.0, 0.0), Point(20.0, 0.0)),
                            segment(Point(20.0, 0.0), Point(40.0, 0.0)),
                            segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                            segment(Point(60.0, 0.0), Point(80.0, 0.0)),
                            segment(Point(80.0, 0.0), Point(100.0, 0.0)),
                            segment(Point(100.0, 0.0), Point(120.0, 0.0)),
                        )
                    )

                val existingSwitchJoints =
                    listOf(
                        FittedSwitchJoint(
                            JointNumber(1),
                            Point(20.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(5),
                            Point(40.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(2),
                            Point(60.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2)),
                        ),
                    )

                val existingLayoutSwitch = createAndLinkSwitch(linkedJoints = existingSwitchJoints)

                val linkedSwitchWithOverlap =
                    createAndLinkSwitch(
                        linkedJoints =
                            listOf(
                                FittedSwitchJoint(
                                    firstJointNumber,
                                    Point(60.0 - overlapAmount, 0.0),
                                    LocationAccuracy.DESIGNED_GEOLOCATION,
                                    matches =
                                        listOf(
                                            suggestedSwitchJointMatch(
                                                locationTrackId = testLocationTrack.id as IntId,
                                                segmentIndex = 2,
                                                m = testAlignment.segments[2].endM - overlapAmount,
                                            )
                                        ),
                                ),
                                FittedSwitchJoint(
                                    secondJointNumber,
                                    Point(80.0, 0.0),
                                    LocationAccuracy.DESIGNED_GEOLOCATION,
                                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 4)),
                                ),
                                FittedSwitchJoint(
                                    thirdJointNumber,
                                    Point(100.0, 0.0),
                                    LocationAccuracy.DESIGNED_GEOLOCATION,
                                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 5)),
                                ),
                            )
                    )

                val (_, linkedTestAlignment) =
                    locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

                assertEquals(null, linkedTestAlignment.segments[0].switchId)
                assertEquals(null, linkedTestAlignment.segments[0].startJointNumber)
                assertEquals(null, linkedTestAlignment.segments[0].endJointNumber)

                (1..2).forEach { segmentIndex ->
                    assertEquals(existingLayoutSwitch.id, linkedTestAlignment.segments[segmentIndex].switchId)
                }

                assertEquals(existingSwitchJoints[0].number, linkedTestAlignment.segments[1].startJointNumber)
                assertEquals(existingSwitchJoints[1].number, linkedTestAlignment.segments[1].endJointNumber)

                assertEquals(existingSwitchJoints[1].number, linkedTestAlignment.segments[2].startJointNumber)
                assertEquals(existingSwitchJoints[2].number, linkedTestAlignment.segments[2].endJointNumber)

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

        val (testLocationTrack, testAlignment) =
            createDraftLocationTrackFromLayoutSegments(
                listOf(
                    segment(Point(0.0, 0.0), Point(20.0, 0.0)),
                    segment(Point(20.0, 0.0), Point(40.0, 0.0)),
                    segment(Point(40.0, 0.0), Point(60.0, 0.0)),
                    segment(Point(60.0, 0.0), Point(80.0, 0.0)),
                    segment(Point(80.0, 0.0), Point(100.0, 0.0)),
                    segment(Point(100.0, 0.0), Point(120.0, 0.0)),
                )
            )

        val linkedSwitch =
            createAndLinkSwitch(
                linkedJoints =
                    listOf(
                        FittedSwitchJoint(
                            JointNumber(1),
                            Point(20.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(5),
                            Point(40.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(2),
                            Point(60.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2)),
                        ),
                    )
            )

        val (_, linkedTestAlignmentBeforeTryingOverlap) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

        (1..2).forEach { segmentIndex ->
            assertEquals(linkedSwitch.id, linkedTestAlignmentBeforeTryingOverlap.segments[segmentIndex].switchId)
        }

        val jointsForSwitchWithTooMuchOverlap =
            listOf(
                FittedSwitchJoint(
                    JointNumber(1),
                    Point(60.0 - moreThanAllowedOverlap, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            suggestedSwitchJointMatch(
                                locationTrackId = testLocationTrack.id as IntId,
                                segmentIndex = 2,
                                m = testAlignment.segments[2].endM - moreThanAllowedOverlap,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(80.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 4)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(100.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 5)),
                ),
            )

        val linkedSwitchWithTooMuchOverlap = createAndLinkSwitch(linkedJoints = jointsForSwitchWithTooMuchOverlap)

        val (_, linkedTestAlignment) =
            locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

        // The original alignment is expected to have been split at the desired starting point of
        // the new switch,
        // as it was not possible to snap it to a nearby segment without overlap.
        assertEquals(testAlignment.segments.size + 1, linkedTestAlignment.segments.size)

        (1..2).forEach { segmentIndex -> assertEquals(null, linkedTestAlignment.segments[segmentIndex].switchId) }

        (3..6).forEach { segmentIndex ->
            assertEquals(linkedSwitchWithTooMuchOverlap.id, linkedTestAlignment.segments[segmentIndex].switchId)
        }

        assertEquals(jointsForSwitchWithTooMuchOverlap[0].number, linkedTestAlignment.segments[3].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[4].endJointNumber)

        assertEquals(null, linkedTestAlignment.segments[4].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[4].endJointNumber)

        assertEquals(jointsForSwitchWithTooMuchOverlap[1].number, linkedTestAlignment.segments[5].startJointNumber)
        assertEquals(jointsForSwitchWithTooMuchOverlap[2].number, linkedTestAlignment.segments[5].endJointNumber)

        assertEquals(jointsForSwitchWithTooMuchOverlap[2].number, linkedTestAlignment.segments[6].startJointNumber)
        assertEquals(null, linkedTestAlignment.segments[6].endJointNumber)
    }

    private fun shiftSegmentGeometry(
        source: LayoutSegment,
        switchId: IntId<TrackLayoutSwitch>?,
        shiftVector: Point,
    ): LayoutSegment =
        source.copy(
            geometry =
                SegmentGeometry(
                    source.geometry.resolution,
                    source.geometry.segmentPoints.map { sp ->
                        sp.copy(x = sp.x + shiftVector.x, y = sp.y + shiftVector.y)
                    },
                ),
            switchId = switchId,
            startJointNumber = if (switchId == null) null else JointNumber(1),
            endJointNumber = null,
        )

    private fun shiftSwitch(source: TrackLayoutSwitch, name: String, shiftVector: Point) =
        source.copy(
            contextData = LayoutContextData.newOfficial(LayoutBranch.main),
            joints = source.joints.map { joint -> joint.copy(location = joint.location + shiftVector) },
            name = SwitchName(name),
        )

    private fun shiftTrack(template: List<LayoutSegment>, switchId: IntId<TrackLayoutSwitch>?, shiftVector: Point) =
        template.map { segment -> shiftSegmentGeometry(segment, switchId, shiftVector) }

    @Test
    fun `validateRelinkingTrack relinks okay cases and gives validation errors about bad ones`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id

        // slightly silly way to make a through track with several switches on a track: Start with a
        // template and
        // paste it over several times
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val shift0 = Point(0.0, 0.0)
        val shift1 =
            templateThroughTrackSegments.last().segmentPoints.last().let { p -> Point(p.x, p.y) } + Point(10.0, 0.0)
        val shift2 = shift1 + shift1

        // through track has three switches; first one is linked OK, second one is linkable but will
        // cause a validation
        // error as the only branching track is a duplicate, third one can't be linked as there is
        // no branching track
        val okSwitch = switchDao.insert(shiftSwitch(templateSwitch, "ok", shift0))
        val okButValidationErrorSwitch = switchDao.insert(shiftSwitch(templateSwitch, "ok but val", shift1))
        val unsaveableSwitch = switchDao.insert(shiftSwitch(templateSwitch, "unsaveable", shift2))

        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                alignment(
                    pasteTrackSegmentsWithSpacers(
                            listOf(
                                listOf(segment(Point(0.0, 0.0), Point(1.0, 0.0))),
                                setSwitchId(templateThroughTrackSegments, okSwitch.id),
                                setSwitchId(templateThroughTrackSegments, okButValidationErrorSwitch.id),
                                setSwitchId(templateThroughTrackSegments, unsaveableSwitch.id),
                            ),
                            Point(10.0, 0.0),
                            Point(-11.0, 0.0),
                        )
                        .flatten()
                ),
            )

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "ok branching track", draft = true),
            alignment(shiftTrack(templateBranchingTrackSegments, null, shift0)),
        )
        // linkable, but will cause a validation error due to being wrongly marked as a duplicate
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "bad branching track", duplicateOf = throughTrack.id, draft = true),
            alignment(shiftTrack(templateBranchingTrackSegments, null, shift1)),
        )
        val validationResult = switchLinkingService.validateRelinkingTrack(LayoutBranch.main, throughTrack.id)
        assertEqualsRounded(
            listOf(
                SwitchRelinkingValidationResult(
                    id = okSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(Point(0.0, 0.0), TrackMeter("0000+0000.000")),
                    validationIssues = listOf(),
                ),
                SwitchRelinkingValidationResult(
                    id = okButValidationErrorSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(shift1, TrackMeter("0000+0044.430")),
                    validationIssues =
                        listOf(
                            LayoutValidationIssue(
                                type = LayoutValidationIssueType.WARNING,
                                localizationKey =
                                    LocalizationKey(
                                        "validation.layout.switch.track-linkage.switch-alignment-only-connected-to-duplicate"
                                    ),
                                params =
                                    LocalizationParams(
                                        mapOf("locationTracks" to "1-3 (bad branching track)", "switch" to "ok but val")
                                    ),
                            )
                        ),
                ),
                SwitchRelinkingValidationResult(
                    id = unsaveableSwitch.id,
                    successfulSuggestion = null,
                    validationIssues =
                        listOf(
                            LayoutValidationIssue(
                                type = LayoutValidationIssueType.ERROR,
                                localizationKey =
                                    LocalizationKey("validation.layout.switch.track-linkage.relinking-failed"),
                                params = LocalizationParams(mapOf("switch" to "unsaveable")),
                            )
                        ),
                ),
            ),
            validationResult,
        )
    }

    @Test
    fun `relinkTrack and validateRelinkingTrack find nearby switches`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id

        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId, switchStructure, draft = false)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val track152 =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, name = "track152", draft = true),
                    alignment(
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))) +
                            shiftTrack(templateThroughTrackSegments, null, Point(10.0, 0.0))
                    ),
                )
                .id
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track13", draft = true),
            alignment(shiftTrack(templateBranchingTrackSegments, null, Point(10.0, 0.0))),
        )
        val okSwitch = switchDao.insert(shiftSwitch(templateSwitch, "ok", Point(10.0, 0.0)))

        val validationResult = switchLinkingService.validateRelinkingTrack(LayoutBranch.main, track152)
        val relinkingResult = switchLinkingService.relinkTrack(LayoutBranch.main, track152)
        assertEquals(
            listOf(
                SwitchRelinkingValidationResult(
                    okSwitch.id,
                    SwitchRelinkingSuggestion(Point(10.0, 0.0), TrackMeter("0000+0010.000")),
                    listOf(),
                )
            ),
            validationResult,
        )
        assertEquals(
            listOf(TrackSwitchRelinkingResult(okSwitch.id, TrackSwitchRelinkingResultType.RELINKED)),
            relinkingResult,
        )
    }

    @Test
    fun `validateRelinkingTrack warns about switches getting unlinked`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id

        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = true)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val basePoint = Point(10.0, 0.0)
        val somewhereElse = Point(100.0, 100.0)

        // we'll be linking topoTrack, which currently has a link to a switch that's actually
        // somewhere completely
        // different, so once it gets relinked, it'll have no match on topoTrack (it's immaterial
        // that the link happens
        // to be topological; the important thing is the misplaced switch)
        val okSwitch = switchDao.insert(shiftSwitch(templateSwitch, "ok", basePoint))
        val switchSomewhereElse = switchDao.insert(shiftSwitch(templateSwitch, "somewhere else", somewhereElse))
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track152", draft = true),
            alignment(shiftTrack(templateThroughTrackSegments, okSwitch.id, basePoint)),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track13", draft = true),
            alignment(shiftTrack(templateBranchingTrackSegments, okSwitch.id, basePoint)),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track152", draft = true),
            alignment(shiftTrack(templateThroughTrackSegments, null, somewhereElse)),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track13", draft = true),
            alignment(shiftTrack(templateBranchingTrackSegments, okSwitch.id, somewhereElse)),
        )

        val topoTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "topoTrack",
                    topologyEndSwitch = TopologyLocationTrackSwitch(okSwitch.id, JointNumber(1)),
                    draft = true,
                ),
                alignment(
                    segment(Point(0.0, 0.0), Point(5.0, 0.0), switchId = switchSomewhereElse.id),
                    segment(Point(5.0, 0.0), basePoint),
                ),
            )
        val validationResult = switchLinkingService.validateRelinkingTrack(LayoutBranch.main, topoTrack.id)
        assertEqualsRounded(
            listOf(
                SwitchRelinkingValidationResult(
                    id = okSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(basePoint, TrackMeter("0000+0010.000")),
                    validationIssues =
                        listOf(
                            LayoutValidationIssue(
                                LayoutValidationIssueType.ERROR,
                                localizationKey =
                                    LocalizationKey("validation.layout.split.track-links-missing-after-relinking"),
                                params = LocalizationParams(mapOf("switchName" to "ok", "sourceName" to "topoTrack")),
                            )
                        ),
                ),
                SwitchRelinkingValidationResult(
                    id = switchSomewhereElse.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(somewhereElse, TrackMeter("0000+0100.000")),
                    validationIssues =
                        listOf(
                            LayoutValidationIssue(
                                LayoutValidationIssueType.WARNING,
                                localizationKey =
                                    LocalizationKey("validation.layout.switch.track-linkage.front-joint-not-connected"),
                                params = LocalizationParams(mapOf("switch" to "somewhere else")),
                            ),
                            LayoutValidationIssue(
                                LayoutValidationIssueType.ERROR,
                                localizationKey =
                                    LocalizationKey("validation.layout.split.track-links-missing-after-relinking"),
                                params =
                                    LocalizationParams(
                                        mapOf("switchName" to "somewhere else", "sourceName" to "topoTrack")
                                    ),
                            ),
                        ),
                ),
            ),
            validationResult,
        )
    }

    @Test
    fun `re-linking switch cleans up previous references consistently`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val branchingTrackSegments = templateTrackSections[1].second.segments
        val switch =
            switchDao.insert(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                alignment(
                    pasteTrackSegmentsWithSpacers(
                            listOf(
                                setSwitchId(templateThroughTrackSegments, switch.id),
                                setSwitchId(templateThroughTrackSegments, null),
                            ),
                            Point(100.0, 0.0),
                        )
                        .flatten()
                ),
            )
        val originallyLinkedBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "originally linked branching track", draft = true),
                alignment(setSwitchId(branchingTrackSegments, switch.id)),
            )
        val newBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "new branching track", draft = true),
                alignment(shiftTrack(branchingTrackSegments, switch.id, Point(134.321, 0.0))),
            )
        val suggestedSwitch =
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(134.321, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)
        assertTrackDraftVersionSwitchLinks(originallyLinkedBranchingTrack.id, null, null, listOf(0.0..34.3 to null))
        assertTrackDraftVersionSwitchLinks(newBranchingTrack.id, null, null, listOf(0.0..34.3 to switch.id))
        assertTrackDraftVersionSwitchLinks(
            throughTrack.id,
            null,
            null,
            listOf(0.0..134.4 to null, 134.5..168.8 to switch.id, 168.9..268.86 to null),
        )
    }

    @Test
    fun `null is suggested when no switch is applicable`() {
        assertNull(
            switchLinkingService.getSuggestedSwitch(
                LayoutBranch.main,
                Point(123.0, 456.0),
                switchDao.insert(switch(draft = false)).id,
            )
        )
    }

    @Test
    fun `re-linking switch cleans up topological connections`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "RR54-4x1:9" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val templateOneTwoTrackSegments = templateTrackSections[0].second.segments
        val templateFourThreeTrackSegments = templateTrackSections[1].second.segments
        val oneFive = templateOneTwoTrackSegments[0]
        val fiveTwo = templateOneTwoTrackSegments[1]
        val switch =
            switchDao.insert(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))

        val oneFiveTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "one-five with topo link",
                    topologyEndSwitch = TopologyLocationTrackSwitch(switch.id, JointNumber(5)),
                    draft = true,
                ),
                alignment(setSwitchId(listOf(oneFive), null)),
            )

        val fiveTwoTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "five-two with topo link",
                    topologyStartSwitch = TopologyLocationTrackSwitch(switch.id, JointNumber(5)),
                    draft = true,
                ),
                alignment(setSwitchId(listOf(fiveTwo), null)),
            )
        val threeFourTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "three-four", draft = true),
                alignment(setSwitchId(templateFourThreeTrackSegments, switch.id)),
            )

        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)

        assertTrackDraftVersionSwitchLinks(oneFiveTrack.id, null, null, listOf(0.0..5.2 to switch.id))
        assertTrackDraftVersionSwitchLinks(fiveTwoTrack.id, null, null, listOf(0.0..5.2 to switch.id))
        assertTrackDraftVersionSwitchLinks(threeFourTrack.id, null, null, listOf(0.0..10.4 to switch.id))
    }

    @Test
    fun `mislinked track with wrong alignment link gets replaced with topology link`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val switch =
            switchDao.insert(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        val shift =
            templateThroughTrackSegments.last().segmentEnd.toPoint() -
                templateThroughTrackSegments.first().segmentStart.toPoint()
        val fullShift = shift + Point(100.0, 0.0)

        val throughTrackStart =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track start", draft = true),
                alignment(setSwitchId(templateThroughTrackSegments + listOf(segment(shift, fullShift)), switch.id)),
            )
        val throughTrackSwitchAndEnd =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track switch and end", draft = true),
                alignment(shiftTrack(templateThroughTrackSegments, switch.id, fullShift)),
            )
        val originallyLinkedBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "originally linked branching track", draft = true),
                alignment(setSwitchId(templateBranchingTrackSegments, switch.id)),
            )
        val newBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "new branching track", draft = true),
                alignment(shiftTrack(templateBranchingTrackSegments, switch.id, fullShift)),
            )

        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, fullShift, switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)

        assertTrackDraftVersionSwitchLinks(throughTrackStart.id, null, switch.id, listOf(0.0..134.4 to null))

        assertTrackDraftVersionSwitchLinks(throughTrackSwitchAndEnd.id, null, null, listOf(0.0..34.4 to switch.id))

        assertTrackDraftVersionSwitchLinks(originallyLinkedBranchingTrack.id, null, null, listOf(0.0..34.3 to null))

        assertTrackDraftVersionSwitchLinks(newBranchingTrack.id, null, null, listOf(0.0..34.3 to switch.id))
    }

    @Test
    fun `relinking moves mislinked topo link to correct switch despite confuser branching track, and does not pointlessly update alignments or tracks`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val templateThroughTrackSegments = templateTrackSections[0].second.segments
        val templateBranchingTrackSegments = templateTrackSections[1].second.segments
        val switch =
            switchDao.insert(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        val someOtherSwitch = switchDao.insert(switch(draft = false))

        val shift =
            templateThroughTrackSegments.last().segmentEnd.toPoint() -
                templateThroughTrackSegments.first().segmentStart.toPoint()
        val fullShift = shift + Point(100.0, 0.0)

        val throughTrackStart =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "through track start",
                    topologyEndSwitch = TopologyLocationTrackSwitch(someOtherSwitch.id, JointNumber(1)),
                    draft = true,
                ),
                alignment(setSwitchId(templateThroughTrackSegments + listOf(segment(shift, fullShift)), null)),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "through track switch and end", draft = true),
            alignment(shiftTrack(templateThroughTrackSegments, switch.id, fullShift)),
        )
        // confuser branching track is misleadingly placed starting at the origin, while the switch
        // is at x=134.43
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "branching track", draft = true),
            alignment(setSwitchId(templateBranchingTrackSegments, switch.id)),
        )
        val uninvolvedTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "uninvolved track", draft = true),
                alignment(shiftTrack(templateThroughTrackSegments, null, fullShift - Point(1.0, 1.0))),
            )
        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, fullShift, switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)

        assertTrackDraftVersionSwitchLinks(throughTrackStart.id, null, switch.id, listOf(0.0..134.4 to null))
        assertEquals(
            locationTrackDao.fetch(throughTrackStart.rowVersion).alignmentVersion!!,
            locationTrackDao.getOrThrow(MainLayoutContext.draft, throughTrackStart.id).alignmentVersion!!,
        )
        assertEquals(
            uninvolvedTrack.rowVersion,
            locationTrackDao.fetchVersion(MainLayoutContext.draft, uninvolvedTrack.id),
        )
    }

    @Test
    fun `relinking removes misplaced topological switch link`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "RR54-4x1:9" }!!
        val (templateSwitch, templateTrackSections) =
            switchAndMatchingAlignments(trackNumberId = trackNumberId, structure = switchStructure, draft = false)
        val switch =
            switchDao.insert(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        templateTrackSections.forEach { (_, a) ->
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                alignment(setSwitchId(a.segments, null)),
            )
        }
        val otherLocationTrackWithTopoSwitchLink =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "unrelated mislinked track",
                    topologyEndSwitch = TopologyLocationTrackSwitch(switch.id, JointNumber(1)),
                    draft = true,
                ),
                alignment(segment(Point(456.7, 345.5), Point(457.8, 346.9))),
            )
        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)
        assertTrackDraftVersionSwitchLinks(
            otherLocationTrackWithTopoSwitchLink.id,
            null,
            null,
            listOf(0.0..1.7 to null),
        )
    }

    @Test
    fun `nearby track end not within bounding box of switch joints still gets topologically connected when linking single switch`() {
        val (_, branchingTrackContinuation, switchId) = setupForLinkingTopoLinkToTrackOutsideSwitchJointBoundingBox()
        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switchId)!!,
            switchId,
        )
        val expected = TopologyLocationTrackSwitch(switchId, JointNumber(3))
        val actual =
            locationTrackDao
                .fetch(locationTrackDao.fetchVersion(MainLayoutContext.draft, branchingTrackContinuation)!!)
                .topologyStartSwitch
        assertEquals(expected, actual)
    }

    @Test
    fun `nearby track end not within bounding box of switch joints still gets topologically connected when linking track`() {
        val (throughTrack, branchingTrackContinuation, switchId) =
            setupForLinkingTopoLinkToTrackOutsideSwitchJointBoundingBox()
        switchLinkingService.relinkTrack(LayoutBranch.main, throughTrack)
        val expected = TopologyLocationTrackSwitch(switchId, JointNumber(3))
        val actual =
            locationTrackDao
                .fetch(locationTrackDao.fetchVersion(MainLayoutContext.draft, branchingTrackContinuation)!!)
                .topologyStartSwitch
        assertEquals(expected, actual)
    }

    private fun setupForLinkingTopoLinkToTrackOutsideSwitchJointBoundingBox():
        Triple<IntId<LocationTrack>, IntId<LocationTrack>, IntId<TrackLayoutSwitch>> {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        // switch structure YV60_300_1_9_O's rightmost joints are at x-coords:
        // - 34.430 (through track)
        // - 34.321 (branching track)
        // so with the branching track's continuation starting at 35, it doesn't fall within the
        // switch's bounding box
        val switchStructureId =
            switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!.id as IntId
        val switchId =
            switchDao
                .insert(
                    switch(
                        structureId = switchStructureId,
                        joints = listOf(switchJoint(Point(0.0, 0.0))),
                        draft = false,
                    )
                )
                .id
        val throughTrackId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, name = "through track", draft = true),
                    alignment(
                        segment(Point(0.0, 0.0), Point(40.0, 0.0))
                            .copy(switchId = switchId, startJointNumber = JointNumber(1))
                    ),
                )
                .id
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "branching track start", draft = true),
            alignment(segment(Point(0.0, 0.0), Point(34.9, -2.0))),
        )
        val branchingTrackContinuationId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, name = "branching track continuation", draft = true),
                    alignment(segment(Point(35.0, -2.0), Point(50.0, -3.0))),
                )
                .id
        return Triple(throughTrackId, branchingTrackContinuationId, switchId)
    }

    private fun assertTrackDraftVersionSwitchLinks(
        trackId: IntId<LocationTrack>,
        topologyStartSwitchId: IntId<TrackLayoutSwitch>?,
        topologyEndSwitchId: IntId<TrackLayoutSwitch>?,
        segmentSwitchesByMRange: List<Pair<ClosedRange<Double>, IntId<TrackLayoutSwitch>?>>,
    ) {
        val track = locationTrackService.get(MainLayoutContext.draft, trackId)!!
        val (_, alignment) = locationTrackService.getWithAlignment(track.version!!)
        assertEquals(topologyStartSwitchId, track.topologyStartSwitch?.switchId)
        assertEquals(topologyEndSwitchId, track.topologyEndSwitch?.switchId)
        assertEquals(segmentSwitchesByMRange.last().first.endInclusive, alignment.end!!.m, 0.1)
        segmentSwitchesByMRange.forEach { (range, switchId) ->
            val rangeStartSegmentIndex = alignment.getSegmentIndexAtM(range.start)
            val rangeEndSegmentIndex = alignment.getSegmentIndexAtM(range.endInclusive)
            assertEquals(
                range.start,
                alignment.segments[rangeStartSegmentIndex].startM,
                0.1,
                "segment range starts at given m-value",
            )
            assertEquals(
                range.endInclusive,
                alignment.segments[rangeEndSegmentIndex].endM,
                0.1,
                "segment range ends at given m-value",
            )
            (rangeStartSegmentIndex..rangeEndSegmentIndex).forEach { i ->
                assertEquals(
                    switchId,
                    alignment.segments[i].switchId,
                    "switch at segment index $i (asserted m-range ${range.start}..${
                        range.endInclusive
                    }, segment m-range ${alignment.segments[i].startM}..${alignment.segments[i].endM}",
                )
            }
        }
    }

    private fun pasteTrackSegmentsWithSpacers(
        segmentss: List<List<LayoutSegment>>,
        spacerVector: Point,
        alignmentShift: Point = Point(0.0, 0.0),
    ): List<List<LayoutSegment>> {
        val acc = mutableListOf<List<LayoutSegment>>()
        var shift = alignmentShift
        segmentss.forEach { segments ->
            acc +=
                segments.map { segment ->
                    val segmentStart = Point(0.0, 0.0) - segment.segmentStart.toPoint()
                    val s =
                        segment.copy(
                            geometry =
                                segment.geometry.copy(
                                    segmentPoints =
                                        segment.geometry.segmentPoints.map { point ->
                                            point.copy(
                                                x = point.x + shift.x + segmentStart.x,
                                                y = point.y + shift.y + segmentStart.y,
                                            )
                                        }
                                )
                        )
                    shift += s.segmentEnd.toPoint() - s.segmentStart.toPoint()
                    s
                }

            acc += listOf(segment(shift, shift + spacerVector))
            shift += spacerVector
        }
        return acc.map { ss -> ss.map { s -> s.copy(id = StringId(), geometry = s.geometry.copy(id = StringId())) } }
    }

    private fun setSwitchId(segments: List<LayoutSegment>, switchId: IntId<TrackLayoutSwitch>?) =
        segments.map { segment ->
            segment.copy(
                switchId = switchId,
                startJointNumber = if (switchId == null) null else JointNumber(1),
                endJointNumber = null,
            )
        }

    private fun createDraftLocationTrackFromLayoutSegments(
        layoutSegments: List<LayoutSegment>
    ): Pair<LocationTrack, LayoutAlignment> {
        val (locationTrack, alignment) =
            locationTrackAndAlignment(
                trackNumberId = mainDraftContext.createLayoutTrackNumber().id,
                segments = layoutSegments,
                draft = true,
            )
        val locationTrackId = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, alignment).id
        return locationTrackService.getWithAlignmentOrThrow(MainLayoutContext.draft, locationTrackId)
    }

    private fun setupJointLocationAccuracyTest(): IntId<GeometrySwitch> {
        val trackNumber = mainOfficialContext.createAndFetchLayoutTrackNumber()
        val (switch, switchAlignments) =
            createSwitchAndAlignments(
                "fooSwitch",
                switchStructure,
                0.01, // avoid plan1's bounding box becoming degenerate by slightly rotating the
                // main track
                Point(50.0, 50.0),
            )

        val plan1 =
            makeAndSavePlan(
                trackNumber.number,
                MeasurementMethod.DIGITIZED_AERIAL_IMAGE,
                switches = listOf(switch),
                alignments = listOf(switchAlignments[0]),
            )

        val plan2 =
            makeAndSavePlan(trackNumber.number, measurementMethod = null, alignments = listOf(switchAlignments[1]))

        (plan1.alignments + plan2.alignments).forEach { geometryAlignment ->
            val (locationTrack, alignment) =
                locationTrackAndAlignmentForGeometryAlignment(
                    trackNumber.id as IntId,
                    geometryAlignment,
                    transformationService.getTransformation(LAYOUT_SRID, LAYOUT_SRID),
                    draft = true,
                )
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack, alignment)
        }

        return plan1.switches[0].id as IntId
    }

    private fun makeAndSavePlan(
        trackNumber: TrackNumber,
        measurementMethod: MeasurementMethod?,
        alignments: List<GeometryAlignment> = listOf(),
        switches: List<GeometrySwitch> = listOf(),
    ): GeometryPlan =
        geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan(
                    trackNumber = trackNumber,
                    alignments = alignments,
                    switches = switches,
                    measurementMethod = measurementMethod,
                    srid = LAYOUT_SRID,
                ),
                testFile(),
                null,
            )
        )

    private fun assertJointPointLocationAccuracy(
        switch: SuggestedSwitch,
        jointNumber: JointNumber,
        locationAccuracy: LocationAccuracy?,
    ) = assertEquals(locationAccuracy, switch.joints.find { j -> j.number == jointNumber }!!.locationAccuracy)

    private fun assertEqualsRounded(
        expected: List<SwitchRelinkingValidationResult>,
        actual: List<SwitchRelinkingValidationResult>,
    ) = assertEquals(roundRelinkingResult(expected), roundRelinkingResult(actual))

    private fun roundRelinkingResult(r: List<SwitchRelinkingValidationResult>): List<SwitchRelinkingValidationResult> =
        r.map { one ->
            one.copy(
                successfulSuggestion =
                    one.successfulSuggestion?.copy(location = one.successfulSuggestion!!.location.round(1).toPoint())
            )
        }
}

fun suggestedSwitchJointMatch(
    locationTrackId: IntId<LocationTrack>,
    segmentIndex: Int,
    m: Double,
): FittedSwitchJointMatch =
    FittedSwitchJointMatch(
        locationTrackId,
        segmentIndex,
        m,
        SwitchJoint(JointNumber(1), Point(1.0, 2.0)),
        SuggestedSwitchJointMatchType.START,
        0.1,
        0.1,
        null,
    )
