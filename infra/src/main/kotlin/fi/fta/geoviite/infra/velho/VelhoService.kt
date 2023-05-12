package fi.fta.geoviite.infra.velho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.inframodel.InfraModelService
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.util.FileName
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant

val PROJEKTIVELHO_DB_USERNAME = UserName("PROJEKTIVELHO_IMPORT")
val SECONDS_IN_A_YEAR: Long = 31556926

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
                latest?.second ?: Instant.now().minusSeconds(SECONDS_IN_A_YEAR),
                latest?.first ?: ""
            )
            velhoDao.insertFetchInfo(
                PROJEKTIVELHO_DB_USERNAME,
                searchStatus.searchId,
                searchStatus.startTime.plusSeconds(searchStatus.validFor)
            )
            return@runWithLock searchStatus
        }
    }

    @Scheduled(fixedRate = 60000)
    fun pollAndFetchIfWaiting() {
        logger.serviceCall("pollAndFetchIfWaiting")
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latestSearch = velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)
            // Mark previous search as stalled if previous search is supposedly still running after
            // having outlived its validity period
            if (latestSearch?.state == FetchStatus.FETCHING && Instant.now() > latestSearch.validUntil) {
                velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latestSearch.id, FetchStatus.ERROR)
                return@runWithLock
            }

            val searchResults =
                if (latestSearch?.state == FetchStatus.WAITING)
                    fetchSearchResults(latestSearch.token)
                else null

            if (latestSearch != null && searchResults != null) importFilesFromProjektiVelho(latestSearch, searchResults)
        }
    }

    fun fetchAineisto() {
        val dict = velhoClient.fetchAineistoMetadata()
        dict.forEach { (type, entries) ->
            entries.map { entry ->
                velhoDao.upsertDictionary(PROJEKTIVELHO_DB_USERNAME, type, entry.code, entry.name)
            }
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

        // TODO Add these when fetches actually work
        /*val assignment = velhoClient.fetchAssignment(match.assignmentOid)
        val project = assignment?.projectOid?.let(velhoClient::fetchProject)
        val projectGroup = project?.projectGroupOid?.let(velhoClient::fetchProjectGroup)*/

        return ProjektiVelhoFile(
            oid = match.oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion,
            assignment = null,
            project = null,
            projectGroup = null
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

    fun isRailroadXml(xml: String, filename: FileName) =
        try {
            infraModelService.parseInfraModel(xml.toByteArray(), filename.toString(), null)
            true
        } catch (e: Exception) {
            false
        }
}
