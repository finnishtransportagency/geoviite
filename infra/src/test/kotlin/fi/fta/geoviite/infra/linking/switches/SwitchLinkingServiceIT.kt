package fi.fta.geoviite.infra.linking.switches

import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationAccuracy
import fi.fta.geoviite.infra.common.MainLayoutContext
import fi.fta.geoviite.infra.common.MeasurementMethod
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
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutSegment
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchJoint
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.SegmentGeometry
import fi.fta.geoviite.infra.tracklayout.SwitchJointRole
import fi.fta.geoviite.infra.tracklayout.SwitchLink
import fi.fta.geoviite.infra.tracklayout.TrackBoundary
import fi.fta.geoviite.infra.tracklayout.TrackBoundaryType
import fi.fta.geoviite.infra.tracklayout.alignment
import fi.fta.geoviite.infra.tracklayout.assertEquals
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
                switchLinkingService.findLocationTracksNearFittedSwitch(LayoutBranch.main, fittedSwitch),
                insertedSwitch.id as IntId,
            )
        val rowVersion =
            switchLinkingService.saveSwitchLinking(
                LayoutBranch.main,
                suggestedSwitch,
                insertedSwitch.id as IntId,
                geometrySwitchId = null,
            )
        val switch = switchDao.fetch(rowVersion)
        assertEquals(switch.source, GeometrySource.GENERATED)
    }

    @Test
    fun linkingExistingGeometrySwitchGetsSwitchAccuracyForJoints() {
        val geometrySwitchId = setupJointLocationAccuracyTest()
        val suggestedSwitch =
            (switchLinkingService.getSuggestedSwitch(LayoutBranch.main, geometrySwitchId, layoutSwitchId = null)
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
                switchLinkingService.findLocationTracksNearFittedSwitch(LayoutBranch.main, fittedSwitch),
                insertedSwitch.id as IntId,
            ),
            insertedSwitch.id as IntId,
            geometrySwitchId = null,
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
                val suggestedSwitch =
                    matchFittedSwitchToTracks(
                        fittedSwitch,
                        switchLinkingService.findLocationTracksNearFittedSwitch(LayoutBranch.main, fittedSwitch),
                        storedSwitch.id as IntId,
                    )
                switchLinkingService.saveSwitchLinking(
                    LayoutBranch.main,
                    suggestedSwitch,
                    storedSwitch.id,
                    geometrySwitchId = null,
                )
            }
            .let { switchDaoResponse -> switchDao.fetch(switchDaoResponse) }
    }

    private data class LocationTracksWithLinkedSwitch(
        val straightTrack: LocationTrack,
        val straightTrackGeometry: LocationTrackGeometry,
        val divertingTrack: LocationTrack,
        val divertingTrackGeometry: LocationTrackGeometry,
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
            straightTrackGeometry = linkedStraightTrackAlignment,
            divertingTrack = linkedDivertingTrack,
            divertingTrackGeometry = linkedDivertingTrackAlignment,
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
                                m = testLocation.straightTrackGeometry.segmentMValues[2].max - switchOverlapAmount,
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
                                testLocation.straightTrackGeometry,
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
                            switchLinkingAtEnd(testLocation.straightTrack.id, testLocation.straightTrackGeometry, 4, 2)
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
                                testLocation.divertingTrackGeometry,
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

        assertEquals(testLocation.straightTrackGeometry.edges.take(2), overlapLinkedStraightAlignment.edges.take(2))
        assertEquals(
            testLocation.straightTrackGeometry.edges[2].startNode,
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
                // clear old location track version from the way to keep the linking comprehensible
                testDBService.clearLayoutTables()

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

    private fun shiftSegmentGeometry(source: LayoutSegment, shiftVector: Point): LayoutSegment =
        source.copy(
            geometry =
                SegmentGeometry(
                    source.geometry.resolution,
                    source.geometry.segmentPoints.map { sp ->
                        sp.copy(x = sp.x + shiftVector.x, y = sp.y + shiftVector.y)
                    },
                )
        )

    private fun shiftSwitch(source: LayoutSwitch, name: String, shiftVector: Point) =
        source.copy(
            contextData = LayoutContextData.newOfficial(LayoutBranch.main),
            joints = source.joints.map { joint -> joint.copy(location = joint.location + shiftVector) },
            name = SwitchName(name),
        )

    private fun shiftTrack(template: List<LayoutSegment>, shiftVector: Point) =
        template.map { segment -> shiftSegmentGeometry(segment, shiftVector) }

    @Test
    fun `validateRelinkingTrack relinks okay cases and gives validation errors about bad ones`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(150.0, 0.0))))
                .id

        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!

        // through track has three switches; first one is linkable OK, second one is linkable but will cause a
        // validation error as the only branching track is a duplicate, third one can't be linked as there is no
        // branching track
        val okSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 0.0), null)),
                    "ok",
                )
            )
        val okButValidationErrorSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(60.0, 0.0), null)),
                    "ok but val",
                )
            )
        val unsaveableSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(110.0, 0.0), null)),
                    "unsaveable",
                )
            )

        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                // switches placed with joint 1s at x-values 10, 60 and 110, joint 2s at 50, 100, and 150
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                        endOuterSwitch = SwitchLink(okSwitch.id, JointNumber(1), switchStructure),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(50.0, 0.0))),
                        startInnerSwitch = SwitchLink(okSwitch.id, JointNumber(1), switchStructure),
                        endInnerSwitch = SwitchLink(okSwitch.id, JointNumber(2), switchStructure),
                    ),
                    edge(
                        listOf(segment(Point(50.0, 0.0), Point(60.0, 0.0))),
                        startOuterSwitch = SwitchLink(okSwitch.id, JointNumber(2), switchStructure),
                        endOuterSwitch = SwitchLink(okButValidationErrorSwitch.id, JointNumber(1), switchStructure),
                    ),
                    edge(
                        listOf(segment(Point(60.0, 0.0), Point(100.0, 0.0))),
                        startInnerSwitch = SwitchLink(okButValidationErrorSwitch.id, JointNumber(1), switchStructure),
                        endInnerSwitch = SwitchLink(okButValidationErrorSwitch.id, JointNumber(2), switchStructure),
                    ),
                    edge(
                        listOf(segment(Point(100.0, 0.0), Point(110.0, 0.0))),
                        startOuterSwitch = SwitchLink(okButValidationErrorSwitch.id, JointNumber(2), switchStructure),
                        endOuterSwitch = SwitchLink(unsaveableSwitch.id, JointNumber(1), switchStructure),
                    ),
                    edge(
                        listOf(segment(Point(110.0, 0.0), Point(150.0, 0.0))),
                        startInnerSwitch = SwitchLink(unsaveableSwitch.id, JointNumber(1), switchStructure),
                        endInnerSwitch = SwitchLink(unsaveableSwitch.id, JointNumber(2), switchStructure),
                    ),
                ),
            )

        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "ok branching track", draft = true),
            trackGeometry(
                edge(
                    listOf(segment(Point(10.0, 0.0), Point(10.0, 0.0) + Point(34.321, -1.967))),
                    startOuterSwitch = SwitchLink(okSwitch.id, JointNumber(1), switchStructure),
                )
            ),
        )
        // linkable, but will cause a validation error due to being wrongly marked as a duplicate
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "bad branching track", duplicateOf = throughTrack.id, draft = true),
            trackGeometry(
                edge(
                    listOf(segment(Point(60.0, 0.0), Point(60.0, 0.0) + Point(34.321, -1.967))),
                    startOuterSwitch = SwitchLink(okButValidationErrorSwitch.id, JointNumber(1), switchStructure),
                )
            ),
        )
        val validationResult =
            switchTrackRelinkingValidationService.validateRelinkingTrack(LayoutBranch.main, throughTrack.id)
        assertEqualsRounded(
            listOf(
                SwitchRelinkingValidationResult(
                    id = okSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(Point(10.0, 0.0), TrackMeter("0000+0010.000")),
                    validationIssues = listOf(),
                ),
                SwitchRelinkingValidationResult(
                    id = okButValidationErrorSwitch.id,
                    successfulSuggestion = SwitchRelinkingSuggestion(Point(60.0, 0.0), TrackMeter("0000+0060.000")),
                    validationIssues =
                        listOf(
                            LayoutValidationIssue(
                                type = LayoutValidationIssueType.WARNING,
                                localizationKey =
                                    LocalizationKey.of(
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
                                    LocalizationKey.of("validation.layout.switch.track-linkage.relinking-failed"),
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
                            shiftTrack(templateThroughTrackSegments, Point(10.0, 0.0))
                    ),
                )
                .id
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "track13", draft = true),
            trackGeometryOfSegments(shiftTrack(templateBranchingTrackSegments, Point(10.0, 0.0))),
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
                    segments = shiftTrack(templateThroughTrackSegments, basePoint),
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
                    segments = shiftTrack(templateBranchingTrackSegments, basePoint),
                    startInnerSwitch = switchLinkYV(okSwitch.id, 1),
                    endInnerSwitch = switchLinkYV(okSwitch.id, 3),
                )
            ),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track152", draft = true),
            trackGeometryOfSegments(shiftTrack(templateThroughTrackSegments, somewhereElse)),
        )
        locationTrackService.saveDraft(
            LayoutBranch.main,
            locationTrack(trackNumberId, name = "some other track13", draft = true),
            trackGeometry(
                edge(
                    segments = shiftTrack(templateBranchingTrackSegments, somewhereElse),
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
                                LocalizationKey.of("validation.layout.split.track-links-missing-after-relinking"),
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
                                LocalizationKey.of("validation.layout.switch.track-linkage.front-joint-not-connected"),
                            params = LocalizationParams(mapOf("switch" to "somewhere else")),
                        ),
                        LayoutValidationIssue(
                            LayoutValidationIssueType.ERROR,
                            localizationKey =
                                LocalizationKey.of("validation.layout.split.track-links-missing-after-relinking"),
                            params =
                                LocalizationParams(
                                    mapOf("switchName" to "somewhere else", "sourceName" to "topoTrack")
                                ),
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
                trackGeometry(edge(segments = shiftTrack(branchingTrackSegments, Point(134.4, 0.0)))),
            )
        val suggestedSwitch =
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(134.321, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id, geometrySwitchId = null)
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
                locationTrack(trackNumberId, name = "one-five with topo link", draft = true),
                trackGeometry(edge(listOf(oneFive), endOuterSwitch = switchLinkYV(switch.id, 5))),
            )

        val fiveTwoTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "five-two with topo link", draft = true),
                trackGeometry(edge(listOf(fiveTwo), startOuterSwitch = switchLinkYV(switch.id, 1))),
            )
        val threeFiveFourTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "three-four", draft = true),
                trackGeometry(
                    edge(
                        templateFourThreeTrackSegments,
                        startInnerSwitch = switchLinkYV(switch.id, 3),
                        endInnerSwitch = switchLinkYV(switch.id, 4),
                    )
                ),
            )

        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id, geometrySwitchId = null)

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
    fun `relinking with head-to-head linked YV switches with initially unlinked through track`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val leftSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints =
                        listOf(
                            LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(40.0, 0.0), null),
                            LayoutSwitchJoint(
                                JointNumber(2),
                                SwitchJointRole.CONNECTION,
                                Point(40.0 - 34.43, 0.0),
                                null,
                            ),
                        ),
                )
            )

        val rightSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints =
                        listOf(
                            LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(40.0, 0.0), null),
                            LayoutSwitchJoint(
                                JointNumber(2),
                                SwitchJointRole.CONNECTION,
                                Point(40.0 + 34.43, 0.0),
                                null,
                            ),
                        ),
                )
            )

        // initial situation: left and right branching tracks for head-to-head YV switches are fully correctly linked,
        // through track isn't linked at all though
        val throughTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "through track", draft = true),
                trackGeometryOfSegments(segment(Point(0.0, 0.0), Point(100.0, 0.0))),
            )

        val leftBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "left branching track", draft = true),
                trackGeometry(
                    edge(
                        segments = listOf(segment(Point(0.0, 2.5), Point(40.0 - 34.4, 2.0))),
                        endOuterSwitch = switchLinkYV(leftSwitch.id, 3),
                    ),
                    edge(
                        segments = listOf(segment(Point(40.0 - 34.4, 2.0), Point(40.0, 0.0))),
                        startInnerSwitch = switchLinkYV(leftSwitch.id, 3),
                        endInnerSwitch = switchLinkYV(leftSwitch.id, 1),
                        endOuterSwitch = switchLinkYV(rightSwitch.id, 1),
                    ),
                ),
            )

        val rightBranchingTrack =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "right branching track", draft = true),
                trackGeometry(
                    edge(
                        segments = listOf(segment(Point(40.0, 0.0), Point(40.0 + 34.4, -2.0))),
                        startOuterSwitch = switchLinkYV(leftSwitch.id, 1),
                        startInnerSwitch = switchLinkYV(rightSwitch.id, 1),
                        endInnerSwitch = switchLinkYV(rightSwitch.id, 3),
                    ),
                    edge(
                        segments = listOf(segment(Point(40.0 + 34.4, -2.0), Point(80.0, -20.0))),
                        startOuterSwitch = switchLinkYV(rightSwitch.id, 3),
                    ),
                ),
            )

        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(40.0, 0.0), leftSwitch.id)!!,
            leftSwitch.id,
            geometrySwitchId = null,
        )
        assertEquals(
            listOf(
                listOf(
                    null,
                    TrackBoundary(leftBranchingTrack.id, TrackBoundaryType.START),
                    null,
                    switchLinkYV(leftSwitch.id, 3),
                ),
                listOf(
                    null,
                    switchLinkYV(leftSwitch.id, 3),
                    switchLinkYV(leftSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 1),
                ),
            ),
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, leftBranchingTrack.id)
                .second
                .edges
                .map {
                    listOf(it.startNode.outerPort, it.startNode.innerPort, it.endNode.innerPort, it.endNode.outerPort)
                },
        )

        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(40.0, 0.0), rightSwitch.id)!!,
            rightSwitch.id,
            geometrySwitchId = null,
        )

        assertEquals(
            listOf(
                listOf(
                    null,
                    TrackBoundary(throughTrack.id, TrackBoundaryType.START),
                    null,
                    switchLinkYV(leftSwitch.id, 2),
                ),
                listOf(null, switchLinkYV(leftSwitch.id, 2), switchLinkYV(leftSwitch.id, 5), null),
                listOf(
                    null,
                    switchLinkYV(leftSwitch.id, 5),
                    switchLinkYV(leftSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 1),
                ),
                listOf(
                    switchLinkYV(leftSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 5),
                    null,
                ),
                listOf(null, switchLinkYV(rightSwitch.id, 5), switchLinkYV(rightSwitch.id, 2), null),
                listOf(
                    switchLinkYV(rightSwitch.id, 2),
                    null,
                    TrackBoundary(throughTrack.id, TrackBoundaryType.END),
                    null,
                ),
            ),
            locationTrackService.getWithGeometryOrThrow(MainLayoutContext.draft, throughTrack.id).second.edges.map {
                listOf(it.startNode.outerPort, it.startNode.innerPort, it.endNode.innerPort, it.endNode.outerPort)
            },
        )

        assertEquals(
            listOf(
                listOf(
                    switchLinkYV(leftSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 1),
                    switchLinkYV(rightSwitch.id, 3),
                    null,
                ),
                listOf(
                    switchLinkYV(rightSwitch.id, 3),
                    null,
                    TrackBoundary(rightBranchingTrack.id, TrackBoundaryType.END),
                    null,
                ),
            ),
            locationTrackService
                .getWithGeometryOrThrow(MainLayoutContext.draft, rightBranchingTrack.id)
                .second
                .edges
                .map {
                    listOf(it.startNode.outerPort, it.startNode.innerPort, it.endNode.innerPort, it.endNode.outerPort)
                },
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
        val switch = switchDao.save(templateSwitch.copy(contextData = LayoutContextData.newOfficial(LayoutBranch.main)))
        templateTrackSections.forEach { (_, a) ->
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, draft = true),
                trackGeometryOfSegments(a.segments),
            )
        }
        val otherLocationTrackWithTopoSwitchLink =
            locationTrackService.saveDraft(
                LayoutBranch.main,
                locationTrack(trackNumberId, name = "unrelated mislinked track", draft = true),
                trackGeometry(
                    edge(
                        listOf(segment(Point(456.7, 345.5), Point(457.8, 346.9))),
                        endOuterSwitch = switchLinkYV(switch.id, 1),
                    )
                ),
            )
        val suggestedSwitch = switchLinkingService.getSuggestedSwitch(LayoutBranch.main, Point(0.0, 0.0), switch.id)!!
        switchLinkingService.saveSwitchLinking(LayoutBranch.main, suggestedSwitch, switch.id, geometrySwitchId = null)
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
            geometrySwitchId = null,
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
                switchLinkingService.findLocationTracksNearFittedSwitch(LayoutBranch.main, fittedSwitch),
                insertedSwitch.id as IntId,
            )
        val ex =
            assertThrows<LinkingFailureException> {
                switchLinkingService.saveSwitchLinking(
                    LayoutBranch.main,
                    suggestedSwitch,
                    insertedSwitch.id as IntId,
                    geometrySwitchId = null,
                )
            }
        assertEquals(ex.localizationKey, LocalizationKey.of("error.linking.switch-deleted"))
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
            setOf(throughTrack, leftBranchingTrack),
            leftSuggestion.trackLinks.filter { (_, suggested) -> suggested.suggestedLinks != null }.keys,
        )
        assertEquals(setOf(rightBranchingTrack), leftSuggestion.topologicallyLinkedTracks)
        assertEquals(
            setOf(throughTrack, rightBranchingTrack),
            rightSuggestion.trackLinks.filter { (_, suggested) -> suggested.suggestedLinks != null }.keys,
        )
        assertEquals(setOf(leftBranchingTrack), rightSuggestion.topologicallyLinkedTracks)
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
                fittedJointMatch(trackA, 1, LineM(0.0)),
                fittedJointMatch(trackA, 5, LineM(16.0)),
                fittedJointMatch(trackA, 2, LineM(30.0)),
                fittedJointMatch(trackB, 1, LineM(0.0)),
                fittedJointMatch(trackB, 3, LineM(32.567)),
            )

        val linkedTracks = linkFittedSwitch(context.context, switchId, fittedSwitch)

        // validate
        assertInnerSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = switchId,
            listOf( //
                1 to LineM(0.0),
                5 to LineM(16.0),
                2 to LineM(30.0),
            ),
        )
        assertInnerSwitchNodeExists(
            linkedTracks,
            trackB.locationTrackId,
            switchId = switchId,
            listOf( //
                1 to LineM(0.0),
                3 to LineM(32.567),
            ),
        )
        assertTopologicalConnectionAtEnd(linkedTracks, trackC.locationTrackId, switchId = switchId, 1)
    }

    @Test
    fun `should clear switch from old location when linking into new location`() {
        // Diverging tracks are somewhat irrelevant in this test and are therefore ignored.
        //
        //     track A      track B      track C
        //  ├──────────┼───────────────┼─────────┤
        //                             1    5    2   current switch position
        //  1    5     2                             new switch position
        //
        // Joints 1, 5, 2 should be removed from track B and C.
        // Joints 1, 5, 2 should be added to track A.
        //
        val trackNumber = mainDraftContext.createLayoutTrackNumber().id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val newSwitchId = switchDao.save(switch(switchStructure.id)).id

        fun saveTrack(lt: TrackForSwitchFitting): TrackForSwitchFitting {
            val version =
                locationTrackDao.save(
                    lt.locationTrack
                        .copy(trackNumberId = trackNumber)
                        .withContext(LayoutContextData.newDraft(LayoutBranch.main, id = null)),
                    lt.geometry,
                )
            return lt.copy(locationTrack = locationTrackDao.fetch(version))
        }

        // in this test tracks don't need to match switch structure geometrically,
        // but might help debugging
        val trackA = saveTrack(createTrack(switchStructure.data, asJointNumbers(1, 5, 2), "track A"))
        val trackB =
            saveTrack(
                trackA
                    .asNew("track B")
                    .moveForward(trackA.length.distance)
                    .withSwitch(newSwitchId, switchStructure.data, topologicalJointAtEnd(1))
            )
        val trackC =
            saveTrack(
                createTrack(switchStructure.data, asJointNumbers(1, 5, 2), "track C")
                    .moveForward(trackA.length.distance + trackB.length.distance)
                    .withSwitch(
                        newSwitchId,
                        switchStructure.data,
                        innerJointAtStart(1),
                        innerJointAtM(LineM(16.0), 5),
                        innerJointAtEnd(2),
                    )
            )

        val nearbyTracks = listOf(trackA.trackAndGeometry, trackB.trackAndGeometry)
        val farawayTracks = listOf(trackC.trackAndGeometry)

        // manually defined fitted switch, m-values don't need to match to switch structure
        // but are relevant for geometry checks
        val fittedSwitch =
            fittedSwitch(
                switchStructure.data,
                fittedJointMatch(trackA, 1, LineM(0.0)),
                fittedJointMatch(trackA, 5, trackA.length / 2),
                fittedJointMatch(trackA, 2, trackA.length),
            )

        switchLinkingService.saveSwitchLinking(
            LayoutBranch.main,
            matchFittedSwitchToTracks(
                fittedSwitch,
                clearSwitchFromTracks(newSwitchId, (nearbyTracks + farawayTracks).associateBy { it.first.id as IntId }),
                newSwitchId,
            ),
            layoutSwitchId = newSwitchId,
            geometrySwitchId = null,
        )
        val linkedTracks =
            locationTrackService.getManyWithGeometries(
                mainDraftContext.context,
                (nearbyTracks + farawayTracks).map { it.first.id as IntId },
            )

        // validate

        assertTracksExists(linkedTracks, trackA.name, trackB.name, trackC.name)

        assertSwitchDoesNotExist(linkedTracks, trackC.locationTrackId, newSwitchId)
        assertInnerSwitchNodeExists(
            linkedTracks,
            trackA.locationTrackId,
            switchId = newSwitchId,
            jointsWithM = listOf(1 to LineM(0.0), 5 to trackA.length / 2, 2 to trackA.length),
        )

        assertTopologySwitchAtStart(linkedTracks, trackB.locationTrackId, newSwitchId, 2)
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
            LineM(innerSwitchesByMRange.last().first.endInclusive),
            geometry.end!!.m,
            0.1,
            "expected given inner switches m-range to cover whole track",
        )
        innerSwitchesByMRange.forEachIndexed { rangeIndex, (range, switchId) ->
            val edge = geometry.getEdgeAtMOrThrow(LineM(range.start))
            val endEdge = geometry.getEdgeAtMOrThrow(LineM(range.endInclusive))
            val edgeIndexRange = geometry.edges.indexOf(edge.first)..geometry.edges.indexOf(endEdge.first)
            val edgeMRange = edgeIndexRange.joinToString { i -> "${geometry.edgeMs[i].min..geometry.edgeMs[i].max}" }
            assertEquals(
                edge,
                endEdge,
                "expected switch m range $rangeIndex ($range) to cover only one edge, but it covers $edgeIndexRange ($edgeMRange)",
            )
            assertEquals(LineM(range.start), edge.second.min, 0.1, "edge range starts at given m-value")
            assertEquals(LineM(range.endInclusive), edge.second.max, 0.1, "edge range ends at given m-value")

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

    @Test
    fun `relinkTrack does not relink unrelated tracks`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val switchToRelink =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(10.0, 0.0), null)),
                )
            )
        val unrelatedSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(40.0, 0.0), null)),
                )
            )

        // layout: relinkingTrack is on y=0.0, goes from x=0.0 to 100.0, branches off to negative y at x=10.0
        // unrelated track is on y=0.5, goes from x=0.0 to 100.0, branches off to positive y at x=40.0
        val relinkingTrack =
            locationTrackDao
                .save(
                    locationTrack(trackNumberId, name = "track to relink"),
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                            endOuterSwitch = switchLinkYV(switchToRelink.id, 1),
                        ),
                        edge(
                            listOf(segment(Point(10.0, 0.0), Point(40.0, 0.0))),
                            startInnerSwitch = switchLinkYV(switchToRelink.id, 1),
                            endInnerSwitch = switchLinkYV(switchToRelink.id, 2),
                        ),
                        edge(
                            listOf(segment(Point(40.0, 0.0), Point(100.0, 0.0))),
                            startOuterSwitch = switchLinkYV(switchToRelink.id, 2),
                        ),
                    ),
                )
                .id
        val branchingTrack =
            locationTrackDao
                .save(
                    locationTrack(trackNumberId, name = "branching track"),
                    trackGeometry(
                        edge(
                            listOf(segment(Point(10.0, 0.0), Point(40.0, -2.0))),
                            startInnerSwitch = switchLinkYV(switchToRelink.id, 1),
                            endInnerSwitch = switchLinkYV(switchToRelink.id, 3),
                        ),
                        edge(
                            listOf(segment(Point(40.0, -2.0), Point(100.0, -10.0))),
                            startOuterSwitch = switchLinkYV(switchToRelink.id, 3),
                        ),
                    ),
                )
                .id

        val unrelatedThroughTrack =
            locationTrackDao
                .save(
                    locationTrack(trackNumberId, name = "unrelated track"),
                    trackGeometry(
                        edge(
                            listOf(segment(Point(0.0, 0.5), Point(40.0, 0.5))),
                            endOuterSwitch = switchLinkYV(unrelatedSwitch.id, 1),
                        ),
                        edge(
                            listOf(segment(Point(40.0, 0.5), Point(80.0, 0.5))),
                            startInnerSwitch = switchLinkYV(unrelatedSwitch.id, 1),
                            endInnerSwitch = switchLinkYV(unrelatedSwitch.id, 2),
                        ),
                        edge(
                            listOf(segment(Point(80.0, 0.5), Point(100.0, 0.5))),
                            startOuterSwitch = switchLinkYV(unrelatedSwitch.id, 2),
                        ),
                    ),
                )
                .id

        val unrelatedBranchingTrack =
            locationTrackDao
                .save(
                    locationTrack(trackNumberId, name = "unrelated branching track"),
                    trackGeometry(
                        edge(
                            listOf(segment(Point(40.0, 0.5), Point(80.0, 2.5))),
                            startInnerSwitch = switchLinkYV(unrelatedSwitch.id, 1),
                            endInnerSwitch = switchLinkYV(unrelatedSwitch.id, 3),
                        ),
                        edge(
                            listOf(segment(Point(80.0, 2.5), Point(100.0, 10.0))),
                            startOuterSwitch = switchLinkYV(unrelatedSwitch.id, 3),
                        ),
                    ),
                )
                .id

        assertEquals(mainOfficialContext.context, mainDraftContext.fetch(unrelatedBranchingTrack)?.layoutContext)
        assertEquals(mainOfficialContext.context, mainDraftContext.fetch(unrelatedThroughTrack)?.layoutContext)

        switchLinkingService.relinkTrack(LayoutBranch.main, relinkingTrack)

        assertEquals(mainDraftContext.context, mainDraftContext.fetch(relinkingTrack)?.layoutContext)
        assertEquals(mainDraftContext.context, mainDraftContext.fetch(branchingTrack)?.layoutContext)
        assertEquals(mainOfficialContext.context, mainDraftContext.fetch(unrelatedBranchingTrack)?.layoutContext)
        assertEquals(mainOfficialContext.context, mainDraftContext.fetch(unrelatedThroughTrack)?.layoutContext)
    }

    @Test
    fun `relinkTrack connects track outward topologically`() {
        val trackNumberId =
            mainOfficialContext
                .createLayoutTrackNumberAndReferenceLine(alignment(segment(Point(0.0, 0.0), Point(200.0, 0.0))))
                .id
        val switchStructure = switchLibraryService.getSwitchStructures().find { it.type.typeName == "YV60-300-1:9-O" }!!
        val startSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(40.0, 0.0), null)),
                )
            )
        val endSwitch =
            switchDao.save(
                switch(
                    switchStructure.id,
                    joints = listOf(LayoutSwitchJoint(JointNumber(1), SwitchJointRole.MAIN, Point(80.0, 0.0), null)),
                )
            )

        // layout: relinkingTrack is on y=0.0, goes from x=40.0 to 80.0
        // beforeTrack and afterTrack are fully correctly linked, to startSwitch and endSwitch respectively
        val relinkingTrack =
            locationTrackDao.save(
                locationTrack(trackNumberId, name = "relinking track"),
                trackGeometryOfSegments(segment(Point(40.0, 0.0), Point(80.0, 0.0))),
            )
        locationTrackDao
            .save(
                locationTrack(trackNumberId, name = "start track"),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 0.0), Point(10.0, 0.0))),
                        endOuterSwitch = switchLinkYV(startSwitch.id, 2),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 0.0), Point(40.0, 0.0))),
                        startInnerSwitch = switchLinkYV(startSwitch.id, 2),
                        endInnerSwitch = switchLinkYV(startSwitch.id, 1),
                    ),
                ),
            )
            .id
        locationTrackDao
            .save(
                locationTrack(trackNumberId, name = "start branching track"),
                trackGeometry(
                    edge(
                        listOf(segment(Point(0.0, 2.0), Point(10.0, 2.0))),
                        endOuterSwitch = switchLinkYV(startSwitch.id, 3),
                    ),
                    edge(
                        listOf(segment(Point(10.0, 2.0), Point(40.0, 0.0))),
                        startInnerSwitch = switchLinkYV(startSwitch.id, 3),
                        endInnerSwitch = switchLinkYV(startSwitch.id, 1),
                    ),
                ),
            )
            .id
        locationTrackDao
            .save(
                locationTrack(trackNumberId, name = "end track"),
                trackGeometry(
                    edge(
                        listOf(segment(Point(80.0, 0.0), Point(120.0, 0.0))),
                        startInnerSwitch = switchLinkYV(endSwitch.id, 1),
                        endInnerSwitch = switchLinkYV(endSwitch.id, 2),
                    ),
                    edge(
                        listOf(segment(Point(120.0, 0.0), Point(130.0, 0.0))),
                        startOuterSwitch = switchLinkYV(endSwitch.id, 2),
                    ),
                ),
            )
            .id
        locationTrackDao
            .save(
                locationTrack(trackNumberId, name = "end branching track"),
                trackGeometry(
                    edge(
                        listOf(segment(Point(80.0, 0.0), Point(120.0, -2.0))),
                        startInnerSwitch = switchLinkYV(endSwitch.id, 1),
                        endInnerSwitch = switchLinkYV(endSwitch.id, 3),
                    ),
                    edge(
                        listOf(segment(Point(120.0, -2.0), Point(130.0, -2.0))),
                        startOuterSwitch = switchLinkYV(endSwitch.id, 3),
                    ),
                ),
            )
            .id

        val relinkingTrackGeometryInitially =
            locationTrackService.getWithGeometryOrThrow(mainDraftContext.context, relinkingTrack.id).second
        assertEquals(null, relinkingTrackGeometryInitially.edges[0].startNode.switchOut)
        assertEquals(null, relinkingTrackGeometryInitially.edges[0].endNode.switchOut)

        switchLinkingService.relinkTrack(LayoutBranch.main, relinkingTrack.id)
        val relinkingTrackGeometry =
            locationTrackService.getWithGeometryOrThrow(mainDraftContext.context, relinkingTrack.id).second
        assertEquals(1, relinkingTrackGeometry.edges.size)
        assertEquals(switchLinkYV(startSwitch.id, 1), relinkingTrackGeometry.edges[0].startNode.switchOut)
        assertEquals(switchLinkYV(endSwitch.id, 1), relinkingTrackGeometry.edges[0].endNode.switchOut)
    }

    private fun createDraftLocationTrackFromLayoutSegments(
        layoutSegments: List<LayoutSegment>
    ): Pair<LocationTrack, LocationTrackGeometry> {
        val (locationTrack, geometry) =
            locationTrackAndGeometry(
                trackNumberId = mainDraftContext.createLayoutTrackNumber().id,
                segments = layoutSegments,
                draft = true,
            )
        val locationTrackId = locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry).id
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
            val (locationTrack, geometry) =
                locationTrackAndAlignmentForGeometryAlignment(
                    trackNumber.id as IntId,
                    geometryAlignment,
                    transformationService.getTransformation(LAYOUT_SRID, LAYOUT_SRID),
                    draft = true,
                )
            locationTrackService.saveDraft(LayoutBranch.main, locationTrack, geometry)
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
    m: LineM<LocationTrackM>,
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
