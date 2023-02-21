package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geometry.GeometryDao
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.RatkoPushDao
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
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


@Service
class PublicationService @Autowired constructor(
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val geocodingDao: GeocodingDao,
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

    fun validateOfficialAssets(request: PublishRequestIds): ValidatedAssets {
        logger.serviceCall("validateOfficialAssets", "request" to request)
        val requestAndDependencies = getRevertRequestDependencies(request)
        val versions = PublicationVersions(
            trackNumbers = requestAndDependencies.trackNumbers.map { PublicationVersion(
                officialId = it,
                validatedAssetVersion = trackNumberDao.fetchOfficialVersionOrThrow(it)
            ) },
            referenceLines = requestAndDependencies.referenceLines.map { PublicationVersion(
                officialId = it,
                validatedAssetVersion = referenceLineDao.fetchOfficialVersionOrThrow(it)
            ) },
            locationTracks = requestAndDependencies.locationTracks.map { PublicationVersion(
                officialId = it,
                validatedAssetVersion = locationTrackDao.fetchOfficialVersionOrThrow(it)
            ) },
            switches = requestAndDependencies.switches.map { PublicationVersion(
                officialId = it,
                validatedAssetVersion = switchDao.fetchOfficialVersionOrThrow(it)
            ) },
            kmPosts = requestAndDependencies.kmPosts.map { PublicationVersion(
                officialId = it,
                validatedAssetVersion = kmPostDao.fetchOfficialVersionOrThrow(it)
            ) }
        )
        val cacheKeys = collectCacheKeys(versions)

        return ValidatedAssets(
            trackNumbers = versions.trackNumbers.map { version ->
                ValidatedAsset(
                    id = version.officialId,
                    errors = validateTrackNumber(version, versions, cacheKeys)
                )
            },
            referenceLines = versions.referenceLines.map { version ->
                ValidatedAsset(
                    id = version.officialId,
                    errors = validateReferenceLine(version, versions, cacheKeys)
                )
            },
            locationTracks = versions.locationTracks.map { version ->
                ValidatedAsset(
                    id = version.officialId,
                    errors = validateLocationTrack(version, versions, cacheKeys)
                )
            },
            switches = versions.switches.map { version ->
                ValidatedAsset(
                    id = version.officialId,
                    errors = validateSwitch(version, versions)
                )
            },
            kmPosts = versions.kmPosts.map { version ->
                ValidatedAsset(
                    id = version.officialId,
                    errors = validateKmPost(version, versions, cacheKeys)
                )
            }
        )
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return publicationDao.fetchChangeTime()
    }

    private fun validateAsPublicationUnit(candidates: PublishCandidates): PublishCandidates {
        val versions = candidates.getPublicationVersions()
        val cacheKeys = collectCacheKeys(versions)
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
                candidate.copy(errors = validateSwitch(candidate.getPublicationVersion(), versions))
            },
            kmPosts = candidates.kmPosts.map { candidate ->
                candidate.copy(errors = validateKmPost(candidate.getPublicationVersion(), versions, cacheKeys))
            },
        )
    }

    fun validatePublishRequest(versions: PublicationVersions) {
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
        versions.switches.forEach { version ->
            assertNoErrors(version, validateSwitch(version, versions))
        }
    }

    @Transactional(readOnly = true)
    fun getRevertRequestDependencies(publishRequestIds: PublishRequestIds): PublishRequestIds {
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
    fun getPublicationVersions(request: PublishRequestIds): PublicationVersions {
        logger.serviceCall("getPublicationVersions", "request" to request)
        return PublicationVersions(
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
        version: PublicationVersion<T>, errors: List<PublishValidationError>,
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

    fun getCalculatedChanges(versions: PublicationVersions): CalculatedChanges =
        calculatedChangesService.getCalculatedChangesInDraft(versions)

    @Transactional
    fun publishChanges(versions: PublicationVersions, calculatedChanges: CalculatedChanges, message: String): PublishResult {
        logger.serviceCall("publishChanges", "versions" to versions)

        val trackNumbers = versions.trackNumbers.map(trackNumberService::publish).map { r -> r.rowVersion }
        val kmPosts = versions.kmPosts.map(kmPostService::publish).map { r -> r.rowVersion }
        val switches = versions.switches.map(switchService::publish).map { r -> r.rowVersion }
        val referenceLines = versions.referenceLines.map(referenceLineService::publish).map { r -> r.rowVersion }
        val locationTracks = versions.locationTracks.map(locationTrackService::publish).map { r -> r.rowVersion }

        val publishId =
            publicationDao.createPublication(trackNumbers, referenceLines, locationTracks, switches, kmPosts, message)

        publicationDao.savePublishCalculatedChanges(publishId, calculatedChanges)

        return PublishResult(
            publishId = publishId,
            trackNumbers = trackNumbers.size,
            referenceLines = referenceLines.size,
            locationTracks = locationTracks.size,
            switches = switches.size,
            kmPosts = kmPosts.size,
        )
    }

    fun validateTrackNumber(
        version: PublicationVersion<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val trackNumber = trackNumberDao.fetch(version.validatedAssetVersion)
        val kmPosts = getKmPostsByTrackNumber(version.officialId, publicationVersions)
        val referenceLine = getReferenceLineByTrackNumber(version.officialId, publicationVersions)
        val locationTracks = getLocationTracksByTrackNumber(version.officialId, publicationVersions)
        val fieldErrors = validateDraftTrackNumberFields(trackNumber)
        val referenceErrors = validateTrackNumberReferences(
            trackNumber,
            referenceLine,
            kmPosts,
            locationTracks,
            publicationVersions.kmPosts.map { it.officialId },
            publicationVersions.locationTracks.map { it.officialId },
        )
        val geocodingErrors = if (trackNumber.exists && referenceLine != null) {
            validateGeocodingContext(cacheKeys[version.officialId], VALIDATION_TRACK_NUMBER)
        } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    fun validateKmPost(
        version: PublicationVersion<TrackLayoutKmPost>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val kmPost = kmPostDao.fetch(version.validatedAssetVersion)
        val trackNumber = kmPost.trackNumberId?.let { id -> getTrackNumber(id, publicationVersions) }
        val referenceLine = kmPost.trackNumberId?.let { id -> getReferenceLineByTrackNumber(id, publicationVersions) }
        val fieldErrors = validateDraftKmPostFields(kmPost)
        val referenceErrors = validateKmPostReferences(
            kmPost,
            trackNumber,
            referenceLine,
            publicationVersions.trackNumbers.map { it.officialId },
        )
        val geocodingErrors = if (kmPost.exists && trackNumber?.exists == true && referenceLine != null) {
            validateGeocodingContext(cacheKeys[kmPost.trackNumberId], VALIDATION_KM_POST)
        } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    fun validateSwitch(
        version: PublicationVersion<TrackLayoutSwitch>,
        publicationVersions: PublicationVersions,
    ): List<PublishValidationError> {
        val switch = switchDao.fetch(version.validatedAssetVersion)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val linkedTracksAndAlignments = getLinkedTracksAndAlignments(version.officialId, publicationVersions)
        val fieldErrors = validateDraftSwitchFields(switch)
        val referenceErrors = validateSwitchLocationTrackLinkReferences(
            switch,
            linkedTracksAndAlignments.map(Pair<LocationTrack, *>::first),
            publicationVersions.locationTracks.map { it.officialId },
        )
        val locationTrackErrors = validateSwitchLocationTrackReferences(
            getLinkedTrackDraftsNotIncludedInPublication(
                version.officialId, publicationVersions
            )
        )
        val structureErrors = validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndAlignments)
        return fieldErrors + referenceErrors + structureErrors + locationTrackErrors
    }

    fun validateReferenceLine(
        version: PublicationVersion<ReferenceLine>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (referenceLine, alignment) = getReferenceLineAndAlignment(version.validatedAssetVersion)
        val trackNumber = getTrackNumber(referenceLine.trackNumberId, publicationVersions)
        val referenceErrors = validateReferenceLineReference(
            referenceLine,
            trackNumber,
            publicationVersions.trackNumbers.map { it.officialId },
        )
        val alignmentErrors = if (trackNumber?.exists == true) validateReferenceLineAlignment(alignment) else listOf()
        val geocodingErrors: List<PublishValidationError> = if (trackNumber?.exists == true) {
            val contextKey = cacheKeys[referenceLine.trackNumberId]
            val contextErrors = validateGeocodingContext(contextKey, VALIDATION_REFERENCE_LINE)
            val addressErrors = contextKey?.let { key ->
                val locationTracks = getLocationTracksByTrackNumber(trackNumber.id as IntId, publicationVersions)
                locationTracks.flatMap { track ->
                    validateAddressPoints(trackNumber, key, track, VALIDATION_REFERENCE_LINE)
                }
            } ?: listOf()
            contextErrors + addressErrors
        } else listOf()
        return referenceErrors + alignmentErrors + geocodingErrors
    }

    fun validateLocationTrack(
        version: PublicationVersion<LocationTrack>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.validatedAssetVersion)
        val trackNumber = getTrackNumber(locationTrack.trackNumberId, publicationVersions)
        val duplicateOfLocationTrack = locationTrack.duplicateOf?.let { duplicateId ->
            getLocationTrack(duplicateId, publicationVersions)
        }
        val fieldErrors = validateDraftLocationTrackFields(locationTrack)
        val referenceErrors = validateLocationTrackReference(
            locationTrack,
            trackNumber,
            publicationVersions.trackNumbers.map { it.officialId },
        )
        val switchErrors = validateSegmentSwitchReferences(
            locationTrack,
            getSegmentSwitches(alignment),
            publicationVersions.switches.map { it.officialId },
        )
        val duplicateErrors = validateDuplicateOfState(
            locationTrack,
            duplicateOfLocationTrack,
            publicationVersions.locationTracks.map { it.officialId },
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

    private fun getLinkedTracksAndAlignments(
        switchId: IntId<TrackLayoutSwitch>,
        versions: PublicationVersions,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        // Include official tracks that are connected and not overridden in the publication
        val linkedOfficial = publicationDao.fetchLinkedLocationTracks(switchId, OFFICIAL)
            .filter { version -> !versions.containsLocationTrack(version.id) }.map(::getLocationTrackAndAlignment)
        // Include publication tracks that are connected
        val linkedDraft = versions.locationTracks.map { plt -> getLocationTrackAndAlignment(plt.validatedAssetVersion) }
            .filter { (track, alignment) -> isLinkedToSwitch(track, alignment, switchId) }
        return linkedOfficial + linkedDraft
    }

    private fun getLinkedTrackDraftsNotIncludedInPublication(
        switchId: IntId<TrackLayoutSwitch>,
        versions: PublicationVersions,
    ): List<LocationTrack> {
        return publicationDao.fetchLinkedLocationTracks(switchId, DRAFT)
            .map(locationTrackDao::fetch)
            .filter { track -> track.draft != null && !versions.containsLocationTrack(track.id as IntId) }
    }

    private fun getTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ): TrackLayoutTrackNumber? {
        val version = publicationVersions.findTrackNumber(id)?.validatedAssetVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }

    private fun getLocationTrack(id: IntId<LocationTrack>, publicationVersions: PublicationVersions): LocationTrack? {
        val version =
            publicationVersions.findLocationTrack(id)?.validatedAssetVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let(locationTrackDao::fetch)
    }

    private fun getReferenceLineByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: PublicationVersions,
    ): ReferenceLine? {
        val officialVersion = referenceLineDao.fetchVersion(OFFICIAL, trackNumberId)
        val publicationLine = versions.referenceLines
            .map { v -> referenceLineDao.fetch(v.validatedAssetVersion) }
            .find { line -> line.trackNumberId == trackNumberId }
        return publicationLine ?: officialVersion?.let { version -> referenceLineDao.fetch(version) }
    }

    private fun getKmPostsByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ) = combineVersions(
        official = kmPostDao.fetchVersions(OFFICIAL, false, trackNumberId),
        draft = publicationVersions.kmPosts,
    ).map(kmPostDao::fetch).filter { km -> km.trackNumberId == trackNumberId }

    private fun getLocationTracksByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ) = combineVersions(
        official = locationTrackDao.fetchVersions(OFFICIAL, false, trackNumberId),
        draft = publicationVersions.locationTracks,
    ).map(locationTrackDao::fetch).filter { lt -> lt.trackNumberId == trackNumberId }

    private fun <T> combineVersions(
        official: List<RowVersion<T>>, draft: List<PublicationVersion<T>>,
    ) = (official.associateBy { it.id } + draft.associate { it.officialId to it.validatedAssetVersion }).values

    private fun getReferenceLineAndAlignment(version: RowVersion<ReferenceLine>) =
        referenceLineWithAlignment(referenceLineDao, alignmentDao, version)

    private fun getLocationTrackAndAlignment(version: RowVersion<LocationTrack>) =
        locationTrackWithAlignment(locationTrackDao, alignmentDao, version)

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        logger.serviceCall("getPublicationDetails", "id" to id)

        val publication = publicationDao.getPublication(id)
        val ratkoStatus = ratkoPushDao.getRatkoStatus(id).sortedByDescending { it.endTime }.firstOrNull()

        val locationTracks = publicationDao.fetchPublishedLocationTracks(id)
        val referenceLines = publicationDao.fetchPublishedReferenceLines(id)
        val kmPosts = publicationDao.fetchPublishedKmPosts(id)
        val switches = publicationDao.fetchPublishedSwitches(id)
        val trackNumbers = publicationDao.fetchPublishedTrackNumbers(id)

        val calculatedChanges = publicationDao.fetchCalculatedChanges(id)

        return PublicationDetails(
            id = publication.id,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            message = publication.message,
            trackNumbers = trackNumbers,
            referenceLines = referenceLines,
            locationTracks = locationTracks,
            switches = switches,
            kmPosts = kmPosts,
            ratkoPushStatus = ratkoStatus?.status,
            ratkoPushTime = ratkoStatus?.endTime,
            calculatedChanges = calculatedChanges
        )
    }

    fun getPublicationDetailsAsTableItems(id: IntId<Publication>): List<PublicationTableItem> {
        logger.serviceCall("getPublicationDetailsAsTableRows", "id" to id)
        return getPublicationDetails(id).let(::mapToPublicationTableItems)
    }

    fun fetchPublications(from: Instant? = null, to: Instant? = null): List<Publication> {
        logger.serviceCall("fetchPublications", "from" to from, "to" to to)
        return publicationDao.fetchPublications(from, to)
    }

    fun fetchPublicationDetails(from: Instant? = null, to: Instant? = null): List<PublicationDetails> {
        logger.serviceCall("fetchPublicationDetails", "from" to from, "to" to to)
        return publicationDao.fetchPublications(from, to).map { getPublicationDetails(it.id) }
    }

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

        return fetchPublicationDetails(from, to)
            .flatMap(this::mapToPublicationTableItems)
            .let { publications ->
                if (sortBy == null) publications
                else publications.sortedWith(getComparator(sortBy, order))
            }
    }

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

        return asCsvFile(orderedPublishedItems)
    }

    fun validateGeocodingContext(cacheKey: GeocodingContextCacheKey?, localizationKey: String) =
        cacheKey?.let(geocodingDao::getGeocodingContext)?.let { context -> validateGeocodingContext(context) }
            ?: listOf(noGeocodingContext(localizationKey))

    fun validateAddressPoints(
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

    private fun getSegmentSwitches(alignment: LayoutAlignment): List<SegmentSwitch> {
        val segmentsBySwitch: Map<TrackLayoutSwitch, List<LayoutSegment>> =
            alignment.segments.mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
                .groupBy({ (switchId, _) -> switchId }, { (_, segment) -> segment })
                .mapKeys { (switchId, _) -> switchDao.fetchDraftVersionOrThrow(switchId).let(switchDao::fetch) }
        return segmentsBySwitch.entries.map { (switch, segments) ->
            SegmentSwitch(
                switch = switch,
                switchStructure = switchLibraryService.getSwitchStructure(switch.switchStructureId),
                segments = segments,
            )
        }
    }

    fun collectCacheKeys(versions: PublicationVersions): Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?> {
        val trackNumberIds = (
                versions.trackNumbers.map { version -> version.officialId } +
                        versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.validatedAssetVersion).trackNumberId } +
                        versions.locationTracks.map { v -> locationTrackDao.fetch(v.validatedAssetVersion).trackNumberId } +
                        versions.referenceLines.map { v -> referenceLineDao.fetch(v.validatedAssetVersion).trackNumberId }
                ).toSet()
        return trackNumberIds.associateWith { tnId -> geocodingService.getGeocodingContextCacheKey(tnId, versions) }
    }

    private fun getComparator(sortBy: PublicationTableColumn, order: SortOrder? = null) =
        if (order == SortOrder.DESCENDING) getComparator(sortBy).reversed() else getComparator(sortBy)

    //Nulls are "last", e.g., 0, 1, 2, null
    private fun <T : Comparable<T>> compareNullableValues(a: T?, b: T?) =
        if (a == null && b == null) 0
        else if (a == null) 1
        else if (b == null) -1
        else a.compareTo(b)

    private fun getComparator(sortBy: PublicationTableColumn): Comparator<PublicationTableItem> {
        return when (sortBy) {
            PublicationTableColumn.NAME -> Comparator.comparing { p -> p.name }
            PublicationTableColumn.TRACK_NUMBERS -> Comparator { a, b ->
                compareNullableValues(a.trackNumbers.minOrNull(), b.trackNumbers.minOrNull())
            }

            PublicationTableColumn.CHANGED_KM_NUMBERS -> Comparator { a, b ->
                compareNullableValues(a.changedKmNumbers?.minOrNull(), b.changedKmNumbers?.minOrNull())
            }

            PublicationTableColumn.OPERATION -> Comparator.comparing { p -> p.operation.priority }
            PublicationTableColumn.PUBLICATION_TIME -> Comparator.comparing { p -> p.publicationTime }
            PublicationTableColumn.PUBLICATION_USER -> Comparator.comparing { p -> p.publicationUser }
            PublicationTableColumn.MESSAGE -> Comparator.comparing { p -> p.message }
            PublicationTableColumn.RATKO_PUSH_TIME -> Comparator { a, b ->
                compareNullableValues(a.ratkoPushTime, b.ratkoPushTime)
            }
        }
    }

    private fun mapToPublicationTableItems(publication: PublicationDetails): List<PublicationTableItem> {
        val trackNumbers = publication.trackNumbers.map { tn ->
            mapToPublicationTableItem(
                name = "${getTranslation("track-number")} ${tn.number}",
                trackNumberIds = setOf(tn.version.id),
                operation = tn.operation,
                publication = publication,
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
            )
        }

        val locationTracks = publication.locationTracks.map { lt ->
            mapToPublicationTableItem(
                name = "${getTranslation("location-track")} ${lt.name}",
                trackNumberIds = setOf(lt.trackNumberId),
                changedKmNumbers = lt.changedKmNumbers,
                operation = lt.operation,
                publication = publication,
            )
        }

        val switches = publication.switches.map { s ->
            mapToPublicationTableItem(
                name = "${getTranslation("switch")} ${s.name}",
                trackNumberIds = s.trackNumberIds,
                operation = s.operation,
                publication = publication,
            )
        }

        val kmPosts = publication.kmPosts.map { kp ->
            mapToPublicationTableItem(
                name = "${getTranslation("km-post")} ${kp.kmNumber}",
                trackNumberIds = setOf(kp.trackNumberId),
                operation = kp.operation,
                publication = publication,
            )
        }

        val calculatedTrackNumbers = publication.calculatedChanges.trackNumbers.map { tn ->
            mapToPublicationTableItem(
                name = "${getTranslation("track-number")} ${tn.number}",
                trackNumberIds = setOf(tn.version.id),
                operation = tn.operation,
                publication = publication,
                isCalculatedChange = true
            )
        }

        val calculatedLocationTracks = publication.calculatedChanges.locationTracks.map { lt ->
            mapToPublicationTableItem(
                name = "${getTranslation("location-track")} ${lt.name}",
                trackNumberIds = setOf(lt.trackNumberId),
                changedKmNumbers = lt.changedKmNumbers,
                operation = lt.operation,
                publication = publication,
                isCalculatedChange = true
            )
        }

        val calculatedSwitches = publication.calculatedChanges.switches.map { s ->
            mapToPublicationTableItem(
                name = "${getTranslation("switch")} ${s.name}",
                trackNumberIds = s.trackNumberIds,
                operation = s.operation,
                publication = publication,
                isCalculatedChange = true
            )
        }

        return (trackNumbers
                + referenceLines
                + locationTracks
                + switches
                + kmPosts
                + calculatedTrackNumbers
                + calculatedLocationTracks
                + calculatedSwitches)
    }

    private fun mapToPublicationTableItem(
        name: String,
        trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
        operation: Operation,
        publication: PublicationDetails,
        changedKmNumbers: List<KmNumber>? = null,
        isCalculatedChange: Boolean = false,
    ) = PublicationTableItem(
        name = name,
        trackNumbers = trackNumberIds.map { id ->
            getTrackNumberAtMomentOrThrow(id, publication.publicationTime).number
        },
        changedKmNumbers = changedKmNumbers,
        operation = operation,
        publicationTime = publication.publicationTime,
        publicationUser = publication.publicationUser,
        message = formatMessage(publication.message, isCalculatedChange),
        ratkoPushTime = if (publication.ratkoPushStatus == RatkoPushStatus.SUCCESSFUL) publication.ratkoPushTime else null,
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
