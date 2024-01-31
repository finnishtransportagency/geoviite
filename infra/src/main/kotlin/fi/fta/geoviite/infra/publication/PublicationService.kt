package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.DuplicateLocationTrackNameInPublicationException
import fi.fta.geoviite.infra.error.DuplicateNameInPublication
import fi.fta.geoviite.infra.error.DuplicateNameInPublicationException
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.*
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.SplitTargetListingHeader
import fi.fta.geoviite.infra.geometry.translateSplitTargetListingHeader
import fi.fta.geoviite.infra.integration.*
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.localization.Translation
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.publication.PublishValidationErrorType.ERROR
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.ratko.RatkoPushDao
import fi.fta.geoviite.infra.split.SplitHeader
import fi.fta.geoviite.infra.split.SplitPublishValidationErrors
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.CsvEntry
import fi.fta.geoviite.infra.util.SortOrder
import fi.fta.geoviite.infra.util.nullsFirstComparator
import fi.fta.geoviite.infra.util.printCsv
import org.postgresql.util.PSQLException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.ZoneId
import java.util.*
import java.util.concurrent.ConcurrentHashMap

private val splitCsvColumns = listOf(
    CsvEntry<Pair<LocationTrack, SplitTargetInPublication>>(
        translateSplitTargetListingHeader(SplitTargetListingHeader.SOURCE_NAME),
    ) { (lt, _) -> lt.name },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.SOURCE_OID),
    ) { (lt, _) -> lt.externalId },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.TARGET_NAME),
    ) { (_, split) -> split.name },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.TARGET_OID),
    ) { (_, split) -> split.oid },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.OPERATION),
    ) { (_, split) -> if (split.newlyCreated) "Uusi kohde" else "Duplikaatti korvattu" },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.START_ADDRESS),
    ) { (_, split) -> split.startAddress },
    CsvEntry(
        translateSplitTargetListingHeader(SplitTargetListingHeader.END_ADDRESS),
    ) { (_, split) -> split.endAddress },
)

@Service
class PublicationService @Autowired constructor(
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
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun collectPublishCandidates(): PublishCandidates {
        logger.serviceCall("collectPublishCandidates")
        return PublishCandidates(
            trackNumbers = publicationDao.fetchTrackNumberPublishCandidates(),
            locationTracks = publicationDao.fetchLocationTrackPublishCandidates(),
            referenceLines = publicationDao.fetchReferenceLinePublishCandidates(),
            switches = publicationDao.fetchSwitchPublishCandidates(),
            kmPosts = publicationDao.fetchKmPostPublishCandidates(),
        ).let(::assignPublicationGroups)
    }

    fun assignPublicationGroups(
        publishCandidates: PublishCandidates,
    ): PublishCandidates {
        val locationTrackIdToPublicationGroup = mutableMapOf<IntId<LocationTrack>, PublicationGroup>()
        val switchIdToPublicationGroup = mutableMapOf<IntId<TrackLayoutSwitch>, PublicationGroup>()

        publishCandidates.locationTracks
            .map { locationTrack -> locationTrack.id }
            .let { locationTrackIds -> splitService.findUnfinishedSplitsForLocationTracks(locationTrackIds) }
            .map { unfinishedSplit ->
                val publicationGroup = PublicationGroup(unfinishedSplit.id)

                unfinishedSplit.locationTracks.forEach { locationTrackId ->
                    locationTrackIdToPublicationGroup[locationTrackId] = publicationGroup
                }

                unfinishedSplit.relinkedSwitches.forEach { switchId ->
                    switchIdToPublicationGroup[switchId] = publicationGroup
                }
            }

        return publishCandidates.copy(
            locationTracks = publishCandidates.locationTracks.map { locationTrackPublishCandidate ->
                locationTrackIdToPublicationGroup[locationTrackPublishCandidate.id]?.let { assignedPublicationGroup ->
                    locationTrackPublishCandidate.copy(
                        publicationGroup = assignedPublicationGroup
                    )
                } ?: locationTrackPublishCandidate
            },

            switches = publishCandidates.switches.map { switchPublishCandidate ->
                switchIdToPublicationGroup[switchPublishCandidate.id]?.let { assignedPublicationGroup ->
                    switchPublishCandidate.copy(
                        publicationGroup = assignedPublicationGroup
                    )
                } ?: switchPublishCandidate
            },
        )
    }

    @Transactional(readOnly = true)
    fun validatePublishCandidates(
        candidates: PublishCandidates,
        request: PublishRequestIds,
    ): ValidatedPublishCandidates {
        logger.serviceCall("validatePublishCandidates", "candidates" to candidates, "request" to request)
        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidates.filter(request), allowMultipleSplits = false),
            allChangesValidated = validateAsPublicationUnit(candidates, allowMultipleSplits = true),
        )
    }

    @Transactional(readOnly = true)
    fun validateTrackNumberAndReferenceLine(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publishType: PublishType,
    ): ValidatedAsset<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "validateTrackNumberAndReferenceLine", "trackNumberId" to trackNumberId, "publishType" to publishType
        )

        val trackNumber = trackNumberService.getOrThrow(publishType, trackNumberId)
        val referenceLine = referenceLineService.getByTrackNumber(publishType, trackNumberId)

        val locationTracks = if (publishType == DRAFT) locationTrackDao
            .fetchVersions(DRAFT, false, trackNumberId)
            .map(locationTrackDao::fetch)
        else emptyList()

        val kmPosts =
            if (publishType == DRAFT) kmPostDao.fetchVersions(DRAFT, false, trackNumberId).map(kmPostDao::fetch)
            else emptyList()

        val versions = toValidationVersions(
            trackNumbers = listOf(trackNumber),
            referenceLines = listOfNotNull(referenceLine),
            kmPosts = kmPosts,
            locationTracks = locationTracks,
        )

        val cacheKeys = collectCacheKeys(versions)

        val trackNumberValidation = validateTrackNumber(
            version = toValidationVersion(trackNumber), validationVersions = versions, cacheKeys = cacheKeys
        )

        val referenceLineValidation = versions.referenceLines.flatMap { rl ->
            validateReferenceLine(
                version = rl,
                validationVersions = versions,
                cacheKeys = cacheKeys,
            )
        }

        return ValidatedAsset(
            errors = (trackNumberValidation + referenceLineValidation).distinct(),
            id = trackNumberId,
        )
    }

    @Transactional(readOnly = true)
    fun validateLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
    ): ValidatedAsset<LocationTrack> {
        logger.serviceCall(
            "validateLocationTrack", "locationTrackId" to locationTrackId, "publishType" to publishType
        )

        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(publishType, locationTrackId)

        val duplicateTrack = locationTrack.duplicateOf?.let { duplicateId ->
            locationTrackService.getOrThrow(publishType, duplicateId)
        }

        val trackNumber = trackNumberService.getOrThrow(publishType, locationTrack.trackNumberId)
        val referenceLine = referenceLineService.getByTrackNumber(publishType, trackNumber.id as IntId)

        val switches = getConnectedSwitchIds(locationTrack, alignment).map { switchId ->
            switchService.getOrThrow(publishType, switchId)
        }

        val versions = toValidationVersions(
            locationTracks = listOfNotNull(locationTrack, duplicateTrack),
            switches = switches,
            trackNumbers = listOf(trackNumber),
            referenceLines = listOfNotNull(referenceLine)
        )

        val cacheKeys = collectCacheKeys(versions)
        val switchTrackLinks = collectSwitchTrackLinks(versions, if (publishType == DRAFT) null else listOf())

        return ValidatedAsset(
            validateLocationTrack(
                version = toValidationVersion(locationTrack),
                validationVersions = versions,
                cacheKeys = cacheKeys,
                switchTrackLinks = switchTrackLinks,
            ),
            locationTrackId,
        )
    }

    @Transactional(readOnly = true)
    fun validateSwitches(
        switchIds: List<IntId<TrackLayoutSwitch>>,
        publishType: PublishType,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        logger.serviceCall("validateSwitches", "switchIds" to switchIds, "publishType" to publishType)

        val switches = switchService.getMany(publishType, switchIds)
        val locationTracks =
            switchDao.findLocationTracksLinkedToSwitches(publishType, switchIds).map { it.rowVersion }.distinct()

        val previouslyLinkedTracks = if (publishType == DRAFT) switchDao
            .findLocationTracksLinkedToSwitches(OFFICIAL, switchIds)
            .mapNotNull { lt -> locationTrackDao.fetchDraftVersion(lt.rowVersion.id) }
            .distinct()
            .filterNot(locationTracks::contains) else emptyList()

        val validationVersions = toValidationVersions(
            switches = switches, locationTracks = (locationTracks + previouslyLinkedTracks).map(locationTrackDao::fetch)
        )
        val nameDuplicates = collectPotentialSwitchNameDuplicates(validationVersions)

        val linkedTracks = publicationDao
            .fetchLinkedLocationTracks(switchIds, if (publishType == DRAFT) null else listOf())
            .mapValues { (_, tracks) -> tracks.toSet() }
        return switches.map { switch ->
            ValidatedAsset(
                validateSwitch(
                    version = toValidationVersion(switch),
                    validationVersions = validationVersions,
                    linkedTracks = linkedTracks.getOrDefault(switch.id, setOf()),
                    nameDuplicates = nameDuplicates,
                ),
                switch.id as IntId,
            )
        }
    }

    @Transactional(readOnly = true)
    fun validateKmPost(
        kmPostId: IntId<TrackLayoutKmPost>,
        publishType: PublishType,
    ): ValidatedAsset<TrackLayoutKmPost> {
        logger.serviceCall("validateKmPost", "kmPostId" to kmPostId, "publishType" to publishType)

        val kmPost = kmPostService.getOrThrow(publishType, kmPostId)
        val trackNumber = kmPost.trackNumberId?.let { trackNumberId ->
            trackNumberService.getOrThrow(publishType, trackNumberId)
        }

        val referenceLine = trackNumber?.let {
            referenceLineService.getByTrackNumber(publishType, trackNumber.id as IntId)
        }

        val versions = toValidationVersions(
            trackNumbers = listOfNotNull(trackNumber),
            referenceLines = listOfNotNull(referenceLine),
            kmPosts = listOf(kmPost)
        )

        val cacheKeys = collectCacheKeys(versions)

        return ValidatedAsset(
            validateKmPost(
                version = toValidationVersion(kmPost), validationVersions = versions, cacheKeys = cacheKeys
            ), kmPostId
        )
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return publicationDao.fetchChangeTime()
    }

    private fun validateAsPublicationUnit(candidates: PublishCandidates, allowMultipleSplits: Boolean): PublishCandidates {
        val versions = candidates.getValidationVersions()
        val cacheKeys = collectCacheKeys(versions)
        precacheLocationTrackAlignmentAddresses(candidates, cacheKeys)
        val switchTrackLinks = collectSwitchTrackLinks(versions, versions.locationTracks.map { v -> v.officialId })
        val nameDuplicates = collectPotentialSwitchNameDuplicates(versions)
        val splitErrors = splitService.validateSplit(versions, allowMultipleSplits)

        return PublishCandidates(
            trackNumbers = candidates.trackNumbers.map { candidate ->
                val trackNumberSplitErrors = splitErrors.trackNumbers[candidate.id] ?: emptyList()
                val validationErrors = validateTrackNumber(candidate.getPublicationVersion(), versions, cacheKeys)

                candidate.copy(errors = trackNumberSplitErrors + validationErrors)
            },
            referenceLines = candidates.referenceLines.map { candidate ->
                val referenceLineSplitErrors = splitErrors.referenceLines[candidate.id] ?: emptyList()
                val validationErrors = validateReferenceLine(candidate.getPublicationVersion(), versions, cacheKeys)

                candidate.copy(errors = referenceLineSplitErrors + validationErrors)
            },
            locationTracks = candidates.locationTracks.map { candidate ->
                val locationTrackSplitErrors = splitErrors.locationTracks[candidate.id] ?: emptyList()

                val validationErrors = validateLocationTrack(
                    version = candidate.getPublicationVersion(),
                    validationVersions = versions,
                    cacheKeys = cacheKeys,
                    switchTrackLinks = switchTrackLinks,
                )

                candidate.copy(errors = validationErrors + locationTrackSplitErrors)
            },
            switches = candidates.switches.map { candidate ->
                val switchSplitErrors = splitErrors.switches[candidate.id] ?: emptyList()

                val validationErrors = validateSwitch(
                    candidate.getPublicationVersion(),
                    versions,
                    switchTrackLinks.trackVersionsBySwitchAfterPublication.getOrDefault(candidate.id, setOf()),
                    nameDuplicates,
                )

                candidate.copy(errors = validationErrors + switchSplitErrors)
            },
            kmPosts = candidates.kmPosts.map { candidate ->
                val kmPostSplitErrors = splitErrors.kmPosts[candidate.id] ?: emptyList()
                val validationErrors = validateKmPost(candidate.getPublicationVersion(), versions, cacheKeys)

                candidate.copy(errors = validationErrors + kmPostSplitErrors)
            },
        )
    }

    private fun precacheLocationTrackAlignmentAddresses(
        candidates: PublishCandidates,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ) = candidates.locationTracks.mapNotNull { candidate ->
        val track = locationTrackDao.fetch(candidate.getPublicationVersion().validatedAssetVersion)
        val alignmentVersion = track.alignmentVersion
        val geocodingContextKey = cacheKeys[track.trackNumberId]
        if (alignmentVersion == null || geocodingContextKey == null) null
        else geocodingService.collectDataForGetAddressPoints(geocodingContextKey, alignmentVersion)
    }.parallelStream().forEach { run -> run() }

    @Transactional(readOnly = true)
    fun validatePublishRequest(versions: ValidationVersions) {
        logger.serviceCall("validatePublishRequest", "versions" to versions)
        val cacheKeys = collectCacheKeys(versions)
        val switchTrackLinks = collectSwitchTrackLinks(versions, versions.locationTracks.map { v -> v.officialId })
        val splitErrors = splitService.validateSplit(versions, allowMultipleSplits = false)
        val nameDuplicates = collectPotentialSwitchNameDuplicates(versions)

        assertNoSplitErrors(splitErrors)

        versions.trackNumbers.forEach { version ->
            assertNoErrors(version, validateTrackNumber(version, versions, cacheKeys))
        }
        versions.kmPosts.forEach { version ->
            assertNoErrors(version, validateKmPost(version, versions, cacheKeys))
        }
        versions.referenceLines.forEach { version ->
            assertNoErrors(version, validateReferenceLine(version, versions, cacheKeys))
        }
        versions.locationTracks.forEach { version ->
            assertNoErrors(version, validateLocationTrack(version, versions, cacheKeys, switchTrackLinks))
        }

        versions.switches.forEach { version ->
            assertNoErrors(
                version,
                validateSwitch(
                    version,
                    versions,
                    switchTrackLinks.trackVersionsBySwitchAfterPublication.getOrDefault(version.officialId, setOf()),
                    nameDuplicates,
                ),
            )
        }
    }

    data class SwitchTrackLinks(
        val switchIdsByTrackBeforeOrAfterPublication: Map<IntId<LocationTrack>, List<IntId<TrackLayoutSwitch>>>,
        val trackVersionsBySwitchAfterPublication:  Map<IntId<TrackLayoutSwitch>, Set<RowVersion<LocationTrack>>>,
    )

    private fun collectSwitchTrackLinks(
        versions: ValidationVersions,
        trackIdsInPublicationUnit: List<IntId<LocationTrack>>?,
    ): SwitchTrackLinks {
        val switchIdsInPublicationUnit = versions.switches.map { v -> v.officialId }
        val linkedSwitchIds =
            publicationDao.fetchLinkedSwitchesBeforeOrAfterPublication(versions.locationTracks.map { v -> v.officialId })
        val allSwitchesForValidation = (switchIdsInPublicationUnit + linkedSwitchIds.values.flatten()).distinct()
        val trackLinks = publicationDao.fetchLinkedLocationTracks(allSwitchesForValidation, trackIdsInPublicationUnit)
        return SwitchTrackLinks(linkedSwitchIds, trackLinks)
    }

    @Transactional(readOnly = true)
    fun getRevertRequestDependencies(requestIds: PublishRequestIds): PublishRequestIds {
        logger.serviceCall("getRevertRequestDependencies", "requestIds" to requestIds)

        val referenceLineTrackNumberIds = referenceLineService.getMany(DRAFT, requestIds.referenceLines).map { rlId ->
            rlId.trackNumberId
        }
        val trackNumbers = trackNumberService.getMany(DRAFT, referenceLineTrackNumberIds + requestIds.trackNumbers)
        val revertTrackNumberIds = trackNumbers.filter(TrackLayoutTrackNumber::isDraft).map { it.id as IntId }
        val draftOnlyTrackNumberIds = trackNumbers.filter(TrackLayoutTrackNumber::isNewDraft).map { it.id as IntId }

        val revertLocationTrackIds = requestIds.locationTracks + draftOnlyTrackNumberIds.flatMap { tnId ->
            locationTrackDao.fetchOnlyDraftVersions(includeDeleted = true, tnId)
        }.map(RowVersion<LocationTrack>::id)

        val revertSplitTracks = splitService.findUnfinishedSplitsForLocationTracks(revertLocationTrackIds).flatMap { it.locationTracks }

        val revertKmPostIds = requestIds.kmPosts.toSet() + draftOnlyTrackNumberIds.flatMap { tnId ->
            kmPostDao.fetchOnlyDraftVersions(includeDeleted = true, tnId)
        }.map { kp -> kp.id }

        val referenceLines = requestIds.referenceLines.toSet() + requestIds.trackNumbers.mapNotNull { tnId ->
            referenceLineService.getByTrackNumber(DRAFT, tnId)
        }.filter(ReferenceLine::isDraft).map { line -> line.id as IntId }

        return PublishRequestIds(
            trackNumbers = revertTrackNumberIds.toList(),
            referenceLines = referenceLines.toList(),
            locationTracks = (revertLocationTrackIds + revertSplitTracks).distinct(),
            switches = requestIds.switches.distinct(),
            kmPosts = revertKmPostIds.toList()
        )
    }

    @Transactional
    fun revertPublishCandidates(toDelete: PublishRequestIds): PublishResult {
        logger.serviceCall("revertPublishCandidates", "toDelete" to toDelete)

        splitService.findUnfinishedSplitsForLocationTracks(toDelete.locationTracks)
            .map { it.id }
            .distinct()
            .forEach(splitService::deleteSplit)

        val locationTrackCount = toDelete.locationTracks.map { id -> locationTrackService.deleteDraft(id) }.size
        val referenceLineCount = toDelete.referenceLines.map { id -> referenceLineService.deleteDraft(id) }.size
        alignmentDao.deleteOrphanedAlignments()
        val switchCount = toDelete.switches.map { id -> switchService.deleteDraft(id) }.size
        val kmPostCount = toDelete.kmPosts.map { id -> kmPostService.deleteDraft(id) }.size
        val trackNumberCount = toDelete.trackNumbers.map { id -> trackNumberService.deleteDraft(id) }.size

        return PublishResult(
            publishId = null,
            trackNumbers = trackNumberCount,
            locationTracks = locationTrackCount,
            referenceLines = referenceLineCount,
            switches = switchCount,
            kmPosts = kmPostCount,
        )
    }

    /**
     * Note: this is intentionally not transactional:
     * each ID is fetched from ratko and becomes an object there -> we want to store it, even if the rest fail
     */
    fun updateExternalId(request: PublishRequestIds) {
        logger.serviceCall("updateExternalId", "request" to request)

        try {
            request.locationTracks
                .filter { trackId -> locationTrackService.getOrThrow(DRAFT, trackId).externalId == null }
                .forEach { trackId -> updateExternalIdForLocationTrack(trackId) }
            request.trackNumbers
                .filter { trackNumberId -> trackNumberService.getOrThrow(DRAFT, trackNumberId).externalId == null }
                .forEach { trackNumberId -> updateExternalIdForTrackNumber(trackNumberId) }
            request.switches
                .filter { switchId -> switchService.getOrThrow(DRAFT, switchId).externalId == null }
                .forEach { switchId -> updateExternalIdForSwitch(switchId) }
        } catch (e: Exception) {
            throw PublicationFailureException(
                message = "Failed to update external IDs for publish candidates",
                cause = e,
                localizedMessageKey = "external-id-update-failed"
            )
        }
    }

    @Transactional(readOnly = true)
    fun getValidationVersions(request: PublishRequestIds): ValidationVersions {
        logger.serviceCall("getValidationVersions", "request" to request)
        return ValidationVersions(
            trackNumbers = trackNumberDao.fetchPublicationVersions(request.trackNumbers),
            referenceLines = referenceLineDao.fetchPublicationVersions(request.referenceLines),
            kmPosts = kmPostDao.fetchPublicationVersions(request.kmPosts),
            locationTracks = locationTrackDao.fetchPublicationVersions(request.locationTracks),
            switches = switchDao.fetchPublicationVersions(request.switches),
        )
    }

    private fun updateExternalIdForLocationTrack(locationTrackId: IntId<LocationTrack>) {
        val locationTrackOid = ratkoClient?.let { s ->
            requireNotNull(s.getNewLocationTrackOid()) { "No OID received from RATKO" }
        }
        locationTrackOid?.let { oid -> locationTrackService.updateExternalId(locationTrackId, Oid(oid.id)) }
    }

    private fun updateExternalIdForTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val routeNumberOid = ratkoClient?.let { s ->
            requireNotNull(s.getNewRouteNumberOid()) { "No OID received from RATKO" }
        }
        routeNumberOid?.let { oid -> trackNumberService.updateExternalId(trackNumberId, Oid(oid.id)) }
    }

    private fun updateExternalIdForSwitch(switchId: IntId<TrackLayoutSwitch>) {
        val switchOid = ratkoClient?.let { s ->
            requireNotNull(s.getNewSwitchOid()) { "No OID received from RATKO" }
        }
        switchOid?.let { oid -> switchService.updateExternalIdForSwitch(switchId, Oid(oid.id)) }
    }

    private inline fun <reified T> assertNoErrors(
        version: ValidationVersion<T>,
        errors: List<PublishValidationError>,
    ) {
        val severeErrors = errors.filter { error -> error.type == ERROR }
        if (severeErrors.isNotEmpty()) {
            logger.warn("Validation errors in published ${T::class.simpleName}: item=$version errors=$severeErrors")
            throw PublicationFailureException(
                message = "Cannot publish ${T::class.simpleName} due to validation errors: $version",
                localizedMessageKey = "validation-failed",
            )
        }
    }

    private fun assertNoSplitErrors(errors: SplitPublishValidationErrors) {
        val splitErrors = (errors.kmPosts + errors.trackNumbers + errors.referenceLines + errors.kmPosts)
            .values
            .flatten()
            .filter { error -> error.type == ERROR }

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
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
        message: String,
    ): PublishResult {
        logger.serviceCall(
            "publishChanges", "versions" to versions, "calculatedChanges" to calculatedChanges, "message" to message
        )

        try {
            return transactionTemplate.execute { publishChangesTransaction(versions, calculatedChanges, message) }
                ?: throw Exception("unexpected null from publishChangesTransaction")
        } catch (exception: DataIntegrityViolationException) {
            enrichDuplicateNameExceptionOrRethrow(exception)
        }
    }

    private fun publishChangesTransaction(
        versions: ValidationVersions,
        calculatedChanges: CalculatedChanges,
        message: String,
    ): PublishResult {
        val trackNumbers = versions.trackNumbers.map(trackNumberService::publish).map { r -> r.rowVersion }
        val kmPosts = versions.kmPosts.map(kmPostService::publish).map { r -> r.rowVersion }
        val switches = versions.switches.map(switchService::publish).map { r -> r.rowVersion }
        val referenceLines = versions.referenceLines.map(referenceLineService::publish).map { r -> r.rowVersion }
        val locationTracks = versions.locationTracks.map(locationTrackService::publish).map { r -> r.rowVersion }
        val publicationId = publicationDao.createPublication(message)
        publicationDao.insertCalculatedChanges(publicationId, calculatedChanges)
        publicationGeometryChangeRemarksUpdateService.processPublication(publicationId)

        splitService.publishSplit(versions.locationTracks.map { it.officialId }, publicationId)

        return PublishResult(
            publishId = publicationId,
            trackNumbers = trackNumbers.size,
            referenceLines = referenceLines.size,
            locationTracks = locationTracks.size,
            switches = switches.size,
            kmPosts = kmPosts.size,
        )
    }

    private fun validateTrackNumber(
        version: ValidationVersion<TrackLayoutTrackNumber>,
        validationVersions: ValidationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val trackNumber = trackNumberDao.fetch(version.validatedAssetVersion)
        val kmPosts = getKmPostsByTrackNumber(version.officialId, validationVersions)
        val referenceLine = getReferenceLineByTrackNumber(version.officialId, validationVersions)
        val locationTracks = getLocationTracksByTrackNumber(version.officialId, validationVersions)
        val fieldErrors = validateDraftTrackNumberFields(trackNumber)
        val referenceErrors = validateTrackNumberReferences(
            trackNumber,
            referenceLine,
            kmPosts,
            locationTracks,
            validationVersions.kmPosts.map { it.officialId },
            validationVersions.locationTracks.map { it.officialId },
        )
        val geocodingErrors = if (trackNumber.exists && referenceLine != null) {
            validateGeocodingContext(cacheKeys[version.officialId], VALIDATION_TRACK_NUMBER, trackNumber.number)
        } else listOf()
        val duplicateNameErrors = validateTrackNumberNumberDuplication(trackNumber, validationVersions)
        return fieldErrors + referenceErrors + geocodingErrors + duplicateNameErrors
    }

    private fun validateTrackNumberNumberDuplication(
        trackNumber: TrackLayoutTrackNumber,
        versions: ValidationVersions,
    ): List<PublishValidationError> {
        val drafts = versions.trackNumbers.map { it.officialId to trackNumberDao.fetch(it.validatedAssetVersion) }
        val officials = trackNumberDao.fetchVersions(OFFICIAL, false).map(trackNumberDao::fetch).filterNot { official ->
            drafts.map { draft -> draft.first }.contains(official.id)
        }
        val officialDuplicateExists =
            officials.any { official -> official.id != trackNumber.id && official.number == trackNumber.number }
        val stagedDuplicateExists =
            drafts.any { (_, draft) -> draft.number == trackNumber.number && draft.id != trackNumber.id && draft.state != LayoutState.DELETED }

        return listOfNotNull(
            if (!stagedDuplicateExists && officialDuplicateExists) PublishValidationError(
                ERROR, "$VALIDATION_TRACK_NUMBER.duplicate-name-official", mapOf("trackNumber" to trackNumber.number)
            ) else null,
            if (stagedDuplicateExists) PublishValidationError(
                ERROR, "$VALIDATION_TRACK_NUMBER.duplicate-name-draft", mapOf("trackNumber" to trackNumber.number)
            ) else null,
        )
    }

    private fun validateKmPost(
        version: ValidationVersion<TrackLayoutKmPost>,
        validationVersions: ValidationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val kmPost = kmPostDao.fetch(version.validatedAssetVersion)
        val trackNumber = kmPost.trackNumberId?.let { id -> getTrackNumber(id, validationVersions) }
        val referenceLine = kmPost.trackNumberId?.let { id -> getReferenceLineByTrackNumber(id, validationVersions) }
        val fieldErrors = validateDraftKmPostFields(kmPost)
        val referenceErrors = validateKmPostReferences(
            kmPost,
            trackNumber,
            referenceLine,
            validationVersions.trackNumbers.map { it.officialId },
        )
        val geocodingErrors = if (kmPost.exists && trackNumber?.exists == true && referenceLine != null) {
            validateGeocodingContext(cacheKeys[kmPost.trackNumberId], VALIDATION_KM_POST, trackNumber.number)
        } else listOf()

        return fieldErrors + referenceErrors + geocodingErrors
    }

    private fun validateSwitch(
        version: ValidationVersion<TrackLayoutSwitch>,
        validationVersions: ValidationVersions,
        linkedTracks: Set<RowVersion<LocationTrack>>,
        nameDuplicates: Map<SwitchName, PotentialDuplicates>,
    ): List<PublishValidationError> {
        val switch = switchDao.fetch(version.validatedAssetVersion)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val linkedTracksAndAlignments = linkedTracks.map(locationTrackService::getWithAlignment)
        val fieldErrors = validateDraftSwitchFields(switch)
        val referenceErrors = validateSwitchLocationTrackLinkReferences(
            switch,
            linkedTracksAndAlignments.map(Pair<LocationTrack, *>::first),
            validationVersions.locationTracks.map { it.officialId },
        )
        val locationErrors = if (switch.exists) validateSwitchLocation(switch) else emptyList()
        val structureErrors = locationErrors.ifEmpty {
            validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndAlignments)
        }

        val duplicationErrors = validateSwitchNameDuplication(
            switch = switch,
            draftsBySameName = nameDuplicates[switch.name]?.draft ?: emptyList(),
            officialsBySameName = nameDuplicates[switch.name]?.official ?: emptyList(),
        )
        return fieldErrors + referenceErrors + structureErrors + duplicationErrors
    }

    data class PotentialDuplicates(val draft: List<TrackLayoutSwitch>, val official: List<TrackLayoutSwitch>)

    private fun collectPotentialSwitchNameDuplicates(
        versions: ValidationVersions,
    ): Map<SwitchName, PotentialDuplicates> {
        val drafts = versions.switches.map { sv -> sv.validatedAssetVersion }.map(switchDao::fetch)
        val draftNamesToCheck = drafts.filter(TrackLayoutSwitch::exists).map(TrackLayoutSwitch::name).distinct()
        val officialVersions = switchDao.findOfficialNameDuplicates(draftNamesToCheck)
        val officials = officialVersions.mapValues { (_, officials) ->
            officials.mapNotNull { v -> if (versions.containsSwitch(v.id)) null else switchDao.fetch(v) }
        }
        return draftNamesToCheck.associate { name ->
            name to PotentialDuplicates(
                draft = drafts.filter { d -> d.name == name && d.exists },
                official = officials[name] ?: listOf(),
            )
        }
    }

    private fun validateReferenceLine(
        version: ValidationVersion<ReferenceLine>,
        validationVersions: ValidationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (referenceLine, alignment) = getReferenceLineAndAlignment(version.validatedAssetVersion)
        val trackNumber = getTrackNumber(referenceLine.trackNumberId, validationVersions)
        val referenceErrors = validateReferenceLineReference(
            referenceLine,
            trackNumber,
            validationVersions.trackNumbers.map { it.officialId },
        )
        val alignmentErrors = if (trackNumber?.exists == true) validateReferenceLineAlignment(alignment) else listOf()
        val geocodingErrors: List<PublishValidationError> = if (trackNumber?.exists == true) {
            val contextKey = cacheKeys[referenceLine.trackNumberId]
            val contextErrors = validateGeocodingContext(contextKey, VALIDATION_REFERENCE_LINE, trackNumber.number)
            val addressErrors = contextKey?.let { key ->
                val locationTracks = getLocationTracksByTrackNumber(trackNumber.id as IntId, validationVersions)
                locationTracks.flatMap { track ->
                    validateAddressPoints(trackNumber, key, track, VALIDATION_REFERENCE_LINE)
                }
            } ?: listOf()
            contextErrors + addressErrors
        } else listOf()

        return referenceErrors + alignmentErrors + geocodingErrors
    }

    private fun validateLocationTrack(
        version: ValidationVersion<LocationTrack>,
        validationVersions: ValidationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
        switchTrackLinks: SwitchTrackLinks,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.validatedAssetVersion)
        val trackNumber = getTrackNumber(locationTrack.trackNumberId, validationVersions)
        val fieldErrors = validateDraftLocationTrackFields(locationTrack)
        val referenceErrors = validateLocationTrackReference(
            locationTrack,
            trackNumber,
            validationVersions.trackNumbers.map { it.officialId },
        )
        val switchErrorsSegments = validateSegmentSwitchReferences(
            locationTrack,
            getSegmentSwitches(alignment, validationVersions),
            validationVersions.switches.map { it.officialId },
        )
        val topologicallyConnectedSwitchError = validateTopologicallyConnectedSwitchReferences(
            getTopologicallyConnectedSwitches(locationTrack, validationVersions),
            validationVersions.switches.map { it.officialId },
        )
        val trackNetworkTopologyErrors =
            validateLocationTrackTopologicalConnectivity(version, locationTrack, validationVersions, switchTrackLinks)
        val duplicateErrors = validateDuplicateOf(version, locationTrack, validationVersions)
        val alignmentErrors = if (locationTrack.exists) validateLocationTrackAlignment(alignment)
        else listOf()
        val geocodingErrors = if (locationTrack.exists && trackNumber != null) {
            cacheKeys[locationTrack.trackNumberId]?.let { key ->
                validateAddressPoints(trackNumber, key, locationTrack, VALIDATION_LOCATION_TRACK)
            } ?: listOf(noGeocodingContext(VALIDATION_LOCATION_TRACK))
        } else listOf()
        val duplicateNameErrors = if (locationTrack.exists) validateLocationTrackNameDuplication(
            locationTrack, validationVersions
        ) else listOf()

        return (fieldErrors +
                referenceErrors +
                switchErrorsSegments +
                topologicallyConnectedSwitchError +
                duplicateErrors +
                alignmentErrors +
                geocodingErrors +
                duplicateNameErrors +
                trackNetworkTopologyErrors)
    }

    private fun validateLocationTrackTopologicalConnectivity(
        version: ValidationVersion<LocationTrack>,
        validatingTrack: LocationTrack,
        validationVersions: ValidationVersions,
        switchTrackLinks: SwitchTrackLinks,
    ): List<PublishValidationError> {
        return switchTrackLinks.switchIdsByTrackBeforeOrAfterPublication
            .getOrDefault(version.officialId, listOf())
            .map { switchId -> getSwitchForValidation(switchId, validationVersions) }
            .filter(TrackLayoutSwitch::exists)
            .flatMap { switch ->
                val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
                val locationTracks = switchTrackLinks.trackVersionsBySwitchAfterPublication
                    .getOrDefault(switch.id, listOf())
                    .mapNotNull { locationTrackVersion ->
                        val track = locationTrackDao.fetch(locationTrackVersion)
                        track.alignmentVersion?.let(alignmentDao::fetch)?.let { track to it }
                    }
                validateSwitchTopologicalConnectivity(switch, structure, locationTracks, validatingTrack)
            }
    }

    @Transactional(readOnly = true)
    fun validateDuplicateOf(
        version: ValidationVersion<LocationTrack>,
        track: LocationTrack,
        validationVersions: ValidationVersions,
    ): List<PublishValidationError> {
        // Find the versions in validation set context. These can be null (draft-only that's not included)
        val duplicatesAfterPublication = locationTrackDao
            .fetchDuplicateIdsInAnyLayoutContext(version.officialId)
            .mapNotNull { id -> getLocationTrack(id, validationVersions) }
            .filter { potentialDuplicate -> potentialDuplicate.duplicateOf == version.officialId }

        val duplicateOf = track.duplicateOf?.let { id -> getLocationTrack(id, validationVersions) }
        // Draft-only won't be found if it's not in the publication set -> get name from draft for validation errors
        val duplicateOfDraftName = track.duplicateOf?.let { id -> locationTrackDao.getOrThrow(DRAFT, id).name }
        return validateDuplicateOfState(track, duplicateOf, duplicateOfDraftName, duplicatesAfterPublication)
    }

    private fun validateLocationTrackNameDuplication(
        locationTrack: LocationTrack,
        versions: ValidationVersions,
    ): List<PublishValidationError> {
        val drafts = versions.locationTracks
            .map { it.officialId to locationTrackDao.fetch(it.validatedAssetVersion) }
            .filter { (_, lt) -> lt.trackNumberId == locationTrack.trackNumberId }
        val officials = locationTrackDao
            .list(OFFICIAL, false, locationTrack.trackNumberId)
            .filterNot { official -> drafts.map { draft -> draft.first }.contains(official.id) }
        val officialDuplicateExists = officials.any { official ->
            official.id != locationTrack.id && official.name == locationTrack.name
        }
        val stagedDuplicateExists = drafts.any { (_, draft) ->
            draft.name == locationTrack.name && draft.id != locationTrack.id && draft.state != LayoutState.DELETED
        }
        val trackNumberName = locationTrack.trackNumberId.let { trackNumberService.get(DRAFT, it)?.number }

        return listOfNotNull(
            if (stagedDuplicateExists) PublishValidationError(
                ERROR,
                "$VALIDATION_LOCATION_TRACK.duplicate-name-draft",
                mapOf("locationTrack" to locationTrack.name, "trackNumber" to trackNumberName)
            ) else null, if (!stagedDuplicateExists && officialDuplicateExists) PublishValidationError(
                ERROR,
                "$VALIDATION_LOCATION_TRACK.duplicate-name-official",
                mapOf("locationTrack" to locationTrack.name, "trackNumber" to trackNumberName)
            ) else null
        )
    }

    private fun getTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): TrackLayoutTrackNumber? {
        val version = versions.findTrackNumber(id)?.validatedAssetVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }

    private fun getLocationTrack(id: IntId<LocationTrack>, versions: ValidationVersions): LocationTrack? {
        val version = versions.findLocationTrack(id)?.validatedAssetVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let(locationTrackDao::fetch)
    }

    private fun getReferenceLineByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ) = versions.referenceLines
        .map { v -> referenceLineDao.fetch(v.validatedAssetVersion) }
        .find { line -> line.trackNumberId == trackNumberId } ?: referenceLineDao
        .fetchVersionByTrackNumberId(OFFICIAL, trackNumberId)
        ?.let(referenceLineDao::fetch)

    private fun getKmPostsByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ) = combineVersions(
        officials = kmPostDao.fetchVersions(OFFICIAL, false, trackNumberId),
        validations = versions.kmPosts,
    ).map(kmPostDao::fetch).filter { km -> km.trackNumberId == trackNumberId }

    private fun getLocationTracksByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ) = combineVersions(
        officials = locationTrackDao.fetchVersions(OFFICIAL, false, trackNumberId),
        validations = versions.locationTracks
    ).map(locationTrackDao::fetch).filter { lt -> lt.trackNumberId == trackNumberId }

    private fun getReferenceLineAndAlignment(version: RowVersion<ReferenceLine>) =
        referenceLineWithAlignment(referenceLineDao, alignmentDao, version)

    private fun getLocationTrackAndAlignment(version: RowVersion<LocationTrack>) =
        locationTrackWithAlignment(locationTrackDao, alignmentDao, version)

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        logger.serviceCall("getPublicationDetails", "id" to id)

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
            indirectChanges = PublishedIndirectChanges(
                trackNumbers = publishedIndirectTrackNumbers,
                locationTracks = publishedIndirectTracks,
                switches = publishedIndirectSwitches
            ),
            split = split?.let(::SplitHeader),
        )
    }

    @Transactional(readOnly = true)
    fun getPublicationDetailsAsTableItems(
        id: IntId<Publication>,
        translation: Translation,
    ): List<PublicationTableItem> {
        logger.serviceCall("getPublicationDetailsAsTableItems", "id" to id)
        val geocodingContextCache =
            ConcurrentHashMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>()
        return getPublicationDetails(id).let { publication ->
            val previousPublication = publicationDao.fetchPublicationTimes().entries
                .sortedByDescending { it.key }
                .find { it.key < publication.publicationTime }
            mapToPublicationTableItems(translation,
                publication,
                publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(publication.id),
                previousPublication?.key ?: publication.publicationTime.minusMillis(1),
                { trackNumberId: IntId<TrackLayoutTrackNumber>, timestamp: Instant ->
                    getOrPutGeocodingContext(
                        geocodingContextCache, trackNumberId, timestamp
                    )
                })
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublications(from: Instant? = null, to: Instant? = null): List<Publication> {
        logger.serviceCall("fetchPublications", "from" to from, "to" to to)
        return publicationDao.fetchPublicationsBetween(from, to)
    }

    @Transactional(readOnly = true)
    fun fetchPublicationDetailsBetweenInstants(from: Instant? = null, to: Instant? = null): List<PublicationDetails> {
        logger.serviceCall("fetchPublicationDetailsBetweenInstants", "from" to from, "to" to to)
        return publicationDao.fetchPublicationsBetween(from, to).map { getPublicationDetails(it.id) }
    }

    @Transactional(readOnly = true)
    fun fetchLatestPublicationDetails(count: Int): List<PublicationDetails> {
        logger.serviceCall("fetchLatestPublicationDetails", "count" to count)
        return publicationDao.fetchLatestPublications(count).map { getPublicationDetails(it.id) }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationDetails(
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        translation: Translation,
    ): List<PublicationTableItem> {
        logger.serviceCall(
            "fetchPublicationDetails",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
        )

        val switchLinkChanges = publicationDao.fetchPublicationLocationTrackSwitchLinkChanges(null, from, to)

        return fetchPublicationDetailsBetweenInstants(from, to).sortedBy { it.publicationTime }.let { publications ->
            val geocodingContextCache =
                ConcurrentHashMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>()
            val trackNumbersCache = trackNumberDao.fetchTrackNumberNames()
            val getGeocodingContextOrNull = { trackNumberId: IntId<TrackLayoutTrackNumber>, timestamp: Instant ->
                getOrPutGeocodingContext(geocodingContextCache, trackNumberId, timestamp)
            }

            publications.mapIndexed { index, publicationDetails ->
                val previousPublication = publications.getOrNull(index - 1)
                publicationDetails to (previousPublication?.publicationTime
                    ?: publicationDetails.publicationTime.minusMillis(1))
            }.flatMap { (publicationDetails, timeDiff) ->
                mapToPublicationTableItems(
                    translation,
                    publicationDetails,
                    switchLinkChanges[publicationDetails.id] ?: mapOf(),
                    timeDiff,
                    getGeocodingContextOrNull,
                    trackNumbersCache,
                )
            }
        }.let { publications ->
            if (sortBy == null) publications
            else publications.sortedWith(getComparator(sortBy, order))
        }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublication(id: IntId<Publication>): SplitInPublication? {
        logger.serviceCall("getPublicationLocationTrackInfo", "id" to id)
        return publicationDao.getPublication(id).let { publication ->
            splitService.getSplitIdByPublicationId(id)?.let { splitId ->
                val split = splitService.getOrThrow(splitId)
                val sourceLocationTrack = locationTrackService.getOfficialAtMoment(split.locationTrackId, publication.publicationTime)
                requireNotNull(sourceLocationTrack) { "Source location track not found" }
                SplitInPublication(
                    id = publication.id,
                    splitId = split.id,
                    locationTrack = sourceLocationTrack,
                    targetLocationTracks = publicationDao
                        .fetchPublishedLocationTracks(id)
                        .let { changes -> changes.indirectChanges + changes.directChanges }
                        .distinctBy { it.version }
                        .map { change ->
                            locationTrackService
                                .getWithAlignment(change.version)
                                .let { (lt, alignment) -> Triple(lt, alignment, change) }
                        }
                        .filter { (lt, _, _) -> lt.id != split.locationTrackId && split.containsLocationTrack(lt.id as IntId) }
                        .map { (lt, alignment, change) ->
                            geocodingService.getGeocodingContextAtMoment(lt.trackNumberId, publication.publicationTime).let { geocodingContext ->
                                SplitTargetInPublication(
                                    id = lt.id as IntId,
                                    name = lt.name,
                                    oid = lt.externalId,
                                    startAddress =
                                    alignment.start?.let { start -> geocodingContext?.getAddress(start)?.first },
                                    endAddress = alignment.end?.let { end -> geocodingContext?.getAddress(end)?.first },
                                    newlyCreated = change.operation == Operation.CREATE,
                                )
                            }
                        }.sortedWith { a, b -> nullsFirstComparator(a.startAddress, b.startAddress) }
                )
            }
        }
    }

    @Transactional(readOnly = true)
    fun getSplitInPublicationCsv(id: IntId<Publication>): Pair<String, AlignmentName?> {
        logger.serviceCall("getSplitInPublicationCsv", "id" to id)
        return getSplitInPublication(id).let { splitInPublication ->
            printCsv(
                splitCsvColumns,
                splitInPublication?.targetLocationTracks?.map { lt -> splitInPublication.locationTrack to lt }
                    ?: emptyList()
            ) to splitInPublication?.locationTrack?.name
        }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationsAsCsv(
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        timeZone: ZoneId? = null,
        translation: Translation,
    ): String {
        logger.serviceCall(
            "fetchPublicationsAsCsv",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
            "timeZone" to timeZone,
        )

        val orderedPublishedItems = fetchPublicationDetails(
            from = from, to = to, sortBy = sortBy, order = order, translation = translation
        )

        return asCsvFile(
            orderedPublishedItems, timeZone ?: ZoneId.of("UTC"), translation
        )
    }

    fun diffTrackNumber(
        translation: Translation,
        trackNumberChanges: TrackNumberChanges,
        newTimestamp: Instant,
        oldTimestamp: Instant,
        geocodingContextGetter: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        val oldEndAddress = trackNumberChanges.endPoint.old?.let { point ->
            geocodingContextGetter(trackNumberChanges.id, oldTimestamp)?.getAddress(point)?.first
        }
        val newEndAddress = trackNumberChanges.endPoint.new?.let { point ->
            geocodingContextGetter(trackNumberChanges.id, newTimestamp)?.getAddress(point)?.first
        }

        return listOfNotNull(
            compareChangeValues(
                trackNumberChanges.trackNumber,
                { it },
                PropKey("track-number"),
            ),
            compareChangeValues(
                trackNumberChanges.state, { it }, PropKey("state"), null, "layout-state"
            ),
            compareChangeValues(
                trackNumberChanges.description, { it }, PropKey("description")
            ),
            compareChangeValues(
                trackNumberChanges.startAddress,
                { it.toString() },
                PropKey("start-address"),
                remark = getAddressMovedRemarkOrNull(
                    translation, trackNumberChanges.startAddress.old, trackNumberChanges.startAddress.new
                )
            ),
            compareChange({ oldEndAddress != newEndAddress },
                oldEndAddress,
                newEndAddress,
                { it.toString() },
                PropKey("end-address"),
                remark = getAddressMovedRemarkOrNull(translation, oldEndAddress, newEndAddress)
            ),
        )
    }

    fun diffLocationTrack(
        translation: Translation,
        locationTrackChanges: LocationTrackChanges,
        switchLinkChanges: LocationTrackPublicationSwitchLinkChanges?,
        publicationTime: Instant,
        previousPublicationTime: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
        changedKmNumbers: Set<KmNumber>,
        getGeocodingContext: (IntId<TrackLayoutTrackNumber>, Instant) -> GeocodingContext?,
    ): List<PublicationChange<*>> {
        val oldAndTime = locationTrackChanges.duplicateOf.old to previousPublicationTime
        val newAndTime = locationTrackChanges.duplicateOf.new to publicationTime
        val oldStartPointAndM = locationTrackChanges.startPoint.old?.let { oldStart ->
            locationTrackChanges.trackNumberId.old?.let {
                getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldStart)
            }
        }
        val oldEndPointAndM =locationTrackChanges.endPoint.old?.let { oldEnd ->
            locationTrackChanges.trackNumberId.old?.let {
                getGeocodingContext(it, oldAndTime.second)?.getAddressAndM(oldEnd)
            }
        }
        val newStartPointAndM = locationTrackChanges.startPoint.new?.let { newStart ->
            locationTrackChanges.trackNumberId.new?.let {
                getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newStart)
            }
        }
        val newEndPointAndM = locationTrackChanges.endPoint.new?.let { newEnd ->
            locationTrackChanges.trackNumberId.new?.let {
                getGeocodingContext(it, newAndTime.second)?.getAddressAndM(newEnd)
            }
        }

        return listOfNotNull(compareChangeValues(
            locationTrackChanges.trackNumberId,
            { tnIdFromChange -> trackNumberCache.findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= publicationTime }?.number },
            PropKey("track-number"),
        ),
            compareChangeValues(
                locationTrackChanges.name, { it }, PropKey("location-track")
            ),
            compareChangeValues(
                locationTrackChanges.state, { it }, PropKey("state"), null, "layout-state"
            ),
            compareChangeValues(
                locationTrackChanges.type, { it }, PropKey("location-track-type"), null, "location-track-type"
            ),
            compareChangeValues(
                locationTrackChanges.descriptionBase, { it }, PropKey("description-base")
            ),
            compareChangeValues(
                locationTrackChanges.descriptionSuffix,
                { it },
                PropKey("description-suffix"),
                enumLocalizationKey = "location-track-description-suffix"
            ),
            compareChangeValues(
                locationTrackChanges.owner,
                { locationTrackService.getLocationTrackOwners().find { owner -> owner.id == it }?.name },
                PropKey("owner")
            ),
            compareChange({ oldAndTime.first != newAndTime.first },
                oldAndTime,
                newAndTime,
                { (duplicateOf, timestamp) ->
                    duplicateOf?.let { locationTrackService.getOfficialAtMoment(it, timestamp)?.name }
                },
                PropKey("duplicate-of")
            ),
            compareLength(
                locationTrackChanges.length.old,
                locationTrackChanges.length.new,
                DISTANCE_CHANGE_THRESHOLD,
                ::roundTo1Decimal,
                PropKey("length"),
                getLengthChangedRemarkOrNull(
                    translation, locationTrackChanges.length.old, locationTrackChanges.length.new
                )
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.startPoint.old, locationTrackChanges.startPoint.new) },
                locationTrackChanges.startPoint.old,
                locationTrackChanges.startPoint.new,
                ::formatLocation,
                PropKey("start-location"),
                getPointMovedRemarkOrNull(
                    translation, locationTrackChanges.startPoint.old, locationTrackChanges.startPoint.new
                )
            ),
            compareChange(
                { oldStartPointAndM?.address != newStartPointAndM?.address },
                oldStartPointAndM?.address,
                newStartPointAndM?.address,
                { it.toString() },
                PropKey("start-address"),
                null
            ),
            compareChange(
                { !pointsAreSame(locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new) },
                locationTrackChanges.endPoint.old,
                locationTrackChanges.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(
                    translation, locationTrackChanges.endPoint.old, locationTrackChanges.endPoint.new
                )
            ),
            compareChange(
                { oldEndPointAndM?.address != newEndPointAndM?.address },
                oldEndPointAndM?.address,
                newEndPointAndM?.address,
                { it.toString() },
                PropKey("end-address"),
                null
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"), ChangeValue(null, null), getKmNumbersChangedRemarkOrNull(
                        translation, changedKmNumbers, locationTrackChanges.geometryChangeSummaries,
                    )
                )
            } else null,
            if (switchLinkChanges == null) null else compareChange({ switchLinkChanges.old != switchLinkChanges.new },
                null,
                null,
                { it },
                PropKey("linked-switches"),
                getSwitchLinksChangedRemark(
                    translation, switchLinkChanges
                )
            )
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
                changes.length.old, changes.length.new,
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
                getPointMovedRemarkOrNull(translation, changes.startPoint.old, changes.startPoint.new)
            ),
            compareChange(
                { !pointsAreSame(changes.endPoint.old, changes.endPoint.new) },
                changes.endPoint.old,
                changes.endPoint.new,
                ::formatLocation,
                PropKey("end-location"),
                getPointMovedRemarkOrNull(translation, changes.endPoint.old, changes.endPoint.new)
            ),
            if (changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    PropKey("geometry"), ChangeValue(null, null), publicationChangeRemark(
                        translation,
                        if (changedKmNumbers.size > 1) "changed-km-numbers" else "changed-km-number",
                        formatChangedKmNumbers(changedKmNumbers.toList())
                    )
                )
            } else null,
        )
    }

    fun diffKmPost(
        translation: Translation,
        changes: KmPostChanges,
        publicationTime: Instant,
        trackNumberCache: List<TrackNumberAndChangeTime>,
    ) = listOfNotNull(
        compareChangeValues(
            changes.trackNumberId,
            { tnIdFromChange -> trackNumberCache.findLast { tn -> tn.id == tnIdFromChange && tn.changeTime <= publicationTime }?.number },
            PropKey("track-number"),
        ),
        compareChangeValues(changes.kmNumber, { it }, PropKey("km-post")),
        compareChangeValues(changes.state, { it }, PropKey("state"), null, "layout-state"),
        compareChangeValues(
            changes.location,
            ::formatLocation,
            PropKey("location"),
            remark = getPointMovedRemarkOrNull(translation, changes.location.old, changes.location.new)
        ),
    )

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
        val oldSwitch = if (relatedJoints.any()) switchService.getOfficialAtMoment(changes.id, oldTimestamp) else null
        val jointLocationChanges = relatedJoints.flatMap { joint ->
            val oldLocation = oldSwitch?.joints?.find { it.number == joint.jointNumber }?.location
            val distance = if (oldLocation != null && !pointsAreSame(joint.point, oldLocation)) calculateDistance(
                listOf(joint.point, oldLocation), LAYOUT_SRID
            ) else 0.0
            val jointPropKeyParams =
                localizationParams("trackNumber" to trackNumberCache.findLast { it.id == joint.trackNumberId && it.changeTime <= newTimestamp }?.number?.value,
                    "switchType" to changes.type.new?.parts?.baseType?.let { switchBaseTypeToProp(translation, it) })
            val oldAddress = oldLocation?.let {
                geocodingContextGetter(
                    joint.trackNumberId, oldTimestamp
                )?.getAddress(it)?.first
            }

            val list = listOfNotNull(
                compareChange(
                    { distance > DISTANCE_CHANGE_THRESHOLD },
                    oldLocation,
                    joint.point,
                    ::formatLocation,
                    PropKey("switch-joint-location", jointPropKeyParams),
                    getPointMovedRemarkOrNull(translation, oldLocation, joint.point),
                    null
                ), compareChange({ oldAddress != joint.address },
                    oldAddress,
                    joint.address,
                    { it.toString() },
                    PropKey("switch-track-address", jointPropKeyParams),
                    getAddressMovedRemarkOrNull(translation, oldAddress, joint.address)
                )
            )
            list
        }.sortedBy { it.propKey.key }
        return listOfNotNull(
            compareChangeValues(changes.name, { it }, PropKey("switch")),
            compareChangeValues(changes.state, { it }, PropKey("state-category"), null, "layout-state-category"),
            compareChangeValues(changes.type, { it.typeName }, PropKey("switch-type")),
            compareChangeValues(
                changes.trapPoint, { it }, PropKey("trap-point"), enumLocalizationKey = "trap-point"
            ),
            compareChangeValues(changes.owner, { it }, PropKey("owner")),
            compareChange(
                { changes.locationTracks.any() },
                if (operation != Operation.CREATE) listOf(
                    translation.t("publication-details-table.not-calculated")
                ) else null,
                changes.locationTracks.map { it.name },
                { list -> list.joinToString(", ") { it } },
                PropKey("location-track-connectivity"),
            ),
            compareChangeValues(
                changes.measurementMethod, { it.name }, PropKey("measurement-method"), null, "measurement-method"
            ),
        ) + jointLocationChanges
    }

    private fun getOrPutGeocodingContext(
        caches: MutableMap<Instant, MutableMap<IntId<TrackLayoutTrackNumber>, Optional<GeocodingContext>>>,
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        timestamp: Instant,
    ) = caches.getOrPut(timestamp) { ConcurrentHashMap() }.getOrPut(trackNumberId) {
        Optional.ofNullable(
            geocodingService.getGeocodingContextAtMoment(
                trackNumberId, timestamp
            )
        )
    }.orElse(null)

    private fun validateGeocodingContext(
        cacheKey: GeocodingContextCacheKey?,
        localizationKey: String,
        trackNumber: TrackNumber,
    ) = cacheKey
        ?.let(geocodingCacheService::getGeocodingContextWithReasons)
        ?.let { context -> validateGeocodingContext(context, trackNumber) }
        ?: listOf(noGeocodingContext(localizationKey))

    private fun validateAddressPoints(
        trackNumber: TrackLayoutTrackNumber,
        contextKey: GeocodingContextCacheKey,
        track: LocationTrack,
        validationTargetLocalizationPrefix: String,
    ) = if (!track.exists || track.alignmentVersion == null) listOf()
    else {
        validateAddressPoints(trackNumber, track, validationTargetLocalizationPrefix) {
            geocodingService.getAddressPoints(contextKey, track.alignmentVersion)
        }
    }

    private fun getSwitchForValidation(
        switchId: IntId<TrackLayoutSwitch>,
        versions: ValidationVersions,
    ): TrackLayoutSwitch {
        val version =
            versions.findSwitch(switchId)?.validatedAssetVersion ?: switchDao.fetchVersionPair(switchId).let { (o, d) ->
                o ?: checkNotNull(d) {
                    "Fetched switch is neither official nor draft, switchId=$switchId"
                }
            }

        return switchDao.fetch(version)
    }

    private fun getSegmentSwitches(alignment: LayoutAlignment, versions: ValidationVersions): List<SegmentSwitch> {
        val segmentsBySwitch = alignment.segments
            .mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
            .groupBy({ (switchId, _) -> switchId }, { (_, segment) -> segment })
            .mapKeys { (switchId, _) -> getSwitchForValidation(switchId, versions) }

        return segmentsBySwitch.entries.map { (switch, segments) ->
            SegmentSwitch(
                switch = switch,
                switchStructure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
                segments = segments,
            )
        }
    }

    private fun getTopologicallyConnectedSwitches(
        locationTrack: LocationTrack,
        versions: ValidationVersions,
    ): List<TrackLayoutSwitch> {
        return listOfNotNull(
            locationTrack.topologyStartSwitch?.switchId,
            locationTrack.topologyEndSwitch?.switchId,
        ).map { switchId -> getSwitchForValidation(switchId, versions) }
    }

    private fun collectCacheKeys(versions: ValidationVersions): Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?> {
        val trackNumberIds = listOf(
            versions.trackNumbers.map { version -> version.officialId },
            versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.validatedAssetVersion).trackNumberId },
            versions.locationTracks.map { v -> locationTrackDao.fetch(v.validatedAssetVersion).trackNumberId },
            versions.referenceLines.map { v -> referenceLineDao.fetch(v.validatedAssetVersion).trackNumberId },
        ).flatten().toSet()
        return trackNumberIds.associateWith { tnId -> geocodingService.getGeocodingContextCacheKey(tnId, versions) }
    }

    private fun latestTrackNumberNamesAtMoment(
        trackNumberNames: List<TrackNumberAndChangeTime>,
        trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
        publicationTime: Instant,
    ) = trackNumberNames
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
            publicationDao.fetchPublicationTrackNumberChanges(publication.id, previousComparisonTime)
        val publicationKmPostChanges = publicationDao.fetchPublicationKmPostChanges(publication.id)
        val publicationReferenceLineChanges = publicationDao.fetchPublicationReferenceLineChanges(publication.id)
        val publicationSwitchChanges = publicationDao.fetchPublicationSwitchChanges(publication.id)

        val trackNumbers = publication.trackNumbers.map { tn ->
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.track-number-long")} ${tn.number}",
                trackNumbers = setOf(tn.number),
                changedKmNumbers = tn.changedKmNumbers,
                operation = tn.operation,
                publication = publication,
                propChanges = diffTrackNumber(
                    translation,
                    publicationTrackNumberChanges.getOrElse(tn.version.id) {
                        error("Track number changes not found: version=${tn.version}")
                    },
                    publication.publicationTime,
                    previousComparisonTime,
                    geocodingContextGetter,
                ),
            )
        }

        val referenceLines = publication.referenceLines.map { rl ->
            val tn = trackNumberNamesCache.findLast {
                it.id == rl.trackNumberId && it.changeTime <= publication.publicationTime
            }?.number

            mapToPublicationTableItem(
                name = "${translation.t("publication-table.reference-line")} $tn",
                trackNumbers = setOfNotNull(tn),
                changedKmNumbers = rl.changedKmNumbers,
                operation = rl.operation,
                publication = publication,
                propChanges = diffReferenceLine(
                    translation,
                    publicationReferenceLineChanges.getOrElse(rl.version.id) {
                        error("Reference line changes not found: version=${rl.version}")
                    },
                    publication.publicationTime,
                    previousComparisonTime,
                    rl.changedKmNumbers,
                    geocodingContextGetter,
                ),
            )
        }

        val locationTracks = publication.locationTracks.map { lt ->
            val trackNumber = trackNumberNamesCache.findLast {
                it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime
            }?.number
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.location-track")} ${lt.name}",
                trackNumbers = setOfNotNull(trackNumber),
                changedKmNumbers = lt.changedKmNumbers,
                operation = lt.operation,
                publication = publication,
                propChanges = diffLocationTrack(
                    translation,
                    publicationLocationTrackChanges.getOrElse(lt.version.id) {
                        error("Location track changes not found: version=${lt.version}")
                    },
                    switchLinkChanges[lt.version.id],
                    publication.publicationTime,
                    previousComparisonTime,
                    trackNumberNamesCache,
                    lt.changedKmNumbers,
                    geocodingContextGetter,
                ),
            )
        }

        val switches = publication.switches.map { s ->
            val tns =
                latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.switch")} ${s.name}",
                trackNumbers = tns,
                operation = s.operation,
                publication = publication,
                propChanges = diffSwitch(
                    translation,
                    publicationSwitchChanges.getOrElse(s.version.id) {
                        error("Switch changes not found: version=${s.version}")
                    },
                    publication.publicationTime,
                    previousComparisonTime,
                    s.operation,
                    trackNumberNamesCache,
                    geocodingContextGetter,
                ),
            )
        }

        val kmPosts = publication.kmPosts.map { kp ->
            val tn = trackNumberNamesCache.findLast {
                it.id == kp.trackNumberId && it.changeTime <= publication.publicationTime
            }?.number
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.km-post")} ${kp.kmNumber}",
                trackNumbers = setOfNotNull(tn),
                operation = kp.operation,
                publication = publication,
                propChanges = diffKmPost(
                    translation,
                    publicationKmPostChanges.getOrElse(kp.version.id) {
                        error("KM Post changes not found: version=${kp.version}")
                    },
                    publication.publicationTime,
                    trackNumberNamesCache,
                ),
            )
        }

        val calculatedLocationTracks = publication.indirectChanges.locationTracks.map { lt ->
            val tn = trackNumberNamesCache.findLast {
                it.id == lt.trackNumberId && it.changeTime <= publication.publicationTime
            }?.number
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.location-track")} ${lt.name}",
                trackNumbers = setOfNotNull(tn),
                changedKmNumbers = lt.changedKmNumbers,
                operation = Operation.CALCULATED,
                publication = publication,
                propChanges = diffLocationTrack(
                    translation,
                    publicationLocationTrackChanges.getOrElse(lt.version.id) {
                        error("Location track changes not found: version=${lt.version}")
                    },
                    switchLinkChanges[lt.version.id],
                    publication.publicationTime,
                    previousComparisonTime,
                    trackNumberNamesCache,
                    lt.changedKmNumbers,
                    geocodingContextGetter,
                ),
            )
        }

        val calculatedSwitches = publication.indirectChanges.switches.map { s ->
            val tns =
                latestTrackNumberNamesAtMoment(trackNumberNamesCache, s.trackNumberIds, publication.publicationTime)
            mapToPublicationTableItem(
                name = "${translation.t("publication-table.switch")} ${s.name}",
                trackNumbers = tns,
                operation = Operation.CALCULATED,
                publication = publication,
                propChanges = diffSwitch(
                    translation,
                    publicationSwitchChanges.getOrElse(s.version.id) {
                        error("Switch changes not found: version=${s.version}")
                    },
                    publication.publicationTime,
                    previousComparisonTime,
                    Operation.CALCULATED,
                    trackNumberNamesCache,
                    geocodingContextGetter,
                ),
            )
        }

        return (trackNumbers + referenceLines + locationTracks + switches + kmPosts + calculatedLocationTracks + calculatedSwitches)
    }

    private fun mapToPublicationTableItem(
        name: String,
        trackNumbers: Set<TrackNumber>,
        operation: Operation,
        publication: PublicationDetails,
        changedKmNumbers: Set<KmNumber>? = null,
        propChanges: List<PublicationChange<*>>,
    ) = PublicationTableItem(
        name = name,
        trackNumbers = trackNumbers.sorted(),
        changedKmNumbers = changedKmNumbers?.let { groupChangedKmNumbers(changedKmNumbers.toList()) } ?: emptyList(),
        operation = operation,
        publicationTime = publication.publicationTime,
        publicationUser = publication.publicationUser,
        message = publication.message ?: "",
        ratkoPushTime = if (publication.ratkoPushStatus == RatkoPushStatus.SUCCESSFUL) publication.ratkoPushTime else null,
        propChanges = propChanges,
    )

    private fun enrichDuplicateNameExceptionOrRethrow(exception: DataIntegrityViolationException): Nothing {
        val psqlException = exception.cause as? PSQLException ?: throw exception
        val constraint = psqlException.serverErrorMessage?.constraint
        val detail = psqlException.serverErrorMessage?.detail ?: throw exception

        when (constraint) {
            "switch_unique_official_name" -> maybeThrowDuplicateSwitchNameException(detail, exception)
            "track_number_number_draft_unique" -> maybeThrowDuplicateTrackNumberNumberException(detail, exception)
            "location_track_unique_official_name" -> maybeThrowDuplicateLocationTrackNameException(detail, exception)
        }
        throw exception
    }

    private val duplicateLocationTrackErrorRegex =
        Regex("""Key \(track_number_id, name\)=\((\d+), ([^)]+)\) conflicts with existing key""")
    private val duplicateTrackNumberErrorRegex = Regex("""Key \(number, draft\)=\(([^)]+), ([tf])\) already exists""")
    private val duplicateSwitchErrorRegex = Regex("""Key \(name\)=\(([^)]+)\) conflicts with existing key""")

    private fun maybeThrowDuplicateLocationTrackNameException(
        detail: String,
        exception: DataIntegrityViolationException,
    ) {
        duplicateLocationTrackErrorRegex.matchAt(detail, 0)?.let { match ->
            val trackIdString = match.groups[1]?.value
            val nameString = match.groups[2]?.value
            val trackId = IntId<TrackLayoutTrackNumber>(Integer.parseInt(trackIdString))
            if (trackIdString != null && nameString != null) {
                val trackNumberVersion = trackNumberDao.fetchOfficialVersion(trackId)
                if (trackNumberVersion != null) {
                    val trackNumber = trackNumberDao.fetch(trackNumberVersion)
                    throw DuplicateLocationTrackNameInPublicationException(
                        AlignmentName(nameString), trackNumber.number, exception
                    )
                }
            }
        }
    }

    private fun maybeThrowDuplicateTrackNumberNumberException(
        detail: String,
        exception: DataIntegrityViolationException,
    ) {
        duplicateTrackNumberErrorRegex.matchAt(detail, 0)?.let { match -> match.groups[1]?.value }?.let { name ->
            throw DuplicateNameInPublicationException(
                DuplicateNameInPublication.TRACK_NUMBER, name, exception
            )
        }
    }

    private fun maybeThrowDuplicateSwitchNameException(detail: String, exception: DataIntegrityViolationException) {
        duplicateSwitchErrorRegex.matchAt(detail, 0)?.let { match -> match.groups[1]?.value }?.let { name ->
            throw DuplicateNameInPublicationException(
                DuplicateNameInPublication.SWITCH, name, exception
            )
        }
    }
}
