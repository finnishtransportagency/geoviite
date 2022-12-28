package fi.fta.geoviite.infra.linking


import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.switchLibrary.SwitchAlignment
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.testdata.createSwitchAndAligments
import fi.fta.geoviite.infra.ui.testdata.locationTrackAndAlignmentForGeometryAlignment
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
    ) : ITTestBase() {

    lateinit var switchStructure: SwitchStructure
    lateinit var switchAlignment_1_5_2: SwitchAlignment

    @BeforeEach
    fun setup() {
        switchStructure = switchStructureDao.fetchSwitchStructures().find { s -> s.type.typeName == "YV60-300-1:9-O" }!!
        switchAlignment_1_5_2 =
            switchStructure.alignments.find { alignment -> alignment.jointNumbers.contains(JointNumber(5)) }
                ?: throw IllegalStateException("Invalid switch structure")
    }

    @Test
    fun getSuggestedSwitchesWorks() {
        val suggestions = switchLinkingService.getSuggestedSwitches(
            BoundingBox(
                x = Range(500000.0, 600000.0),
                y = Range(6900000.0, 7000000.0),
            )
        )
    }

    @Test
    fun updatingSwitchLinkingChangesSourceToGenerated() {
        val insertedSwitch = switchDao.fetch(
            switchDao.insert(
                switch(665)
            )
        )
        val switchLinkingParameters =
            SwitchLinkingParameters(
                layoutSwitchId = insertedSwitch.id as IntId,
                joints = emptyList(),
                geometrySwitchId = null,
                switchStructureId = insertedSwitch.switchStructureId
            )
        val rowVersion = switchLinkingService.saveSwitchLinking(switchLinkingParameters)
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
            segment(Point(start, start), Point(end, end), startLength = startLength)
                .also { s -> startLength += s.length }
        }

        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (locationTrack, locationTrackAlignment) = locationTrackAndAlignment(trackNumberId, segments)
        val locationTrackId = locationTrackService.saveDraft(locationTrack, locationTrackAlignment)

        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(665)))

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 9.5, y = 9.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        segmentM = 0.0
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
                        segmentM = 14.142135623730951
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
                        segmentM = 14.142135623730951
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
            segment(Point(start, start), Point(end, end), startLength = startLength)
                .also { s -> startLength += s.length }
        }

        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (locationTrack, locationTrackAlignment) = locationTrackAndAlignment(trackNumberId, segments)
        val locationTrackId = locationTrackService.saveDraft(locationTrack, locationTrackAlignment)

        val insertedSwitch = switchDao.fetch(switchDao.insert(switch(665)))

        val linkingJoints = listOf(
            SwitchLinkingJoint(
                JointNumber(1),
                Point(x = 9.5, y = 9.5),
                LocationAccuracy.DESIGNED_GEOLOCATION,
                segments = listOf(
                    SwitchLinkingSegment(
                        locationTrackId = locationTrackId.id,
                        segmentIndex = 1,
                        segmentM = 0.0
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
                        segmentM = 14.142135623730951
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
                        segmentM = 14.142135623730951
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

    private fun setupJointLocationAccuracyTest(): SuggestedSwitchCreateParams {
        val trackNumberId = trackNumberDao.insert(trackNumber(getUnusedTrackNumber())).id
        val (switch, switchAlignments) = createSwitchAndAligments(
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
                val (locationTrack, alignment) = locationTrackAndAlignmentForGeometryAlignment(trackNumberId, a)
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
            ), testFile()
        )
    )

    private fun assertJointPointLocationAccuracy(
        switch: SuggestedSwitch,
        jointNumber: JointNumber,
        locationAccuracy: LocationAccuracy?,
    ) = assertEquals(locationAccuracy, switch.joints.find { j -> j.number == jointNumber }!!.locationAccuracy)

}
