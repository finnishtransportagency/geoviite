package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublication
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.error.getPSQLExceptionConstraintAndDetailOrRethrow
import fi.fta.geoviite.infra.geocoding.*
import fi.fta.geoviite.infra.geography.GeographyService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.localization.LocalizationLanguage
import fi.fta.geoviite.infra.localization.LocalizationService
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitHeader
import fi.fta.geoviite.infra.split.SplitLayoutValidationIssues
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsFirstComparator
import fi.fta.geoviite.infra.util.printCsv
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import org.postgresql.util.PSQLException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

@GeoviiteService
class PublicationService
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
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
    private val switchLibraryService: SwitchLibraryService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val calculatedChangesService: CalculatedChangesService,
    private val ratkoClient: RatkoClient?,
    private val ratkoPushDao: RatkoPushDao,
    private val geocodingCacheService: GeocodingCacheService,
    private val transactionTemplate: TransactionTemplate,
    private val publicationGeometryChangeRemarksUpdateService: PublicationGeometryChangeRemarksUpdateService,
    private val splitService: SplitService,
    private val localizationService: LocalizationService,
    private val geographyService: GeographyService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    private fun splitCsvColumns(
        translation: Translation
    ): List<CsvEntry<Pair<LocationTrack, SplitTargetInPublication>>> =
        mapOf<String, (item: Pair<LocationTrack, SplitTargetInPublication>) -> Any?>(
                "split-details-csv.source-name" to { (lt, _) -> lt.name },
                "split-details-csv.source-oid" to { (lt, _) -> lt.externalId },
                "split-details-csv.target-name" to { (_, split) -> split.name },
                "split-details-csv.target-oid" to { (_, split) -> split.oid },
                "split-details-csv.operation" to
                    { (_, split) ->
                        when (split.operation) {
                            SplitTargetOperation.CREATE -> translation.t("split-details-csv.newly-created")
                            SplitTargetOperation.OVERWRITE -> translation.t("split-details-csv.replaces-duplicate")
                            SplitTargetOperation.TRANSFER -> translation.t("split-details-csv.transfers-assets")
                        }
                    },
                "split-details-csv.start-address" to { (_, split) -> split.startAddress },
                "split-details-csv.end-address" to { (_, split) -> split.endAddress },
            )
            .map { (key, fn) -> CsvEntry(translation.t(key), fn) }

    @Transactional(readOnly = true)
    fun collectPublicationCandidates(transition: LayoutContextTransition): PublicationCandidates {
        return PublicationCandidates(
            branch = transition.baseBranch,
            trackNumbers = publicationDao.fetchTrackNumberPublicationCandidates(transition),
            locationTracks = publicationDao.fetchLocationTrackPublicationCandidates(transition),
            referenceLines = publicationDao.fetchReferenceLinePublicationCandidates(transition),
            switches = publicationDao.fetchSwitchPublicationCandidates(transition),
            kmPosts = publicationDao.fetchKmPostPublicationCandidates(transition),
        )
    }

    @Transactional(readOnly = true)
    fun validatePublicationCandidates(
        candidates: PublicationCandidates,
        request: PublicationRequestIds,
    ): ValidatedPublicationCandidates {
        return ValidatedPublicationCandidates(
            validatedAsPublicationUnit =
                validateAsPublicationUnit(candidates = candidates.filter(request), allowMultipleSplits = false),
            allChangesValidated = validateAsPublicationUnit(candidates = candidates, allowMultipleSplits = true),
        )
    }

    @Transactional(readOnly = true)
    fun validateTrackNumbersAndReferenceLines(
        layoutContext: LayoutContext,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): List<ValidatedAsset<TrackLayoutTrackNumber>> {
        if (trackNumberIds.isEmpty()) return emptyList()

        // Switches don't affect tracknumber validity, so they are ignored
        val validationContext =
            when (layoutContext.state) {
                DRAFT ->
                    createValidationContext(
                        branch = layoutContext.branch,
                        locationTracks = locationTrackDao.fetchPublicationVersions(layoutContext.branch),
                        kmPosts = kmPostDao.fetchPublicationVersions(layoutContext.branch),
                        trackNumbers = trackNumberDao.fetchPublicationVersions(layoutContext.branch),
                        referenceLines = referenceLineDao.fetchPublicationVersions(layoutContext.branch),
                    )

                OFFICIAL -> createValidationContext(layoutContext.branch)
            }
        validationContext.preloadTrackNumberAndReferenceLineVersions(trackNumberIds)
        validationContext.preloadTrackNumbersByNumber(trackNumberIds)
        validationContext.preloadKmPostsByTrackNumbers(trackNumberIds)
        validationContext.preloadLocationTracksByTrackNumbers(trackNumberIds)

        return trackNumberIds.mapNotNull { id ->
            val trackNumberIssues = validateTrackNumber(id, validationContext)
            val referenceLineId = validationContext.getReferenceLineIdByTrackNumber(id)
            val referenceLineIssues = referenceLineId?.let { rlId -> validateReferenceLine(rlId, validationContext) }
            if (trackNumberIssues != null || referenceLineIssues != null) {
                val allIssues = ((trackNumberIssues ?: emptyList()) + (referenceLineIssues ?: emptyList())).distinct()
                ValidatedAsset(id, allIssues)
            } else null
        }
    }

    @Transactional(readOnly = true)
    fun validateLocationTracks(
        layoutContext: LayoutContext,
        trackIds: List<IntId<LocationTrack>>,
    ): List<ValidatedAsset<LocationTrack>> {
        if (trackIds.isEmpty()) return emptyList()

        val validationContext =
            when (layoutContext.state) {
                DRAFT ->
                    createValidationContext(
                        branch = layoutContext.branch,
                        switches = switchDao.fetchPublicationVersions(layoutContext.branch),
                        locationTracks = locationTrackDao.fetchPublicationVersions(layoutContext.branch),
                        kmPosts = kmPostDao.fetchPublicationVersions(layoutContext.branch),
                        trackNumbers = trackNumberDao.fetchPublicationVersions(layoutContext.branch),
                        referenceLines = referenceLineDao.fetchPublicationVersions(layoutContext.branch),
                    )

                OFFICIAL -> createValidationContext(layoutContext.branch)
            }
        validationContext.preloadLocationTrackVersions(trackIds)
        validationContext.preloadLocationTracksByName(trackIds)
        validationContext.preloadTrackDuplicates(trackIds)
        validationContext.preloadAssociatedTrackNumberAndReferenceLineVersions(trackIds = trackIds)
        val linkedSwitchIds = trackIds.flatMap(validationContext::getPotentiallyAffectedSwitchIds).distinct()
        validationContext.preloadSwitchVersions(linkedSwitchIds)
        validationContext.preloadSwitchTrackLinks(linkedSwitchIds)

        return trackIds.mapNotNull { id ->
            validateLocationTrack(id, validationContext)?.let { issues -> ValidatedAsset(id, issues) }
        }
    }

    @Transactional(readOnly = true)
    fun validateSwitches(
        layoutContext: LayoutContext,
        switchIds: List<IntId<TrackLayoutSwitch>>,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        if (switchIds.isEmpty()) return emptyList()

        // Only tracks and switches affect switch validation, so we can ignore the other types in
        // the publication unit
        val validationContext =
            when (layoutContext.state) {
                DRAFT ->
                    createValidationContext(
                        branch = layoutContext.branch,
                        switches = switchDao.fetchPublicationVersions(layoutContext.branch),
                        locationTracks = locationTrackDao.fetchPublicationVersions(layoutContext.branch),
                    )

                OFFICIAL -> createValidationContext(layoutContext.branch)
            }
        validationContext.preloadSwitchVersions(switchIds)
        validationContext.preloadSwitchTrackLinks(switchIds)
        validationContext.preloadSwitchesByName(switchIds)

        return switchIds.mapNotNull { id ->
            validateSwitch(id, validationContext)?.let { issues -> ValidatedAsset(id, issues) }
        }
    }

    @Transactional(readOnly = true)
    fun validateKmPosts(
        layoutContext: LayoutContext,
        kmPostIds: List<IntId<TrackLayoutKmPost>>,
    ): List<ValidatedAsset<TrackLayoutKmPost>> {
        if (kmPostIds.isEmpty()) return emptyList()

        // We can ignore switches and locationtracks, as they don't affect km-post validity
        val validationContext =
            when (layoutContext.state) {
                DRAFT ->
                    createValidationContext(
                        branch = layoutContext.branch,
                        kmPosts = kmPostDao.fetchPublicationVersions(layoutContext.branch),
                        trackNumbers = trackNumberDao.fetchPublicationVersions(layoutContext.branch),
                        referenceLines = referenceLineDao.fetchPublicationVersions(layoutContext.branch),
                    )

                OFFICIAL -> createValidationContext(layoutContext.branch)
            }

        validationContext.preloadKmPostVersions(kmPostIds)
        validationContext.preloadTrackNumberAndReferenceLineVersions(
            validationContext.collectAssociatedTrackNumberIds(kmPostIds = kmPostIds)
        )

        return kmPostIds.mapNotNull { id ->
            validateKmPost(id, validationContext)?.let { issues -> ValidatedAsset(id, issues) }
        }
    }

    private fun createValidationContext(
        branch: LayoutBranch,
        trackNumbers: List<ValidationVersion<TrackLayoutTrackNumber>> = emptyList(),
        locationTracks: List<ValidationVersion<LocationTrack>> = emptyList(),
        referenceLines: List<ValidationVersion<ReferenceLine>> = emptyList(),
        switches: List<ValidationVersion<TrackLayoutSwitch>> = emptyList(),
        kmPosts: List<ValidationVersion<TrackLayoutKmPost>> = emptyList(),
    ): ValidationContext =
        createValidationContext(
            ValidationVersions(branch, trackNumbers, locationTracks, referenceLines, switches, kmPosts, emptyList())
        )

    private fun createValidationContext(publicationSet: ValidationVersions): ValidationContext =
        ValidationContext(
            trackNumberDao = trackNumberDao,
            referenceLineDao = referenceLineDao,
            kmPostDao = kmPostDao,
            locationTrackDao = locationTrackDao,
            switchDao = switchDao,
            geocodingService = geocodingService,
            alignmentDao = alignmentDao,
            publicationDao = publicationDao,
            switchLibraryService = switchLibraryService,
            splitService = splitService,
            publicationSet = publicationSet,
        )

    fun getChangeTime(): Instant {
        return publicationDao.fetchChangeTime()
    }

    @Transactional(readOnly = true)
    fun validateAsPublicationUnit(
        candidates: PublicationCandidates,
        allowMultipleSplits: Boolean,
    ): PublicationCandidates {
        val splitVersions =
            splitService.fetchPublicationVersions(
                branch = candidates.branch,
                locationTracks = candidates.locationTracks.map { it.id },
                switches = candidates.switches.map { it.id },
            )
        val versions = candidates.getValidationVersions(candidates.branch, splitVersions)

        val validationContext = createValidationContext(versions).also { ctx -> ctx.preloadByPublicationSet() }
        val splitIssues = splitService.validateSplit(versions, validationContext, allowMultipleSplits)

        return PublicationCandidates(
            branch = candidates.branch,
            trackNumbers =
                candidates.trackNumbers.map { candidate ->
                    val trackNumberSplitIssues = splitIssues.trackNumbers[candidate.id] ?: emptyList()
                    val validationIssues = validateTrackNumber(candidate.id, validationContext) ?: emptyList()
                    candidate.copy(issues = trackNumberSplitIssues + validationIssues)
                },
            referenceLines =
                candidates.referenceLines.map { candidate ->
                    val referenceLineSplitIssues = splitIssues.referenceLines[candidate.id] ?: emptyList()
                    val validationIssues = validateReferenceLine(candidate.id, validationContext) ?: emptyList()
                    candidate.copy(issues = referenceLineSplitIssues + validationIssues)
                },
            locationTracks =
                candidates.locationTracks.map { candidate ->
                    val locationTrackSplitIssues = splitIssues.locationTracks[candidate.id] ?: emptyList()
                    val validationIssues = validateLocationTrack(candidate.id, validationContext) ?: emptyList()
                    candidate.copy(issues = validationIssues + locationTrackSplitIssues)
                },
            switches =
                candidates.switches.map { candidate ->
                    val switchSplitIssues = splitIssues.switches[candidate.id] ?: emptyList()
                    val validationIssues = validateSwitch(candidate.id, validationContext) ?: emptyList()
                    candidate.copy(issues = validationIssues + switchSplitIssues)
                },
            kmPosts =
                candidates.kmPosts.map { candidate ->
                    val kmPostSplitIssues = splitIssues.kmPosts[candidate.id] ?: emptyList()
                    val validationIssues = validateKmPost(candidate.id, validationContext) ?: emptyList()
                    candidate.copy(issues = validationIssues + kmPostSplitIssues)
                },
        )
    }

    @Transactional(readOnly = true)
    fun validatePublicationRequest(versions: ValidationVersions) {
        val validationContext = createValidationContext(versions).also { ctx -> ctx.preloadByPublicationSet() }
        splitService.validateSplit(versions, validationContext, allowMultipleSplits = false).also(::assertNoSplitErrors)

        versions.trackNumbers.forEach { version ->
            assertNoErrors(version, requireNotNull(validateTrackNumber(version.officialId, validationContext)))
        }
        versions.kmPosts.forEach { version ->
            assertNoErrors(version, requireNotNull(validateKmPost(version.officialId, validationContext)))
        }
        versions.referenceLines.forEach { version ->
            assertNoErrors(version, requireNotNull(validateReferenceLine(version.officialId, validationContext)))
        }
        versions.locationTracks.forEach { version ->
            assertNoErrors(version, requireNotNull(validateLocationTrack(version.officialId, validationContext)))
        }
        versions.switches.forEach { version ->
            assertNoErrors(version, requireNotNull(validateSwitch(version.officialId, validationContext)))
        }
    }

    @Transactional(readOnly = true)
    fun getRevertRequestDependencies(branch: LayoutBranch, requestIds: PublicationRequestIds): PublicationRequestIds {
        val referenceLineTrackNumberIds =
            referenceLineService.getMany(branch.draft, requestIds.referenceLines).map { rlId -> rlId.trackNumberId }
        val trackNumbers =
            trackNumberService.getMany(branch.draft, referenceLineTrackNumberIds + requestIds.trackNumbers)
        val revertTrackNumberIds = trackNumbers.filter(TrackLayoutTrackNumber::isDraft).map { it.id as IntId }
        // If revert breaks other draft row references, they should be reverted too
        val draftOnlyTrackNumberIds =
            trackNumbers.filter { tn -> tn.isDraft && tn.contextData.officialRowId == null }.map { it.id as IntId }

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
    fun revertPublicationCandidates(branch: LayoutBranch, toDelete: PublicationRequestIds): PublicationResult {
        splitService.fetchPublicationVersions(branch, toDelete.locationTracks, toDelete.switches).forEach { split ->
            splitService.deleteSplit(split.id)
        }

        val locationTrackCount = toDelete.locationTracks.map { id -> locationTrackService.deleteDraft(branch, id) }.size
        val referenceLineCount = toDelete.referenceLines.map { id -> referenceLineService.deleteDraft(branch, id) }.size
        alignmentDao.deleteOrphanedAlignments()
        val switchCount = toDelete.switches.map { id -> switchService.deleteDraft(branch, id) }.size
        val kmPostCount = toDelete.kmPosts.map { id -> kmPostService.deleteDraft(branch, id) }.size
        val trackNumberCount = toDelete.trackNumbers.map { id -> trackNumberService.deleteDraft(branch, id) }.size

        return PublicationResult(
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
        val draftContext = branch.draft
        try {
            request.locationTracks
                .filter { trackId -> locationTrackService.getOrThrow(draftContext, trackId).externalId == null }
                .forEach { trackId -> updateExternalIdForLocationTrack(branch, trackId) }
            request.trackNumbers
                .filter { trackNumberId ->
                    trackNumberService.getOrThrow(draftContext, trackNumberId).externalId == null
                }
                .forEach { trackNumberId -> updateExternalIdForTrackNumber(branch, trackNumberId) }
            request.switches
                .filter { switchId -> switchService.getOrThrow(draftContext, switchId).externalId == null }
                .forEach { switchId -> updateExternalIdForSwitch(branch, switchId) }
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
        return ValidationVersions(
            branch = branch,
            trackNumbers = trackNumberDao.fetchPublicationVersions(branch, request.trackNumbers),
            referenceLines = referenceLineDao.fetchPublicationVersions(branch, request.referenceLines),
            kmPosts = kmPostDao.fetchPublicationVersions(branch, request.kmPosts),
            locationTracks = locationTrackDao.fetchPublicationVersions(branch, request.locationTracks),
            switches = switchDao.fetchPublicationVersions(branch, request.switches),
            splits = splitService.fetchPublicationVersions(branch, request.locationTracks, request.switches),
        )
    }

    private fun updateExternalIdForLocationTrack(branch: LayoutBranch, locationTrackId: IntId<LocationTrack>) {
        val locationTrackOid =
            ratkoClient?.let { s -> requireNotNull(s.getNewLocationTrackOid()) { "No OID received from RATKO" } }
        locationTrackOid?.let { oid -> locationTrackService.updateExternalId(branch, locationTrackId, Oid(oid.id)) }
    }

    private fun updateExternalIdForTrackNumber(branch: LayoutBranch, trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val routeNumberOid =
            ratkoClient?.let { s -> requireNotNull(s.getNewRouteNumberOid()) { "No OID received from RATKO" } }
        routeNumberOid?.let { oid -> trackNumberService.updateExternalId(branch, trackNumberId, Oid(oid.id)) }
    }

    private fun updateExternalIdForSwitch(branch: LayoutBranch, switchId: IntId<TrackLayoutSwitch>) {
        val switchOid = ratkoClient?.let { s -> requireNotNull(s.getNewSwitchOid()) { "No OID received from RATKO" } }
        switchOid?.let { oid -> switchService.updateExternalIdForSwitch(branch, switchId, Oid(oid.id)) }
    }

    private inline fun <reified T> assertNoErrors(version: ValidationVersion<T>, issues: List<LayoutValidationIssue>) {
        val errors = issues.filter { issue -> issue.type == ERROR }
        if (errors.isNotEmpty()) {
            logger.warn("Validation errors in published ${T::class.simpleName}: item=$version errors=$errors")
            throw PublicationFailureException(
                message = "Cannot publish ${T::class.simpleName} due to validation errors: $version",
                localizedMessageKey = "validation-failed",
            )
        }
    }

    private fun assertNoSplitErrors(issues: SplitLayoutValidationIssues) {
        val splitErrors = issues.allIssues().filter { error -> error.type == ERROR }

        if (splitErrors.isNotEmpty()) {
            logger.warn("Validation errors in split: errors=$splitErrors")
            throw PublicationFailureException(
                message = "Cannot publish split due to split validation errors: $splitErrors",
                localizedMessageKey = "validation-failed",
            )
        }
    }

    fun getCalculatedChanges(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    fun publishChanges(
        branch: LayoutBranch,
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
        message: FreeTextWithNewLines,
    ): PublicationResult {
        try {
            return requireNotNull(
                transactionTemplate.execute { publishChangesTransaction(branch, versions, calculatedChanges, message) }
            )
        } catch (exception: DataIntegrityViolationException) {
            enrichDuplicateNameExceptionOrRethrow(branch, exception)
        }
    }

    @Transactional
    fun mergeChangesToMain(fromBranch: DesignBranch, request: PublicationRequestIds): PublicationResult {
        request.trackNumbers.forEach { id -> trackNumberService.mergeToMainBranch(fromBranch, id) }
        request.referenceLines.forEach { id -> referenceLineService.mergeToMainBranch(fromBranch, id) }
        request.locationTracks.forEach { id -> locationTrackService.mergeToMainBranch(fromBranch, id) }
        request.switches.forEach { id -> switchService.mergeToMainBranch(fromBranch, id) }
        request.kmPosts.forEach { id -> kmPostService.mergeToMainBranch(fromBranch, id) }

        return PublicationResult(
            publicationId = null,
            trackNumbers = request.trackNumbers.size,
            referenceLines = request.referenceLines.size,
            locationTracks = request.locationTracks.size,
            switches = request.switches.size,
            kmPosts = request.kmPosts.size,
        )
    }

    private fun publishChangesTransaction(
        branch: LayoutBranch,
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
        message: FreeTextWithNewLines,
    ): PublicationResult {
        val trackNumbers = versions.trackNumbers.map { v -> trackNumberService.publish(branch, v) }
        val kmPosts = versions.kmPosts.map { v -> kmPostService.publish(branch, v) }
        val switches = versions.switches.map { v -> switchService.publish(branch, v) }
        val referenceLines = versions.referenceLines.map { v -> referenceLineService.publish(branch, v) }
        val locationTracks = versions.locationTracks.map { v -> locationTrackService.publish(branch, v) }
        val publicationId = publicationDao.createPublication(branch, message)
        publicationDao.insertCalculatedChanges(publicationId, calculatedChanges)
        publicationGeometryChangeRemarksUpdateService.processPublication(publicationId)

        splitService.publishSplit(versions.splits, locationTracks, publicationId)

        return PublicationResult(
            publicationId = publicationId,
            trackNumbers = trackNumbers.size,
            referenceLines = referenceLines.size,
            locationTracks = locationTracks.size,
            switches = switches.size,
            kmPosts = kmPosts.size,
        )
    }

    private fun validateTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? =
        validationContext.getTrackNumber(id)?.let { trackNumber ->
            val kmPosts = validationContext.getKmPostsByTrackNumber(id)
            val referenceLine = validationContext.getReferenceLineByTrackNumber(id)
            val locationTracks = validationContext.getLocationTracksByTrackNumber(id)
            val referenceIssues = validateTrackNumberReferences(trackNumber, referenceLine, kmPosts, locationTracks)
            val geocodingIssues =
                if (trackNumber.exists && referenceLine != null) {
                    val geocodingContextCacheKey = validationContext.getGeocodingContextCacheKey(id)
                    validateGeocodingContext(geocodingContextCacheKey, VALIDATION_TRACK_NUMBER, trackNumber.number)
                } else {
                    listOf()
                }
            val duplicateNameIssues =
                validateTrackNumberNumberDuplication(
                    trackNumber = trackNumber,
                    duplicates = validationContext.getTrackNumbersByNumber(trackNumber.number),
                )
            return referenceIssues + geocodingIssues + duplicateNameIssues
        }

    private fun validateKmPost(id: IntId<TrackLayoutKmPost>, context: ValidationContext): List<LayoutValidationIssue>? =
        context.getKmPost(id)?.let { kmPost ->
            val trackNumber = kmPost.trackNumberId?.let(context::getTrackNumber)
            val trackNumberNumber = (trackNumber ?: kmPost.trackNumberId?.let(context::getDraftTrackNumber))?.number
            val referenceLine = trackNumber?.referenceLineId?.let(context::getReferenceLine)

            val referenceIssues = validateKmPostReferences(kmPost, trackNumber, referenceLine, trackNumberNumber)

            val geocodingIssues =
                if (kmPost.exists && trackNumber?.exists == true && referenceLine != null) {
                    validateGeocodingContext(
                        context.getGeocodingContextCacheKey(kmPost.trackNumberId),
                        VALIDATION_KM_POST,
                        trackNumber.number,
                    )
                } else {
                    listOf()
                }
            referenceIssues + geocodingIssues
        }

    private fun validateSwitch(
        id: IntId<TrackLayoutSwitch>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? =
        validationContext.getSwitch(id)?.let { switch ->
            val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
            val linkedTracksAndAlignments = validationContext.getSwitchTracksWithAlignments(id)
            val linkedTracks = linkedTracksAndAlignments.map(Pair<LocationTrack, *>::first)

            val referenceIssues = validateSwitchLocationTrackLinkReferences(switch, linkedTracks)

            val locationIssues = if (switch.exists) validateSwitchLocation(switch) else emptyList()
            val structureIssues =
                locationIssues.ifEmpty {
                    validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndAlignments)
                }

            val duplicationIssues =
                validateSwitchNameDuplication(switch, validationContext.getSwitchesByName(switch.name))
            return referenceIssues + structureIssues + duplicationIssues
        }

    private fun validateReferenceLine(
        id: IntId<ReferenceLine>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? =
        validationContext.getReferenceLineWithAlignment(id)?.let { (referenceLine, alignment) ->
            val trackNumber = validationContext.getTrackNumber(referenceLine.trackNumberId)
            val referenceIssues =
                validateReferenceLineReference(
                    referenceLine = referenceLine,
                    trackNumber = trackNumber,
                    trackNumberNumber = validationContext.getDraftTrackNumber(referenceLine.trackNumberId)?.number,
                )
            val alignmentIssues =
                if (trackNumber?.exists == true) {
                    validateReferenceLineAlignment(alignment)
                } else {
                    listOf()
                }
            val geocodingIssues: List<LayoutValidationIssue> =
                if (trackNumber?.exists == true) {
                    val contextKey = validationContext.getGeocodingContextCacheKey(referenceLine.trackNumberId)
                    val contextIssues =
                        validateGeocodingContext(contextKey, VALIDATION_REFERENCE_LINE, trackNumber.number)
                    val addressIssues =
                        contextKey?.let { key ->
                            val locationTracks =
                                validationContext.getLocationTracksByTrackNumber(referenceLine.trackNumberId)
                            locationTracks.flatMap { track ->
                                validateAddressPoints(trackNumber, key, track, VALIDATION_REFERENCE_LINE)
                            }
                        } ?: listOf()
                    contextIssues + addressIssues
                } else {
                    listOf()
                }

            return referenceIssues + alignmentIssues + geocodingIssues
        }

    private fun validateLocationTrack(
        id: IntId<LocationTrack>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? =
        validationContext.getLocationTrackWithAlignment(id)?.let { (track, alignment) ->
            val trackNumber = validationContext.getTrackNumber(track.trackNumberId)
            val trackNumberName = (trackNumber ?: validationContext.getDraftTrackNumber(track.trackNumberId))?.number

            val referenceIssues = validateLocationTrackReference(track, trackNumber, trackNumberName)
            val segmentSwitches = validationContext.getSegmentSwitches(alignment)
            val switchSegmentIssues = validateSegmentSwitchReferences(track, segmentSwitches)
            val topologicallyConnectedSwitchIssues =
                validateTopologicallyConnectedSwitchReferences(
                    track,
                    validationContext.getTopologicallyConnectedSwitches(track),
                )
            val trackNetworkTopologyIssues =
                validationContext.getPotentiallyAffectedSwitches(id).filter(TrackLayoutSwitch::exists).flatMap { switch
                    ->
                    val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
                    val switchTracks = validationContext.getSwitchTracksWithAlignments(switch.id as IntId)
                    validateSwitchTopologicalConnectivity(switch, structure, switchTracks, track)
                }
            val switchConnectivityIssues =
                if (track.exists) {
                    validateLocationTrackSwitchConnectivity(track, alignment)
                } else {
                    emptyList()
                }

            val duplicatesAfterPublication = validationContext.getDuplicateTracks(id)
            val duplicateOf = track.duplicateOf?.let(validationContext::getLocationTrack)
            // Draft-only won't be found if it's not in the publication set -> get name from draft
            // for validation issue
            val duplicateOfName = track.duplicateOf?.let(validationContext::getDraftLocationTrack)?.name
            val duplicateIssues =
                validateDuplicateOfState(track, duplicateOf, duplicateOfName, duplicatesAfterPublication)

            val alignmentIssues = if (track.exists) validateLocationTrackAlignment(alignment) else listOf()
            val geocodingIssues =
                if (track.exists && trackNumber != null) {
                    validationContext.getGeocodingContextCacheKey(track.trackNumberId)?.let { key ->
                        validateAddressPoints(trackNumber, key, track, VALIDATION_REFERENCE_LINE)
                    } ?: listOf(noGeocodingContext(VALIDATION_LOCATION_TRACK))
                } else listOf()

            val tracksWithSameName = validationContext.getLocationTracksByName(track.name)
            val duplicateNameIssues = validateLocationTrackNameDuplication(track, trackNumberName, tracksWithSameName)

            (referenceIssues +
                switchSegmentIssues +
                topologicallyConnectedSwitchIssues +
                duplicateIssues +
                alignmentIssues +
                geocodingIssues +
                duplicateNameIssues +
                trackNetworkTopologyIssues +
                switchConnectivityIssues)
        }

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        val publication = publicationDao.getPublication(id)
        val ratkoStatus = ratkoPushDao.getRatkoStatus(id).sortedByDescending { it.endTime }.firstOrNull()

        val publishedReferenceLines = publicationDao.fetchPublishedReferenceLines(id)
        val publishedKmPosts = publicationDao.fetchPublishedKmPosts(id)
        val (publishedDirectTrackNumbers, publishedIndirectTrackNumbers) = publicationDao.fetchPublishedTrackNumbers(id)
        val (publishedDirectTracks, publishedIndirectTracks) = publicationDao.fetchPublishedLocationTracks(id)
        val (publishedDirectSwitches, publishedIndirectSwitches) = publicationDao.fetchPublishedSwitches(id)
        val split = splitService.getSplitIdByPublicationId(id)?.let(splitService::get)

        return PublicationDetails(
            id = publication.id,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            message = publication.message,
            trackNumbers = publishedDirectTrackNumbers,
            referenceLines = publishedReferenceLines,
            locationTracks = publishedDirectTracks,
            switches = publishedDirectSwitches,
            kmPosts = publishedKmPosts,
            ratkoPushStatus = ratkoStatus?.status,
            ratkoPushTime = ratkoStatus?.endTime,
            indirectChanges =
                PublishedIndirectChanges(
                    trackNumbers = publishedIndirectTrackNumbers,
                    locationTracks = publishedIndirectTracks,
                    switches = publishedIndirectSwitches,
                ),
            split = split?.let(::SplitHeader),
            layoutBranch = publication.layoutBranch,
        )
    }

    @Transactional(readOnly = true)
    fun getPublicationDetailsAsTableItems(
        id: IntId<Publication>,
        translation: Translation,
    ): List<PublicationTableItem> {
        val geocodingContextCache =
            ConcurrentHashMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>()
        return getPublicationDetails(id).let { publication ->
            val previousPublication =
                publicationDao
                    .fetchPublicationTimes(publication.layoutBranch)
                    .entries
                    .sortedByDescending { it.key }
                    .find { it.key < publication.publicationTime }
            mapToPublicationTableItems(
                translation,
                publication,
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(publication.id),
                previousPublication?.key ?: publication.publicationTime.minusMillis(1),
                { trackNumberId: IntId<TrackLayoutTrackNumber>, timestamp: Instant ->
                    getOrPutGeocodingContext(geocodingContextCache, publication.layoutBranch, trackNumberId, timestamp)
                },
            )
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublications(layoutBranch: LayoutBranch, from: Instant? = null, to: Instant? = null): List<Publication> {
        return publicationDao.fetchPublicationsBetween(layoutBranch, from, to)
    }

    @Transactional(readOnly = true)
    fun fetchPublicationDetailsBetweenInstants(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
    ): List<PublicationDetails> {
        return publicationDao.fetchPublicationsBetween(layoutBranch, from, to).map { getPublicationDetails(it.id) }
    }

    @Transactional(readOnly = true)
    fun fetchLatestPublicationDetails(branchType: LayoutBranchType, count: Int): List<PublicationDetails> {
        return publicationDao.fetchLatestPublications(branchType, count).map { getPublicationDetails(it.id) }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationDetails(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        translation: Translation,
    ): List<PublicationTableItem> {
        val switchLinkChanges =
            publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(null, layoutBranch, from, to)

        return fetchPublicationDetailsBetweenInstants(layoutBranch, from, to)
            .sortedBy { it.publicationTime }
            .let { publications ->
                val geocodingContextCache =
                    ConcurrentHashMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>()
                val trackNumbersCache = trackNumberDao.fetchTrackNumberNames()
                val getGeocodingContextOrNull = { trackNumberId: IntId<TrackLayoutTrackNumber>, timestamp: Instant ->
                    getOrPutGeocodingContext(geocodingContextCache, layoutBranch, trackNumberId, timestamp)
                }

                publications
                    .mapIndexed { index, publicationDetails ->
                        val previousPublication = publications.getOrNull(index - 1)
                        publicationDetails to
                            (previousPublication?.publicationTime ?: publicationDetails.publicationTime.minusMillis(1))
                    }
                    .flatMap { (publicationDetails, timeDiff) ->
                        mapToPublicationTableItems(
                            translation,
                            publicationDetails,
                            switchLinkChanges[publicationDetails.id] ?: mapOf(),
                            timeDiff,
                            getGeocodingContextOrNull,
                            trackNumbersCache,
                        )
                    }
            }
            .let { publications ->
                if (sortBy == null) publications else publications.sortedWith(getComparator(sortBy, order))
            }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublication(id: IntId<Publication>): SplitInPublication? {
        return publicationDao.getPublication(id).let { publication ->
            splitService.getSplitIdByPublicationId(id)?.let { splitId ->
                val split = splitService.getOrThrow(splitId)
                val (sourceLocationTrack, sourceAlignment) =
                    locationTrackService.getWithAlignment(split.sourceLocationTrackVersion)
                val targetLocationTracks =
                    publicationDao
                        .fetchPublishedLocationTracks(id)
                        .let { changes -> (changes.indirectChanges + changes.directChanges).map { c -> c.version } }
                        .distinct()
                        .mapNotNull { v ->
                            createSplitTargetInPublication(
                                sourceAlignment = sourceAlignment,
                                rowVersion = v,
                                publicationBranch = publication.layoutBranch,
                                publicationTime = publication.publicationTime,
                                split = split,
                            )
                        }
                        .sortedWith { a, b -> nullsFirstComparator(a.startAddress, b.startAddress) }
                SplitInPublication(
                    id = publication.id,
                    splitId = split.id,
                    locationTrack = sourceLocationTrack,
                    targetLocationTracks = targetLocationTracks,
                )
            }
        }
    }

    private fun createSplitTargetInPublication(
        sourceAlignment: LayoutAlignment,
        rowVersion: LayoutRowVersion<LocationTrack>,
        publicationBranch: LayoutBranch,
        publicationTime: Instant,
        split: Split,
    ): SplitTargetInPublication? {
        val (track, alignment) = locationTrackService.getWithAlignment(rowVersion)
        return split.getTargetLocationTrack(track.id as IntId)?.let { target ->
            val ctx =
                geocodingService.getGeocodingContextAtMoment(publicationBranch, track.trackNumberId, publicationTime)

            val startBySegments =
                requireNotNull(
                    sourceAlignment.segments[target.segmentIndices.first].segmentStart.let { point ->
                        ctx?.getAddress(point)?.first
                    }
                )
            val endBySegments =
                requireNotNull(
                    sourceAlignment.segments[target.segmentIndices.last].segmentEnd.let { point ->
                        ctx?.getAddress(point)?.first
                    }
                )
            val startByTargetAlignment = requireNotNull(alignment.start?.let { point -> ctx?.getAddress(point)?.first })
            val endByTargetAlignment = requireNotNull(alignment.end?.let { point -> ctx?.getAddress(point)?.first })
            val startAddress = listOf(startBySegments, startByTargetAlignment).maxOrNull()
            val endAddress = listOf(endBySegments, endByTargetAlignment).minOrNull()

            return SplitTargetInPublication(
                id = track.id,
                name = track.name,
                oid = track.externalId,
                startAddress = startAddress,
                endAddress = endAddress,
                operation = target.operation,
            )
        }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublicationCsv(id: IntId<Publication>, lang: LocalizationLanguage): Pair<String, AlignmentName?> {
        return getSplitInPublication(id).let { splitInPublication ->
            val data =
                splitInPublication?.targetLocationTracks?.map { lt -> splitInPublication.locationTrack to lt }
                    ?: emptyList()
            printCsv(splitCsvColumns(localizationService.getLocalization(lang)), data) to
                splitInPublication?.locationTrack?.name
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationsAsCsv(
        layoutBranch: LayoutBranch,
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        timeZone: ZoneId? = null,
        translation: Translation,
    ): String {
        val orderedPublishedItems =
            fetchPublicationDetails(
                layoutBranch = layoutBranch,
                from = from,
                to = to,
                sortBy = sortBy,
                order = order,
                translation = translation,
            )

        return asCsvFile(orderedPublishedItems, timeZone ?: ZoneId.of("UTC"), translation)
    }

    fun diffTrackNumber(
        translation: Translation,
        trackNumberChanges: TrackNumberChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        val oldEndAddress =
            trackNumberChanges.endPoint.old?.let { point ->
                geocodingContextGetter(trackNumberChanges.id, oldTimestamp)?.getAddress(point)?.first
            }
        val newEndAddress =
            trackNumberChanges.endPoint.new?.let { point ->
                geocodingContextGetter(trackNumberChanges.id, newTimestamp)?.getAddress(point)?.first
            }

        return listOfNotNull(
            compareChangeValues(trackNumberChanges.trackNumber, { it }, PropKey("track-number")),
            compareChangeValues(trackNumberChanges.state, { it }, PropKey("state"), null, "layout-state"),
            compareChangeValues(trackNumberChanges.description, { it }, PropKey("description")),
            compareChangeValues(
                trackNumberChanges.startAddress,
                { it.toString() },
                PropKey("start-address"),
                remark =
                    getAddressMovedRemarkOrNull(
                        translation,
                        trackNumberChanges.startAddress.old,
                        trackNumberChanges.startAddress.new,
                    ),
            ),
            compareChange(
                { oldEndAddress != newEndAddress },
                oldEndAddress,
                newEndAddress,
                { it.toString() },
                PropKey("end-address"),
                remark = getAddressMovedRemarkOrNull(translation, oldEndAddress, newEndAddress),
            ),
        )
    }

    fun diffLocationTrack(
        translation: Translation,
        locationTrackChanges: LocationTrackChanges,
        switchLinkChanges: LocationTrackPublicationSwitchLinkChanges?,
        branch: LayoutBranch,
        publicationTime: Instant,
        previousPublicationTime: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        changedKmNumbers: Set<KmNumber>,
        getGeocodingContext: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        val oldAndTime = locationTrackChanges.duplicateOf.old to previousPublicationTime
        val newAndTime = locationTrackChanges.duplicateOf.new to publicationTime
        val oldStartPointAndM =
            locationTrackChanges.startPoint.old?.let { oldStart ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldStart)
                }
            }
        val oldEndPointAndM =
            locationTrackChanges.endPoint.old?.let { oldEnd ->
                locationTrackChanges.trackNumberId.old?.let {
                    getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldEnd)
                }
            }
        val newStartPointAndM =
            locationTrackChanges.startPoint.new?.let { newStart ->
                locationTrackChanges.trackNumberId.new?.let {
                    getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newStart)
                }
            }
        val newEndPointAndM =
            locationTrackChanges.endPoint.new?.let { newEnd ->
                locationTrackChanges.trackNumberId.new?.let {
                    getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newEnd)
                }
            }

        return listOfNotNull(
            compareChangeValues(
                locationTrackChanges.trackNumberId,
                { tnIdFromChange ->
                    trackNumberCache
                        .findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= publicationTime }
                        ?.number
                },
                PropKey("track-number"),
            ),
            compareChangeValues(locationTrackChanges.name, { it }, PropKey("location-track")),
            compareChangeValues(locationTrackChanges.state, { it }, PropKey("state"), null, "location-track-state"),
            compareChangeValues(
                locationTrackChanges.type,
                { it },
                PropKey("location-track-type"),
                null,
                "location-track-type",
            ),
            compareChangeValues(locationTrackChanges.descriptionBase, { it }, PropKey("description-base")),
            compareChangeValues(
                locationTrackChanges.descriptionSuffix,
                { it },
                PropKey("description-suffix"),
                enumLocalizationKey = "location-track-description-suffix",
            ),
            compareChangeValues(
                locationTrackChanges.owner,
                { locationTrackService.getLocationTrackOwners().find { owner -> owner.id == it }?.name },
                PropKey("owner"),
            ),
            compareChange(
                { oldAndTime.first != newAndTime.first },
                oldAndTime,
                newAndTime,
                { (duplicateOf, timestamp) ->
                    duplicateOf?.let { locationTrackService.getOfficialAtMoment(branch, it, timestamp)?.name }
                },
                PropKey("duplicate-of"),
            ),
            compareLength(
                locationTrackChanges.length.old,
                locationTrackChanges.length.new,
                DISTANCE_CHANGE_THRESHOLD,
                ::roundTo1Decimal,
                PropKey("length"),
                getLengthChangedRemarkOrNull(
                    translation,
                    locationTrackChanges.length.old,
                    locationTrackChanges.length.new,
                ),
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.startPoint.old, locationTrackChanges.startPoint.new) },
                locationTrackChanges.startPoint.old,
                locationTrackChanges.startPoint.new,
                ::formatLocation,
                PropKey("start-location"),
                getPointMovedRemarkOrNull(
                    translation,
                    locationTrackChanges.startPoint.old,
                    locationTrackChanges.startPoint.new,
                ),
            ),
            compareChange(
                { oldStartPointAndM?.address != newStartPointAndM?.address },
                oldStartPointAndM?.address,
                newStartPointAndM?.address,
                { it.toString() },
                PropKey("start-address"),
                null,
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new) },
                locationTrackChanges.endPoint.old,
                locationTrackChanges.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(
                    translation,
                    locationTrackChanges.endPoint.old,
                    locationTrackChanges.endPoint.new,
                ),
            ),
            compareChange(
                { oldEndPointAndM?.address != newEndPointAndM?.address },
                oldEndPointAndM?.address,
                newEndPointAndM?.address,
                { it.toString() },
                PropKey("end-address"),
                null,
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"),
                    ChangeValue(null, null),
                    getKmNumbersChangedRemarkOrNull(
                        translation,
                        changedKmNumbers,
                        locationTrackChanges.geometryChangeSummaries,
                    ),
                )
            } else {
                null
            },
            if (switchLinkChanges == null) {
                null
            } else {
                compareChange(
                    { switchLinkChanges.old != switchLinkChanges.new },
                    null,
                    null,
                    { it },
                    PropKey("linked-switches"),
                    getSwitchLinksChangedRemark(translation, switchLinkChanges),
                )
            },
            // TODO owner
        )
    }

    fun diffReferenceLine(
        translation: Translation,
        changes: ReferenceLineChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        changedKmNumbers: Set<KmNumber>,
        getGeocodingContext: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        return listOfNotNull(
            compareLength(
                changes.length.old,
                changes.length.new,
                DISTANCE_CHANGE_THRESHOLD,
                ::roundTo1Decimal,
                PropKey("length"),
                getLengthChangedRemarkOrNull(translation, changes.length.old, changes.length.new),
            ),
            compareChange(
                { !pointsAreSame(changes.startPoint.old, changes.startPoint.new) },
                changes.startPoint.old,
                changes.startPoint.new,
                ::formatLocation,
                PropKey("start-location"),
                getPointMovedRemarkOrNull(translation, changes.startPoint.old, changes.startPoint.new),
            ),
            compareChange(
                { !pointsAreSame(changes.endPoint.old, changes.endPoint.new) },
                changes.endPoint.old,
                changes.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(translation, changes.endPoint.old, changes.endPoint.new),
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"),
                    ChangeValue(null, null),
                    publicationChangeRemark(
                        translation,
                        if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
                        formatChangedKmNumbers(changedKmNumbers.toList()),
                    ),
                )
            } else {
                null
            },
        )
    }

    fun diffKmPost(
        translation: Translation,
        changes: KmPostChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
        crsNameGetter: (srid: Srid) -> String,
    ) =
        listOfNotNull(
            compareChangeValues(
                changes.trackNumberId,
                { tnIdFromChange ->
                    trackNumberCache.findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= newTimestamp }?.number
                },
                PropKey("track-number"),
            ),
            compareChangeValues(changes.kmNumber, { it }, PropKey("km-post")),
            compareChangeValues(changes.state, { it }, PropKey("state"), null, "layout-state"),
            compareChangeValues(
                changes.location,
                ::formatLocation,
                PropKey("layout-location"),
                remark =
                    getPointMovedRemarkOrNull(
                        translation,
                        projectPointToReferenceLineAtTime(
                            oldTimestamp,
                            changes.location.old,
                            changes.trackNumberId.old,
                            geocodingContextGetter,
                        ),
                        projectPointToReferenceLineAtTime(
                            newTimestamp,
                            changes.location.new,
                            changes.trackNumberId.new,
                            geocodingContextGetter,
                        ),
                        "moved-x-meters-on-reference-line",
                    ),
            ),
            compareChangeValues(changes.gkLocation, { formatGkLocation(it, crsNameGetter) }, PropKey("gk-location")),
            compareChangeValues(changes.gkSrid, { crsNameGetter(it) }, PropKey("gk-srid")),
            compareChangeValues(
                changes.gkLocationSource,
                { it },
                PropKey("gk-location-source"),
                enumLocalizationKey = "gk-location-source",
            ),
            compareChangeValues(changes.gkLocationConfirmed, { it }, PropKey("gk-location-confirmed")),
        )

    private fun projectPointToReferenceLineAtTime(
        timestamp: Instant,
        location: Point?,
        trackNumberId: IntId<TrackLayoutTrackNumber>?,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ) =
        location?.let {
            trackNumberId?.let {
                geocodingContextGetter(trackNumberId, timestamp)?.let { context ->
                    context.getM(location)?.let { (m) -> context.referenceLineGeometry.getPointAtM(m)?.toPoint() }
                }
            }
        }

    fun diffSwitch(
        translation: Translation,
        changes: SwitchChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        operation: Operation,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        val relatedJoints = changes.joints.filterNot { it.removed }.distinctBy { it.trackNumberId }

        val oldLinkedLocationTracks =
            changes.locationTracks.associate { lt ->
                locationTrackService.getWithAlignment(lt.oldVersion).let { (track, alignment) ->
                    track.id as IntId to (track to alignment)
                }
            }
        val jointLocationChanges =
            relatedJoints
                .flatMap { joint ->
                    val oldLocation =
                        oldLinkedLocationTracks[joint.locationTrackId]
                            ?.let { (track, alignment) ->
                                findJointPoint(track, alignment, changes.id, joint.jointNumber)
                            }
                            ?.toPoint()
                    val distance =
                        if (oldLocation != null && !pointsAreSame(joint.point, oldLocation)) {
                            calculateDistance(listOf(joint.point, oldLocation), LAYOUT_SRID)
                        } else {
                            0.0
                        }
                    val jointPropKeyParams =
                        localizationParams(
                            "trackNumber" to
                                trackNumberCache
                                    .findLast { it.id == joint.trackNumberId && it.changeTime <= newTimestamp }
                                    ?.number
                                    ?.value,
                            "switchType" to
                                changes.type.new?.parts?.baseType?.let { switchBaseTypeToProp(translation, it) },
                        )
                    val oldAddress =
                        oldLocation?.let {
                            geocodingContextGetter(joint.trackNumberId, oldTimestamp)?.getAddress(it)?.first
                        }

                    val list =
                        listOfNotNull(
                            compareChange(
                                { distance > DISTANCE_CHANGE_THRESHOLD },
                                oldLocation,
                                joint.point,
                                ::formatLocation,
                                PropKey("switch-joint-location", jointPropKeyParams),
                                getPointMovedRemarkOrNull(translation, oldLocation, joint.point),
                                null,
                            ),
                            compareChange(
                                { oldAddress != joint.address },
                                oldAddress,
                                joint.address,
                                { it.toString() },
                                PropKey("switch-track-address", jointPropKeyParams),
                                getAddressMovedRemarkOrNull(translation, oldAddress, joint.address),
                            ),
                        )
                    list
                }
                .sortedBy { it.propKey.key }

        val oldLinkedTrackNames = oldLinkedLocationTracks.values.mapNotNull { it.first.name }.sorted()
        val newLinkedTrackNames = changes.locationTracks.map { it.name }.sorted()

        return listOfNotNull(
            compareChangeValues(changes.name, { it }, PropKey("switch")),
            compareChangeValues(changes.state, { it }, PropKey("state-category"), null, "layout-state-category"),
            compareChangeValues(changes.type, { it.typeName }, PropKey("switch-type")),
            compareChangeValues(changes.trapPoint, { it }, PropKey("trap-point"), enumLocalizationKey = "trap-point"),
            compareChangeValues(changes.owner, { it }, PropKey("owner")),
            compareChange(
                { oldLinkedTrackNames != newLinkedTrackNames },
                oldLinkedTrackNames,
                newLinkedTrackNames,
                { list -> list.joinToString(", ") { it } },
                PropKey("location-track-connectivity"),
            ),
            compareChangeValues(
                changes.measurementMethod,
                { it.name },
                PropKey("measurement-method"),
                null,
                "measurement-method",
            ),
        ) + jointLocationChanges
    }

    private fun getOrPutGeocodingContext(
        caches: MutableMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>,
        branch: LayoutBranch,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        timestamp: Instant,
    ) =
        caches
            .getOrPut(timestamp) { ConcurrentHashMap() }
            .getOrPut(trackNumberId) {
                Optional.ofNullable(geocodingService.getGeocodingContextAtMoment(branch, trackNumberId, timestamp))
            }
            .orElse(null)

    private fun validateGeocodingContext(
        cacheKey: GeocodingContextCacheKey?,
        localizationKey: String,
        trackNumber: TrackNumber,
    ) =
        cacheKey?.let(geocodingCacheService::getGeocodingContextWithReasons)?.let { context ->
            validateGeocodingContext(context, trackNumber)
        } ?: listOf(noGeocodingContext(localizationKey))

    private fun validateAddressPoints(
        trackNumber: TrackLayoutTrackNumber,
        contextKey: GeocodingContextCacheKey,
        track: LocationTrack,
        validationTargetLocalizationPrefix: String,
    ): List<LayoutValidationIssue> =
        if (!track.exists) {
            listOf()
        } else if (track.alignmentVersion == null) {
            throw IllegalStateException("LocationTrack in DB should have an alignment: track=$track")
        } else {
            validateAddressPoints(trackNumber, track, validationTargetLocalizationPrefix) {
                geocodingService.getAddressPoints(contextKey, track.alignmentVersion)
            }
        }

    private fun latestTrackNumberNamesAtMoment(
        trackNumberNames: List<TrackNumberAndChangeTime>,
        trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
        publicationTime: Instant,
    ) =
        trackNumberNames
            .filter { tn -> trackNumberIds.contains(tn.id) && tn.changeTime <= publicationTime }
            .groupBy { it.id }
            .map { it.value.last().number }
            .toSet()

    private fun mapToPublicationTableItems(
        translation: Translation,
        publication: PublicationDetails,
        switchLinkChanges: Map<IntId<LocationTrack>, LocationTrackPublicationSwitchLinkChanges>,
        previousComparisonTime: Instant,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
        trackNumberNamesCache: List<TrackNumberAndChangeTime> = trackNumberDao.fetchTrackNumberNames(),
    ): List<PublicationTableItem> {
        val publicationLocationTrackChanges = publicationDao.fetchPublicationLocationTrackChanges(publication.id)
        val publicationTrackNumberChanges =
            publicationDao.fetchPublicationTrackNumberChanges(
                publication.layoutBranch,
                publication.id,
                previousComparisonTime,
            )
        val publicationKmPostChanges = publicationDao.fetchPublicationKmPostChanges(publication.id)
        val publicationReferenceLineChanges = publicationDao.fetchPublicationReferenceLineChanges(publication.id)
        val publicationSwitchChanges = publicationDao.fetchPublicationSwitchChanges(publication.id)

        val trackNumbers =
            publication.trackNumbers.map { tn ->
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.track-number-long")} ${tn.number}",
                    trackNumbers = setOf(tn.number),
                    changedKmNumbers = tn.changedKmNumbers,
                    operation = tn.operation,
                    publication = publication,
                    propChanges =
                        diffTrackNumber(
                            translation,
                            publicationTrackNumberChanges.getOrElse(tn.id) {
                                error("Track number changes not found: id=${tn.id} version=${tn.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            geocodingContextGetter,
                        ),
                )
            }

        val referenceLines =
            publication.referenceLines.map { rl ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == rl.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number

                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.reference-line")} $tn",
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = rl.changedKmNumbers,
                    operation = rl.operation,
                    publication = publication,
                    propChanges =
                        diffReferenceLine(
                            translation,
                            publicationReferenceLineChanges.getOrElse(rl.id) {
                                error("Reference line changes not found: id=${rl.id} version=${rl.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            rl.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val locationTracks =
            publication.locationTracks.map { lt ->
                val trackNumber =
                    trackNumberNamesCache
                        .findLast { it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.location-track")} ${lt.name}",
                    trackNumbers = setOfNotNull(trackNumber),
                    changedKmNumbers = lt.changedKmNumbers,
                    operation = lt.operation,
                    publication = publication,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            publicationLocationTrackChanges.getOrElse(lt.id) {
                                error("Location track changes not found: id=${lt.id} version=${lt.version}")
                            },
                            switchLinkChanges[lt.id],
                            publication.layoutBranch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val switches =
            publication.switches.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.switch")} ${s.name}",
                    trackNumbers = tns,
                    operation = s.operation,
                    publication = publication,
                    propChanges =
                        diffSwitch(
                            translation,
                            publicationSwitchChanges.getOrElse(s.id) {
                                error("Switch changes not found: id=${s.id} version=${s.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            s.operation,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                        ),
                )
            }

        val kmPosts =
            publication.kmPosts.map { kp ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == kp.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.km-post")} ${kp.kmNumber}",
                    trackNumbers = setOfNotNull(tn),
                    operation = kp.operation,
                    publication = publication,
                    propChanges =
                        diffKmPost(
                            translation,
                            publicationKmPostChanges.getOrElse(kp.id) {
                                error("KM Post changes not found: id=${kp.id} version=${kp.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                            crsNameGetter = { srid -> geographyService.getCoordinateSystem(srid).name.toString() },
                        ),
                )
            }

        val calculatedLocationTracks =
            publication.indirectChanges.locationTracks.map { lt ->
                val tn =
                    trackNumberNamesCache
                        .findLast { it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime }
                        ?.number
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.location-track")} ${lt.name}",
                    trackNumbers = setOfNotNull(tn),
                    changedKmNumbers = lt.changedKmNumbers,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    propChanges =
                        diffLocationTrack(
                            translation,
                            publicationLocationTrackChanges.getOrElse(lt.id) {
                                error("Location track changes not found: id=${lt.id} version=${lt.version}")
                            },
                            switchLinkChanges[lt.id],
                            publication.layoutBranch,
                            publication.publicationTime,
                            previousComparisonTime,
                            trackNumberNamesCache,
                            lt.changedKmNumbers,
                            geocodingContextGetter,
                        ),
                )
            }

        val calculatedSwitches =
            publication.indirectChanges.switches.map { s ->
                val tns =
                    latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
                mapToPublicationTableItem(
                    name = "${translation.t("publication-table.switch")} ${s.name}",
                    trackNumbers = tns,
                    operation = Operation.CALCULATED,
                    publication = publication,
                    propChanges =
                        diffSwitch(
                            translation,
                            publicationSwitchChanges.getOrElse(s.id) {
                                error("Switch changes not found: id=${s.id} version=${s.version}")
                            },
                            publication.publicationTime,
                            previousComparisonTime,
                            Operation.CALCULATED,
                            trackNumberNamesCache,
                            geocodingContextGetter,
                        ),
                )
            }

        return listOf(
                trackNumbers,
                referenceLines,
                locationTracks,
                switches,
                kmPosts,
                calculatedLocationTracks,
                calculatedSwitches,
            )
            .flatten()
            .map { publicationTableItem ->
                addOperationClarificationsToPublicationTableItem(translation, publicationTableItem)
            }
    }

    private fun mapToPublicationTableItem(
        name: String,
        trackNumbers: Set<TrackNumber>,
        operation: Operation,
        publication: PublicationDetails,
        changedKmNumbers: Set<KmNumber>? = null,
        propChanges: List<PublicationChange<*>>,
    ) =
        PublicationTableItem(
            name = FreeText(name),
            trackNumbers = trackNumbers.sorted(),
            changedKmNumbers =
                changedKmNumbers?.let { groupChangedKmNumbers(changedKmNumbers.toList()) } ?: emptyList(),
            operation = operation,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            message = publication.message,
            ratkoPushTime =
                if (publication.ratkoPushStatus == RatkoPushStatus.SUCCESSFUL) publication.ratkoPushTime else null,
            propChanges = propChanges,
        )

    private fun enrichDuplicateNameExceptionOrRethrow(
        branch: LayoutBranch,
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
                maybeThrowDuplicateLocationTrackNameException(branch, detail, exception)
        }
        throw exception
    }

    private val duplicateLocationTrackErrorRegex =
        Regex(
            """Key \(track_number_id, name, layout_context_id\)=\((\d+), ([^,]+), [^)]+\) conflicts with existing key"""
        )
    private val duplicateTrackNumberErrorRegex =
        Regex("""Key \(number, layout_context_id\)=\(([^,]+), [^)]+\) already exists""")
    private val duplicateSwitchErrorRegex =
        Regex("""Key \(name, layout_context_id\)=\(([^,]+), [^)]+\) conflicts with existing key""")

    private fun maybeThrowDuplicateLocationTrackNameException(
        branch: LayoutBranch,
        detail: String,
        exception: DataIntegrityViolationException,
    ) {
        duplicateLocationTrackErrorRegex.matchAt(detail, 0)?.let { match ->
            val trackIdString = match.groups[1]?.value
            val nameString = match.groups[2]?.value
            val trackId = IntId<TrackLayoutTrackNumber>(Integer.parseInt(trackIdString))
            if (trackIdString != null && nameString != null) {
                val trackNumberVersion = trackNumberDao.fetchVersion(branch.official, trackId)
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
