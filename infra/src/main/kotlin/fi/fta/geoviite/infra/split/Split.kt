package fi.fta.geoviite.infra.split

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

enum class BulkTransferState {
    PENDING,
    IN_PROGRESS,
    DONE,
    FAILED,
    TEMPORARY_FAILURE,
}

data class SplitHeader(
    val id: IntId<Split>,
    val locationTrackId: IntId<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val publicationId: IntId<Publication>?,
) {
    constructor(split: Split) : this(
        id = split.id,
        locationTrackId = split.locationTrackId,
        bulkTransferState = split.bulkTransferState,
        publicationId = split.publicationId,
    )
}

data class Split(
    val id: IntId<Split>,
    val locationTrackId: IntId<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val publicationId: IntId<Publication>?,
    val targetLocationTracks: List<SplitTarget>,
    val relinkedSwitches: List<IntId<TrackLayoutSwitch>>,
    val updatedDuplicates: List<IntId<LocationTrack>>,
) {
    @get:JsonIgnore
    val locationTracks by lazy { targetLocationTracks.map { it.locationTrackId } + locationTrackId }

    @JsonIgnore
    val isPending: Boolean = bulkTransferState == BulkTransferState.PENDING && publicationId == null

    fun containsLocationTrack(trackId: IntId<LocationTrack>): Boolean = locationTracks.contains(trackId)

    fun containsSwitch(switchId: IntId<TrackLayoutSwitch>): Boolean = relinkedSwitches.contains(switchId)
}

data class SplitTarget(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndices: IntRange,
)

data class SplitPublicationValidationErrors(
    val trackNumbers: Map<IntId<TrackLayoutTrackNumber>, List<PublicationValidationError>>,
    val referenceLines: Map<IntId<ReferenceLine>, List<PublicationValidationError>>,
    val kmPosts: Map<IntId<TrackLayoutKmPost>, List<PublicationValidationError>>,
    val locationTracks: Map<IntId<LocationTrack>, List<PublicationValidationError>>,
    val switches: Map<IntId<TrackLayoutSwitch>, List<PublicationValidationError>>,
)

data class SplitRequestTarget(
    val duplicateTrackId: IntId<LocationTrack>?,
    val startAtSwitchId: IntId<TrackLayoutSwitch>?,
    val name: AlignmentName,
    val descriptionBase: FreeText,
    val descriptionSuffix: DescriptionSuffixType,
)
data class SplitRequest(
    val sourceTrackId: IntId<LocationTrack>,
    val targetTracks: List<SplitRequestTarget>,
)
