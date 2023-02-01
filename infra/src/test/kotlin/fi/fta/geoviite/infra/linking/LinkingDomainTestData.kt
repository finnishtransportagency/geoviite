import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.linking.*
import fi.fta.geoviite.infra.tracklayout.*

fun publishRequest(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = PublishRequest(
    trackNumbers = trackNumbers,
    kmPosts = kmPosts,
    switches = switches,
    referenceLines = referenceLines,
    locationTracks = locationTracks,
)

fun publicationVersions(
    trackNumbers: List<Pair<IntId<TrackLayoutTrackNumber>, RowVersion<TrackLayoutTrackNumber>>> = listOf(),
    referenceLines: List<Pair<IntId<ReferenceLine>, RowVersion<ReferenceLine>>> = listOf(),
    kmPosts: List<Pair<IntId<TrackLayoutKmPost>, RowVersion<TrackLayoutKmPost>>> = listOf(),
    locationTracks: List<Pair<IntId<LocationTrack>, RowVersion<LocationTrack>>> = listOf(),
    switches: List<Pair<IntId<TrackLayoutSwitch>, RowVersion<TrackLayoutSwitch>>> = listOf(),
) = PublicationVersions(
    trackNumbers = trackNumbers.map { (id,version) -> PublicationVersion(id, version) },
    referenceLines = referenceLines.map { (id,version) -> PublicationVersion(id, version) },
    kmPosts = kmPosts.map { (id,version) -> PublicationVersion(id, version) },
    locationTracks = locationTracks.map { (id,version) -> PublicationVersion(id, version) },
    switches = switches.map { (id,version) -> PublicationVersion(id, version) },
)

fun publish(
    publicationService: PublicationService,
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = publish(publicationService, publishRequest(trackNumbers, kmPosts, switches, referenceLines, locationTracks))

fun publish(publicationService: PublicationService, request: PublishRequest): PublishResult {
    val versions = publicationService.getPublicationVersions(request)
    val calculatedChanges = publicationService.getCalculatedChanges(versions)
    return publicationService.publishChanges(versions, calculatedChanges, "Test")
}
