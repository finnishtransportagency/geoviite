package fi.fta.geoviite.infra.split

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.publication.PublicationValidationError
import fi.fta.geoviite.infra.tracklayout.DescriptionSuffixType
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
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
        locationTrackId = split.sourceLocationTrackId,
        bulkTransferState = split.bulkTransferState,
        publicationId = split.publicationId,
    )
}

data class Split(
    val id: IntId<Split>,
    val rowVersion: RowVersion<Split>,
    val sourceLocationTrackId: IntId<LocationTrack>,
    val sourceLocationTrackVersion: RowVersion<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val publicationId: IntId<Publication>?,
    val targetLocationTracks: List<SplitTarget>,
    val relinkedSwitches: List<IntId<TrackLayoutSwitch>>,
    val updatedDuplicates: List<IntId<LocationTrack>>,
) {
    init {
        if (publicationId != null) {
            require(id == rowVersion.id) { "Split source row version must refer to official row, once published" }
        }
        if (publicationId == null) {
            require(bulkTransferState == BulkTransferState.PENDING) { "Split must be pending if not published" }
        }
    }

    @get:JsonIgnore
    val locationTracks by lazy { targetLocationTracks.map { it.locationTrackId } + sourceLocationTrackId }

    @JsonIgnore
    val isPublishedAndWaitingTransfer: Boolean = bulkTransferState != BulkTransferState.DONE && publicationId != null

    fun containsTargetTrack(trackId: IntId<LocationTrack>): Boolean =
        targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId }
    fun containsLocationTrack(trackId: IntId<LocationTrack>): Boolean = locationTracks.contains(trackId)
    fun getTargetLocationTrack(trackId: IntId<LocationTrack>): SplitTarget? =
        targetLocationTracks.find { track -> track.locationTrackId == trackId }

    fun containsSwitch(switchId: IntId<TrackLayoutSwitch>): Boolean = relinkedSwitches.contains(switchId)
}

enum class SplitTargetOperation { CREATE, OVERWRITE, TRANSFER }

enum class SplitTargetDuplicateOperation {
    TRANSFER,
    OVERWRITE;
    fun toSplitTargetOperation(): SplitTargetOperation = when (this) {
        TRANSFER -> SplitTargetOperation.TRANSFER
        OVERWRITE -> SplitTargetOperation.OVERWRITE
    }
}

data class SplitTarget(
    val locationTrackId: IntId<LocationTrack>,
    val segmentIndices: IntRange,
    val operation: SplitTargetOperation,
)

data class SplitPublicationValidationErrors(
    val trackNumbers: Map<IntId<TrackLayoutTrackNumber>, List<PublicationValidationError>>,
    val referenceLines: Map<IntId<ReferenceLine>, List<PublicationValidationError>>,
    val kmPosts: Map<IntId<TrackLayoutKmPost>, List<PublicationValidationError>>,
    val locationTracks: Map<IntId<LocationTrack>, List<PublicationValidationError>>,
    val switches: Map<IntId<TrackLayoutSwitch>, List<PublicationValidationError>>,
) {
    fun allErrors(): List<PublicationValidationError> =
        (trackNumbers.values + referenceLines.values + kmPosts.values + locationTracks.values + switches.values).flatten()
}

data class SplitRequestTargetDuplicate(
    val id: IntId<LocationTrack>,
    val operation: SplitTargetDuplicateOperation,
)

data class SplitRequestTarget(
    val duplicateTrack: SplitRequestTargetDuplicate?,
    val startAtSwitchId: IntId<TrackLayoutSwitch>?,
    val name: AlignmentName,
    val descriptionBase: FreeText,
    val descriptionSuffix: DescriptionSuffixType,
) {
    fun getOperation(): SplitTargetOperation =
        duplicateTrack?.operation?.toSplitTargetOperation() ?: SplitTargetOperation.CREATE
}

data class SplitRequest(
    val sourceTrackId: IntId<LocationTrack>,
    val targetTracks: List<SplitRequestTarget>,
)
