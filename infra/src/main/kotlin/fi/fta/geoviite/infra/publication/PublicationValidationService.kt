package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingCacheService
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.geocoding.LayoutGeocodingContextCacheKey
import fi.fta.geoviite.infra.publication.LayoutValidationIssueType.ERROR
import fi.fta.geoviite.infra.split.SplitLayoutValidationIssues
import fi.fta.geoviite.infra.split.SplitService
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchDao
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumberDao
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
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
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
    ): List<ValidatedAsset<LayoutTrackNumber>> {
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
        switchIds: List<IntId<LayoutSwitch>>,
    ): List<ValidatedAsset<LayoutSwitch>> {
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
        kmPostIds: List<IntId<LayoutKmPost>>,
    ): List<ValidatedAsset<LayoutKmPost>> {
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
        trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>> = emptyList(),
        locationTracks: List<LayoutRowVersion<LocationTrack>> = emptyList(),
        referenceLines: List<LayoutRowVersion<ReferenceLine>> = emptyList(),
        switches: List<LayoutRowVersion<LayoutSwitch>> = emptyList(),
        kmPosts: List<LayoutRowVersion<LayoutKmPost>> = emptyList(),
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
            assertNoErrors(version, requireNotNull(validateTrackNumber(version.id, validationContext)))
        }
        versions.kmPosts.forEach { version ->
            assertNoErrors(version, requireNotNull(validateKmPost(version.id, validationContext)))
        }
        versions.referenceLines.forEach { version ->
            assertNoErrors(version, requireNotNull(validateReferenceLine(version.id, validationContext)))
        }
        versions.locationTracks.forEach { version ->
            assertNoErrors(version, requireNotNull(validateLocationTrack(version.id, validationContext)))
        }
        versions.switches.forEach { version ->
            assertNoErrors(version, requireNotNull(validateSwitch(version.id, validationContext)))
        }
    }

    private inline fun <reified T : LayoutAsset<T>> assertNoErrors(
        version: LayoutRowVersion<T>,
        issues: List<LayoutValidationIssue>,
    ) {
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
        id: IntId<LayoutTrackNumber>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val trackNumber = validationContext.getTrackNumber(id)
        val kmPosts = validationContext.getKmPostsByTrackNumber(id)
        val referenceLine = validationContext.getReferenceLineByTrackNumber(id)
        val locationTracks = validationContext.getLocationTracksByTrackNumber(id)
        val trackNumberIsCancelled = validationContext.trackNumberIsCancelled(id)

        if (trackNumber == null) {
            return validateTrackNumberReferences(
                trackNumberExists = false,
                trackNumberIsCancelled = trackNumberIsCancelled,
                referenceLine,
                kmPosts,
                locationTracks,
            )
        } else {
            val referenceIssues =
                validateTrackNumberReferences(
                    trackNumberExists = trackNumber.exists,
                    trackNumberIsCancelled = trackNumberIsCancelled,
                    referenceLine,
                    kmPosts,
                    locationTracks,
                )
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
    }

    private fun validateKmPost(id: IntId<LayoutKmPost>, context: ValidationContext): List<LayoutValidationIssue>? =
        context.getKmPost(id)?.let { kmPost ->
            val trackNumber = kmPost.trackNumberId?.let(context::getTrackNumber)
            val trackNumberNumber = (trackNumber ?: kmPost.trackNumberId?.let(context::getCandidateTrackNumber))?.number
            val referenceLine = trackNumber?.referenceLineId?.let(context::getReferenceLine)
            val trackNumberIsCancelled = kmPost.trackNumberId?.let(context::trackNumberIsCancelled) == true

            val referenceIssues =
                validateKmPostReferences(kmPost, trackNumber, referenceLine, trackNumberNumber, trackNumberIsCancelled)

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

            val duplicateNumberIssues =
                if (kmPost.trackNumberId == null || trackNumberNumber == null) listOf()
                else
                    validateKmPostNumberDuplication(
                        kmPost,
                        trackNumberNumber,
                        context.getKmPostsByTrackNumber(kmPost.trackNumberId),
                        context.target.type,
                    )

            referenceIssues + geocodingIssues + duplicateNumberIssues
        }

    private fun validateSwitch(
        id: IntId<LayoutSwitch>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue> {
        val switch = validationContext.getSwitch(id)
        val switchIsCancelled = validationContext.switchIsCancelled(id)
        val linkedTracksAndGeometries = validationContext.getSwitchTracksWithGeometries(id)
        val linkedTracks = linkedTracksAndGeometries.map(Pair<LocationTrack, *>::first)
        if (switch == null) {
            return validateSwitchLocationTrackLinkReferences(
                switchExists = false,
                switchIsCancelled = switchIsCancelled,
                linkedTracks,
            )
        } else {
            val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)

            val referenceIssues =
                validateSwitchLocationTrackLinkReferences(
                    switchExists = switch.exists,
                    switchIsCancelled = switchIsCancelled,
                    linkedTracks,
                )

            val locationIssues = if (switch.exists) validateSwitchLocation(switch) else emptyList()
            val structureIssues =
                locationIssues.ifEmpty {
                    validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndGeometries)
                }

            val duplicationIssues =
                validateSwitchNameDuplication(
                    switch,
                    validationContext.getSwitchesByName(switch.name),
                    validationContext.target.type,
                )
            val oidDuplicationIssues =
                validateSwitchOidDuplication(
                    switch,
                    switch.draftOid?.toString()?.let(switchDao::lookupByExternalId)?.let { row ->
                        switchDao.get(row.context, row.id)
                    },
                )
            return referenceIssues + structureIssues + duplicationIssues + oidDuplicationIssues
        }
    }

    private fun validateReferenceLine(
        id: IntId<ReferenceLine>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val referenceLineWithAlignment = validationContext.getReferenceLineWithAlignment(id)
        return if (referenceLineWithAlignment == null) {
            if (validationContext.referenceLineIsCancelled(id)) {
                val trackNumberId = validationContext.getTrackNumberIdByReferenceLine(id)
                if (trackNumberId == null) null
                else
                    listOfNotNull(
                        validate(validationContext.trackNumberIsCancelled(trackNumberId)) {
                            "$VALIDATION_REFERENCE_LINE.track-number.cancelled"
                        }
                    )
            } else null
        } else {
            val (referenceLine, alignment) = referenceLineWithAlignment
            val trackNumber = validationContext.getTrackNumber(referenceLine.trackNumberId)
            val referenceIssues =
                validateReferenceLineReference(
                    referenceLine = referenceLine,
                    trackNumber = trackNumber,
                    trackNumberNumber = validationContext.getCandidateTrackNumber(referenceLine.trackNumberId)?.number,
                    trackNumberIsCancelled = validationContext.trackNumberIsCancelled(referenceLine.trackNumberId),
                )
            val alignmentIssues =
                if (trackNumber?.exists == true) {
                    validateReferenceLineGeometry(alignment)
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
    }

    private fun validateLocationTrack(
        id: IntId<LocationTrack>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val trackAndGeometry = validationContext.getLocationTrackWithGeometry(id)
        // cancelling a track's creation can cause switches to become disconnected
        val trackNetworkTopologyIssues =
            validationContext.getPotentiallyAffectedSwitches(id).filter(LayoutSwitch::exists).flatMap { switch ->
                val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
                val switchTracks = validationContext.getSwitchTracksWithGeometries(switch.id as IntId)
                validateSwitchTopologicalConnectivity(switch, structure, switchTracks, trackAndGeometry?.first)
            }
        return if (trackAndGeometry == null) {
            trackNetworkTopologyIssues
        } else {
            val (track, geometry) = trackAndGeometry
            val trackNumber = validationContext.getTrackNumber(track.trackNumberId)
            val trackNumberName =
                (trackNumber ?: validationContext.getCandidateTrackNumber(track.trackNumberId))?.number

            val referenceIssues =
                validateLocationTrackReference(
                    track,
                    trackNumber,
                    trackNumberName,
                    trackNumberIsCancelled = validationContext.trackNumberIsCancelled(track.trackNumberId),
                )
            val switchTrackLinkings: List<SwitchTrackLinking> = validationContext.getSwitchTrackLinks(geometry)
            val switchTrackIssues = validateTrackSwitchReferences(track, switchTrackLinkings)
            val trackNetworkTopologyIssues =
                validationContext.getPotentiallyAffectedSwitches(id).filter(LayoutSwitch::exists).flatMap { switch ->
                    val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
                    val switchTracks = validationContext.getSwitchTracksWithGeometries(switch.id as IntId)
                    validateSwitchTopologicalConnectivity(switch, structure, switchTracks, track)
                }
            val switchConnectivityIssues =
                if (track.exists) {
                    validateLocationTrackSwitchConnectivity(track, geometry)
                } else {
                    emptyList()
                }

            val duplicatesAfterPublication = validationContext.getDuplicateTracks(id)
            val duplicateOf = track.duplicateOf?.let(validationContext::getLocationTrack)
            // Draft-only won't be found if it's not in the publication set -> get name from draft
            // for validation issue
            val duplicateOfName = track.duplicateOf?.let(validationContext::getCandidateLocationTrack)?.name
            val duplicateIssues =
                validateDuplicateOfState(
                    track,
                    duplicateOf,
                    duplicateOfName,
                    duplicateOfLocationTrackIsCancelled =
                        track.duplicateOf?.let(validationContext::locationTrackIsCancelled) ?: false,
                    duplicatesAfterPublication,
                )

            val alignmentIssues =
                if (track.exists) {
                    validateLocationTrackGeometry(geometry) +
                        validateEdges(geometry) { id -> requireNotNull(validationContext.getSwitch(id)).name }
                } else listOf()
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
                switchTrackIssues +
                duplicateIssues +
                alignmentIssues +
                geocodingIssues +
                duplicateNameIssues +
                trackNetworkTopologyIssues +
                switchConnectivityIssues)
        }
    }

    private fun validateGeocodingContext(
        cacheKey: LayoutGeocodingContextCacheKey?,
        localizationKey: String,
        trackNumber: TrackNumber,
    ) =
        cacheKey
            ?.let { key -> geocodingCacheService.getGeocodingContextWithReasons(key) }
            ?.let { context -> validateGeocodingContext(context, trackNumber) }
            ?: listOf(noGeocodingContext(localizationKey))

    private fun validateAddressPoints(
        trackNumber: LayoutTrackNumber,
        contextKey: LayoutGeocodingContextCacheKey,
        track: LocationTrack,
        validationTargetLocalizationPrefix: String,
    ): List<LayoutValidationIssue> =
        if (!track.exists) {
            listOf()
        } else {
            validateAddressPoints(trackNumber, track, validationTargetLocalizationPrefix) {
                geocodingService.getAddressPoints(contextKey, track.getVersionOrThrow())
            }
        }
}
