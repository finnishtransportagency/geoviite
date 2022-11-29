package fi.fta.geoviite.infra.linking

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.integration.CalculatedChanges
import fi.fta.geoviite.infra.integration.RatkoPushStatus
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.LocalizationKey
import java.time.Instant

data class PublicationListingItem(
    val id: IntId<Publication>,
    val publishTime: Instant,
    val ratkoPushTime: Instant?,
    val status: RatkoPushStatus?,
    val trackNumberIds: List<IntId<LayoutTrackNumber>>,
    val hasRatkoPushError: Boolean,
)

data class PublicationHeader(
    val id: IntId<Publication>,
    val publishTime: Instant,
    val trackNumbers: List<IntId<LayoutTrackNumber>>,
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

data class PublicationChanges(
    val candidates: PublishCandidates,
    val calculatedChanges: CalculatedChanges,
)

data class PublishCandidates(
    val trackNumbers: List<TrackNumberPublishCandidate>,
    val locationTracks: List<LocationTrackPublishCandidate>,
    val referenceLines: List<ReferenceLinePublishCandidate>,
    val switches: List<SwitchPublishCandidate>,
    val kmPosts: List<KmPostPublishCandidate>,
)

data class PublishRequest(
    val trackNumbers: List<IntId<LayoutTrackNumber>>,
    val locationTracks: List<IntId<LocationTrack>>,
    val referenceLines: List<IntId<ReferenceLine>>,
    val switches: List<IntId<TrackLayoutSwitch>>,
    val kmPosts: List<IntId<TrackLayoutKmPost>>,
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
    constructor(type: PublishValidationErrorType, localizationKey: String, params: List<String> = listOf())
            : this(type, LocalizationKey(localizationKey), params)
}

interface PublishCandidate<T> {
    val type: DraftChangeType
    val id: IntId<T>
    val draftChangeTime: Instant
    val errors: List<PublishValidationError>
}

data class TrackNumberPublishCandidate(
    override val id: IntId<LayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<LayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class ReferenceLinePublishCandidate(
    override val id: IntId<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<LayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class LocationTrackPublishCandidate(
    override val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<LayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class SwitchPublishCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    val name: SwitchName,
    override val draftChangeTime: Instant,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class KmPostPublishCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: IntId<LayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}
