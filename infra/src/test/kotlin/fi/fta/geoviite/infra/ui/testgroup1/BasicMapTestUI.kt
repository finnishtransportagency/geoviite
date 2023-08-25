package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.map.E2ELocationTrackEditDialog
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
import waitAndAssertToaster
import waitAndClearToaster


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
    }

    @Test
    fun `Edit and discard location track changes`() {
        val locationTrackToBeEdited = EAST_LT_NAME

        val trackLayoutPage = goToMap().switchToDraftMode()

        trackLayoutPage.selectionPanel.selectReferenceLine(TRACK_NUMBER_WEST.number.toString())
        trackLayoutPage.toolPanel.referenceLineLocation.zoomTo()

        trackLayoutPage.selectionPanel.selectLocationTrack(locationTrackToBeEdited)
        val infobox = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        val orgTunniste = infobox.oid
        val orgTila = infobox.state
        val orgKuvaus = infobox.description
        //TBD val orgRatanumero = infobox.ratanumero()
        val orgSijaintiraidetunnus = infobox.name

        val editDialog = infobox.edit()
        val editedTunnus = "$orgSijaintiraidetunnus-edited"
        editDialog.setName(editedTunnus)
        //TBD editDialog.editNewRatanumero("HKI1A")
        editDialog.selectType(E2ELocationTrackEditDialog.Type.SIDE)

        val editedKuvaus = "$orgKuvaus-edited"
        editDialog.setDescription(editedKuvaus)
        editDialog.selectState(E2ELocationTrackEditDialog.State.NOT_IN_USE)
        editDialog.save()

        trackLayoutPage.selectionPanel.waitUntilLocationTrackVisible(editedTunnus)
        val infoboxAfterFirstEdit = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        assertEquals(orgTunniste, infoboxAfterFirstEdit.oid)
        assertNotEquals(orgSijaintiraidetunnus, infoboxAfterFirstEdit.name)
        assertNotEquals(orgTila, infoboxAfterFirstEdit.state)
        assertNotEquals(orgKuvaus, infoboxAfterFirstEdit.description)
        //TBD assertNotEquals(orgRatanumero, infoboxAfterFirstEdit.ratanumero())

        val previewChangesPage = trackLayoutPage.goToPreview()
        val changePreviewTable = previewChangesPage.changesTable
        assertTrue(changePreviewTable.rows.isNotEmpty())

        val changedAlignment = changePreviewTable.rows.first { row -> row.name.contains(editedTunnus) }

        val nameColumnValue = "Sijaintiraide $editedTunnus"
        assertEquals(nameColumnValue, changedAlignment.name)
        assertEquals(HKI_TRACK_NUMBER_2, changedAlignment.trackNumber)

        changePreviewTable.openMenu(changedAlignment)
        previewChangesPage.revertChange(nameColumnValue)
        previewChangesPage.goToTrackLayout()
        trackLayoutPage.selectionPanel.selectLocationTrack(locationTrackToBeEdited)

        val infoBoxAfterSecondEdit = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        assertEquals(orgTunniste, infoBoxAfterSecondEdit.oid)
        assertEquals(orgSijaintiraidetunnus, infoBoxAfterSecondEdit.name)
        assertEquals(orgTila, infoBoxAfterSecondEdit.state)
        assertEquals(orgKuvaus, infoBoxAfterSecondEdit.description)
        //TBD assertEquals(orgRatanumero, infoBoxAfterSecondEdit.ratanumero())

    }

    @Test
    fun `Edit and save location track changes`() {
        val locationTrackToBeEdited = WEST_LT_NAME
        val trackLayoutPage = goToMap().switchToDraftMode()

        trackLayoutPage.selectionPanel.selectReferenceLine(TRACK_NUMBER_WEST.number.toString())
        trackLayoutPage.toolPanel.referenceLineLocation.zoomTo()
        trackLayoutPage.selectionPanel.selectLocationTrack(locationTrackToBeEdited)
        val infobox = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        val orgTunniste = infobox.oid
        val orgTila = infobox.state
        val orgKuvaus = infobox.description
        //TBD val orgRatanumero = infobox.ratanumero()
        val orgSijaintiraidetunnus = infobox.name

        val editDialog = infobox.edit()
        val editedTunnus = "$orgSijaintiraidetunnus-edited"
        editDialog.setName(editedTunnus)
        //TBD editDialog.editNewRatanumero("HKI1A")
        editDialog.selectType(E2ELocationTrackEditDialog.Type.SIDE)

        val editedKuvaus = "$orgKuvaus-edited"
        editDialog.setDescription(editedKuvaus)
        editDialog.selectState(E2ELocationTrackEditDialog.State.NOT_IN_USE)
        editDialog.save()
        waitAndClearToaster()

        trackLayoutPage.selectionPanel.waitUntilLocationTrackVisible(editedTunnus)
        val infoboxAfterFirstEdit = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        assertEquals(orgTunniste, infoboxAfterFirstEdit.oid)
        assertNotEquals(orgSijaintiraidetunnus, infoboxAfterFirstEdit.name)
        assertNotEquals(orgTila, infoboxAfterFirstEdit.state)
        assertNotEquals(orgKuvaus, infoboxAfterFirstEdit.description)
        //TBD assertNotEquals(orgRatanumero, infoboxAfterFirstEdit.ratanumero())

        val previewChangesPage = trackLayoutPage.goToPreview()
        val changePreviewTable = previewChangesPage.changesTable
        assertTrue(changePreviewTable.rows.isNotEmpty())

        val changedAlignment = changePreviewTable.rows.first { row -> row.name.contains(editedTunnus) }

        assertEquals("Sijaintiraide $editedTunnus", changedAlignment.name)
        assertEquals(HKI_TRACK_NUMBER_1, changedAlignment.trackNumber)
        previewChangesPage.stageChange(changedAlignment.name)

        previewChangesPage.publish()
        waitAndAssertToaster("Muutokset julkaistu")

        val infoBoxAfterSecondEdit = trackLayoutPage.toolPanel.locationTrackGeneralInfo
        assertEquals(orgTunniste, infoBoxAfterSecondEdit.oid)
        assertNotEquals(orgSijaintiraidetunnus, infoBoxAfterSecondEdit.name)
        assertNotEquals(orgTila, infoBoxAfterSecondEdit.state)
        assertNotEquals(orgKuvaus, infoBoxAfterSecondEdit.description)
        //TBD assertEquals(orgRatanumero, infoBoxAfterSecondEdit.ratanumero())

        WEST_LT_NAME = infoBoxAfterSecondEdit.name
    }

    fun insertReferenceLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) {
        val alignmentVersion = alignmentDao.insert(lineAndAlignment.second)
        referenceLineDao.insert(lineAndAlignment.first.copy(alignmentVersion = alignmentVersion))
    }
}
