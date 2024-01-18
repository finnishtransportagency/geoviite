package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
import org.springframework.stereotype.Service

@Service
class SplitService(
    private val splitDao: SplitDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
    private val locationTrackService: LocationTrackService,
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

        val trackSplitErrors = candidates.locationTracks.associate { (id, version) ->
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
                val targetAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.DRAFT)
                val sourceAddresses = geocodingService.getAddressPoints(split.locationTrackId, PublishType.OFFICIAL)

                validateTargetGeometry(targetAddresses, sourceAddresses)
            }

        val sourceGeometryErrors = splits
            .firstOrNull { it.locationTrackId == locationTrackId }
            ?.let {
                val draftAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.DRAFT)
                val officialAddresses = geocodingService.getAddressPoints(locationTrackId, PublishType.OFFICIAL)

                validateSourceGeometry(draftAddresses, officialAddresses)
            }

        val splitSourceLocationTrackErrors = splits
            .firstOrNull { it.locationTrackId == locationTrackId }
            ?.let { split ->
                validateSplitSourceLocationTrack(split, locationTrackService.getOrThrow(PublishType.DRAFT, locationTrackId))
            }

        val sourceDuplicateTrackErrors = splits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == locationTrackId } }
            ?.let { split ->
                val sourceLocationTrack = locationTrackDao.getOrThrow(PublishType.DRAFT, split.locationTrackId)
                split.targetLocationTracks.mapNotNull { targetLt ->
                    val targetLocationTrack = locationTrackService.getOrThrow(PublishType.DRAFT, targetLt.locationTrackId)
                    validateTargetTrackNumber(sourceLocationTrack, targetLocationTrack)
                }
            } ?: emptyList()

        val statusErrors = splits
            .filterNot { it.bulkTransferState == BulkTransferState.PENDING }
            .firstOrNull { it.isPartOf(locationTrackId) }
            ?.let {
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.split-in-progress"
                )
            }

        return listOfNotNull(statusErrors, targetGeometryErrors, sourceGeometryErrors, splitSourceLocationTrackErrors) + sourceDuplicateTrackErrors
    }

    fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublishValidationError? {
        val splitIds = splitDao.fetchUnfinishedSplitsByTrackNumber(trackNumberId)
        return if (splitIds.isNotEmpty())
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "$VALIDATION_SPLIT.split-in-progress"
            )
        else null
    }

    fun locationTrackHasAnySplitsNotDone(locationTrackId: IntId<LocationTrack>): Boolean =
        splitDao.fetchUnfinishedSplits().any { it.isPartOf(locationTrackId) && it.bulkTransferState != BulkTransferState.DONE }
}
