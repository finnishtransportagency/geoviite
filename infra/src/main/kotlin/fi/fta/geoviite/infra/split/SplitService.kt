package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.SuggestedSwitch
import fi.fta.geoviite.infra.linking.SwitchLinkingService
import fi.fta.geoviite.infra.linking.createSwitchLinkingParameters
import fi.fta.geoviite.infra.linking.fixSegmentStarts
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
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

    fun validateSplit(candidates: ValidationVersions): SplitPublishValidationErrors {
        val splits = splitDao.fetchUnfinishedSplits()
        val splitErrors = validateSplitContent(candidates.locationTracks, splits)

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
                if (split.isPartOf(id)) error else null
            }

            id to ltSplitErrors + contentErrors
        }.filterValues { it.isNotEmpty() }

        return SplitPublishValidationErrors(tnSplitErrors, rlSplitErrors, kpSplitErrors, trackSplitErrors)
    }

    private fun validateSplitForLocationTrack(
        locationTrackId: IntId<LocationTrack>,
        splits: List<SplitSource>,
    ): List<PublishValidationError> {
        val targetGeometryErrors = splits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == locationTrackId } }
            ?.let { split ->
                val targetAddresses = geocodingService.getAddressPoints(locationTrackId, DRAFT)
                val sourceAddresses = geocodingService.getAddressPoints(split.locationTrackId, PublishType.OFFICIAL)

                validateTargetGeometry(targetAddresses, sourceAddresses)
            }

        val sourceGeometryErrors = splits
            .firstOrNull { it.locationTrackId == locationTrackId }
            ?.let {
                val draftAddresses = geocodingService.getAddressPoints(locationTrackId, DRAFT)
                val officialAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.OFFICIAL)

                validateSourceGeometry(draftAddresses, officialAddresses)
            }

        val statusErrors = splits
            .filterNot { it.bulkTransferState == BulkTransferState.PENDING }
            .firstOrNull { it.isPartOf(locationTrackId) }
            ?.let {
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.split-in-progress",
                )
            }

        return listOfNotNull(statusErrors, targetGeometryErrors, sourceGeometryErrors)
    }

    fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublishValidationError? {
        val splitIds = splitDao.fetchUnfinishedSplitsByTrackNumber(trackNumberId)
        return if (splitIds.isNotEmpty()) PublishValidationError(
            PublishValidationErrorType.ERROR,
            "$VALIDATION_SPLIT.split-in-progress",
        )
        else null
    }

    fun locationTrackHasAnySplitsNotDone(locationTrackId: IntId<LocationTrack>): Boolean = splitDao
        .fetchUnfinishedSplits()
        .any { it.isPartOf(locationTrackId) && it.bulkTransferState != BulkTransferState.DONE }

    @Transactional
    fun split(request: SplitRequest): IntId<SplitSource> {
        val sourceTrack = locationTrackDao.getOrThrow(DRAFT, request.sourceTrackId)
        val suggestions = verifySwitchSuggestions(switchLinkingService.getTrackSwitchSuggestions(sourceTrack))
        suggestions.forEach { (id, suggestion) ->
            switchLinkingService.saveSwitchLinking(createSwitchLinkingParameters(suggestion, id))
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
        return splitDao.saveSplit(request.sourceTrackId, splitTargets)
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
            val endSwitch = target.endsAtSwitch?.let { switchId ->
                val jointNumber = suggestions
                    .find { (id, _) -> id == switchId }
                    ?.let { (_, suggestion) -> suggestion.switchStructure.presentationJointNumber }
                    ?: throw SplitFailureException("No re-linked switch for switch: id=${switchId}")
                switchId to jointNumber
            }
            val duplicate = target.duplicateTrackId?.let { id ->
                locationTrackService.getWithAlignmentOrThrow(DRAFT, id)
            }
            SplitTargetParams(target, endSwitch, duplicate)
        }
    }
}

private data class SplitTargetParams(
    val request: SplitRequestTarget,
    val endSwitch: Pair<IntId<TrackLayoutSwitch>, JointNumber>?,
    val duplicate: Pair<LocationTrack, LayoutAlignment>?,
)
private data class SplitTargetResult(
    val locationTrack: LocationTrack,
    val alignment: LayoutAlignment,
    val indices: IntRange,
)

private fun splitLocationTrack(
    track: LocationTrack,
    alignment: LayoutAlignment,
    targets: List<SplitTargetParams>,
): List<SplitTargetResult> {
    var startIndex = 0
    return targets.map { target ->
        val segmentIndices = findSplitIndices(alignment, target.endSwitch, startIndex)
            .also { range -> startIndex = range.endInclusive + 1 }
        val segments = cutSegments(alignment, segmentIndices)
        val connectivityType = calculateTopologicalConnectivity(track, alignment.segments.size, segmentIndices)
        val (newTrack, newAlignment) = target.duplicate?.let { (track, alignment) ->
            updateDuplicateToSplitTarget(track, track, alignment, target.request, segments, connectivityType)
        } ?: createSplitTarget(track, target.request, segments, connectivityType)
        SplitTargetResult(newTrack, newAlignment, segmentIndices)
    }
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

        duplicateOf = null,
        topologyStartSwitch = null,
        topologyEndSwitch = null,
        topologicalConnectivity = topologicalConnectivityType,

        state = sourceTrack.state,
        trackNumberId = sourceTrack.trackNumberId,
        sourceId = sourceTrack.sourceId,
        // owner remains that of the duplicate
        type = sourceTrack.type,

        // Geometry fields come from alignment
        // TODO: Check that these are updated in withSegments!
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

        duplicateOf = null,
        externalId = null,
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
    } else true
    val endConnected = if (sourceSegments == segmentIndices.endInclusive + 1) {
        sourceTrack.topologicalConnectivity.isEndConnected()
    } else true
    return topologicalConnectivityTypeOf(startConnected, endConnected)
}

private fun findSplitIndices(
    alignment: LayoutAlignment,
    endSwitch: Pair<IntId<TrackLayoutSwitch>, JointNumber>?,
    startIndex: Int,
): IntRange {
    return if (startIndex > alignment.segments.lastIndex) {
        throw SplitFailureException(
            message = "Failed to map split switches to segment indices",
            localizedMessageKey = "segment-allocation-failed",
        )
    } else if (endSwitch == null) {
        (startIndex..alignment.segments.lastIndex)
    } else {
        val endIndex = alignment.segments.indexOfFirst { s ->
            s.switchId == endSwitch.first && s.endJointNumber == endSwitch.second
        }
        if (endIndex < startIndex) throw SplitFailureException(
            message = "Failed to map split switches to segment indices",
            localizedMessageKey = "segment-allocation-failed",
        )
        (startIndex..endIndex)
    }
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
