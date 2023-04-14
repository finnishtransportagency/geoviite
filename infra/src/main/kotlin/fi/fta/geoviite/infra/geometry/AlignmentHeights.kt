package fi.fta.geoviite.infra.geometry

import fi.fta.geoviite.infra.common.KmNumber
import fi.fta.geoviite.infra.util.FileName

data class TrackMeterHeight(
    val m: Double,
    val meter: Double,
    val height: Double?,
)
data class KmHeights(
    val kmNumber: KmNumber,
    val trackMeterHeights: List<TrackMeterHeight>,
)

data class PlanLinkingSummaryItem(
    val startM: Double,
    val endM: Double,
    val filename: FileName?,
)

data class AlignmentHeights (
    val alignmentStartM: Double,
    val alignmentEndM: Double,
    val kmHeights: List<KmHeights>,
    val linkingSummary: List<PlanLinkingSummaryItem>,
)

