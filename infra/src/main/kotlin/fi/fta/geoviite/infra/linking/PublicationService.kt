package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingDao
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geometry.GeometryDao
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

    fun validatePublishCandidates(candidates: PublishCandidates, request: PublishRequest): ValidatedPublishCandidates {
        logger.serviceCall("validatePublishCandidates", "candidates" to candidates, "request" to request)
        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidates.filter(request)),
            allChangesValidated = validateAsPublicationUnit(candidates),
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
    fun getRevertRequestDependencies(publishRequest: PublishRequest): PublishRequest {
        val newTrackNumbers = publishRequest.referenceLines
            .mapNotNull { id -> referenceLineService.get(DRAFT, id) }
            .map { rl -> rl.trackNumberId }
            .filter(trackNumberService::draftExists)

        val newReferenceLines = publishRequest.trackNumbers
            .mapNotNull { id -> referenceLineService.getByTrackNumber(DRAFT, id) }
            .filter { line -> line.draft != null }
            .map { line -> line.id as IntId }
        val allTrackNumbers = publishRequest.trackNumbers.toSet() + newTrackNumbers.toSet()
        val allReferenceLines = publishRequest.referenceLines.toSet() + newReferenceLines.toSet()
        val locationTracks = publishRequest.locationTracks.toSet()
        val switches = publishRequest.switches.toSet()
        val (allLocationTracks, allSwitches) = getRevertRequestLocationTrackAndSwitchDependenciesTransitively(
            locationTracks, locationTracks, switches, switches
        )

        return PublishRequest(
            trackNumbers = allTrackNumbers.toList(),
            referenceLines = allReferenceLines.toList(),
            locationTracks = allLocationTracks.toList(),
            switches = allSwitches.toList(),
            kmPosts = publishRequest.kmPosts
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
    fun revertPublishCandidates(toDelete: PublishRequest): PublishResult {
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
    fun updateExternalId(request: PublishRequest) {
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

    private inline fun <reified T> assertNoErrors(
        version: PublicationVersion<T>, errors: List<PublishValidationError>
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
    fun publishChanges(versions: PublicationVersions, calculatedChanges: CalculatedChanges): PublishResult {
        logger.serviceCall("publishChanges", "versions" to versions)

        val trackNumbers = versions.trackNumbers.map(trackNumberService::publish).map { r -> r.rowVersion }
        val kmPosts = versions.kmPosts.map(kmPostService::publish).map { r -> r.rowVersion }
        val switches = versions.switches.map(switchService::publish).map { r -> r.rowVersion }
        val referenceLines = versions.referenceLines.map(referenceLineService::publish).map { r -> r.rowVersion }
        val locationTracks = versions.locationTracks.map(locationTrackService::publish).map { r -> r.rowVersion }

        val publishId =
            publicationDao.createPublication(trackNumbers, referenceLines, locationTracks, switches, kmPosts)

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
        val trackNumber = trackNumberDao.fetch(version.draftVersion)
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
        val kmPost = kmPostDao.fetch(version.draftVersion)
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
        val switch = switchDao.fetch(version.draftVersion)
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
        val (referenceLine, alignment) = getReferenceLineAndAlignment(version.draftVersion)
        val trackNumber = getTrackNumber(referenceLine.trackNumberId, publicationVersions)
        val fieldErrors = validateDraftReferenceLineFields(referenceLine)
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
        return fieldErrors + referenceErrors + alignmentErrors + geocodingErrors
    }

    fun validateLocationTrack(
        version: PublicationVersion<LocationTrack>,
        publicationVersions: PublicationVersions,
        cacheKeys: Map<IntId<TrackLayoutTrackNumber>, GeocodingContextCacheKey?>,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.draftVersion)
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
        val linkedDraft = versions.locationTracks.map { plt -> getLocationTrackAndAlignment(plt.draftVersion) }
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
        val version = publicationVersions.findTrackNumber(id)?.draftVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }

    private fun getLocationTrack(id: IntId<LocationTrack>, publicationVersions: PublicationVersions): LocationTrack? {
        val version =
            publicationVersions.findLocationTrack(id)?.draftVersion ?: locationTrackDao.fetchOfficialVersion(id)
        return version?.let(locationTrackDao::fetch)
    }

    private fun getReferenceLineByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        versions: PublicationVersions
    ): ReferenceLine? {
        val officialVersion = referenceLineDao.fetchVersion(OFFICIAL, trackNumberId)
        val publicationLine = versions.referenceLines
            .map { v -> referenceLineDao.fetch(v.draftVersion) }
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
        official: List<RowVersion<T>>, draft: List<PublicationVersion<T>>
    ) = (official.associateBy { it.id } + draft.associate { it.officialId to it.draftVersion }).values

    private fun getReferenceLineAndAlignment(version: RowVersion<ReferenceLine>) =
        referenceLineWithAlignment(referenceLineDao, alignmentDao, version)

    private fun getLocationTrackAndAlignment(version: RowVersion<LocationTrack>) =
        locationTrackWithAlignment(locationTrackDao, alignmentDao, version)

    @Transactional(readOnly = true)
    fun getPublicationDetails(id: IntId<Publication>): PublicationDetails {
        logger.serviceCall("getPublicationDetails", "id" to id)

        val publication = publicationDao.getPublication(id)
        val ratkoStatus = ratkoPushDao.getRatkoStatus(id).sortedByDescending { it.endTime }.firstOrNull()

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
                        versions.kmPosts.mapNotNull { v -> kmPostDao.fetch(v.draftVersion).trackNumberId } +
                        versions.locationTracks.map { v -> locationTrackDao.fetch(v.draftVersion).trackNumberId } +
                        versions.referenceLines.map { v -> referenceLineDao.fetch(v.draftVersion).trackNumberId }
                ).toSet()
        return trackNumberIds.associateWith { tnId -> geocodingService.getGeocodingContextCacheKey(tnId, versions) }
    }
}
