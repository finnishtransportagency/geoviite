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
import fi.fta.geoviite.infra.math.BoundingBox
import fi.fta.geoviite.infra.math.Point
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
}

data class PublicationTableItem(
    val name: String,
    val trackNumbers: List<TrackNumber>,
    val changedKmNumbers: String?,
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
) { constructor(oldValue: T?, newValue: T?, localizationKey: String?) : this(oldValue, newValue, localizationKey?.let(::LocalizationKey)) }

data class PublicationChange<T>(
    val propKey: PropKey,
    val value: ChangeValue<T>,
    val remark: PublicationChangeRemark?,
)

data class PropKey(
    val key: LocalizationKey,
    val params: List<String>? = null,
) { constructor(key: String, params: List<String>? = null) : this(LocalizationKey(key), params) }

data class PublicationChangeRemark(
    val key: String,
    val value: String,
)

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
) : Publication(id, publicationTime, publicationUser, message) {
    val allPublishedTrackNumbers = trackNumbers + indirectChanges.trackNumbers
    val allPublishedLocationTracks = locationTracks + indirectChanges.locationTracks
    val allPublishedSwitches = switches + indirectChanges.switches
}

enum class DraftChangeType {
    TRACK_NUMBER, LOCATION_TRACK, REFERENCE_LINE, SWITCH, KM_POST,
}

enum class Operation(val priority: Int) {
    CREATE(0),
    MODIFY(1),
    DELETE(2),
    RESTORE(3),
}

data class ValidatedPublishCandidates(
    val validatedAsPublicationUnit: PublishCandidates,
    val allChangesValidated: PublishCandidates,
)

data class ValidatedAsset<T>(
    val errors: List<PublishValidationError>,
    val id: IntId<T>
)

data class PublishCandidates(
    val trackNumbers: List<TrackNumberPublishCandidate>,
    val locationTracks: List<LocationTrackPublishCandidate>,
    val referenceLines: List<ReferenceLinePublishCandidate>,
    val switches: List<SwitchPublishCandidate>,
    val kmPosts: List<KmPostPublishCandidate>,
) {
    fun ids(): PublishRequestIds = PublishRequestIds(
        trackNumbers.map { candidate -> candidate.id },
        locationTracks.map { candidate -> candidate.id },
        referenceLines.map { candidate -> candidate.id },
        switches.map { candidate -> candidate.id },
        kmPosts.map { candidate -> candidate.id },
    )

    fun getValidationVersions() = ValidationVersions(
        trackNumbers = trackNumbers.map(TrackNumberPublishCandidate::getPublicationVersion),
        referenceLines = referenceLines.map(ReferenceLinePublishCandidate::getPublicationVersion),
        locationTracks = locationTracks.map(LocationTrackPublishCandidate::getPublicationVersion),
        switches = switches.map(SwitchPublishCandidate::getPublicationVersion),
        kmPosts = kmPosts.map(KmPostPublishCandidate::getPublicationVersion),
    )

    fun filter(request: PublishRequestIds) = PublishCandidates(
        trackNumbers = trackNumbers.filter { candidate -> request.trackNumbers.contains(candidate.id) },
        referenceLines = referenceLines.filter { candidate -> request.referenceLines.contains(candidate.id) },
        locationTracks = locationTracks.filter { candidate -> request.locationTracks.contains(candidate.id) },
        switches = switches.filter { candidate -> request.switches.contains(candidate.id) },
        kmPosts = kmPosts.filter { candidate -> request.kmPosts.contains(candidate.id) },
    )
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

    fun findTrackNumber(id: IntId<TrackLayoutTrackNumber>) = trackNumbers.find { it.officialId == id }
    fun findLocationTrack(id: IntId<LocationTrack>) = locationTracks.find { it.officialId == id }
    fun findSwitch(id: IntId<TrackLayoutSwitch>) = switches.find { it.officialId == id }
}

data class ValidationVersion<T>(val officialId: IntId<T>, val validatedAssetVersion: RowVersion<T>)

data class PublishRequestIds(
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
    val kmPosts: List<IntId<TrackLayoutKmPost>>,
) {
    operator fun minus(other: PublishRequestIds) = PublishRequestIds(
        trackNumbers - other.trackNumbers.toSet(),
        locationTracks - other.locationTracks.toSet(),
        referenceLines - other.referenceLines.toSet(),
        switches - other.switches.toSet(),
        kmPosts - other.kmPosts.toSet(),
    )
}

data class PublishRequest(
    val content: PublishRequestIds,
    val message: String,
)

data class PublishResult(
    val publishId: IntId<Publication>?,
    val trackNumbers: Int,
    val locationTracks: Int,
    val referenceLines: Int,
    val switches: Int,
    val kmPosts: Int,
)

enum class PublishValidationErrorType { ERROR, WARNING }
data class PublishValidationError(
    val type: PublishValidationErrorType,
    val localizationKey: LocalizationKey,
    val params: List<String> = listOf(),
) {
    constructor(type: PublishValidationErrorType, localizationKey: String, params: List<String> = listOf()) : this(
        type,
        LocalizationKey(localizationKey),
        params
    )
}

interface PublishCandidate<T> {
    val type: DraftChangeType
    val id: IntId<T>
    val rowVersion: RowVersion<T>
    val draftChangeTime: Instant
    val userName: UserName
    val errors: List<PublishValidationError>
    val operation: Operation?

    fun getPublicationVersion() = ValidationVersion(id, rowVersion)
}

data class TrackNumberPublishCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    override val rowVersion: RowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation,
    val boundingBox: BoundingBox?,
) : PublishCandidate<TrackLayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class ReferenceLinePublishCandidate(
    override val id: IntId<ReferenceLine>,
    override val rowVersion: RowVersion<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation?,
    val boundingBox: BoundingBox?,
) : PublishCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class LocationTrackPublishCandidate(
    override val id: IntId<LocationTrack>,
    override val rowVersion: RowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation,
    val boundingBox: BoundingBox?,
) : PublishCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class SwitchPublishCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    override val rowVersion: RowVersion<TrackLayoutSwitch>,
    val name: SwitchName,
    val trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation,
    val location: Point?,
) : PublishCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class KmPostPublishCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    override val rowVersion: RowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation,
    val location: Point?,
) : PublishCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}

data class RemovedTrackNumberReferenceIds(
    val kmPostIds: List<IntId<GeometryKmPost>>,
    val alignmentIds: List<IntId<GeometryAlignment>>,
    val planIds: List<IntId<GeometryPlan>>,
)

data class SwitchLocationTrack(val name: AlignmentName, val trackNumberId: IntId<TrackLayoutTrackNumber>,)

data class Change<T>(
    val old: T?,
    val new: T?,
)

data class LocationTrackChanges(
    val id: IntId<LocationTrack>,
    val startPointChanged: Boolean,
    val endPointChanged: Boolean,
    val name: Change<AlignmentName>,
    val description: Change<FreeText>,
    val state: Change<LayoutState>,
    val duplicateOf: Change<IntId<LocationTrack>>,
    val type: Change<LocationTrackType>,
    val length: Change<Double>,
    val startPoint: Change<Point>,
    val endPoint: Change<Point>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
)

data class SwitchChanges(
    val id: IntId<TrackLayoutSwitch>,
    val name: Change<SwitchName>,
    val state: Change<LayoutStateCategory>,
    val trapPoint: Change<Boolean>,
    val type: Change<SwitchType>,
    val owner: Change<MetaDataName>,
    val measurementMethod: Change<MeasurementMethod>,
    val joints: List<PublicationDao.PublicationSwitchJoint>,
    val locationTracks: List<SwitchLocationTrack>
)

data class ReferenceLineChanges(
    val id: IntId<ReferenceLine>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
    val length: Change<Double>,
    val startPoint: Change<Point>,
    val endPoint: Change<Point>,
)

data class TrackNumberChanges(
    val id: IntId<TrackLayoutTrackNumber>,
    val trackNumber: Change<TrackNumber>,
    val description: Change<FreeText>,
    val state: Change<LayoutState>,
    val startAddress: Change<TrackMeter>,
    val endPoint: Change<Point>,
    val startPointChanged: Boolean,
    val endPointChanged: Boolean,
)

data class KmPostChanges(
    val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: Change<IntId<TrackLayoutTrackNumber>>,
    val kmNumber: Change<KmNumber>,
    val state: Change<LayoutState>,
    val location: Change<Point>
)

fun <T : Draftable<T>> toValidationVersion(draftableObject: T) = ValidationVersion(
    officialId = draftableObject.id as IntId<T>,
    validatedAssetVersion = draftableObject.version as RowVersion<T>
)

fun toValidationVersions(
    trackNumbers: List<TrackLayoutTrackNumber> = emptyList(),
    referenceLines: List<ReferenceLine> = emptyList(),
    kmPosts: List<TrackLayoutKmPost> = emptyList(),
    locationTracks: List<LocationTrack> = emptyList(),
    switches: List<TrackLayoutSwitch> = emptyList(),
) = ValidationVersions(
    trackNumbers = trackNumbers.map(::toValidationVersion),
    referenceLines = referenceLines.map(::toValidationVersion),
    kmPosts = kmPosts.map(::toValidationVersion),
    locationTracks = locationTracks.map(::toValidationVersion),
    switches = switches.map(::toValidationVersion)
)
