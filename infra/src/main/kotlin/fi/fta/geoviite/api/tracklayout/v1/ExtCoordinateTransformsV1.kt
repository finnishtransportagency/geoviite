package fi.fta.geoviite.api.tracklayout.v1

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.geocoding.AlignmentEndPoint
import fi.fta.geoviite.infra.geocoding.AlignmentStartAndEnd
import fi.fta.geoviite.infra.geography.transformNonKKJCoordinate
import fi.fta.geoviite.infra.tracklayout.LAYOUT_SRID

internal fun <T> layoutAlignmentStartAndEndToCoordinateSystem(
    coordinateSystem: Srid,
    startAndEnd: AlignmentStartAndEnd<T>,
): AlignmentStartAndEnd<T> {
    return when (coordinateSystem) {
        LAYOUT_SRID -> startAndEnd
        else -> {
            val start =
                startAndEnd.start?.let { start ->
                    convertAlignmentEndPointCoordinateSystem(LAYOUT_SRID, coordinateSystem, start)
                }

            val end =
                startAndEnd.end?.let { end ->
                    convertAlignmentEndPointCoordinateSystem(LAYOUT_SRID, coordinateSystem, end)
                }

            startAndEnd.copy(start = start, end = end)
        }
    }
}

private fun convertAlignmentEndPointCoordinateSystem(
    sourceCoordinateSystem: Srid,
    targetCoordinateSystem: Srid,
    alignmentEndPoint: AlignmentEndPoint,
): AlignmentEndPoint {
    val convertedPoint =
        transformNonKKJCoordinate(sourceCoordinateSystem, targetCoordinateSystem, alignmentEndPoint.point)
    return alignmentEndPoint.copy(point = alignmentEndPoint.point.copy(x = convertedPoint.x, y = convertedPoint.y))
}
