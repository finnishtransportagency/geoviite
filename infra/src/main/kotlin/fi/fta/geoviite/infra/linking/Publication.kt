package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.geometry.GeometryAlignment
import fi.fta.geoviite.infra.geometry.GeometryKmPost
import fi.fta.geoviite.infra.geometry.GeometryPlan
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.tracklayout.*
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
    val changedKmNumbers: List<KmNumber>? = null,
    val operation: Operation,
    val publicationTime: Instant,
    val publicationUser: UserName,
    val message: String,
    val ratkoPushTime: Instant?,
) {
    val id: StringId<PublicationTableItem> = StringId(hashCode().toString())
}

open class Publication(
    open val id: IntId<Publication>,
    open val publicationTime: Instant,
    open val publicationUser: UserName,
    open val message: String?,
)

data class PublishedTrackNumber(
    val version: RowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    val operation: Operation,
)

data class PublishedReferenceLine(
    val version: RowVersion<ReferenceLine>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: List<KmNumber>,
)

data class PublishedLocationTrack(
    val version: RowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
    val changedKmNumbers: List<KmNumber>,
)

data class PublishedSwitch(
    val version: RowVersion<TrackLayoutSwitch>,
    val trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
    val name: SwitchName,
    val operation: Operation,
)

data class PublishedKmPost(
    val version: RowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation,
)

data class PublishedCalculatedChanges(
    val trackNumbers: List<PublishedTrackNumber>,
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
    val calculatedChanges: PublishedCalculatedChanges,
) : Publication(id, publicationTime, publicationUser, message)

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

data class PublishCandidates(
    val trackNumbers: List<TrackNumberPublishCandidate>,
    val locationTracks: List<LocationTrackPublishCandidate>,
    val referenceLines: List<ReferenceLinePublishCandidate>,
    val switches: List<SwitchPublishCandidate>,
    val kmPosts: List<KmPostPublishCandidate>,
) {
    fun candidatesInRequest(versions: PublicationVersions) = PublishCandidates(
        trackNumbers.filter { candidate -> versions.containsTrackNumber(candidate.id) },
        locationTracks.filter { candidate -> versions.containsLocationTrack(candidate.id) },
        referenceLines.filter { candidate -> versions.containsReferenceLine(candidate.id) },
        switches.filter { candidate -> versions.containsSwitch(candidate.id) },
        kmPosts.filter { candidate -> versions.containsKmPost(candidate.id) },
    )

    fun ids(): PublishRequestIds = PublishRequestIds(
        trackNumbers.map { candidate -> candidate.id },
        locationTracks.map { candidate -> candidate.id },
        referenceLines.map { candidate -> candidate.id },
        switches.map { candidate -> candidate.id },
        kmPosts.map { candidate -> candidate.id },
    )

    fun getTrackNumber(id: IntId<TrackLayoutTrackNumber>): TrackNumberPublishCandidate = getOrThrow(trackNumbers, id)
    fun getLocationTrack(id: IntId<LocationTrack>): LocationTrackPublishCandidate = getOrThrow(locationTracks, id)
    fun getReferenceLine(id: IntId<ReferenceLine>): ReferenceLinePublishCandidate = getOrThrow(referenceLines, id)
    fun getKmPost(id: IntId<TrackLayoutKmPost>): KmPostPublishCandidate = getOrThrow(kmPosts, id)
    fun getSwitch(id: IntId<TrackLayoutSwitch>): SwitchPublishCandidate = getOrThrow(switches, id)

    fun getPublicationVersions() = PublicationVersions(
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

private inline fun <reified T, reified S : PublishCandidate<T>> getOrThrow(all: List<S>, id: IntId<T>) =
    all.find { c -> c.id == id } ?: throw NoSuchEntityException(S::class, id)

data class PublicationVersions(
    val trackNumbers: List<PublicationVersion<TrackLayoutTrackNumber>>,
    val locationTracks: List<PublicationVersion<LocationTrack>>,
    val referenceLines: List<PublicationVersion<ReferenceLine>>,
    val switches: List<PublicationVersion<TrackLayoutSwitch>>,
    val kmPosts: List<PublicationVersion<TrackLayoutKmPost>>,
) {
    fun containsTrackNumber(id: IntId<TrackLayoutTrackNumber>) = trackNumbers.any { it.officialId == id }
    fun containsLocationTrack(id: IntId<LocationTrack>) = locationTracks.any { it.officialId == id }
    fun containsReferenceLine(id: IntId<ReferenceLine>) = referenceLines.any { it.officialId == id }
    fun containsSwitch(id: IntId<TrackLayoutSwitch>) = switches.any { it.officialId == id }
    fun containsKmPost(id: IntId<TrackLayoutKmPost>) = kmPosts.any { it.officialId == id }

    fun findTrackNumber(id: IntId<TrackLayoutTrackNumber>) = trackNumbers.find { it.officialId == id }
    fun findLocationTrack(id: IntId<LocationTrack>) = locationTracks.find { it.officialId == id }
}

data class PublicationVersion<T>(val officialId: IntId<T>, val draftVersion: RowVersion<T>)

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

    fun getPublicationVersion() = PublicationVersion(id, rowVersion)
}

data class TrackNumberPublishCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    override val rowVersion: RowVersion<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation,
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
) : PublishCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}

data class RemovedTrackNumberReferenceIds(
    val kmPostIds: List<IntId<GeometryKmPost>>,
    val alignmentIds: List<IntId<GeometryAlignment>>,
    val planIds: List<IntId<GeometryPlan>>,
)
