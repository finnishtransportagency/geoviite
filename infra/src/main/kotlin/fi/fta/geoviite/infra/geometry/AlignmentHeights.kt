package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.AlignmentName
import fi.fta.geoviite.infra.common.DomainId
import fi.fta.geoviite.infra.common.ElevationMeasurementMethod
import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.common.VerticalCoordinateSystem
import fi.fta.geoviite.infra.geocoding.AddressPoint
import fi.fta.geoviite.infra.map.AlignmentHeader
import fi.fta.geoviite.infra.math.Point
import fi.fta.geoviite.infra.tracklayout.AlignmentM
import fi.fta.geoviite.infra.tracklayout.LayoutState
import fi.fta.geoviite.infra.tracklayout.LineM
import fi.fta.geoviite.infra.util.FileName

data class KmTicks<M : AlignmentM<M>>(val kmNumber: KmNumber, val ticks: List<TrackMeterTick<M>>, val endM: LineM<M>)

data class TrackMeterTick<M : AlignmentM<M>>(val addressPoint: AddressPoint<M>, val segmentIndex: Int?)

data class TrackMeterHeight<M : AlignmentM<M>>(
    val m: LineM<M>,
    val meter: Double,
    val height: Double?,
    val point: Point,
)

data class KmHeights<M : AlignmentM<M>>(
    val kmNumber: KmNumber,
    val trackMeterHeights: List<TrackMeterHeight<M>>,
    val endM: LineM<M>,
)

data class PlanLinkingSummaryItem<M : AlignmentM<M>>(
    val startM: LineM<M>,
    val endM: LineM<M>,
    val filename: FileName?,
    val alignmentHeader: AlignmentHeader<GeometryAlignment, AlignmentName, LayoutState>?,
    val planId: DomainId<GeometryPlan>?,
    val verticalCoordinateSystem: VerticalCoordinateSystem?,
    val elevationMeasurementMethod: ElevationMeasurementMethod?,
)
