package fi.fta.geoviite.infra.publication

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
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
import fi.fta.geoviite.infra.switchLibrary.SwitchType
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText
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
    val name: String,
    val trackNumbers: List<TrackNumber>,
    val changedKmNumbers: List<Range<KmNumber>>,
    val operation: Operation,
    val publicationTime: Instant,
    val publicationUser: UserName,
    val message: String,
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
    constructor(oldValue: T?, newValue: T?, localizationKey: String?) : this(
        oldValue,
        newValue,
        localizationKey?.let(::LocalizationKey),
    )
}

data class PublicationChange<T>(
    val propKey: PropKey,
    val value: ChangeValue<T>,
    val remark: String?,
)

data class PropKey(
    val key: LocalizationKey,
    val params: LocalizationParams = LocalizationParams.empty,
) {
    constructor(key: String, params: LocalizationParams = LocalizationParams.empty) : this(LocalizationKey(key), params)
}

open class Publication(
    open val id: IntId<Publication>,
    open val publicationTime: Instant,
    open val publicationUser: UserName,
    open val message: String?,
)

data class PublishedItemListing<T>(
    val directChanges: List<T>,
    val indirectChanges: List<T>,
)

data class PublishedTrackNumber(
    val id: IntId<TrackLayoutTrackNumber>,
    val version: RowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedReferenceLine(
    val version: RowVersion<ReferenceLine>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedLocationTrack(
    val version: RowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: Set<KmNumber>,
)

data class PublishedSwitch(
    val version: RowVersion<TrackLayoutSwitch>,
    val trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
    val name: SwitchName,
    val operation: Operation,
    @JsonIgnore val changedJoints: List<SwitchJointChange>,
)

data class PublishedKmPost(
    val version: RowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation,
)

data class PublishedIndirectChanges(
    //Currently only used by Ratko integration
    @JsonIgnore val trackNumbers: List<PublishedTrackNumber>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
)

data class PublicationDetails(
    override val id: IntId<Publication>,
    override val publicationTime: Instant,
    override val publicationUser: UserName,
    override val message: String?,
    val trackNumbers: List<PublishedTrackNumber>,
    val referenceLines: List<PublishedReferenceLine>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
    val kmPosts: List<PublishedKmPost>,
    val ratkoPushStatus: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
    val indirectChanges: PublishedIndirectChanges,
    val split: SplitHeader?,
) : Publication(id, publicationTime, publicationUser, message) {
    val allPublishedTrackNumbers = trackNumbers + indirectChanges.trackNumbers
    val allPublishedLocationTracks = locationTracks + indirectChanges.locationTracks
    val allPublishedSwitches = switches + indirectChanges.switches
}

enum class DraftChangeType {
    TRACK_NUMBER, LOCATION_TRACK, REFERENCE_LINE, SWITCH, KM_POST,
}

enum class Operation(val priority: Int) {
    CREATE(0), MODIFY(1), DELETE(2), RESTORE(3), CALCULATED(4),
}

data class ValidatedPublicationCandidates(
    val validatedAsPublicationUnit: PublicationCandidates,
    val allChangesValidated: PublicationCandidates,
)

data class ValidatedAsset<T>(
    val id: IntId<T>,
    val errors: List<PublicationValidationError>,
)

data class PublicationCandidates(
    val trackNumbers: List<TrackNumberPublicationCandidate>,
    val locationTracks: List<LocationTrackPublicationCandidate>,
    val referenceLines: List<ReferenceLinePublicationCandidate>,
    val switches: List<SwitchPublicationCandidate>,
    val kmPosts: List<KmPostPublicationCandidate>,
) : Loggable {
    fun ids(): PublicationRequestIds = PublicationRequestIds(
        trackNumbers.map { candidate -> candidate.id },
        locationTracks.map { candidate -> candidate.id },
        referenceLines.map { candidate -> candidate.id },
        switches.map { candidate -> candidate.id },
        kmPosts.map { candidate -> candidate.id },
    )

    fun getValidationVersions() = ValidationVersions(
        trackNumbers = trackNumbers.map(TrackNumberPublicationCandidate::getPublicationVersion),
        referenceLines = referenceLines.map(ReferenceLinePublicationCandidate::getPublicationVersion),
        locationTracks = locationTracks.map(LocationTrackPublicationCandidate::getPublicationVersion),
        switches = switches.map(SwitchPublicationCandidate::getPublicationVersion),
        kmPosts = kmPosts.map(KmPostPublicationCandidate::getPublicationVersion),
    )

    fun filter(request: PublicationRequestIds) = PublicationCandidates(
        trackNumbers = trackNumbers.filter { candidate -> request.trackNumbers.contains(candidate.id) },
        referenceLines = referenceLines.filter { candidate -> request.referenceLines.contains(candidate.id) },
        locationTracks = locationTracks.filter { candidate -> request.locationTracks.contains(candidate.id) },
        switches = switches.filter { candidate -> request.switches.contains(candidate.id) },
        kmPosts = kmPosts.filter { candidate -> request.kmPosts.contains(candidate.id) },
    )

    override fun toLog(): String = logFormat(
        "trackNumbers" to toLog(trackNumbers),
        "locationTracks" to toLog(locationTracks),
        "referenceLines" to toLog(referenceLines),
        "switches" to toLog(switches),
        "kmPosts" to toLog(kmPosts),
    )

    private fun <T : PublicationCandidate<*>> toLog(list: List<T>): String = "${list.map(PublicationCandidate<*>::rowVersion)}"
}

data class ValidationVersions(
    val trackNumbers: List<ValidationVersion<TrackLayoutTrackNumber>>,
    val locationTracks: List<ValidationVersion<LocationTrack>>,
    val referenceLines: List<ValidationVersion<ReferenceLine>>,
    val switches: List<ValidationVersion<TrackLayoutSwitch>>,
    val kmPosts: List<ValidationVersion<TrackLayoutKmPost>>,
) {
    fun containsLocationTrack(id: IntId<LocationTrack>) = locationTracks.any { it.officialId == id }
    fun containsKmPost(id: IntId<TrackLayoutKmPost>) = kmPosts.any { it.officialId == id }
    fun containsSwitch(id: IntId<TrackLayoutSwitch>) = switches.any { it.officialId == id }

    fun findTrackNumber(id: IntId<TrackLayoutTrackNumber>) = trackNumbers.find { it.officialId == id }
    fun findLocationTrack(id: IntId<LocationTrack>) = locationTracks.find { it.officialId == id }
    fun findSwitch(id: IntId<TrackLayoutSwitch>) = switches.find { it.officialId == id }

    fun getTrackNumberIds() = trackNumbers.map { v -> v.officialId }
    fun getReferenceLineIds() = referenceLines.map { v -> v.officialId }
    fun getLocationTrackIds() = locationTracks.map { v -> v.officialId }
    fun getSwitchIds() = switches.map { v -> v.officialId }
    fun getKmPostIds() = kmPosts.map { v -> v.officialId }
}

data class PublicationGroup(
    val id: IntId<Split>,
)

data class ValidationVersion<T>(val officialId: IntId<T>, val validatedAssetVersion: RowVersion<T>)

data class PublicationRequestIds(
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
    val kmPosts: List<IntId<TrackLayoutKmPost>>,
) {
    operator fun minus(other: PublicationRequestIds) = PublicationRequestIds(
        trackNumbers - other.trackNumbers.toSet(),
        locationTracks - other.locationTracks.toSet(),
        referenceLines - other.referenceLines.toSet(),
        switches - other.switches.toSet(),
        kmPosts - other.kmPosts.toSet(),
    )
}

data class PublicationRequest(
    val content: PublicationRequestIds,
    val message: String,
)

data class PublicationResult(
    val publicationId: IntId<Publication>?,
    val trackNumbers: Int,
    val locationTracks: Int,
    val referenceLines: Int,
    val switches: Int,
    val kmPosts: Int,
)

enum class PublicationValidationErrorType { ERROR, WARNING }
data class PublicationValidationError(
    val type: PublicationValidationErrorType,
    val localizationKey: LocalizationKey,
    val params: LocalizationParams = LocalizationParams.empty,
) {
    constructor(
        type: PublicationValidationErrorType,
        key: String,
        params: Map<String, Any?> = emptyMap(),
    ) : this(type, LocalizationKey(key), localizationParams(params))
}

interface PublicationCandidate<T> {
    val type: DraftChangeType
    val id: IntId<T>
    val rowVersion: RowVersion<T>
    val draftChangeTime: Instant
    val userName: UserName
    val errors: List<PublicationValidationError>
    val operation: Operation?
    val publicationGroup: PublicationGroup?

    fun getPublicationVersion() = ValidationVersion(id, rowVersion)
}

data class TrackNumberPublicationCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    override val rowVersion: RowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublicationValidationError> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<TrackLayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class ReferenceLinePublicationCandidate(
    override val id: IntId<ReferenceLine>,
    override val rowVersion: RowVersion<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublicationValidationError> = listOf(),
    override val operation: Operation?,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class LocationTrackPublicationCandidate(
    override val id: IntId<LocationTrack>,
    override val rowVersion: RowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val errors: List<PublicationValidationError> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    val boundingBox: BoundingBox?,
) : PublicationCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class SwitchPublicationCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    override val rowVersion: RowVersion<TrackLayoutSwitch>,
    val name: SwitchName,
    val trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublicationValidationError> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    val location: Point?,
) : PublicationCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class KmPostPublicationCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    override val rowVersion: RowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublicationValidationError> = listOf(),
    override val operation: Operation,
    override val publicationGroup: PublicationGroup? = null,
    val location: Point?,
) : PublicationCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}

data class RemovedTrackNumberReferenceIds(
    val kmPostIds: List<IntId<GeometryKmPost>>,
    val alignmentIds: List<IntId<GeometryAlignment>>,
    val planIds: List<IntId<GeometryPlan>>,
)

data class SwitchLocationTrack(
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val oldVersion: RowVersion<LocationTrack>,
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
    val state: Change<LayoutState>,
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
    Yes, No, Unknown
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
)

fun <T : LayoutAsset<T>> toValidationVersion(layoutAsset: T): ValidationVersion<T> = ValidationVersion(
    officialId = layoutAsset.id as IntId,
    validatedAssetVersion = layoutAsset.version as RowVersion<T>,
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
    val newlyCreated: Boolean,
)
