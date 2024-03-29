package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublicationState.DRAFT
import fi.fta.geoviite.infra.common.PublicationState.OFFICIAL
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingContext
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.linking.switches.SwitchLinkingService
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.publication.PublicationValidationErrorType
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.switchLibrary.SwitchLibraryService
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class SplitService(
    private val splitDao: SplitDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchLinkingService: SwitchLinkingService,
    private val switchLibraryService: SwitchLibraryService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun saveSplit(
        locationTrackId: IntId<LocationTrack>,
        splitTargets: Collection<SplitTarget>,
        relinkedSwitches: Collection<IntId<TrackLayoutSwitch>>,
        updatedDuplicates: Collection<IntId<LocationTrack>>,
    ): IntId<Split> {
        logger.serviceCall(
            "saveSplit",
            "locationTrackId" to locationTrackId,
            "splitTargets" to splitTargets,
            "relinkedSwitches" to relinkedSwitches,
            "updatedDuplicates" to updatedDuplicates,
        )

        return splitDao.saveSplit(
            locationTrackId,
            splitTargets,
            relinkedSwitches,
            updatedDuplicates
        )
    }

    fun getChangeTime(): Instant {
        logger.serviceCall("getChangeTime")
        return splitDao.fetchChangeTime()
    }

    fun findPendingSplitsForLocationTracks(locationTracks: Collection<IntId<LocationTrack>>) =
        findUnfinishedSplitsForLocationTracks(locationTracks).filter { it.isPending }

    fun findUnpublishedSplitsForLocationTracks(locationTracks: Collection<IntId<LocationTrack>>): List<Split> {
        logger.serviceCall("findUnpublishedSplitsForLocationTracks", "locationTracks" to locationTracks)

        return findUnfinishedSplitsForLocationTracks(locationTracks)
            .filter { split -> split.publicationId == null }
    }

    fun findUnfinishedSplitsForLocationTracks(locationTracks: Collection<IntId<LocationTrack>>): List<Split> {
        logger.serviceCall("findUnfinishedSplitsForLocationTracks", "locationTracks" to locationTracks)

        return splitDao.fetchUnfinishedSplits()
            .filter { split ->
                locationTracks.any { lt -> split.containsLocationTrack(lt) }
            }
    }

    fun findUnpublishedSplitsForSwitches(switches: Collection<IntId<TrackLayoutSwitch>>): List<Split> {
        logger.serviceCall("findUnpublishedSplitsForSwitches", "switches" to switches)

        return findUnfinishedSplitsForSwitches(switches)
            .filter { split -> split.publicationId == null }
    }

    fun findUnfinishedSplitsForSwitches(switches: Collection<IntId<TrackLayoutSwitch>>): List<Split> {
        logger.serviceCall("findUnfinishedSplitsForSwitches", "switches" to switches)

        return splitDao.fetchUnfinishedSplits().filter { split ->
            switches.any { s -> split.containsSwitch(s) }
        }
    }

    fun publishSplit(
        locationTracks: Collection<IntId<LocationTrack>>,
        publicationId: IntId<Publication>,
    ) {
        logger.serviceCall("publishSplit", "locationTracks" to locationTracks, "publicationId" to publicationId)

        findPendingSplitsForLocationTracks(locationTracks)
            .distinctBy { it.id }
            .map { split -> splitDao.updateSplitState(split.id, publicationId = publicationId) }
    }

    fun deleteSplit(splitId: IntId<Split>) {
        logger.serviceCall("deleteSplit", "splitId" to splitId)
        splitDao.deleteSplit(splitId)
    }

    fun validateSplit(candidates: ValidationVersions, allowMultipleSplits: Boolean): SplitPublicationValidationErrors {
        val splitsByLocationTracks = candidates.locationTracks.map { locationTrackValidationVersion ->
            locationTrackValidationVersion.officialId
        }.let(::findUnpublishedSplitsForLocationTracks)

        val splitsBySwitches = candidates.switches.map { switchValidationVersion ->
            switchValidationVersion.officialId
        }.let(::findUnpublishedSplitsForSwitches)

        val splits = (splitsByLocationTracks + splitsBySwitches).distinctBy { it.id }
        val splitErrors = validateSplitContent(candidates.locationTracks, candidates.switches, splits, allowMultipleSplits)

        val tnSplitErrors = candidates.trackNumbers.associate { (id, _) ->
            id to listOfNotNull(validateSplitReferencesByTrackNumber(id))
        }.filterValues { it.isNotEmpty() }

        val rlSplitErrors = candidates.referenceLines.associate { (id, version) ->
            val rl = referenceLineDao.fetch(version)
            id to listOfNotNull(validateSplitReferencesByTrackNumber(rl.trackNumberId))
        }.filterValues { it.isNotEmpty() }

        val kpSplitErrors = candidates.kmPosts.associate { (id, version) ->
            val trackNumberId = kmPostDao.fetch(version).trackNumberId
            id to listOfNotNull(trackNumberId?.let(::validateSplitReferencesByTrackNumber))
        }.filterValues { it.isNotEmpty() }

        val trackSplitErrors = candidates.locationTracks.associate { (id, _) ->
            val ltSplitErrors = validateSplitForLocationTrack(id, splits)
            val contentErrors = splitErrors.mapNotNull { (split, error) ->
                if (split.containsLocationTrack(id)) error else null
            }

            id to ltSplitErrors + contentErrors
        }.filterValues { it.isNotEmpty() }
        val switchSplitErrors = candidates.switches.associate { (id, _) ->
            id to splitErrors.mapNotNull { (split, error) ->
                if (split.containsSwitch(id)) error else null
            }
        }

        return SplitPublicationValidationErrors(
            tnSplitErrors,
            rlSplitErrors,
            kpSplitErrors,
            trackSplitErrors,
            switchSplitErrors,
        )
    }

    fun getSplitIdByPublicationId(publicationId: IntId<Publication>): IntId<Split>? {
        logger.serviceCall("getSplitIdByPublicationId", "publicationId" to publicationId)

        return splitDao.fetchSplitIdByPublication(publicationId)
    }

    fun get(splitId: IntId<Split>): Split? {
        logger.serviceCall("get", "splitId" to splitId)

        return splitDao.get(splitId)
    }

    fun getOrThrow(splitId: IntId<Split>): Split {
        logger.serviceCall("getOrThrow", "splitId" to splitId)

        return splitDao.getOrThrow(splitId)
    }

    private fun validateSplitForLocationTrack(
        trackId: IntId<LocationTrack>,
        splits: Collection<Split>,
    ): List<PublicationValidationError> {
        val pendingSplits = splits.filter { it.isPending }

        val trackNumberMismatchErrors = splits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId } }
            ?.let { split ->
                val sourceLocationTrack = locationTrackDao.getOrThrow(OFFICIAL, split.locationTrackId)
                split.targetLocationTracks.mapNotNull { targetLt ->
                    val targetLocationTrack = locationTrackService.getOrThrow(DRAFT, targetLt.locationTrackId)
                    validateTargetTrackNumber(sourceLocationTrack, targetLocationTrack)
                }
            } ?: emptyList()

        // As an optimization, geometry error checking is skipped if the track numbers are different
        // between any source & target location tracks. The geometry check will rarely if ever succeed
        // for differing track numbers on source & target tracks, and differing track number for any
        // source & target track is already a considered a publication-blocking error.
        val splitGeometryErrors = when {
            trackNumberMismatchErrors.isEmpty() -> validateSplitGeometries(trackId, pendingSplits)
            else -> emptyList()
        }

        val splitSourceLocationTrackError = splits
            .firstOrNull { it.locationTrackId == trackId }
            ?.let {
                validateSplitSourceLocationTrack(locationTrackService.getOrThrow(DRAFT, trackId))
            }

        val statusError = splits
            .firstOrNull { !it.isPending }
            ?.let {
                PublicationValidationError(
                    PublicationValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.split-in-progress",
                )
            }

        return listOf(
            listOfNotNull(
                statusError,
                splitSourceLocationTrackError,
            ),
            trackNumberMismatchErrors,
            splitGeometryErrors,
        ).flatten()
    }

    fun validateSplitGeometries(
        trackId: IntId<LocationTrack>,
        pendingSplits: Collection<Split>,
    ): List<PublicationValidationError> {
        val targetGeometryError = pendingSplits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId } }
            ?.let { split ->
                val targetAddresses = geocodingService.getAddressPoints(trackId, DRAFT)
                val sourceAddresses = geocodingService.getAddressPoints(split.locationTrackId, OFFICIAL)

                validateTargetGeometry(targetAddresses, sourceAddresses)
            }

        val sourceGeometryErrors = pendingSplits
            .firstOrNull { it.locationTrackId == trackId }
            ?.let {
                val draftAddresses = geocodingService.getAddressPoints(trackId, DRAFT)
                val officialAddresses = geocodingService.getAddressPoints(trackId, OFFICIAL)
                validateSourceGeometry(draftAddresses, officialAddresses)
            }

        return listOfNotNull(
            targetGeometryError,
            sourceGeometryErrors,
        )
    }

    fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublicationValidationError? {
        val splitIds = splitDao.fetchUnfinishedSplitIdsByTrackNumber(trackNumberId)
        return if (splitIds.isNotEmpty())
            PublicationValidationError(
                PublicationValidationErrorType.ERROR,
                "$VALIDATION_SPLIT.split-in-progress",
            )
        else null
    }

    fun updateSplitState(splitId: IntId<Split>, state: BulkTransferState): IntId<Split> {
        logger.serviceCall("updateSplitState", "splitId" to splitId)

        return splitDao.getOrThrow(splitId).let { split ->
            splitDao.updateSplitState(split.id, bulkTransferState = state)
        }
    }

    @Transactional
    fun split(request: SplitRequest): IntId<Split> {
        // Original duplicate ids to be stored in split data before updating the location track
        // references which they referenced before the split (request source track).
        // If the references are not updated, the duplicate-of reference will be removed entirely when the
        // source track's layout state is set to "DELETED".
        val sourceTrackDuplicateIds = locationTrackService
            .fetchDuplicates(DRAFT, request.sourceTrackId)
            .map { duplicateTrack -> duplicateTrack.id as IntId }

        val sourceTrack = locationTrackDao.getOrThrow(DRAFT, request.sourceTrackId)
        if (sourceTrack.state != LayoutState.IN_USE) throw SplitFailureException(
            message = "Source track state is not IN_USE: id=${sourceTrack.id}",
            localizedMessageKey = "source-track-state-not-in-use",
        )

        val suggestions = verifySwitchSuggestions(switchLinkingService.getTrackSwitchSuggestions(DRAFT, sourceTrack))
        val relinkedSwitches = switchLinkingService.relinkTrack(request.sourceTrackId).map { it.id }

        // Fetch post-re-linking track & alignment
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, request.sourceTrackId)
        val targetTracks = splitLocationTrack(
            track = track,
            alignment = alignment,
            targets = collectSplitTargetParams(request.targetTracks, suggestions),
        )

        val savedSplitTargetLocationTracks = targetTracks
            .map { targetTrack -> targetTrack to saveTargetTrack(targetTrack) }
            .map { (targetTrack, response) ->
                targetTrack.copy(locationTrack = locationTrackDao.fetch(response.rowVersion))
            }

        geocodingService.getGeocodingContext(DRAFT, sourceTrack.trackNumberId)?.let { geocodingContext ->
            val splitTargetTracksWithAlignments = savedSplitTargetLocationTracks.map { splitTargetResult ->
                splitTargetResult.locationTrack to splitTargetResult.alignment
            }

            updateUnusedDuplicateReferencesToSplitTargetTracks(
                geocodingContext,
                request,
                splitTargetTracksWithAlignments,
            )
        } ?: throw SplitFailureException(
            message = "Geocoding context creation failed: trackNumber=${sourceTrack.trackNumberId}",
            localizedMessageKey = "geocoding-failed",
            localizationParams = localizationParams("trackName" to sourceTrack.name)
        )

        locationTrackService.updateState(request.sourceTrackId, LayoutState.DELETED)

        return savedSplitTargetLocationTracks.map { splitTargetResult ->
            SplitTarget(splitTargetResult.locationTrack.id as IntId, splitTargetResult.indices)
        }.let { splitTargets ->
            splitDao.saveSplit(request.sourceTrackId, splitTargets, relinkedSwitches, sourceTrackDuplicateIds)
        }
    }

    private fun updateUnusedDuplicateReferencesToSplitTargetTracks(
        geocodingContext: GeocodingContext,
        splitRequest: SplitRequest,
        splitTargetLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
    ) {
        val unusedDuplicates = locationTrackService.fetchDuplicates(DRAFT, splitRequest.sourceTrackId)
            .filter { locationTrackDuplicate  ->
                !splitRequest.targetTracks.any { targetTrack ->
                    targetTrack.duplicateTrackId == locationTrackDuplicate.id
                }
            }
            .let { unusedDuplicateTracks ->
                locationTrackService.getAlignmentsForTracks(unusedDuplicateTracks)
            }

        findNewLocationTracksForUnusedDuplicates(
            geocodingContext,
            unusedDuplicates,
            splitTargetLocationTracks,
        ).forEach { updatedDuplicate ->
            locationTrackService.saveDraft(updatedDuplicate)
        }
    }

    private fun saveTargetTrack(target: SplitTargetResult): DaoResponse<LocationTrack> =
        locationTrackService.saveDraft(
            draft = locationTrackService.fetchNearbyTracksAndCalculateLocationTrackTopology(
                track = target.locationTrack,
                alignment = target.alignment,
                startChanged = true,
                endChanged = true,
            ),
            alignment = target.alignment,
        )

    private fun collectSplitTargetParams(
        targets: List<SplitRequestTarget>,
        suggestions: List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch>>,
    ): List<SplitTargetParams> {
        return targets.map { target ->
            val startSwitch = target.startAtSwitchId?.let { switchId ->
                val jointNumber = suggestions
                    .find { (id, _) -> id == switchId }?.let { (_, suggestion) ->
                        switchLibraryService.getSwitchStructure(suggestion.switchStructureId).presentationJointNumber
                    } ?: throw SplitFailureException(
                        message = "No re-linked switch for switch: id=${switchId}",
                        localizedMessageKey = "no-switch-suggestion",
                    )
                switchId to jointNumber
            }
            val duplicate = target.duplicateTrackId?.let { id ->
                locationTrackService.getWithAlignmentOrThrow(DRAFT, id)
            }
            SplitTargetParams(target, startSwitch, duplicate)
        }
    }
}

data class SplitTargetParams(
    val request: SplitRequestTarget,
    val startSwitch: Pair<IntId<TrackLayoutSwitch>, JointNumber>?,
    val duplicate: Pair<LocationTrack, LayoutAlignment>?,
)

data class SplitTargetResult(
    val locationTrack: LocationTrack,
    val alignment: LayoutAlignment,
    val indices: IntRange,
)

fun splitLocationTrack(
    track: LocationTrack,
    alignment: LayoutAlignment,
    targets: List<SplitTargetParams>,
): List<SplitTargetResult> {
    return targets.mapIndexed { index, target ->
        val nextSwitch = targets.getOrNull(index + 1)?.startSwitch
        val segmentIndices = findSplitIndices(alignment, target.startSwitch, nextSwitch)
        val segments = cutSegments(alignment, segmentIndices)
        val connectivityType = calculateTopologicalConnectivity(track, alignment.segments.size, segmentIndices)
        val (newTrack, newAlignment) = target.duplicate?.let { (track, alignment) ->
            updateDuplicateToSplitTarget(track, track, alignment, target.request, segments, connectivityType)
        } ?: createSplitTarget(track, target.request, segments, connectivityType)
        SplitTargetResult(newTrack, newAlignment, segmentIndices)
    }.also { result -> validateSplitResult(result, alignment) }
}

fun validateSplitResult(result: List<SplitTargetResult>, alignment: LayoutAlignment) {
    if (result.sumOf { r -> r.alignment.segments.size } != alignment.segments.size) throw SplitFailureException(
        message = "Not all segments were allocated in the split",
        localizedMessageKey = "segment-allocation-failed",
    )
}

private fun updateDuplicateToSplitTarget(
    sourceTrack: LocationTrack,
    duplicateTrack: LocationTrack,
    duplicateAlignment: LayoutAlignment,
    request: SplitRequestTarget,
    segments: List<LayoutSegment>,
    topologicalConnectivityType: TopologicalConnectivityType,
): Pair<LocationTrack, LayoutAlignment> {
    val newAlignment = duplicateAlignment.withSegments(segments)
    val newTrack = duplicateTrack.copy(
        name = request.name,
        descriptionBase = request.descriptionBase,
        descriptionSuffix = request.descriptionSuffix,

        // After split, the track is no longer duplicate
        duplicateOf = null,
        // Topology is re-resolved after tracks and switches are updated
        topologyStartSwitch = null,
        topologyEndSwitch = null,
        topologicalConnectivity = topologicalConnectivityType,

        state = sourceTrack.state,
        trackNumberId = sourceTrack.trackNumberId,
        sourceId = sourceTrack.sourceId,
        // owner remains that of the duplicate
        type = sourceTrack.type,

        // Geometry fields come from alignment
        segmentCount = newAlignment.segments.size,
        length = newAlignment.length,
        boundingBox = newAlignment.boundingBox,
    )
    return newTrack to newAlignment
}

private fun createSplitTarget(
    sourceTrack: LocationTrack,
    request: SplitRequestTarget,
    segments: List<LayoutSegment>,
    topologicalConnectivityType: TopologicalConnectivityType,
): Pair<LocationTrack, LayoutAlignment> {
    val newAlignment = LayoutAlignment(segments)
    val newTrack = LocationTrack(
        name = request.name,
        descriptionBase = request.descriptionBase,
        descriptionSuffix = request.descriptionSuffix,

        // New track -> no external ID
        externalId = null,
        // After split, tracks are not duplicates
        duplicateOf = null,
        // Topology is re-resolved after tracks and switches are updated
        topologyStartSwitch = null,
        topologyEndSwitch = null,
        topologicalConnectivity = topologicalConnectivityType,

        state = sourceTrack.state,
        trackNumberId = sourceTrack.trackNumberId,
        sourceId = sourceTrack.sourceId,
        ownerId = sourceTrack.ownerId,
        type = sourceTrack.type,

        // Geometry fields come from alignment
        segmentCount = newAlignment.segments.size,
        length = newAlignment.length,
        boundingBox = newAlignment.boundingBox,
        contextData = LayoutContextData.newDraft(),
    )
    return newTrack to newAlignment
}

private fun calculateTopologicalConnectivity(
    sourceTrack: LocationTrack,
    sourceSegments: Int,
    segmentIndices: ClosedRange<Int>,
): TopologicalConnectivityType {
    val startConnected = if (0 == segmentIndices.start) {
        sourceTrack.topologicalConnectivity.isStartConnected()
    } else {
        true
    }
    val endConnected = if (sourceSegments == segmentIndices.endInclusive + 1) {
        sourceTrack.topologicalConnectivity.isEndConnected()
    } else {
        true
    }
    return topologicalConnectivityTypeOf(startConnected, endConnected)
}

private fun findSplitIndices(
    alignment: LayoutAlignment,
    startSwitch: Pair<IntId<TrackLayoutSwitch>, JointNumber>?,
    endSwitch: Pair<IntId<TrackLayoutSwitch>, JointNumber>?,
): IntRange {
    val startIndex = startSwitch?.let { (s, j) -> findIndex(alignment, s, j) } ?: 0
    val endIndex = endSwitch?.let { (s, j) -> findIndex(alignment, s, j) - 1 } ?: alignment.segments.lastIndex

    return if (startIndex < 0 || endIndex > alignment.segments.lastIndex || endIndex < startIndex) {
        val aligmentDesc = alignment.segments.map { s -> s.switchId to "${s.startJointNumber}..${s.endJointNumber}" }
        val debug = "result=$startIndex..$endIndex start=$startSwitch end=$endSwitch alignment=$aligmentDesc"
        throw SplitFailureException(
            message = "Failed to map split switches to segment indices: $debug",
            localizedMessageKey = "segment-allocation-failed",
        )
    } else {
        (startIndex..endIndex)
    }
}

private fun findIndex(alignment: LayoutAlignment, switchId: IntId<TrackLayoutSwitch>, joint: JointNumber): Int {
    alignment.segments.forEachIndexed { index, segment ->
        if (segment.switchId == switchId && segment.startJointNumber == joint) return index
        else if (segment.switchId == switchId && segment.endJointNumber == joint) return index + 1
    }
    return -1
}

private fun cutSegments(alignment: LayoutAlignment, segmentIndices: ClosedRange<Int>): List<LayoutSegment> =
    fixSegmentStarts(alignment.segments.subList(segmentIndices.start, segmentIndices.endInclusive + 1))

private fun verifySwitchSuggestions(
    suggestions: List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch?>>,
): List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch>> = suggestions.map { (id, suggestion) ->
    if (suggestion == null) {
        throw SplitFailureException(
            message = "Switch re-linking failed to produce a suggestion: switch=$id",
            localizedMessageKey = "switch-linking-failed",
        )
    } else {
        id to suggestion
    }
}

private fun findNewLocationTracksForUnusedDuplicates(
    geocodingContext: GeocodingContext,
    unusedDuplicates: List<Pair<LocationTrack, LayoutAlignment>>,
    splitTargetLocationTracks: List<Pair<LocationTrack, LayoutAlignment>>,
): List<LocationTrack> {

    val geocodedUnusedDuplicates = unusedDuplicates
        .mapNotNull { (unusedDuplicate, alignment) ->
            getAlignmentStartAndEndM(geocodingContext, alignment)?.let { startAndEnd ->
                unusedDuplicate to startAndEnd
            }
        }

    val geocodedSplitTargets = splitTargetLocationTracks
        .mapNotNull { (locationTrack, alignment) ->
            getAlignmentStartAndEndM(geocodingContext, alignment)?.let { startEnd ->
                locationTrack to startEnd
            }
        }

    return geocodedUnusedDuplicates.map { (duplicate, duplicateStartAndEnd) ->
        geocodedSplitTargets.fold(
            LocationTrackOverlapReference()
        ) { currentHighestOverlap, (splitTarget, splitTargetStartEnd) ->

            if (currentHighestOverlap.percentage > 99.9) {
                currentHighestOverlap
            } else {
                calculateDuplicateLocationTrackOverlap(splitTarget, splitTargetStartEnd, duplicateStartAndEnd)
                    .takeIf { calculatedOverlap ->
                        calculatedOverlap.percentage > currentHighestOverlap.percentage
                    }
                    ?: currentHighestOverlap
            }

        }.let { bestNewLocationTrackReference ->
            bestNewLocationTrackReference.locationTrack?.id as IntId?
        }?.let { newReferenceTrackId ->
            duplicate.copy(duplicateOf = newReferenceTrackId)
        } ?: throw SplitFailureException(
            message = "Could not find a new reference for duplicate location track: duplicateId=${duplicate.id}",
            localizedMessageKey = "new-duplicate-reference-assignment-failed",
            localizationParams = localizationParams("duplicate" to duplicate.name)
        )
    }
}

data class LocationTrackOverlapReference(
    val locationTrack: LocationTrack? = null,
    val percentage: Double = 0.0,
)

private fun calculateDuplicateLocationTrackOverlap(
    splitTarget: LocationTrack,
    splitTargetStartEnd: AlignmentStartAndEndMeters,
    duplicateStartAndEnd: AlignmentStartAndEndMeters,
): LocationTrackOverlapReference {
    val overlapStart = maxOf(duplicateStartAndEnd.start, splitTargetStartEnd.start)
    val overlapEnd = minOf(duplicateStartAndEnd.end, splitTargetStartEnd.end)

    val overlap = maxOf(0.0, overlapEnd - overlapStart)
    val intervalLength = duplicateStartAndEnd.end - duplicateStartAndEnd.start

    return LocationTrackOverlapReference(
        locationTrack = splitTarget,
        percentage = overlap / intervalLength * 100
    )
}

private data class AlignmentStartAndEndMeters(
    val start: Double,
    val end: Double,
)

private fun getAlignmentStartAndEndM(
    geocodingContext: GeocodingContext,
    alignment: LayoutAlignment
): AlignmentStartAndEndMeters? {
    val startMeters = alignment.start?.let(geocodingContext::getM)?.first
    val endMeters = alignment.end?.let(geocodingContext::getM)?.first

    return if (startMeters != null && endMeters != null) {
        AlignmentStartAndEndMeters(startMeters, endMeters)
    } else {
        null
    }
}
