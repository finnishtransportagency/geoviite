package fi.fta.geoviite.infra.publication

import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.tracklayout.LayoutKmPost
import fi.fta.geoviite.infra.tracklayout.LayoutSwitch
import fi.fta.geoviite.infra.tracklayout.LayoutTrackNumber
import fi.fta.geoviite.infra.tracklayout.LocationTrack
import fi.fta.geoviite.infra.tracklayout.OperationalPoint
import fi.fta.geoviite.infra.tracklayout.ReferenceLine

fun publicationRequestIds(
    trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
    locationTracks: List<IntId<LocationTrack>> = emptyList(),
    referenceLines: List<IntId<ReferenceLine>> = emptyList(),
    switches: List<IntId<LayoutSwitch>> = emptyList(),
    kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    operationalPoints: List<IntId<OperationalPoint>> = emptyList(),
): PublicationRequestIds =
    PublicationRequestIds(trackNumbers, locationTracks, referenceLines, switches, kmPosts, operationalPoints)

fun publicationRequest(
    trackNumbers: List<IntId<LayoutTrackNumber>> = emptyList(),
    locationTracks: List<IntId<LocationTrack>> = emptyList(),
    referenceLines: List<IntId<ReferenceLine>> = emptyList(),
    switches: List<IntId<LayoutSwitch>> = emptyList(),
    kmPosts: List<IntId<LayoutKmPost>> = emptyList(),
    operationalPoints: List<IntId<OperationalPoint>> = emptyList(),
    message: String = "test publication",
): PublicationRequest = PublicationRequest(
    PublicationRequestIds(trackNumbers, locationTracks, referenceLines, switches, kmPosts, operationalPoints),
    PublicationMessage.of(message),
)
