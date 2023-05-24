package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVCode
import PVDictionaryEntry
import PVDictionaryGroup.MATERIAL
import PVDictionaryGroup.PROJECT
import PVDictionaryType
import PVDictionaryType.*
import PVDocument
import PVDocumentStatus
import PVId
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.projektivelho.*
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
class PVServiceIT @Autowired constructor(
    @Value("\${geoviite.projektivelho.test-port:12345}") private val velhoPort: Int,
    private val pvService: PVService,
    private val pvDao: PVDao,
    private val jsonMapper: ObjectMapper,
) : ITTestBase() {

    fun fakeVelho() = FakeProjektiVelho(velhoPort, jsonMapper)

    @BeforeEach
    fun setup() {
        transactional {
            jdbc.update("delete from projektivelho.file where true", mapOf<String, Any>())
            jdbc.update("delete from projektivelho.file_metadata where true", mapOf<String, Any>())
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
    fun `Spinning up search works`(): Unit = fakeVelho().use { fakeVelho ->
        fakeVelho.login()
        fakeVelho.search()
        val search = pvService.search()
        assertNotNull(search)
        assertEquals(search.searchId, pvDao.fetchLatestSearch()?.token)
    }
    
    @Test
    fun `Dictionary update works`(): Unit = fakeVelho().use { fakeVelho ->
        fakeVelho.login()
        PVDictionaryType.values().forEach { type ->
            assertEquals(mapOf(), pvDao.fetchDictionary(type))
        }

        fakeVelho.fetchDictionaries(MATERIAL, materialDictionaries)
        fakeVelho.fetchDictionaries(PROJECT, projectDictionaries)
        pvService.updateDictionaries()
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
        fakeVelho.fetchDictionaries(MATERIAL, materialDictionaries2)
        fakeVelho.fetchDictionaries(PROJECT, projectDictionaries2)
        pvService.updateDictionaries()
        PVDictionaryType.values().forEach { type ->
            assertEquals(
                (materialDictionaries2+projectDictionaries2)[type]!!.map { e -> e.code to e.name }.associate { it },
                pvDao.fetchDictionary(type),
            )
        }
    }

    @Test
    fun `Importing through search happy case works`(): Unit = fakeVelho().use { fakeVelho ->
        val searchId = PVId("123")
        val documentOid = Oid<PVDocument>("1.2.3.4.5")
        val assignmentOid = Oid<PVAssignment>("1.2.4.5.6")
        val version = PVId("1")
        val description = "description 1"
        val documentType = materialDictionaries[DOCUMENT_TYPE]!!.first().code
        val materialState = materialDictionaries[MATERIAL_STATE]!!.first().code
        val materialGroup = materialDictionaries[MATERIAL_GROUP]!!.first().code
        val materialCategory = materialDictionaries[MATERIAL_CATEGORY]!!.first().code

        fakeVelho.login()
        fakeVelho.fetchDictionaries(MATERIAL, materialDictionaries)
        fakeVelho.fetchDictionaries(PROJECT, projectDictionaries)
        fakeVelho.searchStatus(searchId)
        fakeVelho.searchResults(searchId, listOf(PVApiMatch(documentOid, assignmentOid)))
        fakeVelho.fileMetadata(
            documentOid,
            version,
            description = description,
            documentType = documentType,
            materialState = materialState,
            materialGroup = materialGroup,
            materialCategory = materialCategory,
        )
        fakeVelho.fileContent(documentOid)

        pvService.updateDictionaries()
        pvDao.insertFetchInfo(searchId, Instant.now().plusSeconds(3600))
        val search = pvDao.fetchLatestSearch()!!
        val status = pvService.getSearchStatusIfReady(search)!!

        pvService.importFilesFromProjektiVelho(search, status)
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

    private fun assertDocumentExists(
        oid: Oid<PVDocument>,
        status: PVDocumentStatus,
        description: String = "test description",
        documentType: PVCode = PVCode("dokumenttityyppi/dt01"),
        materialState: PVCode = PVCode("aineistotila/tila01"),
        materialCategory: PVCode = PVCode("aineistolaji/al00"),
        materialGroup: PVCode = PVCode("aineistoryhma/ar00"),
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

private fun getTestDataDictionaryName(type: PVDictionaryType, code: PVCode) =
    (materialDictionaries+ projectDictionaries)[type]?.find { e -> e.code == code }?.name
