package fi.fta.geoviite.infra.publication

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geography.GeometryPoint
import fi.fta.geoviite.infra.geometry.MetaDataName
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.RatkoPush
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.integration.SwitchJointChange
import fi.fta.geoviite.infra.integration.TrackNumberChange
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
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
import fi.fta.geoviite.infra.util.FreeTextWithNewLines
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

data class ChangeValue<T>(val oldValue: T?, val newValue: T?, val localizationKey: LocalizationKey? = null) {
    constructor(
        oldValue: T?,
        newValue: T?,
        localizationKey: String?,
    ) : this(oldValue, newValue, localizationKey?.let(::LocalizationKey))
}

data class PublicationChange<T>(
    val propKey: PropKey,
    val value: ChangeValue<T>,
    // The string is intentionally nullable to allow omitting the whole field (change lists can be
    // large)
    val remark: String?,
)

data class PropKey(val key: LocalizationKey, val params: LocalizationParams = LocalizationParams.empty) {
    constructor(key: String, params: LocalizationParams = LocalizationParams.empty) : this(LocalizationKey(key), params)
}

interface IPublication {
    val id: IntId<Publication>
    val uuid: Uuid<Publication>
    val layoutBranch: PublishedInBranch
    val publicationTime: Instant
    val publicationUser: UserName
    val message: FreeTextWithNewLines
    val cause: PublicationCause
}

data class Publication(
    override val id: IntId<Publication>,
    override val uuid: Uuid<Publication>,
    override val layoutBranch: PublishedInBranch,
    override val publicationTime: Instant,
    override val publicationUser: UserName,
    override val message: FreeTextWithNewLines,
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
    LAYOUT_DESIGN_CANCELLATION,
    MERGE_FINALIZATION,
    CALCULATED_CHANGE,
}

data class PublishedItemListing<T>(val directChanges: List<T>, val indirectChanges: List<T>)

data class PublishedTrackNumber(
    val id: IntId<LayoutTrackNumber>,
    val number: TrackNumber,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

// data class PublishedTrackNumber(
//    val version: LayoutRowVersion<LayoutTrackNumber>,
//    val number: TrackNumber,
//    val operation: Operation,
//    val changedKmNumbers: Set<KmNumber>,
// ) {
//    val id: IntId<LayoutTrackNumber>
//        get() = version.id
// }
//
data class PublishedReferenceLine(
    val id: IntId<ReferenceLine>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

// data class PublishedReferenceLine(
//    val version: LayoutRowVersion<ReferenceLine>,
//    val trackNumberId: IntId<LayoutTrackNumber>,
//    val operation: Operation,
//    val changedKmNumbers: Set<KmNumber>,
// ) {
//    val id: IntId<ReferenceLine>
//        get() = version.id
// }

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
    val id: IntId<LayoutSwitch>,
    val trackNumberIds: Set<IntId<LayoutTrackNumber>>,
    val locationTrackIds: Set<IntId<LocationTrack>>,
    val name: SwitchName,
    val operation: Operation,
    @JsonIgnore val changedJoints: List<SwitchJointChange>,
)

// data class PublishedSwitch(
//    val version: LayoutRowVersion<LayoutSwitch>,
//    val switchTracks: List<AugLocationTrackCacheKey>,
//    val name: SwitchName,
//    val operation: Operation,
//    @JsonIgnore val changedJoints: List<SwitchJointChange>,
// ) {
//    val id: IntId<LayoutSwitch>
//        get() = version.id
//
//    val trackIds: Set<IntId<LocationTrack>>
//        get() = switchTracks.map { t -> t.trackVersion.id }.toSet()
//
//    val trackNumberIds: Set<IntId<LayoutTrackNumber>>
//        get() = switchTracks.map { t -> t.trackNumberVersion.id }.toSet()
// }

data class PublishedKmPost(
    val id: IntId<LayoutKmPost>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation,
)

// data class PublishedKmPost(
//    val version: LayoutRowVersion<LayoutKmPost>,
//    val trackNumberId: IntId<LayoutTrackNumber>,
//    val kmNumber: KmNumber,
//    val operation: Operation,
// ) {
//    val id: IntId<LayoutKmPost>
//        get() = version.id
// }
//
data class PublishedIndirectChanges(
    // Currently only used by Ratko integration
    @JsonIgnore val trackNumbers: List<PublishedTrackNumber>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
)

data class PublishedIndirectChangeVersions(
    val trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
    val locationTracks: List<AugLocationTrackCacheKey>,
    val switches: List<LayoutRowVersion<LayoutSwitch>>,
)

sealed class PublishedVersion<T : LayoutAsset<T>> {
    abstract val oldVersion: LayoutRowVersion<T>?
    abstract val newVersion: LayoutRowVersion<T>
    abstract val operation: Operation

    val id: IntId<T>
        get() = newVersion.id
}

data class PublishedKmPostVersion(
    override val oldVersion: LayoutRowVersion<LayoutKmPost>?,
    override val newVersion: LayoutRowVersion<LayoutKmPost>,
    override val operation: Operation,
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
) : PublishedVersion<LayoutKmPost>() {
    val trackNumberId: IntId<LayoutTrackNumber>
        get() = trackNumberVersion.id
}

data class PublishedLocationTrackVersion(
    override val oldVersion: LayoutRowVersion<LocationTrack>?,
    override val operation: Operation,
    val newCacheKey: AugLocationTrackCacheKey,
    val changedKmNumbers: Set<KmNumber>,
    val startPoint: Change<Point>,
    val endPoint: Change<Point>,
    val geometryChangeSummaries: List<GeometryChangeSummary>,
) : PublishedVersion<LocationTrack>() {
    override val newVersion: LayoutRowVersion<LocationTrack>
        get() = newCacheKey.trackVersion

    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>
        get() = newCacheKey.trackNumberVersion

    val trackNumberId: IntId<LayoutTrackNumber>
        get() = trackNumberVersion.id
}

data class PublishedTrackNumberVersion(
    override val oldVersion: LayoutRowVersion<LayoutTrackNumber>?,
    override val newVersion: LayoutRowVersion<LayoutTrackNumber>,
    override val operation: Operation,
    // TODO: GVT-3080 it's kind of pointless that the km-numbers are both her and in the refence line.
    //  Then again, we should combine these entire objects anyhow as they're 1-1
    val changedKmNumbers: Set<KmNumber>,
    val endPoint: Change<Point>,
    val startAddress: Change<TrackMeter>,
) : PublishedVersion<LayoutTrackNumber>()

data class PublishedReferenceLineVersion(
    override val oldVersion: LayoutRowVersion<ReferenceLine>?,
    override val newVersion: LayoutRowVersion<ReferenceLine>,
    override val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
    val start: Change<Point>,
    val end: Change<Point>,
) : PublishedVersion<ReferenceLine>()

data class PublishedSwitchVersion(
    override val oldVersion: LayoutRowVersion<LayoutSwitch>?,
    override val newVersion: LayoutRowVersion<LayoutSwitch>,
    override val operation: Operation,
    val switchTracks: List<AugLocationTrackCacheKey>,
    val joints: List<PublicationDao.PublicationSwitchJoint>,
    val type: Change<SwitchType>,
    val owner: Change<MetaDataName>,
    val measurementMethod: Change<MeasurementMethod>,
) : PublishedVersion<LayoutSwitch>() {
    val trackIds: Set<IntId<LocationTrack>>
        get() = switchTracks.map { t -> t.trackVersion.id }.toSet()

    val trackNumberIds: Set<IntId<LayoutTrackNumber>>
        get() = switchTracks.map { t -> t.trackNumberVersion.id }.toSet()
}

data class PublicationVersions(
    val id: IntId<Publication>,
    val trackNumbers: List<PublishedTrackNumberVersion>,
    val referenceLines: List<PublishedReferenceLineVersion>,
    val locationTracks: List<PublishedLocationTrackVersion>,
    val switches: List<PublishedSwitchVersion>,
    val kmPosts: List<PublishedKmPostVersion>,
    val indirectLocationTrackChanges: List<PublishedLocationTrackVersion>,
    val indirectSwitchChanges: List<PublishedSwitchVersion>,
)

data class PublicationDetails(
    private val publication: Publication,
    val ratkoPushStatus: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
    val trackNumbers: List<PublishedTrackNumber>,
    val referenceLines: List<PublishedReferenceLine>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
    val kmPosts: List<PublishedKmPost>,
    val indirectChanges: PublishedIndirectChanges,
    val split: SplitHeader?,
) : IPublication by publication {
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

data class PublicationRequest(val content: PublicationRequestIds, val message: FreeTextWithNewLines)

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
    ) : this(type, LocalizationKey(key), localizationParams(params))
}

interface PublicationCandidate<T : LayoutAsset<T>> {
    val type: DraftChangeType
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
    override val type = DraftChangeType.TRACK_NUMBER
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
    override val type = DraftChangeType.REFERENCE_LINE
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
    override val type = DraftChangeType.LOCATION_TRACK
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
    override val type = DraftChangeType.SWITCH
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
    override val type = DraftChangeType.KM_POST
}

data class SwitchLocationTrack(
    val oldTrackVersion: LayoutRowVersion<LocationTrack>,
    val newTrackCacheKey: AugLocationTrackCacheKey,
) {
    val locationTrackId: IntId<LocationTrack>
        get() = newTrackCacheKey.trackVersion.id

    val trackNumberId: IntId<LayoutTrackNumber>
        get() = newTrackCacheKey.trackNumberVersion.id
}

// TODO: GVT-3080: the new value should not be nullable - make a Change<T?> where needed
data class Change<T>(val old: T?, val new: T?) {
    companion object {
        fun <S, T> of(old: S?, new: S?, getter: (S) -> T?): Change<T> = Change(old?.let(getter), new?.let(getter))
    }

    fun <S> map(op: (T) -> S?): Change<S> = Change(old?.let(op), new?.let(op))
}

// data class LocationTrackChanges(
//    val id: IntId<LocationTrack>,
//    val namingScheme: Change<LocationTrackNamingScheme>,
//    val nameFreeText: Change<AlignmentName>,
//    val nameSpecifier: Change<LocationTrackNameSpecifier>,
//    val descriptionBase: Change<LocationTrackDescriptionBase>,
//    val descriptionSuffix: Change<LocationTrackDescriptionSuffix>,
//    val state: Change<LocationTrackState>,
//    val duplicateOf: Change<IntId<LocationTrack>>,
//    val type: Change<LocationTrackType>,
//    val length: Change<Double>,
//    val startPoint: Change<Point>,
//    val endPoint: Change<Point>,
//    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
//    val geometryChangeSummaries: List<GeometryChangeSummary>?,
//    val owner: Change<IntId<LocationTrackOwner>>,
// )
//
// Todo: Consider making LayoutSwitch use this for trapPoint as well
enum class TrapPoint {
    YES,
    NO,
    UNKNOWN,
}

sealed class AssetChange<T : LayoutAsset<T>>() {
    abstract val changeVersions: PublishedVersion<T>
    abstract val old: T?
    abstract val new: T

    val id: IntId<T>
        get() = changeVersions.id

    fun <S> getChange(valueGetter: (T) -> S?): Change<S> = Change.of(old, new, valueGetter)
}

data class PublicationChanges(
    val publication: Publication,
    val ratkoPush: RatkoPush?,
    val trackNumbers: List<TrackNumberChange>,
    val referenceLines: List<ReferenceLineChange>,
    val locationTracks: List<LocationTrackChange>,
    val switches: List<SwitchChange>,
    val kmPosts: List<KmPostChange>,
)

data class SwitchChange(
    override val changeVersions: PublishedSwitchVersion,
    override val old: LayoutSwitch?,
    override val new: LayoutSwitch,
) : AssetChange<LayoutSwitch>() {
    val name: Change<SwitchName>
        get() = getChange(LayoutSwitch::name)

    val state: Change<LayoutStateCategory>
        get() = getChange(LayoutSwitch::stateCategory)

    val trapPoint: Change<TrapPoint>
        get() = getChange { value ->
            when (value.trapPoint) {
                null -> TrapPoint.UNKNOWN
                true -> TrapPoint.YES
                false -> TrapPoint.NO
            }
        }

    val joints: List<PublicationDao.PublicationSwitchJoint>
        get() = changeVersions.joints

    val type: Change<SwitchType>
        get() = changeVersions.type

    val owner: Change<MetaDataName>
        get() = changeVersions.owner

    val measurementMethod: Change<MeasurementMethod>
        get() = changeVersions.measurementMethod
}

data class TrackNumberChange(
    override val changeVersions: PublishedTrackNumberVersion,
    override val old: LayoutTrackNumber?,
    override val new: LayoutTrackNumber,
) : AssetChange<LayoutTrackNumber>() {
    val trackNumber: Change<TrackNumber>
        get() = getChange(LayoutTrackNumber::number)

    val description: Change<TrackNumberDescription>
        get() = getChange(LayoutTrackNumber::description)

    val state: Change<LayoutState>
        get() = getChange(LayoutTrackNumber::state)

    val startAddress: Change<TrackMeter>
        get() = changeVersions.startAddress

    val endPoint: Change<Point>
        get() = changeVersions.endPoint
}

data class ReferenceLineChange(
    override val changeVersions: PublishedReferenceLineVersion,
    override val old: ReferenceLine?,
    override val new: ReferenceLine,
) : AssetChange<ReferenceLine>() {
    val length: Change<Double>
        get() = getChange(ReferenceLine::length)

    val startPoint: Change<Point>
        get() = changeVersions.start

    val endPoint: Change<Point>
        get() = changeVersions.end
}

data class LocationTrackChange(
    override val changeVersions: PublishedLocationTrackVersion,
    override val old: LocationTrack?,
    val newAug: AugLocationTrack,
) : AssetChange<LocationTrack>() {
    override val new: LocationTrack = newAug.dbTrack

    val namingScheme: Change<LocationTrackNamingScheme>
        get() = getChange { lt -> lt.dbName.namingScheme }

    val nameFreeText: Change<AlignmentName>
        get() = getChange { lt -> lt.dbName.nameFreeText }

    val nameSpecifier: Change<LocationTrackNameSpecifier>
        get() = getChange { lt -> lt.dbName.nameSpecifier }

    val descriptionBase: Change<LocationTrackDescriptionBase>
        get() = getChange { lt -> lt.dbDescription.descriptionBase }

    val descriptionSuffix: Change<LocationTrackDescriptionSuffix>
        get() = getChange { lt -> lt.dbDescription.descriptionSuffix }

    val state: Change<LocationTrackState>
        get() = getChange(LocationTrack::state)

    val duplicateOf: Change<IntId<LocationTrack>>
        get() = getChange(LocationTrack::duplicateOf)

    val type: Change<LocationTrackType>
        get() = getChange(LocationTrack::type)

    val length: Change<Double>
        get() = getChange(LocationTrack::length)

    val startPoint: Change<Point>
        get() = changeVersions.startPoint

    val endPoint: Change<Point>
        get() = changeVersions.endPoint

    val trackNumberId: Change<IntId<LayoutTrackNumber>>
        get() = getChange(LocationTrack::trackNumberId)

    val geometryChangeSummaries: List<GeometryChangeSummary>
        get() = changeVersions.geometryChangeSummaries

    val owner: Change<IntId<LocationTrackOwner>>
        get() = getChange(LocationTrack::ownerId)
}

data class KmPostChange(
    override val changeVersions: PublishedKmPostVersion,
    override val old: LayoutKmPost?,
    override val new: LayoutKmPost,
) : AssetChange<LayoutKmPost>() {
    val trackNumberId: Change<IntId<LayoutTrackNumber>>
        get() = getChange(LayoutKmPost::trackNumberId)

    val kmNumber: Change<KmNumber>
        get() = getChange(LayoutKmPost::kmNumber)

    val state: Change<LayoutState>
        get() = getChange(LayoutKmPost::state)

    val location: Change<Point>
        get() = getChange(LayoutKmPost::layoutLocation)

    val gkLocation: Change<GeometryPoint>
        get() = getChange { kmp -> kmp.gkLocation?.location }

    val gkSrid: Change<Srid>
        get() = getChange { kmp -> kmp.gkLocation?.location?.srid }

    val gkLocationSource: Change<KmPostGkLocationSource>
        get() = getChange { kmp -> kmp.gkLocation?.source }

    val gkLocationConfirmed: Change<Boolean>
        get() = getChange { kmp -> kmp.gkLocation?.confirmed }
}

// data class SwitchChanges(
//    val id: IntId<LayoutSwitch>,
//    val name: Change<SwitchName>,
//    val state: Change<LayoutStateCategory>,
//    val trapPoint: Change<TrapPoint>,
//    val type: Change<SwitchType>,
//    val owner: Change<MetaDataName>,
//    val measurementMethod: Change<MeasurementMethod>,
//    val joints: List<PublicationDao.PublicationSwitchJoint>,
//    val locationTracks: List<SwitchLocationTrack>,
// )
//
// data class ReferenceLineChanges(
//    val id: IntId<ReferenceLine>,
//    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
//    val length: Change<Double>,
//    val startPoint: Change<Point>,
//    val endPoint: Change<Point>,
//    val alignmentVersion: Change<RowVersion<LayoutAlignment>>,
// )
//
// data class TrackNumberChanges(
//    val id: IntId<LayoutTrackNumber>,
//    val trackNumber: Change<TrackNumber>,
//    val description: Change<TrackNumberDescription>,
//    val state: Change<LayoutState>,
//    val startAddress: Change<TrackMeter>,
//    val endPoint: Change<Point>,
// )
//
// data class KmPostChanges(
//    val id: IntId<LayoutKmPost>,
//    val trackNumberId: Change<IntId<LayoutTrackNumber>>,
//    val kmNumber: Change<KmNumber>,
//    val state: Change<LayoutState>,
//    val location: Change<Point>,
//    val gkLocation: Change<GeometryPoint>,
//    val gkSrid: Change<Srid>,
//    val gkLocationSource: Change<KmPostGkLocationSource>,
//    val gkLocationConfirmed: Change<Boolean>,
// )
//
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
    val trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>>,
    val referenceLines: List<LayoutRowVersion<ReferenceLine>>,
    val locationTracks: List<LayoutRowVersion<LocationTrack>>,
    val switches: List<LayoutRowVersion<LayoutSwitch>>,
    val kmPosts: List<LayoutRowVersion<LayoutKmPost>>,
)

data class PreparedPublicationRequest(
    val branch: LayoutBranch,
    val versions: ValidationVersions,
    val calculatedChanges: CalculatedChanges,
    val message: FreeTextWithNewLines,
    val cause: PublicationCause,
    val parentId: IntId<Publication>?,
)

data class PublicationResultVersions<T : LayoutAsset<T>>(
    val published: LayoutRowVersion<T>,
    val completed: Pair<DesignBranch, LayoutRowVersion<T>>?,
)
