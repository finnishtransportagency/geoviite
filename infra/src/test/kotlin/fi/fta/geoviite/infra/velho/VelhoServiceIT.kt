package fi.fta.geoviite.infra.velho

import VelhoId
import com.fasterxml.jackson.databind.ObjectMapper
import fi.fta.geoviite.infra.ITTestBase
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
class VelhoServiceIT @Autowired constructor(
    @Value("\${geoviite.projektivelho.test-port:12345}") private val velhoPort: Int,
    private val velhoService: VelhoService,
    private val velhoDao: VelhoDao,
    private val jsonMapper: ObjectMapper,
) : ITTestBase() {

    fun fakeVelho() = FakeVelho(velhoPort, jsonMapper)

    @Test
    fun `Spinning up search works`(): Unit = fakeVelho().use { fakeVelho ->
        fakeVelho.login()
        fakeVelho.search()
        val search = velhoService.search()
        assertNotNull(search)
        assertEquals(search.searchId, velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)?.token)
    }

    @Test
    fun `Importing through search happy case works`(): Unit = fakeVelho().use { fakeVelho ->
        val searchId = VelhoId("123")
        val oid = "1.2.3.4.5"
        val version = "1"

        fakeVelho.login()
        fakeVelho.searchStatus(searchId)
        fakeVelho.searchResults(searchId, listOf(oid))
        fakeVelho.fileMetadata(oid, version)
        fakeVelho.fileContent(oid)

        velhoDao.insertFetchInfo(PROJEKTIVELHO_DB_USERNAME, searchId, Instant.now().plusSeconds(3600))
        val search = velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)!!
        val status = velhoService.fetchSearchResults(searchId)!!

        velhoService.importFilesFromProjektiVelho(search, status)
    }
}
