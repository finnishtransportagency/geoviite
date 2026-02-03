package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId

data class StationLink(
    val trackNumberVersion: LayoutRowVersion<LayoutTrackNumber>,
    val startOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    val endOperationalPointVersion: LayoutRowVersion<OperationalPoint>,
    val locationTrackVersions: List<LayoutRowVersion<LocationTrack>>,
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
