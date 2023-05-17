package fi.fta.geoviite.infra.velho

import PVId
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.inframodel.InfraModelService
import fi.fta.geoviite.infra.inframodel.censorAuthorIdentifyingInfo
import fi.fta.geoviite.infra.inframodel.toInfraModelFile
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.formatForLog
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

    init {
        logger.info("Initializing ${this::class.simpleName}")
    }

    @Scheduled(cron = "0 0 * * * *")
    fun search(): PVApiSearchStatus? {
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

    @Scheduled(initialDelay = 60000, fixedRate = 900000) // First run after 1min, then every 15min
    fun pollAndFetchIfWaiting() {
        logger.serviceCall("pollAndFetchIfWaiting")
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) {
            val latestSearch = velhoDao.fetchLatestSearch(PROJEKTIVELHO_DB_USERNAME)
            // Mark previous search as stalled if previous search is supposedly still running after
            // having outlived its validity period
            if (latestSearch?.state == PVFetchStatus.FETCHING && Instant.now() > latestSearch.validUntil) {
                velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latestSearch.id, PVFetchStatus.ERROR)
                return@runWithLock
            }

            updateDictionaries()
            val searchResults =
                if (latestSearch?.state == PVFetchStatus.WAITING)
                    fetchSearchResults(latestSearch.token)
                else null

            if (latestSearch != null && searchResults != null) importFilesFromProjektiVelho(latestSearch, searchResults)
        }
    }

    fun updateDictionaries() {
        logger.serviceCall("updateDictionaries")
        val dict = velhoClient.fetchDictionaries()
        dict.forEach { (type, entries) ->
            velhoDao.upsertDictionary(PROJEKTIVELHO_DB_USERNAME, type, entries)
        }
    }

    fun fetchSearchResults(searchId: PVId)=
        velhoClient
            .fetchVelhoSearches()
            .find { search -> search.searchId == searchId && search.state == PROJEKTIVELHO_SEARCH_STATE_READY }

    fun importFilesFromProjektiVelho(latest: PVSearch, searchResults: PVApiSearchStatus) =
        try {
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, PVFetchStatus.FETCHING)
            velhoClient.fetchSearchResults(searchResults.searchId)
                .matches.map(::fetchFileAndInsertToDb)
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, PVFetchStatus.FINISHED)
        } catch (e: Exception) {
            velhoDao.updateFetchState(PROJEKTIVELHO_DB_USERNAME, latest.id, PVFetchStatus.ERROR)
            throw e
        }

    private fun fetchFileAndInsertToDb(match: PVApiMatch) =
        fetchFileMetadataAndContent(match).let(::insertFileToDatabase)

    private fun fetchFileMetadataAndContent(match: PVApiMatch): PVFileHolder {
        val metadataResponse = velhoClient.fetchFileMetadata(match.oid)
        val content =
            if (metadataResponse.metadata.containsPersonalInfo == true) null
            else velhoClient.fetchFileContent(match.oid, metadataResponse.latestVersion.version)

        // TODO Add these when fetches actually work
        /*val assignment = velhoClient.fetchAssignment(match.assignmentOid)
        val project = assignment?.projectOid?.let(velhoClient::fetchProject)
        val projectGroup = project?.projectGroupOid?.let(velhoClient::fetchProjectGroup)*/

        return PVFileHolder(
            oid = match.oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion,
            assignment = null,
            project = null,
            projectGroup = null
        )
    }

    private fun insertFileToDatabase(file: PVFileHolder) {
        val xmlContent = file.content
            ?.takeIf { content -> isRailroadXml(content ,file.latestVersion.name) }
            ?.let { content -> censorAuthorIdentifyingInfo(content) }
        val metadataId = velhoDao.insertFileMetadata(
            PROJEKTIVELHO_DB_USERNAME,
            file.oid,
            file.metadata,
            file.latestVersion,
            if (xmlContent != null) PVDocumentStatus.IMPORTED else PVDocumentStatus.NOT_IM
        )
        xmlContent?.let { content ->
            velhoDao.insertFileContent(PROJEKTIVELHO_DB_USERNAME, content, metadataId)
        }
    }

    fun isRailroadXml(xml: String, filename: FileName) =
        try {
            infraModelService.parseInfraModel(toInfraModelFile(xml.toByteArray(), filename, null))
            true
        } catch (e: Exception) {
            logger.info("Rejecting XML as not-IM: file=$filename error=${e.message?.let(::formatForLog)}")
            false
        }
}
