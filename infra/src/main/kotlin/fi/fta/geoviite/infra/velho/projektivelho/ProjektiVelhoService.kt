package fi.fta.geoviite.infra.velho.projektivelho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

@Service
@ConditionalOnBean(ProjektiVelhoClientConfiguration::class)
class ProjektiVelhoService @Autowired constructor(
    private val projektiVelhoClient: ProjektiVelhoClient,
    private val projektiVelhoDao: ProjektiVelhoDao,
    private val lockDao: LockDao,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(120)
    private val PROJEKTIVELHO_DB_USERNAME = UserName("PROJEKTIVELHO_IMPORT")
    private val alignmentsTagRegex = Regex("<alignments[^>]*>")

    fun fetch() {
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latest = projektiVelhoDao.fetchLatestFile(PROJEKTIVELHO_DB_USERNAME)
            val searchStatus = projektiVelhoClient.postSearch(
                latest?.second
                    ?: LocalDate.of(2022, 1, 1).atTime(0, 0).toInstant(ZoneOffset.UTC),
                latest?.first ?: ""
            )
            projektiVelhoDao.insertFetchInfo(PROJEKTIVELHO_DB_USERNAME, searchStatus.searchId, searchStatus.startTime.plusSeconds(searchStatus.validFor))
        }
    }

    @Scheduled(fixedRate = 60000)
    fun fetchVelhoFiles() {
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latest = projektiVelhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)
            if (latest != null && latest.state == FetchStatus.WAITING) {
                projektiVelhoClient.fetchVelhoSearches(latest.token)?.let { searchStatus ->
                    val search = searchStatus.find { search -> search.searchId == latest.token }
                    if (search != null && search.state == "valmis") {
                        projektiVelhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FETCHING)
                        val projektivelhoFiles = projektiVelhoClient.fetchMatchesResponse(search.searchId)?.let { matchesResponse ->
                            matchesResponse.matches.mapNotNull { match ->
                                projektiVelhoClient.fetchMatchMetadata(match.oid)?.let { matchMeta ->
                                    projektiVelhoClient.fetchFileContent(
                                        match.oid,
                                        matchMeta.tuoreinVersio.version,
                                        matchMeta.tuoreinVersio.name,
                                        matchMeta.tuoreinVersio.changeTime
                                    )
                                }
                            }
                        } ?: emptyList()

                        projektivelhoFiles.forEach { file ->
                            val fileId = if (isRailroadXml(file.content)) projektiVelhoDao.insertFileContent(PROJEKTIVELHO_DB_USERNAME, file.content, file.filename) else null
                            projektiVelhoDao.insertFile(PROJEKTIVELHO_DB_USERNAME, file.oid, fileId, file.timestamp)
                        }
                        projektiVelhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FINISHED)
                    }
                }
            }
        }
    }

    fun isRailroadXml(xml: String) =
        xml.lowercase().let {
            alignmentsTagRegex.matches(it)
                && it.indexOf("xmlns=\"http://www.inframodel.fi/inframodel\"") >= 0
                && (it.indexOf("label=\"infraCoding\"")  == -1
                    || it.indexOf("label=\"infraCoding\" value=\"281\"") >= 0)
        }
}
