package fi.fta.geoviite.infra.split

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublishValidationError
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

enum class BulkTransferState {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED
}

data class SplitSource(
    val id: IntId<SplitSource>,
    val locationTrackId: IntId<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val errorCause: String?,
    val publicationId: IntId<Publication>?,
    val targetLocationTracks: List<SplitTarget>,
) {
    fun isPartOf(trackId: IntId<LocationTrack>): Boolean =
        trackId == locationTrackId || targetLocationTracks.any { it.locationTrackId == trackId }
}

data class SplitTarget(
    val splitId: IntId<SplitSource>,
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

data class SplitRequestTarget(
    val duplicateTrackId: IntId<LocationTrack>?,
    val endsAtSwitch: IntId<TrackLayoutSwitch>?,
    val name: AlignmentName,
    val descriptionBase: FreeText,
    val descriptionSuffix: DescriptionSuffixType,
)
data class SplitRequest(
    val sourceTrackId: IntId<LocationTrack>,
    val targetTracks: List<SplitRequestTarget>,
)
