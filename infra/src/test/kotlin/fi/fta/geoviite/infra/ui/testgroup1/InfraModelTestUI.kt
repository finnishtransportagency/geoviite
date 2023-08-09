package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.ui.E2EProperties
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.InfraModelPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNotNull


@ActiveProfiles("dev", "test", "e2e")
@EnableAutoConfiguration
@EnableConfigurationProperties(E2EProperties::class)
@SpringBootTest
class InfraModelTestUI @Autowired constructor(
    val properties: E2EProperties,
) : SeleniumTest() {

    lateinit var infraModelPage: InfraModelPage

    val TESTFILE_SIMPLE_PATH: String = "src/test/resources/inframodel/testfile_simple.xml"
    val TESTFILE_CLOTHOID_AND_PARABOLA_PATH: String = "src/test/resources/inframodel/testfile_clothoid_and_parabola.xml"
    val TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH: String =
        "src/test/resources/inframodel/testfile_clothoid_and_parabola_2.xml"

    @BeforeAll
    fun clearDb() {
        clearAllTestData()
    }

    @BeforeEach
    fun setup() {
        startGeoviite()
        infraModelPage = goToInfraModelPage()
    }

    @Test
    fun `Import and edit testfile_simple_xml`() {
        val file = File(TESTFILE_SIMPLE_PATH)
        infraModelPage.lataaUusi(file.absolutePath).tallenna()
        val infraModelEditPage = infraModelPage.openInfraModel("testfile_simple.xml")

        val newProjectName = "Test"
        val projektinTiedot = infraModelEditPage.projektinTiedot()
        projektinTiedot.addNimi(newProjectName)

        infraModelEditPage.tallennaMuutokset()

        val uploadedPlanRow =
            infraModelPage.infraModelList().infraModelRows().find { row -> row.projektinNimi() == newProjectName }
        assertNotNull(uploadedPlanRow)
    }

    @Test
    fun `Import testfile_clothoid_and_parabola_xml`() {
        val infraModelRows = infraModelPage.infraModelList().infraModelRows()

        val file = File(TESTFILE_CLOTHOID_AND_PARABOLA_PATH)

        val uploadForm = infraModelPage.lataaUusi(file.absolutePath)
        val projektinTiedot = uploadForm.projektinTiedot()

        assertEquals("TEST_Clothoid_and_parabola", projektinTiedot.nimi())
        assertEquals("Geoviite", projektinTiedot.suunnitteluYritys())

        val sijaintitiedot = uploadForm.sijaintitiedot()
        assertEquals("", sijaintitiedot.ratanumero())
        assertEquals("", sijaintitiedot.ratakilometrivali())
        assertEquals("KKJ2 EPSG:2392", sijaintitiedot.koordinaattijarjestelma())
        assertEquals("Ei tiedossa", sijaintitiedot.korkeusjarjestelma())

        val tilanneJaLaatutiedot = uploadForm.tilanneJaLaatutiedot()
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.suunnitteluvaihe())
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.vaiheenTarkennus())
        assertEquals("Ei tiedossa", tilanneJaLaatutiedot.laatu())

        //FIXME: disabled until we can se timezone to Helsinki/Finland in AWS
        //val lokiJaLinkitystiedot = uploadForm.lokiJaLinkitystiedot()
        //assertEquals("11.02.2021", lokiJaLinkitystiedot.laadittu())

        uploadForm.tallenna()
        val infraModelPageAfterUpload = InfraModelPage()
        val infraModelRowsAfterUpload = infraModelPageAfterUpload.infraModelList().infraModelRows()
        assertEquals(infraModelRows.size + 1, infraModelRowsAfterUpload.size)

        val uploadedPlanRow =
            infraModelRowsAfterUpload.find { row -> row.projektinNimi() == "TEST_Clothoid_and_parabola" }
        assertNotNull(uploadedPlanRow)
        assertEquals("testfile_clothoid_and_parabola.xml", uploadedPlanRow.tiedostonimi())

        logger.info("Local date time: ${LocalDateTime.now()} and upload time is ${uploadedPlanRow.ladattu()}")

        assertThat(uploadedPlanRow.ladattu()).isBefore(LocalDateTime.now())
    }

    @Test
    fun `Edit and import testfile_clothoid_and_parabola_2_xml`() {
        val infraModelRowsBefore = infraModelPage.infraModelList().infraModelRows()

        val file = File(TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH)

        val uploadForm = infraModelPage.lataaUusi(file.absolutePath)

        //Projektin tiedot
        val projektinTiedot = uploadForm.projektinTiedot()

        val projektinNimi = "E2E IM upload and edit project"
        projektinTiedot.addNimi(projektinNimi)
        assertEquals(projektinNimi, projektinTiedot.nimi())

        val suunnitteluyritys = "Rane ja rautatiet"
        projektinTiedot.addNewSuunnitteluyritys(suunnitteluyritys)
        assertEquals(suunnitteluyritys, projektinTiedot.suunnitteluYritys())

        //Sijaintitiedot
        val sijaintitiedot = uploadForm.sijaintitiedot()
        val ratanumero = "123E2E"
        sijaintitiedot.addRatanumero(ratanumero, "kaunis kuvaus")
        assertEquals(ratanumero, sijaintitiedot.ratanumero())

        //Tilanne ja laatutiedot
        val tilanneJaLaatutiedot = uploadForm.tilanneJaLaatutiedot()
        val suunnitteluvaihe = "Peruskorjaus"
        tilanneJaLaatutiedot.editSuunnitteluvaihe(suunnitteluvaihe)
        assertEquals(suunnitteluvaihe, tilanneJaLaatutiedot.suunnitteluvaihe())
        val vaiheenTarkennus = "Valmis"
        tilanneJaLaatutiedot.editVaiheenTarkennus(vaiheenTarkennus)
        assertEquals(vaiheenTarkennus, tilanneJaLaatutiedot.vaiheenTarkennus())
        val laatu = "Digitoitu ilmakuvasta"
        tilanneJaLaatutiedot.editLaatu(laatu)
        assertEquals(laatu, tilanneJaLaatutiedot.laatu())

        //Loki- ja linkitysdata
        val lokiJaLinkitystiedotFormGroup = uploadForm.lokiJaLinkitystiedot()
        lokiJaLinkitystiedotFormGroup.editLaadittu("elokuu", "1999")
        assertEquals("01.08.1999", lokiJaLinkitystiedotFormGroup.laadittu())

        uploadForm.tallenna(false)
        val infraModelPageAfterUpload = InfraModelPage()
        val infraModelRowsAfterUpload = infraModelPageAfterUpload.infraModelList().infraModelRows()
        assertEquals(infraModelRowsBefore.size + 1, infraModelRowsAfterUpload.size)

        val uploadedPlanRow = infraModelRowsAfterUpload.find { row -> row.projektinNimi() == projektinNimi }
        assertNotNull(uploadedPlanRow)
        assertEquals("testfile_clothoid_and_parabola_2.xml", uploadedPlanRow.tiedostonimi())

    }

    @Test
    fun `Search and sort Infra Model files`() {
        clearAllTestData()

        val file1 = File(TESTFILE_SIMPLE_PATH)
        val file2 = File(TESTFILE_CLOTHOID_AND_PARABOLA_PATH)
        val file3 = File(TESTFILE_CLOTHOID_AND_PARABOLA_2_PATH)

        val uploadForm1 = infraModelPage.lataaUusi(file1.absolutePath)
        uploadForm1.tallenna(true)

        val uploadForm2 = infraModelPage.lataaUusi(file2.absolutePath)
        uploadForm2.tallenna(true)

        val uploadForm3 = infraModelPage.lataaUusi(file3.absolutePath)
        uploadForm3.tallenna(true)

        val table = infraModelPage.infraModelList()

        //sorting
        table.sortBy("Laadittu")
        var rows = table.infraModelRows()
        assertThat(rows[0].laadittu()).isBefore(rows[1].laadittu())

        table.sortBy("Nimi")
        rows = table.infraModelRows()
        assertThat(rows[0].tiedostonimi()).isLessThan(rows[1].tiedostonimi())

        //searching
        val files: List<File> = listOf(file1, file2, file3)
        val randomIndex = (files.indices).random()
        val randomFile = files[randomIndex]

        infraModelPage.search(randomFile.name)
        rows = table.infraModelRows()
        assertThat(rows.first().tiedostonimi()).isEqualTo(randomFile.name)

        clearAllTestData()
    }
}
