import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.*
import fi.fta.geoviite.infra.tracklayout.*

fun publicationRequest(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = PublicationRequestIds(
    trackNumbers = trackNumbers,
    kmPosts = kmPosts,
    switches = switches,
    referenceLines = referenceLines,
    locationTracks = locationTracks,
)

fun validationVersions(
    trackNumbers: List<Pair<IntId<TrackLayoutTrackNumber>, RowVersion<TrackLayoutTrackNumber>>> = listOf(),
    referenceLines: List<Pair<IntId<ReferenceLine>, RowVersion<ReferenceLine>>> = listOf(),
    kmPosts: List<Pair<IntId<TrackLayoutKmPost>, RowVersion<TrackLayoutKmPost>>> = listOf(),
    locationTracks: List<Pair<IntId<LocationTrack>, RowVersion<LocationTrack>>> = listOf(),
    switches: List<Pair<IntId<TrackLayoutSwitch>, RowVersion<TrackLayoutSwitch>>> = listOf(),
) = ValidationVersions(
    trackNumbers = trackNumbers.map { (id,version) -> ValidationVersion(id, version) },
    referenceLines = referenceLines.map { (id,version) -> ValidationVersion(id, version) },
    kmPosts = kmPosts.map { (id,version) -> ValidationVersion(id, version) },
    locationTracks = locationTracks.map { (id,version) -> ValidationVersion(id, version) },
    switches = switches.map { (id,version) -> ValidationVersion(id, version) },
)

fun publish(
    publicationService: PublicationService,
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = publish(publicationService, publicationRequest(trackNumbers, kmPosts, switches, referenceLines, locationTracks))

fun publish(publicationService: PublicationService, request: PublicationRequestIds): PublicationResult {
    val versions = publicationService.getValidationVersions(request)
    val calculatedChanges = publicationService.getCalculatedChanges(versions)
    return publicationService.publishChanges(versions, calculatedChanges, "Test")
}

fun <T> daoResponseToValidationVersion(response: DaoResponse<T>) = ValidationVersion<T>(response.id, response.rowVersion)
