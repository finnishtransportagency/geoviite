package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
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
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.OperationalPointDao
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
    private val operationalPointDao: OperationalPointDao,
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
        branch: LayoutBranch,
        state: PublicationState,
        trackNumberIds: List<IntId<LayoutTrackNumber>>,
    ): List<ValidatedAsset<LayoutTrackNumber>> {
        if (trackNumberIds.isEmpty()) return emptyList()

        val target = LayoutContextTransition.publicationIn(branch)
        // Switches don't affect tracknumber validity, so they are ignored
        val validationContext =
            when (state) {
                PublicationState.DRAFT ->
                    createValidationContext(
                        target = target,
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                PublicationState.OFFICIAL -> createValidationContext(target)
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
        branch: LayoutBranch,
        state: PublicationState,
        trackIds: List<IntId<LocationTrack>>,
    ): List<ValidatedAsset<LocationTrack>> {
        if (trackIds.isEmpty()) return emptyList()

        val target = LayoutContextTransition.publicationIn(branch)
        val validationContext =
            when (state) {
                PublicationState.DRAFT ->
                    createValidationContext(
                        target = target,
                        switches = switchDao.fetchCandidateVersions(target.candidateContext),
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                PublicationState.OFFICIAL -> createValidationContext(target)
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
        branch: LayoutBranch,
        state: PublicationState,
        switchIds: List<IntId<LayoutSwitch>>,
    ): List<ValidatedAsset<LayoutSwitch>> {
        if (switchIds.isEmpty()) return emptyList()
        val target = LayoutContextTransition.publicationIn(branch)

        // Only tracks and switches affect switch validation, so we can ignore the other types in
        // the publication unit
        val validationContext =
            when (state) {
                PublicationState.DRAFT ->
                    createValidationContext(
                        target = target,
                        switches = switchDao.fetchCandidateVersions(target.candidateContext),
                        locationTracks = locationTrackDao.fetchCandidateVersions(target.candidateContext),
                    )

                PublicationState.OFFICIAL -> createValidationContext(target)
            }
        validationContext.preloadSwitchVersions(switchIds)
        validationContext.preloadSwitchTrackLinks(switchIds)
        validationContext.preloadSwitchesByName(switchIds)

        return switchIds.map { id -> ValidatedAsset(id, validateSwitch(id, validationContext)) }
    }

    @Transactional(readOnly = true)
    fun validateKmPosts(
        branch: LayoutBranch,
        state: PublicationState,
        kmPostIds: List<IntId<LayoutKmPost>>,
    ): List<ValidatedAsset<LayoutKmPost>> {
        if (kmPostIds.isEmpty()) return emptyList()
        val target = LayoutContextTransition.publicationIn(branch)

        // We can ignore switches and locationtracks, as they don't affect km-post validity
        val validationContext =
            when (state) {
                PublicationState.DRAFT ->
                    createValidationContext(
                        target = target,
                        kmPosts = kmPostDao.fetchCandidateVersions(target.candidateContext),
                        trackNumbers = trackNumberDao.fetchCandidateVersions(target.candidateContext),
                        referenceLines = referenceLineDao.fetchCandidateVersions(target.candidateContext),
                    )

                PublicationState.OFFICIAL -> createValidationContext(target)
            }

        validationContext.preloadKmPostVersions(kmPostIds)
        validationContext.preloadTrackNumberAndReferenceLineVersions(
            validationContext.collectAssociatedTrackNumberIds(kmPostIds = kmPostIds)
        )

        return kmPostIds.mapNotNull { id ->
            validateKmPost(id, validationContext)?.let { issues -> ValidatedAsset(id, issues) }
        }
    }

    @Transactional(readOnly = true)
    fun validateOperationalPoints(
        branch: LayoutBranch,
        state: PublicationState,
        ids: List<IntId<OperationalPoint>>,
    ): List<ValidatedAsset<OperationalPoint>> {
        if (ids.isEmpty()) return emptyList()
        val target = LayoutContextTransition.publicationIn(branch)
        val validationContext =
            when (state) {
                PublicationState.DRAFT ->
                    createValidationContext(
                        target = target,
                        operationalPoints = operationalPointDao.fetchCandidateVersions(target.candidateContext),
                    )

                PublicationState.OFFICIAL -> createValidationContext(target)
            }

        validationContext.preloadOperationalPointOverlaps(ids)
        validationContext.preloadOperationalPointsByName(ids)
        validationContext.preloadOperationalPointsByAbbreviation(ids)
        validationContext.preloadOperationalPointsByUicCode(ids)
        validationContext.preloadLocationTracksByOperationalPoints(ids)
        validationContext.preloadSwitchesByOperationalPoints(ids)

        return ids.mapNotNull { id ->
            validateOperationalPoint(id, validationContext)?.let { issues -> ValidatedAsset(id, issues) }
        }
    }

    private fun createValidationContext(
        target: LayoutContextTransition,
        trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>> = emptyList(),
        locationTracks: List<LayoutRowVersion<LocationTrack>> = emptyList(),
        referenceLines: List<LayoutRowVersion<ReferenceLine>> = emptyList(),
        switches: List<LayoutRowVersion<LayoutSwitch>> = emptyList(),
        kmPosts: List<LayoutRowVersion<LayoutKmPost>> = emptyList(),
        operationalPoints: List<LayoutRowVersion<OperationalPoint>> = emptyList(),
    ): ValidationContext =
        createValidationContext(
            ValidationVersions(
                target,
                trackNumbers,
                locationTracks,
                referenceLines,
                switches,
                kmPosts,
                operationalPoints,
                emptyList(),
            )
        )

    private fun createValidationContext(publicationSet: ValidationVersions): ValidationContext =
        ValidationContext(
            trackNumberDao = trackNumberDao,
            referenceLineDao = referenceLineDao,
            kmPostDao = kmPostDao,
            locationTrackDao = locationTrackDao,
            switchDao = switchDao,
            operationalPointDao = operationalPointDao,
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
            operationalPoints =
                candidates.operationalPoints.map { candidate ->
                    candidate.copy(issues = validateOperationalPoint(candidate.id, validationContext) ?: emptyList())
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
        versions.operationalPoints.forEach { version ->
            assertNoErrors(version, requireNotNull(validateOperationalPoint(version.id, validationContext)))
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
        val trackNumberExistence = validationContext.getTrackNumberLiveness(id)
        val kmPosts = validationContext.getKmPostsByTrackNumber(id)
        val referenceLine = validationContext.getReferenceLineByTrackNumber(id)
        val locationTracks = validationContext.getLocationTracksByTrackNumber(id)

        val incomingReferencesIssues =
            validateReferencesToTrackNumber(trackNumberExistence, referenceLine, kmPosts, locationTracks)

        if (trackNumber == null) {
            return incomingReferencesIssues
        } else {
            val outgoingReferencesIssues =
                if (trackNumber.referenceLineId == null)
                    listOf(validationError("$VALIDATION_TRACK_NUMBER.reference-line.not-published"))
                else
                    validateReferencesFromTrackNumber(
                        trackNumber,
                        validationContext.getReferenceLineLiveness(trackNumber.referenceLineId),
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
                    validationTargetType = validationContext.target.validationTargetType,
                )
            return incomingReferencesIssues + outgoingReferencesIssues + geocodingIssues + duplicateNameIssues
        }
    }

    private fun validateKmPost(id: IntId<LayoutKmPost>, context: ValidationContext): List<LayoutValidationIssue>? =
        context.getKmPost(id)?.let { kmPost ->
            val trackNumber = kmPost.trackNumberId?.let(context::getTrackNumber)
            val trackNumberNumber = (trackNumber ?: kmPost.trackNumberId?.let(context::getCandidateTrackNumber))?.number
            val referenceLine = trackNumber?.referenceLineId?.let(context::getReferenceLine)

            val referenceIssues =
                if (kmPost.trackNumberId == null) listOf()
                else
                    validateKmPostReferences(
                        kmPost,
                        context.getTrackNumberLiveness(kmPost.trackNumberId),
                        trackNumber?.referenceLineId?.let(context::getReferenceLineLiveness),
                    )

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
                        context.target.validationTargetType,
                    )

            referenceIssues + geocodingIssues + duplicateNumberIssues
        }

    private fun validateSwitch(
        id: IntId<LayoutSwitch>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue> {
        val switch = validationContext.getSwitch(id)
        val switchLiveness = validationContext.getSwitchLiveness(id)
        val linkedTracksAndGeometries = validationContext.getSwitchTracksWithGeometries(id)
        val linkedTracks = linkedTracksAndGeometries.map(Pair<LocationTrack, *>::first)

        val incomingReferencesIssues = validateReferencesToSwitch(switchLiveness, linkedTracks)
        if (switch == null) {
            return incomingReferencesIssues
        } else {
            val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)

            val outgoingReferencesIssues =
                switch.operationalPointId?.let { opId ->
                    validateReferencesFromSwitch(validationContext.getOperationalPointLiveness(opId), switch)
                } ?: emptyList()

            val locationIssues = if (switch.exists) validateSwitchLocation(switch) else emptyList()
            val structureIssues =
                locationIssues.ifEmpty {
                    validateSwitchLocationTrackLinkStructure(switch, structure, linkedTracksAndGeometries)
                }

            val duplicationIssues =
                validateSwitchNameDuplication(
                    switch,
                    validationContext.getSwitchesByName(switch.name),
                    validationContext.target.validationTargetType,
                )
            val oidDuplicationIssues =
                validateSwitchOidDuplication(
                    switch,
                    switch.draftOid?.let(switchDao::lookupByExternalId)?.let { row ->
                        switchDao.get(row.context, row.id)
                    },
                )
            return incomingReferencesIssues +
                outgoingReferencesIssues +
                structureIssues +
                duplicationIssues +
                oidDuplicationIssues
        }
    }

    private fun validateReferenceLine(
        id: IntId<ReferenceLine>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val referenceLineWithAlignment = validationContext.getReferenceLineWithAlignment(id)
        val incomingReferenceIssues =
            validateReferencesToReferenceLine(
                validationContext.getTrackNumberIdByReferenceLine(id)?.let(validationContext::getTrackNumber),
                validationContext.getReferenceLineLiveness(id),
            )
        return if (referenceLineWithAlignment == null) incomingReferenceIssues
        else {
            val (referenceLine, alignment) = referenceLineWithAlignment
            val trackNumber = validationContext.getTrackNumber(referenceLine.trackNumberId)
            val outgoingReferenceIssues =
                validateReferencesFromReferenceLine(
                    referenceLine = referenceLine,
                    trackNumberNumber =
                        (validationContext.getTrackNumber(referenceLine.trackNumberId)
                                ?: validationContext.getCandidateTrackNumber(referenceLine.trackNumberId))
                            ?.number,
                    trackNumberLiveness = validationContext.getTrackNumberLiveness(referenceLine.trackNumberId),
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

            return incomingReferenceIssues + outgoingReferenceIssues + alignmentIssues + geocodingIssues
        }
    }

    private fun validateLocationTrack(
        id: IntId<LocationTrack>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val trackLiveness = validationContext.getLocationTrackLiveness(id)
        val trackAndGeometry = validationContext.getLocationTrackWithGeometry(id)
        // cancelling a track's creation can cause switches to become disconnected
        val trackNetworkTopologyIssues =
            validationContext.getPotentiallyAffectedSwitches(id).filter(LayoutSwitch::exists).flatMap { switch ->
                val structure = switchLibraryService.getSwitchStructure(switch.switchStructureId)
                val switchTracks = validationContext.getSwitchTracksWithGeometries(switch.id as IntId)
                validateSwitchTopologicalConnectivity(switch, structure, switchTracks, trackAndGeometry?.first)
            }
        val incomingReferenceIssues =
            validateReferencesToLocationTrack(trackLiveness, validationContext.getDuplicateTracks(id))

        return if (trackAndGeometry == null) {
            trackNetworkTopologyIssues + incomingReferenceIssues
        } else {
            val (track, geometry) = trackAndGeometry
            val trackNumber = validationContext.getTrackNumber(track.trackNumberId)
            val trackNumberName =
                (trackNumber ?: validationContext.getCandidateTrackNumber(track.trackNumberId))?.number

            val outgoingReferenceIssues =
                validateReferencesFromLocationTrack(
                    validationContext.getTrackNumberLiveness(track.trackNumberId),
                    geometry.switchIds.map(validationContext::getSwitchLiveness),
                    track.operationalPointIds.map(validationContext::getOperationalPointLiveness),
                    track.duplicateOf?.let(validationContext::getLocationTrackLiveness),
                    track,
                )

            val switchTrackLinkings: List<SwitchTrackLinking> = validationContext.getSwitchTrackLinks(geometry)
            val switchTrackIssues = validateTrackSwitchLinkingGeometry(track, switchTrackLinkings)

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
            val duplicateOfName =
                track.duplicateOf?.let { duplicateOfId ->
                    (validationContext.getLocationTrack(duplicateOfId)
                            ?: validationContext.getCandidateLocationTrack(duplicateOfId))
                        ?.name
                }
            val duplicateIssues =
                if (track.duplicateOf != null)
                    validateDuplicateStructure(duplicateOf, duplicateOfName, duplicatesAfterPublication)
                else listOf()

            val alignmentIssues =
                if (track.exists) {
                    validateLocationTrackGeometry(geometry) +
                        validateEdges(geometry) { id -> requireNotNull(validationContext.getSwitch(id)).name }
                } else listOf()
            val geocodingIssues =
                if (track.exists && trackNumber != null && geometry.isNotEmpty) {
                    validationContext.getGeocodingContextCacheKey(track.trackNumberId)?.let { key ->
                        validateAddressPoints(trackNumber, key, track, VALIDATION_LOCATION_TRACK)
                    } ?: listOf(noGeocodingContext(VALIDATION_LOCATION_TRACK))
                } else listOf()

            val switchNameIssues =
                if (track.exists) {
                    val startSwitch = track.startSwitchId?.let(validationContext::getSwitch)
                    val endSwitch = track.endSwitchId?.let(validationContext::getSwitch)
                    validateLocationTrackEndSwitchNames(track, startSwitch, endSwitch)
                } else listOf()

            val tracksWithSameName = validationContext.getLocationTracksByName(track.name)
            val nameDuplicationIssues =
                validateLocationTrackNameDuplication(
                    track,
                    trackNumberName,
                    tracksWithSameName,
                    validationContext.target.validationTargetType,
                )

            (switchTrackIssues +
                duplicateIssues +
                alignmentIssues +
                geocodingIssues +
                switchNameIssues +
                nameDuplicationIssues +
                trackNetworkTopologyIssues +
                incomingReferenceIssues +
                outgoingReferenceIssues +
                switchConnectivityIssues)
        }
    }

    private fun validateOperationalPoint(
        id: IntId<OperationalPoint>,
        validationContext: ValidationContext,
    ): List<LayoutValidationIssue>? {
        val operationalPoint = validationContext.getOperationalPoint(id)
        val switches = validationContext.getSwitchesByOperationalPoint(id)
        val locationTracks = validationContext.getLocationTracksByOperationalPoint(id)
        val liveness = validationContext.getOperationalPointLiveness(id)

        val incomingReferenceIssues = validateReferencesToOperationalPoint(liveness, switches, locationTracks)

        return if (operationalPoint == null || !operationalPoint.exists) incomingReferenceIssues
        else {
            val operationalPointVersion = requireNotNull(operationalPoint.version)
            val alivePointIssues =
                validateAliveOperationalPoint(id, validationContext, operationalPoint, operationalPointVersion)
            incomingReferenceIssues + alivePointIssues
        }
    }

    private fun validateAliveOperationalPoint(
        id: IntId<OperationalPoint>,
        validationContext: ValidationContext,
        operationalPoint: OperationalPoint,
        operationalPointVersion: LayoutRowVersion<OperationalPoint>,
    ): List<LayoutValidationIssue> {
        val nameDuplicationIssues =
            validateOperationalPointNameDuplication(
                operationalPoint,
                validationContext.getOperationalPointsByName(operationalPoint.name),
                validationContext.target.validationTargetType,
            )
        val abbreviationDuplicationIssues =
            if (operationalPoint.abbreviation == null) listOf()
            else
                validateOperationalPointAbbreviationDuplication(
                    operationalPoint,
                    validationContext.getOperationalPointsByAbbreviation(operationalPoint.abbreviation),
                    validationContext.target.validationTargetType,
                )
        val uicCodeIssues =
            if (operationalPoint.uicCode == null)
                listOf(validationError("$VALIDATION_OPERATIONAL_POINT.uic-code-missing"))
            else
                validateOperationalPointUicCodeDuplication(
                    operationalPoint,
                    validationContext.getOperationalPointsByUicCode(operationalPoint.uicCode),
                    validationContext.target.validationTargetType,
                )
        val rinfCodeIssues =
            listOfNotNull(
                validate(operationalPoint.rinfType != null) { "$VALIDATION_OPERATIONAL_POINT.rinf-code-missing" }
            )
        val polygonOverlapIssues =
            validateOperationalPointPolygonOverlap(
                operationalPoint,
                validationContext.getOverlappingOperationalPoints(id),
                validationContext.target.validationTargetType,
            )
        val locationIssues =
            listOfNotNull(
                validate(operationalPoint.polygon != null) { "$VALIDATION_OPERATIONAL_POINT.polygon-missing" },
                validate(operationalPoint.location != null) { "$VALIDATION_OPERATIONAL_POINT.location-missing" },
            )

        val geometryQuality = operationalPointDao.getGeometryQuality(operationalPointVersion)
        val geometryQualityIssues =
            listOfNotNull(
                validate(geometryQuality?.polygonContainsLocation ?: true) {
                    "$VALIDATION_OPERATIONAL_POINT.location-outside-polygon"
                },
                validate(geometryQuality?.polygonIsSimple ?: true) {
                    "$VALIDATION_OPERATIONAL_POINT.polygon-is-not-simple"
                },
            )

        return nameDuplicationIssues +
            abbreviationDuplicationIssues +
            uicCodeIssues +
            rinfCodeIssues +
            polygonOverlapIssues +
            locationIssues +
            geometryQualityIssues
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
        if (!track.exists || track.length.distance == 0.0) {
            listOf()
        } else {
            validateAddressPoints(trackNumber, track, validationTargetLocalizationPrefix) {
                geocodingService.getAddressPoints(contextKey, track.getVersionOrThrow())
            }
        }
}
