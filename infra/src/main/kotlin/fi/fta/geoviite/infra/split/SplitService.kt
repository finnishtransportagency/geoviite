package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.PublishType
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.geocoding.GeocodingService
import fi.fta.geoviite.infra.logging.serviceCall
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.publication.PublishValidationErrorType
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class SplitService(
    private val splitDao: SplitDao,
    private val kmPostDao: LayoutKmPostDao,
    private val referenceLineDao: ReferenceLineDao,
    private val geocodingService: GeocodingService,
    private val locationTrackDao: LocationTrackDao,
) {

    private val logger: Logger = LoggerFactory.getLogger(this::class.java)

    fun saveSplit(
        locationTrackId: IntId<LocationTrack>,
        splitTargets: Collection<SplitTargetSaveRequest>,
    ): IntId<Split> {
        logger.serviceCall(
            "saveSplit",
            "locationTrackId" to locationTrackId,
            "splitTargets" to splitTargets
        )

        return splitDao.saveSplit(locationTrackId, splitTargets)
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
            val ltSplitErrors = validateSplitForLocationTrack(id, version, splits)
            val contentErrors = splitErrors.mapNotNull { (split, error) ->
                if (split.containsLocationTrack(id)) error else null
            }

            id to ltSplitErrors + contentErrors
        }.filterValues { it.isNotEmpty() }

        return SplitPublishValidationErrors(tnSplitErrors, rlSplitErrors, kpSplitErrors, trackSplitErrors)
    }

    private fun validateSplitForLocationTrack(
        trackId: IntId<LocationTrack>,
        trackVersion: RowVersion<LocationTrack>,
        splits: Collection<Split>,
    ): List<PublishValidationError> {
        val pendingSplits = splits.filter { it.isPending }

        val targetGeometryError = pendingSplits
            .firstOrNull { it.targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId } }
            ?.let { split ->
                val targetAddresses = geocodingService.getAddressPoints(trackId, PublishType.DRAFT)
                val sourceAddresses = geocodingService.getAddressPoints(split.locationTrackId, PublishType.OFFICIAL)

                validateTargetGeometry(targetAddresses, sourceAddresses)
            }

        val sourceTrackErrors = pendingSplits
            .firstOrNull { it.locationTrackId == trackId }
            ?.let {
                val draftAddresses = geocodingService.getAddressPoints(trackId, PublishType.DRAFT)
                val officialAddresses = geocodingService.getAddressPoints(trackId, PublishType.OFFICIAL)
                val geometryErrors = validateSourceGeometry(draftAddresses, officialAddresses)
                val sourceSplitErrors = validateSplitSourceLocationTrack(locationTrackDao.fetch(trackVersion))

                listOfNotNull(geometryErrors, sourceSplitErrors)
            } ?: emptyList()

        val statusError = splits
            .firstOrNull { !it.isPending }
            ?.let {
                PublishValidationError(
                    PublishValidationErrorType.ERROR,
                    "$VALIDATION_SPLIT.split-in-progress"
                )
            }

        return sourceTrackErrors + listOfNotNull(statusError, targetGeometryError)
    }

    fun validateSplitReferencesByTrackNumber(
        trackNumberId: IntId<TrackLayoutTrackNumber>,
    ): PublishValidationError? {
        val splitIds = splitDao.fetchUnfinishedSplitIdsByTrackNumber(trackNumberId)
        return if (splitIds.isNotEmpty())
            PublishValidationError(
                PublishValidationErrorType.ERROR,
                "$VALIDATION_SPLIT.split-in-progress"
            )
        else null
    }
}
