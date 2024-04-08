package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.geometry.testFile
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
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
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

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
        TRACK_NUMBER_WEST = trackNumber(HKI_TRACK_NUMBER_1, draft = false)
        val trackNumberEast = trackNumber(HKI_TRACK_NUMBER_2, draft = false)
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
        val locationTrackToBeEdited = EAST_LT_NAME.toString()
        val trackLayoutPage = goToMap().switchToDraftMode()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel
        val locationTrackInfobox = toolPanel.locationTrackGeneralInfo

        selectionPanel.selectReferenceLine(TRACK_NUMBER_WEST.number.toString())
        toolPanel.referenceLineLocation.zoomTo()

        selectionPanel.selectLocationTrack(locationTrackToBeEdited)
        val editDialog = locationTrackInfobox.edit()

        editDialog
            .setName("R36240")
            .setDescription("R36240 kuvaus")
            .selectType(E2ELocationTrackEditDialog.Type.SIDE)
            .selectState(E2ELocationTrackEditDialog.State.NOT_IN_USE)
            .save()

        waitAndClearToast("location-track-dialog.modified-successfully")

        selectionPanel.waitUntilLocationTrackVisible("R36240")

        assertEquals("R36240", locationTrackInfobox.name)

        locationTrackInfobox.waitUntilDescriptionChanges("R36240 kuvaus")
        assertEquals("R36240 kuvaus", locationTrackInfobox.description)

        assertEquals(E2ELocationTrackEditDialog.Type.SIDE.name, locationTrackInfobox.type)
        assertEquals(E2ELocationTrackEditDialog.State.NOT_IN_USE.name, locationTrackInfobox.state)

        val previewChangesPage = trackLayoutPage.goToPreview()
        previewChangesPage.waitForAllTableValidationsToComplete()

        val changePreviewTable = previewChangesPage.changesTable

        assertTrue(changePreviewTable.rows.isNotEmpty())

        val changedAlignment = changePreviewTable.rows.first { row -> row.name.contains("R36240") }

        assertEquals("Sijaintiraide R36240", changedAlignment.name)

        previewChangesPage.revertChange("Sijaintiraide R36240").goToTrackLayout()
        selectionPanel.selectLocationTrack(locationTrackToBeEdited)

        assertNotEquals("R36240", locationTrackInfobox.name)
        locationTrackInfobox.waitUntilDescriptionChanges("east location track description")
        assertNotEquals("R36240 kuvaus", locationTrackInfobox.description)

        assertNotEquals(E2ELocationTrackEditDialog.Type.SIDE.name, locationTrackInfobox.type)
        assertNotEquals(E2ELocationTrackEditDialog.State.NOT_IN_USE.name, locationTrackInfobox.state)
    }

    @Test
    fun `Edit and save location track changes`() {
        val locationTrackToBeEdited = WEST_LT_NAME.toString()
        val trackLayoutPage = goToMap().switchToDraftMode()
        val selectionPanel = trackLayoutPage.selectionPanel
        val toolPanel = trackLayoutPage.toolPanel
        val locationTrackInfobox = toolPanel.locationTrackGeneralInfo

        selectionPanel.selectReferenceLine(TRACK_NUMBER_WEST.number.toString())
        toolPanel.referenceLineLocation.zoomTo()

        selectionPanel.selectLocationTrack(locationTrackToBeEdited)
        val editDialog = locationTrackInfobox.edit()

        editDialog
            .setName("R36240")
            .setDescription("R36240 kuvaus")
            .save()

        waitAndClearToast("location-track-dialog.modified-successfully")

        selectionPanel.waitUntilLocationTrackVisible("R36240")

        assertEquals("R36240", locationTrackInfobox.name)
        locationTrackInfobox.waitUntilDescriptionChanges("R36240 kuvaus")

        val previewChangesPage = trackLayoutPage.goToPreview()
        val changePreviewTable = previewChangesPage.changesTable

        assertTrue(changePreviewTable.rows.isNotEmpty())

        val changedAlignment = changePreviewTable.rows.first { row -> row.name.contains("R36240") }

        assertEquals("Sijaintiraide R36240", changedAlignment.name)

        previewChangesPage.stageChange("Sijaintiraide R36240").publish()
        //selectionPanel.selectLocationTrack("R36240")

        assertEquals("R36240", locationTrackInfobox.name)
        locationTrackInfobox.waitUntilDescriptionChanges("R36240 kuvaus")
    }

    fun insertReferenceLine(lineAndAlignment: Pair<ReferenceLine, LayoutAlignment>) {
        val alignmentVersion = alignmentDao.insert(lineAndAlignment.second)
        referenceLineDao.insert(lineAndAlignment.first.copy(alignmentVersion = alignmentVersion))
    }
}
