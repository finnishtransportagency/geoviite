package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.PublishType.DRAFT
import fi.fta.geoviite.infra.error.SplitFailureException
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.linking.SwitchLinkingService
import fi.fta.geoviite.infra.linking.createSwitchLinkingParameters
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
        val suggestions = switchLinkingService.getTrackSwitchSuggestions(sourceTrack)
        suggestions.forEach { (id, suggestion) ->
            if (suggestion == null) {
                throw SplitFailureException(
                    message = "Switch re-linking failed to produce a suggestion: switch=$id",
                    localizedMessageKey = "switch-linking-failed",
                )
            }
            switchLinkingService.saveSwitchLinking(createSwitchLinkingParameters(suggestion, id))
        }

        // Re-fetch after re-linking
        val (track, alignment) = locationTrackService.getWithAlignmentOrThrow(DRAFT, request.sourceTrackId)
        var startIndex = 0
        val splits = request.targetTracks.map { target ->
            val segments = if (target.endsAtSwitch == null) {
                alignment.segments.subList(startIndex, alignment.segments.size)
            } else {
                val presentationJoint = suggestions
                    .find { (id, _) -> id == target.endsAtSwitch }
                    ?.let { (_, suggestion) -> suggestion?.switchStructure?.presentationJointNumber }
                    ?: throw SplitFailureException("No re-linked switch for switch: id=${target.endsAtSwitch}")
                val nextStartIndex = alignment.segments.indexOfFirst { s ->
                    s.switchId == target.endsAtSwitch && s.endJointNumber == presentationJoint
                } + 1
                alignment.segments.subList(startIndex, nextStartIndex).also { startIndex = nextStartIndex }
            }
            // TODO: Create alignment from new segments (fix starts)
            // TODO: Create new locationtrack
            // TODO: Save results as new drafts

        }

        locationTrackService.updateState(request.sourceTrackId, LayoutState.DELETED)
        TODO()
    }
}
