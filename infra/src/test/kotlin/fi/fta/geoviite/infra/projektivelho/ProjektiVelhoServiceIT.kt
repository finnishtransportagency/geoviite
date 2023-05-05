package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.ITTestBase
import fi.fta.geoviite.infra.velho.projektivelho.PROJEKTIVELHO_DB_USERNAME
import fi.fta.geoviite.infra.velho.projektivelho.ProjektiVelhoClient
import fi.fta.geoviite.infra.velho.projektivelho.ProjektiVelhoDao
import fi.fta.geoviite.infra.velho.projektivelho.ProjektiVelhoService
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
@ConditionalOnBean(ProjektiVelhoClient::class)
class ProjektiVelhoServiceIT @Autowired constructor(
    private val fakeProjektiVelhoService: FakeProjektiVelhoService,
    private val projektiVelhoService: ProjektiVelhoService,
    private val projektiVelhoDao: ProjektiVelhoDao,
) : ITTestBase() {
    lateinit var fakeProjektiVelho: FakeProjektiVelho

    @BeforeEach
    fun startServer() {
        fakeProjektiVelho = fakeProjektiVelhoService.start()
    }

    @AfterEach
    fun stopServer() {
        fakeProjektiVelho.stop()
    }

    @Test
    fun `Spinning up search works`() {
        fakeProjektiVelho.login()
        fakeProjektiVelho.search()
        val search = projektiVelhoService.search()
        assertNotNull(search)
        assertEquals(search.searchId, projektiVelhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)?.token)
    }

    @Test
    fun `Importing through search happy case works`() {
        val searchId = "123"
        val oid = "1.2.3.4.5"
        val version = "1"

        fakeProjektiVelho.login()
        fakeProjektiVelho.searchStatus(searchId)
        fakeProjektiVelho.searchResults(searchId, listOf(oid))
        fakeProjektiVelho.fileMetadata(oid, version)
        fakeProjektiVelho.fileContent(oid, version)

        projektiVelhoDao.insertFetchInfo(PROJEKTIVELHO_DB_USERNAME, searchId, Instant.now().plusSeconds(3600))
        val search = projektiVelhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)!!
        val status = projektiVelhoService.fetchSearchResults(searchId)!!

        projektiVelhoService.importFilesFromProjektiVelho(search, status)
    }
}
