package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublication
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.error.getPSQLExceptionConstraintAndDetailOrRethrow
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.IndirectChanges
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.ratko.model.RatkoOid
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutDesignDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostService
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberService
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.tracklayout.ReferenceLineService
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import java.time.Instant
import org.postgresql.util.PSQLException
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@GeoviiteService
class PublicationService
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val trackNumberService: LayoutTrackNumberService,
    private val switchService: LayoutSwitchService,
    private val kmPostService: LayoutKmPostService,
    private val kmPostDao: LayoutKmPostDao,
    private val locationTrackService: LocationTrackService,
    private val locationTrackDao: LocationTrackDao,
    private val referenceLineService: ReferenceLineService,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchDao: LayoutSwitchDao,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val calculatedChangesService: CalculatedChangesService,
    private val ratkoClient: RatkoClient?,
    private val transactionTemplate: TransactionTemplate,
    private val publicationGeometryChangeRemarksUpdateService: PublicationGeometryChangeRemarksUpdateService,
    private val splitService: SplitService,
    private val publicationValidationService: PublicationValidationService,
    private val layoutDesignDao: LayoutDesignDao,
) {
    @Transactional(readOnly = true)
    fun collectPublicationCandidates(transition: LayoutContextTransition): PublicationCandidates {
        return PublicationCandidates(
            transition = transition,
            trackNumbers = publicationDao.fetchTrackNumberPublicationCandidates(transition),
            locationTracks =
                publicationDao.fetchLocationTrackPublicationCandidates(transition).map { ltc ->
                    ltc.copy(geometryChanges = fetchChangedLocationTrackGeometryRanges(ltc.id, transition))
                },
            referenceLines =
                publicationDao.fetchReferenceLinePublicationCandidates(transition).map { rlc ->
                    rlc.copy(geometryChanges = fetchChangedReferenceLineGeometryRanges(rlc.id, transition))
                },
            switches = publicationDao.fetchSwitchPublicationCandidates(transition),
            kmPosts = publicationDao.fetchKmPostPublicationCandidates(transition),
        )
    }

    fun fetchChangedLocationTrackGeometryRanges(
        id: IntId<LocationTrack>,
        transition: LayoutContextTransition,
    ): GeometryChangeRanges<LocationTrackM> {
        val trackWithGeometry1 = locationTrackService.getWithGeometry(transition.candidateContext, id)
        val trackWithGeometry2 = locationTrackService.getWithGeometry(transition.baseContext, id)
        return getChangedGeometryRanges(
            trackWithGeometry1?.second?.segmentsWithM ?: emptyList(),
            trackWithGeometry2?.second?.segmentsWithM ?: emptyList(),
        )
    }

    fun fetchChangedReferenceLineGeometryRanges(
        id: IntId<ReferenceLine>,
        transition: LayoutContextTransition,
    ): GeometryChangeRanges<ReferenceLineM> {
        val lineWithAlignment1 = referenceLineService.getWithAlignment(transition.candidateContext, id)
        val lineWithAlignment2 = referenceLineService.getWithAlignment(transition.baseContext, id)
        return getChangedGeometryRanges(
            lineWithAlignment1?.second?.segmentsWithM ?: emptyList(),
            lineWithAlignment2?.second?.segmentsWithM ?: emptyList(),
        )
    }

    fun getChangeTime(): Instant {
        return publicationDao.fetchChangeTime()
    }

    @Transactional(readOnly = true)
    fun getRevertRequestDependencies(branch: LayoutBranch, requestIds: PublicationRequestIds): PublicationRequestIds {
        val referenceLineTrackNumberIds =
            referenceLineService.getMany(branch.draft, requestIds.referenceLines).map { rlId -> rlId.trackNumberId }
        val trackNumbers =
            trackNumberService.getMany(branch.draft, referenceLineTrackNumberIds + requestIds.trackNumbers)
        val revertTrackNumberIds = trackNumbers.filter(LayoutTrackNumber::isDraft).map { it.id as IntId }
        // If revert breaks other draft row references, they should be reverted too
        val draftOnlyTrackNumberIds =
            trackNumbers
                .filter { tn ->
                    tn.isDraft && trackNumberDao.fetchVersion(tn.layoutContext.branch.official, tn.id as IntId) == null
                }
                .map { it.id as IntId }

        val revertLocationTrackIds =
            requestIds.locationTracks +
                draftOnlyTrackNumberIds
                    .flatMap { tnId ->
                        locationTrackDao
                            .fetchOnlyDraftVersions(branch, includeDeleted = true, tnId)
                            .map(locationTrackDao::fetch)
                    }
                    .map { track -> track.id as IntId }

        val revertSplits = splitService.findUnpublishedSplits(branch, revertLocationTrackIds, requestIds.switches)
        val revertSplitTracks = revertSplits.flatMap { s -> s.locationTracks }.distinct()
        val revertSplitSwitches = revertSplits.flatMap { s -> s.relinkedSwitches }.distinct()

        val revertKmPostIds =
            requestIds.kmPosts.toSet() +
                draftOnlyTrackNumberIds
                    .flatMap { tnId ->
                        kmPostDao.fetchOnlyDraftVersions(branch, includeDeleted = true, tnId).map(kmPostDao::fetch)
                    }
                    .map { kmPost -> kmPost.id as IntId }

        val referenceLines =
            requestIds.referenceLines.toSet() +
                requestIds.trackNumbers
                    .mapNotNull { tnId -> referenceLineService.getByTrackNumber(branch.draft, tnId) }
                    .filter(ReferenceLine::isDraft)
                    .map { line -> line.id as IntId }

        return PublicationRequestIds(
            trackNumbers = revertTrackNumberIds.toList(),
            referenceLines = referenceLines.toList(),
            locationTracks = (revertLocationTrackIds + revertSplitTracks).distinct(),
            switches = (requestIds.switches + revertSplitSwitches).distinct(),
            kmPosts = revertKmPostIds.toList(),
        )
    }

    @Transactional
    fun revertPublicationCandidates(branch: LayoutBranch, toDelete: PublicationRequestIds): PublicationResultSummary {
        splitService.fetchPublicationVersions(branch, toDelete.locationTracks, toDelete.switches).forEach { split ->
            splitService.deleteSplit(split.id)
        }

        val locationTrackCount = toDelete.locationTracks.map { id -> locationTrackService.deleteDraft(branch, id) }.size
        val referenceLineCount = toDelete.referenceLines.map { id -> referenceLineService.deleteDraft(branch, id) }.size
        alignmentDao.deleteOrphanedAlignments()
        val switchCount = toDelete.switches.map { id -> switchService.deleteDraft(branch, id) }.size
        val kmPostCount = toDelete.kmPosts.map { id -> kmPostService.deleteDraft(branch, id) }.size
        val trackNumberCount = toDelete.trackNumbers.map { id -> trackNumberService.deleteDraft(branch, id) }.size

        return PublicationResultSummary(
            publicationId = null,
            trackNumbers = trackNumberCount,
            locationTracks = locationTrackCount,
            referenceLines = referenceLineCount,
            switches = switchCount,
            kmPosts = kmPostCount,
        )
    }

    /**
     * Note: this is intentionally not transactional: each ID is fetched from ratko and becomes an object there -> we
     * want to store it, even if the rest fail
     */
    fun updateExternalId(branch: LayoutBranch, request: PublicationRequestIds) {
        try {
            request.locationTracks
                .filter { trackId -> locationTrackDao.fetchExternalId(branch, trackId) == null }
                .forEach { trackId -> insertExternalIdForLocationTrack(branch, trackId) }
            request.trackNumbers
                .filter { trackNumberId -> trackNumberDao.fetchExternalId(branch, trackNumberId) == null }
                .forEach { trackNumberId -> insertExternalIdForTrackNumber(branch, trackNumberId) }
            request.switches
                .filter { switchId -> switchDao.fetchExternalId(branch, switchId) == null }
                .forEach { switchId -> insertExternalIdForSwitch(branch, switchId) }
        } catch (e: Exception) {
            throw PublicationFailureException(
                message = "Failed to update external IDs for publication candidates",
                cause = e,
                localizedMessageKey = "external-id-update-failed",
            )
        }
    }

    @Transactional(readOnly = true)
    fun getValidationVersions(branch: LayoutBranch, request: PublicationRequestIds): ValidationVersions {
        val transition = LayoutContextTransition.publicationIn(branch)
        val target = ValidateTransition(transition)
        return ValidationVersions(
            target = target,
            trackNumbers = trackNumberDao.fetchCandidateVersions(transition.candidateContext, request.trackNumbers),
            referenceLines =
                referenceLineDao.fetchCandidateVersions(transition.candidateContext, request.referenceLines),
            kmPosts = kmPostDao.fetchCandidateVersions(transition.candidateContext, request.kmPosts),
            locationTracks =
                locationTrackDao.fetchCandidateVersions(transition.candidateContext, request.locationTracks),
            switches = switchDao.fetchCandidateVersions(transition.candidateContext, request.switches),
            splits =
                splitService.fetchPublicationVersions(
                    transition.candidateBranch,
                    request.locationTracks,
                    request.switches,
                ),
        )
    }

    private fun insertExternalIdForLocationTrack(branch: LayoutBranch, locationTrackId: IntId<LocationTrack>) {
        val locationTrackOid =
            ratkoClient?.let { s -> requireNotNull(s.getNewLocationTrackOid()) { "No OID received from RATKO" } }
        locationTrackOid?.let { oid -> locationTrackService.insertExternalId(branch, locationTrackId, Oid(oid.id)) }
    }

    private fun insertExternalIdForTrackNumber(branch: LayoutBranch, trackNumberId: IntId<LayoutTrackNumber>) {
        val routeNumberOid =
            ratkoClient?.let { s -> requireNotNull(s.getNewRouteNumberOid()) { "No OID received from RATKO" } }
        routeNumberOid?.let { oid -> trackNumberService.insertExternalId(branch, trackNumberId, Oid(oid.id)) }
    }

    private fun insertExternalIdForSwitch(branch: LayoutBranch, switchId: IntId<LayoutSwitch>) {
        val switchOid =
            switchDao.get(branch.draft, switchId)?.draftOid?.also(::ensureDraftIdExists)?.toString()
                ?: ratkoClient?.let { s -> requireNotNull(s.getNewSwitchOid().id) { "No OID received from RATKO" } }
        switchOid?.let { oid -> switchService.insertExternalIdForSwitch(branch, switchId, Oid(switchOid)) }
    }

    private fun ensureDraftIdExists(draftOid: Oid<LayoutSwitch>) {
        requireNotNull(ratkoClient?.getSwitchAsset(RatkoOid(draftOid.toString()))) {
            "OID $draftOid does not exist in Ratko"
        }
    }

    fun getCalculatedChanges(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    fun publishManualPublication(branch: LayoutBranch, request: PublicationRequest): PublicationResult {
        val versions = requireNotNull(transactionTemplate.execute { getValidationVersions(branch, request.content) })
        publicationValidationService.validatePublicationRequest(versions)
        val (inheritedChanges, inheritedChangeIds) =
            if (branch is MainBranch) {
                getInheritedChangesFromMainPublicationToDesigns(versions) to PublicationRequestIds.empty()
            } else {
                mapOf<DesignBranch, IndirectChanges>() to getDesignToSelfInheritedChangeIds(versions)
            }
        updateExternalId(branch, request.content + inheritedChangeIds)
        val calculatedChanges = calculatedChangesService.getCalculatedChanges(versions)
        val mainPublication =
            PreparedPublicationRequest(
                branch,
                versions,
                calculatedChanges,
                request.message,
                PublicationCause.MANUAL,
                parentId = null,
            )
        // publication results already only include direct changes, and all inherited changes are
        // indirect, so all but the first publication result are empty -> can be thrown out
        return publishPublicationRequests(mainPublication, inheritedChanges).first()
    }

    fun publishChanges(
        branch: LayoutBranch,
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
        message: FreeTextWithNewLines,
        cause: PublicationCause,
    ): PublicationResultSummary =
        publishPublicationRequests(
                PreparedPublicationRequest(branch, versions, calculatedChanges, message, cause, parentId = null),
                mapOf(),
            )
            .first()
            .summarize()

    fun publishPublicationRequests(
        mainPublication: PreparedPublicationRequest,
        inheritedChanges: Map<DesignBranch, IndirectChanges>,
    ): List<PublicationResult> {
        val results =
            try {
                requireNotNull(
                    transactionTemplate.execute {
                        val mainResults = publishChangesTransaction(mainPublication)
                        val inheritedResults =
                            calculatedChangesService
                                .combineInheritedChangesAndFinishedMerges(
                                    mainPublication,
                                    inheritedChanges,
                                    mainResults,
                                )
                                .map(::publishChangesTransaction)
                        listOf(mainResults) + inheritedResults
                    }
                )
            } catch (exception: DataIntegrityViolationException) {
                enrichDuplicateNameExceptionOrRethrow(inheritedChanges.keys + mainPublication.branch, exception)
            }
        results.forEach { result ->
            result.publicationId.let { publicationGeometryChangeRemarksUpdateService.processPublication(it) }
        }
        return results
    }

    private fun getInheritedChangesFromMainPublicationToDesigns(
        versions: ValidationVersions
    ): Map<DesignBranch, IndirectChanges> =
        layoutDesignDao
            .list()
            .mapNotNull { design ->
                val inheritorBranch = DesignBranch.of(design.id as IntId)
                val changesInheritedToDesign =
                    calculatedChangesService.getCalculatedChangesForMainToDesignInheritance(
                        inheritorBranch,
                        versions.trackNumbers,
                        versions.referenceLines,
                        versions.locationTracks,
                        versions.switches,
                        versions.kmPosts,
                    )

                if (changesInheritedToDesign.isEmpty()) null else inheritorBranch to changesInheritedToDesign
            }
            .associate { it }

    fun getDesignToSelfInheritedChangeIds(versions: ValidationVersions): PublicationRequestIds {
        val changes = calculatedChangesService.getCalculatedChanges(versions)
        val indirectChanges = changes.indirectChanges
        val switchChangesBySameKmLocationTrackChange =
            (changes.directChanges.locationTrackChanges + changes.indirectChanges.locationTrackChanges)
                .flatMap { locationTrackChange ->
                    calculatedChangesService.getChangedSwitchesFromChangedLocationTrackKms(
                        versions,
                        locationTrackChange,
                    )
                }
                .distinct()
        return PublicationRequestIds(
            trackNumbers = indirectChanges.trackNumberChanges.map { it.trackNumberId },
            referenceLines = listOf(),
            locationTracks = indirectChanges.locationTrackChanges.map { it.locationTrackId },
            switches =
                (indirectChanges.switchChanges.map { it.switchId } + switchChangesBySameKmLocationTrackChange)
                    .distinct(),
            kmPosts = listOf(),
        )
    }

    @Transactional
    fun mergeChangesToMain(fromBranch: DesignBranch, request: PublicationRequestIds): PublicationResultSummary {
        try {
            transactionTemplate.execute {
                request.trackNumbers.forEach { id -> trackNumberService.mergeToMainBranch(fromBranch, id) }
                request.referenceLines.forEach { id -> referenceLineService.mergeToMainBranch(fromBranch, id) }
                request.locationTracks.forEach { id -> locationTrackService.mergeToMainBranch(fromBranch, id) }
                request.switches.forEach { id -> switchService.mergeToMainBranch(fromBranch, id) }
                request.kmPosts.forEach { id -> kmPostService.mergeToMainBranch(fromBranch, id) }
            }
        } catch (exception: DataIntegrityViolationException) {
            enrichDuplicateNameExceptionOrRethrow(listOf(fromBranch), exception)
        }

        return PublicationResultSummary(
            publicationId = null,
            trackNumbers = request.trackNumbers.size,
            referenceLines = request.referenceLines.size,
            locationTracks = request.locationTracks.size,
            switches = request.switches.size,
            kmPosts = request.kmPosts.size,
        )
    }

    private fun publishChangesTransaction(request: PreparedPublicationRequest): PublicationResult {
        val versions = request.versions
        val branch = request.branch
        val message = request.message
        val cause = request.cause
        val calculatedChanges = request.calculatedChanges

        val trackNumbers = versions.trackNumbers.map { v -> trackNumberService.publish(branch, v) }
        val kmPosts = versions.kmPosts.map { v -> kmPostService.publish(branch, v) }
        val switches = versions.switches.map { v -> switchService.publish(branch, v) }
        val referenceLines = versions.referenceLines.map { v -> referenceLineService.publish(branch, v) }
        val locationTracks = versions.locationTracks.map { v -> locationTrackService.publish(branch, v) }
        val publicationId = publicationDao.createPublication(branch, message, cause, request.parentId)
        publicationDao.insertCalculatedChanges(
            publicationId,
            calculatedChanges,
            PublishedVersions(
                trackNumbers.map { it.published },
                referenceLines.map { it.published },
                locationTracks.map { it.published },
                switches.map { it.published },
                kmPosts.map { it.published },
            ),
        )

        splitService.publishSplit(versions.splits, locationTracks.map { it.published }, publicationId)

        return PublicationResult(
            publicationId = publicationId,
            trackNumbers = trackNumbers,
            referenceLines = referenceLines,
            locationTracks = locationTracks,
            switches = switches,
            kmPosts = kmPosts,
        )
    }

    private fun enrichDuplicateNameExceptionOrRethrow(
        possibleBranches: Collection<LayoutBranch>,
        exception: DataIntegrityViolationException,
    ): Nothing {
        val cause = exception.cause
        if (cause !is PSQLException) {
            throw exception
        }

        val (constraint, detail) = getPSQLExceptionConstraintAndDetailOrRethrow(cause)

        when (constraint) {
            "switch_unique_official_name" -> maybeThrowDuplicateSwitchNameException(detail, exception)
            "track_number_number_layout_context_unique" ->
                maybeThrowDuplicateTrackNumberNumberException(detail, exception)
            "location_track_unique_official_name" ->
                maybeThrowDuplicateLocationTrackNameException(possibleBranches, detail, exception)
        }
        throw exception
    }

    private val duplicateLocationTrackErrorRegex =
        Regex(
            """Key \(track_number_id, name, layout_context_id\)=\((\d+), ([^,]+), ([^)]+)\) conflicts with existing key"""
        )
    private val duplicateTrackNumberErrorRegex =
        Regex("""Key \(number, layout_context_id\)=\(([^,]+), [^)]+\) already exists""")
    private val duplicateSwitchErrorRegex =
        Regex("""Key \(name, layout_context_id\)=\(([^,]+), [^)]+\) conflicts with existing key""")

    private fun maybeThrowDuplicateLocationTrackNameException(
        possibleBranches: Collection<LayoutBranch>,
        detail: String,
        exception: DataIntegrityViolationException,
    ) {
        duplicateLocationTrackErrorRegex.matchAt(detail, 0)?.let { match ->
            val trackIdString = match.groups[1]?.value
            val nameString = match.groups[2]?.value
            val layoutContextIdString = match.groups[3]?.value
            val trackId = IntId<LayoutTrackNumber>(Integer.parseInt(trackIdString))
            if (trackIdString != null && nameString != null && layoutContextIdString != null) {
                val branch =
                    requireNotNull(
                        possibleBranches.map { it.official }.find { it.toSqlString() == layoutContextIdString }
                    )
                val trackNumberVersion = trackNumberDao.fetchVersion(branch, trackId)
                if (trackNumberVersion != null) {
                    val trackNumber = trackNumberDao.fetch(trackNumberVersion)
                    throw DuplicateLocationTrackNameInPublicationException(
                        AlignmentName(nameString),
                        trackNumber.number,
                        exception,
                    )
                }
            }
        }
    }

    private fun maybeThrowDuplicateTrackNumberNumberException(
        detail: String,
        exception: DataIntegrityViolationException,
    ) {
        duplicateTrackNumberErrorRegex
            .matchAt(detail, 0)
            ?.let { match -> match.groups[1]?.value }
            ?.let { name ->
                throw DuplicateNameInPublicationException(DuplicateNameInPublication.TRACK_NUMBER, name, exception)
            }
    }

    private fun maybeThrowDuplicateSwitchNameException(detail: String, exception: DataIntegrityViolationException) {
        duplicateSwitchErrorRegex
            .matchAt(detail, 0)
            ?.let { match -> match.groups[1]?.value }
            ?.let { name ->
                throw DuplicateNameInPublicationException(DuplicateNameInPublication.SWITCH, name, exception)
            }
    }
}
