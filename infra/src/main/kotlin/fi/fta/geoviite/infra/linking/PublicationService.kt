package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.integration.CalculatedChangesService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.ratko.RatkoService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

const val ENFORCE_VALIDITY = true

@Service
class PublishService @Autowired constructor(
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
    private val ratkoService: RatkoService?,
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
        logger.serviceCall("validatePublishCandidates")
        val allPublishCandidates = collectPublishCandidates()
        val (candidatesInRequest, candidatesNotInRequest) = allPublishCandidates.splitByRequest(versions)
        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidatesInRequest, versions),
            validatedSeparately = validateSeparately(candidatesNotInRequest),
        )
    }

    fun validateSeparately(publishCandidates: PublishCandidates) = PublishCandidates(
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

    fun validateAsPublicationUnit(candidates: PublishCandidates, versions: PublicationVersions) = PublishCandidates(
        trackNumbers = versions.trackNumbers.map { version ->
            candidates.getTrackNumber(version.officialId).copy(
                errors = validateTrackNumber(version, versions.kmPosts, versions.referenceLines, versions.locationTracks)
            )
        },
        referenceLines = versions.referenceLines.map { version ->
            candidates.getReferenceLine(version.officialId).copy(
                errors = validateReferenceLine(version, versions.trackNumbers, versions.kmPosts)
            )
        },
        locationTracks = versions.locationTracks.map { version ->
            candidates.getLocationTrack(version.officialId).copy(
                errors = validateLocationTrack(version, versions.trackNumbers, versions.switches, versions.locationTracks, versions.kmPosts)
            )
        },
        switches = versions.switches.map { version ->
            candidates.getSwitch(version.officialId).copy(
                errors = validateSwitch(version, versions.locationTracks)
            )
        },
        kmPosts = versions.kmPosts.map { version ->
            candidates.getKmPost(version.officialId).copy(
                errors = validateKmPost(version, versions.trackNumbers, versions.kmPosts)
            )
        },
    )

    fun validatePublishRequest(versions: PublicationVersions) {
        logger.serviceCall("validate", "versions" to versions)
        versions.trackNumbers.forEach { version ->
            assertNoErrors(
                version,
                validateTrackNumber(version, versions.kmPosts, versions.referenceLines, versions.locationTracks),
            )
        }
        versions.kmPosts.forEach { version ->
            assertNoErrors(
                version,
                validateKmPost(version, versions.trackNumbers, versions.kmPosts),
            )
        }
        versions.referenceLines.forEach { version ->
            assertNoErrors(
                version,
                validateReferenceLine(version, versions.trackNumbers, versions.kmPosts),
            )
        }
        versions.locationTracks.forEach { version ->
            assertNoErrors(
                version,
                validateLocationTrack(
                    version,
                    versions.trackNumbers,
                    versions.switches,
                    versions.locationTracks,
                    versions.kmPosts
                )
            )
        }
        versions.switches.forEach { version ->
            assertNoErrors(
                version,
                validateSwitch(version, versions.locationTracks)
            )
        }
    }

    @Transactional
    fun revertPublishCandidates(): PublishResult {
        logger.serviceCall("revertPublishCandidates")
        val locationTrackCount = locationTrackService.deleteDrafts().size
        val referenceLineCount = referenceLineService.deleteDrafts().size
        alignmentDao.deleteOrphanedAlignments()
        val switchCount = switchService.deleteDrafts().size
        val kmPostCount = kmPostService.deleteDrafts().size
        val trackNumberCount = trackNumberService.deleteDrafts().size

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
    fun getPublicationVersions(request: PublishRequest) = PublicationVersions(
        trackNumbers = trackNumberDao.fetchPublicationVersions(DRAFT, request.trackNumbers),
        referenceLines = referenceLineDao.fetchPublicationVersions(DRAFT, request.referenceLines),
        kmPosts = kmPostDao.fetchPublicationVersions(DRAFT, request.kmPosts),
        locationTracks = locationTrackDao.fetchPublicationVersions(DRAFT, request.locationTracks),
        switches = switchDao.fetchPublicationVersions(DRAFT, request.switches),
    )

    private fun updateExternalIdForLocationTrack(locationTrackId: IntId<LocationTrack>) {
        val locationTrackOid = ratkoService?.let { s ->
            s.getNewLocationTrackOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        locationTrackOid?.let { oid -> locationTrackService.updateExternalId(locationTrackId, Oid(oid.id)) }
    }

    private fun updateExternalIdForTrackNumber(trackNumberId: IntId<TrackLayoutTrackNumber>) {
        val routeNumberOid = ratkoService?.let { s ->
            s.getNewRouteNumberTrackOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        routeNumberOid?.let { oid -> trackNumberService.updateExternalId(trackNumberId, Oid(oid.id)) }
    }

    private fun updateExternalIdForSwitch(switchId: IntId<TrackLayoutSwitch>) {
        val switchOid = ratkoService?.let { s ->
            s.getNewSwitchOid() ?: throw IllegalStateException("No OID received from RATKO")
        }
        switchOid?.let { oid -> switchService.updateExternalIdForSwitch(switchId, Oid(oid.id)) }
    }

    private inline fun <reified T> assertNoErrors(version: PublicationVersion<T>, errors: List<PublishValidationError>) {
        val severeErrors = errors.filter { error -> error.type == PublishValidationErrorType.ERROR }
        if (severeErrors.isNotEmpty()) {
            logger.warn("Validation errors in published ${T::class.simpleName}: item=$version errors=$severeErrors")
            if (ENFORCE_VALIDITY) throw PublicationFailureException(
                message = "Cannot publish ${T::class.simpleName} due to validation errors: $version",
                localizedMessageKey = "validation-failed",
            )
        }
    }

    @Transactional
    fun publishChanges(versions: PublicationVersions): PublishResult {
        logger.serviceCall("publishChanges", "versions" to versions)

        // TODO: calculated changes could be done on top of versions as well (perf benefit)
        // TODO: calculated changes could be done outside the transaction
        val calculatedChanges = calculatedChangesService.getCalculatedChangesInDraft(
            versions.trackNumbers.map(PublicationVersion<TrackLayoutTrackNumber>::officialId),
            versions.referenceLines.map(PublicationVersion<ReferenceLine>::officialId),
            versions.kmPosts.map(PublicationVersion<TrackLayoutKmPost>::officialId),
            versions.locationTracks.map(PublicationVersion<LocationTrack>::officialId),
            versions.switches.map(PublicationVersion<TrackLayoutSwitch>::officialId),
        )

        val trackNumbers = request.trackNumbers.map(trackNumberService::publish)
        val kmPosts = request.kmPosts.map(kmPostService::publish)
        val switches = request.switches.map(switchService::publish)
        val referenceLines = request.referenceLines.map(referenceLineService::publish)
        val locationTracks = request.locationTracks.map(locationTrackService::publish)

        val publishId = publicationDao.createPublish(trackNumbers, referenceLines, locationTracks, switches, kmPosts)

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
        publicationKmPosts: List<PublicationVersion<TrackLayoutKmPost>>,
        publicationReferenceLines: List<PublicationVersion<ReferenceLine>>,
        publicationLocationTracks: List<PublicationVersion<LocationTrack>>,
    ): List<PublishValidationError> {
        val trackNumber = trackNumberDao.fetch(version.rowVersion)
        val kmPosts = getPublicationKmPostsByTrackNumber(version.officialId, publicationKmPosts)
        val locationTracks = getPublicationLocationTracksByTrackNumber(version.officialId, publicationLocationTracks)
        val fieldErrors = validateDraftTrackNumberFields(trackNumber)
        val referenceErrors = validateTrackNumberReferences(
            trackNumber,
            kmPosts,
            locationTracks,
            publicationKmPosts.map(PublicationVersion<TrackLayoutKmPost>::officialId),
            publicationLocationTracks.map(PublicationVersion<LocationTrack>::officialId),
        )
        val geocodingErrors =
            if (trackNumber.exists) {
                validateTrackNumberGeocodingContext(version, VALIDATION_TRACK_NUMBER, publishKmPostIds)
            } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }


    // TODO
    fun validateTrackNumberAssociatedTrackAddressesNOTFIXED(
        trackNumber: TrackLayoutTrackNumber,
        publishKmPostIds: List<IntId<TrackLayoutKmPost>>,
    ): List<PublishValidationError> {
        val locationTracks = publicationDao
            .fetchTrackNumberLocationTrackRows(trackNumber.id as IntId)
            .map(locationTrackDao::fetch)
        return locationTracks.filter(LocationTrack::exists).flatMap { locationTrack ->
            validateAddressPoints(trackNumber, locationTrack, VALIDATION_REFERENCE_LINE) {
                geocodingService.getAddressPointsForPublication(locationTrack.id, publishKmPostIds)
            }
        }
    }

    fun validateKmPostOwnInformation(id: IntId<TrackLayoutKmPost>): List<PublishValidationError> =
        validateDraftKmPostFields(kmPostService.getDraft(id))

    fun validateKmPost(
        version: PublicationVersion<TrackLayoutKmPost>,
        publicationTrackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
        publicationKmPosts: List<PublicationVersion<TrackLayoutKmPost>>,
    ): List<PublishValidationError> {
        val kmPost = kmPostDao.fetch(version.rowVersion)
        val trackNumber = kmPost.trackNumberId?.let { id -> getPublicationTrackNumber(id, publicationTrackNumbers) }
        val fieldErrors = validateDraftKmPostFields(kmPost)
        val referenceErrors = validateKmPostReferences(
            kmPost,
            trackNumber,
            publicationTrackNumbers.map(PublicationVersion<TrackLayoutTrackNumber>::officialId)
        )
        val geocodingErrors =
            if (kmPost.exists && trackNumber != null) {
                validateTrackNumberGeocodingContext(kmPost.trackNumberId, VALIDATION_KM_POST, publicationKmPosts)
            } else listOf()
        return fieldErrors + referenceErrors + geocodingErrors
    }

    fun validateSwitchOwnInformation(id: IntId<TrackLayoutSwitch>): List<PublishValidationError> =
        validateDraftSwitchFields(switchService.getDraft(id))

    fun validateSwitch(
        version: PublicationVersion<TrackLayoutSwitch>,
        publicationLocationTracks: List<PublicationVersion<LocationTrack>>,
    ): List<PublishValidationError> {
        val switch = switchDao.fetch(version.rowVersion)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val linkedTracksAndAlignments = getLinkedTracksAndAlignments(version.officialId, publicationLocationTracks)
        val linkedTracks = linkedTracksAndAlignments.map(Pair<LocationTrack,*>::first)
        val fieldErrors = validateDraftSwitchFields(switch)
        val referenceErrors = validateSwitchSegmentReferences(
            switch,
            linkedTracks,
            publicationLocationTracks.map(PublicationVersion<LocationTrack>::officialId),
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
        publicationTrackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
        publishKmPostVersions: List<PublicationVersion<TrackLayoutKmPost>>,
    ): List<PublishValidationError> {
        val (referenceLine, alignment) = getReferenceLineAndAlignment(version.rowVersion)
        val trackNumber = getPublicationTrackNumberOrThrow(referenceLine.trackNumberId, publicationTrackNumbers)
        val fieldErrors = validateDraftReferenceLineFields(referenceLine)
        val referenceErrors = validateReferenceLineReference(
            referenceLine,
            trackNumber,
            publicationTrackNumbers.map(PublicationVersion<TrackLayoutTrackNumber>::officialId),
        )
        val alignmentErrors = if (trackNumber.exists) validateReferenceLineAlignment(alignment) else listOf()
        val geocodingErrors: List<PublishValidationError> =
            if (trackNumber.exists) validateTrackNumberGeocodingContext(
                referenceLine.trackNumberId,
                VALIDATION_REFERENCE_LINE,
                publishKmPostVersions
            ) + validateTrackNumberAssociatedTrackAddresses(trackNumber, publishKmPostVersions)
            else listOf()
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
        publicationTrackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
        publicationSwitches: List<PublicationVersion<TrackLayoutSwitch>>,
        publicationLocationTracks: List<PublicationVersion<LocationTrack>>,
        publicationKmPosts: List<PublicationVersion<TrackLayoutKmPost>>,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getLocationTrackAndAlignment(version.rowVersion)
        val trackNumber = getPublicationTrackNumber(locationTrack.trackNumberId, publicationTrackNumbers)
        val duplicateOfLocationTrack = locationTrack.duplicateOf?.let { duplicateId ->
            getPublicationLocationTrack(duplicateId, publicationLocationTracks)
        }
        val fieldErrors = validateDraftLocationTrackFields(locationTrack)
        val referenceErrors = validateLocationTrackReference(locationTrack, trackNumber, publicationTrackNumbers)
        val switchErrors = validateSegmentSwitchReferences(locationTrack, getSegmentSwitches(alignment), publicationSwitches)
        val duplicateErrors = validateDuplicateOfState(locationTrack, duplicateOfLocationTrack, publicationLocationTracks)
        val alignmentErrors = if (locationTrack.exists) validateLocationTrackAlignment(alignment) else listOf()
        val geocodingErrors =
            if (locationTrack.exists) {
                validateAddressPoints(trackNumber, locationTrack, VALIDATION_LOCATION_TRACK) {
                    geocodingService.getAddressPointsForPublication(locationTrack.id, publicationKmPosts)
                }
            } else listOf()
        return fieldErrors + referenceErrors + switchErrors + duplicateErrors + alignmentErrors + geocodingErrors
    }

    private fun getLinkedTracksAndAlignments(
        switchId: IntId<TrackLayoutSwitch>,
        publicationLocationTracks: List<PublicationVersion<LocationTrack>>,
    ): List<Pair<LocationTrack, LayoutAlignment>> {
        // Include official tracks that are connected and not overridden in the publication
        val linkedOfficial = publicationDao.fetchLinkedLocationTracks(switchId, OFFICIAL)
            .filter { track -> publicationLocationTracks.none { plt -> plt.officialId == track.officialId } }
            .map{ track -> getLocationTrackAndAlignment(track.rowVersion) }
        // Include publication tracks that are connected
        val linkedDraft = publicationLocationTracks.map { plt -> getLocationTrackAndAlignment(plt.rowVersion) }
            .filter { (track, alignment) -> isLinkedToSwitch(track, alignment, switchId) }
        return linkedOfficial + linkedDraft
    }

    private fun getPublicationTrackNumberOrThrow(
        id: IntId<TrackLayoutTrackNumber>,
        publicationTrackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
    ) = getPublicationTrackNumber(id, publicationTrackNumbers)
        ?: throw NoSuchEntityException(TrackLayoutTrackNumber::class, id)

    private fun getPublicationTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        publicationTrackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
    ): TrackLayoutTrackNumber? {
        val publicationVersion = publicationTrackNumbers.find { tn -> tn.officialId == id }?.rowVersion
        val version = publicationVersion ?: trackNumberDao.fetchOfficialVersion(id)
        return version?.let(trackNumberDao::fetch)
    }
    private fun getPublicationLocationTrack(
        id: IntId<LocationTrack>,
        publicationLocationTracks: List<PublicationVersion<LocationTrack>>,
    ): Pair<LocationTrack, LayoutAlignment> {
        val version = publicationLocationTracks.find { plt -> plt.officialId == id }?.rowVersion
    }

    private fun getPublicationKmPostsByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publishKmPostVersions: List<PublicationVersion<TrackLayoutKmPost>>,
    ) = combineVersions(
        official = kmPostDao.fetchPublicationVersions(OFFICIAL, trackNumberId),
        draft = publishKmPostVersions,
    ).map(kmPostDao::fetch)

    private fun getPublicationLocationTracksByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        publicationLocationTrackVersions: List<PublicationVersion<LocationTrack>>,
    ) = combineVersions(
        official = locationTrackDao.fetchPublicationVersions(OFFICIAL, trackNumberId),
        draft = publicationLocationTrackVersions,
    ).map(locationTrackDao::fetch)

    private fun <T> combineVersions(official: List<PublicationVersion<T>>, draft: List<PublicationVersion<T>>) =
        (official.groupBy(PublicationVersion<T>::officialId) + draft.groupBy(PublicationVersion<T>::officialId))
            .values.flatten()
            .map(PublicationVersion<T>::rowVersion)

    private fun getReferenceLineAndAlignment(version: RowVersion<ReferenceLine>) =
        referenceLineDao.fetch(version).let { line ->
            line to alignmentDao.fetch(line.alignmentVersion
                ?: throw IllegalStateException("ReferenceLine from DB must have an alignment")
            )
        }

    private fun getLocationTrackAndAlignment(version: RowVersion<LocationTrack>) =
        locationTrackDao.fetch(version).let { track ->
            track to alignmentDao.fetch(track.alignmentVersion
                ?: throw IllegalStateException("LocationTrack from DB must have an alignment")
            )
        }

    fun getPublicationListing(): List<PublicationListingItem> =
        publicationDao.fetchRatkoPublicationListing()

    @Transactional(readOnly = true)
    fun getPublication(id: IntId<Publication>): Publication {
        val (publishTime, status, pushTime) = publicationDao.fetchPublishTime(id)
        val locationTracks = locationTrackDao.fetchPublicationInformation(id)
        val referenceLines = referenceLineDao.fetchPublicationInformation(id)
        val kmPosts = kmPostDao.fetchPublicationInformation(id)
        val switches = switchDao.fetchSwitchPublicationInformation(id)
        val trackNumbers = trackNumberDao.fetchPublicationInformation(id)

        return Publication(
            id,
            publishTime,
            trackNumbers,
            referenceLines,
            locationTracks,
            switches,
            kmPosts,
            status,
            pushTime,
        )
    }

    private fun validateTrackNumberGeocodingContext(
        trackNumber: PublicationVersion<TrackLayoutTrackNumber>,
        publicationReferenceLines: List<PublicationVersion<ReferenceLine>>,
        publicationKmPosts: List<PublicationVersion<TrackLayoutKmPost>>,
        validationType: String,
    ) = validateGeocodingContext(
        geocodingService.getGeocodingContext(DRAFT, trackNumberId, publishKmPostIds),
        validationType,
    )

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

//    private fun getTrackNumber(
//        id: IntId<TrackLayoutTrackNumber>,
//        publicationVersions: List<PublicationVersion<TrackLayoutTrackNumber>>,
//    ): TrackLayoutTrackNumber {
//        val version = publicationVersions.find { tn -> tn.officialId == id }?.rowVersion
//            ?: trackNumberDao.fetchVersionOrThrow(id, OFFICIAL)
//        return trackNumberDao.fetch(version)
//    }

//    private fun getDraftKmPostWithOfficialId(version: RowVersion<TrackLayoutKmPost>): TrackLayoutKmPost =
//        kmPostService.getDraft(version)
//            .also { kmPost -> require(kmPost.id == version) { "Attempting to publish km-post via draft ID" } }
//
//    private fun getDraftSwitchWithOfficialId(version: RowVersion<TrackLayoutSwitch>) =
//        switchDao.fetch(version).also { switch ->
//            require(switch.id == version) { "Attempting to publish switch via draft ID" }
//        }
//
//    private fun getAndVerifyTrackNumber(version: RowVersion<TrackLayoutTrackNumber>): TrackLayoutTrackNumber =
//        trackNumberService.getDraft(version)
//            .also { trackNumber -> require(trackNumber.id == version) { "Attempting to publish track number via draft ID" } }
//
//    private fun getDraftReferenceLineAndAlignmentWithOfficialId(version: RowVersion<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> =
//        referenceLineService.getWithAlignmentOrThrow(DRAFT, version)
//            .also { (referenceLine, _) -> require(referenceLine.id == version) { "Attempting to publish ReferenceLine via draft ID" } }
//
//    private fun getDraftLocationTrackAndAlignmentWithOfficialId(version: RowVersion<LocationTrack>): Pair<LocationTrack, LayoutAlignment> =
//        locationTrackService.getWithAlignmentOrThrow(DRAFT, version)
//            .also { (locationTrack, _) -> require(locationTrack.id == version) { "Attempting to publish LocationTrack via draft ID" } }
}
