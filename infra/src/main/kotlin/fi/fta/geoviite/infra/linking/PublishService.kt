package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.PublishFailureException
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
    private val publishDao: PublishDao,
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

        val trackNumberCandidates = publishDao.fetchTrackNumberPublishCandidates()
        val locationTrackCandidates = publishDao.fetchLocationTrackPublishCandidates()
        val referenceLineCandidates = publishDao.fetchReferenceLinePublishCandidates()
        val switchCandidates = publishDao.fetchSwitchPublishCandidates()
        val kmPostCandidates = publishDao.fetchKmPostPublishCandidates()

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

    fun validatePublishCandidates(publishRequest: PublishRequest): ValidatedPublishCandidates {
        logger.serviceCall("validatePublishCandidates")
        val allPublishCandidates = collectPublishCandidates()
        val candidatesInPublicationRequest = allPublishCandidates.filteredToRequest(publishRequest)
        val candidatesNotInPublicationRequest =
            allPublishCandidates.filteredToRequest(allPublishCandidates.ids() - publishRequest)

        return ValidatedPublishCandidates(
            validatedAsPublicationUnit = validateAsPublicationUnit(candidatesInPublicationRequest),
            validatedSeparately = validateSeparately(candidatesNotInPublicationRequest),
        )
    }

    fun validateSeparately(publishCandidates: PublishCandidates) =
        PublishCandidates(
            trackNumbers = publishCandidates.trackNumbers.map { candidate ->
                candidate.copy(
                    errors = validateTrackNumberOwnInformation(
                        candidate.id
                    )
                )
            },
            locationTracks = publishCandidates.locationTracks.map { candidate ->
                candidate.copy(
                    errors = validateLocationTrackOwnInformation(
                        candidate.id
                    )
                )
            },
            referenceLines = publishCandidates.referenceLines.map { candidate ->
                candidate.copy(
                    errors = validateReferenceLineOwnInformation(
                        candidate.id
                    )
                )
            },
            switches = publishCandidates.switches.map { candidate ->
                candidate.copy(
                    errors = validateSwitchOwnInformation(
                        candidate.id
                    )
                )
            },
            kmPosts = publishCandidates.kmPosts.map { candidate ->
                candidate.copy(
                    errors = validateKmPostOwnInformation(
                        candidate.id
                    )
                )
            },
        )

    fun validateAsPublicationUnit(publishCandidates: PublishCandidates): PublishCandidates {
        val publishSwitchIds = publishCandidates.switches.map(SwitchPublishCandidate::id)
        val publishKmPostIds = publishCandidates.kmPosts.map(KmPostPublishCandidate::id)
        val publishReferenceLineIds = publishCandidates.referenceLines.map(ReferenceLinePublishCandidate::id)
        val publishLocationTrackIds = publishCandidates.locationTracks.map(LocationTrackPublishCandidate::id)
        val publishTrackNumberIds = publishCandidates.trackNumbers.map(TrackNumberPublishCandidate::id)

        return PublishCandidates(
            trackNumbers = publishCandidates.trackNumbers.map { candidate ->
                candidate.copy(
                    errors = validateTrackNumber(
                        candidate.id,
                        publishKmPostIds,
                        publishReferenceLineIds,
                        publishLocationTrackIds,
                    )
                )
            },
            referenceLines = publishCandidates.referenceLines.map { candidate ->
                candidate.copy(
                    errors = validateReferenceLine(
                        candidate.id,
                        publishTrackNumberIds,
                    )
                )
            },
            locationTracks = publishCandidates.locationTracks.map { candidate ->
                candidate.copy(
                    errors = validateLocationTrack(
                        candidate.id,
                        publishTrackNumberIds,
                        publishSwitchIds,
                        publishLocationTrackIds,
                    )
                )
            },
            switches = publishCandidates.switches.map { candidate ->
                candidate.copy(
                    errors = validateSwitch(
                        candidate.id,
                        publishLocationTrackIds,
                    )
                )
            },
            kmPosts = publishCandidates.kmPosts.map { candidate ->
                candidate.copy(
                    errors = validateKmPost(
                        candidate.id,
                        publishTrackNumberIds,
                    )
                )
            },
        )
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

        request.locationTracks
            .filter { locationTrackId -> locationTrackService.getDraft(locationTrackId).externalId == null }
            .forEach { locationTrackId -> updateExternalIdForLocationTrack(locationTrackId) }
        request.trackNumbers
            .filter { trackNumberId -> trackNumberService.getDraft(trackNumberId).externalId == null }
            .forEach { trackNumberId -> updateExternalIdForTrackNumber(trackNumberId) }
        request.switches
            .filter { switchId -> switchService.getDraft(switchId).externalId == null }
            .forEach { switchId -> updateExternalIdForSwitch(switchId) }
    }

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

    fun validatePublishRequest(request: PublishRequest) {
        logger.serviceCall("validate", "request" to request)
        request.trackNumbers.forEach { trackNumberId ->
            assertNoErrors(
                trackNumberId,
                validateTrackNumber(trackNumberId, request.kmPosts, request.referenceLines, request.locationTracks),
            )
        }
        request.kmPosts.forEach { kmPostId ->
            assertNoErrors(
                kmPostId,
                validateKmPost(kmPostId, request.trackNumbers),
            )
        }
        request.referenceLines.forEach { referenceLineId ->
            assertNoErrors(
                referenceLineId,
                validateReferenceLine(referenceLineId, request.trackNumbers),
            )
        }
        request.locationTracks.forEach { locationTrackId ->
            assertNoErrors(
                locationTrackId,
                validateLocationTrack(locationTrackId, request.trackNumbers, request.switches, request.locationTracks)
            )
        }
        request.switches.forEach { switchId ->
            assertNoErrors(
                switchId,
                validateSwitch(switchId, request.locationTracks)
            )
        }
    }

    private inline fun <reified T> assertNoErrors(id: IntId<T>, errors: List<PublishValidationError>) {
        val severeErrors = errors.filter { error -> error.type == PublishValidationErrorType.ERROR }
        if (severeErrors.isNotEmpty()) {
            logger.warn("Validation errors in published ${T::class.simpleName}: item=$id errors=$severeErrors")
            if (ENFORCE_VALIDITY) {
                throw PublishFailureException("Cannot publish ${T::class.simpleName} due to validation errors: $id")
            }
        }
    }

    @Transactional
    fun publishChanges(request: PublishRequest): PublishResult {
        logger.serviceCall("publishChanges", "request" to request)

        val calculatedChanges = calculatedChangesService.getCalculatedChangesInDraft(
            request.trackNumbers,
            request.referenceLines,
            request.kmPosts,
            request.locationTracks,
            request.switches
        )

        val trackNumbers = request.trackNumbers.map(trackNumberService::publish)
        val kmPosts = request.kmPosts.map(kmPostService::publish)
        val switches = request.switches.map(switchService::publish)
        val referenceLines = request.referenceLines.map(referenceLineService::publish)
        val locationTracks = request.locationTracks.map(locationTrackService::publish)

        val publishId = publishDao.createPublish(trackNumbers, referenceLines, locationTracks, switches, kmPosts)

        publishDao.savePublishCalculatedChanges(publishId, calculatedChanges)

        return PublishResult(
            publishId = publishId,
            trackNumbers = trackNumbers.size,
            referenceLines = referenceLines.size,
            locationTracks = locationTracks.size,
            switches = switches.size,
            kmPosts = kmPosts.size,
        )
    }

    fun validateTrackNumberOwnInformation(id: IntId<TrackLayoutTrackNumber>): List<PublishValidationError> {
        return validateDraftTrackNumberFields(getDraftTrackNumberWithOfficialId(id))
    }

    fun validateTrackNumber(
        id: IntId<TrackLayoutTrackNumber>,
        publishKmPostIds: List<IntId<TrackLayoutKmPost>>,
        publishReferenceLineIds: List<IntId<ReferenceLine>>,
        publishLocationTrackIds: List<IntId<LocationTrack>>,
    ): List<PublishValidationError> {
        val trackNumber = getDraftTrackNumberWithOfficialId(id)
        val kmPosts = kmPostService.list(DRAFT, id)
        val locationTracks = publishDao.fetchTrackNumberLocationTrackRows(id).map(locationTrackDao::fetch)
        return validateDraftTrackNumberFields(trackNumber) +
                validateTrackNumberReferences(
                    trackNumber,
                    kmPosts,
                    locationTracks,
                    publishKmPostIds,
                    publishLocationTrackIds,
                ) +
                if (trackNumber.exists) {
                    validateTrackNumberGeocodingContext(id, VALIDATION_TRACK_NUMBER)
                } else listOf()
    }

    fun validateTrackNumberAssociatedTrackAddresses(
        trackNumber: TrackLayoutTrackNumber,
    ): List<PublishValidationError> {
        val locationTracks = publishDao
            .fetchTrackNumberLocationTrackRows(trackNumber.id as IntId)
            .map(locationTrackDao::fetch)
        return locationTracks.filter(LocationTrack::exists).flatMap { locationTrack ->
            validateAddressPoints(trackNumber, locationTrack, VALIDATION_REFERENCE_LINE) {
                geocodingService.getAddressPoints(locationTrack.id, DRAFT)
            }
        }
    }

    fun validateKmPostOwnInformation(id: IntId<TrackLayoutKmPost>): List<PublishValidationError> {
        return validateDraftKmPostFields(getDraftKmPostWithOfficialId(id))
    }

    fun validateKmPost(
        id: IntId<TrackLayoutKmPost>,
        publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): List<PublishValidationError> {
        val kmPost = getDraftKmPostWithOfficialId(id)
        val trackNumber = kmPost.trackNumberId?.let { tnId ->
            trackNumberService.getDraft(tnId as IntId)
        }
        return validateDraftKmPostFields(kmPost) +
                validateKmPostReferences(kmPost, trackNumber, publishTrackNumberIds) +
                if (kmPost.exists) {
                    validateTrackNumberGeocodingContext(kmPost.trackNumberId as IntId, VALIDATION_KM_POST)
                } else listOf()
    }

    fun validateSwitchOwnInformation(id: IntId<TrackLayoutSwitch>): List<PublishValidationError> {
        return validateDraftSwitchFields(getDraftSwitchWithOfficialId(id))
    }

    fun validateSwitch(
        id: IntId<TrackLayoutSwitch>,
        publishLocationTrackIds: List<IntId<LocationTrack>>,
    ): List<PublishValidationError> {
        val switch = getDraftSwitchWithOfficialId(id)
        val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
        val locationTracksAndAlignments = publishDao.fetchLinkedAlignmentRows(id)
            .map { (trackVersion, alignmentVersion) ->
                val locationTrack = locationTrackDao.fetch(trackVersion)
                val alignment = alignmentDao.fetch(alignmentVersion)
                locationTrack to alignment
            }
        val locationTracks = locationTracksAndAlignments.map { (track, _) -> track }
        return validateDraftSwitchFields(switch) +
                validateSwitchSegmentReferences(switch, locationTracks, publishLocationTrackIds) +
                validateSwitchSegmentStructure(switch, structure, locationTracksAndAlignments)
    }

    fun validateReferenceLineOwnInformation(id: IntId<ReferenceLine>): List<PublishValidationError> {
        val (referenceLine, alignment) = getDraftReferenceLineAndAlignmentWithOfficialId(id)
        val trackNumber = trackNumberService.getDraft(referenceLine.trackNumberId)
        return validateDraftReferenceLineFields(referenceLine) +
                if (trackNumber.exists) {
                    validateReferenceLineAlignment(alignment)
                } else listOf()

    }

    fun validateReferenceLine(
        id: IntId<ReferenceLine>,
        publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): List<PublishValidationError> {
        val (referenceLine, alignment) = getDraftReferenceLineAndAlignmentWithOfficialId(id)
        val trackNumber = trackNumberService.getDraft(referenceLine.trackNumberId)
        return validateDraftReferenceLineFields(referenceLine) +
                validateReferenceLineReference(referenceLine, trackNumber, publishTrackNumberIds) +
                if (trackNumber.exists) {
                    validateTrackNumberGeocodingContext(referenceLine.trackNumberId, VALIDATION_REFERENCE_LINE) +
                            validateReferenceLineAlignment(alignment) +
                            validateTrackNumberAssociatedTrackAddresses(trackNumber)
                } else listOf()
    }

    fun validateLocationTrackOwnInformation(id: IntId<LocationTrack>): List<PublishValidationError> {
        val (locationTrack, alignment) = getDraftLocationTrackAndAlignmentWithOfficialId(id)
        return validateDraftLocationTrackFields(locationTrack) +
                if (locationTrack.exists) {
                    validateLocationTrackAlignment(alignment)
                } else listOf()
    }

    fun validateLocationTrack(
        id: IntId<LocationTrack>,
        publishTrackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
        publishSwitchIds: List<IntId<TrackLayoutSwitch>>,
        publishLocationTrackIds: List<IntId<LocationTrack>>,
    ): List<PublishValidationError> {
        val (locationTrack, alignment) = getDraftLocationTrackAndAlignmentWithOfficialId(id)
        val trackNumber = trackNumberService.getDraft(locationTrack.trackNumberId)
        val duplicateOfLocationTrack = locationTrack.duplicateOf?.let { duplicateId ->
            locationTrackService.getOrThrow(DRAFT, duplicateId)
        }
        return validateDraftLocationTrackFields(locationTrack) +
                validateLocationTrackReference(locationTrack, trackNumber, publishTrackNumberIds) +
                validateSegmentSwitchReferences(locationTrack, getSegmentSwitches(alignment), publishSwitchIds) +
                validateDuplicateOfState(locationTrack, duplicateOfLocationTrack, publishLocationTrackIds) +
                if (locationTrack.exists) {
                    validateLocationTrackAlignment(alignment) +
                            validateAddressPoints(trackNumber, locationTrack, VALIDATION_LOCATION_TRACK) {
                                geocodingService.getAddressPoints(locationTrack.id, DRAFT)
                            }
                } else listOf()
    }

    fun getPublicationListing(): List<PublicationListingItem> =
        publishDao.fetchRatkoPublicationListing()

    @Transactional(readOnly = true)
    fun getPublication(id: IntId<Publication>): Publication {
        val (publishTime, status, pushTime) = publishDao.fetchPublishTime(id)
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
        trackNumberId: IntId<TrackLayoutTrackNumber>,
        validationType: String
    ) = validateGeocodingContext(geocodingService.getGeocodingContext(DRAFT, trackNumberId), validationType)

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

    private fun getDraftKmPostWithOfficialId(id: IntId<TrackLayoutKmPost>): TrackLayoutKmPost {
        val kmPost = kmPostService.getDraft(id)
        require(kmPost.id == id) { "Attempting to publish km-post via draft ID" }
        return kmPost
    }

    private fun getDraftSwitchWithOfficialId(id: IntId<TrackLayoutSwitch>): TrackLayoutSwitch {
        val switch = switchDao.fetch(switchDao.fetchDraftVersionOrThrow(id))
        require(switch.id == id) { "Attempting to publish switch via draft ID" }
        return switch
    }

    private fun getDraftTrackNumberWithOfficialId(id: IntId<TrackLayoutTrackNumber>): TrackLayoutTrackNumber {
        val trackNumber = trackNumberService.getDraft(id)
        require(trackNumber.id == id) { "Attempting to publish track number via draft ID" }
        return trackNumber
    }

    private fun getDraftReferenceLineAndAlignmentWithOfficialId(id: IntId<ReferenceLine>): Pair<ReferenceLine, LayoutAlignment> {
        val (referenceLine, alignment) = requireNotNull(referenceLineService.getWithAlignment(DRAFT, id)) {
            "Cannot find draft reference line: $id"
        }
        require(referenceLine.id == id) { "Attempting to publish ReferenceLine via draft ID" }
        return referenceLine to alignment
    }

    private fun getDraftLocationTrackAndAlignmentWithOfficialId(id: IntId<LocationTrack>): Pair<LocationTrack, LayoutAlignment> {
        val (locationTrack, alignment) = requireNotNull(locationTrackService.getWithAlignment(DRAFT, id)) {
            "Cannot find draft location track: $id"
        }
        require(locationTrack.id == id) { "Attempting to publish LocationTrack via draft ID" }
        return locationTrack to alignment
    }
}
