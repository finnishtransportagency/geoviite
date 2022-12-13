package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.authorization.UserName
import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

data class PublicationListingItem(
    val id: IntId<Publication>,
    val publishTime: Instant,
    val ratkoPushTime: Instant?,
    val status: RatkoPushStatus?,
    val trackNumberIds: List<IntId<TrackLayoutTrackNumber>>,
    val hasRatkoPushError: Boolean,
)

data class PublicationHeader(
    val id: IntId<Publication>,
    val publishTime: Instant,
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
)

data class Publication(
    val id: IntId<Publication>,
    val publishTime: Instant,
    val trackNumbers: List<TrackNumberPublishCandidate>,
    val referenceLines: List<ReferenceLinePublishCandidate>,
    val locationTracks: List<LocationTrackPublishCandidate>,
    val switches: List<SwitchPublishCandidate>,
    val kmPosts: List<KmPostPublishCandidate>,
    val status: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
)

data class PublicationTime(
    val publishTime: Instant,
    val status: RatkoPushStatus?,
    val ratkoPushTime: Instant?,
)

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
    fun filteredToRequest(publishRequest: PublishRequest): PublishCandidates {
        val trackNumberIds = publishRequest.trackNumbers.toSet()
        val locationTrackIds = publishRequest.locationTracks.toSet()
        val referenceLineIds = publishRequest.referenceLines.toSet()
        val switchIds = publishRequest.switches.toSet()
        val kmPostIds = publishRequest.kmPosts.toSet()
        return PublishCandidates(
            trackNumbers.filter { candidate -> trackNumberIds.contains(candidate.id) },
            locationTracks.filter { candidate -> locationTrackIds.contains(candidate.id) },
            referenceLines.filter { candidate -> referenceLineIds.contains(candidate.id) },
            switches.filter { candidate -> switchIds.contains(candidate.id) },
            kmPosts.filter { candidate -> kmPostIds.contains(candidate.id) },
        )
    }

    fun ids(): PublishRequest = PublishRequest(
        trackNumbers.map { candidate -> candidate.id },
        locationTracks.map { candidate -> candidate.id },
        referenceLines.map { candidate -> candidate.id },
        switches.map { candidate -> candidate.id },
        kmPosts.map { candidate -> candidate.id },
    )
}

data class PublicationVersions(
    val trackNumbers: List<RowVersion<TrackLayoutTrackNumber>>,
    val locationTracks: List<RowVersion<LocationTrack>>,
    val referenceLines: List<RowVersion<ReferenceLine>>,
    val switches: List<RowVersion<TrackLayoutSwitch>>,
    val kmPosts: List<RowVersion<TrackLayoutKmPost>>,
)

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
