import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.RowVersion
import fi.fta.geoviite.infra.publication.PublicationRequestIds
import fi.fta.geoviite.infra.publication.PublicationResult
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidationVersion
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.tracklayout.DaoResponse
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber

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
    branch: LayoutBranch = LayoutBranch.main,
) = ValidationVersions(
    branch = branch,
    trackNumbers = trackNumbers.map { (id,version) -> ValidationVersion(id, version) },
    referenceLines = referenceLines.map { (id,version) -> ValidationVersion(id, version) },
    kmPosts = kmPosts.map { (id,version) -> ValidationVersion(id, version) },
    locationTracks = locationTracks.map { (id,version) -> ValidationVersion(id, version) },
    switches = switches.map { (id,version) -> ValidationVersion(id, version) },
    splits = listOf(),
)

fun publish(
    publicationService: PublicationService,
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) = publish(publicationService, publicationRequest(trackNumbers, kmPosts, switches, referenceLines, locationTracks))

fun publish(
    publicationService: PublicationService,
    request: PublicationRequestIds,
    branch: LayoutBranch = LayoutBranch.main,
): PublicationResult {
    val versions = publicationService.getValidationVersions(branch, request)
    val calculatedChanges = publicationService.getCalculatedChanges(versions)
    return publicationService.publishChanges(branch, versions, calculatedChanges, "Test")
}

fun <T> daoResponseToValidationVersion(response: DaoResponse<T>) = ValidationVersion<T>(response.id, response.rowVersion)
