package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.geometry.*
import fi.fta.geoviite.infra.inframodel.PlanElementName
import fi.fta.geoviite.infra.math.*
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.CreateEditLocationTrackDialog
import fi.fta.geoviite.infra.ui.pagemodel.map.MapNavigationPanel
import fi.fta.geoviite.infra.ui.pagemodel.map.MapPage
import fi.fta.geoviite.infra.ui.pagemodel.map.MapToolPanel
import fi.fta.geoviite.infra.ui.testdata.*
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil
import fi.fta.geoviite.infra.util.FileName
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

        MapNavigationPanel()
            .geometryPlanByName("Linking geometry plan")
            .selecAlignment("geo-alignment-a")

        val alignmentA = getGeometryAlignmentFromPlan("geo-alignment-a", geometryPlan)

        val newLocationTrackName = "lt-A"
        createAndLinkLocationTrack(alignmentA, "foo", newLocationTrackName)

        MapToolPanel().selectToolPanelTab("geo-alignment-a")

        assertEquals("Kyllä", MapToolPanel().geometryAlignmentLinking().linkitetty())
        assertContains(MapNavigationPanel().locationTracks().map { lt -> lt.name() }, newLocationTrackName)

        MapToolPanel().selectToolPanelTab(newLocationTrackName)

        val geometryTrackStartPoint = alignmentA.elements.first().start
        val geometryTrackEndPoint = alignmentA.elements.last().end
        val locationTrackLocationInfoBox = MapToolPanel().locationTrackLocation()
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

        MapNavigationPanel().selectLocationTrack("lt-A")
        MapToolPanel().locationTrackGeneralInfo().kohdistaKartalla()
        val locationTrackLengthBeforeLinking = MapToolPanel().locationTrackLocation().todellinenPituusDouble()

        MapNavigationPanel().geometryPlanByName("Linking geometry plan")
            .selecAlignment("replacement alignment")

        val linkingBox = MapToolPanel().geometryAlignmentLinking()
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
        MapToolPanel().selectToolPanelTab("replacement alignment")

        assertEquals("Kyllä", MapToolPanel().geometryAlignmentLinking().linkitetty())

        //Select twice for actually opening the location infobox
        MapToolPanel().selectToolPanelTab("lt-A")
        val locationTrackLengthAfterLinking = MapToolPanel().locationTrackLocation().todellinenPituusDouble()

        assertNotEquals(locationTrackLengthBeforeLinking, locationTrackLengthAfterLinking)
        Assertions.assertThat(locationTrackLengthAfterLinking).isLessThan(locationTrackLengthBeforeLinking)

        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackStartPoint),
            MapToolPanel().locationTrackLocation().alkukoordinaatti()
        )
        assertEquals(
            CommonUiTestUtil.pointToCoordinateString(geometryTrackEndPoint),
            MapToolPanel().locationTrackLocation().loppukoordinaatti()
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
                    Point(4.0, 4.7))
            )
        )
        val locationTrackAlignment = alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        startGeoviiteAndGoToWork()

        MapNavigationPanel().locationTracksList.selectByName("lt-A")
        MapToolPanel().locationTrackGeneralInfo().kohdistaKartalla()
        val locationInfoBox = MapToolPanel().locationTrackLocation()
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
        val locationTrackAlignment = alignmentDao.fetch(locationTrackDao.fetch(originalLocationTrack).alignmentVersion!!)

        startGeoviiteAndGoToWork()

        MapNavigationPanel().locationTracksList.selectByName("lt-A")
        MapToolPanel().locationTrackGeneralInfo().kohdistaKartalla()
        val locationInfoBox = MapToolPanel().locationTrackLocation()
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
            .kmPost("0124",  Point(14.0, 14.0))
            .kmPost("0125",  Point(24.0, 21.0))
            .kmPost("0126",  Point(34.0, 30.0))
            .save()

        startGeoviiteAndGoToWork()

        MapNavigationPanel().selectTrackLayoutKmPost("0123")
        MapToolPanel().layoutKmPostGeneral().kohdistaKartalla()
        val layoutKmPostCoordinatesBeforeLinking = MapToolPanel().layoutKmPostLocation().koordinaatit()

        val geometryPlan = MapNavigationPanel().geometryPlanByName(EspooTestData.GEOMETRY_PLAN_NAME).open().openKmPosts()
        val firstGeometryKmPost = geometryPlan.kmPosts().listItems().first()
        firstGeometryKmPost.select()

        val kmPostLinkingInfoBox = MapToolPanel().geometryKmPostLinking()
        kmPostLinkingInfoBox.aloitaLinkitys()
        val firstTrackLayoutKmPost = kmPostLinkingInfoBox.trackLayoutKmPosts().first()
        assertEquals(firstGeometryKmPost.name().substring(0, 3), firstTrackLayoutKmPost.substring(0, 3))

        kmPostLinkingInfoBox.linkTo(firstTrackLayoutKmPost)
        kmPostLinkingInfoBox.linkita().assertAndClose("Tasakilometripiste linkitetty")

        assertEquals("KYLLÄ", MapToolPanel().geometryKmPostLinking().linkitetty())
        MapToolPanel().selectToolPanelTab("0123", 2)
        assertNotEquals(layoutKmPostCoordinatesBeforeLinking, MapToolPanel().layoutKmPostLocation().koordinaatit())
    }

    fun createAndLinkLocationTrack(
        geometryAlignment: GeometryAlignment,
        trackNumber: String,
        locationTrackName: String
    ) {
        MapToolPanel().geometryAlignmentGeneral().kohdistaKartalla()
        val alignmentLinkingInfoBox = MapToolPanel().geometryAlignmentLinking()

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

    fun saveAndRefetchGeometryPlan(plan: GeometryPlan, boundingBox: List<Point>): GeometryPlan {
        return geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan,
                testFile(),
                boundingBox,
            )
        )
    }

    inner class BuildGeometryPlan(val trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val alignments: MutableList<GeometryAlignment> = mutableListOf()
        val kmPosts: MutableList<GeometryKmPost> = mutableListOf()

        fun alignment(name: String, vararg vectors: Point): BuildGeometryPlan {
            alignments.add(
                createGeometryAlignment(
                    alignmentName = name,
                    trackNumberId = trackNumberId,
                    basePoint = DEFAULT_BASE_POINT,
                    incrementPoints = vectors.asList()
                )
            )
            return this
        }

        fun kmPost(name: String, location: Point): BuildGeometryPlan {
            kmPosts.add(createGeometryKmPost(trackNumberId, DEFAULT_BASE_POINT + location, name))
            return this
        }

        fun save(): GeometryPlan {
            return saveAndRefetchGeometryPlan(
                geometryPlan(trackNumberId, alignments = alignments, kmPosts = kmPosts),
                boundingPolygonPointsByConvexHull(
                    alignments.flatMap { alignment -> alignment.elements.flatMap { element -> element.bounds } } +
                            kmPosts.mapNotNull { kmPost -> kmPost.location },
                    LAYOUT_CRS
                )
            )
        }
    }

    fun buildPlan(trackNumberId: IntId<TrackLayoutTrackNumber>) = BuildGeometryPlan(trackNumberId)

}
