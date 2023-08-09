package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.CreateEditLocationTrackDialog
import fi.fta.geoviite.infra.ui.pagemodel.map.MapNavigationPanel
import fi.fta.geoviite.infra.ui.pagemodel.map.MapToolPanel
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.EAST_LT_NAME
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.HKI_TRACK_NUMBER_1
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.HKI_TRACK_NUMBER_2
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.WEST_LT_NAME
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.eastLocationTrack
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.eastReferenceLine
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.eastTrackLayoutKmPosts
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.eastTrackLayoutSwitch
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.geometryPlan
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westMainLocationTrack
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westReferenceLine
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westTrackLayoutKmPosts
import fi.fta.geoviite.infra.ui.testdata.HelsinkiTestData.Companion.westTrackLayoutSwitch
import fi.fta.geoviite.infra.ui.testdata.createTrackLayoutTrackNumber
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertContains


@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class BasicMapTestUI @Autowired constructor(
    private val switchDao: LayoutSwitchDao,
    private val geometryDao: GeometryDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
) : SeleniumTest() {
    val navigationPanel: MapNavigationPanel = MapNavigationPanel()
    val toolPanel: MapToolPanel = MapToolPanel()

    lateinit var WEST_LT: Pair<LocationTrack, LayoutAlignment>
    lateinit var GEOMETRY_PLAN: GeometryPlan
    lateinit var WEST_REFERENCE_LINE: Pair<ReferenceLine, LayoutAlignment>
    lateinit var EAST_LAYOUT_SWITCH: TrackLayoutSwitch
    lateinit var TRACK_NUMBER_WEST: TrackLayoutTrackNumber

    @BeforeEach
    fun createTestData() {
        clearAllTestData()

        // TODO: GVT-1945  Don't use shared test data - init the data in the test as is needed, so it's clear what is expected
        TRACK_NUMBER_WEST = createTrackLayoutTrackNumber(HKI_TRACK_NUMBER_1)
        val trackNumberEast = createTrackLayoutTrackNumber(HKI_TRACK_NUMBER_2)
        val trackNumberWestId = trackNumberDao.insert(TRACK_NUMBER_WEST)
        val trackNumberEastId = trackNumberDao.insert(trackNumberEast)

        WEST_LT = westMainLocationTrack(trackNumberWestId.id)

        WEST_REFERENCE_LINE = westReferenceLine(trackNumberWestId.id)
        val eastReferenceLine = eastReferenceLine(trackNumberEastId.id)
        val eastLocationTrack = eastLocationTrack(trackNumberEastId.id)


        insertLocationTrack(WEST_LT)
        insertLocationTrack(eastLocationTrack)
        insertReferenceLine(WEST_REFERENCE_LINE)
        insertReferenceLine(eastReferenceLine)

        westTrackLayoutKmPosts(trackNumberWestId.id).forEach(kmPostDao::insert)
        eastTrackLayoutKmPosts(trackNumberEastId.id).forEach(kmPostDao::insert)

        EAST_LAYOUT_SWITCH = eastTrackLayoutSwitch()
        switchDao.insert(westTrackLayoutSwitch())
        switchDao.insert(EAST_LAYOUT_SWITCH)

        GEOMETRY_PLAN = geometryDao.fetchPlan(
            (geometryDao.insertPlan(geometryPlan(trackNumberWestId.id), testFile(), null))
        )

        startGeoviite()
        goToMap()

        // TODO: GVT-1945 Remove this: if the test needs to select something in the beginning, it should do that itself
        navigationPanel.selectReferenceLine(TRACK_NUMBER_WEST.number.toString())
        toolPanel.referenceLineLocation().kohdistaKartalla()
    }

    @Test
    fun `Edit and discard location track changes`() {
        val locationTrackToBeEdited = EAST_LT_NAME

        navigationPanel.locationTracksList.selectByName(locationTrackToBeEdited)
        goToMap().luonnostila()
        val infobox = toolPanel.locationTrackGeneralInfo()
        val orgTunniste = infobox.tunniste()
        val orgTila = infobox.tila()
        val orgKuvaus = infobox.kuvaus()
        //TBD val orgRatanumero = infobox.ratanumero()
        val orgSijaintiraidetunnus = infobox.sijainteraidetunnus()

        val editDialog = infobox.muokkaaTietoja()
        val editedTunnus = "$orgSijaintiraidetunnus-edited"
        editDialog.editSijaintiraidetunnus(editedTunnus)
        //TBD editDialog.editNewRatanumero("HKI1A")
        editDialog.editRaidetyyppi(CreateEditLocationTrackDialog.RaideTyyppi.SIVURAIDE)

        val editedKuvaus = "$orgKuvaus-edited"
        editDialog.editKuvaus(editedKuvaus)
        editDialog.editTila(CreateEditLocationTrackDialog.TilaTyyppi.KAYTOSTA_POISTETTU)
        editDialog.tallenna()

        navigationPanel.locationTracksList.waitUntilNameVisible(editedTunnus)
        val infoboxAfterFirstEdit = toolPanel.locationTrackGeneralInfo()
        assertEquals(orgTunniste, infoboxAfterFirstEdit.tunniste())
        assertNotEquals(orgSijaintiraidetunnus, infoboxAfterFirstEdit.sijainteraidetunnus())
        assertNotEquals(orgTila, infoboxAfterFirstEdit.tila())
        assertNotEquals(orgKuvaus, infoboxAfterFirstEdit.kuvaus())
        //TBD assertNotEquals(orgRatanumero, infoboxAfterFirstEdit.ratanumero())

        val previewChangesPage = goToMap().esikatselu()
        val changePreviewTable = previewChangesPage.changesTable()
        assertTrue(changePreviewTable.changeRows().isNotEmpty())

        val changedAlignment = changePreviewTable.changeRows().first { row -> row.muutoskohde().contains(editedTunnus) }

        val nameColumnValue = "Sijaintiraide $editedTunnus"
        assertEquals(nameColumnValue, changedAlignment.muutoskohde())
        assertEquals(HKI_TRACK_NUMBER_2, changedAlignment.ratanumero())

        changedAlignment.menu().click()
        previewChangesPage.hylkaaMuutos(nameColumnValue)
        previewChangesPage.palaaLuonnostilaan()
        navigationPanel.locationTracksList.selectByName(locationTrackToBeEdited)

        val infoBoxAfterSecondEdit = toolPanel.locationTrackGeneralInfo()
        assertEquals(orgTunniste, infoBoxAfterSecondEdit.tunniste())
        assertEquals(orgSijaintiraidetunnus, infoBoxAfterSecondEdit.sijainteraidetunnus())
        assertEquals(orgTila, infoBoxAfterSecondEdit.tila())
        assertEquals(orgKuvaus, infoBoxAfterSecondEdit.kuvaus())
        //TBD assertEquals(orgRatanumero, infoBoxAfterSecondEdit.ratanumero())

    }

    @Test
    fun `Edit and save location track changes`() {
        val locationTrackToBeEdited = WEST_LT_NAME

        navigationPanel.locationTracksList.selectByName(locationTrackToBeEdited)
        goToMap().luonnostila()
        val infobox = toolPanel.locationTrackGeneralInfo()
        val orgTunniste = infobox.tunniste()
        val orgTila = infobox.tila()
        val orgKuvaus = infobox.kuvaus()
        //TBD val orgRatanumero = infobox.ratanumero()
        val orgSijaintiraidetunnus = infobox.sijainteraidetunnus()

        val editDialog = infobox.muokkaaTietoja()
        val editedTunnus = "$orgSijaintiraidetunnus-edited"
        editDialog.editSijaintiraidetunnus(editedTunnus)
        //TBD editDialog.editNewRatanumero("HKI1A")
        editDialog.editRaidetyyppi(CreateEditLocationTrackDialog.RaideTyyppi.SIVURAIDE)

        val editedKuvaus = "$orgKuvaus-edited"
        editDialog.editKuvaus(editedKuvaus)
        editDialog.editTila(CreateEditLocationTrackDialog.TilaTyyppi.KAYTOSTA_POISTETTU)
        editDialog.tallenna().close()

        navigationPanel.locationTracksList.waitUntilNameVisible(editedTunnus)
        val infoboxAfterFirstEdit = toolPanel.locationTrackGeneralInfo()
        assertEquals(orgTunniste, infoboxAfterFirstEdit.tunniste())
        assertNotEquals(orgSijaintiraidetunnus, infoboxAfterFirstEdit.sijainteraidetunnus())
        assertNotEquals(orgTila, infoboxAfterFirstEdit.tila())
        assertNotEquals(orgKuvaus, infoboxAfterFirstEdit.kuvaus())
        //TBD assertNotEquals(orgRatanumero, infoboxAfterFirstEdit.ratanumero())

        val previewChangesPage = goToMap().esikatselu()
        val changePreviewTable = previewChangesPage.changesTable()
        assertTrue(changePreviewTable.changeRows().isNotEmpty())

        val changedAlignment = changePreviewTable.changeRows().first { row -> row.muutoskohde().contains(editedTunnus) }

        assertEquals("Sijaintiraide $editedTunnus", changedAlignment.muutoskohde())
        assertEquals(HKI_TRACK_NUMBER_1, changedAlignment.ratanumero())
        changedAlignment.nuolinappi().click()

        val notificationAfterSave = previewChangesPage.julkaise().readAndClose()
        assertContains(notificationAfterSave, "Muutokset julkaistu")

        val infoBoxAfterSecondEdit = toolPanel.locationTrackGeneralInfo()
        assertEquals(orgTunniste, infoBoxAfterSecondEdit.tunniste())
        assertNotEquals(orgSijaintiraidetunnus, infoBoxAfterSecondEdit.sijainteraidetunnus())
        assertNotEquals(orgTila, infoBoxAfterSecondEdit.tila())
        assertNotEquals(orgKuvaus, infoBoxAfterSecondEdit.kuvaus())
        //TBD assertEquals(orgRatanumero, infoBoxAfterSecondEdit.ratanumero())

        WEST_LT_NAME = infoBoxAfterSecondEdit.sijainteraidetunnus()
    }

    fun insertReferenceLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) {
        val alignmentVersion = alignmentDao.insert(lineAndAlignment.second)
        referenceLineDao.insert(lineAndAlignment.first.copy(alignmentVersion = alignmentVersion))
    }
}
