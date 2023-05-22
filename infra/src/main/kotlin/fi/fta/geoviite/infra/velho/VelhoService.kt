package fi.fta.geoviite.infra.velho

import PVAssignment
import PVDocumentStatus
import PVProject
import PVProjectGroup
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.authorization.withUser
import fi.fta.geoviite.infra.common.Oid
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

    private fun <T> runIntegration(op: () -> T): T? = withUser(PROJEKTIVELHO_DB_USERNAME) {
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) { op() }
    }

//    @Scheduled(cron = "38 10 * * * *")
    @Scheduled(initialDelay = 60000, fixedRate = 900000) // First run after 1min, then every 15min
    fun search(): PVApiSearchStatus? = runIntegration {
        logger.serviceCall("search")
        val latest = velhoDao.fetchLatestFile()
        val startTime = latest?.second ?: Instant.now().minusSeconds(SECONDS_IN_A_YEAR)
        velhoClient.postXmlFileSearch(startTime, latest?.first)
            .also { status ->
                velhoDao.insertFetchInfo(status.searchId, status.startTime.plusSeconds(status.validFor))
            }
    }

    @Scheduled(initialDelay = 120000, fixedRate = 300000) // First run after 1min, then every 15min
    fun pollAndFetchIfWaiting() = runIntegration {
        logger.serviceCall("pollAndFetchIfWaiting")
        val latestSearch = velhoDao.fetchLatestSearch()
        // Mark previous search as stalled if previous search is supposedly still running after
        // having outlived its validity period
        if (latestSearch?.state == PVFetchStatus.FETCHING && Instant.now() > latestSearch.validUntil) {
            velhoDao.updateFetchState(latestSearch.id, PVFetchStatus.ERROR)
        } else {
            updateDictionaries()
            latestSearch
                ?.takeIf { search -> search.state == PVFetchStatus.WAITING }
                ?.let { search -> velhoClient.fetchVelhoSearchStatus(search.token) }
                ?.takeIf { status -> status.state == PROJEKTIVELHO_SEARCH_STATE_READY }
                ?.let { status -> importFilesFromProjektiVelho(latestSearch, status) }
        }
    }

    fun updateDictionaries() {
        logger.serviceCall("updateDictionaries")
        val dict = velhoClient.fetchDictionaries()
        dict.forEach { (type, entries) ->
            velhoDao.upsertDictionary(type, entries)
        }
    }

    fun importFilesFromProjektiVelho(latest: PVSearch, searchResults: PVApiSearchStatus) =
        try {
            velhoDao.updateFetchState(latest.id, PVFetchStatus.FETCHING)
            val assignments = mutableMapOf<Oid<PVAssignment>, PVApiAssignment?>()
            val projects = mutableMapOf<Oid<PVProject>, PVApiProject?>()
            val projectGroups = mutableMapOf<Oid<PVProjectGroup>, PVApiProjectGroup?>()
            velhoClient.fetchSearchResults(searchResults.searchId).matches
                .map { match -> fetchFileAndInsertToDb(match, assignments, projects, projectGroups) }
            velhoDao.updateFetchState(latest.id, PVFetchStatus.FINISHED)
        } catch (e: Exception) {
            velhoDao.updateFetchState(latest.id, PVFetchStatus.ERROR)
            throw e
        }

    private fun fetchFileAndInsertToDb(
        match: PVApiMatch,
        assignments: MutableMap<Oid<PVAssignment>, PVApiAssignment?>,
        projects: MutableMap<Oid<PVProject>, PVApiProject?>,
        projectGroups: MutableMap<Oid<PVProjectGroup>, PVApiProjectGroup?>
    ) = fetchFileMetadataAndContent(match, assignments, projects, projectGroups)
        .let(::insertFileToDatabase)

    private fun fetchFileMetadataAndContent(
        match: PVApiMatch,
        assignments: MutableMap<Oid<PVAssignment>, PVApiAssignment?>,
        projects: MutableMap<Oid<PVProject>, PVApiProject?>,
        projectGroups: MutableMap<Oid<PVProjectGroup>, PVApiProjectGroup?>
    ): PVFileHolder {
        val metadataResponse = velhoClient.fetchFileMetadata(match.oid)
        val content =
            if (metadataResponse.metadata.containsPersonalInfo == true) null
            else velhoClient.fetchFileContent(match.oid, metadataResponse.latestVersion.version)

        val assignment = match.assignmentOid.let { oid ->
            if (assignments.containsKey(oid)) assignments[oid]
            else velhoClient.fetchAssignment(oid).also { a -> assignments[oid] = a }
        }
        val project = assignment?.projectOid?.let { oid ->
            if (projects.containsKey(oid)) projects[oid]
            else velhoClient.fetchProject(oid).also { p -> projects[oid] = p }
        }
        val projectGroup = project?.projectGroupOid?.let { oid ->
            if (projectGroups.containsKey(oid)) projectGroups[oid]
            else velhoClient.fetchProjectGroup(oid).also { pg -> projectGroups[oid] = pg }
        }

        return PVFileHolder(
            oid = match.oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion,
            assignment = assignment,
            project = project,
            projectGroup = projectGroup,
        )
    }

    private fun insertFileToDatabase(file: PVFileHolder) {
        val xmlContent = file.content
            ?.takeIf { content -> isRailroadXml(content ,file.latestVersion.name) }
            ?.let { content -> censorAuthorIdentifyingInfo(content) }
        val metadataId = velhoDao.insertFileMetadata(
            file.oid,
            file.metadata,
            file.latestVersion,
            if (xmlContent != null) PVDocumentStatus.IMPORTED else PVDocumentStatus.NOT_IM,
            file.assignment?.also(velhoDao::upsertAssignment)?.oid,
            file.project?.also(velhoDao::upsertProject)?.oid,
            file.projectGroup?.also(velhoDao::upsertProjectGroup)?.oid,
        )
        xmlContent?.let { content -> velhoDao.insertFileContent(content, metadataId) }
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
