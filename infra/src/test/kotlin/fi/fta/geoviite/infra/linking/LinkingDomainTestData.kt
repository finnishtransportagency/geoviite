import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.common.PublicationState
import fi.fta.geoviite.infra.publication.PublicationRequestIds
import fi.fta.geoviite.infra.publication.PublicationResult
import fi.fta.geoviite.infra.publication.PublicationService
import fi.fta.geoviite.infra.publication.ValidationTarget
import fi.fta.geoviite.infra.publication.ValidationVersions
import fi.fta.geoviite.infra.publication.draftTransitionOrOfficialState
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.ReferenceLine
import fi.fta.geoviite.infra.tracklayout.TrackLayoutKmPost
import fi.fta.geoviite.infra.tracklayout.TrackLayoutSwitch
import fi.fta.geoviite.infra.tracklayout.TrackLayoutTrackNumber
import fi.fta.geoviite.infra.util.FreeTextWithNewLines

fun publicationRequest(
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) =
    PublicationRequestIds(
        trackNumbers = trackNumbers,
        kmPosts = kmPosts,
        switches = switches,
        referenceLines = referenceLines,
        locationTracks = locationTracks,
    )

fun validationVersions(
    trackNumbers: List<LayoutRowVersion<TrackLayoutTrackNumber>> = listOf(),
    referenceLines: List<LayoutRowVersion<ReferenceLine>> = listOf(),
    kmPosts: List<LayoutRowVersion<TrackLayoutKmPost>> = listOf(),
    locationTracks: List<LayoutRowVersion<LocationTrack>> = listOf(),
    switches: List<LayoutRowVersion<TrackLayoutSwitch>> = listOf(),
    target: ValidationTarget = draftTransitionOrOfficialState(PublicationState.DRAFT, LayoutBranch.main),
) =
    ValidationVersions(
        target = target,
        trackNumbers = trackNumbers,
        referenceLines = referenceLines,
        kmPosts = kmPosts,
        locationTracks = locationTracks,
        switches = switches,
        splits = listOf(),
    )

fun publish(
    publicationService: PublicationService,
    branch: LayoutBranch = LayoutBranch.main,
    trackNumbers: List<IntId<TrackLayoutTrackNumber>> = listOf(),
    kmPosts: List<IntId<TrackLayoutKmPost>> = listOf(),
    switches: List<IntId<TrackLayoutSwitch>> = listOf(),
    referenceLines: List<IntId<ReferenceLine>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
) =
    publish(
        publicationService,
        publicationRequest(trackNumbers, kmPosts, switches, referenceLines, locationTracks),
        branch,
    )

fun publish(
    publicationService: PublicationService,
    request: PublicationRequestIds,
    branch: LayoutBranch = LayoutBranch.main,
): PublicationResult {
    val versions = publicationService.getValidationVersions(branch, request)
    val calculatedChanges = publicationService.getCalculatedChanges(versions)
    return publicationService.publishChanges(branch, versions, calculatedChanges, FreeTextWithNewLines.of("Test"))
}
