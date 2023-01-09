package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.integration.RatkoPushDao
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.RatkoClient
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

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
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    @Transactional(readOnly = true)
    fun collectPublishCandidates(): PublishCandidates {
        logger.serviceCall("collectPublishCandidates")

        val trackNumberCandidates = publicationDao.fetchTrackNumberPublishCandidates()
        val locationTrackCandidates = publicationDao.fetchLocationTrackPublishCandidates()
        val referenceLineCandidates = publicationDao.fetchReferenceLinePublishCandidates()
        val switchCandidates = publicationDao.fetchSwitchPublishCandidates()
        val kmPostCandidates = publicationDao.fetchKmPostPublishCandidates()

        return PublishCandidates(
            trackNumberCandidates,
            locationTrackCandidates,
            referenceLineCandidates,
            switchCandidates,
            kmPostCandidates
        )
    }

    fun getPublishCandidates(): PublishCandidates {
        logger.serviceCall("getPublishCandidates")
        return collectPublishCandidates()
    }

    fun validatePublishCandidates(versions: PublicationVersions): ValidatedPublishCandidates {
        logger.serviceCall("validatePublishCandidates", "versions" to versions)
        val allPublishCandidates = collectPublishCandidates()
        val (candidatesInRequest, candidatesNotInRequest) = allPublishCandidates.splitByRequest(versions)
        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidatesInRequest, versions),
            validatedSeparately = validateSeparately(candidatesNotInRequest),
        )
    }

    private fun validateSeparately(publishCandidates: PublishCandidates)  = PublishCandidates(
        trackNumbers = publishCandidates.trackNumbers.map { candidate ->
            candidate.copy(errors = validateTrackNumberOwnInformation(candidate.id))
        },
        locationTracks = publishCandidates.locationTracks.map { candidate ->
            candidate.copy(errors = validateLocationTrackOwnInformation(candidate.id))
        },
        referenceLines = publishCandidates.referenceLines.map { candidate ->
            candidate.copy(errors = validateReferenceLineOwnInformation(candidate.id))
        },
        switches = publishCandidates.switches.map { candidate ->
            candidate.copy(errors = validateSwitchOwnInformation(candidate.id))
        },
        kmPosts = publishCandidates.kmPosts.map { candidate ->
            candidate.copy(errors = validateKmPostOwnInformation(candidate.id))
        },
    )

    private fun validateAsPublicationUnit(candidates: PublishCandidates, versions: PublicationVersions) =
        collectCacheKeys(versions).let { cacheKeys -> PublishCandidates(
            trackNumbers = versions.trackNumbers.map { version ->
                candidates.getTrackNumber(version.officialId)
                    .copy(errors = validateTrackNumber(version, versions, cacheKeys))
            },
            referenceLines = versions.referenceLines.map { version ->
                candidates.getReferenceLine(version.officialId)
                    .copy(errors = validateReferenceLine(version, versions, cacheKeys))
            },
            locationTracks = versions.locationTracks.map { version ->
                candidates.getLocationTrack(version.officialId)
                    .copy(errors = validateLocationTrack(version, versions, cacheKeys))
            },
            switches = versions.switches.map { version ->
                candidates.getSwitch(version.officialId)
                    .copy(errors = validateSwitch(version, versions))
            },
            kmPosts = versions.kmPosts.map { version ->
                candidates.getKmPost(version.officialId)
                    .copy(errors = validateKmPost(version, versions, cacheKeys))
            },
        ) }


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

    @Transactional
    fun revertPublishCandidates(toDelete: PublishRequest): PublishResult {
        logger.serviceCall("revertPublishCandidates", "toDelete" to toDelete)
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
    fun updateExternalId(request: PublishRequest) {
        logger.serviceCall("updateExternalId", "request" to request)

        try {
            request.locationTracks
                .filter { locationTrackId -> locationTrackService.getDraft(locationTrackId).externalId == null }
                .forEach { locationTrackId -> updateExternalIdForLocationTrack(locationTrackId) }
            request.trackNumbers
                .filter { trackNumberId -> trackNumberService.getDraft(trackNumberId).externalId == null }
                .forEach { trackNumberId -> updateExternalIdForTrackNumber(trackNumberId) }
            request.switches
                .filter { switchId -> switchService.getDraft(switchId).externalId == null }
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
    fun getPublicationVersions(request: PublishRequest): PublicationVersions {
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

    private inline fun <reified T> assertNoErrors(version: PublicationVersion<T>, errors: List<PublishValidationError>) {
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
    fun publishChanges(versions: PublicationVersions, calculatedChanges: CalculatedChanges): PublishResult {
        logger.serviceCall("publishChanges", "versions" to versions)

        val trackNumbers = versions.trackNumbers.map(trackNumberService::publish)
        val kmPosts = versions.kmPosts.map(kmPostService::publish)
        val switches = versions.switches.map(switchService::publish)
        val referenceLines = versions.referenceLines.map(referenceLineService::publish)
        val locationTracks = versions.locationTracks.map(locationTrackService::publish)

        val publishId = publicationDao.createPublication(trackNumbers, referenceLines, locationTracks, switches, kmPosts)

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

    fun validateTrackNumberOwnInformation(id: IntId<TrackLayoutTrackNumber>): List<PublishValidationError> =
        validateDraftTrackNumberFields(trackNumberService.getDraft(id))


    fun validateTrackNumber(
        version: PublicationVersion<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val trackNumber = trackNumberDao.fetch(version.draftVersion)
        val kmPosts = getKmPostsByTrackNumber(version.officialId, publicationVersions)
        val locationTracks = getLocationTracksByTrackNumber(version.officialId, publicationVersions)
        val fieldErrors = validateDraftTrackNumberFields(trackNumber)
        val referenceErrors = validateTrackNumberReferences(
            trackNumber,
            kmPosts,
            locationTracks,
            publicationVersions.kmPosts.map { it.officialId },
            publicationVersions.locationTracks.map { it.officialId },
        )
        val geocodingErrors =
            if (trackNumber.exists) {
                validateGeocodingContext(cacheKeys[version.officialId], VALIDATION_TRACK_NUMBER)
            } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    fun validateKmPostOwnInformation(id: IntId<TrackLayoutKmPost>): List<PublishValidationError> =
        validateDraftKmPostFields(kmPostService.getDraft(id))

    fun validateKmPost(
        version: PublicationVersion<TrackLayoutKmPost>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val kmPost = kmPostDao.fetch(version.draftVersion)
        val trackNumber = kmPost.trackNumberId?.let { id -> getTrackNumber(id, publicationVersions) }
        val fieldErrors = validateDraftKmPostFields(kmPost)
        val referenceErrors = validateKmPostReferences(
            kmPost,
            trackNumber,
            publicationVersions.trackNumbers.map { it.officialId }
        )
        val geocodingErrors =
            if (kmPost.exists && trackNumber != null) {
                validateGeocodingContext(cacheKeys[kmPost.trackNumberId], VALIDATION_KM_POST)
            } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    fun validateSwitchOwnInformation(id: IntId<TrackLayoutSwitch>): List<PublishValidationError> =
        validateDraftSwitchFields(switchService.getDraft(id))

    fun validateSwitch(
        version: PublicationVersion<TrackLayoutSwitch>,
        publicationVersions: PublicationVersions,
    ): List<PublishValidationError> {
        val switch = switchDao.fetch(version.draftVersion)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val linkedTracksAndAlignments = getLinkedTracksAndAlignments(version.officialId, publicationVersions)
        val fieldErrors = validateDraftSwitchFields(switch)
        val referenceErrors = validateSwitchSegmentReferences(
            switch,
            linkedTracksAndAlignments.map(Pair<LocationTrack,*>::first),
            publicationVersions.locationTracks.map { it.officialId },
        )
        val structureErrors = validateSwitchSegmentStructure(switch, structure, linkedTracksAndAlignments)
        return fieldErrors + referenceErrors + structureErrors
    }

    fun validateReferenceLineOwnInformation(id: IntId<ReferenceLine>): List<PublishValidationError> {
        val (referenceLine, alignment) = referenceLineService.getWithAlignmentOrThrow(DRAFT, id)
        val trackNumber = trackNumberService.getDraft(referenceLine.trackNumberId)
        return validateDraftReferenceLineFields(referenceLine) +
                if (trackNumber.exists) {
                    validateReferenceLineAlignment(alignment)
                } else listOf()

    }

    fun validateReferenceLine(
        version: PublicationVersion<ReferenceLine>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (referenceLine, alignment) = getReferenceLineAndAlignment(version.draftVersion)
        val trackNumber = getTrackNumberOrThrow(referenceLine.trackNumberId, publicationVersions)
        val fieldErrors = validateDraftReferenceLineFields(referenceLine)
        val referenceErrors = validateReferenceLineReference(
            referenceLine,
            trackNumber,
            publicationVersions.trackNumbers.map { it.officialId },
        )
        val alignmentErrors = if (trackNumber.exists) validateReferenceLineAlignment(alignment) else listOf()
        val geocodingErrors: List<PublishValidationError> =
            if (trackNumber.exists) {
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
        return fieldErrors + referenceErrors + alignmentErrors + geocodingErrors
    }

    fun validateLocationTrackOwnInformation(id: IntId<LocationTrack>): List<PublishValidationError> {
        val (locationTrack, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, id)
        return validateDraftLocationTrackFields(locationTrack) +
                if (locationTrack.exists) validateLocationTrackAlignment(alignment)
                else listOf()
    }

    fun validateLocationTrack(
        version: PublicationVersion<LocationTrack>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.draftVersion)
        val trackNumber = getTrackNumberOrThrow(locationTrack.trackNumberId, publicationVersions)
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
        val alignmentErrors =
            if (locationTrack.exists) validateLocationTrackAlignment(alignment)
            else listOf()
        val geocodingErrors =
            if (locationTrack.exists) {
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
            .filter { version -> !versions.containsLocationTrack(version.id) }
            .map(::getLocationTrackAndAlignment)
        // Include publication tracks that are connected
        val linkedDraft = versions.locationTracks
            .map { plt -> getLocationTrackAndAlignment(plt.draftVersion) }
            .filter { (track, alignment) -> isLinkedToSwitch(track, alignment, switchId) }
        return linkedOfficial + linkedDraft
    }

    private fun getTrackNumberOrThrow(id: IntId<TrackLayoutTrackNumber>, versions: PublicationVersions) =
        getTrackNumber(id, versions) ?: throw NoSuchEntityException(TrackLayoutTrackNumber::class, id)

    private fun getTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ): TrackLayoutTrackNumber? {
        val version = publicationVersions.findTrackNumber(id)?.draftVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }

    private fun getLocationTrackOrThrow(id: IntId<LocationTrack>, versions: PublicationVersions) =
        getLocationTrack(id, versions) ?: throw NoSuchEntityException(LocationTrack::class, id)

    private fun getLocationTrack(id: IntId<LocationTrack>, publicationVersions: PublicationVersions): LocationTrack? {
        val version = publicationVersions.findLocationTrack(id)?.draftVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let(locationTrackDao::fetch)
    }

    private fun getLocationTrackAndAlignmentOrThrow(id: IntId<LocationTrack>, versions: PublicationVersions) =
        getLocationTrackAndAlignment(id, versions) ?: throw NoSuchEntityException(LocationTrack::class, id)

    private fun getLocationTrackAndAlignment(
        id: IntId<LocationTrack>,
        publicationVersions: PublicationVersions,
    ): Pair<LocationTrack, LayoutAlignment>? {
        val version = publicationVersions.findLocationTrack(id)?.draftVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let { v -> locationTrackWithAlignment(locationTrackDao, alignmentDao, v) }
    }

    private fun getKmPostsByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ) = combineVersions(
        official = kmPostDao.fetchVersions(OFFICIAL, false, trackNumberId),
        draft = publicationVersions.kmPosts,
    ).map(kmPostDao::fetch)

    private fun getLocationTracksByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationVersions: PublicationVersions,
    ) = combineVersions(
        official = locationTrackDao.fetchVersions(OFFICIAL, false, trackNumberId),
        draft = publicationVersions.locationTracks,
    ).map(locationTrackDao::fetch)

    private fun <T> combineVersions(
        official: List<RowVersion<T>>, draft: List<PublicationVersion<T>>) =
        (official.associateBy { it.id } + draft.associate { it.officialId to it.draftVersion })
            .values

    private fun getReferenceLineAndAlignment(version: RowVersion<ReferenceLine>) =
        referenceLineWithAlignment(referenceLineDao, alignmentDao, version)

    private fun getLocationTrackAndAlignment(version: RowVersion<LocationTrack>) =
        locationTrackWithAlignment(locationTrackDao, alignmentDao, version)

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        logger.serviceCall("getPublicationDetails", "id" to id)

        val publication = publicationDao.getPublication(id)
        val ratkoStatus = ratkoPushDao.getRatkoStatus(id)
            .sortedByDescending { it.endTime }
            .firstOrNull()

        val locationTracks = locationTrackDao.fetchPublicationInformation(id)
        val referenceLines = referenceLineDao.fetchPublicationInformation(id)
        val kmPosts = kmPostDao.fetchPublicationInformation(id)
        val switches = switchDao.fetchPublicationInformation(id)
        val trackNumbers = trackNumberDao.fetchPublicationInformation(id)

        return PublicationDetails(
            id = publication.id,
            publicationTime = publication.publicationTime,
            publicationUser = publication.publicationUser,
            trackNumbers = trackNumbers,
            referenceLines = referenceLines,
            locationTracks = locationTracks,
            switches = switches,
            kmPosts = kmPosts,
            ratkoPushStatus = ratkoStatus?.status,
            ratkoPushTime = ratkoStatus?.endTime,
        )
    }

    fun fetchPublications(from: Instant? = null, to: Instant? = null): List<Publication> {
        logger.serviceCall("fetchPublications", "from" to from, "to" to to)
        return publicationDao.fetchPublications(from, to)
    }

    fun fetchPublicationDetails(from: Instant? = null, to: Instant? = null): List<PublicationDetails> {
        logger.serviceCall("fetchPublicationDetails", "from" to from, "to" to to)
        return publicationDao.fetchPublications(from, to).map { getPublicationDetails(it.id) }
    }

    fun validateGeocodingContext(cacheKey: GeocodingContextCacheKey?, localizationKey: String) =
        cacheKey?.let(geocodingDao::getGeocodingContext)
            ?.let { context -> validateGeocodingContext(context) }
            ?: listOf(noGeocodingContext(localizationKey))

    fun validateAddressPoints(
        trackNumber: TrackLayoutTrackNumber,
        contextKey: GeocodingContextCacheKey,
        track: LocationTrack,
        validationTargetLocalizationPrefix: String,
    ) =
        if (!track.exists || track.alignmentVersion == null) listOf()
        else {
            validateAddressPoints(trackNumber, track, validationTargetLocalizationPrefix) {
                geocodingService.getAddressPoints(contextKey, track.alignmentVersion)
            }
        }

    private fun getSegmentSwitches(alignment: LayoutAlignment): List<SegmentSwitch> {
        val segmentsBySwitch: Map<TrackLayoutSwitch, List<LayoutSegment>> = alignment.segments
            .mapNotNull { segment -> segment.switchId?.let { id -> id as IntId to segment } }
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
        val trackNumberIds = versions.trackNumbers.map { version -> version.officialId } +
                versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.draftVersion).trackNumberId } +
                versions.locationTracks.map { v -> locationTrackDao.fetch(v.draftVersion).trackNumberId } +
                versions.referenceLines.map { v-> referenceLineDao.fetch(v.draftVersion).trackNumberId }
        return trackNumberIds.associateWith { tnId ->
            geocodingService.getGeocodingContextCacheKey(tnId, versions)
        }
    }
}
