package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.projektivelho.*
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.MATERIAL
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.PROJECT
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.LocalizationKey
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

val materialDictionaries: Map<PVDictionaryType, List<PVDictionaryEntry>> = mapOf(
    DOCUMENT_TYPE to listOf(
        PVDictionaryEntry("dokumenttityyppi/dt01", "test doc type 1"),
        PVDictionaryEntry("dokumenttityyppi/dt02", "test doc type 2"),
    ),
    MATERIAL_STATE to listOf(
        PVDictionaryEntry("aineistotila/tila01", "test mat state 1"),
        PVDictionaryEntry("aineistotila/tila02", "test mat state 2"),
    ),
    MATERIAL_CATEGORY to listOf(
        PVDictionaryEntry("aineistolaji/al00", "test mat category 0"),
        PVDictionaryEntry("aineistolaji/al01", "test mat category 1"),
    ),
    MATERIAL_GROUP to listOf(
        PVDictionaryEntry("aineistoryhma/ar00", "test mat group 0"),
        PVDictionaryEntry("aineistoryhma/ar01", "test mat group 1"),
    ),
    TECHNICS_FIELD to listOf(
        PVDictionaryEntry("tekniikka-ala/ta00", "test tech field 0"),
        PVDictionaryEntry("tekniikka-ala/ta01", "test tech field 1"),
    ),
)

val projectDictionaries: Map<PVDictionaryType, List<PVDictionaryEntry>> = mapOf(
    PROJECT_STATE to listOf(
        PVDictionaryEntry("tila/tila14", "test state 14"),
        PVDictionaryEntry("tila/tila15", "test state 15"),
    ),
)

@ActiveProfiles("dev", "test")
@SpringBootTest(properties = ["geoviite.projektivelho=true"])
class PVIntegrationServiceIT @Autowired constructor(
    @Value("\${geoviite.projektivelho.test-port:12345}") private val projektiVelhoPort: Int,
    private val pvIntegrationService: PVIntegrationService,
    private val pvDao: PVDao,
    private val pvDocumentService: PVDocumentService,
    private val jsonMapper: ObjectMapper,
) : DBTestBase() {

    fun fakeProjektiVelho() = FakeProjektiVelho(projektiVelhoPort, jsonMapper)

    @BeforeEach
    fun setup() {
        transactional {
            jdbc.update("delete from projektivelho.document_content where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.document_rejection where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.document where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.document_type where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.material_category where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.material_group where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.material_state where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.technics_field where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.project_state where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.assignment where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.project where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.project_group where true", mapOf<String, Any>())
        }
    }

    @Test
    fun `Spinning up search works`(): Unit = fakeProjektiVelho().use { fakeProjektiVelho ->
        fakeProjektiVelho.login()
        fakeProjektiVelho.search()
        val search = pvIntegrationService.search()
        assertNotNull(search)
        assertEquals(search.searchId, pvDao.fetchLatestActiveSearch()?.token)
    }

    @Test
    fun `Dictionary update works`(): Unit = fakeProjektiVelho().use { fakeProjektiVelho ->
        fakeProjektiVelho.login()
        PVDictionaryType.values().forEach { type ->
            assertEquals(mapOf(), pvDao.fetchDictionary(type))
        }

        fakeProjektiVelho.fetchDictionaries(MATERIAL, materialDictionaries)
        fakeProjektiVelho.fetchDictionaries(PROJECT, projectDictionaries)
        pvIntegrationService.updateDictionaries()
        PVDictionaryType.values().forEach { type ->
            assertEquals(
                (materialDictionaries+projectDictionaries)[type]!!.map { e -> e.code to e.name }.associate { it },
                pvDao.fetchDictionary(type),
            )
        }

        val materialDictionaries2: Map<PVDictionaryType, List<PVDictionaryEntry>> = mapOf(
            DOCUMENT_TYPE to listOf(
                PVDictionaryEntry("dokumenttityyppi/dt01", "test doc type 1"),
                PVDictionaryEntry("dokumenttityyppi/dt02", "test doc type 2 altered"),
                PVDictionaryEntry("dokumenttityyppi/dt03", "test doc type 3 added"),
            ),
            MATERIAL_STATE to listOf(
                PVDictionaryEntry("aineistotila/tila01", "test mat state 1"),
                PVDictionaryEntry("aineistotila/tila02", "test mat state 2 altered"),
                PVDictionaryEntry("aineistotila/tila03", "test mat state 3 added"),
            ),
            MATERIAL_CATEGORY to listOf(
                PVDictionaryEntry("aineistolaji/al00", "test mat category 0 altered"),
                PVDictionaryEntry("aineistolaji/al01", "test mat category 1"),
                PVDictionaryEntry("aineistolaji/al02", "test mat category 2 added"),
            ),
            MATERIAL_GROUP to listOf(
                PVDictionaryEntry("aineistoryhma/ar00", "test mat group 0 altered"),
                PVDictionaryEntry("aineistoryhma/ar01", "test mat group 1"),
                PVDictionaryEntry("aineistoryhma/ar02", "test mat group 2 added"),
            ),
            TECHNICS_FIELD to listOf(
                PVDictionaryEntry("tekniikka-ala/ta00", "test tech field 0"),
                PVDictionaryEntry("tekniikka-ala/ta01", "test tech field 1 altered"),
                PVDictionaryEntry("tekniikka-ala/ta02", "test tech field 2 added"),
            ),
        )
        val projectDictionaries2: Map<PVDictionaryType, List<PVDictionaryEntry>> = mapOf(
            PROJECT_STATE to listOf(
                PVDictionaryEntry("tila/tila14", "test state 14 altered"),
                PVDictionaryEntry("tila/tila15", "test state 15"),
                PVDictionaryEntry("tila/tila15", "test state 16 added"),
            ),
        )
        fakeProjektiVelho.fetchDictionaries(MATERIAL, materialDictionaries2)
        fakeProjektiVelho.fetchDictionaries(PROJECT, projectDictionaries2)
        pvIntegrationService.updateDictionaries()
        PVDictionaryType.values().forEach { type ->
            assertEquals(
                (materialDictionaries2+projectDictionaries2)[type]!!.map { e -> e.code to e.name }.associate { it },
                pvDao.fetchDictionary(type),
            )
        }
    }

    @Test
    fun `Importing through search happy case works`(): Unit = fakeProjektiVelho().use { fakeProjektiVelho ->
        val searchId = PVId("123")
        val documentOid = Oid<PVDocument>("1.2.3.4.5")
        val assignmentOid = Oid<PVAssignment>("1.2.4.5.6")
        val version = PVId("1")
        val description = "description 1"
        val documentType = materialDictionaries[DOCUMENT_TYPE]!!.first().code
        val materialState = materialDictionaries[MATERIAL_STATE]!!.first().code
        val materialGroup = materialDictionaries[MATERIAL_GROUP]!!.first().code
        val materialCategory = materialDictionaries[MATERIAL_CATEGORY]!!.first().code

        fakeProjektiVelho.login()
        fakeProjektiVelho.fetchDictionaries(MATERIAL, materialDictionaries)
        fakeProjektiVelho.fetchDictionaries(PROJECT, projectDictionaries)
        fakeProjektiVelho.searchStatus(searchId)
        fakeProjektiVelho.searchResults(searchId, listOf(PVApiMatch(documentOid, assignmentOid)))
        fakeProjektiVelho.fileMetadata(
            documentOid,
            version,
            description = description,
            documentType = documentType,
            materialState = materialState,
            materialGroup = materialGroup,
            materialCategory = materialCategory,
        )
        fakeProjektiVelho.fileContent(documentOid)

        pvIntegrationService.updateDictionaries()
        pvDao.insertFetchInfo(searchId, Instant.now().plusSeconds(3600))
        val search = pvDao.fetchLatestActiveSearch()!!
        val status = pvIntegrationService.getSearchStatusIfReady(search)!!

        pvIntegrationService.importFilesFromProjektiVelho(search, status)
        assertDocumentExists(
            documentOid,
            PVDocumentStatus.SUGGESTED,
            description = description,
            documentType = documentType,
            materialState = materialState,
            materialGroup = materialGroup,
            materialCategory = materialCategory,
        )
    }

    @Test
    fun `Document count fetching works`() {
        insertTestDictionary()
        insertDocumentMetaWithStatus(Oid("1.2.3.4.5"), PVDocumentStatus.SUGGESTED)
        insertDocumentMetaWithStatus(Oid("1.2.3.4.6"), PVDocumentStatus.SUGGESTED)
        insertDocumentMetaWithStatus(Oid("1.2.3.4.7"), PVDocumentStatus.REJECTED)
        val counts = pvDocumentService.getDocumentCounts()
        assertEquals(2, counts.suggested)
        assertEquals(1, counts.rejected)
    }

    @Test
    fun `Document rejection works`() {
        insertTestDictionary()
        val version = insertDocumentMetaWithStatus(Oid("1.2.3.4.7"), PVDocumentStatus.REJECTED)
        pvDao.insertRejection(version, "test")
        val rejection = pvDao.getRejection(version)

        assertEquals(version, rejection.documentVersion)
        assertEquals(LocalizationKey("test"), rejection.reason)
    }

    private fun assertDocumentExists(
        oid: Oid<PVDocument>,
        status: PVDocumentStatus,
        description: String = "test description",
        documentType: PVDictionaryCode = PVDictionaryCode("dokumenttityyppi/dt01"),
        materialState: PVDictionaryCode = PVDictionaryCode("aineistotila/tila01"),
        materialCategory: PVDictionaryCode = PVDictionaryCode("aineistolaji/al00"),
        materialGroup: PVDictionaryCode = PVDictionaryCode("aineistoryhma/ar00"),
    ) {
        val header = pvDao.getDocumentHeaders().find { d -> d.document.oid == oid }
        assertNotNull(header, "Document should exist with OID=$oid")
        assertEquals(description, header.document.description?.toString())
        assertEquals(status, header.document.status)
        assertEquals(getTestDataDictionaryName(DOCUMENT_TYPE, documentType)!!, header.document.type)
        assertEquals(getTestDataDictionaryName(MATERIAL_STATE, materialState)!!, header.document.state)
        assertEquals(getTestDataDictionaryName(MATERIAL_GROUP, materialGroup)!!, header.document.group)
        assertEquals(getTestDataDictionaryName(MATERIAL_CATEGORY, materialCategory)!!, header.document.category)
    }

    private fun insertTestDictionary() {
        pvDao.upsertDictionary(DOCUMENT_TYPE, listOf(PVDictionaryEntry("test", "test")))
        pvDao.upsertDictionary(MATERIAL_CATEGORY, listOf(PVDictionaryEntry("test", "test")))
        pvDao.upsertDictionary(MATERIAL_GROUP, listOf(PVDictionaryEntry("test", "test")))
        pvDao.upsertDictionary(MATERIAL_STATE, listOf(PVDictionaryEntry("test", "test")))
    }

    private fun insertDocumentMetaWithStatus(oid: Oid<PVDocument>, status: PVDocumentStatus) =
        pvDao.insertDocumentMetadata(
            oid = oid,
            assignmentOid = null,
            latestVersion = PVApiLatestVersion(
                version = PVId("test"),
                name = FileName("test"),
                changeTime = Instant.now()
            ),
            metadata = PVApiDocumentMetadata(
                materialCategory = PVDictionaryCode("test"),
                description = null,
                materialGroup = PVDictionaryCode("test"),
                materialState = PVDictionaryCode("test"),
                documentType = PVDictionaryCode("test"),
                technicalFields = emptyList(),
                containsPersonalInfo = false
            ),
            projectGroupOid = null,
            projectOid = null,
            status = status
        )
}

private fun getTestDataDictionaryName(type: PVDictionaryType, code: PVDictionaryCode) =
    (materialDictionaries+ projectDictionaries)[type]?.find { e -> e.code == code }?.name
