package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.error.NoSuchEntityException
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

open class Publication(
    open val id: IntId<Publication>,
    open val publicationTime: Instant,
    open val publicationUser: UserName,
)

data class PublishedTrackNumber(
    val version: RowVersion<TrackLayoutTrackNumber>,
    val id: IntId<TrackLayoutTrackNumber> = version.id,
    val number: TrackNumber,
    val operation: Operation
)

data class PublishedReferenceLine(
    val version: RowVersion<ReferenceLine>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
)

data class PublishedLocationTrack(
    val version: RowVersion<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val operation: Operation,
)

data class PublishedSwitch(
    val version: RowVersion<TrackLayoutSwitch>,
    val trackNumberIds: Set<IntId<TrackLayoutTrackNumber>>,
    val name: SwitchName,
    val operation: Operation
)

data class PublishedKmPost(
    val version: RowVersion<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    val operation: Operation
)

data class PublicationDetails(
    override val id: IntId<Publication>,
    override val publicationTime: Instant,
    override val publicationUser: UserName,
    val trackNumbers: List<PublishedTrackNumber>,
    val referenceLines: List<PublishedReferenceLine>,
    val locationTracks: List<PublishedLocationTrack>,
    val switches: List<PublishedSwitch>,
    val kmPosts: List<PublishedKmPost>,
    val ratkoPushStatus: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
) : Publication(id, publicationTime, publicationUser)

enum class DraftChangeType {
    TRACK_NUMBER,
    LOCATION_TRACK,
    REFERENCE_LINE,
    SWITCH,
    KM_POST,
}

enum class Operation {
    CREATE,
    MODIFY,
    DELETE,
    RESTORE
}

data class ValidatedPublishCandidates(
    val validatedAsPublicationUnit: PublishCandidates,
    val validatedSeparately: PublishCandidates,
)

data class PublishCandidates(
    val trackNumbers: List<TrackNumberPublishCandidate>,
    val locationTracks: List<LocationTrackPublishCandidate>,
    val referenceLines: List<ReferenceLinePublishCandidate>,
    val switches: List<SwitchPublishCandidate>,
    val kmPosts: List<KmPostPublishCandidate>,
) {
    fun splitByRequest(versions: PublicationVersions) =
        PublishCandidates(
            trackNumbers.filter { candidate -> versions.containsTrackNumber(candidate.id) },
            locationTracks.filter { candidate -> versions.containsLocationTrack(candidate.id) },
            referenceLines.filter { candidate -> versions.containsReferenceLine(candidate.id) },
            switches.filter { candidate -> versions.containsSwitch(candidate.id) },
            kmPosts.filter { candidate -> versions.containsKmPost(candidate.id) },
        ) to PublishCandidates(
            trackNumbers.filterNot { candidate -> versions.containsTrackNumber(candidate.id) },
            locationTracks.filterNot { candidate -> versions.containsLocationTrack(candidate.id) },
            referenceLines.filterNot { candidate -> versions.containsReferenceLine(candidate.id) },
            switches.filterNot { candidate -> versions.containsSwitch(candidate.id) },
            kmPosts.filterNot { candidate -> versions.containsKmPost(candidate.id) },
        )

    fun ids(): PublishRequest = PublishRequest(
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
}
private inline fun <reified T, reified S: PublishCandidate<T>> getOrThrow(all: List<S>, id: IntId<T>) =
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
    fun findReferenceLine(id: IntId<ReferenceLine>) = referenceLines.find { it.officialId == id }
    fun findSwitch(id: IntId<TrackLayoutSwitch>) = switches.find { it.officialId == id }
    fun findKmPost(id: IntId<TrackLayoutKmPost>) = kmPosts.find { it.officialId == id }
}

data class PublicationVersion<T>(val officialId: IntId<T>, val draftVersion: RowVersion<T>)

data class PublishRequest(
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
    val kmPosts: List<IntId<TrackLayoutKmPost>>,
) {
    operator fun minus(other: PublishRequest) =
        PublishRequest(
            trackNumbers - other.trackNumbers.toSet(),
            locationTracks - other.locationTracks.toSet(),
            referenceLines - other.referenceLines.toSet(),
            switches - other.switches.toSet(),
            kmPosts - other.kmPosts.toSet(),
        )
}

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
    constructor(type: PublishValidationErrorType, localizationKey: String, params: List<String> = listOf())
            : this(type, LocalizationKey(localizationKey), params)
}

interface PublishCandidate<T> {
    val type: DraftChangeType
    val id: IntId<T>
    val draftChangeTime: Instant
    val userName: UserName
    val errors: List<PublishValidationError>
    val operation: Operation?
}

data class TrackNumberPublishCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<TrackLayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class ReferenceLinePublishCandidate(
    override val id: IntId<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation?
) : PublishCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class LocationTrackPublishCandidate(
    override val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class SwitchPublishCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    val name: SwitchName,
    val trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class KmPostPublishCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}
