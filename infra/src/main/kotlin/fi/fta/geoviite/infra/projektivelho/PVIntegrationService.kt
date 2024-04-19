package fi.fta.geoviite.infra.projektivelho

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.FeatureTypeCode
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.error.InframodelParsingException
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.inframodel.*
import fi.fta.geoviite.infra.integration.DatabaseLock
import fi.fta.geoviite.infra.integration.LockDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.projektivelho.PVDocumentStatus.*
import fi.fta.geoviite.infra.projektivelho.PVFetchStatus.*
import fi.fta.geoviite.infra.util.FileName
import fi.fta.geoviite.infra.util.formatForLog
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import withUser
import java.time.Duration
import java.time.Instant

val PROJEKTIVELHO_INTEGRATION_USERNAME = UserName.of("PROJEKTIVELHO_IMPORT")
val PREFETCH_TIME_RANGE = Duration.ofDays(365)

@Service
@ConditionalOnBean(PVClientConfiguration::class)
class PVIntegrationService @Autowired constructor(
    private val pvClient: PVClient,
    private val pvDao: PVDao,
    private val lockDao: LockDao,
    private val infraModelService: InfraModelService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private val databaseLockDuration = Duration.ofMinutes(15)

    init {
        logger.info("Initializing ${this::class.simpleName}")
    }

    private fun <T> runIntegration(op: () -> T): T? = withUser(PROJEKTIVELHO_INTEGRATION_USERNAME) {
        lockDao.runWithLock(DatabaseLock.PROJEKTIVELHO, databaseLockDuration) { op() }
    }

    @Scheduled(initialDelayString="\${geoviite.projektivelho.search-poll.initial}", fixedRateString="\${geoviite.projektivelho.search-poll.rate}")
    fun search(): PVApiSearchStatus? = runIntegration {
        logger.info("Poll to launch new search")
        val currentSearch = pvDao.fetchLatestActiveSearch()
        if (currentSearch == null) {
            val latest = pvDao.fetchLatestDocument()
            val startTime = latest?.second ?: Instant.now().minus(PREFETCH_TIME_RANGE)
            pvClient.postXmlFileSearch(startTime, latest?.first).also { status ->
                val validUntil = status.startTime.plusSeconds(status.validFor)
                pvDao.insertFetchInfo(status.searchId, validUntil)
            }
        } else {
            logger.info("Not launching a new search as a previous one is still active: " +
                    "search=${currentSearch.token} state=${currentSearch.state} validUntil=${currentSearch.validUntil}")
            null
        }
    }

    @Scheduled(initialDelayString="\${geoviite.projektivelho.result-poll.initial}", fixedRateString="\${geoviite.projektivelho.result-poll.rate}")
    fun pollAndFetchIfWaiting() = runIntegration {
        logger.info("Poll for search results")
        pvDao.fetchLatestActiveSearch()?.let { latestSearch ->
            updateDictionaries()
            getSearchStatusIfReady(latestSearch)?.let { status -> importFilesFromProjektiVelho(latestSearch, status) }
        }
    }

    fun getSearchStatusIfReady(pvSearch: PVSearch): PVApiSearchStatus? = pvSearch
        .takeIf { search -> search.state == WAITING }
        ?.let { search -> pvClient.fetchVelhoSearchStatus(search.token) }
        ?.takeIf { status -> status.state == PVApiSearchState.valmis }

    fun updateDictionaries() {
        logger.serviceCall("updateDictionaries")
        val dict = pvClient.fetchDictionaries()
        dict.forEach { (type, entries) ->
            pvDao.upsertDictionary(type, entries)
        }
    }

    fun importFilesFromProjektiVelho(latest: PVSearch, searchResults: PVApiSearchStatus) =
        try {
            pvDao.updateFetchState(latest.id, FETCHING)
            val assignments = mutableMapOf<Oid<PVAssignment>, PVApiAssignment?>()
            val projects = mutableMapOf<Oid<PVProject>, PVApiProject?>()
            val projectGroups = mutableMapOf<Oid<PVProjectGroup>, PVApiProjectGroup?>()
            pvClient.fetchSearchResults(searchResults.searchId).matches
                .map { match -> fetchFileAndInsertToDb(match, assignments, projects, projectGroups) }
            pvDao.updateFetchState(latest.id, FINISHED)
        } catch (e: Exception) {
            pvDao.updateFetchState(latest.id, ERROR)
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
        val assignment = fetchIfNew(assignments, assignmentOid, pvClient::fetchAssignment, pvDao::upsertAssignment)
        val project = assignment?.projectOid?.let { oid ->
            fetchIfNew(projects, oid, pvClient::fetchProject, pvDao::upsertProject)
        }
        val projectGroup = project?.projectGroupOid?.let { oid ->
            fetchIfNew(projectGroups, oid, pvClient::fetchProjectGroup, pvDao::upsertProjectGroup)
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
        val metadataResponse = pvClient.fetchFileMetadata(oid)
        val content =
            if (metadataResponse.metadata.containsPersonalInfo == true) null
            else pvClient.fetchFileContent(oid, metadataResponse.latestVersion.version)

        return PVFileHolder(
            oid = oid,
            content = content,
            metadata = metadataResponse.metadata,
            latestVersion = metadataResponse.latestVersion,
        )
    }

    private fun insertFileToDatabase(file: PVFileHolder, assignment: PVAssignmentHolder) {
        val result = file.content?.let { content -> checkInfraModel(content, file.latestVersion.name) }
            ?: InfraModelCheckResult(NOT_IM, "error.infra-model.missing-file")
        val xmlContent = if (result.state == NOT_IM) null else file.content?.let(::censorAuthorIdentifyingInfo)
        val pvDocumentRowVersion = pvDao.insertDocumentMetadata(
            file.oid,
            file.metadata,
            file.latestVersion,
            result.state,
            assignment.assignment?.oid,
            assignment.project?.oid,
            assignment.projectGroup?.oid,
        )
        if (xmlContent != null) {
            pvDao.insertDocumentContent(xmlContent, pvDocumentRowVersion.id)
        }
        if (result.rejectionReason != null) {
            pvDao.insertRejection(pvDocumentRowVersion, result.rejectionReason)
        }
    }

    data class InfraModelCheckResult(
        val state: PVDocumentStatus,
        val rejectionReason: String? = null,
    )
    fun checkInfraModel(xml: String, filename: FileName): InfraModelCheckResult =
        try {
            val parsed = infraModelService.parseInfraModel(toInfraModelFile(filename, xml))
            if (parsed.alignments.isEmpty()) InfraModelCheckResult(REJECTED, INFRAMODEL_PARSING_KEY_GENERIC)
            else if (parsed.alignments.any { !isRailroadAlignment(it) }) InfraModelCheckResult(
                REJECTED,
                "$INFRAMODEL_PARSING_KEY_PARENT.alignments.non-railroad-alignments",
            )
            else InfraModelCheckResult(SUGGESTED)
        } catch (e: InframodelParsingException) {
            logger.info("Rejecting XML as not-IM: file=$filename error=${e.message?.let(::formatForLog)}")
            InfraModelCheckResult(NOT_IM, e.localizationKey.toString())
        } catch (e: Exception) {
            logger.warn("Rejecting XML as not-IM: file=$filename error=${e.message?.let(::formatForLog)}")
            InfraModelCheckResult(NOT_IM, INFRAMODEL_PARSING_KEY_GENERIC)
        }

    val railroadAlignmentFeatureTypes = listOf(
        FeatureTypeCode("111"),
        FeatureTypeCode("121"),
        FeatureTypeCode("281"),
    )
    fun isRailroadAlignment(alignment: GeometryAlignment) =
        alignment.featureTypeCode?.let { code -> railroadAlignmentFeatureTypes.contains(code) } ?: true
}

private data class PVAssignmentHolder(
    val assignment: PVApiAssignment?,
    val project: PVApiProject?,
    val projectGroup: PVApiProjectGroup?,
)

private data class PVFileHolder(
    val oid: Oid<PVDocument>,
    val content: String?,
    val metadata: PVApiDocumentMetadata,
    val latestVersion: PVApiLatestVersion,
)
