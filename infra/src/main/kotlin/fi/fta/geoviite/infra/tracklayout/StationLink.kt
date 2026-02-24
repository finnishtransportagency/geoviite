package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonIgnore
import fi.fta.geoviite.infra.common.IntId

data class StationLink(
    @JsonIgnore val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    @JsonIgnore val startOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    @JsonIgnore val endOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    @JsonIgnore val locationTrackVersions: List<LayoutRowVersion<LocationTrack>>,
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
