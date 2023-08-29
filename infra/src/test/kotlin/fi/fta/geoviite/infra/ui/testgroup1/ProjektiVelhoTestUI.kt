package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.common.waitAndClearToastByContent
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.E2EProjektiVelhoListItem
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.io.File
import java.time.Instant
import kotlin.test.assertEquals

const val TESTFILE_SIMPLE_ANONYMIZED_PATH: String = "src/test/resources/inframodel/testfile_simple_anonymized.xml"

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class ProjektiVelhoTestUI @Autowired constructor(
    private val pvDao: PVDao,
) : SeleniumTest() {

    @BeforeEach
    fun clearVelhoTestData() {
        clearAllTestData()
        deleteFromTables("projektivelho", *velhoTables.toTypedArray())
    }

    @Test
    fun rejectAndRestore() {
        materialDictionaries.forEach(pvDao::upsertDictionary)
        projectDictionaries.forEach(pvDao::upsertDictionary)

        insertFullExampleVelhoDocumentMetadata()
        startGeoviite()
        val velhoPage = goToInfraModelPage().openVelhoWaitingForApprovalList()
        fun identifyTestProject(item: E2EProjektiVelhoListItem) = item.projectName == "testi_projekti"
        fun assertTestProjectIsVisible() {
            assertEquals(
                "foo bar.xml",
                velhoPage.getItemWhenMatches(::identifyTestProject).documentName
            )
        }

        assertTestProjectIsVisible()
        velhoPage.rejectFirstMatching(::identifyTestProject)
        velhoPage.openRejectedList()
        assertTestProjectIsVisible()
        velhoPage.restoreFirstMatching(::identifyTestProject)
        velhoPage.openWaitingForApprovalList()
        assertTestProjectIsVisible()
    }

    @Test
    fun import() {
        materialDictionaries.forEach(pvDao::upsertDictionary)
        projectDictionaries.forEach(pvDao::upsertDictionary)

        pvDao.insertDocumentContent(
            File(TESTFILE_SIMPLE_ANONYMIZED_PATH).readText(Charsets.UTF_8),
            insertFullExampleVelhoDocumentMetadata().id
        )
        startGeoviite()
        val velhoPage = goToInfraModelPage().openVelhoWaitingForApprovalList()
        velhoPage
            // project name comes from PV metadata here
            .acceptFirstMatching { item -> item.projectName == "testi_projekti" }
            .save(true)

        waitAndClearToastByContent("Projektivelhon InfraModel-tiedosto tuotu Geoviitteeseen")

        val infraModelPage = velhoPage.goToInfraModelList()
        assertEquals(
            "foo bar.xml",
            infraModelPage.infraModelsList
                // project name comes from test file here
                .getItemWhenMatches { item -> item.projectName == "Geoviite test" }.fileName
        )
    }

    private fun insertFullExampleVelhoDocumentMetadata(
        documentOid: Oid<PVDocument> = Oid("1.2.3.4.5"),
        projectOid: Oid<PVProject> = Oid("5.6.7.8.9"),
        projectGroupOid: Oid<PVProjectGroup> = Oid("4.5.6.7.8"),
        assignmentOid: Oid<PVAssignment> = Oid("3.4.5.6.7"),
    ): RowVersion<PVDocument> {
        val someProperties = PVApiProperties(
            name = PVProjectName("testi_projekti"),
            state = PVDictionaryCode("tila/tila14"),
        )

        pvDao.upsertAssignment(
            PVApiAssignment(
                oid = assignmentOid,
                projectOid = projectOid,
                createdAt = Instant.parse("2023-01-02T03:04:05.006Z"),
                modified = Instant.parse("2023-02-02T03:04:05.006Z"),
                properties = someProperties
            )
        )

        pvDao.upsertProjectGroup(
            PVApiProjectGroup(
                properties = someProperties,
                oid = projectGroupOid,
                createdAt = Instant.parse("2023-01-02T03:04:05.006Z"),
                modified = Instant.parse("2023-02-02T03:04:05.006Z"),
            )
        )

        pvDao.upsertProject(
            PVApiProject(
                properties = someProperties,
                oid = projectOid,
                createdAt = Instant.parse("2023-01-02T03:04:05.006Z"),
                modified = Instant.parse("2023-02-02T03:04:05.006Z"),
                projectGroupOid = projectGroupOid,
            )
        )

        return pvDao.insertDocumentMetadata(
            oid = documentOid,
            metadata = PVApiDocumentMetadata(
                materialState = PVDictionaryCode("aineistotila/tila01"),
                documentType = PVDictionaryCode("dokumenttityyppi/dt01"),
                materialGroup = PVDictionaryCode("aineistoryhma/ar00"),
                technicalFields = listOf(PVDictionaryCode("tekniikka-ala/ta00")),
                materialCategory = PVDictionaryCode("aineistolaji/al00"),
                containsPersonalInfo = false,
                description = FreeText("goo goo ga ga")
            ),
            latestVersion = PVApiLatestVersion(
                version = PVId("123456"),
                name = FileName("foo bar.xml"),
                changeTime = Instant.parse("2023-03-04T05:06:07.089Z"),
            ),
            status = PVDocumentStatus.SUGGESTED,
            assignmentOid = assignmentOid,
            projectGroupOid = projectGroupOid,
            projectOid = projectOid
        )
    }
}
