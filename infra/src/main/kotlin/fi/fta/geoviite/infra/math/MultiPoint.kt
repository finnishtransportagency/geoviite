package fi.fta.geoviite.infra.math

import fi.fta.geoviite.infra.geography.create2DMultiPoint

data class MultiPoint(val points: List<Point>) {
    constructor(vararg points: Point) : this(points.toList())

    constructor(vararg points: IPoint) : this(points.map { Point(it) })

    init {
        require(points.isNotEmpty()) { "Cannot create empty MultiPoint" }
    }

    fun isWithinDistance(target: IPoint, distance: Double): Boolean =
        points.any { p -> lineLength(target, p) <= distance }

    fun toWkt(): String = create2DMultiPoint(points)
}
