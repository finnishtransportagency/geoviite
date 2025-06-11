package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.aspects.GeoviiteService
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.error.PublicationFailureException
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.switches.SuggestedSwitch
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.ValidationContext
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.publication.validationError
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.AugLocationTrack
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackDescription
import fi.fta.geoviite.infra.tracklayout.DbLocationTrackNaming
import fi.fta.geoviite.infra.tracklayout.IAlignment
import fi.fta.geoviite.infra.tracklayout.ILocationTrack
import fi.fta.geoviite.infra.tracklayout.LayoutAlignmentDao
import fi.fta.geoviite.infra.tracklayout.LayoutContextData
import fi.fta.geoviite.infra.tracklayout.LayoutEdge
import fi.fta.geoviite.infra.tracklayout.LayoutKmPostDao
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutSwitchService
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDao
import fi.fta.geoviite.infra.tracklayout.LocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.LocationTrackService
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.ReferenceLineDao
import fi.fta.geoviite.infra.tracklayout.TmpLocationTrackGeometry
import fi.fta.geoviite.infra.tracklayout.TopologicalConnectivityType
import fi.fta.geoviite.infra.tracklayout.topologicalConnectivityTypeOf
import fi.fta.geoviite.infra.util.produceIf
import java.time.Instant
import org.springframework.http.HttpStatus
import org.springframework.transaction.annotation.Transactional

@GeoviiteService
class SplitService(
    private val splitDao: SplitDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchLinkingService: SwitchLinkingService,
    private val switchLibraryService: SwitchLibraryService,
    private val switchService: LayoutSwitchService,
    private val alignmentDao: LayoutAlignmentDao,
) {
    fun getChangeTime(): Instant {
        return splitDao.fetchChangeTime()
    }

    /**
     * Fetches all splits that are not published. Can be filtered by location tracks or switches. If both filters are
     * defined, the result is combined by OR (match by either).
     */
    fun findUnpublishedSplits(
        branch: LayoutBranch,
        locationTrackIds: List<IntId<LocationTrack>>? = null,
        switchIds: List<IntId<LayoutSwitch>>? = null,
    ): List<Split> =
        findUnfinishedSplits(branch, locationTrackIds, switchIds).filter { split -> split.publicationId == null }

    /**
     * Fetches all splits that are not marked as DONE. Can be filtered by location tracks or switches. If both filters
     * are defined, the result is combined by OR (match by either).
     */
    fun findUnfinishedSplits(
        branch: LayoutBranch,
        locationTrackIds: List<IntId<LocationTrack>>? = null,
        switchIds: List<IntId<LayoutSwitch>>? = null,
    ): List<Split> =
        splitDao.fetchUnfinishedSplits(branch).filter { split ->
            val containsTrack = locationTrackIds?.any(split::containsLocationTrack)
            val containsSwitch = switchIds?.any(split::containsSwitch)
            when {
                containsTrack != null && containsSwitch != null -> containsTrack || containsSwitch
                containsTrack != null -> containsTrack
                containsSwitch != null -> containsSwitch
                else -> true
            }
        }

    fun fetchPublicationVersions(
        branch: LayoutBranch,
        locationTracks: List<IntId<LocationTrack>>,
        switches: List<IntId<LayoutSwitch>>,
    ): List<RowVersion<Split>> =
        findUnpublishedSplits(branch, locationTracks, switches).map { split -> split.rowVersion }

    @Transactional
    fun publishSplit(
        validatedSplitVersions: List<RowVersion<Split>>,
        locationTracks: Collection<LayoutRowVersion<LocationTrack>>,
        publicationId: IntId<Publication>,
    ): List<RowVersion<Split>> {
        return validatedSplitVersions.map { splitVersion ->
            val split = splitDao.getOrThrow(splitVersion.id)
            val track =
                requireNotNull(locationTracks.find { t -> t.id == split.sourceLocationTrackId }) {
                    "Source track must be part of the same publication as the split: split=$split"
                }
            splitDao.updateSplit(splitId = split.id, publicationId = publicationId, sourceTrackVersion = track).also {
                updatedVersion ->
                // Sanity-check the version for conflicting update, though this should not be
                // possible
                if (updatedVersion != splitVersion.next()) {
                    throw PublicationFailureException(
                        message = "Split version has changed between validation and publication: split=${split.id}",
                        localizedMessageKey = "split-version-changed",
                        status = HttpStatus.CONFLICT,
                    )
                }
            }
        }
    }

    @Transactional
    fun deleteSplit(splitId: IntId<Split>) {
        splitDao.deleteSplit(splitId)
    }

    @Transactional(readOnly = true)
    fun validateSplit(
        candidates: ValidationVersions,
        context: ValidationContext,
        allowMultipleSplits: Boolean,
        getLocationTrackName: (IntId<LocationTrack>) -> AlignmentName,
    ): SplitLayoutValidationIssues {
        val splitIssues =
            validateSplitContent(
                trackVersions = candidates.locationTracks,
                switchVersions = candidates.switches,
                publicationSplits = context.getPublicationSplits(),
                allowMultipleSplits = allowMultipleSplits,
            )

        val tnSplitIssues =
            candidates.trackNumbers
                .associate { version ->
                    version.id to
                        listOfNotNull(validateSplitReferencesByTrackNumber(version.id, context, getLocationTrackName))
                }
                .filterValues { it.isNotEmpty() }

        val rlSplitIssues =
            candidates.referenceLines
                .associate { version ->
                    val trackNumberId = referenceLineDao.fetch(version).trackNumberId
                    version.id to
                        listOfNotNull(
                            validateSplitReferencesByTrackNumber(trackNumberId, context, getLocationTrackName)
                        )
                }
                .filterValues { it.isNotEmpty() }

        val kpSplitIssues =
            candidates.kmPosts
                .associate { version ->
                    val trackNumberId = kmPostDao.fetch(version).trackNumberId
                    version.id to
                        listOfNotNull(
                            trackNumberId?.let { tnId ->
                                validateSplitReferencesByTrackNumber(tnId, context, getLocationTrackName)
                            }
                        )
                }
                .filterValues { it.isNotEmpty() }

        val trackSplitIssues =
            candidates.locationTracks
                .associate { version ->
                    val ltSplitIssues = validateSplitForLocationTrack(version.id, context, getLocationTrackName)
                    val contentIssues =
                        splitIssues.mapNotNull { (split, error) ->
                            if (split.containsLocationTrack(version.id)) error else null
                        }
                    version.id to ltSplitIssues + contentIssues
                }
                .filterValues { it.isNotEmpty() }

        val switchSplitIssues =
            candidates.switches.associate { version ->
                version.id to
                    splitIssues.mapNotNull { (split, error) -> if (split.containsSwitch(version.id)) error else null }
            }

        return SplitLayoutValidationIssues(
            tnSplitIssues,
            rlSplitIssues,
            kpSplitIssues,
            trackSplitIssues,
            switchSplitIssues,
        )
    }

    fun getSplitIdByPublicationId(publicationId: IntId<Publication>): IntId<Split>? {
        return splitDao.fetchSplitIdByPublication(publicationId)
    }

    fun get(splitId: IntId<Split>): Split? {
        return splitDao.get(splitId)
    }

    fun getOrThrow(splitId: IntId<Split>): Split {
        return splitDao.getOrThrow(splitId)
    }

    private fun validateSplitForLocationTrack(
        trackId: IntId<LocationTrack>,
        context: ValidationContext,
        getLocationTrackName: (IntId<LocationTrack>) -> AlignmentName,
    ): List<LayoutValidationIssue> {
        val splits = context.getUnfinishedSplits().filter { split -> split.locationTracks.contains(trackId) }
        val track = context.getAugLocationTrack(trackId)

        return if (track == null) validateLocationTrackAbsence(trackId, context, splits, getLocationTrackName)
        else
            validateSplitForFoundLocationTrack(
                trackId,
                context,
                splits.map { split ->
                    split to
                        requireNotNull(context.getAugLocationTrack(split.sourceLocationTrackId)) {
                            "Split source track must exist in the validation context: id=${split.sourceLocationTrackId}"
                        }
                },
                track,
            )
    }

    private fun validateLocationTrackAbsence(
        trackId: IntId<LocationTrack>,
        context: ValidationContext,
        splits: List<Split>,
        getLocationTrackName: (IntId<LocationTrack>) -> AlignmentName,
    ): List<LayoutValidationIssue> {
        if (!context.locationTrackIsCancelled(trackId)) {
            throw IllegalArgumentException("The track to validate must exist in the validation context: id=$trackId")
        }
        return splits.flatMap { split ->
            if (trackId == split.sourceLocationTrackId || split.locationTracks.contains(trackId)) {
                listOf(
                    validationError(
                        "$VALIDATION_SPLIT.track-is-cancelled",
                        "name" to
                            context.getCandidateLocationTrack(trackId)?.let { track ->
                                getLocationTrackName(track.id as IntId)
                            },
                    )
                )
            } else listOf()
        }
    }

    private fun validateSplitForFoundLocationTrack(
        trackId: IntId<LocationTrack>,
        context: ValidationContext,
        splits: List<Pair<Split, AugLocationTrack>>,
        track: AugLocationTrack,
    ): List<LayoutValidationIssue> {
        val splitSourceLocationTrackErrors =
            splits.flatMap { (split, _) ->
                if (split.sourceLocationTrackId == trackId) validateSplitSourceLocationTrack(track, split)
                else emptyList()
            }

        val statusErrors = splits.mapNotNull { (split, sourceTrack) -> validateSplitStatus(track, sourceTrack, split) }

        // Note: we only check draft splits from here on, since the situation cannot change after
        // publication:
        // - Official tracks are already validated and published
        // - The above status check will not allow draft tracks to be involved in published pending
        // splits
        // - Published DONE splits don't matter and shouldn't affect future changes
        // - Changes to the geocoding context are blocked for any tracks associated with a split
        val draftSplits = splits.filter { (split, _) -> split.publicationId == null }

        val trackNumberMismatchErrors =
            draftSplits.mapNotNull { (split, sourceTrack) ->
                produceIf(split.containsTargetTrack(trackId)) {
                    validateTargetTrackNumberIsUnchanged(sourceTrack, track)
                }
            }

        // Geometry error checking is skipped if the track numbers are different between any source
        // & target tracks,
        // The geometry check will rarely if ever succeed for differing track numbers on source &
        // target tracks, and
        // differing track number for any source & target track is already a considered a
        // publication-blocking error.
        val splitGeometryErrors =
            when {
                trackNumberMismatchErrors.isEmpty() -> validateSplitGeometries(trackId, draftSplits, context)
                else -> emptyList()
            }

        return listOf(statusErrors, splitSourceLocationTrackErrors, trackNumberMismatchErrors, splitGeometryErrors)
            .flatten()
    }

    private fun validateSplitGeometries(
        trackId: IntId<LocationTrack>,
        splits: List<Pair<Split, AugLocationTrack>>,
        context: ValidationContext,
    ): List<LayoutValidationIssue> {
        // Target address validation ensures that all split target addresspoints match the source
        // addresspoints in
        // the validation context (that is: draft target geometry is a subset of draft source
        // geometry)
        val targetGeometryErrors =
            splits.mapNotNull { (split, sourceTrack) ->
                split.getTargetLocationTrack(trackId)?.let { target ->
                    val (sourceAddresses, targetAddresses) =
                        getTargetValidationAddressPoints(sourceTrack, target, context)
                    validateTargetGeometry(target.operation, targetAddresses, sourceAddresses)
                }
            }

        // Source address validation ensures that the in-validationcontext source addresspoints are
        // unchanged from the
        // official ones (that is: draft source geometry == official source geometry)
        val sourceGeometryErrors =
            splits.mapNotNull { (_, sourceTrack) ->
                produceIf(trackId == sourceTrack.id) {
                    val draftAddresses = context.getAddressPoints(sourceTrack)
                    val officialAddresses = geocodingService.getAddressPoints(context.target.baseContext, trackId)
                    validateSourceGeometry(draftAddresses, officialAddresses)
                }
            }

        return targetGeometryErrors + sourceGeometryErrors
    }

    private fun getTargetValidationAddressPoints(
        sourceTrack: AugLocationTrack,
        target: SplitTarget,
        context: ValidationContext,
    ): Pair<List<AddressPoint>?, List<AddressPoint>?> {
        val sourceGeometry = alignmentDao.fetch(sourceTrack.versionOrThrow)
        val (sourceStartPoint, sourceEndPoint) = sourceGeometry.getEdgeStartAndEnd(target.edgeIndices)
        val sourceAddressPointRange =
            context.getGeocodingContext(sourceTrack.trackNumberId)?.let { geocodingContext ->
                val start = geocodingContext.toAddressPoint(sourceStartPoint)?.first
                val end = geocodingContext.toAddressPoint(sourceEndPoint)?.first
                if (start != null && end != null) start to end else null
            }
        val sourceAddresses: List<AddressPoint>? =
            sourceAddressPointRange?.let { (start, end) ->
                context
                    .getAddressPoints(sourceTrack)
                    ?.midPoints
                    ?.filter { p -> p.address > start.address && p.address < end.address }
                    ?.let { midPoints ->
                        listOfNotNull(start.withIntegerPrecision()) +
                            midPoints +
                            listOfNotNull(end.withIntegerPrecision())
                    }
            }

        val targetAddresses =
            sourceAddressPointRange?.let { (sourceStart, sourceEnd) ->
                val addressRange = sourceStart.address..sourceEnd.address
                context.getAddressPoints(target.locationTrackId)?.integerPrecisionPoints?.let { points ->
                    if (target.operation == SplitTargetOperation.TRANSFER) {
                        points.filter { p -> p.address in addressRange }
                    } else {
                        points
                    }
                }
            }

        return sourceAddresses to targetAddresses
    }

    private fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<LayoutTrackNumber>,
        context: ValidationContext,
        getLocationTrackName: (IntId<LocationTrack>) -> AlignmentName,
    ): LayoutValidationIssue? {
        val affectedTracks = context.getLocationTracksByTrackNumber(trackNumberId)
        val affectedSplits =
            context.getUnfinishedSplits().filter { split ->
                split.locationTracks.any { ltId -> affectedTracks.any { a -> a.id == ltId } }
            }
        return if (affectedSplits.isNotEmpty()) {
            val sourceTrackNames =
                affectedSplits
                    .mapNotNull { split ->
                        context.getLocationTrack(split.sourceLocationTrackId)?.let { track ->
                            getLocationTrackName(track.id as IntId)
                        }
                    }
                    .joinToString(", ")
            validationError("$VALIDATION_SPLIT.affected-split-in-progress", "sourceName" to sourceTrackNames)
        } else {
            null
        }
    }

    @Transactional
    fun updateSplit(
        splitId: IntId<Split>,
        bulkTransferState: BulkTransferState,
        bulkTransferId: IntId<BulkTransfer>? = null,
    ): RowVersion<Split> {
        return splitDao.getOrThrow(splitId).let { split ->
            splitDao.updateSplit(
                splitId = split.id,
                bulkTransferState = bulkTransferState,
                bulkTransferId = bulkTransferId,
            )
        }
    }

    @Transactional
    fun split(branch: LayoutBranch, request: SplitRequest): IntId<Split> {
        // Original duplicate ids to be stored in split data before updating the location track
        // references which they referenced before the split (request source track).
        // If the references are not updated, the duplicate-of reference will be removed entirely
        // when the
        // source track's layout state is set to "DELETED".
        val sourceTrackDuplicateIds =
            locationTrackService.fetchDuplicates(branch.draft, request.sourceTrackId).map { duplicateTrack ->
                duplicateTrack.id as IntId
            }

        val sourceTrack = locationTrackService.getAugLocationTrackOrThrow(request.sourceTrackId, branch.draft)
        if (sourceTrack.state != LocationTrackState.IN_USE) {
            throw SplitFailureException(
                message = "Source track state is not IN_USE: id=${sourceTrack.id}",
                localizedMessageKey = "source-track-state-not-in-use",
            )
        }

        val suggestions =
            switchLinkingService.getTrackSwitchSuggestions(branch.draft, sourceTrack).let(::verifySwitchSuggestions)
        val relinkedSwitches = switchLinkingService.relinkTrack(branch, request.sourceTrackId).map { it.id }

        // Fetch post-re-linking track & alignment
        val (track, geometry) = locationTrackService.getWithGeometryOrThrow(branch.draft, request.sourceTrackId)
        val targetResults =
            splitLocationTrack(
                track = track,
                geometry = geometry,
                targets = collectSplitTargetParams(branch, request.targetTracks, suggestions),
            )

        val savedSplitTargetLocationTracks =
            targetResults.map { result ->
                val response = locationTrackService.saveDraft(branch, result.locationTrack, result.geometry)
                val (resultTrack, resultGeometry) = locationTrackService.getWithGeometry(response)
                result.copy(locationTrack = resultTrack, geometry = resultGeometry)
            }

        geocodingService.getGeocodingContext(branch.draft, sourceTrack.trackNumberId)?.let { geocodingContext ->
            updateUnusedDuplicateReferencesToSplitTargetTracks(
                branch,
                geocodingContext,
                request,
                savedSplitTargetLocationTracks,
            )
        }
            ?: throw SplitFailureException(
                message = "Geocoding context creation failed: trackNumber=${sourceTrack.trackNumberId}",
                localizedMessageKey = "geocoding-failed",
                localizationParams = localizationParams("trackName" to sourceTrack.name),
            )

        val savedSource = locationTrackService.updateState(branch, request.sourceTrackId, LocationTrackState.DELETED)

        return savedSplitTargetLocationTracks
            .map { splitTargetResult ->
                SplitTarget(
                    locationTrackId = splitTargetResult.locationTrack.id as IntId,
                    edgeIndices = splitTargetResult.edgeIndices,
                    operation = splitTargetResult.operation,
                )
            }
            .let { splitTargets ->
                splitDao.saveSplit(savedSource, splitTargets, relinkedSwitches, sourceTrackDuplicateIds)
            }
    }

    private fun updateUnusedDuplicateReferencesToSplitTargetTracks(
        branch: LayoutBranch,
        geocodingContext: GeocodingContext,
        splitRequest: SplitRequest,
        splitTargetResults: List<SplitTargetResult>,
    ) {
        val unusedDuplicates =
            locationTrackService
                .fetchAugDuplicates(branch.draft, splitRequest.sourceTrackId)
                .filter { locationTrackDuplicate ->
                    !splitRequest.targetTracks.any { targetTrack ->
                        targetTrack.duplicateTrack?.id == locationTrackDuplicate.id
                    }
                }
                .let { unusedDuplicateTracks -> locationTrackService.getAlignmentsForTracks(unusedDuplicateTracks) }

        findNewLocationTracksForUnusedDuplicates(geocodingContext, unusedDuplicates, splitTargetResults).forEach {
            updatedDuplicate ->
            locationTrackService.saveDraft(branch, updatedDuplicate.first.dbTrack, updatedDuplicate.second)
        }
    }

    private fun collectSplitTargetParams(
        branch: LayoutBranch,
        targets: List<SplitRequestTarget>,
        suggestions: List<Pair<IntId<LayoutSwitch>, SuggestedSwitch>>,
    ): List<SplitTargetParams> {
        return targets.map { target ->
            val startSwitch =
                target.startAtSwitchId?.let { switchId ->
                    val (jointNumber, name) =
                        suggestions
                            .find { (id, _) -> id == switchId }
                            ?.let { (_, suggestion) ->
                                val joint =
                                    switchLibraryService.getPresentationJointNumber(suggestion.switchStructureId)
                                val name = switchService.getOrThrow(branch.draft, switchId).name
                                joint to name
                            }
                            ?: throw SplitFailureException(
                                message = "No re-linked switch for switch: id=$switchId",
                                localizedMessageKey = "no-switch-suggestion",
                            )
                    SplitPointSwitch(switchId, jointNumber, name)
                }
            val duplicate =
                target.duplicateTrack?.let { d ->
                    val (track, alignment) = locationTrackService.getWithGeometryOrThrow(branch.draft, d.id)
                    SplitTargetDuplicate(d.operation, track, alignment)
                }
            SplitTargetParams(target, startSwitch, duplicate)
        }
    }
}

data class SplitPointSwitch(val id: IntId<LayoutSwitch>, val jointNumber: JointNumber, val name: SwitchName)

data class SplitTargetParams(
    val request: SplitRequestTarget,
    val startSwitch: SplitPointSwitch?,
    val duplicate: SplitTargetDuplicate?,
) {
    fun getOperation(): SplitTargetOperation =
        duplicate?.operation?.toSplitTargetOperation() ?: SplitTargetOperation.CREATE
}

data class SplitTargetDuplicate(
    val operation: SplitTargetDuplicateOperation,
    val track: LocationTrack,
    val geometry: LocationTrackGeometry,
)

data class SplitTargetResult(
    val locationTrack: LocationTrack,
    val geometry: LocationTrackGeometry,
    val edgeIndices: IntRange,
    val operation: SplitTargetOperation,
)

fun splitLocationTrack(
    track: LocationTrack,
    geometry: LocationTrackGeometry,
    targets: List<SplitTargetParams>,
): List<SplitTargetResult> {
    return targets
        .mapIndexed { index, target ->
            val nextSwitch = targets.getOrNull(index + 1)?.startSwitch
            val edgeIndices = findSplitEdgeIndices(geometry, target.startSwitch, nextSwitch)
            val edges: List<LayoutEdge> = cutEdges(geometry, edgeIndices)
            val connectivityType = calculateTopologicalConnectivity(track, geometry.segments.size, edgeIndices)
            val (newTrack, newGeometry) =
                target.duplicate?.let { d ->
                    when (d.operation) {
                        SplitTargetDuplicateOperation.TRANSFER ->
                            updateSplitTargetForTransferAssets(
                                duplicateTrack = d.track,
                                topologicalConnectivityType = connectivityType,
                            ) to d.geometry
                        SplitTargetDuplicateOperation.OVERWRITE ->
                            updateSplitTargetForOverwriteDuplicate(
                                sourceTrack = track,
                                duplicateTrack = d.track,
                                request = target.request,
                                edges = edges,
                                topologicalConnectivityType = connectivityType,
                            )
                    }
                } ?: createSplitTarget(track, target.request, edges, connectivityType)
            SplitTargetResult(
                locationTrack = newTrack,
                geometry = newGeometry,
                edgeIndices = edgeIndices,
                operation = target.getOperation(),
            )
        }
        .also { result -> validateSplitResult(result, geometry) }
}

fun validateSplitResult(results: List<SplitTargetResult>, geometry: LocationTrackGeometry) {
    results.forEachIndexed { index, result ->
        val previousIndices = results.getOrNull(index - 1)?.edgeIndices
        val previousEndIndex = previousIndices?.last ?: -1
        if (previousEndIndex + 1 != result.edgeIndices.first) {
            throw SplitFailureException(
                message =
                    "Not all edges were allocated in the split: last=${previousIndices?.last} first=${result.edgeIndices.first}",
                localizedMessageKey = "segment-allocation-failed",
            )
        }
        if (
            result.operation != SplitTargetOperation.TRANSFER &&
                result.geometry.edges.size != result.edgeIndices.count()
        ) {
            throw SplitFailureException(
                message =
                    "Split target edges don't match calculated indices: edges=${result.geometry.edges.size} indices=${result.edgeIndices.count()}",
                localizedMessageKey = "segment-allocation-failed",
            )
        }
    }
    if (results.last().edgeIndices.last != geometry.edges.lastIndex) {
        throw SplitFailureException(
            message =
                "Not all edges were allocated in the split: lastIndex=${results.last().edgeIndices.last} lastGeometryIndex=${geometry.edges.lastIndex}",
            localizedMessageKey = "segment-allocation-failed",
        )
    }
}

private fun updateSplitTargetForTransferAssets(
    duplicateTrack: LocationTrack,
    topologicalConnectivityType: TopologicalConnectivityType,
): LocationTrack {
    return duplicateTrack.copy(
        // After split, the track is no longer duplicate
        duplicateOf = null,
        topologicalConnectivity = topologicalConnectivityType,
    )
}

private fun updateSplitTargetForOverwriteDuplicate(
    sourceTrack: LocationTrack,
    duplicateTrack: LocationTrack,
    request: SplitRequestTarget,
    edges: List<LayoutEdge>,
    topologicalConnectivityType: TopologicalConnectivityType,
): Pair<LocationTrack, LocationTrackGeometry> {
    val newGeometry = TmpLocationTrackGeometry.of(edges, duplicateTrack.id as? IntId)
    val newTrack =
        duplicateTrack.copy(
            dbName = DbLocationTrackNaming.of(request.namingScheme, request.nameFreeText, request.nameSpecifier),
            dbDescription =
                DbLocationTrackDescription(
                    descriptionBase = request.descriptionBase,
                    descriptionSuffix = request.descriptionSuffix,
                ),

            // After split, the track is no longer duplicate
            duplicateOf = null,
            topologicalConnectivity = topologicalConnectivityType,
            state = sourceTrack.state,
            trackNumberId = sourceTrack.trackNumberId,
            sourceId = sourceTrack.sourceId,
            // owner remains that of the duplicate
            type = sourceTrack.type,

            // Geometry fields come from alignment
            segmentCount = newGeometry.segments.size,
            length = newGeometry.length,
            boundingBox = newGeometry.boundingBox,
        )
    return newTrack to newGeometry
}

private fun createSplitTarget(
    sourceTrack: ILocationTrack,
    request: SplitRequestTarget,
    edges: List<LayoutEdge>,
    topologicalConnectivityType: TopologicalConnectivityType,
): Pair<LocationTrack, LocationTrackGeometry> {
    val newGeometry = TmpLocationTrackGeometry.of(edges, null)
    val newTrack =
        LocationTrack(
            dbName = DbLocationTrackNaming.of(request.namingScheme, request.nameFreeText, request.nameSpecifier),
            dbDescription =
                DbLocationTrackDescription(
                    descriptionBase = request.descriptionBase,
                    descriptionSuffix = request.descriptionSuffix,
                ),

            // After split, tracks are not duplicates
            duplicateOf = null,
            topologicalConnectivity = topologicalConnectivityType,
            state = sourceTrack.state,
            trackNumberId = sourceTrack.trackNumberId,
            sourceId = sourceTrack.sourceId,
            ownerId = sourceTrack.ownerId,
            type = sourceTrack.type,

            // Geometry fields come from alignment
            segmentCount = newGeometry.segments.size,
            length = newGeometry.length,
            boundingBox = newGeometry.boundingBox,
            // TODO: GVT-2399 Split in design branches
            contextData = LayoutContextData.newDraft(LayoutBranch.main, id = null),
        )
    return newTrack to newGeometry
}

private fun calculateTopologicalConnectivity(
    sourceTrack: ILocationTrack,
    sourceEdges: Int,
    edgeIndices: ClosedRange<Int>,
): TopologicalConnectivityType {
    val startConnected =
        if (0 == edgeIndices.start) {
            sourceTrack.topologicalConnectivity.isStartConnected()
        } else {
            true
        }
    val endConnected =
        if (sourceEdges == edgeIndices.endInclusive + 1) {
            sourceTrack.topologicalConnectivity.isEndConnected()
        } else {
            true
        }
    return topologicalConnectivityTypeOf(startConnected, endConnected)
}

private fun findSplitEdgeIndices(
    geometry: LocationTrackGeometry,
    startSwitch: SplitPointSwitch?,
    endSwitch: SplitPointSwitch?,
): IntRange {
    val startIndex =
        startSwitch?.let { (s, j) ->
            findIndex(geometry, 0, s, j) ?: throwSwitchSegmentMappingFailure(geometry, startSwitch)
        } ?: 0
    val endIndex =
        endSwitch?.let { (s, j) ->
            findIndex(geometry, startIndex, s, j)?.let(Int::dec)
                ?: throwSwitchSegmentMappingFailure(geometry, endSwitch)
        } ?: geometry.edges.lastIndex

    return (startIndex..endIndex)
}

private fun throwSwitchSegmentMappingFailure(geometry: LocationTrackGeometry, switch: SplitPointSwitch?): Nothing {
    val geometrySwitches = geometry.trackSwitchLinks.joinToString(", ") { s -> "${s.switchId}[${s.jointNumber}]" }
    throw SplitFailureException(
        message = "Failed to map split switches to segment indices: switch=$switch geometrySwitches=$geometrySwitches",
        localizedMessageKey = "switch-segment-mapping-failed",
        localizationParams = localizationParams("switchName" to switch?.name, "joint" to switch?.jointNumber),
    )
}

private fun findIndex(
    geometry: LocationTrackGeometry,
    startIndex: Int,
    switchId: IntId<LayoutSwitch>,
    joint: JointNumber,
): Int? = geometry.nodes.indexOfFirst { node -> node.containsJoint(switchId, joint) }.takeIf { it >= startIndex }

private fun cutEdges(geometry: LocationTrackGeometry, indices: ClosedRange<Int>): List<LayoutEdge> =
    geometry.edges.subList(indices.start, indices.endInclusive + 1)

private fun verifySwitchSuggestions(
    suggestions: List<Pair<IntId<LayoutSwitch>, SuggestedSwitch?>>
): List<Pair<IntId<LayoutSwitch>, SuggestedSwitch>> =
    suggestions.map { (id, suggestion) ->
        if (suggestion == null) {
            throw SplitFailureException(
                message = "Switch re-linking failed to produce a suggestion: switch=$id",
                localizedMessageKey = "switch-linking-failed",
            )
        } else {
            id to suggestion
        }
    }

private data class GeocodedLocationTrack<T : ILocationTrack>(
    val track: T,
    val geometry: LocationTrackGeometry,
    val startAndEnd: AlignmentStartAndEndMeters,
)

private fun <T : ILocationTrack> getGeocoded(
    context: GeocodingContext,
    track: T,
    geometry: LocationTrackGeometry,
): GeocodedLocationTrack<T>? =
    getAlignmentStartAndEndM(context, geometry)?.let { startAndEnd ->
        GeocodedLocationTrack(track, geometry, startAndEnd)
    }

private fun findNewLocationTracksForUnusedDuplicates(
    geocodingContext: GeocodingContext,
    unusedDuplicates: List<Pair<AugLocationTrack, LocationTrackGeometry>>,
    splitTargetLocationTracks: List<SplitTargetResult>,
): List<Pair<AugLocationTrack, LocationTrackGeometry>> {
    val geocodedUnusedDuplicates =
        unusedDuplicates.mapNotNull { (track, geometry) -> getGeocoded(geocodingContext, track, geometry) }

    val geocodedSplitTargets =
        splitTargetLocationTracks.mapNotNull { (track, geometry) -> getGeocoded(geocodingContext, track, geometry) }

    return geocodedUnusedDuplicates.map { duplicate ->
        geocodedSplitTargets
            .fold(LocationTrackOverlapReference()) { currentHighestOverlap, target ->
                if (currentHighestOverlap.percentage > 99.9) {
                    currentHighestOverlap
                } else {
                    calculateDuplicateLocationTrackOverlap(target.track, target.startAndEnd, duplicate.startAndEnd)
                        .takeIf { calculatedOverlap -> calculatedOverlap.percentage > currentHighestOverlap.percentage }
                        ?: currentHighestOverlap
                }
            }
            .let { bestNewLocationTrackReference -> bestNewLocationTrackReference.locationTrack?.id as IntId? }
            ?.let { newReferenceTrackId ->
                duplicate.track.copy(dbTrack = duplicate.track.dbTrack.copy(duplicateOf = newReferenceTrackId)) to
                    duplicate.geometry
            }
            ?: throw SplitFailureException(
                message =
                    "Could not find a new reference for duplicate location track: duplicateId=${duplicate.track.id}",
                localizedMessageKey = "new-duplicate-reference-assignment-failed",
                localizationParams = localizationParams("duplicate" to duplicate.track.name),
            )
    }
}

data class LocationTrackOverlapReference(val locationTrack: ILocationTrack? = null, val percentage: Double = 0.0)

private fun calculateDuplicateLocationTrackOverlap(
    splitTarget: ILocationTrack,
    splitTargetStartEnd: AlignmentStartAndEndMeters,
    duplicateStartAndEnd: AlignmentStartAndEndMeters,
): LocationTrackOverlapReference {
    val overlapStart = maxOf(duplicateStartAndEnd.start, splitTargetStartEnd.start)
    val overlapEnd = minOf(duplicateStartAndEnd.end, splitTargetStartEnd.end)

    val overlap = maxOf(0.0, overlapEnd - overlapStart)
    val intervalLength = duplicateStartAndEnd.end - duplicateStartAndEnd.start

    return LocationTrackOverlapReference(locationTrack = splitTarget, percentage = overlap / intervalLength * 100)
}

private data class AlignmentStartAndEndMeters(val start: Double, val end: Double)

private fun getAlignmentStartAndEndM(
    geocodingContext: GeocodingContext,
    alignment: IAlignment,
): AlignmentStartAndEndMeters? {
    val startMeters = alignment.start?.let(geocodingContext::getM)?.first
    val endMeters = alignment.end?.let(geocodingContext::getM)?.first

    return if (startMeters != null && endMeters != null) {
        AlignmentStartAndEndMeters(startMeters, endMeters)
    } else {
        null
    }
}
