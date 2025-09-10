package fi.fta.geoviite.infra.split

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.LayoutValidationIssue
import fi.fta.geoviite.infra.publication.Publication
import fi.fta.geoviite.infra.tracklayout.DuplicateStatus
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionStructure
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameFreeTextPart
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameSpecifier
import fi.fta.geoviite.infra.tracklayout.LocationTrackNameStructure
import fi.fta.geoviite.infra.tracklayout.LocationTrackNamingScheme
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.SwitchOnLocationTrack
import java.time.Instant

class BulkTransfer

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
    constructor(
        split: Split
    ) : this(
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
    val sourceLocationTrackVersion: LayoutRowVersion<LocationTrack>,
    val bulkTransferState: BulkTransferState,
    val bulkTransferId: IntId<BulkTransfer>?,
    val publicationId: IntId<Publication>?,
    val publicationTime: Instant?,
    val targetLocationTracks: List<SplitTarget>,
    val relinkedSwitches: List<IntId<LayoutSwitch>>,
    val updatedDuplicates: List<IntId<LocationTrack>>,
) {
    init {
        if (publicationId != null) {
            require(id == rowVersion.id) { "Split source row version must refer to official row, once published" }
        }
        if (publicationId == null) {
            require(bulkTransferState == BulkTransferState.PENDING) { "Split must be pending if not published" }
        }

        if (bulkTransferState == BulkTransferState.IN_PROGRESS) {
            requireNotNull(bulkTransferId) {
                "Split must have a non-null bulk transfer id when bulk transfer state is set to be in progress"
            }
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

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean = relinkedSwitches.contains(switchId)
}

enum class SplitTargetOperation {
    CREATE,
    OVERWRITE,
    TRANSFER,
}

enum class SplitTargetDuplicateOperation {
    TRANSFER,
    OVERWRITE;

    fun toSplitTargetOperation(): SplitTargetOperation =
        when (this) {
            TRANSFER -> SplitTargetOperation.TRANSFER
            OVERWRITE -> SplitTargetOperation.OVERWRITE
        }
}

data class SplitTarget(
    val locationTrackId: IntId<LocationTrack>,
    val edgeIndices: IntRange,
    val operation: SplitTargetOperation,
)

data class SplitLayoutValidationIssues(
    val trackNumbers: Map<IntId<LayoutTrackNumber>, List<LayoutValidationIssue>>,
    val referenceLines: Map<IntId<ReferenceLine>, List<LayoutValidationIssue>>,
    val kmPosts: Map<IntId<LayoutKmPost>, List<LayoutValidationIssue>>,
    val locationTracks: Map<IntId<LocationTrack>, List<LayoutValidationIssue>>,
    val switches: Map<IntId<LayoutSwitch>, List<LayoutValidationIssue>>,
) {
    fun allIssues(): List<LayoutValidationIssue> =
        (trackNumbers.values + referenceLines.values + kmPosts.values + locationTracks.values + switches.values)
            .flatten()
}

data class SplitRequestTargetDuplicate(val id: IntId<LocationTrack>, val operation: SplitTargetDuplicateOperation)

data class SplitRequestTarget(
    val duplicateTrack: SplitRequestTargetDuplicate?,
    val startAtSwitchId: IntId<LayoutSwitch>?,
    val namingScheme: LocationTrackNamingScheme,
    val nameFreeText: LocationTrackNameFreeTextPart?,
    val nameSpecifier: LocationTrackNameSpecifier?,
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
) {
    // Resolve structural forms as vals to get any errors on creation
    val operation: SplitTargetOperation =
        duplicateTrack?.operation?.toSplitTargetOperation() ?: SplitTargetOperation.CREATE
    val nameStructure =
        LocationTrackNameStructure.of(scheme = namingScheme, freeText = nameFreeText, specifier = nameSpecifier)
    val descriptionStructure: LocationTrackDescriptionStructure =
        LocationTrackDescriptionStructure(base = descriptionBase, suffix = descriptionSuffix)
}

data class SplitRequest(val sourceTrackId: IntId<LocationTrack>, val targetTracks: List<SplitRequestTarget>) {
    init {
        require(targetTracks.none { target -> target.duplicateTrack?.id == sourceTrackId }) {
            "Split request target tracks should not contain the source track"
        }

        val nonNullDuplicates = targetTracks.map { target -> target.duplicateTrack?.id }.filterNotNull()
        require(nonNullDuplicates.size == nonNullDuplicates.toSet().size) {
            "Split request target tracks contain non-distinct duplicate track ids. This would lead to geometry validation errors during publication."
        }
    }
}

data class SplittingInitializationParameters(
    val id: IntId<LocationTrack>,
    val switches: List<SwitchOnLocationTrack>,
    val duplicates: List<SplitDuplicateTrack>,
)

data class SplitDuplicateTrack(
    val id: IntId<LocationTrack>,
    val nameStructure: LocationTrackNameStructure,
    val descriptionStructure: LocationTrackDescriptionStructure,
    val name: AlignmentName,
    val length: LineM<LocationTrackM>,
    val status: DuplicateStatus,
)
