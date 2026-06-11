package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.LayoutBranch
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutRowVersion
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.OperationalPoint

fun publicationRequestIds(
    trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
    locationTracks: List<IntId<LocationTrack>> = emptyList(),
    switches: List<IntId<LayoutSwitch>> = emptyList(),
    kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    operationalPoints: List<IntId<OperationalPoint>> = emptyList(),
): PublicationRequestIds = PublicationRequestIds(trackNumbers, locationTracks, switches, kmPosts, operationalPoints)

fun publicationRequest(
    trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
    locationTracks: List<IntId<LocationTrack>> = emptyList(),
    switches: List<IntId<LayoutSwitch>> = emptyList(),
    kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    operationalPoints: List<IntId<OperationalPoint>> = emptyList(),
    message: String = "test publication",
): PublicationRequest =
    PublicationRequest(
        PublicationRequestIds(trackNumbers, locationTracks, switches, kmPosts, operationalPoints),
        PublicationMessage.of(message),
    )

fun validationVersions(
    trackNumbers: List<LayoutRowVersion<LayoutTrackNumber>> = listOf(),
    kmPosts: List<LayoutRowVersion<LayoutKmPost>> = listOf(),
    locationTracks: List<LayoutRowVersion<LocationTrack>> = listOf(),
    switches: List<LayoutRowVersion<LayoutSwitch>> = listOf(),
    operationalPoints: List<LayoutRowVersion<OperationalPoint>> = listOf(),
    target: LayoutContextTransition = PublicationInMain,
): ValidationVersions =
    ValidationVersions(
        target = target,
        trackNumbers = trackNumbers,
        locationTracks = locationTracks,
        switches = switches,
        kmPosts = kmPosts,
        operationalPoints = operationalPoints,
        splits = listOf(),
        trackBoundaryMoves = listOf(),
    )

fun publish(
    publicationService: PublicationService,
    branch: LayoutBranch = LayoutBranch.main,
    trackNumbers: List<IntId<LayoutTrackNumber>> = listOf(),
    locationTracks: List<IntId<LocationTrack>> = listOf(),
    switches: List<IntId<LayoutSwitch>> = listOf(),
    kmPosts: List<IntId<LayoutKmPost>> = listOf(),
    operationalPoints: List<IntId<OperationalPoint>> = listOf(),
): PublicationResultSummary =
    publish(
        publicationService,
        publicationRequestIds(trackNumbers, locationTracks, switches, kmPosts, operationalPoints),
        branch,
    )

fun publish(
    publicationService: PublicationService,
    request: PublicationRequestIds,
    branch: LayoutBranch = LayoutBranch.main,
): PublicationResultSummary {
    val versions = publicationService.getValidationVersions(branch, request)
    val calculatedChanges = publicationService.getCalculatedChanges(versions)
    return publicationService.publishChanges(
        branch,
        versions,
        calculatedChanges,
        PublicationMessage.of("Test"),
        PublicationCause.MANUAL,
    )
}
