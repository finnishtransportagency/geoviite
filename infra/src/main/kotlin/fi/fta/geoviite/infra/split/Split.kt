package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber

enum class BulkTransferState {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED
}

data class Split(
    val id: IntId<Split>,
    val locationTrackId: IntId<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val errorCause: String?,
    val publicationId: IntId<Publication>?,
    val targetLocationTracks: List<SplitTarget>,
) {
    val locationTracks by lazy { targetLocationTracks.map { it.locationTrackId } + locationTrackId }

    val isPending: Boolean = bulkTransferState == BulkTransferState.PENDING && publicationId == null

    fun containsLocationTrack(trackId: IntId<LocationTrack>): Boolean = locationTracks.contains(trackId)
}

data class SplitTarget(
    val splitId: IntId<Split>,
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndices: IntRange,
)

data class SplitTargetSaveRequest(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndices: IntRange,
)

data class SplitPublishValidationErrors(
    val trackNumbers: Map<IntId<TrackLayoutTrackNumber>, List<PublishValidationError>>,
    val referenceLines: Map<IntId<ReferenceLine>, List<PublishValidationError>>,
    val kmPosts: Map<IntId<TrackLayoutKmPost>, List<PublishValidationError>>,
    val locationTracks: Map<IntId<LocationTrack>, List<PublishValidationError>>,
)
