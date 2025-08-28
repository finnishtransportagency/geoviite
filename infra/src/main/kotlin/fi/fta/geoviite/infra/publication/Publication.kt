package fi.fta.geoviite.infra.publication

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DesignBranch
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.JointNumber
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.LayoutContext
import fi.fta.geoviite.infra.common.LocationTrackDescriptionBase
import fi.fta.geoviite.infra.common.MainBranch
import fi.fta.geoviite.infra.common.MeasurementMethod
import fi.fta.geoviite.infra.common.Oid
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.common.StringId
import fi.fta.geoviite.infra.common.SwitchName
import fi.fta.geoviite.infra.common.TrackMeter
import fi.fta.geoviite.infra.common.TrackNumber
import fi.fta.geoviite.infra.common.TrackNumberDescription
import fi.fta.geoviite.infra.common.Uuid
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.localization.LocalizationKey
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
import fi.fta.geoviite.infra.tracklayout.DesignAssetState
import fi.fta.geoviite.infra.tracklayout.KmPostGkLocationSource
import fi.fta.geoviite.infra.tracklayout.LayoutAlignment
import fi.fta.geoviite.infra.tracklayout.LayoutAsset
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LayoutStateCategory
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.LocationTrackDescriptionSuffix
import fi.fta.geoviite.infra.tracklayout.LocationTrackM
import fi.fta.geoviite.infra.tracklayout.LocationTrackOwner
import fi.fta.geoviite.infra.tracklayout.LocationTrackState
import fi.fta.geoviite.infra.tracklayout.LocationTrackType
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.ReferenceLineM
import fi.fta.geoviite.infra.util.ESCAPED_NEW_LINE
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.NEW_LINE_CHARACTER
import fi.fta.geoviite.infra.util.StringSanitizer
import fi.fta.geoviite.infra.util.UnsafeString
import fi.fta.geoviite.infra.util.normalizeLinebreaksToUnixFormat
import java.time.Instant

data class PublicationMessage private constructor(private val value: String) :
    Comparable<PublicationMessage>, CharSequence by value {

    companion object {
        const val ALLOWED_CHARACTERS = FreeText.ALLOWED_CHARACTERS + NEW_LINE_CHARACTER
        val ALLOWED_LENGTH = 0..500

        val sanitizer = StringSanitizer(PublicationMessage::class, ALLOWED_CHARACTERS, ALLOWED_LENGTH)

        @JvmStatic @JsonCreator fun of(value: String) = PublicationMessage(normalizeLinebreaksToUnixFormat(value))
    }

    init {
        sanitizer.assertSanitized(value)
    }

    @JsonValue override fun toString(): String = value

    override fun compareTo(other: PublicationMessage): Int = value.compareTo(other.value)

    fun escapeNewLines(): FreeText {
        return FreeText(UnsafeString(value.replace(NEW_LINE_CHARACTER, ESCAPED_NEW_LINE)))
    }
}

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

sealed class PublishedAsset {
    abstract val type: PublishableObjectType
}

data class PublishedAssetTrackNumber(val asset: LayoutTrackNumber) : PublishedAsset() {
    override val type = PublishableObjectType.TRACK_NUMBER
}

data class PublishedAssetReferenceLine(val asset: ReferenceLine) : PublishedAsset() {
    override val type = PublishableObjectType.REFERENCE_LINE
}

data class PublishedAssetLocationTrack(val asset: LocationTrack) : PublishedAsset() {
    override val type = PublishableObjectType.LOCATION_TRACK
}

data class PublishedAssetSwitch(val asset: LayoutSwitch) : PublishedAsset() {
    override val type = PublishableObjectType.SWITCH
}

data class PublishedAssetKmPost(val asset: LayoutKmPost) : PublishedAsset() {
    override val type = PublishableObjectType.KM_POST
}

data class PublicationTableItem(
    val name: FreeText,
    val publicationId: IntId<Publication>,
    val asset: PublishedAsset,
    val trackNumbers: List<TrackNumber>,
    val changedKmNumbers: List<Range<KmNumber>>,
    val operation: Operation,
    val publicationTime: Instant,
    val publicationUser: UserName,
    val message: PublicationMessage,
    val ratkoPushTime: Instant?,
    val propChanges: List<PublicationChange<*>>,
) {
    val id: StringId<PublicationTableItem> = StringId(hashCode().toString())
}

data class ChangeValue<T>(val oldValue: T?, val newValue: T?, val localizationKey: LocalizationKey? = null) {
    constructor(
        oldValue: T?,
        newValue: T?,
        localizationKey: String?,
    ) : this(oldValue, newValue, localizationKey?.let(LocalizationKey::of))
}

data class PublicationChange<T>(
    val propKey: PropKey,
    val value: ChangeValue<T>,
    // The string is intentionally nullable to allow omitting the whole field (change lists can be
    // large)
    val remark: String?,
)

data class PropKey(val key: LocalizationKey, val params: LocalizationParams = LocalizationParams.empty) {
    constructor(
        key: String,
        params: LocalizationParams = LocalizationParams.empty,
    ) : this(LocalizationKey.of(key), params)
}

interface IPublication {
    val id: IntId<Publication>
    val uuid: Uuid<Publication>
    val publicationTime: Instant
    val publicationUser: UserName
    val message: PublicationMessage
    val layoutBranch: PublishedInBranch
    val cause: PublicationCause
}

data class Publication(
    override val id: IntId<Publication>,
    override val uuid: Uuid<Publication>,
    override val publicationTime: Instant,
    override val publicationUser: UserName,
    override val message: PublicationMessage,
    override val layoutBranch: PublishedInBranch,
    override val cause: PublicationCause,
) : IPublication

sealed class PublishedInBranch {
    abstract val branch: LayoutBranch
}

data object PublishedInMain : PublishedInBranch() {
    override val branch = LayoutBranch.main
}

data class PublishedInDesign(
    val designBranch: DesignBranch,
    @JsonIgnore val designVersion: Int,
    val parentPublicationId: IntId<Publication>?,
) : PublishedInBranch() {
    override val branch = designBranch
}

enum class PublicationCause {
    MANUAL, // the usual cause: All user-created publications
    LAYOUT_DESIGN_CHANGE,
    LAYOUT_DESIGN_DELETE,
    LAYOUT_DESIGN_CANCELLATION,
    MERGE_FINALIZATION,
    CALCULATED_CHANGE,
}

data class PublishedItemListing<T>(val directChanges: List<T>, val indirectChanges: List<T>)

data class PublishedTrackNumber(
    val version: LayoutRowVersion<LayoutTrackNumber>,
    val number: TrackNumber,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
) {
    val id: IntId<LayoutTrackNumber>
        get() = version.id
}

data class PublishedReferenceLine(
    val version: LayoutRowVersion<ReferenceLine>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
) {
    val id: IntId<ReferenceLine>
        get() = version.id
}

data class PublishedLocationTrack(
    val version: LayoutRowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
) {
    val id: IntId<LocationTrack>
        get() = version.id
}

data class PublishedSwitch(
    val version: LayoutRowVersion<LayoutSwitch>,
    val trackNumberIds: Set<IntId<LayoutTrackNumber>>,
    val name: SwitchName,
    val operation: Operation,
    @JsonIgnore val changedJoints: List<SwitchJointChange>,
) {
    val id: IntId<LayoutSwitch>
        get() = version.id
}

data class PublishedKmPost(
    val version: LayoutRowVersion<LayoutKmPost>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation,
) {
    val id: IntId<LayoutKmPost>
        get() = version.id
}

data class PublishedIndirectChanges(
    // Currently only used by Ratko integration
    @JsonIgnore val trackNumbers: List<PublishedTrackNumber>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
)

data class PublicationDetails(
    val publication: Publication,
    val trackNumbers: List<PublishedTrackNumber>,
    val referenceLines: List<PublishedReferenceLine>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
    val kmPosts: List<PublishedKmPost>,
    val ratkoPushStatus: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
    val indirectChanges: PublishedIndirectChanges,
    val split: SplitHeader?,
) : IPublication by publication {
    val allPublishedTrackNumbers = trackNumbers + indirectChanges.trackNumbers
    val allPublishedLocationTracks = locationTracks + indirectChanges.locationTracks
    val allPublishedSwitches = switches + indirectChanges.switches
}

enum class PublishableObjectType {
    TRACK_NUMBER,
    LOCATION_TRACK,
    REFERENCE_LINE,
    SWITCH,
    KM_POST,
}

data class PublishableObjectIdAndType(val id: IntId<*>, val type: PublishableObjectType) {
    companion object {
        fun trackNumber(id: IntId<LayoutTrackNumber>) =
            PublishableObjectIdAndType(id, PublishableObjectType.TRACK_NUMBER)

        fun locationTrack(id: IntId<LayoutTrackNumber>) =
            PublishableObjectIdAndType(id, PublishableObjectType.LOCATION_TRACK)

        fun referenceLine(id: IntId<LayoutTrackNumber>) =
            PublishableObjectIdAndType(id, PublishableObjectType.REFERENCE_LINE)

        fun switch(id: IntId<LayoutTrackNumber>) = PublishableObjectIdAndType(id, PublishableObjectType.SWITCH)

        fun kmPost(id: IntId<LayoutTrackNumber>) = PublishableObjectIdAndType(id, PublishableObjectType.KM_POST)
    }

    @Suppress("UNCHECKED_CAST")
    fun locationTrackId() = if (type != PublishableObjectType.LOCATION_TRACK) null else id as IntId<LocationTrack>

    fun isTrackNumber(other: IntId<LayoutTrackNumber>) = type == PublishableObjectType.TRACK_NUMBER && id == other

    fun isLocationTrack(other: IntId<LocationTrack>) = type == PublishableObjectType.LOCATION_TRACK && id == other

    fun isReferenceLine(other: IntId<ReferenceLine>) = type == PublishableObjectType.REFERENCE_LINE && id == other

    fun isSwitch(other: IntId<LayoutSwitch>) = type == PublishableObjectType.SWITCH && id == other

    fun isKmPost(other: IntId<LayoutKmPost>) = type == PublishableObjectType.KM_POST && id == other
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

data class ValidatedAsset<T>(val id: IntId<T>, val errors: List<LayoutValidationIssue>)

data class PublicationCandidates(
    val transition: LayoutContextTransition,
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

    fun getValidationVersions(transition: LayoutContextTransition, splitVersions: List<RowVersion<Split>>) =
        ValidationVersions(
            target = ValidateTransition(transition),
            trackNumbers = trackNumbers.map(TrackNumberPublicationCandidate::getPublicationVersion),
            referenceLines = referenceLines.map(ReferenceLinePublicationCandidate::getPublicationVersion),
            locationTracks = locationTracks.map(LocationTrackPublicationCandidate::getPublicationVersion),
            switches = switches.map(SwitchPublicationCandidate::getPublicationVersion),
            kmPosts = kmPosts.map(KmPostPublicationCandidate::getPublicationVersion),
            splits = splitVersions,
        )

    fun filter(request: PublicationRequestIds) =
        copy(
            trackNumbers = trackNumbers.filter { candidate -> request.trackNumbers.contains(candidate.id) },
            referenceLines = referenceLines.filter { candidate -> request.referenceLines.contains(candidate.id) },
            locationTracks = locationTracks.filter { candidate -> request.locationTracks.contains(candidate.id) },
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
    val target: ValidationTarget,
    val trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
    val locationTracks: List<LayoutRowVersion<LocationTrack>>,
    val referenceLines: List<LayoutRowVersion<ReferenceLine>>,
    val switches: List<LayoutRowVersion<LayoutSwitch>>,
    val kmPosts: List<LayoutRowVersion<LayoutKmPost>>,
    val splits: List<RowVersion<Split>>,
) {
    companion object {
        fun emptyWithTarget(target: ValidationTarget) =
            ValidationVersions(target, listOf(), listOf(), listOf(), listOf(), listOf(), listOf())
    }

    fun containsLocationTrack(id: IntId<LocationTrack>) = locationTracks.any { it.id == id }

    fun containsKmPost(id: IntId<LayoutKmPost>) = kmPosts.any { it.id == id }

    fun containsSwitch(id: IntId<LayoutSwitch>) = switches.any { it.id == id }

    fun containsSplit(id: IntId<Split>): Boolean = splits.any { it.id == id }

    fun findTrackNumber(id: IntId<LayoutTrackNumber>) = trackNumbers.find { it.id == id }

    fun findLocationTrack(id: IntId<LocationTrack>) = locationTracks.find { it.id == id }

    fun findSwitch(id: IntId<LayoutSwitch>) = switches.find { it.id == id }

    fun getTrackNumberIds() = trackNumbers.map { v -> v.id }

    fun getReferenceLineIds() = referenceLines.map { v -> v.id }

    fun getLocationTrackIds() = locationTracks.map { v -> v.id }

    fun getSwitchIds() = switches.map { v -> v.id }

    fun getKmPostIds() = kmPosts.map { v -> v.id }

    fun getSplitIds() = splits.map { v -> v.id }
}

data class PublicationGroup(val id: IntId<Split>)

data class PublicationRequestIds(
    val trackNumbers: List<IntId<LayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<LayoutSwitch>>,
    val kmPosts: List<IntId<LayoutKmPost>>,
) {
    companion object {
        fun empty(): PublicationRequestIds = PublicationRequestIds(listOf(), listOf(), listOf(), listOf(), listOf())
    }

    operator fun minus(other: PublicationRequestIds) =
        PublicationRequestIds(
            trackNumbers - other.trackNumbers.toSet(),
            locationTracks - other.locationTracks.toSet(),
            referenceLines - other.referenceLines.toSet(),
            switches - other.switches.toSet(),
            kmPosts - other.kmPosts.toSet(),
        )

    operator fun plus(other: PublicationRequestIds) =
        PublicationRequestIds(
            (trackNumbers.toSet() + other.trackNumbers).toList(),
            (locationTracks.toSet() + other.locationTracks).toList(),
            (referenceLines.toSet() + other.referenceLines).toList(),
            (switches.toSet() + other.switches).toList(),
            (kmPosts.toSet() + other.kmPosts).toList(),
        )

    fun isEmpty() =
        trackNumbers.isEmpty() &&
            locationTracks.isEmpty() &&
            referenceLines.isEmpty() &&
            switches.isEmpty() &&
            kmPosts.isEmpty()
}

data class PublicationRequest(val content: PublicationRequestIds, val message: PublicationMessage)

data class PublicationResult(
    val publicationId: IntId<Publication>,
    val trackNumbers: List<PublicationResultVersions<LayoutTrackNumber>>,
    val referenceLines: List<PublicationResultVersions<ReferenceLine>>,
    val locationTracks: List<PublicationResultVersions<LocationTrack>>,
    val switches: List<PublicationResultVersions<LayoutSwitch>>,
    val kmPosts: List<PublicationResultVersions<LayoutKmPost>>,
) {
    fun summarize() =
        PublicationResultSummary(
            publicationId,
            trackNumbers = trackNumbers.size,
            referenceLines = referenceLines.size,
            locationTracks = locationTracks.size,
            switches = switches.size,
            kmPosts = kmPosts.size,
        )
}

data class PublicationResultSummary(
    val publicationId: IntId<Publication>?,
    val trackNumbers: Int,
    val locationTracks: Int,
    val referenceLines: Int,
    val switches: Int,
    val kmPosts: Int,
)

enum class LayoutValidationIssueType {
    FATAL,
    ERROR,
    WARNING,
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
    ) : this(type, LocalizationKey.of(key), localizationParams(params))
}

interface PublicationCandidate<T : LayoutAsset<T>> {
    val type: PublishableObjectType
    val rowVersion: LayoutRowVersion<T>
    val draftChangeTime: Instant
    val userName: UserName
    val issues: List<LayoutValidationIssue>
    val operation: Operation?
    val publicationGroup: PublicationGroup?
    val designAssetState: DesignAssetState?

    val id: IntId<T>
        get() = rowVersion.id

    val cancelled: Boolean
        get() = designAssetState == DesignAssetState.CANCELLED

    fun getPublicationVersion() = rowVersion
}

data class TrackNumberPublicationCandidate(
    override val rowVersion: LayoutRowVersion<LayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    override val designAssetState: DesignAssetState?,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<LayoutTrackNumber> {
    override val type = PublishableObjectType.TRACK_NUMBER
}

data class ReferenceLinePublicationCandidate(
    override val rowVersion: LayoutRowVersion<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<LayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation?,
    override val publicationGroup: PublicationGroup? = null,
    override val designAssetState: DesignAssetState?,
    val boundingBox: BoundingBox?,
    val geometryChanges: GeometryChangeRanges<ReferenceLineM>?,
) : PublicationCandidate<ReferenceLine> {
    override val type = PublishableObjectType.REFERENCE_LINE
}

data class LocationTrackPublicationCandidate(
    override val rowVersion: LayoutRowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<LayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    override val designAssetState: DesignAssetState?,
    val boundingBox: BoundingBox?,
    val geometryChanges: GeometryChangeRanges<LocationTrackM>?,
) : PublicationCandidate<LocationTrack> {
    override val type = PublishableObjectType.LOCATION_TRACK
}

data class SwitchPublicationCandidate(
    override val rowVersion: LayoutRowVersion<LayoutSwitch>,
    val name: SwitchName,
    val trackNumberIds: List<IntId<LayoutTrackNumber>>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    override val designAssetState: DesignAssetState?,
    val location: Point?,
) : PublicationCandidate<LayoutSwitch> {
    override val type = PublishableObjectType.SWITCH
}

data class KmPostPublicationCandidate(
    override val rowVersion: LayoutRowVersion<LayoutKmPost>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val issues: List<LayoutValidationIssue> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    override val designAssetState: DesignAssetState?,
    val location: Point?,
) : PublicationCandidate<LayoutKmPost> {
    override val type = PublishableObjectType.KM_POST
}

data class SwitchLocationTrack(
    val id: IntId<LocationTrack>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val name: AlignmentName,
    val joints: List<PublicationSwitchJoint>,
)

data class PublicationSwitchJoint(val jointNumber: JointNumber, val location: Point, val isPresentationJoint: Boolean)

data class Change<T>(val old: T?, val new: T) {
    companion object {
        fun <S, T> of(old: S?, new: S, getter: (S) -> T): Change<T> = Change(old?.let(getter), getter(new))
    }

    fun <S> map(op: (T) -> S): Change<S> = Change(old?.let(op), op(new))
}

/**
 * Turns a change of lists into a list of changes, picking items by their id, designated byt the [getId] lambda. If some
 * item is duplicated in either list (id is not unique in old or new state), then only the first instance one of said
 * item will be considered for the change.
 */
fun <T, S> Change<List<T>>.itemize(getId: (T) -> S): List<Pair<S, Change<T?>>> {
    val oldItems = old?.distinctBy(getId)?.associateBy(getId) ?: emptyMap()
    val newItems = new.distinctBy(getId).associateBy(getId)
    return (oldItems.keys + newItems.keys).distinct().map { key -> key to Change(oldItems[key], newItems[key]) }
}

fun <T> Change<T?>.ifHasEndState(): Change<T>? = if (new != null) Change(old, new) else null

data class LocationTrackChanges(
    val id: IntId<LocationTrack>,
    val name: Change<AlignmentName>,
    val descriptionBase: Change<LocationTrackDescriptionBase>,
    val descriptionSuffix: Change<LocationTrackDescriptionSuffix>,
    val state: Change<LocationTrackState>,
    val duplicateOf: Change<IntId<LocationTrack>?>,
    val type: Change<LocationTrackType>,
    val length: Change<Double>,
    // TODO: These should not be nullable, but current test data contains broken location tracks
    val startPoint: Change<Point?>,
    val endPoint: Change<Point?>,
    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
    val geometryChangeSummaries: List<GeometryChangeSummary>?,
    val owner: Change<IntId<LocationTrackOwner>>,
)

// Todo: Consider making LayoutSwitch use this for trapPoint as well
enum class TrapPoint {
    YES,
    NO,
    UNKNOWN,
}

data class TrackNumberJointLocationChange(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val jointNumber: JointNumber,
    val location: Change<Point?>,
)

data class TrackJointChange(
    val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val joints: Change<List<JointNumber>?>,
)

data class SwitchChanges(
    val id: IntId<LayoutSwitch>,
    val name: Change<SwitchName>,
    val state: Change<LayoutStateCategory>,
    val trapPoint: Change<TrapPoint>,
    val type: Change<SwitchType>,
    val owner: Change<MetaDataName>,
    val measurementMethod: Change<MeasurementMethod?>,
    val trackConnections: Change<List<SwitchLocationTrack>>,
) {
    val trackJoints: List<TrackJointChange>
        get() =
            getLocationTrackIds().mapNotNull { id ->
                trackConnections
                    .map { tracks -> tracks.find { it.id == id } }
                    .let { trackChange ->
                        val name = trackChange.new?.name ?: trackChange.old?.name
                        val joints = trackChange.map { t -> t?.joints?.map { j -> j.jointNumber } }
                        if (name != null) TrackJointChange(id, name, joints) else null
                    }
            }

    val trackNumberJointLocations: List<TrackNumberJointLocationChange>
        get() =
            getTrackNumberJointNumbers().map { (tnId, jointNumber) ->
                TrackNumberJointLocationChange(
                    trackNumberId = tnId,
                    jointNumber = jointNumber,
                    location = getTrackNumberJointLocation(tnId, jointNumber),
                )
            }

    private fun getLocationTrackIds(): List<IntId<LocationTrack>> =
        ((trackConnections.old?.map { t -> t.id } ?: emptyList()) + trackConnections.new.map { t -> t.id })
            .distinct()
            .sortedBy { it.intValue }

    private fun getTrackNumberJointNumbers(): List<Pair<IntId<LayoutTrackNumber>, JointNumber>> =
        trackConnections
            .map { tracks -> tracks.flatMap { t -> t.joints.map { t.trackNumberId to it.jointNumber } }.distinct() }
            .let { (old, new) -> (old ?: emptyList()) + new }
            .distinct()

    private fun getTrackNumberJointLocation(
        trackNumberId: IntId<LayoutTrackNumber>,
        jointNumber: JointNumber,
    ): Change<Point?> =
        trackConnections.map { tracks -> getTrackNumberJointLocation(tracks, trackNumberId, jointNumber) }

    private fun getTrackNumberJointLocation(
        tracks: List<SwitchLocationTrack>,
        id: IntId<LayoutTrackNumber>,
        jointNumber: JointNumber,
    ): Point? =
        tracks
            .asSequence()
            .filter { t -> t.trackNumberId == id }
            .mapNotNull { t -> t.joints.find { j -> j.jointNumber == jointNumber } }
            .firstOrNull()
            ?.location
}

data class ReferenceLineChanges(
    val id: IntId<ReferenceLine>,
    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
    val length: Change<Double>,
    val startPoint: Change<Point?>,
    val endPoint: Change<Point?>,
    val alignmentVersion: Change<RowVersion<LayoutAlignment>>,
)

data class TrackNumberChanges(
    val id: IntId<LayoutTrackNumber>,
    val trackNumber: Change<TrackNumber>,
    val description: Change<TrackNumberDescription>,
    val state: Change<LayoutState>,
    // TODO: These should not be nullable, but current test data contains broken track numbers
    val startAddress: Change<TrackMeter?>,
    val endPoint: Change<Point?>,
)

data class KmPostChanges(
    val id: IntId<LayoutKmPost>,
    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
    val kmNumber: Change<KmNumber>,
    val state: Change<LayoutState>,
    val location: Change<Point>,
    val gkLocation: Change<GeometryPoint>,
    val gkSrid: Change<Srid>,
    val gkLocationSource: Change<KmPostGkLocationSource>,
    val gkLocationConfirmed: Change<Boolean>,
)

data class SwitchChangeIds(val name: String, val externalId: Oid<LayoutSwitch>?)

data class LocationTrackPublicationSwitchLinkChanges(
    val old: Map<IntId<LayoutSwitch>, SwitchChangeIds>,
    val new: Map<IntId<LayoutSwitch>, SwitchChangeIds>,
)

data class SplitInPublication(
    val id: IntId<Publication>,
    val splitId: IntId<Split>,
    val locationTrack: LocationTrack,
    val locationTrackOid: Oid<LocationTrack>,
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

sealed class LayoutContextTransition {

    companion object {
        fun mergeToMainFrom(layoutBranch: LayoutBranch): LayoutContextTransition {
            assert(layoutBranch is DesignBranch) { "Can't transition from main layout context to main layout context" }
            return MergeFromDesign(layoutBranch as DesignBranch)
        }

        fun publicationIn(layoutBranch: LayoutBranch): LayoutContextTransition =
            if (layoutBranch is DesignBranch) PublicationInDesign(layoutBranch) else PublicationInMain
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

fun publicationInOrMergeFromBranch(branch: LayoutBranch, fromState: PublicationState): LayoutContextTransition {
    assert(branch is DesignBranch || fromState == PublicationState.DRAFT) {
        "Can't transition layout context from main-official"
    }
    return if (branch is DesignBranch) {
        if (fromState == PublicationState.DRAFT) PublicationInDesign(branch) else MergeFromDesign(branch)
    } else PublicationInMain
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

data class InheritanceFromPublicationInMain(override val baseBranch: DesignBranch) : LayoutContextTransition() {
    override val candidateBranch = MainBranch.instance
    override val candidatePublicationState = PublicationState.DRAFT
    override val basePublicationState = PublicationState.OFFICIAL
}

enum class ValidationTargetType {
    PUBLISHING,
    MERGING_TO_MAIN,
    VALIDATING_STATE,
}

sealed class ValidationTarget {
    val candidateContext
        get() = LayoutContext.of(candidateBranch, candidatePublicationState)

    val baseContext
        get() = LayoutContext.of(baseBranch, basePublicationState)

    abstract val candidateBranch: LayoutBranch
    abstract val baseBranch: LayoutBranch
    abstract val candidatePublicationState: PublicationState
    abstract val basePublicationState: PublicationState
    abstract val type: ValidationTargetType

    fun sqlParameters(): Map<String, Any?> =
        mapOf(
            "candidate_state" to candidatePublicationState.name,
            "candidate_design_id" to candidateBranch.designId?.intValue,
            "base_state" to basePublicationState.name,
            "base_design_id" to baseBranch.designId?.intValue,
        )
}

data class ValidateTransition(val transition: LayoutContextTransition) : ValidationTarget() {

    override val candidateBranch = transition.candidateBranch
    override val baseBranch = transition.baseBranch
    override val candidatePublicationState = transition.candidatePublicationState
    override val basePublicationState = transition.basePublicationState
    override val type =
        if (transition is MergeFromDesign) ValidationTargetType.MERGING_TO_MAIN else ValidationTargetType.PUBLISHING
}

data class ValidateContext(val context: LayoutContext) : ValidationTarget() {

    override val candidateBranch = context.branch
    override val baseBranch = context.branch
    override val candidatePublicationState = context.state
    override val basePublicationState = context.state
    override val type = ValidationTargetType.VALIDATING_STATE
}

fun draftTransitionOrOfficialState(publicationState: PublicationState, branch: LayoutBranch): ValidationTarget =
    if (publicationState == PublicationState.DRAFT) {
        ValidateTransition(LayoutContextTransition.publicationIn(branch))
    } else {
        ValidateContext(LayoutContext.of(branch, publicationState))
    }

data class PublishedVersions(
    val trackNumbers: List<Change<LayoutRowVersion<LayoutTrackNumber>>>,
    val referenceLines: List<Change<LayoutRowVersion<ReferenceLine>>>,
    val locationTracks: List<Change<LayoutRowVersion<LocationTrack>>>,
    val switches: List<Change<LayoutRowVersion<LayoutSwitch>>>,
    val kmPosts: List<Change<LayoutRowVersion<LayoutKmPost>>>,
)

data class PreparedPublicationRequest(
    val branch: LayoutBranch,
    val versions: ValidationVersions,
    val calculatedChanges: CalculatedChanges,
    val message: PublicationMessage,
    val cause: PublicationCause,
    val parentId: IntId<Publication>?,
)

data class PublicationResultVersions<T : LayoutAsset<T>>(
    val published: LayoutRowVersion<T>,
    val base: LayoutRowVersion<T>?,
    val completed: Pair<DesignBranch, LayoutRowVersion<T>>?,
) {
    val versionChange: Change<LayoutRowVersion<T>> = Change(base, published)
}
