package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingCacheService
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geography.calculateDistance
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.geometry.GeometrySwitch
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.RatkoPushDao
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.math.roundTo1Decimal
import fi.fta.geoviite.infra.math.roundTo3Decimals
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.switchLibrary.SwitchBaseType
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.switchLibrary.SwitchStructure
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.SortOrder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId
import java.util.*
import kotlin.math.abs


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
    private val geometryDao: GeometryDao,
    private val geocodingCacheService: GeocodingCacheService,
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
        )
    }

    fun validatePublishCandidates(candidates: PublishCandidates, request: PublishRequestIds): ValidatedPublishCandidates {
        logger.serviceCall("validatePublishCandidates", "candidates" to candidates, "request" to request)
        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidates.filter(request)),
            allChangesValidated = validateAsPublicationUnit(candidates),
        )
    }

    fun validateTrackNumberAndReferenceLine(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publishType: PublishType
    ): ValidatedAsset<TrackLayoutTrackNumber> {
        logger.serviceCall(
            "validateTrackNumberAndReferenceLine",
            "trackNumberId" to trackNumberId,
            "publishType" to publishType
        )

        val trackNumber = trackNumberService.getOrThrow(publishType, trackNumberId)
        val referenceLine = referenceLineService.getByTrackNumber(publishType, trackNumberId)

        val locationTracks =
            if (publishType == DRAFT)
                locationTrackDao.fetchVersions(DRAFT, false, trackNumberId).map(locationTrackDao::fetch)
            else emptyList()

        val kmPosts =
            if (publishType == DRAFT)
                kmPostDao.fetchVersions(DRAFT, false, trackNumberId).map(kmPostDao::fetch)
            else emptyList()

        val versions = toValidationVersions(
            trackNumbers = listOf(trackNumber),
            referenceLines = listOfNotNull(referenceLine),
            kmPosts = kmPosts,
            locationTracks = locationTracks,
        )

        val cacheKeys = collectCacheKeys(versions)

        val trackNumberValidation = validateTrackNumber(
            version = toValidationVersion(trackNumber),
            validationVersions = versions,
            cacheKeys = cacheKeys
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

    fun validateLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        publishType: PublishType,
    ): ValidatedAsset<LocationTrack> {
        logger.serviceCall(
            "validateLocationTrack",
            "locationTrackId" to locationTrackId,
            "publishType" to publishType
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

        return ValidatedAsset(
            validateLocationTrack(
                version = toValidationVersion(locationTrack),
                validationVersions = versions,
                cacheKeys = cacheKeys
            ),
            locationTrackId,
        )
    }

    fun validateSwitch(
        switchId: IntId<TrackLayoutSwitch>,
        publishType: PublishType,
    ): ValidatedAsset<TrackLayoutSwitch> {
        logger.serviceCall(
            "validateSwitch",
            "switchId" to switchId,
            "publishType" to publishType
        )

        val layoutSwitch = switchService.getOrThrow(publishType, switchId)
        val locationTracks = switchDao.findLocationTracksLinkedToSwitch(publishType, switchId).map { it.rowVersion }

        val previouslyLinkedTracks = if (publishType == DRAFT)
            switchDao.findLocationTracksLinkedToSwitch(OFFICIAL, switchId)
                .mapNotNull { lt -> locationTrackDao.fetchDraftVersion(lt.rowVersion.id) }
                .filterNot(locationTracks::contains)
        else emptyList()

        val versions = toValidationVersions(
            switches = listOf(layoutSwitch),
            locationTracks = (locationTracks + previouslyLinkedTracks).map(locationTrackDao::fetch)
        )

        val linkedTracks = publicationDao
            .fetchLinkedLocationTracks(listOf(switchId), DRAFT)
            .getOrDefault(switchId, setOf())
        return ValidatedAsset(
            validateSwitch(toValidationVersion(layoutSwitch), versions, linkedTracks),
            switchId,
        )
    }

    fun validateKmPost(
        kmPostId: IntId<TrackLayoutKmPost>,
        publishType: PublishType,
    ): ValidatedAsset<TrackLayoutKmPost> {
        logger.serviceCall(
            "validateKmPost",
            "kmPostId" to kmPostId,
            "publishType" to publishType
        )
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
                version = toValidationVersion(kmPost),
                validationVersions = versions,
                cacheKeys = cacheKeys
            ),
            kmPostId
        )
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return publicationDao.fetchChangeTime()
    }

    private fun validateAsPublicationUnit(candidates: PublishCandidates): PublishCandidates {
        val versions = candidates.getValidationVersions()
        val cacheKeys = collectCacheKeys(versions)
        // TODO: This does not respect the candidate versions
        val switchTrackLinks = publicationDao.fetchLinkedLocationTracks(
            candidates.switches.map { s -> s.id },
            DRAFT,
        )
        return PublishCandidates(
            trackNumbers = candidates.trackNumbers.map { candidate ->
                candidate.copy(errors = validateTrackNumber(candidate.getPublicationVersion(), versions, cacheKeys))
            },
            referenceLines = candidates.referenceLines.map { candidate ->
                candidate.copy(errors = validateReferenceLine(candidate.getPublicationVersion(), versions, cacheKeys))
            },
            locationTracks = candidates.locationTracks.map { candidate ->
                candidate.copy(errors = validateLocationTrack(candidate.getPublicationVersion(), versions, cacheKeys))
            },
            switches = candidates.switches.map { candidate ->
                candidate.copy(errors = validateSwitch(
                    candidate.getPublicationVersion(),
                    versions,
                    switchTrackLinks.getOrDefault(candidate.id, setOf()),
                ))
            },
            kmPosts = candidates.kmPosts.map { candidate ->
                candidate.copy(errors = validateKmPost(candidate.getPublicationVersion(), versions, cacheKeys))
            },
        )
    }

    fun validatePublishRequest(versions: ValidationVersions) {
        logger.serviceCall("validatePublishRequest", "versions" to versions)
        val cacheKeys = collectCacheKeys(versions)
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
            assertNoErrors(version, validateLocationTrack(version, versions, cacheKeys))
        }
        // TODO: This does not respect the validation versions
        val switchTrackLinks = publicationDao.fetchLinkedLocationTracks(versions.switches.map { v -> v.officialId }, DRAFT)
        versions.switches.forEach { version ->
            assertNoErrors(
                version,
                validateSwitch(version, versions, switchTrackLinks.getOrDefault(version.officialId, setOf())),
            )
        }
    }

    @Transactional(readOnly = true)
    fun getRevertRequestDependencies(publishRequestIds: PublishRequestIds): PublishRequestIds {
        logger.serviceCall("getRevertRequestDependencies", "publishRequestIds" to publishRequestIds)

        val newTrackNumbers = publishRequestIds.referenceLines
            .mapNotNull { id -> referenceLineService.get(DRAFT, id) }
            .map { rl -> rl.trackNumberId }
            .filter(trackNumberService::draftExists)

        val newReferenceLines = publishRequestIds.trackNumbers
            .mapNotNull { id -> referenceLineService.getByTrackNumber(DRAFT, id) }
            .filter { line -> line.draft != null }
            .map { line -> line.id as IntId }
        val allTrackNumbers = publishRequestIds.trackNumbers.toSet() + newTrackNumbers.toSet()
        val allReferenceLines = publishRequestIds.referenceLines.toSet() + newReferenceLines.toSet()
        val locationTracks = publishRequestIds.locationTracks.toSet()
        val switches = publishRequestIds.switches.toSet()
        val (allLocationTracks, allSwitches) = getRevertRequestLocationTrackAndSwitchDependenciesTransitively(
            locationTracks, locationTracks, switches, switches
        )

        return PublishRequestIds(
            trackNumbers = allTrackNumbers.toList(),
            referenceLines = allReferenceLines.toList(),
            locationTracks = allLocationTracks.toList(),
            switches = allSwitches.toList(),
            kmPosts = publishRequestIds.kmPosts
        )
    }

    private fun getRevertRequestLocationTrackAndSwitchDependenciesTransitively(
        allPreviouslyFoundLocationTracks: Set<IntId<LocationTrack>>,
        lastLevelLocationTracks: Set<IntId<LocationTrack>>,
        allPreviouslyFoundSwitches: Set<IntId<TrackLayoutSwitch>>,
        lastLevelSwitches: Set<IntId<TrackLayoutSwitch>>,
    ): Pair<Set<IntId<LocationTrack>>, Set<IntId<TrackLayoutSwitch>>> {
        val locationTracks = lastLevelLocationTracks.mapNotNull { id ->
            locationTrackService.getWithAlignment(DRAFT, id)
        }

        val newSwitches = locationTracks
            .flatMap { (locationTrack, alignment) -> getConnectedSwitchIds(locationTrack, alignment) }
            .subtract(allPreviouslyFoundSwitches)
            .filterTo(HashSet(), switchService::draftExists)

        val newLocationTracks = lastLevelSwitches
            .flatMap { switchId -> switchService.getSwitchJointConnections(DRAFT, switchId) }
            .flatMap { connections -> connections.accurateMatches }
            .map { match -> match.locationTrackId }
            .subtract(allPreviouslyFoundLocationTracks)
            .filterTo(HashSet(), locationTrackService::draftExists)

        return if (newSwitches.isNotEmpty() || newLocationTracks.isNotEmpty()) {
            getRevertRequestLocationTrackAndSwitchDependenciesTransitively(
                allPreviouslyFoundLocationTracks + newLocationTracks,
                newLocationTracks,
                allPreviouslyFoundSwitches + newSwitches,
                newSwitches
            )
        } else {
            (allPreviouslyFoundLocationTracks + newLocationTracks) to (allPreviouslyFoundSwitches + newSwitches)
        }
    }

    @Transactional
    fun revertPublishCandidates(toDelete: PublishRequestIds): PublishResult {
        logger.serviceCall("revertPublishCandidates", "toDelete" to toDelete)
        val locationTrackCount = toDelete.locationTracks.map { id -> locationTrackService.deleteDraft(id) }.size
        val referenceLineCount = toDelete.referenceLines.map { id -> referenceLineService.deleteDraft(id) }.size
        alignmentDao.deleteOrphanedAlignments()
        val switchCount = toDelete.switches.map { id -> switchService.deleteDraft(id) }.size
        val kmPostCount = toDelete.kmPosts.map { id -> kmPostService.deleteDraft(id) }.size
        val trackNumberCount = toDelete.trackNumbers.map { id ->
            geometryDao.removeReferencesToTrackNumber(id)
            trackNumberService.deleteDraft(id)
        }.size

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
            request.locationTracks.filter { locationTrackId -> locationTrackService.getDraft(locationTrackId).externalId == null }
                .forEach { locationTrackId -> updateExternalIdForLocationTrack(locationTrackId) }
            request.trackNumbers.filter { trackNumberId -> trackNumberService.getDraft(trackNumberId).externalId == null }
                .forEach { trackNumberId -> updateExternalIdForTrackNumber(trackNumberId) }
            request.switches.filter { switchId -> switchService.getDraft(switchId).externalId == null }
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
        logger.serviceCall("getPublicationVersions", "request" to request)
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
            s.getNewLocationTrackOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        locationTrackOid?.let { oid -> locationTrackService.updateExternalId(locationTrackId, Oid(oid.id)) }
    }

    private fun updateExternalIdForTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val routeNumberOid = ratkoClient?.let { s ->
            s.getNewRouteNumberOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        routeNumberOid?.let { oid -> trackNumberService.updateExternalId(trackNumberId, Oid(oid.id)) }
    }

    private fun updateExternalIdForSwitch(switchId: IntId<TrackLayoutSwitch>) {
        val switchOid = ratkoClient?.let { s ->
            s.getNewSwitchOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        switchOid?.let { oid -> switchService.updateExternalIdForSwitch(switchId, Oid(oid.id)) }
    }

    private inline fun <reified T> assertNoErrors(
        version: ValidationVersion<T>, errors: List<PublishValidationError>,
    ) {
        val severeErrors = errors.filter { error -> error.type == PublishValidationErrorType.ERROR }
        if (severeErrors.isNotEmpty()) {
            logger.warn("Validation errors in published ${T::class.simpleName}: item=$version errors=$severeErrors")
            throw PublicationFailureException(
                message = "Cannot publish ${T::class.simpleName} due to validation errors: $version",
                localizedMessageKey = "validation-failed",
            )
        }
    }

    fun getCalculatedChanges(versions: ValidationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChanges(versions)

    @Transactional
    fun publishChanges(versions: ValidationVersions, calculatedChanges: CalculatedChanges, message: String): PublishResult {
        logger.serviceCall(
            "publishChanges",
            "versions" to versions,
            "calculatedChanges" to calculatedChanges,
            "message" to message
        )

        val trackNumbers = versions.trackNumbers.map(trackNumberService::publish).map { r -> r.rowVersion }
        val kmPosts = versions.kmPosts.map(kmPostService::publish).map { r -> r.rowVersion }
        val switches = versions.switches.map(switchService::publish).map { r -> r.rowVersion }
        val referenceLines = versions.referenceLines.map(referenceLineService::publish).map { r -> r.rowVersion }
        val locationTracks = versions.locationTracks.map(locationTrackService::publish).map { r -> r.rowVersion }

        val publishId = publicationDao.createPublication(message)
        publicationDao.insertCalculatedChanges(publishId, calculatedChanges)

        return PublishResult(
            publishId = publishId,
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
            validateGeocodingContext(cacheKeys[version.officialId], VALIDATION_TRACK_NUMBER)
        } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
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
            validateGeocodingContext(cacheKeys[kmPost.trackNumberId], VALIDATION_KM_POST)
        } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    private fun validateSwitch(
        version: ValidationVersion<TrackLayoutSwitch>,
        validationVersions: ValidationVersions,
        linkedTracks: Set<RowVersion<LocationTrack>>,
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
        val structureErrors = validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndAlignments)
        return fieldErrors + referenceErrors + structureErrors
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
            val contextErrors = validateGeocodingContext(contextKey, VALIDATION_REFERENCE_LINE)
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
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.validatedAssetVersion)
        val trackNumber = getTrackNumber(locationTrack.trackNumberId, validationVersions)
        val duplicateOfLocationTrack = locationTrack.duplicateOf?.let { duplicateId ->
            getLocationTrack(duplicateId, validationVersions)
        }
        val fieldErrors = validateDraftLocationTrackFields(locationTrack)
        val referenceErrors = validateLocationTrackReference(
            locationTrack,
            trackNumber,
            validationVersions.trackNumbers.map { it.officialId },
        )
        val switchErrors = validateSegmentSwitchReferences(
            locationTrack,
            getSegmentSwitches(alignment, validationVersions),
            validationVersions.switches.map { it.officialId },
        )
        val duplicateErrors = validateDuplicateOfState(
            locationTrack,
            duplicateOfLocationTrack,
            validationVersions.locationTracks.map { it.officialId },
        )
        val alignmentErrors = if (locationTrack.exists) validateLocationTrackAlignment(alignment)
        else listOf()
        val geocodingErrors = if (locationTrack.exists && trackNumber != null) {
            cacheKeys[locationTrack.trackNumberId]?.let { key ->
                validateAddressPoints(trackNumber, key, locationTrack, VALIDATION_LOCATION_TRACK)
            } ?: listOf(noGeocodingContext(VALIDATION_LOCATION_TRACK))
        } else listOf()
        return fieldErrors + referenceErrors + switchErrors + duplicateErrors + alignmentErrors + geocodingErrors
    }

    private fun getLinkedTrackDraftsNotIncludedInPublication(
        versions: ValidationVersions,
        linkedTracks: List<LocationTrack>,
    ): List<LocationTrack> = linkedTracks.filter { track ->
        track.draft != null && !versions.containsLocationTrack(track.id as IntId)
    }

    private fun getTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ): TrackLayoutTrackNumber? {
        val version = versions.findTrackNumber(id)?.validatedAssetVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }

    private fun getLocationTrack(id: IntId<LocationTrack>, versions: ValidationVersions): LocationTrack? {
        val version =
            versions.findLocationTrack(id)?.validatedAssetVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let(locationTrackDao::fetch)
    }

    private fun getReferenceLineByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: ValidationVersions,
    ) = versions.referenceLines
        .map { v -> referenceLineDao.fetch(v.validatedAssetVersion) }
        .find { line -> line.trackNumberId == trackNumberId }
        ?: referenceLineDao.fetchVersion(OFFICIAL, trackNumberId)?.let(referenceLineDao::fetch)

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

    private fun <T> combineVersions(
        officials: List<RowVersion<T>>,
        validations: List<ValidationVersion<T>>,
    ): Collection<RowVersion<T>> {
        val officialVersions = officials.filterNot { officialId -> validations.any { v -> v.officialId == officialId } }
        val validationVersions = validations.map { it.validatedAssetVersion }

        return (officialVersions + validationVersions).distinct()
    }

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
            )
        )
    }

    @Transactional(readOnly = true)
    fun getPublicationDetailsAsTableItems(id: IntId<Publication>): List<PublicationTableItem> {
        logger.serviceCall("getPublicationDetailsAsTableRows", "id" to id)
        return getPublicationDetails(id).let(::mapToPublicationTableItems)
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
    ): List<PublicationTableItem> {
        logger.serviceCall(
            "fetchPublicationDetails",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
        )

        return fetchPublicationDetailsBetweenInstants(from, to)
            .flatMap(this::mapToPublicationTableItems)
            .let { publications ->
                if (sortBy == null) publications
                else publications.sortedWith(getComparator(sortBy, order))
            }
    }

    @Transactional(readOnly = true)
    fun fetchPublicationsAsCsv(
        from: Instant? = null,
        to: Instant? = null,
        sortBy: PublicationTableColumn? = null,
        order: SortOrder? = null,
        timeZone: ZoneId? = null,
    ): String {
        logger.serviceCall(
            "fetchPublicationsAsCsv",
            "from" to from,
            "to" to to,
            "sortBy" to sortBy,
            "order" to order,
            "timeZone" to timeZone
        )

        val orderedPublishedItems = fetchPublicationDetails(
            from = from,
            to = to,
            sortBy = sortBy,
            order = order
        )

        return asCsvFile(orderedPublishedItems, timeZone ?: ZoneId.of("UTC"))
    }

    fun diffTrackNumber(newTrackNumber: TrackLayoutTrackNumber, oldTrackNumber: TrackLayoutTrackNumber?) =
        listOf(
            if (newTrackNumber.number != oldTrackNumber?.number) {
                PublicationChange("track-number", oldTrackNumber?.number?.value, newTrackNumber.number.value, null)
            } else null,
            if (newTrackNumber.state != oldTrackNumber?.state) {
                PublicationChange("state", oldTrackNumber?.state?.name, newTrackNumber.state.name, null, "layout-state")
            } else null,
            if (newTrackNumber.description != oldTrackNumber?.description) {
                PublicationChange(
                    "description",
                    oldTrackNumber?.description?.toString(),
                    newTrackNumber.description.toString(),
                    null
                )
            } else null
            // TODO Add start and end address diffing when figuring out which reference line version to use
        ).filterNotNull()

    fun diffLocationTrack(newLocationTrack: LocationTrack, oldLocationTrack: LocationTrack?, changedKmNumbers: Set<KmNumber>?): List<PublicationChange> {
        val oldStartAndEnd = oldLocationTrack?.let { oldLt ->
            geocodingService
                .getGeocodingContext(OFFICIAL, oldLt.trackNumberId)
                ?.let { context ->
                    oldLt.alignmentVersion
                        ?.let(alignmentDao::fetch)
                        ?.let(context::getStartAndEnd)
                }
        }
        val newStartAndEnd = newLocationTrack.alignmentVersion
            ?.let(alignmentDao::fetch)
            ?.let { newLt ->
                geocodingService
                    .getGeocodingContext(OFFICIAL, newLocationTrack.trackNumberId)
                    ?.getStartAndEnd(newLt)
            }

        return listOf(
            if (newLocationTrack.trackNumberId != oldLocationTrack?.trackNumberId) {
                PublicationChange(
                    "track-number",
                    oldLocationTrack?.let { trackNumberService.get(OFFICIAL, oldLocationTrack.trackNumberId) }?.number?.value,
                    trackNumberService.get(OFFICIAL, newLocationTrack.trackNumberId)?.number?.value,
                    null
                )
            } else null,
            if (newLocationTrack.name != oldLocationTrack?.name) {
                PublicationChange("location-track", oldLocationTrack?.name?.toString(), newLocationTrack.name.toString(), null)
            } else null,
            if (newLocationTrack.state != oldLocationTrack?.state) {
                PublicationChange(
                    "state",
                    oldLocationTrack?.state?.name,
                    newLocationTrack.state.name,
                    null,
                    "layout-state",
                )
            } else null,
            if (newLocationTrack.type != oldLocationTrack?.type) {
                PublicationChange(
                    "location-track-type",
                    oldLocationTrack?.type?.name,
                    newLocationTrack.type.name,
                    null,
                    "location-track-type",
                )
            } else null,
            if (newLocationTrack.description != oldLocationTrack?.description) {
                PublicationChange(
                    "description",
                    oldLocationTrack?.description?.toString(),
                    newLocationTrack.description.toString(),
                    null
                )
            } else null,
            if (newLocationTrack.duplicateOf != oldLocationTrack?.duplicateOf) {
                PublicationChange(
                    "duplicate-of",
                    oldLocationTrack?.duplicateOf?.let { locationTrackService.get(OFFICIAL, it) }?.name?.toString(),
                    newLocationTrack.duplicateOf?.let { locationTrackService.get(OFFICIAL, it) }?.name?.toString(),
                    null
                )
            } else null,
            if (oldLocationTrack != null && abs(newLocationTrack.length - oldLocationTrack.length) >= 0.1) {
                PublicationChange(
                    "length",
                    oldLocationTrack.length.let(::roundTo1Decimal),
                    newLocationTrack.length.let(::roundTo1Decimal),
                    oldLocationTrack.let {
                        lengthChangedRemark(oldLocationTrack.length, newLocationTrack.length)
                    }
                )
            } else null,
            if (oldStartAndEnd?.start?.address != newStartAndEnd?.start?.address) {
                PublicationChange(
                    "start-address",
                    oldStartAndEnd?.start?.address?.toString(),
                    newStartAndEnd?.start?.address?.toString(),
                    if (newStartAndEnd?.start?.point != null && oldStartAndEnd?.start?.point != null) {
                        pointMovedRemark(
                            oldStartAndEnd.start.point,
                            newStartAndEnd.start.point,
                            oldLocationTrack.trackNumberId,
                            newLocationTrack.trackNumberId
                        )
                    } else null
                )
            } else null,
            if (oldStartAndEnd?.end?.address != newStartAndEnd?.end?.address) {
                PublicationChange(
                    "end-address",
                    oldStartAndEnd?.end?.address?.toString(),
                    newStartAndEnd?.end?.address?.toString(),
                    if (newStartAndEnd?.end?.point != null && oldStartAndEnd?.end?.point != null) {
                        pointMovedRemark(
                            oldStartAndEnd.end.point,
                            newStartAndEnd.end.point,
                            oldLocationTrack.trackNumberId,
                            newLocationTrack.trackNumberId
                        )
                    } else null
                )
            } else null,
            if (changedKmNumbers != null && changedKmNumbers.isNotEmpty()) {
                PublicationChange(
                    "geometry",
                    null,
                    null,
                    PublicationChangeRemark(
                        "changed-km-numbers",
                        formatChangedKmNumbers(changedKmNumbers.toList())
                    )
                )
            } else null,
            // TODO owner
        ).filterNotNull()
    }

    // TODO Add tests
    fun diffReferenceLine(newReferenceLine: ReferenceLine, oldReferenceLine: ReferenceLine?, changedKmNumbers: Set<KmNumber>?): List<PublicationChange> {
        val oldStartAndEnd = oldReferenceLine?.let { oldRl ->
            geocodingService
                .getGeocodingContext(OFFICIAL, oldRl.trackNumberId)
                ?.let { context ->
                    oldRl.alignmentVersion
                        ?.let(alignmentDao::fetch)
                        ?.let(context::getStartAndEnd)
                }
        }
        val newStartAndEnd = newReferenceLine.alignmentVersion
            ?.let(alignmentDao::fetch)
            ?.let { newRl ->
                geocodingService
                    .getGeocodingContext(OFFICIAL, newReferenceLine.trackNumberId)
                    ?.getStartAndEnd(newRl)
            }

        return listOf(
            if (newReferenceLine.length != oldReferenceLine?.length) {
                PublicationChange(
                    "length",
                    oldReferenceLine?.length?.toString(),
                    newReferenceLine.length.toString(),
                    oldReferenceLine
                        ?.let { lengthChangedRemark(oldReferenceLine.length, newReferenceLine.length) }
                )
            } else null,
            if (oldStartAndEnd?.start?.address != newStartAndEnd?.start?.address) {
                PublicationChange(
                    "start-address",
                    oldStartAndEnd?.start?.address?.toString(),
                    newStartAndEnd?.start?.address?.toString(),
                    if (newStartAndEnd?.start?.point != null && oldStartAndEnd?.start?.point != null) {
                        pointMovedRemark(
                            oldStartAndEnd.start.point,
                            newStartAndEnd.start.point,
                            oldReferenceLine.trackNumberId,
                            newReferenceLine.trackNumberId
                        )
                    } else null
                )
            } else null,
            if (oldStartAndEnd?.end?.address != newStartAndEnd?.end?.address) {
                PublicationChange(
                    "end-address",
                    oldStartAndEnd?.end?.address?.toString(),
                    newStartAndEnd?.end?.address?.toString(),
                    if (newStartAndEnd?.end?.point != null && oldStartAndEnd?.end?.point != null) {
                        pointMovedRemark(
                            oldStartAndEnd.end.point,
                            newStartAndEnd.end.point,
                            oldReferenceLine.trackNumberId,
                            newReferenceLine.trackNumberId
                        )
                    } else null
                )
            } else null,
            if (changedKmNumbers != null) {
                PublicationChange(
                    "geometry",
                    null,
                    null,
                    PublicationChangeRemark(
                        "changed-km-numbers",
                        formatChangedKmNumbers(changedKmNumbers.toList())
                    )
                )
            } else null,
        ).filterNotNull()
    }

    fun diffKmPost(newKmPost: TrackLayoutKmPost, oldKmPost: TrackLayoutKmPost?) =
        listOf(
            if (newKmPost.trackNumberId != oldKmPost?.trackNumberId) {
                PublicationChange(
                    "track-number",
                    oldKmPost?.trackNumberId?.let { trackNumberService.getOrThrow(OFFICIAL, oldKmPost.trackNumberId).number.value },
                    newKmPost.trackNumberId?.let { trackNumberService.getOrThrow(OFFICIAL, newKmPost.trackNumberId).number.value },
                    null
                )
            } else null,
            if (newKmPost.kmNumber != oldKmPost?.kmNumber) {
                PublicationChange("km-post", oldKmPost?.kmNumber?.toString(), newKmPost.kmNumber.toString(), null)
            } else null,
            if (newKmPost.state != oldKmPost?.state) {
                PublicationChange("state", oldKmPost?.state?.name, newKmPost.state.name, null, "layout-state")
            } else null,
            if (newKmPost.location != oldKmPost?.location) {
                PublicationChange("address", oldKmPost?.location?.toString(), newKmPost.location.toString(), null)
            } else null,
        ).filterNotNull()

    fun diffSwitch(newSwitch: TrackLayoutSwitch, oldSwitch: TrackLayoutSwitch?): List<PublicationChange> {
        val oldPresentationJointLocation = oldSwitch
            ?.let(switchService::getPresentationJoint)
            ?.location
        val newPresentationJointLocation = switchService
            .getPresentationJoint(newSwitch)
            ?.location
        val distance = oldPresentationJointLocation?.let { oldLocation ->
            newPresentationJointLocation?.let { newLocation ->
                calculateDistance(listOf(oldLocation, newLocation), LAYOUT_SRID)
            }
        }
        val oldMeasurementMethod = oldSwitch
            ?.let { sw -> sw.sourceId
                ?.let { geometrySwitchId ->
                    geometryDao.getMeasurementMethodForSwitch(geometrySwitchId as IntId<GeometrySwitch>)
                }
            }
        val newMeasurementMethod = newSwitch.sourceId
            ?.let { geometrySwitchId ->
                geometryDao.getMeasurementMethodForSwitch(geometrySwitchId as IntId<GeometrySwitch>)
            }

        return listOf(
            if (newSwitch.name != oldSwitch?.name) {
                PublicationChange("switch", oldSwitch?.name?.toString(), newSwitch.name.toString(), null)
            } else null,
            if (newSwitch.stateCategory != oldSwitch?.stateCategory) {
                PublicationChange(
                    "state-category",
                    oldSwitch?.stateCategory?.name,
                    newSwitch.stateCategory.name,
                    null,
                    "layout-state-category"
                )
            } else null,
            if (newSwitch.switchStructureId != oldSwitch?.switchStructureId) {
                val structureOfNew = newSwitch.switchStructureId.let(switchLibraryService::getSwitchStructure)
                val structureOfOld = oldSwitch?.switchStructureId?.let(switchLibraryService::getSwitchStructure)
                PublicationChange("switch-type", structureOfOld?.type?.typeName, structureOfNew.type.typeName, null)
            } else null,
            if (newSwitch.trapPoint != oldSwitch?.trapPoint) {
                PublicationChange("trap-point", oldSwitch?.trapPoint, newSwitch.trapPoint, null)
            } else null,
            if (newSwitch.ownerId != oldSwitch?.ownerId) {
                PublicationChange(
                    "owner",
                    oldSwitch?.ownerId?.let { ownerId -> switchLibraryService.getSwitchOwner(ownerId)?.name?.toString() },
                    newSwitch.ownerId?.let { ownerId -> switchLibraryService.getSwitchOwner(ownerId)?.name?.toString() },
                    null
                )
            } else null,
            if ((oldPresentationJointLocation != null || newPresentationJointLocation != null) && (oldPresentationJointLocation == null || newPresentationJointLocation == null || (distance
                    ?: 0.0) > 0.001)
            ) {
                PublicationChange(
                    "location-" + switchBaseTypeToProp(switchLibraryService.getSwitchStructure(newSwitch.switchStructureId).baseType),
                    oldPresentationJointLocation?.let {
                        "${roundTo3Decimals(oldPresentationJointLocation.x)} E, ${
                            roundTo3Decimals(
                                oldPresentationJointLocation.y
                            )
                        } N"
                    },
                    newPresentationJointLocation?.let {
                        "${roundTo3Decimals(newPresentationJointLocation.x)} E, ${
                            roundTo3Decimals(
                                newPresentationJointLocation.y
                            )
                        } N"
                    },
                    if (distance != null && distance > 0.001) {
                        PublicationChangeRemark("moved-x-meters", if (distance >= 0.1) "${roundTo1Decimal(distance)}" else "<${roundTo1Decimal(0.1)}}")
                    } else null
                )
            } else null,
            if (oldMeasurementMethod != newMeasurementMethod) {
                PublicationChange(
                    "measurement-method",
                    oldMeasurementMethod?.name,
                    newMeasurementMethod?.name,
                    null,
                    "measurement-method")
            } else null
            // TODO Everything to do with track numbers, location tracks, track addresses and such
        ).filterNotNull()
    }

    private fun lengthChangedRemark(
        oldLength: Double,
        newLength: Double,
    ): PublicationChangeRemark? {
        val lengthDelta = abs(newLength - oldLength)
        return if (lengthDelta >= 0.001) {
            PublicationChangeRemark(
                "changed-x-meters",
                if (lengthDelta >= 0.1) "${roundTo1Decimal(lengthDelta)}" else "<${roundTo1Decimal(0.1)}"
            )
        } else null
    }

    private fun pointMovedRemark(
        oldPoint: LayoutPoint,
        newPoint: LayoutPoint,
        oldTrackNumberId: IntId<TrackLayoutTrackNumber>,
        newTrackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublicationChangeRemark? {
        val oldPointM = geocodingService.getGeocodingContext(
            OFFICIAL,
            oldTrackNumberId
        )?.getM(oldPoint)?.first
        val newPointM = geocodingService.getGeocodingContext(
            OFFICIAL,
            newTrackNumberId
        )?.getM(newPoint)?.first
        val mDelta = if (oldPointM != null && newPointM != null) abs(newPointM - oldPointM) else null
        return if (mDelta != null && mDelta > 0.0005) {
            PublicationChangeRemark(
                "moved-x-meters",
                if (mDelta >= 0.1) "${roundTo1Decimal(mDelta)}" else "<${roundTo1Decimal(0.1)}"
            )
        } else null
    }

    private fun validateGeocodingContext(cacheKey: GeocodingContextCacheKey?, localizationKey: String) =
        cacheKey?.let(geocodingCacheService::getGeocodingContext)?.let { context -> validateGeocodingContext(context) }
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

    private fun getSegmentSwitches(alignment: LayoutAlignment, versions: ValidationVersions): List<SegmentSwitch> {
        val segmentsBySwitch = alignment.segments
            .mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
            .groupBy({ (switchId, _) -> switchId }, { (_, segment) -> segment })
            .mapKeys { (switchId, _) ->
                val version = versions.findSwitch(switchId)?.validatedAssetVersion
                    ?: switchDao.fetchVersionPair(switchId).let { (o, d) ->
                        o ?: checkNotNull(d) {
                            "Fetched switch is neither official nor draft, switchId=$switchId"
                        }
                    }

                switchDao.fetch(version)
            }

        return segmentsBySwitch.entries.map { (switch, segments) ->
            SegmentSwitch(
                switch = switch,
                switchStructure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
                segments = segments,
            )
        }
    }

    private fun collectCacheKeys(versions: ValidationVersions): Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?> {
        val trackNumberIds = (
                versions.trackNumbers.map { version -> version.officialId } +
                        versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.validatedAssetVersion).trackNumberId } +
                        versions.locationTracks.map { v -> locationTrackDao.fetch(v.validatedAssetVersion).trackNumberId } +
                        versions.referenceLines.map { v -> referenceLineDao.fetch(v.validatedAssetVersion).trackNumberId }
                ).toSet()
        return trackNumberIds.associateWith { tnId -> geocodingService.getGeocodingContextCacheKey(tnId, versions) }
    }

    private fun mapToPublicationTableItems(publication: PublicationDetails): List<PublicationTableItem> {
        val trackNumbers = publication.trackNumbers.map { tn ->
            mapToPublicationTableItem(
                name = "${getTranslation("track-number")} ${tn.number}",
                trackNumberIds = setOf(tn.version.id),
                changedKmNumbers = tn.changedKmNumbers,
                operation = tn.operation,
                publication = publication,
                propChanges = diffTrackNumber(trackNumberService.get(tn.version), trackNumberDao.fetchPreviousOfficialVersion(tn.version)?.let(trackNumberService::get)),
            )
        }

        val referenceLines = publication.referenceLines.map { rl ->
            val trackNumber = getTrackNumberAtMomentOrThrow(rl.trackNumberId, publication.publicationTime)

            mapToPublicationTableItem(
                name = "${getTranslation("reference-line")} ${trackNumber.number}",
                trackNumberIds = setOf(rl.trackNumberId),
                changedKmNumbers = rl.changedKmNumbers,
                operation = rl.operation,
                publication = publication,
                propChanges = diffReferenceLine(referenceLineService.get(rl.version), referenceLineDao.fetchPreviousOfficialVersion(rl.version)?.let(referenceLineService::get), rl.changedKmNumbers),
            )
        }

        val locationTracks = publication.locationTracks.map { lt ->
            mapToPublicationTableItem(
                name = "${getTranslation("location-track")} ${lt.name}",
                trackNumberIds = setOf(lt.trackNumberId),
                changedKmNumbers = lt.changedKmNumbers,
                operation = lt.operation,
                publication = publication,
                propChanges = diffLocationTrack(locationTrackService.get(lt.version), locationTrackDao.fetchPreviousOfficialVersion(lt.version)?.let(locationTrackService::get), lt.changedKmNumbers),
            )
        }

        val switches = publication.switches.map { s ->
            mapToPublicationTableItem(
                name = "${getTranslation("switch")} ${s.name}",
                trackNumberIds = s.trackNumberIds,
                operation = s.operation,
                publication = publication,
                propChanges = diffSwitch(switchService.get(s.version), switchDao.fetchPreviousOfficialVersion(s.version)?.let(switchService::get)),
            )
        }

        val kmPosts = publication.kmPosts.map { kp ->
            mapToPublicationTableItem(
                name = "${getTranslation("km-post")} ${kp.kmNumber}",
                trackNumberIds = setOf(kp.trackNumberId),
                operation = kp.operation,
                publication = publication,
                propChanges = diffKmPost(kmPostService.get(kp.version), kmPostDao.fetchPreviousOfficialVersion(kp.version)?.let(kmPostService::get)),
            )
        }

        val calculatedLocationTracks = publication.indirectChanges.locationTracks.map { lt ->
            mapToPublicationTableItem(
                name = "${getTranslation("location-track")} ${lt.name}",
                trackNumberIds = setOf(lt.trackNumberId),
                changedKmNumbers = lt.changedKmNumbers,
                operation = lt.operation,
                publication = publication,
                isCalculatedChange = true,
                propChanges = diffLocationTrack(locationTrackService.get(lt.version), locationTrackDao.fetchPreviousOfficialVersion(lt.version)?.let(locationTrackService::get), lt.changedKmNumbers),
            )
        }

        val calculatedSwitches = publication.indirectChanges.switches.map { s ->
            mapToPublicationTableItem(
                name = "${getTranslation("switch")} ${s.name}",
                trackNumberIds = s.trackNumberIds,
                operation = s.operation,
                publication = publication,
                isCalculatedChange = true,
                propChanges = diffSwitch(switchService.get(s.version), switchDao.fetchPreviousOfficialVersion(s.version)?.let(switchService::get)),
            )
        }

        return (trackNumbers
                + referenceLines
                + locationTracks
                + switches
                + kmPosts
                + calculatedLocationTracks
                + calculatedSwitches)
    }

    private fun mapToPublicationTableItem(
        name: String,
        trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
        operation: Operation,
        publication: PublicationDetails,
        changedKmNumbers: Set<KmNumber>? = null,
        isCalculatedChange: Boolean = false,
        propChanges: List<PublicationChange>,
    ) = PublicationTableItem(
        name = name,
        trackNumbers = trackNumberIds.map { id ->
            getTrackNumberAtMomentOrThrow(id, publication.publicationTime).number
        }.sorted(),
        changedKmNumbers = changedKmNumbers?.let { formatChangedKmNumbers(changedKmNumbers.toList()) },
        operation = operation,
        publicationTime = publication.publicationTime,
        publicationUser = publication.publicationUser,
        message = formatMessage(publication.message, isCalculatedChange),
        ratkoPushTime = if (publication.ratkoPushStatus == RatkoPushStatus.SUCCESSFUL) publication.ratkoPushTime else null,
        propChanges = propChanges,
    )

    private fun getTrackNumberAtMomentOrThrow(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        moment: Instant,
    ): TrackLayoutTrackNumber {
        return checkNotNull(
            trackNumberService.getOfficialAtMoment(
                trackNumberId,
                moment
            )
        ) { "Track number with official id $trackNumberId does not exist at moment $moment" }
    }
}
