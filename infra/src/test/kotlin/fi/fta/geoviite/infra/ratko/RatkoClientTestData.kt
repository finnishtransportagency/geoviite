package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.math.Point3DM
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

fun getUpdateLayoutAlignment(): Pair<LocationTrack, LayoutAlignment> {
    val alignment = alignment(
        listOf(
            segment(
                points = toTrackLayoutPoints(
                    Point3DM(x = 288037.36665503116, y = 7067239.269061557, m = 0.0),
                    Point3DM(x = 288052.17096940894, y = 7067276.688420034, m = 40.23542958800948),
                ),
                resolution = 100,
                start = 0.0,
                source = GeometrySource.PLAN,
            ).copy(id = IndexedId(257, 0)),
        ),
    )
    return LocationTrack(
        name = AlignmentName("PTS 102"),
        description = FreeText("Pietarsaari raide: 102 V111 - V114"),
        type = LocationTrackType.SIDE,
        state = LayoutState.IN_USE,
        trackNumberId = IntId(70),
        sourceId = null,
        id = IntId(257),
        dataType = DataType.TEMP,
        version = RowVersion(IntId(257), 1),
        draft = null,
        externalId = Oid("1.2.246.578.3.10002.189390"),
        boundingBox = alignment.boundingBox,
        length = alignment.length,
        segmentCount = alignment.segments.size,
        duplicateOf = null,
        topologicalConnectivity = TopologicalConnectivityType.NONE,
        topologyStartSwitch = null,
        topologyEndSwitch = null,
    ) to alignment
}

fun getTrackNumber(): TrackLayoutTrackNumber {
    return TrackLayoutTrackNumber(
        number = TrackNumber("0415"),
        description = FreeText("Pännäinen - Pietarsaari"),
        state = LayoutState.IN_USE,
        externalId = Oid("1.2.246.578.3.10001.188976"),
    )
}
