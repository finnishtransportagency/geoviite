package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.TestGeometryPlanService
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.pagemodel.map.E2EKmPostEditDialog
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ELayoutSwitchEditDialog
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ELocationTrackEditDialog
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ETrackLayoutPage
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import fi.fta.geoviite.infra.ui.testdata.locationTrack
import fi.fta.geoviite.infra.ui.testdata.pointsFromIncrementList
import fi.fta.geoviite.infra.ui.testdata.referenceLine
import fi.fta.geoviite.infra.ui.util.metersToDouble
import fi.fta.geoviite.infra.ui.util.pointToCoordinateString
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.TimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// the point where the map opens up by default
val DEFAULT_BASE_POINT = Point(385782.89, 6672277.83)

const val LINKING_TEST_PLAN_NAME = "Linking test plan"

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class LinkingTestUI @Autowired constructor(
    private val testGeometryPlanService: TestGeometryPlanService,
    private val switchDao: LayoutSwitchDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
) : SeleniumTest() {
    @BeforeEach
    fun setup() {
        clearAllTestData()
    }

    fun startGeoviiteAndGoToWork(): E2ETrackLayoutPage {
        startGeoviite()
        return goToMap().zoomToScale(E2ETrackLayoutPage.MapScale.M_500).also { it.toolBar.switchToDraft() }
    }

    @Test
    fun `Create a new location track and link geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val geometryPlan = testGeometryPlanService.buildPlan(trackNumberId).alignment(
            "geo-alignment-a", Point(50.0, 25.0),
            // points after the first one should preferably be Pythagorean pairs, so meter points line up nicely
            Point(20.0, 21.0), Point(40.0, 9.0)
        ).save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel

        selectionPanel.selectPlanAlignment(LINKING_TEST_PLAN_NAME, "geo-alignment-a")

        val alignmentA = getGeometryAlignmentFromPlan("geo-alignment-a", geometryPlan)

        val newLocationTrackName = "lt-A"
        createAndLinkLocationTrack(trackLayoutPage, alignmentA, "foo", newLocationTrackName)

        toolPanel.selectToolPanelTab("geo-alignment-a")

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking.linked)
        assertContains(selectionPanel.locationTracksList.items.map { lt -> lt.name }, newLocationTrackName)

        toolPanel.selectToolPanelTab(newLocationTrackName)

        val geometryTrackStartPoint = alignmentA.elements.first().start
        val geometryTrackEndPoint = alignmentA.elements.last().end
        val locationTrackLocationInfoBox = toolPanel.locationTrackLocation
        assertEquals(
            pointToCoordinateString(geometryTrackStartPoint),
            locationTrackLocationInfoBox.startCoordinates
        )
        assertEquals(
            pointToCoordinateString(geometryTrackEndPoint), locationTrackLocationInfoBox.endCoordinates
        )
    }

    @Test
    fun `Replace existing location track geometry with new geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(
                name = "A",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT - Point(10.0, 10.0),
                incrementPoints = listOf(Point(10.0, 20.0), Point(10.0, 15.0), Point(40.0, 47.0))
            )
        )

        val plan = testGeometryPlanService.buildPlan(trackNumberId).alignment(
            "replacement alignment", Point(10.0, 30.0), Point(3.0, 4.0), Point(9.0, 40.0)
        ).alignment("some other alignment", Point(20.0, 15.0), Point(3.0, 4.0), Point(9.0, 40.0)).save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel

        selectionPanel.selectLocationTrack("lt-A")
        toolPanel.locationTrackGeneralInfo.zoomTo()
        val locationTrackLengthBeforeLinking = toolPanel.locationTrackLocation.trueLengthDouble

        selectionPanel.selectPlanAlignment(LINKING_TEST_PLAN_NAME, "replacement alignment")

        val linkingBox = toolPanel.geometryAlignmentLinking
        linkingBox.initiateLinking()
        linkingBox.linkTo("lt-A")
        linkingBox.lock()

        val geometryAlignment = getGeometryAlignmentFromPlan("replacement alignment", plan)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val (locationTrackStartPoint, locationTrackEndPoint) = alignmentDao.fetch(
            locationTrackDao.fetch(
                originalLocationTrack
            ).alignmentVersion!!
        ).let { alignment ->
            alignment.start!! to alignment.end!!
        }

        trackLayoutPage.clickAtCoordinates(geometryTrackStartPoint)
        trackLayoutPage.clickAtCoordinates(geometryTrackEndPoint)

        trackLayoutPage.clickAtCoordinates(locationTrackEndPoint)
        trackLayoutPage.clickAtCoordinates(locationTrackStartPoint)

        linkingBox.link()
        waitAndClearToast("linking-succeeded-and-previous-unlinked")
        toolPanel.selectToolPanelTab("replacement alignment")

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking.linked)

        //Select twice for actually opening the location infobox
        toolPanel.selectToolPanelTab("lt-A")
        val locationTrackLengthAfterLinking = toolPanel.locationTrackLocation.trueLengthDouble

        assertNotEquals(locationTrackLengthBeforeLinking, locationTrackLengthAfterLinking)
        Assertions.assertThat(locationTrackLengthAfterLinking).isLessThan(locationTrackLengthBeforeLinking)

        assertEquals(
            pointToCoordinateString(geometryTrackStartPoint),
            toolPanel.locationTrackLocation.startCoordinates
        )
        assertEquals(
            pointToCoordinateString(geometryTrackEndPoint),
            toolPanel.locationTrackLocation.endCoordinates
        )
    }

    @Test
    fun `Edit location track end coordinate`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(
                name = "A",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT - Point(1.0, 1.0),
                incrementPoints = listOf(
                    Point(1.0, 2.0), Point(1.0, 1.5), Point(4.0, 4.7)
                )
            )
        )
        val locationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel

        selectionPanel.selectLocationTrack("lt-A")
        toolPanel.locationTrackGeneralInfo.zoomTo()
        val locationInfoBox = toolPanel.locationTrackLocation
        locationInfoBox.startLinking()

        val startPoint = locationTrackAlignment.segments.first().points.first()
        val endPoint = locationTrackAlignment.segments.last().points.last()
        val newEndPoint = locationTrackAlignment.segments.first().points.last()

        trackLayoutPage.clickAtCoordinates(endPoint)
        trackLayoutPage.clickAtCoordinates(newEndPoint)

        locationInfoBox.save()

        assertEquals(pointToCoordinateString(startPoint), locationInfoBox.startCoordinates)
        locationInfoBox.waitForEndCoordinatesChange(pointToCoordinateString(newEndPoint))
    }

    @Test
    fun `Edit location track start coordinate`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(name = "A",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT - Point(1.0, 1.0),
                incrementPoints = (1..10).map { Point(2.0, 3.0) })
        )
        val locationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val toolPanel = trackLayoutPage.toolPanel

        trackLayoutPage.selectionPanel.selectLocationTrack("lt-A")
        toolPanel.locationTrackGeneralInfo.zoomTo()
        val locationInfoBox = toolPanel.locationTrackLocation
        locationInfoBox.startLinking()

        val startPoint = locationTrackAlignment.segments.first().points.first()
        val newStartPoint = locationTrackAlignment.segments.first().points.last()
        val endPoint = locationTrackAlignment.segments.last().points.last()

        E2ETrackLayoutPage.finishLoading()
        trackLayoutPage.clickAtCoordinates(startPoint)
        trackLayoutPage.clickAtCoordinates(newStartPoint)

        locationInfoBox.save()

        locationInfoBox.waitForStartCoordinatesChange(pointToCoordinateString(newStartPoint))
        assertEquals(pointToCoordinateString(endPoint), locationInfoBox.endCoordinates)
    }


    @Test
    fun `Link geometry KM-Post to nearest track layout KM-post`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        kmPostDao.insert(kmPost(trackNumberId, KmNumber("0123"), DEFAULT_BASE_POINT + Point(5.0, 5.0)))
        kmPostDao.insert(kmPost(trackNumberId, KmNumber("0124"), DEFAULT_BASE_POINT + Point(17.0, 18.0)))

        testGeometryPlanService
            .buildPlan(trackNumberId)
            .alignment("foo bar", Point(4.0, 4.0), Point(14.0, 14.0), Point(58.0, 51.0))
            .kmPost("0123G", Point(4.0, 4.0))
            .kmPost("0124G", Point(14.0, 14.0))
            .kmPost("0125G", Point(24.0, 21.0))
            .kmPost("0126G", Point(34.0, 30.0))
            .save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel

        selectionPanel.selectKmPost("0123")
        toolPanel.layoutKmPostGeneral.zoomTo()
        val layoutKmPostCoordinatesBeforeLinking = toolPanel.layoutKmPostLocation.coordinates

        selectionPanel.selectPlanKmPost(LINKING_TEST_PLAN_NAME, "0123G")

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking
        kmPostLinkingInfoBox.initiateLinking()
        val firstTrackLayoutKmPost = kmPostLinkingInfoBox.trackLayoutKmPosts.first()
        assertEquals("012", firstTrackLayoutKmPost.substring(0, 3))

        kmPostLinkingInfoBox.linkTo(firstTrackLayoutKmPost)
        kmPostLinkingInfoBox.link()
        waitAndClearToast("linking-succeed-msg")

        assertEquals("KYLLÄ", toolPanel.geometryKmPostLinking.linked)
        toolPanel.selectToolPanelTab("0123")
        assertNotEquals(layoutKmPostCoordinatesBeforeLinking, toolPanel.layoutKmPostLocation.coordinates)
    }

    @Test
    fun `Link geometry KM-post to new KM-post`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        val lastKmPostLocation = Point(34.0, 30.0)

        testGeometryPlanService
            .buildPlan(trackNumberId)
            .alignment("foo bar", Point(4.0, 4.0), Point(14.0, 14.0), Point(58.0, 51.0))
            .kmPost("0123", Point(4.0, 4.0))
            .kmPost("0124", Point(14.0, 14.0))
            .kmPost("0125", Point(24.0, 21.0))
            .kmPost("0126", lastKmPostLocation)
            .save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel

        val geometryPlan = selectionPanel.geometryPlanByName(LINKING_TEST_PLAN_NAME)
        geometryPlan.selectKmPost("0126")

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking
        kmPostLinkingInfoBox.initiateLinking()

        val newKmPostNumber = "0003NW"
        kmPostLinkingInfoBox.createNewTrackLayoutKmPost()
            .setName(newKmPostNumber)
            .selectState(E2EKmPostEditDialog.State.IN_USE)
            .save()

        waitAndClearToast("insert-succeeded")

        kmPostLinkingInfoBox.link()
        waitAndClearToast("linking-succeed-msg")
        geometryPlan.selectKmPost("0126")

        selectionPanel.selectKmPost(newKmPostNumber)
        val layoutKmPost0003TLCoordinates = toolPanel.layoutKmPostLocation.coordinates
        assertEquals(
            pointToCoordinateString(lastKmPostLocation + DEFAULT_BASE_POINT),
            layoutKmPost0003TLCoordinates
        )
    }

    @Test
    fun `Link geometry switch to new location tracks and layout switch`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        val plan = testGeometryPlanService
            .buildPlan(trackNumberId)
            .switch("switch to link", "YV54-200N-1:9-O", Point(5.0, 5.0))
            .switch("unrelated switch", "YV54-200N-1:9-O", Point(15.0, 15.0))
            // switch to link is at (5, 5); the switch's alignment on the through track lies flat on the X axis from
            // 0 to 28.3, with the math point at 11.077, while the branching track goes down to (28.195, -1.902)
            .alignment("through track", Point(0.0, 5.0), Point(5.0, 0.0), Point(11.0, 0.0), Point(30.0, 0.0))
            .switchData("switch to link", null, 1)
            .switchData("switch to link", 1, 2)
            .switchData("switch to link", 2, 5)
            .alignment("branching track", Point(5.0, 5.0), Point(28.2, -2.0), Point(14.0, -2.0))
            .switchData("switch to link", 1, 3)
            .save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val toolPanel = trackLayoutPage.toolPanel

        val planPanel = trackLayoutPage.selectionPanel.geometryPlanByName(LINKING_TEST_PLAN_NAME)
        planPanel.selectSwitch("switch to link")
        toolPanel.geometrySwitchGeneral.zoomTo()

        val throughTrackGeometryAlignment = getGeometryAlignmentFromPlan("through track", plan)
        planPanel.selectAlignment("through track")

        //Create LT for SW1
        val throughTrack = "lt through track"
        createAndLinkLocationTrack(trackLayoutPage, throughTrackGeometryAlignment, "foo tracknumber", throughTrack)

        //Create LT for SW2
        val branchingTrackGeometryAlignment = getGeometryAlignmentFromPlan("branching track", plan)
        planPanel.selectAlignment("branching track")

        val branchingTrack = "lt branching track"
        createAndLinkLocationTrack(trackLayoutPage, branchingTrackGeometryAlignment, "foo tracknumber", branchingTrack)

        planPanel.selectSwitch("switch to link")

        val layoutSwitchName = "tl-sw-1"
        val switchLinkingInfoBox = toolPanel.geometrySwitchLinking
        switchLinkingInfoBox.initiateLinking()
        switchLinkingInfoBox.createNewTrackLayoutSwitch()
            .setName(layoutSwitchName)
            .selectStateCategory(E2ELayoutSwitchEditDialog.StateCategory.EXISTING)
            .save()

        waitAndClearToast("new-switch-added")

        switchLinkingInfoBox.linkTo(layoutSwitchName)
        switchLinkingInfoBox.link()
        waitAndClearToast("linking-succeed-msg")
        toolPanel.selectToolPanelTab(layoutSwitchName)

        val layoutSwitchInfoBox = toolPanel.layoutSwitchStructureGeneralInfo
        assertEquals("YV54-200N-1:9-O", layoutSwitchInfoBox.type)
        assertEquals("Oikea", layoutSwitchInfoBox.hand)
        assertEquals("Ei tiedossa", layoutSwitchInfoBox.trap)


        val geoSwitchLocation = getGeometrySwitchFromPlan("switch to link", plan).getJoint(JointNumber(1))?.location
        val layoutSwitchLocationInfoBox = toolPanel.layoutSwitchLocation
        assertEquals(
            pointToCoordinateString(geoSwitchLocation!!), layoutSwitchLocationInfoBox.coordinates
        )

        val switchLinesAndTracks = layoutSwitchLocationInfoBox.jointAlignments
        assertEquals("1-5-2", switchLinesAndTracks[0].switchLine)
        assertEquals(throughTrack, switchLinesAndTracks[0].switchTrack)
        assertEquals("1-3", switchLinesAndTracks[1].switchLine)
        assertEquals(branchingTrack, switchLinesAndTracks[1].switchTrack)
    }

    @Test
    fun `Continue location track using geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val plan =
            testGeometryPlanService
                .buildPlan(trackNumberId)
                .alignment("extending track", Point(0.0, 0.0), Point(4.0, 6.0), Point(4.0, 2.0))
                .alignment("unrelated track", Point(0.0, 10.0), Point(10.0, 3.0), Point(10.0, 1.0))
                .save()

        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(
                name = "track to extend",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT + Point(12.0, 12.0),
                incrementPoints = (1..10).map { Point(1.0, 1.0) },
            )
        )
        val originalLocationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val toolPanel = trackLayoutPage.toolPanel

        val geometryAlignment = getGeometryAlignmentFromPlan("extending track", plan)

        trackLayoutPage.selectionPanel.selectLocationTrack("lt-track to extend")
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation
        val locationTrackLengthBeforeLinking = metersToDouble(locationTrackLocationInfobox.trueLength)
        val locationTrackStartBeforeLinking = locationTrackLocationInfobox.startCoordinates
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.endCoordinates
        toolPanel.locationTrackGeneralInfo.zoomTo()
        trackLayoutPage.zoomToScale(E2ETrackLayoutPage.MapScale.M_10)

        trackLayoutPage.selectionPanel.selectPlanAlignment(LINKING_TEST_PLAN_NAME, "extending track")
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking
        alignmentLinkinInfobox.initiateLinking()
        alignmentLinkinInfobox.linkTo("lt-track to extend")
        alignmentLinkinInfobox.lock()

        trackLayoutPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.first())
        trackLayoutPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        trackLayoutPage.clickAtCoordinates(geometryAlignment.elements.first().start)

        alignmentLinkinInfobox.link()
        waitAndClearToast("linking-succeeded-and-previous-unlinked")
        toolPanel.selectToolPanelTab("lt-track to extend")
        val lengthAfterLinking = metersToDouble(locationTrackLocationInfobox.trueLength)

        Assertions.assertThat(locationTrackLengthBeforeLinking).isLessThan(lengthAfterLinking)
        val editedLocationTrack = getLocationTrackAndAlignment(PublishType.DRAFT, originalLocationTrack.id)

        //Check that there's a new segment between GT-end and old LT-start
        assertTrue(
            hasSegmentBetweenPoints(
                start = geometryAlignment.elements.last().end,
                end = originalLocationTrackAlignment.segments.first().points.first().toPoint(),
                layoutAlignment = editedLocationTrack.second,
            )
        )

        assertNotEquals(locationTrackStartBeforeLinking, locationTrackLocationInfobox.startCoordinates)
        assertEquals(
            pointToCoordinateString(geometryAlignment.elements.first().start),
            locationTrackLocationInfobox.startCoordinates
        )
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.endCoordinates)
    }

    @Test
    fun `Continue and replace location track using geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val plan =
            testGeometryPlanService
                .buildPlan(trackNumberId)
                .alignment("extending track", Point(0.0, 0.0), Point(4.0, 6.0), Point(4.0, 2.0))
                .alignment("unrelated track", Point(0.0, 10.0), Point(10.0, 3.0), Point(10.0, 1.0))
                .save()

        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(
                name = "track to extend",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT + Point(12.0, 12.0),
                incrementPoints = (1..10).map { Point(1.0, 1.0) },
            )
        )
        val originalLocationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val toolPanel = trackLayoutPage.toolPanel

        val geometryAlignment = getGeometryAlignmentFromPlan("extending track", plan)
        val geometryAlignmentStart = geometryAlignment.elements.first().start
        val geometryAlignmentEnd = geometryAlignment.elements.last().end

        trackLayoutPage.selectionPanel.selectLocationTrack("lt-track to extend")
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation
        val locationTrackLengthBeforeLinking = metersToDouble(locationTrackLocationInfobox.trueLength)
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.endCoordinates
        toolPanel.locationTrackGeneralInfo.zoomTo()
        trackLayoutPage.zoomToScale(E2ETrackLayoutPage.MapScale.M_10)

        trackLayoutPage.selectionPanel.selectPlanAlignment(LINKING_TEST_PLAN_NAME, "extending track")
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking
        alignmentLinkinInfobox.initiateLinking()
        alignmentLinkinInfobox.linkTo("lt-track to extend")
        alignmentLinkinInfobox.lock()
        E2ETrackLayoutPage.finishLoading()
        trackLayoutPage.clickAtCoordinates(geometryAlignmentStart)
        trackLayoutPage.clickAtCoordinates(geometryAlignmentEnd)
        trackLayoutPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.first())
        trackLayoutPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.last())
        alignmentLinkinInfobox.link()
        waitAndClearToast("linking-succeeded-and-previous-unlinked")

        val locationTrackAfterLinking = getLocationTrackAndAlignment(PublishType.DRAFT, originalLocationTrack.id)

        toolPanel.selectToolPanelTab("lt-track to extend")
        val lengthAfterLinking = metersToDouble(locationTrackLocationInfobox.trueLength)

        Assertions.assertThat(locationTrackLengthBeforeLinking).isLessThan(lengthAfterLinking)
        assertEquals(
            pointToCoordinateString(geometryAlignmentStart),
            locationTrackLocationInfobox.startCoordinates
        )
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.endCoordinates)
        assertTrue(
            hasSegmentBetweenPoints(
                start = geometryAlignmentEnd,
                end = originalLocationTrackAlignment.segments.first().points.last().toPoint(),
                layoutAlignment = locationTrackAfterLinking.second
            )
        )
    }

    @Test
    fun `link track to a reference line`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id

        val originalReferenceLine = referenceLine(
            trackNumber = trackNumberId,
            basePoint = DEFAULT_BASE_POINT + Point(15.0, 35.0),
            incrementPoints = listOf(Point(5.0, 10.0), Point(3.0, 5.0), Point(4.0, 5.0))
        )
        referenceLineDao.insert(
            originalReferenceLine.first.copy(
                alignmentVersion = alignmentDao.insert(
                    originalReferenceLine.second
                )
            )
        )

        val plan = testGeometryPlanService.buildPlan(trackNumberId).alignment(
            "replacement alignment", Point(10.0, 30.0), Point(3.0, 4.0), Point(9.0, 40.0)
        ).alignment("some other alignment", Point(20.0, 15.0), Point(3.0, 4.0), Point(9.0, 40.0)).save()

        val trackLayoutPage = startGeoviiteAndGoToWork()
        val toolPanel = trackLayoutPage.toolPanel

        trackLayoutPage.selectionPanel.selectPlanAlignment(LINKING_TEST_PLAN_NAME, "replacement alignment")
        val alignmentLinkingInfobox = toolPanel.geometryAlignmentLinking
        toolPanel.geometryAlignmentGeneral.zoomTo()
        alignmentLinkingInfobox.initiateLinking()
        alignmentLinkingInfobox.linkTo("foo tracknumber")
        alignmentLinkingInfobox.lock()

        val geometryAlignment = getGeometryAlignmentFromPlan("replacement alignment", plan)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val referenceLineStartPoint = originalReferenceLine.second.segments.first().points.first()
        val referenceLineEndPoint = originalReferenceLine.second.segments.last().points.last()

        E2ETrackLayoutPage.finishLoading()
        trackLayoutPage.clickAtCoordinates(geometryTrackStartPoint)
        trackLayoutPage.clickAtCoordinates(geometryTrackEndPoint)
        trackLayoutPage.clickAtCoordinates(referenceLineStartPoint)
        trackLayoutPage.clickAtCoordinates(referenceLineEndPoint)

        alignmentLinkingInfobox.link()
        waitAndClearToast("linking-succeeded-and-previous-unlinked")

        assertEquals("foo tracknumber", alignmentLinkingInfobox.trackNumber)
        toolPanel.selectToolPanelTab("foo tracknumber")

        val referenceLineLocationInfobox = toolPanel.referenceLineLocation

        assertEquals(
            pointToCoordinateString(geometryTrackStartPoint),
            referenceLineLocationInfobox.startCoordinates
        )
        assertEquals(
            pointToCoordinateString(geometryTrackEndPoint), referenceLineLocationInfobox.endCoordinates
        )
    }


    @Test
    fun `Delete location track`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)

        val originalLocationTrack = locationTrack(
            name = "track to delete",
            trackNumber = trackNumberId,
            basePoint = DEFAULT_BASE_POINT + Point(12.0, 12.0),
            incrementPoints = (1..10).map { Point(1.0, 1.0) },
        )
        saveLocationTrackWithAlignment(originalLocationTrack)
        saveLocationTrackWithAlignment(
            locationTrack(
                name = "unrelated track",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT + Point(18.0, 6.0),
                incrementPoints = (1..10).map { Point(1.0, -1.0) },
            )
        )

        val trackLayoutPage = startGeoviiteAndGoToWork()

        trackLayoutPage.selectionPanel.selectLocationTrack("lt-track to delete")
        trackLayoutPage.toolPanel.locationTrackGeneralInfo.zoomTo()
        trackLayoutPage.toolPanel.locationTrackGeneralInfo.edit()
            .selectState(E2ELocationTrackEditDialog.State.DELETED)
            .save()
        waitAndClearToast("deleted-successfully")

        assertTrue(trackLayoutPage.selectionPanel.locationTracksList.items.none { it.name == "lt-track to delete" })

        val locationTrackJ = originalLocationTrack.second.segments.first().points.first()
        val pointNearLocationTrackJStart = locationTrackJ.plus(Point(x = 2.0, y = 2.0))

        //Click at empty point and info box should be empty
        trackLayoutPage.clickAtCoordinates(pointNearLocationTrackJStart)

        try {
            trackLayoutPage.toolPanel.locationTrackGeneralInfo
        } catch (ex: Exception) {
            Assertions.assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }
    }


    @Test
    fun `Delete track layout switch`() {
        val switchToDelete = switch(
            123, name = "switch to delete", joints = listOf(
                TrackLayoutSwitchJoint(
                    JointNumber(1), Point(DEFAULT_BASE_POINT + Point(1.0, 1.0)), null
                ), TrackLayoutSwitchJoint(
                    JointNumber(3), Point(DEFAULT_BASE_POINT + Point(3.0, 3.0)), null
                )
            )
        )
        switchDao.insert(switchToDelete)

        // unrelated switch
        switchDao.insert(
            switch(
                124, name = "unrelated switch", joints = listOf(
                    TrackLayoutSwitchJoint(
                        JointNumber(1), Point(DEFAULT_BASE_POINT + Point(6.0, 1.0)), null
                    ),
                )
            )
        )
        val trackLayoutPage = startGeoviiteAndGoToWork()
        trackLayoutPage.zoomToScale(E2ETrackLayoutPage.MapScale.M_100)
        trackLayoutPage.selectionPanel.selectSwitch("switch to delete")
        trackLayoutPage.toolPanel.layoutSwitchGeneralInfo.zoomTo()

        trackLayoutPage.toolPanel.layoutSwitchGeneralInfo.edit()
            .selectStateCategory(E2ELayoutSwitchEditDialog.StateCategory.NOT_EXISTING)
            .save()
        waitAndClearToast("modified-successfully")

        trackLayoutPage.selectionPanel.waitUntilSwitchNotVisible("switch to delete")

        //Click near deleted element point to clear tool panels
        //and then try to select deleted element to confirm it disappeared
        val switchPoint = DEFAULT_BASE_POINT + Point(1.0, 1.0)
        trackLayoutPage.clickAtCoordinates(switchPoint + Point(x = 1.0, y = 1.0))
        trackLayoutPage.clickAtCoordinates(switchPoint)

        try {
            trackLayoutPage.toolPanel.layoutSwitchGeneralInfo
        } catch (ex: Exception) {
            Assertions.assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }
    }


    fun createAndLinkLocationTrack(
        trackLayoutPage: E2ETrackLayoutPage,
        geometryAlignment: GeometryAlignment,
        trackNumber: String,
        locationTrackName: String,
    ) {
        trackLayoutPage.toolPanel.geometryAlignmentGeneral.zoomTo()
        val alignmentLinkingInfoBox = trackLayoutPage.toolPanel.geometryAlignmentLinking

        alignmentLinkingInfoBox.initiateLinking()

        alignmentLinkingInfoBox.createNewLocationTrack()
            .setName(locationTrackName)
            .selectTrackNumber(trackNumber)
            .selectState(E2ELocationTrackEditDialog.State.IN_USE)
            .selectType(E2ELocationTrackEditDialog.Type.MAIN)
            .setDescription("manually created location track $locationTrackName")
            .setDescriptionSuffix(E2ELocationTrackEditDialog.DescriptionSuffix.NONE)
            .selectTopologicalConnectivity(E2ELocationTrackEditDialog.TopologicalConnectivity.NONE)
            .save()

        waitAndClearToast("created-successfully")

        alignmentLinkingInfoBox.linkTo(locationTrackName)
        alignmentLinkingInfoBox.lock()

        trackLayoutPage.clickAtCoordinates(geometryAlignment.elements.first().start)
        trackLayoutPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        alignmentLinkingInfoBox.link()
        waitAndClearToast("linking-succeeded")
    }

    private fun createAndInsertCommonReferenceLine(trackNumber: IntId<TrackLayoutTrackNumber>): LayoutAlignment {
        val points = pointsFromIncrementList(
            DEFAULT_BASE_POINT + Point(1.0, 1.0), listOf(
                Point(x = 2.0, y = 3.0), Point(x = 5.0, y = 12.0)
            )
        )

        val trackLayoutPoints = toTrackLayoutPoints(*points.toTypedArray())
        val alignment = alignment(segment(trackLayoutPoints))
        val commonReferenceLine = referenceLine(
            alignment = alignment,
            trackNumberId = trackNumber,
            startAddress = TrackMeter(KmNumber(0), 0),
        )
        referenceLineDao.insert(commonReferenceLine.copy(alignmentVersion = alignmentDao.insert(alignment)))
        return alignment
    }

    private fun getGeometryAlignmentFromPlan(alignmentName: String, geometryPlan: GeometryPlan) =
        geometryPlan.alignments.find { alignment -> alignment.name.toString() == alignmentName }!!

    private fun getGeometrySwitchFromPlan(switchName: String, geometryPlan: GeometryPlan) =
        geometryPlan.switches.find { switch -> switch.name.toString() == switchName }!!

    private fun hasSegmentBetweenPoints(start: Point, end: Point, layoutAlignment: LayoutAlignment): Boolean {
        return layoutAlignment.segments.any { segment -> segment.includes(start) && segment.includes(end) }
    }

    fun getLocationTrackAndAlignment(
        publishType: PublishType,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        val locationTrack = locationTrackDao.fetch(locationTrackDao.fetchVersion(id, publishType)!!)
        val alignment = alignmentDao.fetch(locationTrack.alignmentVersion!!)
        return locationTrack to alignment
    }

    private fun saveLocationTrackWithAlignment(
        locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>,
    ): RowVersion<LocationTrack> {
        return locationTrackDao.insert(
            locationTrackAndAlignment.first.copy(
                alignmentVersion = alignmentDao.insert(
                    locationTrackAndAlignment.second
                )
            )
        ).rowVersion
    }
}
