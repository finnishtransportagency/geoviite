package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.common.PublishType.OFFICIAL
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.linking.SwitchLinkingService
import fi.fta.geoviite.infra.linking.createSwitchLinkingParameters
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SplitService(
    private val splitDao: SplitDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
    private val switchLinkingService: SwitchLinkingService,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun saveSplit(
        locationTrackId: IntId<LocationTrack>,
        splitTargets: Collection<SplitTargetSaveRequest>,
        relinkedSwitches: Collection<IntId<TrackLayoutSwitch>>,
    ): IntId<Split> {
        logger.serviceCall(
            "saveSplit",
            "locationTrackId" to locationTrackId,
            "splitTargets" to splitTargets,
            "relinkedSwitches" to relinkedSwitches,
        )

        return splitDao.saveSplit(locationTrackId, splitTargets, relinkedSwitches)
    }

    fun findPendingSplits(locationTracks: Collection<IntId<LocationTrack>>) =
        findUnfinishedSplits(locationTracks).filter { it.isPending }

    fun findUnfinishedSplits(locationTracks: Collection<IntId<LocationTrack>>): List<Split> {
        logger.serviceCall("findSplits", "locationTracks" to locationTracks)

        return splitDao.fetchUnfinishedSplits().filter { split ->
            locationTracks.any { lt -> split.containsLocationTrack(lt) }
        }
    }

    fun publishSplit(
        locationTracks: Collection<IntId<LocationTrack>>,
        publicationId: IntId<Publication>,
    ) {
        logger.serviceCall(
            "publishSplit",
            "locationTracks" to locationTracks,
            "publicationId" to publicationId,
        )

        findPendingSplits(locationTracks)
            .distinctBy { it.id }
            .map { split -> splitDao.updateSplitState(split.copy(publicationId = publicationId)) }
    }

    fun deleteSplit(splitId: IntId<Split>) {
        logger.serviceCall(
            "deleteSplit",
            "splitId" to splitId
        )

        splitDao.deleteSplit(splitId)
    }

    fun validateSplit(candidates: ValidationVersions): SplitPublishValidationErrors {
        val splits = findUnfinishedSplits(candidates.locationTracks.map { it.officialId })
        val splitErrors = validateSplitContent(candidates.locationTracks, candidates.switches, splits)

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

        return SplitPublishValidationErrors(
            tnSplitErrors,
            rlSplitErrors,
            kpSplitErrors,
            trackSplitErrors,
            switchSplitErrors,
        )
    }

    fun getSplitHeaderByPublicationId(publicationId: IntId<Publication>): SplitHeader? {
        logger.serviceCall(
            "getSplitByPublicationId",
            "publicationId" to publicationId,
        )

        return splitDao.fetchSplitIdByPublication(publicationId)?.let(splitDao::getSplitHeader)
    }

    private fun validateSplitForLocationTrack(
        trackId: IntId<LocationTrack>,
        splits: Collection<Split>,
    ): List<PublishValidationError> {
        val pendingSplits = splits.filter { it.isPending }

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

        val splitSourceLocationTrackErrors = splits
            .firstOrNull { it.locationTrackId == trackId }
            ?.let {
                validateSplitSourceLocationTrack(locationTrackService.getOrThrow(DRAFT, trackId))
            }

        val sourceDuplicateTrackErrors = splits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId } }
            ?.let { split ->
                val sourceLocationTrack = locationTrackDao.getOrThrow(DRAFT, split.locationTrackId)
                split.targetLocationTracks.mapNotNull { targetLt ->
                    val targetLocationTrack = locationTrackService.getOrThrow(DRAFT, targetLt.locationTrackId)
                    validateTargetTrackNumber(sourceLocationTrack, targetLocationTrack)
                }
            } ?: emptyList()

        val statusError = splits
            .firstOrNull { !it.isPending }
            ?.let {
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.split-in-progress",
                )
            }

        return sourceDuplicateTrackErrors +
                listOfNotNull(targetGeometryError, sourceGeometryErrors, splitSourceLocationTrackErrors, statusError)
    }

    fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublishValidationError? {
        val splitIds = splitDao.fetchUnfinishedSplitIdsByTrackNumber(trackNumberId)
        return if (splitIds.isNotEmpty())
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "$VALIDATION_SPLIT.split-in-progress",
            )
        else null
    }

    @Transactional
    fun split(request: SplitRequest): IntId<Split> {
        val sourceTrack = locationTrackDao.getOrThrow(DRAFT, request.sourceTrackId)
        val suggestions = verifySwitchSuggestions(switchLinkingService.getTrackSwitchSuggestions(sourceTrack))
        val relinkedSwitches = suggestions.map { (id, suggestion) ->
            switchLinkingService.saveSwitchLinking(createSwitchLinkingParameters(suggestion, id)).id
        }

        // Fetch post-re-linking track & alignment
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, request.sourceTrackId)
        val targetTracks = splitLocationTrack(
            track = track,
            alignment = alignment,
            targets = collectSplitTargetParams(request.targetTracks, suggestions),
        )
        val splitTargets = targetTracks.map(::saveTargetTrack)

        locationTrackService.updateState(request.sourceTrackId, LayoutState.DELETED)
        return splitDao.saveSplit(request.sourceTrackId, splitTargets, relinkedSwitches)
    }

    private fun saveTargetTrack(target: SplitTargetResult): SplitTargetSaveRequest {
        val id = locationTrackService.saveDraft(
            draft = locationTrackService.fetchNearbyTracksAndCalculateLocationTrackTopology(
                track = target.locationTrack,
                alignment = target.alignment,
                startChanged = true,
                endChanged = true,
            ),
            alignment = target.alignment,
        ).id
        return SplitTargetSaveRequest(id, target.indices)
    }

    private fun collectSplitTargetParams(
        targets: List<SplitRequestTarget>,
        suggestions: List<Pair<IntId<TrackLayoutSwitch>, SuggestedSwitch>>,
    ): List<SplitTargetParams> {
        return targets.map { target ->
            val startSwitch = target.startAtSwitchId?.let { switchId ->
                val jointNumber = suggestions
                    .find { (id, _) -> id == switchId }
                    ?.let { (_, suggestion) -> suggestion.switchStructure.presentationJointNumber }
                    ?: throw SplitFailureException(
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
