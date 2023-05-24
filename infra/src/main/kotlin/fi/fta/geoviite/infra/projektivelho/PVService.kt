package fi.fta.geoviite.infra.projektivelho

import PVAssignment
import PVDocument
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
@ConditionalOnBean(PVClientConfiguration::class)
class PVService @Autowired constructor(
    private val projektiVelhoClient: ProjektiVelhoClient,
    private val pvDao: PVDao,
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

    @Scheduled(
        initialDelayString="\${geoviite.projektivelho.search-poll.initial}",
        fixedRateString="\${geoviite.projektivelho.search-poll.rate}",
    )
    fun search(): PVApiSearchStatus? = runIntegration {
        logger.info("Poll to launch new search")
        val latest = pvDao.fetchLatestFile()
        val startTime = latest?.second ?: Instant.now().minusSeconds(SECONDS_IN_A_YEAR)
        projektiVelhoClient.postXmlFileSearch(startTime, latest?.first).also { status ->
            val validUntil = status.startTime.plusSeconds(status.validFor)
            pvDao.insertFetchInfo(status.searchId, validUntil)
        }
    }

    @Scheduled(
        initialDelayString="\${geoviite.projektivelho.result-poll.initial}",
        fixedRateString="\${geoviite.projektivelho.result-poll.rate}",
    )
    fun pollAndFetchIfWaiting() = runIntegration {
        logger.info("Poll for search results")
        val latestSearch = pvDao.fetchLatestSearch()
        // Mark previous search as stalled if previous search is supposedly still running after
        // having outlived its validity period
        if (latestSearch?.state == PVFetchStatus.FETCHING && Instant.now() > latestSearch.validUntil) {
            pvDao.updateFetchState(latestSearch.id, PVFetchStatus.ERROR)
        } else {
            updateDictionaries()
            latestSearch
                ?.let(::getSearchStatusIfReady)
                ?.let { status -> importFilesFromProjektiVelho(latestSearch, status) }
        }
    }
    fun getSearchStatusIfReady(pvSearch: PVSearch) = pvSearch
        .takeIf { search -> search.state == PVFetchStatus.WAITING }
        ?.let { search -> projektiVelhoClient.fetchVelhoSearchStatus(search.token) }
        ?.takeIf { status -> status.state == PROJEKTIVELHO_SEARCH_STATE_READY }

    fun updateDictionaries() {
        logger.serviceCall("updateDictionaries")
        val dict = projektiVelhoClient.fetchDictionaries()
        dict.forEach { (type, entries) ->
            pvDao.upsertDictionary(type, entries)
        }
    }

    fun importFilesFromProjektiVelho(latest: PVSearch, searchResults: PVApiSearchStatus) =
        try {
            pvDao.updateFetchState(latest.id, PVFetchStatus.FETCHING)
            val assignments = mutableMapOf<Oid<PVAssignment>, PVApiAssignment?>()
            val projects = mutableMapOf<Oid<PVProject>, PVApiProject?>()
            val projectGroups = mutableMapOf<Oid<PVProjectGroup>, PVApiProjectGroup?>()
            projektiVelhoClient.fetchSearchResults(searchResults.searchId).matches
                .map { match -> fetchFileAndInsertToDb(match, assignments, projects, projectGroups) }
            pvDao.updateFetchState(latest.id, PVFetchStatus.FINISHED)
        } catch (e: Exception) {
            pvDao.updateFetchState(latest.id, PVFetchStatus.ERROR)
            throw e
        }

    private fun fetchFileAndInsertToDb(
        match: PVApiMatch,
        assignments: MutableMap<Oid<PVAssignment>, PVApiAssignment?>,
        projects: MutableMap<Oid<PVProject>, PVApiProject?>,
        projectGroups: MutableMap<Oid<PVProjectGroup>, PVApiProjectGroup?>
    ) {
        val assignmentData = fetchAndUpsertAssignmentData(match.assignmentOid, assignments, projects, projectGroups)
        val fileHolder = fetchFileMetadataAndContent(match.oid)
        insertFileToDatabase(fileHolder, assignmentData)
    }

    private fun fetchAndUpsertAssignmentData(
        assignmentOid: Oid<PVAssignment>,
        assignments: MutableMap<Oid<PVAssignment>, PVApiAssignment?>,
        projects: MutableMap<Oid<PVProject>, PVApiProject?>,
        projectGroups: MutableMap<Oid<PVProjectGroup>, PVApiProjectGroup?>,
    ): PVAssignmentHolder {
        val assignment = assignmentOid.let { oid ->
            fetchIfNew(assignments, oid, projektiVelhoClient::fetchAssignment, pvDao::upsertAssignment)
        }
        val project = assignment?.projectOid?.let { oid ->
            fetchIfNew(projects, oid, projektiVelhoClient::fetchProject, pvDao::upsertProject)
        }
        val projectGroup = project?.projectGroupOid?.let { oid ->
            fetchIfNew(projectGroups, oid, projektiVelhoClient::fetchProjectGroup, pvDao::upsertProjectGroup)
        }
        return PVAssignmentHolder(assignment, project, projectGroup)
    }

    private fun <T, S> fetchIfNew(
        known: MutableMap<Oid<S>, T?>,
        oid: Oid<S>,
        fetch: (oid: Oid<S>) -> T?,
        store: (T) -> Unit,
    ): T? =
        // Can't use compute here, as that would try to re-compute nulls. If the thing doesn't exist, don't re-fetch
        if (known.containsKey(oid)) known[oid]
        else fetch(oid)
            .also { value -> known[oid] = value }
            ?.also { value -> store(value) }

    private fun fetchFileMetadataAndContent(oid: Oid<PVDocument>): PVFileHolder {
        val metadataResponse = projektiVelhoClient.fetchFileMetadata(oid)
        val content =
            if (metadataResponse.metadata.containsPersonalInfo == true) null
            else projektiVelhoClient.fetchFileContent(oid, metadataResponse.latestVersion.version)

        return PVFileHolder(
            oid = oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion,
        )
    }

    private fun insertFileToDatabase(file: PVFileHolder, assignment: PVAssignmentHolder) {
        val xmlContent = file.content
            ?.takeIf { content -> isRailroadXml(content ,file.latestVersion.name) }
            ?.let { content -> censorAuthorIdentifyingInfo(content) }
        val metadataId = pvDao.insertFileMetadata(
            file.oid,
            file.metadata,
            file.latestVersion,
            if (xmlContent != null) PVDocumentStatus.SUGGESTED else PVDocumentStatus.NOT_IM,
            assignment.assignment?.oid,
            assignment.project?.oid,
            assignment.projectGroup?.oid,
        )
        xmlContent?.let { content -> pvDao.insertFileContent(content, metadataId) }
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

private data class PVAssignmentHolder(
    val assignment: PVApiAssignment?,
    val project: PVApiProject?,
    val projectGroup: PVApiProjectGroup?,
)

private data class PVFileHolder(
    val oid: Oid<PVDocument>,
    val content: String?,
    val metadata: PVApiFileMetadata,
    val latestVersion: PVApiLatestVersion,
)
