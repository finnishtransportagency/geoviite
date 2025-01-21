package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToast
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EInfraModelPage
import fi.fta.geoviite.infra.ui.util.scrollIntoView
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("dev", "test", "e2e")
@EnableAutoConfiguration
@SpringBootTest
class InfraModelTestUI : SeleniumTest() {
    val TESTFILE_SIMPLE_PATH: String = "src/test/resources/inframodel/testfile_simple.xml"
    val TESTFILE_CLOTHOID_AND_PARABOLA_PATH: String = "src/test/resources/inframodel/testfile_clothoid_and_parabola.xml"
    val TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH: String =
        "src/test/resources/inframodel/testfile_clothoid_and_parabola_2.xml"

    @BeforeEach
    fun clearDb() {
        testDBService.clearAllTables()
    }

    fun startGeoviiteAndGoToWork(): E2EInfraModelPage {
        startGeoviite()
        return goToInfraModelPage()
    }

    @Test
    fun `Import and edit testfile_simple_xml`() {
        val file = File(TESTFILE_SIMPLE_PATH)
        val infraModelPage = startGeoviiteAndGoToWork()

        infraModelPage.upload(file.absolutePath).saveAsNew()
        val infraModelEditPage = infraModelPage.openInfraModel("testfile_simple.xml")

        val newProjectName = "Test"
        val projektinTiedot = infraModelEditPage.metaFormGroup
        projektinTiedot.selectNewProject(newProjectName)

        infraModelEditPage.save(true)
        waitAndClearToast("infra-model.edit.success")

        val uploadedPlanRow = infraModelPage.infraModelsList.getRow(projectName = newProjectName)
        assertNotNull(uploadedPlanRow)
    }

    @Test
    fun `Import testfile_clothoid_and_parabola_xml`() {
        val file = File(TESTFILE_CLOTHOID_AND_PARABOLA_PATH)
        val infraModelPage = startGeoviiteAndGoToWork()
        val uploadForm = infraModelPage.upload(file.absolutePath)
        val projektinTiedot = uploadForm.metaFormGroup

        assertEquals("TEST_Clothoid_and_parabola", projektinTiedot.project)
        assertEquals("Geoviite", projektinTiedot.author)

        val sijaintitiedot = uploadForm.locationFormGroup
        assertEquals("001", sijaintitiedot.trackNumber)
        assertEquals("", sijaintitiedot.kmNumberRange)
        assertEquals("KKJ2 EPSG:2392", sijaintitiedot.coordinateSystem)
        assertEquals("Ei tiedossa", sijaintitiedot.verticalCoordinateSystem)

        val tilanneJaLaatutiedot = uploadForm.qualityFormGroup
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.planPhase)
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.decisionPhase)
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.measurementMethod)

        // FIXME: disabled until we can se timezone to Helsinki/Finland in AWS
        // val lokiJaLinkitystiedot = uploadForm.lokiJaLinkitystiedot()
        // assertEquals("11.02.2021", lokiJaLinkitystiedot.laadittu())

        uploadForm.saveAsNew()
        val infraModelPageAfterUpload = E2EInfraModelPage()
        val infraModelRowsAfterUpload = infraModelPageAfterUpload.infraModelsList

        val uploadedPlanRow =
            infraModelRowsAfterUpload.getItemWhenMatches { r -> r.projectName == "TEST_Clothoid_and_parabola" }
        assertNotNull(uploadedPlanRow)
        assertEquals("testfile_clothoid_and_parabola.xml", uploadedPlanRow.fileName)

        logger.info("Local date time: ${LocalDateTime.now()} and upload time is ${uploadedPlanRow.created}")

        assertThat(uploadedPlanRow.created).isBefore(LocalDateTime.now())
    }

    @Test
    fun `Edit and import testfile_clothoid_and_parabola_2_xml`() {
        val file = File(TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH)

        val infraModelPage = startGeoviiteAndGoToWork()
        val uploadForm = infraModelPage.upload(file.absolutePath)

        // Projektin tiedot
        val projektinTiedot = uploadForm.metaFormGroup

        val projektinNimi = "E2E IM upload and edit project"
        projektinTiedot.selectNewProject(projektinNimi)
        assertEquals(projektinNimi, projektinTiedot.project)

        val suunnitteluyritys = "Rane ja rautatiet"
        projektinTiedot.selectNewAuthor(suunnitteluyritys)
        assertEquals(suunnitteluyritys, projektinTiedot.author)

        // Sijaintitiedot
        val sijaintitiedot = uploadForm.locationFormGroup
        val ratanumero = "123E2E"
        sijaintitiedot.selectManualTrackNumber(ratanumero)
        assertEquals(ratanumero, sijaintitiedot.trackNumber)

        // Tilanne ja laatutiedot
        val tilanneJaLaatutiedot = uploadForm.qualityFormGroup
        val suunnitteluvaihe = "Peruskorjaus"
        tilanneJaLaatutiedot.selectPlanPhase(suunnitteluvaihe)
        assertEquals(suunnitteluvaihe, tilanneJaLaatutiedot.planPhase)
        val vaiheenTarkennus = "Valmis"
        tilanneJaLaatutiedot.selectDecisionPhase(vaiheenTarkennus)
        assertEquals(vaiheenTarkennus, tilanneJaLaatutiedot.decisionPhase)
        val laatu = "Digitoitu ilmakuvasta"
        tilanneJaLaatutiedot.selectMeasurementMethod(laatu)
        assertEquals(laatu, tilanneJaLaatutiedot.measurementMethod)
        val korkeusasema = "Kiskon selkä"
        tilanneJaLaatutiedot.selectElevationMeasurementMethod(korkeusasema)
        assertEquals(korkeusasema, tilanneJaLaatutiedot.elevationMeasurementMethod)

        // Loki- ja linkitysdata
        val lokiJaLinkitystiedotFormGroup = uploadForm.logFormGroup
        lokiJaLinkitystiedotFormGroup.scrollIntoView(true)
        lokiJaLinkitystiedotFormGroup.setPlanTime("elokuu", "1999")
        assertEquals("01.08.1999", lokiJaLinkitystiedotFormGroup.planTime)

        uploadForm.save(true)
        waitAndClearToast("infra-model.upload.success")
        val infraModelPageAfterUpload = E2EInfraModelPage()
        val infraModelRowsAfterUpload = infraModelPageAfterUpload.infraModelsList

        val uploadedPlanRow = infraModelRowsAfterUpload.getRow(projectName = projektinNimi)
        assertNotNull(uploadedPlanRow)
        assertEquals("testfile_clothoid_and_parabola_2.xml", uploadedPlanRow.fileName)
    }

    @Test
    fun `Search and sort Infra Model files`() {
        testDBService.clearAllTables()

        val file1 = File(TESTFILE_SIMPLE_PATH)
        val file2 = File(TESTFILE_CLOTHOID_AND_PARABOLA_PATH)
        val file3 = File(TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH)

        val infraModelPage = startGeoviiteAndGoToWork()

        val uploadForm1 = infraModelPage.upload(file1.absolutePath)
        uploadForm1.saveAsNew()

        val uploadForm2 = infraModelPage.upload(file2.absolutePath)
        uploadForm2.saveAsNew()

        val uploadForm3 = infraModelPage.upload(file3.absolutePath)
        uploadForm3.saveAsNew()

        val table = infraModelPage.infraModelsList

        // sorting
        table.sortBy("Laadittu")
        var rows = table.rows
        assertThat(rows[0].planTime).isBefore(rows[1].planTime)

        table.sortBy("Nimi")
        rows = table.rows
        assertThat(rows[0].fileName).isLessThan(rows[1].fileName)

        // searching
        val files: List<File> = listOf(file1, file2, file3)
        val randomIndex = (files.indices).random()
        val randomFile = files[randomIndex]

        infraModelPage.search(randomFile.name)
        rows = table.rows
        assertThat(rows.first().fileName).isEqualTo(randomFile.name)
    }
}
