package fi.fta.geoviite.infra.velho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.inframodel.InfraModelService
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

val PROJEKTIVELHO_DB_USERNAME = UserName("PROJEKTIVELHO_IMPORT")

@Service
@ConditionalOnBean(VelhoClientConfiguration::class)
class VelhoService @Autowired constructor(
    private val velhoClient: VelhoClient,
    private val velhoDao: VelhoDao,
    private val lockDao: LockDao,
    private val infraModelService: InfraModelService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(15)

    @Scheduled(cron = "0 0 * * * *")
    fun search(): SearchStatus? {
        logger.serviceCall("search")
        return lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latest = velhoDao.fetchLatestFile(PROJEKTIVELHO_DB_USERNAME)
            val searchStatus = velhoClient.postXmlFileSearch(
                latest?.second
                    ?: LocalDate.of(2022, 1, 1).atTime(0, 0).toInstant(ZoneOffset.UTC),
                latest?.first ?: ""
            )
            velhoDao.insertFetchInfo(PROJEKTIVELHO_DB_USERNAME, searchStatus.searchId, searchStatus.startTime.plusSeconds(searchStatus.validFor))
            return@runWithLock searchStatus
        }
    }

    @Scheduled(fixedRate = 60000)
    fun pollAndFetchIfWaiting() {
        logger.serviceCall("pollAndFetchIfWaiting")
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latestSearch = velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)
            val searchResults =
                if (latestSearch != null && latestSearch.state == FetchStatus.WAITING)
                    fetchSearchResults(latestSearch.token)
                else null

            if (latestSearch != null && searchResults != null) importFilesFromProjektiVelho(latestSearch, searchResults)
        }
    }

    fun fetchSearchResults(searchId: String) =
        velhoClient
            .fetchVelhoSearches()
            .find { search -> search.searchId == searchId && search.state == PROJEKTIVELHO_SEARCH_STATE_READY }

    fun importFilesFromProjektiVelho(
        latest: ProjektiVelhoSearch,
        searchResults: SearchStatus
    ) =
        try {
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FETCHING)
            velhoClient.fetchSearchResults(searchResults.searchId)
                .matches.map(::fetchFileAndInsertToDb)
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.FINISHED)
        } catch (e: Exception) {
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, FetchStatus.ERROR)
            throw e
        }

    private fun fetchFileAndInsertToDb(match: Match) =
        fetchFileMetadataAndContent(match).let(::insertFileToDatabase)

    private fun fetchFileMetadataAndContent(match: Match): ProjektiVelhoFile {
        val metadataResponse = velhoClient.fetchFileMetadata(match.oid)
        val content = velhoClient.fetchFileContent(
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
        val shouldBeSavedToDb = isRailroadXml(file.content, file.latestVersion.name)
        val metadataId = velhoDao.insertFileMetadata(
            PROJEKTIVELHO_DB_USERNAME,
            file.oid,
            file.metadata,
            file.latestVersion,
            if (shouldBeSavedToDb) FileStatus.IMPORTED else FileStatus.NOT_IM
        )
        if (shouldBeSavedToDb) velhoDao.insertFileContent(
            PROJEKTIVELHO_DB_USERNAME,
            file.content,
            metadataId
        )
    }

    fun isRailroadXml(xml: String, filename: String) =
        try {
            infraModelService.parseInfraModel(xml.toByteArray(), filename, null)
            true
        } catch (e: Exception) {
            false
        }
}
