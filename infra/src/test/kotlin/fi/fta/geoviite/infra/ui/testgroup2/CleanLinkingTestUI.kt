package fi.fta.geoviite.infra.ui.testgroup2

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.TrackMeter
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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertContains
import kotlin.test.assertEquals

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
    val navigationPanel: MapNavigationPanel = MapNavigationPanel()
    val toolPanel: MapToolPanel = MapToolPanel()

    @BeforeEach
    fun setup() {
        clearAllTestData()
    }

    fun startAndGoToWorkArea() {
        startGeoviite()
        mapPage = goToMap()
        mapPage.luonnostila()
        mapPage.zoomOutToScale("5 km")

        navigationPanel.selectReferenceLine("ESP1")
        toolPanel.referenceLineLocation().kohdistaKartalla()
    }

    fun saveAndRefetchGeometryPlan(plan: GeometryPlan, boundingBox: List<Point>): GeometryPlan {
        return geometryDao.fetchPlan(
            geometryDao.insertPlan(
                plan,
                testFile(),
                boundingBox,
            ))
    }

    data class GeometryAlignmentIntoPlan(val alignmentName: String, val vectors: List<Point>) {
        operator fun invoke(trackNumberId: IntId<TrackLayoutTrackNumber>): GeometryAlignment =
            createGeometryAlignment(
                alignmentName = alignmentName,
                trackNumberId = trackNumberId,
                incrementPoints = vectors
            )
    }

    fun createAndInsertGeometryPlan(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        alignments: List<GeometryAlignmentIntoPlan>
    ): GeometryPlan {
        val alignmentsInPlan = alignments.map { intoPlan -> intoPlan(trackNumberId) }
        return saveAndRefetchGeometryPlan(
            geometryPlan(trackNumberId, alignments = alignmentsInPlan),
            boundingPolygonPointsByConvexHull(
                alignmentsInPlan.flatMap { alignment -> alignment.elements.flatMap { element -> element.bounds } },
                LAYOUT_CRS
            )
        )
    }

    inner class BuildGeometryPlan(val trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val alignments: MutableList<GeometryAlignment> = mutableListOf()
        val alsoInBounds: MutableList<IPoint> = mutableListOf()

        fun alignment(name: String, vararg vectors: Point): BuildGeometryPlan {
            alignments.add(
                createGeometryAlignment(
                    alignmentName = name,
                    trackNumberId = trackNumberId,
                    basePoint = EspooTestData.BASE_POINT,
                    incrementPoints = vectors.asList()
                )
            )
            return this
        }

        fun addingToBounds(points: Collection<IPoint>): BuildGeometryPlan {
            points.forEach(alsoInBounds::add)
            return this
        }

        fun save(): GeometryPlan {
            return saveAndRefetchGeometryPlan(
                geometryPlan(trackNumberId, alignments = alignments),
                boundingPolygonPointsByConvexHull(
                    alignments.flatMap { alignment -> alignment.elements.flatMap { element -> element.bounds } } + alsoInBounds,
                    LAYOUT_CRS
                )
            )
        }
    }

    fun buildPlan(trackNumberId: IntId<TrackLayoutTrackNumber>) = BuildGeometryPlan(trackNumberId)

    @Test
    fun `Create a new location track and link geometry`() {
        val trackNumber = createTrackLayoutTrackNumber("ESP1")
        val trackNumberId = trackNumberDao.insert(trackNumber).id
        val referenceLine = referenceLine1(trackNumberId)
        referenceLineDao.insert(referenceLine.first.copy(alignmentVersion = alignmentDao.insert(referenceLine.second)))

        val geometryPlan = buildPlan(trackNumberId)
            .alignment("geo-alignment-a",
                Point(50.0, 25.0),
                // points after the first one should preferably be Pythagorean pairs, so meter points line up nicely
                Point(20.0, 21.0),
                Point(40.0, 9.0))
            .addingToBounds(referenceLine.second.allPoints())
            .save()

        val alignmentA = getGeometryAlignmentFromPlan("geo-alignment-a", geometryPlan)

        startAndGoToWorkArea()

        navigationPanel
            .geometryPlanByName("Linking geometry plan")
            .selecAlignment("geo-alignment-a")

        val newLocationTrackName = "lt-A"
        createAndLinkLocationTrack(alignmentA, "ESP1", newLocationTrackName)

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

    fun createAndLinkLocationTrack(
        geometryAlignment: GeometryAlignment,
        trackNumber: String,
        locationTrackName: String
    ) {

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
            kmPosts = EspooTestData.geometryKmPosts(trackLayoutTrackNumberId),
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


    fun referenceLine1(trackNumber: IntId<TrackLayoutTrackNumber>): Pair<ReferenceLine, LayoutAlignment> {
        val points = pointsFromIncrementList(
            EspooTestData.BASE_POINT + Point(x = -20.0, y = 5.0),
            listOf(
                Point(x = 140.0, y = 5.0),
                Point(x = 140.0, y = 15.0)
            )
        )

        val trackLayoutPoints = toTrackLayoutPoints(*points.toTypedArray())
        val alignment = alignment(segment(trackLayoutPoints))
        return referenceLine(
            alignment = alignment,
            trackNumberId = trackNumber,
            startAddress = TrackMeter(KmNumber(0), 0),
        ) to alignment
    }

    private fun getGeometryAlignmentFromPlan(alignmentName: String, geometryPlan: GeometryPlan) =
        geometryPlan.alignments.find { alignment -> alignment.name.toString() == alignmentName }!!
}
