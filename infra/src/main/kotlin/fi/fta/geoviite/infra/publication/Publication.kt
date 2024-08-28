package fi.fta.geoviite.infra.publication

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.localization.LocalizationParams
import fi.fta.geoviite.infra.localization.localizationParams
import fi.fta.geoviite.infra.logging.Loggable
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.math.Range
import fi.fta.geoviite.infra.split.Split
import fi.fta.geoviite.infra.split.SplitHeader
import fi.fta.geoviite.infra.split.SplitTargetOperation
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

enum class PublicationTableColumn {
    NAME,
    TRACK_NUMBERS,
    CHANGED_KM_NUMBERS,
    OPERATION,
    PUBLICATION_TIME,
    PUBLICATION_USER,
    MESSAGE,
    RATKO_PUSH_TIME,
    CHANGES,
}

data class PublicationTableItem(
    val name: FreeText,
    val trackNumbers: List<TrackNumber>,
    val changedKmNumbers: List<Range<KmNumber>>,
    val operation: Operation,
    val publicationTime: Instant,
    val publicationUser: UserName,
    val message: FreeTextWithNewLines,
    val ratkoPushTime: Instant?,
    val propChanges: List<PublicationChange<*>>,
) {
    val id: StringId<PublicationTableItem> = StringId(hashCode().toString())
}

data class ChangeValue<T>(
    val oldValue: T?,
    val newValue: T?,
    val localizationKey: LocalizationKey? = null,
) {
    constructor(
        oldValue: T?,
        newValue: T?,
        localizationKey: String?
    ) : this(
        oldValue,
        newValue,
        localizationKey?.let(::LocalizationKey),
    )
}

data class PublicationChange<T>(
    val propKey: PropKey,
    val value: ChangeValue<T>,
    // The string is intentionally nullable to allow omitting the whole field (change lists can be large)
    val remark: String?,
)

data class PropKey(
    val key: LocalizationKey,
    val params: LocalizationParams = LocalizationParams.empty,
) {
    constructor(
        key: String,
        params: LocalizationParams = LocalizationParams.empty
    ) : this(LocalizationKey(key), params)
}

open class Publication(
    open val id: IntId<Publication>,
    open val publicationTime: Instant,
    open val publicationUser: UserName,
    open val message: FreeTextWithNewLines,
    open val layoutBranch: LayoutBranch,
)

data class PublishedItemListing<T>(
    val directChanges: List<T>,
    val indirectChanges: List<T>,
)

data class PublishedTrackNumber(
    val id: IntId<TrackLayoutTrackNumber>,
    val version: LayoutRowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedReferenceLine(
    val id: IntId<ReferenceLine>,
    val version: LayoutRowVersion<ReferenceLine>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedLocationTrack(
    val id: IntId<LocationTrack>,
    val version: LayoutRowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedSwitch(
    val id: IntId<TrackLayoutSwitch>,
    val version: LayoutRowVersion<TrackLayoutSwitch>,
    val trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
    val name: SwitchName,
    val operation: Operation,
    @JsonIgnore val changedJoints: List<SwitchJointChange>,
)

data class PublishedKmPost(
    val id: IntId<TrackLayoutKmPost>,
    val version: LayoutRowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation,
)

data class PublishedIndirectChanges(
    // Currently only used by Ratko integration
    @JsonIgnore val trackNumbers: List<PublishedTrackNumber>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
)

data class PublicationDetails(
    override val id: IntId<Publication>,
    override val publicationTime: Instant,
    override val publicationUser: UserName,
    override val message: FreeTextWithNewLines,
    override val layoutBranch: LayoutBranch,
    val trackNumbers: List<PublishedTrackNumber>,
    val referenceLines: List<PublishedReferenceLine>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
    val kmPosts: List<PublishedKmPost>,
    val ratkoPushStatus: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
    val indirectChanges: PublishedIndirectChanges,
    val split: SplitHeader?,
) : Publication(id, publicationTime, publicationUser, message, layoutBranch) {
    val allPublishedTrackNumbers = trackNumbers + indirectChanges.trackNumbers
    val allPublishedLocationTracks = locationTracks + indirectChanges.locationTracks
    val allPublishedSwitches = switches + indirectChanges.switches
}

enum class DraftChangeType {
    TRACK_NUMBER,
    LOCATION_TRACK,
    REFERENCE_LINE,
    SWITCH,
    KM_POST,
}

enum class Operation(val priority: Int) {
    CREATE(0),
    MODIFY(1),
    DELETE(2),
    RESTORE(3),
    CALCULATED(4),
}

data class ValidatedPublicationCandidates(
    val validatedAsPublicationUnit: PublicationCandidates,
    val allChangesValidated: PublicationCandidates,
)

data class ValidatedAsset<T>(
    val id: IntId<T>,
    val errors: List<LayoutValidationIssue>,
)

data class PublicationCandidates(
    val branch: LayoutBranch,
    val trackNumbers: List<TrackNumberPublicationCandidate>,
    val locationTracks: List<LocationTrackPublicationCandidate>,
    val referenceLines: List<ReferenceLinePublicationCandidate>,
    val switches: List<SwitchPublicationCandidate>,
    val kmPosts: List<KmPostPublicationCandidate>,
) : Loggable {
    fun ids(): PublicationRequestIds =
        PublicationRequestIds(
            trackNumbers.map { candidate -> candidate.id },
            locationTracks.map { candidate -> candidate.id },
            referenceLines.map { candidate -> candidate.id },
            switches.map { candidate -> candidate.id },
            kmPosts.map { candidate -> candidate.id },
        )

    fun getValidationVersions(
        branch: LayoutBranch,
        splitVersions: List<RowVersion<Split>>,
    ) =
        ValidationVersions(
            branch = branch,
            trackNumbers = trackNumbers.map(TrackNumberPublicationCandidate::getPublicationVersion),
            referenceLines =
                referenceLines.map(ReferenceLinePublicationCandidate::getPublicationVersion),
            locationTracks =
                locationTracks.map(LocationTrackPublicationCandidate::getPublicationVersion),
            switches = switches.map(SwitchPublicationCandidate::getPublicationVersion),
            kmPosts = kmPosts.map(KmPostPublicationCandidate::getPublicationVersion),
            splits = splitVersions,
        )

    fun filter(request: PublicationRequestIds) =
        copy(
            trackNumbers =
                trackNumbers.filter { candidate -> request.trackNumbers.contains(candidate.id) },
            referenceLines =
                referenceLines.filter { candidate ->
                    request.referenceLines.contains(candidate.id)
                },
            locationTracks =
                locationTracks.filter { candidate ->
                    request.locationTracks.contains(candidate.id)
                },
            switches = switches.filter { candidate -> request.switches.contains(candidate.id) },
            kmPosts = kmPosts.filter { candidate -> request.kmPosts.contains(candidate.id) },
        )

    override fun toLog(): String =
        logFormat(
            "trackNumbers" to toLog(trackNumbers),
            "locationTracks" to toLog(locationTracks),
            "referenceLines" to toLog(referenceLines),
            "switches" to toLog(switches),
            "kmPosts" to toLog(kmPosts),
        )

    private fun <T : PublicationCandidate<*>> toLog(list: List<T>): String =
        "${list.map(PublicationCandidate<*>::rowVersion)}"
}

data class ValidationVersions(
    val branch: LayoutBranch,
    val trackNumbers: List<ValidationVersion<TrackLayoutTrackNumber>>,
    val locationTracks: List<ValidationVersion<LocationTrack>>,
    val referenceLines: List<ValidationVersion<ReferenceLine>>,
    val switches: List<ValidationVersion<TrackLayoutSwitch>>,
    val kmPosts: List<ValidationVersion<TrackLayoutKmPost>>,
    val splits: List<RowVersion<Split>>,
) {
    fun containsLocationTrack(id: IntId<LocationTrack>) = locationTracks.any { it.officialId == id }

    fun containsKmPost(id: IntId<TrackLayoutKmPost>) = kmPosts.any { it.officialId == id }

    fun containsSwitch(id: IntId<TrackLayoutSwitch>) = switches.any { it.officialId == id }

    fun containsSplit(id: IntId<Split>): Boolean = splits.any { it.id == id }

    fun findTrackNumber(id: IntId<TrackLayoutTrackNumber>) =
        trackNumbers.find { it.officialId == id }

    fun findLocationTrack(id: IntId<LocationTrack>) = locationTracks.find { it.officialId == id }

    fun findSwitch(id: IntId<TrackLayoutSwitch>) = switches.find { it.officialId == id }

    fun getTrackNumberIds() = trackNumbers.map { v -> v.officialId }

    fun getReferenceLineIds() = referenceLines.map { v -> v.officialId }

    fun getLocationTrackIds() = locationTracks.map { v -> v.officialId }

    fun getSwitchIds() = switches.map { v -> v.officialId }

    fun getKmPostIds() = kmPosts.map { v -> v.officialId }

    fun getSplitIds() = splits.map { v -> v.id }
}

data class PublicationGroup(
    val id: IntId<Split>,
)

// TODO: GVT-2629 Rename validatedAssetVersion -> rowVersion
data class ValidationVersion<T>(
    val officialId: IntId<T>,
    val validatedAssetVersion: LayoutRowVersion<T>
)

data class PublicationRequestIds(
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
    val kmPosts: List<IntId<TrackLayoutKmPost>>,
) {
    operator fun minus(other: PublicationRequestIds) =
        PublicationRequestIds(
            trackNumbers - other.trackNumbers.toSet(),
            locationTracks - other.locationTracks.toSet(),
            referenceLines - other.referenceLines.toSet(),
            switches - other.switches.toSet(),
            kmPosts - other.kmPosts.toSet(),
        )
}

data class PublicationRequest(
    val content: PublicationRequestIds,
    val message: FreeTextWithNewLines,
)

data class PublicationResult(
    val publicationId: IntId<Publication>?,
    val trackNumbers: Int,
    val locationTracks: Int,
    val referenceLines: Int,
    val switches: Int,
    val kmPosts: Int,
)

enum class LayoutValidationIssueType {
    ERROR,
    WARNING
}

data class LayoutValidationIssue(
    val type: LayoutValidationIssueType,
    val localizationKey: LocalizationKey,
    val params: LocalizationParams = LocalizationParams.empty,
) {
    constructor(
        type: LayoutValidationIssueType,
        key: String,
        params: Map<String, Any?> = emptyMap(),
    ) : this(type, LocalizationKey(key), localizationParams(params))
}

interface PublicationCandidate<T> {
    val type: DraftChangeType
    val id: IntId<T>
    val rowVersion: LayoutRowVersion<T>
    val draftChangeTime: Instant
    val userName: UserName
    val issues: List<LayoutValidationIssue>
    val operation: Operation?
    val designRowReferrer: DesignRowReferrer
    val publicationGroup: PublicationGroup?

    fun getPublicationVersion() = ValidationVersion(id, rowVersion)
}

data class TrackNumberPublicationCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    override val rowVersion: LayoutRowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val designRowReferrer: DesignRowReferrer,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<TrackLayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class ReferenceLinePublicationCandidate(
    override val id: IntId<ReferenceLine>,
    override val rowVersion: LayoutRowVersion<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation?,
    override val designRowReferrer: DesignRowReferrer,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class LocationTrackPublicationCandidate(
    override val id: IntId<LocationTrack>,
    override val rowVersion: LayoutRowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val designRowReferrer: DesignRowReferrer,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class SwitchPublicationCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    override val rowVersion: LayoutRowVersion<TrackLayoutSwitch>,
    val name: SwitchName,
    val trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val designRowReferrer: DesignRowReferrer,
    override val publicationGroup: PublicationGroup? = null,
    val location: Point?,
) : PublicationCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class KmPostPublicationCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    override val rowVersion: LayoutRowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val designRowReferrer: DesignRowReferrer,
    override val publicationGroup: PublicationGroup? = null,
    val location: Point?,
) : PublicationCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}

data class SwitchLocationTrack(
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val oldVersion: LayoutRowVersion<LocationTrack>,
)

data class Change<T>(
    val old: T?,
    val new: T?,
)

data class LocationTrackChanges(
    val id: IntId<LocationTrack>,
    val name: Change<AlignmentName>,
    val descriptionBase: Change<FreeText>,
    val descriptionSuffix: Change<DescriptionSuffixType>,
    val state: Change<LocationTrackState>,
    val duplicateOf: Change<IntId<LocationTrack>>,
    val type: Change<LocationTrackType>,
    val length: Change<Double>,
    val startPoint: Change<Point>,
    val endPoint: Change<Point>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
    val alignmentVersion: Change<RowVersion<LayoutAlignment>>,
    val geometryChangeSummaries: List<GeometryChangeSummary>?,
    val owner: Change<IntId<LocationTrackOwner>>,
)

// Todo: Consider making TrackLayoutSwitch use this for trapPoint as well
enum class TrapPoint {
    Yes,
    No,
    Unknown
}

data class SwitchChanges(
    val id: IntId<TrackLayoutSwitch>,
    val name: Change<SwitchName>,
    val state: Change<LayoutStateCategory>,
    val trapPoint: Change<TrapPoint>,
    val type: Change<SwitchType>,
    val owner: Change<MetaDataName>,
    val measurementMethod: Change<MeasurementMethod>,
    val joints: List<PublicationDao.PublicationSwitchJoint>,
    val locationTracks: List<SwitchLocationTrack>,
)

data class ReferenceLineChanges(
    val id: IntId<ReferenceLine>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
    val length: Change<Double>,
    val startPoint: Change<Point>,
    val endPoint: Change<Point>,
    val alignmentVersion: Change<RowVersion<LayoutAlignment>>,
)

data class TrackNumberChanges(
    val id: IntId<TrackLayoutTrackNumber>,
    val trackNumber: Change<TrackNumber>,
    val description: Change<FreeText>,
    val state: Change<LayoutState>,
    val startAddress: Change<TrackMeter>,
    val endPoint: Change<Point>,
)

data class KmPostChanges(
    val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
    val kmNumber: Change<KmNumber>,
    val state: Change<LayoutState>,
    val location: Change<Point>,
    val gkLocation: Change<GeometryPoint>,
    val gkSrid: Change<Srid>,
    val gkLocationSource: Change<KmPostGkLocationSource>,
    val gkLocationConfirmed: Change<Boolean>,
)

data class SwitchChangeIds(val name: String, val externalId: Oid<TrackLayoutSwitch>?)

data class LocationTrackPublicationSwitchLinkChanges(
    val old: Map<IntId<TrackLayoutSwitch>, SwitchChangeIds>,
    val new: Map<IntId<TrackLayoutSwitch>, SwitchChangeIds>,
)

data class SplitInPublication(
    val id: IntId<Publication>,
    val splitId: IntId<Split>,
    val locationTrack: LocationTrack,
    val targetLocationTracks: List<SplitTargetInPublication>,
)

data class SplitTargetInPublication(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val oid: Oid<LocationTrack>?,
    val startAddress: TrackMeter?,
    val endAddress: TrackMeter?,
    val operation: SplitTargetOperation,
)

enum class DesignRowReferrer {
    MAIN_DRAFT,
    DESIGN_DRAFT,
    NONE,
}

sealed class LayoutContextTransition {

    companion object {
        fun of(branch: LayoutBranch, fromState: PublicationState): LayoutContextTransition {
            assert(branch is DesignBranch || fromState == PublicationState.DRAFT) {
                "Can't transition layout context from main-official"
            }
            return if (branch is DesignBranch) {
                if (fromState == PublicationState.DRAFT) PublicationInDesign(branch)
                else MergeFromDesign(branch)
            } else PublicationInMain
        }

        fun publicationIn(layoutBranch: LayoutBranch): LayoutContextTransition =
            if (layoutBranch is DesignBranch) PublicationInDesign(layoutBranch)
            else PublicationInMain
    }

    abstract val candidateBranch: LayoutBranch
    abstract val baseBranch: LayoutBranch
    abstract val candidatePublicationState: PublicationState
    abstract val basePublicationState: PublicationState

    val candidateContext
        get() = LayoutContext.of(candidateBranch, candidatePublicationState)

    val baseContext
        get() = LayoutContext.of(baseBranch, basePublicationState)
}

data object PublicationInMain : LayoutContextTransition() {
    override val candidateBranch = MainBranch.instance
    override val baseBranch = MainBranch.instance
    override val candidatePublicationState = PublicationState.DRAFT
    override val basePublicationState = PublicationState.OFFICIAL
}

data class PublicationInDesign(private val branch: DesignBranch) : LayoutContextTransition() {
    override val candidateBranch = branch
    override val baseBranch = branch
    override val candidatePublicationState = PublicationState.DRAFT
    override val basePublicationState = PublicationState.OFFICIAL
}

data class MergeFromDesign(override val candidateBranch: DesignBranch) : LayoutContextTransition() {
    override val baseBranch = MainBranch.instance
    override val candidatePublicationState = PublicationState.OFFICIAL
    override val basePublicationState = PublicationState.DRAFT
}
