package fi.fta.geoviite.infra.projektivelho

import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.DBTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.localization.LocalizationKey
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.MATERIAL
import fi.fta.geoviite.infra.projektivelho.PVDictionaryGroup.PROJECT
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.DOCUMENT_TYPE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_CATEGORY
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_GROUP
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.MATERIAL_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.PROJECT_STATE
import fi.fta.geoviite.infra.projektivelho.PVDictionaryType.TECHNICS_FIELD
import fi.fta.geoviite.infra.util.UnsafeString
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ActiveProfiles("dev", "test")
@SpringBootTest(properties = ["geoviite.projektivelho=true"])
class PVIntegrationServiceIT
@Autowired
constructor(
    @Value("\${geoviite.projektivelho.test-port:12346}") private val projektiVelhoPort: Int,
    private val pvIntegrationService: PVIntegrationService,
    private val pvDao: PVDao,
    private val pvDocumentService: PVDocumentService,
    private val jsonMapper: ObjectMapper,
) : DBTestBase() {

    fun fakeProjektiVelho() = FakeProjektiVelho(projektiVelhoPort, jsonMapper)

    @BeforeEach
    fun setup() {
        testDBService.clearProjektivelhoTables()
    }

    @Test
    fun `Spinning up search works`(): Unit =
        fakeProjektiVelho().use { fakeProjektiVelho ->
            fakeProjektiVelho.login()
            fakeProjektiVelho.search()
            val search = pvIntegrationService.search()
            assertNotNull(search)
            assertEquals(search.searchId, pvDao.fetchLatestActiveSearch()?.token)
        }

    @Test
    fun `Dictionary update works`(): Unit =
        fakeProjektiVelho().use { fakeProjektiVelho ->
            fakeProjektiVelho.login()
            PVDictionaryType.entries.forEach { type -> assertEquals(mapOf(), pvDao.fetchDictionary(type)) }

            fakeProjektiVelho.fetchDictionaries(MATERIAL, materialDictionaries)
            fakeProjektiVelho.fetchDictionaries(PROJECT, projectDictionaries)
            pvIntegrationService.updateDictionaries()
            PVDictionaryType.entries.forEach { type ->
                assertEquals(
                    (materialDictionaries + projectDictionaries)[type]!!
                        .map { e -> e.code to PVDictionaryName(e.name) }
                        .associate { it },
                    pvDao.fetchDictionary(type),
                )
            }

            val materialDictionaries2: Map<PVDictionaryType, List<PVApiDictionaryEntry>> =
                mapOf(
                    DOCUMENT_TYPE to
                        listOf(
                            PVApiDictionaryEntry("dokumenttityyppi/dt01", "test doc type 1"),
                            PVApiDictionaryEntry("dokumenttityyppi/dt02", "test doc type 2 altered"),
                            PVApiDictionaryEntry("dokumenttityyppi/dt03", "test doc type 3 added"),
                        ),
                    MATERIAL_STATE to
                        listOf(
                            PVApiDictionaryEntry("aineistotila/tila01", "test mat state 1"),
                            PVApiDictionaryEntry("aineistotila/tila02", "test mat state 2 altered"),
                            PVApiDictionaryEntry("aineistotila/tila03", "test mat state 3 added"),
                        ),
                    MATERIAL_CATEGORY to
                        listOf(
                            PVApiDictionaryEntry("aineistolaji/al00", "test mat category 0 altered"),
                            PVApiDictionaryEntry("aineistolaji/al01", "test mat category 1"),
                            PVApiDictionaryEntry("aineistolaji/al02", "test mat category 2 added"),
                        ),
                    MATERIAL_GROUP to
                        listOf(
                            PVApiDictionaryEntry("aineistoryhma/ar00", "test mat group 0 altered"),
                            PVApiDictionaryEntry("aineistoryhma/ar01", "test mat group 1"),
                            PVApiDictionaryEntry("aineistoryhma/ar02", "test mat group 2 added"),
                        ),
                    TECHNICS_FIELD to
                        listOf(
                            PVApiDictionaryEntry("tekniikka-ala/ta00", "test tech field 0"),
                            PVApiDictionaryEntry("tekniikka-ala/ta01", "test tech field 1 altered"),
                            PVApiDictionaryEntry("tekniikka-ala/ta02", "test tech field 2 added"),
                        ),
                )
            val projectDictionaries2: Map<PVDictionaryType, List<PVApiDictionaryEntry>> =
                mapOf(
                    PROJECT_STATE to
                        listOf(
                            PVApiDictionaryEntry("tila/tila14", "test state 14 altered"),
                            PVApiDictionaryEntry("tila/tila15", "test state 15"),
                            PVApiDictionaryEntry("tila/tila15", "test state 16 added"),
                        )
                )
            fakeProjektiVelho.fetchDictionaries(MATERIAL, materialDictionaries2)
            fakeProjektiVelho.fetchDictionaries(PROJECT, projectDictionaries2)
            pvIntegrationService.updateDictionaries()
            PVDictionaryType.entries.forEach { type ->
                assertEquals(
                    (materialDictionaries2 + projectDictionaries2)[type]!!
                        .map { e -> e.code to PVDictionaryName(e.name) }
                        .associate { it },
                    pvDao.fetchDictionary(type),
                )
            }
        }

    @Test
    fun `Importing through search happy case works`(): Unit =
        fakeProjektiVelho().use { fakeProjektiVelho ->
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
        insertTestDictionary(pvDao)
        insertDocumentMetaWithStatus(pvDao, Oid("1.2.3.4.5"), PVDocumentStatus.SUGGESTED)
        insertDocumentMetaWithStatus(pvDao, Oid("1.2.3.4.6"), PVDocumentStatus.SUGGESTED)
        insertDocumentMetaWithStatus(pvDao, Oid("1.2.3.4.7"), PVDocumentStatus.REJECTED)
        val counts = pvDocumentService.getDocumentCounts()
        assertEquals(2, counts.suggested)
        assertEquals(1, counts.rejected)
    }

    @Test
    fun `Document rejection works`() {
        insertTestDictionary(pvDao)
        val version = insertDocumentMetaWithStatus(pvDao, Oid("1.2.3.4.7"), PVDocumentStatus.REJECTED)
        pvDao.insertRejection(version, "test")
        val rejection = pvDao.getRejection(version)

        assertEquals(version, rejection.documentVersion)
        assertEquals(LocalizationKey.of("test"), rejection.reason)
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
}

fun insertTestDictionary(pvDao: PVDao) {
    pvDao.upsertDictionary(DOCUMENT_TYPE, listOf(PVApiDictionaryEntry("test", "test")))
    pvDao.upsertDictionary(MATERIAL_CATEGORY, listOf(PVApiDictionaryEntry("test", "test")))
    pvDao.upsertDictionary(MATERIAL_GROUP, listOf(PVApiDictionaryEntry("test", "test")))
    pvDao.upsertDictionary(MATERIAL_STATE, listOf(PVApiDictionaryEntry("test", "test")))
}

fun insertDocumentMetaWithStatus(pvDao: PVDao, oid: Oid<PVDocument>, status: PVDocumentStatus) =
    pvDao.insertDocumentMetadata(
        oid = oid,
        assignmentOid = null,
        latestVersion =
            PVApiLatestVersion(version = PVId("test"), name = UnsafeString("test"), changeTime = Instant.now()),
        metadata =
            PVApiDocumentMetadata(
                materialCategory = PVDictionaryCode("test"),
                description = null,
                materialGroup = PVDictionaryCode("test"),
                materialState = PVDictionaryCode("test"),
                documentType = PVDictionaryCode("test"),
                technicalFields = emptyList(),
                containsPersonalInfo = false,
            ),
        projectGroupOid = null,
        projectOid = null,
        status = status,
    )

private fun getTestDataDictionaryName(type: PVDictionaryType, code: PVDictionaryCode): PVDictionaryName? =
    (materialDictionaries + projectDictionaries)[type]?.find { e -> e.code == code }?.name?.let(::PVDictionaryName)
