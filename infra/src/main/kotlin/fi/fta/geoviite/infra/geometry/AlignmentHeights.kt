package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.util.FileName

data class TrackMeterHeight(val m: Double, val meter: Double, val height: Double?, val point: Point)

data class KmHeights(val kmNumber: KmNumber, val trackMeterHeights: List<TrackMeterHeight>, val endM: Double)

data class PlanLinkingSummaryItem(
    val startM: Double,
    val endM: Double,
    val filename: FileName?,
    val alignmentHeader: AlignmentHeader<GeometryAlignment, LayoutState>?,
    val planId: DomainId<GeometryPlan>?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
)
