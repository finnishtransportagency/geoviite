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
    val trackNumbers: List<TrackNumberPublishCandidateWithOperation>,
    val referenceLines: List<ReferenceLinePublishCandidateWithOperation>,
    val locationTracks: List<LocationTrackPublishCandidateWithOperation>,
    val switches: List<SwitchPublishCandidateWithOperation>,
    val kmPosts: List<KmPostPublishCandidateWithOperation>,
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

data class PublishCandidates(
    val trackNumbers: List<TrackNumberPublishCandidateWithOperation>,
    val locationTracks: List<LocationTrackPublishCandidateWithOperation>,
    val referenceLines: List<ReferenceLinePublishCandidateWithOperation>,
    val switches: List<SwitchPublishCandidateWithOperation>,
    val kmPosts: List<KmPostPublishCandidateWithOperation>,
)

data class PublishRequest(
    val trackNumbers: List<IntId<TrackLayoutTrackNumber>>,
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
    val userName: UserName
    val errors: List<PublishValidationError>
}

interface WithOperation {
    val operation: Operation
}

data class TrackNumberPublishCandidate(
    override val id: IntId<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<TrackLayoutTrackNumber> {
    override val type = DraftChangeType.TRACK_NUMBER
}

data class TrackNumberPublishCandidateWithOperation(
    override val id: IntId<TrackLayoutTrackNumber>,
    val number: TrackNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<TrackLayoutTrackNumber>, WithOperation {
    override val type = DraftChangeType.TRACK_NUMBER
    constructor(candidate: TrackNumberPublishCandidate, operation: Operation) :
            this(
                candidate.id,
                candidate.number,
                candidate.draftChangeTime,
                candidate.userName,
                candidate.errors,
                operation
            )
}

data class ReferenceLinePublishCandidate(
    override val id: IntId<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<ReferenceLine> {
    override val type = DraftChangeType.REFERENCE_LINE
}

data class ReferenceLinePublishCandidateWithOperation(
    override val id: IntId<ReferenceLine>,
    val name: TrackNumber,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<ReferenceLine>, WithOperation {
    override val type = DraftChangeType.REFERENCE_LINE
    constructor(candidate: ReferenceLinePublishCandidate, operation: Operation) :
            this(
                candidate.id,
                candidate.name,
                candidate.trackNumberId,
                candidate.draftChangeTime,
                candidate.userName,
                candidate.errors,
                operation
            )
}

data class LocationTrackPublishCandidate(
    override val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<LocationTrack> {
    override val type = DraftChangeType.LOCATION_TRACK
}

data class LocationTrackPublishCandidateWithOperation(
    override val id: IntId<LocationTrack>,
    val name: AlignmentName,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    override val draftChangeTime: Instant,
    val duplicateOf: IntId<LocationTrack>?,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) : PublishCandidate<LocationTrack>, WithOperation {
    override val type = DraftChangeType.LOCATION_TRACK
    constructor(candidate: LocationTrackPublishCandidate, operation: Operation) :
            this(
                candidate.id,
                candidate.name,
                candidate.trackNumberId,
                candidate.draftChangeTime,
                candidate.duplicateOf,
                candidate.userName,
                candidate.errors,
                operation
            )
}

data class SwitchPublishCandidate(
    override val id: IntId<TrackLayoutSwitch>,
    val name: SwitchName,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<TrackLayoutSwitch> {
    override val type = DraftChangeType.SWITCH
}

data class SwitchPublishCandidateWithOperation(
    override val id: IntId<TrackLayoutSwitch>,
    val name: SwitchName,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) :  PublishCandidate<TrackLayoutSwitch>, WithOperation {
    override val type = DraftChangeType.SWITCH
    constructor(candidate: SwitchPublishCandidate, operation: Operation) :
            this(
                candidate.id,
                candidate.name,
                candidate.draftChangeTime,
                candidate.userName,
                candidate.errors,
                operation
            )
}

data class KmPostPublishCandidate(
    override val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
) : PublishCandidate<TrackLayoutKmPost> {
    override val type = DraftChangeType.KM_POST
}

data class KmPostPublishCandidateWithOperation(
    override val id: IntId<TrackLayoutKmPost>,
    val trackNumberId: IntId<TrackLayoutTrackNumber>,
    val kmNumber: KmNumber,
    override val draftChangeTime: Instant,
    override val userName: UserName,
    override val errors: List<PublishValidationError> = listOf(),
    override val operation: Operation
) :PublishCandidate<TrackLayoutKmPost>, WithOperation {
    override val type = DraftChangeType.KM_POST
    constructor(candidate: KmPostPublishCandidate, operation: Operation) : this(
        candidate.id,
        candidate.trackNumberId,
        candidate.kmNumber,
        candidate.draftChangeTime,
        candidate.userName,
        candidate.errors,
        operation
    )
}

fun operationFromStateTransition(oldState: LayoutState?, newState: LayoutState) =
    if (oldState != LayoutState.DELETED && newState == LayoutState.DELETED) Operation.DELETE
    else if (oldState == LayoutState.DELETED && newState != LayoutState.DELETED) Operation.RESTORE
    else if (oldState == null) Operation.CREATE
    else Operation.MODIFY

fun operationFromStateCategoryTransition(oldState: LayoutStateCategory?, newState: LayoutStateCategory) =
    if (oldState != LayoutStateCategory.NOT_EXISTING && newState == LayoutStateCategory.NOT_EXISTING) Operation.DELETE
    else if (oldState == LayoutStateCategory.NOT_EXISTING && newState != LayoutStateCategory.NOT_EXISTING) Operation.RESTORE
    else if (oldState == null) Operation.CREATE
    else Operation.MODIFY
