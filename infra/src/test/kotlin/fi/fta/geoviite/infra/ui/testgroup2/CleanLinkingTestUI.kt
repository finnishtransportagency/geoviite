package fi.fta.geoviite.infra.ui.testgroup2

import closeBrowser
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.*
import fi.fta.geoviite.infra.ui.testdata.*
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil
import fi.fta.geoviite.infra.util.FileName
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openqa.selenium.TimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

// the point where the map opens up by default
val DEFAULT_BASE_POINT = Point(385782.89, 6672277.83)

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class CleanLinkingTestUI @Autowired constructor(
    private val switchStructureDao: SwitchStructureDao,
    private val switchDao: LayoutSwitchDao,
    private val geometryDao: GeometryDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val locationTrackDao: LocationTrackDao,
    private val alignmentDao: LayoutAlignmentDao,
) : SeleniumTest() {
    lateinit var mapPage: MapPage
    lateinit var navigationPanel: MapNavigationPanel
    lateinit var toolPanel: MapToolPanel

    @BeforeEach
    fun setup() {
        clearAllTestData()
    }

    fun startGeoviiteAndGoToWork() {
        startGeoviite()
        mapPage = goToMap()
        mapPage.luonnostila()
        // largest scale where location tracks show up
        mapPage.zoomInToScale("500 m")
        navigationPanel = MapNavigationPanel()
        toolPanel = MapToolPanel()
    }

    @Test
    fun `Create a new location track and link geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val geometryPlan = buildPlan(trackNumberId)
            .alignment(
                "geo-alignment-a",
                Point(50.0, 25.0),
                // points after the first one should preferably be Pythagorean pairs, so meter points line up nicely
                Point(20.0, 21.0),
                Point(40.0, 9.0)
            )
            .save()

        startGeoviiteAndGoToWork()

        navigationPanel
            .geometryPlanByName("Linking geometry plan")
            .selecAlignment("geo-alignment-a")

        val alignmentA = getGeometryAlignmentFromPlan("geo-alignment-a", geometryPlan)

        val newLocationTrackName = "lt-A"
        createAndLinkLocationTrack(alignmentA, "foo", newLocationTrackName)

        toolPanel.selectToolPanelTab("geo-alignment-a")

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking().linkitetty())
        assertContains(navigationPanel.locationTracks().map { lt -> lt.name() }, newLocationTrackName)

        toolPanel.selectToolPanelTab(newLocationTrackName)

        val geometryTrackStartPoint = alignmentA.elements.first().start
        val geometryTrackEndPoint = alignmentA.elements.last().end
        val locationTrackLocationInfoBox = toolPanel.locationTrackLocation()
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackStartPoint),
            locationTrackLocationInfoBox.alkukoordinaatti()
        )
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackEndPoint),
            locationTrackLocationInfoBox.loppukoordinaatti()
        )
    }

    fun saveLocationTrackWithAlignment(locationTrackAndAlignment: Pair<LocationTrack, LayoutAlignment>): RowVersion<LocationTrack> {
        return locationTrackDao.insert(
            locationTrackAndAlignment.first.copy(
                alignmentVersion = alignmentDao.insert(
                    locationTrackAndAlignment.second
                )
            )
        ).rowVersion
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

        val plan = buildPlan(trackNumberId)
            .alignment("replacement alignment", Point(10.0, 30.0), Point(3.0, 4.0), Point(9.0, 40.0))
            .alignment("some other alignment", Point(20.0, 15.0), Point(3.0, 4.0), Point(9.0, 40.0))
            .save()

        startGeoviiteAndGoToWork()

        navigationPanel.selectLocationTrack("lt-A")
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        val locationTrackLengthBeforeLinking = toolPanel.locationTrackLocation().todellinenPituusDouble()

        navigationPanel.geometryPlanByName("Linking geometry plan")
            .selecAlignment("replacement alignment")

        val linkingBox = toolPanel.geometryAlignmentLinking()
        linkingBox.aloitaLinkitys()
        linkingBox.linkTo("lt-A")
        linkingBox.lukitseValinta()

        val geometryAlignment = getGeometryAlignmentFromPlan("replacement alignment", plan)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val (locationTrackStartPoint, locationTrackEndPoint) =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!).let { alignment ->
                alignment.start!! to alignment.end!!
            }

        mapPage.clickAtCoordinates(geometryTrackStartPoint)
        mapPage.clickAtCoordinates(geometryTrackEndPoint)

        mapPage.clickAtCoordinates(locationTrackEndPoint)
        mapPage.clickAtCoordinates(locationTrackStartPoint)

        linkingBox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")
        toolPanel.selectToolPanelTab("replacement alignment")

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking().linkitetty())

        //Select twice for actually opening the location infobox
        toolPanel.selectToolPanelTab("lt-A")
        val locationTrackLengthAfterLinking = toolPanel.locationTrackLocation().todellinenPituusDouble()

        assertNotEquals(locationTrackLengthBeforeLinking, locationTrackLengthAfterLinking)
        Assertions.assertThat(locationTrackLengthAfterLinking).isLessThan(locationTrackLengthBeforeLinking)

        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackStartPoint),
            toolPanel.locationTrackLocation().alkukoordinaatti()
        )
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackEndPoint),
            toolPanel.locationTrackLocation().loppukoordinaatti()
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
                    Point(1.0, 2.0),
                    Point(1.0, 1.5),
                    Point(4.0, 4.7)
                )
            )
        )
        val locationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        startGeoviiteAndGoToWork()

        navigationPanel.locationTracksList.selectByName("lt-A")
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        val locationInfoBox = toolPanel.locationTrackLocation()
        locationInfoBox.muokkaaAlkuLoppupistetta()

        val startPoint = locationTrackAlignment.segments.first().points.first()
        val endPoint = locationTrackAlignment.segments.last().points.last()
        val newEndPoint = locationTrackAlignment.segments.first().points.last()

        mapPage.clickAtCoordinates(endPoint)
        mapPage.clickAtCoordinates(newEndPoint)

        locationInfoBox.valmis().assertAndClose("Raiteen päätepisteet päivitetty")

        assertEquals(CommonUiTestUtil.pointToCoordinateString(startPoint), locationInfoBox.alkukoordinaatti())
        locationInfoBox.waitForLoppukoordinaatti(CommonUiTestUtil.pointToCoordinateString(newEndPoint))
    }


    @Test
    fun `Edit location track start coordinate`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val originalLocationTrack = saveLocationTrackWithAlignment(
            locationTrack(
                name = "A",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT - Point(1.0, 1.0),
                incrementPoints = (1..10).map { Point(2.0, 3.0) }
            )
        )
        val locationTrackAlignment =
            alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        startGeoviiteAndGoToWork()

        navigationPanel.locationTracksList.selectByName("lt-A")
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        val locationInfoBox = toolPanel.locationTrackLocation()
        locationInfoBox.muokkaaAlkuLoppupistetta()

        val startPoint = locationTrackAlignment.segments.first().points.first()
        val newStartPoint = locationTrackAlignment.segments.first().points.last()
        val endPoint = locationTrackAlignment.segments.last().points.last()

        mapPage.clickAtCoordinates(startPoint)
        mapPage.clickAtCoordinates(newStartPoint)

        locationInfoBox.valmis().assertAndClose("Raiteen päätepisteet päivitetty")

        locationInfoBox.waitForAlkukoordinaatti(CommonUiTestUtil.pointToCoordinateString(newStartPoint))
        assertEquals(CommonUiTestUtil.pointToCoordinateString(endPoint), locationInfoBox.loppukoordinaatti())
    }


    @Test
    fun `Link geometry KM-Post to nearest track layout KM-post`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        kmPostDao.insert(kmPost(trackNumberId, KmNumber("0123"), DEFAULT_BASE_POINT + Point(5.0, 5.0)))
        kmPostDao.insert(kmPost(trackNumberId, KmNumber("0124"), DEFAULT_BASE_POINT + Point(17.0, 18.0)))

        buildPlan(trackNumberId)
            .alignment("foo bar", Point(4.0, 4.0), Point(14.0, 14.0), Point(58.0, 51.0))
            .kmPost("0123", Point(4.0, 4.0))
            .kmPost("0124", Point(14.0, 14.0))
            .kmPost("0125", Point(24.0, 21.0))
            .kmPost("0126", Point(34.0, 30.0))
            .save()

        startGeoviiteAndGoToWork()

        // the problem: kmPosts() waits until there is a list of km posts at all, but the list we actually get depends
        // on the zoom level, such that being farther out skips more km posts; and 0123 gets skipped when we skip
        // the odd-numbered ones, as we do while zooming in at the start
        navigationPanel.kmPostsList.selectByName("0123")
        toolPanel.layoutKmPostGeneral().kohdistaKartalla()
        val layoutKmPostCoordinatesBeforeLinking = toolPanel.layoutKmPostLocation().koordinaatit()

        val geometryPlan =
            navigationPanel.geometryPlanByName(EspooTestData.GEOMETRY_PLAN_NAME).open().openKmPosts()
        val firstGeometryKmPost = geometryPlan.kmPosts().listItems().first()
        firstGeometryKmPost.select()

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking()
        kmPostLinkingInfoBox.aloitaLinkitys()
        val firstTrackLayoutKmPost = kmPostLinkingInfoBox.trackLayoutKmPosts().first()
        assertEquals(firstGeometryKmPost.name().substring(0, 3), firstTrackLayoutKmPost.substring(0, 3))

        kmPostLinkingInfoBox.linkTo(firstTrackLayoutKmPost)
        kmPostLinkingInfoBox.linkita().assertAndClose("Tasakilometripiste linkitetty")

        assertEquals("KYLLÄ", toolPanel.geometryKmPostLinking().linkitetty())
        toolPanel.selectToolPanelTab("0123", 2)
        assertNotEquals(layoutKmPostCoordinatesBeforeLinking, toolPanel.layoutKmPostLocation().koordinaatit())
    }

    @Test
    fun `Link geometry KM-post to new KM-post`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo")).id
        val lastKmPostLocation = Point(34.0, 30.0)
        buildPlan(trackNumberId)
            .alignment("foo bar", Point(4.0, 4.0), Point(14.0, 14.0), Point(58.0, 51.0))
            .kmPost("0123", Point(4.0, 4.0))
            .kmPost("0124", Point(14.0, 14.0))
            .kmPost("0125", Point(24.0, 21.0))
            .kmPost("0126", lastKmPostLocation)
            .save()

        startGeoviiteAndGoToWork()
        val geometryPlan =
            navigationPanel.geometryPlanByName(EspooTestData.GEOMETRY_PLAN_NAME).open().openKmPosts()
        val lastGeometryKmPost = geometryPlan.kmPosts().listItemByName("0126")
        lastGeometryKmPost.select()

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking()
        kmPostLinkingInfoBox.aloitaLinkitys()

        val newKmPostNumber = "0003NW"
        kmPostLinkingInfoBox.createNewTrackLayoutKmPost().editTasakmpistetunnus(newKmPostNumber)
            .editTila(KmPostEditDialog.TilaTyyppi.KAYTOSSA).tallenna(waitUntilRootIsStale = false)
            .assertAndClose("Uusi tasakilometripiste lisätty")

        kmPostLinkingInfoBox.linkita().assertAndClose("Tasakilometripiste linkitetty onnistuneesti")
        lastGeometryKmPost.select()

        navigationPanel.selectTrackLayoutKmPost(newKmPostNumber)
        val layoutKmPost0003TLCoordinates = toolPanel.layoutKmPostLocation().koordinaatit()
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(lastKmPostLocation + DEFAULT_BASE_POINT),
            layoutKmPost0003TLCoordinates
        )
    }

    @Test
    fun `Link geometry switch to new location tracks and layout switch`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        val plan = buildPlan(trackNumberId)
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

        startGeoviiteAndGoToWork()

        val planPanel = navigationPanel.geometryPlanByName("Linking geometry plan")
        planPanel.selectSwitch("switch to link")
        toolPanel.geometrySwitchGeneral().kohdistaKartalla()

        val throughTrackGeometryAlignment = getGeometryAlignmentFromPlan("through track", plan)
        planPanel.selecAlignment("through track")

        //Create LT for SW1
        val throughTrack = "lt through track"
        createAndLinkLocationTrack(throughTrackGeometryAlignment, "foo tracknumber", throughTrack)

        //Create LT for SW2
        val branchingTrackGeometryAlignment = getGeometryAlignmentFromPlan("branching track", plan)
        planPanel.selecAlignment("branching track")

        val branchingTrack = "lt branching track"
        createAndLinkLocationTrack(branchingTrackGeometryAlignment, "foo tracknumber", branchingTrack)

        planPanel.selectSwitch("switch to link")

        val layoutSwitchName = "tl-sw-1"
        val switchLinkingInfoBox = toolPanel.geometrySwitchLinking()
        switchLinkingInfoBox.aloitaLinkitys()
        switchLinkingInfoBox.createNewTrackLayoutSwitch().editVaihdetunnus(layoutSwitchName)
            .editTilakategoria(CreateEditLayoutSwitchDialog.Tilakategoria.OLEMASSA_OLEVA_KOHDE).tallenna()
            .assertAndClose("Uusi vaihde lisätty")

        switchLinkingInfoBox.linkTo(layoutSwitchName)
        switchLinkingInfoBox.linkita().assertAndClose("Vaihde linkitetty")
        toolPanel.selectToolPanelTab(layoutSwitchName)

        val layoutSwitchInfoBox = toolPanel.layoutSwitchStructureGeneralInfo()
        assertEquals("YV54-200N-1:9-O", layoutSwitchInfoBox.tyyppi())
        assertEquals("Oikea", layoutSwitchInfoBox.katisyys())
        assertEquals("Ei tiedossa", layoutSwitchInfoBox.turvavaihde())


        val geoSwitchLocation =
            getGeometrySwitchFromPlan("switch to link", plan).getJoint(JointNumber(1))?.location
        val layoutSwitchLocationInfoBox = toolPanel.layoutSwitchLocation()
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geoSwitchLocation!!),
            layoutSwitchLocationInfoBox.koordinaatit()
        )

        val switchLinesAndTracks = layoutSwitchLocationInfoBox.vaihteenLinjat()
        assertEquals("1-5-2", switchLinesAndTracks[0].switchLine)
        assertEquals(throughTrack, switchLinesAndTracks[0].switchTrack)
        assertEquals("1-3", switchLinesAndTracks[1].switchLine)
        assertEquals(branchingTrack, switchLinesAndTracks[1].switchTrack)
    }

    @Test
    fun `Continue location track using geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val plan = buildPlan(trackNumberId)
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

        startGeoviiteAndGoToWork()

        val geometryAlignment = getGeometryAlignmentFromPlan("extending track", plan)

        navigationPanel.locationTracksList.selectByName("lt-track to extend")
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation()
        val locationTrackLengthBeforeLinking =
            CommonUiTestUtil.metersToDouble(locationTrackLocationInfobox.todellinenPituus())
        val locationTrackStartBeforeLinking = locationTrackLocationInfobox.alkukoordinaatti()
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.loppukoordinaatti()
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        mapPage.zoomOutToScale("10 m")

        selectPlanAlignment("extending track")
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking()
        alignmentLinkinInfobox.aloitaLinkitys()
        alignmentLinkinInfobox.linkTo("lt-track to extend")
        alignmentLinkinInfobox.lukitseValinta()

        mapPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.first())
        mapPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        mapPage.clickAtCoordinates(geometryAlignment.elements.first().start)

        alignmentLinkinInfobox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")
        toolPanel.selectToolPanelTab("lt-track to extend")
        val lengthAfterLinking = CommonUiTestUtil.metersToDouble(locationTrackLocationInfobox.todellinenPituus())

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

        assertNotEquals(locationTrackStartBeforeLinking, locationTrackLocationInfobox.alkukoordinaatti())
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryAlignment.elements.first().start),
            locationTrackLocationInfobox.alkukoordinaatti()
        )
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.loppukoordinaatti())
    }

    @Test
    fun `Continue and replace location track using geometry`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)
        val plan = buildPlan(trackNumberId)
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

        startGeoviiteAndGoToWork()

        val geometryAlignment = getGeometryAlignmentFromPlan("extending track", plan)
        val geometryAlignmentStart = geometryAlignment.elements.first().start
        val geometryAlignmentEnd = geometryAlignment.elements.last().end

        navigationPanel.locationTracksList.selectByName("lt-track to extend")
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation()
        val locationTrackLengthBeforeLinking =
            CommonUiTestUtil.metersToDouble(locationTrackLocationInfobox.todellinenPituus())
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.loppukoordinaatti()
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        mapPage.zoomOutToScale("10 m")

        selectPlanAlignment("extending track")
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking()
        alignmentLinkinInfobox.aloitaLinkitys()
        alignmentLinkinInfobox.linkTo("lt-track to extend")
        alignmentLinkinInfobox.lukitseValinta()
        MapPage.finishLoading()
        mapPage.clickAtCoordinates(geometryAlignmentStart)
        mapPage.clickAtCoordinates(geometryAlignmentEnd)
        mapPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.first())
        mapPage.clickAtCoordinates(originalLocationTrackAlignment.segments.first().points.last())
        alignmentLinkinInfobox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")

        val locationTrackAfterLinking = getLocationTrackAndAlignment(PublishType.DRAFT, originalLocationTrack.id)

        toolPanel.selectToolPanelTab("lt-track to extend")
        val lengthAfterLinking = CommonUiTestUtil.metersToDouble(locationTrackLocationInfobox.todellinenPituus())

        Assertions.assertThat(locationTrackLengthBeforeLinking).isLessThan(lengthAfterLinking)
        assertEquals(CommonUiTestUtil.pointToCoordinateString(geometryAlignmentStart), locationTrackLocationInfobox.alkukoordinaatti())
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.loppukoordinaatti())
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

        val originalReferenceLine =
            referenceLine(
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

        val plan = buildPlan(trackNumberId)
            .alignment("replacement alignment", Point(10.0, 30.0), Point(3.0, 4.0), Point(9.0, 40.0))
            .alignment("some other alignment", Point(20.0, 15.0), Point(3.0, 4.0), Point(9.0, 40.0))
            .save()

        startGeoviiteAndGoToWork()

        selectPlanAlignment("replacement alignment")
        val alignmentLinkingInfobox = toolPanel.geometryAlignmentLinking()
        toolPanel.geometryAlignmentGeneral().kohdistaKartalla()
        alignmentLinkingInfobox.aloitaLinkitys()
        alignmentLinkingInfobox.linkTo("foo tracknumber")
        alignmentLinkingInfobox.lukitseValinta()

        val geometryAlignment = getGeometryAlignmentFromPlan("replacement alignment", plan)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val referenceLineStartPoint = originalReferenceLine.second.segments.first().points.first()
        val referenceLineEndPoint = originalReferenceLine.second.segments.last().points.last()

        MapPage.finishLoading()
        mapPage.clickAtCoordinates(geometryTrackStartPoint)
        mapPage.clickAtCoordinates(geometryTrackEndPoint)
        mapPage.clickAtCoordinates(referenceLineStartPoint)
        mapPage.clickAtCoordinates(referenceLineEndPoint)

        alignmentLinkingInfobox.linkita().assertAndClose("Raide linkitetty")

        assertEquals("foo tracknumber", alignmentLinkingInfobox.pituusmittauslinja())
        toolPanel.selectToolPanelTab("foo tracknumber")

        val referenceLineLocationInfobox = toolPanel.referenceLineLocation()

        assertEquals(CommonUiTestUtil.pointToCoordinateString(geometryTrackStartPoint), referenceLineLocationInfobox.alkukoordinaatti())
        assertEquals(CommonUiTestUtil.pointToCoordinateString(geometryTrackEndPoint), referenceLineLocationInfobox.loppukoordinaatti())
    }


    @Test
    fun `Delete location track`() {
        val trackNumberId = trackNumberDao.insert(createTrackLayoutTrackNumber("foo tracknumber")).id
        createAndInsertCommonReferenceLine(trackNumberId)

        val originalLocationTrack =
            locationTrack(
                name = "track to delete",
                trackNumber = trackNumberId,
                basePoint = DEFAULT_BASE_POINT + Point(12.0, 12.0),
                incrementPoints = (1..10).map { Point(1.0, 1.0) },
            )
        saveLocationTrackWithAlignment(originalLocationTrack)
        saveLocationTrackWithAlignment(locationTrack(
            name = "unrelated track",
            trackNumber = trackNumberId,
            basePoint = DEFAULT_BASE_POINT + Point(18.0, 6.0),
            incrementPoints = (1..10).map { Point(1.0, -1.0) },
        ))

        startGeoviiteAndGoToWork()

        mapPage.navigationPanel.locationTracksList.selectByName("lt-track to delete")
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        mapPage.toolPanel.locationTrackGeneralInfo().muokkaaTietoja().editTila(CreateEditLocationTrackDialog.TilaTyyppi.POISTETTU).tallenna()
            .assertAndClose("Sijaintiraide poistettu")

        assertTrue(mapPage.navigationPanel.locationTracksList.items.none { it.name == "lt-track to delete" })

        val locationTrackJ = originalLocationTrack.second.segments.first().points.first()
        val pointNearLocationTrackJStart = locationTrackJ.plus(Point(x = 2.0, y = 2.0))

        //Click at empty point and info box should be empty
        mapPage.clickAtCoordinates(pointNearLocationTrackJStart)

        try {
            mapPage.toolPanel.locationTrackGeneralInfo()
        } catch (ex: Exception) {
            Assertions.assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }
    }


    @Test
    fun `Delete track layout switch`() {
        val switchToDelete = switch(
            123,
            name = "switch to delete",
            joints = listOf(
                TrackLayoutSwitchJoint(
                    JointNumber(1),
                    Point(DEFAULT_BASE_POINT + Point(1.0, 1.0)),
                    null
                ),
                TrackLayoutSwitchJoint(
                    JointNumber(3),
                    Point(DEFAULT_BASE_POINT + Point(3.0, 3.0)),
                    null
                )
            )
        )
        switchDao.insert(switchToDelete)
        
        // unrelated switch
        switchDao.insert(switch(124, name = "unrelated switch",
            joints = listOf(
                TrackLayoutSwitchJoint(
                    JointNumber(1),
                    Point(DEFAULT_BASE_POINT + Point(6.0, 1.0)),
                    null
                ),
            )))
        startGeoviiteAndGoToWork()
        mapPage.zoomInToScale("100 m")
        mapPage.navigationPanel.selectTrackLayoutSwitch("switch to delete")
        toolPanel.layoutSwitchGeneralInfo().kohdistaKartalla()

        mapPage.toolPanel.layoutSwitchGeneralInfo().muokkaaTietoja().editTilakategoria(CreateEditLayoutSwitchDialog.Tilakategoria.POISTUNUT_KOHDE)
            .tallenna().assertAndClose("Vaihteen tiedot päivitetty")

        mapPage.navigationPanel.waitUntilSwitchNotVisible("switch to delete")

        //Click near deleted element point to clear tool panels
        //and then try to select deleted element to confirm it disappeared
        val switchPoint = DEFAULT_BASE_POINT + Point(1.0, 1.0)
        mapPage.clickAtCoordinates(switchPoint + Point(x = 1.0, y = 1.0))
        mapPage.clickAtCoordinates(switchPoint)

        try {
            mapPage.toolPanel.layoutSwitchGeneralInfo()
        } catch (ex: Exception) {
            Assertions.assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }
    }


    fun createAndLinkLocationTrack(
        geometryAlignment: GeometryAlignment,
        trackNumber: String,
        locationTrackName: String
    ) {
        toolPanel.geometryAlignmentGeneral().kohdistaKartalla()
        val alignmentLinkingInfoBox = toolPanel.geometryAlignmentLinking()

        alignmentLinkingInfoBox.aloitaLinkitys()

        alignmentLinkingInfoBox.createNewLocationTrack().editSijaintiraidetunnus(locationTrackName)
            .editExistingRatanumero(trackNumber).editTila(CreateEditLocationTrackDialog.TilaTyyppi.KAYTOSSA)
            .editRaidetyyppi(CreateEditLocationTrackDialog.RaideTyyppi.PAARAIDE)
            .editKuvaus("manually created location track $locationTrackName")
            .editTopologinenKytkeytyminen(CreateEditLocationTrackDialog.TopologinenKytkeytyminen.EI_KYTKETTY)
            .tallenna(false).assertAndClose("Uusi sijaintiraide lisätty rekisteriin")

        alignmentLinkingInfoBox.linkTo(locationTrackName)
        alignmentLinkingInfoBox.lukitseValinta()

        mapPage.clickAtCoordinates(geometryAlignment.elements.first().start)
        mapPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        alignmentLinkingInfoBox.linkita().assertAndClose("Raide linkitetty")
    }

    fun geometryPlan(
        trackLayoutTrackNumberId: IntId<TrackLayoutTrackNumber>,
        switches: List<GeometrySwitch> = listOf(),
        alignments: List<GeometryAlignment> = listOf(),
        kmPosts: List<GeometryKmPost> = listOf(),
    ): GeometryPlan {

        return GeometryPlan(
            source = PlanSource.GEOMETRIAPALVELU,
            project = createProject(EspooTestData.GEOMETRY_PLAN_NAME),
            application = application(),
            author = null,
            planTime = null,
            units = tmi35GeometryUnit(),
            trackNumberId = trackLayoutTrackNumberId,
            switches = switches,
            alignments = alignments,
            kmPosts = kmPosts,
            fileName = FileName("espoo_test_data.xml"),
            pvDocumentId = null,
            planPhase = PlanPhase.RAILWAY_PLAN,
            decisionPhase = PlanDecisionPhase.APPROVED_PLAN,
            measurementMethod = MeasurementMethod.VERIFIED_DESIGNED_GEOMETRY,
            message = null,
            uploadTime = Instant.now(),
            trackNumberDescription = PlanElementName("diipa daapa")
        )
    }


    private fun createAndInsertCommonReferenceLine(trackNumber: IntId<TrackLayoutTrackNumber>): LayoutAlignment {
        val points = pointsFromIncrementList(
            DEFAULT_BASE_POINT + Point(1.0, 1.0),
            listOf(
                Point(x = 2.0, y = 3.0),
                Point(x = 5.0, y = 12.0)
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

    fun saveAndRefetchGeometryPlan(plan: GeometryPlan, boundingBox: List<Point>): GeometryPlan {
        return geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan,
                testFile(),
                boundingBox,
            )
        )
    }

    class BuildGeometryAlignment(val name: String, val firstPoint: Point, val incrementPoints: List<Point>) {
        val switchData: MutableList<SwitchData> = mutableListOf()
    }

    inner class BuildGeometryPlan(val trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val alignments: MutableList<BuildGeometryAlignment> = mutableListOf()
        val kmPosts: MutableList<GeometryKmPost> = mutableListOf()
        val switches: MutableList<GeometrySwitch> = mutableListOf()

        fun alignment(name: String, firstPoint: Point, vararg incrementPoints: Point): BuildGeometryPlan {
            alignments.add(BuildGeometryAlignment(name, firstPoint, incrementPoints.asList()))
            return this
        }

        fun switchData(switchName: String, startJointNumber: Int?, endJointNumber: Int?): BuildGeometryPlan {
            alignments.last().switchData.add(
                SwitchData(
                    StringId(switchName),
                    startJointNumber?.let(::JointNumber),
                    endJointNumber?.let(::JointNumber),
                )
            )
            return this
        }


        fun kmPost(name: String, location: Point): BuildGeometryPlan {
            kmPosts.add(createGeometryKmPost(trackNumberId, DEFAULT_BASE_POINT + location, name))
            return this
        }

        fun switch(name: String, typeName: String, location: Point, rotationRad: Double = 0.0): BuildGeometryPlan {
            val switchStructure =
                switchStructureDao.fetchSwitchStructures().first { structure -> structure.type.typeName == typeName }

            switches.add(GeometrySwitch(
                id = StringId(name),
                name = SwitchName(name),
                typeName = GeometrySwitchTypeName(typeName),
                switchStructureId = switchStructure.id as IntId<SwitchStructure>,
                state = PlanState.EXISTING,
                joints = switchStructure.joints.map { ssj ->
                    GeometrySwitchJoint(
                        ssj.number,
                        DEFAULT_BASE_POINT + location + rotateAroundOrigin(rotationRad, ssj.location),
                    )
                }
            ))
            return this
        }

        fun save(): GeometryPlan {
            val builtAlignments = alignments.map { build ->
                createGeometryAlignment(
                    alignmentName = build.name,
                    trackNumberId = trackNumberId,
                    basePoint = DEFAULT_BASE_POINT + build.firstPoint,
                    incrementPoints = build.incrementPoints,
                    switchData = build.switchData,
                )
            }
            return saveAndRefetchGeometryPlan(
                geometryPlan(trackNumberId, alignments = builtAlignments, kmPosts = kmPosts, switches = switches),
                boundingPolygonPointsByConvexHull(
                    builtAlignments.flatMap { alignment -> alignment.elements.flatMap { element -> element.bounds } } +
                            kmPosts.mapNotNull { kmPost -> kmPost.location },
                    LAYOUT_CRS
                )
            )
        }
    }

    fun buildPlan(trackNumberId: IntId<TrackLayoutTrackNumber>) = BuildGeometryPlan(trackNumberId)

    private fun selectPlanAlignment(alignmentName: String) =
        navigationPanel.geometryPlanByName("Linking geometry plan").selecAlignment(alignmentName)
}
