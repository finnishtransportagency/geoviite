package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingCacheService
import fi.fta.geoviite.infra.geocoding.GeocodingContextCacheKey
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.split.SplitLayoutValidationIssues
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class PublicationValidationService
@Autowired
constructor(
    private val publicationDao: PublicationDao,
    private val geocodingService: GeocodingService,
    private val kmPostDao: LayoutKmPostDao,
    private val locationTrackDao: LocationTrackDao,
    private val referenceLineDao: ReferenceLineDao,
    private val alignmentDao: LayoutAlignmentDao,
    private val switchDao: LayoutSwitchDao,
    private val switchLibraryService: SwitchLibraryService,
    private val trackNumberDao: LayoutTrackNumberDao,
    private val geocodingCacheService: GeocodingCacheService,
    private val splitService: SplitService,
) {
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

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
        target: ValidationTarget,
        trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    ): List<ValidatedAsset<TrackLayoutTrackNumber>> {
        if (trackNumberIds.isEmpty()) return emptyList()

        // Switches don't affect tracknumber validity, so they are ignored
        val validationContext =
            when (target) {
                is ValidateTransition ->
                    createValidationContext(
                        target = target,
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                is ValidateContext -> createValidationContext(target)
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
        target: ValidationTarget,
        trackIds: List<IntId<LocationTrack>>,
    ): List<ValidatedAsset<LocationTrack>> {
        if (trackIds.isEmpty()) return emptyList()

        val validationContext =
            when (target) {
                is ValidateTransition ->
                    createValidationContext(
                        target = target,
                        switches = switchDao.fetchCandidateVersions(target.candidateContext),
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                is ValidateContext -> createValidationContext(target)
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
        target: ValidationTarget,
        switchIds: List<IntId<TrackLayoutSwitch>>,
    ): List<ValidatedAsset<TrackLayoutSwitch>> {
        if (switchIds.isEmpty()) return emptyList()

        // Only tracks and switches affect switch validation, so we can ignore the other types in
        // the publication unit
        val validationContext =
            when (target) {
                is ValidateTransition ->
                    createValidationContext(
                        target = target,
                        switches = switchDao.fetchCandidateVersions(target.candidateContext),
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                    )

                is ValidateContext -> createValidationContext(target)
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
        target: ValidationTarget,
        kmPostIds: List<IntId<TrackLayoutKmPost>>,
    ): List<ValidatedAsset<TrackLayoutKmPost>> {
        if (kmPostIds.isEmpty()) return emptyList()

        // We can ignore switches and locationtracks, as they don't affect km-post validity
        val validationContext =
            when (target) {
                is ValidateTransition ->
                    createValidationContext(
                        target = target,
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                is ValidateContext -> createValidationContext(target)
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
        target: ValidationTarget,
        trackNumbers: List<ValidationVersion<TrackLayoutTrackNumber>> = emptyList(),
        locationTracks: List<ValidationVersion<LocationTrack>> = emptyList(),
        referenceLines: List<ValidationVersion<ReferenceLine>> = emptyList(),
        switches: List<ValidationVersion<TrackLayoutSwitch>> = emptyList(),
        kmPosts: List<ValidationVersion<TrackLayoutKmPost>> = emptyList(),
    ): ValidationContext =
        createValidationContext(
            ValidationVersions(target, trackNumbers, locationTracks, referenceLines, switches, kmPosts, emptyList())
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

    @Transactional(readOnly = true)
    fun validateAsPublicationUnit(
        candidates: PublicationCandidates,
        allowMultipleSplits: Boolean,
    ): PublicationCandidates {
        val splitVersions =
            splitService.fetchPublicationVersions(
                branch = candidates.transition.candidateBranch,
                locationTracks = candidates.locationTracks.map { it.id },
                switches = candidates.switches.map { it.id },
            )
        val versions = candidates.getValidationVersions(candidates.transition, splitVersions)

        val validationContext = createValidationContext(versions).also { ctx -> ctx.preloadByPublicationSet() }
        val splitIssues = splitService.validateSplit(versions, validationContext, allowMultipleSplits)

        return PublicationCandidates(
            transition = candidates.transition,
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
                    validationTargetType = validationContext.target.type,
                )
            return referenceIssues + geocodingIssues + duplicateNameIssues
        }

    private fun validateKmPost(id: IntId<TrackLayoutKmPost>, context: ValidationContext): List<LayoutValidationIssue>? =
        context.getKmPost(id)?.let { kmPost ->
            val trackNumber = kmPost.trackNumberId?.let(context::getTrackNumber)
            val trackNumberNumber = (trackNumber ?: kmPost.trackNumberId?.let(context::getCandidateTrackNumber))?.number
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
                validateSwitchNameDuplication(
                    switch,
                    validationContext.getSwitchesByName(switch.name),
                    validationContext.target.type,
                )
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
                    trackNumberNumber = validationContext.getCandidateTrackNumber(referenceLine.trackNumberId)?.number,
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
            val trackNumberName =
                (trackNumber ?: validationContext.getCandidateTrackNumber(track.trackNumberId))?.number

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
            val duplicateNameIssues =
                validateLocationTrackNameDuplication(
                    track,
                    trackNumberName,
                    tracksWithSameName,
                    validationContext.target.type,
                )

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
}