package fi.fta.geoviite.infra.tracklayout

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonValue
import org.eclipse.emf.common.util.BasicMonitor.Delegating
import kotlin.math.abs

sealed interface AnyM<M : AnyM<M>>

data object SegmentM : AnyM<SegmentM>

sealed interface AlignmentM<M : AlignmentM<M>> : AnyM<M>

data object EdgeM : AlignmentM<EdgeM>

data object LocationTrackM : AlignmentM<LocationTrackM>

sealed interface GeocodingAlignmentM<M : GeocodingAlignmentM<M>> : AlignmentM<M>

data object ReferenceLineM : AlignmentM<ReferenceLineM>, GeocodingAlignmentM<ReferenceLineM>

data object PlanLayoutAlignmentM : AlignmentM<PlanLayoutAlignmentM>, GeocodingAlignmentM<PlanLayoutAlignmentM>

data class LineM<M : AnyM<M>> @JsonCreator(mode = JsonCreator.Mode.DELEGATING) constructor(
    @JsonValue val distance: Double,
) : Comparable<LineM<M>> {

    constructor(distance: Int) : this(distance.toDouble())

    override fun compareTo(other: LineM<M>): Int = distance.compareTo(other.distance)

    operator fun plus(offset: Double) = LineM<M>(distance + offset)

    operator fun plus(other: LineM<M>) = LineM<M>(distance + other.distance)

    operator fun minus(offset: Double) = LineM<M>(distance - offset)

    operator fun minus(other: LineM<M>) = LineM<M>(distance - other.distance)

    operator fun times(scale: Double) = LineM<M>(distance * scale)
    operator fun times(scale: Int) = LineM<M>(distance * scale)

    operator fun div(denominator: Double) = LineM<M>(distance / denominator)
    operator fun div(denominator: Int) = LineM<M>(distance / denominator)

    fun isFinite() = distance.isFinite()

    fun coerceAtLeast(min: Double) = LineM<M>(distance.coerceAtLeast(min))

    fun coerceAtMost(max: Double) = LineM<M>(distance.coerceAtMost(max))

    fun toInt() = distance.toInt()

    fun <OtherM: AnyM<OtherM>> castToDifferentM() = LineM<OtherM>(distance)
}

fun locationTrackM(m: Double) = LineM<LocationTrackM>(m)


fun <M : AnyM<M>> maxM(a: LineM<M>, b: LineM<M>) = if (a.distance > b.distance) a else b

fun <M : AnyM<M>> minM(a: LineM<M>, b: LineM<M>) = if (a.distance < b.distance) a else b

fun <M : AnyM<M>> abs(m: LineM<M>): LineM<M> = m.copy(distance = abs(m.distance))

fun LineM<LocationTrackM>.toEdgeM(edgeStart: LineM<LocationTrackM>) = LineM<EdgeM>(distance - edgeStart.distance)

fun <M : AlignmentM<M>> LineM<M>.toSegmentM(segmentStart: LineM<M>): LineM<SegmentM> =
    LineM(distance - segmentStart.distance)

fun LineM<EdgeM>.toLocationTrackM(edgeStart: LineM<LocationTrackM>) =
    LineM<LocationTrackM>(distance + edgeStart.distance)

fun <M : AlignmentM<M>> LineM<SegmentM>.segmentToAlignmentM(segmentStart: LineM<M>) =
    LineM<M>(distance + segmentStart.distance)

fun <M : AlignmentM<M>> LineM<EdgeM>.toAlignmentM(edgeStart: LineM<M>) = LineM<M>(distance + edgeStart.distance)

fun LineM<SegmentM>.toLocationTrackM(edgeStart: LineM<LocationTrackM>, segmentStart: LineM<EdgeM>) =
    LineM<LocationTrackM>(distance + edgeStart.distance + segmentStart.distance)
