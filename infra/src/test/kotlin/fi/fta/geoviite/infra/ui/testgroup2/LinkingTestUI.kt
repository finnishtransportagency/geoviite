package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.geography.boundingPolygonPointsByConvexHull
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.frontpage.PublicationDetailRowContent
import fi.fta.geoviite.infra.ui.pagemodel.map.*
import fi.fta.geoviite.infra.ui.pagemodel.map.CreateEditLayoutSwitchDialog.Tilakategoria
import fi.fta.geoviite.infra.ui.pagemodel.map.CreateEditLocationTrackDialog.RaideTyyppi
import fi.fta.geoviite.infra.ui.pagemodel.map.CreateEditLocationTrackDialog.TilaTyyppi
import fi.fta.geoviite.infra.ui.testdata.EspooTestData
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEOMETRY_PLAN_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_A_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_B_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_C_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_D_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_E_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_F_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_ALIGNMENT_I_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_SWITCH_1_ALIGNMENT_NAMES
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_SWITCH_1_NAME
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.GEO_SWITCH_1_STRUCTURE
import fi.fta.geoviite.infra.ui.testdata.EspooTestData.Companion.REFERENCE_LINE_1_NAME
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.metersToDouble
import fi.fta.geoviite.infra.ui.util.CommonUiTestUtil.Companion.pointToCoordinateString
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.openqa.selenium.TimeoutException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue


@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class LinkingTestUI @Autowired constructor(
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
    val navigationPanel: MapNavigationPanel = MapNavigationPanel()
    val toolPanel: MapToolPanel = MapToolPanel()

    lateinit var LOCATION_TRACK_B: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_D_1: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_D_2: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_E: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_E_ID: IntId<LocationTrack>
    lateinit var LOCATION_TRACK_F: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_F_ID: IntId<LocationTrack>
    lateinit var LOCATION_TRACK_G: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_H: Pair<LocationTrack, LayoutAlignment>
    lateinit var LOCATION_TRACK_J: Pair<LocationTrack, LayoutAlignment>

    lateinit var TRACK_LAYOUT_SWITCH_A: TrackLayoutSwitch

    lateinit var LAYOUT_KM_POST_0000: TrackLayoutKmPost

    lateinit var REFERENCE_LINE_ESP1: Pair<ReferenceLine, LayoutAlignment>
    lateinit var REFERENCE_LINE_ESP2: Pair<ReferenceLine, LayoutAlignment>
    lateinit var REFERENCE_LINE_ESP3: Pair<ReferenceLine, LayoutAlignment>


    lateinit var GEOMETRY_PLAN: GeometryPlan

    val ESPOO_TRACK_NUMBER_1 = "ESP1"
    val ESPOO_TRACK_NUMBER_2 = "ESP2"
    val ESPOO_TRACK_NUMBER_3 = "ESP3"

    fun clearDrafts() {
        locationTrackDao.deleteDrafts()
        referenceLineDao.deleteDrafts()
        alignmentDao.deleteOrphanedAlignments()
        switchDao.deleteDrafts()
        kmPostDao.deleteDrafts()
        trackNumberDao.deleteDrafts()
    }

    @BeforeEach
    fun createTestData() {
        clearAllTestData()

        // TODO: GVT-1945  Don't use shared test data - init the data in the test as is needed, so it's clear what is expected
        EspooTestData.switchStructures = switchStructureDao.fetchSwitchStructures()

        val trackNumber1 = createTrackLayoutTrackNumber(ESPOO_TRACK_NUMBER_1)
        val trackNumber1Id = trackNumberDao.insert(trackNumber1).id

        val trackNumber2 = createTrackLayoutTrackNumber(ESPOO_TRACK_NUMBER_2)
        val trackNumber2Id = trackNumberDao.insert(trackNumber2).id

        val trackNumber3 = createTrackLayoutTrackNumber(ESPOO_TRACK_NUMBER_3)
        val trackNumber3Id = trackNumberDao.insert(trackNumber3).id

        LOCATION_TRACK_B = EspooTestData.locationTrackB(trackNumber1Id)

        val locationTrackD = EspooTestData.locationTrackD(trackNumber1Id)
        LOCATION_TRACK_D_1 = locationTrackD[0]
        LOCATION_TRACK_D_2 = locationTrackD[1]

        LOCATION_TRACK_E = EspooTestData.locationTrackE(trackNumber1Id)
        LOCATION_TRACK_F = EspooTestData.locationTrackF(trackNumber1Id)

        LOCATION_TRACK_G = EspooTestData.locationTrackG(trackNumber1Id)
        LOCATION_TRACK_H = EspooTestData.locationTrackH(trackNumber1Id)
        LOCATION_TRACK_J = EspooTestData.locationTrackJ(trackNumber1Id)

        TRACK_LAYOUT_SWITCH_A = EspooTestData.trackLayoutSwitchA()
        switchDao.insert(TRACK_LAYOUT_SWITCH_A)

        REFERENCE_LINE_ESP1 = EspooTestData.referenceLine1(trackNumber1Id)
        REFERENCE_LINE_ESP2 = EspooTestData.referenceLine2(trackNumber2Id)
        REFERENCE_LINE_ESP3 = EspooTestData.referenceLine3(trackNumber3Id)

        EspooTestData.trackLayoutKmPosts(trackNumber1Id).also { LAYOUT_KM_POST_0000 = it.first() }
            .forEach { kmPostDao.insert(it) }

        insertLocationTrack(LOCATION_TRACK_B)
        insertLocationTrack(LOCATION_TRACK_D_1)
        insertLocationTrack(LOCATION_TRACK_D_2)
        LOCATION_TRACK_E_ID = insertLocationTrack(LOCATION_TRACK_E).id
        LOCATION_TRACK_F_ID = insertLocationTrack(LOCATION_TRACK_F).id
        insertLocationTrack(LOCATION_TRACK_G)
        insertLocationTrack(LOCATION_TRACK_H)
        insertLocationTrack(LOCATION_TRACK_J)

        insertReferenceLine(REFERENCE_LINE_ESP1)
        insertReferenceLine(REFERENCE_LINE_ESP2)
        //insertReferenceLine(REFERENCE_LINE_ESP3)

        val boundingBox = boundingPolygonPointsByConvexHull(
            REFERENCE_LINE_ESP1.second.allPoints() + REFERENCE_LINE_ESP2.second.allPoints() + REFERENCE_LINE_ESP3.second.allPoints(),
            LAYOUT_CRS
        )

        GEOMETRY_PLAN = geometryDao.fetchPlan(
            (geometryDao.insertPlan(
                EspooTestData.geometryPlan(trackNumber1Id),
                testFile(),
                boundingBox
            ))
        )

        clearDrafts()
        startGeoviite()

        mapPage = goToMap()
        mapPage.luonnostila()
        mapPage.zoomOutToScale("5 km")

        navigationPanel.selectReferenceLine(ESPOO_TRACK_NUMBER_1)
        toolPanel.referenceLineLocation().kohdistaKartalla()
    }

    @Test
    fun `Create a new location track and link geometry`() {
        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_A_NAME)

        val alignmentA = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_A_NAME)

        val newLocationTrackName = "lt-A"
        createAndLinkLocationTrack(alignmentA, newLocationTrackName)

        toolPanel.selectToolPanelTab(GEO_ALIGNMENT_A_NAME)

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking().linkitetty())
        assertContains(navigationPanel.locationTracks().map { lt -> lt.name() }, newLocationTrackName)

        toolPanel.selectToolPanelTab(newLocationTrackName)

        val geometryTrackStartPoint = alignmentA.elements.first().start
        val geometryTrackEndPoint = alignmentA.elements.last().end
        val locationTrackLocationInfoBox = toolPanel.locationTrackLocation()
        assertEquals(pointToCoordinateString(geometryTrackStartPoint), locationTrackLocationInfoBox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(geometryTrackEndPoint), locationTrackLocationInfoBox.loppukoordinaatti())

        publishChanges()
        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide $newLocationTrackName")
    }

    @Test
    fun `Replace existing location track geometry with new geometry`() {
        navigationPanel.selectLocationTrack(LOCATION_TRACK_B.first.name.toString())
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        val locationTrackLengthBeforeLinking = toolPanel.locationTrackLocation().todellinenPituusDouble()

        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_B_NAME)

        toolPanel.geometryAlignmentGeneral().kohdistaKartalla()

        val linkingBox = toolPanel.geometryAlignmentLinking()
        linkingBox.aloitaLinkitys()
        linkingBox.linkTo(LOCATION_TRACK_B.first.name.toString())
        linkingBox.lukitseValinta()

        val geometryAlignment = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_B_NAME)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val locationTrackStartPoint = LOCATION_TRACK_B.second.segments.first().points.first()
        val locationTrackEndPoint = LOCATION_TRACK_B.second.segments.last().points.last()

        //Zooms to starting point and focuses map to include linked area
        //mapPage.clickAtCoordinates(locationTrackStartPoint, doubleClick = true)

        mapPage.clickAtCoordinates(geometryTrackStartPoint)
        mapPage.clickAtCoordinates(geometryTrackEndPoint)

        mapPage.clickAtCoordinates(locationTrackEndPoint)
        mapPage.clickAtCoordinates(locationTrackStartPoint)

        linkingBox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")
        toolPanel.selectToolPanelTab(GEO_ALIGNMENT_B_NAME)

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking().linkitetty())

        //Select twice for actually opening the location infobox
        toolPanel.selectToolPanelTab(LOCATION_TRACK_B.first.name.toString())
        val locationTrackLengthAfterLinking = toolPanel.locationTrackLocation().todellinenPituusDouble()

        assertNotEquals(locationTrackLengthBeforeLinking, locationTrackLengthAfterLinking)
        assertThat(locationTrackLengthAfterLinking).isGreaterThan(locationTrackLengthBeforeLinking)

        assertEquals(
            pointToCoordinateString(geometryTrackStartPoint),
            toolPanel.locationTrackLocation().alkukoordinaatti()
        )
        assertEquals(
            pointToCoordinateString(geometryTrackEndPoint),
            toolPanel.locationTrackLocation().loppukoordinaatti()
        )

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide ${LOCATION_TRACK_B.first.name}")

    }

    @Test
    fun `Edit location track end coordinate`() {
        navigationPanel.selectLocationTrack(LOCATION_TRACK_G.first.name.toString())
        val locationInfoBox = toolPanel.locationTrackLocation()
        locationInfoBox.muokkaaAlkuLoppupistetta()

        val startPoint = LOCATION_TRACK_G.second.segments.first().points.first()
        val endPoint = LOCATION_TRACK_G.second.segments.last().points.last()
        val newEndPoint = LOCATION_TRACK_G.second.segments.first().points.last()

        mapPage.clickAtCoordinates(endPoint)
        mapPage.clickAtCoordinates(newEndPoint)

        locationInfoBox.valmis().assertAndClose("Raiteen päätepisteet päivitetty")

        assertEquals(pointToCoordinateString(startPoint), locationInfoBox.alkukoordinaatti())
        locationInfoBox.waitForLoppukoordinaatti(pointToCoordinateString(newEndPoint))

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide ${LOCATION_TRACK_G.first.name}")
    }

    @Test
    fun `Edit location track start coordinate`() {
        navigationPanel.locationTracksList.selectByName(LOCATION_TRACK_H.first.name.toString())
        val locationInfoBox = toolPanel.locationTrackLocation()
        locationInfoBox.muokkaaAlkuLoppupistetta()

        val startPoint = LOCATION_TRACK_H.second.segments.first().points.first()
        val newStartPoint = LOCATION_TRACK_H.second.segments.first().points.last()
        val endPoint = LOCATION_TRACK_H.second.segments.last().points.last()

        mapPage.finishLoading()
        mapPage.clickAtCoordinates(startPoint)
        mapPage.clickAtCoordinates(newStartPoint)

        locationInfoBox.valmis().assertAndClose("Raiteen päätepisteet päivitetty")

        locationInfoBox.waitForAlkukoordinaatti(pointToCoordinateString(newStartPoint))
        assertEquals(pointToCoordinateString(endPoint), locationInfoBox.loppukoordinaatti())

        publishChanges("Muutokset julkaistu paikannuspohjaan")

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide ${LOCATION_TRACK_H.first.name}")
    }

    @Test
    fun `Link geometry KM-Post to nearest track layout KM-post`() {
        navigationPanel.selectTrackLayoutKmPost(LAYOUT_KM_POST_0000.kmNumber.toString())
        val layoutKmPostCoordinatesBeforeLinking = toolPanel.layoutKmPostLocation().koordinaatit()

        val geometryPlan = navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).open().openKmPosts()
        val firstGeometryKmPost = geometryPlan.kmPosts().listItems().first()
        firstGeometryKmPost.select()

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking()
        kmPostLinkingInfoBox.aloitaLinkitys()
        val firstTrackLayoutKmPost = kmPostLinkingInfoBox.trackLayoutKmPosts().first()
        assertEquals(firstGeometryKmPost.name().substring(0, 3), firstTrackLayoutKmPost.substring(0, 3))

        kmPostLinkingInfoBox.linkTo(firstTrackLayoutKmPost)
        kmPostLinkingInfoBox.linkita().assertAndClose("Tasakilometripiste linkitetty")

        assertEquals("KYLLÄ", toolPanel.geometryKmPostLinking().linkitetty())
        toolPanel.selectToolPanelTab(LAYOUT_KM_POST_0000.kmNumber.toString())
        assertNotEquals(layoutKmPostCoordinatesBeforeLinking, toolPanel.layoutKmPostLocation().koordinaatit())

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Tasakilometripiste ${LAYOUT_KM_POST_0000.kmNumber}")
    }

    @Test
    fun `Link geometry KM-post to new KM-post`() {
        val geometryPlan = navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).open().openKmPosts()
        val lastGeometryKmPost = geometryPlan.kmPosts().listItems().last()
        lastGeometryKmPost.select()

        val kmPostLinkingInfoBox = toolPanel.geometryKmPostLinking()
        kmPostLinkingInfoBox.aloitaLinkitys()

        val newKmPostNumber = "0003NW"
        kmPostLinkingInfoBox.createNewTrackLayoutKmPost().editTasakmpistetunnus(newKmPostNumber)
            .editTila(KmPostEditDialog.TilaTyyppi.KAYTOSSA).tallenna(waitUntilRootIsStale = false)
            .assertAndClose("Uusi tasakilometripiste lisätty")

        kmPostLinkingInfoBox.linkita().assertAndClose("Tasakilometripiste linkitetty onnistuneesti")
        mapPage.zoomOutToScale("50 m")
        lastGeometryKmPost.select()

        val geometryKmPost0003GMCoordinates =
            pointToCoordinateString(GEOMETRY_PLAN.kmPosts.find { kmPost -> kmPost.kmNumber.toString() == lastGeometryKmPost.name() }?.location!!)
        navigationPanel.selectTrackLayoutKmPost(newKmPostNumber)
        val layoutKmPost0003TLCoordinates = toolPanel.layoutKmPostLocation().koordinaatit()
        assertEquals(geometryKmPost0003GMCoordinates, layoutKmPost0003TLCoordinates)

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Tasakilometripiste $newKmPostNumber")
    }


    @Test
    @Disabled //Review after MVP
    fun linkTwoNewLocationTracksTogether() {
        val geoD = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_C_NAME)
        val firstElementStart = geoD.elements.first().start
        val firstElementEnd = geoD.elements.first().end
        val secondElementEnd = geoD.elements.last().end

        //Create and link two new location tracks from geo track D
        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_C_NAME)

        val linkingBox = toolPanel.geometryAlignmentLinking()
        linkingBox.aloitaLinkitys()


        val newLocationTrackPart1Name = "ext-d-1"
        linkingBox.createNewLocationTrack().editSijaintiraidetunnus(newLocationTrackPart1Name)
            .editExistingRatanumero(ESPOO_TRACK_NUMBER_1).editTila(TilaTyyppi.KAYTOSSA)
            .editRaidetyyppi(RaideTyyppi.PAARAIDE).editKuvaus("A new location track to be linked part 1")
            .tallenna(false)

        linkingBox.linkTo(newLocationTrackPart1Name)
        linkingBox.lukitseValinta()

        mapPage.clickAtCoordinates(firstElementStart)
        mapPage.clickAtCoordinates(firstElementEnd)

        linkingBox.linkita()

        toolPanel.selectToolPanelTab(GEO_ALIGNMENT_C_NAME)

        linkingBox.lisaaLinkitettavia()

        val newLocationTrackPart2Name = "ext-d-2"
        linkingBox.createNewLocationTrack().editSijaintiraidetunnus(newLocationTrackPart2Name)
            .editExistingRatanumero(ESPOO_TRACK_NUMBER_1).editTila(TilaTyyppi.KAYTOSSA)
            .editRaidetyyppi(RaideTyyppi.PAARAIDE).editKuvaus("A new location track to be linked part 1")
            .tallenna(false)

        linkingBox.linkTo(newLocationTrackPart2Name)
        linkingBox.lukitseValinta()

        mapPage.clickAtCoordinates(firstElementEnd)
        mapPage.clickAtCoordinates(secondElementEnd)

        linkingBox.linkita()

        mapPage.clickAtCoordinates(firstElementEnd)

        mapPage.addEndPointDialog().jatkuuToisenaRaiteena().valitseJatkuvaRaide(newLocationTrackPart2Name).jatka()


        //ext-d-1 should be selected as default
        val extD1LocationInfoBox = toolPanel.locationTrackLocation()
        assertEquals(pointToCoordinateString(firstElementStart), extD1LocationInfoBox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(firstElementEnd), extD1LocationInfoBox.loppukoordinaatti())
        assertEquals("Ei asetettu", extD1LocationInfoBox.alkupiste())
        assertEquals(newLocationTrackPart2Name, extD1LocationInfoBox.loppupiste())

        toolPanel.selectToolPanelTab(newLocationTrackPart2Name)
        val extD2LocationInfobox = toolPanel.locationTrackLocation()
        assertEquals(pointToCoordinateString(firstElementEnd), extD2LocationInfobox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(secondElementEnd), extD2LocationInfobox.loppukoordinaatti())
        assertEquals("Ei asetettu", extD2LocationInfobox.loppupiste())
        assertEquals(newLocationTrackPart1Name, extD2LocationInfobox.alkupiste())

    }

    @Test
    @Disabled //Review after MVP
    fun linkExistingLocationTracksTogether() {
        val locationTrackD1Name = LOCATION_TRACK_D_1.first.name.toString()
        navigationPanel.selectLocationTrack(locationTrackD1Name)

        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_D_NAME)

        val linkingBox = toolPanel.geometryAlignmentLinking()
        linkingBox.aloitaLinkitys()
        linkingBox.linkTo(locationTrackD1Name)
        linkingBox.lukitseValinta()

        val geometryAlignment = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_D_NAME)
        val gtPart1StartPoint = geometryAlignment.elements.first().start
        val gtPart1EndPoint = geometryAlignment.elements.first().end

        val locationTrackD1Segment = LOCATION_TRACK_D_1.second.segments.first()
        val ltPart1StartPoint = locationTrackD1Segment.points.first()
        val ltPart1EndPoint = locationTrackD1Segment.points.last()

        //Zooms to starting point and focuses map to include linked area
        mapPage.clickAtCoordinates(ltPart1StartPoint, doubleClick = true)

        mapPage.clickAtCoordinates(gtPart1StartPoint)
        mapPage.clickAtCoordinates(gtPart1EndPoint)

        mapPage.clickAtCoordinates(ltPart1EndPoint)
        mapPage.clickAtCoordinates(ltPart1StartPoint)

        linkingBox.linkita()
        toolPanel.selectToolPanelTab(GEO_ALIGNMENT_D_NAME)

        assertEquals("Kyllä", toolPanel.geometryAlignmentLinking().linkitetty())
        mapPage.zoomOutToScale("20 m")

        linkingBox.lisaaLinkitettavia()

        val locationTrackD2Name = LOCATION_TRACK_D_2.first.name.toString()

        linkingBox.linkTo(locationTrackD2Name)
        linkingBox.lukitseValinta()

        val gtPart2EndPoint = geometryAlignment.elements[1].end
        val ltPart2EndPoint = LOCATION_TRACK_D_2.second.segments[0].points[1]

        //Focus map
        mapPage.clickAtCoordinates(gtPart1EndPoint)
        mapPage.clickAtCoordinates(gtPart2EndPoint)

        mapPage.clickAtCoordinates(ltPart2EndPoint)
        mapPage.clickAtCoordinates(ltPart1EndPoint)

        linkingBox.linkita()

        mapPage.clickAtCoordinates(ltPart1EndPoint)

        mapPage.addEndPointDialog().jatkuuToisenaRaiteena().valitseJatkuvaRaide(locationTrackD2Name).jatka()

        val extD1LocationInfoBox = toolPanel.locationTrackLocation()
        assertEquals(pointToCoordinateString(ltPart1StartPoint), extD1LocationInfoBox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(ltPart1EndPoint), extD1LocationInfoBox.loppukoordinaatti())
        assertEquals("Ei asetettu", extD1LocationInfoBox.alkupiste())
        assertEquals(locationTrackD2Name, extD1LocationInfoBox.loppupiste())

        toolPanel.selectToolPanelTab(locationTrackD2Name)
        val extD2LocationInfobox = toolPanel.locationTrackLocation()
        assertEquals(pointToCoordinateString(ltPart1EndPoint), extD2LocationInfobox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(ltPart2EndPoint), extD2LocationInfobox.loppukoordinaatti())
        assertEquals("Ei asetettu", extD2LocationInfobox.loppupiste())
        assertEquals(locationTrackD1Name, extD2LocationInfobox.alkupiste())

    }

    @Test
    fun `Link geometry switch to new location tracks and layout switch`() {
        val geoPlan = navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME)
        geoPlan.selectSwitch(GEO_SWITCH_1_NAME)
        toolPanel.geometrySwitchGeneral().kohdistaKartalla()

        val alignmentSw1 = getGeometryAlignmentFromPlan(GEO_SWITCH_1_ALIGNMENT_NAMES[0])
        mapPage.clickAtCoordinates(alignmentSw1.elements.first().start, doubleClick = true)
        mapPage.clickAtCoordinates(alignmentSw1.elements.first().start, doubleClick = true)

        geoPlan.selecAlignment(GEO_SWITCH_1_ALIGNMENT_NAMES[0])

        //Create LT for SW1
        val newLocationTrackSw1 = "sw-lt-1"
        createAndLinkLocationTrack(alignmentSw1, newLocationTrackSw1)

        //Create LT for SW2
        val alignmentSw2 = getGeometryAlignmentFromPlan(GEO_SWITCH_1_ALIGNMENT_NAMES[1])
        geoPlan.selecAlignment(GEO_SWITCH_1_ALIGNMENT_NAMES[1])

        val newLocationTrackSw2 = "sw-lt-2"
        createAndLinkLocationTrack(alignmentSw2, newLocationTrackSw2)

        geoPlan.selectSwitch(GEO_SWITCH_1_NAME)

        val layoutSwitchName = "tl-sw-1"
        val switchLinkingInfoBox = toolPanel.geometrySwitchLinking()
        switchLinkingInfoBox.aloitaLinkitys()
        switchLinkingInfoBox.createNewTrackLayoutSwitch().editVaihdetunnus(layoutSwitchName)
            .editTilakategoria(Tilakategoria.OLEMASSA_OLEVA_KOHDE).tallenna().assertAndClose("Uusi vaihde lisätty")

        switchLinkingInfoBox.linkTo(layoutSwitchName)
        switchLinkingInfoBox.linkita().assertAndClose("Vaihde linkitetty")
        toolPanel.selectToolPanelTab(layoutSwitchName)

        val layoutSwitchInfoBox = toolPanel.layoutSwitchStructureGeneralInfo()
        assertEquals(GEO_SWITCH_1_STRUCTURE.type.typeName, layoutSwitchInfoBox.tyyppi())
        assertEquals("Oikea", layoutSwitchInfoBox.katisyys())
        assertEquals("Ei tiedossa", layoutSwitchInfoBox.turvavaihde())


        val geoSwitchLocation = getGeometrySwitchFromPlan(GEO_SWITCH_1_NAME).getJoint(JointNumber(1))?.location
        val layoutSwitchLocationInfoBox = toolPanel.layoutSwitchLocation()
        assertEquals(pointToCoordinateString(geoSwitchLocation!!), layoutSwitchLocationInfoBox.koordinaatit())

        val switchLinesAndTracks = layoutSwitchLocationInfoBox.vaihteenLinjat()
        assertEquals("1-5-2", switchLinesAndTracks[0].switchLine)
        assertEquals(newLocationTrackSw1, switchLinesAndTracks[0].switchTrack)
        assertEquals("1-3", switchLinesAndTracks[1].switchLine)
        assertEquals(newLocationTrackSw2, switchLinesAndTracks[1].switchTrack)

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde(
            "Sijaintiraide sw-lt-1",
            "Sijaintiraide sw-lt-1",
            "Vaihde tl-sw-1"
        )
    }

    @Test
    fun `Continue location track using geometry`() {
        val geometryAlignment = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_E_NAME)

        navigationPanel.selectLocationTrack(LOCATION_TRACK_E.first.name.toString())
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation()
        val locationTrackLengthBeforeLinking = metersToDouble(locationTrackLocationInfobox.todellinenPituus())
        val locationTrackStartBeforeLinking = locationTrackLocationInfobox.alkukoordinaatti()
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.loppukoordinaatti()
        toolPanel.locationTrackGeneralInfo().kohdistaKartalla()
        mapPage.zoomOutToScale("10 m")

        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_E_NAME)
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking()
        alignmentLinkinInfobox.aloitaLinkitys()
        alignmentLinkinInfobox.linkTo(LOCATION_TRACK_E.first.name.toString())
        alignmentLinkinInfobox.lukitseValinta()

        mapPage.clickAtCoordinates(LOCATION_TRACK_E.second.segments.first().points.first())
        mapPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        mapPage.clickAtCoordinates(geometryAlignment.elements.first().start)

        alignmentLinkinInfobox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")
        toolPanel.selectToolPanelTab(LOCATION_TRACK_E.first.name.toString())
        val lengthAfterLinking = metersToDouble(locationTrackLocationInfobox.todellinenPituus())

        assertThat(locationTrackLengthBeforeLinking).isLessThan(lengthAfterLinking)
        val editedLocationTrack = getLocationTrackAndAlignment(PublishType.DRAFT, LOCATION_TRACK_E_ID)

        //Check that there's a new segment between GT-end and old LT-start
        assertTrue(
            hasSegmentBetweenPoints(
                start = geometryAlignment.elements.last().end,
                end = LOCATION_TRACK_E.second.segments.first().points.first().toPoint(),
                layoutAlignment = editedLocationTrack.second,
            )
        )

        assertNotEquals(locationTrackStartBeforeLinking, locationTrackLocationInfobox.alkukoordinaatti())
        assertEquals(
            pointToCoordinateString(geometryAlignment.elements.first().start),
            locationTrackLocationInfobox.alkukoordinaatti()
        )
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.loppukoordinaatti())

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide ${LOCATION_TRACK_E.first.name}")
    }


    @Test
    fun `Continue and replace location track using geometry`() {
        val geometryAlignment = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_F_NAME)
        val geometryAlignmentStart = geometryAlignment.elements.first().start
        val geometryAlignmentEnd = geometryAlignment.elements.last().end


        navigationPanel.selectLocationTrack(LOCATION_TRACK_F.first.name.toString())
        val locationTrackLocationInfobox = toolPanel.locationTrackLocation()
        val locationTrackLengthBeforeLinking = metersToDouble(locationTrackLocationInfobox.todellinenPituus())
        val locationTrackEndBeforeLinking = locationTrackLocationInfobox.loppukoordinaatti()

        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_F_NAME)
        val alignmentLinkinInfobox = toolPanel.geometryAlignmentLinking()
        alignmentLinkinInfobox.aloitaLinkitys()
        alignmentLinkinInfobox.linkTo(LOCATION_TRACK_F.first.name.toString())
        alignmentLinkinInfobox.lukitseValinta()
        mapPage.finishLoading()
        mapPage.clickAtCoordinates(geometryAlignmentStart)
        mapPage.clickAtCoordinates(geometryAlignmentEnd)
        mapPage.clickAtCoordinates(LOCATION_TRACK_F.second.segments.first().points.first())
        mapPage.clickAtCoordinates(LOCATION_TRACK_F.second.segments.first().points.last())
        alignmentLinkinInfobox.linkita().assertAndClose("Raide linkitetty ja vanhentuneen geometrian linkitys purettu")

        val locationTrackAfterLinking = getLocationTrackAndAlignment(PublishType.DRAFT, LOCATION_TRACK_F_ID)

        toolPanel.selectToolPanelTab(LOCATION_TRACK_F.first.name.toString())
        val lengthAfterLinking = metersToDouble(locationTrackLocationInfobox.todellinenPituus())

        assertThat(locationTrackLengthBeforeLinking).isLessThan(lengthAfterLinking)
        assertEquals(pointToCoordinateString(geometryAlignmentStart), locationTrackLocationInfobox.alkukoordinaatti())
        assertEquals(locationTrackEndBeforeLinking, locationTrackLocationInfobox.loppukoordinaatti())
        assertTrue(
            hasSegmentBetweenPoints(
                start = geometryAlignmentEnd,
                end = LOCATION_TRACK_F.second.segments.first().points.last().toPoint(),
                layoutAlignment = locationTrackAfterLinking.second
            )
        )

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide ${LOCATION_TRACK_F.first.name}")
    }

    @Test
    fun `link track to a reference line`() {
        navigationPanel.geometryPlanByName(GEOMETRY_PLAN_NAME).selecAlignment(GEO_ALIGNMENT_I_NAME)
        val alignmentLinkingInfobox = toolPanel.geometryAlignmentLinking()
        alignmentLinkingInfobox.aloitaLinkitys()
        alignmentLinkingInfobox.linkTo(REFERENCE_LINE_1_NAME)
        alignmentLinkingInfobox.lukitseValinta()

        val geometryAlignment = getGeometryAlignmentFromPlan(GEO_ALIGNMENT_I_NAME)
        val geometryTrackStartPoint = geometryAlignment.elements.first().start
        val geometryTrackEndPoint = geometryAlignment.elements.last().end

        val referenceLineStartPoint = REFERENCE_LINE_ESP1.second.segments.first().points.first()
        val referenceLineEndPoint = REFERENCE_LINE_ESP1.second.segments.first().points.last()

        mapPage.finishLoading()
        mapPage.clickAtCoordinates(geometryTrackStartPoint)
        mapPage.clickAtCoordinates(geometryTrackEndPoint)
        mapPage.clickAtCoordinates(referenceLineStartPoint)
        mapPage.clickAtCoordinates(referenceLineEndPoint)

        alignmentLinkingInfobox.linkita().assertAndClose("Raide linkitetty")

        assertEquals(REFERENCE_LINE_1_NAME, alignmentLinkingInfobox.pituusmittauslinja())

        //Deselect reference line to open location info box
        navigationPanel.selectLocationTrack(LOCATION_TRACK_E.first.name.toString())
        navigationPanel.selectReferenceLine(REFERENCE_LINE_1_NAME)

        val referenceLineLocationInfobox = toolPanel.referenceLineLocation()

        assertEquals(pointToCoordinateString(geometryTrackStartPoint), referenceLineLocationInfobox.alkukoordinaatti())
        assertEquals(pointToCoordinateString(geometryTrackEndPoint), referenceLineLocationInfobox.loppukoordinaatti())

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Pituusmittauslinja ESP1")
    }

    @Test
    fun `Delete location track`() {
        mapPage.navigationPanel.selectLocationTrack(LOCATION_TRACK_J.first.name.toString())
        mapPage.toolPanel.locationTrackGeneralInfo().muokkaaTietoja().editTila(TilaTyyppi.POISTETTU).tallenna()
            .assertAndClose("Sijaintiraide poistettu")

        assertTrue(mapPage.navigationPanel.locationTracksList.items.none { it.name == LOCATION_TRACK_J.first.name.toString() })

        val locationTrackJ = LOCATION_TRACK_J.second.segments.first().points.first()
        val pointNearLocationTrackJStart = locationTrackJ.plus(Point(x = 2.0, y = 2.0))

        //Click at empty point and info box should be empty
        mapPage.clickAtCoordinates(pointNearLocationTrackJStart)

        try {
            mapPage.toolPanel.locationTrackGeneralInfo()
        } catch (ex: Exception) {
            assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }

        publishChanges()
        assertThatLatestPublicationDetailsIncludeMuutoskohde("Sijaintiraide lt-J")
    }

    @Test
    fun `Delete track layout switch`() {
        mapPage.zoomOutToScale("50 m")

        val switchName = TRACK_LAYOUT_SWITCH_A.name.toString()
        mapPage.navigationPanel.selectTrackLayoutSwitch(switchName)
        mapPage.toolPanel.layoutSwitchGeneralInfo().muokkaaTietoja().editTilakategoria(Tilakategoria.POISTUNUT_KOHDE)
            .tallenna().assertAndClose("Vaihteen tiedot päivitetty")

        mapPage.navigationPanel.waitUntilSwitchNotVisible(switchName)

        //Click near deleted element point to clear tool panels
        //and then try to select deleted element to confirm it disappeared
        val switchPoint = TRACK_LAYOUT_SWITCH_A.joints.first().location
        mapPage.clickAtCoordinates(switchPoint + Point(x = 1.0, y = 1.0))
        mapPage.clickAtCoordinates(switchPoint)

        try {
            mapPage.toolPanel.layoutSwitchGeneralInfo()
        } catch (ex: Exception) {
            assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Vaihde switch-A")
    }

    @Test
    fun `Delete a reference line and a track number`() {
        mapPage.navigationPanel.selectReferenceLine(ESPOO_TRACK_NUMBER_2)
        mapPage.toolPanel.trackNumberGeneralInfo().muokkaaTietoja()
            .editTila(CreateEditTrackNumberDialog.TilaTyyppi.POISTETTU).tallenna(false)
            .assertAndClose("Ratanumero tallennettu")

        mapPage.navigationPanel.waitForTrackNumberNamesTo { names -> names.none(ESPOO_TRACK_NUMBER_2::equals) }
        mapPage.navigationPanel.waitForReferenceLineNamesTo { names -> names.none(ESPOO_TRACK_NUMBER_2::equals) }

        //Click near deleted element point to clear tool panels
        //and then try to select deleted element to confirm it disappeared
        val referenceLineStartPoint = REFERENCE_LINE_ESP2.second.start
        mapPage.clickAtCoordinates(referenceLineStartPoint!!.plus(Point(x = 1.0, y = 1.0)))
        mapPage.clickAtCoordinates(referenceLineStartPoint)

        try {
            mapPage.toolPanel.trackNumberGeneralInfo()
        } catch (ex: Exception) {
            assertThat(ex).isInstanceOf(TimeoutException::class.java)
        }

        publishChanges()

        assertThatLatestPublicationDetailsIncludeMuutoskohde("Ratanumero ESP2")
    }

    fun publishChanges(expectedPublishMessage: String = "Muutokset julkaistu") {
        val previewChangesPage = mapPage.esikatselu()
        previewChangesPage.logChanges()
        previewChangesPage.lisaaMuutoksetJulkaisuun()
        previewChangesPage.julkaise().assertAndClose(expectedPublishMessage)
    }

    fun createAndLinkLocationTrack(geometryAlignment: GeometryAlignment, locationTrackName: String) {

        val alignmentLinkingInfoBox = toolPanel.geometryAlignmentLinking()

        alignmentLinkingInfoBox.aloitaLinkitys()

        alignmentLinkingInfoBox.createNewLocationTrack().editSijaintiraidetunnus(locationTrackName)
            .editExistingRatanumero(ESPOO_TRACK_NUMBER_1).editTila(TilaTyyppi.KAYTOSSA)
            .editRaidetyyppi(RaideTyyppi.PAARAIDE).editKuvaus("manually created location track $locationTrackName")
            .editTopologinenKytkeytyminen(CreateEditLocationTrackDialog.TopologinenKytkeytyminen.EI_KYTKETTY)
            .tallenna(false).assertAndClose("Uusi sijaintiraide lisätty rekisteriin")

        alignmentLinkingInfoBox.linkTo(locationTrackName)
        alignmentLinkingInfoBox.lukitseValinta()

        mapPage.clickAtCoordinates(geometryAlignment.elements.first().start)
        mapPage.clickAtCoordinates(geometryAlignment.elements.last().end)
        alignmentLinkingInfoBox.linkita().assertAndClose("Raide linkitetty")
    }

    fun insertReferenceLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>): IntId<ReferenceLine> {
        val alignmentVersion = alignmentDao.insert(lineAndAlignment.second)
        return referenceLineDao.insert(lineAndAlignment.first.copy(alignmentVersion = alignmentVersion)).id
    }

    fun getLocationTrackAndAlignment(
        publishType: PublishType,
        id: IntId<LocationTrack>,
    ): Pair<LocationTrack, LayoutAlignment> {
        return when (publishType) {
            PublishType.DRAFT -> {
                val locationTrack = locationTrackDao.fetch(locationTrackDao.fetchDraftVersionOrThrow(id))
                val alignment = alignmentDao.fetch(locationTrack.alignmentVersion!!)
                locationTrack to alignment
            }

            PublishType.OFFICIAL -> {
                val locationTrack = locationTrackDao.fetch(locationTrackDao.fetchOfficialVersion(id)!!)
                val alignment = alignmentDao.fetch(locationTrack.alignmentVersion!!)
                locationTrack to alignment
            }
        }
    }

    private fun latestPublicationDetails(): List<PublicationDetailRowContent> =
        mapPage.mainNavigation.goToFrontPage().openLatestPublication().detailRowContents()

    private fun assertThatLatestPublicationDetailsIncludeMuutoskohde(vararg muutoskohde: String) =
        assertThat(latestPublicationDetails()).anyMatch { muutoskohde.toList().contains(it.muutoskohde) }

    private fun getGeometryAlignmentFromPlan(alignmentName: String) =
        GEOMETRY_PLAN.alignments.find { alignment -> alignment.name.toString() == alignmentName }!!

    private fun getGeometrySwitchFromPlan(switchName: String) =
        GEOMETRY_PLAN.switches.find { switch -> switch.name.toString() == switchName }!!

    private fun hasSegmentBetweenPoints(start: Point, end: Point, layoutAlignment: LayoutAlignment): Boolean {
        return layoutAlignment.segments.any { segment -> segment.includes(start) && segment.includes(end) }
    }

    private fun layoutAlignmentIncludesPoints(layoutAlignment: LayoutAlignment, vararg points: Point) {
        layoutAlignment.segments.flatMap { segment -> segment.points }.containsAll(toTrackLayoutPoints(*points))
    }


}
