package fi.fta.geoviite.infra.ui.testgroup1

import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.switchLibrary.SwitchStructureDao
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.ui.SeleniumTest
import fi.fta.geoviite.infra.ui.pagemodel.inframodel.VelhoListItem
import fi.fta.geoviite.infra.ui.util.byQaId
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.FreeText
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals

@ActiveProfiles("dev", "test", "e2e")
@SpringBootTest
class VelhoTestUI @Autowired constructor(
    private val pvDao: PVDao,
) : SeleniumTest() {

    @BeforeEach
    fun clearVelhoTestData() {
        deleteFromTables("projektivelho", *velhoTables.toTypedArray())
    }

    @Test
    fun rejectAndRestore() {
        materialDictionaries.forEach(pvDao::upsertDictionary)
        projectDictionaries.forEach(pvDao::upsertDictionary)

        insertFullExampleVelhoDocumentMetadata()
        startGeoviite()
        val velhoPage = goToInfraModelPage().openVelhoWaitingForApprovalList()
        fun identifyTestProject(item: VelhoListItem) = item.getColumnContent("Projektin tiedot") == "testi_projekti"
        fun assertTestProjectIsVisible() {
            assertEquals(
                "foo bar.xml",
                velhoPage.getItemWhenMatches(::identifyTestProject).getColumnContent("Dokumentin nimi"))
        }

        assertTestProjectIsVisible()
        velhoPage.rejectFirstMatching(::identifyTestProject)
        velhoPage.openRejectedList()
        assertTestProjectIsVisible()
        velhoPage.restoreFirstMatching(::identifyTestProject)
        velhoPage.openWaitingForApprovalList()
        assertTestProjectIsVisible()
    }

    private fun insertFullExampleVelhoDocumentMetadata(
        documentOid: Oid<PVDocument> = Oid("1.2.3.4.5"),
        projectOid: Oid<PVProject> = Oid("5.6.7.8.9"),
        projectGroupOid: Oid<PVProjectGroup> = Oid("4.5.6.7.8"),
        assignmentOid: Oid<PVAssignment> = Oid("3.4.5.6.7"),
    ) {
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

        pvDao.insertDocumentMetadata(
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
