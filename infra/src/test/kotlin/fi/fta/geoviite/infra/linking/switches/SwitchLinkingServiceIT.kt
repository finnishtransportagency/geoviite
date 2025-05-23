package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.LinkingFailureException
import fi.fta.geoviite.infra.geography.CoordinateTransformationService
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.geometry.plan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResult
import fi.fta.geoviite.infra.linking.TrackSwitchRelinkingResultType
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureJoint
import fi.fta.geoviite.infra.switchLibrary.data.YV60_300_1_9_O
import fi.fta.geoviite.infra.tracklayout.GeometrySource
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TopologyLocationTrackSwitch
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.edge
import fi.fta.geoviite.infra.tracklayout.locationTrack
import fi.fta.geoviite.infra.tracklayout.locationTrackAndGeometry
import fi.fta.geoviite.infra.tracklayout.segment
import fi.fta.geoviite.infra.tracklayout.switch
import fi.fta.geoviite.infra.tracklayout.switchAndMatchingAlignments
import fi.fta.geoviite.infra.tracklayout.switchLinkYV
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtEnd
import fi.fta.geoviite.infra.tracklayout.switchLinkingAtStart
import fi.fta.geoviite.infra.tracklayout.trackGeometry
import fi.fta.geoviite.infra.tracklayout.trackGeometryOfSegments
import fi.fta.geoviite.infra.ui.testdata.createSwitchAndAlignments
import fi.fta.geoviite.infra.ui.testdata.locationTrackAndAlignmentForGeometryAlignment
import fi.fta.geoviite.infra.ui.testdata.switchJoint
import org.junit.jupiter.api.Assertions.assertEquals
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
class SwitchLinkingServiceIT
@Autowired
constructor(
    private val switchLinkingService: SwitchLinkingService,
    private val switchTrackRelinkingValidationService: SwitchTrackRelinkingValidationService,
    private val switchDao: LayoutSwitchDao,
    private val switchService: LayoutSwitchService,
    private val locationTrackService: LocationTrackService,
    private val geometryDao: GeometryDao,
    private val switchStructureDao: SwitchStructureDao,
    private val transformationService: CoordinateTransformationService,
    private val switchLibraryService: SwitchLibraryService,
    private val locationTrackDao: LocationTrackDao,
) : DBTestBase() {

    @Autowired private lateinit var layoutAlignmentDao: LayoutAlignmentDao
    lateinit var switchStructure: SwitchStructure
    lateinit var switchAlignment_1_5_2: SwitchStructureAlignment

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
        val insertedSwitch = switchDao.fetch(switchDao.save(switch(draft = false)))
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
            switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, insertedSwitch.id as IntId)
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
        val layoutSwitchId = switchDao.save(switch(structureId = switchStructure.id)).id
        val suggestedSwitch =
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(50.0, 50.0), layoutSwitchId)!!

        for (joint in suggestedSwitch.joints.map { j -> j.number }) {
            assertJointPointLocationAccuracy(suggestedSwitch, joint, LocationAccuracy.GEOMETRY_CALCULATED)
        }
    }

    @Test
    fun `should filter out switch matches that do not match with switch structure alignment`() {
        val segments =
            (1..5).map { num ->
                val start = (num - 1).toDouble() * 10.0
                val end = start + 10.0
                segment(Point(start, start), Point(end, end))
            }

        val (initTrack, initAlignment) =
            locationTrackAndGeometry(trackNumberId = mainDraftContext.createLayoutTrackNumber().id, segments = segments)
        val locationTrackId = mainDraftContext.save(initTrack, initAlignment).id

        val insertedSwitch = switchDao.fetch(switchDao.save(switch(draft = false)))

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
                                m = initAlignment.segmentMValues[1].min,
                                1,
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
                                m = initAlignment.segmentMValues[1].max,
                                5,
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
                                m = initAlignment.segmentMValues[1].max,
                                3,
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

        val (_, alignment) = locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, locationTrackId)
        val joint12Edge = alignment.edges[1]

        assertEquals(JointNumber(1), joint12Edge.startNode.switchIn?.jointNumber)
        assertEquals(JointNumber(3), joint12Edge.endNode.switchIn?.jointNumber)

        assertTrue(
            alignment.edges.none {
                it.endNode.switchIn?.jointNumber == JointNumber(5) ||
                    it.startNode.switchIn?.jointNumber == JointNumber(5)
            }
        )
    }

    private fun createAndLinkSwitch(linkedJoints: List<FittedSwitchJoint>): LayoutSwitch {
        return switch(joints = listOf(), stateCategory = LayoutStateCategory.EXISTING)
            .let(mainOfficialContext::saveAndFetch)
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
            .let { switchDaoResponse -> switchDao.fetch(switchDaoResponse) }
    }

    private data class LocationTracksWithLinkedSwitch(
        val straightTrack: LocationTrack,
        val straightTrackAlignment: LocationTrackGeometry,
        val divertingTrack: LocationTrack,
        val divertingTrackAlignment: LocationTrackGeometry,
        val linkedSwitch: LayoutSwitch,
    )

    private fun createLocationTracksWithLinkedSwitch(): LocationTracksWithLinkedSwitch {
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
                            switchLinkingAtStart(straightTrack.id, straightAlignment, 1, 1),
                            switchLinkingAtStart(divertingTrack.id, divertingAlignment, 0, 1),
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(40.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(straightTrack.id, straightAlignment, 2, 5)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(60.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtEnd(straightTrack.id, straightAlignment, 2, 2)),
                ),

                // Diverting track
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(100.0, 60.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtEnd(divertingTrack.id, divertingAlignment, 0, 3)),
                ),
            )

        val linkedSwitch = createAndLinkSwitch(linkedJoints = switchJoints)

        val (linkedStraightTrack, linkedStraightTrackAlignment) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, straightTrack.id as IntId)

        val (linkedDivertingTrack, linkedDivertingTrackAlignment) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, divertingTrack.id as IntId)

        val switchStructure = switchLibraryService.getSwitchStructure(linkedSwitch.switchStructureId)
        assertEquals(
            listOf(
                SwitchLink(linkedSwitch.id as IntId, switchJoints[0].number, switchStructure),
                SwitchLink(linkedSwitch.id, switchJoints[1].number, switchStructure),
                SwitchLink(linkedSwitch.id, switchJoints[2].number, switchStructure),
            ),
            linkedStraightTrackAlignment.nodes.map { it.portA }.filter { it is SwitchLink },
        )

        assertEquals(
            listOf(
                SwitchLink(linkedSwitch.id as IntId, switchJoints[0].number, switchStructure),
                SwitchLink(linkedSwitch.id, switchJoints[3].number, switchStructure),
            ),
            linkedDivertingTrackAlignment.nodes.map { it.portA }.filter { it is SwitchLink },
        )

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
        val switchOverlapAmount = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE - 0.01
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
                                m = testLocation.straightTrackAlignment.segmentMValues[2].max - switchOverlapAmount,
                                jointNumber = 1,
                            ),
                            switchLinkingAtStart(secondDiversionTrack.id, secondDiversionAlignment, 0, 1),
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(80.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtStart(
                                testLocation.straightTrack.id,
                                testLocation.straightTrackAlignment,
                                4,
                                5,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(100.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtEnd(testLocation.straightTrack.id, testLocation.straightTrackAlignment, 4, 2)
                        ),
                ),

                // Diverting track
                FittedSwitchJoint(
                    JointNumber(3),
                    Point(100.0, 100.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches =
                        listOf(
                            switchLinkingAtEnd(
                                testLocation.divertingTrack.id,
                                testLocation.divertingTrackAlignment,
                                0,
                                3,
                            )
                        ),
                ),
            )

        val newSwitch = createAndLinkSwitch(linkedJoints = overlappingSwitchJoints)

        val (_, overlapLinkedStraightAlignment) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, testLocation.straightTrack.id as IntId)

        val testLocationLinkedSwitchStructure =
            switchLibraryService.getSwitchStructure(testLocation.linkedSwitch.switchStructureId)
        val newSwitchStructure = switchLibraryService.getSwitchStructure(newSwitch.switchStructureId)

        assertEquals(testLocation.straightTrackAlignment.edges.take(2), overlapLinkedStraightAlignment.edges.take(2))
        assertEquals(
            testLocation.straightTrackAlignment.edges[2].startNode,
            overlapLinkedStraightAlignment.edges[2].startNode,
        )

        assertEquals(
            listOf(
                SwitchLink(testLocation.linkedSwitch.id as IntId, JointNumber(1), testLocationLinkedSwitchStructure),
                SwitchLink(testLocation.linkedSwitch.id, JointNumber(5), testLocationLinkedSwitchStructure),
                SwitchLink(testLocation.linkedSwitch.id, JointNumber(2), testLocationLinkedSwitchStructure),
                SwitchLink(newSwitch.id as IntId, JointNumber(1), newSwitchStructure),
                SwitchLink(newSwitch.id, JointNumber(5), newSwitchStructure),
                SwitchLink(newSwitch.id, JointNumber(2), newSwitchStructure),
            ),
            overlapLinkedStraightAlignment.nodes
                .flatMap { listOfNotNull(it.portA, it.portB) }
                .filter { it is SwitchLink },
        )
    }

    @Test
    fun `Switch linking slight overlap correction should work regardless of the joint number order`() {
        val overlapAmount = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE - 0.01

        listOf(
                Triple(JointNumber(1), JointNumber(5), JointNumber(2)) to RelativeDirection.Along,
                Triple(JointNumber(2), JointNumber(5), JointNumber(1)) to RelativeDirection.Against,
            )
            .forEach { (jointNumbers, matchDirection) ->
                val (firstJointNumber, secondJointNumber, thirdJointNumber) = jointNumbers
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
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1, 1)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(5),
                            Point(40.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2, 5)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(2),
                            Point(60.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2, 2)),
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
                                                m = testAlignment.segmentMValues[2].max - overlapAmount,
                                                jointNumber = firstJointNumber.intValue,
                                                matchDirection = matchDirection,
                                            )
                                        ),
                                ),
                                FittedSwitchJoint(
                                    secondJointNumber,
                                    Point(80.0, 0.0),
                                    LocationAccuracy.DESIGNED_GEOLOCATION,
                                    matches =
                                        listOf(
                                            switchLinkingAtStart(
                                                    testLocationTrack.id,
                                                    testAlignment,
                                                    4,
                                                    secondJointNumber.intValue,
                                                )
                                                .copy(direction = matchDirection)
                                        ),
                                ),
                                FittedSwitchJoint(
                                    thirdJointNumber,
                                    Point(100.0, 0.0),
                                    LocationAccuracy.DESIGNED_GEOLOCATION,
                                    matches =
                                        listOf(
                                            switchLinkingAtStart(
                                                    testLocationTrack.id,
                                                    testAlignment,
                                                    5,
                                                    thirdJointNumber.intValue,
                                                )
                                                .copy(direction = matchDirection)
                                        ),
                                ),
                            )
                    )

                val (_, linkedTestAlignment) =
                    locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

                assertEquals(null, linkedTestAlignment.startSwitchLink)

                assertEquals(
                    listOf(
                        null,
                        existingSwitchJoints[0].number,
                        existingSwitchJoints[1].number,
                        firstJointNumber,
                        secondJointNumber,
                        null,
                    ),
                    linkedTestAlignment.edges.map { (it.startNode.innerPort as? SwitchLink)?.jointNumber },
                )

                assertEquals(
                    listOf(
                        null,
                        existingLayoutSwitch.id,
                        existingLayoutSwitch.id,
                        linkedSwitchWithOverlap.id,
                        linkedSwitchWithOverlap.id,
                        null,
                    ),
                    linkedTestAlignment.edges.map { (it.startNode.innerPort as? SwitchLink)?.id },
                )

                assertEquals(
                    listOf(
                        null,
                        existingSwitchJoints[1].number,
                        existingSwitchJoints[2].number,
                        secondJointNumber,
                        thirdJointNumber,
                        null,
                    ),
                    linkedTestAlignment.edges.map { (it.endNode.innerPort as? SwitchLink)?.jointNumber },
                )

                assertEquals(
                    SwitchLink(
                        linkedSwitchWithOverlap.id as IntId,
                        SwitchJointRole.of(
                            switchLibraryService.getSwitchStructure(linkedSwitchWithOverlap.switchStructureId),
                            firstJointNumber,
                        ),
                        firstJointNumber,
                    ),
                    linkedTestAlignment.edges[2].endNode.switchOut,
                )
                assertEquals(
                    SwitchLink(
                        existingLayoutSwitch.id as IntId,
                        SwitchJointRole.CONNECTION,
                        existingSwitchJoints[2].number,
                    ),
                    linkedTestAlignment.edges[3].startNode.switchOut,
                )
            }
    }

    @Test
    fun `Switch linking slight overlap correction should override the original switch when the overlap correction limit is exceeded`() {
        val moreThanAllowedOverlap = SWITCH_JOINT_NODE_ADJUSTMENT_TOLERANCE + 0.01

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
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 1, 1)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(5),
                            Point(40.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 2, 5)),
                        ),
                        FittedSwitchJoint(
                            JointNumber(2),
                            Point(60.0, 0.0),
                            LocationAccuracy.DESIGNED_GEOLOCATION,
                            matches = listOf(switchLinkingAtEnd(testLocationTrack.id, testAlignment, 2, 2)),
                        ),
                    )
            )

        val (_, linkedTestAlignmentBeforeTryingOverlap) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, testLocationTrack.id as IntId)

        val linkedSwitchStructure = switchLibraryService.getSwitchStructure(linkedSwitch.switchStructureId)
        assertEquals(
            listOf(
                SwitchLink(linkedSwitch.id as IntId, JointNumber(1), linkedSwitchStructure),
                SwitchLink(linkedSwitch.id, JointNumber(5), linkedSwitchStructure),
                SwitchLink(linkedSwitch.id, JointNumber(2), linkedSwitchStructure),
            ),
            linkedTestAlignmentBeforeTryingOverlap.nodes.map { it.portA }.filter { it is SwitchLink },
        )

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
                                m = testAlignment.segmentMValues[2].max - moreThanAllowedOverlap,
                                1,
                            )
                        ),
                ),
                FittedSwitchJoint(
                    JointNumber(5),
                    Point(80.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 4, 5)),
                ),
                FittedSwitchJoint(
                    JointNumber(2),
                    Point(100.0, 0.0),
                    LocationAccuracy.DESIGNED_GEOLOCATION,
                    matches = listOf(switchLinkingAtStart(testLocationTrack.id, testAlignment, 5, 2)),
                ),
            )

        createAndLinkSwitch(linkedJoints = jointsForSwitchWithTooMuchOverlap)

        val (_, linkedTestAlignment) =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, testLocationTrack.id)

        assertEquals(linkedTestAlignmentBeforeTryingOverlap, linkedTestAlignment)
    }

    private fun shiftSegmentGeometry(
        source: LayoutSegment,
        switchId: IntId<LayoutSwitch>?,
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

    private fun shiftSwitch(source: LayoutSwitch, name: String, shiftVector: Point) =
        source.copy(
            contextData = LayoutContextData.newOfficial(LayoutBranch.main),
            joints = source.joints.map { joint -> joint.copy(location = joint.location + shiftVector) },
            name = SwitchName(name),
        )

    private fun shiftTrack(template: List<LayoutSegment>, switchId: IntId<LayoutSwitch>?, shiftVector: Point) =
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
        val okSwitch = switchDao.save(shiftSwitch(templateSwitch, "ok", shift0))
        val okButValidationErrorSwitch = switchDao.save(shiftSwitch(templateSwitch, "ok but val", shift1))
        val unsaveableSwitch = switchDao.save(shiftSwitch(templateSwitch, "unsaveable", shift2))

        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                trackGeometryOfSegments(
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
            trackGeometryOfSegments(shiftTrack(templateBranchingTrackSegments, null, shift0)),
        )
        // linkable, but will cause a validation error due to being wrongly marked as a duplicate
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "bad branching track", duplicateOf = throughTrack.id, draft = true),
            trackGeometryOfSegments(shiftTrack(templateBranchingTrackSegments, null, shift1)),
        )
        val validationResult =
            switchTrackRelinkingValidationService.validateRelinkingTrack(LayoutBranch.main, throughTrack.id)
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
                    trackGeometryOfSegments(
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))) +
                            shiftTrack(templateThroughTrackSegments, null, Point(10.0, 0.0))
                    ),
                )
                .id
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track13", draft = true),
            trackGeometryOfSegments(shiftTrack(templateBranchingTrackSegments, null, Point(10.0, 0.0))),
        )
        val okSwitch = switchDao.save(shiftSwitch(templateSwitch, "ok", Point(10.0, 0.0)))

        val validationResult = switchTrackRelinkingValidationService.validateRelinkingTrack(LayoutBranch.main, track152)
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

        // we'll be linking topoTrack, which currently has a link to a switch that's actually somewhere completely
        // different, so once it gets relinked, it'll have no match on topoTrack (it's immaterial that the link happens
        // to be topological; the important thing is the misplaced switch)
        val okSwitch = switchDao.save(shiftSwitch(templateSwitch, "ok", basePoint))
        val switchSomewhereElse = switchDao.save(shiftSwitch(templateSwitch, "somewhere else", somewhereElse))
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track152", draft = true),
            trackGeometry(
                edge(
                    segments = shiftTrack(templateThroughTrackSegments, null, basePoint),
                    startInnerSwitch = switchLinkYV(okSwitch.id, 1),
                    endInnerSwitch = switchLinkYV(okSwitch.id, 2),
                )
            ),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track13", draft = true),
            trackGeometry(
                edge(
                    segments = shiftTrack(templateBranchingTrackSegments, null, basePoint),
                    startInnerSwitch = switchLinkYV(okSwitch.id, 1),
                    endInnerSwitch = switchLinkYV(okSwitch.id, 3),
                )
            ),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track152", draft = true),
            trackGeometryOfSegments(shiftTrack(templateThroughTrackSegments, null, somewhereElse)),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track13", draft = true),
            trackGeometry(
                edge(
                    segments = shiftTrack(templateBranchingTrackSegments, null, somewhereElse),
                    startInnerSwitch = switchLinkYV(okSwitch.id, 1),
                    endInnerSwitch = switchLinkYV(okSwitch.id, 3),
                )
            ),
        )

        val topoTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "topoTrack", draft = true),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 0.0), basePoint)),
                        startInnerSwitch = switchLinkYV(switchSomewhereElse.id, 1),
                        endInnerSwitch = switchLinkYV(switchSomewhereElse.id, 2),
                        endOuterSwitch = switchLinkYV(okSwitch.id, 1),
                    )
                ),
            )
        val validationResult =
            switchTrackRelinkingValidationService.validateRelinkingTrack(LayoutBranch.main, topoTrack.id)
        val expectedOkSwitchValidationResult =
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
            )
        val expectedSwitchSomewhereElseValidationResult =
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
                                LocalizationParams(mapOf("switchName" to "somewhere else", "sourceName" to "topoTrack")),
                        ),
                    ),
            )

        assertEquals(expectedOkSwitchValidationResult, validationResult.find { it.id == okSwitch.id })
        assertEquals(
            expectedSwitchSomewhereElseValidationResult,
            validationResult.find { it.id == switchSomewhereElse.id },
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
        val switch = switchDao.save(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                trackGeometry(
                    edge(
                        segments = templateThroughTrackSegments,
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 3),
                    ),
                    edge(
                        segments = listOf(segment(Point(34.43, 0.0), Point(200.0, 0.0))),
                        startOuterSwitch = switchLinkYV(switch.id, 3),
                    ),
                ),
            )
        val originallyLinkedBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "originally linked branching track", draft = true),
                trackGeometry(
                    edge(
                        segments = branchingTrackSegments,
                        startInnerSwitch = switchLinkYV(switch.id, 1),
                        endInnerSwitch = switchLinkYV(switch.id, 3),
                    )
                ),
            )
        val newBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "new branching track", draft = true),
                trackGeometry(edge(segments = shiftTrack(branchingTrackSegments, null, Point(134.4, 0.0)))),
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
            listOf(0.0..134.35 to null, 134.45..151.0 to switch.id, 151.1..168.8 to switch.id, 168.9..200.0 to null),
        )
    }

    @Test
    fun `null is suggested when no switch is applicable`() {
        assertNull(
            switchLinkingService.getSuggestedSwitch(
                LayoutBranch.main,
                Point(123.0, 456.0),
                switchDao.save(switch(draft = false)).id,
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
        val switch = switchDao.save(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))

        val oneFiveTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(
                    trackNumberId,
                    name = "one-five with topo link",
                    topologyEndSwitch = TopologyLocationTrackSwitch(switch.id, JointNumber(5)),
                    draft = true,
                ),
                trackGeometryOfSegments(setSwitchId(listOf(oneFive), null)),
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
                trackGeometryOfSegments(setSwitchId(listOf(fiveTwo), null)),
            )
        val threeFiveFourTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "three-four", draft = true),
                trackGeometryOfSegments(setSwitchId(templateFourThreeTrackSegments, switch.id)),
            )

        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)

        assertTrackDraftVersionSwitchLinks(oneFiveTrack.id, null, null, listOf(0.0..5.2 to switch.id))
        assertTrackDraftVersionSwitchLinks(fiveTwoTrack.id, null, null, listOf(0.0..5.2 to switch.id))
        assertTrackDraftVersionSwitchLinks(
            threeFiveFourTrack.id,
            null,
            null,
            listOf(0.0..5.2 to switch.id, 5.3..10.4 to switch.id),
        )
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
        val switch = switchDao.save(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        val someOtherSwitch = switchDao.save(switch(draft = false))

        val shift =
            templateThroughTrackSegments.last().segmentEnd.toPoint() -
                templateThroughTrackSegments.first().segmentStart.toPoint()
        val fullShift = shift + Point(100.0, 0.0)

        val throughTrackStart =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track start", draft = true),
                trackGeometry(
                    edge(
                        segments = templateThroughTrackSegments + listOf(segment(shift, fullShift)),
                        endOuterSwitch = SwitchLink(someOtherSwitch.id, SwitchJointRole.MAIN, JointNumber(1)),
                    )
                ),
            )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "through track switch and end", draft = true),
            trackGeometry(
                edge(
                    segments = shiftTrack(templateThroughTrackSegments, null, fullShift),
                    startInnerSwitch = SwitchLink(switch.id, SwitchJointRole.MAIN, JointNumber(1)),
                    endInnerSwitch = SwitchLink(switch.id, SwitchJointRole.CONNECTION, JointNumber(2)),
                )
            ),
        )
        // confuser branching track is misleadingly placed starting at the origin, while the switch
        // is at x=134.43
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "branching track", draft = true),
            trackGeometry(
                edge(
                    segments = templateBranchingTrackSegments,
                    startInnerSwitch = SwitchLink(switch.id, SwitchJointRole.MAIN, JointNumber(1)),
                    endInnerSwitch = SwitchLink(switch.id, SwitchJointRole.CONNECTION, JointNumber(3)),
                )
            ),
        )
        val uninvolvedTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "uninvolved track", draft = true),
                trackGeometryOfSegments(shiftTrack(templateThroughTrackSegments, null, fullShift - Point(1.0, 1.0))),
            )
        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, fullShift, switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id)

        assertTrackDraftVersionSwitchLinks(throughTrackStart.id, null, switch.id, listOf(0.0..134.4 to null))
        val updatedThroughTrackStartGeometry =
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, throughTrackStart.id).second
        assertEquals(null, updatedThroughTrackStartGeometry.edges[0].startNode.switchIn)
        assertEquals(null, updatedThroughTrackStartGeometry.edges[0].startNode.switchOut)
        assertEquals(null, updatedThroughTrackStartGeometry.edges[0].endNode.switchIn)
        assertEquals(switchLinkYV(switch.id, 1), updatedThroughTrackStartGeometry.edges[0].endNode.switchOut)
        assertEquals(uninvolvedTrack, locationTrackDao.fetchVersion(MainLayoutContext.draft, uninvolvedTrack.id))
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
        val switch = switchDao.save(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        templateTrackSections.forEach { (_, a) ->
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometryOfSegments(setSwitchId(a.segments, null)),
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
                trackGeometryOfSegments(segment(Point(456.7, 345.5), Point(457.8, 346.9))),
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
        val expected = SwitchLink(switchId, SwitchJointRole.CONNECTION, JointNumber(3))
        val actual =
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, branchingTrackContinuation)
                .second
                .startNode
                ?.switchOut
        assertEquals(expected, actual)
    }

    @Test
    fun `linking to deleted layout switch is not allowed`() {
        val insertedSwitch =
            switchDao.fetch(switchDao.save(switch(draft = false, stateCategory = LayoutStateCategory.NOT_EXISTING)))
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
        val ex =
            assertThrows<LinkingFailureException> {
                switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, insertedSwitch.id as IntId)
            }
        assertEquals(ex.localizationKey, LocalizationKey("error.linking.switch-deleted"))
    }

    @Test
    fun `nearby track end not within bounding box of switch joints still gets topologically connected when linking track`() {
        val (throughTrack, branchingTrackContinuation, switchId) =
            setupForLinkingTopoLinkToTrackOutsideSwitchJointBoundingBox()
        switchLinkingService.relinkTrack(LayoutBranch.main, throughTrack)
        val expected = SwitchLink(switchId, SwitchJointRole.CONNECTION, JointNumber(3))
        val actual =
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, branchingTrackContinuation)
                .second
                .startNode
                ?.switchOut
        assertEquals(expected, actual)
    }

    @Test
    fun `head-to-head YV switches prefer to retain their original positions`() {
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!

        // left side is slightly squished on the y axis compared to the right side, to make the
        // arrangement realistically slightly asymmetric
        val leftSwitchJoints =
            switchStructure.joints.map { joint ->
                LayoutSwitchJoint(
                    joint.number,
                    SwitchJointRole.of(switchStructure, joint.number),
                    Point(-joint.location.x, -joint.location.y * 0.99),
                    null,
                )
            }
        val rightSwitchJoints =
            switchStructure.joints.map { joint ->
                LayoutSwitchJoint(
                    joint.number,
                    SwitchJointRole.of(switchStructure, joint.number),
                    Point(joint.location.x, joint.location.y),
                    null,
                )
            }

        val leftSwitch =
            mainOfficialContext.save(switch(structureId = switchStructure.id, joints = leftSwitchJoints)).id
        val rightSwitch =
            mainOfficialContext.save(switch(structureId = switchStructure.id, joints = rightSwitchJoints)).id
        val trackNumber = mainOfficialContext.createLayoutTrackNumber().id
        val throughTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            segments = listOf(segment(Point(-35.0, 0.0), Point(0.0, 0.0))),
                            startInnerSwitch = SwitchLink(leftSwitch, JointNumber(2), switchStructure),
                            endInnerSwitch = SwitchLink(leftSwitch, JointNumber(1), switchStructure),
                            endOuterSwitch = SwitchLink(rightSwitch, JointNumber(1), switchStructure),
                        ),
                        edge(
                            segments = listOf(segment(Point(0.0, 0.0), Point(35.0, 0.0))),
                            startOuterSwitch = SwitchLink(leftSwitch, JointNumber(1), switchStructure),
                            startInnerSwitch = SwitchLink(rightSwitch, JointNumber(1), switchStructure),
                            endInnerSwitch = SwitchLink(rightSwitch, JointNumber(2), switchStructure),
                        ),
                    ),
                )
                .id
        val leftBranchingTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            segments = listOf(segment(Point(-35.0, 1.967 * 0.99), Point(0.0, 0.0))),
                            startInnerSwitch = SwitchLink(leftSwitch, JointNumber(3), switchStructure),
                            endInnerSwitch = SwitchLink(leftSwitch, JointNumber(1), switchStructure),
                        )
                    ),
                )
                .id
        val rightBranchingTrack =
            mainOfficialContext
                .save(
                    locationTrack(trackNumber),
                    trackGeometry(
                        edge(
                            segments = listOf(segment(Point(0.0, 0.0), Point(35.0, -1.967))),
                            startInnerSwitch = SwitchLink(rightSwitch, JointNumber(1), switchStructure),
                            endInnerSwitch = SwitchLink(rightSwitch, JointNumber(3), switchStructure),
                        )
                    ),
                )
                .id
        val leftSuggestion = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), leftSwitch)!!
        val rightSuggestion = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), rightSwitch)!!

        // left switch wants to remain connected to segments on left side tracks, and topologically
        // to right side branching track; right side conversely
        assertEquals(
            mapOf(throughTrack to true, leftBranchingTrack to true, rightBranchingTrack to false),
            leftSuggestion.trackLinks.mapValues { (_, links) ->
                links.suggestedLinks?.let { suggestedLinks -> suggestedLinks.joints.size > 1 }
            },
        )
        assertEquals(
            mapOf(throughTrack to true, leftBranchingTrack to false, rightBranchingTrack to true),
            rightSuggestion.trackLinks.mapValues { (_, links) ->
                links.suggestedLinks?.let { suggestedLinks -> suggestedLinks.joints.size > 1 }
            },
        )
    }

    @Test
    fun `Switch linking finds topological connection`() {
        // track A   track B
        //  2 |    / 3
        //    |   /
        //  5 |  /
        //    | /
        //  1 |/ 1
        //    T
        //    |
        //    |
        // track C
        //
        // Track C should connect topologically to joint 1

        val context = mainDraftContext
        val switchStructure = YV60_300_1_9_O()
        val switchId = IntId<LayoutSwitch>(1)

        // in this test tracks don't need to match switch structure geometrically,
        // but it might be easier to follow the test this way
        val trackNumber = mainDraftContext.createLayoutTrackNumber()
        val trackA =
            createTrack(switchStructure, asJointNumbers(1, 5, 2), "track A").setTrackNumber(trackNumber.id).let {
                insert(context.context, it)
            }
        val trackB =
            createTrack(switchStructure, asJointNumbers(1, 3), "track B").setTrackNumber(trackNumber.id).let {
                insert(context.context, it)
            }
        val trackC = createPrependingTrack(trackA, 10.0, "track C").let { insert(context.context, it) }

        // manually defined fitted switch, m-values don't need to match to switch structure
        val fittedSwitch =
            fittedSwitch(
                switchStructure,
                fittedJointMatch(trackA, 1, 0.0),
                fittedJointMatch(trackA, 5, 16.0),
                fittedJointMatch(trackA, 2, 30.0),
                fittedJointMatch(trackB, 1, 0.0),
                fittedJointMatch(trackB, 3, 32.567),
            )

        val linkedTracks = linkFittedSwitch(context.context, switchId, fittedSwitch)

        // validate
        assertInnerSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = switchId,
            listOf( //
                1 to 0.0,
                5 to 16.0,
                2 to 30.0,
            ),
        )
        assertInnerSwitchNodeExists(
            linkedTracks,
            trackB.locationTrackId,
            switchId = switchId,
            listOf( //
                1 to 0.0,
                3 to 32.567,
            ),
        )
        assertTopologicalConnectionAtEnd(linkedTracks, trackC.locationTrackId, switchId = switchId, 1)
    }

    private fun insert(context: LayoutContext, track: TrackForSwitchFitting): TrackForSwitchFitting {
        val locationTrackVersion =
            locationTrackService.saveDraft(
                context.branch,
                track.locationTrack.copy(contextData = LayoutContextData.new(context, null)),
                track.geometry,
            )
        val (locationTrack, geometry) = locationTrackService.getWithGeometry(locationTrackVersion)
        return TrackForSwitchFitting(emptyList(), locationTrack, geometry)
    }

    private fun setupForLinkingTopoLinkToTrackOutsideSwitchJointBoundingBox():
        Triple<IntId<LocationTrack>, IntId<LocationTrack>, IntId<LayoutSwitch>> {
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
            switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!.id
        val switchId =
            switchDao
                .save(
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
                    trackGeometry(
                        edge(
                            segments = listOf(segment(Point(0.0, 0.0), Point(34.43, 0.0))),
                            startInnerSwitch = SwitchLink(switchId, SwitchJointRole.MAIN, JointNumber(1)),
                            endInnerSwitch = SwitchLink(switchId, SwitchJointRole.CONNECTION, JointNumber(2)),
                        ),
                        edge(
                            segments = listOf(segment(Point(34.43, 0.0), Point(40.0, 0.0))),
                            startOuterSwitch = SwitchLink(switchId, SwitchJointRole.CONNECTION, JointNumber(2)),
                        ),
                    ),
                )
                .id
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "branching track start", draft = true),
            trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(34.9, -2.0))),
        )
        val branchingTrackContinuationId =
            locationTrackService
                .saveDraft(
                    LayoutBranch.main,
                    locationTrack(trackNumberId, name = "branching track continuation", draft = true),
                    trackGeometryOfSegments(segment(Point(35.0, -2.0), Point(50.0, -3.0))),
                )
                .id
        return Triple(throughTrackId, branchingTrackContinuationId, switchId)
    }

    private fun assertTrackDraftVersionSwitchLinks(
        trackId: IntId<LocationTrack>,
        topologyStartSwitchId: IntId<LayoutSwitch>?,
        topologyEndSwitchId: IntId<LayoutSwitch>?,
        innerSwitchesByMRange: List<Pair<ClosedRange<Double>, IntId<LayoutSwitch>?>>,
    ) {
        val track = locationTrackService.get(MainLayoutContext.draft, trackId)!!
        val (_, geometry) = locationTrackService.getWithGeometry(track.version!!)
        assertEquals(topologyStartSwitchId, geometry.outerStartSwitch?.id)
        assertEquals(topologyEndSwitchId, geometry.outerEndSwitch?.id)
        assertEquals(
            innerSwitchesByMRange.last().first.endInclusive,
            geometry.end!!.m,
            0.1,
            "expected given inner switches m-range to cover whole track",
        )
        innerSwitchesByMRange.forEachIndexed { rangeIndex, (range, switchId) ->
            val edge = geometry.getEdgeAtMOrThrow(range.start)
            val endEdge = geometry.getEdgeAtMOrThrow(range.endInclusive)
            val edgeIndexRange = geometry.edges.indexOf(edge.first)..geometry.edges.indexOf(endEdge.first)
            val edgeMRange = edgeIndexRange.joinToString { i -> "${geometry.edgeMs[i].min..geometry.edgeMs[i].max}" }
            assertEquals(
                edge,
                endEdge,
                "expected switch m range $rangeIndex ($range) to cover only one edge, but it covers $edgeIndexRange ($edgeMRange)",
            )
            assertEquals(range.start, edge.second.min, 0.1, "edge range starts at given m-value")
            assertEquals(range.endInclusive, edge.second.max, 0.1, "edge range ends at given m-value")

            val edgeIndex = geometry.edges.indexOf(edge.first)

            assertEquals(
                switchId,
                edge.first.startNode.switchIn?.id,
                "switch id at start side of edge at index $edgeIndex (asserted m-range ${range.start}..${
                        range.endInclusive
                    }, edge m-range ${edge.second.min}..${edge.second.max}",
            )
            assertEquals(
                switchId,
                edge.first.endNode.switchIn?.id,
                "switch id at end side of edge at index $edgeIndex (asserted m-range ${range.start}..${
                    range.endInclusive
                }, edge m-range ${edge.second.min}..${edge.second.max}",
            )
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
        return acc.map { ss -> ss.map { s -> s.copy(geometry = s.geometry.copy(id = StringId())) } }
    }

    private fun setSwitchId(segments: List<LayoutSegment>, switchId: IntId<LayoutSwitch>?) =
        segments.map { segment ->
            segment.copy(
                switchId = switchId,
                startJointNumber = if (switchId == null) null else JointNumber(1),
                endJointNumber = null,
            )
        }

    private fun createDraftLocationTrackFromLayoutSegments(
        layoutSegments: List<LayoutSegment>
    ): Pair<LocationTrack, LocationTrackGeometry> {
        val (locationTrack, alignment) =
            locationTrackAndGeometry(
                trackNumberId = mainDraftContext.createLayoutTrackNumber().id,
                segments = layoutSegments,
                draft = true,
            )
        val locationTrackId = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, alignment).id
        return locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, locationTrackId)
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

    private fun linkFittedSwitch(
        layoutContext: LayoutContext,
        switchId: IntId<LayoutSwitch>,
        fittedSwitch: FittedSwitch,
    ): List<Pair<LocationTrack, LocationTrackGeometry>> {
        val fittedSwitchLocationTrackIds =
            fittedSwitch.joints.flatMap { joint -> joint.matches.map { match -> match.locationTrackId } }.distinct()
        val fittedSwitchTracks =
            fittedSwitchLocationTrackIds.map { locationTrackId ->
                requireNotNull(locationTrackService.getWithGeometry(layoutContext, locationTrackId)) {
                    "Location track $locationTrackId for fitted switch not found"
                }
            }
        val switchContainingTracks = switchService.getLocationTracksLinkedToSwitch(layoutContext, switchId)
        val linkedTracks =
            directlyApplyFittedSwitchChangesToTracks(
                    switchId,
                    fittedSwitch,
                    fittedSwitchTracks + switchContainingTracks,
                )
                .let { modifiedTracks ->
                    locationTrackService.recalculateTopology(layoutContext, modifiedTracks, switchId)
                }

        return linkedTracks
    }
}

fun suggestedSwitchJointMatch(
    locationTrackId: IntId<LocationTrack>,
    segmentIndex: Int,
    m: Double,
    jointNumber: Int,
    matchDirection: RelativeDirection = RelativeDirection.Along,
): FittedSwitchJointMatch =
    FittedSwitchJointMatch(
        locationTrackId,
        segmentIndex,
        m,
        SwitchStructureJoint(JointNumber(jointNumber), Point(1.0, 2.0)),
        SuggestedSwitchJointMatchType.START,
        0.1,
        0.1,
        matchDirection,
        location = Point(1.0, 2.0),
    )

fun directlyApplyFittedSwitchChangesToTracks(
    switchId: IntId<LayoutSwitch>,
    fittedSwitch: FittedSwitch,
    relevantTracks: List<Pair<LocationTrack, LocationTrackGeometry>>,
): List<Pair<LocationTrack, LocationTrackGeometry>> {
    val clearedTracks = clearSwitchFromTracks(switchId, relevantTracks.associateBy { it.first.id as IntId })
    val suggested = matchFittedSwitchToTracks(fittedSwitch, clearedTracks, switchId)
    return withChangesFromLinkingSwitch(suggested, fittedSwitch.switchStructure, switchId, clearedTracks)
}
