package fi.fta.geoviite.infra.velho.projektivelho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.serviceCall
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
    private val databaseLockDuration = Duration.ofMinutes(15)
    private val PROJEKTIVELHO_DB_USERNAME = UserName("PROJEKTIVELHO_IMPORT")
    private val alignmentsTagRegex = Regex("<alignments[^>]*>")

    fun search() {
        logger.serviceCall("search")
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latest = projektiVelhoDao.fetchLatestFile(PROJEKTIVELHO_DB_USERNAME)
            val searchStatus = projektiVelhoClient.postXmlFileSearch(
                latest?.second
                    ?: LocalDate.of(2022, 1, 1).atTime(0, 0).toInstant(ZoneOffset.UTC),
                latest?.first ?: ""
            )
            projektiVelhoDao.insertFetchInfo(PROJEKTIVELHO_DB_USERNAME, searchStatus.searchId, searchStatus.startTime.plusSeconds(searchStatus.validFor))
        }
    }

    @Scheduled(fixedRate = 60000)
    fun pollAndFetchIfWaiting() {
        logger.serviceCall("pollAndFetchIfWaiting")
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val potentiallyWaitingSearch = projektiVelhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)
            if (potentiallyWaitingSearch != null && potentiallyWaitingSearch.state == FetchStatus.WAITING)
                projektiVelhoClient
                    .fetchVelhoSearches()
                    .find { search -> search.searchId == potentiallyWaitingSearch.token && search.state == READY }
                    ?.let { results -> importFilesFromProjektiVelho(potentiallyWaitingSearch, results) }
        }
    }

    private fun importFilesFromProjektiVelho(
        latest: ProjektiVelhoSearch,
        searchResults: SearchStatus
    ) =
        try {
            projektiVelhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FETCHING)
            projektiVelhoClient.fetchSearchResults(searchResults.searchId)
                .matches.map(::fetchFileAndInsertToDb)
            projektiVelhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FINISHED)
        } catch (e: Exception) {
            projektiVelhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.ERROR)
            throw e
        }

    private fun fetchFileAndInsertToDb(match: Match) =
        fetchFileMetadataAndContent(match).let(::insertFileToDatabase)

    private fun fetchFileMetadataAndContent(match: Match): ProjektiVelhoFile {
        val metadataResponse = projektiVelhoClient.fetchFileMetadata(match.oid)
        val content = projektiVelhoClient.fetchFileContent(
            match.oid,
            metadataResponse.latestVersion.version,
        )

        return ProjektiVelhoFile(
            oid = match.oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion
        )
    }

    private fun insertFileToDatabase(file: ProjektiVelhoFile) {
        val shouldBeSavedToDb = isRailroadXml(file.content)
        val metadataId = projektiVelhoDao.insertFileMetadata(
            PROJEKTIVELHO_DB_USERNAME,
            file.oid,
            file.metadata,
            file.latestVersion,
            if (shouldBeSavedToDb) FileStatus.IMPORTED else FileStatus.NOT_IM
        )
        if (shouldBeSavedToDb) projektiVelhoDao.insertFileContent(
            PROJEKTIVELHO_DB_USERNAME,
            file.content,
            metadataId
        )
    }

    fun isRailroadXml(xml: String) =
        xml.lowercase().let {
            alignmentsTagRegex.matches(it)
                && it.indexOf("xmlns=\"http://www.inframodel.fi/inframodel\"") >= 0
                && (it.indexOf("label=\"infraCoding\"")  == -1
                    || it.indexOf("label=\"infraCoding\" value=\"281\"") >= 0)
        }
}
