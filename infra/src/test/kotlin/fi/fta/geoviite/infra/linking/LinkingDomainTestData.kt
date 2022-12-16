import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.linking.PublishRequest
import fi.fta.geoviite.infra.linking.PublishResult
import fi.fta.geoviite.infra.linking.PublishService
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

fun publish(
    publicationService: PublishService,
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = publish(publicationService, publishRequest(trackNumbers, kmPosts, switches, referenceLines, locationTracks))

fun publish(publicationService: PublishService, request: PublishRequest): PublishResult =
    publicationService.publishChanges(publicationService.getPublicationVersions(request))
