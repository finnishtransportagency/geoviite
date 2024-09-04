package fi.fta.geoviite.infra.geography

import fi.fta.geoviite.infra.common.Srid
import fi.fta.geoviite.infra.math.IPoint

data class GeometryPoint(override val x: Double, override val y: Double, val srid: Srid) : IPoint {
    constructor(point: IPoint, srid: Srid) : this(point.x, point.y, srid)
}
