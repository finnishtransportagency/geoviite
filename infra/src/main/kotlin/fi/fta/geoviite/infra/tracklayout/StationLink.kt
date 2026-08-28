package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId
import fi.fta.geoviite.infra.common.TrackMeter

data class StationLink(
    @JsonIgnore val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    @JsonIgnore val startOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    @JsonIgnore val endOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    @JsonIgnore val locationTrackVersions: List<LayoutRowVersion<LocationTrack>>,
    val startAddress: TrackMeter,
    val endAddress: TrackMeter,
    val length: Double,
) {
    val trackNumberId: IntId<LayoutTrackNumber>
        get() = trackNumberVersion.id

    val startOperationalPointId: IntId<OperationalPoint>
        get() = startOperationalPointVersion.id

    val endOperationalPointId: IntId<OperationalPoint>
        get() = endOperationalPointVersion.id

    val locationTrackIds: List<IntId<LocationTrack>>
        get() = locationTrackVersions.map { it.id }
}

enum class StationLinkIssueType {
    UNREACHABLE_STATION_MIDPOINT,
    SUSPICIOUSLY_LONG_ROUTE,
}

enum class StationLinkIssueSeverity {
    WARNING,
    ERROR,
}

data class StationLinkIssue(
    val type: StationLinkIssueType,
    val severity: StationLinkIssueSeverity,
    val operationalPointId: IntId<OperationalPoint>,
    val otherOperationalPointId: IntId<OperationalPoint>?,
    val locationTrackId: IntId<LocationTrack>?,
    val trackNumberId: IntId<LayoutTrackNumber>?,
    val details: Map<String, String> = emptyMap(),
)

data class StationLinkResult(
    val links: List<StationLink>,
    val issues: List<StationLinkIssue>,
)
