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
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.SwitchOnLocationTrack
import java.time.Instant

data class SplitHeader(
    val id: IntId<Split>,
    val locationTrackId: IntId<LocationTrack>,
    val bulkTransferState: BulkTransferState?,
    val bulkTransferExpeditedStart: Boolean?,
    val publicationId: IntId<Publication>?,
) {
    constructor(
        split: PublishedSplit
    ) : this(
        id = split.id,
        locationTrackId = split.sourceLocationTrackId,
        bulkTransferState = requireNotNull(split.bulkTransfer).state,
        bulkTransferExpeditedStart = requireNotNull(split.bulkTransfer).expeditedStart,
        publicationId = split.publicationId,
    )
}

sealed class Split {
    abstract val id: IntId<Split>
    abstract val rowVersion: RowVersion<Split>
    abstract val sourceLocationTrackId: IntId<LocationTrack>
    abstract val sourceLocationTrackVersion: LayoutRowVersion<LocationTrack>
    abstract val targetLocationTracks: List<SplitTarget>
    abstract val relinkedSwitches: List<IntId<LayoutSwitch>>
    abstract val updatedDuplicates: List<IntId<LocationTrack>>

    @get:JsonIgnore
    val locationTracks by lazy { targetLocationTracks.map { it.locationTrackId } + sourceLocationTrackId }

    fun containsTargetTrack(trackId: IntId<LocationTrack>): Boolean =
        targetLocationTracks.any { tlt -> tlt.locationTrackId == trackId }

    fun containsLocationTrack(trackId: IntId<LocationTrack>): Boolean = locationTracks.contains(trackId)

    fun getTargetLocationTrack(trackId: IntId<LocationTrack>): SplitTarget? =
        targetLocationTracks.find { track -> track.locationTrackId == trackId }

    fun containsSwitch(switchId: IntId<LayoutSwitch>): Boolean = relinkedSwitches.contains(switchId)

    val isPublished: Boolean
        get() =
            when (this) {
                is PublishedSplit -> true
                is UnpublishedSplit -> false
            }

    val isUnpublished: Boolean
        get() = !isPublished
}

data class UnpublishedSplit(
    override val id: IntId<Split>,
    override val rowVersion: RowVersion<Split>,
    override val sourceLocationTrackId: IntId<LocationTrack>,
    override val sourceLocationTrackVersion: LayoutRowVersion<LocationTrack>,
    override val targetLocationTracks: List<SplitTarget>,
    override val relinkedSwitches: List<IntId<LayoutSwitch>>,
    override val updatedDuplicates: List<IntId<LocationTrack>>,
) : Split()

data class PublishedSplit(
    override val id: IntId<Split>,
    override val rowVersion: RowVersion<Split>,
    override val sourceLocationTrackId: IntId<LocationTrack>,
    override val sourceLocationTrackVersion: LayoutRowVersion<LocationTrack>,
    override val targetLocationTracks: List<SplitTarget>,
    override val relinkedSwitches: List<IntId<LayoutSwitch>>,
    override val updatedDuplicates: List<IntId<LocationTrack>>,
    val publicationId: IntId<Publication>,
    val publicationTime: Instant,
    val bulkTransfer: BulkTransfer,
) : Split()

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
    val segmentIndices: IntRange,
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
    val name: AlignmentName,
    val descriptionBase: LocationTrackDescriptionBase,
    val descriptionSuffix: LocationTrackDescriptionSuffix,
) {
    fun getOperation(): SplitTargetOperation =
        duplicateTrack?.operation?.toSplitTargetOperation() ?: SplitTargetOperation.CREATE
}

data class SplitRequest(val sourceTrackId: IntId<LocationTrack>, val targetTracks: List<SplitRequestTarget>)

data class SplittingInitializationParameters(
    val id: IntId<LocationTrack>,
    val switches: List<SwitchOnLocationTrack>,
    val duplicates: List<SplitDuplicateTrack>,
)

data class SplitDuplicateTrack(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val length: Double,
    val status: DuplicateStatus,
)
