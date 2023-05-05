package fi.fta.geoviite.infra.velho

import fi.fta.geoviite.infra.ITTestBase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@ActiveProfiles("dev", "test")
@SpringBootTest
@ConditionalOnBean(VelhoClient::class)
class VelhoServiceIT @Autowired constructor(
    private val fakeVelhoService: FakeVelhoService,
    private val velhoService: VelhoService,
    private val velhoDao: VelhoDao,
) : ITTestBase() {
    lateinit var fakeVelho: FakeVelho

    @BeforeEach
    fun startServer() {
        fakeVelho = fakeVelhoService.start()
    }

    @AfterEach
    fun stopServer() {
        fakeVelho.stop()
    }

    @Test
    fun `Spinning up search works`() {
        fakeVelho.login()
        fakeVelho.search()
        val search = velhoService.search()
        assertNotNull(search)
        assertEquals(search.searchId, velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)?.token)
    }

    @Test
    fun `Importing through search happy case works`() {
        val searchId = "123"
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
