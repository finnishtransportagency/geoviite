package fi.fta.geoviite.infra.ratko

import fi.fta.geoviite.infra.common.*
import fi.fta.geoviite.infra.tracklayout.*
import fi.fta.geoviite.infra.util.FreeText

fun getUpdateLayoutAlignment(): Pair<LocationTrack, LayoutAlignment> {
    val alignment = LayoutAlignment(
        listOf(
            LayoutSegment(
                points = listOf(
                    LayoutPoint(
                        x = 288037.36665503116,
                        y = 7067239.269061557,
                        z = null,
                        m = 0.0,
                        cant = null
                    ),
                    LayoutPoint(
                        x = 288052.17096940894,
                        y = 7067276.688420034,
                        z = null,
                        m = 40.23542958800948,
                        cant = null
                    )
                ),
                sourceId = null,
                sourceStart = null,
                resolution = 100,
                switchId = null,
                startJointNumber = null,
                endJointNumber = null,
                start = 0.0,
                id = IndexedId(257, 0),
                source = GeometrySource.PLAN,
            ),
        ),
        sourceId = null,
    )
    return LocationTrack(
        name = AlignmentName("PTS 102"),
        description = FreeText("Pietarsaari raide: 102 V111 - V114"),
        type = LocationTrackType.SIDE,
        state = LayoutState.IN_USE,
        trackNumberId = IntId(70),
        sourceId = null,
        id = IntId(257),
        startPoint = null,
        endPoint = null,
        dataType = DataType.STORED,
        version = Version(1, 1),
        draft = null,
        externalId = Oid("1.2.246.578.3.10002.189390"),
        boundingBox = alignment.boundingBox,
        length = alignment.length,
        segmentCount = alignment.segments.size,
        duplicateOf = null,
        topologicalConnectivity = TopologicalConnectivityType.NONE,
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
