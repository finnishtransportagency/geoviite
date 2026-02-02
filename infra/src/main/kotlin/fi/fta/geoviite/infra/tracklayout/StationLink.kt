package fi.fta.geoviite.infra.tracklayout

import fi.fta.geoviite.infra.common.IntId

data class StationLink(
    val trackNumberId: IntId<LayoutTrackNumber>,
    val startOperationalPointId: IntId<OperationalPoint>,
    val endOperationalPointId: IntId<OperationalPoint>,
    val locationTrackIds: List<IntId<LocationTrack>>,
    val length: Double,
)
